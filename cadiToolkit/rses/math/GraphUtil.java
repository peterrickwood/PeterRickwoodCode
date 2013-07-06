package rses.math;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import rses.Debug;
import rses.util.Heap;
import rses.util.HeapElement;
import rses.util.Util;




public final class GraphUtil
{
	private GraphUtil()
	{	throw new UnsupportedOperationException("Cant instantiate this class");}
	
	
	
	public static java.util.HashMap<String, GraphNode> readGraph(String filename) throws java.io.IOException
	{
		java.util.HashMap<String, GraphNode> nodes = new java.util.HashMap<String, GraphNode>();
		
		
		//each line in the file must be in the format
		//
		//NODEID    NODEID weight  NODEID weight NODEID weight ...
		BufferedReader rdr = new BufferedReader(new java.io.FileReader(filename));
		ArrayList<String[]> linesarr = new ArrayList<String[]>();
		
		String line = rdr.readLine();
		while(line != null) {
			linesarr.add(Util.getWords(line));
			line = rdr.readLine();
		}
				
		//create the Vertices first
		for(String[] linewords : linesarr)
		{
			String id = linewords[0];
			GraphNode n = new GraphNode(id);
			nodes.put(id, n);
		}
		
		//now add the edges
		for(String[] linewords : linesarr)
		{
			String origid = linewords[0];
			GraphNode orignode = (GraphNode) nodes.get(origid);
			
			for(int j = 1; j < linewords.length; j+=2)
			{
				String destid = linewords[j];
				GraphNode destnode = (GraphNode) nodes.get(destid);
				double weight = Double.parseDouble(linewords[j+1]);
				orignode.addEdge(destnode, weight);
			}
		}
		
		
		return nodes;
		
	}
	
	
	


	
	
	/** Get *all* paths from the path tree, along with their lengths.
	 * 
	 * The returned Map maps a node id to the <i>last</i> link in the path to that node.
	 * The 'info' field of that last edge will be initialized to a 2-element array,
	 * where the first is the OLD 'info' value that was there, and the second is a double with the 
	 * total path length.
	 * 
	 * @param pathtree
	 * @return
	 */
	public static java.util.Map<String, GraphEdge> getAllPathsFromPathTree(GraphNode pathtree)
	{
		java.util.Map<String, GraphEdge> paths = new java.util.HashMap<String, GraphEdge>();
		getAllPathsFromPathTree(pathtree, paths, 0.0);		
		return paths;
	}

	private static void getAllPathsFromPathTree(GraphNode pathtree, java.util.Map<String, GraphEdge> paths, double distance)
	{
		for(GraphEdge e: pathtree.getEdges())
		{
			//is we are suspicious, we check to see that we can't reach any nodes that we already have a path to
			//This should be impossible -- if it is a tree then there is only 1 path to each node.
			if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
				if(paths.containsKey(e.leadsto.getId())) throw new RuntimeException("Traversing what I thought was a path tree, but isnt, because it has loops");
			
			//remember each path by putting the last link in
			paths.put(e.leadsto.getId(), new GraphEdge(e.leadsfrom, e.leadsto, e.weight, new Object[] {e.info, distance+e.weight}));
			
			//get all downstream paths
			getAllPathsFromPathTree(e.leadsto, paths, distance+e.weight);
		}
	}
	
	
	public static java.util.List<GraphNode> getShortestPathNodesFromPathTree(GraphNode pathtree, String destid)
	{
		java.util.ArrayList<GraphNode> nodes = new java.util.ArrayList<GraphNode>();
		java.util.List<GraphEdge> edges = getShortestPathFromPathTree(pathtree, destid);
		
		if(edges.get(0).leadsfrom != pathtree) throw new RuntimeException("Internal Error");
		
		GraphNode current = pathtree;
		nodes.add(current);
		for(GraphEdge edge : edges) 
		{
			if(current != edge.leadsfrom) throw new RuntimeException("Internal Error 2");
			nodes.add(edge.leadsto);
			current = edge.leadsto;
		}
		return nodes;
	}
	
	public static java.util.List<GraphEdge> getShortestPathFromPathTree(GraphNode pathtree, String destid)
	{
		java.util.ArrayList<GraphEdge> edges = new java.util.ArrayList<GraphEdge>();
		
		for(GraphEdge edge : pathtree.getEdges()) 
		{
			edges.add(edge);
			if(getShortestPathFromPathTree(destid, edges))
				return edges;
			edges.remove(edges.size()-1);
		}
		
		//couldnt find any path!
		GraphUtil.printGraph(pathtree, System.err);
		throw new RuntimeException("Couldnt find any path to destination!");
	}
	
	private static boolean getShortestPathFromPathTree(String targetid, java.util.List<GraphEdge> edgessofar)
	{
		int linkssofar = edgessofar.size();
		GraphNode current = edgessofar.get(linkssofar-1).leadsto;
		
		if(current.getId().equalsIgnoreCase(targetid))
			return true;
		else if(current.getEdges().size() == 0)
			return false;
		
		for(GraphEdge edge : current.getEdges())
		{
			//for each edge, we add that edge and try to find a path to the destination
			edgessofar.add(edge);
			if(getShortestPathFromPathTree(targetid, edgessofar))
				return true;
			
			//if we dont find the destination along that edge, we remove the edge and try the next one
			edgessofar.remove(linkssofar);
		}
		
		//ok, so we tried along all edges and we didnt find the destination...
		return false;
	}

	
	
	
	/** Traverse a path tree and find the path length from the root node to every other node in
	 *  the tree.
	 *  
	 *  Note: No pathlength is specified for the root node (i.e. pathtree). That is, the distance from the
	 *  root of the tree to itself it unspecified in the returned Map.
	 *  
	 *  If 'pathtree' is in fact not a path tree, this method with throw an exception.
	 * 
	 * @param pathtree  The root node in a path tree
	 * @return a map that maps the destination node id to its distance from the root node (i.e. pathtree)
	 */
	public static java.util.Map<String, Double> getPathLengthsFromPathTree(GraphNode pathtree)
	{
		java.util.HashMap<String, Double> result = new HashMap<String, Double>();
		java.util.HashMap<String, Object> visited = new HashMap<String, Object>();
		visited.put(pathtree.getId(), null);
		result.put(pathtree.getId(), 0.0);
		
		for(GraphEdge edge : pathtree.getEdges()) 
			getPathLengthsFromPathTree(pathtree, edge.leadsto, edge.weight, result, visited);
		
		return result;
		
	}
	
	private static void getPathLengthsFromPathTree(GraphNode root, GraphNode current, double currentdist, java.util.Map<String, Double> res, java.util.Map<String, Object> visited)
	{
		//make sure we are in an actual tree (it should be impossible to visit an already visited node)
		if(visited.containsKey(current.getId())) throw new RuntimeException("Already visitied node "+current.getId()+" is being traversed a second time -- this is not a tree, but it must be!");
		
		//remember we have visited this node 
		visited.put(current.getId(), null);
		
		//remember the path length to it
		res.put(current.getId(), currentdist);
		
		//go and visit all the children
		for(GraphEdge edge : current.getEdges()) 
			getPathLengthsFromPathTree(root, edge.leadsto, currentdist+edge.weight, res, visited);			
		
	}

	
	public static double Astar(GraphNode startnode, GraphNode endnode, 
			rses.math.DistanceFunction<GraphNode> distanceFunc, 
			List<rses.math.GraphEdge> returnedPath)
	{		
		assert false : "This method is not tested yet. AT ALL!!! Test it before you use it! Thoroughly";
		
		assert returnedPath.size() == 0 : "returnedPath parameter must be an empty list, because we use it to return the shortest path";
		
		/* OK, we wont bother checking ALL edges in the graph, but lets at least check those
		 * at the start and end nodes to make sure none of them are negative
		 */
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
		{
			for(GraphEdge e: startnode.getEdges())
				assert e.weight >= 0 : "Negative edge weight..... this will BREAK this algorithm";
			for(GraphEdge e: endnode.getEdges())
				assert e.weight >= 0 : "Negative edge weight..... this will BREAK this algorithm";
		}
		
		// The set of nodes already evaluated.
		java.util.Set<GraphNode> closedset = new java.util.HashSet<GraphNode>();

		//camefrom[X] gives the previous node along the shortest path to X
		java.util.HashMap<GraphNode, GraphNode> camefrom = new java.util.HashMap<GraphNode, GraphNode>();
    
		// Cost from start along best known path.
		java.util.HashMap<GraphNode, Double> gscore = new java.util.HashMap<GraphNode, Double>();
		
	    //Estimated total cost to goal through any node
		//this is the lowest cost from from the start node plus our estimate to the end (which 
		//should never overestimate the distance).
		java.util.HashMap<GraphNode, Double> fscore = new java.util.HashMap<GraphNode, Double>();

	    // The set of tentative nodes to be evaluated, initially containing the start node
		//they are put in a queue with the estimated cost to destination
		rses.util.Heap<GraphNode> openset = new rses.util.Heap<GraphNode>(1024);
		java.util.Set<GraphNode> opensetmembers = new java.util.HashSet<GraphNode>();
		
		gscore.put(startnode, 0.0); 
		fscore.put(startnode, distanceFunc.getDistanceBetween(startnode, endnode));
		
		openset.insert(new rses.util.HeapElement<GraphNode>(fscore.get(startnode), startnode));
		opensetmembers.add(startnode);
		
		while(!openset.isEmpty())
		{
			//remove next node from front of open set
			GraphNode current = openset.extractMin().getObject();
			opensetmembers.remove(current);
	
			//if we are at the goal, quit and return the path and pathlength
			if(current == endnode)
			{
				GraphNode tmp = current;
				double pathlen = 0.0;
				while(camefrom.containsKey(tmp))
				{
					GraphNode prev = camefrom.get(tmp);
					GraphEdge edge  = null;
					for(GraphEdge e : prev.getEdges()) {
						if(e.leadsto == tmp) {
							edge = e; break;
						}
					}
					assert edge != null : "Apparently there is NO link from prev to current node. Should be impossible because we did find a path!!!! I mustve stuffed up somewhere when coding this";
					returnedPath.add(0, edge);
					pathlen += edge.weight;	
					tmp = prev;
				}
				assert returnedPath.get(0).leadsfrom == startnode: "Path doesnt start at start node! Could not reconstruct path between start and end node... which should be impossible, since we found a path";
				assert returnedPath.get(returnedPath.size()-1).leadsto == endnode: "Path doesnt end at end node! Could not reconstruct path between start and end node... which should be impossible, since we found a path";	
				return pathlen;
			}

			//add current to the closed set so we dont visit it again
			closedset.add(current);
			
			//for each node reachable from current
			for(GraphEdge e : current.getEdges())
			{
				//if neighbour is in closed set we dont bother
				if(closedset.contains(e.leadsto))
					continue;
				
				//tentative cost from start to this node along best known path
				//This is the currently known best known path to 'current', plus the edge distance 
				double gguess = gscore.get(current) + e.weight;
				
				//if the neighbour is not in the open set, or we found a better path
				if(!opensetmembers.contains(e.leadsto) || gguess <= gscore.get(e.leadsto))
				{
					camefrom.put(e.leadsto, current);
					gscore.put(e.leadsto, gguess);
					fscore.put(e.leadsto, gguess + distanceFunc.getDistanceBetween(e.leadsto, endnode));
					if(!opensetmembers.contains(e.leadsto)) {
						openset.insert(new HeapElement<GraphNode>(fscore.get(e.leadsto), e.leadsto));
						opensetmembers.add(e.leadsto);
					}
				}
				
			}
		}
		
		throw new RuntimeException("No path found to goal node");
	}
	
	
	
	/** Djikstra's algorithm to construct a shortest path tree, but allow an early stop
	 * 
	 * 
	 * TODO This is just a copy of the main djikstra implementation because I have not had time
	 * to test these changes, and I dont want to break the main Djikstra, so safer to copy
	 * for now.
	 *  
	 * @param startnodeid
	 * @param graph The shortest path tree
	 * @return
	 */
	public static GraphNode getShortestPathTree(GraphNode start, String targetid)
	{
		//initial setup. initialize shortest path tree to just
		//the start node, and set all distances to 0.0
		
		//previous is the tree that has been built. It contains nodes that 
		//we have found the shortest path to, as well as nodes we have a path to 
		//but which are not yet known to be optimal
		HashMap<String, String> previous = new HashMap<String, String>();
		
		//for fast lookup, a map from nodeid to the (so far) shortest distance to
		//that node
		HashMap<String, Double> distances = new HashMap<String, Double>();

		//the 'candidates' for addition to the shortest path tree, ordered 
		//by distance. Nodes already in the shortest path tree are NOT in this.
		Heap<GraphNode> distQ = new Heap<GraphNode>(4096);
		
		//we ALSO need to be able to look up all the elements in the distQ by their nodeid's,
		//so that we can reorder the queue when we find shorter distances than the current
		//best known distances
		java.util.Map<String, HeapElement<GraphNode>> heapelems = new HashMap<String, HeapElement<GraphNode>>();
		
		//initialize distances and the shortest path tree (previous) to be a 
		//graph will all nodes but no links.
		distances.put(start.getId(), 0.0);
		previous.put(start.getId(), null);

		//build the heap... at the start this is just the start node
		HeapElement<GraphNode> dhe = new HeapElement<GraphNode>(0.0, start);
		heapelems.put(start.getId(), dhe);
		distQ.insert(dhe);
		
		//ok, we have our heap, lets go.
		
		
		//ok, now do the algorithm proper
		while(!distQ.isEmpty()) 
		{
			//get closest node not already in shortest path tree
			HeapElement<GraphNode> he = distQ.extractMin();
			
			//special case. If we pop off the target node, we stop
			if(he.getObject().getId().equals(targetid)) 
				break;

			//Debug.println("Added node "+he.getObject()+" to the shortest path tree at cost "+he.getHeapValue(), Debug.INFO);
			
			//go through all the vertices reachable from the mincost vertex,
			//and update their distances
			GraphNode closest = he.getObject();
			double basecost = he.getHeapValue();
			List<GraphEdge> links = closest.getEdges();
			//Debug.println("Minimum distance to this node that we are adding is "+basecost, Debug.INFO);
			for(GraphEdge edge : links) 
			{
				GraphNode dest = edge.leadsto;
				String destid = dest.getId();
				//Debug.println("    Looking at neighbouring node "+destid+" with edge weight "+edge.weight, Debug.INFO);
				double cost = edge.weight+basecost;
				
				//if cost is better than our currently best known cost,
				//then we update
				if(distances.get(destid) == null || cost < distances.get(destid))
				{
					//if we have no distance to this node, we havent seen it before, so we 
					//need to add it to the distance queue, and all the other bookkeeping objects
					if(distances.get(destid) == null) {
						distances.put(destid, Double.POSITIVE_INFINITY);
						previous.put(destid, closest.getId());
						HeapElement<GraphNode> helem = new HeapElement<GraphNode>(Double.POSITIVE_INFINITY, dest);
						heapelems.put(destid, helem);
						distQ.insert(helem);
					}
					
					//Debug.println("Distance "+cost+" to node "+destid+" is better than previous best distance of "+distances.get(destid), Debug.INFO);
					//update distance
					distances.put(destid, cost);
					if(edge.info != null) throw new RuntimeException("This bastardized version of djikstra doesnt keep track of 'extra' edge info.....");
					
					//fix the distance queue because its score has changed
					HeapElement<GraphNode> changed = heapelems.get(destid);
					distQ.remove(changed); //remove it, because it is not in the right place
					changed.setHeapValue(cost); //update its value
					distQ.insert(changed); //then insert it, so that it goes to the right place

					//we keep the current shortest path to each node we know how to reach at all
					previous.put(dest.getId(), he.getObject().getId());
				}
			}
		}
		
		//dont need these anymore, so let them be GC-ed
		heapelems = null;
		distQ = null;
		System.gc();
		
		//ok, we should have enough info to create the 
		//shortest path tree. Build it and return it
		
		//pathtree contains all the edges in the path tree,
		//so we build it from that
		
		HashMap<String, GraphNode> pathtree = new HashMap<String, GraphNode>();
		String treerootid = null;
		String dest = targetid;

		while(treerootid == null) 
		{
			Debug.println("Building shortest path tree, adding reachable node "+dest, Debug.INFO);
			String orig = previous.get(dest);
			Debug.println("Reachable from node "+orig, Debug.INFO);
			if(orig == null) //a root, or an unconnected node 
			{  
				if(dest.equals(start.getId()))
					treerootid = dest;
				else
					Debug.println("Node "+dest+" has no nodes that link to it, and its not the root of the shortest path tree. Check this is OK.. your input graph may be invalid", Debug.IMPORTANT);
				
				continue;
			}
				
			//if(!distances.containsKey(orig)) throw new RuntimeException("No distance value for node with id "+orig);
			double basecost = distances.get(orig);
			double cost = distances.get(dest);
			double linklength = cost-basecost;
			
			if(!pathtree.containsKey(dest)) 
				pathtree.put(dest, new GraphNode(dest));
			if(!pathtree.containsKey(orig))
				pathtree.put(orig, new GraphNode(orig));
			
			//now add the link
			GraphNode orignode = pathtree.get(orig);
			GraphNode destnode = pathtree.get(dest);
			orignode.addEdge(destnode, linklength);
			
			//and continue up the path to the start
			dest = orig;
		}
		
		
		
		//return the root of the tree
		if(treerootid == null)
			throw new IllegalStateException("Found no root to the shortest path tree -- this must be a bug!");
		GraphNode root = pathtree.get(treerootid);
		return root;
	}

	
	
	/** Djikstra's algorithm to construct a shortest path tree
	 * 
	 * @param startnodeid
	 * @param graph The shortest path tree
	 * @return
	 */
	public static GraphNode getShortestPathTree(String startnodeid, java.util.Map<String, GraphNode> graph)
	{
		//initial setup. initialize shortest path tree to just
		//the start node, and set all distances to 0.0
		
		//previous is the tree that has been built. It contains nodes that 
		//we have found the shortest path to, as well as nodes we have a path to 
		//but which are not yet known to be optimal
		HashMap<String, String> previous = new HashMap<String, String>();
		
		//for fast lookup, a map from nodeid to the (so far) shortest distance to
		//that node
		HashMap<String, Double> distances = new HashMap<String, Double>();
		HashMap<String, Object> extra = new HashMap<String, Object>();

		//the 'candidates' for addition to the shortest path tree, ordered 
		//by distance. Nodes already in the shortest path tree are NOT in this.
		Heap<String> distQ = new Heap<String>(graph.size()*4);
		
		//we ALSO need to be able to look up all the elements in the distQ by their nodeid's,
		//so that we can reorder the queue when we find shorter distances than the current
		//best known distances
		java.util.Map<String, HeapElement<String>> heapelems = new HashMap<String, HeapElement<String>>();
		
		//initialize distances and the shortest path tree (previous) to be a 
		//graph will all nodes but no links.
		for(String nodeid : graph.keySet())
		{
			distances.put(nodeid, Double.POSITIVE_INFINITY);
			previous.put(nodeid, null);
		}
		distances.put(startnodeid, 0.0);

		//build the heap
		for(String nodeid : graph.keySet())
		{
			HeapElement<String> dhe;
			if(nodeid.equals(startnodeid)) 
				dhe = new HeapElement<String>(0.0, nodeid);
			else
				dhe = new HeapElement<String>(Double.POSITIVE_INFINITY, nodeid);
			
			heapelems.put(nodeid, dhe);
			distQ.insert(dhe);
		}
		
		//ok, we have our heap, lets go.
		
		
		//ok, now do the algorithm proper
		while(!distQ.isEmpty()) 
		{
			//get closest node not already in shortest path tree
			HeapElement<String> he = distQ.extractMin();

			//Debug.println("Added node "+he.getObject()+" to the shortest path tree at cost "+he.getHeapValue(), Debug.INFO);
			
			//go through all the vertices reachable from the mincost vertex,
			//and update their distances
			GraphNode closest = graph.get(he.getObject());
			double basecost = he.getHeapValue();
			List<GraphEdge> links = closest.getEdges();
			//Debug.println("Minimum distance to this node that we are adding is "+basecost, Debug.INFO);
			for(GraphEdge edge : links) 
			{
				GraphNode dest = edge.leadsto;
				String destid = dest.getId();
				//Debug.println("    Looking at neighbouring node "+destid+" with edge weight "+edge.weight, Debug.INFO);
				double cost = edge.weight+basecost;
				
				//if cost is better than our currently best known cost,
				//then we update
				if(cost < distances.get(destid))
				{
					//Debug.println("Distance "+cost+" to node "+destid+" is better than previous best distance of "+distances.get(destid), Debug.INFO);
					//update distance
					distances.put(destid, cost);
					extra.put(destid, edge.info);
					
					//now fix the distance queue because its score has changed
					HeapElement<String> changed = heapelems.get(destid);
					distQ.remove(changed); //remove it, because it is not in the right place
					changed.setHeapValue(cost); //update its value
					distQ.insert(changed); //then insert it, so that it goes to the right place

					//we keep the current shortest path to each node we know how to reach at all
					previous.put(destid, he.getObject());
				}
			}
		}
		
		
		//ok, we should have enough info to create the 
		//shortest path tree. Build it and return it
		
		//pathtree contains all the edges in the path tree,
		//so we build it from that
		
		HashMap<String, GraphNode> pathtree = new HashMap<String, GraphNode>();
		String treerootid = null;
		Object[] dests = previous.keySet().toArray();
		for(int i = 0; i < dests.length; i++) 
		{
			String dest = (String) dests[i];
			String orig = previous.get(dest);
			if(orig == null) //a root, or an unconnected node 
			{  
				if(dest.equals(startnodeid))
					treerootid = dest;
				else
					Debug.println("Node "+dest+" has no nodes that link to it, and its not the root of the shortest path tree. Check this is OK.. your input graph may be invalid", Debug.IMPORTANT);
				
				continue;
			}
				
			double basecost = distances.get(orig);
			double cost = distances.get(dest);
			double linklength = cost-basecost;
			Object linkextra = extra.get(dest);
			
			if(!pathtree.containsKey(dest)) 
				pathtree.put(dest, graph.get(dest).copyWithoutEdges());
			if(!pathtree.containsKey(orig))
				pathtree.put(orig, graph.get(orig).copyWithoutEdges());
			
			//now add the link
			GraphNode orignode = pathtree.get(orig);
			GraphNode destnode = pathtree.get(dest);
			orignode.addEdge(destnode, linklength, linkextra);
		}
		
		
		//return the root of the tree
		if(treerootid == null)
			throw new IllegalStateException("Found no root to the shortest path tree -- this must be a bug!");
		GraphNode root = pathtree.get(treerootid);
		return root;
	}
	

	
	
	
	
	/** Get the minimum cost spanning tree for the graph. 
	 * 
	 * @return
	 */
	/*public static GraphNode getMinimumCostSpanningTree(java.util.HashMap graph)
	{
		//ok, just use kruskal's algorithm 
		
		class EdgeHeapElem implements HeapElement {
			int origvertex;
			int destvertex;
			double weight;
			EdgeHeapElem(int orig, int dest, double w) {
				origvertex = orig; destvertex = dest; weight = w;
			}
			
			public double getHeapValue() {
				return weight;
			}
		}
		
		Heap edgeheap = new Heap(Math.max(512, graph.size()*4));

		java.util.Iterator keys = graph.keySet().iterator();
		while(keys.hasNext())
		{
			Integer nodeid = (Integer) keys.next();
			GraphNode orig = (GraphNode) graph.get(nodeid);
			List edges = orig.getEdges();
			Iterator edgeit = edges.iterator();
			while(edgeit.hasNext()) {
				GraphEdge edge = (GraphEdge) edgeit.next();
				edgeheap.insert(new EdgeHeapElem(orig.getId(), edge.leadsto.getId(), edge.weight));
			}
		}
		
		//ok, we have all the edges, sorted. 
		
		throw new UnsupportedOperationException("Not finished implementing this yet");
	}*/
	
	
	
	/* Get a HashMap containing all the nodes that are reachable
	 * from the start node. Indexed by node id (as in Integer) 
	 */
	public static HashMap<String, GraphNode> getGraph(GraphNode start)
	{
		HashMap<String, GraphNode> result = new HashMap<String, GraphNode>();
		result.put(start.getId(), start);
		getGraph(start, result);
		return result;
	}
	
	private static void getGraph(GraphNode cur, HashMap<String, GraphNode> map)
	{
		Iterator edgeit = cur.getEdges().iterator();
		while(edgeit.hasNext())
		{
			GraphEdge edge = (GraphEdge) edgeit.next();
			if(map.containsKey(edge.leadsto.getId()))
				continue; //already in graph
			
			//not in graph. add it and its children
			map.put(edge.leadsto.getId(), edge.leadsto);
			getGraph(edge.leadsto, map);
		}
	}
	
	
	
	
	
	
	
	
	
	/** Prints a connected graph.
	 * 
	 * @param start A node in the graph, from which all other nodes are reachable
	 * 
	 * @param stream the stream to print to.
	 */
	public static void printGraph(GraphNode start, PrintStream stream)
	{
		HashMap visited = new HashMap();
		Stack<GraphNode> todo = new Stack<GraphNode>();
		
		todo.push(start);
		
		while(!todo.isEmpty()) 
		{
			GraphNode node = todo.pop();
			String printstring = node.getId()+" --> ";
			if(!visited.containsKey(node.getId()))
			{
				//remember we have visited it
				visited.put(node.getId(), null);
				
				//print all the edges, and add them to our todo list if we
				//havent visited them yet
				for(GraphEdge edge : node.getEdges())
				{
					printstring += edge.leadsto.getIdStr()+" "+edge.weight+"(/"+edge.info+") , ";
					String leadstoid = edge.leadsto.getId(); 
					if(!visited.containsKey(leadstoid))
						todo.push(edge.leadsto);
				}
				stream.println(printstring);
			}
		}
	}
	
	

	
	
	
	
	
	
	
	public static void main(String[] args) throws Exception
	{
		//read in a graph and then get the shortest path spanning tree
		//and them print it out
		Debug.setVerbosityLevel(Debug.INFO);
		Debug.setDebugLevel(Debug.DEBUG_PARANOID);
		
		HashMap<String, GraphNode> nodes = GraphUtil.readGraph(new java.io.File("unit_tests", "graph.tmp").getAbsolutePath());
		GraphNode start = nodes.get("1");
		
		GraphNode root = GraphUtil.getShortestPathTree(start.getId(), nodes);
		
		Debug.println("HERE IS THE SHORTEST PATH TREE FROM (1) VIA DJIKSTRA", Debug.INFO);
		printGraph(root, System.out);
		System.out.println();
		
		//now extract all the shortest paths
		java.util.Map<String, GraphEdge> paths = getAllPathsFromPathTree(root);
		for(String destid : paths.keySet())
		{
			//first print the path
			GraphEdge edge = paths.get(destid);
			Debug.print("(pathlen "+edge.info+") "+destid+" ", Debug.IMPORTANT);
			while(true)
			{
				Debug.print(" <-- "+edge.leadsfrom.getId(), Debug.IMPORTANT);				
				if(!paths.containsKey(edge.leadsfrom.getId()))
					break; //exit when we reach the root, because there is no path to the root
				edge = paths.get(edge.leadsfrom.getId());
			}
			Debug.println("",Debug.IMPORTANT);
		}
		
		
	}
	
	
}



