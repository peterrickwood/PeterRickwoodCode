package rses.util;


import java.util.*;
import java.io.*;

import rses.Debug;


/**	A class with a bunch of static utility functions. 
 *	Actually the range of utility functions in this
 *	class is probably getting sufficiently diverse
 *	to warrant splitting it into multiple classes
 *	sometime soon.
 *
 *	@author Peter Rickwood
 */


public class Util 
{
	private Util() {}  //can't instantiate this class

	

	public static String raw_input()
	throws IOException
	{
		return getUserInput();
	}
	
	public static String getUserInput()
	throws IOException
	{
		return new BufferedReader(new InputStreamReader(System.in)).readLine();
	}
	

	
	/**
	 * 
	 * retval[foldnum][0] is the training set for foldnum
	 * retval[foldnum][1] is the test set for foldnum
	 * 
	 * @param objects
	 * @param numfolds
	 * @return train/test sets for each fold
	 */
	public static Object[][][] getCrossValidationFolds(Object[] objects, int numfolds)
	{
		Object[][] partitions = partition(objects, numfolds);
		Object[][][] result = new Object[numfolds][2][];
		for(int i =0; i < numfolds; i++)
		{
			ArrayList trainpartition = new ArrayList();
			for(int j = 0; j < partitions.length; j++)
			{
				if(j != i) //exclude the test set
				{
					for(int n = 0; n < partitions[j].length; n++)
						trainpartition.add(partitions[j][n]);
				}
			}
			Object[] test = partitions[i];
			Object[] train = new Object[trainpartition.size()];
			
			for(int j = 0; j < train.length; j++)
				train[j] = trainpartition.get(j);
				
			result[i][0] = train;
			result[i][1] = test;
		}
		
		return result;
	}
	
	
	/** Partition a set of objects up into random subsets.
	 *  Useful for things like cross-validation
	 * 
	 * @param objects
	 * @param numpartitions
	 * @return
	 */
	public static Object[][] partition(Object[] objects, int numpartitions)
	{
		if(numpartitions > objects.length)
			throw new IllegalArgumentException("more partitions than data!");
		
		Object[][] result = new Object[numpartitions][];
		
		Object[] randomized = Util.jumble(objects);
		
			
		ArrayList[] partitions = new ArrayList[numpartitions];
		for(int i = 0; i < partitions.length; i++)
			partitions[i] = new ArrayList();
		
		//now we partition
		for(int i =0; i < randomized.length; i++)
			partitions[i % numpartitions].add(randomized[i]);
		
		for(int i = 0; i < numpartitions; i++)
			result[i] = partitions[i].toArray();
		
		return result;
	}
	
	
	
	
	public static boolean equal(double[] a1, double[] a2)
	{
		if(a1.length != a2.length)
			return false;
		for(int i =0; i < a1.length; i++)
			if(a1[i] != a2[i])
				return false;
		return true;
	}
	
	
	/* squash a value to between 0 and 1 (if value if positive)
	 * or 0 and -1 (if val is negative)
	 */
	public static double squash(double val)
	{
		double atanres = Math.atan(val);
		//this gives us a value in the range PI/2 to -PI/2
		double res = atanres/(Math.PI/2);
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_NORMAL))
		{
			if(val >= 0 && (res < 0 || res > 1))
				throw new IllegalStateException("internal math error. positive value resulted in atan out of bounds");				
			else if(val < 0 && (res < -1 || res > 0))
				throw new IllegalStateException("internal math error. positive value resulted in atan out of bounds");			
		}
		return res;
    }

	
	
	
	
	public static void printarray(double[] array, PrintStream pstream)
	{
		pstream.println(arrayToString(array));
	}

	public static void printarray(float[] array, PrintStream pstream)
	{
		pstream.println(arrayToString(array));
	}

	public static void printarray(int[] array, PrintStream pstream)
	{
		pstream.println(arrayToString(array));
	}


	/** parse an aray from a string. 2 possible encodings are possible.
	 *  1) A line with a bunch of numbers seperated by whitespace
	 *  2) A line beginning with '[' and ending in ']', with numbers in between
	 *     seperated by commas.
	 * 
	 * 
	 * 
	 * @param line
	 * @param type
	 * @return
	 */
	public static Object parseArray(String line, Class type)
	{
		String[] words = getWords(line);
		int[] inta = null;
		double[] doublea = null;
		float[] floata = null;
		int arraylen = words.length;
		int inc = 1;
		int start = 0;

		if(words[0].equals("[]")) //special case for empty list
		{
			arraylen = 0;
			start = Integer.MAX_VALUE;
		}
		else if(words[0].equals("[")) 
		{
			words = line.substring(1, line.length()-1).split(",");
		 	arraylen = words.length;
		 	inc = 1;
		 	start = 0;
		}
		
		
		
		if(type == int.class)
			inta = new int[arraylen];
		else if(type == double.class)
			doublea = new double[arraylen];
		else if(type == float.class)
			floata = new float[arraylen];
		else
			throw new UnsupportedOperationException("only integer, double, and float arrays are supported");
		
		int count = 0;
		for(int i = start; i < words.length; i+=inc, count++)
		{
			if(type == int.class)
				inta[count] = Integer.parseInt(words[i].trim());
			else if(type == double.class)
				doublea[count] = Double.parseDouble(words[i].trim());
			else if(type == float.class)
				floata[count] = Float.parseFloat(words[i].trim());
			else
				throw new IllegalStateException("Impossible case reached!!!");			
		}

		if(type == int.class)
			return inta;
		else if(type == double.class)
			return doublea;
		else if(type == float.class)
			return floata;
		else
			throw new IllegalStateException("Impossible case reached!!!");
	}



	public static String arrayToString(Object[] array)
	{
		if(array.length == 0)
			return "[]";
		int alengthminus1 = array.length-1;
		StringBuffer sb = new StringBuffer("[ ");
		for(int i = 0; i < alengthminus1; i++)
			sb.append(array[i]+" , ");
		sb.append(array[alengthminus1]);
		sb.append(" ]");
		return sb.toString();		
	}



	public static String arrayToString(byte[] array)
	{
		if(array.length == 0)
			return "[]";
		int alengthminus1 = array.length-1;
		StringBuffer sb = new StringBuffer("[ ");
		for(int i = 0; i < alengthminus1; i++)
			sb.append(array[i]+" , ");
		sb.append(array[alengthminus1]);
		sb.append(" ]");
		return sb.toString();		
	}


	public static String arrayToString(double[] array)
	{
		if(array.length == 0)
			return "[]";
		int alengthminus1 = array.length-1;
		StringBuffer sb = new StringBuffer("[ ");
		for(int i = 0; i < alengthminus1; i++)
			sb.append(array[i]+" , ");
		sb.append(array[alengthminus1]);
		sb.append(" ]");
		return sb.toString();		
	}

	public static String arrayToString(float[] array)
	{
		if(array.length == 0)
			return "[]";
		int alengthminus1 = array.length-1;
		StringBuffer sb = new StringBuffer("[ ");
		for(int i = 0; i < alengthminus1; i++)
			sb.append(array[i]+" , ");
		sb.append(array[alengthminus1]);
		sb.append(" ]");
		return sb.toString();		
	}


	public static String arrayToString(int[] array)
	{
		if(array.length == 0)
			return "[]";
		int alengthminus1 = array.length-1;
		StringBuffer sb = new StringBuffer("[ ");
		for(int i = 0; i < alengthminus1; i++)
			sb.append(array[i]+" , ");
		sb.append(array[alengthminus1]);
		sb.append(" ]");
		return sb.toString();		
	}



	public static int getIndex(int val, int[] array)
	{
		int alength = array.length;
		for(int i = 0; i < alength; i++)
		{
			if(array[i] == val)
				return i;
		}
		return -1;
	}


	
	public String join(String[] bits, String filler)
	{
		String res = "";
		for(int i = 0; i < bits.length-1; i++)
			res = res + bits[i] + filler;
		res = res + bits[bits.length-1];
		return res;
	}
	
	public static int getIndex(float val, float[] array)
	{
		int alength = array.length;
		for(int i = 0; i < alength; i++)
		{
			if(array[i] == val)
				return i;
		}
		return -1;
	}
	

	public static int getIndex(double val, double[] array)
	{
		int alength = array.length;
		for(int i = 0; i < alength; i++)
		{
			if(array[i] == val)
				return i;
		}
		return -1;
	}

	
	
	public static int getIndex(Object val, Object[] array)
	{
		int alength = array.length;
		for(int i =0; i < alength; i++)
		{
			if(array[i].equals(val))
				return i;
		}
		return -1;
	}


	/** Normalizes an array (ie - ensures that the sum of all
	 * elements in the array is 1.0). Does this by dividing
	 * each element in the array by the sum of all elements
	 * in the array.
	 * <p>
	 * If the sum of all elements in the array is 0, then
	 * all array elements are given value 1.0/array.length
	 * <p>
	 * @param array		The array to be normalized
	 */
	public static void normalize(double[] array)
	{
		int alength = array.length;
		double sum = getSum(array);
		if(Double.isNaN(sum)) throw new RuntimeException("cant normalize");
		if(sum == 0) for(int i = 0; i < alength; i++)
			array[i] = 1.0/alength;
		else for(int i = 0; i < alength; i++)
			array[i] /= sum;
	}

	public static void normalize(Double[] array)
	{
		int alength = array.length;
		double sum = getSum(array);
		if(Double.isNaN(sum)) throw new RuntimeException("cant normalize");
		if(sum == 0) for(int i = 0; i < alength; i++)
			array[i] = 1.0/alength;
		else for(int i = 0; i < alength; i++)
			array[i] /= sum;
	}

	
	public static void normalize(double[][] array)
	{
		double sum = getSum(array);
		if(Double.isNaN(sum)) throw new RuntimeException("cant normalize");

		for(int i = 0; i < array.length; i++)
			for(int j = 0; j < array[i].length; j++)
				array[i][j] /= sum;
	}






	public static int[] copy(int[] array)
	{
		int alength = array.length;
		int[] cp = new int[alength];
		for(int i = 0; i < alength; i++)
			cp[i] = array[i];
		return cp;	
	}


	public static double[] copy(double[] array)
	{
		int alength = array.length;
		double[] cp = new double[alength];
		for(int i = 0; i < alength; i++)
			cp[i] = array[i];
		return cp;
	}

	public static double[][] copy(double[][] array)
	{
		int rows, i;
		rows = array.length;
		double[][] cp = new double[rows][];
		for(i = 0; i < rows; i++)
			cp[i] = (array[i] == null ? null : Util.copy(array[i]));
		return cp;
	}

	public static boolean[][] copy(boolean[][] array)
	{
		int rows, i;
		rows = array.length;
		boolean[][] cp = new boolean[rows][];
		for(i = 0; i < rows; i++)
			cp[i] = (array[i] == null) ? null : Util.copy(array[i]);

		return cp;	
	}


	public static byte[][] copy(byte[][] array)
	{
		int rows, i;
		rows = array.length;
		byte[][] cp = new byte[rows][];
		for(i = 0; i < rows; i++)
			cp[i] = (array[i] == null) ? null : Util.copy(array[i]);

		return cp;	
	}

	
	public static int[][] copy(int[][] array)
	{
		int rows, i;
		rows = array.length;
		int[][] cp = new int[rows][];
		for(i = 0; i < rows; i++)
			cp[i] = (array[i] == null) ? null : Util.copy(array[i]);

		return cp;	
	}


	public static float[][] copy(float[][] array)
	{
		int rows, i;
		rows = array.length;
		float[][] cp = new float[rows][];
		for(i = 0; i < rows; i++)
			cp[i] = (array[i] == null) ? null : Util.copy(array[i]);

		return cp;	
	}


	public static boolean[] copy(boolean[] array)
	{
		int alength = array.length;
		boolean[] cp = new boolean[alength];
		for(int i = 0; i < alength; i++)
			cp[i] = array[i];
		return cp;
	}



	public static float[] copy(float[] array)
	{
		int alength = array.length;
		float[] cp = new float[alength];
		for(int i = 0; i < alength; i++)
			cp[i] = array[i];
		return cp;
	}

	public static byte[] copy(byte[] array)
	{
		int alength = array.length;
		byte[] cp = new byte[alength];
		for(int i = 0; i < alength; i++)
			cp[i] = array[i];
		return cp;
	}

	
	
	
	public static Object[] jumble(Object[] array)
	{
		return jumble(array, new Random());
	}
	
	
	/** Return an array that is a random arrangement of the
	 *  elements in <code>array</code>. The original array is
	 *  not modified.
	 *  
	 *  This is implemented by tupling each element in array
	 *  with a randomly drawn number, and then (heap)sorting 
	 *  on the randomly drawn number. Thus, the operation
	 *  is O(n.log(n)), where n = array.length
	 * 
	 * @param array
	 * @param rand The random number generator used to do the jumbling
	 * @return
	 */
	public static Object[] jumble(Object[] array, Random rand)
	{
		if(array == null || array.length < 1)
			throw new IllegalArgumentException("Empty or null array passed as argument");
		Heap h = new Heap(array.length+1);


		for(int i = 0; i < array.length; i++)
		{
			HeapElement<Object> he = new HeapElement<Object>(rand.nextDouble(), array[i]);
			h.insert(he);
		}
		
		//now pop them off the heap one by one
		Object[] result = new Object[array.length];
		for(int i =0; i < result.length; i++)
		{
			HeapElement jhe = h.extractMin();
			result[i] = jhe.getObject();
		}
		
		return result;
	}
	
	
	
	/** Get a sample of size <code>nsam</code>
	 *  drawn (without replacement) from <code>pop</code>
	 * 
	 * @param pop
	 * @return
	 */
	public static Object[] getSubSample(Object[] pop, int nsam)
	{
		Random r = new Random();
		Object[] result = new Object[nsam];
		
		//if it is a small subsample, then we just draw by
		//trying at random
		if(nsam <= pop.length/2)
		{
			boolean[] picked = new boolean[pop.length];
			int numpicked = 0;
			while(numpicked < nsam)
			{
				int rval = r.nextInt(pop.length);
				if(!picked[rval])
				{
					picked[rval] = true;
					result[numpicked] = pop[rval];
					numpicked++;
				}
			}
		}
		else //it is a large subsample, so it is more efficient to do it by jumbling
		{
			Object[] jumbled = jumble(pop);
			for(int i =0; i < result.length; i++)
				result[i] = jumbled[i];
		}
		return result;
	}
	
	
	
	/** Get a sample of size <code>nsam</code>
	 *  drawn (with replacement) from <code>pop</code>
	 *  The returned result is a 2 element array, with
	 *  the first element being the array of 
	 *  bootstrap samples, and the second being the array
	 *  of samples not in the bootstrap sample, but in
	 *  the original population.
	 * 
	 * @param pop
	 * @return
	 */
	public static Object[][] getBootstrapSample(Object[] pop, int nsam)
	{
		boolean[] included = new boolean[pop.length];
		int leftout = pop.length;
		Random r = new Random();
		Object[][] result = new Object[2][];
		result[0] = new Object[nsam];
		for(int i = 0; i < nsam; i++)
		{
			int index = r.nextInt(pop.length);
			result[0][i] = pop[index];
			if(!included[index])
			{
				included[index] = true;
				leftout--;
			}
		}
		result[1] = new Object[leftout];
		for(int i = 0; i < included.length; i++)
			if(!included[i])
				result[1][--leftout] = pop[i];
			
		return result;
	}
	
	

	/** Returns an integer array that indicated the
         *  indicies of the array elements <i>if</i>
	 *  they were sorted in ascending order.
	 * 
	 *  The actual data array is not modified.
	*/
	public static int[] quickSortIndex(float[] data)
	{
		int[] indices = new int[data.length];
		for(int i = 0; i < indices.length; i++)
			indices[i] = i;
		quickSortIndex(indices, data, 0, data.length - 1);
		return indices;
	}



	public static void quickSortIndex(int[] indices, float[] data, 
	                                  int first, int last)
	{
		if(first >= last)
			return;  //done sorting
			
		/* else */
		int q = partition(indices, data, first, last);
		quickSortIndex(indices, data, first, q);
		quickSortIndex(indices, data, q+1, last);
	}

	
	
	private static int partition(int[] indices, float[] data, int first, int last)
	{
		float x = data[indices[first]];
		int i = first - 1;
		int j = last + 1;
		int tempi;

		
		while(true)
		{
			do j--;
			while(data[indices[j]] > x);

			do i++;
			while(data[indices[i]] < x);

			if(i < j)  //exchange data[i] and data[j]
			{
				tempi = indices[i];
				indices[i] = indices[j];
				indices[j] = tempi;
			}
			else
				return j;
		}
	}


	public static void quickSort(float[] data)
	{
		quickSort(data, 0, data.length - 1);
	}



	public static void quickSort(float[] data, int first, int last)
	{
		if(first >= last)
			return;  //done sorting
			
		/* else */
		int q = partition(data, first, last);
		quickSort(data, first, q);
		quickSort(data, q+1, last);
	}

	
	
	private static int partition(float[] data, int first, int last)
	{
		float x = data[first];
		int i = first - 1;
		int j = last + 1;
		float temp;

		
		while(true)
		{
			do j--;
			while(data[j] > x);

			do i++;
			while(data[i] < x);

			if(i < j)  //exchange data[i] and data[j]
			{
				temp = data[i];
				data[i] = data[j];
				data[j] = temp;
			}
			else
				return j;
		}
	}
	



	public static boolean isSorted(float[] array)
	{
		for(int i = 0; i < array.length-1; i++)
		{
			if(array[i] > array[i+1])
				return false;
		}
		return true;
	}








	public static void quickSort(int[] data)
	{
		quickSort(data, 0, data.length - 1);
	}



	public static void quickSort(int[] data, int first, int last)
	{
		if(first >= last)
			return;  //done sorting
			
		/* else */
		int q = partition(data, first, last);
		quickSort(data, first, q);
		quickSort(data, q+1, last);
	}

	
	
	private static int partition(int[] data, int first, int last)
	{
		int x = data[first];
		int i = first - 1;
		int j = last + 1;
		int temp;

		
		while(true)
		{
			do j--;
			while(data[j] > x);

			do i++;
			while(data[i] < x);

			if(i < j)  //exchange data[i] and data[j]
			{
				temp = data[i];
				data[i] = data[j];
				data[j] = temp;
			}
			else
				return j;
		}
	}
	



	public static boolean isSorted(int[] array)
	{
		for(int i = 0; i < array.length-1; i++)
		{
			if(array[i] > array[i+1])
				return false;
		}
		return true;
	}



















	
	
	public static double getSumSquares(float[] array, int length)
	{
		double result = 0.0;
		for(int i = 0; i < length; i++)
			result += (array[i]*array[i]);
		return result;
	}

	public static double getSumSquares(float[] array)
	{
		return getSumSquares(array, array.length);
	}


	public static double getSum(float[] array, int length)
	{
		double result = 0.0;
		for(int i = 0; i < length; i++)
			result += array[i];
		return result;		
	}
	
	public static double getSum(float[] array)
	{
		return getSum(array, array.length);
	}

	public static double getSum(Number[] array)
	{
		double result = 0.0;
		for(int i = 0; i < array.length; i++)
			result = result + array[i].doubleValue();
		return result;	
	}

	
	public static double getSum(double[] array)
	{
		double result = 0.0;
		for(int i = 0; i < array.length; i++)
			result += array[i];
		return result;	
	}

	public static double getSum(double[][] array)
	{
		double result = 0.0;
		for(int i = 0; i < array.length; i++)
			result += getSum(array[i]);
		return result;	
	}

	
	public static int getSum(int[] array)
	{
		int result = 0;
		for(int i = 0; i < array.length; i++)
			result += array[i];
		return result;
	}

	public static int getSum(short[] array)
	{
		int result = 0;
		for(int i = 0; i < array.length; i++)
			result += array[i];
		return result;
	}

	public static int getMax(Integer[] array)
	{
		return array[getMaxIndex(array)];
	}

	
	public static int getMaxIndex(Integer[] array) 
	{
		int max = array[0];
		int maxindex = 0;
		
		for(int i = 1; i < array.length; i++)
		{
			if(array[i] > max)
			{
				max = array[i];
				maxindex = i;
			}
		}
		return maxindex;
	}

	
	public static int getMax(int[] array)
	{
		return array[getMaxIndex(array)];
	}

	
	
	public static int getMaxIndex(int[] array) 
	{
		int max = array[0];
		int maxindex = 0;
		
		for(int i = 1; i < array.length; i++)
		{
			if(array[i] > max)
			{
				max = array[i];
				maxindex = i;
			}
		}
		return maxindex;
	}


	public static double getMin(int[] array)
	{
		return array[getMinIndex(array)];
	}

	public static double getMin(double[] array)
	{
		return array[getMinIndex(array)];
	}

	public static double getMax(double[] array)
	{
		return array[getMaxIndex(array)];
	}
	
	
	
	public static int getMaxIndex(double[] array) 
	{
		double max = array[0];
		int maxindex = 0;
		
		for(int i = 1; i < array.length; i++)
		{
			if(array[i] > max)
			{
				max = array[i];
				maxindex = i;
			}
		}
		return maxindex;
	}


	public static int getMaxIndex(float[] array) 
	{
		float max = array[0];
		int maxindex = 0;
		
		for(int i = 1; i < array.length; i++)
		{
			if(array[i] > max)
			{
				max = array[i];
				maxindex = i;
			}
		}
		return maxindex;
	}


	public static int getMaxIndex(short[] array) 
	{
		short max = array[0];
		int maxindex = 0;
		
		for(int i = 1; i < array.length; i++)
		{
			if(array[i] > max)
			{
				max = array[i];
				maxindex = i;
			}
		}
		return maxindex;
	}





	public static int getMinIndex(int[] array) 
	{
		int min = array[0];
		int minindex = 0;
		
		for(int i = 1; i < array.length; i++)
		{
			if(array[i] < min)
			{
				min = array[i];
				minindex = i;
			}
		}
		return minindex;
	}



	public static int getMinIndex(double[] array) 
	{
		double min = array[0];
		int minindex = 0;
		
		for(int i = 1; i < array.length; i++)
		{
			if(array[i] < min)
			{
				min = array[i];
				minindex = i;
			}
		}
		return minindex;
	}
	
	
	
	
	public static String join(String joinstr, String[] words)
	{
		String res = words[0];
		for(int i = 1; i < words.length; i++)
			res += joinstr+words[i];
		return res;
	}
	
	

	/** Splits a String up into an array of Strings,
	 * using whitespace (as defined by Character.isWhiteSpace())
	 * as delimiters.
	 * <p>
	 * @param line		The string to be parsed.
	 * <p>
	 * @return 		The tokens in the string.
	 * 
	 * <b>NB:</b>  This function pre-dates Java 1.4. Since java 1.4
	 * there is a String.split() function which you can use instead.
	 */
	public static String[] getWords(String line)
	{
		return getWords(line, null);
	}
	
	
	public static String[] getWords(String line, String delimiters)
	{
		Vector words = new Vector();
		int i = 0;
		
		String word = "";
		
		while(i < line.length())
		{
			if(delimiters == null)
			{
				if(Character.isWhitespace(line.charAt(i)))
				{
					if(word.length() > 0)
					{
						words.addElement(word); 
						word = "";
					}
				}
				else
					word += line.charAt(i);
			}
			else
			{
				if(delimiters.indexOf(line.charAt(i)) >= 0)
				{
					if(word.length() > 0)
					{
						words.addElement(word); 
						word = "";
					}
				}
				else
					word += line.charAt(i);
				
			}
			
			i++;
		}
		
		if(word.length() > 0)
			words.addElement(word);
		
		String[] result = new String[words.size()];
		
		for(int j = 0; j < words.size(); j++)
		{
			result[j] = (String) words.elementAt(j);
		}
		return result;
	}



	
	public static void initializeArray(double[][] array, double val)
	{
		for(int i =0; i < array.length; i++)
			initializeArray(array[i], val);
	}
	
	public static void initializeArray(double[] array, double val)
	{
		for(int i =0; i < array.length; i++)
			array[i] = val;
	}
	
	
	

	public static double log_n(double arg, double base)
	{
		return (Math.log(arg)/Math.log(base));
	}


	private static final double log2 = Math.log(2); 
	public static double log_2(double arg)
	{
		return (Math.log(arg)/log2);
	}


	public static double entropy(int[] class_members, int min_count)
	{
		double[] pd = new double[class_members.length];
		int total = getSum(class_members) + min_count*pd.length;
		
		for(int i = 0; i < pd.length; i++)
		{
			pd[i] = ((double) (class_members[i]+min_count)) / total;
		}
		return entropy(pd);
	}

	public static double entropy(int[] class_members)
	{
		return entropy(class_members, 0);
	}


	public static double entropy(double[] pd)
	{
		double result = 0.0;
		for(int i = 0; i < pd.length; i++)
		{
			if(pd[i] <= 0.0)
				continue;
			result += (-log_2(pd[i])*pd[i]);
		}
		return result;
	}


	public static double entropy(float[] pd)
	{
		double result = 0.0;
		for(int i = 0; i < pd.length; i++)
		{
			if(pd[i] <= 0.0)
				continue;
			result += (-log_2(pd[i])*pd[i]);
		}
		return result;
	}








	public static double getSquaredDifference(double[] v1, double[] v2)
	{
		double diff = 0;
		double tmp;
		int vlength = v1.length;
		if(v2.length != vlength)
			throw new RuntimeException("vectors are of different lengths");
		for(int i = 0; i < vlength; i++)
		{
			tmp = v1[i]-v2[i];
			diff += tmp*tmp;
		}
		return diff;
	}




	/** In general, this function will return the index of the first
	 *  array element whose cummulative sum (i.e. the sum of that
	 *  array element and all others) is &gt= the provided score.
	 *  If we have a discrete probability density function,
	 *  and wish to determine where a particular probability
	 *  falls, we can use this function.
	 *  <p>
	 *  For example, suppose we have a simple pdf {0.4, 0.4, 0.2}. 
	 *  getPDFIndex({0.5, 0.5}, x) will return 0 for any score &lt=
	 *  0.4, 1 for any score between 0.4 and 0.8, and 2 for any score
	 *  greater than 0.8.
	 * <p>
	 *  NB: score MUST be &lt= sum(pdf) for this function to make sense. 
	 * 
	 * @param pdf An array of values, typically a density function.
	 * @param score A score whose cumulative index we wish to find
	 * <p>
	 * @return The index of the first element whose cummulative sum
	 * is &gt= the provided score. The definition of cummulative sum 
	 * is found above, in the main method description.
	 */
	public static int getCummulativeIndex(double[] pdf, double score)
	{
		double sum = 0.0;
		int index = 0;

		while(index < pdf.length)
		{
			sum += pdf[index];
			if(sum >= score)
				return index;
			index++;
		}

		if(score <= 1.0)
			return pdf.length-1;

		throw new RuntimeException("incorrect arguments supplied -- could not determine index. sum is "+sum);
	
	}



	/**
	 * 
	 * @param d The number to br printed
	 * @param width The width (in columns) that the number is to fit into
	 * @return A string of width 'width' (or less), that is a truncated representation of 'd',
	 * 			or <code>null</code> if the number cannot be sensibly displayed in the given width
	 */
	public static String getTruncatedReal(double d, int width)
	{
		if(d >= Math.pow(10, width)) //number is too big, throw exception
			return null;
		else if(d < 0 && (-d) >= Math.pow(10, width-1)) //-1 to fit in minus sign
			return null;
		else {
			String num = ""+d;
			if(num.length() > width)
				num = num.substring(0, width);				
			return num;
		}
	}
	
	
	/** A 'safe' version of getTruncatedReal(), that does not return null if the number cannot be
	 *   sensibly siplayed in the given width, but instead returns invalidResult instead
	 * 
	 * @param d
	 * @param width
	 * @param invalidResult
	 * @return
	 */
	public static String safeGetTruncatedReal(double d, int width, String invalidResult)
	{
		String res = getTruncatedReal(d, width);
		if(res == null)
			return invalidResult;
		return res;
	}
	
	
	
	/**
	 * @see rses.util.Util#getTruncatedReal(double, int)
	 */
	public static String getTruncatedReal(float f, int width)
	{
		return getTruncatedReal((double) f, width);
	}




	/** Given a Map and a value, find a key that maps to that value. Note
	 *   that this key may not be unique (in which case any of the matching
	 *   keys may be returned), and may not exist (in which case null is
	 *   returned)
	 * 
	 * @param map The Map to search
	 * @param value The value to find a key for 
	 * @return A key that maps to <code>value</code>, if one exists. <code>null</code> otherwise
	 */
	public static Object doInverseLookup(java.util.Map map, Object value)
	{
		java.util.Set keys = map.keySet();
		Object[] keyarray = keys.toArray();
		for(int i =0; i < keyarray.length; i++)
		{
			Object val = map.get(keyarray[i]);
			if(val == null && value == null) //null values match. Special case
				return keyarray[i];
			else if(val != null) { //we have a non-null mapping
				if(val.equals(value))
					return keyarray[i];
			}
		}
		return null;
	}

	public static double distance(double[] a, double[] b)
	{
		double sumsq = 0.0;
		
		for(int i = 0; i < a.length; i++)
			sumsq += (a[i]-b[i])*(a[i]-b[i]);
			
		return Math.sqrt(sumsq);
	}
	
	public static double distance(float[] a, float[] b)
	{
		double sumsq = 0.0;
		
		for(int i = 0; i < a.length; i++)
			sumsq += (a[i]-b[i])*(a[i]-b[i]);
			
		return Math.sqrt(sumsq);
	}


	public static void midpoint(double[] a, double[] b, double[] result)
	{
		for(int i =0; i < a.length; i++)
			result[i] = (a[i]+b[i])/2;
	}

	public static double norm(double[] vect)
	{
		double sumsq = 0.0;

		for(int i = 0; i < vect.length; i++)
			sumsq += vect[i]*vect[i];

		return Math.sqrt(sumsq);
	}


	private static long guidcounter = 0;
	private static Object guidlock = new Object();
	/** Obtain a 
	 * 
	 * @return
	 */public static String getUID()
	{
		Random r = new Random();
		long num;
		synchronized(guidlock)
		{
		 	if(guidcounter == Long.MAX_VALUE)
		 		throw new RuntimeException("WARNING!!!! Long Integer wraparound -- guids no longer guaranteed unique!!!");
			num = guidcounter;
			guidcounter++;
		}
		
		String prefix = "";
		for(int i =0; i < 8; i++)
			prefix = prefix + r.nextInt(10);
		return prefix+num;
	}


	 
	 
	public static String convertToHexidecimal(int nbr)
	{
		if(nbr > 15)
			return convertToHexidecimal(nbr/16)+convertToHexidecimal(nbr%16);
		else if(nbr <= 9)
			return new String(""+((char) ('0'+nbr)));
		else //between 10 and 15 inclusive
			return new String(""+((char) ('A'+(nbr-10))));
	}
	 
	 
	 
	 
	 
	public static Process executeProcess(String cmd)
	throws IOException
	{
		return executeProcess(cmd, System.out, System.err);
	}
	
	public static Process executeProcess(String cmd, OutputStream stdout, OutputStream stderr)
	throws IOException
	{
		Process p = Runtime.getRuntime().exec(cmd);

		new StreamGobbler(p.getErrorStream(), stderr).start();
		new StreamGobbler(p.getInputStream(), stdout).start();
		return p;
	}
	 
	 
	 
	 
	public static void main(String[] args)
	{
		System.out.println(convertToHexidecimal(Integer.parseInt(args[0])));
	}
	 
	public static void main2(String[] args)
	{
		float[] array = new float[10];
		Random r = new Random();

		for(int i = 0; i < 100; i++)
		{
			for(int j = 0; j < 10; j++)
				array[j] = r.nextFloat();
			
			int[] ind = quickSortIndex(array);
			printarray(ind, System.out);	
			printarray(array, System.out);
			
			quickSort(array);
			
			if(!isSorted(array))
				throw new RuntimeException("bummer");
		}

		String line = "this is a line of text ";		
		String[] words = getWords(line);
		
		for(int i = 0; i < words.length; i++)
		{
			System.out.println(words[i]);
		}

		System.out.println("-------testing GetCummulativeFrequency()-------");
		double[] darr = new double[1028];
		for(int i = 0; i < 1028; i++)
			darr[i] = Math.random();
		Util.normalize(darr);
		System.out.println("index of 0.1 is "+getCummulativeIndex(darr, 0.1));
		System.out.println("index of 0.2 is "+getCummulativeIndex(darr, 0.2));
		System.out.println("index of 0.3 is "+getCummulativeIndex(darr, 0.3));
		System.out.println("index of 0.4 is "+getCummulativeIndex(darr, 0.4));
		System.out.println("index of 0.5 is "+getCummulativeIndex(darr, 0.5));
		System.out.println("index of 0.6 is "+getCummulativeIndex(darr, 0.6));
		System.out.println("index of 0.7 is "+getCummulativeIndex(darr, 0.7));
		System.out.println("index of 0.8 is "+getCummulativeIndex(darr, 0.8));
		System.out.println("index of 0.9 is "+getCummulativeIndex(darr, 0.9));
		System.out.println("index of 1.0 is "+getCummulativeIndex(darr, 1.0));
		System.out.println("--------------");
	}
	
}


