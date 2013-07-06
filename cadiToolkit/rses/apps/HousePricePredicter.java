package rses.apps;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;


import rses.Debug;
import rses.math.MathUtil;
import rses.regression.Datum;
import rses.regression.randomforest.RegressionTree;
import rses.util.FileUtil;
import rses.util.Util;





public class HousePricePredicter
{
	public static final boolean filter = false;
	public static final boolean savememory = false;
	public static final int NEWAXES = 10;
	public static final int NUMTREES = 20;
	public static final int TERMINALNODESIZE = 256;
	public static final double ERRORNORM = 1.0;
	public static final double CONF = 0.95;
	
	
	private HousePricePredicter() {}
	
	
	
	static Datum[] readData() throws Exception
	{
		double[][] data = FileUtil.readVectorsFromFile(new java.io.File("rf.dat"));
		if(data[0].length != 4) throw new RuntimeException("we assume data has only lat, long, time, and price");
		//add a small error to each lat/long coord and each price,
		//to make sure we get trees with single leaf nodes
		Random rand = new Random();
		for(int i =0; i < data.length; i++)
		{
			data[i][0] += rand.nextGaussian()*0.000001;
			data[i][1] += rand.nextGaussian()*0.000001;
			data[i][2] += rand.nextGaussian()*0.0001; //~1 hour shift
			data[i][3] += rand.nextGaussian()*1; //~$1 shift
		}
		
		
		Datum[] result = new Datum[data.length];
		
		double[][][] newaxes = new double[NEWAXES][][];
		for(int i =0; i < newaxes.length; i++)
			newaxes[i] = MathUtil.generateRandom2DVector();
		
		for(int i =0; i < result.length; i++)
		{
			if(savememory)
				result[i] = new SmallMemDatum(data[i], newaxes);
			else
				result[i] = getDatum(data[i], newaxes);
			data[i] = null; //save memory
			
			if(i % 10000 == 9999) System.gc(); //try and reclaim memory
		}
		
		return result;
	}
	
	
	
	static Datum getDatum(double[] paramsandval, double[][][] newaxes)
	{
		double[] res = new double[paramsandval.length+newaxes.length*3-1];
		res[0] = paramsandval[0];
		res[1] = paramsandval[1];
		res[2] = paramsandval[2];
		for(int i =0; i < newaxes.length; i++)
		{
			double[] nd = MathUtil.expressInNewCoordinateSystem(res[0], res[1], newaxes[i][0], newaxes[i][1]);
			res[3+2*i] = nd[0];
			res[4+2*i] = nd[1];
		}
		
		//boost up the time dimension so that we split on it reasonably
		//often. Otherwise we always split on lat long or some realigned
		//version of that
		for(int i = 0; i < newaxes.length; i++)
			res[paramsandval.length-1+newaxes.length*2+i] = res[2];

		return new Datum(res, paramsandval[3]);
	}
	

	

	
	public static void main(String[] args) throws Exception
	{
		//Debug.setVerbosityLevel(Debug.MAX_VERBOSITY);
		if(args.length > 0)
		{
			double startyear = 1980;
			double endyear = 2006;
			double yearstep = 1.0/12; //monthly steps
			double startlat = -34.5;
			double endlat = -33;
			double latstep = 0.001;
			double startlon = 150.12;
			double endlon = 151.72;
			double lonstep = 0.001;
			
			//read in the forest
			ObjectInputStream ois = new ObjectInputStream(new java.io.FileInputStream("forest.obj"));
			RegressionTree[] forest = new RegressionTree[NUMTREES];
			for(int i =0; i < forest.length; i++)
				forest[i] = (RegressionTree) ois.readObject();
			ois.close();
			
			//now use it to create data files for each time period
			
		}
		else
			generateForest();
	}
	
	
	static void generateForest() throws Exception
	{	
		System.out.println("extracting data... I'll tell you when I'm done");
		Datum[] data = readData();
		System.out.println("extracted data");
		
		
		//get rid of wierd data
		if(filter) {
			data = RandomForestDataFilter.filterData(data, CONF, TERMINALNODESIZE, 1.0, NUMTREES);
			System.gc();
		}
		
		//now we train with the possibly filtered data
		RegressionTree[] forest = new RegressionTree[NUMTREES];
		for(int i =0; i < forest.length; i++)
		{
			Object[] bso = Util.getBootstrapSample(data, data.length)[0];
			ArrayList train = new ArrayList(data.length);
			for(int j = 0; j < data.length; j++)
				train.add(bso[j]);
			forest[i] = new RegressionTree(RegressionTree.LOG2_IN_DATA_SPLITFUNCTION, 1.0);
			System.out.println("training tree "+(i+1));
			forest[i].train(train, TERMINALNODESIZE, ERRORNORM);
			forest[i].compress();
			System.out.println("built forest proper tree "+(i+1));
			System.out.println();
			System.out.println();
		}
		
		
		//now save the forest to file
		System.out.println("saving forest to file");
		ObjectOutputStream oos = new ObjectOutputStream(new java.io.FileOutputStream("forest.obj"));
		for(int i =0; i < forest.length; i++)
			oos.writeObject(forest[i]);
		oos.close();
		System.out.println("finished saving");
		
	}
}




class SmallMemDatum extends Datum
{
	private double[][][] extensionaxes = null;
	
	SmallMemDatum(double[] paramsandval, double[][][] extensionaxes)
	{
		super(paramsandval);
		if(paramsandval.length != 4) throw new RuntimeException("this class is specifically designed for lat/long/time/price data points");
		this.extensionaxes = extensionaxes;
	}
	
	public int getNumParameters()
	{
		return this.getParameters().length+extensionaxes.length*3;
	}
	
	public double getParameter(int index)
	{
		if(index < 2) {
			return super.getParameter(index);
		}
		else if(index < 2*(extensionaxes.length+1)) { //its a lat/long param
			double[] axis1 = this.extensionaxes[index/2][0];
			double[] axis2 = this.extensionaxes[index/2][1];
			double origx = super.getParameter(0);
			double origy = super.getParameter(1);
			double[] res = MathUtil.expressInNewCoordinateSystem(origx, origy, axis1, axis2);
			return res[index%2];
		}
		else {
			return super.getParameter(2);
		}
	}
	
}

