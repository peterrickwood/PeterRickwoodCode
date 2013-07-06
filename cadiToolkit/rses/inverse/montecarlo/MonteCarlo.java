package rses.inverse.montecarlo;

import java.util.ArrayList;
import java.util.List;



import rses.Model;
import rses.ModelGenerator;
import rses.inverse.InteractiveInverter;
import rses.inverse.ModelObserver;
import rses.inverse.UserFunctionHandle;
import rses.util.distributed.TaskThread;
import rses.util.distributed.NodeInfo;
import rses.util.distributed.RemoteForward;
import rses.Debug;







public class MonteCarlo implements rses.inverse.ObservableInverter, InteractiveInverter
{
	private rses.inverse.UserFunctionHandle handle = null;
	private java.util.Random random = null;
	private boolean isFinished = false;
	private long timeToRun = Long.MAX_VALUE;
	
	private double besterr = Double.POSITIVE_INFINITY;
	private Model bestModel = null;
	
	private List observers = new java.util.Vector();
	private NodeInfo[] nodes = null;
	//the number of models that we have evaluated
	private int numModels = 0;

	
	public MonteCarlo(rses.inverse.UserFunctionHandle h)
	{
		handle = h;
		this.random = new java.util.Random();
	}

	public MonteCarlo(rses.inverse.UserFunctionHandle h, int seed)
	{
		handle = h;
		this.random = new java.util.Random(seed);
	}


	private MonteCarlo(NodeInfo[] nodelist, UserFunctionHandle h)
	{
		this.handle = h;
		this.nodes = nodelist;
		this.random = new java.util.Random();
	}

	public static MonteCarlo getDistributedMonteCarlo(NodeInfo[] nodelist, UserFunctionHandle handle)
	{
		return new MonteCarlo(nodelist, handle);
	}



	private Model generateNewModel()
	{
		return generateNewModel(this.handle, this.random);
	}
	

	/* generate a new model in parameter space */
	public static Model generateNewModel(UserFunctionHandle handle, java.util.Random random)
	{
		int dim = handle.getDimensionOfModelSpace();
		double[][] bounds = handle.getBoundsOnModelSpace();
		double[] newmodel = new double[dim];

		for(int i = 0; i < dim; i++)
		{
			double range = bounds[i][1]-bounds[i][0];
			newmodel[i] = bounds[i][0] + random.nextDouble()*range;
		}

		return new Model(newmodel);
	}

	
	
	private void newModelsFound(Model[] models)
	{
		for(int i = 0; i < models.length; i++)
		{
			Model model = models[i];
			model.setDiscoveryTime(this.getRunningTime());
			double misfit = model.getMisfit();
			numModels++;
			if(misfit < besterr) {
				besterr = misfit;
				bestModel = model;
			}
		}
		for(int i = 0; i < observers.size(); i++) //notify observers
			((ModelObserver) observers.get(i)).newModelsFound(models);
	}
	
	
	
	

	public UserFunctionHandle getUserFunctionHandle()
	{
		return handle;
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
			Debug.println("Uniform sampler finished", Debug.IMPORTANT);
		}
	}


	private long startTime;
	private long stopTime = -1L;
	private void internalRun() throws Exception
	{
		startTime = System.currentTimeMillis();
		
		if(nodes != null) //it's a distributed instance
		{
			doDistributed();
			return;
		}
		else do //local version
		{
			for(int i = 0; i < 10; i++) //only check the time every 10 models
			{
				Model m = generateNewModel();
				m.setMisfit(handle.getErrorForModel(m.getModelParameters()));
				this.newModelsFound(new Model[] {m});
			}
		}
		while(this.getRunningTime() < this.timeToRun || this.isFinished);
	}



	//generate forward models and farm them out.
	//This method should return only when running time is up.
	private void doDistributed()
	{
		System.currentTimeMillis();
		Debug.println("generating models in MC inverter", Debug.INFO);
		ArrayList models = new ArrayList();
		for(int i = 0; i < nodes.length; i++)
			models.add(generateNewModel());
		Debug.println("generating threads in MC inverter", Debug.INFO);			
		List threads = TaskThread.getThreadList(nodes, RemoteForward.getTaskList(handle, models), true, true);
		int numthreads = threads.size();

		int freeIndex =0;
		int nummods = 1;
		
		while(this.getRunningTime() < this.timeToRun && !this.isFinished)
		{
			freeIndex = TaskThread.getFreeThreadIndex(threads, freeIndex);
	
			if(freeIndex >= 0)  
			{		
				TaskThread thread = (TaskThread) threads.get(freeIndex);
				Debug.println("got free thread on host "+thread.getHostname(), Debug.INFO);
				Model[] result = (Model[]) thread.getResult();

				if(thread.getTask() instanceof RemoteForward) {
					long time = thread.getComputationTimeMillisecs();
					//aim for a 1 second delay
					nummods = ((RemoteForward) thread.getTask()).calculateNumberOfModelsRequired(time, result.length, numthreads, 1.0,0.2,result[0].getMemoryEstimate()*2);
				}
				
				this.newModelsFound(result);

				//give the thread a new job to do
				ModelGenerator mg = Model.getUniformModelGenerator(handle.getBoundsOnModelSpace());
				Debug.println("giving "+nummods+" new models to host "+thread.getHostname()+" for evaluation", Debug.IMPORTANT);
				mg = ModelGenerator.getNShotModelGenerator(mg, nummods);
				thread.setTask(new RemoteForward(handle, mg)); 
			}
			else //all threads busy
			{
				if(!shutUp) Debug.println("all threads busy... sleeping", Debug.IMPORTANT);
				try { Thread.sleep(Long.MAX_VALUE); }
				catch(InterruptedException ie)
				{
					/* do nothing... just wake up and keep going */
					if(!shutUp) Debug.println("main thread got woken up..... probably is work to do", Debug.INFO);
				}				
			}
			
			freeIndex = (freeIndex + 1) % numthreads;			
		}
	}


	public Model getBestModel()
	{
		return this.bestModel;
	}


	public long getRunningTime()
	{
		if(this.isFinished())
			return stopTime-startTime;
		return System.currentTimeMillis()-startTime;
	}
	
	
	public void addModelObserver(ModelObserver obs)
	{
		observers.add(obs);
	}
	

	public void removeModelObserver(ModelObserver obs)
	{
		observers.remove(obs);
	}
	
	public double getStage()
	{
		return (double) numModels;
	}

	public String getStageName()
	{
		return "number of models";
	}
	
	
	private boolean shutUp = false;
	public void shutUp(boolean shutUp)
	{
		this.shutUp = shutUp;
	}
	
	public void setTimeToRun(long millis)
	{
		this.timeToRun = millis;
	}
	
	public void stopInversion()
	{
		this.stopTime = System.currentTimeMillis();
		this.isFinished = true;
	}

}




