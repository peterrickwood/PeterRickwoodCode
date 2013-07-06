package rses.util.distributed;


import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import rses.Debug;
import rses.Model;
import rses.ModelGenerator;
import rses.inverse.UserFunctionHandle;
import rses.inverse.util.JavaUserFunctionHandle;
import rses.inverse.util.LegacyUserFunctionHandle;





/** This is the forward task that can be executed on a remote host. It is
 *   created on the client side and passed as an argument to a
 *   ComputeNode on the server side.
 * 
 * In the case where the forward task that we execute on the remote
 * host has to take place with the help of a LegacyUserFunctionHandle  
 * (that is, one where native code is involved), we need to assume that
 * this executable is available on the other end (through NFS or HTTP
 * or some such). That way, we build it once on the remote end and
 * subsequent creations of RemoteForward objects can be done without
 * the need to rebuild.
 * 
 * Notes:
 * 	a) caching the handle on the remote end is OK, but <i>only</i>
 *         if we can ensure that multiple threads are not executing in the
 *         same JVM on the remote hosts (with the same native library)
 *         So, do *not* have more than 1 RemoteForward per node on
 *         a particular task. (Multiple remote forwards, each doing 
 *         different things is fine).
 * b) The contract for RemoteForward when used with a ModelGenerater
 *    is that the RemoteForward <i>MUST</i> calculate and set the misfit
 *    of each Model as it is calculated.
 *          
 * @author peter rickwood
 *
 */

public class RemoteForward implements DistributableTask
{
	//if at all possible, we keep a 'cached' version of 
	//handles, to save having to recreate them every time.	
	private static java.util.Map cachedNativeHandles = Collections.synchronizedMap(new HashMap());
	private static java.util.Map cachedJavaHandles = Collections.synchronizedMap(new HashMap());

	//thes fields are to allow us to recreate a UserFunctionHandle on
	//the other end if we need to
	private String userObjectFilePath = null;
	private String gluelib = null;
	private String userlib = null;
	private byte[] data;
	
	private UserFunctionHandle h = null;
	
	private ModelGenerator modelGenerator = null;
	
	/** In some circumstances, you may wish to create a RemoteForward and
	 *  then set the ModelGenerator later. You can use this method to obtain
	 *  just such an 'empty' RemoteForward. You <i>must</i> then set the
	 *  ModelGenerator of this RemoteForward before it is used.
	 * 
	 * @param h
	 * @return
	 */
	public static RemoteForward getEmptyRemoteForward(UserFunctionHandle h)
	{
		return new RemoteForward(h);
	}

	
	public RemoteForward(UserFunctionHandle handle, ModelGenerator modgen)
	{
		this(handle);
		this.modelGenerator = modgen;
	}
	
	
	public RemoteForward(UserFunctionHandle handle, Model[] models)
	{
		this(handle);
		this.modelGenerator = ModelGenerator.getModelGenerator(models);
	}
	
	
	public RemoteForward(UserFunctionHandle handle, Model m)
	{
		this(handle);
		this.modelGenerator = ModelGenerator.getOneShotModelGenerator(m);
	}



	private RemoteForward(UserFunctionHandle handle)
	{
		//we cant serialize a LegacyUserFunctionHandle
		if(handle instanceof LegacyUserFunctionHandle)
		{
			this.gluelib = ((LegacyUserFunctionHandle) handle).getGlueLib().getAbsolutePath();
			this.userlib = ((LegacyUserFunctionHandle) handle).getUserLib().getAbsolutePath();
			this.userObjectFilePath = ((LegacyUserFunctionHandle) handle).getOriginalObjectFilePath();
			this.data = ((LegacyUserFunctionHandle) handle).getData();
		}
		else //we can serialize a JavaUserFunctionHandle ok.
			this.h = handle;
	}		



	/** Obtain the ModelGenerator used by this RemoteForward. 
	 * 
	 * @return The ModelGenerator used by this RemoteForward. 
	 */
	public ModelGenerator getModelGenerator()
	{
		return this.modelGenerator;
	}






	public void setModelGenerator(ModelGenerator mg)
	{
		this.modelGenerator = mg;
	}

		
	
	
	/** Farm off a set of models onto remote hosts and get the results back
	 * 
	 * @param handle
	 * @param models
	 * @param nodes
	 */
	public static void evaluateModels(UserFunctionHandle handle, Model[] models, NodeInfo[] nodes)
	{
		int todo = 0;
		for(int i =0; i < models.length; i++)
			if(!models[i].isMisfitAvailable())
				todo++;
		
		if(todo == 0)
		{
			Debug.println("evaluateModels called... but nothing to do... returning", Debug.IMPORTANT);
			return;
		}
		
		if(models.length < nodes.length)
			throw new IllegalArgumentException("More nodes than models to evaluate makes no sense");
		
		int numdone = 0;
		TaskThread[] taskthreads = new TaskThread[nodes.length];
		for(int i =0; i < taskthreads.length; i++) 
		{
			try { 
				taskthreads[i] = new TaskThread(nodes[i].hostid, new RemoteForward(handle, models[i]));
				taskthreads[i].setIndex(i);
				taskthreads[i].start();
			}
			catch(Exception e) {
				Debug.println("Error trying to start a remote task on host "+nodes[i].hostid, Debug.IMPORTANT);
				taskthreads[i] = null; 
			}
		}
		
		
		while(true)
		{
			try { Thread.sleep(2000); }
			catch(Exception e) {}
			
			//find free thread
			int freeindex = TaskThread.getFreeThreadIndex(taskthreads, 0);
			if(freeindex < 0)
				continue;
			
			Debug.println("found free thread on host "+taskthreads[freeindex].getHostname(), Debug.INFO);
			
			//set misfit in appropriate model
			Model[] res = (Model[]) taskthreads[freeindex].getResult();
			if(res.length != 1) throw new IllegalStateException("should only ever get 1 result per node");
			int modind = taskthreads[freeindex].getIndex();
			if(!models[modind].isMisfitAvailable())
			{
				models[modind].setMisfit(res[0].getMisfit());
				models[modind].setDiscoveryTime(res[0].getDiscoveryTime());
				numdone++;
			}

			if(numdone == todo)
				break;
			
			//now find the next model that needs to be evaluated
			int newindex = -1;
			for(int i = modind+nodes.length; i < modind+nodes.length+models.length; i++)
			{
				if(!models[i % models.length].isMisfitAvailable())
					newindex = i % models.length;
			}
			if(newindex < 0) throw new IllegalStateException("Impossible case! no unevaluated model!");
			
			Debug.println("giving model number "+newindex+" to host "+taskthreads[freeindex].getHostname(), Debug.INFO);
			
			//set the task running again on the new task
			taskthreads[freeindex].setIndex(newindex);
			taskthreads[freeindex].setTask(new RemoteForward(handle, models[newindex]));
		}

		Debug.println("Finished evaluating models on hosts.... ", Debug.INFO);
		
	}
	
	
	
	/** Given a List of models, generate a List of RemoteForwards, where
	 *   each RemoteForward 'contains' a single model in <code>modelList</code>
	 * 
	 * @param modelList The list of models we want to generate tasks for
	 * @param handle The UserFunctionHandle which is to be used to evaluate these models
	 * @return The List of tasks
	 */
	public static List getTaskList(UserFunctionHandle handle, List modelList)
	{
		java.util.ArrayList al = new java.util.ArrayList();
		for(int i = 0; i < modelList.size(); i++) {
			rses.Model m = (rses.Model) modelList.get(i);
			al.add(new RemoteForward(handle, m));
		}
		return al;
	}
	
	
	
	
	
	/** The execute method to be called on the server side.
	 */
	public Object execute()
	{		
		//get an instance of a functionhandle
		UserFunctionHandle handle = null;
		if(gluelib != null) //native code... need to load libraries and create a new handle
		{
			Debug.println("RemoteForward.execute() trying to handle LegacyUserFunctionHandle", Debug.INFO);
			String key = this.userObjectFilePath+" "+this.userlib;
			if(cachedNativeHandles.containsKey(key))
			{
				//we can reuse the cached handle!, so we dont need to do anything really
				handle = (UserFunctionHandle) cachedNativeHandles.get(key);
				Debug.println("Oh... we've already created one of these handles,... Reusing cached handle "+handle, Debug.INFO);
			}
			else //make a new handle and cache it
			{
				Debug.println("First time I've seen this handle... creating one locally", Debug.INFO);

				if(this.data == null) //assume a shared filesystem
				{
					File userlibf = new File(userlib);
					File gluelibf = new File(gluelib);
					if(!userlibf.exists() || !gluelibf.exists()) {
						String msg = "No data stream available, and no shared filesystem (cannot find "+userlibf.getAbsolutePath()+")... I cannot recreate a function handle in these circumstances";
						Debug.println(msg, Debug.INFO);
						throw new RuntimeException(msg);
					}
					try { handle = new LegacyUserFunctionHandle(userlibf, gluelibf, userObjectFilePath);}
					catch(Exception e) {
						Debug.println("Something went wrong trying to build handle", Debug.IMPORTANT);
						if(e instanceof RuntimeException) throw (RuntimeException) e;
						else throw new RuntimeException(e);
					}
					Debug.println("created handle via shared FS OK... putting in cache so we dont have to create it again", Debug.INFO);
					cachedNativeHandles.put(key, handle); 					
				}
				else
				{
					String objname = new java.io.File(this.userObjectFilePath).getName();
					
					//we assume that, on the server side, we are executing in the cadiserver directory
					String cwd = System.getProperty("user.dir");
					try { handle = LegacyUserFunctionHandle.recreate(this.data, objname, cwd); }
					catch(Exception e) {
						Debug.println("Something went wrong trying to build handle", Debug.IMPORTANT);
						if(e instanceof RuntimeException) throw (RuntimeException) e;
						else throw new RuntimeException(e);
					}
					Debug.println("created handle OK... putting in cache so we dont have to create it again", Debug.INFO);
					cachedNativeHandles.put(key, handle); 
				}
			}
		}
		else if(h instanceof JavaUserFunctionHandle) //a 'normal' java function handle.... we need to recreate it
		{
			Debug.println("RemoteForward.execute() trying to handle JavaUserFunctionHandle", Debug.INFO);
			JavaUserFunctionHandle jh = (JavaUserFunctionHandle) h;
			try 
			{ 
				//if classdata is null, then we assume a shared filesystem, and just load via file
				if(jh.getClassData() == null) 
				{
					//first we check if we have already loaded it from the filesystem.
					Object res = cachedJavaHandles.get(jh.getImplementationClassname());
					//if we have not, then we do so, and keep a cached copy
					if(res == null) {
						Debug.println("loading java function handle "+jh.getImplementationClassname()+" from shared filesystem at "+jh.getOrigClasspath(), Debug.IMPORTANT);
						handle = new JavaUserFunctionHandle(new File(jh.getOrigClasspath()), jh.getImplementationClassname(), false);
						cachedJavaHandles.put(jh.getImplementationClassname(), handle);
					}
					//otherwise we just use the cached copy
					else {
						Debug.println("Already loaded handle "+jh.getImplementationClassname()+" from filesystem, just reusing cached copy", Debug.IMPORTANT);
						handle = (JavaUserFunctionHandle) res;
					}
				}
				//otherwise, we create it on disc locally and load it, unless we have
				//already done this, in which case we just reuse the loaded version
				else {
					Debug.println("recreating Java function handle from bytes..."+jh.getImplementationClassname(), Debug.IMPORTANT);
					handle = JavaUserFunctionHandle.recreate(jh.getClassData(), jh.getImplementationClassname());
				}
			}
			catch(Exception e) { throw new RuntimeException(e);}
			this.h = handle;
		}
		else  //we just assume that other types of handles are perfectly serializable
		{
			Debug.println("RemoteForward.execute() handle is vanilla (serializable) UserFunctionHandle", Debug.INFO);
			handle = h;
		}
		
		//do the real work
		java.util.ArrayList models = new java.util.ArrayList();
		this.modelGenerator.setMisfitEvaluator(handle);
		Model m = this.modelGenerator.generateNewModel();
		do
		{ 
			if(!m.isMisfitAvailable()) 
				throw new IllegalStateException("misfit not set in ModelGenerator, but setMisfitEvaluator() has been called for that ModelGenerator");
			models.add(m);
			m = this.modelGenerator.generateNewModel();
		}
		while(m != null);
		
		Model[] res = new Model[models.size()];
		for(int i = 0; i < res.length; i++)
			res[i] = (Model) models.get(i);

		return res;			
	}
	
	
	
	
	public boolean equals(Object obj)
	{
		if(!(obj instanceof RemoteForward))
			return false;
		RemoteForward rf = (RemoteForward) obj;
		
		if(this.gluelib != null && rf.gluelib != null)
		{
			if(rf.gluelib.equals(this.gluelib) && rf.userlib.equals(this.userlib))
				return true;
			return false;			
		}
		else throw new RuntimeException("cannot sensibly compare two RemoteForward objects unless they are both LegacyUserFunctionHandle-related");
	}
	
	
		
	

	public boolean containsNativeMethods()
	{
		if(this.gluelib != null || this.userlib != null)
			return true;
		return false;
	}
	
	
	
	/** Calculate the number of models that should be evaluated on each node before results
	 *   are ferried back to the main node, if we wish to have a delay of 'targetDelay' seconds
	 *   between each result coming back.
	 * 
	 *  The estimate is based on how long it took this RemoteForward to complete, so the caller
	 *  of this method must have timed to execution of this RemoteForward. The caller must also
	 *  know how many nodes/threads are taking part in the computation.
	 * 
	 *  The estimate is actually a stochastic estimate. Specifically, the return value of this method
	 *  is actually a number selected from a uniform distribution centred on the 'true' result.
	 *  This uniform distribution varies from [true_result] if <code>distwidth</code> is
	 *  0 (i.e. no variation at all. return true estimate), to [1..2*true_result] if <code>distwidth</code>
	 *  is 1.
	 *
	 * 
	 * NB: In a heterogeneous environment, this calculation will only be appropriate for the node
	 * that completed the RemoteForward upon which this result is calculated. For example, 
	 * if this RemoteForward completed on a slow node, the estimated number of models 
	 * should then be run on that same node.
	 * 
	 * 
	 * @param runTimeMillis How long this RemoteForward took to complete
	 * @param nummods     The number of models evaluated before
	 * @param numthreads  How many nodes/threads are taking part in this computation
	 * @param targetDelay  What delay are we aiming for between results?
 	 * @param distwidth      The width of the uniform distribution from which to select the result
	 *                                (must be between 0 and 1)
	 * @param bytesPerModEst  An estimate of the number of bytes per model. This must be passed in
	 *                        so that the calculation can take into account the fact that
	 *                        having too many models can overrun available memory on the main node
	 * @return The number of models that should be evaluated.
	 */
    private java.util.Random rand = new java.util.Random();
    private int maxmods = -1;
	public int calculateNumberOfModelsRequired(long runTimeMillis, int nummods, int numthreads, double targetDelay, double distwidth, int bytesPerModEst)
	{
		//work out the maximum number of modes we should handle
		//so that memory doesnt get filled up
		if(maxmods < 0) {
			maxmods = (int) (Runtime.getRuntime().maxMemory()*0.9)/(numthreads*bytesPerModEst);
			if(maxmods <= 0)
				throw new RuntimeException("Something strange going on.... cannot fit even a single model for each node");
		}
			
		double timepermodel = (runTimeMillis*0.001)/nummods;
		int targetnummods = (int) (1+targetDelay*numthreads/timepermodel);
		targetnummods = targetnummods + (int) ((rand.nextDouble()*2-1)*distwidth*targetnummods);
		
		//can run RemoteForward very quickly, so intermediate storage/buffering
		//of result on main node is the real issue. We have to scale things
		//back so that the main node can actually store the results from the
		//nodes.
		if(targetnummods > maxmods)
		{
			Debug.println("Not enough memory on main node... scaling back distribution to nodes... You are not getting full use of your cluster....", Debug.IMPORTANT);
			return maxmods;
		}
		if(targetnummods >= 1)
			return targetnummods;
		return 1;		
	}


}

