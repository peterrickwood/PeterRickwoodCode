package rses.regression.geneticprog.numericnodes;
import rses.regression.geneticprog.*;




public class Multiply extends rses.regression.geneticprog.NumericNode
{

	public int getNumChildren()
	{
		return 2;
	}


	public double evaluate(Datum d)
	{
		return children[0].evaluate(d) * children[1].evaluate(d);
	}



	public String toString()
	{
		return "("+children[0]+" * "+children[1]+")";
	}

}



