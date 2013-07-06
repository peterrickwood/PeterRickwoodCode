import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.Remote;



/** This is just a class to load the Cadi Toolkit
 *  classes over the network. It is just smart enough
 *  to start up, install a URLClassLoader, and wait for RMI requests
 * 
 * @author peterr
 */
public class CadiServer
{
	private static final String secpolf = "caditk.security.policy";
	
	
	//Usage is
	//
	//java CadiServer ARGS_TO_PASS_TO_SERVER_JVMS
	//
	//
	//for example, 
	//
	//java CadiServer -Xmx1024m
	//
	//
	public static void main(String[] args) throws Exception
	{	
		//first, we need to make sure we are in the Cadi Toolkit directory
		checkThatWeAreInCadiServerDirectory();
		

		//look in repositoryURLs.txt file for codebase
		URL[] urls = getRepositoryURLs();
		System.out.println("Using "+urls[0]+" as codebase");			

		
		CadiServer cs = new CadiServer();


		Process rmireg = null;
		Process rmid = null;
		String localhost = null;
		try {
			localhost = InetAddress.getLocalHost().getHostAddress();
			
			System.out.println("starting registry");
			try {
				rmireg = Runtime.getRuntime().exec("rmiregistry -J-Djava.net.preferIPv4Stack=true");
			}
			catch(java.io.IOException ioe) {
				System.err.println("Error starting rmiregistry.");
				System.err.println("");
				System.err.println("This is most likely caused by the fact that the 'rmiregistry'");
				System.err.println("could not be found. Please make sure it is located in your current path ");
				throw ioe;
			}
			
			cs.new StreamGobbler(rmireg.getErrorStream(), "rmiregistry err").start();
			cs.new StreamGobbler(rmireg.getInputStream(), "rmiregistry out").start();

			System.out.println("starting activation daemon");
			String rmidcmd = "rmid -log "+localhost+".log -J-Djava.net.preferIPv4Stack=true -J-Djava.security.policy="+secpolf;
			for(int i =0; i < args.length; i++)
				rmidcmd += " -C"+args[i];
			System.out.println("launching activation daemon (rmid) with: "+rmidcmd);
			try {
				rmid = Runtime.getRuntime().exec(rmidcmd);
			}
			catch(java.io.IOException ioe) {
				System.err.println("Error starting rmi daemon.");
				System.err.println("");
				System.err.println("This is most likely caused by the fact that the 'rmid'");
				System.err.println("could not be found. Please make sure it is located in your current path ");
				throw ioe;
			}
			cs.new StreamGobbler(rmid.getErrorStream(), "rmid err").start();
			cs.new StreamGobbler(rmid.getInputStream(), "rmid out").start();
		
			System.out.println("waiting for registry and activation daemon to start...");
			Thread.sleep(3000);

			System.out.println("starting ComputeServer object");
			System.setProperty("java.security.policy", secpolf);
			System.setProperty("java.rmi.server.codebase", urls[0].toString());
		
			System.out.println("");
			System.out.println("Starting up Compute Server.......");
			System.out.println("");
		
			System.setSecurityManager(new RMISecurityManager());
		}
		catch(Exception e)
		{
			System.err.println("aborting!!");
			System.err.println("Unhandled Exception..... "+e);
			e.printStackTrace();
			if(rmireg != null)
				rmireg.destroy();
			if(rmid != null)
				rmid.destroy();
			System.exit(1);
		}

		
		
		String name = "ComputeServer";
		try {
			//we install a new ClassLoader to load classes over the network
			//create a new ClassLoader
			URLClassLoader urlloader = new URLClassLoader(new URL[] {urls[0]});
			Class serverimplclass = urlloader.loadClass("rses.util.distributed.server.ComputeServerImpl");
			Constructor[] constructors = serverimplclass.getConstructors();
			Constructor constructor = serverimplclass.getConstructor(new Class[] {"".getClass(), "".getClass()});
			Remote engine = (Remote) constructor.newInstance(new Object[] {secpolf, urls[0].toString()});
			Naming.rebind(name, engine);
			System.out.println("Compute Server bound on host "+localhost);
		} 
		catch (Exception e) {
			System.err.println("Couldn't bind server!!! aborting");
			e.printStackTrace();

			if(rmireg != null)
				rmireg.destroy();
			if(rmid != null)
				rmid.destroy();
			System.exit(2);
		}
	}

	
	
	
	private static URL[] getRepositoryURLs()
	throws IOException
	{
		File f = new File("repositoryURLs.txt");
		java.util.ArrayList urls = new java.util.ArrayList();
		if(f.exists()) {
			BufferedReader rdr = new BufferedReader(new FileReader(f));
			String line = rdr.readLine();
			while(line != null && line.trim().length() > 0) {
				urls.add(new URL(line));
				line = rdr.readLine();
			}
		}
		else if(new File("caditk.jar").exists())
			urls.add(new java.io.File("caditk.jar").toURL());
		else {
			System.err.println("no codebase.... did you forget to run the install program first?");
			System.exit(-2);
		}
		
		URL[] res = new URL[urls.size()];
		for(int i =0; i < res.length; i++)
			res[i] = (URL) urls.get(i);
		return res;
	}

	
	
	
	
	
	
	public static void checkThatWeAreInCadiServerDirectory()
	{
		File cwd = new File(System.getProperty("user.dir"));
		if(!cwd.getName().equalsIgnoreCase("cadiserver")) 
		{
			System.err.println("You must start the CadiServer in the cadiserver directory");
			System.exit(1);
		}
		if(!new File("lib").exists()) {
			System.err.println("Cannot find 'lib' directory in cadiserver directory.... aborting");
			System.exit(2);
		}
		if(!new File(secpolf).exists()) {
			System.err.println("Cannot find security policy file "+secpolf);
			System.exit(2);
		}
		
		if(!new File(".caditk").exists()) 
		{
			System.err.println("****ERROR****");
			System.err.println("Are you sure that you are running this program from within the ");
			System.err.println("cadiserver directory (the one where you installed the cadiserver");
			System.err.println("program)?");
			System.err.println("");
			System.err.println("Or perhasps you have forgotton to run the installation program?");
			System.err.println();
			System.exit(-3);
		}
	}
	
	

	
	
	class StreamGobbler extends Thread
	{
		InputStream is;
		OutputStream os;
		String ident;
	    
		StreamGobbler(InputStream is, String ident)
		{
			this(is, ident, null);
		}

		StreamGobbler(InputStream is, String ident, OutputStream redirect)
		{
			this.is = is;
			this.os = redirect;
			this.ident = ident;
		}
	    
		public void run()
		{
			try
			{
				PrintWriter pw = null;
				if (os != null)
					pw = new PrintWriter(os);
	                
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line=null;
				while ( (line = br.readLine()) != null)
				{
					if (pw != null)
						pw.println(ident+"> "+line);
					System.out.println(ident+"> "+line);    
				}
				if (pw != null)
					pw.flush();
			} 
			catch (IOException ioe)
			{
				ioe.printStackTrace();  
			}
		}
	}	
}


