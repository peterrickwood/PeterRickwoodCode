package rses.apps;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import rses.Debug;
import rses.math.GraphEdge;
import rses.math.GraphNode;
import rses.math.GraphUtil;
import rses.math.MathUtil;
import rses.util.Util;

public final class GarnautSubmissionSim2 
{
	
	
	
	private static void getTripTimes(int origid, double sofar, GraphNode pathtree, HashMap triptimes, HashMap newtriptimes)
	{
		
		String key = origid+" "+pathtree.getId();
		double base = ((Double) triptimes.get(key)).doubleValue();
		
		if(sofar > base) {
			//we are traversing a shortest path tree, so sofar should always
			//be shorter than (or equal to) the base travel cost, because
			//otherwise it shouldnt be in the tree!
			throw new RuntimeException("shortest path is not shortest path for key "+key+" old was "+base+" new is "+sofar+" printing shortest path tree");
		}
		else {
			Object old = newtriptimes.put(key, new Double(sofar));
			if(old != null)
				throw new RuntimeException("already had a mapping for key: "+key);
		}
		
		//now call recursively for all edges from this node
		Iterator edgeit = pathtree.getEdges().iterator();
		while(edgeit.hasNext())
		{
			GraphEdge edge = (GraphEdge) edgeit.next();
			double cost = edge.weight;
			GraphNode leadsto = edge.leadsto;
			getTripTimes(origid, cost, leadsto, triptimes, newtriptimes);
		}
		
		
	}
	
	
	private static HashMap doSim(HashMap triptimes) throws Exception
	{
		HashMap newtriptimes = new HashMap();
		
		//read in the whole network graph  
		HashMap railgraph = GraphUtil.readGraph(graphname);
		
		
		java.util.Iterator tzit = railgraph.keySet().iterator();
		/* FOR TESTING List l = new LinkedList();
		l.add(new Integer(858));
		java.util.Iterator tzit = l.iterator();*/
		while(tzit.hasNext())
		{
			GraphNode tznode =  (GraphNode) railgraph.get(tzit.next());
			Debug.println("Getting new traveltimes from node "+tznode.getId(), Debug.IMPORTANT);
			
			//find the shortest path tree for this node
			GraphNode pathtree = GraphUtil.getShortestPathTree(tznode.getId(), railgraph);
			//GraphUtil.printGraph(pathtree, System.out);			
			
			//now work out the trip times to each node by just traversing the tree
			try {
			getTripTimes(tznode.getId(), 0.0, pathtree, triptimes, newtriptimes);
			}
			catch(Exception e)
			{
				GraphUtil.printGraph(pathtree, System.err);
				throw e;
			}
			//DEBUG DEBUG press enter to continue
			/*System.out.println("Hit enter to continue");
			new BufferedReader(new InputStreamReader(System.in)).readLine();*/
		}
		
		return newtriptimes;
	}
	
	
	

	
	public static HashMap getDistanceMatrix() throws Exception
	{
		HashMap result = new HashMap();
		BufferedReader rdr = new BufferedReader(new FileReader("tz_numlatlon.txt"));
		String line = rdr.readLine();
		HashMap centroids = new HashMap();
		int tzcount = 0;
		while(line != null)
		{
			String[] bits = Util.getWords(line);
			Integer tz = new Integer(bits[0]);
			double lat = Double.parseDouble(bits[1]);
			double lon = Double.parseDouble(bits[2]);
			centroids.put(tz, new double[] {lat,lon});
			tzcount++;
			line = rdr.readLine();
		}
		
		
		//get the list of travelzones
		int[] tzs = new int[tzcount];
		tzcount = 0;
		Iterator keyit = centroids.keySet().iterator();
		while(keyit.hasNext()) 
			tzs[tzcount++] = ((Integer) keyit.next()).intValue();
		Arrays.sort(tzs);
		
		//now calculate distance matrix
		for(int i = 0; i < tzs.length; i++)
		{
			double[] centroid1 = (double[]) centroids.get(new Integer(tzs[i]));
			for(int j = i+1; j < tzs.length; j++)
			{
				double[] centroid2 = (double[]) centroids.get(new Integer(tzs[j]));
				double dist = MathUtil.getDistanceBetweenPointsOnEarth(centroid1[0], centroid1[1], centroid2[0], centroid2[1]);
				result.put(tzs[i]+" "+tzs[j], new Double(dist/1000.0));
			}
			Debug.println("Calculated all distances from "+tzs[i], Debug.IMPORTANT);
		}
			
		return result;
	}
	
	
	
	public static HashMap getTravelTimes(boolean isroad) throws Exception
	{
		HashMap result = new HashMap();
		BufferedReader rdr;
		if(!isroad) rdr = new BufferedReader(new FileReader("skims.rail.dat"));
		else 
			rdr = new BufferedReader(new FileReader("skims.road.dat"));
		String line = rdr.readLine();
		line = rdr.readLine(); //skip first line
		while(line != null)
		{
			String[] bits = Util.getWords(line);
			if(bits.length != 8)
				break;
			String origtz = bits[0];
			String desttz = bits[1];
			Double time = new Double(bits[6]);
			if(isroad)
				time = new Double(bits[2]);
			result.put(origtz+" "+desttz, time);
			
			line = rdr.readLine();
		}
		return result;
	}
	
	private static String graphname = "graph.rail";	
	public static void main(String[] args) throws Exception
	{
		boolean isroad = true;
		Debug.println("In "+(isroad? " ROAD " : " RAIL ")+" mode", Debug.IMPORTANT);

		graphname = args[0];
			
		//read in the O/D JTW pairs and counts.
		
		//read in the full trip matrix
		HashMap traveltimes = getTravelTimes(isroad);
		Debug.println("Got traveltime matrix", Debug.IMPORTANT);
		
		//calculate the distance matrix
		//HashMap distances = getDistanceMatrix();
		//Debug.println("Got distance matrix", Debug.IMPORTANT);
		
		HashMap newtriptimes = null;

		newtriptimes = doSim(traveltimes);

		//now print out the new trip times
		Iterator keyit = newtriptimes.keySet().iterator();
		while(keyit.hasNext())
		{
			Object key = keyit.next();
			double newtime = ((Double) newtriptimes.get(key)).doubleValue();
			double oldtime = ((Double) traveltimes.get(key)).doubleValue();
			newtime = Math.min(newtime, oldtime);
			System.out.println("NEW_VS_OLD_TRIPTIME "+key+" "+newtime+" "+oldtime+" "+(oldtime-newtime));
		}
		
	}
	
	public static void test1() throws Exception
	{
		Debug.setVerbosityLevel(Debug.INFO);
		//read in the O/D JTW pairs and counts.
		
		//read in the full trip matrix
		HashMap traveltimes = getTravelTimes(false);
		
		//calculate the distance matrix
		HashMap distances = getDistanceMatrix();
		Debug.println("Got distance matrix", Debug.IMPORTANT);
		
		//read in the base graph
		HashMap fullgraph = GraphUtil.readGraph("graph.rail");
		
		//TESTING
		//get the shortest path tree 
		GraphNode pathtree = GraphUtil.getShortestPathTree(607, fullgraph);
		//print out the shortest path tree
		GraphUtil.printGraph(pathtree, System.out);

		
		
	}
	
	

}
