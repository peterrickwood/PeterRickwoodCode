package rses.inverse.na;


import java.util.ArrayList;
import java.util.List;

import rses.Debug;
import rses.Model;
import rses.ModelGenerator;
import rses.inverse.ModelObserver;
import rses.inverse.ObservableInverter;
import rses.inverse.UserFunctionHandle;
import rses.util.Util;
import rses.util.distributed.server.ComputeComponent;
import rses.util.distributed.server.ComputeNode;
import rses.util.distributed.DistributableTask;
import rses.util.distributed.TaskThread;
import rses.util.distributed.NodeInfo;
import rses.util.distributed.RemoteForward;



//TODO jumble is not implemented. That is, misfits that are equal
//are not randomly ordered

/** An implementation of Malcolm Sambridge's Neighbourhood Algorithm
 *  in Java. This is basically a straight-forward port of the fortran 77
 *  code, so is as ugly as all hell.
 * 
 * @author peter rickwood
 *
 */
public class NAJavaImpl implements ObservableInverter
{
	private java.util.ArrayList observers = new java.util.ArrayList();
	private UserFunctionHandle handle = null;
	
	private rses.Model bestmodel = null;
	private double besterr = Double.POSITIVE_INFINITY;
	
	private boolean isdone = false;
	private boolean isDistributed = false;


	private NodeInfo[] nodes;
	private int nsamplei, ns, nr, num_models;


	public NAJavaImpl(UserFunctionHandle h, int nsamplei, int ns, int nr, int num_models)
	{
		if(nsamplei > num_models)
			throw new IllegalArgumentException("Illegal argument value for Neighbourhood Algorithm. nsamplei larger than num_models");
		if(nsamplei < nr)
			throw new IllegalArgumentException("Illegal argument value for Neighbourhood Algorithm. nsamplei less than nr");

		this.handle = h;
		this.nsamplei = nsamplei;
		this.ns = ns;
		this.nr = nr;
		this.num_models = num_models;
	}


	private NAJavaImpl(NodeInfo[] nodes, UserFunctionHandle h, int nsamplei, int ns, int nr, int num_models)
	{
		this(h,nsamplei,ns,nr,num_models);
		this.nodes = nodes;
		this.isDistributed = true;
	}

	public static NAJavaImpl getDistributedInstance(NodeInfo[] nodes, UserFunctionHandle h, int nsamplei, int ns, int nr, int num_models)
	{
		return new NAJavaImpl(nodes, h, nsamplei, ns, nr, num_models);
	}



	public String getStageName()
	{
		return "blah";
	}
	
	public double getStage()
	{
		return 1.0;
	}

	public long getRunningTime()
	{
		if(this.isdone)
			return endTime-startTime;
		return System.currentTimeMillis() - startTime;
	}
	
	public boolean isFinished()
	{
		return isdone;
	}
	
	public void addModelObserver(ModelObserver obs)
	{
		observers.add(obs);
	}
	
	public void removeModelObserver(ModelObserver obs)
	{
		observers.remove(obs);
	}
	
	public UserFunctionHandle getUserFunctionHandle()
	{
		return handle;
	}
	
	public rses.Model getBestModel()
	{
		return this.bestmodel;
	}


	private void newModelsFound(Model[] models)
	{
		for(int i = 0; i < models.length; i++)
		{
			Model model = models[i];
			model.setDiscoveryTime(this.getRunningTime());
			double misfit = model.getMisfit();
			if(misfit < besterr) {
				besterr = misfit;
				bestmodel = model;
			}
		}
		for(int i = 0; i < observers.size(); i++) //notify observers
			((ModelObserver) observers.get(i)).newModelsFound(models);
	}
	
	
	
	private long startTime;
	private long endTime;
	public void run()
	{
		startTime = System.currentTimeMillis();
		if(this.isDistributed)
		{
			doDistributedNA();			
		}
		else //do local version
		{
			doLocalNA();
		}
		this.isdone = true;
		endTime = System.currentTimeMillis();
		Debug.println("Neighbourhood Algorithm finished", Debug.IMPORTANT);
	}


	private void doDistributedNA()
	{
		int nd = handle.getDimensionOfModelSpace();
		java.util.List na_models = new java.util.ArrayList();
		na_models.add(null); //0'th model doesnt count
		float[] misfit = new float[num_models+ns+1];
		int[] mfitord = new int[num_models+ns+1];
		float[] xcur = new float[nd+1];
		float[][] range = new float[3][nd+1];
		float[] dlist = new float[num_models+ns+1];

		double[][] bounds = handle.getBoundsOnModelSpace();
		for(int i =1; i <= nd; i++) {
			range[1][i] = (float) bounds[i-1][0];
			range[2][i] = (float) bounds[i-1][1];
			xcur[i] = (float) 0.5;
		}

		//generate the initial random sample and farm it off
		java.util.Random rand = new java.util.Random();
		Debug.println("generating models in NA inverter", Debug.INFO);
		ArrayList tasks = new ArrayList();
		for(int i = 0; i < nodes.length; i++) {
			ModelGenerator inner = Model.getUniformModelGenerator(handle.getBoundsOnModelSpace());
			int nm = nsamplei/nodes.length+1;
			Debug.println("Initial montecarlo task for node "+nodes[i].hostid+" has "+nm+" models", Debug.INFO);
			ModelGenerator mg = ModelGenerator.getNShotModelGenerator(inner, nm); 
			tasks.add(new RemoteForward(handle, mg));
		}
		Debug.println("generating threads in NA inverter", Debug.INFO);			
		List threads = TaskThread.getThreadList(nodes, tasks, true, true);
		int numthreads = threads.size();

		int freeIndex =0;
		int ntot = 0;
		
		
		String[] ids = new String[numthreads];
		
		//the model index up to which each thread has seen
		//so thread i has seen all models up to, *and*
		//including, model seen[i]
		int[] seen = new int[numthreads];
		
		for(int i =0; i < numthreads; i++)
		{
			TaskThread ft = ((TaskThread) threads.get(i)); 
			String hostname = ft.getHostname();
			Debug.println("installing ComputeComponent on host "+hostname, Debug.INFO);
			
			try {
				ComputeNode comp = ft.getComputeNode();
				List namodels = new ArrayList();
				namodels.add(null); //the 0th entry
				
				//install a special component on each node so that we can
				//keep track (on each node) of all the models. We need to
				//do this so that our Voronoi cell decomposition is correct
				ids[i] = comp.installComputeComponent(new NA_Component(namodels,
					ntot, Math.max(1, ns/numthreads), nd, 1, nr, misfit, mfitord, xcur, true, 500, dlist));
			}
			catch(Exception e) {
				Debug.println(e, Debug.INFO);
				Debug.println("Could not install Component on host "+hostname, Debug.CRITICAL);
			}
		}

		
		while(ntot < this.num_models)
		{
			freeIndex = TaskThread.getFreeThreadIndex(threads, freeIndex);
	
			if(freeIndex >= 0)  
			{		
				TaskThread thread = (TaskThread) threads.get(freeIndex);
				Debug.println("got free thread on host "+thread.getHostname(), Debug.INFO);
				
				Object res = thread.getResult();
				Debug.println("result from TaskThread is "+res, Debug.INFO);
				Model[] result = null;
				if(res instanceof Model[]) //it was a normal DistributableTask
 					result = (Model[]) res;
 				else if(res instanceof Object[]) //it was a NA_sampler task
 				{
 					Object[] objres = (Object[]) res;
 					result = (Model[]) objres[1];
 				}
 				else throw new IllegalStateException("Got incorrect return type of "+res+" -- couldnt do anything useful with this");
 				
				Debug.println("thread has results for "+result.length+" models", Debug.INFO);
				this.newModelsFound(result);
				for(int i = 0; i < result.length; i++)
				{
					Model m = result[i];
					float[] f = new float[m.getNumParameters()+1];
					for(int j =1; j < f.length; j++) //scale between 0 and 1
						f[j] = (float) ((m.getModelParameter(j-1)-bounds[j-1][0])/(bounds[j-1][1]-bounds[j-1][0]));
					na_models.add(f); //add normalized model to na_models list
					misfit[na_models.size()-1] = (float) m.getMisfit();
				}
				ntot += result.length;

				//still in initial sample, do uniform sampling
				if(ntot < nsamplei) {
					Debug.println("Still in initial iteration.... generating uniformly", Debug.INFO);
					ModelGenerator inner = Model.getUniformModelGenerator(bounds);
					thread.setTask(new RemoteForward(handle, ModelGenerator.getNShotModelGenerator(inner, nsamplei/nodes.length+1)));
					continue;
				}

				//give the thread all the models that it hasnt seen
				Debug.println("Showing thread models from "+(seen[freeIndex]+1)+" to "+(na_models.size()-1)+" inclusive", Debug.INFO);
				List newmod = new java.util.ArrayList(na_models.size()-seen[freeIndex]-1);
				float[] newmf = new float[na_models.size()-seen[freeIndex]-1];
				for(int i = seen[freeIndex]+1; i < na_models.size(); i++)
				{
					float[] f = (float[]) na_models.get(i);
					newmod.add(f);
					newmf[newmod.size()-1] = misfit[i];
				}
				seen[freeIndex] = na_models.size()-1;
	
				//give the thread a new job to do
				Debug.println("Constructing new NA_sampler task for thread", Debug.INFO);
				RemoteForward rf = RemoteForward.getEmptyRemoteForward(handle);
				rses.util.distributed.DistributableTaskList tasklist = new NA_sampler(thread.getComputeNode(), ids[freeIndex], newmod, newmf, range, rf);
 				thread.setTaskList(tasklist);
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
		}
	}



	private void doLocalNA()
	{		
		int nd = handle.getDimensionOfModelSpace();
		java.util.List na_models = new java.util.ArrayList();
		na_models.add(null); //0'th model doesnt count
		float[] misfit = new float[num_models+ns+1];
		int[] mfitord = new int[num_models+ns+1];
		float[] xcur = new float[nd+1];
		float[][] range = new float[3][nd+1];
		float[] dlist = new float[num_models+ns+1];

		double[][] bounds = handle.getBoundsOnModelSpace();
		for(int i =1; i <= nd; i++) {
			range[1][i] = (float) bounds[i-1][0];
			range[2][i] = (float) bounds[i-1][1];
			xcur[i] = (float) 0.5;
		}

		//initial sample
		for(int i = 0; i < this.nsamplei; i++) 
		{
			float[] f = new float[nd+1];
			f[0] = 0;
			double sum = 0.0;
			for(int j=1; j <= nd; j++) 
			{
				//f[j] = (float) (range[1][j] + Math.random()*(range[2][j]-range[1][j]));
				f[j] = (float) Math.random();
			}
			na_models.add(f);
			f = unNormalize(range, f);
			
			double[] d = new double[f.length-1];
			for(int j =0; j < d.length; j++)
				d[j] = f[j+1];
			double mf = handle.getErrorForModel(d);
				
			this.newModelFound(f, mf);
			
			//print the model
			if(Debug.equalOrMoreVerbose(Debug.EXTRA_INFO))
			{
				for(int j=1; j < nd; j++)
					System.out.print(f[j]+" ");
				System.out.println(f[nd]);
			}
			
			misfit[i+1] = (float) mf;
			mfitord[i+1] = i+1;
		}

		Debug.println("Finished generation of initial models", Debug.IMPORTANT);

		//now order misfits
		rses.util.Util.quickSortIndex(mfitord, misfit, 1, nsamplei);

		
		//now generate the rest
		for(int i = nsamplei; i < num_models; i+=ns)
		{
			Debug.println("finished "+i+" models of "+num_models+" (including initial sample)", Debug.IMPORTANT);
			
			//generate ns samples
			Debug.println("about to call NA_sample", Debug.INFO);
			List newmods = new NA_Component(na_models, i, ns, nd, 1, nr, misfit,
				mfitord, xcur, true, 500, dlist).NA_sample();
			Debug.println("finished call to NA_sample", Debug.INFO);
			na_models.addAll(newmods);

			//print the model and put it in misfits array
			Debug.println("ordering misfits array", Debug.INFO);
			for(int q = 0; q < ns; q++)
			{
				float[] f = (float[]) na_models.get(i+q+1);
				//checkIsBest(f, na_models, (float[]) na_models.get(mfitord[1]));
				f = unNormalize(range, f);
				double[] d = new double[f.length-1];
				for(int j =0; j < d.length; j++)
					d[j] = f[j+1];
				double mf = handle.getErrorForModel(d);
				misfit[i+q+1] = (float) mf;
				mfitord[i+q+1] = i+q+1;
				this.newModelFound(f, mf);
				
				insertNewMisfit(i+q+1, mf, misfit, mfitord, nr);
			}
			Debug.println("finished ordering misfits array", Debug.INFO);
		}
		
		Debug.println("Finished NA optimization", Debug.IMPORTANT);
	}
	



	public static void insertNewMisfit(int newmfindex, double mf, float[] misfit, int[] mfitord, int nr)
	{
		//if the misfit is among the best nr
		if(mf < misfit[mfitord[nr]]) 
		{
			Debug.println("new misfit of model "+mf+" is better than worst best cell misfit of "+misfit[mfitord[nr]], Debug.EXTRA_INFO);
			
			//insert the misfit in the appropriate place
			//and shift the rest up
			int index = 1;
			for(index = 1; index <= nr; index++)
				if(mf < misfit[mfitord[index]])
					break;

			//shift the rest up
			mfitord[newmfindex] = mfitord[nr];
			for(int r = nr; r > index; r--)
				mfitord[r] = mfitord[r-1];
			mfitord[index] = newmfindex;

		}
		
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID))
			checkIsSorted(mfitord, misfit, 1, nr);
	}				




	static void checkIsSorted(int[] mfitord, float[] mfit, int start, int end)
	{
		for(int i =start; i < end; i++)
		{
			if(mfit[mfitord[i]] > mfit[mfitord[i+1]])
				throw new RuntimeException("misfit orderings are skewiff");
		}
		float worstbest = mfit[mfitord[end]];
		int count = 0;
		for(int i = 1; i < mfit.length; i++)
		{
			//stop if we hit a zero because we assume this means we hit end of array
			if(mfit[i] == 0.0f)
				break;
				
			if(mfit[i] < worstbest)
				count++;
				
			if(count > (end-1))
				throw new IllegalStateException("found value better than one in best nr");				
		}
	}

	public static void checkIsBest(float[] f, java.util.List na_models, float[] best)
	{
		int nmod = na_models.size();
		double bestdist = calcDist(best, f);
		for(int i = 1; i < nmod; i++)
		{
			float[] mod = (float[]) na_models.get(i);
			double dist = calcDist(mod, f);
			if(dist == 0)
				continue;
			if(dist < bestdist) {
				System.out.println("dist is "+dist);
				System.out.println("While looking for models close to "+Util.arrayToString(f));
				System.out.println("I thought that closest model is "+Util.arrayToString(best));
				System.out.println("But closer model found is "+Util.arrayToString(f));
				throw new RuntimeException("found closer model than current best!!!!! Abort!");
			}
		}
	}


	static void printModels(List models, float[] misfit, int[] mfitord)
	{
		System.err.println("PRINTMODELS called");
		for(int i =1; i < models.size(); i++) 
		{
			float[] f = (float[]) models.get(i);
			System.err.println("model "+i+" has misfit "+misfit[i]+"  "+rses.util.Util.arrayToString(f)); 
		}
	}


	public static double calcDist(float[] f1, float[] f2)
	{
		double dist = 0.0;
		for(int j =1; j < f1.length; j++)
		{
			dist += (f1[j]-f2[j])*(f1[j]-f2[j]);
		}
		return Math.sqrt(dist);
	}



	static float[] unNormalize(float[][] range, float[] scaled)
	{
		float[] res = new float[scaled.length];
		
		for(int i =1; i < scaled.length; i++)
		{
			res[i] = range[1][i] + (range[2][i]-range[1][i])*scaled[i];
		}
		return res;
	}




	static rses.Model getModelFromUnnormalized(float[] raw)
	{
		double[] params = new double[raw.length-1];
		for(int i = 1; i < raw.length; i++)
			params[i-1] = raw[i];
		rses.Model m = new rses.Model(params);
		return m;		
	}



	private void newModelFound(float[] mod, double mf)
	{
		Model m = getModelFromUnnormalized(mod);
		m.setMisfit(mf);
		if(m.getDiscoveryTime() < 0)
			m.setDiscoveryTime(this.getRunningTime());		
		if(this.bestmodel == null)
			this.bestmodel = m;
			
		if(mf < bestmodel.getMisfit())
			bestmodel = m;
			
		int nobs = observers.size();
		for(int i =0; i < nobs; i++)
		{
			((ModelObserver) observers.get(i)).newModelFound(m);
		}
	}

}
	

























class NA_sampler implements rses.util.distributed.DistributableTaskList
{
	private DistributableTask generationTask = null;
	
	//the follow up task to evaluate misfits
	private RemoteForward evaluationTask = null;
	
	
	private int stagecount = 0; 
	public DistributableTask nextTask()
	{
		if(stagecount == 0) {
			stagecount++;
			return generationTask;
		}
		else if(stagecount == 1) {
			stagecount++;
			return evaluationTask;
		}
		else throw new IndexOutOfBoundsException("Attempt to get next task when there are no more tasks");
	}
	
	
	public boolean hasMoreTasks()
	{
		return stagecount < 2;
	}
	
	public NA_sampler(ComputeNode cn, String compid, List newmod, float[] newmf, float[][] bounds, RemoteForward rf)
	{
		this.generationTask = new nagentask(cn, compid, newmod, newmf, bounds);
		this.evaluationTask = rf;
	}
	
	class nagentask implements DistributableTask
	{
		//private String bindname;
		private ComputeNode cn;
		private String id;
		private float[] newmf;
		private List newmod;
		private float[][] bounds;

		
		nagentask(ComputeNode cn, String compid, List newmod, float[] newmf, float[][] bounds)
		{
			//this.bindname = bindname;
			this.cn = cn;
			this.newmod = newmod;
			this.id = compid;
			this.newmf = newmf;
			this.bounds = bounds;
		}
		
		public Object execute()
		{
			Object result = null;

			Debug.println("In execute method of nagentask....", Debug.EXTRA_INFO);
			//ComputeNode comp = (ComputeNode) java.rmi.Naming.lookup(bindname);
			ComputeNode comp = cn;
			
			//generate the new set of samples
			Debug.println("Found compute node, calling executeObject on component....", Debug.EXTRA_INFO);
			Object res1 = null;
			try {
				res1 = comp.executeObject(id, new Object[] {newmod, newmf});
			}
			catch(java.rmi.RemoteException re) { throw new RuntimeException("RemoteException ruined DistributableTask chain in nagentask");}
			
			Debug.println("unNormalizing models in NA_sampler....", Debug.EXTRA_INFO);
			//now turn the new samples into models, and evaluate their misfit, then return them
			if(!(res1 instanceof List))
				throw new RuntimeException("execute object (id "+id+") failed to return something of type List. Instead got "+res1);
			List listres = (List) res1;
			Model[] models = new Model[listres.size()];
			for(int i =0; i < models.length; i++)
			{
				float[] unNormalized = NAJavaImpl.unNormalize(bounds, (float[]) listres.get(i));
				models[i] = NAJavaImpl.getModelFromUnnormalized(unNormalized);
			}
			
			//set the model generator of the RemoteForward that was sent
			NA_sampler.this.evaluationTask.setModelGenerator(ModelGenerator.getModelGenerator(models));
			
			Debug.println("set up evaluation task... about to exit generation task ", Debug.INFO);

			return models;
		}
		
		public boolean containsNativeMethods()
		{
			return false;
		}

	}
}








/* 
c
c ----------------------------------------------------------------------------
c
c       NA_sample - generates a new sample of models using 
c                   the Neighbourhood algorithm by distributing
c		    nsample new models in ncells cells.
c
c	Comments:
c		 If xcur is changed between calls then restartNA 
c		 must be set to true. logical restartNA must also 
c		 be set to true on the first call.
c
c       Calls are made to various NA_routines.
c
c						M. Sambridge
c						Last updated May 1999.
c
c
c  May. 2004.
c       changes made to allow NA to work in 'continuous' mode
c  June. 2004
c       changes made to allow NA to work out ncells for itself rather
c       than relying on the user. This happens if ncells is set to -1
c       Currently this only works in continuous mode with MPI.
c
c
c-----------------------------------------------------------------------
*/
//        Subroutine NA_sample
//     &             (na_models, ntot, nsample, nd, nsleep, ncells,
//     &              misfit, mfitord, range, 
//     &              check, xcur, restartNA, 
//     &              calcmovement, nclean, dlist)




class NA_Component implements ComputeComponent
{
	private java.util.Random rand = new java.util.Random();
	private int ic = 1;
	private boolean resetlist = true;


	private List na_models; private int ntot, nsample, nd, nsleep;
	private int ncells; private float[] misfit; private int[] mfitord; 
	private float[] xcur; private boolean restartNA; private int nclean; 
	private float[] dlist; 
	NA_Component(List models, int ntot, int nsample,
		int nd, int nsleep, int ncells, float[] misfit, int[] mfitord,
		float[] xcur, boolean restartNA, int nclean, float[] dlist)
	{
		this.na_models = models; this.ntot = ntot;
		this.nsample = nsample; this.nd = nd; this.nsleep = nsleep;
		this.ncells = ncells; this.misfit = misfit; 
		this.mfitord = mfitord;
		this.xcur = xcur; this.restartNA = restartNA; this.nclean = nclean;
		this.dlist = dlist;
	}




	//given a list of new models (args[0])
	//and their misfits (args[1]) 
	//create ns new models and return the list of models generated 
	public Object execute(Object[] args)
	{
		Debug.println("In execute method of NA_Component", Debug.INFO);
		
		List newmod = (List) args[0];
		float[] mf = (float[]) args[1];
		Debug.println("still in execute of NA_Component, current models is "+na_models.size()+", num new models is "+mf.length, Debug.INFO);
		if(mf.length != newmod.size())
			throw new RuntimeException("Invalid arguments to execute on NA_Component. # new models and # new misfits does not match");
		
		boolean initialSample = (ntot == 0);	
		for(int i = 0; i < mf.length; i++, ntot++)
		{
			//add new model to our list of models
			na_models.add(newmod.get(i));
			
			//add misfit and misfitord values
			misfit[ntot+1] = mf[i];
			mfitord[ntot+1] = ntot+1;
			
			//reorder misfits so that top ncells are accurate
			if(!initialSample) {
				float[] nm = (float[]) newmod.get(i);
				Debug.println("inserting model "+rses.util.Util.arrayToString(nm)+" at index "+(ntot+1)+" with misfit "+mf[i], Debug.EXTRA_INFO);
				NAJavaImpl.insertNewMisfit(ntot+1, mf[i], misfit, mfitord, Math.min(ncells, ntot));
			}
		}
		if(initialSample) {
			rses.util.Util.quickSortIndex(mfitord, misfit, 1, ntot);
			if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID))
				NAJavaImpl.checkIsSorted(mfitord, misfit, 1, ntot);
		}
		//NAJavaImpl.printModels(na_models, misfit, mfitord);
		
		//return the list of models
		List newmods = NA_sample();		
		return newmods;
	}



	
	//for debugging only. To make sure that we are in fact sampling 
	//the next point from the cell that we think we are.
	private int getClosestCell(float[] model)
	{
		double mindist = Double.MAX_VALUE;
		int ind = -1;
		for(int i =1; i <= ntot; i++)
		{
			double dist = rses.util.Util.distance(model, (float[])na_models.get(i));
			if(dist < mindist)
			{
				mindist = dist;
				ind = i;
			}
		}
		return ind;
	}
	
	

	private int id; //local variable to NA_sample
	public List NA_sample()
	{
		java.util.ArrayList newmodels = new java.util.ArrayList(nsample);
		int cell = 1;

		//choose initial axis randomly
		int idnext = rand.nextInt(nd)+1;
        
		if(nsample <= 0) throw new RuntimeException("nsample is less than 1 in NA_sample");       
 
		this.ic = this.ic + 1;
		
		if((ic % nclean) == 0)
			resetlist = true;
		int idiff = 0;
		int ndc = 0;
        
		int mopt = mfitord[cell];
		Debug.println("mopt is "+mopt, Debug.EXTRA_INFO);
		//Debug.println("best model is "+Util.arrayToString((float[]) na_models.get(mopt)), Debug.EXTRA_INFO);
		int ind_cellnext = mopt;
		int ind_celllast = 0;
		float dsum = 0.0f;
		float dcount = 0.0f;
		int nodex = 0;
		float dminx = 0;
		
                
		int nrem = nsample % ncells;
		int nsampercell = nsample/ncells;
		if(nsampercell == 0) nsampercell++;

		//we are doing NAMPIP, so we pick a cell at random
		//and we also pick a walk length at random
		if(nsample < ncells)
			cell = rand.nextInt(ncells)+1;
		else
			cell = 1;
			
		ind_cellnext = mfitord[cell];

		int icount = 0;
		Debug.println(" nsample     = "+nsample, Debug.EXTRA_INFO);
		Debug.println(" nsampercell = "+nsampercell, Debug.EXTRA_INFO);
		if(Debug.equalOrMoreParanoid(Debug.MAX_PARANOIA))
		{
			for(int i = 1; i <= ncells; i++)
				Debug.println(i+": cell "+mfitord[i]+" has misfit "+misfit[mfitord[i]]+"  "+rses.util.Util.arrayToString((float[]) na_models.get(mfitord[i])), Debug.EXTRA_INFO);
		}

		//loop over samples
		for(int is = 1; is <= nsample; is++)
		{
			//choose Voronoi cell for sampling
			int ind_cell = ind_cellnext;
			icount = icount + 1;
			Debug.println(" sampling in cell "+ind_cell+" "+Util.arrayToString((float[]) na_models.get(ind_cell)), Debug.EXTRA_INFO);
			
			if(ind_cell != ind_celllast)
			{
				//reset walk to chosen model
				boolean[] btmp = new boolean[1];
				btmp[0] = restartNA;
				NA_restart(na_models,nd,ind_cell,xcur,btmp);
				restartNA = btmp[0];
			}
           		
			if(restartNA) {
				resetlist = true;
				restartNA = false;
			}

			int nsleep0 = nsleep;
			if(nsample < ncells)
				nsleep0 = rand.nextInt(nsampercell)+1;
				
			float[] ftmp = new float[1];
			int[] itmp = new int[1];
			//loop over walk steps
			for(int il = 1; il <= nsleep0; il++)
			{
				//loop over dimensions
				for(int iw = 1; iw <= nd; iw++)
				{
					//update dlist and nodex
					//for new axis
					if(!resetlist)
					{
						//incremental update
						//Debug.println("calling update dlist in dim "+iw+" sleep "+il, Debug.EXTRA_INFO);
						NNupdate_dlist
						(idnext,id,dlist,na_models,
						nd,ntot,xcur,itmp,ftmp);
						nodex = itmp[0];
						dminx = ftmp[0];
					}
					else
					{
						//full update
						//Debug.println("calling calc dlist in dim "+iw+" sleep "+il, Debug.EXTRA_INFO);
						NNcalc_dlist
						(idnext,dlist,na_models,
						nd,ntot,xcur,itmp);
						nodex = itmp[0];
						resetlist = false;
					}
					
					id = idnext;

					
					
					Debug.println("closest cell to xcur is "+nodex, Debug.MAX_VERBOSITY);
					if(Debug.equalOrMoreParanoid(Debug.MAX_PARANOIA)) 
					{
						Debug.println("misfit of cell we are sampling from is "+misfit[ind_cell], Debug.INFO);
						if(nodex != ind_cell)
							throw new IllegalStateException("WARNING!!! Closest cell is not the cell we aim to be sampling!!!");
						int closestcheck = this.getClosestCell(xcur);
						if(closestcheck != ind_cell)
							throw new IllegalStateException("WARNING!!! Closest cell is not the cell we aim to be sampling!!!");
						else
							Debug.println("Model sampled from correct cell (double-checked due to paranoia)", Debug.INFO);
					}

					
					/*Calculate intersection
					of current Voronoi cell 
					with current 1-D axis*/

					float[] x1x2 = NNaxis_intersect
					(xcur,id,dlist,na_models,nd,ntot,
					nodex,0.0f, 1.0f);

					//Generate new node in 
					//Voronoi cell of input point
					xcur[id] = (float) (x1x2[0]+(x1x2[1]-x1x2[0])*rand.nextDouble());

					//increment axis 
					idnext = idnext + 1;
					if(idnext > nd)idnext=1;
				}
			}

			//put new sample in list
			int j = ntot+is;
			float[] newmod = new float[nd+1];
			for(int i = 1; i <= nd; i++)
				newmod[i] = xcur[i];
			newmodels.add(newmod);
			if(Debug.equalOrMoreVerbose(Debug.EXTRA_INFO))
				Debug.println("generated model: "+rses.util.Util.arrayToString(xcur), Debug.EXTRA_INFO);


			ind_celllast = ind_cell;

			if(nsample < ncells)
			{
				icount = 0;
				cell = rand.nextInt(ncells)+1;
				ind_cellnext = mfitord[cell];
			}
			else if(icount == nsampercell)
			{
				icount = 0;
				cell = cell + 1;
				ind_cellnext = mfitord[cell];
				if(cell == (nrem+1))
					nsampercell = nsampercell - 1;
			}
		}
		
		return newmodels;
	}















/*
c
c-----------------------------------------------------------------------
c
c	NNaxis_intersect - find intersections of current Voronoi cell 
c			   with current 1-D axis.
c
c       Input:
c	      x(nd)		:point on axis
c	      dim		:dimension index (defines axis)
c	      dlist		:set of distances of base points to axis 
c	      bp(nd,nb)		:set of base points
c	      nd		:number of dimensions
c	      nb		:number of base points 
c	      resetlist		:TRUE if dlist and nodex is to be calculated
c	      nodex		:index of base node closest to x
c	      dmin_in		:distance of base node closest to x
c	      xmin		:start point along axis
c	      xmax		:end point along axis
c
c       Output:
c	      x1		:intersection of first Voronoi boundary 
c	      x2		:intersection of second Voronoi boundary 
c
c       Comment:
c	        This method uses a simple formula to exactly calculate
c		the intersections of the Voronoi cells with the 1-D axis.
c		It makes use of the perpendicluar distances of all nodes
c		to the current axis contained in the array dlist. 
c
c	        The method involves a loop over ensemble nodes for 
c		each new intersection found. For an axis intersected
c		by ni Voronoi cells the run time is proportional to ni*ne.
c
c		It is assumed that the input point x(nd) lies in
c		the Vcell of nodex, i.e. nodex is the closest node to x(nd).
c
c		Note: If the intersection points are outside of either
c		      axis range then the axis range is returned, i.e.
c
c		      		x1 is set to max(x1,xmin) and  
c		      		x2 is set to min(x2,xmin) and  
c
c                                       M. Sambridge, RSES, June 1998
c
c-----------------------------------------------------------------------
c
*/

	public float[] NNaxis_intersect(float[] x, int dim, float[] dlist,
	                             List models,int nd, int nb,
	                             int nodex,float xmin, float xmax)
	{
		float[] x1x2 = new float[2];
		x1x2[0] = xmin;
		x1x2[1] = xmax;

		float[] model = (float[]) models.get(nodex);
		float dp0 = dlist[nodex];
		float x0 = model[dim];

		//find intersection of current Voronoi cell with 1-D axis
		for(int j =1; j <= nodex-1; j++)
		{
			model = (float[]) models.get(j);
			float xc = model[dim];
			float dpc = dlist[j];
			
			//calculate intersection of
			//interface (between nodes 
			//nodex and j) and 1-D axis.
			float dx = x0 - xc;

			if(dx != 0.0f)
			{
				float xi = (float) 0.5*(x0+xc+(dp0-dpc)/dx);
				if(xi > xmin && xi < xmax)
				{
					if(xi > x1x2[0] && x0 > xc)
						x1x2[0] = xi;
					else if(xi < x1x2[1] && x0 < xc)
						x1x2[1] = xi;
				}
			}
		}


		for(int j = nodex+1; j <= nb; j++)
		{
			model = (float[]) models.get(j);
			float xc = model[dim];
			float dpc = dlist[j];

			//calculate intersection of interface (between nodes
			//nodex and j) and 1-D axis.
			float dx = x0 - xc;
			
			if(dx != 0.0f)
			{
				float xi = (float) (0.5*(x0+xc+(dp0-dpc)/dx));
				if(xi > xmin && xi < xmax)
				{
					if(xi > x1x2[0] && x0 > xc)
						x1x2[0] = xi;
					else if(xi < x1x2[1] && x0 < xc)
						x1x2[1] = xi;
				}
			}
		}

		return x1x2;
	}







/*
c
c ----------------------------------------------------------------------------
c
c       NA_restart - resets NA walk to start from input model.
c
c       Calls no other routines.
c
c						M. Sambridge, Oct. 1996
c
c ----------------------------------------------------------------------------
c*/

	public void NA_restart(List na_models, int nd, int mreset,
		float[] x, boolean[] restartNA) 
	{
		if(Debug.equalOrMoreVerbose(Debug.EXTRA_INFO))
		{
			Debug.println("NA_restart: reset to model "+mreset, Debug.EXTRA_INFO);
			Debug.print("Current model on entry: ", Debug.EXTRA_INFO);
			Debug.println(rses.util.Util.arrayToString(x), Debug.EXTRA_INFO);
		}

		float[] mod = (float[]) na_models.get(mreset);
		for(int i =1; i <= nd; i++)
			x[i] = mod[i];

		restartNA[0] = true;

		if(Debug.equalOrMoreVerbose(Debug.INFO))
		{
			Debug.print("Current model on exit: ", Debug.EXTRA_INFO);
			Debug.println(rses.util.Util.arrayToString(x), Debug.EXTRA_INFO);
		}
	}







/*c
c-----------------------------------------------------------------------
c
c       Subroutine NNcalc_dlist - calculates square of distance from
c                                 all base points to new axis (defined
c                                 by dimension dim through point x.
c                                 It also updates the nearest node and
c                                 distance to the point x.
c
c       This is a full update of dlist, i.e. not using a previous dlist.
c
c-----------------------------------------------------------------------
c*/

	public void NNcalc_dlist(int dim, float[] dlist, 
		List models, int nd, int nb, float[] x, 
		/* out */ int[] nodex)
	{
		float[] model = (float[]) models.get(1);
		double dmin = 0.0f;
		double d = 0;
		for(int j =1; j <= dim-1; j++)
		{
			d = x[j]-model[j];
			d = d*d;
			dmin = dmin + d;
		}   
		for(int j = dim+1; j <= nd; j++)
		{
			d = x[j]-model[j];
			d = d*d;
			dmin = dmin + d;
		}
		
		dlist[1] = (float) dmin;
		d = x[dim]-model[dim];
		d = d*d;
		dmin = dmin + d;
		nodex[0] = 1;
         
		for(int i = 2; i <= nb; i++)
		{  
			model = (float[]) models.get(i);
			double dsum = 0.0;
			for(int j=1; j <=dim-1; j++)
			{
				d = x[j]-model[j];
				d = d*d;
				dsum = dsum + d;
			}
			for(int j = dim+1; j <= nd; j++)
			{
				d = x[j]-model[j];
				d = d*d;
				dsum = dsum + d;
			}
			dlist[i] = (float) dsum;
			d = x[dim]-model[dim];
			d = d*d;
			dsum = dsum + d;

			if(dmin > dsum)
			{
				dmin = dsum;
				nodex[0] = i;
			}
		}

	}











/*
c
c-----------------------------------------------------------------------
c
c	Subroutine NNupdate_dlist - calculates square of distance from 
c				     all base points to new axis, assuming
c                                    dlist contains square of all distances 
c				     to previous axis dimlast. It also
c				     updates the nearest node to the
c				     point x through which the axes pass.
c				
c
c-----------------------------------------------------------------------
c*/
	public void NNupdate_dlist(int dim, int dimlast, float[] dlist,
		List models, int nd, int nb, float[] x, 
		/*out */int[] node, /* out */float[] dmin)
	{
		float[] model = (float[]) models.get(1);
		double d1 = x[dimlast]-model[dimlast];
		d1 = d1*d1;
		dmin[0] = (float) (dlist[1]+d1);
		node[0] = 1;
		double d2 = x[dim]-model[dim];
		d2 = d2*d2;
		dlist[1] = (float) (dmin[0]-d2);

		for(int i = 2; i <= nb; i++)
		{
			model = (float[]) models.get(i);
			d1 = x[dimlast]-model[dimlast];
			//ds = d1
			d1 = dlist[i]+d1*d1;
			if(dmin[0] > d1)
			{
				dmin[0] = (float) d1;
				node[0] = i;
			}

			d2 = x[dim]-model[dim];
			d2 = d2*d2;
			dlist[i] = (float) (d1-d2);
		}
	}
}