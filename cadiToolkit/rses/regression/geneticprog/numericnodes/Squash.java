package rses.regression.geneticprog.numericnodes;
import rses.regression.geneticprog.*;



//use arctan to squash the number to between -1 and 1
//
public class Squash extends rses.regression.geneticprog.NumericNode
{

	public int getNumChildren()
	{
		return 1;
	}


	public double evaluate(Datum d)
	{
		return rses.util.Util.squash(this.children[0].evaluate(d));	
	}

	public String toString()
	{
		return "squash("+children[0]+")";
	}
}



