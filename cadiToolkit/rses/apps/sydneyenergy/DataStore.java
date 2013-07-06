package rses.apps.sydneyenergy;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import rses.Debug;
import rses.spatial.GISLayer;
import rses.util.Util;

/**
 * Stores all the demographic and spatial (and any other) data needed for the
 * energy simulation.
 * 
 * In effect, functions like a database.
 * 
 * The whole thing is just a series of tables, with each table having key -->
 * value mappings.
 * 
 * Everything is mapped by string.
 * 
 * @author peterr
 * 
 */
public final class DataStore {
	// the tables. Each table is itself a HashMap
	private HashMap tables;

	// GISLayers
	private HashMap gislayers;

	// create the database
	private void initialize() throws IOException {
		this.tables = new HashMap();
		readTablesFromTextFiles(tables);
		HashMap globals = readGlobalsFromTextFiles();
		if(globals != null)
			tables.put("globals", globals);
		this.gislayers = new HashMap();
		readGISLayers(gislayers);
	}
	
	
	// look in the directory 'db' and load any
	// information in them
	private static void readTablesFromTextFiles(HashMap tables)
			throws IOException 
	{
		Debug.println("reading in user-specified data-base tables", Debug.INFO);
		File dbdir = new File("db");
		if (!dbdir.exists()) {
			Debug.println("No 'db' directory found... assuming no user-supplied database files",Debug.IMPORTANT);
			return;
		}
		File[] dbfiles = dbdir.listFiles();
		addFiles(dbfiles, tables, "");
	}
	
	private static void addFiles(File[] files, HashMap tables, String prefix) throws IOException
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
	}

	
	
	
	
	private static HashMap readGlobalsFromTextFiles()
	throws IOException 
	{
		HashMap table = new HashMap();
		Debug.println("reading in global user-specified data-base table", Debug.INFO);
		File dbdir = new File("globals");
		if (!dbdir.exists()) {
			Debug.println("No 'globals' directory found... assuming no user-supplied globals",Debug.IMPORTANT);
			return null;
		}
		File[] dbfiles = dbdir.listFiles();
		for (int i = 0; i < dbfiles.length; i++) 
		{
			BufferedReader rdr = new BufferedReader(new FileReader(dbfiles[i]));
			String line = rdr.readLine();
			while(line != null)
			{
				if(line.charAt(0) != '#')
				{
					String[] words = Util.getWords(line);
					if(words.length != 2)
						Debug.println("Line in "+dbfiles[i].getName()+" has more than 2 entries... ignoring subsequent entries", Debug.IMPORTANT);
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
	 * @param dbfile
	 * @return
	 */
	public static HashMap readTableFromFile(File dbfile) throws IOException 
	{
		Debug.println("reading " + dbfile.getName(), Debug.INFO);
		HashMap table = new HashMap();
		BufferedReader rdr = new BufferedReader(new FileReader(dbfile));
		String line = rdr.readLine();
		while (line != null) 
		{
			if(line.charAt(0) == '#') { //a comment line
				line = rdr.readLine();
				continue;
			}
			String[] bits = Util.getWords(line);
			if(bits.length < 2)
				throw new RuntimeException("entry in file has incorrectly formatted line -- must be at least 2 entries on each line");
			if(bits.length > 2)
				Debug.println("entry in file has incorrectly formatted line -- too many entries (more than 2) found on a single line. Ignoring additional entries",Debug.IMPORTANT);
			try {
				double val = Double.parseDouble(bits[1]);
				Double vald = new Double(val);
				Object old = table.put(bits[0], vald);
				if (old != null)
					throw new RuntimeException("Duplicate mapping in database table for key "+bits[0]);				
			}
			catch(NumberFormatException nfe) {
				Debug.println("Incorrect format for database file.... expected a number, got "+bits[1], Debug.CRITICAL);
				System.exit(2);
			}
			line = rdr.readLine();
		}
		rdr.close();
		return table;
	}

	/**
	 * Read in any GISLayers in the gis directory. DataStore clients can query
	 * spatially on lat/long
	 * 
	 * @param tables
	 * @throws IOException
	 */
	public static void readGISLayers(HashMap tables) throws IOException {
		Debug.println("reading in available GIS layers", Debug.INFO);
		File gisdir = new File("gis");
		if (!gisdir.exists()) {
			Debug.println("No 'gis' directory found... assuming no GIS layers",
					Debug.IMPORTANT);
			return;
		}
		File[] gisfiles = gisdir.listFiles();
		for (int i = 0; i < gisfiles.length; i++) {
			String[] filebits = gisfiles[i].getName().split("\\.");
			if (filebits[filebits.length - 1].toLowerCase().equals("gis")) {
				String tabname = gisfiles[i].getName().substring(0,
						gisfiles[i].getName().length() - 4);
				tables.put(tabname, GISLayer
						.readFromFile(gisfiles[i].getCanonicalPath()));
			}
		}
	}

	public DataStore() throws IOException {
		initialize();
	}

	
	public void addTable(String tablename, HashMap values) {
		if(this.tables.containsKey(tablename))
			throw new RuntimeException("Table "+tablename+" already exists");
		
		this.tables.put(tablename, values);
	}
	
	
	public void replaceTable(String tablename, HashMap values)
	{
		if(!this.tables.containsKey(tablename))
			throw new RuntimeException("Table "+tablename+" doesnt exists, so I can hardly replace it...");
		
		this.tables.put(tablename, values);
	}
	
	public boolean hasTable(String tablename) {
		return this.tables.containsKey(tablename)
				|| this.gislayers.containsKey(tablename);
	}

	/** Get the names of all the tables that represent
	 *  data at the region level.
	 *  
	 *  i.e. exclude GIS layers and globals.
	 * 
	 * @return
	 */
	public String[] getRegionTableNames() 
	{
		String[] res = new String[this.tables.size()-1];
		
		int count = 0;
		Iterator keyit = this.tables.keySet().iterator();
		while(keyit.hasNext())
		{
			String key = (String) keyit.next();
			if(key.equalsIgnoreCase("globals"))
				continue;
			else
				res[count++] = key;
		}
		return res;
	}
	
	
	
	public GISLayer getGISLayer(String name) {
		return (GISLayer) this.gislayers.get(name);
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
	public Object lookupArbitrary(String tablename, String key)
	{
		HashMap table = (HashMap) this.tables.get(tablename);
		if (table == null)
			throw new RuntimeException("Specified table '" + tablename
					+ "' does not exist");
		
		Object val = table.get(key);
		return val;
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
	public double lookup(String tablename, String key) {
		return lookup(tablename, new String[] { key })[0];
	}

	/**
	 * Perform multiple lookups on a table
	 * 
	 * @param tablename
	 * @param keys
	 * @return
	 */
	public double[] lookup(String tablename, String[] keys) {
		HashMap table = (HashMap) this.tables.get(tablename);
		if (table == null)
			throw new RuntimeException("Specified table '" + tablename
					+ "' does not exist");
		double[] res = new double[keys.length];
		for (int i = 0; i < res.length; i++)
		{
			Object entry = table.get(keys[i]);
			if(entry == null)
				throw new NoSuchEntryException("No entry found in table "+tablename+" for key "+keys[i]);
			res[i] = ((Double) entry).doubleValue();
		}
		return res;
	}

	/**
	 * Perform a single lookup on a GISLayer. For multiple querys, its more
	 * efficient to use
	 * 
	 * @link #lookup(String, double[][])
	 * 
	 * @param tablename
	 * @param lat
	 * @param lon
	 * @return
	 */
	public float lookup(String tablename, double lat, double lon) {
		return lookup(tablename, new double[][] { { lat, lon } })[0];
	}

	/**
	 * Perform multiple lookups on a GISLayer
	 * 
	 * @param tablename
	 * @param latlongs
	 * @return
	 */
	public float[] lookup(String tablename, double[][] latlongs) {
		GISLayer layer = (GISLayer) this.gislayers.get(tablename);
		if (layer == null)
			throw new RuntimeException("Specified table '" + tablename
					+ "' does not exist");

		float[] res = new float[latlongs.length];
		for (int i = 0; i < latlongs.length; i++) {
			double lat = latlongs[i][0];
			double lon = latlongs[i][1];
			if (lat < layer.getMinLat() || lat > layer.getMaxLat()
					|| lon < layer.getMinLong() || lon > layer.getMaxLong()) {
				Debug.println("GIS query outside of lat/long bounds for layer "
						+ tablename + " ... treating as missing value",
						Debug.IMPORTANT);
				res[i] = Float.NaN;
			} else
				res[i] = layer.getValue(lat, lon);
		}
		return res;
	}

	
	
	public HashMap getTable(String name)
	{
		return (HashMap) tables.get(name);
	}
	
	/**
	 * 
	 * @param tablename
	 * @param key
	 * @param val
	 * @param barfifvaluenotalreadypresent
	 * @return true if there was already a value present in the table for this key
	 */
	public boolean replaceValue(String tablename, String key, Double val, boolean barfifvaluenotalreadypresent)
	{
		HashMap table = (HashMap) tables.get(tablename);
		if(table == null)
			throw new RuntimeException("Tried to replace valule in nonexistant table -- "+tablename);
		Object old = table.put(key, val);
		if(old == null && barfifvaluenotalreadypresent)
			throw new RuntimeException("There was no old value for key "+key+" in table "+tablename);
		return old != null;
	}
	
	
	
	
	
	
	
	
	/**
	 * Dump everything (except gis data) in the database to a file
	 *
	 */
	public void dumpOutEverything(PrintWriter stream)
	{
		Iterator keyit = this.tables.keySet().iterator();
		while(keyit.hasNext())
		{
			String tablename = (String) keyit.next();
			HashMap keyvalmappings = (HashMap) tables.get(tablename);
			//now print out all key/value mappings
			Iterator it = keyvalmappings.keySet().iterator();
			while(it.hasNext())
			{
				String key = (String) it.next();
				Object val = this.lookupArbitrary(tablename, key);
				stream.println(tablename+" "+key+" "+val);
			}
		}
		
		
		
	}
	
	
	
	
	
	
}