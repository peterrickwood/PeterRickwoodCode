package rses.apps.imageclassifier;

import rses.util.Util;






public class PartitionTreeForest
{
	private ColourSpacePartitionTree[] forest;
	
	public PartitionTreeForest(int numtrees, int treedepth, int numcategories)
	{
		//initialize our random forest
		this.forest = new ColourSpacePartitionTree[numtrees];
		for(int i = 0; i < numtrees; i++)
			this.forest[i] = new ColourSpacePartitionTree(treedepth, numcategories);
	}
	
	
	public double[] classify(int r, int g, int b)
	{
		double[] result = new double[forest[0].getNumCategories()];
		
		//take a combined classification from all of our trees
		//(weighted according to that tree's predicted error) 
		for(int i = 0; i < forest.length; i++)
		{
			int[] tres = forest[i].classify(r, g, b);
			double[] dtmp = new double[tres.length];
			for(int j =0; j < tres.length; j++)
				dtmp[j] = tres[j];
			Util.normalize(dtmp);
			
			//weight the contribution of each tree 
			//double weight = forest[i].getExpectedError();
			double weight = 1.0;
			
			for(int j =0; j < result.length; j++)
				result[j] += weight*tres[j];
		}
		
		Util.normalize(result);
		
		
		return result;
	}
	
	
	public void updateClassifier(int r, int g, int b, int category)
	{
		for(int i =0; i < forest.length; i++)
			forest[i].updateTree(r, g, b, category);
	}
	
	
	public int getNumCategories()
	{
		return forest[0].getNumCategories();
	}
	
	
	
	public String toString()
	{
		String nl = System.getProperty("line.separator");
		StringBuffer sb = new StringBuffer();
		for(int i =0; i < this.forest.length; i++)
		{
			sb.append("     ----- Tree "+i+" -----"+nl);
			sb.append(forest[i].toString());
			sb.append("     ----- End Tree "+i+" -----"+nl);
		}
		return sb.toString();
	}
	
}


