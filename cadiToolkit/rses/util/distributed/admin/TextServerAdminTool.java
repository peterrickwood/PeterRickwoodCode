package rses.util.distributed.admin;


import java.io.*;
import java.net.InetAddress;


import rses.Debug;
import rses.util.distributed.TextInterfaceServerObject;



/** A simple administration tool to enable the user to keep track of
 *   remote nodes.
 *
 */
public class TextServerAdminTool
{
	
	private static boolean connected = false;
	private static InetAddress connecthost = null;
	private static int connectport = -1;


	private static String getPrompt()
	{
		if(connected)
			return connecthost+"> ";
		else
			return "CADI_AdminTool> ";
	}

	public static void main(String[] args) throws IOException
	{
		System.out.print(getPrompt());
		BufferedReader rdr = new BufferedReader(new InputStreamReader(System.in));

		for(String line = rdr.readLine(); line != null; System.out.print(getPrompt()), line = rdr.readLine())
		{
			line = line.trim();
			if(line.length() == 0)
				continue;
				
			if(connected)
				doNonLocalCommand(line);
			else
				doLocalCommand(line);
		}
	}


	private static void doNonLocalCommand(String line) throws IOException
	{
		String reply = TextInterfaceServerObject.sendCommand(line, connecthost, connectport);

		if(reply.equals(TextInterfaceServerObject.TERMINATION_REPLY)) 
		{
			connected = false;
			Debug.println("Connection closed", Debug.IMPORTANT);
		}
		//else if(reply.equals(ComputeServerImpl.RESTART_REPLY)) 
		//{
		//	Debug.println(reply, Debug.IMPORTANT);
		//	connected = false;
		//	Debug.println("Connection closed", Debug.IMPORTANT);
		//}
		else
			Debug.println(reply, Debug.IMPORTANT); //show user the reply we got
	}
	
	








	private static void doLocalCommand(String line)
	{
		//handle all the 'local' commands first	
		if(line.toLowerCase().equals("more verbose"))
		{
			Debug.moreVerbose();
			System.out.println("Increased verbosity level");
		}
		else if(line.toLowerCase().equals("less verbose"))
		{
			Debug.lessVerbose();
			System.out.println("Decreased verbosity level");
		}
		else if(line.toLowerCase().startsWith("connect"))
		{
			String[] words = rses.util.Util.getWords(line);
			if(words.length != 3) {
				System.out.println("command must be 'connect HOSTNAME PORT'");
				return;
			}
			if(connected)  {
				Debug.println("disconnecting old connection first", Debug.IMPORTANT);
				connected = false;
			}
			 	
			connectport = Integer.parseInt(words[2]);
			try {
				InetAddress hostaddress = InetAddress.getByName(words[1]);
				connecthost = hostaddress;
				TextInterfaceServerObject.connectToServer(connecthost, connectport).close();
				connected = true;
			}
			catch(IOException ioe) {
				ioe.printStackTrace(System.err);
			}
		}
		else if(line.equalsIgnoreCase("quit")) {
			connected = false;
			System.exit(0);
		}
		else if(line.equalsIgnoreCase("help"))
			doHelp();
		else {
			Debug.println("Not connected..... (type 'help' for help)", Debug.IMPORTANT);
		}
	}







	
	
	
	private static void doHelp()
	{
		System.out.println("");
		System.out.println("Valid commands are:");
		System.out.println("\thelp                   -- display this help");
		System.out.println("\tconnect HOSTNAME PORT  -- connect to server");
		System.out.println("\tmore verbose           -- increase verbosity");
		System.out.println("\tless verbose           -- decrease verbosity");
		System.out.println("\tquit                   -- quit");
		System.out.println("");
	}
	
	
}


