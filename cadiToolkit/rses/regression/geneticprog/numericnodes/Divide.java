package rses.regression.geneticprog.numericnodes;
import rses.regression.geneticprog.*;




public class Divide extends rses.regression.geneticprog.NumericNode
{

	public int getNumChildren()
	{
		return 2;
	}


	public double evaluate(Datum d)
	{
		double denominator = children[1].evaluate(d);
		if(denominator == 0.0)
			return Double.MAX_VALUE;
		return children[0].evaluate(d)/denominator;
	}

	public String toString()
	{
		return "("+children[0]+" / "+children[1]+")";
	}

}



