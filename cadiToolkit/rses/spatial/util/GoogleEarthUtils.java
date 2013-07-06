package rses.spatial.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import rses.Debug;
import rses.PlatformInfo;
import rses.util.Util;




public final class GoogleEarthUtils
{
	private GoogleEarthUtils() {}//never instantiate this class
	
	/**
	 * 
	 * @param imgname The name of the image to use as the overlay
	 * @param kmlfilename The name of the kml file to create
	 * @param description The description to put in the kml file
	 * @param fileurl The url to the directory which holds imgfile
	 * @param north north bound on image
	 * @param south south bound on image
	 * @param east east bound on image
	 * @param west west bound on image
	 * @throws IOException
	 */
	public static void writeKMLForOverlay(String imgname,
			String kmlfilename, String description, String fileurl,
			double north, double south, double east, double west)
	throws IOException
	{
		String filename = kmlfilename;
		FileWriter file = new FileWriter(filename);
		String nl = PlatformInfo.nl;
		if(!fileurl.endsWith("/")) //its a url, so it is always a '/', even on windows
			fileurl = fileurl + "/";
		
		file.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+nl+
		"<kml xmlns=\"http://earth.google.com/kml/2.0\">"+nl+
		"<GroundOverlay>"+nl+
		"<description>"+description+"</description>"+nl+
		"<name>"+imgname+"</name>"+nl+
		"<visibility>1</visibility>"+nl+
		"<Icon>"+nl+
		"<href>"+fileurl+imgname+"</href>"+nl+
		"</Icon>"+nl+
		"<LatLonBox id=\""+imgname+"\">"+nl+
		"   <north>"+north+"</north>"+nl+
	    "  <south>"+south+"</south>"+nl+
		"    <east>"+east+"</east>"+nl+
		"    <west>"+west+"</west>"+nl+
		"    <rotation>0</rotation>"+nl+
		"  </LatLonBox>"+nl+
		"</GroundOverlay>"+nl+
		"</kml>"+nl);
		
		file.close();
	}
	
	
	
	
	
	public static void writePathToKML(String kmlfilename, java.util.Date[] times, double[] lats, double[] lons)
	throws java.io.IOException
	{
		String filename = kmlfilename;
		FileWriter file = new FileWriter(filename);
		String nl = PlatformInfo.nl;
		

		if(times.length != lats.length || times.length != lons.length)
			throw new RuntimeException("Incorrect arguments to writePathToKML");
		
		//write header
		file.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+nl+
		"<kml xmlns=\"http://www.opengis.net/kml/2.2\""+nl+
		"xmlns:gx=\"http://www.google.com/kml/ext/2.2\">"+nl+		
		"<Folder>"+nl+
		"  <Placemark>"+nl+
		"    <gx:Track>"+nl);
		
		
		
		//write data
		for(int i =0; i < times.length; i++)
		{
			String datestr = (times[i].getYear()+1900)+"-";
			int mon = (times[i].getMonth()+1);
			if(mon < 10)
				datestr += "0"+mon+"-";
			else
				datestr += mon+"-";
			
			int day = (times[i].getDate());
			if(day < 10)
				datestr += "0"+day+"T";
			else
				datestr += ""+day+"T";
			
			datestr += times[i].getHours() < 10 ? "0"+times[i].getHours() : ""+times[i].getHours();
			datestr += ":";
			datestr += times[i].getMinutes() < 10 ? "0"+times[i].getMinutes() : ""+times[i].getMinutes();
			datestr += ":";
			datestr += times[i].getSeconds() < 10 ? "0"+times[i].getSeconds() : ""+times[i].getSeconds();
			datestr += "Z";
			file.write("    <when>"+datestr+"</when>"+nl);
		}
		for(int i =0; i < times.length; i++)
		{
			file.write("<gx:coord>"+lons[i]+" "+lats[i]+" 0.0</gx:coord>"+nl);
		}
		
		
		      /*<when>2010-05-28T02:02:09Z</when>
		      <when>2010-05-28T02:02:35Z</when>
		      <when>2010-05-28T02:02:44Z</when>
		      <when>2010-05-28T02:02:53Z</when>
		      <when>2010-05-28T02:02:54Z</when>
		      <when>2010-05-28T02:02:55Z</when>
		      <when>2010-05-28T02:02:56Z</when>
		      <gx:coord>-122.207881 37.371915 156.000000</gx:coord>
		      <gx:coord>-122.205712 37.373288 152.000000</gx:coord>
		      <gx:coord>-122.204678 37.373939 147.000000</gx:coord>
		      <gx:coord>-122.203572 37.374630 142.199997</gx:coord>
		      <gx:coord>-122.203451 37.374706 141.800003</gx:coord>
		      <gx:coord>-122.203329 37.374780 141.199997</gx:coord>
		      <gx:coord>-122.203207 37.374857 140.199997</gx:coord>*/
		
		
		//write footer
		file.write(
		"    </gx:Track>"+nl+
		"  </Placemark>"+nl+
		"</Folder>"+nl+ 
		"</kml>");

		
		file.close();
		
	}
	
	
	
	
	public static String getKMLHeader()
	{
		String hdr = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
		return hdr;
	}
	

	public static String getKMLFooter()
	{
		return "</kml>";
	}

	
	
	public static String getKMLforPlacemark(String id, double lat, double lon)
	{
		String nl = PlatformInfo.nl;
		String str = "  <Placemark>"+nl;
		str +=       "    <name> "+id+" </name>"+nl;
	    str +=       "    <visibility>1</visibility>"+nl;
	    str +=       "    <Point>"+nl;
	    str +=       "      <coordinates>"+lon+","+lat+"</coordinates>"+nl;
	    str +=       "    </Point>"+nl;
	    str +=       "  </Placemark>"+nl;
	    return str;
	}

	
	public static String getKMLforLine(String id, double lat1, double lon1, double lat2, double lon2)
	{
		String nl = PlatformInfo.nl;
		String str = "  <Placemark>"+nl;
		str +=       "    <LineString>"+nl;
		str +=       "      <coordinates>"+nl;
		str +=       "      "+lon1+","+lat1+",0"+nl;
		str +=       "      "+lon2+","+lat2+",0"+nl;
		str +=       "      </coordinates>"+nl;
		str +=       "    </LineString>"+nl;
		str +=       "  </Placemark>"+nl;
		return str;
	}

	
	
	/** Extract all the polygons from a KML file
	 * 
	 * @param kmlfile
	 * @return
	 */
	public static GoogleEarthPolygon[] getPolygonsFromKMLFile(String kmlfile, String namefield) throws java.io.IOException
	{
		return getPolygonsFromKMLFile(kmlfile, namefield, false);
	}
	
	
	
	public static GoogleEarthPolygon[] getPolygonsFromKMLFile(String kmlfile, String namefield, boolean reprintfile) throws java.io.IOException
	{
		BufferedReader rdr = new BufferedReader(new FileReader(kmlfile));
		ArrayList polys = new ArrayList();
		
		outerloop: while(true)
		{
			String line = rdr.readLine();
			if(line == null)
				break;
			else if(reprintfile) System.out.println(line);
			
			line = line.toLowerCase();
			while(line.indexOf("<placemark>") == -1) { 
				line = rdr.readLine();
				if(line == null) 
					break outerloop;
				if(reprintfile) System.out.println(line);				
				line = line.toLowerCase();
			}
			while(line.indexOf(namefield) == -1)
			{
				line = rdr.readLine();
				if(reprintfile) System.out.println(line);
				line = line.toLowerCase();
			}
			
			//now get the name
			//int starti = line.indexOf("<"+namefield+">")+("<"+namefield+">").length();
			
			//this assumes the first index of '>' gives the end of the kml tag
			int starti = line.indexOf(">")+1;
			
			//assumes this is the only closing tag on the line
			int endi = line.indexOf("</");
			if(endi == -1) endi = line.length();
			
			String name = line.substring(starti, endi).trim();
			//replace spaces with underscores
			name = name.trim().replace(' ', '_');
			
			Debug.println("Got name of polygon: "+name, Debug.INFO);
			
			coordwhile: while(true)
			{
				while(line.indexOf("<coordinates>") == -1)
				{
					line = rdr.readLine();
					if(reprintfile) System.out.println(line);
					line = line.toLowerCase();
				}
			
				//move to the start of the polygonvertices
				line = rdr.readLine().toLowerCase();

			
				//ok, we now expect one polygon point per line
				GoogleEarthPolygon poly = new GoogleEarthPolygon();
				poly.name = name;
				ArrayList points = new ArrayList();
				while(line.toLowerCase().indexOf("</coordinates>") == -1)
				{
					String[] bits = line.split(",");
					double[] res = new double[bits.length];
					for(int i =0; i < bits.length; i++)
						res[i] = Double.parseDouble(bits[i]);
					double[] latlon = new double[] {res[1], res[0]};
					points.add(latlon);
					line = rdr.readLine();
				}
			
				//now make sure that the last point in the polygon is the same
				//as the first.
				double[] fst = (double[]) points.get(0);
				double[] lst = (double[]) points.get(points.size()-1);
				if(fst[0] != lst[0] || fst[1] != lst[1])
					throw new RuntimeException("Polygon does not have identical first and last vertices");

				poly.latlongpoints = new double[points.size()][];
				Debug.println("read in "+poly.latlongpoints.length+" points in this polygon", Debug.INFO);
				for(int i =0; i < poly.latlongpoints.length; i++)
					poly.latlongpoints[i] = (double[]) points.get(i);
			
				//now make sure that the polygon is ordered counter-clockwise (in long-lat coord space
				//which equates to clockwise in lat/long coord space)
				if(rses.math.MathUtil.isCounterClockwise(poly.latlongpoints))
					poly.latlongpoints = rses.math.MathUtil.reversePolygon(poly.latlongpoints);
			
			
				if(reprintfile) {
					for(int i = 0; i < poly.latlongpoints.length; i++)
						System.out.println(poly.latlongpoints[i][1]+","+poly.latlongpoints[i][0]+",0.0");
				}
				if(reprintfile) System.out.println(line);
			
				polys.add(poly);
				//see if there are any more polygons
				while(line.toLowerCase().indexOf("</placemark>") < 0)
				{
					if(line.toLowerCase().indexOf("<coordinates>") >= 0) {
						Debug.println("Found another polygon for placemark "+name+", reading it in", Debug.IMPORTANT);
						continue coordwhile;
					}
					line = rdr.readLine();
				}
				
				break; //no more polygons, and finished placemark
			}
			
		}
		
		GoogleEarthPolygon[] res = new GoogleEarthPolygon[polys.size()];
		for(int i =0; i < res.length; i++)
			res[i] = (GoogleEarthPolygon) polys.get(i);
		return res;
	}

	
	
	
	//for testing
	public static void main(String[] args) throws Exception
	{
		//Debug.setVerbosityLevel(Debug.INFO);
		GoogleEarthPolygon[] polys = getPolygonsFromKMLFile(args[0], "name", true);
	}
	
	
	
}		




