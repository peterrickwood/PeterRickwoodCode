package rses.regression.geneticprog;

import java.util.Stack;

import rses.Debug;

public class NumericNode implements Cloneable 
{
	protected NumericNode parent = null;
	protected NumericNode[] children;

	
	//doesnt make sense to instantiate this node directly
	protected NumericNode()
	{}
	
	//children must override this
	public int getNumChildren()
	{
		throw new UnsupportedOperationException("call to getNumChildren in NumericNode stub class.!!!!");
	}

	//children must override this
	public double evaluate(Datum d) 
	{
		throw new UnsupportedOperationException("call to evaluate in NumericNode stub class.!!!!");
	}

	
	/** Given a NodeGenerater which tells us which nodes to 
	 *  use trees, generate a random tree made up of those nodes
	 * 
	 * @param maxdepth
	 * @param nodegen
	 * @return
	 */	
	public static NumericNode generateRandomTree(int maxdepth, NodeGenerater nodegen)
	{
		if(maxdepth == 0)
			throw new IllegalArgumentException();
		
		NumericNode nn = null;
		if(maxdepth == 1)
		{
			while(nn == null)
			{
				nn = nodegen.generateNode();
				if(nn.getNumChildren() != 0)
					nn = null;
			}
			return nn;
		}
		
		nn = nodegen.generateNode();
        int nchild = nn.getNumChildren();
        nn.children = new NumericNode[nchild];

        //now generate the children for that node
        for(int i = 0; i < nchild; i++) {
                nn.children[i] = generateRandomTree(maxdepth-1, nodegen);
                nn.children[i].parent = nn;
        }

        if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID))
        	nn.checkTree();
        
        return nn;
	}
	
	
	
	/** Obtain a deep copy of the entire tree
	 * 
	 * @return
	 */
	public NumericNode getDeepCopy()
	{
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID))
			checkTree();
		
		NumericNode base;
		try 
		{ 
			base  = (NumericNode) this.clone();
			if(base == this)
				throw new IllegalStateException("clone operation is not a memory copy. this breaks everything... internel error");
			//now clear off any cached copies of the tree 
			base.collectedTree = null;
			base.parent = null;
			base.children = null;
			if(base.getNumChildren() != 0)
				base.children = new NumericNode[base.getNumChildren()];
		}
		catch(CloneNotSupportedException cnse)
		{	throw new RuntimeException("clone not supported"); }
		
		for(int i =0; i < base.getNumChildren(); i++) {
			base.children[i] = this.children[i].getDeepCopy();
			base.children[i].parent = base;
		}
		
		return base;
	}


	//choose a sub-tree at random
	private Stack collectedTree = null;
	
	/** Get a random subtree of this tree
	 *  This is a copy of the subtree, so any changes
	 *  you make to the returned tree do not effect the original
	 * 
	 *  Also, the returned subtree is no longer attached to
	 *  its parent (that is, the returned tree is a new root tree)
	 * 
	 * @return
	 */
	public NumericNode getRandomSubTree()
	{
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID))
			checkTree();

		
		java.util.Stack stack = null;
		if(collectedTree != null)
			stack = collectedTree;
		else
		{
			stack = new java.util.Stack();
			collecttree(stack);
			collectedTree = stack;
		}
		int numnodes = stack.size();
		int node = (int) (Math.random()*numnodes);
		NumericNode orig = ((NumericNode) stack.get(node));
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID))
			orig.checkTree();
		NumericNode result = orig.getDeepCopy(); 
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID)) {
			result.checkTree();
			orig.checkTree();
		}

		if(result.parent != null)
			throw new IllegalStateException("returned subtree is still attached to parent");
		return result;
	}

	
	
	
	//collect this tree into a stack
	private void collecttree(java.util.Stack stack)
	{
		stack.push(this);
		for(int i =0; i < getNumChildren(); i++)
			children[i].collecttree(stack);
	}


	/**
	 * Grab a random subtree from partner and graft it on
	 * at a random point in this tree and return the result.
	 * Neither this tree, nor the partner tree are modified.
	 * that is, a copy of the resulting spliced tree is returned
	 * 
	 * @param partner
	 * @return
	 */
	public NumericNode breed(NumericNode partner)
	{
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID)) {
			checkTree();
			partner.checkTree();
		}


		//copy this tree and let a linearized representation of it
		NumericNode origcopy = this.getDeepCopy();
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID))
			origcopy.checkTree();

		
		Stack copytree =  new java.util.Stack();
		origcopy.collecttree(copytree);
		
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID)) {
			origcopy.checkTree();
			this.checkTree();
		}
		
		
		//copy the partner tree and get a random subtree 
		NumericNode tosplice = partner.getRandomSubTree();
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID)) {
			origcopy.checkTree();
			tosplice.checkTree();
			this.checkTree();
		}

		
		//choose a random node in the original tree
		int rind = (int) (Math.random()*copytree.size());
	
		//special case if we replace whole tree
		NumericNode result = null;
		if(rind == 0)
			return tosplice;

		//nn is the node that we are replacing
		NumericNode nn = (NumericNode) copytree.get(rind);
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID)) {
			origcopy.checkTree();
			tosplice.checkTree();
			this.checkTree();
			nn.checkTree();
		}
		
		boolean done = false;
		for(int i =0; i < nn.parent.getNumChildren(); i++)
		{
			if(nn.parent.children[i] == nn) 
			{
				//attach new subtree
				done = true;
				nn.parent.children[i] = tosplice;
				tosplice.parent = nn.parent;
				nn.parent = null; //cut nn off so that it gets garbage collected
				break;
			}
		}
		if(!done)
			throw new IllegalStateException("tree splicing routine is broken!");

		if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID)) {
			origcopy.checkTree();
			tosplice.checkTree();
			this.checkTree();
			nn.checkTree();
		}
		
		return origcopy;
	}



	
	
	private void checkTree()
	{
		if(this.children != null && this.children.length != this.getNumChildren())
			throw new IllegalStateException("num children does not match actual number of children");
		for(int i =0; i < this.getNumChildren(); i++)
		{
			if(this.children[i].parent != this)
				throw new IllegalStateException("child does not think this tree is its parent!");
		}
		
		if(this.parent != null)
		{
			boolean foundself = false;
			for(int i = 0; i < parent.getNumChildren(); i++)
				if(parent.children[i] == this)
					foundself = true;
			if(!foundself)
				throw new IllegalStateException("parent does not think I am its child!");
		}
		
		//now check children
		for(int i =0; i < this.getNumChildren(); i++)
		{
			this.children[i].checkTree();
		}
		
	}
	
	

	//perturb this node. Subclasses should override this,
	//otherwise, we just perturb the children
	public void perturb()
	{
		if(getNumChildren() > 0)
			//go and perturb a child instead
			children[(int) (Math.random()*getNumChildren())].perturb();
	}
}
