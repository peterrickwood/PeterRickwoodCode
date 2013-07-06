package rses.regression.geneticprog.numericnodes;
import rses.regression.geneticprog.*;




public class IfGEQ extends rses.regression.geneticprog.NumericNode
{

	public int getNumChildren()
	{
		return 4;
	}


	public double evaluate(Datum d)
	{
		if(children[0].evaluate(d) >= children[1].evaluate(d))
			return children[2].evaluate(d);
		return children[3].evaluate(d);
	}

	public String toString()
	{
		return "(if("+children[0]+" >= "+children[1]+") "+children[2]+" : "+children[3]+")";
	}
}



