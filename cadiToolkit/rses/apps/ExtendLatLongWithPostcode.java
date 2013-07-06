package rses.apps;

import rses.spatial.GISLayer;
import rses.util.FileUtil;








public final class ExtendLatLongWithPostcode
{
	private ExtendLatLongWithPostcode()
	{}
	
	
	//args[0] == name of file we wish to extend with postcodes 
	//
	public static void main(String[] args) throws Exception
	{
		String[] lines = FileUtil.getLines(args[0]);
		double[][] latlongvects = FileUtil.readVectorsFromFile(new java.io.File(args[0]));
	
		//read in postcode layer
		GISLayer postcodelayer = GISLayer.readFromFile("postcodemembership.gis");
		
		//first and second entries must be lat/long
		for(int i =0; i < latlongvects.length; i++)
		{
			double lat = latlongvects[i][0];
			double lon = latlongvects[i][1];
			
			float val = postcodelayer.getValue(lat, lon);
			System.out.println(lines[i]+" "+((int) Math.round(val)));
		}
		
	}
}


