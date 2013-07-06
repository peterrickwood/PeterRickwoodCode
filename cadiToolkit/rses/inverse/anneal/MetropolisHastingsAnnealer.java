package rses.inverse.anneal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import rses.inverse.ModelObserver;
import rses.inverse.ModelPerturber;
import rses.inverse.UserFunctionHandle;
import rses.inverse.mcmc.MCMC_Model;
import rses.inverse.montecarlo.MonteCarlo;
import rses.util.distributed.TaskThread;
import rses.util.distributed.NodeInfo;
import rses.util.distributed.RemoteForward;
import rses.Model;
import rses.ModelGenerator;
import rses.Debug;



/** A Metropolis-Hastings annealer. The user must set a cooling schedule
 *  and may optionally set a perturber object, that performs the
 *  proposal perturbation. If the perturber is not set, then a
 *  uniform perturbation across the whole axis is performed. The
 *  standard Metropolis test (with temperature factor) is used
 *  to determine acceptance. 
 *
 *  Note that
 *  the sematics of a distributed instance are slightly
 *  different from those of a single one. 
 * 
 *  Here is how it works in the distributed case:
 *  <ol>
 *  <li>We start with a model m.
 *  <li>Each node is asked to anneal at a fixed temperature
 *      for a set number of iterations
 *  <li>As each node returns its results, we take the last
 *      accepted model in each chain, and replace the current 
 *      metropolis model with that model.
 *  <li> We then update local book-keeping and 
 *       go back to 1)
 * </ol>
 * 
 * One problem with this scheme is that the temperature
 * schedule set is not followed exactly, and not even closely
 * is the number of iterations per temperature is small
 * relative to the number of models evaluated on each node. 
 *
 * @author peter rickwood
 *
 */
public class MetropolisHastingsAnnealer implements SimulatedAnnealer
{
	private List observers = new java.util.Vector();
	
	private TemperatureSchedule tempsched = null;
	private java.util.Iterator temperatures = null;
	private UserFunctionHandle handle = null;
	
	//the current model
	private Model mhm = null;

	private double currentTemp = Double.NaN;
	private boolean done = false;
	
	private java.util.Random rand = null;

	//how many perturbations at this temperature?
	private int perturbcount = 0;
	
	//how many times have we cycled through all dimensions?
	//NB: this isnt structly true, since we perturb individual 
	//dimensions at random.... but every time perturbcount
	//gets to dimension of model space, we count that as
	//an iteration
	private int iterationCount = 0;
		
	
	
	private boolean isDistributed = false;
	
	
	
	/** Get a distributed version of the Annealer, for running
	 *  on CadiServer instances on other machines. 
	 * 
	 * @param nodelist The list of nodes on which to run (hostnames)
	 * @param handle The user supplied library that evaluates models
	 * @return
	 */
	public static MetropolisHastingsAnnealer getDistributedMetropolisHastings(
		NodeInfo[] nodelist, UserFunctionHandle handle)
	{
		return new MetropolisHastingsAnnealer(nodelist, handle);
	}
	
	private NodeInfo[] nodes = null;
	
	/** For internal class use only (and sub-class use). This
	 *  is the constructor to create a distributed instance of the 
	 *  annealer. 
	 * 
	 * @param nodelist
	 * @param handle
	 */
	protected MetropolisHastingsAnnealer(NodeInfo[] nodelist, UserFunctionHandle handle)
	{
		this(handle);
		this.nodes = nodelist;
		this.isDistributed = true;
		
		this.setModelPerturber(new UniformPerturber(handle.getBoundsOnModelSpace()));
	}
	
	

	/** creates a local (single-machine) instance of the annealer.
	 * 
	 * @param handle The user supplied library to evaluate models.
	 */
	public MetropolisHastingsAnnealer(UserFunctionHandle handle)
	{
		this.handle = handle;
		this.rand = new Random();
		this.setModelPerturber(new UniformPerturber(handle.getBoundsOnModelSpace()));
	}
	
	
	/** Set the cooling schedule. If the annealer is already running,
	 * it will continue to run, but will start again at the start
	 * of this new temperature schedule. 
	 * 
	 * @param ts The temperature schedule to use during annealing
	 * @see SimulatedAnnealer
	 */
	public synchronized void setTemperatureSchedule(TemperatureSchedule ts)
	{ 
		done = false;
		iterationCount = 0; //start iteration count again
		
		this.tempsched = ts; 
		
		this.temperatures = ts.getTemperatures();
		if(!temperatures.hasNext())
			throw new IllegalArgumentException("empty temperatures schedule specified!");
		
		this.currentTemp = ((Number) temperatures.next()).doubleValue();	
	}


	/** Get the cooling schedule
	 * 
	 */
	public TemperatureSchedule getTemperatureSchedule()
	{
		return tempsched; 
	}



	private Object modelpertlock = new Object();
	private ModelPerturber modelperturber = null;
	/** Set the ModelPerturber object 
	 * 
	 * @param mp
	 * @see rses.inverse.ModelPerturber
	 */
	public void setModelPerturber(ModelPerturber mp)
	{
		synchronized(modelpertlock)
		{	this.modelperturber = mp;}
	}
	


	/** Get the ModelPerturber object being used in this
	 *  simulated annealer.
	 * 
	 * @return
	 * @see rses.inverse.ModelPerturber
	 */
	public ModelPerturber getModelPerturber()
	{
		synchronized(modelpertlock)
		{return this.modelperturber;}
	}





	/** Apply the standard Metropolis-Hastings test, with an
	 *  adjustment for temperature.
	 * 
	 *  That is, if the proposed move is to point x<sub>new</sub>
	 *  with error err<sub>new</sub> from x<sub>old</sub> with
	 *  error  err<sub>old</sub>. then the move is accepted 
	 *  if r > exp(err<sub>old</sub>-err<sub>new</sub>)/temp.
	 *  where r is a number drawn uniformly from [0..1], and temp
	 *  is the current temperature.
	 * 
	 * 
	 * @param orig
	 * @param newmod
	 * @param temp
	 * @return true is the move is accepted, false otherwise.
	 */
	public static boolean applyMetropolisTest(Model orig, Model newmod, double temp)
	{
		if(temp == Double.NEGATIVE_INFINITY)
			return false;
			
		double newerror = newmod.getMisfit();
		double olderr = orig.getMisfit();
				
		if(newerror <= olderr)
		{
			//do nothing, just accept the move
			return true;
		}
		else //roll a dice and see if we should move
		{ 
			double r = Math.random();
			double probToChange = Math.exp((olderr-newerror)/temp);
			if(r < probToChange)
				return true; 
			
			//else reject the move... do nothing
			return false;
		}						
	}
	
	

	/** The method that the annealer uses to determine 
	 *   whether to accept a move from model
	 *   <code>orig</code> to model <code>newModel</code>.
	 * 
	 *  
	 * @param original The starting model
	 * @param newmodel The proposed model to move to
	 * @return true if the move is accepted, false otherwise
	 */
	protected boolean acceptChange(Model original, Model newmodel)
	{
		return applyMetropolisTest(original, newmodel, this.getCurrentTemperature());
	}





	//update the bookeeping to reflect the fact that we have seen 
	//numnewmodels new models
	private void updateBookeeping(int numnewmodels)
	{
		//now update bookeeping
		perturbcount+=numnewmodels;
		if(perturbcount >= handle.getDimensionOfModelSpace()) 
		{
			iterationCount += perturbcount/handle.getDimensionOfModelSpace();
			perturbcount = 0;
			if(iterationCount >= tempsched.getIterationsPerTemperature())
			{
				Debug.println("end of annealing at temperature "+this.getCurrentTemperature(), Debug.IMPORTANT);
				if(temperatures.hasNext()) {
					iterationCount = 0;
					currentTemp = ((Number) temperatures.next()).doubleValue();
				}
				else {
					//otherwise, we've reached the end of
					//the schedule and we dont do any more
					//annealing.... so we don't
					//reset iterationCount or currentTemp
					Debug.println("Finished annealing", Debug.IMPORTANT);
					stopTime = System.currentTimeMillis();
					done = true; 
				}
			}
		}		
	}




	/* perturb the current model parameters along a random axis and return 
	 * the perturbed model.
	 * 
	 * This method must be quick, as we call it a lot, and in the distributed case,
	 * it could become a bottleneck with a quick forward() model.
	 * 
	 * This method MUST be called every time that a new model is considered,
	 * because it keeps track of which iteration we are in, what the
	 * temperature is, etc
	 */
	private double[] getPerturbedModelAndUpdateBookeeping(double[] orig)
	{
		//get the perturbed model
		double[] result = null;
		synchronized (this) { result = this.getModelPerturber().getPerturbedModel(orig); }

		//update the bookeeping
		this.updateBookeeping(1);		
		
		return result;
	}




	
	/** Perturb along 1 axis and either accept or reject the
	 *  change, based on the current temperature and the
	 *  change in misfit.
	 * 
	 *  <p>
	 *  This method is to allow a caller to perform an anneal
	 *  by repeatedly calling this method (rather than calling
	 *  the run method and sitting back). 
	 * 
	 *  <p>
	 *  It is considered illegal to 
	 *  call this method on a distributed version.
	 *   
	 */
	public boolean doNextPerturbation()
	{
		if(this.isDistributed)
			throw new UnsupportedOperationException("You cannot call doNextPerturbation() on a distributed instance of the MetropolisHastingsAnnealer.. it's not thread-safe");
		if(tempsched == null)
			throw new UnsupportedOperationException("Cannot anneal. No temperature schedule set, and no default temperature schedule currently implemented for this SimmulatedAnnealer"); 
		if(mhm == null)
			throw new IllegalStateException("Impossible case.... mhm is null in non-distributed instance");
	
		//see if we've reached the end of the temperature schedule
		if(done)
			return false;
				

		
		double[] newmodel = this.getPerturbedModelAndUpdateBookeeping(mhm.getModelParameters());
		Model nmhm = new rses.Model(newmodel);
		nmhm.setMisfit(handle.getErrorForModel(newmodel));
		
		this.newModelsFound(new Model[] {nmhm});
		
		if(acceptChange(mhm, nmhm))
		{
			this.mhm = nmhm;
			return true;
		}
		
		return false;
	}


	
	public double getCurrentTemperature()
	{
		return currentTemp;
	}
	
	
	
	private void newModelsFound(Model[] models)
	{
		for(int i = 0; i < models.length; i++)
		{
			if(models[i].getDiscoveryTime() < 0)
				models[i].setDiscoveryTime(this.getRunningTime());
		}
		for(int i = 0; i < observers.size(); i++) //notify observers
			((ModelObserver) observers.get(i)).newModelsFound(models);
	}
	
	
	
	
	
	
	private void internalRun() throws Exception
	{
		if(tempsched == null) //no temperature schedule set
			throw new UnsupportedOperationException("Cannot anneal. No temperature schedule set, and no default temperature schedule currently implemented for this SimmulatedAnnealer"); 
		this.startTime = System.currentTimeMillis();
		
		if(this.isDistributed)
			doDistributed();
		else
		{ 

			//generate the initial model, if the user hasnt set it
			if(this.mhm == null) {
				Debug.println("Generating random initial model", Debug.IMPORTANT);
				this.mhm = MonteCarlo.generateNewModel(handle, rand);
				this.mhm.setMisfit(handle.getErrorForModel(mhm.getModelParameters()));
			}
			else
				Debug.println("initial model set by user", Debug.IMPORTANT);
			
			while(!done) //a local (single-node) version of the algorithm
				doNextPerturbation();
		}
	}


	/** By default, the simmulated annealer chooses a model at random
	 *   and perturbs that. If you wish to specify a starting model, you
	 *   may do so by calling this method before starting to anneal.  
	 * 
	 * @param m
	 */
	public void setInitialModel(Model m)
	{
		this.mhm = m;
	}


	//generate forward models and farm them out.
	//This method should return when this.done is true (that is,
	//when we have reached the end of our temperature schedule)
	private void doDistributed()
	{
		if(this.mhm == null) {
			this.mhm = MonteCarlo.generateNewModel(handle, rand);
			this.mhm.setMisfit(handle.getErrorForModel(mhm.getModelParameters()));
		}
		
		//generate inital set of models randomly and start up threads
		ArrayList models = new ArrayList(nodes.length);
		for(int i = 0; i < nodes.length; i++) 
			models.add(new MCMC_Model(this.getPerturbedModelAndUpdateBookeeping(mhm.getModelParameters()), -1));
		
		List taskList = RemoteForward.getTaskList(handle, models);
		List threads = TaskThread.getThreadList(nodes, taskList, false, true);
		
			
		int freeIndex = 0;
		int numthreads = threads.size();	
		int nummods = 1;

		while(!this.done)
		{
			freeIndex = TaskThread.getFreeThreadIndex(threads, freeIndex);
			
			if(freeIndex >= 0)
			{
				TaskThread thread = (TaskThread) threads.get(freeIndex);
				Model[] mods = (Model[]) thread.getResult();
				
				if(thread.getTask() instanceof RemoteForward) 
				{
					long time = thread.getComputationTimeMillisecs();
					//aim for a 1 second delay
					nummods = ((RemoteForward) thread.getTask()).calculateNumberOfModelsRequired(time, mods.length, numthreads, 1.0, 0.2, mods[0].getMemoryEstimate()*2);

					//we dont want too many models being evaluated on each node, 
					//because otherwise we dont get the right temperature slowing
					if(nummods > (int) (1.2*(tempsched.getIterationsPerTemperature()*handle.getDimensionOfModelSpace()))) {
						Debug.println("temperature schedule is too fine-grained to run on this many nodes. Solution is to increase your iterations per temperature.", Debug.IMPORTANT);
						Debug.println("I'll continue to run, but realize that your temperature schedule is not being followed properly", Debug.IMPORTANT);
					}
				}
				else
					throw new IllegalStateException("Task was not a RemoteForward");

				this.newModelsFound(mods);
				for(int i = mods.length-1; i >= 0; i--)
				{
					Model m = mods[i];
					
					//if the model was accepted, we accept it!
					MCMC_Model mcmc = (MCMC_Model) m;
					if(mcmc.isAccepted()) {
						mhm = m;
						break;
					}
				}
				
				//update bookeeping (i.e. keep track of temperature schedule)
				this.updateBookeeping(mods.length);
										
				//give the thread a new job to do
				double[] curparams = mhm.getModelParameters();
				double[][] bounds = handle.getBoundsOnModelSpace();
				Debug.println("giving "+thread.getHostname()+" generator for "+nummods+" new models", Debug.EXTRA_INFO);
				ModelGenerator mg = ModelGenerator.getNShotModelGenerator(new MH_ModelGenerator(curparams, bounds, this.getModelPerturber(), getCurrentTemperature()), nummods);
				thread.setTask(new RemoteForward(handle, mg));
			}
			else //all threads busy
			{
				Debug.println("all threads busy... sleeping", Debug.IMPORTANT);
				try { Thread.sleep(Long.MAX_VALUE); }
				catch(InterruptedException ie)
				{
					/* do nothing... just wake up and keep going */
					Debug.println("main thread got woken up..... probably is work to do", Debug.INFO);
				}
			}
			freeIndex = (freeIndex + 1) % numthreads;
		} //end of main loop
	}




	/** Implements { @link rses.inverse.ObservableInverter } */
	public void addModelObserver(ModelObserver obs)
	{ this.observers.add(obs); }
	
	/** Implements { @link rses.inverse.ObservableInverter } */
	public void removeModelObserver(ModelObserver obs)
	{	this.observers.remove(obs); }
	
	/** Implements { @link rses.inverse.ObservableInverter } */
	public long getRunningTime()
	{
		if(this.isFinished())
			return stopTime-startTime;
		return System.currentTimeMillis() - startTime; 
	}
	 
	/** Implements { @link rses.inverse.ObservableInverter } */
	public Model getBestModel()
	{ return this.mhm; }
	
	/** Implements { @link rses.inverse.ObservableInverter } */
	public boolean isFinished()
	{ return this.done; }
	
	/** Implements { @link rses.inverse.ObservableInverter } */
	public void stopInversion()
	{
		stopTime = System.currentTimeMillis();	
		this.done = true; 
	}
	
	/** Implements { @link rses.inverse.ObservableInverter } */
	public UserFunctionHandle getUserFunctionHandle()
	{ return this.handle; }
	
	/** Implements { @link rses.inverse.ObservableInverter } */
	public String getStageName()
	{	return "temperature"; }
	
	/** Implements { @link rses.inverse.ObservableInverter } */
	public double getStage()
	{ return this.currentTemp; }
	
	
	private long startTime = -1L;
	private long stopTime = -1L;
		 
	/** Implements Runnable, so you can run this inversion 
	 * in a separate thread if you want to. If you are happy 
	 * to run it in this thread, you can just call run() directly.
	 * 
	 *  Perform simmulated annealing until the end
	 *  of the specified temperature schedule is
	 *  set.
	 *  <p>
	 *  If no temperature schedule has been set, the
	 *  annealer can either determine its own, or
	 *  can throw an UnsupportedOperationException.
	 *  
	 */	 
	public void run()
	{
		try { this.internalRun(); }
		catch(Exception e)
		{
			if(e instanceof RuntimeException)
				throw (RuntimeException) e;	
			else 
				throw new RuntimeException(e); 
		}
		finally {
			stopTime = System.currentTimeMillis(); 
			this.done = true;
		}
		Debug.println("Inversion/Optimization finished", Debug.IMPORTANT);
	}


}



/** A ModelGenerator
 * 
 * @author peterr
 * 
 * @see rses.inverse.ModelGenerator
 */
class MH_ModelGenerator extends ModelGenerator
{
	private double[][] bounds;
	private MCMC_Model current;
	private ModelPerturber modpert = null;
	private double temp;
	
	
	MH_ModelGenerator(double[] start, double[][] bounds, ModelPerturber modpert, double temp)
	{
		this.current = new MCMC_Model(start, -1);
		this.bounds = bounds;
		this.modpert = modpert;
		this.temp = temp;
	}
	

	protected Model generateModel()
	{
		if(!current.isMisfitAvailable())
		{
			current.setMisfit(this.getMisfitEvaluator().getErrorForModel(current.getModelParameters()));
			current.setAccepted(true);
		}
		
		double[] newmod = this.modpert.getPerturbedModel(current.getModelParameters());
		MCMC_Model m = new MCMC_Model(newmod, -1);
		m.setMisfit(this.getMisfitEvaluator().getErrorForModel(newmod));

		if(MetropolisHastingsAnnealer.applyMetropolisTest(current, m, temp))
		{
			m.setAccepted(true);
			current = m;
		}
		
		return m;		
	}
	
}


