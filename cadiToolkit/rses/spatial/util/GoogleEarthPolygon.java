package rses.spatial.util;

import rses.Debug;
import rses.math.MathUtil;
import rses.util.Util;




public class GoogleEarthPolygon
{
	public String name;
	public double[][] latlongpoints;
	
	
	transient double[] centroid = null;
	transient double[][] shiftedlatlon = null;
	
	public GoogleEarthPolygon()
	{}
	
	
	public GoogleEarthPolygon(double[][] latlon, String name)
	{
		
		//now make sure that the last point in the polygon is the same
		//as the first.
		double[] fst = latlon[0];
		double[] lst = latlon[latlon.length-1];
		if(fst[0] != lst[0] || fst[1] != lst[1])
			throw new RuntimeException("Polygon does not have identical first and last vertices");

		latlongpoints = new double[latlon.length][2];
		for(int i =0; i < latlongpoints.length; i++) {
			latlongpoints[i][0] = latlon[i][0];
			latlongpoints[i][1] = latlon[i][1];
		}
		
		//now make sure that the polygon is ordered counter-clockwise (in long-lat coord space
		//which equates to clockwise in lat/long coord space)
		if(rses.math.MathUtil.isCounterClockwise(latlongpoints))
			latlongpoints = rses.math.MathUtil.reversePolygon(latlongpoints);

		this.name = name;
	}
	
	
	public boolean isInPoly(double lat, double lon)
	{
		if(centroid == null)
			centroid = this.getCentroid();
		if(this.shiftedlatlon == null)
		{
			shiftedlatlon = Util.copy(latlongpoints);
		
			for(int i =0; i < latlongpoints.length; i++)
			{
				shiftedlatlon[i][0] -= centroid[0];
				shiftedlatlon[i][1] -= centroid[1];
			}
		}
		double shiftedlat = lat-centroid[0];
		double shiftedlon = lon-centroid[1];
		
		return MathUtil.isPointInPolygon(shiftedlatlon, shiftedlat, shiftedlon);
	}
	
	

	
	private double cachedarea = Double.NaN;
	private int cachedintsamples = -1;
	public double getArea(int integrationsamples, boolean returncached)
	{
		if(returncached && cachedintsamples > 0 && integrationsamples <= cachedintsamples)
			return cachedarea;
		
		double minlat = latlongpoints[0][0];
		double minlon = latlongpoints[0][1];
		double maxlat = latlongpoints[0][0];
		double maxlon = latlongpoints[0][1];
		
		for(int i = 0; i < latlongpoints.length; i++)
		{
			if(latlongpoints[i][0] < minlat) minlat = latlongpoints[i][0];
			if(latlongpoints[i][0] > maxlat) maxlat = latlongpoints[i][0];
			if(latlongpoints[i][1] < minlon) minlon = latlongpoints[i][1];
			if(latlongpoints[i][1] > maxlon) maxlon = latlongpoints[i][1];
		}
		
		
		//ok, work out area of the bounding square that encloses this 
		//polygon
		double latside = MathUtil.getDistanceBetweenPointsOnEarth(minlat, (minlon+maxlon)/2, maxlat, (minlon+maxlon)/2);
		double lonside = MathUtil.getDistanceBetweenPointsOnEarth((minlat+maxlat)/2, minlon, (minlat+maxlat)/2, maxlon);
		double areasqm = latside*lonside;
		Debug.println("latside is "+latside+" lonside is "+lonside, Debug.EXTRA_INFO);
		
		
		
		//now sample
		int numin = 0;
		int numout = 0;
		for(int i = 0; i < integrationsamples; i++)
		{
			double lat = minlat+Math.random()*(maxlat-minlat);
			double lon = minlon+Math.random()*(maxlon-minlon);
			if(this.isInPoly(lat, lon))
				numin++;
			else
				numout++;
		}
		
		double inprop = numin/((double) integrationsamples);
		double val =  areasqm*inprop;
		
		if(integrationsamples > this.cachedintsamples) {
			this.cachedintsamples = integrationsamples;
			this.cachedarea = val;
		}
		
		return val;
	}
	
	

	
	
	/** Does this polygon completely contain another polygon?
	 * 
	 * This is worked out by testing if all vertices are within the other polygon
	 * 
	 * @param poly
	 * @return
	 */
	public boolean contains(GoogleEarthPolygon poly)
	{
		for(int i = 0; i < poly.latlongpoints.length; i++)
		{
			if(!this.isInPoly(poly.latlongpoints[i][0], poly.latlongpoints[i][1]))
				return false;
		}
		return true;
	}
	
	
	
	public double[] getCentroid()
	{
		if(this.centroid == null)
			this.centroid = MathUtil.calculate2DpolygonCentroid(latlongpoints);
		return this.centroid;
	}
	
	
	
	
	
	
	
	

	public static void main(String[] args) throws Exception
	{
		//read in the polygons
		GoogleEarthPolygon[] polys = GoogleEarthUtils.getPolygonsFromKMLFile(args[0], "name");
		
		//now print out centroids and areas
		for(int i = 0; i < polys.length; i++)
		{
			double area = polys[i].getArea(50000, false);
			double[] centroid = polys[i].getCentroid();
			
			double lat = centroid[0];
			double lon = centroid[1];
			
			System.out.println(polys[i].name+" "+lat+" "+lon+" "+area);
		}
		
	}
	
	
	
	
}