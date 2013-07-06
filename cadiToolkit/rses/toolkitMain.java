package rses;


import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarFile;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JWindow;




import rses.util.ArgumentParser;
import rses.util.Util;
import rses.util.gui.GuiUtil;
import rses.util.distributed.server.ComputeServerImpl;
import rses.util.distributed.NodeInfo;

/** The starting point for the client side of the cadi toolkit.
 * 
 * @author peterr
 */


// NOTE: This class should not be renamed (or moved to another package),
// because the CadiClient relies on a hard-coded String to load it over
// a network.

public class toolkitMain
{
	private boolean showsplash = true;
	//should we assume a shared filesystem? If so, we
	//can dispense with a lot of the copying of user files
	//and stuff
	private boolean sharedFS = false;
	public boolean assumeSharedFS()
	{
		return sharedFS;
	}
	
	private NodeInfo[] nodelist;
	private java.io.File userdir;
	private String tkpath;
	
	
	
	private static boolean instantiated = false; 
	
	public toolkitMain(String[] args) throws Exception
	{
		synchronized(toolkitMain.class) {
			if(instantiated) {
				Debug.println("Cannot create two instances of toolkitMain within the one JVM.. singleton only", Debug.CRITICAL);
				System.exit(1);
			}
			else
				instantiated = true;
		}
		
		//parse startup arguments
		String cadiapp = this.init(args);
		
		//after init, we can start up the splash screen
		if(cadiapp.equals("CADImain") && this.showsplash) 
		{
			try {	startSplash(); }
			catch(java.io.IOException ioe) {
				Debug.println("Cannot load/display splash image.. skipping splash screen", Debug.IMPORTANT);
			}
		}
		
		//read .caditk file for information
		PlatformInfo.readPlatformInfo(new File(tkpath, ".caditk"));
		
		userdir = new java.io.File(System.getProperty("user.dir"));
		if(!userdir.exists())
			userdir = java.io.File.listRoots()[0];

		//load any extension modules
		loadExtensions();
		
		//register cleanup thread to remove any temporary files we create
		class cleanupthread extends Thread 
		{
			private NodeInfo[] nodelist;
			cleanupthread(NodeInfo[] nodelist) { this.nodelist = nodelist; }
			public void run()
			{			
				Debug.println("Running clean-up thread", Debug.IMPORTANT);
				rses.util.FileUtil.deleteTmpFiles();
				System.runFinalization();
			}
		}
		Runtime.getRuntime().addShutdownHook(new cleanupthread(nodelist));
		
		//now launch the requested application
		if(cadiapp.equals("CADImain")) {
			Debug.println("Launching main window", Debug.INFO);
			javax.swing.JFrame frame = null;
			frame = new CADIMainFrame(this);
			frame.setSize(800,700);
			frame.setLocation(GuiUtil.getCentredWindowPoint(frame));
			frame.show();
		}
		//else if(cadiapp.toLowerCase().equals("generatefieldfile"))
		//{
			//dont launch it as a thread, since we want to exit when it exits
			//new rses.apps.GenerateFieldFile(args).run();
			//System.exit(0);
		//}
		else {
			Debug.println("requested program: "+cadiapp+" is not available", Debug.CRITICAL);
			System.exit(2);
		}
	}



	private void startSplash() throws java.io.IOException
	{
		Debug.println("starting splash screen.... ", Debug.EXTRA_INFO);
		
		//
		File splashimage = new File(this.tkpath, "cadilogo.png");
		
		BufferedImage img = ImageIO.read(splashimage);
		
		//start up a splash screen with the cadi logo
		JWindow splash = new JWindow();
		
		class SplashPanel extends javax.swing.JPanel
		{
			BufferedImage img;
			SplashPanel(BufferedImage img) {
				this.img = img;
			}
			
			public void paintComponent(java.awt.Graphics g) {
				g.drawImage(img, 0, 0, this);
			}
		}
		
		JPanel panel = new SplashPanel(img);
		panel.setSize(img.getWidth(), img.getHeight());
		splash.getContentPane().add(panel);
		
		JPanel progpanel = new JPanel();
		JProgressBar prog = new JProgressBar(0, 100);
		progpanel.add(prog);
		splash.getContentPane().add(progpanel);
		
		splash.setSize(img.getWidth(), img.getHeight());
		splash.setLocation(rses.util.gui.GuiUtil.getCentredWindowPoint(splash));
		splash.setVisible(true);
		
		for(int i = 0; i < 100; i++)
		{
			prog.setValue(i);
			try { Thread.sleep(20); } catch(InterruptedException e) {}
		}
		
		splash.setVisible(false);
		splash.dispose();
		
		Debug.println("finished displaying splash image...", Debug.EXTRA_INFO);
	}
	
	
	

	public static void main(String[] args) throws Exception
	{
		toolkitMain tk = new toolkitMain(args);
	}

	

	public String gettkpath()
	{
		return this.tkpath;
	}
	
	
	
	public NodeInfo[] getNodes()
	{
		return this.nodelist;
	}
	
	
	
	
	
	
	

	private void help()
	{
		String nl = PlatformInfo.nl;
		String msg = nl+
			"usage:"+nl+nl+
			"java CadiClient TKPATH OPTIONS"+nl+
			""+nl+
			"where TKPATH is the full (absolute) path to the place where the cadi client"+nl+
			"is installed, and OPTIONS are:"+nl+nl+
			"[--local | --distributed | --machinefile=FILENAME]"+nl+
			"--distributed specifies distributed execution, where the toolkit scans the "+nl+
			"local network for other computers willing to take part in a computation, or"+nl+
			"(with the --machinefile option), you can specify a file with a list of machines"+nl+
			"to run on. If neither is specified, the toolkit will just run on the local"+nl+
			"machine (this is the default)"+nl+nl+
			"[--sharedfs]"+nl+
			"specify that the CADI toolkit can assume a shared (networked) filesystem. "+nl+
			"The default is to assume that there is no shared filesystem. Only used in"+nl+
			"distributed mode"+nl+nl+
			"[--help]"+nl+
			"print this help message"+nl+nl+
			"[--verbosity=NUM]"+nl+
			"tell the toolkit how verbose it should be (lower numbers mean less verbosity)"+nl+
			"Minimum is 0, Maximum is "+(Debug.MAX_VERBOSITY-Debug.MIN_VERBOSITY)+nl+nl+
			"[--debuglvl=NUM]"+nl+
			"tell the toolkit how much run-time checking it should do (lower numbers mean"+nl+
			"faster execution, but fewer checks). Minimum is 0, Maximum is "+(Debug.MAX_PARANOIA-Debug.MIN_PARANOIA)+nl+nl+
			"[--skipsplash]"+nl+
			"skip the startup splash screen"+nl+nl+
			"[--program=PROG]"+nl+
			"Do not launch the default CADI main window, instead launch CADI program PROG directly"+nl+
			"Note that this option, if present, must be the last option, with additional arguments"+nl+
			"being passed to the called program"+nl;
		Debug.println(msg, Debug.IMPORTANT);
		System.exit(0);
	}
	
	
	
	//Allowed options
	// args[0] = cadi toolkit path
	//--machinefile = MFILE, --distributed, --local
	//--sharedfs
	//--verbosity=NUM (0 = min verbosity)
	//--debuglvl=NUM (0 = most lax debugging)
	//--skipsplash
	//--program=PROG
	//--help
	private String init(String[] args) throws Exception
	{
		NodeInfo[] nodes = null;
		java.io.File mf = null;		
		
		if(args == null || args.length == 0)
			help();
		
		//the path to the toolkit
		this.tkpath = args[0];
		
		if(!new java.io.File(tkpath).exists())
			throw new RuntimeException("Could not find CADI Toolkit directory.... aborting....");

		
		ArgumentParser parser = new ArgumentParser(args);
		
		//if any arguments are not recognized, we print help, so we go through them first
		//and make sure we know about them
		String[] validargs = new String[] {"--help", "--verbosity",
				"--debuglvl", "--distributed", "--machinefile", "--local",
				"--sharedfs", "--skipsplash", "--program"};
		
		//skip the first argument, which is the toolkit path
		for(int i =1; i < parser.getNumArgs(); i++) {
			String opt = parser.getOptions()[i];
			if(opt.equals("--program"))
				break; //dont do any more checking. 
				       //--program must be last known argument
				       //with extra arguments passed to the called program
			if(Util.getIndex(opt, validargs) < 0) {
				Debug.println("", Debug.CRITICAL);
				Debug.println("Option "+opt+" not recognized", Debug.CRITICAL);
				help();
			}
		}
		
		
		if(parser.isOption("--help"))
				help();
		
		if(parser.isOption("--verbosity"))
		{
			String vlvlstr = parser.getValue("--verbosity");
			if(vlvlstr == null) {
				Debug.println("No value specified for verbosity... must be a number >= 0", Debug.CRITICAL);
				System.exit(1);
			}
			int vlvl = -1;
			try { vlvl = Integer.parseInt(vlvlstr); }
			catch(NumberFormatException nfe) {
				Debug.println("Invalid value specified for verbosity... must be a number >= 0", Debug.CRITICAL);
				System.exit(1);
			}
			Debug.println("Setting verbosity level to "+vlvl, Debug.IMPORTANT);
			Debug.setVerbosityLevel(Debug.MIN_VERBOSITY);
			for(int i =0; i < vlvl; i++)
				Debug.moreVerbose();
		}

		
		Debug.println("parsing other arguments ", Debug.EXTRA_INFO);
		if(Debug.equalOrMoreVerbose(Debug.MAX_VERBOSITY))
			parser.print(Debug.getPrintStream(Debug.MAX_VERBOSITY));
		
		if(parser.isOption("--debuglvl"))
		{
			String dlvlstr = parser.getValue("--debuglvl");
			if(dlvlstr == null) {
				Debug.println("No value specified for debug level... must be a number >= 0", Debug.CRITICAL);
				System.exit(1);
			}
			int dlvl = -1;
			try { dlvl = Integer.parseInt(dlvlstr); }
			catch(NumberFormatException nfe) {
				Debug.println("Invalid value specified for debug level... must be a number >= 0", Debug.CRITICAL);
				System.exit(1);
			}
			Debug.println("Setting debug level to "+dlvl, Debug.IMPORTANT);
			Debug.setDebugLevel(Debug.MIN_PARANOIA);
			for(int i =0; i < dlvl; i++)
				Debug.moreParanoid();
		}

		
		if(parser.isOption("--distributed")) 
		{
			Debug.println("Running toolkit in distributed mode ", Debug.IMPORTANT);
			Debug.println("scanning network for available nodes... please wait", Debug.IMPORTANT);
			nodes = ComputeServerImpl.findAvailableNodes();
			for(int i =0; i < nodes.length; i++) 
				Debug.println("adding "+nodes[i].hostid+" ("+nodes[i].platform+") to list of available nodes", Debug.IMPORTANT);
		}
		else if(parser.isOption("--machinefile"))
		{
			Debug.println("Running toolkit in distributed mode with user-specified machinefile ", Debug.IMPORTANT);
			String mfile = parser.getValue("--machinefile");
			if(mfile == null) {
				Debug.println("No value specified for --machinefile option", Debug.CRITICAL);
				System.exit(1);
			}
			mf = new java.io.File(mfile);
			if(!mf.exists())
			{
				Debug.println("Specified machinefile '"+mf.getAbsolutePath()+"' does not exist", Debug.CRITICAL);
				System.exit(1);
			}
			nodes = getNodes(mf);
		}
		else if(parser.isOption("--local") && parser.isOption("--machinefile"))
		{
			Debug.println("--local and --machinefile options cannot be mixed.", Debug.CRITICAL);
			System.exit(1);
		}
		else
		{
			Debug.println("Running toolkit in standalone mode ", Debug.IMPORTANT);
			nodes = new NodeInfo[0];
		}
		
		Debug.println("found "+nodes.length+" nodes", Debug.IMPORTANT);
		this.nodelist = nodes;
		
		if(parser.isOption("--sharedfs"))
		{
			Debug.println("Assuming a shared filesystem....", Debug.IMPORTANT);
			this.sharedFS = true;
		}
		
		if(parser.isOption("--skipsplash"))
			this.showsplash = false;
		
		String requestedprog = "CADImain";
		if(parser.isOption("--program"))
		{
			if(parser.getValue("--program") == null) {
				Debug.println("No program specified with --program option", Debug.CRITICAL);
				Debug.println("", Debug.CRITICAL);
				help();
			}
			else
				requestedprog = parser.getValue("--program");
		}
		return requestedprog;
	}
	





	private static NodeInfo[] getNodes(java.io.File machinefile) throws java.io.IOException
	{
		java.util.ArrayList result = new java.util.ArrayList();
		
		if(machinefile == null || !machinefile.exists())
			throw new IllegalStateException("Illegal state reached. Specified machinefile doesn't exist, but this should have been checked previously.");	
				
		//read the nodes from the machinefile
		java.io.BufferedReader rdr = new java.io.BufferedReader(new java.io.FileReader(machinefile));
		int linenum = 0;
		String line = rdr.readLine();
		while(line != null)
		{
			linenum++;
			if(line.indexOf(':') >= 0)
			{
				Debug.println("Your machinefile seems to be in an invalid format.", Debug.CRITICAL);
				Debug.println("Each line must be the name of a computer and (optionally) the platform/architecture.", Debug.CRITICAL);
				Debug.println("Please remove the ':' character from line "+linenum, Debug.CRITICAL);
				System.exit(1);
			}
			String[] words = rses.util.Util.getWords(line.trim());
			String nodename = words[0];
			if(nodename == null || nodename.length() == 0)
			{
				Debug.println("Your machinefile seems to be in an invalid format.", Debug.CRITICAL);
				Debug.println("Each line must be the name of a computer and (optionally) the platform/architecture.", Debug.CRITICAL);
				System.exit(1);
			}
			String arch = "unknown";
			if(words.length > 1)
				arch = words[1];
			Debug.println("Adding "+nodename+" ("+arch+") to list of available nodes", Debug.IMPORTANT);
			result.add(new NodeInfo(nodename, arch));
			line = rdr.readLine(); 
		}
		rdr.close();
		
		NodeInfo[] toreturn = new NodeInfo[result.size()];
		for(int i = 0; i < toreturn.length; i++)
			toreturn[i] = (NodeInfo) result.get(i);
		return toreturn;
	}

	
	
	/** Get a Set of all the extensions that have been loaded.
	 *  Call runExtension(setelement) to run that extension. 
	 * 
	 */
	public java.util.Set getExtensions()
	{
		return extensionMappings.keySet();
	}
	
	
	/** Run the extension with name 'extname'  
	 * 
	 * @return
	 */
	public void runExtension(String extName)
	{
		String name = extName;
		String jarfilename = (String) extensionMappings.get(name);
		try {
			URL url = new File(jarfilename).toURL();
			URLClassLoader loader = new URLClassLoader(new java.net.URL[] {url});
			Class c = loader.loadClass(name);
			try { c.getConstructor(new Class[] {}); }
			catch(NoSuchMethodException nsme) {
				throw new RuntimeException("Extension main class does not have a no-argument constructor");
			}
			if(Thread.class.isAssignableFrom(c))
				((Thread) c.newInstance()).start();
			else if(Runnable.class.isAssignableFrom(c))
				new Thread((Runnable) c.newInstance()).start();
			else
				throw new RuntimeException("Main class in extension "+name+" does not implement run() method");
		}
		catch(ClassNotFoundException cnfe) {
			throw new RuntimeException("Class "+name+" could not be found");
		}
		catch(InstantiationException ie) {
			throw new RuntimeException("Class "+name+" found but could not be instantiated");
		}
		catch(IllegalAccessException iae) {
			throw new RuntimeException("Class "+name+" could not be accessed.... security settings prevent it");
		}
		catch(java.io.IOException ioe) {
			throw new RuntimeException("Couldnt start "+jarfilename);
		}
	}
	
	
	//a map of names to jarfiles
	private java.util.Map extensionMappings = new java.util.HashMap();
	
	
	/* Look in the ext directory of the toolkit and see if there are
	 * any extensions there. If there are, we load them.
	 * 
	 */
	private void loadExtensions() throws java.io.IOException
	{
		File extdir = new File(this.tkpath, "ext");
		if(!extdir.exists()) {
			Debug.println("No extensions directory, skipping search for extensions", Debug.INFO);
			return;
		}
		File[] jarfiles = extdir.listFiles();
		for(int i =0; i < jarfiles.length; i++)
		{
			if(jarfiles[i].getName().toLowerCase().endsWith(".jar"))
			{
				JarFile jfile = new JarFile(jarfiles[i]);
				if(jfile.getManifest() == null)
				{
					Debug.println("Archive "+jfile.getName()+" does not contain a valid manifest", Debug.IMPORTANT);
					continue;
				}
				String clazz = jfile.getManifest().getMainAttributes().getValue(java.util.jar.Attributes.Name.MAIN_CLASS);
				if(clazz == null) {
					Debug.println("No main class specified in "+jfile.getName(), Debug.IMPORTANT);
					continue;
				}
				Debug.println("Loading extension "+clazz+" from "+jarfiles[i].getAbsolutePath(), Debug.IMPORTANT);
				extensionMappings.put(clazz, jarfiles[i].getAbsolutePath());
				
				//make sure extension extends Thread or implements Runnable,
				//and has a no-argument constructor
				URL url = jarfiles[i].toURL();
				URLClassLoader loader = new URLClassLoader(new java.net.URL[] {url});
				Class c = null;
				try {
					c = loader.loadClass(clazz);
					c.getConstructor(new Class[] {}); 
				}
				catch(ClassNotFoundException cnfe) {
					throw new RuntimeException("Class "+clazz+" could not be found");
				}
				catch(NoSuchMethodException nsme) {
					Debug.println("Extension main class does not have a no-argument constructor", Debug.IMPORTANT);
					continue;
				}
				if(!Thread.class.isAssignableFrom(c) && !Runnable.class.isAssignableFrom(c))
				{
					Debug.println("Main class in extension "+clazz+" does not implement run() method", Debug.IMPORTANT);
					continue;
				}
				
			}
		}
	}
	
	
	

}



