package rses.CoSpedestriantracking;
import java.io.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.org.apache.bcel.internal.generic.GETSTATIC;

import rses.Debug;
import rses.math.MathUtil;
import rses.spatial.GISLayer;
import rses.util.FileUtil;
import sun.reflect.ReflectionFactory.GetReflectionFactoryAction;

public class Main 
{
	public static class initInfo {
		rses.spatial.GISLayer gis;
		RSSObservation[] rss;
		Distribution P0prior;
		PMF prior;
		Map<String, PMF> decayMapsPerObserver;
		Observer[] observers;
		double noiseSigma;
		double stepsize;
	}

	private static void usage()
	{
		//the input data files are
		//args[0] == observations file (receiverid, sourceId, time, dbm)
		//args[1] == observers file (id,lat,lon,height)
		//args[2] == gis bounds (minlat, minlon, maxlat, maxlon, latsteps,lonsteps)
		//args[3] == "noprior" or the name of a file which specifies the prior
		//args[4 to 4+numobservers-1] == the distance decay maps for each observer
		//args[4+numobservers] == noise parameter (sigma)
		//args[4+numobservers+1] == P0 prior mean
		//args[4+numobservers+2] == P0 prior standard deviation
		
		System.err.println("Reminder of usage: ");
		System.err.println("  ");
		System.err.println("java Main OBSERVATIONS OBSERVERS BOUNDS PRIOR [DECAYMAPS] NOISESIGMA P0MEAN P0STDDEV INPUTFILE RUNTYPE");
		System.err.println("where:");
		System.err.println("  OBSERVATIONS is a file of the observations (i.e. signals received from phones). Each line in the file is of the form \"RID SID yyyy-MM-dd HH:mm:ss.SSS dBm\" where RID is the unique id of the receiver who saw the signal, SID is the unique id of the source (i.e. the phone), then a timestamp of when the signal was received, and finally the received signal strength of the signal at the receiver");
		System.err.println("  ");
		System.err.println("  OBSERVERS is a file of the observers (i.e. receiving stations). Each line in the file is of the form \"ID lat lon z\" where ID is the unique ID of the receiver, lat/lon is the location of the receiver, and z is the height (above ground level) of the receiver");
		System.err.println("  ");
		System.err.println("  BOUNDS is a file with one line of the form \"MINLAT MINLONG MAXLAT MAXLON STEPSIZEM\" where the first 4 specify the bounding box of the region, and the last specifies the size (in meters) of each grid cell");
		System.err.println("  ");
		System.err.println("  ");
		System.err.println("  PRIOR is either 'noprior' (in which case a uniform prior is used) or else is a spatial prior");
		System.err.println("  ");
		System.err.println("  [DECAYMAPS] is a list of files that specify the dBm decay map for each receiver. These *MUST* be called decayMap_ID.txt, where ID is the unique id of the receiver");
		System.err.println("  ");
		System.err.println("  NOISESIGMA is the standard deviation to use in the model of signal decay");
		System.err.println("  ");
		System.err.println("  P0MEAN is our initial 'guess' of the signal strength of the mobile phone at a distance of 1m from the phone. ");
		System.err.println("  ");
		System.err.println("  P0STDDEV is the standard deviation of our 'guess' of the initial signal strength (i.e. it helps define the 'prior' distribution of source signal strength)");
		System.err.println("  ");
		System.err.println("  INPUTFILE is a file with key input parameters");
		System.err.println("  ");		
		System.err.println("  RUNTYPE is either 'inputgen', 'quick' or 'full'");
		System.err.println("  ");
		System.err.println("  ");		
	}
	
	
	public static GISLayer getGISLayer(String boundsfile, String name)
	throws java.io.IOException
	{
		BufferedReader rdr = new BufferedReader(new FileReader(boundsfile));
		String[] bits = rses.util.Util.getWords(rdr.readLine());
		double minlat = Double.parseDouble(bits[0]);
		double minlon = Double.parseDouble(bits[1]);
		double maxlat = Double.parseDouble(bits[2]);
		double maxlon = Double.parseDouble(bits[3]);
		int latsteps, lonsteps;
		if(bits.length == 5) 
		{
			double stepsize_metres = Double.parseDouble(bits[4]);
			double latmetres = rses.math.MathUtil.getDistanceBetweenPointsOnEarth(minlat, (minlon+maxlon)/2, maxlat, (minlon+maxlon)/2);
			double lonmetres = rses.math.MathUtil.getDistanceBetweenPointsOnEarth((minlat+maxlat)/2, minlon, (minlat+maxlat)/2, maxlon);
			double onemetreequalsthismanylatdegrees = (maxlat-minlat)/latmetres;
			double onemetreequalsthismanylondegrees = (maxlon-minlon)/lonmetres;
			latsteps = (int) Math.round(latmetres/stepsize_metres);
			lonsteps = (int) Math.round(lonmetres/stepsize_metres);
			maxlat = minlat + latsteps*onemetreequalsthismanylatdegrees*stepsize_metres;
			maxlon = minlon + lonsteps*onemetreequalsthismanylondegrees*stepsize_metres;
			Debug.println("There are "+latsteps+" lat steps and "+lonsteps+" lonsteps with stepsize of "+stepsize_metres+"m ", Debug.INFO);
		}
		else {
			throw new RuntimeException("Old format of bounds file is no longer supported. You must specify minlat,maxlat,minlong,maxlong,stepsize_in_metres");
			//latsteps = Integer.parseInt(bits[4]);
			//lonsteps = Integer.parseInt(bits[5]);
		}		
		rses.spatial.GISLayer gis = new rses.spatial.GISLayer(minlat, minlon, maxlat-minlat, maxlon-minlon, name, new float[latsteps][lonsteps]);
		Debug.println("minlat: "+minlat, Debug.INFO);
		Debug.println("maxlat: "+maxlat, Debug.INFO);
		Debug.println("minlon: "+minlon, Debug.INFO);
		Debug.println("maxlon: "+maxlon, Debug.INFO);
		Debug.println("latsteps: "+latsteps, Debug.INFO);
		Debug.println("lonsteps: "+lonsteps, Debug.INFO);
		return gis;
	}
	
	public static initInfo initialize(String[] args) throws Exception
	{

		//get Observers
		Observer[] obs = readObservers(args[1]);
		Debug.println("Read observers file "+args[1], Debug.INFO);
		
		//get Observations
		RSSObservation[] rss = RSSObservation.readObservations(args[0], obs);
		Debug.println("Read observations file "+args[0], Debug.INFO);

		//get GIS bounds and create a GIS layer that covers these bounds
		GISLayer gis = getGISLayer(args[2], "noname");
		Debug.println("Read bounds file "+args[2]+" and created geography", Debug.INFO);
		
		//get the spatial prior
		PMF prior = null;
		if(!args[3].equalsIgnoreCase("noprior")) 
			prior = PMF.read(args[3], gis);
		else { //just go with uniform prior
			prior = new PMF(gis, true);
		}
		Debug.println("Initialized spatial prior", Debug.INFO);
		
		gis.generateGoogleEarthOverlay("docs");
		if(prior != null)
			prior.writeImage("Prior");

		//now get the decay maps for each observer
		Map<String, PMF> decayMaps = new HashMap<String, PMF>();
		for(int i = 0; i < obs.length; i++) 
		{
			String path = args[4+i];
			String filename = new java.io.File(path).getName();
			Debug.println("Reading decay map "+filename, Debug.INFO);
			if(!filename.startsWith("decayMap_")) throw new RuntimeException("Argument "+path+" is not a valid decay map name, and that is what I expected");			
			String rid = filename.split("_")[1].split("\\.")[0];
			Debug.println("    Receiver ID for this decay map looks like "+rid, Debug.INFO);
			
			boolean validrid = false;
			for(int j = 0; j < obs.length; j++) 
				if(obs[j].id.equals(rid)) { validrid = true; break; }
			if(!validrid) throw new RuntimeException("decay map specified for receiver "+rid+" but I dont know about any such receiver");
			
			PMF decayMap = PMF.read(path, gis, false);
			Debug.println("Read decay map for observer "+rid, Debug.INFO);
			if(decayMaps.containsKey(rid)) throw new RuntimeException("Suplicate decay map specified for observer "+rid);
			decayMaps.put(rid, decayMap); 
		}

		
		double P0mean = Double.parseDouble(args[4+obs.length+1]);
		Debug.println("Source signal strength prior mean set to user specified value of "+P0mean, Debug.INFO);
		double P0stddev = Double.parseDouble(args[4+obs.length+2]);
		Debug.println("Source signal strength prior standard dev set to user specified value of "+P0stddev, Debug.INFO);
		class priorDist implements Distribution {
			double mean;
			double stddev;
			priorDist(double m, double s) { this.mean = m; this.stddev = s;}
			public double drawFrom() { return new java.util.Random().nextGaussian()*stddev+mean; }
			public double pdf(double val) { return rses.math.MathUtil.getGaussPdf(val, mean, stddev); }
		};		
		Distribution P0prior = new priorDist(P0mean, P0stddev);
		
		initInfo inf = new initInfo();
		inf.noiseSigma = Double.parseDouble(args[4+obs.length]);		
		Debug.println("'Noise' standard deviation on received signal strength set to user specified value of "+inf.noiseSigma+" dBm", Debug.INFO);
		inf.gis = gis;
		inf.P0prior = P0prior;
		inf.prior = prior;
		inf.rss = rss;
		inf.observers = obs;
		inf.decayMapsPerObserver = decayMaps;
		return inf;
	}
	
	
	
	
	private static void swapDistanceMeasures(rses.math.GraphNode pathtree, double squaresize)
	{
		for(rses.math.GraphEdge edge : pathtree.getEdges())
		{
			PathNode from = (PathNode) edge.leadsfrom;
			PathNode to = (PathNode) edge.leadsto;
			double dist = Math.sqrt(Math.pow(squaresize*(from.lati-to.lati), 2.0)+Math.pow(squaresize*(from.loni-to.loni), 2.0));
						
			if(edge.info == null)
				edge.info = dist;
			
			double old = edge.weight;
			edge.weight = ((Double) edge.info).doubleValue();
			edge.info = old;
			swapDistanceMeasures(edge.leadsto, squaresize);
		}
	}

	
	public static double[][][][] getRawDistanceFromPriorGraph(PMF prior)
	{
		java.util.HashMap<String, rses.math.GraphNode> graph = new java.util.HashMap<String, rses.math.GraphNode>();
		
		
		//initialize a 2D array with the nodes
		PathNode[][] mesh = new PathNode[prior.getDimensions()[0]][prior.getDimensions()[1]];
		double[][][][] result = new double[mesh.length][mesh[0].length][mesh.length][mesh[0].length];
		for(int lati = 0; lati < prior.getDimensions()[0]; lati++)
			for(int loni = 0; loni < prior.getDimensions()[1]; loni++) 
			{
				if(prior.getProbByIndices(lati, loni) > 0) 
				{
					mesh[lati][loni] = new PathNode(-1, lati, loni, prior.getDimensions()[0], prior.getDimensions()[1]);
					graph.put(lati+" "+loni, mesh[lati][loni]);
				}
				else
					mesh[lati][loni] = null;
			}
		

		//now add edges
		for(int lati = 0; lati < prior.getDimensions()[0]; lati++)
			for(int loni = 0; loni < prior.getDimensions()[1]; loni++)		
				//add edges to neighbours
				if(prior.getProbByIndices(lati, loni) > 0) 
					for(int latoff = -1; latoff <= 1; latoff++)
						for(int lonoff = -1; lonoff <= 1; lonoff++)
						{
							if(lati+latoff < 0 || lati+latoff >= prior.getDimensions()[0] || loni+lonoff < 0 || loni+lonoff >= prior.getDimensions()[1])
								continue;
							else if(prior.getProbByIndices(lati+latoff, loni+lonoff) > 0.0)
							{
								double dist = Math.sqrt(Math.pow(latoff*prior.getSquareSize(), 2)+Math.pow(lonoff*prior.getSquareSize(), 2.0));
								mesh[lati][loni].addEdge(mesh[lati+latoff][loni+lonoff], dist);
							}
							else
								result[lati][loni][lati+latoff][loni+lonoff] = Double.POSITIVE_INFINITY;
						}
		
		
		//work out shortest path trees and whack the distances into an array
		for(int lati = 0; lati < prior.getDimensions()[0]; lati++)
			for(int loni = 0; loni < prior.getDimensions()[1]; loni++)
				if(mesh[lati][loni] != null)
				{
					rses.math.GraphNode pathtree = rses.math.GraphUtil.getShortestPathTree(lati+" "+loni, graph);
					java.util.Map<String, Double> dists = rses.math.GraphUtil.getPathLengthsFromPathTree(pathtree);
					for(String key : dists.keySet())
					{
						PathNode dest = (PathNode) graph.get(key);
						result[lati][loni][dest.lati][dest.loni] = dists.get(key);
					}
				}
		
		
		return result;
	}
	
	
	
	
	
	public static void main(String[] args) throws Exception
	{
		usage();
		
		Debug.setVerbosityLevel(Debug.INFO);
		initInfo inf = initialize(args);
		
		String paramfile = args[args.length-2];
		Map<String, String> keysvals = FileUtil.readKeyValueMappingsFromFile(paramfile);
		LazyPathNode.init(keysvals);

		String runtype = args[args.length-1];
		if(!(runtype.equals("inputgen") || runtype.equals("quick") || runtype.equals("full")))
			throw new RuntimeException("unknown runtype "+runtype);
			
		Map<String, List<RSSObservation>> sourceIdToObservation = new HashMap<String, List<RSSObservation>>();
		
		//split up the signals according to source
		for(int i = 0; i < inf.rss.length; i++)
		{
			String sourceid = inf.rss[i].sourceId;
			if(!sourceIdToObservation.containsKey(sourceid)) {
				Debug.println("Saw new source id "+sourceid, Debug.INFO);
				sourceIdToObservation.put(sourceid, new ArrayList<RSSObservation>());
			}
			
			sourceIdToObservation.get(sourceid).add(inf.rss[i]);
		}

		//build all the shortest path trees
		Debug.println("Building prior graph", Debug.INFO);
		inf.prior.cacheLogProbs(); //This saves a *lot* of Math.log calls
		PathNode[][] priorgraph = inf.prior.getDistanceGraph();
		java.util.HashMap<String, rses.math.GraphNode> graph = new java.util.HashMap<String, rses.math.GraphNode>();
		for(int lati = 0; lati < priorgraph.length; lati++)
			for(int loni = 0; loni < priorgraph[0].length; loni++)
				if(priorgraph[lati][loni] != null)
					graph.put(priorgraph[lati][loni].getId(), priorgraph[lati][loni]);

		Debug.println("Building shortest path trees", Debug.INFO);
		PathNode[][] pathtrees = new PathNode[priorgraph.length][priorgraph[0].length];

		//this is the distance in meters, when we go by the shortest physical distance path
		double[][][][] rawdistance = getRawDistanceFromPriorGraph(inf.prior);
		
		//this is the path length in terms of log probs (i.e. the shortest path according the spatial prior)
		double[][][][] pathlengths = new double[priorgraph.length][priorgraph[0].length][priorgraph.length][priorgraph[0].length];

		//This one is a little bit odd. It is the number of steps (edges) along the shortest path between a,b and c,d
		int[][][][] pathsteps = new int[priorgraph.length][priorgraph[0].length][priorgraph.length][priorgraph[0].length];

		//now go through and get the shortest path from each origin to each destination
		for(int lati = 0; lati < priorgraph.length; lati++)
			for(int loni = 0; loni < priorgraph[0].length; loni++) 
			{
				if(priorgraph[lati][loni] == null) { //origin is not reachable
					pathlengths[lati][loni] = null;
					continue;
				}
				
				pathtrees[lati][loni] = (PathNode) rses.math.GraphUtil.getShortestPathTree(priorgraph[lati][loni].getId(), graph);
				
				java.util.Map<String, Double> shortest = rses.math.GraphUtil.getPathLengthsFromPathTree(pathtrees[lati][loni]);
				for(int destlati = 0; destlati < priorgraph.length; destlati++)
					for(int destloni = 0; destloni < priorgraph[0].length; destloni++)
					{
						if(destlati == lati && destloni == loni)
							continue; //path steps is 0
						
						if(priorgraph[destlati][destloni] != null)
						{
							pathlengths[lati][loni][destlati][destloni] = shortest.get(priorgraph[destlati][destloni].getId());
							//the number of steps between the two squares
							//Debug.println("Getting path steps from "+lati+","+loni+" to "+destlati+","+destloni, Debug.INFO);
							pathsteps[lati][loni][destlati][destloni] = rses.math.GraphUtil.getShortestPathFromPathTree(pathtrees[lati][loni], destlati+" "+destloni).size();
						}
						else
						{
							pathlengths[lati][loni][destlati][destloni] = Double.POSITIVE_INFINITY;
							pathsteps[lati][loni][destlati][destloni] = -1;
						}
					}
			}
		Debug.println("Finished building shortest path trees, now calculating distances by most probable path", Debug.INFO);
		

		//to get physical distances by way of the shortest probability path, 
		//we go through and replace the current edge weights (which are based on the -logprob 
		//of the prior at the destination) with the distance weights 
		double[][][][] pathdistances = new double[priorgraph.length][priorgraph[0].length][priorgraph.length][priorgraph[0].length];
		for(int lati = 0; lati < priorgraph.length; lati++)
			for(int loni = 0; loni < priorgraph[0].length; loni++)
			{
				if(pathtrees[lati][loni] == null) {
					pathdistances[lati][loni] = null;
					continue;
				}

				swapDistanceMeasures(pathtrees[lati][loni], inf.prior.getSquareSize());
				java.util.Map<String, Double> shortest = rses.math.GraphUtil.getPathLengthsFromPathTree(pathtrees[lati][loni]);
				for(int destlati = 0; destlati < priorgraph.length; destlati++)
					for(int destloni = 0; destloni < priorgraph[0].length; destloni++)
						if(priorgraph[destlati][destloni] != null)
							pathdistances[lati][loni][destlati][destloni] = shortest.get(priorgraph[destlati][destloni].getId());
						else
							pathdistances[lati][loni][destlati][destloni] = Double.POSITIVE_INFINITY;
				
				swapDistanceMeasures(pathtrees[lati][loni], inf.prior.getSquareSize()); //swap edges back to be prior based, not distance based
			}
		
		
		Debug.println("Finished calculating all distances & path trees, now analyzing", Debug.INFO);
		
		for(String sourceid : sourceIdToObservation.keySet())
		{
			Debug.println("Analyzing source id "+sourceid, Debug.INFO);
			inf.rss = sourceIdToObservation.get(sourceid).toArray(new RSSObservation[0]);

			//Lets dump stuff out for steve
			if(runtype.equals("inputgen"))
				dumpStuffForSteve(inf);
			
			//NB: A minor question is whether we use raw distances to do propogation of
			//probabilities, or if we use the distance of the most probable path....
			//If we use shortest path, then we project probabilities out along the shortest path
			//tree. This seems reasonable to a point, but if we are doing work afterwards to work out
			//the most probable path properly perhaps it doesnt make much sense and we should just do
			//the raw distance based projection.
			else if(runtype.equals("quick"))
				submain(inf, pathlengths, pathdistances, pathtrees, true);
			else
				submain(inf, pathlengths, pathdistances, pathtrees, false);
			//submain(sourceIdToObservation.get(sourceid), inf, pathlengths, rawdistance, pathtrees);
		}
		
	}
	
	
	public static void submain(initInfo inf,
			double[][][][] pathlengths, double[][][][] rawdistance, PathNode[][] pathtrees, boolean quickMode) 
	throws Exception
	{
		for(int i = 1; i < inf.rss.length; i++)
			if(!inf.rss[i].sourceId.equals(inf.rss[0].sourceId)) throw new RuntimeException("Source id's do not match in submain -- this should be impossible");
						
		new CosProbModel(inf.gis, inf.rss, inf.P0prior, inf.prior, pathlengths, 
				null /*pathsteps*/, rawdistance, 
				pathtrees, null /*speeddist*/, 
				inf.decayMapsPerObserver, inf.noiseSigma,
				quickMode);
				
	}
	
	
	/** Dump out stuff for Steve to run his script (or for us to try other stuff on). The stuff we
	 *  need to dump out consists of
	 *  
	 *  1) source observations file 
	 *  2) decay maps
	 *  3) spatial prior
	 *  4) other info:
	 *           size of square (in metres)
	 *           other?
	 * 
	 * @param observations
	 * @param inf
	 * @throws java.io.IOException
	 */
	private static void dumpStuffForSteve(initInfo inf)
	throws java.io.IOException
	{
		//create a directory
		File dir = new File(new File("inputsBySource"), "SOURCE_"+inf.rss[0].sourceId);
		if(dir.exists() && !dir.isDirectory()) throw new RuntimeException("File "+dir.getCanonicalPath()+" exists already and is not a directory!");
		if(!dir.exists()) 
			if(!dir.mkdirs()) throw new RuntimeException("Failed to create required directory "+dir.getCanonicalPath());
		
		
		String srccheck = inf.rss[0].sourceId;
		//ok, dump out observations
		java.io.PrintStream ps = new java.io.PrintStream(new java.io.File(dir, "observations.txt"));
		for(RSSObservation obs : inf.rss) {
			String y = ""+obs.time.get(Calendar.YEAR);
			String m = ""+obs.time.get(Calendar.MONTH);
			if(m.length() == 1) m = "0"+m;
			String d = ""+obs.time.get(Calendar.DAY_OF_MONTH);
			if(d.length() == 1) d = "0"+d;
			String hr = ""+obs.time.get(Calendar.HOUR_OF_DAY);
			if(hr.length() == 1) hr = "0"+hr;
			String min = ""+obs.time.get(Calendar.MINUTE);
			if(min.length() == 1) min = "0"+min;
			String s = ""+obs.time.get(Calendar.SECOND);
			if(s.length() == 1) s = "0"+s;
			String timestr = y+"-"+m+"-"+d+" "+hr+":"+min+":"+s;
			ps.println(obs.observer.id+" "+obs.sourceId+" "+timestr+" "+obs.ss);
			if(!obs.sourceId.equals(srccheck)) throw new RuntimeException("Source id's are not all identical!");
		}
		ps.close();
		
		//write decay maps
		for(String observer : inf.decayMapsPerObserver.keySet())
		{
			PMF decay = inf.decayMapsPerObserver.get(observer);
			decay.printToFile(dir, "decayMap_for_"+observer+".txt");
		}
		
		//write spatial prior
		inf.prior.printToFile(dir, "SpatialPrior.txt");
		
		//write miscellaneous information (size of grid square?)
		ps = new java.io.PrintStream(new java.io.File(dir, "misc.txt"));
		double squaresize_m1 = inf.gis.getLatStepSizeInMetres();
		double squaresize_m2 = inf.gis.getLonStepSizeInMetres();
		Debug.println("Step size 1 is "+squaresize_m1, Debug.INFO);
		Debug.println("Step size 2 is "+squaresize_m2, Debug.INFO);
		double diff = (squaresize_m1-squaresize_m2)/(0.5*squaresize_m1+0.5*squaresize_m2); 
		if(diff > 0.1)
			throw new RuntimeException("Difference between lat/long based square size is too great!");
		
		ps.println("SquareSize_in_metres "+(0.5*squaresize_m1+0.5*squaresize_m2));
		ps.close();		
	}

	
	
	public static Observer[] readObservers(String filename) throws Exception
	{
		java.io.BufferedReader rdr = new java.io.BufferedReader(new java.io.FileReader(filename));
		java.util.ArrayList observers = new java.util.ArrayList();
		String line = rdr.readLine();
		while(line != null)
		{
			String[] bits = rses.util.Util.getWords(line);
			String id = bits[0];
			double lat = Double.parseDouble(bits[1]);
			double lon = Double.parseDouble(bits[2]);
			double z = Double.parseDouble(bits[3]);
			observers.add(new Observer(lat, lon, z, id));
			line = rdr.readLine();
		}
		Observer[] toreturn = new Observer[observers.size()];
		for(int i =0; i < toreturn.length;i++)
			toreturn[i] = (Observer) observers.get(i);
		
		return toreturn;
		
	}
	

}
