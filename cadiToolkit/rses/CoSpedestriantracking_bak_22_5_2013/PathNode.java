package rses.CoSpedestriantracking;

public class PathNode extends rses.math.GraphNode 
{
	int time;
	int lati;
	int loni;
	private int lonsize;
	private int latsize;

	PathNode(int time, int lat, int lon, int latsize, int lonsize)
	{
		super(lat+" "+lon);
		this.time = time;
		this.lati = lat; this.loni = lon;
		this.latsize = latsize;
		this.lonsize = lonsize;
	}
	
	@Override
	public PathNode copyWithoutEdges()
	{
		return new PathNode(time, lati, loni, latsize, lonsize);
	}
	
	@Override
	public String getIdStr()
	{
		return "(id:"+super.getIdStr()+" lati,loni: "+lati+","+loni+")";
	}
	
	public static int getIDforXY(int lat, int lon, int latsize, int lonsize)
	{
		return lon*latsize+lat;
	}
}
