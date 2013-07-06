package rses.apps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import rses.Debug;
import rses.inverse.UserFunctionHandle;
import rses.inverse.util.FakeUserFunctionHandle;
import rses.math.MathUtil;
import rses.spatial.GISLayer;
import rses.spatial.util.DataStore;
import rses.spatial.util.GeoDataTable;
import rses.util.Util;
import rses.visualisation.ModelDisplayPanel;
import sun.misc.JavaUtilJarAccess;



/** Load up the geodatabase, including layer membership and the like
 * 
 * 
 * Structure assumed is as follows:
 * 
 * There is a directory called "regions" that contains all the region membership gis files
 * 
 * There is a directory called "db" that contains the following:
 * 
 *                    EITHER
 *                    
 *       files that are simple region_id --> value mappings.
 *       these filenames must end in ".tab"
 *       THE FIRST LINE IN THESE FILES MUST START WITH A LINE LIKE THIS:
 *       #layer LAYERNAME
 *       where LAYERNAME is the file stem of the gis membership layer it refers to
 * 
 *                     OR
 *                     
 *       files that are raster layers
 *       these must end in ".gis"
 * 	    
 *      
 * 
 * 
 * 
 * 
 * 
 * 
 * 
 * 
 * @author peterr
 *
 */
public final class MCA_CompareTool implements Runnable
{
	private rses.spatial.gui.MainWindow mainwindow;
	
	private MCA_CompareTool(rses.spatial.gui.MainWindow mainwindow)  
	{
		this.mainwindow = mainwindow;
	}
	
	
	public void run()
	{
		//ok, the aim is to ask the user to compare/choose different 
		//regions and use these to infer all the ones in-between
		
		
		//ask the user how many assessments to do
		String numassess = javax.swing.JOptionPane.showInputDialog("How many manual assessments? ");
		int n;
		while(true) {	
			try { n = Integer.parseInt(numassess); break; }
			catch(NumberFormatException nfe) {}
		}
		

		HashMap<String, rses.spatial.util.GeoDataTable> tables = database.getTablesForLayer(mainwindow.getCurrentMembershipLayerName());

		//first lets get a list of all the different options and the dimensions
		//on which we evaluate them
		java.util.Iterator<String> tablesit = tables.keySet().iterator();
		ArrayList<String> options = new ArrayList<String>();
		ArrayList<String> optionDimensions = new ArrayList<String>();
		while(tablesit.hasNext())
		{
			String table = tablesit.next();
			if(table.startsWith("OPT_")) //its an option table
			{
				String optionname = table.split("/")[0];
				String dimension = table.split("/")[1];
				
				if(!options.contains(optionname)) {
					Debug.println("Adding OPTION "+optionname, Debug.IMPORTANT);
					options.add(optionname);
				}
				
				if(!optionDimensions.contains(dimension)) {
					Debug.println("Adding DIMENSION "+dimension, Debug.IMPORTANT);
					optionDimensions.add(dimension);
				}

			}
		}
		
		//TODO go through and check that each option has tables for each dimension
		GISLayer layerforanalysis = mainwindow.getCurrentMembershipLayer();
		
		
		//now estimate coefficients for each dimension.. this is just like
		//estimating the underlying utility function coefficients when we only
		//observe the optimal choice
		ArrayList<double[]> data = new ArrayList<double[]>(); //the mca scores on each dimension
		ArrayList<Integer> choices = new ArrayList<Integer>(); //the observed choice

		

		
		//go through and prompt the user to do an MCA for random regions
		for(int i =0; i < n; i++)
		{			
			String reg = pickRegion(null);
			Debug.println("Chose region "+reg, Debug.IMPORTANT);
	
			String suggestion = "";
			for(int j = 0; j < options.size(); j++)
				suggestion += options.get(j)+" ";
						
			//prompt for an assessment and record the response
			String optionsuite = promptAssessment(reg, suggestion);
			if(!options.contains(optionsuite))
				throw new RuntimeException("Invalid option suite entered: "+optionsuite); //TODO handle this case
			
			//now record the result
			//each data point is the scores for all options and the option chosen
			double[] d = new double[optionDimensions.size()*options.size()];
			for(int optnum = 0; optnum < options.size(); optnum++) //scores for each option
			{
				for(int j = 0; j < optionDimensions.size(); j++)
				{
					String tabname = options.get(optnum)+"/"+optionDimensions.get(j);					
					d[optnum*optionDimensions.size()+j] = (Double) database.getTable(mainwindow.getCurrentMembershipLayerName(), tabname).lookup(reg);
				}
			}
			data.add(d);
			choices.add(options.indexOf(optionsuite));
			
		}
		
		
		class UFH extends FakeUserFunctionHandle 
		{
			ArrayList<double[]> dat;
			ArrayList<Integer> ch;
			int maxch;
			public UFH(int nd, double[][] bounds, ArrayList<double[]> dat, ArrayList<Integer> ch, int numchoices) {
				super(nd, bounds);
				this.dat = dat;
				this.ch = ch;
				this.maxch = numchoices;
			}
			
			public double getPriorForModel(double[] m) { return 1.0; }
			
			public double getErrorForModel(double[] m) 
			{
				double numcorrect = 0.0;
				
				Debug.println("Model: "+Util.arrayToString(m), Debug.IMPORTANT);
				
				//for each data point
				for(int i = 0; i < dat.size(); i++)
				{

					//work out the score for each choice
					double[] scorebychoice = new double[maxch];

					for(int c = 0; c < maxch; c++)
					{
						double score = 0.0;
						for(int criteria = 0; criteria < m.length; criteria++)
							scorebychoice[c] += m[criteria]*dat.get(i)[c*m.length+criteria];
					}
					
					Debug.println("Data :"+Util.arrayToString(dat.get(i)), Debug.IMPORTANT);
					Debug.println("Score for choices :"+Util.arrayToString(scorebychoice), Debug.IMPORTANT);
					Debug.println("Actual choice is :"+ch.get(i), Debug.IMPORTANT);
					
					//find out best one
					int best = Util.getMaxIndex(scorebychoice);
					if(best == ch.get(i))
						numcorrect++;
					
				}
				
				Debug.println("Error for model is "+(-numcorrect), Debug.IMPORTANT);
				return -numcorrect;
			}
				
		}

		Debug.println("Building UserFunctionHandle", Debug.IMPORTANT);

		//ok, done. now estimate results
		double[][] bounds = new double[optionDimensions.size()][2];
		for(int i = 0; i < bounds.length; i++) bounds[i] = new double[] {0.1, 3.0};
		UFH ufh = new UFH(optionDimensions.size(), bounds, data, choices, options.size());
		rses.inverse.montecarlo.MonteCarlo mc = new rses.inverse.montecarlo.MonteCarlo(ufh);
		
		Debug.println("Running inversion", Debug.IMPORTANT);
		
		mc.setTimeToRun(10000);
		mc.run();
		rses.Model model = mc.getBestModel();
		
		String[] pnames = new String[bounds.length];
		for(int i =0; i < pnames.length; i++)
			pnames[i] = optionDimensions.get(i);
		rses.visualisation.ModelDisplayPanel mdp = new ModelDisplayPanel(model, pnames);
		javax.swing.JFrame frame = new javax.swing.JFrame();
		frame.setSize(400, 600);
		frame.getContentPane().add(mdp);
		frame.setVisible(true);
		
		
		//ok, so now create a layer showing the optimal combination
		for(int i = 0; i < layerforanalysis.getCategoryNames().length; i++)
		{
			String region = layerforanalysis.getCategoryNames()[i]; 
			int bestopt = getBestOptionForRegion(model.getModelParameters(), region, mainwindow.getCurrentMembershipLayerName(), mainwindow.getCurrentMembershipLayer(), optionDimensions, options);
			database.getTable(mainwindow.getCurrentMembershipLayerName(), "BestOption").set(region, (double) bestopt);
		}
		
		
		
	}
	
	
	
	private static int getBestOptionForRegion(double[] critweights, String region, String layerforanalysisname, GISLayer layerforanalysis, ArrayList<String> optionDimensions, ArrayList<String> options)
	{		
		double[] scores = new double[options.size()];
		//now get the total score for each option
		for(int opt = 0; opt < options.size(); opt++)
		{
			//the score for the option is the optionscore for each criteria times the weight for each criteria
			for(int criteria = 0; criteria < critweights.length; criteria++)
				scores[opt] += critweights[criteria]*((Double) database.getTable(layerforanalysisname, options.get(opt)+"/"+optionDimensions.get(criteria)).lookup(region));			
		}
		
		return Util.getMaxIndex(scores);
	}
	
	
	public String pickRegion(Object[] donesofar)
	{
		//pick at random
		GISLayer gis = mainwindow.getCurrentMembershipLayer();
		
		String[] names = gis.getCategoryNames();
		String chosen = names[(int) (Math.random()*names.length)];
		if(donesofar == null || donesofar.length == 0)
			/* do nothing */;
		else while(Util.getIndex(chosen, donesofar) >= 0) 
			chosen = names[(int) (Math.random()*names.length)];			
		
		return chosen;
	}
	
	
	
		
	//prompt an assessment of a region
	public String promptAssessment(String regionname, String suggestion)
	{
		//first pop up a region view dialog
		rses.spatial.gui.RegionDataView regionview = new rses.spatial.gui.RegionDataView(mainwindow.getCurrentMembershipLayerName(), regionname, database);
		
		//now get the user to select what combination works best
		//at the moment just get the user to enter the text of the option
		String result = javax.swing.JOptionPane.showInputDialog("Enter most suitable suite of demand/supply options for this site: ", suggestion);
		
		//and we are done!!!
		return result;
	}
	
	
	
	private static DataStore database;
	public static void main(String[] args) throws Exception
	{
		Debug.setDebugLevel(Debug.DEBUG_SUSPICIOUS);
		Debug.setVerbosityLevel(Debug.EXTRA_INFO);
		
		//first we create the data store
		database = new DataStore();

		
		//get a membership matrix for each layer
		HashMap<String, int[][]> membershipPerLayer = new HashMap<String, int[][]>();
		
		java.util.List<String> layernames = database.getVectorLayers();
		
		java.util.Iterator<String> layerit = layernames.iterator();
		while(layerit.hasNext())
		{
			String name = layerit.next();
			GISLayer regionmembership = database.getVectorLayer(name);
			
			//int[][] membership = DisplayableModel.getMembershipMatrix(regionmembership, regionmembership.getLongSteps(), regionmembership.getLatSteps());
			
			//order our membership data so that it matches the coordinate system for graphics layout.
			int[][] membership = new int[regionmembership.getLatSteps()][];
			for(int i = 0; i < membership.length; i++)
				membership[i] = regionmembership.categoricaldata[membership.length-i-1];
			membershipPerLayer.put(name, membership);
		}
		
		//ok, now launch the main window
		rses.spatial.gui.MainWindow mainwindow = new rses.spatial.gui.MainWindow(800, 800, database, layernames, membershipPerLayer);
		
		
		
		HashMap<String, rses.spatial.util.GeoDataTable> tables = database.getTablesForLayer(mainwindow.getCurrentMembershipLayerName());

		//first lets get a list of all the different options and the dimensions
		//on which we evaluate them
		java.util.Iterator<String> tablesit = tables.keySet().iterator();
		ArrayList<String> options = new ArrayList<String>();
		ArrayList<String> optionDimensions = new ArrayList<String>();
		while(tablesit.hasNext())
		{
			String table = tablesit.next();
			if(table.startsWith("OPT_")) //its an option table
			{
				String optionname = table.split("/")[0];
				String dimension = table.split("/")[1];
				
				if(!options.contains(optionname)) {
					Debug.println("Adding OPTION "+optionname, Debug.IMPORTANT);
					options.add(optionname);
				}
				
				if(!optionDimensions.contains(dimension)) {
					Debug.println("Adding DIMENSION "+dimension, Debug.IMPORTANT);
					optionDimensions.add(dimension);
				}

			}
		}

		
		//create default layer
		for(int i = 0; i < mainwindow.getCurrentMembershipLayer().getCategoryNames().length; i++)
		{
			String region = mainwindow.getCurrentMembershipLayer().getCategoryNames()[i]; 
			int bestopt = getBestOptionForRegion(new double[] {1.0, 1.0}, region, mainwindow.getCurrentMembershipLayerName(), mainwindow.getCurrentMembershipLayer(), optionDimensions, options);
			database.getTable(mainwindow.getCurrentMembershipLayerName(), "BestOption").set(region, (double) bestopt);
		}

		
		//add a component
		MCA_CompareTool ct = new MCA_CompareTool(mainwindow);
		mainwindow.addRunnableComponent(ct, "MCA calibration");
		
		mainwindow.setVisible(true);
	}
	
	
	
	

}
