package rses.inverse;

import java.awt.Choice;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;

import javax.swing.JTextField;

import rses.inverse.anneal.MetropolisHastingsAnnealer;
import rses.inverse.anneal.TemperatureSchedule;
import rses.inverse.anneal.TemperatureScheduleImp;
import rses.inverse.deity.DeityOptimizer;
import rses.inverse.genetic.GeneticSearch;
import rses.inverse.genetic.Organism;
import rses.inverse.mcmc.MarkovChainMonteCarloInverter;
import rses.inverse.montecarlo.MonteCarlo;
import rses.inverse.powell.PowellMin;
import rses.visualisation.ModelExplorer;
import rses.visualisation.VisualisationPanel;
import rses.util.distributed.NodeInfo;
import rses.Debug;
import rses.PlatformInfo;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;

public class InversionMain extends JPanel implements ItemListener, ActionListener 
{

	private static final java.util.HashMap naoptions;
	private static final String[] naoptionKeys;
	
	//all the inversion threads running in this JVM 
	//(or at least all those kicked off by instances of inversion main)
	private static ArrayList runningInversions = new ArrayList(); 
	
	public static ArrayList getInversions() {
		return (ArrayList) runningInversions.clone();
	}
	
	public static int getNumberOfRunningInversions() {
		java.util.ArrayList inversions = getInversions();
		int numinv = inversions.size();
		int count = 0;
		for(int i =0; i < numinv; i++) 
		{
			ObservableInverter inv = (ObservableInverter) inversions.get(i);
			
			//special case for NadFile and ModelFile, as these two inverters never finish,
			//and in any case, it is fine to interrupt them even if they havent finished.
			//So we just consider that they have finished running even if they havent
			if(inv instanceof rses.inverse.na.NadFile || inv instanceof rses.inverse.util.ModelFile)
				continue; 
			else if(!inv.isFinished())
				count++;
		}
		return count;
	}

	//setup options tables.
	//The layout of the table is as follows
	//key (String) == the name of the option
	//value (String[]) == null if there is no restriction on possible values
	//if possible values are limited, then
	//value[0] = first allowable option
	//value[1] = how this should be written in the na.in file
	//value[2] = 2nd allowable option
	//value[3] = how this should be written in the na.in file
	//etc
	static {
		naoptionKeys =
			new String[] {
			"Algorithm type", "Maximum number of iterations",
			"Sample size for first iteration", "Sample size for other iterations",
			"Number of cells to resample", "Use quasi-random number generator?",
			"Random seed", "Type of initial sample", "Output information level",
			"Timing mode on?", "Debug mode on?" };

		naoptions = new java.util.HashMap();
		naoptions.put(naoptionKeys[0], new String[] { "NA", "0", "UMC", "1" });
		naoptions.put(naoptionKeys[1], null);
		naoptions.put(naoptionKeys[2], null);
		naoptions.put(naoptionKeys[3], null);
		naoptions.put(naoptionKeys[4], null);
		naoptions.put(naoptionKeys[5], new String[] { "No", "n", "Yes", "y" });
		naoptions.put(naoptionKeys[6], null);
		naoptions.put(naoptionKeys[7], new String[] { "Random", "0", "From nad file", "1" });
		naoptions.put(naoptionKeys[8], new String[] {"silent","0","summary","1","verbose","2" });
		naoptions.put(naoptionKeys[9],new String[] { "Yes", "y", "No", "n" });
		naoptions.put(naoptionKeys[10],new String[] { "No", "n", "Yes", "y" });
	}


	private Choice inversionType = null;	
	private JCheckBox launchExplorerOnFinishCheckBox = new JCheckBox();
	private UserFunctionHandle handle = null;

	//all the possible algorithm options.. held in this HashMap.
	private java.util.HashMap options = new java.util.HashMap();
	private JPanel optionsPanel = new JPanel();

	private NodeInfo[] nodelist;

	//The directory in which the user's code lives
	//initialized at start
	private File userDir = new File(System.getProperty("user.dir"));


	private static final String nl = System.getProperty("line.separator");



	//was this panel initialized OK.
	//if not, we dont display it.
	//basically, this variable is set if the initializer finished ok
	private boolean isvalid = false;


	public InversionMain(UserFunctionHandle handle, NodeInfo[] nodelist) 
	{
		if(nodelist == null)
			throw new IllegalArgumentException("nodelist argument to InversionMain cannot be null");
		this.nodelist = nodelist;
		this.handle = handle;
		
		//initialize inversion choices
		inversionType = new Choice();
		inversionType.add("Neighbourhood Algorithm (continuous)");
		inversionType.add("Genetic Algorithm");
		inversionType.add("Uniform Monte Carlo");
		inversionType.add("Simulated Annealing");
		inversionType.add("Markov Chain Monte Carlo");
		inversionType.add("Recursive Hypercubing Optimizer");
		inversionType.add("Powell's method (local search)");
		inversionType.add("Adaptive Powell's method");
		inversionType.addItemListener(this);

		this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		JPanel icp = getInversionChoicePanel();
		this.add(icp);

		this.add(optionsPanel);

		this.isvalid = true;				
	}

















	public void paintComponent(java.awt.Graphics g) 
	{
		//if we arent valid, we remove ourselves
		if(!this.isvalid) {
			Debug.println("Invalid (cancelled) inversion panel.... removing panel from parent", Debug.EXTRA_INFO);
			java.awt.Container parent = this.getParent();
			if(parent != null) {
				parent.remove(this);
				parent.validate();
				parent.repaint();
				return;
			}
		}
		super.paintComponent(g);
	}











	private rses.inverse.util.LogfileModelObserver getLogfileObserver(String name, UserFunctionHandle h)
	{
		rses.inverse.util.LogfileModelObserver mobs = null;
		if(name == null || name.trim().length() == 0)
			return null;
		 
		try {	
			mobs = new rses.inverse.util.LogfileModelObserver(new java.io.File(name), h); 
		} 
		catch(java.io.IOException ioe) {
			JOptionPane.showMessageDialog(this, "couldn't find/open/write-to logfile...... aborting");
			return null;
		}
		return mobs;
	}








	public void actionPerformed(ActionEvent e) 
	{
		if(!isvalid) return;
		
		try  { unsafeActionPerformed(e); }
		catch(Exception excpt)
		{
			JOptionPane.showMessageDialog(this, "Error starting inversion -- "+excpt.getMessage());
			Debug.println(excpt.getMessage(), Debug.CRITICAL);
			Debug.println(excpt, Debug.CRITICAL);
			return;					
		}				
	}
	
	
	private void unsafeActionPerformed(ActionEvent e)
	throws Exception
	{		
		if (e.getActionCommand().equals("Do inversion!")) 
		{
			//NOTE: currently, we only allow a single running inversion.... although in
			//principle you could have multiple inversions running, this has not been
			//tested and I'm not sure that it'll work, so for now we restrict the
			//user to just 1 
			if(getNumberOfRunningInversions() > 0) {
				JOptionPane.showMessageDialog(this, "You are already running an inversion!"+PlatformInfo.nl
					+"You will need to start a new instance of the toolkit to run a new inversion.");
				return;
			}
			
			ObservableInverter inverter = null;

			String logfilename = (String) options.get("log file (leave blank for no logging)");
			rses.inverse.util.LogfileModelObserver logobserver = getLogfileObserver(logfilename, handle);
			Debug.println("finished trying to create log file (if needed)", Debug.INFO);	
				
			if(inversionType.getSelectedItem().equals("Neighbourhood Algorithm (continuous)"))
			{
				int nsamplei = Integer.parseInt((String) options.get("Sample size in first iteration"));
				int ns = Integer.parseInt((String) options.get("Sample size in other iterations"));
				int nr = Integer.parseInt((String) options.get("Number of cells to resample"));
				int num_models = nsamplei + Integer.parseInt((String) options.get("number of iterations"))*ns;
					
				if(nodelist.length == 0)
					inverter = new rses.inverse.na.NAJavaImpl(handle, nsamplei, ns, nr, num_models);
				else
					inverter = rses.inverse.na.NAJavaImpl.getDistributedInstance(nodelist, handle, nsamplei, ns, nr, num_models);
			}
			else if (inversionType.getSelectedItem().equals("Genetic Algorithm")) 
			{
				Debug.println("GA selected", Debug.INFO);
				int popsize  = Integer.parseInt((String) options.get("population size"));
				Debug.println("popsize is "+popsize, Debug.EXTRA_INFO);
				double runtime = Double.parseDouble((String) options.get("running time (hours)"));
				long runtimel = Math.round(runtime*60*60*1000);

				if(nodelist.length == 0) //just do a standalone (local machine) inversion
					inverter = new GeneticSearch(popsize, handle);					
				else //do a distributed parallel one
					inverter = GeneticSearch.getDistributedGeneticSearch(nodelist, popsize, handle, Organism.getFactory(handle));
				
				((GeneticSearch) inverter).setTimeToRun(runtimel);
			} 
			else if (inversionType.getSelectedItem().equals("Uniform Monte Carlo")) 
			{
				double runtime = Double.parseDouble((String) options.get("running time (hours)"));
				long runtimel = Math.round(runtime*60*60*1000);
				if(nodelist.length == 0) //standalone (1 machine) version
					inverter = new MonteCarlo(handle);
				else
					inverter = MonteCarlo.getDistributedMonteCarlo(nodelist, handle);
				((MonteCarlo) inverter).setTimeToRun(runtimel);
			}
			else if(inversionType.getSelectedItem().equals("Simulated Annealing"))
			{
				Debug.println("SA seletced for inversion", Debug.INFO);
				double init_temp = Double.parseDouble((String) options.get("initial temperature"));
				double end_temp = Double.parseDouble((String) options.get("end temperature"));
				double temp_mul =  Double.parseDouble((String) options.get("temperature multiplier"));
				int iterations = Integer.parseInt((String) options.get("iterations per temperature"));
				Debug.println("init temp is "+init_temp, Debug.EXTRA_INFO);
				Debug.println("end temp is "+end_temp, Debug.EXTRA_INFO);
				Debug.println("temp_mul is "+temp_mul, Debug.EXTRA_INFO);
				Debug.println("iterations is "+iterations, Debug.EXTRA_INFO);
				Debug.println("Getting temperature schedule, please wait", Debug.IMPORTANT);
				TemperatureSchedule ts = new TemperatureScheduleImp(init_temp, end_temp, 
					temp_mul, iterations); 
				Debug.println("Got temperature schedule", Debug.IMPORTANT);
				if(nodelist.length == 0)
					inverter = new MetropolisHastingsAnnealer(handle);
				else
					inverter = MetropolisHastingsAnnealer.getDistributedMetropolisHastings(nodelist, handle);
				((rses.inverse.anneal.SimulatedAnnealer) inverter).setTemperatureSchedule(ts);
				Debug.println("temperature schedule set for annealer", Debug.EXTRA_INFO);
				JOptionPane.showMessageDialog(this, "You will now be asked to choose how the initial model"+nl+" in the chain is generated.");
				rses.Model startMod = rses.Model.guiGetNewModel(this, handle.getBoundsOnModelSpace());
				if(startMod != null)
				{
					if(!startMod.isMisfitAvailable())
						startMod.setMisfit(handle.getErrorForModel(startMod.getModelParameters()));
					((rses.inverse.anneal.MetropolisHastingsAnnealer) inverter).setInitialModel(startMod);
				}
				else
					Debug.println("Starting with random initial model", Debug.IMPORTANT);
			}
			else if (inversionType.getSelectedItem().equals("Markov Chain Monte Carlo"))
			{
				inverter = getMCMCinversion();
			}
			else if (inversionType.getSelectedItem().equals("Recursive Hypercubing Optimizer"))
			{
				double runtime = Double.parseDouble((String) options.get("running time (hours)"));
				double precision = Double.parseDouble((String) options.get("Minimum change in misfit deemed significant"));
				//int nsamp = Integer.parseInt((String) options.get("Samples per hypercube"));
				long runtimel = Math.round(runtime*60*60*1000);
				inverter = new DeityOptimizer(this.handle, precision);
				((DeityOptimizer) inverter).setTimeToRun(runtimel);
			}
			else if(inversionType.getSelectedItem().equals("Powell's method (local search)"))
			{
				double[] scalevect = new double[handle.getDimensionOfModelSpace()];
				for(int i =0; i < scalevect.length; i++)
					scalevect[i] = Double.parseDouble((String) options.get(handle.getParameterName(i)));
				double minsig = Double.parseDouble((String) options.get("Minimum change in misfit deemed significant"));
				rses.Model startMod = null;
				startMod = rses.Model.guiGetNewModel(this, handle.getBoundsOnModelSpace());
				if(startMod == null) {
					Debug.println("User aborted selection of initial model.", Debug.IMPORTANT);
					return;
				}
				else {
					if(!startMod.isMisfitAvailable())
						startMod.setMisfit(handle.getErrorForModel(startMod.getModelParameters()));
					inverter = new PowellMin(handle, scalevect, minsig, startMod, 0L);
				}
			}
			else if(inversionType.getSelectedItem().equals("Adaptive Powell's method"))
			{
				double runtime = Double.parseDouble((String) options.get("running time (hours)"));
				double minsig = Double.parseDouble((String) options.get("Minimum change in misfit deemed significant"));
				inverter = PowellMin.getAdaptivePowell(handle, minsig, (long) (runtime*1000*60*60));
			}
			else throw new IllegalStateException("Invalid selected algorithm -- "
			    +inversionType.getSelectedItem());
			    
			
			
			if(inverter != null) 
			{	
				if(logobserver != null) inverter.addModelObserver(logobserver);
				
				try //we built an inverter, so lets start it
				{
					startInversion(inverter);
					Debug.println("OK, started inversion, back in event handler thread", Debug.INFO);
				}
				catch(Exception e2)
				{
					JOptionPane.showMessageDialog(this, "Problems launching inversion:"+nl+e2.getMessage());
				}
				
				//now lets start a thread to listen for when the inversion finished. and pop
				//up a ModelExplorer window, if that is what the user has asked
				(new WaitForFinishThread(inverter)).start();
			}
			else
				throw new IllegalStateException("Problems launching inversion... no inverter built.. this should be impossible");			
				
		} 
		else
			throw new IllegalStateException("Unhandled action -- " + e.getActionCommand());
	}




	
	
	
	
	
	
	

	private MarkovChainMonteCarloInverter getMCMCinversion()
	{
		MarkovChainMonteCarloInverter inverter = null;
		double runtime = Double.parseDouble((String) options.get("running time (hours)"));
		long runtimel = Math.round(runtime*60*60*1000);

		int numchains = Integer.parseInt((String) options.get("number of chains"));
		String densitylog = (String) options.get("density log file");

		int numDensityBins = Integer.parseInt((String) options.get("# density bins"));

		double stddev = MarkovChainMonteCarloInverter.DEFAULT_INITIAL_STDDEV;
		String stddevstr = (String) options.get("std dev (normalized)");
		stddev = Double.parseDouble(stddevstr);
		
		String localoptstring = (String) options.get("local optimization time");
		double localopt = Double.parseDouble(localoptstring);
 

		int nevdsamp = 0;
		if(options.get("estimate evidence?").toString().equals("yes"))
			nevdsamp = Integer.parseInt((String) options.get("evidence estimation effort"));

		if(nodelist.length == 0) //standalone (1 machine) version
			inverter = new MarkovChainMonteCarloInverter(handle, stddev, numDensityBins, densitylog, numchains, nevdsamp, localopt);
		else
		{
			inverter = MarkovChainMonteCarloInverter.getDistributedInstance(nodelist,
				    handle, stddev, numDensityBins, densitylog, numchains, nevdsamp, localopt);
		}


		
		inverter.setTimeToRun(runtimel);
	
		//launch a density plotter
		JFrame jframe = inverter.getDensityFunction().getDisplayFrame(3000);
		jframe.setVisible(true);

		return inverter;
	}




	//start the inversion.... and a visualisation panel
	private void startInversion(ObservableInverter inverter)
	{
		Debug.println("built inverter...  "+inverter, Debug.INFO);
				
		//start the thread and keep track of what inversions are running
		Debug.println("trying to start built inverter", Debug.INFO);
		Thread newthread = new Thread(inverter);
		newthread.setDaemon(false); //keep running if main application finishes
		Debug.println("created inversion thread OK, havent started it yet though", Debug.INFO);
		rses.visualisation.VisualisationPanel vispanel = new rses.visualisation.VisualisationPanel(inverter, handle.getDimensionOfModelSpace());
		Debug.println("created VisualisationPanel", Debug.INFO);
		inverter.addModelObserver(vispanel);
		Debug.println("registered VisualisationPanel as listener for inverter... starting thread", Debug.INFO);
		runningInversions.add(inverter);
		newthread.start();
				
		Debug.println("launching visualisation window", Debug.INFO); 
				
		class VisPanelCleanup extends java.awt.event.WindowAdapter
		{
			ObservableInverter inv = null; VisualisationPanel vp = null;
			VisPanelCleanup(ObservableInverter inv, VisualisationPanel vp) { this.inv = inv; this.vp=vp;}
			public void windowClosed(java.awt.event.WindowEvent we) { 
				inv.removeModelObserver(vp); 
			}
		}
				
		javax.swing.JFrame f = new javax.swing.JFrame("Inversion Visualisation");
		f.addWindowListener(new VisPanelCleanup(inverter, vispanel));
		f.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
		f.setSize(750,600);
		JScrollPane scrollpane = new JScrollPane(vispanel, 
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
			JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		f.getContentPane().add(scrollpane);
		f.show();
	}




	public void itemStateChanged(java.awt.event.ItemEvent e) 
	{
		if(!isvalid) return;
		if (e.getSource() == inversionType) 
		{
			this.setVisible(false);
			this.invalidate();
			this.removeAll();
			optionsPanel.removeAll();

			if(inversionType.getSelectedItem().equals("Neighbourhood Algorithm (continuous)")) {
				setupNACoptionsPanel();
			}
			else if (inversionType.getSelectedItem().equals("Genetic Algorithm")) {
				Debug.println("GA selected", Debug.INFO);
				setupGAoptionsPanel();
			} 
			else if (inversionType.getSelectedItem().equals("Uniform Monte Carlo")) {
				Debug.println("UMC selected", Debug.INFO);
				setupUMCoptionsPanel();
			}
			else if (inversionType.getSelectedItem().equals("Simulated Annealing")) {
				Debug.println("SA selected", Debug.INFO);
				setupSAoptionsPanel();
			}
			else if(inversionType.getSelectedItem().equals("Markov Chain Monte Carlo")) {
				Debug.println("MCMC selected", Debug.INFO);
				setupMCMCoptionsPanel();				
			}
			else if(inversionType.getSelectedItem().equals("Powell's method (local search)")) {
				Debug.println("Powell's method selected", Debug.INFO);
				setupPowellOptionsPanel();
			}
			else if(inversionType.getSelectedItem().equals("Adaptive Powell's method")) {
				Debug.println("Adaptive Powell's method selected", Debug.INFO);
				setupAdaptivePowellOptionsPanel();
			}
			else if(inversionType.getSelectedItem().equals("Recursive Hypercubing Optimizer")) {
				Debug.println("Recursive Hypercuber selected", Debug.INFO);
				setupHyperCube();
			}
			else
				throw new IllegalStateException("Impossible selection... please contact software vendors abut this bug");

			JButton doit = new JButton("Do inversion!");
			optionsPanel.add(doit);
			doit.addActionListener(this);
			
			optionsPanel.add(new JLabel("     Launch parameter space explorer on finish?"));
			optionsPanel.add(launchExplorerOnFinishCheckBox);
			this.launchExplorerOnFinishCheckBox.setSelected(false);	
			
			JPanel icp = getInversionChoicePanel();
			this.add(icp);
			this.add(optionsPanel);	
			this.validate();
			this.isvalid = true;
			this.setVisible(true);
		} 
		else 
		{
			throw new IllegalStateException("Impossible case reached, please contact software vendor and notify of this error");
		}
	}




















	private JPanel inversionChoicePanel = null;
	private JPanel getInversionChoicePanel() 
	{
		if (inversionChoicePanel == null) 
		{
			inversionChoicePanel = new JPanel();
			inversionChoicePanel.add(new JLabel("Select inversion method"));
			inversionChoicePanel.add(inversionType);			
			inversionChoicePanel.setPreferredSize(new Dimension(400, 150));
			inversionChoicePanel.setMaximumSize(new Dimension(400, 200));
		}
		return inversionChoicePanel;
	}
















	private void setupNACoptionsPanel() 
	{		
		String key = null;
		String val = null;

		key = "log file (leave blank for no logging)";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));

		
		key = "Sample size in first iteration";
		if(nodelist.length <= 1)
			val = "100";
		else
			val = ""+nodelist.length;
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));

		key = "Sample size in other iterations";
		if(nodelist.length <= 1)
			val = "100";
		else
			val = ""+nodelist.length;
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));

		key = "Number of cells to resample";
		if(nodelist.length <= 1)
			val = "10";
		else
			val = ""+(1+nodelist.length/10);
		
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));
		
		key = "number of iterations";
		val = "10";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));
	}











	private void setupGAoptionsPanel() 
	{
		String key = null;
		String val = null;

		key = "population size";
		if(nodelist.length <= 1)
			val = "500";
		else
			val = ""+nodelist.length*5;
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));

		key = "log file (leave blank for no logging)";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));
		
		key = "running time (hours)";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));

	}





	private void setupSAoptionsPanel() 
	{
		String key = null;
		String val = null;

		key = "initial temperature";
		options.put(key, "");
		optionsPanel.add(new OptionPanel(key, val));

		key = "end temperature";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));

		key = "temperature multiplier";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));

		key = "iterations per temperature";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));

		key = "log file (leave blank for no logging)";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));
	}






	
	
	public void setupHyperCube() 
	{
		String key = null;
		String val = null;
		
		

		key = "Minimum change in misfit deemed significant";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));		

		
		/*key = "Samples per hypercube";
		val = ""+this.handle.getDimensionOfModelSpace();
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));*/		

		key = "running time (hours)";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));		
		
		key = "log file (leave blank for no logging)";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));
		
	}

	
	







	public void setupMCMCoptionsPanel() 
	{
		String key = null;
		String val = null;
		
		key = "log file (leave blank for no logging)";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));

		key = "running time (hours)";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));
		
		key = "std dev (normalized)";
		val = "0.1";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));

		key = "# density bins";
		val = "100";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));
		
		key = "number of chains";
		val = "1";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));		

		key = "density log file";
		val = "mcmc.density";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));
		
		String[] vals = new String[] {"yes", "no"};
		key = "estimate evidence?";
		val = vals[1];
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val, vals));
		
		key = "local optimization time";
		val = "0.2";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));
		
		key = "evidence estimation effort";
		val = "1";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));
		
		
		
	}



	
	
	public void setupRJMCMCoptionsPanel() 
	{
		String key = null;
		String val = null;
		
		key = "log file (leave blank for no logging)";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));

		key = "running time (hours)";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));
		
		key = "std dev (normalized)";
		val = "0.1";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));

		key = "# density bins";
		val = "100";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));
		
		key = "burn in misfit";
		val = "0.0";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));		

		key = "density log file";
		val = "mcmc.density";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));		
		
	}

	


	public void setupUMCoptionsPanel() 
	{
		String key = null;
		String val = null;
		
		key = "log file (leave blank for no logging)";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));

		key = "running time (hours)";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));
	}


	
	public void setupAdaptivePowellOptionsPanel()
	{
		String key = null;
		String val = null;
		
		key = "log file (leave blank for no logging)";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));

		key = "Minimum change in misfit deemed significant";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));
		
		key = "running time (hours)";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));

	}

	public void setupPowellOptionsPanel() 
	{
		String key = null;
		String val = null;
		
		key = "log file (leave blank for no logging)";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));

		key = "Minimum change in misfit deemed significant";
		val = "";
		options.put(key, val);
		optionsPanel.add(new OptionPanel(key, val));
		
		double[][] bounds = handle.getBoundsOnModelSpace();

		for(int i =0; i < handle.getDimensionOfModelSpace(); i++)
		{
			key = handle.getParameterName(i);
			val = "0.1";
			options.put(key, val);
			optionsPanel.add(new OptionPanel(key, val));
		}
	}












	class OptionPanel extends JPanel implements ActionListener, FocusListener
	{
		private String option;
		private String value;

		OptionPanel(String option) {
			this(option, "");
		}

		OptionPanel(String option, String[] possibleValues) {
			this(option, possibleValues[0], possibleValues);
		}

		OptionPanel(String option, String value) 
		{
			this.option = option;
			this.value = value;
			this.add(new JLabel(option));
			JTextField field = new JTextField(value, 16);
			field.addFocusListener(this);
			field.addMouseListener(new WatchForMouseExit());
			this.add(field);

			this.setBorder(
				BorderFactory.createLineBorder(Color.black));
		}

		OptionPanel(String option,String value,String[] possibleValues) 
		{
			boolean isSelected = false;
			this.option = option;
			this.value = value;
			this.add(new JLabel(option));
			ButtonGroup buttongroup = new ButtonGroup();

			//add all the buttons to the panel and to the ButtonGroup
			for (int i = 0; i < possibleValues.length; i++) 
			{
				String label = possibleValues[i];
				JRadioButton but = new JRadioButton(label);
				but.setActionCommand(label);
				buttongroup.add(but);
				this.add(but);
				but.addActionListener(this);
				if (value.equals(label)) 
				{
					isSelected = true;
					but.setSelected(true);
				}
			}
			this.setBorder(BorderFactory.createLineBorder(Color.black));

			if (!isSelected)
				throw new IllegalArgumentException("specified value '"+ value+ "' isn't value for option "+ option);

		}

		String getOptionValue() {
			return value;
		}
		
		
		String getOptionName() {
			return option;
		}

		//we handle our own events if a button is selected
		public void actionPerformed(ActionEvent e) 
		{
			//the user clicked a button, so we set the value accordingly
			String buttonlabel = e.getActionCommand();
			this.value = buttonlabel;
			options.put(this.option, this.value);
		}

		
		
		//if we lose focus on a textfield, we get the (possibly) new value from
		//the text field
		public void focusGained(FocusEvent fe) {
		}
		public void focusLost(FocusEvent fe) 
		{
			Object src = fe.getSource();
			if (!(src instanceof JTextField))
				throw new IllegalStateException("Got focus event from non-text field. Should be Impossibe. Bug in software! Please report to vendor");

			this.value = ((JTextField) src).getText();
			options.put(this.option, this.value);
		}


		class WatchForMouseExit extends java.awt.event.MouseAdapter
		{
			//if the mouse leaves the textfield, we also should get a possible new value
			//from the text field
			public void mouseExited(MouseEvent e) 
			{
				Object src = e.getSource();
				if (!(src instanceof JTextField))
					throw new IllegalStateException("Got mouseExit event from non-text field. Should be Impossibe. Bug in software! Please report to vendor");

				OptionPanel.this.value = ((JTextField) src).getText();
				options.put(OptionPanel.this.option, OptionPanel.this.value);			
			}
		}
	}
	
	
	
	
	class WaitForFinishThread extends Thread 
	{
		private ObservableInverter inverter = null;
		WaitForFinishThread(ObservableInverter inverter) {
				this.inverter = inverter;
		}

		class ModelExplorerCleanup extends java.awt.event.WindowAdapter
		{
			javax.swing.JFrame explframe = null; ModelExplorer mexp;
			ModelExplorerCleanup(javax.swing.JFrame mexp_frame, ModelExplorer mexp) 
			{ this.explframe=mexp_frame; this.mexp = mexp; }
			public void windowClosed(java.awt.event.WindowEvent we) {
				Debug.println("In windowClosed event handler method of ModelExplorerCleanup", Debug.EXTRA_INFO); 
				mexp.stop();
			}
		}
		
		public void run() {
			while(true) {
				try { Thread.sleep(10000); } //sleep for 10 seconds
				catch(InterruptedException ioe) {}
				
				//see if the inverter has finished, and if it has,
				//launch the ModelExplorer (if thats what the user wants)
				if(inverter.isFinished() && InversionMain.this.launchExplorerOnFinishCheckBox.isSelected()) 
				{
					javax.swing.JFrame jf = new javax.swing.JFrame();
					jf.setTitle("Model Explorer");
					jf.setSize(InversionMain.this.getSize());
					jf.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

					rses.visualisation.ModelExplorer me = new rses.visualisation.ModelExplorer(handle, nodelist);
					JScrollPane jsp = new JScrollPane(me, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
						JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
					me.setSize(jf.getSize());
					if(inverter.getBestModel() != null)
						me.setModel(inverter.getBestModel());					

					jf.getContentPane().add(jsp);
					jf.show();
					return; //we only launch the once
				}
			}
		}
	}
	
	
	
}

