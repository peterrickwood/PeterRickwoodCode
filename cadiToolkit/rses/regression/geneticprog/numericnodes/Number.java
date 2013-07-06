package rses.regression.geneticprog.numericnodes;
import rses.regression.geneticprog.*;




public class Number extends rses.regression.geneticprog.NumericNode
{
	protected double num;
	static java.util.Random r = new java.util.Random();
	public Number()
	{
		num = r.nextGaussian()*25;
	}


	public int getNumChildren()
	{
		return 0;
	}


	public double evaluate(Datum d)
	{
		return num;	
	}

	public String toString()
	{
		return ""+num;
	}

	public void perturb()
	{
		double stddev = Math.abs(num/4);
		if(stddev < 1)
			stddev = 1.0;
		num = num + r.nextGaussian()*stddev;
	}
}



