package rses.regression.geneticprog.numericnodes;
import rses.regression.geneticprog.*;




public class Sqrt extends rses.regression.geneticprog.NumericNode
{

	public int getNumChildren()
	{
		return 1;
	}


	public double evaluate(Datum d)
	{
		return Math.sqrt(Math.abs(children[0].evaluate(d)));
	}


	public String toString()
	{
		return "sqrt("+children[0]+")";
	}
}



