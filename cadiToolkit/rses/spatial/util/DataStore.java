package rses.spatial.util;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

import rses.Debug;
import rses.spatial.GISLayer;
import rses.spatial.gui.NoSuchEntryException;
import rses.util.Util;

/**
 * 
 * In effect, functions like a database.
 * 
 * The whole thing is just a series of tables, with each table having key -->
 * value mappings.
 * 
 * But there can also be a number of raster layers also.
 * 
 * Everything in the region membership based layers is mapped by string.
 * 
 * <hr>
 * Here is the expected layout:
 *
 * a directory called globals which, if present, can contain any number of NAME->VALUE ".tab"
 * files specifying the value of global (non-spatial) variables. Even if the globals directory
 * is not there, a globals table is still created, and populated with at least the following:
 * <ul>
 * 	<li> An Integer[] array for each membership layer specifying the valid regions in that layer. The
 * key to get the array is the name of the layer plus "_validregions__". So if the layer is called "BLAH"
 * the key that is put into the globals hashmap is "BLAH_validregions__"
 * </ul>
 * 
 * 
 * 
 * a 'gis' directory which contains:
 * 
 * ".gis" files for each raster layer, plus
 * a separate directory for each polygon-based layer. Within each directory, there must be
 * a membership.gis file specifying membership for each lat/long point, and one or more
 * ".tab" files with REGIONID --> VALUE mappings. 
 * 
 *                      
 * 
 * 
 * 
 * 	    
 * 
 * 
 * 
 * @author peterr
 * 
 */
public class DataStore 
{
	//the vector based layers, each consisting of a membership layer and 1 or more tables
	private HashMap<GISLayer, HashMap<String, GeoDataTable>> layers;
	//keep a list of the valid regions for each layer -- these are the regions that
	//actually appear in that layer.
	private HashMap<GISLayer, String[]> validregionsperlayer = new HashMap<GISLayer, String[]>();
	
	//keep a directoryname->membershiplayer map for each of the above layers
	private HashMap<String, GISLayer> name_to_membershiplayer;
	

	//these are the raster layers that have actual data in them
	private HashMap<String, GISLayer> datalayers;
	
	private HashMap<String, Object> globals;
	

	// create the database
	private void initialize() throws IOException 
	{
		//first get all the GIS Layers
		this.layers = new HashMap<GISLayer, HashMap<String, GeoDataTable>>();
		this.datalayers = new HashMap<String, GISLayer>();
		this.name_to_membershiplayer = new HashMap<String, GISLayer>();

		globals = readGlobalsFromTextFiles();
		readGISLayers(datalayers, layers, name_to_membershiplayer, globals);
		

	}
	
	
	/*
	private static void addFiles(File[] files, HashMap<String, HashMap<String, Object>> tables, String prefix) throws IOException
	{
		for (int i = 0; i < files.length; i++) 
		{
			if(files[i].isDirectory()) {
				if(prefix.length() != 0)
					throw new RuntimeException("cannot have multiply nested database files, only a 2 level directory tree is allowed");
				addFiles(files[i].listFiles(), tables, files[i].getName()+"/");
				continue;
			}
			
			String fname = files[i].getName();
			Debug.println("Looking at file "+fname, Debug.INFO);
			if(fname.substring(fname.length()-3, fname.length()).toLowerCase().equals("tab"))
			{
				String stem = prefix+fname.substring(0, fname.length()-4);
				String tabname = stem;
				if(tables.containsKey(tabname))
					throw new RuntimeException("Duplicate table : "+tabname);
				tables.put(tabname, readTableFromFile(files[i]));
			}
		}
	}*/

	
	
	
	
	private static HashMap<String, Object> readGlobalsFromTextFiles()
	throws IOException 
	{
		HashMap<String, Object> table = new HashMap<String, Object>();
		Debug.println("reading in global user-specified data-base table", Debug.INFO);
		File dbdir = new File("globals");
		if (!dbdir.exists()) {
			Debug.println("No 'globals' directory found... assuming no user-supplied globals",Debug.IMPORTANT);
			return table; //empty table
		}
		File[] dbfiles = dbdir.listFiles();
		for (int i = 0; i < dbfiles.length; i++) 
		{
			//skip directories, read everything else
			if(dbfiles[i].isDirectory())
				continue;
			
			//assume white-space separated by default
			boolean commasep = false;
			if(dbfiles[i].getName().toLowerCase().endsWith(".csv"))
				commasep = true;
			
			BufferedReader rdr = new BufferedReader(new FileReader(dbfiles[i]));
			String line = rdr.readLine();
			while(line != null)
			{
				//skip blank lines and lines that start with a hash
				if(line.trim().length() != 0 && line.charAt(0) != '#')
				{
					Debug.println("READ GLOBALS LINE: "+line, Debug.IMPORTANT);
					String[] words;
					if(commasep)
						words = line.split(",");
					else
						words = Util.getWords(line);
					
					if(words.length != 2)
						Debug.println("Line in "+dbfiles[i].getName()+" has more than 2 entries... ignoring subsequent entries", Debug.IMPORTANT);

					//blank lines in a csv are OK 
					if(words[0].trim().length() == 0)
					{
						if(words[1].trim().length() != 0)
							throw new RuntimeException("No key for value "+words[1]+" in globals file "+dbfiles[i].getName());
						else
							continue;
					}
					
					Object old = table.put(words[0], words[1]);
					if(old != null)
						throw new RuntimeException("Duplicate entries for global key "+words[0]);
				}
				line = rdr.readLine();
			}
			rdr.close();
		}
		return table;
	}


	
	
	
	
	
	
	/**
	 * The format of a database file is just a plain text file with each line
	 * being an entry, with the first value in the entry being the key, and the
	 * next being the value associated with that key. Whitespace delimits values
	 * on each line.
	 * 
	 * 
	 * TODO we really should change these tables so that they map REGION-NAMES to VALUES
	 * (because thats what is in the text files) rather than turning them into internal ID-->VALUE
	 * mappings.  
	 * 
	 * @param dbfile
	 * @return
	 */
	public static HashMap<String, Object> readTableFromFile(File dbfile, HashMap<String, Integer> nametoid, boolean allowStringValues) throws IOException 
	{
		Debug.println("reading " + dbfile.getName(), Debug.DEBUG_NORMAL);
		HashMap<String, Object> table = new HashMap<String, Object>();
		BufferedReader rdr = new BufferedReader(new FileReader(dbfile));
		
		String line = rdr.readLine();
		boolean commasep = false;
		boolean matrix = false;
		if(dbfile.getName().toLowerCase().endsWith(".csv"))
			commasep=true;
		else if(dbfile.getName().toLowerCase().endsWith("matrix.csv"))
			matrix = true;
		
		while (line != null) 
		{
			if(line.charAt(0) == '#') { //a comment line
				line = rdr.readLine();
				continue;
			}
			String[] bits = null;
			if(commasep || matrix) bits = line.split(",");
			else bits = Util.getWords(line);
			
			if(bits.length < 2 || matrix && bits.length < 3)
				throw new RuntimeException("entry in file has incorrectly formatted line -- must be at least 2 entries on each line for a regular file or three for a matrix");
			//if(bits.length > 2)
			//	Debug.println("entry in file has incorrectly formatted line -- too many entries (more than 2) found on a single line. Ignoring additional entries",Debug.IMPORTANT);
			
			//look up by region name
			if(!nametoid.containsKey(bits[0]) || matrix && !nametoid.containsKey(bits[1])) {  
				//throw new RuntimeException("No id for region with name "+bits[0]);
				Debug.println("No id for region with name "+bits[0]+" skipping... ", Debug.IMPORTANT);
				//Util.raw_input();
				line = rdr.readLine();
				continue; //just skip the line, its not a region in our study area
			}
			
			if(!matrix && allowStringValues && bits[1].startsWith("\"") || matrix && allowStringValues && bits[2].startsWith("\""))
			{
				//ok, we expect a string. Make sure it terminates
				if(!matrix && !bits[bits.length-1].endsWith("\"") || matrix && !bits[bits.length-1].endsWith("\"")) {
					Debug.println("Incorrect format for database file.... string doesnt seem to terminate for key "+bits[0], Debug.CRITICAL);
					System.exit(2);
				}
					
				//so we know its a string, starting and ending with double-quotes. So just
				//rip out the bit between the quotes
				String strval = line.substring(line.indexOf('"'+1), line.lastIndexOf('"'));
				String key = bits[0];
				if(matrix) key += " "+bits[1];
				Object old = table.put(key, strval);
				if (old != null)
					throw new RuntimeException("Duplicate mapping in database table for key "+key);				
			}
			else //values must be numeric
			{
				try 
				{
					double val;
					String key = bits[0];
					if(matrix) {
						val = Double.parseDouble(bits[2]);
						key += " "+bits[1];
					}
					else
						val = Double.parseDouble(bits[1]);
					Double vald = new Double(val);
					Object old = table.put(key, vald);
					if (old != null)
						throw new RuntimeException("Duplicate mapping in database table for key "+bits[0]);				
				}
				catch(NumberFormatException nfe) {
					Debug.println("Incorrect format for database file.... expected a number, got "+bits[1], Debug.CRITICAL);
					System.exit(2);
				}
			}
			line = rdr.readLine();
		}
		rdr.close();
		return table;
	}

	
	
	
	/**
	 * Read in any GISLayers in the regions directory. DataStore clients can query
	 * spatially on lat/long
	 * 
	 * @param tables
	 * @throws IOException
	 */
	public void readGISLayers(HashMap<String, GISLayer> rasters, HashMap<GISLayer, HashMap<String, GeoDataTable>> layers, HashMap<String, GISLayer> nametombr, HashMap<String, Object> globals) throws IOException 
	{
		Debug.println("reading in available GIS layers", Debug.INFO);
		File gisdir = new File("gis");
		if (!gisdir.exists()) 
			throw new RuntimeException("No "+gisdir.getName()+" directory. Aborting");

		File[] gisfiles = gisdir.listFiles();
		
		for (int i = 0; i < gisfiles.length; i++) 
		{
			//for each directory, read in the membership layer and any associated tables
			if(gisfiles[i].isDirectory()) 
			{
				Debug.println("Decending into directory "+gisfiles[i].getAbsolutePath(), Debug.INFO);
				int numlyr = nametombr.size();
				Integer[] validregions = readVectorLayer(gisfiles[i], layers, nametombr);
				
				//sanity check
				if(nametombr.size() == numlyr+1) //we added a layer
				{
					if(validregions == null) throw new IllegalStateException("Layer added ("+gisfiles[i].getName()+") has null valid regions.. should be impossible");
					if(validregions.length == 0) throw new RuntimeException("Layer added ("+gisfiles[i].getName()+") has no valid regions....");
					
					globals.put(gisfiles[i].getName()+"_validregions__", validregions);
				}
				else if(nametombr.size() == numlyr)
				{
					//didnt add a region
					if(validregions != null) throw new IllegalStateException("Have valid regions for "+gisfiles[i].getName()+" but no membership file added. Should be impossible");					
				}
				else
					throw new IllegalStateException("Impossible case. Internal error.");
				
								
				Debug.println("Came back up to "+gisdir.getAbsolutePath(), Debug.INFO);
				continue;
			}
			
			//else
			//for each file, read it only if it is a gis raster file
			String[] filebits = gisfiles[i].getName().split("\\.");
			if (filebits[filebits.length - 1].toLowerCase().equals("gis")) 
			{
				Debug.println("Reading in raster gis layer "+gisfiles[i].getName(), Debug.INFO);
				String tabname = gisfiles[i].getName().substring(0,
						gisfiles[i].getName().length() - 4);
				rasters.put(tabname, GISLayer.readFromFile(gisfiles[i].getCanonicalPath()));
			}
			else
				Debug.println("Skipping "+gisfiles[i].getName(), Debug.INFO);
		}
	}


	/** Read a vector layer and any associated tabled from a directory.
	 * Add all tables found to the 'layers' LIst of GeoDataTables.
	 * 
	 * @param directory
	 * @param layers
	 * @throws java.io.IOException
	 */
	public Integer[] readVectorLayer(File directory, HashMap<GISLayer, HashMap<String, GeoDataTable>> layers, HashMap<String, GISLayer> nametombr) throws 
	java.io.IOException
	{
		File[] files = directory.listFiles();
		
		GISLayer membershiplayer = null;
		Integer[] validregions = null;
		
		Debug.println("Looking at files in vector layer in directory "+directory.getAbsolutePath(), Debug.INFO);
		
		//go through and get the membership layer first
		for(int i = 0; i < files.length; i++)
		{
			if(files[i].getName().equals("membership.gis")) 
			{
				Debug.println("Found membership GIS file.. processing that", Debug.INFO);
				membershiplayer = GISLayer.readFromFile(files[i].getAbsolutePath());
				nametombr.put(directory.getName(), membershiplayer);
				layers.put(membershiplayer, new HashMap<String, GeoDataTable>());
				//now loop through the layer and get a list of all the valid regions
				Debug.println("Looking for valid regions in membership gis file", Debug.INFO);
				ArrayList<Number> uniqvals = membershiplayer.getValues();
				validregions = new Integer[uniqvals.size()];
				for(int j = 0; j < validregions.length; j++)
				{
					Number f = uniqvals.get(j);
					
					//check there are no fractions
					if(f.floatValue() - f.intValue() != 0.0) 
						throw new RuntimeException("Membership layer must have region identifiers that are whole integers less than "+((int) Math.pow(2, 24)));
					
					//check that there are no negative numbers
					if(f.floatValue() < 0)
						throw new RuntimeException("Membership value was less that zero!! Should never happen -- membership indices should start from 0");
					
					//ok, it's in the right range and is a whole integer
					validregions[j] = f.intValue();
				}
				
				//make sure that there are as many or more category names than valid regions.
				//and make sure that the maximum category index is less than the length of the category name 
				if(validregions.length > membershiplayer.getCategoryNames().length || Util.getMax(validregions) >= validregions.length)
					throw new RuntimeException("Number of distinct regions greater thah number of category names!! There are "+membershiplayer.getCategoryNames().length+" category names and "+validregions.length+" valid regions!");
				
				//found membership layer, so stop looking
				Debug.println("Finished processing membership file.. there were "+validregions.length+" unique regions", Debug.INFO);
				break;
			}
		}
	
		//make sure we found a membership layer
		if(membershiplayer == null)
		{
			Debug.println("No membership layer found in directory "+directory.getAbsolutePath()+" hit ENTER to confirm that this directory will be skipped", Debug.IMPORTANT);
			Util.raw_input();
			return null;
		}
		
		//ok, found membership layer, now go through and add all the tables

		//now do the data tables
		Debug.println("Looking for data tables for this layer...", Debug.INFO);
		ArrayList<String> pathstr = new ArrayList<String>();
		getTables(pathstr, directory, membershiplayer, layers);
		
		
		//use the valid regions to create a list of valid region names as well.
		//then return the valid regions for this membership layer (calculated earlier in this function)
		String[] validregionnames = new String[validregions.length];
		for(int j = 0; j < validregions.length; j++)
			validregionnames[j] = membershiplayer.getCategoryNames()[validregions[j]];
		this.validregionsperlayer.put(membershiplayer, validregionnames);

		return validregions;
	}
	
	
	//add all the tables from the current directory
	private static void getTables(ArrayList<String> dirpath, File curdir, GISLayer membership, HashMap<GISLayer, HashMap<String, GeoDataTable>> layers)
	throws IOException
	{
		Debug.println("Looking for tables in "+curdir.getAbsolutePath()+" dirpath.size()="+dirpath.size(), Debug.EXTRA_INFO);
		
		File[] files = curdir.listFiles();

		//get a name to region id map for this membership layer
		HashMap<String, Integer> nametoid = new HashMap<String, Integer>();
		for(int i = 0; i < membership.getCategoryNames().length; i++)
			nametoid.put(membership.getCategoryNames()[i], new Integer(i));
		

		for(int i =0; i < files.length; i++)
		{
			if(files[i].isDirectory())
			{
				ArrayList<String> dirpath2 = (ArrayList<String>) dirpath.clone();
				dirpath2.add(files[i].getName());
				
				Debug.println("Found directory "+files[i].getAbsolutePath()+", descending...", Debug.INFO);
				getTables(dirpath2, files[i], membership, layers);
			}
			else //its a file
			{
				Debug.println("Processing file "+files[i].getName(), Debug.INFO);
				if(files[i].getName().indexOf("/") >= 0)
					throw new RuntimeException("File name contains a forward slash.... this is not allowed");
								
				//otherwise, if its a table, then read it in
				String[] filebits = files[i].getName().split("\\.");
				if (filebits[filebits.length - 1].toLowerCase().equals("tab") || filebits[filebits.length - 1].toLowerCase().equals("csv")) 
				{
					String tabname = files[i].getName().substring(0,files[i].getName().length() - 4);
					if(Util.getIndex(tabname, reservedTables) >= 0)
						throw new RuntimeException("Cannot have table with name "+tabname+" -- this is a reserved name");
					
					for(int j =dirpath.size()-1; j >= 0; j--) tabname = dirpath.get(j)+"/"+tabname;
					
					Debug.println("Table name is "+tabname+" ... reading data", Debug.INFO);
					HashMap<String, Object> keyvaltab = readTableFromFile(files[i], nametoid, true);
					Debug.println("Read "+keyvaltab.size()+" values in table", Debug.INFO);
					GeoDataTable geotab = new GeoDataTable(tabname, membership, keyvaltab);
					Debug.println("Adding "+tabname, Debug.IMPORTANT);
					layers.get(membership).put(tabname, geotab);
				}
				else
					Debug.println("Not a table, skipping", Debug.INFO);
			}
		}
	}
	
	
	
	
	
	public DataStore() throws IOException {
		initialize();
	}

	
	public static final String CENTROID_X_TABLENAME = "centroid_X";
	public static final String CENTROID_Y_TABLENAME = "centroid_Y";	
	public static final String[] reservedTables = new String[] {CENTROID_X_TABLENAME, CENTROID_Y_TABLENAME};
	
	
	public GeoDataTable[] getCentroidsTables(String layer)
	{		
		//if we havent worked out centroids already, do it.
		if(!this.hasTable(layer, CENTROID_X_TABLENAME) || !this.hasTable(layer, CENTROID_Y_TABLENAME))
		{
			java.util.Map<String, double[]> centroids = getVectorLayer(layer).getRegionCentroids();
			Iterator<String> regions = centroids.keySet().iterator();
			java.util.Map<String, Object> X = new java.util.HashMap<String, Object>();
			java.util.Map<String, Object> Y = new java.util.HashMap<String, Object>();
			while(regions.hasNext())
			{
				String region = regions.next();
				double[] yx = centroids.get(region);
				if(yx == null)
					continue;
				X.put(region, yx[1]);
				Y.put(region, yx[0]);
			}
			GeoDataTable tabX = new GeoDataTable(CENTROID_X_TABLENAME, this.getVectorLayer(layer), X);
			GeoDataTable tabY = new GeoDataTable(CENTROID_Y_TABLENAME, this.getVectorLayer(layer), Y);
			
			this.addTable(tabX);
			this.addTable(tabY);
		}
		
		return new GeoDataTable[] {this.getTable(layer, CENTROID_X_TABLENAME), this.getTable(layer, CENTROID_Y_TABLENAME)};
		

	}
	
	
	
	
	public GeoDataTable getTable(String layer, String table)
	{
		GISLayer gislayer = this.name_to_membershiplayer.get(layer);
		return this.layers.get(gislayer).get(table);
	}
	

	public void addTable(GeoDataTable values) 
	{
		this.addTable(values, false);
	}
	
	private void addTable(GeoDataTable values, boolean replaceexisting) 
	{
		//if we dont know about the membership layer that this geodatatable uses
		//then we treat that as an error
		if(!this.layers.containsKey(values.getMembershipLayer()))
		{
			throw new RuntimeException("Trying to add GeoDataTable which has a membership layer we dont know about. Aborting");
		}	
		else
		{
			//check for duplicates 
			if(this.layers.get(values.getMembershipLayer()).containsKey(values.getName()))
			{
				//got duplicate... whether we replace or barf depends on boolean replaceexisting param
				
				//just replace
				if(replaceexisting) 
					this.layers.get(values.getMembershipLayer()).put(values.getName(), values);
				//barf
				else
					throw new RuntimeException("Trying to add table "+values.getName()+" that is already in datastore");
			}
			else
			{
				layers.get(values.getMembershipLayer()).put(values.getName(), values);
			}
		}
	}
	
	
	public void replaceTable(GeoDataTable values)
	{
		this.addTable(values, true);
	}
	

	
	
	public boolean hasLayer(String layername)
	{
		return (this.name_to_membershiplayer.get(layername) != null) || (this.datalayers.get(layername) != null);
	}
	
	
	public boolean hasTable(String layername, String tablename)
	{
		GISLayer layer = this.name_to_membershiplayer.get(layername);
		if(layer == null) return false;
		
		return this.layers.get(layer).get(tablename) != null;
		
	}
	
	
	
	/** Special lookup where the result may not be a double.
	 *  This allows for there to be tables in the database
	 *  with values other than doubles.
	 *  
	 *  The caller is responsible for making sense of the
	 *  result.
	 * 
	 * @param tablename
	 * @param key
	 * @return
	 */
	public Object lookupGlobal(String key)
	{
		return globals.get(key);
	}
	

	public Object lookupArbitrary(String layername, String tablename, String key)
	{
		return this.lookupArbitrary(layername, tablename, key, true); //barf by default
	}
	
	
	/**
	 * 
	 * @param layername
	 * @param tablename
	 * @param key
	 * @return
	 */
	public Object lookupArbitrary(String layername, String tablename, String key, boolean barfIfNoMapping)
	{
		GISLayer layer = this.name_to_membershiplayer.get(layername);
		GeoDataTable table = layers.get(layer).get(tablename);
		
		if (table == null)
			throw new RuntimeException("Specified table '" + tablename+ "' does not exist");

		Object entry = table.lookup(key);
		if(entry == null && barfIfNoMapping)
			throw new NoSuchEntryException("No entry found in table "+tablename+" for key "+key);
		
		return entry;

	}
	
	
	/**
	 * For multiple lookups, much better to use
	 * 
	 * @link #lookup(String, String[])
	 * 
	 * @param tablename
	 * @param key
	 * @return
	 */
	/*public double lookupByRegionIndex(String layername, String tablename, String key) {
		return lookupByRegionIndex(layername, tablename, new String[] { key })[0];
	}*/

	/**
	 * Perform multiple lookups on a table
	 * 
	 * @param tablename
	 * @param keys
	 * @return
	 */
	/*public double[] lookupByRegionIndex(String layername, String tablename, String[] keys) 
	{
		GISLayer layer = this.name_to_membershiplayer.get(layername);
		GeoDataTable table = layers.get(layer).get(tablename);
		
		if (table == null)
			throw new RuntimeException("Specified table '" + tablename+ "' does not exist");
		double[] res = new double[keys.length];
		for (int i = 0; i < res.length; i++)
		{
			Object entry = table.lookup(keys[i]);
			if(entry == null)
				throw new NoSuchEntryException("No entry found in table "+tablename+" for key "+keys[i]);
			res[i] = ((Double) entry).doubleValue();
		}
		return res;
	}*/
	
	
	
	

	/**
	 * Perform a single lookup on a raster GISLayer. For multiple querys, its more
	 * efficient to use
	 * 
	 * @link #lookup(String, double[][])
	 * 
	 * @param tablename
	 * @param lat
	 * @param lon
	 * @return
	 */
	/*public float lookupByRegionIndex(String tablename, double lat, double lon) {
		return lookupByRegionIndex(tablename, new double[][] { { lat, lon } })[0];
	}*/

	/**
	 * Perform multiple lookups on a GISLayer
	 * 
	 * @param tablename
	 * @param latlongs
	 * @return
	 */
	/*public float[] lookupByRegionIndex(String tablename, double[][] latlongs) 
	{
		GISLayer layer = this.datalayers.get(tablename);
		if (layer == null)
			throw new RuntimeException("Specified table '" + tablename+ "' does not exist");

		float[] res = new float[latlongs.length];
		for (int i = 0; i < latlongs.length; i++) 
		{
			double lat = latlongs[i][0];
			double lon = latlongs[i][1];
			if (lat < layer.getMinLat() || lat > layer.getMaxLat()
					|| lon < layer.getMinLong() || lon > layer.getMaxLong()) 
			{
				Debug.println("GIS query outside of lat/long bounds for layer "+ tablename + " ... treating as missing value",
						Debug.IMPORTANT);
				res[i] = Float.NaN;
			} 
			else
				res[i] = layer.getValue(lat, lon);
		}
		return res;
	}*/

	
	
	
	/**
	 * 
	 * @param tablename
	 * @param key
	 * @param val
	 * @param barfifvaluenotalreadypresent
	 * @return true if there was already a value present in the table for this key
	 */
	public boolean replaceValue(String layername, String tablename, String key, Object val, boolean barfifvaluenotalreadypresent)
	{
		GISLayer mbr = this.name_to_membershiplayer.get(layername);
		if(mbr == null)
			throw new RuntimeException("Tried to replace value in nonexistant layer  -- "+layername);
			
		GeoDataTable table = this.layers.get(mbr).get(tablename);
		if(table == null)
			throw new RuntimeException("Tried to replace value in nonexistant table -- "+tablename);
		
		Object old = table.set(key, val);
		if(old == null && barfifvaluenotalreadypresent)
			throw new RuntimeException("There was no old value for key "+key+" in table "+tablename);
		return old != null;
	}
	
	
	
	public java.util.List<String> getVectorLayers()
	{
			java.util.List<String> vectorlayers = new ArrayList<String>();
			
			Iterator<String> keyit = this.name_to_membershiplayer.keySet().iterator();
			while(keyit.hasNext())
				vectorlayers.add(keyit.next());
			
			return vectorlayers;
	}
	
	public GISLayer getVectorLayer(String name)
	{
		return this.name_to_membershiplayer.get(name);
	}
	
	
	
	public HashMap<String, GeoDataTable> getTablesForLayer(String layername)
	{
		GISLayer layer = name_to_membershiplayer.get(layername);
		HashMap<String, GeoDataTable> tables = this.layers.get(layer);
		return tables;
	}
	
	
	/* Get the valid regions for a layer -- that is, the regions
	 * that actually appear at lest once in the layer
	 * 
	 */
	public String[] getValidRegionsForLayer(String layername)
	{
		GISLayer layer = name_to_membershiplayer.get(layername);
		return this.validregionsperlayer.get(layer);
	}
	
	
	/**
	 * Dump everything (except gis data) in the database to a file
	 *
	 */
	public void dumpOutEverything(PrintWriter stream)
	{
		//print all the raster layers first
		Iterator<String> rasternames = this.datalayers.keySet().iterator();
		while(rasternames.hasNext()) 
			stream.println("RASTER LAYER: '"+rasternames.next()+"'");
		
		//now print the globals table, if any
		stream.println("GLOBALS:");
		Iterator<String> keys = globals.keySet().iterator();
		while(keys.hasNext()) {
			String key = keys.next();
			stream.println("\tGLOBALS\t"+key+"  -->  "+globals.get(key));
		}
		
		//now print all the other tables
		Iterator<String> layernames = this.name_to_membershiplayer.keySet().iterator();
		while(layernames.hasNext())
		{
			String layername = layernames.next();
			stream.println(layername+":");
			
			GISLayer layer = name_to_membershiplayer.get(layername);
			Iterator<String> tablenames = layers.get(layer).keySet().iterator();
			
			while(tablenames.hasNext())
			{
				String tablename = tablenames.next();
				stream.println("\t"+layername+"\t"+tablename);
				GeoDataTable table = layers.get(layer).get(tablename);
				Iterator<String> keyit = table.getunderlyingMappings().keySet().iterator();
				while(keyit.hasNext())
				{
					String k = keyit.next();
					Object v = table.getunderlyingMappings().get(k);
					stream.println("\t\t"+layername+"\t"+tablename+"    "+k+" --> "+v);
				}
			}
		}		
	}
	
	
	
	
	
	
}
