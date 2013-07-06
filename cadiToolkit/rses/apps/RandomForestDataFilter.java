package rses.apps;

import java.util.ArrayList;
import java.util.Arrays;

import rses.Debug;
import rses.regression.Datum;
import rses.regression.randomforest.RandomForest2DInterpolation;
import rses.regression.randomforest.RegressionTree;
import rses.spatial.util.Axes;
import rses.util.FileUtil;
import rses.util.Util;




public class RandomForestDataFilter
{
	private RandomForestDataFilter() {}
	
	
	
	
	public static Datum[] filterData(Datum[] data, double conf, int terminalNodeSize, double errorNorm, int numtrees)
	{
		if(terminalNodeSize != 1)
			throw new RuntimeException("This routine kind of assumes that you use a terminal node capacity of 1 and that all values are unique. Otherwise, it doesnt work");
		
		RegressionTree[] forest = new RegressionTree[numtrees];
		
		//first we build the forest
		for(int i = 0; i < forest.length; i++)
		{
			Object[] bso = Util.getBootstrapSample(data, data.length)[0];
			ArrayList train = new ArrayList(data.length);
			for(int j = 0; j < data.length; j++)
				train.add(bso[j]);
			forest[i] = new RegressionTree(RegressionTree.LOG2_IN_DATA_SPLITFUNCTION, 1.0);
			Debug.print("training tree "+(i+1)+"..", Debug.INFO);
			forest[i].train(train, terminalNodeSize, errorNorm);
			Debug.print("...tree built... now compressing it...", Debug.INFO);
			forest[i].compress();
			Debug.println("... tree compressed", Debug.INFO);
		}
		
		ArrayList okdata = new ArrayList();
		
		//now we work out confidence bounds
		for(int i =0; i < data.length; i++)
		{
			Debug.println("working out confidence bounds for datum "+(i+1), Debug.INFO);
			ArrayList errors = new ArrayList(data.length/2);
			for(int j =0; j < forest.length; j++)
			{
				double pred = forest[j].getValue(data[i]);
				double actual = data[i].getValue();
				if(pred == actual) //perfect fit
					;
				else 
					errors.add(new Double(pred-actual));
			}
			
			double[] errs = new double[errors.size()];
			for(int j =0; j < errs.length; j++)
				errs[j] = ((Double) errors.get(j)).doubleValue();
			
			Arrays.sort(errs);
			//now work out confidence bounds
			double tails = (1.0-conf)/2;
			int lwr = (int) Math.round(tails*errs.length);
			int upr = (int) Math.round((1.0-tails)*errs.length);
			if(upr == errs.length) upr--;
			//System.out.println("lwr/upr bounds on error for datum "+i+" are "+errs[lwr]+"/"+errs[upr]);
			if(errs[lwr] > 0 || errs[upr] < 0) 
			{
				double lat = data[i].getParameter(0);
				double lon = data[i].getParameter(1);
				double val = data[i].getValue();
				Debug.println("excluding datum "+(i+1)+" (lat/long/val) ("+lat+"/"+lon+"/"+val+") as it looks a bit dodgy", Debug.INFO);
			}
			else
				okdata.add(data[i]);
		}

		Datum[] res = new Datum[okdata.size()];
		for(int i =0; i < res.length; i++)
			res[i] = (Datum) okdata.get(i);
		
		return res;
	}
	
	
	public static void usage()
	{
		System.err.println("");
		System.err.println("Usage is:");
		System.err.println("");
		System.err.println("arg1: datafile");
		System.err.println("arg2: number of new axes");
		System.err.println("arg3: filter level. Must be < 1.0. ");
		System.err.println("arg4: # trees in forest");
	}
	
	
	public static void main(String[] args) throws Exception
	{
		Debug.setVerbosityLevel(Debug.INFO);
		
		if(args.length != 4)
			usage();
		
		
		String datafile = args[0];
		int newAxes = Integer.parseInt(args[1]);
		double conf = Double.parseDouble(args[2]);	
		int treesInForest = Integer.parseInt(args[3]);
		
		//extract the data
		double[][] data = FileUtil.readVectorsFromFile(new java.io.File(datafile));
	
		//add extra axes
		Axes axes = RandomForest2DInterpolation.generateBasisVectors(newAxes);
		double[][] newdata = RandomForest2DInterpolation.addExtraAxesToData(data, axes);
		for(int i =0; i < newdata.length; i++)
			Debug.println(Util.arrayToString(newdata[i]), Debug.INFO);
		
		
		
		Datum[] datums = new Datum[newdata.length];
		for(int i =0; i < datums.length; i++)
			datums[i] = new Datum(newdata[i]);
		
		if(conf < 1.0)
			datums = RandomForestDataFilter.filterData(datums, conf, 1, 1.0, treesInForest);
		
		
		for(int i = 0; i < datums.length; i++)
		{
			double lat = datums[i].getParameter(0);
			double lon = datums[i].getParameter(1);
			double val = datums[i].getValue();
			Debug.println("FILTERED_DATA: "+lat+" "+lon+" "+val, Debug.IMPORTANT);
		}
	}
}