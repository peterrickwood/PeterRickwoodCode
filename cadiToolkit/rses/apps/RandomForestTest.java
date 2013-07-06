package rses.apps;

import rses.regression.Datum;
import rses.regression.randomforest.RandomForest2DInterpolation;
import rses.regression.randomforest.RandomForestRegression;
import rses.regression.randomforest.RegressionTree;
import rses.spatial.util.Axes;
import rses.util.FileUtil;








public abstract class RandomForestTest
{
	private RandomForestTest() {}
	
	
	public static void main(String[] args) throws Exception
	{
		double[][] vectors = FileUtil.readVectorsFromFile(new java.io.File(args[0]));
		Axes a = RandomForest2DInterpolation.generateBasisVectors(500);
		double[][] expanded = RandomForest2DInterpolation.addExtraAxesToData(vectors, a);
		int ntrees = Integer.parseInt(args[1]);
		Datum[] data = new Datum[vectors.length];
		for(int i =0; i < data.length; i++)
			data[i] = new Datum(expanded[i]);
		
		
		RandomForestRegression rf = new RandomForestRegression(1,
				RegressionTree.CONSTANT_SPLITFUNCTION, 1.0, ntrees, false, true, 1.0);
		rf.train(data);
		
		for(double x = -0.5; x < 0.5; x+=0.025)
		{
			for(double y=0.0; y < 0.87; y+=0.025)
			{
				double[] vect = new double[] {x, y, 0.0};
				double[] nv = RandomForest2DInterpolation.expandToNewAxes(vect, a.axes1, a.axes2);
				double val = rf.getValue(new Datum(nv));
				System.out.println(x+" "+y+"  "+val);
			}
		}
	}
}