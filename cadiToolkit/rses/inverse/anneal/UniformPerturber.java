package rses.inverse.anneal;


import rses.inverse.ModelPerturber;


/** A class to perturb a model along a single axis and return the resulting model
 * 
 * @author peter rickwood
 *
 */
public class UniformPerturber implements ModelPerturber, java.io.Serializable
{
	private double[][] bounds; 
	
	public UniformPerturber(double[][] bounds)
	{	
		this.bounds = bounds;
	}
	
	public double[] getPerturbedModel(double[] orig)
	{
		int dim = orig.length;
		double[] result = new double[dim];
		System.arraycopy(orig, 0, result, 0, dim);
		int tochange = (int) (Math.random()*dim);

		double upper = bounds[tochange][1];
		double lower = bounds[tochange][0];
	
		result[tochange] = lower+Math.random()*(upper-lower);
			
		return result;
	}


}



