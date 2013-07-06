package rses.util.distributed.server;

import java.rmi.Remote;
import java.rmi.RemoteException;








public interface ComputeServer extends Remote
{
	/** Ask the ComputeServer to create a ComputeNode object 
	 *  and return a unique key that identifies that object.
	 *  A reference to the ComputeNode object can then be
	 *  obtained using <code>Naming.lookup("//"+hostname+"/"+id);</code>
	 *  where <code>hostname</code> is host that the server
	 *  is running on and <code>id</code> is  
	 * 
	 * @param jarfiledata The raw byte data from each extension jar file to
	 *                    be added to the ComputeNode's classpath 
	 * @return
	 * @throws RemoteException
	 */
	public String obtainComputeNodeKey(byte[][] jarfiledata) throws RemoteException;

	/** Ask the ComputeServer to create a ComputeNode object 
	 *  and return a unique key that identifies that object.
	 *  A reference to the ComputeNode object can then be
	 *  obtained using <code>Naming.lookup("//"+hostname+"/"+id);</code>
	 *  where <code>hostname</code> is host that the server
	 *  is running on and <code>id</code> is  
	 * 
	 * @return
	 * @throws RemoteException
	 */
	public String obtainComputeNodeKey() throws RemoteException;
}



