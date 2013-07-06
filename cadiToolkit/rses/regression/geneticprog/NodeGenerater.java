package rses.regression.geneticprog;

import java.util.Random;

import rses.regression.geneticprog.numericnodes.*;


/** A class which tells us all the node types we can use in a genetic
 *  program
 */
public class NodeGenerater
{
	private static final int NUMNODES = 16;
	
	private Random noderand = new Random(); 
	public NumericNode generateNode()
	{
		int type = noderand.nextInt(NUMNODES);
		
		switch(type)
		{
			case 0: return new Abs();
			case 1: return new Add();
			case 2: return new Divide();
			case 3: return new Exp();
			case 4: return new IfGEQ();
			case 5: return new Log();
			case 6: return new Minus();
			case 7: return new Multiply();
			case 8: return new rses.regression.geneticprog.numericnodes.Number();
			case 9: return new One();
			case 10: return new Pow();
			case 11: return new Sqrt();
			case 12: return new Square();
			case 13: return new Squash();
			case 14: return new UnaryMinus();
			case 15: return new Zero();
			default: throw new IllegalStateException("impossible case reached");
				
		}
	}
	
	public int getNumNodeTypes()
	{
		return NUMNODES;
	}
		
}
