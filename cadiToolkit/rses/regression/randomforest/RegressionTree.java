package rses.regression.randomforest;



import java.io.Serializable;
import java.util.ArrayList;


import rses.math.MathUtil;
import rses.regression.Datum;
import rses.util.Util;
import rses.Debug;





public class RegressionTree implements Serializable
{
	private static String nl = System.getProperty("line.separator");
	private static java.util.Random rand = new java.util.Random();
	
	public static final int RANDOMSPLITVAL=23546;
	public static final int OPTIMALSPLITVAL=23547;		
	public static final int REGRESSIONTREE = 235;
	public static final int CLASSIFIERTREE = 236;
	
	
	private int treetype = -1;
	private int nclass = -1;
	private int splitmode = RANDOMSPLITVAL;
	


	private RegressionTree lteq = null;
	private RegressionTree gt = null;

	private int splitvar = -1;
	private double splitval = Double.NaN;

	//the data associated-with/classified-by this node
	private ArrayList data = new ArrayList();

	public static final int LOG2_IN_DATA_SPLITFUNCTION = 5743;
	public static final int CONSTANT_SPLITFUNCTION = 5744;
	public static final int LINEAR_IN_DATA_SPLITFUNCTION = 5745;
	private SplitFunction splitFunction = null;


	public RegressionTree(int splitfunc, double splitconst)
	{
		this.splitFunction = new SplitFunction(splitfunc, splitconst);
	}

	RegressionTree(SplitFunction sf)
	{
		this.splitFunction = sf;
	}
	
	
	private double cachedval = Double.NaN;
	private double[] cacheddist = null;
	public void compress()
	{
		if(this.isLeaf()) 
		{
			if(this.treetype == RegressionTree.CLASSIFIERTREE)
				//note that because we cache a boosted class distribution
				//a forest of compressed trees may give slightly different
				//answers to an uncompressed version of the same forest 
				this.cacheddist = getClassDist(this.data, this.nclass, true);
			else
				this.cachedval = this.getValue();
			this.data = null;
			this.splitFunction = null;
		}
		else
		{
			this.gt.compress();
			this.lteq.compress();
		}
	}
	
	
	
	
	public void setSplitMode(int mode)
	{
		if(mode < RANDOMSPLITVAL || mode > OPTIMALSPLITVAL)
			throw new IllegalArgumentException("illegal split mode");
		this.splitmode = mode;
	}

	
	public void setSplitFunction(int type, double C)
	{
		this.splitFunction = new SplitFunction(type, C);
	}
	
	public boolean isLeaf()
	{
		return this.lteq == null;
	}


	private double getValue()
	{	
		if(this.treetype == -1)
			throw new RuntimeException("getValue() called on untrained/uninitialized tree");	
		else if(this.treetype == CLASSIFIERTREE)
			throw new UnsupportedOperationException("Trying to get regression value from classifier tree");
		else if(this.treetype != REGRESSIONTREE)
			throw new IllegalStateException("Tree has unknown type");
		
		if(!this.isLeaf())
			throw new RuntimeException("getValue() called with no data on non-leaf node. Internal error");
	
		if(!Double.isNaN(this.cachedval)) {
			Debug.println("returning cached value in node", Debug.EXTRA_INFO);
			return this.cachedval;
		}
			
		double sum = 0.0;
		int ndat = data.size();
		if(ndat == 0) throw new IllegalStateException("Impossible case reached. Empty leaf node");
		for(int i =0; i < ndat; i++)
			sum += ((Datum) (data.get(i))).getValue();
		return sum/ndat;

	}


	public int getMostProbableClass(Datum d)
	{
		double[] dist = this.getClassDistribution(d); 		
		return Util.getMaxIndex(dist);
	}
	

	public double[] getClassDistribution(Datum d)
	{
		return getClassDistribution(d, true); //boost by default
	}
	
	public double[] getClassDistribution(Datum d, boolean boost)
	{
		if(this.isLeaf())
		{
			if(this.cacheddist != null)
			{
				if(!boost)
					Debug.println("WARNING -- cached distribution is boosted, even though you have asked for an unboosted one.", Debug.IMPORTANT);
				return this.cacheddist;
			}
			if(this.treetype == -1)
				throw new RuntimeException("getValue() called on untrained/uninitialized tree");	
			else if(this.treetype == REGRESSIONTREE)
				throw new UnsupportedOperationException("Trying to get class distribution from regression tree");
			else if(this.treetype != CLASSIFIERTREE)
				throw new IllegalStateException("Tree has unknown type");

			return getClassDist(this.data, nclass, boost);
		}
		else
		{
			if(d == null) throw new IllegalArgumentException("asking for class of null Datum");
			
			if(d.getParameter(splitvar) <= splitval)
				return lteq.getClassDistribution(d);
			return gt.getClassDistribution(d);			
		}
	}
	
	public double getValue(Datum d)
	{
		if(this.isLeaf()) 
		{
			return getValue();
		}
		else
		{
			if(d.getParameter(splitvar) <= splitval)
				return lteq.getValue(d);
			return gt.getValue(d);
		}
	}


	
	
	
	public void train(Datum[] trainingdata, int nodecapacity, double errorNorm)
	{
		ArrayList datacopy = new ArrayList();
		for(int i =0; i < trainingdata.length; i++)
			datacopy.add(trainingdata[i]);
		train(datacopy, nodecapacity, errorNorm);
	}

	/** 
	 * 
	 * @param trainingdata
	 * @param nodecapacity
	 * @param nsplit
	 * @param errorNorm Has a few special meanings. If errorNorm is positive,
	 *                  then it is the value of the errorNorm to apply when 
	 *                  calculating error. That is, is errorNorm is 2 (say),
	 *                  then it is the squared error that is used.
	 *                  <p>If errorNorm is negative, then the tree is in
	 *                  classifier mode (not regression mode) and the
	 *                  number of target classes is Math.round(-errorNorm).
	 *                  <p>If errorNorm is exactly 0.0, then we are in 
	 *                  regression mode but the split decision is made purely
	 *                  on the difference between the means in any binary split.
	 */
	public void train(ArrayList trainingdata, int nodecapacity, double errorNorm)
	{
		if(!Double.isNaN(this.cachedval) || this.cacheddist != null)
			throw new RuntimeException("attempt to train a compressed tree. This is not allowed");
		
		if(this.treetype == -1) //first train of tree
		{
			if(errorNorm < 0) {
				this.treetype = CLASSIFIERTREE;
				this.nclass = (int) Math.round(-errorNorm);
			}
			else this.treetype = REGRESSIONTREE;
		}
		else if(this.treetype == CLASSIFIERTREE) 
		{
			int nspecifiedclasses = (int) Math.round(-errorNorm);
			if(nspecifiedclasses != this.nclass)
				throw new IllegalArgumentException("training tree with incompatible data set -- different number of target classes");
		}
		
		if(this.isLeaf())
		{
			//enough data to justify another split
			//so keep splitting (recursively)
			if(trainingdata.size()+data.size() > nodecapacity)
			{
				//if we already have some data, we combine
				//it with the existing data
				if(data.size() > 0)
					for(int i =0; i < data.size(); i++)
						trainingdata.add(data.get(i));

				//find a split variable and value
				Split s = findSplit(trainingdata, this.splitFunction, errorNorm, splitmode);
				//if no split is possible (due, for example,
				//to their being many multiples of the same
				//sample in the dataset to be split -- as 
				//a result of a bagged sample, for instance)
				//then we dont split.
				if(s == null) 
				{
					Debug.println("No split found...", Debug.EXTRA_INFO);
					for(int i =0; i < trainingdata.size(); i++)
						this.data = trainingdata;
					if(this.treetype == REGRESSIONTREE)
						Debug.println("NO split found. Terminal node with "+this.data.size()+" elements, and prediction val "+this.getValue(), Debug.EXTRA_INFO);
					else if(this.treetype == CLASSIFIERTREE)
						Debug.println("NO split found. Terminal node with "+this.data.size()+" elements, and class "+this.getMostProbableClass(null), Debug.EXTRA_INFO);
				}
				else //a split is possible, so we do it
				{	
					this.splitvar = s.splitvar;
					this.splitval = s.splitval;
					this.data = null;

					//now train the subtrees
					ArrayList[] splitdat = partitionData(trainingdata, splitvar, splitval);
					this.lteq = new RegressionTree(this.splitFunction);
					this.lteq.train(splitdat[0], nodecapacity, errorNorm);
					this.gt = new RegressionTree(this.splitFunction);
					this.gt.train(splitdat[1], nodecapacity, errorNorm);
				}
			}
			//not enough data to justify a split.
			else
			{
				for(int i =0; i < trainingdata.size(); i++)
					this.data.add(trainingdata.get(i));
				if(this.treetype == REGRESSIONTREE)
					Debug.println("Terminal node with "+this.data.size()+" elements, and prediction val "+this.getValue(), Debug.EXTRA_INFO);
				else if(this.treetype == CLASSIFIERTREE)
					Debug.println("Terminal node with "+this.data.size()+" elements, and prediction class "+this.getMostProbableClass(null), Debug.EXTRA_INFO);
			}
		}
		else //not a leaf node. we just palm off to our children
		{
			if(this.data != null) throw new IllegalStateException("Internal node has attached data");
			
			ArrayList lteqdata = new ArrayList();
			ArrayList gtdata = new ArrayList();

			for(int i =0; i < trainingdata.size(); i++)
			{
				Datum d = (Datum) trainingdata.get(i);
				if(d.getParameter(splitvar) <= splitval)
					lteqdata.add(d);
				else
					gtdata.add(d);
			}
			lteq.train(lteqdata, nodecapacity, errorNorm);
			gt.train(gtdata, nodecapacity, errorNorm);
		}
	}


	
	
	
	
	public double getEstimateOfMemoryRequired()
	{
		if(this.isLeaf())
			return 40+this.data.size()*((Datum)data.get(0)).getNumParameters()*8;
		else return 40+this.gt.getEstimateOfMemoryRequired()+this.lteq.getEstimateOfMemoryRequired();
	}
	
	public double getEstimateOfCompressedMemoryRequired()
	{
		if(this.isLeaf())
			return 40;
		else return 40+this.gt.getEstimateOfCompressedMemoryRequired()+this.lteq.getEstimateOfCompressedMemoryRequired();		
	}
	
	
	
	
	
	

	/** Try <code>nsplit</code> different splits on
	 *  the training data and choose the best possible split.
	 *  
	 *  If the RegressionTree is in randomsplitval mode, then
	 *  <code>nsplit</code> can be anything from <code>(1..infinity)</code>.
	 *  If we are is best split mode, then <code>nsplit</code> 
	 *  must be less than (or equal to, but then it's not a random forest) 
	 *  the number of parameters in the training data.
	 * 
	 * 
	 * @param trainingdata
	 * @param nsplit
	 * @return
	 */
	public static Split findSplit(ArrayList trainingdata, SplitFunction sf, double errorNorm, int splitmode)
	{
		Debug.println("finding split for node with "+trainingdata.size()+" data points", Debug.EXTRA_INFO);
		
		int ndat = trainingdata.size();
		Datum first = (Datum) trainingdata.get(0);
		//very first thing we do is check to see if all the
		//data in trainingdata are identical or have the
		//same target/training function value.
		//In the first case, we cant split, in the
		//second case, there isnt much point
		boolean identical = true;
		boolean fvalidentical = true;
		double fval = first.getValue();
		for(int i=1; i < ndat && (identical || fvalidentical); i++)
		{
			Datum d = (Datum) trainingdata.get(i);
			if(d != first) 
				identical = false;
			if(d.getValue() != fval)
				fvalidentical = false;
		}
		if(identical || fvalidentical) {
			Debug.println("All values in node are identical. Not splitting",Debug.INFO);
			return null; //not possible (or useful) to split identical data
		}
	
		
		int nparam = first.getNumParameters();
		double besterr = Double.POSITIVE_INFINITY;
		int bestsplitvar = -1;
		double bestsplitval = Double.NaN;

		
		if(ndat <= 1)
			throw new IllegalArgumentException("trying to split with less than 2 data points");
				
		
		int nsplit = sf.getSplitTries(ndat);
		if(nsplit <= 0) throw new IllegalStateException();
		
		//these are all the splitvars that we try
		Object[] splitvars;
		if(splitmode == RANDOMSPLITVAL) //repeated attempts to split on the same variable are OK
		{
			splitvars = new Object[nsplit];
			for(int i =0; i < nsplit; i++)
				splitvars[i] = new Integer((int) (Math.random()*nparam));
		}
		else if(splitmode == OPTIMALSPLITVAL) //repeated tries make no sense (because we choose the best split)
		{
			Integer[] sptmp = new Integer[nparam];
			for(int i = 0; i < sptmp.length; i++)
				sptmp[i] = new Integer(i);
			splitvars = rses.util.Util.getSubSample(sptmp, nsplit);			
		}
		else 
		{
			throw new IllegalArgumentException("Unknown split mode specified");
		}
		
		
		Debug.println("trying to split on "+splitvars.length+" splitvars in tree node", Debug.EXTRA_INFO);
		
		//try nsplit different split variables
		tryloop: for(int i =0; i < nsplit; i++)
		{
			int tryvar = ((Integer) splitvars[i]).intValue();
			Debug.println("Trying split on variable "+tryvar, Debug.EXTRA_INFO);
			
			//two cases, either 
			//1)pick a split value at random
			//OR
			//2)try all possible split values
			double tryval = Double.NaN;
			if(splitmode == RANDOMSPLITVAL) //pick a split value at random
			{
				//we do this by choosing 2 points at random
				//from the training points and choosing a 
				//random split-point between these two
				int p1 = rand.nextInt(ndat);
				int p2 = rand.nextInt(ndat);
				while(p2 == p1)
					p2 = rand.nextInt(ndat);

				//choose uniformly from the range [lwr,upper]
				double lwr = ((Datum) trainingdata.get(p1)).getParameter(tryvar);
				double upr = ((Datum) trainingdata.get(p2)).getParameter(tryvar);
				if(lwr == upr) //no split possible
				{
					Debug.println("No partition of data possible on variable "+tryvar, Debug.EXTRA_INFO);
					continue;
				}
				tryval = lwr+rand.nextDouble()*(upr-lwr);
				
				if(tryval == upr) {
					Debug.println("WARNING-- splitvalue tight on upper bound... substituting alternate value", Debug.CRITICAL);
					tryval = (upr+lwr)/2;
				}
	
				Debug.println("....trying split on value "+tryval, Debug.EXTRA_INFO);
				ArrayList[] dsplit = partitionData(trainingdata, tryvar, tryval);
					
				if(dsplit[0].size() == 0 || dsplit[1].size() == 0)
					throw new IllegalStateException("Empty partition -- should not be possible");

				if(Math.min(dsplit[0].size(), dsplit[1].size()) < trainingdata.size()/16) 
				{
					//split is too small. we reject it
					Debug.println("Rejecting small partition", Debug.EXTRA_INFO);
					continue;
				}
				
				double err;
				if(errorNorm == 0.0) //special regression case where we maximize difference in means
					err = -calcDifferenceInMeans(dsplit[0], dsplit[1]);
				else
					err = calculateError(dsplit[0], dsplit[1], errorNorm);
				Debug.println("Error for split is "+err, Debug.EXTRA_INFO);
				if(err < besterr)
				{
					bestsplitvar = tryvar;
					bestsplitval = tryval;
					besterr = err;
				}
				
				if(err == 0.0 && errorNorm != 0.0) //no need to try others, take this one
				{
					Debug.println("Found perfect split in leaf node. No need to try others", Debug.EXTRA_INFO);
					break tryloop;
				}
			}
			else //we try every possible split value and choose the best one
			{
				//note that this operation has n^2 complexity
				//so we really should have some way of restricting 
				//partitioning to a few different values
				
				//first we sort the data values
				double[] vals = new double[ndat];
				for(int j =0; j < ndat; j++)
					vals[j] = ((Datum) trainingdata.get(j)).getParameter(tryvar);
				java.util.Arrays.sort(vals); 
				
				//next we get rid of duplicates
				ArrayList uniq = new ArrayList();
				uniq.add(new Double(vals[0]));
				for(int j = 1; j < vals.length; j++)
					if(vals[j] != vals[j-1])
						uniq.add(new Double(vals[j]));
				double[] uniqvals = new double[uniq.size()];
				for(int j = 0; j < uniqvals.length; j++)
					uniqvals[j] = ((Double) uniq.get(j)).doubleValue();
				
				//and last, we calculate all split vals
				double[] totry = new double[uniqvals.length-1];
				for(int j = 0; j < totry.length; j++)
					totry[j] = (uniqvals[j]+uniqvals[j+1])/2.0;

				for(int j = 0; j < totry.length; j++)
				{
					tryval = totry[j];
					Debug.println("....trying split on value "+tryval, Debug.EXTRA_INFO);
					ArrayList[] dsplit = partitionData(trainingdata, tryvar, tryval);
					double err = calculateError(dsplit[0], dsplit[1], errorNorm);
					Debug.println("Error for split is "+err, Debug.EXTRA_INFO);
					if(err < besterr)
					{
						bestsplitvar = tryvar;
						bestsplitval = tryval;
						besterr = err;
					}
					
					if(err == 0.0)
					{
						Debug.println("Found perfect split in leaf node. No need to try others", Debug.INFO);
						break tryloop;
					}
				}
			}
		}
		
		if(bestsplitvar < 0) //no split found
		{
			Debug.println("didnt find a split in node.... not splitting", Debug.EXTRA_INFO);
			return null;
		}
		Debug.println("OK, FOUND SPLIT: splitting on variable "+bestsplitvar+" with value "+bestsplitval, Debug.EXTRA_INFO);
		return new Split(bestsplitvar, bestsplitval);
		
	}

	
	private static double calcDifferenceInMeans(ArrayList a, ArrayList b)
	{
		double m1 = 0.0;
		double m2 = 0.0;
		int alen = a.size();
		int blen = b.size();
		for(int i = 0; i < alen; i++)
			m1 += ((Datum) a.get(i)).getValue();
		for(int i = 0; i < blen; i++)
			m2 += ((Datum) b.get(i)).getValue();

		return Math.abs(m1/alen-m2/blen);
	}
	
	


	public static ArrayList[] partitionData(ArrayList data, int var, double val)
	{
		int ndat = data.size();
		ArrayList left = new ArrayList(ndat);
		ArrayList right = new ArrayList(ndat);
		
		
		for(int i =0; i < ndat; i++)
		{
			Datum d = (Datum) data.get(i);
			if(d.getParameter(var) <= val)
				left.add(d);
			else 
				right.add(d);
		}
		return new ArrayList[] {left, right};
	}

	
	
	
	private static double[] getClassDist(ArrayList data, int nclasses, boolean boost)
	{
		Datum[] d = new Datum[data.size()];
		for(int i =0; i < d.length; i++)
			d[i] = (Datum) data.get(i);
		return getClassDist(d,nclasses, boost);
		
	}
	
	private static double[] getClassDist(Datum[] a, int nclasses, boolean boost)
	{
		double[] dist = new double[nclasses]; 
		for(int i =0; i < a.length; i++) { 
			int clazz = (int) Math.round(a[i].getValue());
			dist[clazz] += 1;
		}
		
		//if there are any 0s, we replace them with 1s
		if(boost)
		{
			for(int i = 0; i < dist.length; i++)
			{
				if(dist[i] == 0.0) {
					Debug.println("0 count for class replaced with 1", Debug.INFO);
					dist[i] = dist[i]+1;
				}
			}
		}
		
		Util.normalize(dist);
		return dist;
	}
	
	
	public static double[] getClassDistribution(RegressionTree[] forest, Datum d)
	{
		if(forest[0].treetype != CLASSIFIERTREE) throw new UnsupportedOperationException("Trying to get class distribution in regression mode");

		double[] res = forest[0].getClassDistribution(d);
		
		for(int i = 1; i < forest.length; i++)
		{
			if(forest[i].treetype != CLASSIFIERTREE) throw new UnsupportedOperationException("Trying to get class distribution in regression mode");
			double[] dist = forest[i].getClassDistribution(d);
			for(int j=0; j < dist.length; j++)
				res[j] += dist[j];
		}
		
		Util.normalize(res);
		return res;
	}
	

	/*
	public double calculateError(ArrayList data, double errorNorm)
	{
		double totalerror = 0.0;
		int datalen = data.size();
		for(int i = 0; i < datalen; i++)
		{
			Datum d = (Datum) data.get(i);
			double pred = this.getValue(d);
			double actual = d.getValue();
			if(errorNorm < 0)
				totalerror += 1.0;
			else if(errorNorm > 0){
				double err = Math.abs(Math.pow(pred-actual, errorNorm));
				totalerror += err;
			}
			else throw new IllegalStateException("Error norm of 0.0 makes no sense in calculateError() method. This code should never be reached. This is a bug."); 
		}

		return totalerror;
	}*/

	
	public static double calculateError(ArrayList a, ArrayList b, double errorNorm)
	{
		double err = 0.0;
		
		int alen = a.size();
		int blen = b.size();
		
		if(errorNorm >= 0) //regression 
		{ 
			double left = 0.0;
			double right = 0.0;
			for(int i =0; i < alen; i++) 
				left = left + ((Datum) a.get(i)).getValue();
			left = left/alen; //the prediction for a
			for(int i =0; i < alen; i++)
				err += Math.abs(Math.pow(left-((Datum) a.get(i)).getValue(), errorNorm));

			for(int i =0; i < blen; i++)
				right = right + ((Datum) b.get(i)).getValue();
			right = right/blen; //the prediction for b
			for(int i =0; i < blen; i++)
				err += Math.abs(Math.pow(right-((Datum) b.get(i)).getValue(), errorNorm));
		}
		else { //classification
			int nclass = (int) Math.round(-errorNorm);
			double[] leftdist = getClassDist(a, nclass, true);
			double[] rightdist = getClassDist(b, nclass, true);
			for(int i =0; i < alen; i++) {
				int clazz = (int) Math.round(((Datum) a.get(i)).getValue());
				double prob = leftdist[clazz];
				err += Math.log(prob);
			}
			for(int i =0; i < blen; i++) {
				int clazz = (int) Math.round(((Datum) b.get(i)).getValue());
				double prob = rightdist[clazz];
				err += Math.log(prob);
			}
		}

		return err;
	}



	public String toString()
	{
		return this.toString(0);
	}

	private String toString(int depth)
	{
		String indent = "";
		for(int i =0; i < depth; i++)
			indent = indent + "\t";
		String result = "";
		if(this.isLeaf())
		{
			if(this.treetype == REGRESSIONTREE)
				result = indent+this.getValue();
			else if(this.treetype == CLASSIFIERTREE) {
				if(this.cacheddist != null)
					result = indent+Util.arrayToString(cacheddist);
				else
					result = indent+Util.arrayToString(getClassDist(this.data, nclass, true));
			}
			else if(this.treetype == -1)
				throw new RuntimeException("Uninitialized tree");
			else throw new IllegalStateException("Tree has unknown type");
		}
		else
		{
			result = indent+"if "+this.splitvar+" <= "+this.splitval+nl;
			result = result + lteq.toString(depth+1)+nl;
			result = result + indent+"else"+nl;
			result = result + gt.toString(depth+1);
		}
		return result;
	}



}



class Split
{
	Split(int svar, double sval)
	{
		this.splitvar = svar; this.splitval = sval;
	}
	
	int splitvar;
	double splitval;
}




class SplitFunction implements Serializable {
	private double c;
	private int type;
	SplitFunction(int t, double c) 
	{ 
		if(t != RegressionTree.LOG2_IN_DATA_SPLITFUNCTION && 
		   t != RegressionTree.LINEAR_IN_DATA_SPLITFUNCTION &&
		   t != RegressionTree.CONSTANT_SPLITFUNCTION)
			throw new IllegalArgumentException("Invalid Split Function specified.");
		this.type = t; this.c = c; 
	}
	
	
	int getSplitTries(int numdata)
	{
		if(type == RegressionTree.LOG2_IN_DATA_SPLITFUNCTION)
			return (int) Math.max(c*Util.log_2(numdata), 1);
		else if(type == RegressionTree.LINEAR_IN_DATA_SPLITFUNCTION)
			return (int) Math.max(Math.round(numdata*c), 1);
		else if(type == RegressionTree.CONSTANT_SPLITFUNCTION)
			return (int) c;
		else
			throw new IllegalStateException("Impossible case reached. This is a bug");

	}
}