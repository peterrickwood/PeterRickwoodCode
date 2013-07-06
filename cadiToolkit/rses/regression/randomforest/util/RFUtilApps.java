package rses.regression.randomforest.util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import rses.Debug;
import rses.regression.Datum;
import rses.regression.randomforest.RandomForestRegression;
import rses.regression.randomforest.RegressionTree;
import rses.util.FileUtil;
import rses.util.Util;








public final class RFUtilApps
{
	private RFUtilApps() {
		throw new UnsupportedOperationException("Cant instantiate this class");
	}
	
	
	//generate a random forest classifier or regression tree
	//and then save it to file for later use
	//
	//
	//usage:
	//		args[0] == savepredictortofile
	//		args[1] == datafile
	//		args[2] == ntrees
	//		args[3] == nodesize
	//		args[4] == 'bootstrap' or 'nobootstrap'
	//		args[5] == 'regression' or 'classifier'
	//		args[6] == file to save to
	//
	//
	public static void saveToFile(String[] args) throws Exception
	{
		String datafile = args[1];
		int ntrees = Integer.parseInt(args[2]);
		int nodesize = Integer.parseInt(args[3]);
		boolean bootstrap = true;
		if(args[4].equalsIgnoreCase("nobootstrap")) bootstrap = false;
		else if(!args[4].equalsIgnoreCase("bootstrap")) throw new RuntimeException("args[4] must be either 'bootstrap' or 'nobootstrap'");
		
		//convert all data in the data file to the right format
		double[][] datavects = FileUtil.readVectorsFromFile(new java.io.File(datafile));
		Datum[] data = new Datum[datavects.length];
		ArrayList classlabels = new ArrayList();
		for(int i = 0; i < data.length; i++) {
			data[i] = new Datum(datavects[i]);
			Double classlabel = new Double(datavects[i][datavects[i].length-1]);
			if(!classlabels.contains(classlabel))
				classlabels.add(classlabel);
		}
		double[] classes = new double[classlabels.size()];
		for(int i = 0; i < classes.length; i++)
			classes[i] = ((Double) classlabels.get(i)).doubleValue();
		Arrays.sort(classes);
		if(classes[0] != 0.0) throw new RuntimeException("Min class label must beb equal to 0, and its not!");
		
		RandomForestRegression rf;
		if(args[5].equalsIgnoreCase("classifier")) {
			rf = RandomForestRegression.getClassifierInstead(
				nodesize, 
				RegressionTree.LOG2_IN_DATA_SPLITFUNCTION, 0.5, 
				ntrees, 
				bootstrap,  
				classes.length);
		}
		else if(args[5].equalsIgnoreCase("regression")) {
			rf = new RandomForestRegression(nodesize, 
					RegressionTree.LOG2_IN_DATA_SPLITFUNCTION, 0.5,
					ntrees, 
					false, 
					bootstrap, 
					2.0);
		}
		else throw new RuntimeException("args5 must be either 'classifier' or 'regression'");
		
		Debug.println("OK, training Random Forest", Debug.IMPORTANT);
		rf.train(data);
		Debug.println("Finished training random forest. Compressing it before saving it", Debug.IMPORTANT);
		rf.compress();
		Debug.println("Finished compressing. Writing to file "+args[6], Debug.IMPORTANT);
		
		ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(args[6]));
		out.writeObject(rf);
		out.close();
		
	}
	
	public static void predictFromFile(String[] args) throws Exception
	{
		String rffile = args[1];
		String datafile = args[2];
		Debug.println("Reading in Random Forest object....", Debug.IMPORTANT);
		RandomForestRegression rf = (RandomForestRegression) (new ObjectInputStream(new FileInputStream(rffile)).readObject());
		Debug.println("Done.... now reading data file....", Debug.IMPORTANT);
		double[][] vects = FileUtil.readVectorsFromFile(new java.io.File(datafile));
		Debug.println("Done.... now doing predictions", Debug.IMPORTANT);
		
		
		for(int i = 0; i < vects.length; i++) 
		{
			Datum data = new Datum(vects[i]);
			if(rf.isClassifier()) {
				int clazz = rf.getClass(data);
				System.out.println(data.toSimpleWhiteSpaceDelimitedParameterString()+" CLASSDIST: "+Util.arrayToString(rf.getClassDistribution(data))+" ACTUALCLASS: "+data.getValue()+" PREDICTEDCLASS: "+clazz);
			}
			else {
				double pred = rf.getValue(data);
				double medpred = rf.getMedianValue(data);
				System.out.println(data.toSimpleWhiteSpaceDelimitedParameterString()+" ACTUAL: "+data.getValue()+" MEANPRED: "+pred+"  MEDPRED: "+medpred);
			}
		}
	}
	
	public static void main(String[] args) throws Exception
	{
		if(args[0].equalsIgnoreCase("predictFromFile")) {
			predictFromFile(args);
		}
		else if(args[0].equalsIgnoreCase("savePredictorToFile")) {
			saveToFile(args);
		}
	}
	
	
}



