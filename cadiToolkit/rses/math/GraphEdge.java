package rses.math;

public class GraphEdge 
{
	public double weight = 1.0;
	
	public GraphNode leadsfrom;
	public GraphNode leadsto;
	public Object info; //just a useful hook to hook more info to
	
	public GraphEdge(GraphNode from, GraphNode leadsto, double weight)
	{
		this(from, leadsto, weight, null);
	}
	
	public GraphEdge(GraphNode from, GraphNode leadsto, double weight, Object info)
	{
		this.leadsfrom = from;
		this.weight = weight;
		this.leadsto = leadsto;
		this.info = info;
	}
	
	
	public GraphEdge(GraphNode from, GraphNode leadsto)
	{
		this(from, leadsto, 1.0, null);
	}
	
	
	
	
}
