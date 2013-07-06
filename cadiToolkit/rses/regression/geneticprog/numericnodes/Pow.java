package rses.regression.geneticprog.numericnodes;
import rses.regression.geneticprog.*;




public class Pow extends rses.regression.geneticprog.NumericNode
{

	public int getNumChildren()
	{
		return 2;
	}


	public double evaluate(Datum d)
	{
		double base = children[0].evaluate(d);
		double exp = children[1].evaluate(d);
		
		//a negative base means that the exponent 
		//must be an integer
		return Math.pow(base, (int) (Math.round(exp)));
	}


	public String toString()
	{
		return children[0]+"^"+children[1];
	}
}



