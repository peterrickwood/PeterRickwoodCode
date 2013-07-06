package rses.math;

import java.util.Arrays;

import rses.util.Util;






public class DistributionStatistics
{
	public double sum;
	public double mean;
	public double median = Double.NaN;
	
	
	/** The unbiased estimate of the population stdandard deviation 
	 */
	public double popstddev;
	
	/** The sample standard deviation */
	public double samplestddev;
	public double min;
	public double max;
	
	
	public DistributionStatistics()
	{}
	
	public DistributionStatistics(double[] vals, boolean calculateMedian)
	{
		if(vals.length <= 1)
			throw new IllegalArgumentException("distribution must have more than 1 value");
		sum = 0.0;
		if(calculateMedian)
		{
			double[] valscopy = Util.copy(vals);
			Arrays.sort(valscopy);
			if(vals.length%2 == 0)
				median = (valscopy[vals.length/2-1]+valscopy[vals.length/2])/2;
			else
				median = valscopy[vals.length/2];
		}
		for(int i =0; i < vals.length; i++)
			sum += vals[i];
		mean = sum/vals.length;
		double sumsqdif = 0.0;
		min = Double.POSITIVE_INFINITY;
		max = Double.NEGATIVE_INFINITY;
		for(int i =0; i < vals.length; i++)
		{
			double dif = vals[i]-mean;
			sumsqdif += (dif*dif);
			if(vals[i] < min) min = vals[i];
			if(vals[i] > max) max = vals[i];
		}
		samplestddev = Math.sqrt(sumsqdif/vals.length);
		popstddev = Math.sqrt(sumsqdif/(vals.length-1));
	}
	
	
	
	
}