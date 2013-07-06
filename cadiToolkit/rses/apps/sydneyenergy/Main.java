package rses.apps.sydneyenergy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import rses.Debug;
import rses.math.MathUtil;
import rses.spatial.GISLayer;
import rses.util.Util;




public final class Main
{
	private Main() throws Exception {
		throw new IllegalAccessException("Cannot be instantiated");
	}
	
	
	
	/** Each line in the file must be of the format 
	 * REGIONID LAT LONG REGION_NAME 
	 * 
	 * REGIONID must be a numerical ID (Strings are not supported for region ids).
	 * 
	 * REGION_NAME CAN BE ANY STRING (including spaces)
	 * 
	 * @param filename
	 * @return
	 * @throws IOException
	 */
	public static HashMap readRegionCentroidFile(String filename) throws IOException
	{
		File f = new File(filename);
		if(!f.exists())
			throw new RuntimeException("Cannot find region file '"+filename+"'");
		
		HashMap centroids = new HashMap();
		BufferedReader rdr = new BufferedReader(new FileReader(filename));
		String line = rdr.readLine();
		while(line != null)
		{
			try {
				String[] words = Util.getWords(line);
				int id = Integer.parseInt(words[0]);
				double lat = Double.parseDouble(words[1]);
				double lon = Double.parseDouble(words[2]);
				String name = "";
				for(int i = 3; i < words.length; i++)
					name = name+words[i]+" ";
				name = name.trim();
				centroids.put(new Integer(id), new Object[] {name, new double[] {lat, lon}});
			}
			catch(Exception e) {
				Debug.println("Could not read region file '"+filename+"' -- suspect incorrect format.. please read the documentation", Debug.CRITICAL);
				System.exit(-1);
			}
			line = rdr.readLine();
		}
		rdr.close();
		return centroids;
	}

	
	
	
	/** returns a map that maps each region id to a Double
	 * value that is the total energy consumed by citizens
	 * in that region.
	 * 
	 * @param transport
	 * @param house
	 * @param regionids
	 * @return
	 */
	public HashMap calcEnergyByRegion(TransportEnergyModule transport, 
			HousingEnergyModule house, Integer[] regionids)
	{
		HashMap result = new HashMap();
		//go through each region and calculate the energy
		for(int i =0; i < regionids.length; i++)
		{
			Integer key = regionids[i];
			double transportenergy = transport.calcEnergyForRegion(key);
			double housingenergy = house.calcEnergyForRegion(key); 
			result.put(key, new Double(transportenergy+housingenergy));
		}
		return result;
	}

	/* Work out the area, in square km, for each region, and add this info to the
	 * database.
	 * 
	 * 
	 */
	private static Integer[] addRegionAreasAndCalculateValidRegions(GISLayer regionmembership, DataStore database)
	{
		HashMap areainfo = new HashMap();
		double latsize = (regionmembership.getMaxLat()-regionmembership.getMinLat())/regionmembership.getLatSteps();
		double lonsize = (regionmembership.getMaxLong()-regionmembership.getMinLong())/regionmembership.getLongSteps();
		Debug.println("lat increment per grid square is "+latsize+" degrees", Debug.INFO);
		Debug.println("lon increment per grid square is "+lonsize+" degrees", Debug.INFO);
		double centerlat = (regionmembership.getMinLat()+regionmembership.getMaxLat())/2;
		double centerlon = (regionmembership.getMinLong()+regionmembership.getMaxLong())/2;
		double latgridkm = MathUtil.getDistanceBetweenPointsOnEarth(centerlat, centerlon, centerlat+latsize, centerlon)/1000.0;
		double longridkm = MathUtil.getDistanceBetweenPointsOnEarth(centerlat, centerlon, centerlat, centerlon+lonsize)/1000.0;
		Debug.println("Lat km for 1 grid square is "+latgridkm, Debug.INFO);
		Debug.println("Lon km for 1 grid square is "+longridkm, Debug.INFO);
		double gridsizesqkm = latgridkm*longridkm;
		Debug.println("Each grid square is "+gridsizesqkm+" square kilometres", Debug.INFO); 
		
		double[] totals = new double[(int) Math.rint(regionmembership.getMaxVal())+1];
		float[][] data = regionmembership.continuousdata;
		for(int i = 0; i < data.length; i++)
			for(int j = 0; j < data[0].length; j++)
			{
				if(!Float.isNaN(data[i][j]))
				{
					int region = (int) Math.rint(data[i][j]);
					totals[region] += gridsizesqkm;
				}
			}
		
		ArrayList validregions = new ArrayList();
		for(int i =0; i < totals.length; i++)
			if(totals[i] > 0) {
				areainfo.put(""+i, new Double(totals[i]));
				validregions.add(new Integer(i));
			}
		
		if(!database.hasTable("tz_area_sqkm"))
			database.addTable("tz_area_sqkm", areainfo);
		else
			areainfo = null;
		
		Integer[] res = new Integer[validregions.size()];
		for(int i = 0; i < res.length; i++)
			res[i] = (Integer) validregions.get(i);
		return res;
	}
	
	
	/* Work out the distance from the CBD for each TZ centroid
	 * 
	 */
	private static void addDistanceToCBD(DataStore database, HashMap regioncentroidsandnames)
	{
		if(database.hasTable("distcbd"))
			return; //the user has calculated it already in a file, so use those values
		
		HashMap distcbdtable = new HashMap();
		
		double dist = 0;
		java.util.Iterator keys = regioncentroidsandnames.keySet().iterator();
		while(keys.hasNext())
		{
			Integer key = (Integer) keys.next();
			Object[] match = (Object[]) regioncentroidsandnames.get(key);
			String name = (String) match[0];
			double[] latlon = (double[]) match[1];
			double zlat = latlon[0];
			double zlon = latlon[1];
			
			try {
				double cbdlat = Double.parseDouble((String) database.lookupArbitrary("globals", "cbdlat"));
				double cbdlon = Double.parseDouble((String) database.lookupArbitrary("globals", "cbdlong"));
				dist = MathUtil.getDistanceBetweenPointsOnEarth(cbdlat, cbdlon, zlat, zlon)/1000.0;
			}
			catch(NoSuchEntryException nsee) {
				Debug.println("Missing required global value for cbdlat or cbdlong", Debug.CRITICAL);
				System.exit(1);
			}

			//ok now stick it in the database
			distcbdtable.put(key.toString(), new Double(dist));
		}
		
		database.addTable("distcbd", distcbdtable);
	}
	
	
	public static void main(String[] args) throws Exception
	{
		boolean batch = false;
		if(args.length > 0 && args[0].equalsIgnoreCase("run"))
			batch = true;
		
		Debug.setDebugLevel(Debug.DEBUG_SUSPICIOUS);
		Debug.setVerbosityLevel(Debug.INFO);
		
		//first we create the data store
		DataStore database = new DataStore();

		//database must have a gis layer with region membership
		if(!database.hasTable("regions"))
			throw new RuntimeException("No gis layer 'regions.gis' found with region membership information. I cannot run without this.");
		if(!database.hasTable("placemarks"))
			Debug.println("No placemark file found.... skipping", Debug.IMPORTANT);
		
		rses.spatial.GISLayer regionmembership = database.getGISLayer("regions");
		rses.spatial.GISLayer placemarkgis = database.getGISLayer("placemarks");
		Integer[] validregions = addRegionAreasAndCalculateValidRegions(regionmembership, database); //work out the area of each zone
		database.getTable("globals").put("validregions", validregions);
		
		//now read in the region centroid file and work out the region ids
		//So far, we actually dont use the centroids.... even though
		//we read them in
		String regionfile = "regions.dat";
		Debug.println("Looking for region IDs and centroids in "+regionfile, Debug.IMPORTANT);
		HashMap regionCentroidsAndNames = readRegionCentroidFile(regionfile);
		Debug.println("Successfully read region IDs and centroids from "+regionfile, Debug.IMPORTANT);
		addDistanceToCBD(database, regionCentroidsAndNames);
		Integer[] regionids = new Integer[regionCentroidsAndNames.size()];
		HashMap names = new HashMap();
		java.util.Iterator keys = regionCentroidsAndNames.keySet().iterator();
		int keycount = 0;
		while(keys.hasNext()) 
		{
			Integer key = (Integer) keys.next();
			Object[] val = (Object[]) regionCentroidsAndNames.get(key);
			names.put(key, (String) val[0]);
			regionids[keycount] = key;
			keycount++;
		}
		//now we create our Transport and Housing Models
		TransportEnergyModule transport = new TransportEnergyModule(database, regionids);
		HousingEnergyModule housing = new HousingEnergyModule(database, regionids);
				
		
		int[][] membership = DisplayableModel.getMembershipMatrix(regionmembership, regionmembership.getLongSteps(), regionmembership.getLatSteps());
		int[][] placemarks = null;
		if(placemarkgis != null) 
		{
			if(placemarkgis.getLatSteps() != regionmembership.getLatSteps() || 
			   placemarkgis.getLongSteps() != regionmembership.getLongSteps())
				throw new RuntimeException("Placemark and Region membership files do not match (i.e. differing resolution)");
			placemarks = DisplayableModel.getMembershipMatrix(placemarkgis, placemarkgis.getLongSteps(), placemarkgis.getLatSteps());
			Debug.println("Got placemark positions..."+placemarks, Debug.INFO);
		}
		
		
		RegionalEnergyModel regionenergy = new RegionalEnergyModel(
				housing, transport, regionids, database);
		regionenergy.recalculate();
		
		HouseholdLocationModel mainmodel = null;
		//try { 
			mainmodel = new HouseholdLocationModel(database); 
		//}
		//catch(Exception e) { 
		//	System.err.println("WARNING!!!! FATAL ERROR initializing HouseholdLocationModel.. HIT ENTER TO CONTINUE ANYWAY");
		//	new BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
		//}
		
		
		if(!batch)
		{
			//ok, now launch the main window
			MainWindow mainwindow = new MainWindow(800, 800, mainmodel, regionenergy, database, membership, placemarks, names);
			mainwindow.setVisible(true);
		}
		else {
			mainmodel.run(database);
			System.exit(0);
		}
	}
}