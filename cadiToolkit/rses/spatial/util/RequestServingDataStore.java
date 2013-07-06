package rses.spatial.util;

import java.util.Iterator;

import rses.Debug;
import rses.PlatformInfo;
import rses.apps.sydneyenergy.DisplayableModel;
import rses.spatial.gui.DataBaseBackedDisplayableModel;
import rses.util.distributed.RequestServer;
import rses.util.distributed.TextInterfaceServerObject;



/** A wrapper around the DataStore object that will accept text-string requests 
 * and send text-string replies to those requests
 * 
 * @author peterr
 *
 */
public class RequestServingDataStore extends DataStore implements rses.util.distributed.RequestServer 
{
	
	public RequestServingDataStore() throws java.io.IOException 
	{
		super();
	}

	
	
	public String handleRequest(String request)
	{
		try {
			String res = HandleRequest_internal(request);
			return res;
		}
		catch(Exception e)
		{
			if(Debug.equalOrMoreVerbose(Debug.INFO))
			{
				java.io.PrintStream ps = Debug.getPrintStream(Debug.INFO);
				ps.println("Exception raised while servicing request. Printing stack trace:");
				e.printStackTrace(ps);
			}
			return "Error: "+e.getMessage();
		}
	}
	
	/** Handle all requests sent to this object.
	 * 
	 */
	private String HandleRequest_internal(String request)
	throws java.io.IOException 
	{
		if(request == null)
			return "NULL REQUEST OK. DID NOTHING";
		
		String[] bits = request.trim().split(" ");
		
		if(bits.length == 0)
			return "EMPTY REQUEST OK. DID NOTHING";
		
		
		if(bits[0].equalsIgnoreCase("help") || bits[0].equals("?"))
		{
			String helpstr = "";
			helpstr += "Valid commands are:";
			helpstr += "   getVectorVal GEOGNAME TABLENAME X Y ,";
			helpstr += "   getBounds GEOGNAME ,";
			helpstr += "   getGeographies ,";
			helpstr += "   getLayersForGeography GEOGNAME ,";
			helpstr += "   getRegionsInGeography GEOGNAME ,";
			helpstr += "   getValueForRegion GEOGNAME TABLENAME REGION_NAME ,";	
			helpstr += "   getCentroidForRegion GEOGNAME REGION_NAME ,";	
			helpstr += "   getImageForLayer GEOGNAME TABLENAME PLOTSTYLE   where PLOTSTYLE is one of [continuous,equalnumbersX,equalrangesX,clustX] ,";
			helpstr += "   createTable GEOGNAME TABLENAME ,";
			helpstr += "   setValue GEOGNAME TABLENAME REGION_NAME VALUE"; 
			return helpstr;
		}
		else if(bits[0].equalsIgnoreCase("getVectorVal")) //get a value at lat/long for a particular layer
		{
			if(bits.length != 5)
				return "getVectorVal takes 4 arguments: GEOGNAME TABLENAME X Y";
			String geographyname = bits[1];
			String tablename = bits[2];
			double lat = Double.parseDouble(bits[3]);
			double lon = Double.parseDouble(bits[4]);
			return ""+this.getTable(geographyname, tablename).getMembershipLayer().getValue(lat, lon);
		}
		else if(bits[0].equalsIgnoreCase("getbounds")) //get the lat/long bounds of a layer
		{
			if(bits.length != 2)
				return "getbounds takes 1 argument: GEOGNAME";
			String geographyname = bits[1];
			rses.spatial.GISLayer layer = this.getVectorLayer(geographyname);
			return "LAT: "+layer.getMinLat()+" -> "+layer.getMaxLat()+" LON: "+layer.getMinLong()+" -> "+layer.getMaxLong();
		}
		else if(bits[0].equalsIgnoreCase("getGeographies"))
		{
			Iterator<String> geognames = this.getVectorLayers().iterator();
			String res = "";
			while(geognames.hasNext()) 
				res += geognames.next()+",";
			
			//chop off last comma
			if(res.length() > 0 && res.charAt(res.length()-1) == ',')
				res = res.substring(0, res.length()-1);
			return res;
		}
		else if(bits[0].equalsIgnoreCase("getLayersForGeography"))
		{
			if(bits.length != 2)
				return "getLayersForGeography takes 1 argument: GEOGNAME";
			
			java.util.HashMap<String, GeoDataTable> tables = this.getTablesForLayer(bits[1]);
			String res = "";
			Iterator<String> tabnames = tables.keySet().iterator();
			//sort them so they come out in alphabetica order
			String[] sorted = new String[tables.keySet().size()];
			for(int i = 0; i < sorted.length; i++)
				sorted[i] = tabnames.next();

			java.util.Arrays.sort(sorted);

			for(int i = 0; i < sorted.length; i++)
				res += sorted[i]+",";

			//chop off last comma
			if(res.length() > 0 && res.charAt(res.length()-1) == ',')
				res = res.substring(0, res.length()-1);
			return res;
		}
		else if(bits[0].equalsIgnoreCase("getRegionsInGeography")) //get all the region names
		{
			if(bits.length != 2)
				return "getRegionsInGeography takes 1 argument: GEOGNAME";
			
			String[] catnames = this.getValidRegionsForLayer(bits[1]);
			String res = "";
			for(int i = 0; i < catnames.length-1; i++)
				res += catnames[i]+",";
			res += catnames[catnames.length-1];
			return res;
		}
		else if(bits[0].equalsIgnoreCase("getValueForRegion")) //get a value by region name
		{
			if(bits.length != 4)
				return "getValueForRegion takes 3 arguments: GEOGNAME TABLENAME REGION_NAME";
			
			GeoDataTable table = this.getTable(bits[1], bits[2]);
			return ""+table.lookup(bits[3]);
		}
		else if(bits[0].equalsIgnoreCase("getCentroidForRegion"))
		{
			if(bits.length != 3)
				return "getCentroidForRegion takes 2 arguments: GEOGNAME  REGION_NAME";
			
			GeoDataTable[] xy = this.getCentroidsTables(bits[1]);
			Debug.println(""+xy, Debug.IMPORTANT);
			Debug.println(""+xy[0], Debug.IMPORTANT);
			Debug.println(""+xy[1], Debug.IMPORTANT);
			
			Double x = ((Double) xy[0].lookup(bits[2]));
			Double y = ((Double) xy[1].lookup(bits[2]));
			
			if(x != null && y == null || x == null && y != null)
				throw new RuntimeException("internal error -- have either x or y centroid but not both. Strange....");
			
			if(x == null && y == null)
				return "NULL,NULL";

			return x+","+y;
		}
		else if(bits[0].equalsIgnoreCase("createTable"))
		{
			if(bits.length != 3)
				return "createTable takes 2 arguments: GEOGNAME TABLENAME";
			
			if(this.hasTable(bits[1], bits[2]))
				return "Table already exists";
			
			java.util.Map<String, Object> mappings = new java.util.HashMap<String, Object>();
			rses.spatial.GISLayer lyr = this.getVectorLayer(bits[1]);
			rses.spatial.util.GeoDataTable table = new rses.spatial.util.GeoDataTable(bits[2], lyr, mappings);
			this.addTable(table);
			
			return "OK";
		}
		else if(bits[0].equalsIgnoreCase("setValue"))
		{
			if(bits.length != 5)
				return "setValue takes 4 arguments: GEOGNAME TABLENAME REGION_NAME VALUE";
			
			//try and convert the value to a double. If not, then just revert to string values
			Double d = null;
			try { d = new Double(bits[4]); }
			catch(NumberFormatException nfe) {}
			
			if(d != null)
				this.getTable(bits[1], bits[2]).set(bits[3], d);
			else
				this.getTable(bits[1], bits[2]).set(bits[3], bits[4]);
			
			return "OK";
			
		}
		else if(bits[0].equalsIgnoreCase("getImageForLayer"))
		{
			//"   getImageForLayer GEOGNAME TABLENAME PLOTSTYLE   where PLOTSTYLE is one of [continuous,equalnumbersX,equalrangesX,clustX]";	
			if(bits.length != 4)
				return "getImageForLayer takes 3 arguments: GEOGNAME TABNAME PLOTSTYLE";
			
			DataBaseBackedDisplayableModel displaymodel = new DataBaseBackedDisplayableModel(this, bits[1], bits[2]);
			if(bits[3].equalsIgnoreCase("continuous")) {
				displaymodel.setDisplayToContinuous();
			}
			else if(bits[3].toLowerCase().startsWith("equalnumbers")) {
				int num = new Integer(bits[3].substring("equalnumbers".length(), bits[3].length())).intValue();
				displaymodel.setDisplayToDiscrete(DisplayableModel.EQUALNUMBERS, num);
			}
			else if(bits[3].toLowerCase().startsWith("equalranges")) {
				int num = new Integer(bits[3].substring("equalranges".length(), bits[3].length())).intValue();
				displaymodel.setDisplayToDiscrete(DisplayableModel.EQUALRANGES, num);
			}
			else if(bits[3].toLowerCase().startsWith("clust")) {
				int num = new Integer(bits[3].substring("clust".length(), bits[3].length())).intValue();
				displaymodel.setDisplayToDiscrete(DisplayableModel.KMEANS, num);
			}
			else
				return "Unkown plotstyle: "+bits[3];
				
			rses.spatial.GISLayer regionmembership = getVectorLayer(bits[1]);
			int[][] membership_flipY = new int[regionmembership.getLatSteps()][];
			for(int i = 0; i < membership_flipY.length; i++)
				membership_flipY[i] = regionmembership.categoricaldata[membership_flipY.length-i-1];

			java.awt.image.BufferedImage bim = displaymodel.getDisplay(membership_flipY, null, 1);
			java.io.File f = java.io.File.createTempFile("ISFGIS_", ".png");
			boolean ok = javax.imageio.ImageIO.write(bim, "png", f);
			if(!ok)
				return "Image save failed!";
			else
				return f.getCanonicalPath();
		}
		else
			return "UNKNOWN REQUEST: "+request;
	}

	
	public static void main(String[] args) throws Exception
	{
		Debug.setVerbosityLevel(Debug.INFO);
	
		//test a RequestServingDataStore
		RequestServer svrobj = new rses.spatial.util.RequestServingDataStore();
	
	
		TextInterfaceServerObject server = new TextInterfaceServerObject(42426, svrobj);
	}
	
}
