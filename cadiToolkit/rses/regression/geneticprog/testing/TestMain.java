package rses.regression.geneticprog.testing;
import rses.Debug;
import rses.regression.geneticprog.*; 
import rses.util.Util;

import java.io.*;
import java.util.*;




public class TestMain
{
	//args[0] = directory with data
	//args[1] = maximum depth of tree
	//args[2] = number of trees
	//args[3] = interest_rate
	//args[4] = max_iterations
	public static void main(String[] args)
	throws Exception
	{
		if(args == null || args.length != 5)
		{
			System.err.println("args[0] = dir with data");
			System.err.println("args[1] = maxdepth");
			System.err.println("args[2] = numtrees");
			System.err.println("args[3] = interestrate");
			System.err.println("args[4] = max_it");
			System.exit(0);
		}
		
		Debug.setDebugLevel(Debug.DEBUG_SUSPICIOUS);
		Debug.setVerbosityLevel(Debug.INFO);

		int maxdepth = Integer.parseInt(args[1]);
		int numtrees = Integer.parseInt(args[2]);
		double interestrate = Double.parseDouble(args[3]);
		int maxiterations = Integer.parseInt(args[4]);

		File dir = new File(args[0]);
		File[] files = dir.listFiles();
		ArrayList data = new ArrayList();
	
		//read in all the data	
		for(int i = 0; i < files.length; i++)
		{
			System.out.println("getting data for "+files[i].getPath());
			StockDatum[] sd = StockDatum.getData(files[i].getAbsoluteFile());
			for(int j = 0; j < sd.length; j++)
				data.add(sd[j]);
		} 

		System.out.println("partitioning data");
		Object[][] partitions = Util.partition(data.toArray(), 6);


		for(int i = 0; i < 10; i++)
		{
			System.out.println("initializing population of trees");
			PredicterTree[] trees = new PredicterTree[numtrees];
			for(int j =0; j < numtrees; j++)
				trees[j] = new PredicterTree(maxdepth, interestrate);
			run(partitions, i, trees, maxiterations, maxdepth, interestrate);
		}
		
	}




	private static void run(Object[][] partitions, int testpartition, PredicterTree[] trees, int iterations, int maxdepth, double irate)
	{
		StockDatum[] train = getTrainingData(partitions, testpartition);
		StockDatum[] test = getTestData(partitions, testpartition);

		for(int i = 0; i < iterations; i++)
		{
			System.out.println("Starting iteration "+i+" for partition "+testpartition);
			doIteration(trees, train, maxdepth, irate);
		}
	}



	private static void doIteration(PredicterTree[] trees, StockDatum[] data, int maxdepth, double irate)
	{
		//choose tree pairs at random and try replacing existing trees
		for(int i =0; i < trees.length; i++)
		{
			double move = Math.random();
			System.out.println("breeding tree "+i+" move is "+move);

			int toreplace = (int) (Math.random()*trees.length);
			PredicterTree newtree = null;

			if(move < 0.25) //replace with random tree
				newtree = new PredicterTree(maxdepth, irate);


			//try {
			else if(move < 0.5) //splice
			{
				int index = (int) (Math.random()*trees.length);
				newtree = trees[i].breed(trees[index]);
			}
			else if(move < 0.75) //perturb
			{
				newtree = trees[i].perturb();
			}
			else //mutate
			{
				newtree = trees[i].mutate();
			}
			/*}
			catch(Exception e)
			{
				System.out.println("error breeding trees");
			}*/

			double pratio = 0.0;
			
			//try {
				double val = newtree.getLogProb(data);
				Debug.println("newtree has logprob "+val, Debug.IMPORTANT);
				double oldval = trees[toreplace].getLogProb(data);
				Debug.println("oldtree has logprob "+oldval, Debug.IMPORTANT);
				pratio = Math.exp(val-oldval);
			//}
			//catch(Exception e)
			//{}

			/*try {
			System.out.println("repeat???");
			BufferedReader rdr = new BufferedReader(new InputStreamReader(System.in));
			String line = rdr.readLine();
			if(line.equalsIgnoreCase("y"))
			{
		
				double val = newtree.getLogProb(data);
				Debug.println("newtree has logprob "+val, Debug.IMPORTANT);
				double oldval = trees[toreplace].getLogProb(data);
				Debug.println("oldtree has logprob "+oldval, Debug.IMPORTANT);
				pratio = Math.exp(val-oldval);
			}
			}
			catch(Exception e)
			{}*/

			
			
			if(Double.isNaN(pratio)) {
				Debug.println("NaN for pratio !!!!", Debug.IMPORTANT);
				Debug.println("newtree: "+newtree, Debug.IMPORTANT);
				Debug.println("oldtree: "+trees[toreplace], Debug.IMPORTANT);
				Debug.moreVerbose();
				//now reevaluate
				newtree.getLogProb(data);
				trees[toreplace].getLogProb(data);
				System.exit(0);
			}
			if(Math.random() < pratio)
				trees[toreplace] = newtree;
		}
		
		//now work out best logprob
		double best = Double.NEGATIVE_INFINITY;
		int bestindex = -1;
		for(int j = 0; j < trees.length; j++)
		{
			double lp = trees[j].getLogProb(data);
			if(lp > best) {
				best = lp;
				bestindex = j;
			}
		}
		System.out.println("best model is \n:"+trees[bestindex]);
		System.out.println("best model has log prob "+best);

	}




	private static StockDatum[] getTrainingData(Object[][] parts, int testpart)
	{
		ArrayList res = new ArrayList();
		for(int i = 0; i < parts.length; i++)
		{
			if(i == testpart)
				continue;
			for(int j = 0; j < parts[i].length; j++)
				res.add(parts[i][j]);
		}
		StockDatum[] sdres = new StockDatum[res.size()];
		for(int i =0; i < sdres.length; i++)
			sdres[i] = (StockDatum) res.get(i);
		return sdres;
	}


	private static StockDatum[] getTestData(Object[][] parts, int testpart)
	{
		StockDatum[] res = new StockDatum[parts[testpart].length];
		for(int i =0; i < res.length; i++)
			res[i] = (StockDatum) parts[testpart][i];
		return res;
	}

}




class PredicterTree
{
	private int maxdepth;
	private double interestrate;
	private NumericNode meantree;
	private NumericNode stddevtree;
	private static Random rand = new Random();
	
	public PredicterTree(int maxdepth, double irate)
	{
		this.interestrate = irate;
		meantree = NumericNode.generateRandomTree(maxdepth, StockDatum.getNodeGenerater());
		stddevtree = NumericNode.generateRandomTree(maxdepth, StockDatum.getNodeGenerater());
		this.maxdepth = maxdepth;
	}

	private PredicterTree(NumericNode mt, NumericNode sdt, double irate, int md)
	{
		this.interestrate = irate;
		this.meantree = mt;
		this.stddevtree = sdt;
		this.maxdepth = md;
	}

	public PredicterTree breed(PredicterTree partner)
	{
		NumericNode mt = this.meantree.breed(partner.meantree);
		NumericNode sdt = this.stddevtree.breed(partner.stddevtree);
		return new PredicterTree(mt, sdt, this.interestrate, this.maxdepth);
	}

	public PredicterTree perturb()
	{
		if(Math.random() < 0.5)
		{
			NumericNode mt = this.meantree.getDeepCopy();
			mt.perturb();
			NumericNode sdt = this.stddevtree.getDeepCopy();
			return new PredicterTree(mt, sdt, interestrate, this.maxdepth);
		}
		else
		{
			NumericNode mt = this.meantree.getDeepCopy();
			NumericNode sdt = this.stddevtree.getDeepCopy();
			sdt.perturb();
			return new PredicterTree(mt, sdt, interestrate, this.maxdepth);
		}
				
	}

	//we mutate by replacing part of one of the trees by 
	//a random subtree
	public PredicterTree mutate()
	{
		NumericNode mt;
		NumericNode sdt;

		//the random subtree to graft on
		int subtreesize = (int) (Math.random()*maxdepth+1);
		Debug.println("in mutate, depth of subtree is "+subtreesize, Debug.INFO);
		NumericNode randtree = NumericNode.generateRandomTree(subtreesize, StockDatum.getNodeGenerater()); 
		
		
		if(Math.random() < 0.5)
		{		
			mt = meantree.breed(randtree);
			sdt = stddevtree.getDeepCopy();
		}
		else
		{
			sdt = stddevtree.breed(randtree);
			mt = meantree.getDeepCopy();
		}
		Debug.println("finishing mutating... creating new PredicterTree", Debug.INFO);
		return new PredicterTree(mt, sdt, interestrate, this.maxdepth);
	}



	public String toString()
	{
		return "MEAN TREE: \n"+meantree+"\nSTDDEV TREE: \n"+stddevtree;
	}



	private double lpcache = Double.NaN;
	private StockDatum[] cachedata = null;
	public double getLogProb(StockDatum[] data)
	{
		if(!Double.isNaN(lpcache))
		{
			if(data != cachedata)
				throw new IllegalStateException("cached logprob for wrong data!");
			return lpcache;
		}

		double lp = 0.0;
		for(int i =0; i < data.length; i++) {
			Debug.println("getting log prob for datum "+i, Debug.EXTRA_INFO);
			Debug.println(""+data[i].toString(), Debug.EXTRA_INFO);
			double tmp = getLogProb(data[i]);
			if(Double.isNaN(tmp)) tmp = getLogProb(data[i]);
			Debug.println("log prob for datum "+i+" is "+tmp, Debug.EXTRA_INFO);
			lp += tmp;
		}
		lpcache = lp;
		cachedata = data;
		return lp;
	}


	private static final double rt2pi = Math.sqrt(2*Math.PI);

	public static final double ACCURACY = 0.001; //accurate up to 0.2% of the stock price
	public double getLogProb(StockDatum d)
	{
		//no data to predict
		if(d.isLastInChain())
			return 0.0;
		
		
		double mean = Util.squash(meantree.evaluate(d))+interestrate/365.0;
		double stddev = Math.abs(Util.squash(stddevtree.evaluate(d)));
		Debug.println("mean: "+mean+"  stddev: "+stddev, Debug.EXTRA_INFO);
		if(stddev == 0.0)
			return Math.log(Double.MIN_VALUE);
	
		double curval = d.getClose();
		double nextval = d.getNext().getClose();
		double movepct = (nextval-curval)/curval;
		
		double delta = ACCURACY;//we assume accuracy up to about to 0.2 percent
		
		double sqdif1 =(movepct-mean+delta)*(movepct-mean+delta); 
		double pd1 = 1/(rt2pi*stddev);
		double sdsq = stddev*stddev;
		pd1 *= Math.exp(-0.5*(sqdif1/(stddev*stddev)));
		
		double sqdif2 =(movepct-mean-delta)*(movepct-mean-delta); 
		double pd2 = 1/(rt2pi*stddev);
		pd2 *= Math.exp(-0.5*(sqdif2/(stddev*stddev)));

		//pd1 or pd2 can be NaN, if, for example, the standard
		//deviation is so small that it cannot be squared. 
		//There may be other cases too.
		//In these cases, we simply set the probability to 
		//being very small
		if(Double.isNaN(pd1)) {
			pd1 = Double.MIN_VALUE;
			Debug.println("setting min prob value in NaN case..", Debug.IMPORTANT);
			Debug.println("mean is "+mean+"... stddev is "+stddev, Debug.IMPORTANT);
		}
		if(Double.isNaN(pd2)) {
			pd2 = Double.MIN_VALUE;
			Debug.println("setting min prob value in NaN case..", Debug.IMPORTANT);
			Debug.println("mean is "+mean+"... stddev is "+stddev, Debug.IMPORTANT);
		}

		
		double prob = Math.abs(pd1-pd2)*delta;
		if(prob == 0.0) prob = Double.MIN_VALUE;
		return Math.log(prob);
	}






}




