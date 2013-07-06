package rses.util.distributed;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.util.Properties;
import java.util.Random;


import rses.Debug;
import rses.PlatformInfo;
import rses.spatial.util.DataStore;
import rses.util.Util;
import rses.util.distributed.*;

import java.util.Calendar;




/** This is basically just a wrapper class that allows some object to send and receive text commands and responses over
 *  a socket
 *  
 *  This is a simple networked server because we dont bother going in for any RMI stuff, we just 
 *  have a network socket that we listen to plain text requests from. If this gets more complex than 
 *  exposing a couple of GET/SET functions, better to make a proper RMI interface
 *  
 *  
 * @author peterr
 *
 */
public class TextInterfaceServerObject 
{
	
	public static final String encoding = "UTF-8";
		
	//public static final String RESTART_REPLY = "OK, restarting. This may take a while....";
	public static final String TERMINATION_REPLY = "OK, terminating connection";
	public static final String ARE_YOU_ALIVE_REPLY_PREFIX = "Alive and well. arch=";
	public static final String UNKNOWN_REQUEST_REPLY = "Unknown request";
	public static final String IDENTITY_STRING = "TextInterfaceServerObject";
	
	//UDP requests
	public static final String ARE_YOU_ALIVE_REQUEST = "Are you alive?";

	//TCP requests	
	public static final String LAST_ERROR_REQUEST = "lasterror";
	public static final String TERMINATION_REQUEST = "quit";
	public static final String IDENTIFY_REQUEST = "IDENTIFY";	
	//public static final String RESTART_REQUEST = "restart";
	public static final String INFO_REQUEST = "INFO";
		

	private int LISTEN_PORTNUM = -1;
	
	private RequestServer underlying = null;
	
	
	public TextInterfaceServerObject(int portnum, RequestServer underlyingserverobject)
	throws Exception
	{		
		underlying = underlyingserverobject;
		this.LISTEN_PORTNUM = portnum;
		PlatformInfo.readPlatformInfo();
						
		Debug.println("starting off listener Thread in TextInterfaceServerObject", Debug.INFO);
		new commandThread(LISTEN_PORTNUM).start();
	}

	
	
	class commandThread extends Thread
	{
		int pnum = -1;
		commandThread(int port) { pnum=port; }
		public void run()
		{
			Debug.println("Server started up, entering listenForCommands() subroutine", Debug.INFO);
			
			//now sit around listening to a port for administration commands
			listenForCommands(pnum);
		}
	}

		
		
	Exception lastException = null;
		
		
		
		
		
	private void listenForCommands(int portnum)
	{
		java.net.ServerSocket listenSocket = null;
		java.net.DatagramSocket datagramListen = null;

		//get TCP and UDP sockets
		try {
			listenSocket = new java.net.ServerSocket(portnum);		
			datagramListen = new DatagramSocket(portnum);
		}
		catch(IOException ioe) {/* do nothing.... clean up below instead */; }
		
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
				Debug.println("Exception in main listen loop of TextInterfaceServer.... "+ioe, Debug.IMPORTANT);
				lastException = ioe;
				/* ignore errors, just keep listening */
			}
		}
	}
		







	public static String sendCommand(String command, Socket socket) throws IOException
	{
		Debug.println("connected to socket "+socket, Debug.INFO);
		java.io.PrintStream out = new java.io.PrintStream(socket.getOutputStream(), true, encoding);
		java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), encoding));										
			
		//Now we send the 'real' command
		out.println(command);
		out.flush();
		String line = in.readLine();
			
		return line;
	}






	//send a command to the remote host
	public static String sendCommand(String command, InetAddress addr, int port) throws IOException
	{
		Socket socket = connectToServer(addr, port);
		String reply = sendCommand(command, socket);
			
		//if the user didnt send a termination request, we terminate the connection here
		if(!command.equalsIgnoreCase(TextInterfaceServerObject.TERMINATION_REQUEST))
		{
			//terminate the TCP connection
			String termreply = sendCommand(TextInterfaceServerObject.TERMINATION_REQUEST, socket);
			if(termreply == null || !termreply.equals(TextInterfaceServerObject.TERMINATION_REPLY))
				Debug.println("WARNING: Could not cleanly close connection", Debug.IMPORTANT);			
		}
			
		//clean up
		socket.close();
			
		return reply;
	}
		






	//obtain a socket for talking to a server on host 'hostaddress' at port 'port'
	//If there is no compute node on that host, null is returned
	public static java.net.Socket connectToServer(InetAddress hostaddress, int port)
	{
		Socket s = null;

		try {
			Debug.println("trying to connect to "+hostaddress+" on port "+port, Debug.INFO);
			s = new Socket(hostaddress, port);
			String reply = sendCommand(TextInterfaceServerObject.IDENTIFY_REQUEST, s);
			if(!reply.equals(TextInterfaceServerObject.IDENTITY_STRING)) 
			{
				//connected, but got weird reply
				s.close();
				s = null;
			}			
		}
		catch(IOException ioe) {
			Debug.println("failed to connect to "+hostaddress+" on port "+port, Debug.INFO);
		}
			
		return s;
	}
		
		
		




//		handles either a TCP or a UDP connection
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
				lastException = ioe;
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
					else if(command.startsWith(TextInterfaceServerObject.ARE_YOU_ALIVE_REQUEST))
					{
						String[] words = Util.getWords(command);
						int replyport = packet.getPort();
						if(words.length > Util.getWords(TextInterfaceServerObject.ARE_YOU_ALIVE_REQUEST).length) //reply to a different port
							replyport = Integer.parseInt(words[words.length-1]);
						Debug.println("request issued from port "+packet.getPort()+". port to reply to is "+replyport, Debug.EXTRA_INFO);
							
						String arch = System.getProperty("os.name")+System.getProperty("os.arch");
						String replstring = TextInterfaceServerObject.ARE_YOU_ALIVE_REPLY_PREFIX+arch;
												 
						reply = replstring.getBytes();
						packet.setPort(replyport);
					}
					else if(command.startsWith(TextInterfaceServerObject.ARE_YOU_ALIVE_REPLY_PREFIX))
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
						reply = (TextInterfaceServerObject.UNKNOWN_REQUEST_REPLY+" : "+command).getBytes();
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
					lastException = ioe;
				}
			}
		}



		private void tcpRun() throws java.io.IOException
		{
			java.io.InputStreamReader is = new java.io.InputStreamReader(s.getInputStream(), encoding);
				
			String line = readLine(is);
			Debug.println("tcpRun got command: "+line,Debug.INFO);
			java.io.PrintStream ps = new java.io.PrintStream(s.getOutputStream(), true, encoding);

			if(!line.equalsIgnoreCase(TextInterfaceServerObject.IDENTIFY_REQUEST)) {
				Debug.println("TCP connection handler closed socket because IDENTIFY was not first request", Debug.IMPORTANT);
				s.close(); //IDENTIFY must be first command
				return;
			}
			
		 	//do the first IDENTIFY request
			handleCommand(line, ps);
					
			for(line = readLine(is); line != null && !line.equalsIgnoreCase(TextInterfaceServerObject.TERMINATION_REQUEST); line = readLine(is))
			{
				Debug.println("rcpRun got command: "+line,Debug.INFO);
				handleCommand(line, ps);
			}

			//either got a QUIT or we got an EOF.
			//either way, close connection
			ps.println(TextInterfaceServerObject.TERMINATION_REPLY);
			ps.flush();
			s.close();
			return;
		}




		private void handleCommand(String line, java.io.PrintStream ps) throws java.io.IOException
		{
			if(line.equalsIgnoreCase(TextInterfaceServerObject.IDENTIFY_REQUEST))
			{
				ps.println(TextInterfaceServerObject.IDENTITY_STRING);
			}
			else if(line.equalsIgnoreCase(TextInterfaceServerObject.INFO_REQUEST))
			{
				ps.println("TCP port is "+this.tcp+" "+s.toString()+" ; UDP port is "+this.udp);
			}
			else if(line.equalsIgnoreCase(TextInterfaceServerObject.LAST_ERROR_REQUEST))
			{
				if(lastException == null)
					ps.println("No errors.");
				else {
					ps.print("Last error was : "+lastException+" stacktrace: "); 
					ps.println(rses.util.Util.arrayToString(lastException.getStackTrace()));
				}
			}
			else
			{
				ps.println(TextInterfaceServerObject.this.underlying.handleRequest(line));
				//ps.println("unknown command: "+line);
			}
			
			ps.flush();
		}



		private String readLine(java.io.InputStreamReader is) throws java.io.IOException
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
			return sb.toString().trim();
		}
	}
	
	
	
	//for testing only
	public static void main(String[] args)
	throws Exception
	{
		Debug.setVerbosityLevel(Debug.INFO);
		
		//test a RequestServingDataStore
		RequestServer svrobj = new rses.spatial.util.RequestServingDataStore();
		
		
		TextInterfaceServerObject server = new TextInterfaceServerObject(42426, svrobj);
		
	}
	 
	
}
