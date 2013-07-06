package rses.visualisation;


import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;

import rses.Model;
import rses.inverse.ModelPerturber;
import rses.inverse.UserFunctionHandle;
import rses.inverse.anneal.DegenerateAnnealer;
import rses.inverse.anneal.UniformPerturber;
import rses.util.distributed.NodeInfo;
import rses.util.gui.VerticallyResizingJPanel;
import rses.Debug;





/** A class to allow the manipulation and visualisation of
 *   models and the parameter spaces that contain them.
 * 
 * @author peter rickwood
 *
 */ 
public class ModelExplorer extends VerticallyResizingJPanel implements ActionListener, ItemListener, rses.inverse.ModelObserver
{	
	
	private UserFunctionHandle handle = null;
	private NodeInfo[] nodelist;
	
	
	//a lock to prevent concurrent access to the current model 
	//and to the annealer that is perturbing it.
	private Object modelLock = new Object();
	private Model currentModel = null;
	//we use an inverter to search the parameter space
	//such that we can plot the 1d/2d slices and some other
	//statistics
	private DegenerateAnnealer annealer = null;
	
	
	private JScrollPane[] slicePlots;
		
	private JButton getNewModelButton = new JButton("get new model");
	private JButton stopRunningButton = new JButton("Stop");
	private JPanel modelDisplayPanel = new JPanel();
	
	private JComboBox plottingMode;
	private static final String oneDplotting = "1D plots";
	private static final String twoDplotting = "2D plots";

	
	public ModelExplorer(UserFunctionHandle handle, NodeInfo[] nodes)
	{		
		this.nodelist = nodes;
		this.handle = handle;
		int nd = handle.getDimensionOfModelSpace();
		this.slicePlots = new JScrollPane[(nd*(nd-1))/2+nd];
		
		this.plottingMode = new JComboBox(new String[] {oneDplotting, twoDplotting});
		this.plottingMode.setEditable(false);

		this.add(modelDisplayPanel);
		this.add(getNewModelButton);
		this.add(stopRunningButton);
		this.add(plottingMode);
		
		this.setFocusable(true);
		
		this.getNewModelButton.addActionListener(this);
		this.stopRunningButton.addActionListener(this);
		this.plottingMode.addItemListener(this);
		this.addKeyListener(rses.util.VerbosityListener.getVerbosityListener());
	}
	
	
	
	
	/** Set a new model for the explorer to explore around
	 * 
	 *  This will wipe out old plots and start new ones centred on the new Model.
	 * 
	 * @param m
	 */
	public void setModel(Model m) 
	{
		synchronized(modelLock) {
		if(m.getNumParameters() != handle.getDimensionOfModelSpace())
			throw new RuntimeException("Model has wrong number of dimensions!!! expected "+handle.getDimensionOfModelSpace()+", got "+m.getNumParameters());
		else  
			currentModel = m;
		
		if(!this.currentModel.isMisfitAvailable())
			this.currentModel.setMisfit(handle.getErrorForModel(currentModel.getModelParameters()));

		restartSlicePlots();
		Debug.println("updated/added 1d slice plots & started inverter", Debug.EXTRA_INFO);

		String[] pnames = new String[handle.getDimensionOfModelSpace()];
		for(int i =0; i < pnames.length; i++) pnames[i] = handle.getParameterName(i);
		this.modelDisplayPanel.removeAll(); 
		this.modelDisplayPanel.add(new ModelDisplayPanel(currentModel, pnames));

		}
		this.revalidate();
		this.repaint();
	}

	
	
	
	
	
	
	
	
	
	
	
	//prompt the user with a dialog asking them to fill in values
	private Model getNewModelFromUser()
	{
		return Model.guiPromptUserForModel(this, handle.getBoundsOnModelSpace());
	}
	
	
	
	
	


	public void itemStateChanged(ItemEvent event)
	{
		if(event.getStateChange() != ItemEvent.SELECTED)
			return;

		if(currentModel == null || slicePlots == null) 
			return;
		
		synchronized(modelLock)
		{
		Debug.println("In itemStateChanged", Debug.EXTRA_INFO);
		if(event.getSource() == this.plottingMode)
		{
			if(this.plottingMode.getSelectedItem().equals(oneDplotting))
			{
				Debug.println("In itemStateChanged case 1", Debug.EXTRA_INFO);
				

				//remove all 2d plots
				for(int i = currentModel.getNumParameters(); i < slicePlots.length; i++)
				{
					if(slicePlots[i] != null)
						this.remove(slicePlots[i]);
					slicePlots[i] = null;
				}
				
				//now add 1D plots if they arent already there
				for(int i = 0; i < currentModel.getNumParameters(); i++)
				{
					if(slicePlots[i] != null)
						continue;
					String name = handle.getParameterName(i);
					slicePlotPanel ufpp = makePlotPanel(name, handle.getBoundsOnModelSpace()[i]);
					JScrollPane jsp = new JScrollPane(ufpp, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
					jsp.setPreferredSize(new java.awt.Dimension(PLOT_WIDTH+20,PLOT_HEIGHT+20));
					jsp.setMaximumSize(new java.awt.Dimension(PLOT_WIDTH+20,PLOT_HEIGHT+20));
					this.add(jsp);
					this.slicePlots[i] = jsp;
				}
				
				//make sure annealer is only varying 1 dimension
				this.annealer.setModelPerturber(new UniformPerturber(handle.getBoundsOnModelSpace()));
			}
			else if(this.plottingMode.getSelectedItem().equals(twoDplotting))
			{
				//1D model, cant plot in 2D!!!
				if(handle.getDimensionOfModelSpace() < 2) 
					return;
				
				Debug.println("In itemStateChanged case 2", Debug.EXTRA_INFO);
				
				//keep the oneD plots..... we don't update them anymore, but
				//we can still have them around for reference
				if(currentModel != null)
				{
					int nd = currentModel.getNumParameters();
					
					for(int i = 0; i < nd; i++) //resize the old 1D plots to standard size
						//this.getPanel(i).unZoomAll();
						this.getPanel(i).unzoom();
					
					this.add2DPanels(); //add 2D plots
				}
						
				//make sure annealer is varying 2 dimensions
				if(this.annealer != null)
					this.annealer.setModelPerturber(new twoDperturber(handle.getBoundsOnModelSpace()));
			} 
		}
		else throw new IllegalStateException("unhandled item event -- "+event);
				
		this.revalidate();
		this.getParent().repaint();
		
		Debug.println("leaving itemStateChanged", Debug.EXTRA_INFO);
		}
	}

	
	
	public static final int PLOT_WIDTH=250;
	public static final int PLOT_HEIGHT=300;
	private slicePlotPanel makePlotPanel(String xname, double[] xbounds)
	{
		String[] names = new String[] {xname, "misfit"};
		double[][] bounds = new double[][] {xbounds,null};
		slicePlotPanel ufpp = new slicePlotPanel(names, bounds, this, new Dimension(PLOT_WIDTH,PLOT_HEIGHT));
		addPopupOptions(ufpp);
		return ufpp;
	}
	
	private slicePlotPanel makePlotPanel(String xname, String yname, double[] xbounds, double[] ybounds)
	{
		String[] names = new String[] {xname, yname, "misfit"};
		double[][] bounds = new double[][] {xbounds,ybounds,null};
		slicePlotPanel ufpp = new slicePlotPanel(names, bounds, this, new Dimension(PLOT_WIDTH,PLOT_HEIGHT));
		addPopupOptions(ufpp);
		return ufpp;
	}


	//add plotting options that are specific to our needs
	private void addPopupOptions(slicePlotPanel spp)
	{
		JPopupMenu popmenu = spp.getPopupMenu();
		
		javax.swing.JMenu scalemenu = new javax.swing.JMenu("rescale...");
		//The panel handles its own scaling
		scalemenu.add("rescale locally").addActionListener(spp);
		scalemenu.add("rescale globally").addActionListener(spp);
		//but we have to handle a request to alter scaling ofr all plots
		scalemenu.add("rescale all locally").addActionListener(this);
		scalemenu.add("rescale all globally").addActionListener(this);
		popmenu.add(scalemenu);
		
		
		javax.swing.JMenu curmod = new javax.swing.JMenu("current model..."); 
		curmod.add("show position of current model").addActionListener(this);
		curmod.add("do not show position of current model").addActionListener(this);
		popmenu.add(curmod);
		javax.swing.JMenu savemenu = new javax.swing.JMenu("Save all...");
		savemenu.add("save all images").addActionListener(this);
		savemenu.add("save all data (comma delimited)").addActionListener(this);
		savemenu.add("save all data (space delimited)").addActionListener(this);
		popmenu.add(savemenu);
	}
	
	
	
	public void actionPerformed(ActionEvent event)
	{
		Debug.println("In actionPerformed", Debug.EXTRA_INFO);
		if(event.getSource() == this.getNewModelButton)
		{
			Model m  = null;
			try {
				m = Model.guiGetNewModel(this, handle.getBoundsOnModelSpace());
				this.setModel(m);
			}
			catch(Exception e) {
				Debug.println("Error loading model "+e, Debug.INFO);
				Debug.println(e, Debug.EXTRA_INFO);
				javax.swing.JOptionPane.showMessageDialog(this, e.getMessage());				
			}
		}
		else if(event.getSource() == this.stopRunningButton)
			this.stop();
		else if(event.getActionCommand().equals("rescale all locally"))
			this.setPlotScaling(true);
		else if(event.getActionCommand().equals("rescale all globally"))
			this.setPlotScaling(false);
		else if(event.getActionCommand().equals("save all images"))
			this.saveImages();
		else if(event.getActionCommand().equals("save all data (comma delimited)"))
			this.saveData(",");
		else if(event.getActionCommand().equals("save all data (space delimited)"))
			this.saveData(" ");
		else if(event.getActionCommand().equals("show position of current model"))
			setShowPositionOfCurrentModel(true);
		else if(event.getActionCommand().equals("do not show position of current model"))
			setShowPositionOfCurrentModel(false);
		else
			throw new IllegalStateException("Unhandled/unknown event "+event);
	}
	
	

	private void setShowPositionOfCurrentModel(boolean show)
	{
		for(int i = 0; i < slicePlots.length; i++)
		{
			if(slicePlots[i] == null) continue;
			
			slicePlotPanel spp = getPlotPanel(i);
			int[] params = this.getParameters(i);
			if(params.length+1 != spp.getPlotDimension())
				throw new IllegalStateException("Number of parameters does not match plot dimension.. internal bug/error");
			
			
			if(show)
			{
				if(params.length == 1)
				{
					double xval = this.currentModel.getModelParameter(params[0]);
					spp.addXLine(xval);
				}
				else if(params.length == 2)
				{
					double xval = this.currentModel.getModelParameter(params[0]);
					double yval = this.currentModel.getModelParameter(params[1]);
					spp.addCross(xval, yval);
				}
			}
			else
			{
				if(params.length == 1)
					spp.removeXlines();
				else
					spp.removeCrosses();
			}
		}
	}

	/* When we want to restart plotting (with a new model), this method
	 * gets called. It restarts the inversion, relays out all the plots, and so on.
	 */
	private void restartSlicePlots()
	{
		//stop the current inverter if there is one
		if(this.annealer != null) {
			annealer.stopInversion();
			annealer.removeModelObserver(this);
			Debug.println("stopped old inverter....", Debug.EXTRA_INFO);
		}

		//remove any 1D or 2D plots
		for(int i =0; i < this.slicePlots.length; i++) {
			if(slicePlots[i] != null)
				this.remove(slicePlots[i]);
			slicePlots[i] = null;
		}
		this.maxmf = Double.NEGATIVE_INFINITY;
		this.minmf = Double.POSITIVE_INFINITY;
		
	
		//create the inverter, with the current model
		if(this.nodelist != null && this.nodelist.length > 1)
			annealer = DegenerateAnnealer.getDistributedInstance(this.nodelist, this.handle);
		else
			annealer = new DegenerateAnnealer(this.handle);
		annealer.setInitialModel(this.currentModel);
		annealer.addModelObserver(this);
		Debug.println("created degenerate annealer", Debug.EXTRA_INFO);

		//if we are in 1D plotting mode.... we add 1D plots
		int nd = this.currentModel.getNumParameters();
		if(this.plottingMode.getSelectedItem().equals(oneDplotting))
		{
			for(int i =0; i < nd; i++) {
				String xname = handle.getParameterName(i);
				slicePlotPanel ufpp = makePlotPanel(xname, handle.getBoundsOnModelSpace()[i]);
				JScrollPane jsp = new JScrollPane(ufpp, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
				jsp.setPreferredSize(new java.awt.Dimension(PLOT_WIDTH+20,PLOT_HEIGHT+20));
				jsp.setMaximumSize(new java.awt.Dimension(PLOT_WIDTH+20,PLOT_HEIGHT+20));
				this.slicePlots[i] = jsp;
				this.add(jsp);
			}
		}
		//if we are in 2d plotting mode.... we add 2D plots
		else if(this.plottingMode.getSelectedItem().equals(twoDplotting))
		{
			if(nd < 2) return; //1D model.. cant plot in 2D!!!
			
			this.add2DPanels();
			
			annealer.setModelPerturber(new twoDperturber(this.handle.getBoundsOnModelSpace()));
		}
		

		this.revalidate();
		Debug.println("added all plotting panels", Debug.EXTRA_INFO);

		//We do not need to set the termperature schudule for a 
		//DegenerateAnnealer, since by default it has an endless
		//schedule at temperature -INIFINITY
		
		//start the inversion and listen for events
		new Thread(annealer).start();
		
		Debug.println("started thread", Debug.EXTRA_INFO);
	}
	
		
	
	
	public void stop()
	{
		synchronized(modelLock) {
			this.annealer.stopInversion();
			Debug.println("Stopped inversion... some old results may still come in", Debug.INFO);
		}
	}
	
	
	
	private double maxmf = Double.NEGATIVE_INFINITY;
	double getMaxMisfit() { return maxmf; }
	

	private double minmf = Double.POSITIVE_INFINITY;
	double getMinMisfit() 
	{  
		if(minmf == Double.POSITIVE_INFINITY)
			return 0.0;
		else return minmf;
	}

	
	
	public void newModelsFound(Model[] models)
	{
		for(int i = 0; i < models.length; i++)
			newModelFound(models[i]);
			
		//repaint everything (should be fairly efficient, because we do a 
		//lot of buffering in the actual paint routines, so plots that havent
		//changed dont need to be repainted)
		this.repaint();
	}
	
	
	public void newModelFound(Model m)
	{
			
		boolean newmfbounds = false; 
		double[] mfbounds = null;
		
		//Debug.println("In newModelFound");
		synchronized(this.modelLock)
		{
			double mf = m.getMisfit();
			if(mf > maxmf) { maxmf = mf; newmfbounds = true; }
			if(mf < minmf) { minmf = mf; newmfbounds = true; }
			mfbounds = new double[] {minmf, maxmf};
		}
		//if we have new misfit bounds, we tell all plots that are
		//globally scaled
		for(int i =0; i < slicePlots.length; i++)
		{
			if(slicePlots[i] != null)
			{
				slicePlotPanel ufpp = getPlotPanel(i);
				if(ufpp.getPlotDimension() == 2 && !ufpp.isYscaledLocally())
					ufpp.setYbounds(mfbounds);
				else if(ufpp.getPlotDimension() == 3 && !ufpp.isZscaledLocally())
					ufpp.setZbounds(mfbounds);
			}
		}
		
		
		//find out which dimension(s) it varies in
		int varydim1 = -1;
		int varydim2 = -1;
		
		for(int i = 0; i < handle.getDimensionOfModelSpace(); i++)
		{
			if(m.getModelParameter(i) != currentModel.getModelParameter(i))
			{
				//more than 2 parameters vary.... this means that we have 
				//changed models but have got an 'old' event. In this case
				//we just throw it away   
				if(varydim1 != -1 && varydim2 != -1) {
					Debug.println("throwing away notification of old (stale) model change", Debug.EXTRA_INFO);
					return;
				}
				else if(varydim1 == -1)
					varydim1 = i;
				else
					varydim2 = i;
			}
		}
		
		//tell the appropriate plotter
		double[] point = new double[2];
		int index = 0;
		if(varydim2 == -1 && varydim1 == -1) //nothing varied!!!
			return;
		else if(varydim2 ==-1) //1 parameter varied
		{
			index = this.getPanelIndex(varydim1);
			point[0] = m.getModelParameter(varydim1);
			point[1] = m.getMisfit();
		}
		else { //2 parameters varied
			index = this.getPanelIndex(varydim1, varydim2);
			point = new double[3];
			point[0] = m.getModelParameter(varydim1);
			point[1] = m.getModelParameter(varydim2);
			point[2] = m.getMisfit();			
		}
		
		
		
		//Debug.println("calling slicePlots newModelFound with index "+index);
		//Debug.println(m);
		if(slicePlots[index] != null)
			//((slicePlotterPanel) this.slicePlots[index].getViewport().getView()).newModelFound(m);
			getPlotPanel(index).newPointFound(point);
		//Debug.println("leaving newModelFound");
		
	}


	private void setPlotScaling(boolean localScaling, int plotpanelindex)
	{
		//slicePlotterPanel spp = (slicePlotterPanel) slicePlots[i].getViewport().getView();	
		//spp.setScaling(localScaling);
		//spp.repaint();
		slicePlotPanel ufpp = getPlotPanel(plotpanelindex);
		
		if(localScaling)
		{
			if(ufpp.getPlotDimension() == 2)
				ufpp.scaleYlocally();
			else if(ufpp.getPlotDimension() == 3)
				ufpp.scaleZlocally();
		}
		else //global scaling
		{
			if(ufpp.getPlotDimension() == 2)
				ufpp.setYbounds(new double[] {this.minmf, this.maxmf});
			else if(ufpp.getPlotDimension() == 3)
				ufpp.setZbounds(new double[] {this.minmf, this.maxmf});
		}
		ufpp.repaint();		
	}
	

	public void setPlotScaling(boolean localScaling)
	{
		for(int i = 0; i < this.slicePlots.length; i++)
		{
			if(slicePlots[i] != null)
				setPlotScaling(localScaling, i);
		}
	}




	//save all the images to files
	void saveImages()
	{
		String res = javax.swing.JOptionPane.showInputDialog(this, "Enter file prefix for save files: ");
		if(res == null || res.trim().length() == 0)
			return; //user cancelled or entered empty stem
					
		for(int i = 0; i < this.slicePlots.length; i++)
		{
			if(slicePlots[i] == null)
				continue;
			
			//slicePlotterPanel spp = (slicePlotterPanel) plot.getViewport().getView();
			slicePlotPanel ufpp = getPlotPanel(i);
			int[] params = this.getParameters(i);
			try {
				String xname = handle.getParameterName(params[0]);
				if(params.length == 1) 
					ufpp.saveAsImage(new java.io.File(res+"."+xname+".png"));
				else {
					String yname = handle.getParameterName(params[1]);
					ufpp.saveAsImage(new java.io.File(res+"."+xname+"."+yname+".png"));
				}
			}
			catch(java.io.IOException ioe) {
				javax.swing.JOptionPane.showMessageDialog(this, "Error saving image...skipping image and continuing");
			}
		}
	}


	//save all the data to files
	void saveData(String delimiter)
	{
		String res = javax.swing.JOptionPane.showInputDialog(this, "Enter file prefix for save files: ");
		if(res == null || res.trim().length() == 0)
			return; //user cancelled or entered empty stem
					 
		int nd = this.handle.getDimensionOfModelSpace();
		
		for(int i = 0; i < this.slicePlots.length; i++)
		{
			if(slicePlots[i] == null) continue;
			//slicePlotterPanel spp = (slicePlotterPanel) plot.getViewport().getView();
			slicePlotPanel ufpp = getPlotPanel(i);
			int[] params = this.getParameters(i);
			try {
				String xname = handle.getParameterName(params[0]);
				if(params.length == 1) 
					ufpp.saveAsData(new java.io.File(res+"."+xname+".txt"), delimiter);
				else {
					String yname = handle.getParameterName(params[1]);
					ufpp.saveAsData(new java.io.File(res+"."+xname+"."+yname+".txt"), delimiter);
				}
			}
			catch(java.io.IOException ioe) {
				javax.swing.JOptionPane.showMessageDialog(this, "Error saving data...skipping ");
			}
		}
	}




	
	
	private void add2DPanels()
	{
		int nd = handle.getDimensionOfModelSpace();
		for(int i =0; i < nd; i++) //add 2D plots
		{
			for(int j = i+1; j < nd; j++)
			{ 
				String xname = handle.getParameterName(i);
				String yname = handle.getParameterName(j);
				int panelIndex = this.getPanelIndex(i, j);
				slicePlotPanel ufpp = makePlotPanel(xname, yname, handle.getBoundsOnModelSpace()[i], handle.getBoundsOnModelSpace()[j]);
				JScrollPane jsp = new JScrollPane(ufpp, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
				jsp.setPreferredSize(new java.awt.Dimension(PLOT_WIDTH+20,PLOT_HEIGHT+20));
				jsp.setMaximumSize(new java.awt.Dimension(PLOT_WIDTH+20,PLOT_HEIGHT+20));
				this.add(jsp);
				this.slicePlots[panelIndex] = jsp;
			}
		}
	}
	
	
	
	private int[] getParameters(int panelIndex)
	{ 
		int nd = handle.getDimensionOfModelSpace();
		
		if(panelIndex < nd)  //base case for 1d
			return new int[] {panelIndex};
		
		int count = nd;
		for(int i = 0; i < nd; i++)
			for(int j = i+1; j < nd; j++, count++)
				if(count == panelIndex)
					return new int[] {i,j};
		
		throw new IllegalArgumentException("panelIndex of "+panelIndex+" does not make sense");
	}
	
	
	
	
	private slicePlotPanel getPlotPanel(int index)
	{
		if(slicePlots[index] == null)
			return null;
		return (slicePlotPanel) slicePlots[index].getViewport().getView();
	}
	
	
	
	
	
	
	//private slicePlotterPanel getPanel(int p1)
	private slicePlotPanel getPanel(int p1)
	{
		int index = getPanelIndex(p1);
		//slicePlotterPanel spp = (slicePlotterPanel) this.slicePlots[index].getViewport().getView();
		slicePlotPanel spp = getPlotPanel(index);
		return spp;		
	}

	//private slicePlotterPanel getPanel(int p1, int p2)
	//{
	//	int index = getPanelIndex(p1, p2);
	//	slicePlotterPanel spp = (slicePlotterPanel) this.slicePlots[index].getViewport().getView();
	//	return spp;		
	//}
	
	private int getPanelIndex(int param1)
	{
		return param1;
	}
	
	private int getPanelIndex(int param1, int param2)
	{
		if(param1 > param2)
			return getPanelIndex(param2, param1);
		else if(param1 == param2)
			return -1; //illegal combination
		
		int nd = handle.getDimensionOfModelSpace();
		
		if(param1 == 0) //base case
			return nd+param2-1;
		
		//recursive case
		return getPanelIndex(param1-1, nd-1) + param2-param1;
	}
	
	
}
















































class slicePlotPanel extends UpdatingPointPlottingPanel
{
	
	private ModelExplorer owner;
	
	slicePlotPanel(String[] dimnames, double[][] dimbounds, ModelExplorer owner, Dimension basesize)
	{
		super(dimnames, dimbounds, basesize);
		this.owner = owner;
	}

	public void actionPerformed(ActionEvent event)
	{
		Debug.println("got actionevent in slicePlotPanel... command is "+event.getActionCommand(), Debug.EXTRA_INFO);
		
		//handle event
		if(event.getActionCommand().equals("rescale locally"))
		{
			if(this.getPlotDimension() == 2)
				this.scaleYlocally();
			else if(this.getPlotDimension() == 3)
				this.scaleZlocally();
		}
		else if(event.getActionCommand().equals("rescale globally"))
		{
			if(this.getPlotDimension() == 2)
				this.setYbounds(new double[] {owner.getMinMisfit(), owner.getMaxMisfit()});
			else if(this.getPlotDimension() == 3)
				this.setZbounds(new double[] {owner.getMinMisfit(), owner.getMaxMisfit()});
		}
		else
			super.actionPerformed(event);
		
		this.repaint();
	}
}









class twoDperturber implements ModelPerturber, java.io.Serializable
{
	private double[][] bounds;
	
	twoDperturber(double[][] bounds)
	{	this.bounds = bounds; }
	
	public double[] getPerturbedModel(double[] orig)
	{
		if(orig.length < 2)
			throw new IllegalArgumentException("cant perturb 2 dimensions of a "+orig.length+" dimensional model");
			
		double[] res = new double[orig.length];
		System.arraycopy(orig, 0, res, 0, orig.length);
		int d1 = (int) (Math.random()*res.length);
		int d2 = (int) (Math.random()*res.length);
		while(d1 == d2)
			d2 = (int) (Math.random()*res.length);
		
		res[d1] = bounds[d1][0] + Math.random()*(bounds[d1][1] - bounds[d1][0]);
		res[d2] = bounds[d2][0] + Math.random()*(bounds[d2][1] - bounds[d2][0]);
		return res;
	}
}

