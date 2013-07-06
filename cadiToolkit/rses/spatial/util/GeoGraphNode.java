package rses.spatial.util;

import rses.math.GraphEdge;
import rses.math.GraphNode;


/** A Node in a graph, but with geo-coordinates for each node. Has some utilities for dumping the graph out
 * in KML format
 * @author peterr
 *
 */
public class GeoGraphNode extends GraphNode 
{
	private double lat;
	private double lon;
	private String name;
	
	public GeoGraphNode(int id, String name, double lat, double lon)
	{
		super(id);
		this.name = name;
		this.lat = lat;
		this.lon = lon;
	}
	
	
	
	public static void dumpKMLforGraph(String filename, java.util.Map<Integer, GeoGraphNode> graph) throws java.io.IOException
	{
		java.io.PrintStream ps = new java.io.PrintStream(new java.io.FileOutputStream(filename));
		ps.println(rses.spatial.util.GoogleEarthUtils.getKMLHeader());
		//add all the nodes
		for(int nodeid : graph.keySet()) {
			GeoGraphNode g = graph.get(nodeid);
			ps.print(rses.spatial.util.GoogleEarthUtils.getKMLforPlacemark(g.name, g.lat, g.lon));
		}
		
		//add all the edges
		for(int nodeid : graph.keySet()) {
			GeoGraphNode g = graph.get(nodeid);
			for(GraphEdge e : g.getEdges()) {
				GeoGraphNode leadsto = (GeoGraphNode) e.leadsto;
				ps.print(rses.spatial.util.GoogleEarthUtils.getKMLforLine(g.name+" "+leadsto.name, g.lat, g.lon, leadsto.lat, leadsto.lon));
			}
		}
		
		ps.println(rses.spatial.util.GoogleEarthUtils.getKMLFooter());
		ps.close();
	}
	

}
