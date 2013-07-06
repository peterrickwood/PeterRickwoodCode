package rses.apps;

import java.io.File;

import rses.Debug;
import rses.math.MathUtil;
import rses.regression.Datum;
import rses.regression.kriging.Kriging;
import rses.regression.randomforest.RandomForest2DInterpolation;
import rses.regression.randomforest.RandomForestRegression;
import rses.regression.randomforest.RegressionTree;
import rses.util.FileUtil;
import rses.util.Util;





public class SpatialInterpolaterComparison
{
	public static final int MAX_KRIG_POINTS=1000;
	

	
	//cant instantiate this class
	private SpatialInterpolaterComparison()
	{	throw new UnsupportedOperationException(); }
	
	
	
	
	
	private static void help()
	{
		System.err.println("Usage:");
		System.err.println("");
		System.err.println("arg1: file with complete data set");
		System.err.println("arg2: # new axes for random forest");
		System.err.println("arg3: # trees in random forest");
		System.err.println("arg4: # split tries multiplier (log_2 function used)");
		System.err.println("arg5: rstep for kriging variogram analysis");
		System.err.println("arg6: # folds");
		System.err.println("");
	}
	
	public static void main(String[] args) throws Exception
	{
		double krignorm = 2; //0.5 or 2
		
		Debug.setVerbosityLevel(Debug.INFO);
		if(args.length != 6) {
			help();
			System.exit(1);
		}
		
		double[][] vects = FileUtil.readVectorsFromFile(new File(args[0]));
		int nnaxes = Integer.parseInt(args[1]);
		int ntrees = Integer.parseInt(args[2]);
		double splitconst = Double.parseDouble(args[3]);
		double rstep = Double.parseDouble(args[4]);
		int folds = Integer.parseInt(args[5]);
		
		Debug.println("Extended data to new axes", Debug.INFO);
		double[][] extendeddata = RandomForest2DInterpolation.addExtraAxesToData(vects, nnaxes);
		Datum[] data = new Datum[vects.length];
		for(int i =0; i < data.length; i++)
			data[i] = new Datum(extendeddata[i]);
		
		

		//now do n-fold cross-validation
		Debug.println("Peforming "+folds+"-fold cross validation", Debug.INFO);
		Object[][][] testtrain = Util.getCrossValidationFolds(data, folds);
		if(testtrain.length != folds) throw new IllegalStateException("Asked for "+folds+" folds but got "+testtrain.length);
		
		double minsill = Double.NaN;
		double maxsill = Double.NaN;
		double minr = Double.NaN;
		double maxr = Double.NaN;
		
		double totalkrigerr = 0.0;
		double totalkriganis1err = 0.0;
		double totalkriganis2err = 0.0;
		double totalrferr = 0.0;
		double totalrf2err = 0.0;
		double totalnnerr = 0.0;
		double totalidwerr = 0.0;
		double totaldumberr = 0.0;
		double totalnn2err = 0.0;
		
		for(int i =0; i < folds; i++)
		{
			Debug.println("In fold "+(i+1), Debug.INFO);
			Object[] traino = testtrain[i][0];
			Object[] testo = testtrain[i][1];
			Datum[] test = new Datum[testo.length];
			Datum[] train = new Datum[traino.length];
			
			
			//print out test and train datasets for
			//each fold, so that we can use other
			//non-caditoolkit-based methods 
			for(int j=0; j < test.length; j++) 
			{
				test[j] = (Datum) testo[j];
				double[] params = test[j].getParameters();
				System.out.print("TEST"+i+" ");
				for(int k=0; k < params.length; k++)
					System.out.print(params[k]+" ");
				System.out.println(test[j].getValue());
			}
			for(int j=0; j < train.length; j++) 
			{
				train[j] = (Datum) traino[j];
				double[] params = train[j].getParameters();
				System.out.print("TRAIN"+i+" ");
				for(int k=0; k < params.length; k++)
					System.out.print(params[k]+" ");
				System.out.println(train[j].getValue());
			}
			
			if(testo.length > traino.length+1)
				throw new RuntimeException("training set smaller than test set");

			
			//first just predict the mean
			double mean = 0.0;
			for(int j=0; j < train.length; j++)
				mean += train[j].getValue();
			mean /= train.length;
			double dumberr = 0.0;
			for(int j = 0; j < test.length; j++)
			{
				double pred = mean;
				double actual = test[j].getValue();
				dumberr += Math.pow(pred-actual, 2);
			}
			Debug.println("Default (dumb mean) error for fold "+(i+1)+" is "+dumberr, Debug.IMPORTANT);
			totaldumberr += dumberr;
			
			
			
			
			//next do IDW
			Debug.println("Building IDW for fold "+(i+1), Debug.INFO);
			IDW idw = new IDW(train);
			double idwerr = 0.0;
			for(int j = 0; j < test.length; j++)
			{
				double pred = idw.getPrediction(test[j]);
				double actual = test[j].getValue();
				idwerr += Math.pow(pred-actual, 2);
			}
			Debug.println("IDW error for fold "+(i+1)+" is "+idwerr, Debug.IMPORTANT);
			totalidwerr += idwerr;

			
			//next do 2-NN
			Debug.println("Building NN2 for fold "+(i+1), Debug.INFO);
			NN2 nn2 = new NN2(train);
			double nn2err = 0.0;
			for(int j = 0; j < test.length; j++)
			{
				double pred = nn2.getPrediction(test[j]);
				double actual = test[j].getValue();
				nn2err += Math.pow(pred-actual, 2);
			}
			Debug.println("NN2 error for fold "+(i+1)+" is "+nn2err, Debug.IMPORTANT);
			totalnn2err += nn2err;
			
			
			
			//now we test kriging
			Debug.println("Estimating radius and sill for kriging", Debug.INFO);
			double[] rs = null;
			if(i == 0) {
				rs = Kriging.estimateRadiusAndSill(train, rstep, krignorm);
				minr = rs[0]*0.5;
				maxr = rs[0]*1.5;
				minsill = rs[1]*0.5;
				maxsill = rs[1]*1.5;
			}
			else {
				rs = Kriging.estimateRadiusAndSill(train, rstep, minr, maxr, minsill, maxsill, krignorm);
			}
			Debug.println("Estimated radius and sill ("+rs[0]+","+rs[1]+"), building krigger", Debug.INFO);
			Debug.println("NB: limiting kriging to "+MAX_KRIG_POINTS+" closest points", Debug.INFO);
			Kriging krig = new Kriging(train, rs[0], rs[1], MAX_KRIG_POINTS);
			double krigerr = 0.0;
			Debug.println("Built krigger... using to predict test data", Debug.INFO);
			for(int j = 0; j < test.length; j++)
			{
				double pred = krig.getPrediction(test[j]);
				double actual = test[j].getValue();
				krigerr += Math.pow(pred-actual, 2);
			}
			Debug.println("Krig error for fold "+(i+1)+" is "+krigerr, Debug.IMPORTANT);
			totalkrigerr += krigerr;

			
			/*
			//now test kriging with N/S anisotropy
			Debug.println("Estimating radius and sill for N/S anisotropic kriging", Debug.INFO);
			Datum[][] transformed = Kriging.estimateRadiusSillWithAnisotropy(train, test, rstep, 0.0, krignorm);
			Datum[] transformedtrain = transformed[0];
			Datum[] transformedtest = transformed[1];
			double[] rs1 = Kriging.estimateRadiusAndSill(transformedtrain, rstep/2, krignorm);
			Debug.println("Estimated N/S anisotropic radius and sill ("+rs1[0]+","+rs1[1]+"), building krigger", Debug.INFO);
			Debug.println("NB: limiting kriging to "+MAX_KRIG_POINTS+" closest points", Debug.INFO);
			Kriging krig1 = new Kriging(transformedtrain, rs1[0], rs1[1], MAX_KRIG_POINTS);
			double krigerr1 = 0.0;
			Debug.println("Built krigger... using to predict test data", Debug.INFO);
			for(int j = 0; j < transformedtest.length; j++)
			{
				double pred = krig1.getPrediction(transformedtest[j]);
				double actual = transformedtest[j].getValue();
				krigerr1 += Math.pow(pred-actual, 2);
			}
			Debug.println("N/S anisotropic Krig error for fold "+(i+1)+" is "+krigerr1, Debug.IMPORTANT);
			totalkriganis1err += krigerr1;
			

			
			//now test kriging with 45 degree anisotropy
			Debug.println("Estimating radius and sill for 45 degree anisotropic kriging", Debug.INFO);
			transformed = Kriging.estimateRadiusSillWithAnisotropy(train, test, rstep, Math.PI/4, krignorm);
			transformedtrain = transformed[0];
			transformedtest = transformed[1];
			double[] rs2 = Kriging.estimateRadiusAndSill(transformedtrain, rstep/2, krignorm);
			Debug.println("Estimated N/S anisotropic radius and sill ("+rs2[0]+","+rs2[1]+"), building krigger", Debug.INFO);
			Debug.println("NB: limiting kriging to "+MAX_KRIG_POINTS+" closest points", Debug.INFO);
			Kriging krig2 = new Kriging(transformedtrain, rs2[0], rs2[1], MAX_KRIG_POINTS);
			double krigerr2 = 0.0;
			Debug.println("Built krigger... using to predict test data", Debug.INFO);
			for(int j = 0; j < transformedtest.length; j++)
			{
				double pred = krig2.getPrediction(transformedtest[j]);
				double actual = transformedtest[j].getValue();
				krigerr2 += Math.pow(pred-actual, 2);
			}
			Debug.println("45 degree anisotropic Krig error for fold "+(i+1)+" is "+krigerr2, Debug.IMPORTANT);
			totalkriganis2err += krigerr2;
			*/
			
			
			//now we test random forest
			Debug.println("Building random forest for fold "+(i+1), Debug.INFO);
			RandomForestRegression forest = new RandomForestRegression(1, RegressionTree.LOG2_IN_DATA_SPLITFUNCTION, splitconst, ntrees, false, true, 2.0);
			forest.train(train);
			Debug.println("Built random forest... using to predict test data", Debug.INFO);
			double rferr = 0.0;
			for(int j = 0; j < test.length; j++)
			{
				double pred = forest.getValue(test[j]);
				double actual = test[j].getValue();
				rferr += Math.pow(pred-actual, 2);
			}
			Debug.println("Random Forest error for fold "+(i+1)+" is "+rferr, Debug.IMPORTANT);
			totalrferr += rferr;

			
			//now we test random forest without bootstrapping
			Debug.println("Building random forest (sans bootstrap) for fold "+(i+1), Debug.INFO);
			forest = new RandomForestRegression(1, RegressionTree.LOG2_IN_DATA_SPLITFUNCTION, splitconst, ntrees, false, false, 2.0);
			forest.train(train);
			Debug.println("Built random forest sans bootstrapping... using to predict test data", Debug.INFO);
			double rf2err = 0.0;
			for(int j = 0; j < test.length; j++)
			{
				double pred = forest.getValue(test[j]);
				double actual = test[j].getValue();
				rf2err += Math.pow(pred-actual, 2);
			}
			Debug.println("Random Forest (sans bootstrap) error for fold "+(i+1)+" is "+rf2err, Debug.IMPORTANT);
			totalrf2err += rf2err;

			
			
			//do Thiessen polygon/Voronoi cell/nearest neighbour predicter
			double nnerr = 0.0;
			for(int j =0; j < test.length; j++)
			{
				double pred = getClosest(train, test[j]).getValue();
				double actual = test[j].getValue();
				nnerr += Math.pow(pred-actual, 2);
			}
			Debug.println("Nearest neighbour error for fold "+(i+1)+" is "+nnerr, Debug.IMPORTANT);
			totalnnerr += nnerr;
		}
		
		Debug.println("Average Dumb Mean error is "+totaldumberr/vects.length, Debug.IMPORTANT);
		Debug.println("Average 2-NN error is "+totalnn2err/vects.length, Debug.IMPORTANT);
		Debug.println("Average Krig error is "+totalkrigerr/vects.length, Debug.IMPORTANT);
		//Debug.println("Average N/S anisotropic Krig error is "+totalkriganis1err/vects.length, Debug.IMPORTANT);
		//Debug.println("Average 45 degree anisotropic Krig error is "+totalkriganis2err/vects.length, Debug.IMPORTANT);
		Debug.println("Average IDW error is "+totalidwerr/vects.length, Debug.IMPORTANT);
		Debug.println("Average RF error is "+totalrferr/vects.length, Debug.IMPORTANT);
		Debug.println("Average RF (no bootstrap) error is "+totalrf2err/vects.length, Debug.IMPORTANT);
		Debug.println("Average Thiessen error is "+totalnnerr/vects.length, Debug.IMPORTANT);
		
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




class NN2
{
	private double[][] trainvals;
	NN2(Datum[] latlongvals)
	{
		this.trainvals = new double[latlongvals.length][3];
		for(int i = 0; i < latlongvals.length; i++)
		{
			trainvals[i][0] = latlongvals[i].getParameter(0);
			trainvals[i][1] = latlongvals[i].getParameter(1);
			trainvals[i][2] = latlongvals[i].getValue();
		}
	}

	public double getPrediction(Datum data)
	{
		double lat = data.getParameter(0);
		double lon = data.getParameter(1);
		
		double mindist = Double.POSITIVE_INFINITY;
		int minindex = -1;
		double nextmin = Double.POSITIVE_INFINITY;
		int nextminindex = -1;
		for(int i = 0; i < trainvals.length; i++)
		{
			double dist = MathUtil.getDistanceBetweenPointsOnEarth(lat, lon, trainvals[i][0], trainvals[i][1]);
			if(dist < mindist)
			{
				nextmin = mindist;
				nextminindex = minindex;
				minindex = i;
				mindist = dist;
			}
			else if(dist < nextmin)
			{
				nextmin = dist;
				nextminindex = i;
			}
		}
		
		double weight1 = nextmin/(nextmin+mindist);
		double weight2 = mindist/(nextmin+mindist);
		
		return weight1*trainvals[minindex][2]+weight2*trainvals[nextminindex][2];
	}
}


class IDW
{
	private double[][] trainvals;
	IDW(Datum[] latlongvals)
	{
		this.trainvals = new double[latlongvals.length][3];
		for(int i = 0; i < latlongvals.length; i++)
		{
			trainvals[i][0] = latlongvals[i].getParameter(0);
			trainvals[i][1] = latlongvals[i].getParameter(1);
			trainvals[i][2] = latlongvals[i].getValue();
		}
	}
	
	
	public double getPrediction(Datum data)
	{
		double lat = data.getParameter(0);
		double lon = data.getParameter(1);
		
		double[] weights = new double[trainvals.length];
		double wsum = 0.0;
		for(int i=0; i < weights.length; i++)
		{
			if(lat == trainvals[i][0] && lon == trainvals[i][1])
				throw new RuntimeException("test data point identical to train data point in IDW .....");
			double dist = MathUtil.getDistanceBetweenPointsOnEarth(lat, lon,
					trainvals[i][0], trainvals[i][1])/1000.0; //distance in km
			weights[i] = 1.0/(dist*dist);
			wsum += weights[i];
		}
		//Util.printarray(weights, System.out);
		for(int i = 0; i < weights.length; i++)
			weights[i] /= wsum;

		
		//now predict
		double pred = 0.0;
		for(int i = 0; i < weights.length; i++)
			pred += weights[i]*trainvals[i][2];
		return pred;
	}
}

