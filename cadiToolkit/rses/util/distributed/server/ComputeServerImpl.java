package rses.util.distributed.server;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.rmi.*;
import java.rmi.server.*;
import java.rmi.activation.*;
import java.util.Calendar;
import java.util.Properties;
import java.util.Random;


import rses.Debug;
import rses.PlatformInfo;
import rses.util.Util;
import rses.util.distributed.*;



/** The starting point for the server side of the cadi toolkit
 * 
 *  Although, in theory, other classes could implement ComputeNode,
 *   this <i>must</i> be the only implementor of ComputeNode,
 *   as we rely on it implementing multicast listener threads
 *   and the like, and, more importantly, we use it to keep track
 *   of JVM-wide information. For example, it keeps track of installed
 *   components and the like.
 * 
 *   It is also important that only 1 ComputeNodeImpl is running on 
 *   a particular computer at any one time, because otherwise they
 *   are not guaranteed to give locally unique names for their 
 *   components. 
 * 
 * @author peterr
 */


 //   NOTE: This class cannot be renamed, because the CadiServer
 //   bootstrap class makes a hard-coded assumption about the
 //   complete package/classname of this class, in order to
 //   load it

public final class ComputeServerImpl extends UnicastRemoteObject implements ComputeServer
{
	//locally scoped IP Multicast address that we use
	public static final String multicast_channel_string = "239.255.42.42";
	
	public static final int LISTEN_PORT_BASE = 4242;
	public static final int LISTEN_PORT_MAX = LISTEN_PORT_BASE+8;
	
	//how long to wait for responses from available nodes
	public static final int RESPONSE_TIMEOUT = 5000; //5 seconds 
	
	//public static final String RESTART_REPLY = "OK, restarting. This may take a while....";
	public static final String TERMINATION_REPLY = "OK, terminating connection to Compute Node";
	public static final String ARE_YOU_ALIVE_REPLY_PREFIX = "Alive and well. arch=";
	public static final String UNKNOWN_REQUEST_REPLY = "Unknown request";
	public static final String IDENTITY_STRING = "Cadi Compute Node";
	
	//UDP requests
	public static final String ARE_YOU_ALIVE_REQUEST = "Are you alive?";

	//TCP requests	
	public static final String LAST_ERROR_REQUEST = "lasterror";
	public static final String TERMINATION_REQUEST = "quit";
	public static final String IDENTIFY_REQUEST = "IDENTIFY";	
	//public static final String RESTART_REQUEST = "restart";
	public static final String INFO_REQUEST = "INFO";
		
	
	
	private String policyPath;
	private String codebaseurl;
	public ComputeServerImpl(String policyFile, String codebaseurl) throws RemoteException, IOException 
	{
		super();
	
		Debug.setVerbosityLevel(Debug.INFO);
		
		//read platform info. We shouldnt need it, but we read it anyway
		//we require that the server gets started in the cadiserver
		//directory, so we can use a relative path like this safely
		PlatformInfo.readPlatformInfo(new File(".caditk"));
		
		//make sure policypath and classpath refer to actual files
		policyPath = policyFile;
		if(!new File(policyFile).exists())
			throw new IllegalArgumentException("Specified policy file does not exist. Aborting creation of Compute Server");
		this.codebaseurl = codebaseurl;	
		
		Debug.println("starting off listener Thread in ComputeServerImpl", Debug.INFO);
		new commandThread().start();
	}

	class commandThread extends Thread
	{
		public void run()
		{
			Debug.println("Compute Server started up, entering listenForCommands() subroutine", Debug.INFO);
		
			//now sit around listening to a port for administration commands
			listenForCommands();
		}
	}

	
	
	
	
	public String obtainComputeNodeKey() throws RemoteException
	{
		return this.obtainComputeNodeKey(null);
	}
	
	public String obtainComputeNodeKey(byte[][] jarfiledata) throws RemoteException 
	{
		//generate a unique key
		Random rand = new Random();
		String key = Util.getUID();
		
		//now install a ComputeNode with that key
		try {
			createAndInstallComputeNode(key, this.policyPath, this.codebaseurl, jarfiledata);
		}
		catch(ActivationException e) {
			throw new RuntimeException(e);
		}
		catch(MalformedURLException mfurle) {
			throw new RuntimeException(mfurle);
		}
		catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
		
		return key;
	}
	
	
	
	
	
	private void createAndInstallComputeNode(String key, String securityPolicyFile, String codebase, byte[][] jarfiledata)
	throws RemoteException, ActivationException, MalformedURLException, IOException
	{		
		//Because of the Java 2 security model, a security policy should 
		//be specified for the ActivationGroup VM.  
		Properties props = new Properties(); 
		props.put("java.security.policy", 
		   securityPolicyFile); 
		
		ActivationGroupDesc.CommandEnvironment ace = null; 
		ActivationGroupDesc activationGroup = new ActivationGroupDesc(props, ace);

		//Once the ActivationGroupDesc has been created, register it 
		//with the activation system to obtain its ID
		ActivationGroupID agi = 
		   ActivationGroup.getSystem().registerGroup(activationGroup);
		
		//The "location" String specifies a URL from where the class   
		//definition will come when this object is requested (activated).
		String location = codebase;
		if(!location.endsWith(".jar") && !location.endsWith("/"))
			Debug.println("Warning: codebase does not end in a '/' and is not a jar file.... have you specified codebase correctly?", Debug.IMPORTANT);
		Debug.println("location of code in Activation Daemon registry is "+location, Debug.INFO);

		MarshalledObject data = new MarshalledObject(jarfiledata);
		 
		//The location argument to the ActivationDesc constructor will be used 
		//to uniquely identify this class; it's location is relative to the 
		//URL-formatted String, location.
		ActivationDesc desc = new ActivationDesc 
		    (agi, "rses.util.distributed.server.ComputeNodeImpl", 
		      location, data);
		
		ComputeNode nodeint = (ComputeNode) Activatable.register(desc);
		Debug.println("Got the stub for the ComputeNodeImpl", Debug.INFO);

		//Bind the stub, that was returned by the Activatable.register 
		//method, to a name in the rmiregistry
		Naming.rebind(key, nodeint);
		Debug.println("Exported Compute Node with name "+key, Debug.INFO); 
	}
	
		
	
	
	
	static Exception lastException = null;
	
	
	
	
	
	
	
	
	
	//bind to port LISTEN_PORT_BASE (or the next available port above that if
	//that port is already taken), and listen for admin commands
	private static void listenForCommands()
	{
		java.net.ServerSocket listenSocket = null;
		//DatagramSocket datagramListen = null;
		java.net.MulticastSocket datagramListen = null;
		//try to bind to a few different sockets
		for(int i = LISTEN_PORT_BASE; i <= LISTEN_PORT_MAX; i++)  
		{
			try {
				listenSocket = new java.net.ServerSocket(i);
				break; //sucessfully bound to socket.. break out of loop
			}
			catch(java.io.IOException ioe) { 
				/*couldnt bind to socket for some reason, try next one*/
				Debug.println("Couldnt bind to TCP socket "+i+" trying next socket", Debug.IMPORTANT); 
			}
		}
		for(int i = LISTEN_PORT_BASE; i <= LISTEN_PORT_MAX; i++)  
		{
			try 
			{
				//datagramListen = new DatagramSocket(i, );
				//datagramListen.setBroadcast(true);
				datagramListen = new java.net.MulticastSocket(i);
				Debug.println("Joining Cadi toolkit local multicast group", Debug.INFO);
				datagramListen.joinGroup(InetAddress.getByName(multicast_channel_string));
				break;
			}
			catch(java.io.IOException ioe) 
			{ 
				/*couldnt bind to socket for some reason, try next one*/
				Debug.println("couldnt bind Multicast datagram Socket "+i+" trying next socket", Debug.IMPORTANT);
				Debug.println(ioe, Debug.INFO);
			}
		}
		
		if(listenSocket == null || datagramListen == null) //couldnt bind to *any* socket! 
		{ 
			Debug.println("Could not bind to any socket! Exiting", Debug.CRITICAL);
			if(listenSocket != null) try { listenSocket.close(); } catch(Exception e) {}
			else if(datagramListen != null) try { datagramListen.close(); } catch(Exception e) {}
			
			System.exit(-1);
		}
		
		
		Debug.println("starting up UDP listener thread", Debug.INFO);
		//spawn a thread to listen to the datagram socket
		new connectionHandlerThread(datagramListen).start();
				
		//sit around listening to the socket for TCP connections
		java.net.Socket s = null;
		while(true)
		{
			try 
			{ 
				Debug.println("waiting for TCP connections", Debug.INFO);
				s = listenSocket.accept();
				
				Debug.println("got TCP connection, spawning thread to handle it", Debug.INFO);
				//create a thread to handle the communication
				new connectionHandlerThread(s, listenSocket.getLocalPort(), datagramListen.getLocalPort()).start();				
			}
			catch(java.io.IOException ioe) 
			{ 
				Debug.println("Exception in main listen loop of ComputeServerImpl.... "+ioe, Debug.IMPORTANT);
				ComputeServerImpl.lastException = ioe;
				/* ignore errors, just keep listening */
			}
		}
	}
	














	//the below are all utility routines
	
	
	public static void multiSend(DatagramSocket dgs, DatagramPacket dgp) 
	{
		class MultisendThread extends Thread		 
		{
			DatagramSocket s; DatagramPacket p;
			public void run()
			{
				for(int i =LISTEN_PORT_BASE; i <= LISTEN_PORT_MAX; i++)
				{
					p.setPort(i);
					try { s.send(p); }
					catch(IOException ioe)
					{
						Debug.println("Warning, IOError sending this packet: "+p,Debug.IMPORTANT);
						Debug.println(ioe, Debug.INFO);
					}
				}
			}
		};
		
		MultisendThread t = new MultisendThread();
		t.p = dgp; t.s = dgs;
		
		t.start();
	}








	public static String sendCommand(String command, Socket socket) throws IOException
	{
		Debug.println("connected to socket "+socket, Debug.INFO);
		java.io.PrintStream out = new java.io.PrintStream(socket.getOutputStream());
		java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));										
		
		//Now we send the 'real' command
		out.println(command);
		out.flush();
		String line = in.readLine();
		
		return line;
	}






	//send a command to the remote host
	public static String sendCommand(String command, InetAddress addr) throws IOException
	{
		Socket socket = connectToComputeNode(addr);
		String reply = sendCommand(command, socket);
		
		//if the user didnt send a termination request, we terminate the connection here
		if(!command.equalsIgnoreCase(ComputeServerImpl.TERMINATION_REQUEST))
		  //&& !command.equalsIgnoreCase(ComputeServerImpl.RESTART_REQUEST))
		{
			//terminate the TCP connection
			String termreply = sendCommand(ComputeServerImpl.TERMINATION_REQUEST, socket);
			if(termreply == null || !termreply.equals(ComputeServerImpl.TERMINATION_REPLY))
				Debug.println("WARNING: Could not cleanly close connection", Debug.IMPORTANT);			
		}
		
		//clean up
		socket.close();
		
		return reply;
	}
	






	//obtain a socket for talking to any compute node on host 'hostaddress'
	//If there is no compute node on that host, null is returned
	public static java.net.Socket connectToComputeNode(InetAddress hostaddress)
	{
		Socket s = null;
		for(int i =ComputeServerImpl.LISTEN_PORT_BASE; i<= ComputeServerImpl.LISTEN_PORT_MAX; i++)
		{
			try {
				Debug.println("trying to connect to "+hostaddress+" on port "+i, Debug.INFO);
				s = new Socket(hostaddress, i);
				String reply = sendCommand(ComputeServerImpl.IDENTIFY_REQUEST, s);
				if(reply.equals(ComputeServerImpl.IDENTITY_STRING))
					break; //got socket OK
			}
			catch(IOException ioe) {
				/* ignore, try the next port */;
			}
			Debug.println("failed to connect to "+hostaddress+" on port "+i, Debug.INFO);
		}
		
		return s;
	}
	
	
	








	
	public static NodeInfo[] findAvailableNodes() throws Exception
	{
		InetAddress multicast_address = InetAddress.getByName(ComputeServerImpl.multicast_channel_string);
		java.util.ArrayList nodes = new java.util.ArrayList(); 
		java.util.ArrayList seen = new java.util.ArrayList();
		
		java.net.DatagramSocket dgs_recv = new java.net.DatagramSocket();
		DatagramSocket dgs_send = new DatagramSocket();
		//if we have a interval with no replies, we assume no more are coming
		dgs_recv.setSoTimeout(ComputeServerImpl.RESPONSE_TIMEOUT);
		byte[] sendbuf = (ComputeServerImpl.ARE_YOU_ALIVE_REQUEST+" "+dgs_recv.getLocalPort()).getBytes();
		Debug.println("Sending \"Are you alive\" request on multicast channel..... "+sendbuf.length+" bytes in packet", Debug.EXTRA_INFO);

		java.net.DatagramPacket packet = new java.net.DatagramPacket(sendbuf, sendbuf.length, 
			multicast_address,ComputeServerImpl.LISTEN_PORT_BASE);
		
		//fork a new thread to collect replies
		class nodecollectthread extends Thread
		{
			DatagramSocket s;
			java.util.ArrayList receivedPackets = new java.util.ArrayList();
			boolean isdone = false;
			
			public void run()
			{
				while(true)
				{
					byte[] recvbuf = new byte[1024];
					java.net.DatagramPacket receivePacket = new java.net.DatagramPacket(recvbuf, recvbuf.length);
					try 
					{
						s.receive(receivePacket);
						receivedPackets.add(receivePacket);
					}
					catch(IOException ioe) 
					{
						if(ioe instanceof java.net.SocketTimeoutException)
							break;
						else
							/* keep going */;
					}
				}
				isdone = true;
			}
		}
		nodecollectthread nct = new nodecollectthread();
		nct.s = dgs_recv;
		nct.start();
		//wait for listener to start up
		try { Thread.sleep(1000); } catch(Exception e) {}
		
		Debug.println("Sending queries to remote nodes", Debug.INFO);
		//fork a new thread that sends packets
		multiSend(dgs_send, packet);
		
		Debug.println("waiting for replies from available nodes....", Debug.IMPORTANT);
		
		//wait for collection thread to timeout
		while(!nct.isdone)
		{
			try { Thread.sleep(1000); }
			catch(Exception e) {}
		}
		
		//now process replies
		for(int i =0; i < nct.receivedPackets.size(); i++)
		{	
			java.net.DatagramPacket receivePacket = (java.net.DatagramPacket) nct.receivedPackets.get(i);
			Debug.println("got UDP reply", Debug.EXTRA_INFO);
			Debug.println("got UDP reply of length "+receivePacket.getLength()+" from "+receivePacket.getAddress()+" on port "+receivePacket.getPort(), Debug.INFO);
			String remotehost = receivePacket.getAddress().getHostAddress();
			byte[] reply = receivePacket.getData();
			String replystring = new String(reply, 0, receivePacket.getLength());
			Debug.println("String is: "+replystring, Debug.EXTRA_INFO);
				
			//check the reply
			if(reply == null || replystring.trim().length() == 0)
				Debug.println("Got null or empty reply from host "+remotehost, Debug.IMPORTANT);
			if(replystring.startsWith(ComputeServerImpl.ARE_YOU_ALIVE_REPLY_PREFIX))
			{
				String arch = replystring.substring(ComputeServerImpl.ARE_YOU_ALIVE_REPLY_PREFIX.length(), replystring.length());
				Debug.println("Got reply from Compute Node on host "+remotehost+" (arch="+arch+")", Debug.IMPORTANT);
				if(!(seen.indexOf(remotehost) >= 0)) { //make sure we dont have duplicate entries
					nodes.add(new NodeInfo(remotehost, arch));
					seen.add(remotehost);
				}
			}
			else if(replystring.equals(ComputeServerImpl.UNKNOWN_REQUEST_REPLY))
			{
				Debug.println("Remote host "+remotehost+" replied with UNKNOWN_REQUEST", Debug.IMPORTANT);
				if(reply.length > 1) Debug.println("It thinks that the request was "+reply[1], Debug.IMPORTANT);
			}
			else
				Debug.println("Got strange reply of length "+replystring.length()+" from "+remotehost+": "+replystring, Debug.IMPORTANT);
		}
		
		Debug.println("Seems like no more replies from Compute nodes.... closing socket", Debug.INFO);
		dgs_send.close();
		dgs_recv.close();
		NodeInfo[] result = new NodeInfo[nodes.size()];
		for(int i =0; i < result.length; i++)
		{
			result[i] = (NodeInfo) nodes.get(i);
		}
			
		return result;
	}	
	
}	  
	























//	handles either a TCP or a UDP connection
class connectionHandlerThread extends Thread
{ 
	private java.net.Socket s;
	private int tcp, udp;
	 	
	private DatagramSocket ds;
 	

	public void finalize()
	{
		if(s != null)
			try {s.close();} catch(java.io.IOException ioe) {};
		if(ds != null)
			ds.close();
	}
	
	
	public connectionHandlerThread(java.net.Socket socket, int tcpport, int udpport)
	{
		tcp = tcpport;
		udp = udpport;
		s = socket;
	}
	
	public connectionHandlerThread(DatagramSocket socket)
	{
		ds = socket;
	}

 
 
	public void run()
	{
		try {
			if(s != null)
				tcpRun();
			else if(ds != null)
				udpRun();
			else
				throw new IllegalStateException("Impossible case reached");
		}
		catch(java.io.IOException ioe) {
			/* ignore errors... just quit */
			ComputeServerImpl.lastException = ioe;
			return;
		}
	}


	private void udpRun() 
	{
		byte[] buf = new byte[1024];
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		while(true)
		{
			try 
			{
				packet.setData(buf);
				ds.receive(packet);
				String command = new String(buf, 0, packet.getLength());
				String datestr = Calendar.getInstance().getTime().toString();
				Debug.println(datestr+": UDP listener Got UDP packet from "+packet.getAddress()+".... command is: "+command, Debug.INFO);
				byte[] reply = null;

				if(command.trim().length() == 0)
					continue/* null request. do nothing */;
				else if(command.startsWith(ComputeServerImpl.ARE_YOU_ALIVE_REQUEST))
				{
					String[] words = Util.getWords(command);
					int replyport = packet.getPort();
					if(words.length > Util.getWords(ComputeServerImpl.ARE_YOU_ALIVE_REQUEST).length) //reply to a different port
						replyport = Integer.parseInt(words[words.length-1]);
					Debug.println("request issued from port "+packet.getPort()+". port to reply to is "+replyport, Debug.EXTRA_INFO);
						
					String arch = System.getProperty("os.name")+System.getProperty("os.arch");
					String replstring = ComputeServerImpl.ARE_YOU_ALIVE_REPLY_PREFIX+arch;
					
					//wait for a little while so that we dont flood the network
					try {Thread.sleep((long) (ComputeServerImpl.RESPONSE_TIMEOUT/2*Math.random())); }
					catch(InterruptedException ioe) {}
					 
					reply = replstring.getBytes();
					packet.setPort(replyport);
				}
				else if(command.startsWith(ComputeServerImpl.ARE_YOU_ALIVE_REPLY_PREFIX))
				{
					//we got an unsolicited packet telling us about another node
					//we just ignore it, since we dont care about other nodes...
					
					//we dont reply, but we add the node to our known_nodes list
					//String arch = command.substring(ComputeServerImpl.ARE_YOU_ALIVE_REPLY_PREFIX.length(), command.length());
					//String addr = packet.getAddress().getHostAddress();
					//if(!ComputeServerImpl.known_nodes.contains(addr)) {
					//	ComputeServerImpl.known_nodes.add(addr);
					//	ComputeServerImpl.architectures.put(addr, arch);
					//}
					continue; //no reply
				}
				else 
				{
					reply = (ComputeServerImpl.UNKNOWN_REQUEST_REPLY+" : "+command).getBytes();
				}
				packet.setData(reply);
				//System.arraycopy(reply, 0, buf, 0, reply.length); 
				ds.send(packet);
				Debug.println("sent UDP reply of: "+new String(reply), Debug.INFO);
			}
			catch(java.io.IOException ioe)
			{
				Debug.println("UDP listener thread got exception: "+ioe, Debug.INFO);
				/* ignore error, try again*/
				ComputeServerImpl.lastException = ioe;
			}
		}
	}



	private void tcpRun() throws java.io.IOException
	{
		java.io.InputStream is = s.getInputStream();
			
		String line = readLine(is);
		java.io.PrintStream ps = new java.io.PrintStream(s.getOutputStream());

		if(!line.equalsIgnoreCase(ComputeServerImpl.IDENTIFY_REQUEST)) {
			Debug.println("TCP connection handler closed socket because IDENTIFY was not first request", Debug.IMPORTANT);
			s.close(); //IDENTIFY must be first command
			return;
		}
		
	 	//do the first IDENTIFY request
		handleCommand(line, ps);
				
		for(line = readLine(is); line != null && !line.equalsIgnoreCase(ComputeServerImpl.TERMINATION_REQUEST); line = readLine(is))
		{
			handleCommand(line, ps);
		}

		//either got a QUIT or we got an EOF.
		//either way, close connection
		ps.println(ComputeServerImpl.TERMINATION_REPLY);
		ps.flush();
		s.close();
		return;
	}




	private void handleCommand(String line, java.io.PrintStream ps) throws java.io.IOException
	{
		if(line.equalsIgnoreCase(ComputeServerImpl.IDENTIFY_REQUEST))
		{
			ps.println(ComputeServerImpl.IDENTITY_STRING);
		}
		else if(line.equalsIgnoreCase(ComputeServerImpl.INFO_REQUEST))
		{
			ps.println("TCP port is "+this.tcp+" "+s.toString()+" ; UDP port is "+this.udp);
		}
		else if(line.equalsIgnoreCase(ComputeServerImpl.LAST_ERROR_REQUEST))
		{
			if(ComputeServerImpl.lastException == null)
				ps.println("No errors.");
			else {
				ps.print("Last error was : "+ComputeServerImpl.lastException+" stacktrace: "); 
				ps.println(rses.util.Util.arrayToString(ComputeServerImpl.lastException.getStackTrace()));
			}
		}
		//else if(line.equalsIgnoreCase(ComputeServerImpl.RESTART_REQUEST))
		//{
		//	Debug.println("received restart request..... restarting", Debug.INFO);
		//	ps.println(ComputeServerImpl.RESTART_REPLY);
		//	ps.flush();
		//	ps.close();
		//	System.exit(0);
		//}
		else
			ps.println("unknown command: "+line);

		ps.flush();
	}



	private String readLine(java.io.InputStream is) throws java.io.IOException
	{
		StringBuffer sb = new StringBuffer();
		int c = is.read();
		if(c == -1)
			return null;
		
		while(c != -1 && c != '\n')
		{
			sb.append((char) c);
			c = is.read();
		}	
		return sb.toString();
	}
}
 
 
 
 
 
 