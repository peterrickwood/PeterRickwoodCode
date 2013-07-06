package rses.spatial;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import javax.imageio.ImageIO;

import rses.Debug;
import rses.PlatformInfo;
import rses.toolkitMain;
import rses.math.MathUtil;
import rses.math.RealFunction;
import rses.spatial.util.GoogleEarthUtils;
import rses.util.FileUtil;
import rses.util.Util;
import rses.util.gui.GuiUtil;
import rses.visualisation.ColourGradient;
import rses.visualisation.FunctionPlotter;








/* TODO Really should make this 3 seperate classes (binary, categorical, continuous).
 * Do this if the class gets any more complex than it already is.
 * 
 * TODO Also, I ignore, at the moment, the problem of a longitudinal or
 * latitudinal 'wraparound'. This is OK for now, but for English data,
 * it might be important to fix the longitudinal wraparound problem.
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
public class GISLayer implements Cloneable
{
	public static final int MAGIC = -2791975; //-my birthday
	
	
	private String projection = "WGS84"; //only supported projection at the moment
	
	//the bounding box of the layer. 
	private double southlat; 
	private double latsize; //in degrees
	private double westlong;
	private double longsize; //in degrees
	
	//private double maxlat;
	//private double minlong;
	//private double maxlong;
	
	
	//only one of these should have a value,
	//depending on the layer type
	public int[][] categoricaldata; //ONLY POSITIVE VALUES ALLOWED. MUST START FROM 0. NUMBER GIVES INDEX INTO NAME ARRAY
	private String[] catnames;
	//the centroid for each category. This may only make sense in some situations
	//(where, for example, each category is a separate region)
	private double[][] centroids;  
	
	public boolean[][] binarycategoricaldata;	
	public float[][] continuousdata;
	
	//the range of values
	private float minval;
	private float maxval;
	
	private String name="";
	
	private GISLayer() {};
	
	
	private String comment = "You can write any text or comments you like here,"+PlatformInfo.nl+"over as many lines as you like"+PlatformInfo.nl;
	
	
	
	
	public Object clone() throws CloneNotSupportedException
	{
		GISLayer res = (GISLayer) super.clone();
		//now deep copy the objects that need to be
		if(this.binarycategoricaldata != null)
			res.binarycategoricaldata = Util.copy(binarycategoricaldata);
		else if(this.categoricaldata != null) {
			res.categoricaldata = Util.copy(categoricaldata);
			res.catnames = (String[]) catnames.clone(); //just cloning is OK, strings are immutable
		}
		else
			res.continuousdata = Util.copy(continuousdata);
		
		return res;
	}
	
	
	
	/**
	 *  Data elements that are equal to -1 indicate that there
	 *  is no classification for the element.
	 *  
	 * @param name
	 * @param minlat
	 * @param maxlat
	 * @param minlon
	 * @param maxlon
	 * @param data
	 * @param catnames
	 * 
	 * @deprecated
	 */
	public GISLayer(String name, double minlat, double maxlat, double minlon, 
			double maxlon, int[][] data, String[] catnames)
	{
		this(minlat, minlon, maxlat-minlat, maxlon-minlon, name, data, catnames);
		
		if(maxlat < minlat || maxlon < minlon)
			throw new RuntimeException("Using a constructor in GISLayer that doesnt handle wraparound");
	}
	
	public GISLayer(double south, double west, double latsize, double lonsize, String name, int[][] data, String[] catnames)
	{
		this.name = name;
		this.categoricaldata = data;
		this.catnames = catnames;
		this.minval = 0.0f;
		this.maxval = catnames.length-1;
		
		//put the categories as a comment. (They are also in the data, but add a comment so readers can see them
		for(int i = 0; i < catnames.length; i++)
			this.comment = this.comment+PlatformInfo.nl+(i+" -- ")+catnames[i];
		this.comment += PlatformInfo.nl;
		
		this.southlat = south;
		this.westlong = west;
		this.latsize = latsize;
		this.longsize = lonsize;
		
	}
		
	

	/**
	 * 
	 * @param name
	 * @param minlat
	 * @param maxlat
	 * @param minlon
	 * @param maxlon
	 * @param data
	 * @deprecated
	 */
	public GISLayer(String name, double minlat, double maxlat, double minlon, 
			double maxlon, boolean[][] data)
	{
		this(minlat, minlon, maxlat-minlat, maxlon-minlon, name, data);
		
		if(maxlat < minlat || maxlon < minlon)
			throw new RuntimeException("Using a constructor in GISLayer that doesnt handle wraparound");		
	}
	
	
	
	public GISLayer(double south, double west, double latsize, double lonsize, String name, boolean[][] data)
	{
		this.name = name;
		this.binarycategoricaldata = data;
		Debug.println("GIS grid has "+data.length+" latsteps and "+data[0].length+" longsteps", Debug.INFO);
		this.minval = 0.0f;
		this.maxval = 1.0f;
		
		this.southlat = south;
		this.westlong = west;
		this.latsize = latsize;
		this.longsize = lonsize;
	}
	
	
	/**
	 * 
	 * @param name
	 * @param minlat
	 * @param maxlat
	 * @param minlon
	 * @param maxlon
	 * @param latsteps
	 * @param lonsteps
	 * @param interp
	 * 
	 * @deprecated
	 */
	public GISLayer(String name, double minlat, double maxlat, double minlon, 
			double maxlon, int latsteps, int lonsteps, Interpolater interp)
	{
		this(minlat, minlon, maxlat-minlat, maxlon-minlon, name, latsteps, lonsteps, interp);
		
		if(maxlat < minlat || maxlon < minlon)
			throw new RuntimeException("Using a constructor in GISLayer that doesnt handle wraparound");		
	}
	
	
	
	
	
	
	
	public GISLayer(double south, double west, double latsize, double lonsize, String name, int latsteps, int lonsteps, Interpolater interp)
	{
		this.name = name;
		
		this.southlat = south;
		this.latsize = latsize;
		this.westlong = west;
		this.longsize = lonsize;

		this.continuousdata = new float[latsteps][lonsteps];
		this.minval = Float.POSITIVE_INFINITY;
		this.maxval = Float.NEGATIVE_INFINITY;

		
		Debug.println("Generating data from interpolater... this may take some time...", Debug.IMPORTANT);
		double latstep = (latsize)/latsteps;
		double lonstep = (lonsize)/lonsteps;
		for(int lat = 0; lat < latsteps; lat++)
			for(int lon = 0; lon < lonsteps; lon++)
			{
				double latd = southlat + (lat+0.5)*latstep;
				double lond = westlong + (lon+0.5)*lonstep;
				this.continuousdata[lat][lon] = (float) interp.getPrediction(latd, lond);
				if(this.continuousdata[lat][lon] < this.minval)
					this.minval = this.continuousdata[lat][lon];
				if(this.continuousdata[lat][lon] > this.maxval)
					this.maxval = this.continuousdata[lat][lon];
			}
		

	}
	
	
	
	/**
	 * 
	 * @param name
	 * @param minlat
	 * @param maxlat
	 * @param minlon
	 * @param maxlon
	 * @param latlonvals
	 * 
	 * @deprecated
	 */
	public GISLayer(String name, double minlat, double maxlat, double minlon, 
			double maxlon, float[][] latlonvals)
	{
		this(minlat, minlon, maxlat-minlat, maxlon-minlon, name, latlonvals);
	}
	
	
	
	public GISLayer(double south, double west, double latsize, double lonsize, String name, float[][] latlonvals)
	{
		this.name = name;
		
		this.southlat = south;
		this.westlong = west;
		this.latsize = latsize;
		this.longsize = lonsize;
		
		this.continuousdata = latlonvals;
		this.minval = Float.POSITIVE_INFINITY;
		this.maxval = Float.NEGATIVE_INFINITY;

		
		//find min/max data values
		for(int lat = 0; lat < continuousdata.length; lat++)
			for(int lon = 0; lon < continuousdata[0].length; lon++)
			{
				if(Float.isNaN(this.continuousdata[lat][lon]))
					continue;
				
				if(this.continuousdata[lat][lon] < this.minval)
					this.minval = this.continuousdata[lat][lon];
				if(this.continuousdata[lat][lon] > this.maxval)
					this.maxval = this.continuousdata[lat][lon];
			}
		

	}
	
	
	
	
	
	
	/** Try to read in the GIS layer data from an ascii file.
	 *  
	 *  format for file:
	 *  line[0]: minlat maxlat minlong maxlong
	 *  line[1]: # columns
	 *  lin[2]: # rows
	 *  line[3]...line[rows+2]: data rows, space delimited, in order 
	 *  of <i>increasing</i> latitude
	 * 
	 * 
	 * where min/max lat/long are the min/max lat/long values of
	 * the actual data points in the file
	 * 
	 * 
	 * For binary layers, all values must be 0 or 1
	 * For categorical layers, a value of -1 means no value for
	 * that lat/long
	 * For continuous layers, a string of "NA" indicates no value
	 * 
	 * 
	 * @param file
	 * @param nametogivelayer
	 * @param layertype must be one of "binary" , "categorical" or "continuous"
	 * 
	 * 
	 * 
	 * 
	 * @return
	 */
	public static GISLayer makeLayerFromFile(String file, String nametogivelayer, String layertype)
	throws IOException
	{
		BufferedReader rdr = new BufferedReader(new FileReader(file));
		String[] line1 = Util.getWords(rdr.readLine());

		
		double minlat = Double.parseDouble(line1[0]);
		double maxlat = Double.parseDouble(line1[1]);
		double minlong = Double.parseDouble(line1[2]);
		double maxlong = Double.parseDouble(line1[3]);
		
		int ncol = Integer.parseInt(rdr.readLine());
		int nrow = Integer.parseInt(rdr.readLine());

		boolean[][] bdata = null;
		int[][] cdata = null;
		float[][] fdata = null;
		if(layertype.toLowerCase().startsWith("bin"))
			bdata = new boolean[nrow][ncol];
		else if(layertype.toLowerCase().startsWith("cat"))
			cdata = new int[nrow][ncol]; 
		else if(layertype.toLowerCase().startsWith("cont"))
			fdata = new float[nrow][ncol];
		else
			throw new IllegalArgumentException("specified layer type: "+layertype+" not understood");
		

		int maxcat = 0;
		
		         
		for(int i =0; i < nrow; i++)
		{
			String[] words = Util.getWords(rdr.readLine());
			if(words.length != ncol)
				throw new RuntimeException("File is not in correct format. Specified number of columns in header does not match data");
			for(int j = 0; j < ncol; j++)
			{
				if(bdata != null)
				{
					if(words[j].equals("0") || words[j].equals("1"))
						bdata[i/*nrow-i-1*/][j] = (words[j].equals("1"));
					else
						throw new RuntimeException("Data in data file is not binary!");
				}
				else if(cdata != null)
				{
					int val = Integer.parseInt(words[j]);
					if(val < -1)
						throw new RuntimeException("Invalid value of "+val+" in categorical data file");
					if(val > maxcat) maxcat = val;
					cdata[i/*nrow-i-1*/][j] = val;
				}
				else if(fdata != null)
				{
					if(words[j].equalsIgnoreCase("NA"))
						fdata[i/*nrow-i-1*/][j] = Float.NaN;
					else
						fdata[i/*nrow-i-1*/][j] = Float.parseFloat(words[j]);
				}
			}		
		}
		rdr.close();
		double latstep = (maxlat-minlat)/(nrow-1);
		double longstep = (maxlong-minlong)/(ncol-1);
	
		if(bdata != null)
			return new GISLayer(nametogivelayer, minlat-latstep/2, maxlat+latstep/2, 
					minlong-longstep/2, maxlong+longstep/2, bdata);
		else if(cdata != null) 
		{
			String[] catnames = new String[maxcat];
			for(int i = 0; i < maxcat; i++)
				catnames[i] = "cat"+i;
			return new GISLayer(nametogivelayer, minlat-latstep/2, maxlat+latstep/2, 
					minlong-longstep/2, maxlong+longstep/2, cdata, catnames);
		}
		else
		{
			return new GISLayer(nametogivelayer, minlat-latstep/2, maxlat+latstep/2, 
					minlong-longstep/2, maxlong+longstep/2, fdata);
		}

	}
	
	
	
	
	public boolean contains(double lat, double lon)
	{
		return lat >= getMinLat() && lat <= getMaxLat() && lon >= getMinLong() && lon <= getMaxLong();
	}
	
	
	
	/** Returns a boolean matrix that is true
	 *  at index [i,j] if and only if that
	 *  offset is within <code>radius_in_km</code>
	 *  of square [0,0]
	 * 
	 *  The matrix is guaranteed to be square, for convenience.
	 *  
	 * @param radius_in_km
	 * @return 
	 */
	public boolean[][] calculateRadius(double radius_in_km)
	{
		double minlat = this.getMinLat();
		double minlong = this.getMinLong();
		double maxlat = minlat+latsize;
		double maxlong = minlong+longsize;
		double latstep = (maxlat-minlat)/this.getLatSteps();
		double lonstep = (maxlong-minlong)/this.getLongSteps();
		
		//work out the radius of points we need to consider
		Debug.println("calculating radius", Debug.IMPORTANT);
		int r = 1;
		while(true)
		{
			if(MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+r*latstep, minlong)/1000 > radius_in_km
				&& MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat, minlong+r*lonstep)/1000 > radius_in_km)
				break; //we are outside the circle
			r++;
		}
		Debug.println("radius is "+r, Debug.IMPORTANT);

		//take a shortcut and pre-compute the distances. This
		//results in only slight innacuaries
		Debug.println("calculating distance matrix", Debug.IMPORTANT);
		boolean[][] distmatrix = new boolean[r][r];
		for(int i =0; i < r; i++)
			for(int j=0; j < r; j++)
			{
				distmatrix[i][j] = MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+i*latstep, minlong+j*lonstep)/1000 < radius_in_km;
				//Debug.println("distmatrix "+i+" "+j+" == "+distmatrix[i][j], Debug.INFO);
			}

		return distmatrix;
	}
	
	


	/** For each lat/long centroid in the centroids file,
	 * calculate the sum/integral of all squares within 
	 * radius kilometres. Print out this sum for each centroid 
	 * 
	 * Each line in the centroid file must be
	 * ID LAT LON
	 * 
	 * @param centroidfile the centroids to sum from
	 * @param datalayerfile the continuous data layer to sum
	 * @param radius the radius to sum around, in kilometres
	 * @throws java.io.IOException
	 */
	public static void calculateIntegralAtRegionCentroids(String centroidfile, String datalayerfile, double radius) 
	throws java.io.IOException
	{
		double[][] centroids = FileUtil.readVectorsFromFile(new File(centroidfile));
		GISLayer datalayer = GISLayer.readFromFile(datalayerfile);
		float[][] data = datalayer.continuousdata;
		boolean[][] distmatrix = datalayer.calculateRadius(radius);
		
		for(int centroidnum = 0; centroidnum < centroids.length; centroidnum++)
		{
			double[] centroid = centroids[centroidnum];
			//Util.printarray(centroid, System.out);
			int regionid = (int) Math.round(centroid[0]);
			int[] ind = datalayer.getLatLongIndices(centroid[1], centroid[2]);
			int lati = ind[0];
			int loni = ind[1];
			Debug.println("centroid at "+lati+" "+loni, Debug.INFO);
			double sum = 0.0;
			for(int i = -distmatrix.length+1; i < distmatrix.length; i++)
			{
				for(int j = -distmatrix.length+1; j < distmatrix.length; j++)
				{
					if(lati+i >= 0 && lati+i < data.length &&
					   loni+j >= 0 && loni+j < data[0].length &&
					   distmatrix[Math.abs(i)][Math.abs(j)])
					{
						if(!Float.isNaN(data[lati+i][loni+j]))
							sum += data[lati+i][loni+j];
					}
				}
			}
			
			System.out.println(regionid+" "+sum);
		}
		
		
		
	}
	
	
	
	
	/** Create a GISLayer from a datafile with lines in the
	 *  format:
	 *  
	 *  REGION_ID  VALUE_FOR_REGION_ID
	 *  
	 *  You must also specify a GIS layer that has region 
	 *  membership information. This operation then creates a 
	 *  layer where every square in region X has the same
	 *  value (as specified in the data file).
	 *  
	 *  If you specify areaaverage as true, the values in the region
	 *  are divided by its area.
	 * 
	 * 
	 * @param datfile Data file of REGION_ID VALUE tuples
	 * @param membership GIS layer with membership information for regions
	 * @param areaaverage map values directly, or divide by area
	 * @throws java.io.IOException
	 */
	public static void createRegionLayer(String datfile, String membership, boolean areaaverage) 
	throws java.io.IOException
	{		
		//the number of pixels in each region.
		//only needed if we are area averaging
		HashMap pixelcounts = new HashMap();
		
		//read in the file
		double[][] vects = FileUtil.readVectorsFromFile(new java.io.File(datfile));
		if(vects[0].length != 2)
			throw new RuntimeException("Expected file with 2 columns only: cdnum value");
		//make a table of REGION --> VALUE mappings
		HashMap map = new HashMap(vects.length);
		for(int i =0; i < vects.length; i++)
		{
			int cd = (int) Math.round(vects[i][0]);
			float val = (float) vects[i][1];
			map.put(new Integer(cd), new Float(val));

			if(areaaverage) pixelcounts.put(new Integer(cd), new int[] {0}); 
			
		}
		
		//read in the region membership layer
		Debug.println("Reading in region membership layer, for reference", Debug.IMPORTANT);
		GISLayer cdlayer = GISLayer.readFromFile(membership);
	

		
		//now create the layer
		float[][] cddata = cdlayer.continuousdata;
		float[][] newdata = new float[cddata.length][cddata[0].length];
		for(int i =0; i < newdata.length; i++)
			for(int j = 0; j < newdata[0].length; j++)
			{
				int cdnum =  (int) Math.round(cddata[i][j]);
				Integer key = new Integer(cdnum);
				float val = Float.NaN;
				if(map.containsKey(key)) {
					val = ((Float) map.get(key)).floatValue();
					if(areaaverage)
						((int[]) pixelcounts.get(key))[0] += 1;
				}
				//else
				//	Debug.println("No data for region "+cdnum+" leaving it blank", Debug.INFO);
				newdata[i][j] = val;
			}

		if(areaaverage && Debug.equalOrMoreVerbose(Debug.INFO)) {
			Iterator keyit = pixelcounts.keySet().iterator();
			while(keyit.hasNext()) {
				Integer tmpkey = (Integer) keyit.next();
				int val = ((int[]) pixelcounts.get(tmpkey))[0];
				Debug.println("count for region "+tmpkey+" is "+val, Debug.INFO);
			}
		}
		
		//if we are area averaging, we now go and divide by area
		int[] cached = null;
		Integer cachedid = new Integer(Integer.MIN_VALUE+1);
		if(map.containsKey(cachedid))
			throw new RuntimeException("Internal error in area average. You have a strange identifier number for a region -- "+cachedid+" which I cant handle");
		if(areaaverage)
		{
			for(int i =0; i < newdata.length; i++)
				for(int j = 0; j < newdata[0].length; j++)
				{
					int cdnum =  (int) Math.round(cddata[i][j]);
					Integer key = new Integer(cdnum);
					int[] countarr = cached;
					if(!cachedid.equals(key)) {
						countarr = (int[]) pixelcounts.get(key);
						cached = countarr;
						cachedid = key;
					}
					
					if(countarr == null) //no data for that region, and so we didnt bother counting
						/* do nothing */;
					else if(countarr[0] == 0)
						Debug.println("Region "+cdnum+" has no members!", Debug.IMPORTANT);
					else
					{
						if(Float.isNaN(newdata[i][j]))
							throw new IllegalStateException("Region has value specified and has count, but somehow has NaN value. Internal error");
						newdata[i][j] /= countarr[0];
					}
				}
			
		}
		
		//new GISLayer("XX", cdlayer.getMinLat(), cdlayer.getMaxLat(), 
			//	cdlayer.getMinLong(), cdlayer.getMaxLong(), newdata).saveToFile("layer.gis");
		
		
		new GISLayer(cdlayer.getMinLat(), cdlayer.getMinLong(), cdlayer.getLatSize(), cdlayer.getLongSize(), "XXX", newdata).saveToFile("layer.gis");
	}

	
	
	
	/** A special shrink operation for continuous
	 * layers where no averaging is done. Instead,
	 * the mode is used. This ensures that the
	 * set of values in the shrunk layer is
	 * a subset of those in the larger layer. 
	 * 
	 * @param shrinkfact
	 */
	public void shrink_modal(int shrinkfact)
	{
		if(this.continuousdata == null)
			throw new IllegalArgumentException("only valid for continuous layers");
		
		float[][] newdata = new float[getLatSteps()/shrinkfact][getLongSteps()/shrinkfact];

		for(int i =0; i+shrinkfact <= continuousdata.length; i+=shrinkfact)
			for(int j =0; j+shrinkfact <= continuousdata[0].length; j+= shrinkfact)
			{
				float[] vals = new float[shrinkfact*shrinkfact];
				int count = 0;
				for(int k = 0; k < shrinkfact; k++)
					for(int l = 0; l < shrinkfact; l++)
					{
						float val = continuousdata[i+k][j+l];
						vals[count++] = val;
					}
				
				//work out mode. First we sort
				Arrays.sort(vals, 0, count);
				int seqstart = 0;
				int longestseqlen = 1;
				float modeval = vals[0];
				for(int m = 1; m < count; m++) 
				{
					if(vals[m] != vals[m-1] || m == count-1) //end of sequence
					{
						int seqlen = m-seqstart;
						if(seqlen > longestseqlen) {
							longestseqlen = seqlen;
							modeval = vals[m-1];
						}
						seqstart = m;
					}
				}
				
				newdata[i/shrinkfact][j/shrinkfact] = modeval;
			}			
		this.continuousdata = newdata;
		
	}
	
	
	public void shrink(int shrinkfact)
	{
		if(this.binarycategoricaldata != null)
		{
			//BitSet[] newdata = new BitSet[getLatSteps()/shrinkfact];
			//int rowcount = 0;
			boolean[][] newdata = new boolean[getLatSteps()/shrinkfact][getLongSteps()/shrinkfact];
			
			
			for(int i =0; i+shrinkfact <= binarycategoricaldata.length; i+=shrinkfact)
			{
				//newdata[rowcount++] = new BitSet(getLongSteps()/shrinkfact);
				
				for(int j =0; j+shrinkfact <= getLongSteps(); j+= shrinkfact)
				{
					int count = 0;
					for(int k = 0; k < shrinkfact; k++)
						for(int l = 0; l < shrinkfact; l++)
						{
							//if(binarycategoricaldata[i+k].get(j+l))
							if(binarycategoricaldata[i+k][j+l])
								count++; 
						}
					
					if(count >= (int) Math.round(shrinkfact*shrinkfact/2.0))
						//newdata[i/shrinkfact].set(j/shrinkfact, true);
						newdata[i/shrinkfact][j/shrinkfact] = true;
				}
			}
			this.binarycategoricaldata = newdata;
		}
		else if(this.categoricaldata != null)
		{
			int[][] newdata = new int[getLatSteps()/shrinkfact][getLongSteps()/shrinkfact];
			for(int i =0; i+shrinkfact <= categoricaldata.length; i+=shrinkfact)
				for(int j =0; j+shrinkfact <= categoricaldata[0].length; j+= shrinkfact)
				{
					int[] catcount = new int[catnames.length+1]; //extra entry for unknown
					for(int k = 0; k < shrinkfact; k++)
						for(int l = 0; l < shrinkfact; l++)
						{
							int cat = categoricaldata[i+k][j+l]; 
							if(cat == -1)
								catcount[catcount.length-1]++;
							else
								catcount[cat]++;
						}
					int majority = Util.getMaxIndex(catcount);
					if(majority == catcount.length-1)
						newdata[i/shrinkfact][j/shrinkfact] = -1;
					else
						newdata[i/shrinkfact][j/shrinkfact] = majority;
				}			
			this.categoricaldata = newdata;
		}
		else {
			float minval = Float.POSITIVE_INFINITY;
			float maxval = Float.NEGATIVE_INFINITY;
			float[][] newdata = new float[getLatSteps()/shrinkfact][getLongSteps()/shrinkfact];
			for(int i =0; i+shrinkfact <= continuousdata.length; i+=shrinkfact)
				for(int j =0; j+shrinkfact <= continuousdata[0].length; j+= shrinkfact)
				{
					double sum = 0.0;
					int count = 0;
					for(int k = 0; k < shrinkfact; k++)
						for(int l = 0; l < shrinkfact; l++)
						{
							float val = continuousdata[i+k][j+l];
							if(!Float.isNaN(val)) {
								sum += val;
								count ++;
							}
						}
					float avg = Float.NaN;
					if(count > 0)  { 
						avg = (float) (sum/count);
						if(Float.isNaN(avg)) throw new IllegalStateException("How can I get here? SHould be impossible I thought");
						if(avg < minval) minval = avg;
						if(avg > maxval) maxval = avg;
					}
					newdata[i/shrinkfact][j/shrinkfact] = avg;
				}			
			this.continuousdata = newdata;
			this.minval = minval;
			this.maxval = maxval;
		}
	}
	
	
	
	
	public static double[] getWrapAroundLatLong(double lat, double lon)
	{
		double[] res = new double[] {lat, lon};
		
		if(lat > 90.0) res[0] = 180.0-lat;
		
		if(lon > 180.0) res[1] = -360+lon;

		return res;
	}
	
	
	
	/** Note that this will not print 'wrapped-around' values. So if (say) longitude wraps around
	 * from 170 to -175, the values actually printed by this routine will be printed as longitudes
	 * of 170 to 185, even though we know that a longitude of 185 doesnt make sense. This is a
	 * deliberate choice because I think it makes the output easier to interpret than adjusting
	 * for long wraparound
	 * 
	 */
	public void dumpAsciiLatLongVal()
	{
		double latstep = (this.latsize)/this.getLatSteps();
		double longstep = (this.longsize)/this.getLongSteps();
		double lat = southlat + latstep/2;

		while(lat < southlat+latsize)
		{			
			double lon = westlong + longstep/2;
			while(lon < westlong+longsize)
			{
				System.out.println(lat+" "+lon+" "+getValue(lat, lon));
				lon += longstep; 
			}
			lat += latstep;
		}
	}


	
	
	
	public static GISLayer readFromFile(String filename) throws java.io.IOException
	{
		FileReader fin = new FileReader(filename);
		BufferedReader rdr  = new BufferedReader(fin);
		
		GISLayer layer = new GISLayer();
		
		//first we read the header
		layer.name =  Util.getWords(rdr.readLine())[2];
		layer.projection = Util.getWords(rdr.readLine())[2];
		
		
		layer.southlat = Double.parseDouble(Util.getWords(rdr.readLine())[2]);
		
		String[] words = Util.getWords(rdr.readLine());
		if(words[0].equalsIgnoreCase("maxlat"))
			layer.latsize = Double.parseDouble(words[2])-layer.southlat;
		else if(words[0].equalsIgnoreCase("latsize"))
			layer.latsize = Double.parseDouble(words[2]);
		
		layer.westlong = Double.parseDouble(Util.getWords(rdr.readLine())[2]);
		
		words = Util.getWords(rdr.readLine());
		if(words[0].equalsIgnoreCase("maxlong"))
			layer.longsize = Double.parseDouble(words[2])-layer.westlong;
		else if(words[0].equalsIgnoreCase("longsize"))
			layer.longsize = Double.parseDouble(words[2]);
		
		
		layer.minval = Float.parseFloat(Util.getWords(rdr.readLine())[2]);
		layer.maxval = Float.parseFloat(Util.getWords(rdr.readLine())[2]);
		int latsteps = Integer.parseInt(Util.getWords(rdr.readLine())[2]);
		int longsteps = Integer.parseInt(Util.getWords(rdr.readLine())[2]);
		String datatype = Util.getWords(rdr.readLine())[2];
		
		Debug.println("Layer has min/max val of "+layer.minval+" "+layer.maxval, Debug.INFO);
		
		if(datatype.toUpperCase().startsWith("BINARY"))
		{
			//layer.binlongsteps = longsteps;
			//layer.binarycategoricaldata = new BitSet[latsteps];
			layer.binarycategoricaldata = new boolean[latsteps][longsteps];
			//for(int i = 0; i < latsteps; i++) 
				//layer.binarycategoricaldata[i] = new BitSet(longsteps);

		}
		else if(datatype.toUpperCase().startsWith("CATEGORICAL")) {
			int ncat = (int) Math.round(layer.maxval-layer.minval)+1;
			String catline = rdr.readLine();
			String[] catwords = Util.getWords(catline);
			layer.categoricaldata = new int[latsteps][longsteps];
			layer.catnames = new String[ncat];
			for(int i =0; i < ncat; i++)
				layer.catnames[i] = catwords[2+i];
		}
		else if(datatype.toUpperCase().startsWith("CONTINUOUS")) {
			layer.continuousdata = new float[latsteps][longsteps];
		}
		else
			throw new RuntimeException("Unknown data type in header -- "+datatype);
	
		
		//now we read the actual data
		//we need to skip to the start of the data, which we
		//have to do in this (slightly) laborious way because
		//the magic number that signals the start of data may
		//not be on an integer boundary
		rdr.close();
		RandomAccessFile file = new RandomAccessFile(filename, "r");
		try {
			while(file.readInt() != MAGIC)
				file.seek(file.getFilePointer()-3);
		}
		catch(EOFException eof) {
			Debug.println("Hit end of file without finding data start delimiter. File corrupted or incorrect format.", Debug.CRITICAL);
			return null;
		}

		//ok, work out where the magic number ends. We need to skip this many
		//bytes when reading
		long datastart = file.getFilePointer();
		file.close();
		java.io.FileInputStream finstr = new java.io.FileInputStream(filename);
		java.io.BufferedInputStream binstr = new java.io.BufferedInputStream(finstr);
		java.io.DataInputStream dinstr = new java.io.DataInputStream(binstr);
		//now skip to the start of the data
		for(long l = 0; l < datastart; l++)
			dinstr.readByte();
		
		//now get the stream for reading the data
		java.io.DataInputStream instr = null;
		
		if(datatype.toLowerCase().endsWith("zipped"))
			instr = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(binstr));
		else
			instr = new java.io.DataInputStream(binstr);
		
		//OK, now we are at the start of the data, start reading
		
		
		//int truecount = 0; //debugging for binary layers
		
		//ok, we are at the binary data. read it in
		for(int i =0; i < latsteps; i++)
		{
			//boolean data is a little tricky. We need to unpack it
			//from bytes 
			if(datatype.toUpperCase().startsWith("BINARY")) {
				int endj = (longsteps/8) + (((longsteps%8)==0)?0:1);
				int longcount=0;
				for(int j = 0; j < endj; j++) {
					int ubyte = instr.read(); //file.read();
					for(int k=0; k < 8 && longcount < longsteps; k++, longcount++)
					{
						//layer.binarycategoricaldata[i].set(longcount, ((ubyte >>> (7-k))% 2 != 0));
						layer.binarycategoricaldata[i][longcount] = ((ubyte >>> (7-k))% 2 != 0);
						//if(layer.binarycategoricaldata[i][longcount]) truecount++;
					}
				}
				if(longcount != longsteps)
					throw new RuntimeException("Cannot parse data in file... longitude row not of expected length. Got "+longcount+" bits, but expected to get "+longsteps);
				
				//if(i == 0)
				//	Debug.println("# of bits per row (there are "+latsteps+" rows) is "+layer.binarycategoricaldata[0].size(), Debug.IMPORTANT);
			}
			else if(datatype.toUpperCase().startsWith("CATEGORICAL"))
				for(int j =0; j < longsteps; j++)
					layer.categoricaldata[i][j] = instr.readInt(); //file.readByte(); //this one is easy
			else
				for(int j =0; j < longsteps; j++)
					layer.continuousdata[i][j] = instr.readFloat(); //file.readFloat();
			
			Debug.println("Read "+(i+1)+" of "+latsteps+" lines...", Debug.INFO);
		}
		//Debug.println("truecount for binary layer (0 otherwise) is "+truecount, Debug.INFO);
		
		file.close();
		return layer;
	}

	
	
	
	
	public void saveToFile(String filename) throws java.io.IOException
	{
		FileOutputStream fout = new FileOutputStream(filename);
		BufferedOutputStream bout = new BufferedOutputStream(fout);
		DataOutputStream dout = new DataOutputStream(bout);
		
		//first we write the header
		String nl = PlatformInfo.nl;
		dout.writeBytes("name = "+this.name+nl);
		dout.writeBytes("projection = "+this.projection+nl);
		dout.writeBytes("minlat = "+this.southlat+nl);
		dout.writeBytes("latsize = "+this.latsize+nl);
		dout.writeBytes("minlong = "+this.westlong+nl);
		dout.writeBytes("longsize = "+this.longsize+nl);
		dout.writeBytes("minval = "+this.minval+nl);
		dout.writeBytes("maxval = "+this.maxval+nl);
		dout.writeBytes("latsteps = "+this.getLatSteps()+nl);
		dout.writeBytes("longsteps = "+this.getLongSteps()+nl);
		if(this.binarycategoricaldata != null)
			dout.writeBytes("DATA = BINARY_ZIPPED"+nl);
		else if(this.categoricaldata != null) {
			dout.writeBytes("DATA = CATEGORICAL_ZIPPED"+nl);
			String catn = "";
			for(int i =0; i < this.catnames.length; i++)
				catn = catn + " " +  catnames[i];
			dout.writeBytes("categories ="+catn+nl);
		}
		else if(this.continuousdata != null) 
			dout.writeBytes("DATA = CONTINUOUS_ZIPPED"+nl);
		
	
		dout.writeBytes("DESCRIPTION"+nl+this.comment);
		
		//write the 'MAGIC' number signaling the start of data
		//This is my birthday :-)
		dout.writeInt(MAGIC); 
		
		
		//now write the rest of the data file compressed
		java.io.DataOutputStream dout2 = new DataOutputStream(new java.util.zip.GZIPOutputStream(bout));
		
		//now we write the actual data
		for(int i =0; i < this.getLatSteps(); i++)
		{
			//boolean data is a little tricky. We need to pack it
			//into bytes and pad the end of each array
			if(this.binarycategoricaldata != null)
				for(int j = 0; j < this.getLongSteps(); j+=8) {
					//BitSet chunk = binarycategoricaldata[i].get(j, j+8);
					//boolean[] eightbits = new boolean[8];
					//for(int k = 0; k < 8; k++) eightbits[k] = chunk.get(k);
					dout2.writeByte(FileUtil.getPackedBooleans(binarycategoricaldata[i], j));
					//dout.writeByte(FileUtil.getPackedBooleans(eightbits, 0));
				}
			else if(this.categoricaldata != null)
				for(int j =0; j < this.getLongSteps(); j++)
					dout2.writeInt(this.categoricaldata[i][j]); //this one is easy
			else
				for(int j =0; j < this.getLongSteps(); j++)
					dout2.writeFloat(this.continuousdata[i][j]);
			
		}
		
		dout2.flush();
		dout2.close();
	}
	
	

	
	public void generateGoogleEarthOverlay(String urlbase, Color lwr, Color upr) throws java.io.IOException
	{
		this.generateGoogleEarthOverlay(this.getLongSteps(), this.getLatSteps(), lwr, upr, urlbase, "");
	}

	
	/** Generate a google earth overlay image from this GIS layer
	 * and a corresponding KML file
	 */
	public void generateGoogleEarthOverlay(String urlbase) throws java.io.IOException
	{
		this.generateGoogleEarthOverlay(this.getLongSteps(), this.getLatSteps(), urlbase);
	}
	
	/** Generate a google earth overlay image from this GIS layer
	 * and a corresponding KML file
	 * 
	 * @param xpixels the number of x pixels in the image
	 * @param ypixels the number of y pixels
	 */
	public void generateGoogleEarthOverlay(int xpixels, int ypixels, String urlbase)
	throws java.io.IOException
	{
		Debug.println("Generating blue/red overlay", Debug.INFO);
		this.generateGoogleEarthOverlay(xpixels, ypixels, Color.blue, Color.red, urlbase, "br");
		Debug.println("Generating white/black overlay", Debug.INFO);
		this.generateGoogleEarthOverlay(xpixels, ypixels, Color.white, Color.black, urlbase, "wb");
		Debug.println("Generating red/blue overlay", Debug.INFO);
		this.generateGoogleEarthOverlay(xpixels, ypixels, Color.red, Color.blue, urlbase, "rb");
	}
	
	public void generateGoogleEarthOverlay(int xpixels, int ypixels, 
			Color lwrcolour, Color upprcolour, String urlbase, String tag) 
	throws java.io.IOException
	{
		//create our image
		BufferedImage bim = new BufferedImage(xpixels, ypixels, BufferedImage.TYPE_3BYTE_BGR);
		//create a graphics 
		java.awt.Graphics2D g2d = bim.createGraphics();
		Color c = Color.white;
		float[] comps = c.getComponents(null);
		comps[3] = 0.0f;
		g2d.setBackground(new Color(comps[0], comps[1], comps[2], comps[3]));

		FunctionPlotter.setPointSize((int) Math.round(Math.min(xpixels/getLongSteps(), ypixels/getLatSteps()))-1);
		g2d.clearRect(0,0,xpixels, ypixels);
		
		//paint points in batches, in case there are memory constraints
		int bufpoints = 1024*1024/8;
		ArrayList points = new ArrayList(bufpoints);
		int inbuf = 0;
		double latstep = (latsize)/this.getLatSteps();
		double longstep = (longsize)/this.getLongSteps();
		for(double lat = southlat+latsize-latstep/2; lat > southlat; lat -= latstep)
		{
			for(double lon = westlong + longstep/2; lon < westlong+longsize; lon += longstep)
			{
				float val = this.getValue(lat, lon);
				//System.out.println("val at "+lat+" "+lon+" is "+val);
				if(Float.isNaN(val))
					continue; //we dont plot invalid points. Leave them the background colour
				points.add(new double[] {lon, lat, val});
				inbuf++;
				if(inbuf == bufpoints) {
					FunctionPlotter.plotPoints3D(g2d, points, westlong, westlong+longsize, southlat, southlat+latsize, minval, maxval, xpixels, ypixels, lwrcolour, upprcolour);
					inbuf = 0;
					points.clear();
				}
			}
		}
		
		//clear the last dregs of the data
		if(inbuf > 0)
			FunctionPlotter.plotPoints3D(g2d, points, westlong, westlong+longsize, southlat, southlat+latsize, minval, maxval, xpixels, ypixels, lwrcolour, upprcolour);
		
		
		//now draw the grid
		Color oldcol = g2d.getColor();
		g2d.setColor(Color.blue);
		//g2d.drawRect(0, 0, xpixels-1, ypixels-1);
		for(int y = 0; y < this.getLatSteps(); y++)
			g2d.drawLine(0, y*(ypixels/getLatSteps()), xpixels, y*(ypixels/getLatSteps()));
		for(int x = 0; x< this.getLongSteps(); x++)
			g2d.drawLine(x*xpixels/getLongSteps(), 0, x*xpixels/getLongSteps(), ypixels);
		g2d.setColor(oldcol);
		
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_NORMAL)) {
			if(Math.abs(this.southlat+latsize) > 65)
				Debug.println("WARNING: You shouldnt be using this software above latitudes of 65... the distortions are too great. I'm going to continue, but dont expect the results to be very meaningful....", Debug.IMPORTANT);
			if(longsize > 80)
				Debug.println("WARNING: Longitudinal range is very large. Results will almost surely be at least a little skewiff, and possibly a lot skewiff.", Debug.IMPORTANT);
			else if(longsize > 40)
				Debug.println("Longitudinal range is quite large. Results will probably be a little skewiff, and possibly a lot skewiff.", Debug.IMPORTANT);
		}
	
		

		
		int scw = 400;
		int sch = 240;
		BufferedImage bim2 = new BufferedImage(scw, sch, BufferedImage.TYPE_3BYTE_BGR);
		g2d = bim2.createGraphics();
		Graphics g = g2d;
		
		//now write the scale to a separate file
		//create our image
		//int scw = xpixels/6;
		//int sch = ypixels/10;
		
		//create a graphics 
		//java.awt.Graphics g = g2d.create(xpixels-scw, ypixels-sch, scw, sch);

		int indent = scw/8;
		int catwidth = scw/4;
		int xrindent = scw/8; 
		if(this.binarycategoricaldata != null) 
		{
			g.setColor(Color.GRAY);
			g.fillRect(0, 0, scw, sch);
			int catheight = (sch-2*indent)/2;
			GuiUtil.setFontToSpecificHeight(g, catheight/3);
			g.setColor(lwrcolour);
			g.fillRect(indent, indent, catwidth, catheight);
			g.setColor(upprcolour);
			g.fillRect(indent, indent+catheight, catwidth, catheight);
			g.setColor(Color.BLACK);
			g.drawString("FALSE", indent+catwidth+xrindent, indent+2*(catheight/3));
			g.drawString("TRUE", indent+catwidth+xrindent, indent+catheight+2*(catheight/3));
		}
		else if(this.categoricaldata != null) 
		{
			g.setColor(Color.GRAY);
			g.fillRect(0, 0, scw, sch);
			int catheight=(sch-2*indent)/catnames.length;
			GuiUtil.setFontToSpecificHeight(g, (int) (0.9*catheight));
			ColourGradient cg = new ColourGradient(lwrcolour, this.minval, upprcolour, this.maxval);
			for(int i = 0; i < this.catnames.length; i++) 
			{
				g.setColor(cg.getColour(i));
				g.fillRect(indent, indent+i*catheight, catwidth, catheight);
				g.setColor(Color.BLACK);
				g.drawString(catnames[i], indent+catwidth+xrindent, indent+i*catheight+(int)(0.8*catheight));
			}
		}
		else { //continuous data
			scw = (int) (scw*1.5); //we need more x space for continunous data
			sch = sch/2; //but less y space
			indent = scw/8;
			
			//g = g2d.create(xpixels-scw, ypixels-sch, scw, sch);
			bim2 = new BufferedImage(scw, sch, BufferedImage.TYPE_3BYTE_BGR);
			g2d = bim2.createGraphics();
			g = g2d;
			
			g.setColor(Color.GRAY);
			g.fillRect(0, 0, scw, sch);
			ColourGradient cg = new ColourGradient(lwrcolour, this.minval, upprcolour, this.maxval);
			g.setColor(Color.BLACK);
			cg.paintColourScale(g, scw, sch, false);
		}

		//now write the image file
		String fbase = this.name+tag;
		String imgname = fbase+".png";
		javax.imageio.ImageIO.write(bim, "png", new File(imgname));
		//now write the scale/key file 
		ImageIO.write(bim2, "png", new File(fbase+".scale.png"));
		//now write the KML
		double north = getMaxLat();
		double east = getMaxLong();
		double[] ne = getWrapAroundLatLong(north, east);
		GoogleEarthUtils.writeKMLForOverlay(imgname, fbase+".kml", "NONE", urlbase,
				ne[0], southlat, ne[1], westlong);
	}
	
	

	
	public Map<String, double[]> getRegionCentroids()
	{
		if(this.categoricaldata == null)
			throw new RuntimeException("Asking for centroids only makes sense for categorical layers where each category is a region");
		
		
		//to start with, the double[] array is actually a triple -- latsum, longsum, count
		HashMap<String, double[]> centroids = new HashMap<String, double[]>();
		
		
		double minlat = this.getMinLat();
		double latstep = this.getLatSize()/this.getLatSteps();
		double minlong = this.getMinLong();
		double longstep = this.getLongSize()/this.getLongSteps();
		
		String lastseen = null;
		double[] lastarr = null;
		
		int lati = 0;
		
		for(double lat = minlat+latstep/2; lat < getMaxLat(); lat += latstep, lati++)
		{
			int loni = 0;
			for(double lon = minlong+longstep/2; lon < getMaxLong(); lon += longstep, loni ++)
			{
				int regionid = this.categoricaldata[lati][loni];
				if(regionid < 0)
					continue;
				String regionname = this.catnames[regionid];
				
				if(lastseen == null || !lastseen.equals(regionname))
				{
					lastseen = regionname;
					lastarr = centroids.get(lastseen);
					if(lastarr == null) {
						lastarr = new double[3];
						centroids.put(regionname, lastarr);
					}
				}
				
				lastarr[0] += lat;
				lastarr[1] += lon;
				lastarr[2] += 1.0;
			}
		}
		
		//ok, finished... go back and work out the centroids
		Iterator<String> keys = centroids.keySet().iterator();
		while(keys.hasNext())
		{
			String key = keys.next();
			double[] sums = centroids.get(key);
			if(sums[2] < 1.0) throw new RuntimeException("Count for key "+key+" is "+sums[2]+" which should be impossible - they should all be 1 or more");
			double[] centroid = new double[] {sums[0]/sums[2], sums[1]/sums[2]};
			centroids.put(key, centroid);
		}
		
		
		//and now, if required, make sure each centroid is actually inside the region it is a centroid for
		//TODO implement this functionality later and add a boolean parameter for the caller to indicate that
		//they want that behaviour
		
		
		return centroids;
	}
	
	

	
	
	
	public static Map<Float, double[]> getRegionCentroids(GISLayer regionmembership)
	{
		//to start with, the double[] array is actually a triple -- latsum, longsum, count
		HashMap<Float, double[]> centroids = new HashMap<Float, double[]>();
		
		
		double minlat = regionmembership.getMinLat();
		double latstep = regionmembership.getLatSize()/regionmembership.getLatSteps();
		double minlong = regionmembership.getMinLong();
		double longstep = regionmembership.getLongSize()/regionmembership.getLongSteps();
		
		Float lastseen = null;
		double[] lastarr = null;
		
		for(double lat = minlat+latstep/2; lat < regionmembership.getMaxLat(); lat += latstep)
		{
			for(double lon = minlong+longstep/2; lon < regionmembership.getMaxLong(); lon += longstep)
			{
				Float val = new Float(regionmembership.getValue(lat, lon));
				if(val.isNaN())
					continue;
				
				
				if(lastseen == null || !lastseen.equals(val))
				{
					lastseen = val;
					lastarr = centroids.get(lastseen);
					if(lastarr == null) {
						lastarr = new double[3];
						centroids.put(val, lastarr);
					}
				}
				
				lastarr[0] += lat;
				lastarr[1] += lon;
				lastarr[2] += 1.0;
			}
		}
		
		//ok, finished... go back and work out the centroids
		Iterator<Float> keys = centroids.keySet().iterator();
		while(keys.hasNext())
		{
			Float key = keys.next();
			double[] sums = centroids.get(key);
			if(sums[2] < 1.0) throw new RuntimeException("Count for key "+key+" is "+sums[2]+" which should be impossible - they should all be 1 or more");
			double[] centroid = new double[] {sums[0]/sums[2], sums[1]/sums[2]};
			centroids.put(key, centroid);
		}
		
		
		//and now, if required, make sure each centroid is actually inside the region it is a centroid for
		//TODO implement this functionality later and add a boolean parameter for the caller to indicate that
		//they want that behaviour
		
		
		return centroids;
	}
	
	
	
	
	
	
	
	
	public Map<String, List<String>> getAdjacencyList()
	{
		if(this.categoricaldata == null) throw new UnsupportedOperationException("Cannot get adjacency list for non-categorical layer");
		
		HashMap<String, List<String>> result = new HashMap<String, List<String>>();
		for(int i =0; i < catnames.length; i++)
			result.put(catnames[i], new ArrayList<String>());
		
		
		for(int a = 0; a < this.categoricaldata.length; a++)
		{
			for(int b = 0; b < this.categoricaldata[0].length; b++)
			{
				int cati = this.categoricaldata[a][b];
				if(cati < 0) continue;
				
				if(a-1 >= 0 && this.categoricaldata[a-1][b] != cati && this.categoricaldata[a-1][b] >= 0)
				{
					result.get(this.catnames[cati]).add(this.catnames[categoricaldata[a-1][b]]);
					result.get(this.catnames[categoricaldata[a-1][b]]).add(this.catnames[cati]);
				}
				if(b-1 >= 0 && this.categoricaldata[a][b-1] != cati && this.categoricaldata[a][b-1] >= 0)
				{
					result.get(this.catnames[cati]).add(this.catnames[categoricaldata[a][b-1]]);
					result.get(this.catnames[categoricaldata[a][b-1]]).add(this.catnames[cati]);
				}
			}
		}
		
		return result;
	}
	
	
	
	
	/** Given a GIS Layer with particular values, and a function,
	 *  create a new GIS Layer with the mapped values.
	 *  
	 *  This is a bit abstract, but can be useful, for example,
	 *  if you have a layer that specifies region membership (say),
	 *  and want to create a layer where the membership is replaced
	 *  with a value.
	 *  
	 * 
	 * @param orig
	 * @param mappings
	 * @return
	 */
	public static GISLayer createMappedLayer(GISLayer orig, RealFunction func)
	{
		float[][] res = new float[orig.getLatSteps()][orig.getLongSteps()];
		
		for(int i =0; i < res.length; i++)
			for(int j = 0; j < res[0].length; j++)
				res[i][j] = (float) func.invoke(new double[] {orig.getRawValueByIndices(i, j)});
		
		
		//return new GISLayer("MAPPED", orig.getMinLat(), orig.getMaxLat(), orig.getMinLong(), orig.getMaxLong(), res);
		return new GISLayer(orig.getMinLat(), orig.getMinLong(), orig.getLatSize(), orig.getLongSize(), "MAPPED", res);
		
	}
	
	
	
	
	
	/** Find the indices in the layer that are closest to
	 *  the indices for (lat,lon), and for which the layer has
	 *  the specified value.
	 *  
	 *  Will not exhaustively search the layer, and will only look 
	 *  in a neighbourhood of (lat,lon), so you cant be guaranteed
	 *  that this will actually find a value, in which case it will
	 *  return `null'
	 * 
	 * @param lat
	 * @param lon
	 * @param targetVal
	 * @return
	 */
	public int[] getClosestIndicesForValue(double lat, double lon, float targetVal, int maxradius)
	{
		int[] start = this.getLatLongIndices(lat, lon);
		if(this.continuousdata[start[0]][start[1]] == targetVal)
			return start;
		
		//look in the neighbourhood
		for(int r = 1; r <= maxradius; r++)
		{
			for(int m1 = -1; m1 <= 1; m1++)
			{
				for(int m2 = -1; m2 <= 1; m2++)
				{
					int[] tolook = new int[] {start[0]+m1*r, start[1]+m2*r};
					if(tolook[0] < 0 || tolook[1] < 0 || 
					   tolook[1] >= continuousdata[0].length ||
					   tolook[0] >= continuousdata.length)
						/* do nothing, index is out of range of layer */;
					else {
						if(this.continuousdata[tolook[0]][tolook[1]] == targetVal)
							return tolook;						
					}
				}
			}
		}
		
		//couldnt find in any of the nearby squares
		return null;
	}
	
	
	
	/** Determined which elements are on a boundary where the value
	 * of the layer changes.
	 * 
	 * @param layer
	 * @return
	 */
	public static boolean[][] calcBoundaryLayerForContinuousData(GISLayer layer)
	{
		float[][] data = layer.continuousdata;
		boolean[][] result = new boolean[data.length][data[0].length];
		for(int i =0; i < result.length; i++)
		{
			for(int j =0; j < result[0].length; j++)
			{
				if(i > 0 && data[i-1][j] != data[i][j])
					result[i][j] = true;
				else if(i < data.length-1 && data[i+1][j] != data[i][j])
					result[i][j] = true;
				else if(j > 0 && data[i][j-1] != data[i][j])
					result[i][j] = true;
				else if(j < data[0].length && data[i][j+1] != data[i][j])
					result[i][j] = true;
			}
		}
		
		return result;
	}
	
	
	public int getLatSteps()
	{
		if(this.binarycategoricaldata != null)
			return this.binarycategoricaldata.length;
		else if(this.categoricaldata != null)
			return this.categoricaldata.length;
		else
			return this.continuousdata.length;
	}
	
	public int getLongSteps()
	{
		if(this.binarycategoricaldata != null)
			//return this.binlongsteps;
			return this.binarycategoricaldata[0].length;
		else if(this.categoricaldata != null)
			return this.categoricaldata[0].length;
		else
			return this.continuousdata[0].length;		
	}

	
	public float getMinVal() { 
		return this.minval;
	}
	
	public float getMaxVal() {
		return this.maxval;		
	}
	
	public double getMinLat() { return this.southlat; }
	public double getLatSize() { return this.latsize; }
	
	public double getMinLong() { return this.westlong; }
	public double getLongSize() { return this.longsize; }

	
	public double getLatStepSize() { return this.getLatSize()/this.getLatSteps(); }
	public double getLongStepSize() { return this.getLongSize()/this.getLongSteps(); }
	
	/** Provides for ease of use, but be warned -- these two functions do not handle
	 *  wrap-around, so you can get values greater than 180 degrees long, for example.
	 * @return
	 */
	public double getMaxLat() {return this.southlat+this.latsize; }
	/** Provides for ease of use, but be warned -- these two functions do not handle
	 *  wrap-around, so you can get values greater than 180 degrees long, for example.
	 * @return
	 */
	public double getMaxLong() {return this.westlong+this.longsize; }
	
	
	
	/** Returns the category names (NOT A COPY!!)
	 *  Throws an exception if this is not a categorical layer
	 * @return
	 */
	public String[] getCategoryNames()
	{
		if(this.categoricaldata == null)
			throw new RuntimeException("Not a categorical layer!!!");
		return this.catnames;
	}
	
	
	
	/** Get a list of all the distinct values in the layer.
	 *  Does <i>not</i> include undefined or missing values.
	 *  
	 *  NB: For categorical layers, the 'value' returned is the index of the
	 *  category name, not the category name itself
	 * 
	 * @return A list of all all distinct values (no repeats)
	 */
	public ArrayList<Number> getValues()
	{
		if(this.binarycategoricaldata != null)
			throw new UnsupportedOperationException("Method not implemented for binary or categorical layers yet");
		
		HashMap<Number, Number> result = new HashMap<Number, Number>();
		Number lastnum = new Float(Float.NaN);
		for(int i = 0; i < this.getLatSteps(); i++)
		{
			for(int j = 0; j < this.getLongSteps(); j++)
			{
				Number val = null;
				if(this.continuousdata != null)
				{
					val = new Float(this.continuousdata[i][j]);
					if(Float.isNaN(val.floatValue()))
						continue; //skip NaN
				}
				else
				{
					val = new Integer(this.categoricaldata[i][j]);
					if(val.intValue() < 0)
						continue;
				}
					
				if(val.equals(lastnum))
					continue; //already seen this value
				
				//ok, new value that was not the same as the last seen
				lastnum = val;
				if(result.containsKey(val))
					continue; //ahh, we have seen it
				else //we havent, so put it in
					result.put(val, val);
			}
		}
		
		//ok, now pack all the results into an array
		ArrayList<Number> listres = new ArrayList<Number>();
		Object[] values = result.values().toArray();
		//sort it, just for fun :-)
		Arrays.sort(values);
		
		for(int i =0; i < values.length; i++)
			listres.add((Number) values[i]);
		
		return listres;
	}
	
	
	
	/** Each GISlayer does not keep up to date totally with the maximum and minimum values that it has.
	 * (because otherwise it would need to keep track of where they were and when they were overwritten)
	 * 
	 * So, this forces it to read through all its data and get the right ones.
	 * 
	 */
	public void updateMaxMinVals()
	{
		if(this.continuousdata == null) throw new RuntimeException("Not implemented for other layers");
		float minval = Float.POSITIVE_INFINITY;
		float maxval = Float.NEGATIVE_INFINITY;
		for(int i = 0; i < continuousdata.length; i++)
		{
			for(int j = 0; j < continuousdata[i].length; j++)
			{
				if(continuousdata[i][j] < minval) minval = continuousdata[i][j];
				if(continuousdata[i][j] > maxval) maxval = continuousdata[i][j];				
			}
		}
		this.minval = minval;
		this.maxval = maxval;
	}

	public void setValueByIndices(int lati, int loni, float val)
	{
		if(this.continuousdata != null)
			this.continuousdata[lati][loni] = val;
		if(val < this.minval)
			this.minval = val;
		if(val > this.maxval)
			this.maxval = val;
	}

	
	public void setValue(double lat, double lon, float val)
	{
		int[] latloni = this.getLatLongIndices(lat, lon);
		if(this.continuousdata != null)
			this.continuousdata[latloni[0]][latloni[1]] = val;
		if(val < this.minval)
			this.minval = val;
		if(val > this.maxval)
			this.maxval = val;
	}
	
	/** Returns the 'raw' underlying value at a particular lat/long
	 *  index. Useful for cloning and/or transforming a layer.
	 *  Note that the values returned may be NaN (in the continuous
	 *  layer case) or -1 (in the categorical).
	 *  
	 *  NB: for categorical layers, the 'value' returned is the index
	 *  of the category name
	 * 
	 * @param latindex
	 * @param longindex
	 * @return
	 */
	public double getRawValueByIndices(int latindex, int longindex)
	{
		if(this.binarycategoricaldata != null)
			//return this.binarycategoricaldata[latindex].get(longindex) ? 1.0: 0.0;
			return this.binarycategoricaldata[latindex][longindex] ? 1.0: 0.0;
		else if(this.categoricaldata != null)
			return this.categoricaldata[latindex][longindex];
		else
			return this.continuousdata[latindex][longindex];
	}
	
	
	
	
	
	
	public float getValue(double lat, double lon)
	{
		return getValue(lat, lon, false);
	}
	
	
	/*
	 * Binary layers have all values as either true or false.
	 * 
	 * Categorical layers do not have to classify the whole region. Unclassified
	 * regions have value of -1 internally as a class label, but
	 * return NaN for this method
	 * 
	 * Continuous layers can return either a value within the range, if 
	 * a value can be calculated, or NaN, which indicates no value
	 * for a particular region.
	 * 
	 * 
	 * 
	 * For continuous layers, you can specify the value returned to be
	 * an interpolation of the neighbouring points (weighted by 1/distance) 
	 * for which actual data is available, but by default, it just
	 * takes the value at the index closest to the specified lat/long
	 */	
	public float getValue(double lat, double lon, boolean interp)
	{
		if(lat < this.southlat || lat > this.southlat+this.latsize || lon < this.westlong || lon > this.westlong+this.longsize) {
			Debug.println("Trying to get value at lat/long outside of layer range. Returning NaN ", Debug.IMPORTANT);
			return Float.NaN;
		}
		
		int latsteps = this.getLatSteps();
		int longsteps = this.getLongSteps();
		int[] latlonindex = getLatLongIndices(lat, lon, southlat, southlat+latsize, westlong, westlong+longsize, latsteps, longsteps);
		int gislat = latlonindex[0];
		int gislong = latlonindex[1];
		
		
		if(this.binarycategoricaldata != null)
			//return this.binarycategoricaldata[gislat].get(gislong) ? 1 : 0;
			return this.binarycategoricaldata[gislat][gislong] ? 1 : 0;
		else if(this.categoricaldata != null)
		{
			if(categoricaldata[gislat][gislong] == -1)
				return Float.NaN;
			return categoricaldata[gislat][gislong];
		}
		else 
		{
			if(!interp) {
				//System.out.println("Lati/Longi are "+gislat+","+gislong+" and value is "+continuousdata[gislat][gislong]);
				return this.continuousdata[gislat][gislong];
			}
			
			double[] realind = getLatLongPartialIndices(lat, lon, southlat, southlat+latsize, westlong, westlong+longsize, latsteps, longsteps);
			double weightsum = 0.0;
			double unscaledsum = 0.0;
			for(int i = -1; i <= 1; i++)
				for(int j = -1; j <= 1; j++)
				{
					if(gislat+i >= 0 && gislong+j >= 0 && gislat+i < continuousdata.length && gislong+j < continuousdata[0].length) 
					{
						float val = this.continuousdata[gislat+i][gislong+j];
						if(Float.isNaN(val))
							continue;
						double dist = Math.sqrt(Math.pow(realind[0]-gislat-i-0.5, 2)+Math.pow(realind[1]-gislong-j-0.5, 2));
						Debug.println(lat+" , "+lon+" is "+dist+" away from data point with value "+val, Debug.EXTRA_INFO);
						if(dist < 0.05) 
						{
							Debug.println(lat+" , "+lon+" : Value exactly on data point", Debug.EXTRA_INFO);
							if(i != 0 || j != 0)
								throw new IllegalStateException("Internal error in GIS interpolation");
							return val;
						}
						double weight = 1.0/dist;
						weightsum += weight;
						unscaledsum += val*weight;
					}

				}
			return (float) (unscaledsum/weightsum);
		}
		
		
	}

	
	
	
	
	public int[] getLatLongIndices(double lat, double lon)
	{
		double[] ind = getLatLongPartialIndices(lat, lon, southlat, southlat+latsize, 
				westlong, westlong+longsize, this.getLatSteps(), this.getLongSteps());
		int[] res = new int[] {(int) Math.floor(ind[0]), (int) Math.floor(ind[1])};
		if(res[0] == this.getLatSteps())
			res[0]--;
		if(res[1] == this.getLongSteps())
			res[1]--;
		return res;
	}

	
	
	public double getLatStepSizeInMetres()
	{
		double avglong = 0.5*getMinLong()+0.5*getMaxLong();
		double squaresize_m = MathUtil.getDistanceBetweenPointsOnEarth(getMinLat(), avglong, getMaxLat(), avglong)/getLatSteps();
		return squaresize_m;
	}

	public double getLonStepSizeInMetres()
	{
		double avglat = 0.5*getMinLat()+0.5*getMaxLat();
		double squaresize_m = MathUtil.getDistanceBetweenPointsOnEarth(avglat, getMinLong(), avglat, getMaxLong())/getLongSteps();
		return squaresize_m;
	}

	
	private static int[] getLatLongIndices(double lat, double lon, double minlat, double maxlat, 
			double minlong, double maxlong, int latsteps, int longsteps)
	{
		double[] ind = getLatLongPartialIndices(lat, lon, minlat, maxlat, 
				minlong, maxlong, latsteps, longsteps);
		int[] res = new int[] {(int) Math.floor(ind[0]), (int) Math.floor(ind[1])};
		if(res[0] == latsteps)
			res[0]--;
		if(res[1] == longsteps)
			res[1]--;
		return res;
	}

	
	public static double[] getLatLongPartialIndices(double lat, double lon, double minlat, double maxlat, 
			double minlong, double maxlong, int latsteps, int longsteps)
	{
		double gislat = ((lat-minlat)/(maxlat-minlat))*latsteps;
		double gislong = ((lon-minlong)/(maxlong-minlong))*longsteps;
		return new double[] {gislat, gislong};
	}

	
	
	public void sqrtTransform()
	{
		//now do the transform
		for(int i =0; i < continuousdata.length; i++)
			for(int j = 0; j < continuousdata.length; j++)
			{
				if(!Float.isNaN(continuousdata[i][j]))
					continuousdata[i][j] = (float) Math.sqrt(-this.minval+continuousdata[i][j]);
			}
		
		this.minval = 0.0f;
		this.maxval = (float) Math.sqrt(maxval-minval);		
	}
	
	
	
	/** Log transform with automatically selected pivot of minval
	 *  (if minval >= 1), or (-minval+1) otherwise.
	 *
	 */
	public void logTransform()
	{
		if(this.continuousdata == null)
			throw new UnsupportedOperationException("Cannot perform ma log transform of a non-continuouus layer");

		double offset = minval;
		if(this.minval < 1)
			offset = -minval+1;
		logTransform(offset);
	}
	
	/** Take the natural log transform of the layer. 
	 * 
	 * Specifically, each data value is transformed to
	 * Math.log(oldvalue+pivot).
	 * 
	 *
	 */
	public void logTransform(double pivot)
	{
		
		//now do the transform
		for(int i =0; i < continuousdata.length; i++)
			for(int j = 0; j < continuousdata[0].length; j++)
			{
				if(!Float.isNaN(continuousdata[i][j]))
					continuousdata[i][j] = (float) Math.log(pivot+continuousdata[i][j]);
			}
		
		this.minval = (float) Math.log(minval+pivot);
		this.maxval = (float) Math.log(maxval+pivot);
	}
	
	
	
	
	
	public double[] getValuesAtLatLons(double[][] latlons) 
	{
		double[] res = new double[latlons.length];
		for(int i = 0; i < latlons.length; i++)
			res[i] = this.getValue(latlons[i][0], latlons[i][1]);
		return res;
	}

	
	public void printValuesAtLatLons(String latlonfile) throws IOException
	{
		String[] lines = FileUtil.getLines(latlonfile);
		double[][] latlongvects = FileUtil.readVectorsFromFile(new java.io.File(latlonfile));
			
		//first and second entries must be lat/long
		for(int i =0; i < latlongvects.length; i++)
		{
			double lat = latlongvects[i][0];
			double lon = latlongvects[i][1];
			
			int index = (int) Math.round(this.getValue(lat, lon));
			String val = this.catnames[index];
			System.out.println(lines[i]+" "+val);
		}
	}

	
	
	
	/**cycle through every pixel in this layer, and if the
	 * mask layer is > 0 (or true, for non-binary layers)
	 * we mask in the mask layer. Behavior is dependent on
	 * the layer types.
	 * BINARY) If the mask layer
	 * is true, this layer is set to true. (i.e equivalent to OR) 
	 * CATEGORICAL) If the mask layer
	 * has a category set, we set this layer category to -1. 
	 * CONTINUOUS) If the mask layer has a value, we set 
	 * this layer to that value.
	 * 
	 * @param mask
	 * @throws Exception
	 */
	public void maskIn(GISLayer mask) throws Exception
	{
		GISLayer layer1 = this;
		
		int latsteps = layer1.getLatSteps();
		int lonsteps = layer1.getLongSteps();
		
		double minlat = layer1.getMinLat();
		double maxlat = minlat+layer1.getLatSize();
		double minlong = layer1.getMinLong();
		double maxlong = minlong + layer1.getLongSize();
		double latstep = (maxlat-minlat)/latsteps;
		double lonstep = (maxlong-minlong)/lonsteps;

		
		for(int i = 0; i < latsteps; i++)
		{
			double lat = minlat+i*latstep+latstep/2;
			for(int j = 0; j < lonsteps; j++)
			{
				//check water layers. if they say it is water, we mask out trees/grass
				double lon = minlong+j*lonstep+lonstep/2;
				float val = mask.getValue(lat, lon);
				if(val > 0.0f)
				{
					if(layer1.binarycategoricaldata != null)
						layer1.binarycategoricaldata[i][j]= true;
					else if(layer1.categoricaldata != null)
						layer1.categoricaldata[i][j] = -1;
					else 
						layer1.continuousdata[i][j] = val;
				}
			}
		}
	}

	

	
	
	
	
	
	private static void usage()
	{
		System.err.println("usage:");
		System.err.println("");
		System.err.println("arg1: either \"makelayer\" OR \"makeoverlay\" or \"transform\" or \"extract\" or \"join\" or \"createLayerFromPlacemarks\" or \"calcregionarea\"");
		System.err.println("");
		System.err.println("For makelayer");
		System.err.println("arg2: data file name (to make layer from)");
		System.err.println("arg3: layer type (binary, categorical, continuous)");
		System.err.println("format for file is:");
		System.err.println("   line[0]: minlat maxlat minlong maxlong");
		System.err.println("   line[1]: # columns");
		System.err.println("   line[2]: # rows");
		System.err.println("   line[3]...line[rows+2]: data rows, space delimited, in order of *increasing* latitude"); 
		System.err.println("NB: layer is saved as \"layer.gis\"");
		System.err.println("");
		System.err.println("For makeoverlay");
		System.err.println("arg2: gis layer file to read GIS layer from");
		System.err.println("arg3: urlbase where image will live");
		System.err.println("");
		System.err.println("For transform");
		System.err.println("arg2: gis layer file to read GIS layer from");
		System.err.println("arg3: name of transformation");
		System.err.println("(NB: transformed layer is saved as transform.gis)");
		System.err.println("");
		System.err.println("For extract");
		System.err.println("arg2: gis layer file to read GIS layer from");
		System.err.println("arg3: file name with lat/longs to extract values for");
		System.err.println("(NB: results are written to the file 'tmpgis.dat')");
		System.err.println("");
		System.err.println("For extractRegionData");
		System.err.println("arg2: gis layer file with region membership");
		System.err.println("arg3: gis layer with data");
		System.err.println("You will be prompted for a data file with lines of the form REGIONID LAT LON");
		System.err.println("The data is extracted at lat/lon, unless that position is not in the region (i.e. centroid is outside of region polygon), in which case it looks nearby until it finds the closest value to lat/long that is in the right region");
		System.err.println("(NB: results are written to the file 'tmpgis.dat')");
		System.err.println("");
		System.err.println("For join");
		System.err.println("arg2: gis layer file # 1");
		System.err.println("arg3: gis layer file #2");
		System.err.println("(NB: results are written to the file 'joined.gis')");
		System.err.println("");
		System.err.println("For crop");
		System.err.println("arg2: base gis layer");
		System.err.println("arg3: crop gis layer");
		System.err.println("In the base layer, anywhere that the crop layer has a (false,-1,NaN) value (for binary, categorical, continuous crop layers)");
		System.err.println("gets cropped (to (false,-1,NaN) depending on the type of the base layer).");
		System.err.println("In addition, anything outside the bounds of the base layer is also masked out");
		System.err.println("(NB: results are written to the file 'cropped.gis')");
		System.err.println("");		
		System.err.println("For map");
		System.err.println("arg2: gis layer file # 1");
		System.err.println("arg3: file specifying mapping values");
		System.err.println("every value in layer is mapped to the value specified in the file.");
		System.err.println("");
		System.err.println("For areaaverage");
		System.err.println("arg2: gis layer file # 1 (containing region membership)");
		System.err.println("arg3: gis layer file # 2 (containing values)");
		System.err.println("The average value (from gis layer 2) in calculated for each region in gis layer 1");
		System.err.println("Results are printed to screen");
		System.err.println("");
		System.err.println("For calcregionarea");
		System.err.println("arg2: gis layer file # 1 (containing region membership)");
		System.err.println("arg3: name of LOGFILE to log results to"); 
		System.err.println("The area counts (i.e. # of gis grid squares) is counted for each area... this is *not* a physical measure)");
		System.err.println("Results are written to LOGFILE");
		System.err.println("");		
		System.err.println("For createLayerFromPlacemarks");
		System.err.println("arg2: gis layer file (data not used, used only to define region and resolution)");
		System.err.println("arg3: data file with lat/long placemarks");
		System.err.println("Creates a binary layer, where all squares in the gis layer within X metres of a placemark are given the value 1, or 0 otherwise (X is prompted for)");
		System.err.println("");
		System.err.println("For printdata");
		System.err.println("arg2: gis layer");
		System.err.println("arg3: another gis layer to print, or MULTI if multiple other layers should be printed, in which case the user will be prompted.");
		System.err.println("");
		System.err.println("");
		System.err.println("For calcregioncentroids");
		System.err.println("arg2: gis layer");
		System.err.println("arg3: output file.");
		System.err.println("");
		System.err.println("For dump");
		System.err.println("arg2: gis layer");
		System.err.println("arg3: must be the string \"NONE\" (without the quotes)");
		System.err.println("");
		System.err.println("For getValuesAtLatLons");
		System.err.println("arg2: membership gis layer");
		System.err.println("arg3: a file with a lat and long value on each line");
		System.err.println("");
		System.exit(1);
	}
	
	/** create a KML layer from a GIS layer
	 * 
	 * @param args
	 */
	public static void main(String[] args) throws Exception
	{
		Debug.setVerbosityLevel(Debug.INFO);
		if(args.length != 3)
			usage();
		
		String mode = args[0];
		
		if(mode.equalsIgnoreCase("makelayer"))
		{
			Debug.println("Reading in data", Debug.IMPORTANT);
			GISLayer gis = GISLayer.makeLayerFromFile(args[1], "NONE", args[2]);
			String savefile = "layer.gis";
			Debug.println("Saving layer to file "+savefile, Debug.IMPORTANT);
			gis.saveToFile(savefile);
		}
		else if(mode.equalsIgnoreCase("makeoverlay"))
		{
			GISLayer layer = GISLayer.readFromFile(args[1]);
			Debug.println("Read in layer... creating overlay", Debug.INFO);
			BufferedReader rdr = new BufferedReader(new java.io.InputStreamReader(System.in));
			Debug.print("what shrink factor to use?: ", Debug.CRITICAL);
			int shrinkfact = Integer.parseInt(rdr.readLine());
			if(shrinkfact != 1)
				layer.shrink(shrinkfact);
			Debug.print("do you want to log-scale the data?: ", Debug.CRITICAL);
			char yn = rdr.readLine().toLowerCase().charAt(0);
			if(yn == 'y') {
				Debug.print("What should I add to each value (default is 1.0)?: ", Debug.CRITICAL);
				double baseval = Double.parseDouble(rdr.readLine());
				layer.logTransform(baseval);
			}
			Debug.print("What colours to use (br,rb,wb,all) ", Debug.CRITICAL);
			String type = rdr.readLine();
			if(type.equalsIgnoreCase("br"))
				layer.generateGoogleEarthOverlay(args[2], Color.BLUE, Color.RED);
			else if(type.equalsIgnoreCase("rb"))
				layer.generateGoogleEarthOverlay(args[2], Color.RED, Color.BLUE);
			else if(type.equalsIgnoreCase("wb"))
				layer.generateGoogleEarthOverlay(args[2], Color.WHITE, Color.BLACK);
			else if(type.equalsIgnoreCase("all"))
				layer.generateGoogleEarthOverlay(args[2]);
			else
				throw new RuntimeException("No colours specified.");
		}
		else if(mode.equalsIgnoreCase("transform"))
		{
			GISLayer layer = GISLayer.readFromFile(args[1]);
			Debug.println("Read in layer... now transforming", Debug.INFO);
			if(args[2].equalsIgnoreCase("logscale"))
				layer.logTransform();
			else if(args[2].equalsIgnoreCase("shrink")) {
				BufferedReader rdr = new BufferedReader(new InputStreamReader(System.in));
				System.out.print("What shrink factor to use? ");
				String line = rdr.readLine();
				int sfact = Integer.parseInt(line.trim());
				layer.shrink(sfact);
			}
			else if(args[2].equalsIgnoreCase("shrink_modal")) {
				BufferedReader rdr = new BufferedReader(new InputStreamReader(System.in));
				System.out.print("What shrink factor to use? ");
				String line = rdr.readLine();
				int sfact = Integer.parseInt(line.trim());
				layer.shrink_modal(sfact);
			}
			else if(args[2].equalsIgnoreCase("sqrt"))
				layer.sqrtTransform();
			else if(args[2].equalsIgnoreCase("or")) {
				BufferedReader rdr = new BufferedReader(new InputStreamReader(System.in));
				System.out.print("Enter mask layer: ");
				String masklayer = rdr.readLine().trim();
				rdr.close();
				GISLayer mask = GISLayer.readFromFile(masklayer);
				layer.maskIn(mask);
			}
			else if(args[2].equalsIgnoreCase("truncateDisplay")) {
				System.out.println("Please note -- this will not alter te underlying layer data.. it will only set the maximum threshold for displayed values. That is, all values greater than the truncated value will be displayed as the truncated value.. but any analytics you do with the layer will still get access to the true value");
				BufferedReader rdr = new BufferedReader(new InputStreamReader(System.in));
				System.out.print("Enter max value: ");
				float truncval = Float.parseFloat(rdr.readLine().trim());
				layer.comment = layer.comment+PlatformInfo.nl+"Truncated. Old maxval was "+layer.maxval;
				layer.maxval = truncval;
			}
			else
				throw new RuntimeException("Unknown/Unsupported transform: "+args[2]);
			layer.saveToFile("transform.gis");			
		}
		else if(mode.equalsIgnoreCase("crop"))
		{
			GISLayer baselayer = GISLayer.readFromFile(args[1]);
			GISLayer croplayer = GISLayer.readFromFile(args[2]);
			Debug.println("Read in layers... now cropping", Debug.INFO);
			
			//If the crop layer is binary, we crop when it is false
			//If the crop layer is categorical, we crop when the category is -1
			//If the crop layer is continuous, we crop when value is NaN
			
			float[][] res_float = null;
			int[][] res_cat = null;
			boolean[][] res_bin = null;
			
			int latsteps = baselayer.getLatSteps();
			int longsteps = baselayer.getLongSteps();
			double latstep = (baselayer.getMaxLat()-baselayer.getMinLat())/baselayer.getLatSteps();
			double lonstep = (baselayer.getMaxLong()-baselayer.getMinLong())/baselayer.getLongSteps();
			
			if(baselayer.binarycategoricaldata != null) res_bin = Util.copy(baselayer.binarycategoricaldata);
			else if(baselayer.categoricaldata != null) res_cat = Util.copy(baselayer.categoricaldata);
			else if(baselayer.continuousdata != null) res_float = Util.copy(baselayer.continuousdata);
			else throw new RuntimeException("No data in base layer?");
			
			int lati = 0;
			for(double lat = baselayer.getMinLat()+latstep/2; lat < baselayer.getMaxLat(); lat += latstep, lati++)
			{
				int loni = 0;
				for(double lon = baselayer.getMinLong()+lonstep/2; lon < baselayer.getMaxLong(); lon += lonstep, loni++)
				{
					if(lat < croplayer.getMinLat() || lat > croplayer.getMaxLat() ||
					   lon < croplayer.getMinLong() || lon > croplayer.getMaxLong())
					{
						if(baselayer.binarycategoricaldata != null) res_bin[lati][loni] = false; 
						else if(baselayer.categoricaldata != null) res_cat[lati][loni] = -1;
						else res_float[lati][loni] = Float.NaN;						
					}
					else
					{
						boolean maskOut = false;
						if(croplayer.continuousdata != null && Float.isNaN(croplayer.getValue(lat, lon)))
							maskOut = true;
						else if(croplayer.categoricaldata != null && (int) croplayer.getValue(lat, lon) == -1)
							maskOut = true;
						else if(croplayer.binarycategoricaldata != null && croplayer.getValue(lat, lon) == 0.0)
							maskOut = true;
						
						if(maskOut)
						{
							if(baselayer.binarycategoricaldata != null) 
								res_bin[lati][loni] = false;
							else if(baselayer.categoricaldata != null) 
								res_cat[lati][loni] = -1;
							else
								res_float[lati][loni] = Float.NaN;						
						}
						
					}
				}
			}
			
			if(baselayer.binarycategoricaldata != null)  
				new GISLayer(baselayer.getMinLat(), baselayer.getMinLong(), baselayer.getLatSize(), baselayer.getLongSize(), "CROPPED", res_bin).saveToFile("cropped.gis");
			else if(baselayer.categoricaldata != null) 
				new GISLayer(baselayer.getMinLat(), baselayer.getMinLong(), baselayer.getLatSize(), baselayer.getLongSize(), "CROPPED", res_cat, baselayer.catnames).saveToFile("cropped.gis");
			else 								
				new GISLayer(baselayer.getMinLat(), baselayer.getMinLong(), baselayer.getLatSize(), baselayer.getLongSize(), "CROPPED", res_float).saveToFile("cropped.gis");
			
			
			
			
			
			
			
		}
		else if(mode.equalsIgnoreCase("join")) 
		{
			GISLayer layer1 = GISLayer.readFromFile(args[1]);
			GISLayer layer2 = GISLayer.readFromFile(args[2]);
			Debug.println("Read in layers... now joining", Debug.INFO);
			
			
			if(layer1.binarycategoricaldata != null)
			{
				double startlat = Math.min(layer1.getMinLat(), layer2.getMinLat());
				double endlat = Math.max(layer1.getMinLat()+layer1.getLatSize(), layer2.getMinLat()+layer2.getLatSize());
				double startlon = Math.min(layer1.getMinLong(), layer2.getMinLong());
				double endlon = Math.max(layer1.getMinLong()+layer1.getLongSize(), layer2.getMinLong()+layer2.getLongSize());

				double l1_maxlat = layer1.getMinLat()+layer1.getLatSize();
				double l2_maxlat = layer2.getMinLat()+layer2.getLatSize();
				double l1_maxlong = layer1.getMinLong()+layer1.getLongSize();
				double l2_maxlong = layer2.getMinLong()+layer2.getLongSize();
				
				
				int latsteps = layer1.getLatSteps()+layer2.getLatSteps();
				int lonsteps = layer1.getLongSteps()+layer2.getLongSteps();
				double latstep = (endlat-startlat)/latsteps;
				double lonstep = (endlon-startlon)/lonsteps;
				boolean[][] comb = new boolean[latsteps][lonsteps];
				int lati = 0;
				for(double lat = startlat+latstep/2; lat < endlat; lat += latstep, lati++)
				{
					int loni = 0;
					for(double lon = startlon+lonstep/2; lon < endlon; lon += lonstep, loni++)
					{
						if(lat <= l1_maxlat && lat >= layer1.getMinLat() &&
						   lon <= l1_maxlong && lon >= layer1.getMinLong() &&
						   layer1.getValue(lat, lon) > 0)
							comb[lati][loni] = true;
						if(lat <= l2_maxlat && lat >= layer2.getMinLat() &&
								   lon <= l2_maxlong && lon >= layer2.getMinLong() &&
								   layer2.getValue(lat, lon) > 0)
									comb[lati][loni] = true;	
					}
				}
				
				new GISLayer("JOINED", startlat, endlat, startlon, endlon, comb).saveToFile("joined.gis");
				
			}
			else
				throw new UnsupportedOperationException("Not implemented for non-binary layers yet. Shouldnt take long to do.");
			
		}
		else if(mode.equalsIgnoreCase("extract"))
		{
			GISLayer layer = GISLayer.readFromFile(args[1]);
			double[][] datavects = FileUtil.readVectorsFromFile(new File(args[2]));
			PrintStream outstream = new PrintStream(new FileOutputStream("tmpgis.dat"));
			for(int i = 0; i < datavects.length; i++)
			{
				float val = layer.getValue(datavects[i][0], datavects[i][1]);
				for(int j = 0; j < datavects[0].length; j++)
					outstream.print(datavects[i][j]+" ");
				outstream.println(val);
				//outstream.println(datavects[i][0]+" "+datavects[i][1]+" "+val);
			}
			outstream.close();
		}
		else if(mode.equalsIgnoreCase("extractRegionData"))
		{
			BufferedReader rdr = new BufferedReader(new InputStreamReader(System.in));
			System.out.print("Name of file with REGIONID LAT LONG values ? ");
			String line = rdr.readLine();
			double[][] datavects = FileUtil.readVectorsFromFile(new File(line));

			GISLayer membershiplayer = GISLayer.readFromFile(args[1]);
			GISLayer datalayer = GISLayer.readFromFile(args[2]);			
			
			PrintStream outstream = new PrintStream(new FileOutputStream("tmpgis.dat"));
			for(int i = 0; i < datavects.length; i++)
			{
				double lat = datavects[i][1];
				double lon = datavects[i][2];
				if(lat < membershiplayer.southlat || lat > membershiplayer.getMaxLat() ||
				   lon > membershiplayer.getMaxLong() || lon < membershiplayer.westlong)
				{
					Debug.println("Centroid for region "+datavects[i][0]+" outside of layer coverage", Debug.CRITICAL);
					continue;
				}
				else if(lat < datalayer.southlat || lat > datalayer.getMaxLat() ||
						   lon > datalayer.getMaxLong() || lon < datalayer.westlong)
				{
					Debug.println("Centroid for region "+datavects[i][0]+" outside of data layer coverage", Debug.CRITICAL);
					continue;					
				}
				int[] ind = datalayer.getClosestIndicesForValue(lat, lon, (float) datavects[i][0], 10);
				if(ind == null)
					Debug.println("couldnt find any squares near centroid for region "+datavects[i][0]+" with specified membership.. membership at centroid is "+membershiplayer.getValue(lat, lon)+" and value is "+datalayer.getValue(lat, lon), Debug.CRITICAL);
				else {
					float val = datalayer.continuousdata[ind[0]][ind[1]];
					for(int j = 0; j < datavects[0].length; j++)
						outstream.print(datavects[i][j]+" ");
					outstream.println(val);
				}
			}
			outstream.close();
		}		
		else if(mode.equalsIgnoreCase("calcregionarea"))
		{
			GISLayer regionfile = GISLayer.readFromFile(args[1]);
			PrintStream logfile = new PrintStream(new FileOutputStream(args[2]));
			HashMap countmap = new HashMap();
			
			for(int lati = 0; lati < regionfile.getLatSteps(); lati++)
			{
				for(int longi = 0; longi < regionfile.getLongSteps(); longi++)
				{
					double regionid =  regionfile.getRawValueByIndices(lati, longi);
					if(Double.isNaN(regionid))
						continue;
					
					Integer key = new Integer((int) regionid);
					if(countmap.containsKey(key))
					{
						int[] count = (int[]) countmap.get(key);
						count[0]++;
					}
					else
					{
						countmap.put(key, new int[] {1});
					}
				}
				Debug.println("Collated results from lat row "+lati, Debug.IMPORTANT);
			}
			
			
			//ok, go through and print results.
			Iterator keyit = countmap.keySet().iterator();
			while(keyit.hasNext()) {
				Object key = keyit.next();
				int count = ((int[]) countmap.get(key))[0];
				logfile.println(key+" "+(count));
			}
			
		}
		else if(mode.equalsIgnoreCase("calcregioncentroids"))
		{
			GISLayer regionfile = GISLayer.readFromFile(args[1]);
			PrintStream logfile = new PrintStream(new FileOutputStream(args[2]));
			
			Map<Float, double[]> centroids = getRegionCentroids(regionfile);
			
			Iterator<Float> keyit = centroids.keySet().iterator();
			while(keyit.hasNext())
			{
				Float key = keyit.next();
				double[] latlon = centroids.get(key);
				logfile.println(key+" "+latlon[0]+" "+latlon[1]);
			}
			logfile.close();
		}
		else if(mode.equalsIgnoreCase("areaaverage"))
		{
			GISLayer regionfile = GISLayer.readFromFile(args[1]);
			GISLayer valuefile = GISLayer.readFromFile(args[2]);
			
			//need to keep track of averages for each region
			HashMap map = new HashMap();
			HashMap countmap = new HashMap();
			
			double latrange = valuefile.getLatSize();
			double lonrange = valuefile.getLongSize();
			double latstep = latrange/valuefile.getLatSteps();
			double lonstep = lonrange/valuefile.getLongSteps();
			
			int lati = 0;
			for(double lat = valuefile.getMinLat()+latstep/2; lat < valuefile.getMinLat()+latrange; lat += latstep, lati++)
			{
				for(double lon = valuefile.getMinLong()+lonstep/2; lon < valuefile.getMinLong()+lonrange; lon += lonstep)
				{
					float regionid = regionfile.getValue(lat, lon);
					float val = valuefile.getValue(lat, lon);
					if(Float.isNaN(regionid) || Float.isNaN(val))
						continue;
					
					Integer key = new Integer((int) regionid);
					if(map.containsKey(key))
					{
						double[] sum = (double[]) map.get(key);
						sum[0] = sum[0] + val;
						int[] count = (int[]) countmap.get(key);
						count[0]++;
					}
					else
					{
						map.put(key, new double[] {val});
						countmap.put(key, new int[] {1});
					}
				}
				Debug.println("Collated results from lat row "+lati, Debug.IMPORTANT);
			}
			
			
			//ok, go through and print results.
			Iterator keyit = map.keySet().iterator();
			while(keyit.hasNext()) {
				Object key = keyit.next();
				double sum = ((double[]) map.get(key))[0];
				int count = ((int[]) countmap.get(key))[0];
				Debug.println(key+" "+(sum/count), Debug.IMPORTANT);
			}
			
		}
		else if(mode.equalsIgnoreCase("map"))
		{
			class RealMapFunction extends RealFunction 
			{
				HashMap map;
				RealMapFunction(HashMap map) {
					this.map = map;
				}
				
				public int getDimensionOfDomain() { return 1; }
				
				public double[][] getDomain() {
					return new double[][] {{Float.MIN_VALUE, Float.MAX_VALUE}};
				}
				
				public double invoke(double[] args) {
					Double origval = new Double(args[0]);
					int val = ((int) Math.round(args[0]));
					Double mapping = (Double) map.get(new Integer(val));
					if(mapping == null)
						return Double.NaN;
					return mapping.doubleValue(); 
				}
				
				public double invoke(int[] args) {throw new UnsupportedOperationException(); }
			}
			
			//create the hashmap from the file specified
			HashMap mappings = new HashMap();
			BufferedReader rdr = new BufferedReader(new FileReader(args[2]));
			String line = rdr.readLine();
			Debug.println("reading mappings....", Debug.IMPORTANT);
			while(line != null) {
				String[] words = Util.getWords(line);
				if(words.length != 2) throw new RuntimeException("mapping file not in right format");
				
				Integer val = new Integer(words[0]);
				
				Double mapping = new Double(words[1]);
				mappings.put(val, mapping);
				line = rdr.readLine();
			}
			Debug.println("Finished reading mappings, now reading unmapped layer", Debug.IMPORTANT);
			GISLayer gis = GISLayer.readFromFile(args[1]);
			
			Debug.println("OK, now applying mapping", Debug.IMPORTANT);
			createMappedLayer(gis, new RealMapFunction(mappings)).saveToFile("mapped.gis");
		}
		else if(mode.equalsIgnoreCase("createLayerFromPlacemarks"))
		{
			GISLayer base = GISLayer.readFromFile(args[1]);
			double[][] placemarks = FileUtil.readVectorsFromFile(new File(args[2]));
			System.out.print("How many metres from each place mark is defined as being near the placemark? ");
			double X = Double.parseDouble(new BufferedReader(new InputStreamReader(System.in)).readLine());
			boolean[][] resdata = new boolean[base.getLatSteps()][base.getLongSteps()];
			double latstep = (base.getMaxLat()-base.getMinLat())/base.getLatSteps();
			double lonstep = (base.getMaxLong()-base.getMinLong())/base.getLongSteps();
			int lati = 0;
			for(double lat = base.getMinLat()+latstep/2; lat < base.getMaxLat(); lat += latstep, lati++)
			{
				int loni = 0;
				for(double lon = base.getMinLong()+lonstep/2; lon < base.getMaxLong(); lon += lonstep, loni++)
				{
					//go through all the placemarks and find if its within X metres of them
					for(int i = 0; i < placemarks.length; i++)
					{
						double dist = MathUtil.getDistanceBetweenPointsOnEarth(lat, lon, placemarks[i][0], placemarks[i][1]);
						if(dist <= X) {
							resdata[lati][loni] = true;
							break;
						}
					}
				}
				Debug.println("Done lat row "+lati+" of "+base.getLatSteps(), Debug.INFO);
			}
			
			GISLayer newlayer = new GISLayer("PlacemarkProximity", base.getMinLat(), base.getMaxLat(), base.getMinLong(), base.getMaxLong(), resdata);
			newlayer.saveToFile("placemarkprox.gis");
			
		}
		else if(mode.equalsIgnoreCase("replaceNaN"))
		{
			GISLayer layer = GISLayer.readFromFile(args[1]);
			float replacewith = Float.parseFloat(args[2]);
			for(int i = 0; i < layer.continuousdata.length; i++)
				for(int j = 0; j < layer.continuousdata[0].length; j++)
					if(Float.isNaN(layer.continuousdata[i][j]))
						layer.continuousdata[i][j] = replacewith;
			layer.saveToFile("replacedlayer.gis");
		}
		else if(mode.equalsIgnoreCase("printdata"))
		{
			/*GISLayer layer = GISLayer.readFromFile(args[1]);
			
			//work out what layers we need
			GISLayer[] otherlayers = null;
			if(args[2].equalsIgnoreCase("MULTI")) {
				Debug.println("OK, enter all gis layers to read from on the single line (below), separated by spaces", Debug.CRITICAL);
				Debug.print("layers?> ", Debug.CRITICAL);
				BufferedReader rdr = new BufferedReader(new java.io.InputStreamReader(System.in));
				String[] layernames = Util.getWords(rdr.readLine());
				otherlayers = new GISLayer[layernames.length];
				for(int i =0; i < layernames.length; i++)
					otherlayers[i] = GISLayer.readFromFile(layernames[i]);
			}
			else
				otherlayers = new GISLayer[] {GISLayer.readFromFile(args[2])};
			
			//now go through and print out the main layer and results 
			//from all other layers
			double latstep = (layer.getMaxLat()-layer.getMinLat())/layer.getLatSteps();
			double longstep = (layer.getMaxLong()-layer.getMinLong())/layer.getLongSteps();
			for(double lat = layer.getMinLat()+latstep/2; lat < layer.getMaxLat(); lat += latstep)
			{
				for(double lon = layer.getMinLong()+longstep/2; lon < layer.getMaxLong(); lon += longstep)
				{
					
				}
			}
			*/
			throw new RuntimeException("Not implemented yet");
		}
		else if(mode.equalsIgnoreCase("dump")) 
		{
			GISLayer layer = GISLayer.readFromFile(args[1]);
			layer.dumpAsciiLatLongVal();
		}
		else if(mode.equalsIgnoreCase("getValuesAtLatLons")) 
		{
			GISLayer layer = GISLayer.readFromFile(args[1]);
			if(layer.categoricaldata == null)
				throw new RuntimeException("Layer must be categorical");
			String llfile = args[2];
			layer.printValuesAtLatLons(llfile);
		}
		else usage();
	}
	
	
	
	
}