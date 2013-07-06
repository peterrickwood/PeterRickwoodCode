package rses.CoSpedestriantracking;
import java.io.*;

import com.sun.org.apache.bcel.internal.generic.GETSTATIC;

import rses.Debug;
import rses.math.MathUtil;
import sun.reflect.ReflectionFactory.GetReflectionFactoryAction;

public class Main_old 
{
	public static class initInfo {
		rses.spatial.GISLayer gis;
		RSSObservation[] rss;
		Distribution P0prior;
		PMF prior;
		PMF[] decayMapsPerObserver;
		Observer[] observers;
	}
	
	public static initInfo initialize(String[] args) throws Exception
	{
		//the input data files are
		//args[0] == observations file (receiverid, sourceId, time, dbm)
		//args[1] == observers file (id,lat,lon,height)
		//args[2] == gis bounds (minlat, minlon, maxlat, maxlon, latsteps,lonsteps)
		//args[3] == "noprior" or the name of a file which specifies the prior
		//args[4 to 4+numobservers-1] == the distance decay maps for each observer

		//get Observers
		Observer[] obs = readObservers(args[1]);
		
		//get Observations
		RSSObservation[] rss = RSSObservation.readObservations(args[0], obs);

		//get GIS bounds and create a GIS layer that covers these bounds
		BufferedReader rdr = new BufferedReader(new FileReader(args[2]));
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
			latsteps = Integer.parseInt(bits[4]);
			lonsteps = Integer.parseInt(bits[5]);
		}		
		rses.spatial.GISLayer gis = new rses.spatial.GISLayer(minlat, minlon, maxlat-minlat, maxlon-minlon, "noname", new float[latsteps][lonsteps]);
		
		//get the spatial prior
		PMF prior = null;
		if(!args[3].equalsIgnoreCase("noprior")) 
			prior = PMF.read(args[3], gis);
		else { //just go with uniform prior
			prior = new PMF(gis, true);
		}
		
		Debug.println("minlat: "+minlat, Debug.INFO);
		Debug.println("maxlat: "+maxlat, Debug.INFO);
		Debug.println("minlon: "+minlon, Debug.INFO);
		Debug.println("maxlon: "+maxlon, Debug.INFO);
		Debug.println("latsteps: "+latsteps, Debug.INFO);
		Debug.println("lonsteps: "+lonsteps, Debug.INFO);
		gis.generateGoogleEarthOverlay("docs");
		if(prior != null)
			prior.writeImage("Prior");

		//now get the decay maps for each observer
		PMF[] decayMaps = new PMF[obs.length];
		for(int i = 0; i < decayMaps.length; i++)
			decayMaps[i] = PMF.read(args[4+i], gis, false);
		
		Distribution P0prior = new Distribution() {
			double mean = -35;
			double stddev = 5;
			public double drawFrom() { return new java.util.Random().nextGaussian()*stddev+mean; }
			public double pdf(double val) { return rses.math.MathUtil.getGaussPdf(val, mean, stddev); }
		};		
		
		initInfo inf = new initInfo();
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
		Debug.setVerbosityLevel(Debug.INFO);
		initInfo inf = initialize(args);
		//make sure all signals come from the one source
		for(int i = 1; i < inf.rss.length; i++)
			if(!inf.rss[i].sourceId.equals(inf.rss[0].sourceId)) throw new RuntimeException("Input data file "+args[0]+" has signals from multiple sources. Not allowed!");
		
		//build all the shortest path trees
		Debug.println("Building prior graph", Debug.INFO);
		inf.prior.cacheLogProbs(); //This saves a *lot* of Math.log calls
		//PathNode[][] priorgraph = inf.prior.getGraph();
		PathNode[][] priorgraph = inf.prior.getDistanceGraph();
		java.util.HashMap<String, rses.math.GraphNode> graph = new java.util.HashMap<String, rses.math.GraphNode>();
		for(int lati = 0; lati < priorgraph.length; lati++)
			for(int loni = 0; loni < priorgraph[0].length; loni++)
				if(priorgraph[lati][loni] != null)
					graph.put(priorgraph[lati][loni].getId(), priorgraph[lati][loni]);
		
		Debug.println("Building shortest path trees", Debug.INFO);
		PathNode[][] pathtrees = new PathNode[priorgraph.length][priorgraph[0].length];

		double[][][][] rawdistance = getRawDistanceFromPriorGraph(inf.prior); 
		             
		double[][][][] pathlengths = new double[priorgraph.length][priorgraph[0].length][priorgraph.length][priorgraph[0].length];

		//This one is a little bit odd. It is the number of steps (edges) along the shortest path
		//between a,b and c,d
		int[][][][] pathsteps = new int[priorgraph.length][priorgraph[0].length][priorgraph.length][priorgraph[0].length];

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
		Debug.println("Finished building shortest path trees, now calculating raw distances", Debug.INFO);
		
		
		//to get distances, we go through and replace the current edge weights (which are based on the -logprob 
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
		
		
		Debug.println("Finished calculating raw distances, now initializing walking speed distribution", Debug.INFO);
		
		//speed dist
		Distribution speeddist = new Distribution() {
			double stationarypct = 0.1;
			double mean = 2.0;
			double stddev = 0.5;

			
			public double drawFrom() 
			{
				if(Math.random() < stationarypct)
					return 0.0;
				
				//return Math.exp(new java.util.Random().nextGaussian()*stddev+mean);
				return Math.max(0.0, new java.util.Random().nextGaussian()*stddev+mean);
			}
			
			public double pdf(double val) 
			{ 
				if(val < 0) throw new IllegalArgumentException("values less than zero make no sense");

				if(val == 0.0)
					return this.stationarypct;

				return 0.9*MathUtil.getGaussPdf(val, mean, stddev);
			}
		};
		
		//attenuation
		double attenuation = 2.5;
		
		//noise
		double noise = 6;

		Debug.println("Finished all initialization, now estimating model from data", Debug.INFO);
		
		//new CosProbModel(inf.gis, inf.rss, inf.P0prior, inf.prior);
		
		//new CosProbModel(inf.gis, inf.rss, inf.P0prior, inf.prior, pathlengths, pathsteps, rawdistance, pathtrees, speeddist, attenuation, noise);
		new CosProbModel(inf.gis, inf.rss, inf.P0prior, inf.prior, null, null, pathlengths, pathtrees, speeddist, attenuation, noise);
		
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
