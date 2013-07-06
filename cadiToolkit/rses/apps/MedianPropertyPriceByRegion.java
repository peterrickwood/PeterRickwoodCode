package rses.apps;

import java.util.HashMap;
import java.util.Iterator;

import rses.Debug;
import rses.spatial.GISLayer;
import rses.util.FileUtil;

public class MedianPropertyPriceByRegion {

	/**
	 * @param args
	 * 
	 * arg1 == region membership file
	 * arg2 == data file with each line as LAT LONG YEAR PRICE
	 */
	public static void main(String[] args) throws Exception 
	{
		Debug.setVerbosityLevel(Debug.INFO);
		GISLayer regionmembership = GISLayer.readFromFile(args[0]);
		
		
		double[][] data = FileUtil.readVectorsFromFile(new java.io.File(args[1]));
		Debug.println("Read data from data file", Debug.INFO);
		
		HashMap regiontots = new HashMap();
		HashMap regioncounts = new HashMap();

		
		for(int i = 0; i < data.length; i++)
		{
			Debug.println("Doing sale "+i+" of "+data.length, Debug.INFO);
			double lat = data[i][0];
			double lon = data[i][1];
			double price = data[i][3];
			if(price < 135000.0 || price > 2000000.0)
				continue;
			
			double region = regionmembership.getValue(lat, lon, false);
			if(Double.isNaN(region))
				continue;
			
			Integer rint = new Integer((int) Math.rint(region));
			if(regiontots.containsKey(rint)) 
			{
				double oldval = ((Double) regiontots.get(rint)).doubleValue();
				int oldcount = ((Integer) regioncounts.get(rint)).intValue();
				regioncounts.put(rint, new Integer(oldcount+1));
				regiontots.put(rint, new Double((oldval*oldcount+price)/(oldcount+1)));
			}
			else
			{
				regioncounts.put(rint, new Integer(1));
				regiontots.put(rint, new Double(price));
			}
		}
		
		//now print out the results
		Iterator keyit = regioncounts.keySet().iterator();
		while(keyit.hasNext()) 
		{
			Integer key = (Integer) keyit.next();
			int count = ((Integer) regioncounts.get(key)).intValue();
			double avgprice = ((Double) regiontots.get(key)).doubleValue();
			System.out.println(key+" "+avgprice+" "+count);
		}
		
		
	}

}
