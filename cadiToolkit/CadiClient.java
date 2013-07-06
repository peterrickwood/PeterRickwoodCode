import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.io.*;





/** This is just a class to load the Cadi Toolkit
 *  classes over the network. It is just smart enough
 *  to start up, install a URLClassLoader, and then
 *  kick off toolkitMain
 * 
 * @author peterr
 */
public class CadiClient
{
		
	/** args are:
	 * 
	 *  args[0...n] = arguments to toolkitMain constructor
	 * 
	 * @param args
	 */
	public static void main(String[] args) throws Exception
	{
		//look for a file called repositoryURLs
		URL[] urls = null;
		if(args.length > 0)
			urls = getRepositoryURLs(args[0]);
		//if user does not specify any arguments
		//we ask toolkitMain to print a help message
		if(urls == null) {
			System.err.println("Incorrect usage....");
			System.err.println();
			System.err.println("You must specify, as the first argument, the full path to where the cadiclient software is installed");
			System.err.println("");
			System.err.println("e.g. java CadiClient /path/to/cadiclient/directory");
			System.err.println("");
			System.err.println("");
			System.err.println("For full help, you can type");
			System.err.println("\tjava CadiClient /path/to/cadiclient --help");
			System.err.println();
			System.exit(1);
		}
		
		//create a new ClassLoader
		URLClassLoader urlloader = new URLClassLoader(urls);
		Class tkmainclass = urlloader.loadClass("rses.toolkitMain");
		Constructor[] constructors = tkmainclass.getConstructors();
		Constructor constructor = tkmainclass.getConstructor(new Class[] {(new String[1]).getClass()});
		
		try {
		Object tkmain = constructor.newInstance(new Object[] {args});
		}
		catch(java.lang.reflect.InvocationTargetException ite) {
			//print out underlying exception message
			Throwable t = ite.getCause();
			if(t != null)
				System.out.println(t.getMessage());
			else
				throw ite;
		}
	}

	/* Look for the file that specifies where to fetch the class files
	 * from. This can be either somewhere on the local filesystem, or
	 * a URL (i.e. http://etc), if the code is to be downloaded over the
	 * internet. 
	 * 
	 */
	private static URL[] getRepositoryURLs(String tkdir)
	throws IOException
	{
		if(!new File(tkdir).exists()) {
			System.err.println("'"+tkdir+"' is not a valid path.....");
			return null;
		}
			
		File f = new File(tkdir, "repositoryURLs.txt");
		java.util.ArrayList urls = new java.util.ArrayList();
		if(f.exists()) {
			BufferedReader rdr = new BufferedReader(new FileReader(f));
			String line = rdr.readLine();
			while(line != null && line.trim().length() > 0) {
				System.out.println("adding repository "+line);
				urls.add(new URL(line));
				line = rdr.readLine();
			}
		}
		else if(new File(tkdir, "caditk.jar").exists())
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
	
}
