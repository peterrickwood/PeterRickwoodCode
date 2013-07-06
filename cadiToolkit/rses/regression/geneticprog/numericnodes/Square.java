package rses.regression.geneticprog.numericnodes;
import rses.regression.geneticprog.*;




public class Square extends rses.regression.geneticprog.NumericNode
{

	public int getNumChildren()
	{
		return 1;
	}


	public double evaluate(Datum d)
	{
		double v = children[0].evaluate(d);
		return v*v;
	}


	public String toString()
	{
		return children[0]+"^2";
	}
}



