package rses.inverse.mcmc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


import rses.Model;
import rses.ModelGenerator;
import rses.inverse.InteractiveInverter;
import rses.inverse.ModelObserver;
import rses.inverse.ObservableInverter;
import rses.inverse.UserFunctionHandle;
import rses.inverse.powell.PowellMin;
import rses.math.DensityFunction;
import rses.math.RealFunction;
import rses.util.Util;
import rses.util.distributed.DistributableTask;
import rses.util.distributed.TaskThread;
import rses.util.distributed.NodeInfo;
import rses.util.distributed.RemoteForward;
import rses.Debug;




/** For this MCMC implementation to work, the users getErrorForModel() function 
 *  must return the <i>negative</i> logarithm of the value of probability 
 *  density function for each model (up to an additive constant). In other
 *  words, if P(x) is the (unknown) ppd you wish to sample from, then the 
 *  error for each model must be equal to -(C+ln(P(x))) for some constant C.
 *  (C constant across models, of course).
 *  <p>
 *  As an example, if you have N variables and assume each is gaussian with
 *  mean 0 and variance 1, then you can simply return the sum of
 *  squares*0.5*prior.
 *
 * 
 *  <p>
 *  The general idea is that we have multiple Markov Chains. The proposal
 *  distribution for each of these is a small Gaussian step. We ask
 *  for the next accepted move from that chain, and use this as the
 *  next proposal move for one of the other chains. We can accept it
 *  without doing a metropolis test because it is generated from the
 *  posterior. This allows for mixing between all of the chains. 
 *  <p>
 *  This class contains code to calculate the evidence (using samples
 *  from the posterior and samples from a uniform distribution). However,
 *  on most problems of high enough dimension and/or complexity, these
 *  estimates often take a long time to converge. 
 *  
 * 
 * 
 * 
 * @author peterr
 *
 */


public class MarkovChainMonteCarloInverter implements ObservableInverter, InteractiveInverter
{	
	//these two no longer used, since we have abandoned adaptive MCMC for the moment
	public static final double MAX_STDDEV = 0.25;
	public static final double MIN_STDDEV = 0.0001;
	
	
	public static final double DEFAULT_INITIAL_STDDEV = 0.1; 
	
	private List observers = new java.util.Vector();
	protected double initialstddev = DEFAULT_INITIAL_STDDEV;
	
	private rses.math.DensityFunction densityFunction;
	
	private UserFunctionHandle handle;
	
	private String densityFile;
	
	protected boolean isDistributed = false;
	protected NodeInfo[] nodelist = null;
	
	
	private double besterr = Double.POSITIVE_INFINITY;
	private MCMC_Model bestModel = null;
	
	private java.util.Random rand = new java.util.Random();

	
	private int numchains = -1;
	

	private double local_opt_time = Double.NaN;
	
	
	//for every model drawn from the posterior and evaluated, this 
	//number of uniformly generated models are evaluated to 
	//estimate the evidence. This way, we have two estimates of the
	//evidence -- one from the posterior sampling and one from
	//uniform. A value of 0 indicates no uniform sampling, so
	//the only estimate you will get of evidence is the posterior
	//one, which comes more or less for 'free' in terms of 
	//computational effort, but which has high variance.
	private int numEvdSamples = 0;
	
	
	//the area of the space we integrate over when we estimate
	//the evidence
	private double scalefact = 1.0;
	
	
	public static MarkovChainMonteCarloInverter getDistributedInstance(NodeInfo[] nodes,
	    UserFunctionHandle h, double stddev,
	    int numDensityBins, String densityLogfile,
		int numchains, int numEvidenceSamples, double local_opt_pct)
	{
		if(numchains < nodes.length)
			throw new IllegalArgumentException("In Distributed MCMC, number of chains is less than number of compute nodes. This is wasteful...");
		MarkovChainMonteCarloInverter inv = new MarkovChainMonteCarloInverter(
		    h, stddev, numDensityBins, densityLogfile, numchains, numEvidenceSamples,
			local_opt_pct);
		inv.isDistributed = true;
		inv.nodelist = nodes;
		return inv;
	}

	
	
	
	

	public MarkovChainMonteCarloInverter(UserFunctionHandle h, double stddev,  
		int numDensityBins, String densityLogfile,
		int numchains, int numEvidenceSamples, double local_opt_pct)
	{
		this(h, numDensityBins, densityLogfile, numchains, numEvidenceSamples, local_opt_pct);
		this.initialstddev = stddev;
	}

	
	
	/** 
	 * @param h the UserFunctionHandle to use in the inversion
	 * @param numDensityBins The number of density bins with which to divide each parameter,
	 *                              when calculating the density function.
	 *  
	 */
	public MarkovChainMonteCarloInverter(UserFunctionHandle h,  
		int numDensityBins, String densityLogfile,
		int numchains, int numEvidenceSamples, double local_opt_pct)
	{
		this.handle = h;
		this.densityFile = densityLogfile;
		this.densityFunction = new rses.math.DensityFunction(h.getDimensionOfModelSpace(), h.getBoundsOnModelSpace(), numDensityBins);
		this.prior = Model.getUniformModelGenerator(h.getBoundsOnModelSpace());
		this.numchains = numchains;
		this.numEvdSamples = numEvidenceSamples;
		this.local_opt_time = local_opt_pct;
		
		//NB: we assume that the posterior is non-zero
		//over the entire range....
		double[][] bounds = handle.getBoundsOnModelSpace();
		for(int i =0; i < bounds.length; i++)
			scalefact *= (bounds[i][1]-bounds[i][0]);
	}

	
	
	public DensityFunction getDensityFunction()
	{
		return this.densityFunction;
	}
	
	
	private void internalRun() throws Exception
	{		
		if(isDistributed)
			doDistributed();
		else
			doLocal();
	}
	
	
	
	
	public static MCMC_Model doLocalOpt(UserFunctionHandle handle, MCMC_Model m, List observers, long runtime, long maxruntime)
	{
		PowellMin powell = PowellMin.getAdaptivePowell(handle, 1.0, runtime);
		if(observers != null) for(int i =0; i < observers.size(); i++)
			powell.addModelObserver((ModelObserver) observers.get(i));
		new Thread(powell).start();
		while(!powell.isFinished())
		{
			try { Thread.sleep(runtime/100); }
			catch(InterruptedException ie) {}
		}
		m = new MCMC_Model(powell.getBestModel().getModelParameters(), -1);
		double prior = handle.getPriorForModel(m.getModelParameters());
		m.setMisfit(powell.getBestModel().getMisfit(), prior);
		m.setAccepted(true);
		
			
		Debug.println("Best model after local optimization has misfit "+m.getMisfit(), Debug.IMPORTANT);
		return m;
	}
	
	
	
	
	/** Get a local optimization task that can be distributed on a remote node
	 * 
	 * @param h
	 * @param start
	 * @param burnin
	 * @param runtime
	 * @param maxruntime
	 * @return
	 */
	public static DistributableTask getLocalOptTask(UserFunctionHandle h, MCMC_Model start, long runtime, long maxruntime)
	{
		class LocalOptModGen extends ModelGenerator
		{
			MCMC_Model start; 
			long runtime; long maxruntime;
			LocalOptModGen(MCMC_Model start, long runtime, long maxruntime) 
			{ 
				this.start = start;
				this.runtime = runtime;
				this.maxruntime = maxruntime;
			}
			
			public Model generateModel()
			{
				if(this.getMisfitEvaluator() == null)
					throw new RuntimeException("misfit evaluater not set in Local Opt model generator... but it should be");
				UserFunctionHandle h = this.getMisfitEvaluator();
				
				return MarkovChainMonteCarloInverter.doLocalOpt(h, start, null, runtime, maxruntime);
			}
		};
		
		ModelGenerator locmg = new LocalOptModGen(start, runtime, maxruntime);
		ModelGenerator oneshot = ModelGenerator.getOneShotModelGenerator(locmg);
		return new RemoteForward(h, oneshot);
	}
	
	
	

	public void doLocal()
	{
		double[] stddevs = new double[handle.getDimensionOfModelSpace()];
		Util.initializeArray(stddevs, this.initialstddev);

		double[][] bounds = handle.getBoundsOnModelSpace();

		Debug.println("Starting local MCMC with "+numchains+" chains", Debug.IMPORTANT);
		
		//we need numchains seperate chains
		MCMC_Model[] m = new MCMC_Model[this.numchains]; 
		for(int i =0; i < m.length; i++) 
		{
			m[i] = generateModelAccordingToPrior();
			if(!m[i].isMisfitAvailable())
			{
				double prior = handle.getPriorForModel(m[i].getModelParameters());
				m[i].setMisfit(handle.getErrorForModel(m[i].getModelParameters()), prior);
			}
			m[i].setAccepted(true);
			newModelsFound(m[i], new MCMC_Model[] {m[i]});
			
			//run a local optimizer first to get a reasonable model
			m[i] = doLocalOpt(handle, m[i], observers, (long) ((endTime-startTime)*local_opt_time)/numchains, endTime);
		}
		
		MCMC_ModelGenerator[] mg = new MCMC_ModelGenerator[numchains];
		for(int i =0; i < numchains; i++)
		{
			mg[i] = (MCMC_ModelGenerator) this.getModelGenerator(m[i], bounds, -1, stddevs);
			mg[i].setMisfitEvaluator(this.handle);
		}
		
		int chainnum = 0;
		while(!isStopped && System.currentTimeMillis() < endTime)
		{
			//generate new model from a random chain.
			//Because this model comes from a markov chain, 
			//this model is thus generated from the posterior
			//distribution, and we can accept it without
			//performing a metropolis test
			int generatingchain = rand.nextInt(numchains);
			
			MCMC_Model newm = (MCMC_Model) mg[generatingchain].generateNewModel();
			Debug.println("generating model from chain number "+generatingchain, Debug.EXTRA_INFO);

			int changed = newm.whichAxisChanged(); //remember, this can be -1
				
			if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
			{
				if(!newm.isValid())
					throw new IllegalStateException("ModelGenerater generated invalid model");
				else if(!newm.isMisfitAvailable())
					throw new IllegalStateException("Misfit not set on local model");
			}

			//update uniform evidence estimate
			if(this.numEvdSamples > 0) 
			{
				this.uevidencesum += newm.getEvidenceSum();
				this.unumevd += newm.getNumberOfEvidenceSamples();
			}
			
			//update the chain we are altering. 
			MCMC_Model orig = m[chainnum];
			if(newm.isAccepted()) m[chainnum] = newm;
			else m[chainnum] = m[generatingchain];
			mg[chainnum].setCurrentModel(m[chainnum]);
						
			newModelsFound(m[generatingchain], new MCMC_Model[] {newm});
			chainnum = (chainnum+1) % numchains;

		}
	}
	
	

	private MCMC_Model[] oldModels = null;	
	//this method should never return
	private void doDistributed()
	{
		if(!this.isDistributed || this.nodelist == null)
			throw new IllegalStateException("doDistributed() called in non-distributed instance of MCMC");
		
		int chainspernode = (int) Math.round(((double)numchains)/nodelist.length);
		Debug.println("There will be "+chainspernode+" chains per thread", Debug.IMPORTANT);
		int totalchains = chainspernode*nodelist.length;
		if(totalchains > numchains) //make #chains a multiple of # of nodes
			Debug.println("Boosted number of chains from "+numchains+" to "+totalchains+" for running on "+nodelist.length+" nodes", Debug.IMPORTANT);
		numchains = totalchains;
		
		//the step size is the same for each chain
		double[][] stddevs = new double[numchains][handle.getDimensionOfModelSpace()];
		for(int i =0; i < stddevs.length; i++)
			Util.initializeArray(stddevs[i], this.initialstddev);

		//generate initial models and evaluate them
		Debug.println("generating threads in MCMC inverter", Debug.INFO);			
		oldModels = new MCMC_Model[numchains]; 
		for(int i =0; i < numchains; i++) 
			oldModels[i] = this.generateModelAccordingToPrior();

		//evaluate the models, if that is needed
		RemoteForward.evaluateModels(handle, oldModels, nodelist);

		//start tasks with Local Optimizer first, to get a 'good' model
		//we locally optimize every chain
		List threads = new ArrayList();
		for(int j =0; j < chainspernode; j++)
		{
			ArrayList taskList = new ArrayList();
			for(int i =j*nodelist.length; i < (j+1)*nodelist.length; i++)
			{
				if(!oldModels[i].isMisfitAvailable())
					throw new IllegalStateException("misfit unavailable for intial model in chain");
				oldModels[i].setAccepted(true); //always accept first model 
				//taskList.add(new RemoteForward(handle, this.getModelGenerator(oldModels[i], handle.getBoundsOnModelSpace(), 1, stddevs[i])));
				long rt = this.endTime-this.startTime;
				taskList.add(getLocalOptTask(handle, oldModels[i], (long) (local_opt_time*rt)/chainspernode, rt));
			}
			Debug.println("Starting task threads on remote nodes....", Debug.INFO);
			List tmpthreads = TaskThread.getThreadList(this.nodelist, taskList, true, true);
			threads.addAll(tmpthreads);
		}
		
		//OK, so currently, there is a local optimizer working on every 
		//chain
		
		Debug.println("In MCMC inverter........ entering main loop", Debug.EXTRA_INFO);
		
		int freeIndex = 0;
		int numthreads = threads.size();
		int nummods = 1;
		
		while(this.getRunningTime()+this.startTime < this.endTime && !this.isStopped)
		{
			freeIndex = TaskThread.getFreeThreadIndex(threads, freeIndex);
			
			if(freeIndex >= 0)
			{
				TaskThread thread = (TaskThread) threads.get(freeIndex);
				Model[] result = (Model[]) thread.getResult();
				if(thread.getTask() instanceof RemoteForward) 
				{
					long time = thread.getComputationTimeMillisecs();
					//aim for a 1 second delay
					nummods = ((RemoteForward) thread.getTask()).calculateNumberOfModelsRequired(time, result.length, numthreads, 1, 0.2,result[0].getMemoryEstimate()*2);
					Debug.println("calculated "+nummods+" as optimal number of models to evaluate on node "+thread.getHostname(), Debug.INFO);
				}
				else
					throw new IllegalStateException("Task of Remote thread was not a RemoteForward...");
				
				Debug.println("got "+result.length+" from node "+thread.getHostname(), Debug.INFO);
				String hostname = thread.getHostname();
				int generatingchain = thread.getIndex();

				//the chain that we should update. Choose one at random
				//Note that, right at the start, some of the chains wont
				//have finished their initial local optimization, but
				//we dont worry about this and just choose a chain at
				//random anyway. This may result in some wasted computation
				int chaintoupdate = rand.nextInt(oldModels.length);
 
				if(oldModels[chaintoupdate] == null ||
				  oldModels[generatingchain] == null) throw new IllegalStateException("oldModel not set");

				//the first model in the generating chain
				MCMC_Model orig = oldModels[generatingchain];
				
				//we are sampling from another chain. We copy the
				//first item of this other chain because, if the first
				//generated model in the sampling chain is rejected, we need
				//to take the last accepted model in that chain as
				//the first move.
				oldModels[chaintoupdate] = oldModels[generatingchain];
				
				
				Debug.println("post-process loop in MCMC", Debug.INFO);
				
				//go through and work out which models are accepted in
				//the updated chain
				for(int i = 0; i < result.length; i++)
				{  
					MCMC_Model m = (MCMC_Model) result[i];
					
					if(!m.isValid()) throw new IllegalStateException("Invalid model produced by model generator");
					
					//update uniform estimate of evidence
					if(this.numEvdSamples > 0 && m.hasEvidence()) 
					{
						this.uevidencesum += m.getEvidenceSum();
						this.unumevd += m.getNumberOfEvidenceSamples();
					}
					
					//since the new model comes from a markov chain,
					//we can accept it without a check
					if(m.isAccepted()) oldModels[chaintoupdate] = m;
					//otherwise, we dont need to do anything					
				}

				//update density and plots
				this.newModelsFound(orig, result, thread.getHostname());				

				Debug.println("giving thread on "+thread.getHostname()+" new task with "+nummods+" models to evaluate", Debug.IMPORTANT);
				
				//give the thread a new job to do
				double[][] bounds = handle.getBoundsOnModelSpace(); 
					
				ModelGenerator mg = this.getModelGenerator(oldModels[generatingchain], bounds, nummods, stddevs[generatingchain]);
				thread.setTask(new RemoteForward(handle, mg));
			}
			else //all threads are busy
			{
				Debug.println("all threads busy... sleeping", Debug.IMPORTANT);
				try { Thread.sleep(1000); }
				catch(InterruptedException ie)
				{
					/* do nothing... just wake up and keep going */
					Debug.println("main thread got woken up..... probably there is work to do", Debug.INFO);
				}
			}

			
			freeIndex = (freeIndex + 1) % numthreads; 
		}


		Debug.println("exited main loop in MCMC...returning from doDistributed... isStopped is "+isStopped, Debug.INFO);
	}
	

	
	
	
	private long lastDensityLog = 0; 
	private void logDensity()
	{
		if(this.densityFile == null || this.densityFile.trim().length() == 0)
			return;

		try
		{
			Debug.println("logging density to file "+this.densityFile, Debug.INFO);
			java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.FileWriter(this.densityFile)));
			pw.println(this.densityFunction.toString());
			pw.close();
			Debug.println("finished logging density to file "+this.densityFile, Debug.INFO);
		}
		catch(java.io.IOException ioe) {
			Debug.println("could not log to density file "+this.densityFile, Debug.CRITICAL);
		}

		lastDensityLog = this.getRunningTime();
	}
	
	
	
	private ModelGenerator prior = null;
	public void setPriorModelGenerator(ModelGenerator priorgen)
	{
		this.prior = priorgen;
	}
	
	
	
	
	
	
	
	
	
	/** Generate a new model according to the prior
	 * 
	 * @return a new model
	 */
	public MCMC_Model generateModelAccordingToPrior()
	{
		MCMC_Model m = new MCMC_Model(prior.generateNewModel(), -1);
		if(!m.isValid())
			throw new IllegalStateException("model generated according to prior is invalid!");
		return m;
	}
	
	
	
	
	
	
	
	

	
	/** This method must be called each time new models are discovered
	 * 
	 *  All accepted models in the chain contribute to the density
	 *  estimate.
	 *  
	 * 
	 * @param start
	 * @param model
	 * @param host
	 */
	protected void newModelsFound(MCMC_Model start, Model[] models, String host)
	{
		//boolean burntin = burntIn.containsKey(host);
		long time = this.getRunningTime();

		Debug.println("At start of newModelsFound in MCMC inverter", Debug.INFO);
		MCMC_Model prev = start;
		for(int i =0; i < models.length; i++)
		{
			MCMC_Model cur = (MCMC_Model) models[i];
			if(!cur.isValid())
				throw new RuntimeException("Invalid models should never be passed to newModelFound.... use the last valid model in the chain");

			if(cur.getDiscoveryTime() < 0)
				cur.setDiscoveryTime(time);
			cur.setHostname(host);
			double misfit = cur.getMisfit();
			
			if(misfit < besterr) 
			{
				besterr = misfit;
				bestModel = cur;
				
				//if we get a new best model significantly better than
				//the best one we already have, we start calculating
				//our evidence again, as it is likely to be wrong
				//we also scrub out our density function estimate, 
				//since it also is bound to be wrong
				if(this.numEvdSamples > 0 && misfit < evdbase-5)
				{
					this.evidenceest = 0.0; 
					this.numevd = 0;
					evdbase = misfit;
					densityFunction.reinitialize();
				}
			}
			
			
			if(cur.isAccepted())
				prev = cur;
			
			//if the prior isnt set, then we set it
			if(!prev.isPriorSet())
				prev.setPrior(handle.getPriorForModel(prev.getModelParameters()));
			this.updateDensity(prev);
						
			//prev must always be accepted
			if(!prev.isAccepted())
				throw new IllegalStateException("previous model not accepted... should be impossible... thats the definition of a Markov Chain!");
		}
		
		// update the density function		
		if(this.getRunningTime() > lastDensityLog+60000)
		{
			if(this.numevd > 0) Debug.println("Posterior estimate of evidence is "+this.getEstimateOfEvidence(), Debug.IMPORTANT);
			if(this.unumevd > 0) Debug.println("Uniform estimate of evidence is "+(scalefact*uevidencesum/unumevd), Debug.IMPORTANT);
			this.logDensity();
		}

		Debug.println("About to notify interested observers in MCMC inverter", Debug.EXTRA_INFO);
		for(int i = 0; i < observers.size(); i++) //notify observers
			((ModelObserver) observers.get(i)).newModelsFound(models);		
		Debug.println("Finished notifying interested observers in MCMC inverter", Debug.EXTRA_INFO);
	}
	
	private void newModelsFound(MCMC_Model start, MCMC_Model[] models)
	{ newModelsFound(start, models, "localhost"); }
	

		
	
	
	public double getEstimateOfEvidence()
	{
		if(this.numevd == 0)
			return 0.0;
		
		return numevd/evidenceest;
	}
	
	
	
	//estimates of the evidence based on posterior sampling
	private int numevd = 0;
	private double evidenceest = 0.0;
	private double evdbase = Double.POSITIVE_INFINITY;
	
	
	
	
	
	//estimates based on uniform sampling
	private int unumevd = 0;
	private double uevidencesum = 0.0;
	
	/** Only models that are accepted contribute to the estimated density
	 * 
	 * @param acceptedmodel
	 */
	protected void updateDensity(MCMC_Model acceptedmodel)
	{
		if(!acceptedmodel.isAccepted())
			throw new IllegalStateException("updateDensity called for rejected model!");
		
		double likelihood = acceptedmodel.getLikelihood();
		evidenceest += 1.0/likelihood;
		numevd ++;
		
		this.densityFunction.update(acceptedmodel);
	}

	
	
	
	
	
	private static class MCMCModelGenerationMethodsImpl implements MCMCModelGenerationMethods
	{
		public MCMC_Model generateNewModel(MCMC_Model current, java.util.Random rand, 
				double[][] bounds, double[] stddev)
		{
			return MarkovChainMonteCarloInverter.generateNewMCMCModel(current, rand,
					bounds,	stddev);
		}
		
		public boolean acceptChange(MCMC_Model orig, MCMC_Model newmod, Random r)
		{
			return MarkovChainMonteCarloInverter.applySymmetricMetropolisTest(orig, newmod, r);
		}
	}
	
	
	/**
	 * 
	 * @param start
	 * @param bounds
	 * @param nummods If nummods is negative, the generater should never stop generating models
	 * @param stddevs
	 * @return
	 */
	protected ModelGenerator getModelGenerator(MCMC_Model start, double[][] bounds, int nummods, double[] stddevs)
	{
		MCMCModelGenerationMethods meth = new MCMCModelGenerationMethodsImpl(); 
		ModelGenerator mg = new MCMC_ModelGenerator(meth, start, bounds, stddevs, numEvdSamples);
		if(nummods < 0)
			/* do nothing */;
		else
			mg = ModelGenerator.getNShotModelGenerator(mg, nummods);
		return mg;
	}
	
	
	
	
	private long startTime; //the time at which the inversion started
	private long stopTime = -1L; //the time at which the inversion stopped
	public void run()
	{
		startTime = System.currentTimeMillis();
		try {
			internalRun();
		}
		catch(Exception e)
		{
			if(e instanceof RuntimeException)
				throw (RuntimeException) e;
			else
				throw new RuntimeException(e);
		}
		finally
		{
			logDensity();
			
			this.stopTime = System.currentTimeMillis();
			this.isStopped = true;
			Debug.println("Markov Chain Monte Carlo sampler finished", Debug.IMPORTANT);
		}
		
	}
	
	
	public rses.Model getBestModel()
	{
		return bestModel;
	}
	
	
	public UserFunctionHandle getUserFunctionHandle()
	{
		return handle;
	}
	
	public void addModelObserver(ModelObserver obs)
	{
		observers.add(obs);
	}

	public void removeModelObserver(ModelObserver obs)
	{
		observers.remove(obs);
	}

	
	public long getRunningTime()
	{
		if(this.isFinished())
			return stopTime - startTime;
		return System.currentTimeMillis()-startTime;
	}
	
	public boolean isFinished()
	{
		return this.isStopped;
	}
	
	
	
	public String getStageName()
	{
		return "not implemented";
	}
	
	public double getStage()
	{
		return 0;
	}
	
	
	
	private boolean isStopped = false;
	public void stopInversion()
	{
		this.stopTime = System.currentTimeMillis();
		this.isStopped = true;
	}
	
	private long endTime = Long.MAX_VALUE;
	public void setTimeToRun(long millisecs)
	{
		endTime = System.currentTimeMillis() + millisecs;
	}	
	
	protected boolean acceptChange(MCMC_Model orig, MCMC_Model newmod, Random rand)
	{
		return applySymmetricMetropolisTest(orig, newmod, rand);
	}
	
	
	

	/** Apply the standard Metropolis test for whether a 
	 *  symmetric move should be accepted
	 * 
	 * @param orig The original model we are perturbing
	 * @param newmod The 'proposed' new model
	 * @param rand
	 * @return
	 */
	public static boolean applySymmetricMetropolisTest(MCMC_Model orig, MCMC_Model newmod, Random rand)
	{
		return applyMetropolisTest(orig, newmod, rand, 1.0);
	}


	/**
	 * 
	 * @param orig
	 * @param newmod
	 * @param rand
	 * @param proposalRatio The ratio of probabilities P(orig-->newmod)/P(newmod-->orig)
	 * @return
	 */
	public static boolean applyMetropolisTest(MCMC_Model orig, MCMC_Model newmod, Random rand, double proposalRatio)
	{
		double newmf = newmod.getMisfit();
		double oldmf = orig.getMisfit();
		if(newmf <= oldmf)
			return true;
		
		double Lratio = Math.exp(oldmf-newmf)/proposalRatio;
		
		if(rand.nextDouble() < Lratio)
			return true;
		
		return false;
	}


	
	
	public static MCMC_Model generateNewMCMCModel(Model current, Random rand, double[][] bounds, 
		double[] perturbStdDev)
	{
		int tochange = rand.nextInt(bounds.length);
		double[] origparam = current.getModelParameters();
		double[] resparam = Util.copy(origparam);
       		
		double range = bounds[tochange][1]-bounds[tochange][0];

		double tryval = resparam[tochange]+rand.nextGaussian()*perturbStdDev[tochange]*range;
		boolean invalid = false;
		if(tryval < bounds[tochange][0] || tryval > bounds[tochange][1])
			invalid = true; //model is out of bounds...  
			
		resparam[tochange] = tryval;

		MCMC_Model result = new MCMC_Model(resparam, tochange);
		if(invalid)
			result.setInvalid();
		return result;
	}






	
	
	
	
	
	
	
	
	/* for testing */
	public static void main(String[] args) throws Exception
	{
		double[][] bounds = new double[51][];
		bounds[0] = new double[] {-150.0, 0.0};
		bounds[1] = new double[] {-117.64000000000001, 0.0};
		bounds[2] = new double[] {-77.64000000000001, 0.0};
		bounds[3] = new double[] {-37.640000000000015, -33.640000000000015};
		bounds[4] = new double[] {-77.64000000000001, -30.47};
		bounds[5] = new double[] {-117.64000000000001, -27.30000000000001};
		bounds[6] = new double[] {-150.0, -24.129999999999995};
		bounds[7] = new double[] {-150.0, -20.960000000000008};
		bounds[8] = new double[] {-150.0, -17.789999999999992};
		bounds[9] = new double[] {-150.0, -14.620000000000005};
		bounds[10] = new double[] {-150.0, -11.449999999999989};
		bounds[11] = new double[] {-150.0, -8.280000000000001};
		bounds[12] = new double[] {-150.0, -5.110000000000014};
		bounds[13] = new double[] {-130.2, -1.9399999999999977};
		bounds[14] = new double[] {-90.19999999999999, -6.199999999999989};
		bounds[15] = new double[] {-50.19999999999999, -46.19999999999999};
		bounds[16] = new double[] {-51.03, -47.03};
		bounds[17] = new double[] {-91.03, -43.859999999999985};
		bounds[18] = new double[] {-131.03, -40.69};
		bounds[19] = new double[] {-150.0, -37.51999999999998};
		bounds[20] = new double[] {-130.01, -34.349999999999994};
		bounds[21] = new double[] {-90.00999999999999, -31.180000000000007};
		bounds[22] = new double[] {-50.00999999999999, -46.00999999999999};
		bounds[23] = new double[] {-90.00999999999999, -42.84};
		bounds[24] = new double[] {-127.32999999999998, -39.66999999999999};
		bounds[25] = new double[] {-87.32999999999998, -36.5};
		bounds[26] = new double[] {-47.329999999999984, -43.329999999999984};
		bounds[27] = new double[] {-87.32999999999998, -40.16};
		bounds[28] = new double[] {-127.32999999999998, -36.99000000000001};
		bounds[29] = new double[] {-100.65, -33.81999999999999};
		bounds[30] = new double[] {-60.65, -40.650000000000006};
		bounds[31] = new double[] {-100.65, -37.47999999999999};
		bounds[32] = new double[] {-115.13999999999999, -34.31};
		bounds[33] = new double[] {-75.13999999999999, -71.13999999999999};
		bounds[34] = new double[] {-115.13999999999999, -67.97};
		bounds[35] = new double[] {-150.0, -64.8};
		bounds[36] = new double[] {-110.46000000000001, -61.629999999999995};
		bounds[37] = new double[] {-70.46000000000001, -54.46000000000005};
		bounds[38] = new double[] {-110.46000000000001, -51.29000000000005};
		bounds[39] = new double[] {-150.0, -48.12000000000006};
		bounds[40] = new double[] {-150.0, -44.95000000000006};
		bounds[41] = new double[] {-150.0, -41.78000000000006};
		bounds[42] = new double[] {-112.75700000000006, -38.610000000000056};
		bounds[43] = new double[] {-72.75700000000006, -64.44};
		bounds[44] = new double[] {-112.75700000000006, -61.269999999999996};
		bounds[45] = new double[] {-150.0, -58.099999999999994};
		bounds[46] = new double[] {-150.0, -54.92999999999999};
		bounds[47] = new double[] {-150.0, -51.75999999999999};
		bounds[48] = new double[] {-150.0, -48.59};
		bounds[49] = new double[] {-150.0, -45.42};
		bounds[50] = new double[] {-150.0, -42.25};
		
		double[][] priors = rses.math.DeltaConstrainedPrior.getPriorDensity(bounds, 40, 75);
		double maxval = Double.NEGATIVE_INFINITY;
		for(int i = 0; i < priors.length; i++)
			for(int j = 0; j < priors[i].length; j++)
				if(priors[i][j] > maxval)
					maxval = priors[i][j];
		
		javax.swing.JFrame f = new javax.swing.JFrame();
		f.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);

		f.setSize(600, 600);
		f.setVisible(true);
		f.show();

		System.out.println("Calling 'plot' at 1 second intervals");

		while(true)
		{
			rses.visualisation.FunctionPlotter.plot2D((java.awt.Graphics2D) f.getGraphics(), 
			new TestFuncImp(priors, bounds, 2.0),
			51, 75, //xgran/ygran
			0.0, 50, //xlower/upper
			-150, 0.0, //ylower/upper
			10, 500,
			10, 500,
			0.0, 0.06);

			Thread.sleep(1000);
		}

	}
}












final class TestFuncImp extends RealFunction
{
	double[][] func = null;
	double[][] bounds;
	double granularity;
	
	TestFuncImp(double[][] vals, double[][] bounds, double granularity)
	{
		this.bounds = bounds;
		this.granularity = granularity;
		int numbins = (int) Math.round(150/granularity);
		func = new double[bounds.length][numbins+1];

		for(int i=0; i < bounds.length; i++)
		{
			for(int j =0; j < vals[i].length; j++)
			{
				int binnum = (int) ((bounds[i][0]+150)/granularity + j);
				func[i][binnum] = vals[i][j];
			}
		}
	}
	
	public double[][] getDomain()
	{
		return bounds;
	}
	
	
	public int getDimensionOfDomain()
	{
		return 2;
	}
	
	public double invoke(double[] args)
	{
		int[] args2 = new int[2];
		args2[0] = (int) Math.round(args[0]);
		args2[1] = (int) Math.round(args[1]);
		return invoke(args2);
	}
	
	
	public double invoke(int[] args)
	{
		int n1 = 50-args[0];
		int n2 = (int) (150/granularity+args[1]/granularity);
		return func[n1][n2];
	}
}
