package rses.spatial.util;


/** A quadtree class to compress spatial data
 * 
 * @author peterr
 *
 */
public class QuadTree_int 
{
	QuadTree_int q1 = null;
	QuadTree_int q2 = null;
	QuadTree_int q3 = null;
	QuadTree_int q4 = null;
	
	int value;
	
	//TODO make this parameterized
	public QuadTree_int()
	{
		
	}
	
	
	public boolean isLeaf()
	{
		return q1 == null;
	}
	
	
	
	

	public static QuadTree_int buildQuadTree(int[][] pixels, int startrow, int lenr, int startcol, int lenc)
	{
		QuadTree_int root = new QuadTree_int();
		if(lenr == 1 && lenc == 1) //at a single pixel
		{
			root.value = pixels[startrow][startcol];
			return root;
		}
		else if(lenr == 0) { //can be caused by non-power of 2 images
			root.value = pixels[startrow-1][startcol];
			return root;
		}
		else if(lenc == 0) { //can be caused by non-power of 2 images
			root.value = pixels[startrow][startcol-1];
			return root;
		}
		
				
		int newlenr = Math.max(lenr/2,1);
		int newlenc = Math.max(lenc/2,1);
		root.q1 = buildQuadTree(pixels, startrow, newlenr, startcol, newlenc);
		root.q2 = buildQuadTree(pixels, startrow+newlenr, lenr-newlenr, startcol, newlenc);
		root.q3 = buildQuadTree(pixels, startrow, newlenr, startcol+newlenc, lenc-newlenc);
		root.q4 = buildQuadTree(pixels, startrow+newlenr, lenr-newlenr, startcol+newlenc, lenc-newlenc);
		
		//if all subtrees are leafs, and have the same value, then compress them
		if(root.q1.isLeaf() && root.q2.isLeaf() && root.q3.isLeaf() && root.q4.isLeaf())
		{
			if(root.q1.value == root.q2.value && root.q1.value == root.q3.value && root.q1.value == root.q4.value)
			{
				root.value = root.q1.value;
				root.q1 = null; root.q2 = null; root.q3 = null; root.q4 = null;
			}
		}
		
		return root;
			
	}

	
	public static QuadTree_int buildQuadTree(int[][] pixels)
	{
		
		/*TODO FIXME this should be reworked to break the image up into 
		chunks of 4 as much as possible...
		You see, doing it the way it is currently done you get a lot of 
		unbalanced quadtrees at the leafs, which makes the space taken
		up by the quadtree much larger than it should be. and after all
		the whole point of quantreeing the thing is to compress it*/
		return buildQuadTree(pixels, 0, pixels.length, 0, pixels[0].length);
	}
	
	
	
	//for testing
	public static void main(String[] args)
	{
		int[][] pixels = new int[][] {
				{1,2,3},
				{4,5,6},
				{7,8,9}
		};
		
		buildQuadTree(pixels);
	}
	
	
}
