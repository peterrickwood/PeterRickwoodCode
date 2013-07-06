package rses.inverse.genetic;

import rses.Model;
import rses.ModelGenerator;
import rses.inverse.ModelObserver;
import rses.inverse.ObservableInverter;
import rses.inverse.UserFunctionHandle;
import rses.util.distributed.TaskThread;
import rses.util.distributed.NodeInfo;
import rses.util.distributed.RemoteForward;
import rses.Debug;


import java.util.HashMap;
import java.util.List;






/** 
 * 
 * @author peter rickwood
 *
 */
public class GeneticSearch implements ObservableInverter, rses.inverse.InteractiveInverter
{
	private Organism[] population = null;
	private double bestMisfit = Double.POSITIVE_INFINITY;
	private rses.Model bestModel = null;
	private double misfitSum = 0.0;
	private UserFunctionHandle handle = null;
	private boolean isInitialized = false;
	private long endTime = Long.MAX_VALUE;
	private long stopTime = -1L; //the time at which the inversion stopped
	private boolean isFinished = false;
	
	private static final java.util.Random rand = new java.util.Random(); 
		
	private boolean isDistributed = false;
	private NodeInfo[] nodelist = null;
	private java.util.List threads = null;
	public static GeneticSearch getDistributedGeneticSearch(NodeInfo[] nodelist, int popsize, UserFunctionHandle handle, OrganismFactory factory)
	{
		Debug.println("trying to get a distributed Genetic Search instance", Debug.EXTRA_INFO);
		return new GeneticSearch(nodelist, popsize, handle, factory);
	}

	private GeneticSearch(NodeInfo[] nodelist, int popsize, UserFunctionHandle handle, OrganismFactory factory)
	{
		this.handle = handle;
		isDistributed = true;
		population = new Organism[popsize];
		this.nodelist = nodelist;
		threads = new java.util.ArrayList();

		if(nodelist.length > popsize)
			throw new RuntimeException("Number of nodes/threads in computation greater than population size... this is a waste of computational resources, since we cant use all the threads");
		
		for(int i = 0; i < popsize; i++)
			population[i] = factory.getOrganism();
 
 
		if(!shutUp) Debug.println("Distributed genetic search initialized... (no forward solution yet though)", Debug.INFO);	
	}

	
	
	
	
	/* the generation we are currently breeding */
	private int generation = 0;
	
	public GeneticSearch(int popsize, UserFunctionHandle handle)
	{
		this.handle = handle;
		population = new Organism[popsize];
	}
	
	public GeneticSearch(Organism[] population, UserFunctionHandle h)
	{
		this.population = population;
		this.handle = h;
	}
	


	private void initLocal() 
	{
		if(this.isDistributed)
			throw new RuntimeException("Attempt to do local initialization of distributed instance");
		if(isInitialized)
			throw new IllegalStateException("attempt to initialize already initialized local instance");

		double[][] boundstmp = this.handle.getBoundsOnModelSpace();
		for(int i = 0; i < boundstmp.length; i++)
			Debug.println("Param bounds "+handle.getParameterName(i)+" :: "+boundstmp[i][0]+" to "+boundstmp[i][1], Debug.INFO);
		
		int popsize = population.length;
		for(int i =0; i < popsize; i++) 
		{
			if(population[i] == null) 
				population[i] = new Organism(handle);
			population[i].setMisfit(handle.getErrorForModel(population[i].getModelParameters()));
			double mf = population[i].getMisfit();
			misfitSum += mf;
			if(!shutUp) Debug.println("initial population member "+i+" misfit is "+population[i].getMisfit(), Debug.EXTRA_INFO);
			this.newModelsFound(new Model[] {population[i]});
		}
		
		this.isInitialized = true;
	} 







	//our estimate of the fastest call to forward().
	private double minfwd = 0.001; 
	
	
	private void initDistributed()  
	{
		boolean[] isdone;
		
		if(!this.isDistributed)
			throw new RuntimeException("Illegal attempt to initialize GeneticSearch instance");
		if(isInitialized)
			throw new IllegalStateException("attempt to initialize already initialized distributed instance");
		if(nodelist.length > this.population.length)
			throw new IllegalStateException("nodelist.size() > popsize. This should be impossible (checked in constructor)");
		this.isInitialized = true;
		
		int popsize = this.population.length;		
		isdone=new boolean[popsize];

		java.util.ArrayList models = new java.util.ArrayList();
		for(int i =0; i < nodelist.length; i++)
			models.add(population[i]);
		List taskList = RemoteForward.getTaskList(handle, models);
					
		//start threads and give it an organism to work out the fitness of
		//this also sets the index of the thread for us (by calling TaskThread.setIndex())
		this.threads = TaskThread.getThreadList(nodelist, taskList, !shutUp, !shutUp);
		
		boolean firstresult = true;
		long start = System.currentTimeMillis();
		
		int numdone = 0;
		this.misfitSum = 0.0;
		int freeIndex = 0;
		int numthreads = threads.size();
		int searchindex = threads.size() % popsize;
		while(true)
		{
			freeIndex = TaskThread.getFreeThreadIndex(threads, freeIndex); 

			if(firstresult) {
				firstresult = false;
				minfwd = (System.currentTimeMillis()-start+1)*0.001;
			}

			if(freeIndex >= 0) 
			{
				TaskThread freethread = (TaskThread) threads.get(freeIndex);
				Model[] result = (Model[]) freethread.getResult();
				Debug.println(result.length+" results in from node "+freethread.getHostname(), Debug.INFO);
				int threadindex = freethread.getIndex();
				if(!shutUp) Debug.println("Got result for organism "+threadindex+" in initial population -- "+result[0]+" from "+freethread.getHostname(), Debug.EXTRA_INFO);
				if(!isdone[threadindex])
				{
					numdone++;
					population[threadindex].setMisfit(result[0].getMisfit());
					misfitSum += population[threadindex].getMisfit();
					this.newModelsFound(result);
					isdone[threadindex] = true;
					if(numdone == popsize)
					{
						if(!shutUp) Debug.println("Finished evaluation of initial population", Debug.IMPORTANT);
						isdone = new boolean[popsize];
						return;
					}
					else
					{
						//give it a new one to evaluate
						if(!shutUp) Debug.println("looking for organism that still needs to be evaluated", Debug.EXTRA_INFO);
						while(isdone[searchindex])
							searchindex = (searchindex+1) % popsize;
						if(!shutUp) Debug.println("found that  organism "+searchindex+" still needs to be evaluated -- giving to "+freethread.getHostname(), Debug.EXTRA_INFO);
						freethread.setIndex(searchindex);
						ModelGenerator mg = ModelGenerator.getOneShotModelGenerator(population[searchindex]);
						freethread.setTask(new RemoteForward(handle, mg));
						searchindex = (searchindex+1) % popsize;
					}
				}
				else //already have result
				{
					double resmf = ((Model[]) freethread.getResult())[0].getMisfit();
					if(resmf != population[threadindex].getMisfit()) {
						Debug.println("NEW MISFIT CALCULATED ON NODE "+freethread.getHostname(), Debug.CRITICAL);
						Debug.println("newmf = "+resmf+" oldmf = "+population[threadindex].getMisfit(), Debug.CRITICAL);
						Debug.println("difference is "+(resmf - population[threadindex].getMisfit()), Debug.CRITICAL);
						Debug.println("Master node reckons the misfit is..."+handle.getErrorForModel(population[threadindex].getModelParameters()), Debug.CRITICAL);
						Debug.println(""+((Model[]) freethread.getResult())[0], Debug.CRITICAL);
						Debug.println(""+population[threadindex], Debug.CRITICAL);
						throw new IllegalStateException("ILLEGALSTATE..... got two different results from independent assessments of fitness for organism "+threadindex);
					}
					if(!shutUp) Debug.println("We already have a result for organism "+threadindex+" discarding duplicate result", Debug.EXTRA_INFO);
					if(!shutUp) Debug.println("looking for organism that still needs to be evaluated", Debug.EXTRA_INFO);
					while(isdone[searchindex])
						searchindex = (searchindex+1) % popsize;
					if(!shutUp) Debug.println("found that  organism "+searchindex+" still needs to be evaluated -- giving to "+freethread.getHostname(), Debug.EXTRA_INFO);
					freethread.setIndex(searchindex);
					freethread.setTask(new RemoteForward(handle, ModelGenerator.getOneShotModelGenerator(population[searchindex])));
					searchindex = (searchindex+1) % popsize;
				}				
			}
			else
			{
				if(!shutUp) Debug.println("All threads busy... sleeping", Debug.IMPORTANT);
				try { 
					Thread.sleep(Long.MAX_VALUE); 
				}
				catch(InterruptedException ie)
				{	if(!shutUp) Debug.println("sleeping main thread got woken up in initDistributed... must be results in", Debug.INFO); } 				
			}
			
			freeIndex = (freeIndex + 1) % numthreads;
			
		}
	}











	public synchronized void evolve(int numgenerations)
	{
		if(this.isDistributed)
		{
			if(!this.isInitialized) 
				this.initDistributed();
			distributedBreed(numgenerations);
		}
		else 
		{
			if(!this.isInitialized) 
				this.initLocal();
			for(int i =0; i < numgenerations; i++)
			{
				breed(0.333);
				breed(0.333);
				breed(0.333);
				this.generation++;
			}		
		}
	}









	private void distributedBreed(int numgenerations)
	{
		int startgen = generation;
		
		//keep track of threads still working on old problems we dont care about
		HashMap oldthreads = new HashMap();
			
		//and now lets all get them started
		if(!shutUp) Debug.println("kicking off all non-busy threads in distributedBreed", Debug.INFO);
		for(int i = 0; i < threads.size(); i++)
		{
			TaskThread ft = (TaskThread) threads.get(i);
			if(ft.isFinished())
			{
				if(!shutUp) Debug.println("Thread "+i+" is ready, giving it new job", Debug.EXTRA_INFO);
				giveThreadNewJob(ft, population, 1);
			}
			else //thread is working on an out of date problem
			{
				if(!shutUp) Debug.println("Thread "+i+" is busy.... putting it in old thread queue", Debug.EXTRA_INFO);
				oldthreads.put(ft, ft);
			} 
		}
			
		if(!shutUp) Debug.println("All threads kicked off... entering main loop... there are "+oldthreads.size()+" old threads", Debug.IMPORTANT);
			
		int numdone = 0;
		int freeIndex = 0; 
		int numthreads = threads.size();
		int nummods = 1;

		//If we get to here, we are guaranteed that all members of
		//population have had their fitness calculated
		//(i.e. population[i].getMisfit() is defined)
		
		while(this.generation < startgen+numgenerations) 
		{	
			freeIndex = TaskThread.getFreeThreadIndex(threads, freeIndex);

			if(freeIndex >= 0) 
			{
				TaskThread ft = (TaskThread) threads.get(freeIndex);
				if(oldthreads.remove(ft) != null)
				{
					if(!shutUp) Debug.println("Thread on host "+ft.getHostname()+" was working on old computation.... giving it new task and removing it from old list", Debug.EXTRA_INFO);
					Debug.println("size of old thread queue after removal is "+oldthreads.size(), Debug.EXTRA_INFO);
					giveThreadNewJob(ft, population, 1);
				}
				else //thread has finished a result that we are interested in
				{
					Model[] result = (Model[]) ft.getResult();
					this.newModelsFound(result);

					if(ft.getTask() instanceof RemoteForward) {
						long time = ft.getComputationTimeMillisecs();
						//aim for a 1 second delay
						nummods = ((RemoteForward) ft.getTask()).calculateNumberOfModelsRequired(time, result.length, numthreads, 1, 0.2, result[0].getMemoryEstimate()*2);
					}

					//now see if we should replace
					int replaced = 0;
					for(int i = 0; i < result.length; i++, numdone++)
					{
						int popindex = rand.nextInt(population.length);
						Organism org = (Organism) result[i];
						double newmisfit = org.getMisfit();
						double oldmisfit = population[popindex].getMisfit();
						//if(!shutUp) System.err.println("got it's misfit.... "+newmisfit);
						//if(!shutUp) System.err.println("old organism misfit is "+oldmisfit);
						if(newmisfit <= oldmisfit)
						{
							//if(!shutUp) System.err.println("new misfit better than the old misfit of "+oldmisfit+" replacing old organism "+popindex);
							replaced++;
							this.misfitSum -= oldmisfit;
							this.misfitSum += newmisfit;
							population[popindex] = org;
						}	
						else //old one was better, keep that
						{
							//if(!shutUp) System.err.println("ahhh, old misfit of "+oldmisfit+" was better, not replacing organism "+popindex);
						}
					}
					
					if(!shutUp) Debug.println("job on node "+ft.getHostname()+" caused "+replaced+" new organisms to enter population", Debug.INFO);
					
					if(numdone >= population.length)
					{
						numdone = 0;	
						if(!shutUp) Debug.println("end of generation: MEAN FITNESS IS "+misfitSum/population.length, Debug.IMPORTANT);

						this.generation++;
						if(this.getRunningTime() >= this.endTime || this.isFinished)
							return; // we're done 
					}
					
					//give the thread a new job
					giveThreadNewJob(ft, population, nummods);
				}
			}
			else //all busy, wait for a while
			{
				if(!shutUp) Debug.println("All threads busy... sleeping", Debug.IMPORTANT);
				try { Thread.sleep(Long.MAX_VALUE); }
				catch(InterruptedException ie)
				{
					/* don't do anything, just get on with our work */
					if(!shutUp) Debug.println("main loop got woken up, there must be threads that have completed", Debug.INFO); 
				}
			}
			
			freeIndex = (freeIndex + 1) % numthreads;
							
		}
	}







	private void breed(double percentToReplace)
	{	
		int popsize = population.length;
	
		if(this.isDistributed)
			throw new RuntimeException("non-distributed version of breed called for distributed instance");			
	
		misfitSum = 0.0;
		
		for(int i = 0; i < popsize; i++)
		{
			if(rand.nextDouble() < percentToReplace)
			{	
				int orga = (int) (rand.nextDouble()*popsize);
				int orgb = (int) (rand.nextDouble()*popsize);
				
				Organism neworg = population[orga].breed(population[orgb], handle.getBoundsOnModelSpace());
				neworg.setMisfit(handle.getErrorForModel(neworg.getModelParameters()));

				if(neworg.getMisfit() < population[i].getMisfit())
				{
					if(!shutUp) Debug.println("replacing organism "+i+" with new organism", Debug.EXTRA_INFO);
					misfitSum -= population[i].getMisfit();
					misfitSum += neworg.getMisfit();		
					population[i] = neworg;
				}
				
				this.newModelsFound(new Organism[] {neworg});
				
			}
			misfitSum += population[i].getMisfit();
		}
		
		if(!shutUp) Debug.println("MEAN FITNESS IS "+misfitSum/population.length, Debug.IMPORTANT);
	}


 

	//find a member of the population that we can replace, 
	//create a new organism, and get the thread to start working on its misfit 
	private void giveThreadNewJob(TaskThread ft, Organism[] population, int nummodels)
	{
		int popsize = population.length;
		int index = (int) (rand.nextDouble()*popsize);

		int orga = (int) (rand.nextDouble()*popsize);
		int orgb = (int) (rand.nextDouble()*popsize);
					
		if(!shutUp) Debug.println(nummodels+" new organisms on "+ft.getHostname()+" will be children of organisms "+orga+" and "+orgb, Debug.EXTRA_INFO);
		
		ft.setIndex(index);
		ModelGenerator mg = Organism.getChildGenerator(population[orga], population[orgb], handle.getBoundsOnModelSpace());
		mg = ModelGenerator.getNShotModelGenerator(mg, nummodels); 
		ft.setTask(new RemoteForward(handle, mg));
	}
	

	

	
	
	
	/** Internal method called when a new model is found.
	 *   This notifies all interested observers, updates best misfit,
	 *  sets discovery time, etc 
	 */ 
	private void newModelsFound(Model[] org)
	{
		for(int i = 0; i < org.length; i++)
		{
			double mf = org[i].getMisfit();
			org[i].setDiscoveryTime(this.getRunningTime());
			if(mf < bestMisfit) {
				bestMisfit = mf;
				bestModel = org[i];
				Debug.println("new best model found: "+org[i], Debug.INFO);
			}
		}
		for(int j =0; j < observers.size(); j++)
			((ModelObserver) observers.get(j)).newModelsFound(org);
	}
	
	
	
	private boolean shutUp = false;
	public void shutUp(boolean shutUp)
	{
		this.shutUp = true;
	}
	
	


	/** How long should the inversion run before stopping?
	 *  calling this method tells the inverter to stop running
	 *  at time System.currentTimeMillis()+millisecs.
	 * 
	 *  Calling it multiple times will reset the running time
	 *  (so you can interactively adjust the end time,
	 *   depending on how the inversion is going)
	 * 
	 * @param millisecs How much longer to run for
	 */
	public void setTimeToRun(long millisecs)
	{
		this.endTime = millisecs;
	}
	
	/** Stop the inversion at the next opportune moment
	 *   (this may not be immediately), but only
	 *   if the inversion is running (this is, if the inverters
	 *   run method has been called).
	 */
	public void stopInversion()
	{
		this.stopTime = System.currentTimeMillis();
		this.isFinished = true;
	}



	public boolean isFinished()
	{
		return this.isFinished;
	}


	
	public void run()
	{
		this.isFinished = false;
		try {internalRun(); }
		catch(Exception e)
		{
			if(e instanceof RuntimeException)
				throw (RuntimeException) e;
			else
				throw new RuntimeException(e);
		}
		finally
		{
			this.stopTime = System.currentTimeMillis();
			this.isFinished = true;
		}
		Debug.println("Inversion/Optimization finished", Debug.IMPORTANT);
	}



	
	/* methods to implement of ObservableInverter */
	private long startTime;
	private void internalRun() throws Exception
	{
		startTime = System.currentTimeMillis();
		
		if(this.isDistributed && !this.isInitialized)  
			this.initDistributed();

		if(this.isDistributed)
			evolve(Integer.MAX_VALUE);
		else {
			/* else, we are in a local version, so we just keep evolving locally */
			while(this.getRunningTime() < this.endTime && !this.isFinished)			
				evolve(1);
		}
	}
	
	
	public rses.Model getBestModel()
	{
		return bestModel;
	}
	
	public double getStage()
	{
		return generation;
	}
	
	public String getStageName()
	{
		return "Generation";
	}
	
	public long getRunningTime()
	{
		if(this.isFinished())
			return (this.stopTime - this.startTime);
		long current = System.currentTimeMillis();
		return (current-this.startTime);
	}
	
	private java.util.List observers = new java.util.Vector();
	public void addModelObserver(ModelObserver observer)
	{
		this.observers.add(observer);
	}
	
	public void removeModelObserver(ModelObserver observer)
	{
		this.observers.remove(observer);
	}
	
	public UserFunctionHandle getUserFunctionHandle()
	{
		if(this.handle == null)
			throw new UnsupportedOperationException();
		return this.handle;
	}
	
		
}




