package rses.regression.geneticprog.numericnodes;
import rses.regression.geneticprog.*;




public class Log extends rses.regression.geneticprog.NumericNode
{

	public int getNumChildren()
	{
		return 1;
	}


	public double evaluate(Datum d)
	{
		double val = Math.abs(children[0].evaluate(d));
		if(val == 0.0)
			val = Double.MIN_VALUE;
			
		return Math.log(val);
	}


	public String toString()
	{
		return "log("+children[0]+")";
	}
}



