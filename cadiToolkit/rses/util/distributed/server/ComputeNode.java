package rses.util.distributed.server;



import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;

import rses.util.distributed.*;

/** Although, in theory, other classes could implement ComputeNode,
 *   ComputeNodeImpl in fact <i>must</i> be the only implementor of ComputeNode,
 *   as we rely on it implementing multicast listener threads
 *   and the like, and, more importantly, we use it to keep track
 *   of JVM-wide information. For example, it keeps track of installed
 *   components and loaded native libraries and the like.
 * 
 * @author peterr
 */
public interface ComputeNode extends Remote 
{
	public Object executeTask(DistributableTask t) throws RemoteException;
	
	public Object[] executeTaskList(DistributableTaskList list) throws RemoteException;
    
	public String installComputeComponent(ComputeComponent o) throws RemoteException;
    
	public Object executeObject(String objectId, Object[] args) throws RemoteException;
    
	public void destroyComputeComponent(String objectId) throws RemoteException;
    
	public String[] listComponents() throws RemoteException;	
	
	/** This method does not return, as the server responds by immediately
	 *  killing itself (and so cannot return from this method). Instead,
	 *  an UnmarshalException usually indicates that the destruction took
	 *  place sucesfully.
	 * 
	 * @throws UnmarshalException If remote ComputeNode terminated sucessfully
	 * @throws RemoteException If anything else goes wrong
	 */
	public void destroy() throws RemoteException, UnmarshalException;
}
