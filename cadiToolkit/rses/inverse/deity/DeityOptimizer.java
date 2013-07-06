package rses.inverse.deity;


import java.util.ArrayList;


import rses.Debug;
import rses.Model;
import rses.inverse.ObservableInverter;
import rses.inverse.InteractiveInverter;
import rses.inverse.ModelObserver;
import rses.inverse.UserFunctionHandle;
import rses.inverse.powell.PowellMin;
import rses.util.FileUtil;
import rses.util.Util;
import rses.util.distributed.NodeInfo;



/** Recursive Hypercubing Optimizer.
 * 
 *  The idea is roughly to do a kind of 
 *  repeated depth first search through 
 *  a tree, with the tree being a hypercube
 *  breakdown of the parameter space. So 
 *  at each decision point, we partition the
 *  entire search space into two, and 
 *  make a guess (based on a few samples) on
 *  which direction to head in. While in any
 *  individual search, this may be a bad strategy,
 *  repeating this multiple times results in 
 *  better results. It also has the advantage that
 *  we never absolutely cut off any section of space.
 * 
 * 
 *  Currently, a distributed version of this algorithm
 *  is not implemented, and wont be any time soon.
 * 
 * @author peterr
 */
public class DeityOptimizer implements ObservableInverter, InteractiveInverter, ModelObserver
{
	private UserFunctionHandle handle;
	private NodeInfo[] nodes = null;
	private Model best = null;
	private double maxprecision;


	
	

	/** 
	 * 
	 * @param h The user supplied misfit evaluater
	 * @param precision Two points that differ in misfit 
	 *        by less then this amount are considered to
	 *        be on a plateau (i.e.\ they are equally good).
	 */
	public DeityOptimizer(UserFunctionHandle h, double precision)
	{
		this.handle = h;
		this.maxprecision = precision;
	}
	
	
	private long startTime;
	private long runtime = Long.MAX_VALUE;
	boolean isFinished = false;
	public void run()
	{
		startTime = System.currentTimeMillis();
		if(nodes == null)
			doLocal();
		else
			doDistributed();
		this.isFinished = true;
		Debug.println("Inversion/Optimization finished", Debug.IMPORTANT);
	}
	
	
	public synchronized void setTimeToRun(long millisecs)
	{
		this.runtime = millisecs;
	}
	

	
	private void doLocal()
	{
		//generate a very small number of random models to 
		//get a very rough idea of what sort of scale we 
		//are talking about with our misfit function
		for(int i =0; i < handle.getDimensionOfModelSpace(); i++)
		{
			Model m = new Model(handle.getBoundsOnModelSpace());
			m.setMisfit(handle.getErrorForModel(m.getModelParameters()));
			this.newModelFound(m);
		}
		double initialmf = best.getMisfit();
		
		//first of all, we try to get a quick but good model
		//by doing hypercubing starting at rough precision
		//and getting finer and finer
		Debug.println("Doing quick initial runs at rough precision in hypercuber", Debug.IMPORTANT);
		double precision = (maxprecision + best.getMisfit()/2)/2;
		while(precision > maxprecision)
		{ 
			this.generateModels(precision, 1); //do quick searching
			precision /= 2;
		}

		
		Debug.println("Going into general search mode in hypercuber", Debug.IMPORTANT);
		//now go into the more general mode, where we 
		//try increasing sample sizes at high precision
		int n = 1;
		while(!isFinished() && this.getRunningTime() < this.runtime)
		{
			this.generateModels(maxprecision, n);
			n++;
		}
	}
	
	
	
	
	// Generate nsam models in each of two subcubes, and choose
	// the sub-cube with the best model. Then repeat, until
	// we get to a plateau.
	private void generateModels(double precision, int nsam)
	{
		double[][] bounds = Util.copy(handle.getBoundsOnModelSpace());
		
		Model tmp = new Model(bounds);
		tmp.setMisfit(handle.getErrorForModel(tmp.getModelParameters()));


		while(!isFinished() && this.getRunningTime() < runtime)
		{	
			Model bestthisiteration = new Model(bounds);
			bestthisiteration.setMisfit(handle.getErrorForModel(bestthisiteration.getModelParameters()));
			Model worstthisiteration = new Model(bounds);
			worstthisiteration.setMisfit(handle.getErrorForModel(worstthisiteration.getModelParameters()));
			if(worstthisiteration.getMisfit() < bestthisiteration.getMisfit())
			{
				Model tmpswap = bestthisiteration;
				bestthisiteration = worstthisiteration;
				worstthisiteration = tmpswap;
			}
			this.newModelFound(bestthisiteration);
			this.newModelFound(worstthisiteration);

			ArrayList goodmodels = new ArrayList();
			if(bestthisiteration.getMisfit()-precision < tmp.getMisfit())
				goodmodels.add(bestthisiteration);
				
			

			for(int i =0; i < nsam; i++) 
			{
				Model mod = new Model(bounds);
				mod.setMisfit(handle.getErrorForModel(mod.getModelParameters()));

				if(mod.getMisfit() < bestthisiteration.getMisfit())
					bestthisiteration = mod;
				if(mod.getMisfit() > worstthisiteration.getMisfit())
					worstthisiteration = mod;

				if(mod.getMisfit()-precision < tmp.getMisfit())
					goodmodels.add(mod);

				this.newModelFound(mod);
			}

			
			//we didnt find a model better (or equal to) the current best model
			//in either subcube. So we choose the subcube that contains
			//the current best model.
			if(goodmodels.size() == 0)
			//if(bestthisiteration.getMisfit()-precision > tmp.getMisfit())
			{
				Debug.println("No models close to previous best model... defaulting to best model in previous hypercube", Debug.INFO);
				goodmodels.add(tmp);
				bestthisiteration = tmp;
			}

			
			
			double worst = worstthisiteration.getMisfit();
			double best = bestthisiteration.getMisfit();
			Debug.println("bestmf = "+best+" worst="+worst, Debug.INFO);
			if((worst-best) < precision)
			{
				//reached plateau, since worst-best is pretty much the same
				Debug.println("reached specified precision.... exiting", Debug.INFO);

				//do a powell minimization
				
				//first, we need to set up the scale factors for the powell min.
				//we use the current hypercube dimensions to get the scale
				//factors.
				double[] scalevect = new double[bounds.length];
				double[][] fullbounds = handle.getBoundsOnModelSpace();
				for(int i =0; i < scalevect.length; i++) 
				{
					scalevect[i] = (0.5*(bounds[i][1]-bounds[i][0]))/(fullbounds[i][1]-fullbounds[i][0]);
					if(scalevect[i] == 0.0) 
					{
						Debug.println("Cannot set sensible scale vector for parameter "+i+" in Powell descent... setting to default guess", Debug.IMPORTANT);
						scalevect[i] = 0.00001;
					}
				}
				PowellMin powell = new PowellMin(handle, scalevect, precision, bestthisiteration);
				powell.addModelObserver(this);
				new Thread(powell).start();
				while(!powell.isFinished())
				{
					try { Thread.sleep(1000); }
					catch(InterruptedException ie) {}
				}
				break;
			}

			
			//create a new hypercube on a random model that
			//is better than or 'close enough' to the best model 
			//in the previous hypercube 
			Model tozoom = (Model) goodmodels.get((int) (Math.random()*goodmodels.size()));
			getNewBounds(bounds, tozoom);
			tmp = tozoom;
			
			//getNewBounds(bounds, bestthisiteration);
			//tmp = bestthisiteration;
		}		
	}
	
	
	
	
	
	

	
	
	private void getNewBounds(double[][] bounds, Model m)
	{
		int splitvar = (int) (Math.random()*bounds.length);
		double pval = m.getModelParameter(splitvar);
		double step = (bounds[splitvar][1]-bounds[splitvar][0])/4;
		double lwr = Math.max(pval-step, bounds[splitvar][0]);
		double upr = Math.min(pval+step, bounds[splitvar][1]);
		Debug.println("Splitting on "+splitvar+": ["+lwr+" ... "+upr+"]   (mf="+m.getMisfit()+")", Debug.INFO);
		bounds[splitvar][0] = lwr;
		bounds[splitvar][1] = upr;
	}
	
	
	
	
	
	
	
	private void doDistributed()
	{
		throw new UnsupportedOperationException("Distributed version not implemented yet");
	}
	
	
	
	public UserFunctionHandle getUserFunctionHandle()
	{
		return handle;
	}
	
	

	public synchronized boolean isFinished()
	{
		return this.isFinished;
	}
	
	
	public Model getBestModel()
	{
		return best;
	}
	
	
	
	public synchronized long getRunningTime()
	{
		if(this.startTime < 0) return 0;
		return System.currentTimeMillis()-this.startTime;
	}
	
	
	
	
	public double getStage()
	{
		return Double.NaN;
	}
	
	
	public String getStageName()
	{
		return null;
	}
	
	
	
	private ArrayList observers = new ArrayList();
	public void addModelObserver(rses.inverse.ModelObserver obs)
	{
		observers.add(obs);
	}
	
	
	public void removeModelObserver(rses.inverse.ModelObserver obs)
	{
		observers.remove(obs);
	}
	
	
	public void newModelFound(Model m)
	{
		m.setDiscoveryTime(this.getRunningTime());
		
		if(best == null || m.getMisfit() < this.best.getMisfit()) 
			this.best = m;
		
		for(int i =0; i < this.observers.size(); i++)
			((rses.inverse.ModelObserver) this.observers.get(i)).newModelFound(m);
		
	}
	
	
	public void newModelsFound(Model[] models)
	{
		for(int i =0; i < models.length; i++)
			newModelFound(models[i]);
	}
	

	public synchronized void stopInversion()
	{
		this.isFinished = true;
	}
	
	
	
	
	
	
	//args[0] = rsesToookitDir
	public static void main(String[] args) throws Exception
	{
		UserFunctionHandle h = FileUtil.guiGetUserFunctionHandle("", null, new java.io.File("."), args[0], false);
		DeityOptimizer opt = new DeityOptimizer(h, 0.001);
		opt.run();
	}
	
	
	

}