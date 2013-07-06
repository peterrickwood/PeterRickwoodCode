package rses.regression.geneticprog.numericnodes;
import rses.regression.geneticprog.*;




public class Exp extends rses.regression.geneticprog.NumericNode
{

	public int getNumChildren()
	{
		return 1;
	}


	public double evaluate(Datum d)
	{
		return Math.exp(children[0].evaluate(d));
	}


	public String toString()
	{
		return "exp("+children[0]+")";
	}
}



