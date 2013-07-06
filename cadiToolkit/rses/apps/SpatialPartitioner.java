package rses.apps;

import rses.Model;
import rses.math.MathUtil;
import rses.regression.Datum;
import rses.regression.randomforest.RandomForest2DInterpolation;
import rses.regression.randomforest.RegressionTree;
import rses.spatial.util.Axes;
import rses.util.FileUtil;
import rses.util.Util;


/** given data of the form [lat, long, class]
 * build a random forest to predict class based
 * only on lat/long
 *  
 * 
 * 
 * 
 * 
 */
public class SpatialPartitioner 
{
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception 
	{
		
		
		int nodecap = Integer.parseInt(args[1]);
		int ntrees = Integer.parseInt(args[2]);
		int nclass = Integer.parseInt(args[3]);
		int newaxes = Integer.parseInt(args[4]);
		
		double[][] data1 = FileUtil.readVectorsFromFile(new java.io.File(args[0]));
		//now add extra axes to the data so that its not just north south
		//east west
		Axes axes = RandomForest2DInterpolation.generateBasisVectors(newaxes);
		double[][] data = RandomForest2DInterpolation.addExtraAxesToData(data1, axes);

		
		RegressionTree[] forest = new RegressionTree[ntrees];
		for(int i = 0; i < forest.length; i++) 
		{
			//get training data
			Object[] bootstrap = Util.getBootstrapSample(data, data.length);
			Datum[] trainingdata = new Datum[bootstrap.length];
			for(int j = 0; j < bootstrap.length; j++)
				trainingdata[j] = new Datum((double[]) bootstrap[j]);

			//train tree on data
			forest[i] = new RegressionTree(RegressionTree.LOG2_IN_DATA_SPLITFUNCTION, 1.0);
			forest[i].train(trainingdata, nodecap, -nclass);
			
			//compress tree to save memory
			forest[i].compress();
		}
		
		//ok, now we have the forest, print the results
		//
		//the results of interest are the boundaries
		//of the trees in the forest, and the 
		//class distribution/prediction for each area
		//print out all the boundary points
		for(int i = 0; i < forest.length; i++)
		{
			//TODO;
		}
		
		


	}

}
