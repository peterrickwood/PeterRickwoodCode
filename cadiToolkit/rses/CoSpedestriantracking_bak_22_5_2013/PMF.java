package rses.CoSpedestriantracking;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.org.apache.bcel.internal.generic.GETSTATIC;

import rses.spatial.GISLayer;
import rses.util.Util;
import rses.Debug;



/**
 *  Probability mass function over space
 *  
 *  
 * @author peterr
 *
 */
public class PMF 
{
	private rses.spatial.GISLayer geography;
	
	//data is indexed by lat/lon indices
	private double[][] data;
	private int latpos = -1;
	private int lonpos = -1;
	
	public PMF(PMF tobecopied)
	{
		this.data = rses.util.Util.copy(tobecopied.data);
		this.geography = tobecopied.geography;
	}
	
	public PMF(rses.spatial.GISLayer regionOfInterest)
	{
		this(regionOfInterest, false);
	}

	public PMF(rses.spatial.GISLayer regionOfInterest, int latpos, int lonpos)
	{
		this.geography = regionOfInterest;
		this.latpos = latpos;
		this.lonpos = lonpos;
	}
	
	public PMF(rses.spatial.GISLayer regionOfInterest, boolean initializeToUniform)
	{
		this.geography = regionOfInterest;
		data = new double[regionOfInterest.getLatSteps()][regionOfInterest.getLongSteps()];
		if(initializeToUniform) 
		{
			for(int i = 0; i < data.length; i++)
				for(int j = 0; j < data[0].length; j++)
					data[i][j] = 1.0/(data.length*data[0].length);
		}
	}
	
	public GISLayer getGeography()
	{
		return this.geography;
	}
	
	/*
	 * get square size in metres
	 */
	public double getSquareSize()
	{
		double lat1 = 0.5*(this.geography.getMinLat()+this.geography.getMaxLat());
		double lonmetres = rses.math.MathUtil.getDistanceBetweenPointsOnEarth(lat1, this.geography.getMinLong(), 
				lat1, this.geography.getMaxLong());
		double lonm = lonmetres/this.geography.getLongSteps();

		double lon1 = 0.5*(this.geography.getMinLat()+this.geography.getMaxLat());
		double latmetres = rses.math.MathUtil.getDistanceBetweenPointsOnEarth(this.geography.getMinLat(), 
				lon1, this.geography.getMaxLat(), lon1);
		double latm = latmetres/this.geography.getLatSteps();
		
		if(Math.abs(latm-lonm) > 0.1) //if there is more than 10cm difference, chuck a wobbly
			throw new RuntimeException("square size is not square......");
		
		return (latm+lonm)/2.0;
	}
	
	public int[] getDimensions()
	{
		return new int[] {this.geography.getLatSteps(), this.geography.getLongSteps()};
	}
	
	public void setValue(double lat, double lon, double val)
	{
		int[] latloni = geography.getLatLongIndices(lat, lon);
		if(data != null)
			data[latloni[0]][latloni[1]] = val;
		else throw new RuntimeException("You are trying to set the value of an ML (read-only) PMF");
	}
	
	
	double[][] logprobcache;
	public void cacheLogProbs()
	{
		this.logprobcache = new double[data.length][data[0].length];
		for(int i = 0; i < data.length; i++)
			for(int j = 0; j < data[0].length; j++) 
				logprobcache[i][j] = Math.log(data[i][j]);	
	}
	
	
	public double getLogProb(double lat, double lon)
	{
		int[] latloni = geography.getLatLongIndices(lat, lon);
		if(logprobcache != null)
			return logprobcache[latloni[0]][latloni[1]];
		else
			return Math.log(getProb(lat, lon));
	}
	
	public double getLogProbByIndices(int latpos, int lonpos)
	{
		if(logprobcache != null)
			return logprobcache[latpos][lonpos];
		else
			return Math.log(getProbByIndices(latpos, lonpos));
	}
	
	
	public double getProb(double lat, double lon)
	{
		int[] latloni = geography.getLatLongIndices(lat, lon);
		if(data != null)
			return data[latloni[0]][latloni[1]];
		else
			return (latloni[0] == latpos && latloni[1] == lonpos) ? 1 : 0;
	}
	
	public double getProbByIndices(int latpos, int lonpos)
	{
		if(data != null)
			return data[latpos][lonpos];
		else
			return (latpos == this.latpos && lonpos == this.lonpos) ? 1 : 0;
	}

	
	
	public void multiplyBy(double fact)
	{
		if(this.data == null)
			throw new RuntimeException("trying to do operation on ML pmf. These are read-only and you probably shouldnt ever be doing this");

		for(int x = 0; x < data.length; x++)
		{
			for(int y = 0; y < data[x].length; y++)
				data[x][y] *= fact;
		}

	}
	
	public void multiply(PMF pmf2, boolean normalizeafter)
	{
		if(this.data == null)
			throw new RuntimeException("trying to do operation on ML pmf. These are read-only and you probably shouldnt ever be doing this");
		this.cache = new HashMap<List<Integer>, PMF>(); //invalidate cache
		
		if(pmf2.data.length != data.length || pmf2.data[0].length != data[0].length) throw new RuntimeException("Trying to multiply mismatched pmf's");
		for(int x = 0; x < data.length; x++)
		{
			for(int y = 0; y < data[x].length; y++)
				data[x][y] *= pmf2.data[x][y];
		}
		
		if(normalizeafter)
			normalize();
		
	}


	
	public boolean isZeroEverywhere()
	{
		if(data == null)
			return false; //ML pmf's are by definition 1 in one (and only one) grid cell
		
		for(int a =0; a < data.length; a++)
			for(int b=0; b < data[0].length; b++)
				if(data[a][b] > 0) return false;
		return true;
	}
	
	public boolean isUniform()
	{
		if(data == null)
			return false; //ML pmf's are by definition 1 in one (and only one) grid cell

		double val = data[0][0];
		for(int a =0; a < data.length; a++)
			for(int b=0; b < data[0].length; b++)
				if(data[a][b] != val) return false;
		return true;		
	}
	
	
	
	
	

	
	
	
	
	
	
	
	/** Project the pmf forward for up to maxtimesteps (or until it becomes uniform)
	 * 
	 * @param maxtimesteps The number of steps to take *at the slowest speed* before assuming its relaxed to uniform
	 * 
	 * 
	 * @param spatialPrior (the spatial prior for the PMF) We need to know this because,
	 *                     even though the spatial prior should already be embedded in this
	 *                     PMF (i.e. this PMF should have been multiplied by the prior),
	 *                     we need to make sure that where the prior is zero, we always
	 *                     keep it at zero.
	 * @return
	 */
	public PMF[] project(int maxtimesteps, PMF spatialPrior, double[][][][] pathdists, double[] speeds) 
	throws java.io.IOException
	{
		PMF[] result = new PMF[maxtimesteps];
		result[0] = this;
		
		//now, at each speed, for *each* potential starting point, we spread out
		for(int s = 0; s < speeds.length; s++)
		{			
			double speed = speeds[s];
			
			for(int startlat = 0; startlat < geography.getLatSteps(); startlat++)
				for(int startlon = 0; startlon < geography.getLongSteps(); startlon++)
					if(spatialPrior.getProbByIndices(startlat, startlon) > 0 && getProbByIndices(startlat, startlon) > 0) 
					{
						for(int i = 1; i < maxtimesteps; i++)
						{
							PMF next = null;
							int r = (int) (i*speed/getSquareSize());
							if(r >= maxtimesteps)
								break; //assume we've got to uniform

								
							if(r == 0) {
								next = new PMF(this.geography);
								next.data[startlat][startlon] = data[startlat][startlon];
							}
							else 
								next = this.getRadiusProb(startlat, startlon,r, spatialPrior, pathdists);
							
							//else next = getRadiusProb_old(startlat, startlon, i, spatialPrior);

							if(result[i] == null) result[i] = next;
							else result[i].add(next);
						}
					}
					else if(this.getProbByIndices(startlat, startlon) > 0)
						throw new RuntimeException("PMF that we are projecting has non-zero element somewhere where the prior is zero! This should be impossible");
		}	

		
		
		for(int i=1; i < maxtimesteps; i++)
		{
			if(result[i] == null) //whack in uniform distribution 
			{
				throw new RuntimeException("Null PMF at projection step "+i+" in projection?!"); //should be impossible to have NO data at timestep i, no?
				
				//rses.Debug.println("Reverting to uniform distribution at offset "+i, rses.Debug.INFO);
				//result[i] = new PMF(this.geography, true);
			}
			else if(result[i].isZeroEverywhere())
				throw new RuntimeException("Zero-everywhere PMF at i="+i);
			else
				result[i].normalize();
		}
		
		return result;
	}

	
	
	//does the path from startx,starty go along a valid path
	//
	//NOTE -- we do NOT check the start point of the path, just a few steps along the way
	private boolean isValidPath(double startx, double starty, double endx, double endy)
	{
		final int NSTEPS = 3;
		double xdelta = (endx-startx)/NSTEPS;
		double ydelta = (endy-starty)/NSTEPS;
		for(int i = 1; i <= NSTEPS; i++)
		{
			int x = (int) (startx+i*xdelta);
			int y = (int) (starty+i*ydelta);
			
			if(x < 0 || x >= data.length || y < 0 || y >= data[0].length)
				continue; //if the position is out of bounds we assume its OK.
			else if(getProbByIndices(x,y) == 0)
				return false;
		}
		return true;
	}
	
	
	
	public PMF getRadiusProb(int startlati, int startloni, int r, 
			PMF spatialPrior, double[][][][] pathdists)
	{
		//System.out.println("At start "+startlati+","+startloni+" radius "+r);
		if(spatialPrior.getProbByIndices(startlati, startloni) == 0.0)
			throw new RuntimeException("geting radius prob from start square that has zero prior?!");

		//if we are projecting from a zero probability start square, we return a zero PMF
		if(data[startlati][startloni] == 0.0)
			throw new RuntimeException("Projecting from zero probability data square at "+startlati+" startloni"); 
					
		double rsq = r*getSquareSize()*r*getSquareSize();
		PMF next = new PMF(this.geography); //get a zeroed out PMF to start with
		for(int i = Math.max(0, startlati-r); i <= startlati+r && i < data.length ; i++)
		{				
			for(int j = Math.max(0, startloni-r); j <= startloni+r && j < data[0].length; j++)
			{
				double priorprob = spatialPrior.getProbByIndices(i, j);
				if(priorprob <= 0) { //need this check because pathdists may be null for unreachable squares
					next.data[i][j] = 0.0;
					continue;
				}

				double dist = pathdists[startlati][startloni][i][j];
								
				//System.out.println("    i,j "+i+","+j+" dist is "+dist);
				if(dist*dist <= rsq) 
				{					
					if(priorprob > 0) 
						next.data[i][j] = 1.0; 
				}
			}
		}
		
		//IMPORTANT TO NORMALIZE BEFORE WE MULTIPLY BY START PROBS, BECAUSE OTHERWISE WE OBLITERATE 
		//THE ACTUAL START PROBABILITIES 
		next.normalize();
		next.multiplyBy(data[startlati][startloni]);
		
		return next;
	}
	

	
	

	/** For a walker starting at the original startx,starty, spread out r timesteps from this distribution
	 * so, this.data tells us where we are *now*, and startlati, startloni tell us where we
	 * started, so we want to know where we are after r timesteps.
	 * @param startx
	 * @param starty
	 * @return
	 */
	Map<List<Integer>, PMF> cache = new HashMap<List<Integer>, PMF>();
	public PMF getRadiusProbCache(int startlati, int startloni, int r, 
			PMF spatialPrior, double[][][][] pathdists)
	{
		//System.out.println("At start "+startlati+","+startloni+" radius "+r);
		if(spatialPrior.getProbByIndices(startlati, startloni) == 0.0)
			throw new RuntimeException("geting radius prob from start square that has zero prior?!");

		//if we are projecting from a zero probability start square, we return a zero PMF
		if(data[startlati][startloni] == 0.0)
			throw new RuntimeException("Projecting from zero probability data square at "+startlati+" startloni"); 
		
		/*ArrayList<Integer> key = null;
		if(r > 20) //doing it for small r is pointless, so we only cache for big r 
		{
			key = new ArrayList<Integer>();
			key.add(startlati); key.add(startloni); key.add(r);
			
			if(cache.containsKey(key)) {
				//Debug.println("Using cached version of radius prob from "+startlati+" "+startloni+" "+r, Debug.INFO);
				return cache.get(key);
			}
		}*/
			
		double rsq = r*getSquareSize()*r*getSquareSize();
		double sqsz = getSquareSize();
		PMF next = new PMF(this.geography); //get a zeroed out PMF to start with
		for(int i = startlati-r; i <= startlati+r ; i++)
		{				
			for(int j = startloni-r; j <= startloni+r; j++)
			{
				int safei = i;
				int safej = j;
				
				//if we are out of bounds of our bounding box, then we just pretend that we are
				//at the border and add probability there
				boolean outofbounds = false;
				if(i < 0 || i >= data.length || j < 0 || j >= data[0].length)
				{
					outofbounds = true;
					safei = Math.min(Math.max(0, i), data.length-1);
					safej = Math.min(Math.max(0, j), data[0].length-1);
					if(spatialPrior.getProbByIndices(safei, safej) <= 0.0)
						throw new RuntimeException("Edge of spatial prior CANNOT be zero. We need it to be non-zero around the entire edge for propogration to work properly");
				}
					
				double priorprob = spatialPrior.getProbByIndices(safei, safej);
				if(priorprob <= 0) { //need this check because pathdists may be null for unreachable squares
					next.data[safei][safej] = 0.0;
					continue;
				}

				double dist = pathdists[startlati][startloni][safei][safej];
				
				//if we are out of bounds we need to add the extra distance from the edge of the grid to the
				//actual out-of-grid square. We assume straight line distance from the closest square in the grid
				//which may not actually be correct (because that may not be the shortest distance edge square), 
				//but we dont worry about that -- near enough is good enough here
				if(outofbounds)
					dist += Math.sqrt((sqsz*(i-safei))*(sqsz*(i-safei))+(sqsz*(j-safej))*(sqsz*(j-safej)));
				
				//System.out.println("    i,j "+i+","+j+" dist is "+dist);
				if(dist*dist <= rsq) 
				{					
					if(priorprob > 0) 
						next.data[safei][safej] += 1.0; 
				}
			}
		}
		
		//IMPORTANT TO NORMALIZE BEFORE WE MULTIPLY BY START PROBS, BECAUSE OTHERWISE WE OBLITERATE 
		//THE ACTUAL START PROBABILITIES 
		next.normalize();
		next.multiplyBy(data[startlati][startloni]);
		/*if(key != null) {
			//Debug.println("Adding projection from "+startlati+" "+startloni+" radius "+r, Debug.INFO);
			cache.put(key, next);
		}*/
		
		return next;
	}
	

	
	
	
	
	
	
	
	
	public void add(PMF pmf2)
	{
		if(this.data == null)
			throw new RuntimeException("trying to do operation on ML pmf. These are read-only and you probably shouldnt ever be doing this");
		this.cache = new HashMap<List<Integer>, PMF>(); //invalidate cache

		
		if(pmf2.data.length != data.length || pmf2.data[0].length != data[0].length) throw new RuntimeException("Trying to add mismatched pmf's");
		for(int x = 0; x < data.length; x++)
		{
			for(int y = 0; y < data[x].length; y++)
				data[x][y] += pmf2.data[x][y];
		}
	}
	
	public void normalize()
	{
		if(this.data == null)
			return; //ML pmfs are always normalized
		this.cache = new HashMap<List<Integer>, PMF>(); //invalidate cache
		
		
		double tot = 0.0;
		for(int x = 0; x < data.length; x++)
			for(int y = 0; y < data[x].length; y++)
				tot += data[x][y];
		for(int x = 0; x < data.length; x++)
			for(int y = 0; y < data[x].length; y++)
				data[x][y]/=tot;		
	}

	public void print()
	{
		print(false, System.out);
	}

	public void printToFile(String fname) throws java.io.IOException
	{
		printToFile(null, fname);
	}

	public void printToFile(java.io.File dir, String fname) throws java.io.IOException
	{
		java.io.PrintStream ps = null;
		
		if(dir == null) ps = new java.io.PrintStream(new java.io.File(fname));
		else ps = new java.io.PrintStream(new java.io.File(dir, fname));
		
		this.print(false, ps);
		ps.close();
	}
	
	
	/*
	 * sample an x,y from this pmf.
	 * 
	 * Returns the (x,y) tuple
	 * 
	 * This only works if the PMF is normalized.
	 * It is *your* responsibility to make sure that the PMF is normalizd before you sample from it!
	 */
	public int[] sampleFrom()
	{
		if(this.data == null) //ML pmfs have only 1 non-zero location
			return new int[] {latpos, lonpos};
		
		double r = Math.random();
		int x = 0;
		int y = 0;
		double psum = data[x][y];
		while(psum < r)
		{
			y++;
			if(y == data[x].length) {
				y=0; x++;
			}
			psum += data[x][y];
		}
		return new int[] {x,y};
	}
	
	
	/** Get best lat,long indices
	 * 
	 * @return
	 */
	public int[] getML()
	{
		if(data == null)
			return sampleFrom();
		
		int maxlati = -1;
		int maxloni = -1;
		double maxval = 0.0;
		for(int lati = 0; lati < data.length; lati++)
		{
			for(int loni = 0; loni < data[lati].length; loni++)
				if(data[lati][loni] > maxval) 
				{
					maxlati = lati;
					maxloni = loni;
					maxval = data[lati][loni];
				}
		}
		return new int[] {maxlati, maxloni};
	}

	public double[] getMLlatlon()
	{
		if(data == null)
		{
			return new double[] {
				geography.getMinLat()+(latpos+0.5)*geography.getLatStepSize(), 
				geography.getMinLong()+(lonpos+0.5)*geography.getLongStepSize()
			};
		}
		int maxlati = -1;
		int maxloni = -1;
		double maxval = 0.0;
		for(int lati = 0; lati < data.length; lati++)
		{
			for(int loni = 0; loni < data[lati].length; loni++)
				if(data[lati][loni] > maxval) 
				{
					maxlati = lati;
					maxloni = loni;
					maxval = data[lati][loni];
				}
		}
		return new double[] {
				geography.getMinLat()+(maxlati+0.5)*geography.getLatStepSize(), 
				geography.getMinLong()+(maxloni+0.5)*geography.getLongStepSize()
		};
	}

	public static PMF read(String filename, GISLayer geog) throws java.io.IOException
	{
		return read(filename, geog, true);
	}
	
	//read a PMF in from a file
	//
	//Each line is for a fixed lat, varying long. Starts at maxlat
	//So, basically, the physical layout in the file matches the actual locations -- the top of the file is the
	//max lat, the left is minlon, etc etc
	//
	public static PMF read(String filename, GISLayer geog, boolean normalizeAfter) throws java.io.IOException
	{
		PMF p = new PMF(geog);
		BufferedReader rdr = new BufferedReader(new FileReader(filename));
		java.util.ArrayList<double[]> vals = new java.util.ArrayList<double[]>();
		String line = rdr.readLine();
		while(line != null)
		{
			double[] row = (double[]) Util.parseArray(line, double.class);
			vals.add(row);
			line = rdr.readLine();
		}
		
		p.data = new double[vals.size()][];
		for(int i = 0; i < p.data.length; i++)
			p.data[p.data.length-i-1] = vals.get(i);
		
		if(normalizeAfter)
			p.normalize();
		
		return p;
	}
	
	public void print(boolean printGeogLayerToo, java.io.PrintStream ps)
	{
		if(data == null)
			throw new UnsupportedOperationException("Not implemented yet");			
		
		int maxlati = -1;
		int maxloni = -1;
		double maxval = 0.0;
		for(int lati = 0; lati < data.length; lati++)
		{
			for(int loni = 0; loni < data[data.length-lati-1].length; loni++)
				if(data[data.length-lati-1][loni] > maxval) 
				{
					maxlati = data.length-lati-1;
					maxloni = loni;
					maxval = data[data.length-lati-1][loni];
				}
			rses.util.Util.printarray(data[data.length-lati-1], ps);	
			if(printGeogLayerToo) { ps.print("GEOGLAYER: "); rses.util.Util.printarray(this.geography.continuousdata[data.length-lati-1], ps);}	
		}
		//ps.println("Maximum likelihood index is "+maxloni+" "+maxlati);
	}
	
	
	public void writeImage(String tag) throws java.io.IOException
	{
		if(data == null)
			throw new UnsupportedOperationException("Not implemented yet");
		
		for(int lati=0; lati < geography.getLatSteps(); lati++)
		{
			for(int loni = 0; loni < geography.getLongSteps(); loni++)
				geography.setValueByIndices(lati, loni, (float) data[lati][loni]);
		}
		geography.updateMaxMinVals();
		
		geography.generateGoogleEarthOverlay(geography.getLongSteps()*10, geography.getLatSteps()*10, Color.blue, Color.red, "XXX", tag);
	}
	
	
	

	PathNode[][] getDistanceGraph()
	{
		return getGraph(true);
	}
	
	PathNode[][] getGraph()
	{
		return getGraph(false);
	}
	
	private PathNode[][] getGraph(boolean distanceBased)
	{
		int xdim = this.getDimensions()[0];
		int ydim = this.getDimensions()[1];

		//first we build a graph of the area so that we can calculate shortest paths
		PathNode[][] squares = new PathNode[xdim][ydim];		
		for(int x = 0; x < xdim; x++)
			for(int y = 0; y < ydim; y++) 
				if(this.getProbByIndices(x, y) > 0)
					squares[x][y] = new PathNode(-1, x, y, xdim, ydim);
		
		//add edges (weights are either distance or the -log of the spatial prior at the edge endpoint)
		for(int x = 0; x < xdim; x++)
			for(int y = 0; y < ydim; y++) 
				for(int xoff = -1; xoff <= 1; xoff++)
					for(int yoff = -1; yoff <= 1; yoff++)
						//need to check dest out of bounds and get rid of self edge and make sure that
						//the destination actual has a prior value > 0
						if(x+xoff >= 0 && x+xoff < xdim && y+yoff >= 0 && y+yoff < ydim)
						{
							if(xoff == 0 && yoff == 0) continue; //dont add self-edges
							if(squares[x][y] == null) continue; //can never reach here because prior is 0
							if(squares[x+xoff][y+yoff] == null) continue; //can never get to dest because is unreachable
							
							double dist = -this.getLogProbByIndices(x+xoff, y+yoff);
							double physicaldist = Math.sqrt(Math.pow(xoff*getSquareSize(), 2.0)+Math.pow(yoff+getSquareSize(), 2.0));
							
							if(distanceBased)
								squares[x][y].addEdge(new rses.math.GraphEdge(squares[x][y], squares[x+xoff][y+yoff], physicaldist, dist));
							else
								squares[x][y].addEdge(new rses.math.GraphEdge(squares[x][y], squares[x+xoff][y+yoff], dist, physicaldist));
						}
		
		return squares;
	}
	

	public static void main(String[] args) throws IOException
	{
		Debug.setVerbosityLevel(Debug.INFO);
		System.err.println("Usage: java PMF BoundsFile PMFdatafile OutputName");
		System.err.println("    If PMFdatafile is NONE, then a data file is created for you, initialized to uniform");
		
		if(args[0].equals("PROJTEST")) 
		{
			test(args[1]);
			System.exit(0);
		}
		
		//read in the gis layer
		GISLayer layer = Main.getGISLayer(args[0], "");
		
		//read in the PMF data
		PMF pmf = new PMF(layer, true);
		if(!args[1].equals("NONE"))
			pmf = read(args[1], layer, false);
		else
			pmf.printToFile("pmfdummydata.txt");
		
		//go and replace any infinite values with NaN's (otherwise our colour scale sucks)
		for(int x = 0; x < pmf.data.length; x++)
			for(int y = 0; y < pmf.data[0].length; y++)
				if(pmf.data[x][y] == Double.POSITIVE_INFINITY)
					pmf.data[x][y] = Double.NaN;
		
		//then write out an image of it
		pmf.writeImage(args[2]);
	}

	public static void test(String boundsfile) throws IOException
	{
		//create a PMF
		rses.spatial.GISLayer layer = Main.getGISLayer(boundsfile, "test");
		int X = layer.getLatSteps();
		int Y = layer.getLongSteps();
		
		PMF pmf = new PMF(layer);
		
		//now initialize it so that it has some probabilities
		for(int i = 0; i < pmf.data.length; i++)
			for(int j = 0; j < pmf.data[0].length; j++)
				pmf.data[i][j] = 0.0;
		for(int n = 0; n < 3; n++)
		{
			int starti = (int) (Math.random()*pmf.data.length);
			int startj = (int) (Math.random()*pmf.data[0].length);
			System.out.println("starti,startj are "+starti+","+startj);
			pmf.data[starti][startj] = 1.0;
		}
		pmf.normalize();
		pmf.writeImage("START");
				
		
		double[][][][] pathdists = new double[X][Y][X][Y];
		for(int origx = 0; origx < X; origx++)
			for(int origy = 0; origy < Y; origy++)
				for(int destx = 0; destx < X; destx++)
					for(int desty = 0; desty < Y; desty++) {
						double xdiff = pmf.getSquareSize()*(origx-destx);
						double ydiff = pmf.getSquareSize()*(origy-desty);
						pathdists[origx][origy][destx][desty] = Math.sqrt(xdiff*xdiff+ydiff*ydiff);
					}
		
		PMF[] pmfs = pmf.project(Math.max(X, Y), new PMF(layer, true), pathdists, new double[] {pmf.getSquareSize()+0.0001, 2*pmf.getSquareSize()+0.0001});
		for(int i = 0; i < pmfs.length; i++)
			pmfs[i].writeImage("test_time_"+CosProbModel.len5int(i));
		
	}
	
}
