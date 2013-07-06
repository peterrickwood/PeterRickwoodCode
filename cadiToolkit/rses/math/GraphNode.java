package rses.math;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import rses.math.GraphEdge;

public class GraphNode 
{
	private String nodeid;
	private LinkedList<GraphEdge> edges = new LinkedList<GraphEdge>();
	
	
	public GraphNode(String nodeid)
	{
		this.nodeid = nodeid;
	}
	
	
	
	public ArrayList<GraphEdge> getAllEdgesInGraph()
	{
		ArrayList<GraphEdge> result = new ArrayList<GraphEdge>();
		getAllEdges_internal(result, new HashMap<Integer, GraphNode>());
		return result;
	}
	
	private void getAllEdges_internal(ArrayList<GraphEdge> result, HashMap<Integer, GraphNode> donenodes)
	{
		donenodes.put(new Integer(this.getId()), this);
		for(GraphEdge edge : edges)
		{
			result.add(edge);
			if(!donenodes.containsKey(new Integer(edge.leadsto.getId())))
				edge.leadsto.getAllEdges_internal(result, donenodes);
		}
	}
	
	
	public boolean isLinkedTo(String nodeid)
	{
		for(GraphEdge edge : edges)
		{
			if(edge.leadsto.getId().equals(nodeid))
				return true;
		}
		return false;
		
	}
	
	
	public String getIdStr()
	{
		return ""+this.getId();
	}
	
	public void addEdge(GraphNode vertex)
	{
		addEdge(vertex, 1.0);
	}
		
	public void addEdge(GraphEdge newedge)
	{
		if(newedge.leadsfrom != this)
			throw new IllegalArgumentException("Trying to add invalid edge to node... does not leave from this node!");
		edges.addLast(newedge);
	}
	
	public void addEdge(GraphNode vertex, double weight, Object info)
	{
		edges.addLast(new GraphEdge(this, vertex, weight,info));
	}
	
	public void addEdge(GraphNode vertex, double weight)
	{
		addEdge(vertex, weight, null);
	}
	
	
	public void removeAllEdges()
	{
		while(!edges.isEmpty())
			edges.removeFirst();
	}
	
	public void removeEdge(GraphEdge edge)
	{
		boolean removed = edges.remove(edge);
		if(!removed)
			throw new RuntimeException("Requested to remove edge, but no such edge");
	}
	
	
	public void setId(String id)
	{
		this.nodeid = id;
	}
	
	public String getId()
	{
		return this.nodeid;
	}
	
	public java.util.List<GraphEdge> getEdges()
	{
		return edges;
	}
	
	public GraphNode copyWithoutEdges()
	{
		return new GraphNode(this.nodeid);
	}
}
