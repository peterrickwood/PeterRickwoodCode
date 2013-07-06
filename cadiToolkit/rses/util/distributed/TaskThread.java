package rses.util.distributed;

import rses.Debug;
import rses.util.distributed.server.ComputeNode;
import rses.util.distributed.server.ComputeServer;

import java.rmi.*;
import java.util.List;

import javax.swing.JOptionPane;








/** This class is called TaskThread because it is a thread
 *  that, given a DistributableTask, or a DistributableTaskList, will farm 
 *  that task off and wait for a reply.
 * 
 * @author peter rickwood
 *
 */
public class TaskThread extends Thread
{	
	static java.util.List nodes = new java.util.ArrayList();
	
	/* A thread to destroy all the ComputeNodes that have been created by
	 * this JVM. 
	 */
	static class cleanupthread extends Thread 
	{
		java.util.List nodes = new java.util.ArrayList();
		cleanupthread(java.util.List nodes) { this.nodes = nodes; }
		public void run()
		{
			int numnodes = nodes.size();

			for(int i =0; i < numnodes; i++)
			{
				try {					
				ComputeNode cn = (ComputeNode) nodes.get(i);
				//strictly speaking, this should not be necessary, since 
				//this run() method should only be running in the
				//shutdown, so no other threads should be accessing
				//the nodes list. Still, I'm paranoid.
				if(cn != null) 
					cn.destroy();
				}
				catch(Exception e) {}
			}

			System.runFinalization();
		}
	}
	
	static {
		Runtime.getRuntime().addShutdownHook(new cleanupthread(nodes));
	}


	public static synchronized void registerNodeForCleanup(ComputeNode n)
	{
		nodes.add(n);
	}
	
	
	
	//The thread that should be notified when work completes
	private Thread threadToNotify = null; 
		
	private Object result = null;
	private DistributableTask task = null;
	private DistributableTaskList tasklist = null;
	private boolean resultReady;
				
	private int index = -1;			
	private String hostname;

	private ComputeNode comp = null;
	private String nodekey = null;

	
	private byte[][] extensionData = null;
	

	/** Get a collection of nodes.size() TaskThreads, each one initialized with a 
	 *   DistributableTask taken from distributableTaskList. 
	 *   (Thus, distributableTaskList and nodes must be the same size)
	 *  <p>
	 *   Thread number n is <i>not</i> guaranteed to be given task number n. 
	 *   The proper way to check which thread got assigned which model is to
	 *   check the {@link TaskThread#getIndex()} method of the thread, 
	 *   as the {@link TaskThread#setIndex()} method of each
	 *   thread is called by this method to allow the retrieval of this information.
	 * 
	 * 
	 * @param nodes  The nodes to create TaskThreads on
	 * @param distributableTaskList  A list of tasks to be evaluated , 1 for each node.
	 * @param guierror  If an error occurs, do we show a window?
	 * @param errprint   If an error occurs, do we print out information?
	 * @return A List of TaskThread instances, that have already had their
	 *                  start() method called.
	 */
	public static List getThreadList(NodeInfo[] nodes, List listOfDistributableTasks, boolean guierror, boolean errprint)
	{
		boolean cancel = false; //by default, we dont cancel
		if(nodes.length != listOfDistributableTasks.size())
			throw new IllegalArgumentException("number of nodes not equal to number of tasks");
		
		java.util.ArrayList threads = new java.util.ArrayList(nodes.length);
		for(int i =0; i < nodes.length; i++) 
		{
			TaskThread ft = null;
			String hostname = nodes[i].hostid;
			if(errprint) Debug.println("generating thread for host "+hostname, Debug.CRITICAL);
			try {
				DistributableTask t = (DistributableTask) listOfDistributableTasks.get(threads.size());
				ft = new TaskThread(hostname, t);
				ft.setIndex(threads.size());
				threads.add(ft);
				ft.start();				
			}
			catch(Exception e) 
			{
				if(errprint) 
				{
					Debug.println("CADI DIAGNOSTICS START", Debug.CRITICAL);
					Debug.println("", Debug.CRITICAL);
					Debug.println("Exception instance: ", Debug.CRITICAL);
					Debug.println(e.toString(), Debug.CRITICAL);
					Debug.println("Exception message: ", Debug.CRITICAL);
					Debug.println(e.getMessage(), Debug.CRITICAL);
					Debug.println("Exception stack trace: ", Debug.CRITICAL);
					Debug.println(e.getStackTrace().toString(), Debug.CRITICAL);
					cancel = false;
				}
				if(guierror)
				{
					int retval = JOptionPane.CANCEL_OPTION;
					while(retval != JOptionPane.YES_OPTION && retval != JOptionPane.NO_OPTION)
						retval = JOptionPane.showConfirmDialog(null, "could not start up on node "+hostname+
						rses.PlatformInfo.nl+"Do you want to continue without this node?");
					if(retval == JOptionPane.NO_OPTION)
						cancel = true;
				}
			}
			if(cancel)
				throw new RuntimeException("Inversion Aborted due to failure of node "+hostname); //abort!
		}

		return threads;

	}


	public int getIndex()
	{
		return this.index;
	}
	
	
	public void setIndex(int ind)
	{
		this.index = ind;
	}



	public static int getFreeThreadIndex(List runningTaskThreads)
	{
		return getFreeThreadIndex(runningTaskThreads, 0);
	}
	
	public static int getFreeThreadIndex(List runningTaskThreads, int startIndex)
	{
		int numthreads = runningTaskThreads.size();
		int numnull = 0;
		for(int i =0; i < numthreads; i++)
		{
			TaskThread ft = (TaskThread) runningTaskThreads.get((i+startIndex) % numthreads);
			if(ft == null) {
				numnull++;
				if(numnull == numthreads)
					throw new RuntimeException("No valid threads to run...");
				continue;
			}
			else numnull = 0;
			if(ft.isFinished())
				return (i+startIndex) % numthreads;
		}
		return -1;
	}


	public static int getFreeThreadIndex(TaskThread[] runningTaskThreads)
	{
		return getFreeThreadIndex(runningTaskThreads, 0);
	}
	
	public static int getFreeThreadIndex(TaskThread[] runningTaskThreads, int startIndex)
	{
		int numthreads = runningTaskThreads.length;
		int numnull = 0;
		for(int i =0; i < numthreads; i++)
		{
			TaskThread ft = (TaskThread) runningTaskThreads[(i+startIndex) % numthreads];
			if(ft == null) {
				numnull++;
				if(numnull == numthreads)
					throw new RuntimeException("No valid threads to run...");
				continue;
			}
			else numnull = 0;
			if(ft.isFinished())
				return (i+startIndex) % numthreads;
		}
		return -1;
	}




	public static List getFreeThreads(List runningTaskThreads)
	{
			int runsize = runningTaskThreads.size();
			List freethreads = new java.util.ArrayList(runsize);
			
			//go through threads and see if any are free
			for(int i = 0; i < runsize; i++)
			{
				TaskThread thread = (TaskThread) runningTaskThreads.get(i); 
				if(thread.isFinished()) //we found a free thread
					freethreads.add(thread);
			}
			return freethreads;
	}















	/** create a thread that will farm out a DistributableTask to
	 * computer 'hostname'  (on which we assume that there
	 * is a RMI registry running)
	 * 
	 * This thread will interrupt() the thread that created it
	 * (i.e. the thread that called it's constructor) 
	 * when it is finished.
	 * 
	 * If this thread has no work to do, it will go to sleep until
	 * it is interrupted. 
	 * 
	 * @param hostname The computer that will do the actual work
	 * @param task The task to execute
	 */	
	public TaskThread(String hostname, DistributableTask task) throws Exception
	{
		this(hostname, task, null);
	}
	


	
	/** create a thread that will farm out a DistributableTask to
	 * computer 'hostname'  (on which we assume that there
	 * is a RMI registry running)
	 * 
	 * This thread will interrupt() the thread that created it
	 * (i.e. the thread that called it's constructor) 
	 * when it is finished.
	 * 
	 * If this thread has no work to do, it will go to sleep until
	 * it is interrupted. 
	 * 
	 * @param hostname
	 * @param task
	 * @param extensionData
	 * @throws Exception
	 */
	public TaskThread(String hostname, DistributableTask task, byte[][] extensionData) throws Exception
	{
		this.extensionData = extensionData;
		this.task = task;
		this.threadToNotify = Thread.currentThread();
		this.resultReady = false;
		this.hostname = hostname;
		this.createComputeNode();		
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID))
			this.doTest();
	}

	
	
	

	/** create a thread that will farm out a DistributableTaskList to
	 * computer 'hostname'  (on which we assume that there
	 * is a RMI registry running)
	 * 
	 * This thread will interrupt() the thread that created it
	 * (i.e. the thread that called it's constructor) 
	 * when it is finished.
	 * 
	 * If this thread has no work to do, it will go to sleep until
	 * it is interrupted. 
	 * 
	 * @param hostname The computer that will do the actual work
	 * @param task The task to execute
	 */	
	public TaskThread(String hostname, DistributableTaskList tasklist) throws Exception
	{
		this(hostname, tasklist, null);
	}


	

	/** create a thread that will farm out a DistributableTaskList to
	 * computer 'hostname'  (on which we assume that there
	 * is a RMI registry running)
	 * 
	 * This thread will interrupt() the thread that created it
	 * (i.e. the thread that called it's constructor) 
	 * when it is finished.
	 * 
	 * If this thread has no work to do, it will go to sleep until
	 * it is interrupted.
	 * 
	 * @param hostname
	 * @param tasklist
	 * @throws Exception
	 */
	public TaskThread(String hostname, DistributableTaskList tasklist, byte[][] extensionData) throws Exception
	{
		this.extensionData = extensionData;
		this.tasklist = tasklist;
		this.threadToNotify = Thread.currentThread();
		this.resultReady = false;
		this.hostname = hostname;
		this.createComputeNode();
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
			this.doTest();
	}

	
	
	private void createComputeNode() throws Exception
	{
		String name = "//" + hostname + "/ComputeServer";
		ComputeServer comps = (ComputeServer) Naming.lookup(name);
		this.nodekey = comps.obtainComputeNodeKey(this.extensionData);
		this.comp = (ComputeNode) Naming.lookup("//"+hostname+"/"+nodekey);
		Debug.println("Obtained ComputeNode object "+nodekey+" from ComputeServer", Debug.INFO);		
		
		/* Each ComputeNode we create should be destroyed on exit, so
		 * that the server can reclaim its resources.
		 * 
		 */
		TaskThread.registerNodeForCleanup(this.comp);
		Debug.println("Registered ComputeNode object "+nodekey+" for deletion on JVM exit", Debug.INFO);
	}
	
	
	public void setTask(DistributableTask toevaluate)
	{
		if(!resultReady) //cant use isReady() as we already own a lock
			throw new RuntimeException("Attempt to initialize busy thread!  ("+hostname+")");
						
		resultReady = false;
		this.task = toevaluate;
		this.tasklist = null;
					
		//now wake this thread up and tell it its got work to do
		this.interrupt();
	}




	public void setTaskList(DistributableTaskList toevaluate)
	{
		if(!resultReady) //cant use isReady() as we already own a lock
			throw new RuntimeException("Attempt to initialize busy thread!  ("+hostname+")");
						
		resultReady = false;
		this.tasklist = toevaluate;
		this.task = null;
					
		//now wake this thread up and tell it its got work to do
		this.interrupt();
	}

		
		
		
	public DistributableTask getTask()
	{
		if(this.task != null)
			return task;
		throw new RuntimeException("attempt to get task when no task has been set");
	}
		
		
	public String getHostname()
	{
		return this.hostname;
	}
	

	public boolean isFinished()
	{
		return this.resultReady;
	}
	
	public Object getResult()
	{
		if(!this.resultReady)
			throw new RuntimeException("getResult() called when result is not ready!");
		return this.result;
	}
	
	private long compTime = 0L;
	public long getComputationTimeMillisecs()
	{
		return compTime;
	}
		
	
	
	
	public void run()
	{
		boolean done = false;
		Debug.println("entering run() in TaskThread on host "+hostname, Debug.INFO);
		//get a server to run this task
		while(!done)
		{	
			if(resultReady)
				throw new IllegalStateException("resultReady is true in thread before starting computation.... this should be impossible ("+hostname+")");
					
			try {  
				//do work
				doComputation(); 
				
				//now sleep until we're given some more work
				while(true) Thread.sleep((long) Integer.MAX_VALUE);
			} 
			catch(InterruptedException ie)
			{
				if(resultReady) 
					throw new IllegalStateException("Thread on host "+hostname+" got woken up, but wasn't given anything to do!!"); 
			}			
			catch (Exception e) 
			{
				Debug.println("Exception on node "+this.hostname+".... not attempting to recover... exiting main thread", Debug.IMPORTANT);
				Debug.println(e, Debug.INFO);
				done = true;
			}
		}
	}
		
		
	private void doTest() throws Exception
	{
		//create the remote task we want to do
		TestTask tt  = new TestTask();
			
		Object res = comp.executeTask(tt);
		if(res instanceof Exception)
			throw (Exception) res;		 
	}
		
	
	/** 
	 * 
	 * @return the ComputeNode that is being used to carry out the tasks
	 *  assigned with this thread
	 */
	public ComputeNode getComputeNode()
	{
		return this.comp;
	}	
		
		

	
	private void doComputation() throws Exception 
	{
		long start = System.currentTimeMillis();

		Object res = null;
		Debug.println("about to call remote (RemoteForward) execute method on ComputeNode with key "+this.nodekey, Debug.EXTRA_INFO);
		if(this.task != null && this.tasklist != null)
			throw new IllegalStateException("both task and tasklist are non-null in TaskThread.... this makes no sense");
		if(this.task != null)			
			res = comp.executeTask(this.task);
		else if(this.tasklist != null)
			res = comp.executeTaskList(this.tasklist);
		else throw new IllegalStateException("neither task nor tasklist set in TaskThread....");
		
		this.compTime = System.currentTimeMillis()-start; //remember how long it took
		this.result = res;
		this.resultReady = true;
		this.threadToNotify.interrupt(); //tell main thread we are done
	}
}


