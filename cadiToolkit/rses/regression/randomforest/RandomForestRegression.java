package rses.regression.randomforest;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import rses.Debug;
import rses.math.MathUtil;
import rses.regression.Datum;
import rses.util.FileUtil;
import rses.util.Util;


/** 
 * 
 * @author peterr
 *
 */


public class RandomForestRegression implements Serializable
{

	
	/** If errorNorm is negative, then we are in classification mode,
	 * and it is the number of classes in the classifier
	 * 
	 * @param terminalNodeSize
	 * @param nsplittries
	 * @param data
	 * @param errorNorm
	 * @return
	 */
	public static RegressionTree generateRegressionTree(int terminalNodeSize, 
			int splitfunc, double splitconst, Datum[] data, double errorNorm)
	{
		RegressionTree tree = new RegressionTree(splitfunc, splitconst);
		tree.setSplitFunction(splitfunc, splitconst);
		tree.train(data, terminalNodeSize, errorNorm);
		return tree;
	}


	
	//the out of bag error distribution
	private ArrayList errors = new ArrayList();
	private double[] errorbounds = null;

	//individual out-of-bag distributions for 
	//every data point in the training set
	ArrayList[] outofbag = null;
	
	//bounds that include 95% of the data
	private double[][] conf95; 
	                 
	//bounds that include 90% of the data
	private double[][] conf90; 

	//bounds that include 75% of the data
	private double[][] conf75;
 
	
	
	private double errorNorm = Double.NaN;
	private int terminalNodeSize = -1;
	private boolean bootstrapSample = false;
	private boolean inregressionmode = true;
	private int nclasses = -1; //only used in classifier mode
	private RegressionTree[] forest;
	private boolean estimateConfidenceBounds = false;
	private int splitfunc;
	private double splitconst;
	
	
	public static RandomForestRegression getClassifierInstead(
			int terminalNodeSize, 
			int splitfunc, double splitconst, int numtrees, 
			boolean bootstrapSample, int numClasses)
	{
		RandomForestRegression rf =  
			new RandomForestRegression(terminalNodeSize, splitfunc, splitconst, numtrees, false, bootstrapSample, -numClasses);
		return rf;
	}
	
	
	
	public RandomForestRegression(int terminalNodeSize, 
			int splitfunc, double splitconst, int numtrees, 
			boolean estimateConfidenceBounds, boolean bootstrapsample, 
			double errorNorm)
	{
		this.errorNorm = errorNorm;
		this.inregressionmode = (errorNorm >= 0);
		this.forest = new RegressionTree[numtrees];
		this.bootstrapSample = bootstrapsample;
		this.estimateConfidenceBounds = estimateConfidenceBounds;
		if(!inregressionmode) this.nclasses = (int) Math.round(-errorNorm);
		this.terminalNodeSize = terminalNodeSize;
		this.splitfunc = splitfunc;
		this.splitconst = splitconst;
	}
	
	
	
	public boolean isClassifier()
	{
		return !this.inregressionmode;
	}
	
	
	
	public void train(Datum[] data)
	{
		double[] outofbagpredictions = null;
		int[] outofbagcount = null;
		
		//only collect out of bag error statistics for regression
		//just at the moment. Need TODO this for classification
		if(this.inregressionmode) {
			outofbagpredictions = new double[data.length];
			outofbagcount = new int[data.length];
		}
		
		//if asked, we keep the distribution of out-of-bag
		//error for each data point in the training set
		if(estimateConfidenceBounds)
		{
			if(!this.inregressionmode)
				throw new IllegalArgumentException("cannot estimate confidence bounds for classifier.. only for regression");
			int start = 0;
			if(this.outofbag == null)
				outofbag = new ArrayList[data.length];
			else {
				ArrayList[] newoutofbag = new ArrayList[outofbag.length+data.length];
				for(int i =0; i < outofbag.length; i++)
					newoutofbag[i] = outofbag[i];
				start = outofbag.length;
				outofbag = newoutofbag;
			}
			for(int j = start; j < start+data.length; j++)
				outofbag[j] = new ArrayList(data.length/2);
		}
		
		//now do the actual training
		Random rand = new Random();
		for(int i =0; i < forest.length; i++)
		{
			if(forest[i] == null)
				forest[i] = new RegressionTree(this.splitfunc, this.splitconst); //first train, create the tree
			
			//collect training data. This could either be a bootstrap
			//of the provided data, or the provided data as is
			Datum[] train = data;
			boolean[] included = new boolean[data.length];
			if(this.bootstrapSample)
			{
				//first we get a bootstrap sample
				train = new Datum[data.length];
				for(int j = 0; j < train.length; j++)
				{
					int index = rand.nextInt(data.length);
					included[index] = true;
					train[j] = data[index];
				}
			}
			else for(int j=0; j < data.length; j++)
				included[j] = true;
			
			//do the training
			forest[i].train(train, terminalNodeSize, errorNorm);
			forest[i].compress(); //TODO is this always safe?
			
			//work out predictions for out-of-bag samples
			if(this.inregressionmode) for(int j = 0; j < data.length; j++)
			{
				if(!included[j])
				{
					double pred = forest[i].getValue(data[j]);
					if(estimateConfidenceBounds)
						outofbag[j].add(new Double(pred));
					outofbagpredictions[j] += pred;
					outofbagcount[j]++;
				}
			}
			
			
			Debug.println("Finished generating tree "+i, Debug.EXTRA_INFO);
		}
		
		
		//Debug.println(bootstrapSample+" "+inregressionmode, Debug.INFO);
		
		//not that we are done, go and work out any out-of-bag estimates
		//that we want to have
		//Also note we dont calculate out of bag estimates for classification
		//yet. Need TODO this at some point.
		if(this.bootstrapSample && this.inregressionmode)
			calculateOutOfBagEstimates(outofbagpredictions, outofbagcount, data);
		
	}


	//calculate out-of-bag estimates of the error distibution 
	//overall, and for each data point in the training set
	private void calculateOutOfBagEstimates(double[] outofbagpredictions, int[] outofbagcount, Datum[] data)
	{
		Debug.println("Calculating out of bag error estimates", Debug.INFO);
		
		//first we calculate the 95% bounds for each sample in the
		//training data set.
		//
		//We can only do this if we kept (during forest generation)
		//the out of bag errors for each point in the training set
		if(outofbag != null)
		{
			this.conf75 = new double[outofbag.length][];
			this.conf90 = new double[outofbag.length][];
			this.conf95 = new double[outofbag.length][];
			
			//for each data point, calculate the error

			for(int i = 0; i < outofbag.length; i++)
			{
				double[] valarray = new double[outofbag[i].size()];
				for(int j = 0; j < valarray.length; j++)
					valarray[j] = ((Double) outofbag[i].get(j)).doubleValue();
				Arrays.sort(valarray);
				
				Debug.println("There are "+valarray.length+" out-of-bag error estimates for datum "+i, Debug.INFO);

				
				//if we have fewer than 40 samples, we cannot do 95% interval
				if(valarray.length < 40)
					conf95[i] = null;
				else
					conf95[i] = MathUtil.getTwoTailedBounds(valarray, 0.95);
		
				
				//if we have fewer than 20 samples, we cannot do 90% interval
				if(valarray.length < 20)
					conf90[i] = null;
				else
					conf90[i] = MathUtil.getTwoTailedBounds(valarray, 0.90);

				
				if(valarray.length < 8) //need 8 samples for 75% bounds
					conf75[i] = null;
				else
					conf75[i] = MathUtil.getTwoTailedBounds(valarray, 0.75);
			}
		}
	
	
		//now calculate the out-of-bag error distribution,
		//and the upper and lower bound on this distribution
		double lwr = Double.POSITIVE_INFINITY;
		double upr = Double.NEGATIVE_INFINITY;
		for(int i =0; i < data.length; i++)
		{
			if(outofbagcount[i] == 0) { 
				Debug.println("WARNING: no out of bag prediction possible for datum "+i+" This means you arent growing a large enough forest", Debug.IMPORTANT);
				continue;
			}
			double pred = outofbagpredictions[i]/outofbagcount[i];
			double actual = data[i].getValue();
			double err = pred-actual;
			this.errors.add(new Double(err));
			if(err < lwr) lwr = err;
			if(err > upr) upr = err;
		}
	
		errorbounds = new double[] {lwr, upr};
	}
	
	
	//take the unweighted combination
	//of all the trees in the forest
	public double getValue(Datum d)
	{
		double sum = 0.0;
		for(int i =0; i < forest.length; i++)
			sum = sum + forest[i].getValue(d);
		return sum/forest.length;
	}
	
	
	public double getMedianValue(Datum d)
	{
		double[] results = new double[this.forest.length];
		for(int i = 0; i < results.length; i++)
			results[i] = this.forest[i].getValue(d);
		Arrays.sort(results);
		double result = (results[results.length/2]+results[(results.length-1)/2])/2;
		return result;
	}
	
	
	
	public double[] getClassDistribution(Datum d)
	{
		return RegressionTree.getClassDistribution(forest,d);
	}
	
	public int getClass(Datum d)
	{
		if(this.inregressionmode) throw new UnsupportedOperationException("Trying to get class distribution in regression mode");
		double[] classdist = getClassDistribution(d);
		int mindex = Util.getMaxIndex(classdist);
		return mindex;
	}
	
	
	
	public int[] getErrorHistogram(int minbincount)
	{
		double[] errarray = new double[errors.size()];
		for(int i = 0; i < errarray.length; i++)
			errarray[i] = ((Double) errors.get(i)).doubleValue();
	
		int[] hist = MathUtil.getAdaptiveHistogram(errarray, minbincount);
		return hist;
	}

	/** Get the upper and lower bounds on (predicted-actual)
	 *  for out of bag data.
	 *  
	 * @return
	 */
	public double[] getErrorBounds()
	{
		return this.errorbounds;
	}

	
	public double[][] get90PctConfidenceBounds()
	{
		if(this.conf90 == null)
			throw new RuntimeException("I cannot tell you confidence bounds unless you ask me to collect them during the training run");
		return getconfinternal(conf90);
	}

	
	
	public double[][] get95PctConfidenceBounds()
	{
		if(this.conf95 == null)
			throw new RuntimeException("I cannot tell you confidence bounds unless you ask me to collect them during the training run");
		return getconfinternal(conf95);
	}

	public double[][] get75PctConfidenceBounds()
	{
		if(this.conf75 == null)
			throw new RuntimeException("I cannot tell you confidence bounds unless you ask me to collect them during the training run");
		return getconfinternal(conf75);
	}
	

	
	private static double[][] getconfinternal(double[][] cbounds)
	{
		double[][] result = new double[cbounds.length][2];
		for(int i = 0; i < cbounds.length; i++)
		{
			if(cbounds[i] == null)
				result[i] = null;
			else
			{
				result[i][0] = cbounds[i][0];
				result[i][1] = cbounds[i][1];
			}
		}
		return result;
	}
	
	
	public void compress()
	{
		if(this.estimateConfidenceBounds)
			throw new RuntimeException("Cannot compress tree that you have specified to estimate confidence bounds for");
		for(int i = 0; i < this.forest.length; i++)
			forest[i].compress();
	}
	
	
	
	
	
	private static void usage()
	{
		System.err.println("usage is:");
		System.err.println("");
		System.err.println("java RandomForestRegression [args]");
		System.err.println("where:");
		System.err.println("");
		System.err.println("args[0] == datafile");
		System.err.println("args[1] == forest size");
		System.err.println("args[2] == terminal node size");
		System.err.println("args[3] == 'bootstrap' or 'normal'");
		System.err.println("args[4] == 'confbounds' or 'nobounds'    (whether or not to estimate bounds)");
		System.err.println("");
	}
	
		
	public static void main(String[] args) throws Exception
	{
		Debug.setVerbosityLevel(Debug.INFO);
		
		if(args[0].equalsIgnoreCase("predfromfile")) {
			pred(args);
			System.exit(0);
		}
		
		if(args.length != 5) {
			usage();
			System.exit(-1);
		}
		
		
		Debug.println("Reading data file", Debug.INFO);
		double[][] datavects = FileUtil.readVectorsFromFile(new File(args[0]));
		Debug.println("Finished reading data file", Debug.INFO);
		int forestsize = Integer.parseInt(args[1]);
		int terminalnodesize = Integer.parseInt(args[2]);
		boolean bootstrap = false;
		
		if(args[3].equalsIgnoreCase("bootstrap"))
			bootstrap = true;
		else if(!args[3].equalsIgnoreCase("normal"))
			throw new RuntimeException("argument 4 must be either 'bootstrap' or 'normal'");
		

		boolean estBounds = false;
		if(args[4].equalsIgnoreCase("confbounds"))
			estBounds = true;
		else if(!args[4].equalsIgnoreCase("nobounds"))
			throw new RuntimeException();
		
		Datum[] data = new Datum[datavects.length];
		for(int i = 0; i < data.length; i++)
			data[i] = new Datum(datavects[i]);
		
		Debug.println("Starting regression", Debug.INFO);

		RandomForestRegression rf = new RandomForestRegression(terminalnodesize,
				RegressionTree.LOG2_IN_DATA_SPLITFUNCTION,
				0.5, forestsize, estBounds, bootstrap, 2.0);
		
		
		
		rf.train(data);
		
		double errsum =0.0;
		double errsum2 = 0.0;
		for(int i = 0; i < data.length; i++)
		{
			String bounds = "notestimated";
			if(rf.conf75 != null) bounds = rf.conf95[i][0]+" "+
			                rf.conf90[i][0]+" "+
			                rf.conf75[i][0]+" "+
			                rf.conf75[i][1]+" "+
			                rf.conf90[i][1]+" "+
			                rf.conf95[i][1];
			
			double medianpred = rf.getMedianValue(data[i]);			
			
			double predv = rf.getValue(data[i]);
			double realv = data[i].getValue();
			System.out.println(data[i].toSimpleWhiteSpaceDelimitedParameterString()+" MEANPRED: "+predv+"  MEDIANPRED: "+medianpred+"  ACTUAL: "+realv+"  BOUNDS: "+bounds);
			double err = Math.abs(predv-realv);
			double mederr = Math.abs(medianpred-realv);
			errsum += err;
			errsum2 += mederr;
		}
		System.out.println("avg error is "+errsum/data.length);
		System.out.println("avg median error is "+errsum2/data.length);
		
		
		
		ObjectOutputStream objout = new ObjectOutputStream(new FileOutputStream("rf.obj"));
		objout.writeObject(rf);
		objout.close();
	}
	
	
	
	public static void pred(String[] args) throws Exception
	{
		RandomForestRegression rf = readFromFile(args[1]); //read the forest
		
		Datum[] data = Datum.readFromFile(args[2]); //get training data
		
		//now get error on data
		double errtot = 0.0;
		for(int i = 0; i < data.length; i++)
		{
			double pred = rf.getValue(data[i]);
			double actual = data[i].getValue();
			errtot += Math.abs(pred-actual);
		}
		
		System.out.println("Mean absolute error is "+errtot/data.length);
	}
	
	
	public static RandomForestRegression readFromFile(String filename)
	throws Exception
	{
		java.io.ObjectInputStream objin  = new java.io.ObjectInputStream(new FileInputStream(filename));
		RandomForestRegression rf = (RandomForestRegression) objin.readObject();
		objin.close();
		return rf;
	}
	
	
	public static void serializationTest(String[] args) throws Exception
	{
		Datum[] data = new Datum[100];
		for(int i =0; i < data.length; i++)
		{
			double d1 = Math.random();
			double d2 = Math.random();
			double res = d1+d2+(Math.random()-0.5)/2;
			data[i] = new Datum(new double[] {d1, d2, res});
		}
		
		RandomForestRegression rf = new RandomForestRegression(1, RegressionTree.CONSTANT_SPLITFUNCTION, 1.0, 50, true, true, 1.0);
		rf.train(data);
		double errsum =0.0;
		for(int i = 0; i < data.length; i++)
		{
			double predv = rf.getValue(data[i]);
			double realv = data[i].getValue();
			double err = Math.abs(predv-realv);
			errsum += err;
		}
		System.out.println("avg error is "+errsum/data.length);
		
	}



}


