package rses.apps;


import rses.Debug;
import rses.regression.Datum;
import rses.regression.kriging.Kriging;
import rses.util.FileUtil;




public class Kriging2DInterpolation
{

	private static void help()
	{
		System.err.println("Usage:");
		System.err.println("");
		System.err.println("arg1 -- data file");
		System.err.println("arg2 -- maxdata");
		System.err.println("arg3 -- # lat points to interpolate");
		System.err.println("arg4 -- # long points to interpolate");
		System.err.println("arg5 -- r step (in metres)");
		System.err.println("arg6 -- error norm (use 2 for squared error)");
		System.err.println("arg7 -- min x value to interpolate");
		System.err.println("arg8 -- max x value to interpolate");
		System.err.println("arg9 -- min y value to interpolate");
		System.err.println("arg10 -- max y value to interpolate");
		System.err.println();
		System.err.println();
		
	}
	

	public static void main(String[] args) throws Exception
	{
		Debug.setVerbosityLevel(Debug.INFO);
		
		if(args.length != 10)
		{
			help();
			System.exit(1);
		}
		double[][] vect = FileUtil.readVectorsFromFile(new java.io.File(args[0]));
		int maxdata = Integer.parseInt(args[1]);
		int nlat = Integer.parseInt(args[2]);
		int nlon = Integer.parseInt(args[3]);
		double rstep = Integer.parseInt(args[4]);
		double norm = Double.parseDouble(args[5]);
		Datum[] d = new Datum[vect.length];
		double minlat = Double.parseDouble(args[6]);
		double maxlat = Double.parseDouble(args[7]);
		double minlong = Double.parseDouble(args[8]);
		double maxlong = Double.parseDouble(args[9]);
		
		for(int i = 0; i < d.length; i++)
			d[i] = new Datum(vect[i]);

		Debug.println("Estimating radius and sill", Debug.INFO);
		double[] r_and_sill = Kriging.estimateRadiusAndSill(d, rstep, norm);
		Debug.println("Estimated radius "+r_and_sill[0]+" and sill "+r_and_sill[1], Debug.INFO);
		
		Kriging k = new Kriging(d, r_and_sill[0], r_and_sill[1], maxdata);
		
		
		double latstep = (maxlat-minlat)/nlat;
		double lonstep = (maxlong-minlong)/nlon;
		double lat = minlat;
		while(lat <= maxlat)
		{
			double lon = minlong;
			while(lon <= maxlong)
			{
				double v = k.getPrediction(new Datum(new double[] {lat, lon, 0.0}));
				System.out.println("PRED "+lat+" "+lon+" "+v);
				lon += lonstep;
			}
			lat += latstep;
		}
		
		
	}
}	