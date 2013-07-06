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

public final class GarnautSubmissionSim 
{
	
	//refine links until
	public static final double MAXLINKLENGTH = 4.5; //4.5 km max link length
	public static final double MINLINKLENGTH = 2; //2 km min link length
	private static GraphNode refineRailTree(GraphNode pathtree, HashMap pathtreegraph, HashMap graph, HashMap distances, HashMap traveltimes)
	{
		//first of all, get all the edges in the graph
		ArrayList edges = pathtree.getAllEdgesInGraph();
		
		//now get the set of edges in a new graph that is
		//created by splitting the edges in the old graph
		HashMap edges2 = new HashMap();
		

		for(int i = 0; i < edges.size(); i++)
		{
			GraphEdge edge = (GraphEdge) edges.get(i);
			int origid = edge.leadsfrom.getId();
			int destid = edge.leadsto.getId();
			String distkey = Math.min(origid, destid)+" "+Math.max(origid, destid);
			double dist = ((Double) distances.get(distkey)).doubleValue();
			Debug.println("Distance from "+origid+" -> "+destid+" is "+dist, Debug.INFO);
			if(dist < MAXLINKLENGTH) {
				edges2.put(edge.leadsfrom.getId()+" "+edge.leadsto.getId(), edge);
				continue;  //dont replace this edge
			}

			//its a link in the new rail network, so dont refine/replace it
			if(Util.getIndex(origid, railtzs) >= 0 && Util.getIndex(destid, railtzs) >= 0) {
				Debug.println("link is in new rail corridor, not refining it", Debug.INFO);
				edges2.put(edge.leadsfrom.getId()+" "+edge.leadsto.getId(), edge);
				continue;  //dont replace this edge
			}
			
			
			if(!Double.isNaN(getSpecialLinkCost(origid, destid))) {
				Debug.println("link is in special links list, not refining it", Debug.INFO);
				edges2.put(edge.leadsfrom.getId()+" "+edge.leadsto.getId(), edge);
				continue;  //dont replace this edge				
			}
			
			//else, we have to refine.
			GraphEdge[] newedges = replaceLink(edge, graph, distances);
			if(newedges == null)
			{
				Debug.println("Could not find a path replacement from "+origid+" -> "+destid, Debug.INFO);
				edges2.put(edge.leadsfrom.getId()+" "+edge.leadsto.getId(), edge); //dont replace this edge
				continue;
			}
			
			if(newedges[newedges.length-1].leadsto.getId() != edge.leadsto.getId())
				throw new IllegalStateException("New path does not end at old path end!!!!");
			
			String newedgesstr = newedges[0].leadsfrom.getId()+"";
			for(int j = 0; j < newedges.length; j++) {
				edges2.put(newedges[j].leadsfrom.getId()+" "+newedges[j].leadsto.getId(), newedges[j]);
				newedgesstr +=  " -> "+newedges[j].leadsto.getId();
			}
			Debug.println("Replacing edge "+origid+" -> "+destid+" with "+newedgesstr, Debug.INFO);
			
		}	
		
		
		//now build up a new graph from the new edges.
		HashMap nodes = new HashMap();
		Object[] keys = edges2.keySet().toArray();
		for(int i = 0; i < keys.length; i++)
		{
			GraphEdge edge = (GraphEdge) edges2.get(keys[i]);
			Integer from = new Integer(edge.leadsfrom.getId());
			Integer to = new Integer(edge.leadsto.getId());
			if(!nodes.containsKey(from))
				nodes.put(from, new GraphNode(from.intValue()));
			if(!nodes.containsKey(to))
				nodes.put(to, new GraphNode(to.intValue()));
			
			GraphNode fromnode = (GraphNode) nodes.get(from);
			GraphNode tonode = (GraphNode) nodes.get(to);
			//use traveltime weights from the original travel time
			//table, unless its a new link, in which case we use 
			//that link weight
			int i1 = Util.getIndex(fromnode.getId(), railtzs);
			int i2 = Util.getIndex(tonode.getId(), railtzs);
			//if its a new link, use the link cost
			if(i1 >= 0 && i2 >= 0 && Math.abs(i1-i2) == 1)
				fromnode.addEdge(tonode, getCost(new int[] {fromnode.getId(), tonode.getId()}));
			else if(!Double.isNaN(getSpecialLinkCost(fromnode.getId(), tonode.getId())))
				fromnode.addEdge(tonode, getSpecialLinkCost(fromnode.getId(), tonode.getId()));
			else //just look up the old traveltime cost
			{
				double dist = ((Double) distances.get(Math.min(from.intValue(), to.intValue())+" "+Math.max(from.intValue(), to.intValue()))).doubleValue();
				double traveltime = ((Double) traveltimes.get(fromnode.getId()+" "+tonode.getId())).doubleValue();
				fromnode.addEdge(tonode, traveltime);
			}
		}
		
		
		return (GraphNode) nodes.get(new Integer(pathtree.getId()));
	}
	
	
	
	
	private static GraphEdge[] replaceLink(GraphEdge toreplace, HashMap graph, HashMap distances)
	{
		//keep track of all the edges and nodes in the path, and make
		//sure we dont have either:
		//a) duplicate nodes
		//b) edges that we used to have but have replaced with multistep alternatives 
		HashMap seenedges = new HashMap();
		seenedges.put(toreplace.leadsfrom.getId()+" "+toreplace.leadsto.getId(), null);
		HashMap visitednodes = new HashMap();
		visitednodes.put(new Integer(toreplace.leadsfrom.getId()), null);
		visitednodes.put(new Integer(toreplace.leadsto.getId()), null);
		
		LinkedList edges = new LinkedList();
		edges.addFirst(toreplace);
		
		//just keep on replacing links until they cant be replaced anymore
		do
		{
			LinkedList newedges = new LinkedList();
			Iterator edgeit = edges.iterator();
			boolean newedgesadded = false;
			while(edgeit.hasNext())
			{
				GraphEdge edge = (GraphEdge) edgeit.next();
				GraphEdge[] replacement = replaceLink2step(edge.leadsfrom, edge.leadsto, graph, distances);
				if(replacement == null) {
					newedges.addLast(edge); //keep the old one
					Debug.println("No 2-step replacement found, keeping old link", Debug.INFO);
				}
				else
				{
					Debug.println("Proposed replacement is "+replacement[0].leadsfrom.getId()+" -> "+replacement[0].leadsto.getId()+" -> "+replacement[1].leadsto.getId(), Debug.INFO);
					if(!seenedges.containsKey(replacement[0].leadsfrom+" "+replacement[0].leadsto) &&
					   !seenedges.containsKey(replacement[1].leadsfrom+" "+replacement[1].leadsto) &&
					   !visitednodes.containsKey(new Integer(replacement[0].leadsto.getId()))) 
					{
						//if the second link ends at the final destination but this is
						//not the last link in the chain, then we can it
						if(replacement[1].leadsto.getId() == toreplace.leadsto.getId() &&
						   edgeit.hasNext())
						{
							Debug.println("link contains destination node but is not the lat link in the chain.... rejecting", Debug.INFO);
							newedges.addLast(edge); //keep old edge
							continue;
						}
						
						Debug.println("Accepting proposed replacement", Debug.INFO);
						
						newedges.addLast(replacement[0]);
						newedges.addLast(replacement[1]);
						seenedges.put(replacement[0].leadsfrom+" "+replacement[0].leadsto, null);
						seenedges.put(replacement[1].leadsfrom+" "+replacement[1].leadsto, null);
						visitednodes.put(new Integer(replacement[0].leadsto.getId()), null);
						visitednodes.put(new Integer(replacement[1].leadsto.getId()), null);
						newedgesadded = true;
						if(replacement[0].leadsto.getId() != replacement[1].leadsfrom.getId())
							throw new IllegalStateException("Something strange going on.... ");
						Debug.println("Adding new link: "+replacement[0].leadsfrom.getId()+" -> "+replacement[0].leadsto.getId()+" -> "+replacement[1].leadsto.getId(), Debug.INFO);
					}
					else {
						Debug.println("Already seen this node or this edge... rejecting link", Debug.INFO);
						newedges.addLast(edge);
					}
						
				}
			}
			if(newedgesadded) {
				edges = newedges;
				continue;
			}
			else
				break;
		}
		while(true);
		
		
		GraphEdge[] result = new GraphEdge[edges.size()];
		String linkstr = ""+toreplace.leadsfrom.getId();
		for(int i = 0; i < result.length; i++) {
 			result[i] = (GraphEdge) edges.get(i);
			linkstr += " -> "+result[i].leadsto.getId();
		}
		Debug.println(linkstr, Debug.IMPORTANT);
		return result;
	}
	
	
	
	//find the best 2 step link from orig to destid
	private static GraphEdge[] replaceLink2step(GraphNode ptorig, GraphNode ptdest, HashMap graph, HashMap distances)
	{
		double best = Double.MAX_VALUE;
		GraphEdge[] bestpath = null; 
		
		//we have to get the nodes from the full graph, as the path tree
		//is heavily pruned
		GraphNode orig = (GraphNode) graph.get(new Integer(ptorig.getId()));
		GraphNode dest = (GraphNode) graph.get(new Integer(ptdest.getId()));

		int origid = orig.getId();
		int destid = dest.getId();
		String distkey = Math.min(origid, destid)+" "+Math.max(origid, destid);
		double dist = ((Double) distances.get(distkey)).doubleValue();
		double presplitdist = dist;
		if(dist < MAXLINKLENGTH) {
			Debug.println("Not replacing link "+origid+" -> "+destid+" because its short enough ("+dist+"km)", Debug.INFO);
			return null;  //dont replace this edge
		}

		
		Debug.println("trying replacement for link: "+orig.getId()+" -> "+dest.getId(), Debug.INFO);
		
		Iterator edgeit1 = orig.getEdges().iterator();
		while(edgeit1.hasNext())
		{
			GraphEdge edge = (GraphEdge) edgeit1.next();
			double weight = edge.weight;
			if(weight > best)
				continue;
			
			//the first edge cannot end at the destination... this is
			//what we are replacing
			if(edge.leadsto.getId() == dest.getId())
				continue;
			
			//check distance
			String key = Math.min(orig.getId(), edge.leadsto.getId())+" "+Math.max(orig.getId(), edge.leadsto.getId());
			dist = ((Double) distances.get(key)).doubleValue();
			//SPECIAL CASE HERE, IF 0.35*dist is less than MINLINKLENGTH
			//then we allow links down to that size. This is because
			//when splitting a 4km link, say, a 'natural' intermediate
			//link may be slightly less than 2 km.
			if(dist < Math.min(Math.max(MINLINKLENGTH, 0.35*presplitdist), 0.35*presplitdist))
				continue;
			
			//ok, so edge is greater than MINLINKLENGTH and its traveltime
			//does not exceed the best complete path seen so far, so we 
			//check the next link
			Iterator edgeit2 = edge.leadsto.getEdges().iterator();
			while(edgeit2.hasNext())
			{
				GraphEdge edge2 = (GraphEdge) edgeit2.next();
				if(edge2.leadsto.getId() != dest.getId())
					continue; //2nd link must end at destination
					
				//and it must be better than the best seen so far
				double weight2 = edge2.weight;
				if(weight2+weight > best)
					continue;
				
				//and it must be greater than MINLINKLENGTH is distance
				key = Math.min(edge.leadsto.getId(), dest.getId())+" "+Math.max(edge.leadsto.getId(), dest.getId());
				Double ddist = (Double) distances.get(key);
				if(ddist == null)
					throw new RuntimeException("No distance mapping for key: "+key);
				dist = ddist.doubleValue();
				if(dist < Math.min(Math.max(MINLINKLENGTH, 0.35*presplitdist), 0.35*presplitdist))
					continue;
				
				//ok, we found a new best path to the destination. 
				//Remember it.
				best = weight2+weight;
				bestpath = new GraphEdge[] {edge, edge2};
			}
		}
				
		return bestpath;
	}
	
	
	//assumes that all links are the same in both directions
	private static HashMap costmap = null;
	private static double getCost(int[] newrailstopsvisited)
	{
		if(costmap == null) 
		{
			costmap = new HashMap();
			for(int i = 0; i < railtzs.length-1; i++)
			{
				String key1 = railtzs[i]+" "+railtzs[i+1];
				String key2 = railtzs[i+1]+" "+railtzs[i];
				double cost = costs[i];
				costmap.put(key1, new Double(cost));
				costmap.put(key2, new Double(cost));
			}
		}
		
		double cost = 0.0;
		for(int i = 0; i < newrailstopsvisited.length-1; i++)
		{
			String key = newrailstopsvisited[i]+" "+newrailstopsvisited[i+1];
			Double dcost = (Double) costmap.get(key);
			if(dcost == null)
				throw new RuntimeException("No mapping for link "+key);
			cost += (dcost).doubleValue();
		}
		return cost;
	}
	
	
	private static double getSpecialLinkCost(int from, int to)
	{
		for(int i = 0; i < speciallinks.length; i++)
		{
			if(speciallinks[i][0] == from && speciallinks[i][1] == to)
				return specialcosts[i];
		}
		return Double.NaN;
	}
	
	//private static int[] railtzs = new int[] {830, 451, 430, 432};
	//private static double[] costs = new double[] {10.18, 9.5, 8.64};
	//private static final double waitauxtime = 6.0;
	private static int[][] speciallinks = new int[][] {{830,784},{784,830},{784,787},{787,784}}; //special links that shouldnt be modified
	private static double[] specialcosts = new double[] {15.0,15.0,16.0,16.0}; //and the costs of those links
	private static int[] railtzs = new int[] {750, 624, 754, 753, 755, 452, 830, 479, 487, 489, 186, 184, 29, 7, 14, 838};
	private static double[] costs = new double[] {8, 7, 6, 7, 7, 11, 10, 7, 5, 8, 6, 7, 6, 5, 5};
	private static final double waitauxtime = 4.0;
	
	private static void getNewRailTripTimes(int origid, int[] newrailstopsvisited, GraphNode pathtree, HashMap triptimes, HashMap newtriptimes)
	{
		int curid = pathtree.getId();
		
		String key = origid+" "+curid;

		double basetriptime = ((Double) triptimes.get(key)).doubleValue();
		double newtriptime = Double.NaN;
		
		Debug.println("In getNewRailTripTimes(), with origid == "+origid+" curid == "+curid, Debug.IMPORTANT);
		//Debug.println("rail stops visited in new link are: "+Util.arrayToString(newrailstopsvisited), Debug.IMPORTANT);
		
		

		if(newrailstopsvisited.length == 0)
		{
			//we havent used the new link, so we just keep the old time.
			//do nothing.
			newtriptime = basetriptime;
			Debug.println("Didnt go through link, leaving base trip time at "+basetriptime, Debug.IMPORTANT);
		}
		else if(Util.getIndex(curid, railtzs) == -1)
		{
			//we have passed through the link and come out
			//the other side.
			double tostartoflink;
			if(Util.getIndex(origid, railtzs) >= 0)
				tostartoflink = 0.0;
			else {
				key = origid+" "+newrailstopsvisited[0];
				tostartoflink = ((Double) triptimes.get(key)).doubleValue();
			}
			double throughlink = getCost(newrailstopsvisited);

			
			//entry/exit and wait time (for the next leg) is already 
			//counted at the
			//far end in the 'fromendoflink' calculation.
			//In the tostartoflink calculation, station exit time
			//is already counted, and we dont add anything more, 
			//which means that basically, we are assuming that
			//wait time on change is the same as exit time. seems
			//reasonable. 
			//if(newrailstopsvisited.length > 2)
			throughlink -= (newrailstopsvisited.length-1)*waitauxtime;
			
			key = newrailstopsvisited[newrailstopsvisited.length-1]+" "+curid;
			double fromendoflink = ((Double) triptimes.get(key)).doubleValue();
			newtriptime = tostartoflink+throughlink+fromendoflink;
			Debug.println("Passed through new rail link, new trip time is "+newtriptime, Debug.IMPORTANT);
			Debug.println("Thats "+tostartoflink+" to start of link, "+throughlink+" through the link, and "+fromendoflink+" from the end of the link", Debug.IMPORTANT);
		}
		else //we are currently in the link (or on the far end)!
		{
			double tostartoflink;
			if(Util.getIndex(origid, railtzs) >= 0)
				tostartoflink = 0.0;
			else {
				key = origid+" "+newrailstopsvisited[0];
				tostartoflink = ((Double) triptimes.get(key)).doubleValue();
			}
			double throughlink = getCost(newrailstopsvisited);
			
			//if(newrailstopsvisited.length > 2) //subtract unnecessary boarding/wait time counted
			//see previous case. same here. All we need to do is add exit
			//time at the final station
				throughlink -= (newrailstopsvisited.length-1)*waitauxtime;
				throughlink += 4; //4 minutes exit/walk time
			
			
			newtriptime = tostartoflink+throughlink;
			Debug.println("Currently in new rail link, new trip time is "+newtriptime, Debug.IMPORTANT);
			Debug.println("Thats "+tostartoflink+" to start of link, and "+throughlink+" through the link", Debug.IMPORTANT);
		}

		
		double saving = basetriptime-newtriptime;
		Debug.println("Traveltime saving from "+origid+" to "+curid+" is "+saving, Debug.IMPORTANT);
		
		key = origid+" "+curid;
		Object old = newtriptimes.put(key, new Double(newtriptime));
		if(old != null)
			throw new IllegalStateException("ERROR!! duplicate entry for trip time "+key);
		
		
		//ok, done, now do all edges leading from the
		//current node
		Iterator edgeit = pathtree.getEdges().iterator();
		while(edgeit.hasNext())
		{
			GraphEdge edge = (GraphEdge) edgeit.next();
			int nextid = edge.leadsto.getId();
			boolean curinlink = false;
			boolean nextinlink = false;
			for(int i = 0; i < railtzs.length; i++)
				if(curid == railtzs[i]) curinlink = true;
			for(int i = 0; i < railtzs.length; i++)
				if(nextid == railtzs[i]) nextinlink = true;
			
			if(curinlink && nextinlink) 
			{
				int[] newrail = new int[newrailstopsvisited.length+1];
				System.arraycopy(newrailstopsvisited, 0, newrail, 0, newrailstopsvisited.length);
				newrail[newrail.length-1] = curid;
				getNewRailTripTimes(origid, newrail, edge.leadsto, triptimes, newtriptimes);
			}
			else if(curinlink && !nextinlink && newrailstopsvisited.length > 0) 
			{
				//cur is the last stop in the link (i.e. we are about to
				//exit the link)
				int[] newrail = new int[newrailstopsvisited.length+1];
				System.arraycopy(newrailstopsvisited, 0, newrail, 0, newrailstopsvisited.length);
				newrail[newrail.length-1] = curid;
				getNewRailTripTimes(origid, newrail, edge.leadsto, triptimes, newtriptimes);				
			}
			else
				getNewRailTripTimes(origid, newrailstopsvisited, edge.leadsto, triptimes, newtriptimes);
		}
	}
	
	
	private static HashMap doRailSim(HashMap triptimes, HashMap distances) throws Exception
	{
		HashMap newtriptimes = new HashMap();
		
		//read in the reduced network graph (coarse) 
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
			/*Debug.println("Got coarse shortest path tree from node "+tznode.getId()+", now printing it:", Debug.IMPORTANT);
			GraphUtil.printGraph(pathtree, System.out);*/
			
			//now do a refine from the start node
			Debug.println("Refining shortest path tree....", Debug.IMPORTANT);
			GraphNode newgraph = refineRailTree(pathtree, GraphUtil.getGraph(pathtree), railgraph, distances, triptimes);
			/*Debug.println("Got refined graph from node "+tznode.getId()+", now printing it:", Debug.IMPORTANT);
			GraphUtil.printGraph(newgraph, System.out);*/
			
			//now get the shortest path tree from that
			pathtree = GraphUtil.getShortestPathTree(tznode.getId(), GraphUtil.getGraph(newgraph));
			/*Debug.println("Got new shortest path tree from refined graph, now printing it:", Debug.IMPORTANT);
			GraphUtil.printGraph(pathtree, System.out);*/
			
			//now work out the trip times to each node
			getNewRailTripTimes(tznode.getId(), new int[0], pathtree, triptimes, newtriptimes);
			
			//DEBUG DEBUG press enter to continue
			/*System.out.println("Hit enter to continue");
			new BufferedReader(new InputStreamReader(System.in)).readLine();*/
		}
		
		return newtriptimes;
	}
	
	
	
	private static void getNewRoadTripTimes(int startid, GraphNode pathtree, HashMap triptimes, HashMap newtriptimes)
	{
		throw new UnsupportedOperationException("Not implemented yet");
	}
	

	private static HashMap doRoadSim(HashMap triptimes, HashMap distances) throws Exception
	{
		HashMap newtriptimes = new HashMap();
		
		//read in the reduced network graph
		HashMap roadgraph = GraphUtil.readGraph("graph.withparramattaeppingroad");
		
		java.util.Iterator tzit = roadgraph.keySet().iterator();
		while(tzit.hasNext())
		{
			GraphNode tznode =  (GraphNode) roadgraph.get(tzit.next());
			Debug.println("Getting new traveltimes from node "+tznode.getId(), Debug.IMPORTANT);
			
			//find the shortest path tree for this node
			GraphNode pathtree = GraphUtil.getShortestPathTree(tznode.getId(), roadgraph);
			/*Debug.println("Got coarse shortest path tree from node "+tznode.getId()+", now printing it:", Debug.IMPORTANT);
			GraphUtil.printGraph(pathtree, System.out);*/
			
			
			//now work out the trip times to each node
			getNewRoadTripTimes(tznode.getId(), pathtree, triptimes, newtriptimes);
			
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
		BufferedReader rdr = new BufferedReader(new FileReader("skims.dat"));
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
				time = new Double(bits[3]);
			result.put(origtz+" "+desttz, time);
			
			line = rdr.readLine();
		}
		return result;
	}
	
	private static String graphname = "graph.rail";	
	public static void main(String[] args) throws Exception
	{
		boolean isroad = false;

		if(args.length > 0)
		{	
			if(args[0].equalsIgnoreCase("road"))
				isroad = true;
			else if(args[0].equalsIgnoreCase("test1"))
			{
				test1();
				return;
			}
			else
				graphname = args[0];
		}
		else
			throw new RuntimeException("No initial graph specified");
			
		//read in the O/D JTW pairs and counts.
		
		//read in the full trip matrix
		HashMap traveltimes = getTravelTimes(isroad);
		Debug.println("Got traveltime matrix", Debug.IMPORTANT);
		
		//calculate the distance matrix
		HashMap distances = getDistanceMatrix();
		Debug.println("Got distance matrix", Debug.IMPORTANT);
		
		HashMap newtriptimes = null;
		if(isroad)
			newtriptimes = doRoadSim(traveltimes, distances);
		else
			newtriptimes = doRailSim(traveltimes, distances);

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
		
		//now do a single refine from the start node
		GraphNode newgraph = refineRailTree(pathtree, GraphUtil.getGraph(pathtree), fullgraph, distances, traveltimes);
		
		//create the shortest path spanning tree
		GraphNode newpathtree = GraphUtil.getShortestPathTree(607, GraphUtil.getGraph(newgraph));
		
		//now print the resulting path tree
		GraphUtil.printGraph(newpathtree, System.out);
		
	}
	
	

}
