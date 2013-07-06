package rses.regression.randomforest;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Random;

import rses.Debug;
import rses.math.MathUtil;
import rses.regression.Datum;
import rses.regression.randomforest.RegressionTree;
import rses.util.FileUtil;
import rses.util.Util;
import rses.spatial.Interpolater;
import rses.spatial.util.Axes;





public class RandomForest2DInterpolation implements Serializable, Interpolater
{
	private Axes axes;
	private RegressionTree[] forest;
	
	
	private RandomForest2DInterpolation(Axes axes, RegressionTree[] forest)
	{
		this.axes = axes;
		this.forest = forest;
	}
	
	
	public static RandomForest2DInterpolation readFromFile(String filename) throws Exception
	{
		ObjectInputStream ooi = new ObjectInputStream(new FileInputStream(filename));
		RandomForest2DInterpolation rf2d = (RandomForest2DInterpolation) ooi.readObject();
		ooi.close();
		return rf2d;
	}
	
	

	public double getPrediction(double lat, double lon)
	{
		double[] dummy = expandToNewAxes(new double[] {lat, lon, 0.0}, axes.axes1, axes.axes2);
		double val = 0.0;
		for(int i =0; i < forest.length; i++)
			val += forest[i].getValue(new Datum(dummy));
		return val/forest.length;
	}
	
	
	
	
	
	private static void help()
	{
		System.err.println("Usage:");
		System.err.println("");
		System.err.println("argument 1 is the name of the input data file");
		System.err.println("argument 2 is the number of points to interpolate in the x direction");
		System.err.println("argument 3 is the number of points to interpolate in the y direction");
		System.err.println("argument 4 is the number of trees in the random forest");
		System.err.println("argument 5 is the new number of coordinate systems to introduce");		
		System.err.println("argument 6 is the min x value to interpolate");
		System.err.println("argument 7 is the max x value to interpolate");
		System.err.println("argument 8 is the min y value to interpolate");
		System.err.println("argument 9 is the max y value to interpolate");
		System.err.println("argument 10 is the terminal data points in each node");
		System.err.println("argument 11 is the error norm to use (2 is squared error)");
		System.err.println("argument 12 is either 'true' or 'false', indicating whether boostrap sampling should be used");
		System.err.println();
	}
	
	
	//interpolate data by generating a random forest 
	//and predicting all the in-between data
	public static void main(String[] args) throws Exception
	{
		Debug.setVerbosityLevel(Debug.INFO);
		
		if(args.length > 0 && args[0].equalsIgnoreCase("fromobjfile")) {
			predictFromStoredPredictors(args[1], args[2]); //args 1 is the object file, 2 the data file to predict
			return;
		}
		
		if(args.length != 12) {
			help(); System.exit(1);
		}
		String datafile = args[0];
		int xpoints = Integer.parseInt(args[1]);
		int ypoints = Integer.parseInt(args[2]);
		int treesInForest = Integer.parseInt(args[3]);
		int newAxes = Integer.parseInt(args[4]);
		double xlwr = Double.parseDouble(args[5]);
		double xupr = Double.parseDouble(args[6]);
		double ylwr = Double.parseDouble(args[7]);
		double yupr = Double.parseDouble(args[8]);
		int nodecapacity = Integer.parseInt(args[9]);
		double norm = Double.parseDouble(args[10]);
		boolean bootstrap = args[11].equalsIgnoreCase("true");
		
		//extract the data
		double[][] data = FileUtil.readVectorsFromFile(new java.io.File(datafile));
	
		//add extra axes
		Axes axes = generateBasisVectors(newAxes);
		double[][] newdata = addExtraAxesToData(data, axes);
		for(int i =0; i < newdata.length; i++)
			Debug.println(Util.arrayToString(newdata[i]), Debug.INFO);
		
		
		
		Datum[] datums = new Datum[newdata.length];
		for(int i =0; i < datums.length; i++)
			datums[i] = new Datum(newdata[i]);
				
		//now build a random forest from the data
		//(use bootstrap sampling)
		RegressionTree[] forest = new RegressionTree[treesInForest];
		for(int i =0; i < forest.length; i++)
		{
			forest[i] = new RegressionTree(RegressionTree.LOG2_IN_DATA_SPLITFUNCTION, 1.0);
			Datum[] traindata = datums;
			if(bootstrap)
			{
				Object[] bso = Util.getBootstrapSample(datums, datums.length)[0];
				traindata = new Datum[datums.length];
				for(int j =0; j < traindata.length; j++)
					traindata[j] = (Datum) bso[j];
			}
			forest[i].train(traindata, nodecapacity, norm);
			forest[i].compress();
			Debug.println("Finishing training tree "+(i+1)+" of "+forest.length, Debug.INFO);
		}
		
		//now save the Forest to File
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("rf.obj"));
		oos.writeObject(new RandomForest2DInterpolation(axes,forest));
		oos.close();
		
		if(Debug.equalOrMoreVerbose(Debug.EXTRA_INFO)) {
			for(int i =0; i < forest.length; i++)
				Debug.println(forest[i].toString(), Debug.EXTRA_INFO);
		}
		
		double xstep = (xupr-xlwr)/xpoints;
		double ystep = (yupr-ylwr)/ypoints;
			
		
		double x = xlwr;
		while(x+xstep/2 < xupr)
		{
			double y = ylwr;
			String gisline = "LAYER ";
			while(y+ystep/2 < yupr)
			{
				double[] dummy = expandToNewAxes(new double[] {x, y, 0.0}, axes.axes1, axes.axes2);
				if(Debug.equalOrMoreVerbose(Debug.EXTRA_INFO)) {
					Debug.println("predicting for "+Util.arrayToString(dummy), Debug.EXTRA_INFO);
				}
				Datum d = new Datum(dummy);
				double val = 0.0;
				for(int i =0; i < forest.length; i++)
				{
					Debug.println("prediction from tree "+i+" is "+forest[i].getValue(d), Debug.EXTRA_INFO);
					val += forest[i].getValue(d);
				}
				val /= forest.length;
				System.out.println("PRED "+x+" "+y+" "+val);
				gisline = gisline+val+" ";
				y += ystep;
			}
			System.out.println(gisline);
			x += xstep;
		}
		
		
	}
	
	
	//read in a stored RF object and use it to predict 
	//each value in a data file
	//
	//datafile is assumed to be in [LAT LONG VALUE]  format 
	private static void predictFromStoredPredictors(String objfile, String datafile) throws Exception
	{
		double[][] data = FileUtil.readVectorsFromFile(new java.io.File(datafile));
		RandomForest2DInterpolation rf2d = readFromFile(objfile);
		double toterror = 0.0;
		
		for(int i =0; i < data.length; i++)
		{
			double pred = rf2d.getPrediction(data[i][0], data[i][1]);
			double error = Math.pow(pred-data[i][2], 2.0);
			toterror += error;
			System.out.println("PRED: "+data[i][0]+" "+data[i][1]+" "+pred);
		}
		System.out.println("Mean Squared Error: "+toterror/data.length);
	}
	
	
	/** Generate <code>numtogenerate</code> randomly chosen
	 *  basis vectors (of unit length). They are chosen
	 *  by selecting an angle at random for the first axis
	 *  and then constructing the basis from that.
	 * 
	 * @param numtogenerate
	 * @return
	 */
	public static Axes generateBasisVectors(int numtogenerate)
	{
		Axes result = new Axes();
		result.axes1 = new double[numtogenerate][];
		result.axes2 = new double[numtogenerate][];
		Random rand = new Random();
		
		for(int i = 0; i < numtogenerate; i++)
		{
			//choose a new axis at random by choosinng a random direction
			double angle = rand.nextDouble()*Math.PI; 
			
			//work out the unit vector associated with it
			double[] vec1 = new double[] {Math.cos(angle), Math.sin(angle)};
			
			//work out the unit vector perpendicular to it
			double[] vec2 = new double[] {Math.cos(Math.PI/2+angle), Math.sin(Math.PI/2+angle)};
			
			Debug.println("Random Basis Vectors are:", Debug.IMPORTANT);
			Debug.println(Util.arrayToString(vec1), Debug.IMPORTANT);
			Debug.println(Util.arrayToString(vec2), Debug.IMPORTANT);
			
			result.axes1[i] = vec1;
			result.axes2[i] = vec2;
		}
		
		return result;
	}
	
	
	
	public static double[] expandToNewAxes(double[] data, double[][] axes1, double[][] axes2)
	{
		double[] res = new double[data.length+axes1.length*2];
		res[0] = data[0];
		res[1] = data[1];
		res[res.length-1] = data[2];
		
		for(int i = 0; i < axes1.length; i++)
		{
			double[] vec1 = axes1[i];
			double[] vec2 = axes2[i];
			
			Debug.println("Rexpressing "+Util.arrayToString(data)+" in new basis", Debug.EXTRA_INFO);
			double[][] A = new double[][] {
				{vec1[0], vec2[0], data[0]},
				{vec1[1], vec2[1], data[1]}
			};
			//System.out.println(MathUtil.getMatrixStringRepresentation(A));
			MathUtil.gaussElim(A);
			//System.out.println(MathUtil.getMatrixStringRepresentation(A));
			//System.out.println("Finished gauss elim");
			res[i*2+2] = A[0][2];
			res[i*2+3] = A[1][2];
		}
		
		return res;
	}
	
	
	public static double[][] addExtraAxesToData(double[][] data, int numnewaxes)
	{
		Axes axes = generateBasisVectors(numnewaxes);
		return addExtraAxesToData(data, axes);
	}
	
	public static double[][] addExtraAxesToData(double[][] data, Axes axes)
	{
		double[][] newaxes1 = axes.axes1;
		double[][] newaxes2 = axes.axes2;
		double[][] res = new double[data.length][];

		
		//now reexpress each point in terms of the new basis vectors
		for(int j = 0; j < data.length; j++)
		{
			res[j] = expandToNewAxes(data[j], newaxes1, newaxes2);
		}

		
		return res;
	}
	
	

}

