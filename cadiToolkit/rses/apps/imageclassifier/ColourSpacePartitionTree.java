

package rses.apps.imageclassifier;

import rses.util.Util;





/** A tree that partitions the RGB colour space (cube) into
 *  sub-cubes, and maintains a class distribution in each
 *  leaf node.
 * 
 * @author peterr
 */
public class ColourSpacePartitionTree
{
	private TreeNode root;
	private int numcategories;
	
	public ColourSpacePartitionTree(int depth, int numcat)
	{
		root = TreeNode.generateRandomTree(depth, numcat);
		this.numcategories = numcat;
	}
	
	
	public void updateTree(int r, int g, int b, int category)
	{
		root.newInstance(r, g, b, category);
	}
	
	public int[] classify(int r, int g, int b)
	{
		return root.classifyInstance(r, g, b);
	}
	
	public int getNumCategories()
	{
		return this.numcategories;
	}
	
	
	public double getExpectedError()
	{
		return root.getExpectedError();
	}
	
	public String toString()
	{
		double[][] bounds = new double[][] {{0,255},{0,255},{0,255}};
		return this.root.toString(bounds);
	}
}


class TreeNode
{
	static final int SPLITONRED = 0;
	static final int SPLITONGREEN = 1;
	static final int SPLITONBLUE = 2;
	
	//both are null if this is a leaf node
	TreeNode lteqfork;
	TreeNode gtfork;
	
	//the variable to split on
	int splitvar; 
	
	//the value to split on. This value is not absolute,
	//but a number between 0 and 1 that indicates the relative
	//split that should go left and right.
	double splitval;
	
	
	//the class distribution for this sub-cube.
	int[] classdist;
	
	//how many elements in classdist 
	int instancecount = 0;
	
	
	public static TreeNode generateRandomTree(int treedepth, int numcategories)
	{
		if(treedepth == 1) //we are returning a leaf node
		{
			TreeNode result = new TreeNode(numcategories);
			return result;
		}
		
		TreeNode result = new TreeNode(numcategories);
		result.gtfork = generateRandomTree(treedepth-1, numcategories);
		result.lteqfork = generateRandomTree(treedepth-1, numcategories);
		
		return result;
	}
	
	//generate a tree node with a random split on a random variable
	TreeNode(int numcategories)
	{
		this.splitvar = (int) (Math.random()*3);
		this.splitval = Math.random();
		this.classdist = new int[numcategories];
	}
	
	

	double getExpectedError()
	{
		int errors = this.getExpectedErrors();
		return ((double) errors)/this.instancecount;
	}
	
	private int getExpectedErrors()
	{
		if(this.lteqfork == null)
		{
			int maxentry = Util.getMax(this.classdist);
			return this.instancecount-maxentry;
		}
		else
			return lteqfork.getExpectedErrors()+gtfork.getExpectedErrors();
	}
	
	//a new pixel with a classification has been found
	void newInstance(int r, int g, int b, int category)
	{
		double[][] bounds = new double[][] {{0,255},{0,255},{0,255}};
		
		this.newInstance(r, g, b, category, bounds);
	}
	

	//bounds is modified by the call
	private void newInstance(int r, int g, int b, int category, double[][] bounds)
	{
		double range = bounds[splitvar][1]-bounds[splitvar][0];
		double splitpoint = bounds[splitvar][0]+this.splitval*range;
		double sval;
		if(splitvar == SPLITONRED) sval = r;
		else if(splitvar == SPLITONGREEN) sval = g; 
		else if(splitvar == SPLITONBLUE) sval = b;
		else throw new IllegalStateException();
		
		//we always update our distribution, regardless of whether
		//we are a leaf node
		updateDistribution(category);
		
		//if we are a leaf node, we are finished
		if(this.lteqfork == null)
			return;
		
		//otherwise, we delegate to a child
		if(sval <= splitpoint) 
		{
			bounds[splitvar][1] = splitpoint;
			this.lteqfork.newInstance(r, g, b, category, bounds);
		}
		else
		{
			bounds[splitvar][0] = splitpoint;
			this.gtfork.newInstance(r, g, b, category, bounds);
		}
	}
	
	
	
	private void updateDistribution(int category)
	{
		this.classdist[category]++;
		this.instancecount++;
	}
	
	
	
	
	int[] classifyInstance(int r, int g, int b)
	{
		double[][] bounds = new double[][] {{0,255},{0,255},{0,255}};
		return this.classifyInstance(r, g, b, null, bounds);
	}
	
	
	
	//bounds is modified by this method
	private int[] classifyInstance(int r, int g, int b, int[] currentguess, double[][] bounds)
	{
		//the root node hasnt seen any instances, so it cannot classify anything
		if(this.instancecount == 0 && currentguess == null)
			throw new RuntimeException("Classifier Tree asked to classify even though it hasnt seen any training instances");
			
			
		int[] provisionalresult = currentguess;
		
		if(this.instancecount != 0)
			provisionalresult = this.classdist;

		
		
		if(this.gtfork == null) //we are a leaf node
		{
			//return either our parent's best guess (if we 
			//havent seen any examples, and so cant guess),
			//or our own guess.
			return provisionalresult;
		}
		//we are not a leaf node, delegate
		else
		{
			double range = bounds[splitvar][1]-bounds[splitvar][0];
			double splitpoint = bounds[splitvar][0]+this.splitval*range;
			double sval;
			if(splitvar == SPLITONRED) sval = r;
			else if(splitvar == SPLITONGREEN) sval = g; 
			else if(splitvar == SPLITONBLUE) sval = b;
			else throw new IllegalStateException();	
			
			if(sval <= splitpoint) 
			{
				bounds[splitvar][1] = splitpoint;
				return this.lteqfork.classifyInstance(r, g, b, provisionalresult, bounds);
			}
			else
			{
				bounds[splitvar][0] = splitpoint;
				return this.gtfork.classifyInstance(r, g, b, provisionalresult, bounds);
			}
		}	
	}
	
	

	
	String toString(double[][] bounds)
	{
		String nl = System.getProperty("line.separator");
		if(this.gtfork == null) //leaf node
		{
			return "if("+bounds[SPLITONRED][0]+" < RED < "+bounds[SPLITONRED][1]+" && "+
			                      bounds[SPLITONGREEN][0]+" < GREEN < "+bounds[SPLITONGREEN][1]+" && "+
								  bounds[SPLITONBLUE][0]+" < BLUE < "+bounds[SPLITONBLUE][1]+") "+
			                      " --> "+Util.arrayToString(this.classdist)+nl;
		}
		else //internal node
		{
			double sval = (bounds[splitvar][1]-bounds[splitvar][0])*this.splitval+bounds[splitvar][0];
			
			double[][] ltbounds = Util.copy(bounds);
			ltbounds[splitvar][1] = sval;
			bounds[splitvar][0] = sval;
			
			return lteqfork.toString(ltbounds)+nl+
			       gtfork.toString(bounds);
		}
	}
	
}

