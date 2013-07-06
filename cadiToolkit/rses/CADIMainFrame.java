package rses;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import javax.swing.*;



import rses.inverse.ObservableInverter;
import rses.inverse.UserFunctionHandle;
import rses.inverse.na.NadFile;
import rses.math.DensityFunction;
import rses.math.MathUtil;
import rses.math.RealFunction;
import rses.util.FileUtil;
import rses.util.Util;

/** Main frame for toolkit with gui enabled
 * 
 * @author peterr
 *
 */



public class CADIMainFrame extends JFrame
{
	private java.io.File userdir;	
	private toolkitMain tkmain;

	
	private rses.inverse.InversionMain invmain = null;
	

	CADIMainFrame(toolkitMain tkmain) throws Exception
	{
		this.tkmain = tkmain;
		setupInitialMenuBar();
		this.setTitle("CADI Toolkit");
		this.userdir = new java.io.File(System.getProperty("user.dir"));
		this.addWindowListener(new mainframeWindowListener());
		loadExtensions();
	}



	private JMenuBar menubar = new JMenuBar();

	/* The menu bar for the rsesToolkit main frame
	 */
	private void setupInitialMenuBar()
	{
		RsesMenuListener menuHandler = new RsesMenuListener(this);
		
		JMenu mainmenu = new JMenu("Exit");
		JMenuItem	mi = mainmenu.add("Graceful exit (continue running inversion)");
		mi.addActionListener(menuHandler);
		mi = mainmenu.add("Exit (stop any running inversions)");
		mi.addActionListener(menuHandler);
		menubar.add(mainmenu);
 		
		JMenu menu = new JMenu("Inversion");
		mi = menu.add("Start new inversion"); 
		mi.addActionListener(menuHandler);
		mi = menu.add("View inversion results");
		mi.addActionListener(menuHandler);
		menubar.add(menu);
		
		JMenu exploreMenu = new JMenu("Analysis");
		mi = exploreMenu.add("sensitivity analysis");
		mi.addActionListener(menuHandler);
		//mi = exploreMenu.add("derivative analysis");
		//mi.addActionListener(menuHandler);
		menubar.add(exploreMenu);
		
		JMenu plotMenu = new JMenu("Plotting");
		mi = plotMenu.add("Density plot");
		mi.addActionListener(menuHandler);
		menubar.add(plotMenu);
		
		JMenu miscMenu = new JMenu("Miscellaneous");
		mi = miscMenu.add("About this software");
		mi.addActionListener(menuHandler);
		JMenu verbositySubMenu = new JMenu("Verbosity...");
		mi = verbositySubMenu.add("less verbose");
		mi.addActionListener(rses.util.VerbosityListener.getVerbosityListener());
		mi = verbositySubMenu.add("more verbose");
		mi.addActionListener(rses.util.VerbosityListener.getVerbosityListener());
		miscMenu.add(verbositySubMenu);
		menubar.add(miscMenu);
		

		
		
		this.setJMenuBar(menubar);
	}

	
	

	class RsesMenuListener implements java.awt.event.ActionListener
	{
		JFrame tobenotified = null;
	
		RsesMenuListener(JFrame menuowner)
		{
			tobenotified = menuowner;
		}
	
	
		
		private UserFunctionHandle getUserFunctionHandle() throws Exception
		{
			boolean copyFiles = true;
			if(tkmain.assumeSharedFS() || tkmain.getNodes().length == 0)
				copyFiles = false;
			UserFunctionHandle result = null;
			String nl = PlatformInfo.nl;
			try { result = FileUtil.guiGetUserFunctionHandle(null, CADIMainFrame.this, userdir, tkmain.gettkpath(), copyFiles); }
			catch(IOException ioe)
			{
				throw new Exception(nl+"IO error while reading user file: "+nl+ioe.getMessage());
			}
			catch(ClassNotFoundException cnfe)
			{
				throw new Exception(nl+"Could not find expected class definition"+nl+
						  "Class is incorrectly named, or is in wrong package:"+nl+cnfe.getMessage());
			}
			catch(IllegalAccessException ilae)
			{
				throw new Exception(nl+"Methods in user class have too restrictive access modifiers"+nl+
						"They must be public"+nl+ilae.getMessage());
			}
			catch(InstantiationException ie)
			{
				throw new Exception(nl+"User defined class is abstract or an interface. Cannot instantiate: "+ie.getMessage());
			}
			catch(InvocationTargetException ite)
			{
				throw new Exception(nl+"User constructor threw an exception during object creation.", ite);
			}
			catch(NoSuchMethodException nsme)
			{
				throw new Exception(nl+"User class does not implement the required functions"+nl+
						"please read the CADI toolkit documentation and implement to required functions");
			}
			
			return result;
		}
		
		
		
		public void actionPerformed(java.awt.event.ActionEvent e)
		{
			if(e.getActionCommand().equals("Start new inversion"))
			{
				UserFunctionHandle handle = null;
				try {
					handle = getUserFunctionHandle();
				}
				catch(Exception excpt)
				{
					JOptionPane.showMessageDialog(CADIMainFrame.this, "Error loading user module/library/class"+PlatformInfo.nl+
						excpt.getMessage());
					Debug.println(excpt, Debug.INFO);
					return;
				}
				if(handle == null) return;

				if(invmain != null)
					tobenotified.getContentPane().remove(invmain);
				
				invmain = new rses.inverse.InversionMain(handle,tkmain.getNodes());
				tobenotified.getContentPane().add(invmain);
				tobenotified.validate();
				tobenotified.repaint();

			}
			else if(e.getActionCommand().equals("View inversion results"))
			{
				java.io.File f = FileUtil.guiSelectModelFile(tobenotified);
				if(f == null) //cancelled
					return; 				
				rses.inverse.ObservableInverter inverter = getLogFileInverter(f);
				int nd = inverter.getUserFunctionHandle().getDimensionOfModelSpace();
				rses.visualisation.VisualisationPanel vispanel = new rses.visualisation.VisualisationPanel(inverter, nd);
				inverter.addModelObserver(vispanel);
				
				Debug.println("launching visualisation window", Debug.INFO);
				 				
				javax.swing.JFrame vf = new javax.swing.JFrame("Log file viewing (results)");
				vf.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
				vf.setSize(600,600);
				JScrollPane scrollpane = new JScrollPane(vispanel, 
					JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
					JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
				vf.getContentPane().add(scrollpane);
				vf.show();
				
				new Thread(inverter).start(); //start reading results from file
			}
			else if(e.getActionCommand().equals("sensitivity analysis"))
			{
				UserFunctionHandle handle = null;
				try { handle = getUserFunctionHandle(); }
				catch(Exception excpt)
				{	
					JOptionPane.showMessageDialog(CADIMainFrame.this, "Error loading user module/library/class"+PlatformInfo.nl+
						excpt.getMessage());
					return;
				}
				if(handle == null) return;
			
				//user hasn't cancelled, so we can remove the lot of stuff and remove the menu
				//(cant do this before cancel, because then cancel doesnt get the user back to
				//where they were before)	
				tobenotified.getContentPane().removeAll();
				tobenotified.setJMenuBar(null); //remove(menubar) doesnt seem to work.... but this does
				
				
				promptForParamNames(handle);
				
				rses.visualisation.ModelExplorer me = new rses.visualisation.ModelExplorer(handle, tkmain.getNodes());
				JScrollPane jsp = new JScrollPane(me, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
					JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
				me.setSize(CADIMainFrame.this.getSize());
				tobenotified.getContentPane().add(jsp);
				tobenotified.validate();
				tobenotified.repaint();
			}
			else if(e.getActionCommand().equals("derivative analysis"))
			{
				UserFunctionHandle handle = null;
				try { handle = getUserFunctionHandle(); }
				catch(Exception excpt)
				{	
					JOptionPane.showMessageDialog(CADIMainFrame.this, "Error loading user module/library/class"+PlatformInfo.nl+
						excpt.getMessage());
					return;
				}
				if(handle == null) return;
				
				RealFunction f = RealFunction.generateRealFunction(handle);
				
				//get the starting model
				double[][] bounds = handle.getBoundsOnModelSpace();
				rses.Model m = null;
				try {m = rses.Model.guiGetNewModel(CADIMainFrame.this, bounds); }
				catch(Exception ex) {
					JOptionPane.showMessageDialog(CADIMainFrame.this, "Could not generate model -- "+ex.getMessage());
					return;
				}
				
				double[] steps = new double[m.getNumParameters()];
				for(int i =0; i < steps.length; i++) 
					steps[i] = (bounds[i][1]-bounds[i][0])*0.001; 
				double[] fd = MathUtil.calculateFiniteDifference(f, m.getModelParameters(), steps);
				Debug.println(Util.arrayToString(m.getModelParameters()), Debug.IMPORTANT);
				Debug.println(Util.arrayToString(fd), Debug.IMPORTANT);
			}
			else if(e.getActionCommand().equals("Density plot"))
			{
				java.io.File densityfile = FileUtil.guiSelectFile(CADIMainFrame.this, "Choose density file");
				DensityFunction density = null;
				if(densityfile == null) return;
				try { 
					//read the density function and plot it
					density = DensityFunction.readFromFile(densityfile);
					density.getDisplayFrame(-1).setVisible(true);
				}
				catch(java.io.IOException ioe) {
					JOptionPane.showMessageDialog(CADIMainFrame.this, "Could not read density file: "+ioe.getMessage());
					Debug.println(ioe, Debug.INFO);
				}
			}
			else if(e.getActionCommand().equals("Graceful exit (continue running inversion)"))
			{
				//ok, if we get to here then something is still running and 
				//we need to warn the user that they have to clean up their
				//own jobs
				java.io.File[] tmpfiles = rses.util.FileUtil.getTmpFiles();
				if(tmpfiles.length > 0)
				{
					String msg = "You cannot exit gracefully, as the following temporary files still exist:"+PlatformInfo.nl;
					for(int i =0; i < tmpfiles.length; i++)
						msg += tmpfiles[i].getAbsolutePath()+PlatformInfo.nl;
					msg += PlatformInfo.nl+"If you had exited normally, I clean all these files for you. But now"+PlatformInfo.nl;
					msg+= "that you want to keep your inversion running, you must delete these files yourself manually"+PlatformInfo.nl;
					msg += "when your inversion has finished.";
					JOptionPane.showMessageDialog(CADIMainFrame.this, msg);
					//return; still go on and exit, even if there are temp files
				}
				CADIMainFrame.this.setVisible(false);
				CADIMainFrame.this.dispose();
				waitForExit();
			}
			else if(e.getActionCommand().equals("Exit (stop any running inversions)"))
			{
				//just exit. The toolkitMain class is responsible for
				//doing a clean shutdown
				System.exit(0);
			}
			else if(e.getActionCommand().equals("About this software"))
			{
				String msg = "This software is authored principally by Peter Rickwood"+PlatformInfo.nl;
				msg +=       "(email peter.rickwood@anu.edu.au), at the Centre"+PlatformInfo.nl;
				msg +=       "for Advanced Data Inference (CADI), at the Australian"+PlatformInfo.nl;
				msg +=       "National University"+PlatformInfo.nl+PlatformInfo.nl;
				msg +=       "Contributions by Malcolm Sambridge (Neighbourhood algorithm)"+PlatformInfo.nl+PlatformInfo.nl;
				msg +=       "Comments/feedback/questions welcome -- send to either"+PlatformInfo.nl;
				msg +=       "the author, or to cadi@rses.anu.edu.au";
				JOptionPane.showMessageDialog(CADIMainFrame.this, msg);
			}
			else 
			{
				String msg = "Illegal option : command "+e.getActionCommand()+" not recognised"+PlatformInfo.nl;
				msg += PlatformInfo.nl+"An internal error has occurred in the toolkit. Please send us an email"+PlatformInfo.nl;
				msg +=      "giving details of this error (email cadi@rses.anu.edu.au) "+PlatformInfo.nl+PlatformInfo.nl+"Thankyou."+PlatformInfo.nl;
				throw new IllegalStateException(msg);
			}
			
		}
	}

	class mainframeWindowListener extends WindowAdapter
	{
		public void windowClosing(java.awt.event.WindowEvent event)
		{
			//hide the window.
			CADIMainFrame.this.setVisible(false);
			
			//Just exit. The toolkitMain class is responsible
			//for making sure we do a clean shutdown, deleting tmp files.
			//etc etc
			System.exit(0);
		}
	}

	

	private void loadExtensions()
	{
		ActionListener actlist = new ActionListener() {
			public void actionPerformed(ActionEvent event)
			{
				tkmain.runExtension(event.getActionCommand());
			}
		};
		
		Set extensions = tkmain.getExtensions();
		java.util.Iterator it = extensions.iterator();
		JMenu extmenu = new JMenu("Extensions");
		int next = 0;
		while(it.hasNext())
		{
			String extname = (String) it.next();
			JMenuItem mi = extmenu.add(extname);
			mi.addActionListener(actlist);
			next++;
		}
		if(next > 0)
			this.menubar.add(extmenu);
	}
	
	
	
	private void promptForParamNames(UserFunctionHandle handle)
	{			
		int ret = JOptionPane.showConfirmDialog(CADIMainFrame.this, "Do you want to name your parameter axes?", "Name parameter axes?", JOptionPane.YES_NO_OPTION);
		if(ret == JOptionPane.YES_OPTION)
		{
			java.io.File paramFile = rses.util.FileUtil.guiSelectFile(CADIMainFrame.this, "Choose file");
			if(paramFile == null) return;
			String[] pn = rses.util.FileUtil.guiReadParameterNames(CADIMainFrame.this, paramFile, handle.getDimensionOfModelSpace());
			for(int i =0; i < pn.length; i++)
				handle.setParameterName(i, pn[i]);
		}
	}


	
	
	
	


	private ObservableInverter getLogFileInverter(java.io.File f)
	{
		//open up the nad file and plot the results
		if(f.getName().endsWith(".nad"))
		{
			NadFile nadfile = null;
			try {
				nadfile = new NadFile(f);
			}
			catch(java.io.IOException ioe)
			{
				JOptionPane.showMessageDialog(this, "IO Error reading NAD file.... "+PlatformInfo.nl+ioe.getMessage());
			}
			return nadfile;
		}
		else {
			ObservableInverter inverter = null; 
			try { inverter = new rses.inverse.util.ModelFile(f); }
			catch(java.io.IOException ioe) {
				JOptionPane.showMessageDialog(this, "Input/Output error.. couldn't read file: "+ioe.getMessage());
			}
			return inverter;
		}
		
	}




	private int getNumberOfRunningInversions()
	{
		if(invmain == null)
			return 0;
		
		return rses.inverse.InversionMain.getNumberOfRunningInversions();
	}



	private void waitForExit()
	{
		while(this.getNumberOfRunningInversions() > 0)
		{
			try {
				Debug.println("Waiting for running inversion to exit", Debug.IMPORTANT);
				Thread.sleep(10000);
			} 
			catch(Exception ignore) {} 
		}
		
		System.exit(0);
	}

}



