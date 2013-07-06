import java.io.*;



/** Install class for both client and server part of the CADI toolkit.
 *  Asks the user for information about the system (for example,
 *  what C compiler to use, what linker, etc.)
 * 
 * @author peterr
 */
public class CadiInstaller
{
	public static void main(String[] args) throws Exception
	{
		CadiInstaller cinst = new CadiInstaller();
		BufferedReader rdr = new BufferedReader(new InputStreamReader(System.in));

		/* C compiler/linker stuff */
		String CC = "cc";
		String CCCOMPILEONLYFLAG = "-c ";
		String CCSHAREDOBJECTFLAG = "-shared ";
		String CCINCLUDEFLAG = "-I";
		String CCLIBFLAG = "-L";
		String CCOUTFLAG = "-o ";
		
		System.out.println("What C compiler should I use?");
		System.out.println("[default:"+CC+"]. type NONE for java only");
		System.out.print("CC ["+CC+"]?: ");
		String cctmp = rdr.readLine();
		if(cctmp.trim().length() > 0)
			CC = cctmp;

		if(!CC.equalsIgnoreCase("none"))
		{

			System.out.print("How do I tell "+CC+" to produce object files ["+CCCOMPILEONLYFLAG+"] ?: ");
			cctmp = rdr.readLine();
			if(cctmp.trim().length() > 0)
				CCCOMPILEONLYFLAG = cctmp;		

			System.out.print("How do I tell "+CC+" to produce a shared object ["+CCSHAREDOBJECTFLAG+"] ?: ");
			cctmp = rdr.readLine();
			if(cctmp.trim().length() > 0)
				CCSHAREDOBJECTFLAG = cctmp;		

			System.out.print("For "+CC+", how do I specify include file directories ["+CCINCLUDEFLAG+"] ?: ");
			cctmp = rdr.readLine();
			if(cctmp.trim().length() > 0)
				CCINCLUDEFLAG = cctmp;

			System.out.print("For "+CC+", how do I specify library directories ["+CCLIBFLAG+"] ?: ");
			cctmp = rdr.readLine();
			if(cctmp.trim().length() > 0)
				CCLIBFLAG = cctmp;
		
			System.out.print("For "+CC+" how do I specify the output file to produce ["+CCOUTFLAG+"] ?: ");
			cctmp = rdr.readLine();
			if(cctmp.trim().length() > 0)
				CCOUTFLAG = cctmp;
		}

		
		/* java stuff */
		String JAVADIR = "/usr/java/j2sdk";		
		System.out.print("Where is the java sdk installed ["+JAVADIR+"] ?: ");
		String jtmp = rdr.readLine().trim();
		if(jtmp.length() > 0)
			JAVADIR = jtmp;
		if(!JAVADIR.endsWith(File.separator))
			JAVADIR += File.separator;
		File javadir = new File(JAVADIR);
		if(!javadir.exists() || !javadir.isDirectory())
		{
			System.out.println("Directory "+JAVADIR+" does not exist!");
			System.exit(1);
		}
		

		/* repository stuff */
		String[] REPURLs = new String[] {
				"http://galitsin.anu.edu.au/caditk/softwarerepository/current/",
				"http://rses.anu.edu.au/~peterr/cadi/caditk/softwarerepository/current/"
		};
		
		boolean autoupdateanswered = false;
		while(!autoupdateanswered)
		{
			autoupdateanswered = true;
			System.out.print("Do you want to allow automatic updates to this software? [NO] ");
			String autoupdate = rdr.readLine().trim();
			if(autoupdate.equalsIgnoreCase("NO") || autoupdate.equalsIgnoreCase("N") || autoupdate.length() == 0)
				REPURLs = new String[0];	
			else if(autoupdate.equalsIgnoreCase("YES") || autoupdate.equalsIgnoreCase("Y")) 
			{
				System.out.println("");
				System.out.println("*** CADI Toolkit repository location ***");
				System.out.println("You will now be asked to select where the CADI toolkit program");
				System.out.println("should be loaded from. You need to type a URL -- probably the");
				System.out.println("same URL that you downloaded this software from");
				System.out.println("");
				System.out.println("Enter CADI software URL ["+REPURLs[0]+"]");
				System.out.print(": ");
				String rtmp = rdr.readLine();
				if(!(rtmp.trim().length() == 0))
					REPURLs[0] = rtmp;
				if(!REPURLs[0].endsWith("/") && !REPURLs[0].toLowerCase().endsWith(".jar"))
					REPURLs[0] += "/";			
			}
			else {
				autoupdateanswered = false;
			}
		}
				
		PrintStream ps = new PrintStream(new FileOutputStream(new File("repositoryURLs.txt")));
		for(int i =0; i < REPURLs.length; i++)
			ps.println(REPURLs[i]);
		ps.println(new File("caditk.jar").toURL());
		ps.close();
		
		
		//first make sure we have a lib directory
		if(!(new File("lib").exists()))
		{
			System.out.println("No lib directory!!!!");
			System.out.println("Are you running this install script from the right directory?");
			System.out.println("creating a lib directory....");
			new File("lib").mkdir();
		}

		//check include directorys
		if(!CC.equalsIgnoreCase("none"))
		{
			String INCLUDE = CCINCLUDEFLAG+". "+CCINCLUDEFLAG+JAVADIR+"include "+CCINCLUDEFLAG+JAVADIR+"Headers ";
			
			File incdir1 = new File(JAVADIR,"include");
			File incdir2 = new File(JAVADIR,"Headers");
			if(!incdir1.exists() && !incdir2.exists()) 
			{
				System.out.println("Cannot find Java include files.... ");
				System.out.println("");
				System.out.println("I have tried looking in the following locations:");
				System.out.println("\t"+incdir1.getAbsolutePath()+" , "+incdir2.getAbsolutePath());
				System.out.println();
				System.out.println("This error is probably caused by an incorrectly specified");
				System.out.println("j2sdk install directory.... If this is the case, you need to");
				System.out.println("re-run this install program and give the correct path to ");
				System.out.println("the Java sdk.");
				System.out.println();
				if(new File(new File(JAVADIR).getParentFile(), "include").exists() ||
				   new File(new File(JAVADIR).getParentFile(), "Headers").exists())
				{
					File properdir = new File(new File(JAVADIR).getParentFile(), "include");
					if(!properdir.exists())
						properdir = new File(new File(JAVADIR).getParentFile(), "Headers");
					if(!properdir.exists())
						throw new IllegalStateException("Impossible case reached in Installer... aborting..... please notify software vendor");
					 
					System.out.println("**************** HINT *********************");
					System.out.println("*** It seems like the correct directory is in fact "+properdir.getAbsolutePath());
					System.out.println("*** and not "+JAVADIR+", as you specified earlier in the install");
					System.out.println("**************** END HINT *********************");
				}
				System.exit(1);
			}
			else
			{
				File[] files = null;
				if(incdir1.exists())
				{
					files = incdir1.listFiles();
				
					for(int i =0; i < files.length; i++) {
						if(files[i].isDirectory()) {
							String path = files[i].getAbsolutePath();
							INCLUDE += CCINCLUDEFLAG+path+" ";
						}
					}
				}
				if(incdir2.exists())
				{
					files = incdir2.listFiles();
					for(int i =0; i < files.length; i++) {
						if(files[i].isDirectory()) {
							String path = files[i].getAbsolutePath();
							INCLUDE += CCINCLUDEFLAG+path+" ";
						}
					}
				}
			}


			if(new File("LegacyUserFunctionHandleImp.c").exists())
			{
				System.out.println("....");
				System.out.println("compiling C stub for interfacing with fortran/c");
				String cmdstr = CC + " "+INCLUDE+" "+CCCOMPILEONLYFLAG+" LegacyUserFunctionHandleImp.c";
				System.out.println(cmdstr);
				Process cc = Runtime.getRuntime().exec(cmdstr);
				cinst.new StreamGobbler(cc.getErrorStream()).start();
				cinst.new StreamGobbler(cc.getInputStream()).start();
				int retval = cc.waitFor();
				if(retval != 0)
					System.exit(retval);
				System.out.println("....");
				System.out.println("moving object files to lib directory");
				CopyableFile cf = cinst.new CopyableFile("LegacyUserFunctionHandleImp.o");
				cf.copyTo(new File("lib", "LegacyUserFunctionHandleImp.o"));
				cf.delete();
				System.out.println("....");
			}
			else
			{
				System.err.println("could not find C stub file..... aborting");
				System.exit(1);
			}
		}
		

		
		
		//create a .caditk file to tag the directory
		File newf = new File(".caditk");
		newf.delete();
		if(!newf.createNewFile())
		{
			System.err.println("Could not create .caditk file in this directory, aborting...");
			System.exit(2);
		}

		//write in the name of the C compiler that we can use
		PrintWriter pwriter = new PrintWriter((new FileOutputStream(newf)));
		pwriter.println("cc = "+CC);
		pwriter.println("ld = "+CC);
		pwriter.println("cc_compile = "+CCCOMPILEONLYFLAG);
		pwriter.println("cc_include = "+CCINCLUDEFLAG);
		pwriter.println("cc_libdirs = "+CCLIBFLAG);
		pwriter.println("cc_out = "+CCOUTFLAG);
		pwriter.println("ld_shared = "+CCSHAREDOBJECTFLAG);
		pwriter.println("javadir = "+JAVADIR);
		pwriter.close();
		
		System.out.println("Final Note:");
		System.out.println();
		System.out.println("If you want anyone other than you to be able to use this program, you must make the 'lib' directory writable by those users");
		System.out.println();
		
		
		System.exit(0);
		
	}
	
	
	
	class StreamGobbler extends Thread
	{
		InputStream is;
		OutputStream os;
	    
		StreamGobbler(InputStream is)
		{
			this(is, null);
		}

		StreamGobbler(InputStream is, OutputStream redirect)
		{
			this.is = is;
			this.os = redirect;
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
						pw.println(line);
					System.out.println(line);    
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



	class CopyableFile extends File {

	    public CopyableFile(String pathname) {
	        super(pathname);
	    }

	    public CopyableFile(java.net.URI uri) {
	        super(uri);
	    }

	    public CopyableFile(File parent, String child) {
	        super(parent, child);
	    }

	    public CopyableFile(String parent, String child) {
	        super(parent, child);
	    }

	    public void copyTo(File destination) throws IOException {
	        BufferedInputStream bIS = null;
	        BufferedOutputStream bOS = null;

	        try {
	            bIS = new BufferedInputStream(new FileInputStream(this));
	            bOS = new BufferedOutputStream(new FileOutputStream(destination));
	            byte[] buffer = new byte[2048];
	            int bytesRead;
	            while ((bytesRead = bIS.read(buffer, 0, buffer.length)) != -1) {
	                bOS.write(buffer, 0, bytesRead);
	            }
	            bOS.flush();
	        } finally {
	            if (bIS != null) {
	                try {
	                    bIS.close();
	                    bIS = null;
	                } catch(IOException e) {
	                }
	            }
	            if (bOS != null) {
	                try {
	                    bOS.close();
	                    bOS = null;
	                } catch (IOException e) {
	                }
	            }
	        }
	    }
	}
}


