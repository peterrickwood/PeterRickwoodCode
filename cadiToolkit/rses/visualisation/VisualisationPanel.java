package rses.visualisation;


import java.awt.Color;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;


import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.Border;
import javax.swing.event.ChangeListener;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.util.List;

import rses.inverse.ObservableInverter;
import rses.util.FileUtil;
import rses.util.Util;
import rses.util.gui.VerticallyResizingJPanel;
import rses.Debug;






public class VisualisationPanel extends VerticallyResizingJPanel implements ActionListener, rses.inverse.ModelObserver, ChangeListener, ItemListener
{
	private JLabel p1label = new JLabel("X axis");
	private JComboBox xaxis;
	private JLabel p2label = new JLabel("Y axis");
	private JComboBox yaxis;
	private JLabel p3label = new JLabel("Colour Gradient Axis");
	private JComboBox zaxis;
	
	private JButton addGraphButton = new JButton("Add graph");
	private JButton launch3DvisButton = new JButton("View 3D plot");
	
	private ObservableInverter inverter = null;
	
	public static final int MAX_MODELS = 10000; //only keep this many models
	private int modelIndex = 0;
	private java.util.ArrayList  models = new java.util.ArrayList(MAX_MODELS);
	
	//private java.util.Vector models = new java.util.Vector(); 
	 
	private double besterr = Double.POSITIVE_INFINITY;
	private java.util.Vector besterrs = new java.util.Vector();
	private rses.Model bestmod = null;
	private double worsterr = Double.NEGATIVE_INFINITY;
	
	
	//external frames that we need to tell to update themselves
	private java.util.ArrayList externalFrames = new java.util.ArrayList(); 
	
	private int numdimensions;
	
	//a slider to let the user change the resolution of the
	//misfit gradient
	// 
	//x%      -->     besterr --> besterr + (worsterr-besterr)*(x/100)
	private javax.swing.JSlider slider;
	private double slidepercent = 1.0;
	private GradientPanel gradientPanel = new GradientPanel(0, Double.MAX_VALUE);
	
	
	private JCheckBox showbestmodelCheckBox = new JCheckBox();
	
	
	public VisualisationPanel(ObservableInverter inverter, int maxdimensions)
	{		
		java.util.Vector xaxisparams = new java.util.Vector();
		java.util.Vector yaxisparams = new java.util.Vector();
		java.util.Vector zaxisparams = new java.util.Vector();
		
		
		xaxisparams.add("time");
		
		yaxisparams.add("misfit");
		yaxisparams.add("ln(misfit+1)");
		
		zaxisparams.add("none");
		zaxisparams.add("misfit");
		zaxisparams.add("time");

		this.inverter = inverter;
		this.numdimensions = maxdimensions;
		for(int i = 0; i < numdimensions; i++)
		{
			xaxisparams.add(inverter.getUserFunctionHandle().getParameterName(i));
			yaxisparams.add(inverter.getUserFunctionHandle().getParameterName(i));
		}
		
		xaxis = new JComboBox(xaxisparams);
		xaxis.setSelectedIndex(0);
		yaxis = new JComboBox(yaxisparams);
		yaxis.setSelectedIndex(0);
		zaxis = new JComboBox(zaxisparams);
		zaxis.setSelectedIndex(0);
		xaxis.setEditable(false);
		yaxis.setEditable(false);
		zaxis.setEditable(false);		
		addGraphButton.addActionListener(this);
		launch3DvisButton.addActionListener(this);


		Box b = Box.createHorizontalBox();
		Border bord = BorderFactory.createLineBorder(Color.black); 
		b.setBorder(bord);
		b.add(xaxis);
		b.add(yaxis);
		b.add(zaxis);
		b.add(this.addGraphButton);
		b.add(this.launch3DvisButton);
		this.add(b);
		
		slider = new javax.swing.JSlider(javax.swing.JSlider.HORIZONTAL, 0, 100, 0);
		
		//Turn on labels at major tick marks.
		slider.setMajorTickSpacing(50);
		slider.setMinorTickSpacing(25);
		slider.setPaintTicks(true);
		
		//Create the label table
		java.util.Hashtable labelTable = new java.util.Hashtable();
		labelTable.put( new Integer( 0 ), new JLabel("Low resolution") );
		labelTable.put( new Integer( 100 ), new JLabel("High resolution") );
		slider.setLabelTable( labelTable );

		slider.setPaintLabels(true);
		slider.addChangeListener(this);
		
		bestmoddialog = new JDialog();
		bestmoddialog.setTitle("Best Model So Far");
		bestmoddialog.setModal(false);
		bestmoddialog.setVisible(false);	
		bestmoddialog.setSize(450,300);
		bestmoddialog.setDefaultCloseOperation(javax.swing.WindowConstants.HIDE_ON_CLOSE);
		
		JPanel showbestmodelCheckBoxPanel = new JPanel();
		showbestmodelCheckBoxPanel.add(new JLabel("Show best model"));
		showbestmodelCheckBoxPanel.add(showbestmodelCheckBox);
		showbestmodelCheckBox.setSelected(false);
		showbestmodelCheckBox.addItemListener(this);
		
		
		Box bv1 = Box.createVerticalBox();
		bv1.setBorder(BorderFactory.createLineBorder(Color.black));
		
		Box b2 = Box.createHorizontalBox();
		b2.add(slider);
		b2.add(showbestmodelCheckBoxPanel);
		
		Box b3 = Box.createHorizontalBox();
		gradientPanel.setMinimumSize(b2.getMinimumSize());
		gradientPanel.setPreferredSize(b2.getPreferredSize());		
		b3.add(gradientPanel);
		
		bv1.add(b2);
		bv1.add(b3);		
		this.add(bv1);
	}







	public void itemStateChanged(ItemEvent e)
	{
		if(!(e.getSource() == this.showbestmodelCheckBox))
			throw new IllegalStateException("Unkown source for item event!?!?!");
		
		if(e.getStateChange() == ItemEvent.SELECTED)
		{
			bestmoddialog.validate();
			this.bestmoddialog.setVisible(true);
			bestmoddialog.repaint();
		}
		else if(e.getStateChange() == ItemEvent.DESELECTED)
			this.bestmoddialog.setVisible(false);
	}



	//implements ChangeListener
	public void stateChanged(javax.swing.event.ChangeEvent e) 
	{
		JSlider source = (JSlider)e.getSource();
		
		if(!source.getValueIsAdjusting())
		{		
			//value isn't adjusting, it's finished being adjusted, so we
			//rescale evrything as appropriate
			int pct = (int)source.getValue();
			
			this.slidepercent = (100-pct)/100.0;
			double gradend = Math.log(besterr+1) + slidepercent*(Math.log(worsterr+1) - Math.log(besterr+1));
			this.gradientPanel.setBounds(besterr, Math.exp(gradend)-1);
			
			//now repaint
			this.repaint();
		}
	}






	public void newModelsFound(rses.Model[] models)
	{
		if(models.length >= MAX_MODELS)
			Debug.println("WARNING... recieved greater than "+MAX_MODELS+" models in a single hit.... painting may now be wonky......", Debug.CRITICAL);
		
		for(int i = 0; i < models.length; i++)
			updateModelsList(models[i]);
		for(int i =0; i < this.externalFrames.size(); i++)
			((javax.swing.JFrame) this.externalFrames.get(i)).repaint();
		
		this.repaint();			
	}




	//stick a model in the models list and update best/worst model
	//(if appropriate). Do not tell any model observers
	private void updateModelsList(rses.Model model)
	{
		//if the inverter is lazy and isnt setting discovery times, then 
		//we do it here
		if(model.getDiscoveryTime() == -1L)
		{
			Debug.println("***WARNING*** VisualisationPanel notified of new model with discovery time unset", Debug.INFO);
			model.setDiscoveryTime(inverter.getRunningTime());
		} 

		//if its a new best model then we show that 
		if(model.getMisfit() < this.besterr) 
		{
			synchronized(modelLock) {
				this.besterr = model.getMisfit();
				this.bestmod = model; 
				this.besterrs.add(model);
			}			
		
			double gradend = Math.log(besterr+1)+slidepercent*(Math.log(worsterr+1)-Math.log(besterr+1));
			gradientPanel.setBounds(besterr, Math.exp(gradend)-1);	
			bestmoddialog.setVisible(false);
			bestmoddialog.getContentPane().removeAll();
			
			String[] pnames = new String[inverter.getUserFunctionHandle().getDimensionOfModelSpace()];
			for(int i =0; i < pnames.length; i++) pnames[i] = inverter.getUserFunctionHandle().getParameterName(i);
			bestmoddialog.getContentPane().add(new ModelDisplayPanel(model, pnames));
			
			//old code when we didnt have a ModelDisplayPanel class
			/*JPanel basepanel = new JPanel();
			basepanel.setLayout(new BoxLayout(basepanel, BoxLayout.Y_AXIS));
			int nd = model.getNumParameters();
			for(int i =0; i < nd; i++) 
				basepanel.add(new JLabel("P"+(i+1)+" = "+model.getModelParameter(i))); 
			
			basepanel.add(new JLabel("misfit = "+model.getMisfit()));
			javax.swing.JScrollPane sp = new javax.swing.JScrollPane(basepanel);
			bestmoddialog.getContentPane().add(sp);*/
			
			if(this.showbestmodelCheckBox.isSelected())
			{	
				bestmoddialog.validate();
				bestmoddialog.setVisible(true);
				bestmoddialog.repaint();
			}
		}
		
		synchronized(modelLock) 
		{
			if(model.getMisfit() > this.worsterr) {
				this.worsterr = model.getMisfit();
				double gradend = Math.log(besterr+1) + slidepercent*(Math.log(worsterr+1)-Math.log(besterr+1));
				gradientPanel.setBounds(besterr, Math.exp(gradend)-1);
			}

			int nummods = models.size();
			if(modelIndex == nummods)
				this.models.add(model);
			else if(modelIndex > nummods)
				throw new IllegalStateException("Impossible case reached.... modelIndex is out of whack");
			else //replacing an existing model
				this.models.set(modelIndex, model);	
			modelIndex = (modelIndex + 1) % MAX_MODELS;
		}
	}








	private JDialog bestmoddialog;
	private Object modelLock = new Object();
	//synchronization note: we need to make sure that the models
	//array (and also bestmod, besterr, worsterr, etc) is only fiddled 
	//with by one thread at a time. Declaring
	//this method to be synchornized is a mistake, because this
	//object is locked by event handler threads. A finer grained
	//locking scheme is required to protect just models. We do this
	//with the modelLock variable 
	//
	//implements ModelObserver
	public void newModelFound(rses.Model model)
	{ 
		updateModelsList(model);
		for(int i =0; i < this.externalFrames.size(); i++)
			((javax.swing.JFrame) this.externalFrames.get(i)).repaint();
		
		this.repaint();
	}




	public void actionPerformed(java.awt.event.ActionEvent e)
	{
		if(e.getSource() == this.launch3DvisButton)
		{
			List modelsClone = null;
			synchronized(modelLock) 
			{
				modelsClone = (List) this.models.clone();
			}
			double gradend = Math.log(besterr+1) + slidepercent*(Math.log(worsterr+1) - Math.log(besterr+1));
			String[] pnames = new String[this.numdimensions];
			for(int i =0; i < numdimensions; i++)
				pnames[i] = inverter.getUserFunctionHandle().getParameterName(i);
			
			throw new UnsupportedOperationException("Need to fix 3D plot window before this can be supported");
			/*ThreeDimensionalPlotWindow.guiPromptUserForPlot(modelsClone, this.besterr, 
				Math.exp(gradend)-1, this.inverter.getUserFunctionHandle().getBoundsOnModelSpace(), pnames);
			return;*/
		}
		else if(!e.getActionCommand().equals("Add graph"))
			throw new IllegalStateException();
			
		String xitem = (String) xaxis.getSelectedItem();
		String yitem =  (String) yaxis.getSelectedItem();
		String zitem =  (String) zaxis.getSelectedItem();
		
		if(xitem == null || yitem == null || zitem == null)
			throw new IllegalStateException("null value found when not expected.... contact software vendor ");
		
		if(xitem.equals("none") || yitem.equals("none")) {
			JOptionPane.showMessageDialog(this, "missing value for X or Y axis");
			return;
		}
		
		String[] pnames = new String[this.numdimensions];
		for(int i =0; i < pnames.length; i++)
			pnames[i] = inverter.getUserFunctionHandle().getParameterName(i);
		
		ParamPlotPanel pp = new ParamPlotPanel(xitem, rses.util.Util.getIndex(xitem, pnames), 
		                                                          yitem, rses.util.Util.getIndex(yitem, pnames), zitem);
		pp.setSize(new java.awt.Dimension(300,300));
		this.add(pp);
		
		this.revalidate();
		this.repaint();
		
	}








	class ParamPlotPanel extends JPanel implements MouseListener, ActionListener
	{
		private boolean distanceFade = false;
		private double fadepercent = 1.0;
		
		private String colourParam;
		
		private String xparam;
		private Integer p1 = null;
		private String yparam;
		private Integer p2 = null;
		
		private double xupper, xlower;
		private double yupper, ylower;
		javax.swing.JPopupMenu popmenu = new javax.swing.JPopupMenu();
		
		private String axislabel;
		public void setAxisLabel(String label)
		{
			this.axislabel = label;
		}
		
		
		public ParamPlotPanel(String xaxis, int xnum, String yaxis, int ynum, String colourParam)
		{
			this.colourParam = colourParam;
			this.addMouseListener(this);
			

			popmenu.add("close").addActionListener(this);			
			popmenu.add("in separate window").addActionListener(this);
			popmenu.add("details").addActionListener(this);
			javax.swing.JMenu savemenu = new javax.swing.JMenu("save as...");
			savemenu.add("image").addActionListener(this);
			savemenu.add("comma seperated text").addActionListener(this);
			savemenu.add("space seperated text").addActionListener(this);
			popmenu.add(savemenu);
			javax.swing.JMenu dfmenu = new javax.swing.JMenu("distance fade...");
			dfmenu.add("distance fade 10%").addActionListener(this);
			dfmenu.add("distance fade 25%").addActionListener(this);
			dfmenu.add("distance fade 50%").addActionListener(this);
			popmenu.add(dfmenu);
			//this.setOpaque(true);
			//this.setBackground(Color.white);
			
			if(xnum >= 0)
				this.p1 = new Integer(xnum);
			if(ynum >= 0)
				this.p2 = new Integer(ynum);

						 
			axislabel = ""+xaxis+" (x)  VS  "+yaxis+" (y)";
			
			this.setBorder(BorderFactory.createLineBorder(Color.black));
			
			this.xparam = xaxis;
			this.yparam = yaxis;

			if(inverter == null)
				throw new IllegalStateException("No inverter set as as source for graph data in PlottingFunction");
				
			//work out upper/lower bounds for each parameter
			//if we can work them out statically (like in the case of parameters)
			if(p1 != null)
			{
				xupper = inverter.getUserFunctionHandle().getBoundsOnModelSpace()[p1.intValue()][1]; 
				xlower = inverter.getUserFunctionHandle().getBoundsOnModelSpace()[p1.intValue()][0];
			}
			if(p2 != null)
			{
				yupper = inverter.getUserFunctionHandle().getBoundsOnModelSpace()[p2.intValue()][1];
				ylower = inverter.getUserFunctionHandle().getBoundsOnModelSpace()[p2.intValue()][0];
			}			
		}
		
		
		
		
		
		
		//wipe the graphics clean and paint a white background
		private void wipeClean(java.awt.Graphics g)
		{
			Color oldcol = g.getColor(); 
			g.setColor(Color.white);
			g.fillRect(0,0,displaySize.width, displaySize.height);
			g.setColor(oldcol);			
		}
		
		
		
		
		private java.awt.Dimension displaySize;
		private java.awt.Image offscreen;
		private java.awt.Graphics bufferGraphics;  
		private int numDrawn = 0; //the number of models that we have drawn in our off screen buffer
		private double slidepctbase;
		private double besterrbase;
		private double worsterrbase;
		
		
		public void paintComponent(java.awt.Graphics g)
		{
			paintPoints(g, false);
		}
		

				
		//paint the component and optionally (if returnPoints is true) return the points 
		//that have been plotted
		private float[][] paintPoints(java.awt.Graphics g, boolean returnPoints)
		{
			java.awt.Graphics onscreen = g; 
			
			
			//The list of models that we are to plot. This can either be all the models
			//(if we are doing a complete repaint), or only some of the models
			//(if we are just doing an update).
			List baseModels = models; //at first, assume all models need to be drawn
			
			//If it is the first calll to paint, or the size has changed, or the type of plotting
			//has changed (i.e. distance fade is on or something), then we need to recalculate and 
			//redraw everything
			//special case -- if the x variable is time, we have to redraw everything anyway
			//special case 2 -- if the slider (governing shading, etc) has changed, then we
			//                      need to redraw
			//special case 3 -- If a new best or worst model has been found, we need to redraw
			//special case 4 -- If we are colouring the points based on time, we need to redraw always
			//special case 5 -- If the caller has asked for a list of all the points plotted, we replot all 
			//                       points and return to the caller a list of all those points (This facility
			//                       is used to implement saving of data from plots)
			if(displaySize == null || !this.getSize().equals(displaySize) || this.numDrawn == 0
				|| xparam.equalsIgnoreCase("time") || slidepercent != slidepctbase ||
				besterr != besterrbase || worsterr != worsterrbase ||
				colourParam.equalsIgnoreCase("time") || returnPoints) 
			{ 
				slidepctbase = slidepercent;
				besterrbase = besterr;
				worsterrbase = worsterr;
				displaySize = this.getSize();
				offscreen = createImage(displaySize.width, displaySize.height);
				bufferGraphics = offscreen.getGraphics();
				//super.paintComponent(bufferGraphics);
				wipeClean(bufferGraphics); //draw a white background
				numDrawn = 0; //need to redraw everything
				//Debug.println("doing full draw of "+baseModels.size());
			}
			else { //our offscreen buffer is (almost) up to date, we just need to redraw the new points
				baseModels = new java.util.ArrayList();
				synchronized(modelLock) 
				{
					int nummods = models.size();
					if(nummods == MAX_MODELS) {
						for(int i = numDrawn % MAX_MODELS; i != modelIndex; i=(i+1)%MAX_MODELS)
							baseModels.add(models.get(i));
					}
					else for(int i = numDrawn; i < modelIndex; i++)
						baseModels.add(models.get(i));
				}
				//Debug.println("doing partial draw of "+baseModels.size());
			}
			g = bufferGraphics; 


			int width = displaySize.width;
			int height = displaySize.height;
			
			//work out upper/lower bounds for x/y if we have to
			if(xparam.equalsIgnoreCase("time")) {
				xlower = 0; xupper = inverter.getRunningTime()/1000;
			}
			
			if(yparam.equalsIgnoreCase("misfit")) {
				ylower = besterr;
				double ge = Math.log(besterr+1) + slidepercent*(Math.log(worsterr+1)-Math.log(besterr+1));
				yupper = Math.exp(ge)-1; 
			}
			else if(yparam.equalsIgnoreCase("ln(misfit+1)")) {
				ylower = Math.log(besterr+1);
				yupper = ylower + slidepercent*(Math.log(worsterr+1)-ylower); 
			}

			
			
			//adjust for inset
			java.awt.Insets insets = this.getBorder().getBorderInsets(this); 
			java.awt.FontMetrics fm = g.getFontMetrics();
			int xinset = fm.stringWidth("0.000")+insets.left;
			int lineheight =  fm.getMaxAscent()+fm.getMaxDescent()+fm.getLeading(); 
			int yinset = lineheight*2;
			width -= xinset;
			height -= yinset;
			
			//only need to draw axes once (except for special cases, which are handled by a full redraw anyway)
			if(numDrawn == 0) 
			{
				//draw y axis and y axis label
				g.drawString(""+Util.safeGetTruncatedReal(yupper,5,"XXXXX"), insets.left, insets.top+fm.getMaxAscent());
				g.drawString(""+Util.safeGetTruncatedReal(ylower,5,"XXXXX"), insets.left, insets.top+height);
				g.drawLine(xinset, 0, xinset, height+insets.top); //yaxis
						
				//draw x axis and x axis label
				g.drawString(""+Util.safeGetTruncatedReal(xlower,5,"XXXXX"), xinset, displaySize.height-lineheight-fm.getMaxDescent());
				String labstr = Util.safeGetTruncatedReal(xupper,5,"XXXXX");
				g.drawString(labstr, displaySize.width-fm.stringWidth(labstr), displaySize.height-lineheight-fm.getMaxDescent());
				g.drawLine(xinset, height+insets.top, displaySize.width, height+insets.top); //xaxis
				
				int stringwidth = fm.stringWidth(axislabel); 
				java.awt.Font oldfont = g.getFont();
				g.setFont(oldfont.deriveFont(java.awt.Font.BOLD));
				g.drawString(axislabel, (displaySize.width-stringwidth)/2, displaySize.height-fm.getMaxDescent());
				g.setFont(oldfont);
			}

			
			
			if(models.size() == 0) 
			{
				onscreen.drawImage(offscreen, 0, 0, this);
				onscreen.drawString("No data yet....", xinset*2, height/2);
				return null;
			}
			
			
			ColourGradient grad = null;
			double gradientEnd = 0.0;
			if(colourParam.equalsIgnoreCase("misfit"))
			{
				gradientEnd = Math.log(besterr+1)+slidepercent*(Math.log(worsterr+1)-Math.log(besterr+1));
				grad = new ColourGradient(Color.red, Math.log(besterr+1), Color.blue, gradientEnd);
			}
			else if(colourParam.equalsIgnoreCase("time"))
			{
				gradientEnd = VisualisationPanel.this.inverter.getRunningTime()/1000;
				grad = new ColourGradient(Color.blue, 0, Color.red, gradientEnd);
			}
			//Debug.println("gradient end is "+gradientEnd);

			
			float xwidth = (float) (xupper-xlower);
			float ywidth = (float) (yupper-ylower);
			
			
			
			List modelsToPlot = null;
			//work out what we are plotting, by looking at x/y axes			
			double[] distances = null;
			double maxdist = Double.NEGATIVE_INFINITY;
			if(this.p1 != null && this.p2 != null && this.distanceFade)
			{
				//have to cache the number of models, because it can change while we
				//do this (we are asynchronously accessing the models List, and it can change)
				int nummods = baseModels.size();
				
				distances = new double[nummods];

				//We also fade them 
				//based on their euclidean distance from the best model in the
				//non-plotted parameters
				modelsToPlot = new java.util.ArrayList();
				rses.Model curbest = bestmod;
				for(int i =0; i < nummods; i++)
				{
					rses.Model m = (rses.Model) baseModels.get(i);
					//m can be null because we are accessing the models list 
					//while it is (potentially) being modified by another thread
					if(m != null)
					{
						if(besterr+(worsterr-besterr)*fadepercent < m.getMisfit())
							continue;
						
						int index = modelsToPlot.size();
						distances[index] = Math.pow(m.distanceFrom(curbest), 2);
						distances[index] -= Math.pow(m.getModelParameter(p1.intValue())-
						                                  curbest.getModelParameter(p1.intValue()), 2);
						distances[index] -= Math.pow(m.getModelParameter(p2.intValue())-
										  curbest.getModelParameter(p2.intValue()), 2);
						distances[index] = Math.sqrt(distances[index]);
						if(distances[index] > maxdist)
							maxdist = distances[index];
						modelsToPlot.add(m);
					}
				}
			}
			else if(p1 != null) //x param is just a normal parameter
				modelsToPlot = baseModels;
			else if(xparam.equalsIgnoreCase("time"))//x param must be time
			{
				if(yparam.equalsIgnoreCase("misfit"))
					modelsToPlot = besterrs;
				else if(yparam.equalsIgnoreCase("ln(misfit+1)"))
					modelsToPlot = besterrs;
				else if(p2 != null) //otherwise, y must just be a parameter
					modelsToPlot = baseModels;
				else
					throw new IllegalStateException("Impossible parameter combination reached! "+xparam+" "+yparam);
			}
			else
				throw new IllegalStateException("Internal Error: Impossible parameter combination "+xparam+" - please notify software author ");
			
			//now do the plotting part
			Color oldcolour = g.getColor();
			int numModels = modelsToPlot.size();
			int lastxp = -1; //the last point plotted
			int lastyp = -1; 
			Color lastcol = null;
			int startIndex = 0;
			if(modelsToPlot == models)
				startIndex = modelIndex;

			//keep track of the points that we plot
			java.util.ArrayList plottedPoints = new java.util.ArrayList();

			for(int i = startIndex; i < startIndex+numModels; i++)
			{
				//get the next model
				rses.Model m = (rses.Model) modelsToPlot.get(i % numModels);
				
				//we can get a null result because the models array is modified but
				//is not protected by a semaphore/monitor. So we can see
				//inconsistent state (like a null entry, which would otherwise
				//be impossible)
				if(m == null) continue;
				
				//now lets plot it x/y
				float xp;
				if(this.p1 != null) //just a model parameter 
					xp = (float) m.getModelParameter(p1.intValue());
				else if(xparam.equalsIgnoreCase("time"))
					xp = (float) m.getDiscoveryTime()/1000;
				else
					throw new IllegalStateException("Internal error.. x param unrecognized as "+xparam);
								
				
				int xpos = xinset + (int) (width*(xp-xlower)/(xwidth));
				
				
				float yp;
				if(this.p2 != null) //just a model parameter
					yp = (float) m.getModelParameter(p2.intValue());
				else if(yparam.equalsIgnoreCase("misfit"))
					yp = (float) m.getMisfit();
				else if(yparam.equalsIgnoreCase("ln(misfit+1)"))
					yp = (float) Math.log(m.getMisfit()+1);
				else
					throw new IllegalStateException("Internal error.. y param unrecognized as "+yparam);
					
				int ypos = (int) (height - height*(yp-ylower)/(ywidth));
				
				

					
				double zp = Double.NaN;
				if(grad != null) //we are colouring the results 
				{ 
					if(colourParam.equalsIgnoreCase("misfit"))
						zp = Math.log(m.getMisfit()+1);
					else if(colourParam.equalsIgnoreCase("time"))
						zp = m.getDiscoveryTime()/1000;
					else
						throw new IllegalStateException("Impossible case reached -- didnt expect "+colourParam+" as a colour parameter");
					if(Double.isNaN(zp))
						g.setColor(oldcolour);
					else if(zp > gradientEnd)
						g.setColor(grad.getColour(gradientEnd));
					else	
						g.setColor(grad.getColour(zp));
				}
				
				
				if(this.p1 != null && this.p2 != null && this.distanceFade)
				{
					Color cur = g.getColor();
					float[] hsbvals = new float[3];
					Color.RGBtoHSB(cur.getRed(), cur.getGreen(), cur.getBlue(), hsbvals);
					hsbvals[1] = (float) (1.0 - distances[i]/maxdist);
					g.setColor(Color.getHSBColor(hsbvals[0], hsbvals[1], hsbvals[2]));
				}
				
				
				//plot the point
				g.fillRect(xpos, ypos,3,3);
				Color newcol = g.getColor();
				
				//if we are keeping track of the points we plot, do that
				if(returnPoints) {
					if(grad != null && colourParam.equalsIgnoreCase("misfit")) 
						plottedPoints.add(new float[] {xp, yp, (float) (Math.exp(zp)-1)});
					else if(grad != null)
						plottedPoints.add(new float[] {xp, yp, (float) zp});
					else 
						plottedPoints.add(new float[] {xp, yp});
				}
				
				//if they are plotting misfit over time then join the dots for them
				if(yparam.indexOf("misfit") >= 0 && xparam.equalsIgnoreCase("time") && lastxp >= 0)
				{
					g.setColor(lastcol);
					g.drawLine(lastxp, lastyp, xpos, ypos);
				}
				
				lastcol = newcol;
				lastxp = xpos;
				lastyp = ypos;
			}
			g.setColor(oldcolour);
						
			numDrawn += baseModels.size();			
			onscreen.drawImage(offscreen, 0, 0, this);
			
			if(returnPoints) {
				float[][] res = new float[plottedPoints.size()][];
				for(int i = 0; i < plottedPoints.size(); i++)
					res[i] = (float[]) plottedPoints.get(i);
				return res;
			}
			return null;
		}
		


		public void saveAsText(String delimiter, java.io.File file) throws java.io.IOException
		{
			float[][] points = this.paintPoints(this.getGraphics(), true);
			if(points == null) //no points
			{
				javax.swing.JOptionPane.showMessageDialog(this, "Sorry, no data to save yet");
				return;
			}			
			
			java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(file));
			pw.println("# "+this.axislabel + " VS " + this.colourParam);
			pw.println("# xrange: "+this.xlower+" --> "+this.xupper+"   yrange: "+this.ylower+" --> "+this.yupper);
			if(points[0].length == 2) for(int i = 0; i < points.length; i++)
				pw.println(points[i][0]+delimiter+points[i][1]);
			else if(points[0].length == 3) for(int i = 0; i < points.length; i++)
				pw.println(points[i][0]+delimiter+points[i][1]+delimiter+points[i][2]);
			else throw new IllegalStateException("Impossible dimension found in points array...");
			pw.close();
		}


		public void saveAsImage() 
		{
			//create our image
			BufferedImage bim = new BufferedImage(this.getWidth(), this.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
			
			//create a graphics object and paint it
			java.awt.Graphics2D g2d = bim.createGraphics();
			this.paint(g2d);
			
			//now save the image
			java.io.File savefile = FileUtil.guiSaveFile(this, "Save image", 
				FileUtil.getFileFilterBySuffixes(new String[] {".png", ".PNG"}), "myimage.png");
			if(savefile == null)
				return; //user must have cancelled
				
			try { javax.imageio.ImageIO.write(bim, "png", savefile); }
			catch(java.io.IOException ioe) {
				javax.swing.JOptionPane.showMessageDialog(this, "Error saving image.");
			}
		}


		public java.awt.Dimension getPreferredSize()
		{
			return new java.awt.Dimension(250,250);
		}
		
		public java.awt.Dimension getMinimumSize()
		{
			return new java.awt.Dimension(250,250);
		}







		public void actionPerformed(java.awt.event.ActionEvent e)
		{
			if(e.getActionCommand().equals("close")) 
			{				
				//if it is in the visualisation panel, we remove it
				VisualisationPanel.this.remove(this);
			}
			else if(e.getActionCommand().equals("details"))
				JOptionPane.showMessageDialog(VisualisationPanel.this, 
				axislabel+System.getProperty("line.separator")+"colour gradient: "+colourParam);
			else if(e.getActionCommand().equals("in separate window"))
			{
				//remove the 'in separate window operation
				if(e.getSource() instanceof JMenuItem) 
					popmenu.remove((JMenuItem) e.getSource());
					
				//remove the 'close' operation
				for(int i = 0; i < popmenu.getComponentCount(); i++)
				{
					if(popmenu.getComponent(i) instanceof JMenuItem && 
					   ((JMenuItem) popmenu.getComponent(i)).getText().equals("close"))
					{
					   popmenu.remove(popmenu.getComponent(i));
					   break;
					}
				}		
				
				VisualisationPanel.this.remove(this);
				javax.swing.JFrame f = new javax.swing.JFrame();
				f.setSize(VisualisationPanel.this.getSize());
				f.getContentPane().add(this);
				this.setSize(f.getSize());
				VisualisationPanel.this.externalFrames.add(f);
				f.setVisible(true);
			}
			else if(e.getActionCommand().equals("image")) {
				this.saveAsImage();
			}
			else if(e.getActionCommand().indexOf("seperated text") >= 0) 
			{
				//ask the user where to save it
				java.io.File f = rses.util.FileUtil.guiSaveFile(this, "Save file...", null, "data.txt");
				
				try {
					if(e.getActionCommand().equals("comma seperated text"))
						this.saveAsText(" , ", f);
					else if(e.getActionCommand().equals("space seperated text"))
						this.saveAsText("   ", f);
					else throw new IllegalStateException("unknown/unhandled action command: "+e.getActionCommand());
				}
				catch(java.io.IOException ioe) {
					javax.swing.JOptionPane.showMessageDialog(this, "Save failed... IO Error");
				}
			}
			else if(e.getActionCommand().equals("distance fade 10%"))
			{
				this.numDrawn = 0; //we need to redraw even those points we've already drawn
				this.distanceFade = true;
				this.fadepercent = 0.1;
			}
			else if(e.getActionCommand().equals("distance fade 25%"))
			{
				this.numDrawn = 0; //we need to redraw even those points we've already drawn
				this.distanceFade = true;
				this.fadepercent = 0.25;
			}
			else if(e.getActionCommand().equals("distance fade 50%"))
			{
				this.numDrawn = 0; //we need to redraw even those points we've already drawn
				this.distanceFade = true;
				this.fadepercent = 0.5;
			}
			else
				throw new IllegalStateException("Impossible case.. unknown event with command "+e.getActionCommand());
			
			//redo layout because we may have removed a component	
			VisualisationPanel.this.revalidate();
			VisualisationPanel.this.repaint();
		}

		
		
		public void mouseReleased(MouseEvent e) 
		{
			maybeShowPopup(e);
		} 
		public void mousePressed(MouseEvent e)
		{
			maybeShowPopup(e);
		}


		private void maybeShowPopup(MouseEvent e) 
		{
			if (e.isPopupTrigger())
				popmenu.show(e.getComponent(),e.getX(), e.getY());
		}

		//not interested in these events
		public void mouseClicked(MouseEvent e) 
		{}
		public void mouseEntered(MouseEvent e)
		{} 
		public void mouseExited(MouseEvent e)
		{}
				
	}
	
}



