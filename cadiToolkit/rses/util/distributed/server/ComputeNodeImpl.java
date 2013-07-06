package rses.util.distributed.server;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.rmi.*;
import java.rmi.activation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

import rses.Debug;
import rses.PlatformInfo;
import rses.util.FileUtil;
import rses.util.distributed.*;



/**  
 * 
 * @author peterr
 */
public final class ComputeNodeImpl extends Activatable implements ComputeNode
{
	//is any ComputeNodeImpl in this JVM currently in one
	//of its executeTask() methods?
	private static boolean isExecuting = false;
	
	private static Object globalLock = new Object();
	private static DistributableTask currentNativeTask = null;
		
	/** create a ComputeNodeImpl object, providing the activation id, 
	 *  any extension data (as a 2d array of bytes)
	 * 
	 * @param id
	 * @param data
	 * @throws RemoteException
	 * @throws java.io.IOException
	 */
	public ComputeNodeImpl(ActivationID id, MarshalledObject data)
    throws RemoteException, java.io.IOException
	{		
		// Register the object with the activation system
        // then export it on an anonymous port
		super(id, 0);
        
		
		Debug.println("ComputeNodeImpl instantiated version 2.0", Debug.IMPORTANT);

		//create any necessary extensions
		try 
		{
			byte[][] jarfileextensions = (byte[][]) data.get(); 
			if(jarfileextensions != null)
			{
				File[] files = new File[jarfileextensions.length];
				for(int i =0; i < jarfileextensions.length; i++)
				{
					File cwd = new File(System.getProperty("user.dir"));
					File f = FileUtil.createFile("ctk_ext", ".tmp", cwd, jarfileextensions[i]);
					f.mkdir();
					
					//ok, directory is created, now unzip jar file into it 
					ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(jarfileextensions[i]));
					FileUtil.unzip(zin,f);
					zin.close();
					
					//now move everything in the temporary directory to the current
					//directory, so that the default classloader can load it
					File[] dirfiles = f.listFiles();
					for(int j =0; j < dirfiles.length; j++)
					{
						File tmpf = null;
						if(dirfiles[j].isDirectory())
							tmpf = FileUtil.copyDirectory(dirfiles[j], cwd, false);
						else
							tmpf = FileUtil.copyFile(dirfiles[i], cwd);
						
						//make sure we delete these extension files on exit
						FileUtil.addTmpFile(tmpf);
					}
					
					//now delete the temporary directory we copied from
					FileUtil.deleteDirectory(f);
					
					Debug.println("unzipped jarfile to "+cwd.getAbsolutePath(), Debug.IMPORTANT);				 
				}
			}
		}
        catch(Exception e) {
        	Debug.println("Could not load extensions... exception occurred .... trying to continue anyway (full exception message follows)", Debug.IMPORTANT);
        	Debug.println(e, Debug.IMPORTANT);
        }
        
        //read in available platform information
        //we need to do this so that each ComputeNode
        //has access to the available C linker/compiler info
        //we know we are running in the cadiserver directory, so
        //a relative path should work fine
        PlatformInfo.readPlatformInfo(new java.io.File(".caditk"));
        
	}


	public void destroy() throws RemoteException, UnmarshalException
	{
		FileUtil.deleteTmpFiles();
		System.exit(0);
	}
	
	
	public Object executeTask(DistributableTask task)
	{
		//strictly speaking, we shouldnt need to lock here,
		//because each client should obtain a seperate
		//ComputeNode object to satisfay their requests.
		//However, it doesnt hurt to make sure that this
		//is the case.
		synchronized(globalLock) 
		{
			if(isExecuting)
				throw new RuntimeException("Attempt to execute task on Node that is already executing a task");
			else isExecuting = true;
		}
		
		Object result = null;
		
		if(task.containsNativeMethods())
		{
			if(currentNativeTask == null)
			{
				currentNativeTask = task;
				result = task.execute();
			}
			else if(currentNativeTask.equals(task))
			{
				result = task.execute();					
			}
			else 
			{ 
				//we cant unload native libraries, so we tell the user that they need to use a different ComputeNode
				Debug.println("New user library incompatible with current native library, rejecting execute request (acquire a new server)",Debug.IMPORTANT);
				throw new RuntimeException("New user library incompatible with current native library, rejecting execute request (acquire a new server)");
			}
		}
		else //java task.  
		{
			result = task.execute();
		}			
		
		synchronized(globalLock)
		{
			isExecuting = false;
		}
		return result;		
	}	
	
		
	
	
	public Object[] executeTaskList(DistributableTaskList tasks)
	{
		Debug.println("In executeTaskList on ComputeNode....", Debug.INFO);
		java.util.List results = new java.util.ArrayList();
		int count = 0;
		while(tasks.hasMoreTasks())
		{
			DistributableTask t = tasks.nextTask();
			Debug.println("next task in task list is "+t, Debug.INFO);
			results.add(this.executeTask(t));
		}
		return results.toArray();
	}
	
	
 
	private static Map components = new HashMap();
	private static long id = 0;
	public String installComputeComponent(ComputeComponent comp)
	{
		synchronized(components)
		{
			Debug.println("Installing ComputeComponent with id "+id, Debug.INFO);
			components.put(""+id, comp);
			String retval = ""+id;
			id++;
			return retval;
		}
	}



	/** Call the execute method of an installed component with the
	 *   specified arguments. Access to Compute Components
	 *   is <i>not</i> synchronized for you. If you install a component,
	 *   it is <i>your</i> responsibility to make sure that it is
	 *   only accessed by 1 client at a time or, otherwise, to make
	 *   sure that it is thread-safe.
	 */	
	public Object executeObject(String compId, Object[] args)
	{
		ComputeComponent comp = null;
		synchronized(components)
		{
			comp = (ComputeComponent) components.get(compId);
		}
		if(comp == null)
			Debug.println("Failed to obtain requested component. No component known with id "+compId, Debug.IMPORTANT);
		else
			Debug.println("Got component with ID "+compId, Debug.INFO);
		return comp.execute(args);
	}



	public void destroyComputeComponent(String compId)
	{
		synchronized(components)
		{
			components.remove(compId);
		}
	}
	
	
	public String[] listComponents()
	{
		synchronized(components)
		{
			Object[] keys = components.keySet().toArray();
			String[] res = new String[keys.length];
			for(int i =0; i < keys.length; i++)
			{
				ComputeComponent cc = (ComputeComponent) components.get(keys[i]);
				res[i] = keys[i]+" "+cc.toString();
			}
			return res;
		}
	}	
}