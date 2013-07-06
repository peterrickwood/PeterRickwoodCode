package rses.math;

import java.util.Arrays;

import rses.Debug;
import rses.PlatformInfo;
import rses.util.Util;




public final class MathUtil
{
	private MathUtil() {} //never instantiate this class

	
	public static double[][] divideMatrices(double[][] a, double[][] b)
	{
		if(a.length != b.length) throw new IllegalArgumentException("Trying to divide two matrices of different shape");
		int cols = a[0].length;
		double[][] res = new double[a.length][a[0].length];
		for(int i = 0; i < a.length; i++)
		{
			if(a[i].length != cols || b[i].length != cols) throw new IllegalArgumentException("Jagged matrix or matrices with mismatched size");
			for(int j = 0; j < a[i].length; j++)
				res[i][j] = a[i][j]/b[i][j];
		}
		return res;
	}

	public static double[][] multiplyMatrices(double[][] a, double[][] b)
	{
		if(a.length != b.length) throw new IllegalArgumentException("Trying to multiply two matrices of different shape");
		int cols = a[0].length;
		double[][] res = new double[a.length][a[0].length];
		for(int i = 0; i < a.length; i++)
		{
			if(a[i].length != cols || b[i].length != cols) throw new IllegalArgumentException("Jagged matrix or matrices with mismatched size");
			for(int j = 0; j < a[i].length; j++)
				res[i][j] = a[i][j]*b[i][j];
		}
		return res;
	}

	
	public static double[][][][] addMatrices(double[][][][] a, double[][][][] b)
	{
		if(a.length != b.length) throw new IllegalArgumentException("Trying to add two matrices of different shape");
		int cols = a[0].length;
		double[][][][] res = new double[a.length][a[0].length][a[0][0].length][a[0][0][0].length];
		for(int i = 0; i < a.length; i++)
		{
			if(a[i].length != cols || b[i].length != cols) throw new IllegalArgumentException("Jagged matrix or matrices with mismatched size");
			for(int j = 0; j < a[i].length; j++)
				for(int k = 0; k < a[i][j].length; k++)
					for(int l = 0; l < a[i][j][k].length; l++)
						res[i][j][k][l] = a[i][j][k][l] + b[i][j][k][l];
			
		}
		return res;
	}

	
	public static double[][] addMatrices(double[][] a, double[][] b)
	{
		if(a.length != b.length) throw new IllegalArgumentException("Trying to add two matrices of different shape");
		int cols = a[0].length;
		double[][] res = new double[a.length][a[0].length];
		for(int i = 0; i < a.length; i++)
		{
			if(a[i].length != cols || b[i].length != cols) throw new IllegalArgumentException("Jagged matrix or matrices with mismatched size");
			for(int j = 0; j < a[i].length; j++)
				res[i][j] = a[i][j] + b[i][j];
		}
		return res;
	}
	
	public static double[][] subtractMatrices(double[][] a, double[][] b)
	{
		if(a.length != b.length) throw new IllegalArgumentException("Trying to add two matrices of different shape");
		int cols = a[0].length;
		double[][] res = new double[a.length][a[0].length];
		for(int i = 0; i < a.length; i++)
		{
			if(a[i].length != cols || b[i].length != cols) throw new IllegalArgumentException("Jagged matrix or matrices with mismatched size");
			for(int j = 0; j < a[i].length; j++)
				res[i][j] = a[i][j] - b[i][j];
		}
		return res;
	}
	
	
	public static void printMatrix(double[][] m, java.io.PrintStream pstream)
	{
		for(int i = 0; i < m.length; i++)
			Util.printarray(m[i], pstream);
	}
	
	
	/** Turn data that is in lat/long coordinates into 
	 *  x/y coordinates, with the new x,y origin (0,0)
	 *  being the smallest lat/long values in the dataset
	 *  
	 * 
	 * @param data
	 */
	public static void planarizeData(double[][] data)
	{		
		double minlat = data[0][0];
		double minlong = data[0][1];
		for(int i =1; i < data.length; i++)
		{
			if(data[i].length < 2)
				throw new RuntimeException("Incorrect argument to planarizeData... less that 2 dimensions.. expecting at least lat/long");
			if(data[i][0] < minlat)
				minlat = data[i][0];
			if(data[i][1] < minlong)
				minlong = data[i][1];
		}	
		
		//use minlat/minlong as our origin, and convert
		//to cartesian
		for(int i =1; i < data.length; i++)
		{
			double lat = data[i][0];
			double lon = data[i][1];
			double x = MathUtil.getDistanceBetweenPointsOnEarth(0, lon, 0, minlong);
			double y = MathUtil.getDistanceBetweenPointsOnEarth(lat, 0, minlat, 0);
			data[i][0] = x;
			data[i][1] = y;
			if(Debug.equalOrMoreVerbose(Debug.EXTRA_INFO))
			{
				Debug.getPrintStream(Debug.EXTRA_INFO).println(lat+","+lon+" --> "+x+","+y);
			}
		}
	}
	
	
	
	public static double[] calculateFiniteDifference(RealFunction func, double[] point, double[] stepsize)
	{
		double[] result = new double[func.getDimensionOfDomain()];
		double[] tmppoint = rses.util.Util.copy(point);
		double tmp, leftval, rightval;
		
		for(int i=0; i < result.length; i++)
		{
			tmp = tmppoint[i];
			tmppoint[i] -= stepsize[i];
			leftval = func.invoke(tmppoint);
			tmppoint[i] = tmp+stepsize[i];
			rightval = func.invoke(tmppoint);
			result[i] = (rightval-leftval)/(stepsize[i]*2); 
			tmppoint[i] = tmp;
		}
		return result;
		
	}
	
	
	

	public static double getUniformRandom(double[] lwrupr)
	{
		return getUniformRandom(lwrupr[0], lwrupr[1]);
	}

	
	public static double getUniformRandom(double lower, double upper)
	{
		return Math.random()*(upper-lower)+lower;
	}
	
	
    /** This is a closed-form (i.e. quick to evaluate) approximation
     *  for the gaussian cummulative density.
     * 
     * @param x
     * @return
     */
    public static double getGaussProbApproximation(double x)
    {
    	double xval = Math.abs(x); //this only works for +ve x, and since gauss is symmetric, we make sure x is +ve 
    	double val = Math.exp(-(xval*xval/2))/(1.64*xval + Math.sqrt(0.76*xval*xval+4));
    	return val;
    }

    
    public static double getLogGaussPdf(double obs, double mean, double sigma)
    {
    	double term1 = Math.log(1/(sigma*Math.sqrt(2*Math.PI)));
    	double term2 = -0.5*Math.pow((obs-mean)/sigma, 2);
    	if(Debug.equalOrMoreParanoid(Debug.MAX_PARANOIA) && Math.abs((term1+term2)-Math.log(getGaussPdf(obs, mean, sigma))) > 0.00001)
    		throw new RuntimeException("getLogGauss and getGaussPdf do not agree to within 0.00001!");
    		
    	return term1 + term2;
    }
    
    public static double getGaussPdf(double obs, double mean, double sigma)
    {
    	double term1 = 1/(sigma*Math.sqrt(2*Math.PI));
    	double term2 = Math.exp(-0.5*Math.pow((obs-mean)/sigma, 2));
    	return term1*term2;
    }
    
    public static double getLogNormalPdf(double obs, double mean, double sigma)
    {
    	double term1 = 1/(obs*sigma*Math.sqrt(2*Math.PI));
    	double term2 = Math.exp(-Math.pow(Math.log(obs)-mean, 2.0)/(2*sigma*sigma));
    	return term1*term2;
    }

    
    
    public static double calculateMode(double[] data, double datamin, double datamax, int[] histogram)
    {
       	int maxindex = Util.getMaxIndex(histogram);
    	double binwidth = (datamax-datamin)/histogram.length;
    	return datamin+(maxindex+0.5)*binwidth;
    	
    }
    
    public static double calculateMode(double[] data, int[] histogram)
    {
    	return calculateMode(data, Util.getMin(data), Util.getMax(data), histogram);
    }
    
    
    
    
    
    public static DistributionStatistics calculateDistributionStatistics(double[] vals)
    {
    	return new DistributionStatistics(vals, true);
    }
    

    public static DistributionStatistics calculateDistributionStatistics(double[] vals, boolean calculateMedian)
    {
    	return new DistributionStatistics(vals, calculateMedian);
    }

    
    
    public static double calculateMedian(double[] vals)
    {
    	double median;
		double[] valscopy = Util.copy(vals);
		Arrays.sort(valscopy);
		if(vals.length%2 == 0)
			median = (valscopy[vals.length/2-1]+valscopy[vals.length/2])/2;
		else
			median = valscopy[vals.length/2];
		return median;
    }
    
    public static double calculateMean(double[] vals)
    {
    	if(vals.length == 0) throw new IllegalArgumentException("Empty array provided as argument");
    	double res = 0.0;
    	for(int i =0; i < vals.length; i++)
    		res += vals[i];
    	return res/vals.length;
    	
    }
    

    /** Calculate the distance in metres between two points on the
     *  earths surface. Assumes a spherical earth with a radius
     *  of 6372.795 metres. Uses the haversine formula to do the
     *  actual calculation of distance.
     *   
     * 
     * @param lat1
     * @param long1
     * @param lat2
     * @param long2
     * @return distance in metres
     */
    public static double getDistanceBetweenPointsOnEarth(double lat1, double long1, double lat2, double long2)
    {
    	//convert to radians
    	double latr1 = Math.toRadians(lat1);
    	double longr1 = Math.toRadians(long1);
    	double latr2 = Math.toRadians(lat2);
    	double longr2 = Math.toRadians(long2);
    	
        double longdif = longr1-longr2;
        double latdif = latr1-latr2;
        
        double term1 = Math.pow(Math.sin(latdif/2), 2);
        double term2 = Math.cos(latr1)*Math.cos(latr2)*Math.pow(Math.sin(longdif/2), 2);
        //use math.min to prevent roundoff errors in antipodal calculations
        double angdif = 2*Math.asin(Math.sqrt(Math.min(1.0, term1+term2)));
        
        return 6372.795*1000*angdif;
        
    }
    

    
    
    /** Construct a histogram binning of some data where we
     *  take finer and finer histogram bins and stop when
     *  we get to the point where going any finer will result
     *  in a bin having less than minbincount samples.
     * 
     * @param points
     * @param minbincount
     * @return
     */
    public static int[] getAdaptiveHistogram(double[] points, int minbincount)
    {
    	if(points.length < minbincount)
    		throw new IllegalArgumentException("number of points is less that min bin count");
    	double min = Util.getMin(points);
    	double max = Util.getMax(points);
    	int[] result = new int[] {points.length};
    	int nbins = 1;    	
    	
    	while(true)
    	{
    		int[] tryresult = getHistogram(points, nbins, min, max);
    		if(Util.getMin(tryresult) < minbincount)
    			break; //reached maximum resolution
    		result = tryresult;
    		nbins++;
    	}
    	
    	return result;
    }
    
    
    public static int[] getHistogram(double[] points, int numbins, double xlower, double xupper)
    {
		int[] bins = new int[numbins];	
		double binwidth = (xupper-xlower)/numbins;
		
		for(int i =0; i < points.length; i++)
		{
			 int bin = (int) Math.floor((points[i]-xlower)/binwidth);
			 
			 //special case, where highest number actually is off the end
			 if(bin==numbins) bin--;
			 
			 bins[bin]++;
		}
		return bins;    	
    }
    
    
    public static int[] getHistogram(double[] points, int numbins)
    {
		double xlower = Double.POSITIVE_INFINITY;
		double xupper = Double.NEGATIVE_INFINITY;
		for(int i =0; i < points.length; i++) {
			if(points[i] < xlower) xlower = points[i];
			if(points[i] > xupper) xupper = points[i];
		}
		return getHistogram(points, numbins, xlower, xupper);
    }
	
    
    
    
    /** A is a double array with each element a row
     *  in the matrix
     *  
     * @return
     */
    public static void gaussElim(double[][] A)
    {
    	int m = A.length;
    	int n = A[0].length;
    	//System.out.println(MathUtil.getMatrixStringRepresentation(A));
    	int i=0;
    	int j=0;
    	while (i < m && j < n)
    	{
    		//Find pivot in column j, starting in row i:
    		double max_val = A[i][j];
    		int max_ind = i;
    		for(int k=i+1; k < m; k++)
    		{
    			double val = A[k][j];
    			if(Math.abs(val) > Math.abs(max_val))
    			{
    				max_val = val;
    				max_ind = k;
    			}
    		}
    		
    		if(max_val != 0)
    		{
    			//switch rows i and max_ind but remain the values of i and max_ind
    			double[] tmp = A[i];
    			A[i] = A[max_ind];
    			A[max_ind] = tmp;
    	    	//System.out.println(MathUtil.getMatrixStringRepresentation(A));
    			
    			//Now A[i, j] will contain the same value as max_val
    			//divide row i by max_val
    			for(int q =0; q < n; q++)
    				A[i][q] /= max_val;
    	    	//System.out.println(MathUtil.getMatrixStringRepresentation(A));
    			
    			//Now A[i, j] will have the value 1
    			for(int u = 0; u < m; u++)
    			{
    				if(u != i)
    				{
    			        //subtract A[u, j] * row i from row u
    					double multiplier = A[u][j];
    			        for(int q=0; q < n; q++)
    						A[u][q] -= multiplier*A[i][q];
    			        //#Now A[u, j] will be 0, since A[u, j] - A[u, j] * A[i, j] = A[u, j] - 1 * A[u, j] = 0
    				}
    			}
    			i = i + 1;
    		}
        	//System.out.println(MathUtil.getMatrixStringRepresentation(A));
    		j = j + 1;
    	}
    }
    
    
    
    
    public static String getMatrixStringRepresentation(double[][] A)
    {
    	StringBuffer res = new StringBuffer();
    	for(int row = 0; row < A.length; row++)
    	{
			res.append("[ ");
			for(int col = 0; col < A[0].length; col++)
    		{
				res.append(A[row][col]+" ");
    		}
			res.append("]"+PlatformInfo.nl);
    	}
    	return res.toString();
    }
    
    
    
    /** Re-express the point (x,y) in a new coordinate system
     *  where vec1/vec2 are the basis vectors
     * 
     * @param x
     * @param y
     * @param vec1
     * @param vec2
     * @return
     */
    public static double[] expressInNewCoordinateSystem(double x, double y, double[] vec1, double[] vec2)
    {
		double[][] A = new double[][] {
			{vec1[0], vec2[0], x},
			{vec1[1], vec2[1], y}
		};
		MathUtil.gaussElim(A);
		return new double[] { A[0][2], A[1][2] };
    }
    
    /** Generate a pair of `random' basis vectors (2D only). Does this 
     *  by choosing an angle at random and then working out
     *  a unit vector in that direction and the corresponding
     *  perpendicular unit vector.
     * @return
     */
    public static double[][] generateRandom2DVector()
    {
		//choose a new axis at random by choosinng a random direction
		double angle = Math.random()*Math.PI; 
		
		//work out the unit vector associated with it
		double[] vec1 = new double[] {Math.cos(angle), Math.sin(angle)};
		
		//work out the unit vector perpendicular to it
		double[] vec2 = new double[] {Math.cos(Math.PI/2+angle), Math.sin(Math.PI/2+angle)};

		return new double[][] {vec1, vec2};
    }
    
    
    
    /** Given some data, find the lower/upper bounds on the data values
     * such that 95% of the data values are included.
     * 
     * @param data
     * @param pcttoinclude
     * @return
     */
    public static double[] getTwoTailedBounds(double[] data, double pcttoinclude)
    {
    	int upper = data.length-1;
    	int lower = 0;
    	double excluded = 0.0;
    	double pctperexclude = 1.0/data.length;
    	
    	//keep closing in bounds until we have only just enough data included
    	while(true)
    	{
    		excluded = excluded + pctperexclude;
    		if(excluded > 1.0-pcttoinclude)
    			break;
    		lower++;
    		
    		excluded = excluded+pctperexclude;
    		if(excluded > 1.0-pcttoinclude)
    			break;
    		upper--;
    	}
    	
    	
    	return new double[] {data[lower], data[upper]};
    }
    
    
    
    public static final int MAXIT = 50;

    /** Perform kmeans clustering (using standard euclidean distance
     *  metric) on a set of vectors. This procedure is deterministic
     *  -- that is, it always returns the same result given the same
     *  input vectors (including ordering). If you reorder the same
     *  set of input vectors, results may change.  
     *  
     *  
     * @param vectors
     * @param k
     * @return
     */
    public static int[] kmeans(double[][] vectors, int k)
    {
    	return kmeans(vectors, k, MAXIT);
    }
    
    
    public static int[] kmeans(double[][] vectors, int k, int maxit)
    {
    	//set initial membership
    	int[] membership = new int[vectors.length];
    	for(int i = 0; i < vectors.length; i++)
    		membership[i] = i % k;
    	
    	double[][] centroids = null;
    	
    	//now iterate
    	for(int it = 0; it < maxit; it++)
    	{
    		//calculate centroids
    		centroids = new double[k][vectors[0].length];
    		int[] counts = new int[k];
    		for(int i =0; i < membership.length; i++)
    		{
    			int c = membership[i];
    			for(int j = 0; j < vectors[i].length; j++) {
    				centroids[c][j] += vectors[i][j];
    				counts[c]++;
    			}
    		}
    		
    		for(int c = 0; c < k; c++) {
    			if(counts[c] == 0) {
    				Debug.println("WARNING: Empty centroid #"+c+" in kmeans clustering... assigning value", Debug.IMPORTANT);
    				centroids[c] = Util.copy(vectors[vectors.length/2]);
    			}
    			else for(int j = 0; j < vectors[0].length; j++)
    				centroids[c][j] /= counts[c];
    		}
    		
    		
    		//now calculate new membership
    		int[] newmembership = new int[membership.length];
    		int membershipchanges = 0;
        	for(int i = 0; i < vectors.length; i++)
        	{
        		double mindist = Double.POSITIVE_INFINITY;
        		int minc = -1;
        		for(int c = 0; c < centroids.length; c++)
        		{
        			double dist = Util.distance(centroids[c], vectors[i]);
        			if(dist < mindist) {
        				mindist = dist;
        				minc = c;
        			}
        		}
        		newmembership[i] = minc;
        		if(newmembership[i] != membership[i])
        			membershipchanges++;
        	}
    		
        	if(membershipchanges == 0)
        		break; //no change to membership, so we can stop early
    		
        	membership = newmembership;
        	
        	Debug.println(membershipchanges+" changes to cluster membership out of "+vectors.length+" vectors in iteration "+it, Debug.INFO);        	
    	}
    	
    	return membership;
    	
    }
    
    
    
    
    
    
    
    /**
     * 
     * @param polyPoints An array of tuples defining the points in the polygon.
     *                   The last point in the polygon *must* be the same as the first.
     * @return
     */
    public static double calculate2DpolygonSignedArea(double[][] polyPoints) 
	{
    	if(polyPoints[0][0] != polyPoints[polyPoints.length-1][0] || 
    	   polyPoints[0][1] != polyPoints[polyPoints.length-1][1])
    		throw new IllegalArgumentException("ill-formed polygon -- first point must be the same as the last point");

    	if(polyPoints.length < 4) //cant be a polygon
    		throw new IllegalArgumentException("Invalid polygon ... less than 4 vertices");

		double[][] translatedpolypoints = new double[polyPoints.length][];
		for(int i = 0; i < polyPoints.length; i++)
			translatedpolypoints[i] = new double[] {polyPoints[i][0]-polyPoints[0][0], polyPoints[i][1]-polyPoints[0][1]};
    	
		double area = 0;

		for (int i = 0; i < polyPoints.length-1; i++) {
			area += translatedpolypoints[i][0] * translatedpolypoints[i+1][1];
			area -= translatedpolypoints[i+1][0] * translatedpolypoints[i][1];
		}
		return area/2;
	}

    /**
     * 
     * @param polyPoints An array of tuples defining the points in the polygon.
     *                   The last point in the polygon *must* be the same as the first.
     * @return
     */
    public static double calculate2DpolygonArea(double[][] polyPoints) 
	{
		return Math.abs(calculate2DpolygonSignedArea(polyPoints));
	}

	
	/**
	 */
	public static double[] calculate2DpolygonCentroid(double[][] polyPoints) 
	{
		double cx = 0, cy = 0;
		double area =  calculate2DpolygonSignedArea(polyPoints);
		
		/** OK, because the centroid calculation is 
		 *  susceptible to floating point error-creep,
		 *  its much better to do the centroid calculation on 
		 *  an identical polygon that has been translated near
		 *  the origin. Then just translate the centroid back.
		 */
		double[][] translatedpolypoints = new double[polyPoints.length][];
		for(int i = 0; i < polyPoints.length; i++)
			translatedpolypoints[i] = new double[] {polyPoints[i][0]-polyPoints[0][0], polyPoints[i][1]-polyPoints[0][1]};
		
		double factor = 0;
		for (int i = 0; i < polyPoints.length-1; i++) 
		{
			factor = (translatedpolypoints[i][0] * translatedpolypoints[i+1][1]
					- translatedpolypoints[i+1][0] * translatedpolypoints[i][1]);
			cx += (translatedpolypoints[i][0] + translatedpolypoints[i+1][0]) * factor;
			cy += (translatedpolypoints[i][1] + translatedpolypoints[i+1][1]) * factor;
		}
		factor = 1 / (6*area);
		cx *= factor;
		cy *= factor;
		return new double[] {polyPoints[0][0]+cx, polyPoints[0][1]+cy};
	}
    
    
    
    
    
    public static void matrixTest(String[] args)
    {
    	double[][] A = new double[][] {
    			{7.0, -1.0, 4.0},
    			{2.0, 3.0, 3.0}
    	};
    	double[][] orig = Util.copy(A);
    	System.out.println(MathUtil.getMatrixStringRepresentation(A));
    	MathUtil.gaussElim(A);
    	System.out.println(MathUtil.getMatrixStringRepresentation(A));
    	
    	//now do the double check
    	for(int i = 0; i < A.length; i++)
    	{
    		double res = 0;
    		for(int j = 0; j < A[0].length-1; j++)
    		{
    			res += A[j][A[0].length-1]*orig[i][j];
    		}
    		System.out.println(res);
    	}
    	
    }
    
    
    
    
    public static boolean isPointInPolygon(double[][] closedpolygon, double x, double y)
    {
    	//MAKE SURE POLYGON IS CLOSED
    	if(closedpolygon[0][0] != closedpolygon[closedpolygon.length-1][0] ||
    	    	   closedpolygon[0][1] != closedpolygon[closedpolygon.length-1][1])
    	    		throw new RuntimeException("Polygon must be closed (i.e. last and first points must be the same");

    	
    	double[][] polygon = new double[closedpolygon.length-1][];
    	for(int i =0; i < polygon.length;i++) //translate and unclose the polygon
    		polygon[i] = new double[] {closedpolygon[i][0]-closedpolygon[0][0], closedpolygon[i][1]-closedpolygon[0][1]};
    	
    	//so translate the x,y points too
    	x = x - closedpolygon[0][0];
    	y = y - closedpolygon[0][1];
    	
    	int counter = 0;
    	int i;
    	double[] p1,p2;

    	p1 = polygon[0];
    	for (i=1;i<=polygon.length;i++) 
    	{
    		p2 = polygon[i % polygon.length];
    		if (y > Math.min(p1[1],p2[1])) 
    		{
    			if (y <= Math.max(p1[1],p2[1])) 
    			{
    				if (x <= Math.max(p1[0],p2[0])) 
    				{
    					if (p1[1] != p2[1]) 
    					{
    							double xinters = (y-p1[1])*(p2[0]-p1[0])/(p2[1]-p1[1])+p1[0];
    							if (p1[0] == p2[0] || x <= xinters)
    									counter++;
    					}
    				}
    			}
    		}
    		p1 = p2;
    	}

    	if (counter % 2 == 0)
    		return false;
    	else
    		return true;
	}
    
    
    
    
    public static boolean isPolygonConcave(double[][] points)
    {
    	if(points[0][0] != points[points.length-1][0] ||
    	   points[0][1] != points[points.length-1][1])
    		throw new RuntimeException("Polygon must be closed (i.e. last and first points must be the same");
    	
    	//Note that this calculation is stable even if the polygon is far from the origin,
    	//because the factors that are multiplied are points that are subtracted from one another,
    	//so a kind of default translation is done anyway.
    	
    	boolean seenpositive = false;
    	boolean seennegative = false;
    	for(int i = 1; i < points.length-1; i++) {
    		double crossprod = (points[i][0]-points[i-1][0])*(points[i+1][1]-points[i][1])-
    		             (points[i][1]-points[i-1][1])*(points[i+1][0]-points[i][0]);
    		if(crossprod >= 0)
    			seenpositive = true;
    		else
    			seennegative = true;
    	}
    	
    	if(seenpositive && seennegative)
    		return true; //its concave
    	else
    		return false; //convex
    		
    }
    
    
    /** Determine whether a polygon is clockwise or not.
     * 
     * Note this only works if the points are (x,y) points.
     * If your points are (y,x) points (such as geographic
     * lat,longs), then you need to reverse this, as a counter
     * clockwise polygon in that coordinate system will be
     * clockwise in this one.
     * 
     * @param points
     * @return
     */
    public static boolean isCounterClockwise(double[][] points)
    {
    	if(points[0][0] != points[points.length-1][0] ||
    	    	   points[0][1] != points[points.length-1][1])
    	    		throw new RuntimeException("Polygon must be closed (i.e. last and first points must be the same");

    	if(isPolygonConcave(points))
    	{
    		double area = calculate2DpolygonSignedArea(points);
    		if(area > 0)
    			return true;
    		return false;
    	}
    	else //its convex
    	{
    		//Note that this calculation is stable even if the polygon is not near the origin
    		
    		//we can just take the cross-product of any two edges we 
    		//like, and this tells us whether its ccw or not
    		double crossprod = (points[1][0]-points[0][0])*(points[2][1]-points[1][1])-
                               (points[1][1]-points[0][1])*(points[2][0]-points[1][0]);
    		if(crossprod >= 0.0)
    			return true;
    		return false;
    			
    	}
    }
    
    
    public static double[][] reversePolygon(double[][] points)
    {
    	double[][] res = new double[points.length][2];
    	for(int i = 0; i < res.length; i++)
    	{
    		res[res.length-1-i][0] = points[i][0];
    		res[res.length-1-i][1] = points[i][1];
    	}
    	return res;
    }
    
    
    /** Calculate the mean centroid of a polygon
     * (i.e. the average of all the vertices).
     * 
     * @param poly Must be a closed polygon (the last closing point is excluded from the calculation)
     * @return
     */
    public static double[] calculateMeanCentroid(double[][] poly)
    {
    	if(poly[0][0] != poly[poly.length-1][0] ||
 	    	   poly[0][1] != poly[poly.length-1][1])
 	    		throw new RuntimeException("Polygon must be closed (i.e. last and first points must be the same");
    	
    	double x = 0.0;
    	double y = 0.0;
    	for(int i = 0; i < poly.length-1; i++)
    	{
    		x += poly[i][0];
    		y += poly[i][1];
    	}
    	return new double[] {x/(poly.length-1),y/(poly.length-1)};
    	
    }
    
    
    
    
    
    
    public static void main(String[] args)
    {	
    	double[][] polypoints = {
    			{-33.9094945420787, 151.204156849836},
    			{-33.9092945420787, 151.204156849836},
    			{-33.9092945420787, 151.203956849836},
    			{-33.9094945420787, 151.203956849836},
    			{-33.9094945420787, 151.204156849836}
    	};
    	
    	//reverse the x,y coords
    	/*for(int i = 0; i < polypoints.length; i++)
    	{
    		polypoints[i] = new double[] {polypoints[i][1], polypoints[i][0]};
    	}*/
    	
    	double[] centroid = calculate2DpolygonCentroid(polypoints);
    	Util.printarray(centroid, System.out);
    
    	
    	
    	double x = Double.parseDouble(args[0]);
    	double y = Double.parseDouble(args[1]);
    	double[][] poly = new double[][] {
    			{0,0},
    			{1,0},
    			{1,1},
    			{0,1},
    			{0,0}
    	};
    	
    	System.out.println(isPointInPolygon(poly, x, y));
    }
    
}
