package rses.inverse.util;

import rses.inverse.UserFunctionHandle;



/** A 'fake' UserFunctionHandle that can be used to create
 *  an impotent functionhandle that knows bounds and dimension
 *  but cannot calculate misfits or priors.
 * 
 * @author peterr
 */
public class FakeUserFunctionHandle implements rses.inverse.UserFunctionHandle
{
	private int numDimensions;
	private double[][] bounds;
	private String[] paramNames;
	
	
	public int getDimensionOfModelSpace()
	{
		return numDimensions;
	}
	
	public double[][] getBoundsOnModelSpace()
	{
		return bounds;
	}
		
	public double getErrorForModel(double[] model)
	{
		throw new UnsupportedOperationException();
	}
	
	public double getPriorForModel(double[] model)
	{
		throw new UnsupportedOperationException();
	}
	
	
	public FakeUserFunctionHandle(int nd, double[][] bounds)
	{
		this.numDimensions = nd;
		this.bounds = bounds;
		this.paramNames = new String[nd];
		for(int i =0; i < nd; i++)
			paramNames[i] = "parameter "+(i+1);
	}
	
	
	public static FakeUserFunctionHandle read(java.io.BufferedReader rdr) throws java.io.IOException
	{
		//read in dimension of model space
		String line = rdr.readLine();
		String[] words = rses.util.Util.getWords(line);
		if(!words[0].equals("NumDimensions:"))
			throw new RuntimeException("Unexpected Token - "+words[0]);
		int nd = Integer.parseInt(words[1]);
		
		//read in bounds
		double[][] bounds = new double[nd][2];
		for(int i =0; i < bounds.length; i++)
		{
			line = rdr.readLine();
			words = rses.util.Util.getWords(line);
			if(!words[0].equals("bounds["+(i+1)+"]:"))
				throw new RuntimeException("Unexpected token: "+words[0]);
			bounds[i][0] = Double.parseDouble(words[1]);
			bounds[i][1] = Double.parseDouble(words[2]);
		}
		//read in scales
		//double[] scales = new double[nd];
		//for(int i =0; i < scales.length; i++)
		//{
		//	line = rdr.readLine();
		//	words = rses.util.Util.getWords(line);
		//	if(!words[0].equals("scales["+(i+1)+"]:"))
		//		throw new RuntimeException("Unexpected token: "+words[0]);
		//	scales[i] = Double.parseDouble(words[1]);
		//}
		
		while(line.indexOf("BEGIN MODELS") < 0)
			line = rdr.readLine();
		
		return new FakeUserFunctionHandle(nd, bounds);
	}
	
	
	public static void write(UserFunctionHandle h, java.io.PrintWriter file) throws java.io.IOException
	{
		file.println("NumDimensions: "+h.getDimensionOfModelSpace());
		double[][] bounds = h.getBoundsOnModelSpace();
		for(int i = 0; i < h.getDimensionOfModelSpace(); i++)
		{
			file.println("bounds["+(i+1)+"]:  "+bounds[i][0]+"    "+bounds[i][1]);
		}
		file.println("BEGIN MODELS");
	}
	
	
	 
	public String getParameterName(int pnum)
	{
		return this.paramNames[pnum];
	}
	
	public void setParameterName(int pnum, String name)
	{
		this.paramNames[pnum] = name;
	}
}