package rses.spatial.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;



import rses.Debug;
import rses.math.MathUtil;
import rses.spatial.GISLayer;
import rses.util.FileUtil;
import rses.util.Util;








public class CreateLayers
{
	
	
	
	
	
	private static byte[][] shrinkLayer(byte[][] layer, int shrinkfactor)
	{
		//Debug.println("WARNING-- shrinking discards categories other than the first 2", Debug.IMPORTANT);
		if(layer.length % shrinkfactor != 0 ||
		   layer[0].length % shrinkfactor != 0)
			throw new RuntimeException("Cannot shrink layer. Not exactly divisible");
		byte[][] res = new byte[layer.length/shrinkfactor][layer[0].length/shrinkfactor];
		for(int i=0; i < layer.length; i+= shrinkfactor)
		{
			for(int j = 0; j < layer[0].length; j+= shrinkfactor)
			{
				//take the majority
				int[] count = new int[Byte.MAX_VALUE];
				for(int k=0; k < shrinkfactor; k++)
					for(int l = 0; l < shrinkfactor; l++)
					{
						int cat = layer[i+k][j+l];
						if(cat >= 0) //dont count 'dont knows'
							count[cat]++;
					}
				if(Util.getSum(count) > 0)
					res[i/shrinkfactor][j/shrinkfactor] = (byte) Util.getMaxIndex(count);
				else
					res[i/shrinkfactor][j/shrinkfactor] = -1;
			}
		}
		return res;
	}

	
	
	private static float[][] shrinkLayer(float[][] layer, int shrinkfactor)
	{
		if(layer.length % shrinkfactor != 0 ||
		   layer[0].length % shrinkfactor != 0)
			throw new RuntimeException("Cannot shrink layer. Not exactly divisible");
		float[][] res = new float[layer.length/shrinkfactor][layer[0].length/shrinkfactor];
		for(int i=0; i < layer.length; i+= shrinkfactor)
		{
			for(int j = 0; j < layer[0].length; j+= shrinkfactor)
			{
				double sum = 0.0;
				//just sum them all
				for(int k=0; k < shrinkfactor; k++)
					for(int l = 0; l < shrinkfactor; l++)
						sum += layer[i+k][j+l];
				res[i/shrinkfactor][j/shrinkfactor] = (float) sum;
			}
		}
		return res;
	}
	
	private static void normalize(float[][] layer)
	{
		double sum = 0.0;
		
		for(int i = 0; i < layer.length; i++)
			for(int j = 0; j < layer[0].length; j++)
				sum += layer[i][j];
		
		sum = sum/(layer.length*layer[0].length);
		
		for(int i = 0; i < layer.length; i++)
			for(int j = 0; j < layer[0].length; j++)
				layer[i][j] /= sum;

		
	}
	
	
	
	
	
	
	public static boolean[][] expandLayer(boolean[][] layerdata)
	{
		boolean[][] newdata = new boolean[layerdata.length][layerdata[0].length];
		
		for(int i =0; i < layerdata.length; i++)
		{
			jloop: for(int j =0; j < layerdata[0].length; j++)
			{
				for(int k = -1; k <= 1; k++)
					for(int l = -1; l <= 1; l++)
						if((i+k) >= 0 && (i+k) < layerdata.length && 
						   (j+l) >= 0 && (j+l) < layerdata[0].length &&
						   layerdata[i+k][j+l])
						{
							newdata[i][j] = true;
							continue jloop;
						}
			}
			if(i > 0)
				layerdata[i-1] = null;
		}
		return newdata;
	}
	
		
	
	
	
	
	//mask out everywhere where the mask layer is > 0
	//(or is true, in the case of binary layers)
	public static void maskOut(GISLayer layer1,	GISLayer mask) throws Exception
	{
		int latsteps = layer1.getLatSteps();
		int lonsteps = layer1.getLongSteps();
		
		double minlat = layer1.getMinLat();
		double maxlat = layer1.getMaxLat();
		double minlong = layer1.getMinLong();
		double maxlong = layer1.getMaxLong();
		double latstep = (maxlat-minlat)/latsteps;
		double lonstep = (maxlong-minlong)/lonsteps;

		
		for(int i = 0; i < latsteps; i++)
		{
			double lat = minlat+i*latstep+latstep/2;
			for(int j = 0; j < lonsteps; j++)
			{
				//check water layers. if they say it is water, we mask out trees/grass
				double lon = minlong+j*lonstep+lonstep/2;
				if(mask.getValue(lat, lon) > 0.0f)
				{
					if(layer1.binarycategoricaldata != null)
						layer1.binarycategoricaldata[i][j]= false;
					else if(layer1.categoricaldata != null)
						layer1.categoricaldata[i][j] = -1;
					else 
						layer1.continuousdata[i][j] = Float.NaN;
				}
			}
		}
	}

	

	
	
	
	
	
	
	
	
	
	
	
	
	
	/* Given a specified spatial aggregation
	 * (must be a GIS layer that is either categorical
	 * or continuous. If continuous, each distinct value
	 * is treated as a category), calculate the average
	 * value in each spatial region. A second GIS layer
	 * must be provided with the actual data. 
	 * 
	 * It only makes sense to do this if the second
	 * layer has data that can be interpreted as something
	 * like 'units of BLAH per unit area'. Housing density
	 * or population density are good examples that will work
	 */
	public static void createAggregateLayer(String gisregions, String gisdata) throws Exception
	{
		GISLayer regionlayer = GISLayer.readFromFile(gisregions);
		GISLayer datalayer = GISLayer.readFromFile(gisdata);

		
		//first we work out how many regions there are.
		//We keep a map of regionid (int) to 
		//a 2 element array with the first element being the
		//total and the second element being the number
		//of contributing values to that sum
		//HashMap map = new HashMap();
		
		double minlat = regionlayer.getMinLat();
		double maxlat = regionlayer.getMaxLat();
		double minlong = regionlayer.getMinLong();
		double maxlong = regionlayer.getMaxLong();
		double latstep = (maxlat-minlat)/regionlayer.getLatSteps();
		double lonstep = (maxlong-minlong)/regionlayer.getLongSteps();
		int rowcount = 1;
		int maxregions = (int) (regionlayer.getMaxVal()+1);
		double[] sums = new double[maxregions];
		double[] counts = new double[maxregions]; 
		for(double lat = minlat+latstep/2; lat < maxlat; lat += latstep) 
		{
			for(double lon = minlong + lonstep/2; lon < maxlong; lon += lonstep)
			{	
				float val = Float.NaN;
				if(regionlayer.categoricaldata != null) {
					val = regionlayer.getValue(lat, lon);
					if(val < 0) val = Float.NaN;
				}
				else
					val = regionlayer.getValue(lat, lon);
				
				if(Float.isNaN(val)) //ignore regions without an id
					continue;
				
				int regionid = (int) val;
				
				//Integer regionid = new Integer((int)Math.round(val));

				float datval = datalayer.getValue(lat, lon);
				if(Float.isNaN(datval)) 
					continue; //ignore regions without data
				
				sums[regionid] += datval;
				counts[regionid] += 1;
				
				//double[] v = (double[]) map.get(regionid);
				//if(v == null) {
				//	v = new double[2];
				//	map.put(regionid, v);
				//}
				
				//v[0] = v[0] + datval;
				//v[1] = v[1] + 1;
			}
			Debug.println("scanned values in row "+rowcount+" of "+regionlayer.getLatSteps(), Debug.INFO);
			rowcount++;
		}
		
		/*if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
		{
			int keys = map.keySet().size();
			int vals = map.values().size();
			if(keys != vals) 
				throw new RuntimeException("#keys and #vals do not match");
			Debug.println("There are "+keys+" regions in thhe region layer", Debug.INFO);
		}*/
		
		
		
		//work out the averages
		/*Iterator valit = map.values().iterator();
		while(valit.hasNext()) 
		{
			double[] v = (double[]) valit.next();
			if(v[1] > 0.0)
				v[0] = v[0] / v[1];
			else
				Debug.println("Region has no entries... this is a bit strange... setting average to 0", Debug.IMPORTANT);
		}*/
		for(int i =0; i < sums.length; i++)
			if(counts[i] > 0)
				sums[i] = sums[i]/counts[i];
		
		
		
		
		//ok, we have the regions and the average for each region.
		//now just create a new layer
		Debug.println("Worked out averages... initializing new layer", Debug.INFO);
		float[][] newdata = new float[regionlayer.getLatSteps()][regionlayer.getLongSteps()];
		int lati = 0;
		for(double lat = minlat+latstep/2; lat < maxlat; lat += latstep, lati++) 
		{
			int loni = 0;
			for(double lon = minlong + lonstep/2; lon < maxlong; lon += lonstep, loni++)
			{
				//ok, get the region
				float val = regionlayer.getValue(lat, lon);
				//what do we do with NaN's??
				//just preserve them I guess
				if(!Float.isNaN(val)) 
				{
					int regionid = (int) Math.round(val);
					//Integer regionid = new Integer((int)Math.round(val));
					//double avgval = ((double[]) map.get(regionid))[0];
					//val = (float) avgval;
					val = (float) sums[regionid];
				}
				newdata[lati][loni] = val;
			}
		}
		String savefile = "avgdata.gis";
		Debug.println("Saving averaged data to file "+savefile, Debug.IMPORTANT);
		new GISLayer("name", minlat, maxlat, minlong, maxlong, newdata).saveToFile(savefile);
		
	}
	
	
	

	private static void createCDLayer(
			String kmlfile, boolean excludeRegionsWithCentroidOutsideBoundingBox, int coarsesteps)
	throws Exception
	{
		double minlat = -34.10141604662701;
		double maxlat = -33.54141728667299;
		double minlong = 150.65141715537853;
		double maxlong = 151.3614161776215;

		createMembershipLayer(kmlfile, "name", excludeRegionsWithCentroidOutsideBoundingBox, coarsesteps, minlat, maxlat-minlat, minlong, maxlong-minlong);
	}
	
	
	
	/** Create a layer that specifies membership into regions
	 *  (CD's, TZ's, postcodes, whatever).
	 *  
	 * 
	 * 
	 * @param kmlfile
	 * @param excludeRegionsWithCentroidOutsideSydneyBoundingBox
	 * @throws Exception
	 */
	private static void createMembershipLayer(
			String kmlfile, String namefield, boolean excludeRegionsWithCentroidOutsideBoundingBox, int coarsesteps,
			double bb_minlat, double bb_latsize, double bb_minlon, double bb_lonsize) 
	throws Exception
	{
		createMembershipLayer(kmlfile, namefield, excludeRegionsWithCentroidOutsideBoundingBox, coarsesteps, coarsesteps,
				bb_minlat, bb_latsize, bb_minlon, bb_lonsize);
	}
	
	private static void createMembershipLayer(
			String kmlfile, String namefield, boolean excludeRegionsWithCentroidOutsideBoundingBox, int coarselatsteps, int coarselongsteps,
			double bb_minlat, double bb_latsize, double bb_minlon, double bb_lonsize) 
	throws Exception
	{
		int latsteps = coarselatsteps;
		int longsteps = coarselongsteps;
		GoogleEarthPolygon[] polys = GoogleEarthUtils.getPolygonsFromKMLFile(kmlfile, namefield);
		
		
		double minlat = bb_minlat;
		double maxlat = bb_minlat+bb_latsize;
		double minlong = bb_minlon;
		double maxlong = bb_minlon+bb_lonsize;


		GoogleEarthPolygon bb = new GoogleEarthPolygon(new double[][] 
        {
				{ minlat, minlong } , {minlat, maxlong} , {maxlat, maxlong} , {maxlat, minlong} , {minlat, minlong}
        }, 
				"bounding box");
		//throw away any polygons that arent in the bounding box
		ArrayList<GoogleEarthPolygon> arr = new ArrayList<GoogleEarthPolygon>();
		for(int i =0; i < polys.length; i++) 
			if(bb.contains(polys[i])) arr.add(polys[i]);
			else Debug.println("Throwing away polygon outside bounding box", Debug.IMPORTANT);
		
		//polys stores the raw kml polygons. There can be more than one for each region
		polys = new GoogleEarthPolygon[arr.size()];
		for(int i =0; i < polys.length; i++)
			polys[i] = arr.get(i);
		

		//minlat = -34.10141604662701;
		//maxlat = -33.54141728667299;
		//minlong = 150.65141715537853;
		//maxlong = 151.3614161776215;
		
		//small area for testing
		//minlat = -33.93;
		//maxlat = -33.86;
		//minlong = 151.00;
		//maxlong = 151.1;

		ArrayList newcentroids = new ArrayList();
		for(int i =0; i < polys.length; i++)
		{
			double[] centroid = polys[i].getCentroid();
			Debug.println("centroid is "+centroid[0]+" "+centroid[1], Debug.IMPORTANT);
			//Debug.println("CD "+polys[i].name+" has area "+polys[i].getArea(), Debug.IMPORTANT);
			if(polys[i].name.charAt(0) == '"')
				polys[i].name = polys[i].name.substring(1, polys[i].name.length()-1);

			Debug.println("poly name is "+polys[i].name, Debug.IMPORTANT);
			
			if(excludeRegionsWithCentroidOutsideBoundingBox)
			{
				Debug.println("EXCLUSING POLY OUTSIDE BOUNDING BOX", Debug.IMPORTANT);
				if(centroid[0] > minlat && centroid[0] < maxlat &&
				   centroid[1] > minlong && centroid[1] < maxlong)
					newcentroids.add(polys[i]);
			}
			else
				newcentroids.add(polys[i]);
		}
		GoogleEarthPolygon[] newpolys = new GoogleEarthPolygon[newcentroids.size()];
		for(int i =0; i < newpolys.length; i++)
			newpolys[i] = (GoogleEarthPolygon) newcentroids.get(i);

		polys = newpolys;
		Debug.println("There are "+newpolys.length+" valid districts", Debug.IMPORTANT);
				

		//remember which CDs have member points
		boolean[] hasmember = new boolean[polys.length];
		
		//now step through and work out which poly each lat/on is in. 
		//we just work out the index of the poly that the lat/lon is in, not the name
		int[][] data = new int[coarselatsteps][coarselongsteps];
		double latstep = (maxlat-minlat)/coarselatsteps;
		double longstep = (maxlong-minlong)/coarselongsteps;
		int lati = 0;
		for(double lat = minlat+latstep/2; lat < maxlat; lat+=latstep, lati++)
		{
			int failedtofind = 0;
			Debug.println("Starting lat row "+(lati+1)+" of "+latsteps, Debug.IMPORTANT);
			int loni = 0;
			for(double lon = minlong+longstep/2; lon < maxlong; lon+=longstep, loni++)
			{
				int whichpoly = -1;
				for(int i = 0; i < polys.length; i++)
				{
					if(polys[i].isInPoly(lat, lon)) 
					{
						if(whichpoly != -1) //it's already in a polygon!
						{
							//one polygon should be contained within another. Find the smallest one that 
							//contains the point.
							if(polys[whichpoly].contains(polys[i]))
								whichpoly = i; //polys[i] is the smaller one, so use that
							else if(polys[i].contains(polys[whichpoly]))
								/* The poly we already know about is smaller, so keep that */;
							else {
								Debug.println("Point "+lat+" "+lon+" is inside poly "+polys[whichpoly].name+" AND "+polys[i].name, Debug.CRITICAL);
								Debug.println("I will just pick the smaller of these polygons ... hit ENTER to confim:", Debug.CRITICAL);
								Util.raw_input();
								if(polys[i].getArea(1000, true) < polys[whichpoly].getArea(1000, true)) 
									whichpoly = i;
									
								//throw new RuntimeException("Point is within more than one poly and those polies intersect.... dont know what to do with this");
							}
						}
						else
							whichpoly = i;
					}
				}	
				if(whichpoly == -1) {
					//Debug.println("Found no polygon for "+lat+" , "+lon, Debug.INFO);
					failedtofind++;
					data[lati][loni] = -1; //Float.NaN;
				}
				else {
					if(!hasmember[whichpoly])
						hasmember[whichpoly] = true;
					//int cd = Integer.parseInt(polys[whichpoly].name);
					data[lati][loni] = whichpoly; //(float) cd;
				}
			}
			Debug.println("Done "+(lati+1)+" of "+latsteps+" (failed to find "+failedtofind+" in this row)", Debug.IMPORTANT);
		}
		
		//ok, so at this point data[i][j] has an index which X which says that polys[X] is the polygon containing the point
		
		
		//write out intermediate (coarse) results
		String[] nametmp = new String[polys.length]; for(int i =0; i < polys.length; i++) nametmp[i] = polys[i].name;
		new GISLayer("COARSE_CD", minlat, maxlat, minlong, maxlong, data, nametmp).saveToFile("tmpcd.gis");
		Debug.println("Produced coarse layer, now refining", Debug.IMPORTANT);
		
		//now we increase resolution and redo it in a smart way
		
		
		//first, we make a list of all the 'orphan' CDs that do not have
		//any points in them.
		ArrayList orphans = new ArrayList();
		for(int i =0; i < hasmember.length; i++)
			if(!hasmember[i]) {
				double[] centroid = polys[i].getCentroid();
				Debug.println("orphan centroid is "+centroid[0]+" "+centroid[1], Debug.IMPORTANT);
				if(centroid[0] > minlat && centroid[0] < maxlat
				   && centroid[1] > minlong && centroid[1] < maxlong)
				orphans.add(new Integer(i));
			}
		Debug.println("There are "+orphans.size()+" orphan zones", Debug.IMPORTANT);
		
		//increase resolution
		int resmult = 10;
		latsteps *= resmult;
		longsteps *= resmult;
		int[][] finedata = new int[latsteps][longsteps];
		latstep = (maxlat-minlat)/latsteps;
		longstep = (maxlong-minlong)/longsteps;
		lati = 0;
		
		//map polygon name to the the id we will use
		HashMap<String, Integer> polynamemap = new HashMap<String, Integer>();
		ArrayList<String> polynamesbyfineindex = new ArrayList<String>();
		int numuniq = 0;
		for(int i = 0; i < polys.length; i++) 
		{
			if(!polynamemap.containsKey(polys[i].name)) 
			{
				polynamemap.put(polys[i].name, new Integer(numuniq++));
				polynamesbyfineindex.add(polys[i].name); 
			}
		}
		
		//the category number for each of the polygons
		int[] polycatnum = new int[polys.length];
		for(int i=0; i < polys.length; i++)
			polycatnum[i] = polynamemap.get(polys[i].name).intValue();
		

		//keep track of which polygons actually get used
		HashMap<Integer, Object> values = new HashMap<Integer, Object>();
		int lastval = -1;
		
		for(double lat = minlat+latstep/2; lat < maxlat; lat+=latstep, lati++)
		{
			Debug.println("Starting lat row "+(lati+1)+" of "+latsteps, Debug.IMPORTANT);
			int loni = 0;
			int failedtofind = 0;
			
			for(double lon = minlong+longstep/2; lon < maxlong; lon+=longstep, loni++)
			{
			
				boolean foundpoly = false;
				int coarselati = lati/resmult;
				int coarseloni = loni/resmult;
				tryfindloop: for(int i = 0; i < 3; i++)
				{
					for(int j = 0; j < 3; j++)
					{
						if(coarselati+i >= 0 && coarselati+i < data.length &&
						   coarseloni+j >= 0 && coarseloni+j < data[0].length)
						{
							//get the coarse CD membership
							int trypoly = data[coarselati+i][coarseloni+j];
							if(trypoly != -1)
							{
								if(polys[trypoly].isInPoly(lat, lon)) {
									finedata[lati][loni] = polycatnum[trypoly];
									foundpoly = true;
									break tryfindloop;
								}
							}
						}
						if(coarselati+i >= 0 && coarselati+i < data.length &&
								   coarseloni-j >= 0 && coarseloni-j < data[0].length)
						{
							int trypoly = data[coarselati+i][coarseloni-j];
							if(trypoly != -1)
							{
								if(polys[trypoly].isInPoly(lat, lon)) {
									finedata[lati][loni] = polycatnum[trypoly];
									foundpoly = true;
									break tryfindloop;
								}
							}
						}
						if(coarselati-i >= 0 && coarselati-i < data.length &&
								   coarseloni+j >= 0 && coarseloni+j < data[0].length)
						{
							int trypoly = data[coarselati-i][coarseloni+j];
							if(trypoly != -1)
							{
							
								if(polys[trypoly].isInPoly(lat, lon)) {
									finedata[lati][loni] = polycatnum[trypoly];
									foundpoly = true;
									break tryfindloop;
								}
							}
						}
						if(coarselati-i >= 0 && coarselati-i < data.length &&
								   coarseloni-j >= 0 && coarseloni-j < data[0].length)
						{
							int trypoly = data[coarselati-i][coarseloni-j];
							if(trypoly != -1)
							{	
								if(polys[trypoly].isInPoly(lat, lon)) {
									finedata[lati][loni] = polycatnum[trypoly];
									foundpoly = true;
									break tryfindloop;
								}
							}
						}
					}
				}
			
				//if we havent found which CD we are in, we check the missing (orphan) polygons
				if(!foundpoly)
				{
					int whichpoly = -1;
					
					//check through all the orphans
					for(int i =0; i < orphans.size(); i++)
					{
						int index = ((Integer) orphans.get(i)).intValue();
						//double[] centroid = polys[index].getCentroid();
						//if(MathUtil.getDistanceBetweenPointsOnEarth(lat, lon, centroid[0], centroid[1]) > 8000)
						//	continue; //dont bother checking if the centroid is too far away

						//its in an orphan. 
						if(polys[index].isInPoly(lat, lon)) 
						{
							if(whichpoly != -1) //it's already in a polygon!
							{
								//one polygon should be contained within another. Find the smallest one that 
								//contains the point.
								if(polys[whichpoly].contains(polys[index]))
									whichpoly = index; //polys[index] is the smaller one, so use that
								else if(polys[index].contains(polys[whichpoly]))
									/* The poly we already know about is smaller, so keep that */;
								else {
									Debug.println("Point "+lat+" "+lon+" is inside poly "+polys[whichpoly].name+" AND "+polys[i].name, Debug.CRITICAL);
									Debug.println("I will just pick one of these polygons arbitrarily... hit ENTER to confim:", Debug.CRITICAL);
									Util.raw_input();
									//throw new RuntimeException("Point is within more than one poly and those polies intersect.... dont know what to do with this");
								}
							}
							else
								whichpoly = index;
							
							//if(whichpoly != -1)
							//	Debug.println(lat+" "+lon+" is in polygon "+polys[index].name+" as well as "+polys[whichpoly].name, Debug.CRITICAL);
							foundpoly = true;
						}
					}
					
					if(foundpoly) {
						finedata[lati][loni] = polycatnum[whichpoly]; //Integer.parseInt(polys[index].name);
						Debug.println(lat+","+lon+" is in zone "+polys[whichpoly].name, Debug.INFO);
					}
				}
				
				
				if(!foundpoly) 
				{
					failedtofind++;
					finedata[lati][loni] = -1;
					//Debug.println("Could not find a home for "+lat+" , "+lon, Debug.IMPORTANT);
				}
				
				//keep track of which values actually get assigned
				if(finedata[lati][loni] != -1 && finedata[lati][loni] != lastval && !values.containsKey(new Integer(finedata[lati][loni])))
					values.put(new Integer(finedata[lati][loni]), null);
					
				//remember last value we saw (saves us some lookups)	
				if(finedata[lati][loni] != -1)
					lastval = finedata[lati][loni];
			}
			Debug.println("Done "+(lati+1)+" of "+latsteps, Debug.IMPORTANT);
			Debug.println("Failed to find "+failedtofind+" points (of "+longsteps+") in row at lat "+lat, Debug.IMPORTANT);
		}
		
		//now remap everything so that we only have categories that actually appear in the layer
		//map the original category number to the new one
		int[] origtonew = new int[polys.length];
		String[] catnames = new String[values.keySet().size()];
		Iterator<Integer> origit = values.keySet().iterator();
		int count = 0;
		while(origit.hasNext()) {
			int orig = origit.next();
			int newi = count++;
			origtonew[orig] = newi;
			catnames[newi] = polynamesbyfineindex.get(orig);
		}
		
		lati = 0;
		for(double lat = minlat+latstep/2; lat < maxlat; lat+=latstep, lati++)
		{
			Debug.println("REMAPPING: Starting lat row "+(lati+1)+" of "+latsteps, Debug.IMPORTANT);
			int loni = 0;
			
			for(double lon = minlong+longstep/2; lon < maxlong; lon+=longstep, loni++) 
				if(finedata[lati][loni] != -1)
					finedata[lati][loni] = origtonew[finedata[lati][loni]];
			
		}
		
				
		//double-check to make sure every category has a name
		for(int i = 0; i < catnames.length; i++)
			if(catnames[i] == null) throw new RuntimeException("No name for category !! Should be impossible .."+i);
		
		
		GISLayer cdlayer = new GISLayer("REGION_MEMBERSHIP", minlat, maxlat, minlong, maxlong, finedata, catnames);
		cdlayer.saveToFile("membership.gis");
	}
	

	//given a data file CDNUM val,
	//create a layer
	public static void getCDbasedData(String datfile) throws java.io.IOException
	{
		GISLayer.createRegionLayer(datfile, "cdmembership.gis", false);
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public static void main(String[] args) throws Exception
	{
		Debug.setVerbosityLevel(Debug.INFO);
		Debug.setDebugLevel(Debug.DEBUG_SUSPICIOUS);
		Debug.println("creating layer", Debug.INFO);
		
		String namefield = "name";
		if(args.length >= 7)
			namefield = args[6];
		
		Debug.println("Namefield is: "+namefield, Debug.INFO);

		double minlat, latsize, minlon, lonsize;
		
		if(!args[1].equalsIgnoreCase("bydata"))
		{
			minlat = Double.parseDouble(args[1]);
			latsize = Double.parseDouble(args[2]);
			minlon = Double.parseDouble(args[3]);
			lonsize = Double.parseDouble(args[4]);
		}
		else
		{
			minlat = Double.POSITIVE_INFINITY;
			double maxlat = Double.NEGATIVE_INFINITY;
			minlon = Double.POSITIVE_INFINITY;
			double maxlon = Double.NEGATIVE_INFINITY;
			GoogleEarthPolygon[] polys = GoogleEarthUtils.getPolygonsFromKMLFile(args[0], namefield);
			
			for(int i = 0; i < polys.length; i++)
			{
				for(int j = 0; j < polys[i].latlongpoints.length; j++)
				{
					double lat = polys[i].latlongpoints[j][0];
					double lon = polys[i].latlongpoints[j][1];	
					if(lat < minlat) minlat = lat;
					if(lat > maxlat) maxlat = lat;
					if(lon < minlon) minlon = lon;
					if(lon > maxlon) maxlon = lon;
				}
			}
			
			latsize = (maxlat-minlat)*1.02;
			lonsize = (maxlon-minlon)*1.02;
			minlat = minlat-0.01*latsize;
			minlon = minlon-0.01*lonsize;
		}
		
		int coarselongsteps = Integer.parseInt(args[5]);

		//work out the aspect ratio
		double lonsz = rses.math.MathUtil.getDistanceBetweenPointsOnEarth(minlat+latsize/2, minlon, minlat+latsize/2, minlon+lonsize);
		double latsz = rses.math.MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlon+lonsize/2, minlat+latsize, minlon+lonsize/2);
		
		double latlonratio = latsz/lonsz;
		
		int coarselatsteps = (int) Math.round(latlonratio*coarselongsteps);

		
		Debug.println("Lat: "+minlat+" - "+(minlat+latsize), Debug.IMPORTANT);
		Debug.println("Lon: "+minlon+" - "+(minlon+lonsize), Debug.IMPORTANT);
		
		//int coarselongsteps = Integer.parseInt(args[6]);
		//createMembershipLayer(args[0], false, 50, -34.5, 1.5, 150, 1.7);
		//createMembershipLayer(args[0], false, coarselatsteps, coarselongsteps, minlat, latsize, minlon, lonsize);
		
		Debug.println("Create layer that has aspect ratio "+coarselongsteps+" x "+coarselatsteps, Debug.INFO);
		createMembershipLayer(args[0], namefield, false, coarselatsteps, coarselongsteps, minlat, latsize, minlon, lonsize);

	}
	
	
}

