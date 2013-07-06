package rses.apps;


import java.io.File;
import java.util.Random;

import rses.Debug;
import rses.math.MathUtil;
import rses.regression.Datum;
import rses.regression.kriging.Kriging;
import rses.regression.randomforest.RandomForest2DInterpolation;
import rses.regression.randomforest.RandomForestRegression;
import rses.regression.randomforest.RegressionTree;
import rses.util.FileUtil;
import rses.util.Util;





public class SyntheticSpatialInterpolaterComparison
{
	//cant instantiate this class
	private SyntheticSpatialInterpolaterComparison()
	{	throw new UnsupportedOperationException(); }
	
	
	
	
	
	private static void help()
	{
		System.err.println("Usage:");
		System.err.println("");
		System.err.println("arg1: file with seed data set");
		System.err.println("arg2: # new axes for random forest");
		System.err.println("arg3: # trees in random forest");
		System.err.println("arg4: # split tries multiplier (log_2 function used)");
		System.err.println("arg5: rstep for kriging variogram analysis");
		System.err.println("arg6: # test data points");
		System.err.println("arg7: error on target variable");
		System.err.println("arg8: error on lat variable");
		System.err.println("arg9: error on long variable");
		System.err.println("arg10: # of data points in synthetic data set");
		System.err.println("arg11: type of noise 0=gauss 1=uniform 2=exp 3=mixed");
		System.err.println("");
	}
	
	public static void main(String[] args) throws Exception
	{
		int MAX_KRIG_POINTS=500;
		boolean krigsynthetic=true; //use either krig or IDW as synthetic data generator
		
		Debug.setVerbosityLevel(Debug.INFO);
		if(args.length != 11) {
			help();
			System.exit(1);
		}
		
		double[][] vects = FileUtil.readVectorsFromFile(new File(args[0]));
		int nnaxes = Integer.parseInt(args[1]);
		int ntrees = Integer.parseInt(args[2]);
		double splitconst = Double.parseDouble(args[3]);
		double rstep = Double.parseDouble(args[4]);
		int ntest = Integer.parseInt(args[5]);
		double targetgauss = Double.parseDouble(args[6]);
		double latgauss = Double.parseDouble(args[7]);
		double longgauss = Double.parseDouble(args[8]);
		int nsynthetic = Integer.parseInt(args[9]); //#training data to generate
		int noisetype = Integer.parseInt(args[10]);
		
		Random rand = new Random();
		
		//work out the minimum and maximum lat/long from the
		//seed data
		double minlat = Double.POSITIVE_INFINITY;
		double maxlat = Double.NEGATIVE_INFINITY;
		double minlon = Double.POSITIVE_INFINITY;
		double maxlon = Double.NEGATIVE_INFINITY;
		for(int i =0; i < vects.length; i++)
		{
			minlat = Math.min(minlat, vects[i][0]);
			maxlat = Math.max(maxlat, vects[i][0]);
			minlon = Math.min(minlon, vects[i][1]);
			maxlon = Math.max(maxlon, vects[i][1]);
		}
		
		
		//first of all we build a krigger based on the 
		//noise free seed data. We then produce noisy
		//synthetic data for training, and noiseless
		//synthetic data for testing
		Datum[] seeddata = new Datum[vects.length];
		for(int i =0; i < seeddata.length; i++)
			seeddata[i] = new Datum(vects[i]);
		
		IDW idw = null;
		Kriging kr = null;
		vects = new double[nsynthetic][];
		if(krigsynthetic)
		{
			//build krigger based on noiseless seed data
			double[] rs = Kriging.estimateRadiusAndSill(seeddata, rstep, 2.0);
			kr = new Kriging(seeddata, rs[0], rs[1], MAX_KRIG_POINTS);
			//now use krigger to produce noisy training data
			for(int i =0; i < nsynthetic; i++)
			{
				double lat = minlat+Math.random()*(maxlat-minlat);
				double lon = minlon+Math.random()*(maxlon-minlon);
				double[] arr = new double[] {lat, lon, 0.0};
				double val = kr.getPrediction(new Datum(arr));
				if(noisetype == 0) //gaussian
					vects[i] = new double[] {lat+rand.nextGaussian()*latgauss,
						lon+rand.nextGaussian()*longgauss,
						val+rand.nextGaussian()*targetgauss};
				else if(noisetype == 1) //uniform
					vects[i] = new double[] {lat+(rand.nextDouble()-0.5)*latgauss,
						lon+(rand.nextDouble()-0.5)*longgauss,
						val+(rand.nextDouble()-0.5)*targetgauss};
				else if(noisetype == 2) //exponential noise
				{
					double laterr = Math.exp(rand.nextDouble()*latgauss)-1;
					double lonerr = Math.exp(rand.nextDouble()*longgauss)-1;
					double valerr = Math.exp(rand.nextDouble()*targetgauss)-1;
					if(rand.nextDouble() < 0.5) laterr *= -1;
					if(rand.nextDouble() < 0.5) lonerr *= -1;
					if(rand.nextDouble() < 0.5) valerr *= -1;
					vects[i] = new double[] {lat+laterr, lon+lonerr, val+valerr}; 
				}
				else if(noisetype == 3) //mixed
				{
					//every 10th data point has exponential error,
					//others have gaussian
				
					if(rand.nextDouble() < 0.1) //exponential error
					{
						double laterr = Math.exp(rand.nextDouble()*latgauss)-1;
						double lonerr = Math.exp(rand.nextDouble()*longgauss)-1;
						double valerr = Math.exp(rand.nextDouble()*targetgauss)-1;
						if(rand.nextDouble() < 0.5) laterr *= -1;
						if(rand.nextDouble() < 0.5) lonerr *= -1;
						if(rand.nextDouble() < 0.5) valerr *= -1;
						vects[i] = new double[] {lat+laterr, lon+lonerr, val+valerr};
					}
					else //gaussian
					{
						vects[i] = new double[] {lat+rand.nextGaussian()*latgauss,
								lon+rand.nextGaussian()*longgauss,
								val+rand.nextGaussian()*targetgauss};
					}	
				
				}
				else
					throw new RuntimeException("Unknown noise type");
			}
		}
		else //do IDW
		{
			idw = new IDW(seeddata);
			for(int i =0; i < vects.length; i++)
			{
				double lat = minlat+Math.random()*(maxlat-minlat);
				double lon = minlon+Math.random()*(maxlon-minlon);
				double[] arr = new double[] {lat, lon, 0.0};
				double val = idw.getPrediction(new Datum(arr));
				if(noisetype == 0) //gaussian
					vects[i] = new double[] {lat+rand.nextGaussian()*latgauss,
						lon+rand.nextGaussian()*longgauss,
						val+rand.nextGaussian()*targetgauss};
				else
					throw new RuntimeException("only gaussian noise handled for IDW synthetic case"); 
			}
		}
		
		
		
		//now we generate ntest noiseless data test points
		double[][] truevects = new double[ntest][];
		for(int i = 0; i < truevects.length; i++)
		{
			double lat = minlat+Math.random()*(maxlat-minlat);
			double lon = minlon+Math.random()*(maxlon-minlon);
			double[] arr = new double[] {lat, lon, 0.0};
			double val;
			if(krigsynthetic) val = kr.getPrediction(new Datum(arr));
			else val = idw.getPrediction(new Datum(arr));
			truevects[i] = new double[] {lat, lon, val};
		}		
		double[][] allvects = new double[vects.length+truevects.length][];
		for(int i =0; i < vects.length; i++) allvects[i] = vects[i];
		for(int i =0; i < ntest; i++) allvects[vects.length+i] = truevects[i];
		
		
		Debug.println("Extending data to new axes", Debug.INFO);
		double[][] extendeddata = RandomForest2DInterpolation.addExtraAxesToData(allvects, nnaxes);
		Datum[] data = new Datum[vects.length];
		Datum[] truedata = new Datum[ntest];
		for(int i =0; i < vects.length; i++)
			data[i] = new Datum(extendeddata[i]);
		for(int i =0; i < ntest; i++)
			truedata[i] = new Datum(extendeddata[vects.length+i]);
		
		
		
		
		//test dumb mean
		double mean = 0.0;
		for(int j = 0; j < data.length; j++)
			mean += data[j].getValue();
		mean /= data.length;
		double dumbmeanerr = 0.0;
		for(int j = 0; j < truedata.length; j++)
		{
			double pred = mean;
			double actual = truedata[j].getValue();
			dumbmeanerr += Math.pow(pred-actual, 2);
		}
		Debug.println("Dumb mean error is "+dumbmeanerr, Debug.IMPORTANT);
		
		
			
		//now we test kriging
		Debug.println("Estimating radius and sill for kriging", Debug.INFO);
		double[] rs = null;
		rs = Kriging.estimateRadiusAndSill(data, rstep, 2.0);

		Debug.println("Estimated radius and sill ("+rs[0]+","+rs[1]+"), building krigger", Debug.INFO);

		Debug.println("NB: limiting kriging to "+MAX_KRIG_POINTS+" closest points", Debug.INFO);
		Kriging krig = new Kriging(data, rs[0], rs[1], MAX_KRIG_POINTS);
		double krigerr = 0.0;
		Debug.println("Built krigger... using to predict true data", Debug.INFO);
		for(int j = 0; j < truedata.length; j++)
		{
			double pred = krig.getPrediction(truedata[j]);
			double actual = truedata[j].getValue();
			krigerr += Math.pow(pred-actual, 2);
		}
		Debug.println("Krig error is "+krigerr, Debug.IMPORTANT);
		
		
		//now we test idw
		Debug.println("Building IDW ", Debug.INFO);
		idw = new IDW(data);
		double idwerr = 0.0;
		for(int j = 0; j < truedata.length; j++)
		{
			double pred = idw.getPrediction(truedata[j]);
			double actual = truedata[j].getValue();
			idwerr += Math.pow(pred-actual, 2);
		}
		Debug.println("IDW error is "+idwerr, Debug.IMPORTANT);
		
		
		//now we test random forest
		Debug.println("Building random forest", Debug.INFO);
		RandomForestRegression forest = new RandomForestRegression(1, RegressionTree.LOG2_IN_DATA_SPLITFUNCTION, splitconst, ntrees, false, true, 1.0);
		forest.train(data);
		Debug.println("Built random forest... using to predict test data", Debug.INFO);
		double rferr = 0.0;
		for(int j = 0; j < truedata.length; j++)
		{
			double pred = forest.getValue(truedata[j]);
			double actual = truedata[j].getValue();
			rferr += Math.pow(pred-actual, 2);
		}
		Debug.println("Random Forest error is "+rferr, Debug.IMPORTANT);
		
		//now we test random forest without bootstrapping
		Debug.println("Building random forest (sans bootstrap)", Debug.INFO);
		forest = new RandomForestRegression(1, RegressionTree.LOG2_IN_DATA_SPLITFUNCTION, splitconst, ntrees, false, false, 1.0);
		forest.train(data);
		Debug.println("Built random forest sans bootstrapping... using to predict test data", Debug.INFO);
		double rf2err = 0.0;
		for(int j = 0; j < truedata.length; j++)
		{
			double pred = forest.getValue(truedata[j]);
			double actual = truedata[j].getValue();
			rf2err += Math.pow(pred-actual, 2);
		}
		Debug.println("Random Forest (sans bootstrap) error is "+rf2err, Debug.IMPORTANT);
			
		//do Thiessen polygon/Voronoi cell/nearest neighbour predicter
		double nnerr = 0.0;
		for(int j =0; j < truedata.length; j++)
		{	
			double pred = getClosest(data, truedata[j]).getValue();
			double actual = truedata[j].getValue();
			nnerr += Math.pow(pred-actual, 2);
		}
		Debug.println("Nearest neighbour error is "+nnerr, Debug.IMPORTANT);
		
		
		Debug.println("Average Dumb mean error is "+dumbmeanerr/truedata.length, Debug.IMPORTANT);
		Debug.println("Average Krig error is "+krigerr/truedata.length, Debug.IMPORTANT);
		Debug.println("Average IDW error is "+idwerr/truedata.length, Debug.IMPORTANT);
		Debug.println("Average RF error is "+rferr/truedata.length, Debug.IMPORTANT);
		Debug.println("Average RF (no bootstrap) error is "+rf2err/truedata.length, Debug.IMPORTANT);
		Debug.println("Average NN error is "+nnerr/truedata.length, Debug.IMPORTANT);
		
	}
	
	private static Datum getClosest(Datum[] data, Datum d)
	{
		double smallestdist = Double.POSITIVE_INFINITY;
		Datum closest = null;
		
		for(int i =0; i < data.length; i++)
		{
			double xdist = data[i].getParameter(0)-d.getParameter(0);
			double ydist = data[i].getParameter(1)-d.getParameter(1);
			double dist = Math.sqrt(xdist*xdist+ydist*ydist);
			if(dist < smallestdist) {
				smallestdist = dist;
				closest = data[i];
			}
		}
		return closest;
	}
	
}

