package rses.regression.geneticprog.numericnodes;
import rses.regression.geneticprog.*;




public class UnaryMinus extends rses.regression.geneticprog.NumericNode
{

	public int getNumChildren()
	{
		return 1;
	}


	public double evaluate(Datum d)
	{
		return -(children[0].evaluate(d));
	}


	public String toString()
	{
		return "-"+children[0];
	}
}



