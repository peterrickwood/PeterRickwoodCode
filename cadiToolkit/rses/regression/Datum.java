package rses.regression;
import java.io.Serializable;

import rses.*;
import rses.util.Util;



/** A Datum is like a rses.Model, but not quite.
 *
 */
public class Datum implements Serializable
{
	protected double[] params;
	protected double value;

	
	public Datum(double[] paramsandvalue)
	{
		this.params = new double[paramsandvalue.length-1];
		for(int i =0; i < params.length; i++)
			params[i] = paramsandvalue[i];
		this.value = paramsandvalue[paramsandvalue.length-1];
	}

	public Datum(double[] params, double value)
	{
		this.params = Util.copy(params);
		this.value = value;
	}


	public int getNumParameters()
	{
		return this.params.length;
	}


	public double[] getParameters()
	{
		return params;
	}

	public double getParameter(int i)
	{
		return params[i];
	}

	public double getValue()
	{
		return value;
	}


	public String toSimpleWhiteSpaceDelimitedParameterString()
	{
		String res = "";
		for(int i= 0; i < params.length; i++)
			res = res + params[i]+" ";
		return res;
	}
	
	
	public static Datum[] readFromFile(String filename)
	throws java.io.IOException
	{
		double[][] datavects = rses.util.FileUtil.readVectorsFromFile(new java.io.File(filename));
		Datum[] result = new Datum[datavects.length];
		for(int i = 0; i < result.length; i++)
			result[i] = new Datum(datavects[i]);
		return result;
	}
	

}











