package rses.math;

import java.util.Random;

import rses.Debug;
import rses.Model;
import rses.ModelGenerator;




public class DeltaConstrainedPrior extends ModelGenerator
{
	private double deltaMax;
	private double[][] bounds;
	private double[][] priors;
	private Random rand; 

	public DeltaConstrainedPrior(double deltaMax, double[][] priors, double[][] bounds, Random rand)
	{
		this.deltaMax = deltaMax;
		this.bounds = bounds;
		this.priors = priors;
		this.rand = rand;
	}


	protected Model generateModel()
	{
		int[] path = new int[bounds.length];
		double[] params = new double[bounds.length];
		
		double binsize = (bounds[0][1] - bounds[0][0]) / priors[0].length;
		path[0] = rses.util.Util.getCummulativeIndex(priors[0], rand.nextDouble());
		params[0] = bounds[0][0] + path[0]*binsize+binsize/2;
		
		for(int i = 1; i < path.length; i++)
		{
			double[] pdf = new double[priors[i].length];
			binsize = (bounds[i][1] - bounds[i][0]) / priors[i].length; 
			for(int j = 0; j < pdf.length; j++)
			{
				int bindif = Math.abs(path[i-1]-j);
				if(bindif*binsize < deltaMax) //it's reachable
					pdf[j] = priors[i][j];
			}
			rses.util.Util.normalize(pdf);
			path[i] = rses.util.Util.getCummulativeIndex(pdf, rand.nextDouble());
			params[i] = bounds[i][0] + path[i]*binsize+binsize/2;
		}
		return new Model(params);
	}
	
	
	
	
	
	public static double[][] getPriorDensity(double[][] parambounds, double gradientChange, 
																			int numBins)
	{
		double[][] left = new double[parambounds.length][];
		double[][] right = new double[parambounds.length][];
		
		int numbins = numBins;
		for(int i =0; i < left.length; i++) {
			left[i] = new double[numbins];
			right[i] = new double[numbins];
		}
		
		//initialize first parameter nodes to weight 1
		rses.util.Util.normalize(left[0]);
		rses.util.Util.normalize(right[right.length-1]);
		
		//now work out paths from left to right
		for(int param = 0; param < left.length-1; param++)
		{ 
			double paramwidth = parambounds[param][1]-parambounds[param][0];
			double binwidth = paramwidth/left[param].length;
			double toparamwidth = parambounds[param+1][1]-parambounds[param+1][0]; 
			double tobinwidth = toparamwidth/left[param+1].length;
			 
			for(int from = 0; from < left[param].length; from++)
			{
				double fromval = parambounds[param][0]+(from+1)*(binwidth) - 0.5*binwidth;
				if(left[param].length == 1)
					fromval = (parambounds[param][1]-parambounds[param][0])/2;
				
				for(int to = 0; to < left[param+1].length; to++)
				{
					double toval = parambounds[param+1][0]+(to+1)*(tobinwidth) - 0.5*tobinwidth;
					if(left[param+1].length == 1)
						toval = (parambounds[param+1][1]-parambounds[param+1][0])/2;
						
					//now lets work out if we can get from 'from' to 'to'
					if(toval <= fromval + gradientChange && toval >= fromval - gradientChange)
						left[param+1][to] += left[param][from];  
				}
			}
			//now lets normalize the layer we have just done
			rses.util.Util.normalize(left[param+1]);
		}
		
		//now work out paths from right to left
		for(int param = right.length-1; param > 0; param--)
		{ 
			double paramwidth = parambounds[param][1]-parambounds[param][0];
			double binwidth = paramwidth/right[param].length;
			double toparamwidth = parambounds[param-1][1]-parambounds[param-1][0]; 
			double tobinwidth = toparamwidth/right[param-1].length;
			 
			for(int from = 0; from < right[param].length; from++)
			{
				double fromval = parambounds[param][0]+(from+1)*(binwidth) - 0.5*binwidth;
				if(right[param].length == 1)
					fromval = (parambounds[param][1]-parambounds[param][0])/2;
				
				for(int to = 0; to < right[param-1].length; to++)
				{
					double toval = parambounds[param-1][0]+(to+1)*(tobinwidth) - 0.5*tobinwidth;
					if(right[param-1].length == 1)
						toval = (parambounds[param-1][1]-parambounds[param-1][0])/2;
						
					//now lets work out if we can get from 'from' to 'to'
					if(toval <= fromval + gradientChange && toval >= fromval - gradientChange)
						right[param-1][to] += right[param][from];  
				}
			}
			//now lets normalize the layer we have just done
			rses.util.Util.normalize(right[param-1]);
		}
		
		
		double[][] result = new double[parambounds.length][];
		for(int i =0; i < result.length; i++) 
		{
			result[i] = new double[right[i].length];
			for(int j = 0; j < result[i].length; j++)
			{
				result[i][j] = left[i][j]*right[i][j];
			}
			rses.util.Util.normalize(result[i]);
			
			Debug.print("Left ", Debug.EXTRA_INFO);
			Debug.println(rses.util.Util.arrayToString(left[i]), Debug.EXTRA_INFO);
			Debug.println("Right ", Debug.EXTRA_INFO);
			Debug.println(rses.util.Util.arrayToString(right[i]), Debug.EXTRA_INFO);
			Debug.print("Total ", Debug.EXTRA_INFO);
			Debug.println(rses.util.Util.arrayToString(result[i]), Debug.EXTRA_INFO);
		}			
		
		
		return result;
	}

	
}
