package rses.regression.geneticprog.numericnodes;
import rses.regression.geneticprog.*;


public class Abs extends rses.regression.geneticprog.NumericNode
{
	public int getNumChildren()
	{	
		return 1;
	}


	public double evaluate(Datum d)
	{
		return Math.abs(this.children[0].evaluate(d));
	}

	public String toString()
	{
		return "abs("+children[0]+")";
	}	
}



