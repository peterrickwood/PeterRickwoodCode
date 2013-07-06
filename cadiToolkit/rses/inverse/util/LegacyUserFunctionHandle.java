package rses.inverse.util;



import java.io.*;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import rses.Debug;
import rses.PlatformInfo;
import rses.util.FileUtil;
import rses.util.Util;

/** An implementation that glues together a user's
 *  fortran or C code so that it can be used by
 *  this java program.
 *
 *  <p>
 *  You can use this class to obtain a 'free' implementation
 *  of UserFunctionHandle if you have existing c/fortran/c++
 *  code that implements the following methods
 *  <ul>
 *  <li> initialize(char* basedir, int* len) </li>
 *  <li> user_init(int* numDimensions, float[][] bounds, float[] scale) </li>
 *  <li> forward(int* numDimensions, float[] model, float* misfit) </li>
 *  </ul>
 *
 *  <p>
 *  The user (native) code must be compiled into an object file
 *  
 *  <p>
 *  This object file must have
 *  entry points <code>user_init_</code>,
 *  <code>ctk_initialize_</code>
 *  <code>forward_</code>,  
 *  Note the trailing underscore's
 *  -- this is because most fortran compilers will give
 *  a subroutine called <code>user_init</code> an entry point called
 *  <code>user_init_</code>. Most C compilers don't do this,
 *  so if you have a C implementation, you probably need to
 *  call your functions <code>user_init_</code> and
 *  <code>forward_</code>, but this is all just rule-of-thumb,
 *  and you should consult your compiler's documentation if
 *  you are not sure.
 *
 *  <p>
 *  The object file can be either a shared (dynamically linked)
 *  library, or a static library. It <i>cannot</i> be just an
 *  archive of object files (created with the UNIX 'ar' utility).
 *  You need to do something like
 *
 *  <blockquote>
 *  Example:
 *
 *  ld -shared *.o -o nativeUser.obj (FOR A SHARED LIBRARY)
 *  </blockquote>
 *
 *  <p>
 *
 *  You can then instantiate an instance of this class by
 *  doing
 *  <code> lufh = new LegacyUserFunctioHandler(PATH_TO_USER_DIRECTORY, PATH_TO_RSES_BASE) </code>.
 *  Where PATH_TO_USER_DIRECTORY is the full path to the directory that holds
 *  the user library and any required input/data files for the user code, and
 *  PATH_TO_RSES_BASE is the full path to the RSES toolkit.
 *
 *  @author Peter Rickwood
 *
 */




public class LegacyUserFunctionHandle implements LegacyFunctionHandle
{
	private static final int MAX_DIMENSIONS = 8192;
	
	private int numDimensions;
	private double[][] bounds;

	private String[] paramNames;

	private String objectfilepath;
	private byte[] directoryData;

	private transient File userlibf = null;
	private transient File gluelibf = null;



	

	public File getUserLib()
	{	return userlibf; }
	
	
	public File getGlueLib()
	{	return gluelibf; }


	public String getOriginalObjectFilePath()
	{
		return this.objectfilepath;
	}

	public byte[] getData()
	{
		return this.directoryData;
	}

	

		
	public LegacyUserFunctionHandle(File userObjectFile, String rsesToolkitBase, boolean zip)
	throws Exception
	{
		File dir = userObjectFile.getParentFile();
		
		if(zip)
		{
			//zip up the directory and write it to a byte array
			Debug.println("zipping up compiled binary and other files for possible relocation", Debug.IMPORTANT);
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			ZipOutputStream zout = new ZipOutputStream(bout);
			FileUtil.zipDir(dir, zout);
			zout.flush();
			zout.close();
		
			//now get the underlying byte array for the zipped directory and for the binary
			this.directoryData = bout.toByteArray();
			bout = null;
		}
		
		this.objectfilepath = userObjectFile.getAbsolutePath();
		
		//build and load the user library
		this.initializeLibrary(rsesToolkitBase);
		
		//call the users init() method, set up bounds array
		init();
		
		//set up parameter names
		this.paramNames = new String[this.getDimensionOfModelSpace()];
		for(int i =0; i < paramNames.length; i++)
			paramNames[i] = "parameter "+(i+1);
		printParamInfo();
	}
	
	
	
	private LegacyUserFunctionHandle()
	{}

	
	
	
    /** If user libraries and/or glue libraries have already been
     *   built, then you can create a Handle by just saying where they are.
     *   This allows us to get 'copies' of a legacy function handle,
     *   rather than having to rebuild libraries every time.
     *
     * @param userlib    The sucessfully built user library
     * @param gluelib     The sucessfully built glue library
     * @param origObjectFilePath The path to the original object file that
     *                       was used to build user and glue libraries
     * @throws Exception If anything goes wrong :-)
     */
    public LegacyUserFunctionHandle(File userlib, File gluelib, String origObjectFilePath)
    throws Exception
    {
    	this.userlibf = userlib;
    	this.gluelibf = gluelib;
    	this.objectfilepath = origObjectFilePath;

    	//load the already built userlib
    	System.load(userlib.getAbsolutePath());
    	Debug.println("new client handle loaded user library OK in cloned handle", Debug.INFO);

    	//load the glue code that allows us to get to the users native code
        System.load(gluelib.getAbsolutePath());
        Debug.println("new client handle loaded glue code OK in cloned handle", Debug.INFO);
   	
    	init();

    	this.paramNames = new String[this.getDimensionOfModelSpace()];
    	for(int i =0; i < paramNames.length; i++)
    		paramNames[i] = "parameter "+(i+1);
    }

	
	
	

    
    
    
    
    
    
    
    
	
	public static LegacyUserFunctionHandle recreate(byte[] dirdata, String objectname, String rsesToolkitBase)
	throws Exception
	{
		//write user directory out to file
		File tmpdir = File.createTempFile("cadi_", ".tmp");
		tmpdir.delete();
		tmpdir.mkdir();
		FileUtil.addTmpFile(tmpdir);
		FileUtil.unzip(new ZipInputStream(new ByteArrayInputStream(dirdata)), tmpdir);
	
		File[] files = tmpdir.listFiles();
		if(files.length != 1)
			throw new RuntimeException("Cannot find user directory under "+tmpdir.getAbsolutePath());
		
		tmpdir = new File(tmpdir, files[0].getName());

		LegacyUserFunctionHandle result = new LegacyUserFunctionHandle();
		result.objectfilepath = new File(tmpdir, objectname).getAbsolutePath();		

		//build and load the user library
		result.initializeLibrary(rsesToolkitBase);
		
		//call the users init() method, set up bounds array
		result.init();
		
		//set up parameter names
		result.paramNames = new String[result.getDimensionOfModelSpace()];
		for(int i =0; i < result.paramNames.length; i++)
			result.paramNames[i] = "parameter "+(i+1);
		result.printParamInfo();
		
		return result;
	}

		
	
	
	







	/* This method creates the user libraries and loads them
	 */
	private void initializeLibrary(String rsesToolkitBaseDir)
	throws IOException, InterruptedException
	{
		Debug.println("", Debug.INFO);
		Debug.println("***WARNING***", Debug.INFO);
		Debug.println("**", Debug.INFO);
		Debug.println("**Interfacing with legacy code....", Debug.INFO);
		Debug.println("**Arbitrarily placing upper bound of "+MAX_DIMENSIONS+" on parameter space", Debug.INFO);
		Debug.println("**", Debug.INFO);
		Debug.println("***END WARNING***", Debug.INFO);
		Debug.println("", Debug.INFO);

		int libraryID = (int) (Math.random()*999999);

		//this is where we need to store our linked library
		String libdir = new File(rsesToolkitBaseDir,"lib").getAbsolutePath();

		//this is the library we are going to make 'on-the-fly'.
		//It is just a copy of the users library, really.
		String tmplibname = new File(libdir, "libnativeuser"+libraryID+".so").getAbsolutePath();

		//make sure that the 'on-the-fly' library
		//is unique (i.e. doesnt already exist)
		while(new File(tmplibname).exists())
		{
			Debug.println("conflicting library already exists...trying again", Debug.INFO);
			libraryID = (int) (Math.random()*999999);
			tmplibname = new File(libdir,"libnativeuser"+libraryID+".so").getAbsolutePath();
		}

		//String buildcmd = ldcmd+" -L"+libdir+" -rpath "+libdir+" -shared "+this.objectfilepath+" -o "+tmplibname;
		String buildcmd = PlatformInfo.getLinker()+" "+PlatformInfo.getCCLibdirFlag()+libdir+" "+
		PlatformInfo.getLDSharedFlag()+this.objectfilepath+" "+PlatformInfo.getCCOutputFlag()+tmplibname;
		Debug.println("build command is: "+buildcmd, Debug.IMPORTANT);
		Process p = Util.executeProcess(buildcmd);
		p.waitFor();
		if(p.exitValue() != 0)
			throw new RuntimeException("couldn't build user library!");
			 
		Debug.println("user library linked and copied to "+tmplibname, Debug.IMPORTANT);
		rses.util.FileUtil.addTmpFile(new File(tmplibname));

		//load the 'on-the-fly' user library we just made
		System.load(tmplibname);

		Debug.println("loaded user library OK", Debug.IMPORTANT);
		this.userlibf = new File(tmplibname);
		userlibf.deleteOnExit();

		//build the glue code for going back and forth between java and native
		File glueLib = new File(libdir,buildGlueLibrary(libdir, libraryID));
		rses.util.FileUtil.addTmpFile(glueLib);

		Debug.println("built glue code OK", Debug.INFO);
		Debug.println("glue code lives at "+glueLib.getAbsolutePath(), Debug.INFO);

		System.load(glueLib.getAbsolutePath());

		Debug.println("loaded glue code OK", Debug.IMPORTANT);
		this.gluelibf = glueLib;
		gluelibf.deleteOnExit();
		
	}
	







	private void init()
	{
		Debug.println("initializing LegacyUserFunctionHandle", Debug.INFO);
		
		float[][] ranges = new float[MAX_DIMENSIONS][2];
		float[] scales = new float[MAX_DIMENSIONS];


		/* call the native user code to get our ranges
		 * and scales */
		numDimensions = userinit(ranges, scales);
		
		bounds = new double[numDimensions][2];
		for(int i =0; i < numDimensions; i++)
		{
				bounds[i][0] = ranges[i][0];
				bounds[i][1] = ranges[i][1];
				if(bounds[i][0] > bounds[i][1])
					throw new RuntimeException("User-specified lower bound greater than upper bound");
		}
		
		/* Call the users initialize function to tell it where it lives
		 */
		String userdir = new File(this.objectfilepath).getParentFile().getAbsolutePath();
		char[] chars = new char[userdir.length()+1];
		userdir.getChars(0, userdir.length(), chars, 0);
		chars[chars.length-1] = '\0'; //NUL terminate it just in case someone relies on this
		initialize(chars, userdir.length());
	}











	private void printParamInfo()
	{
		Debug.println("", Debug.IMPORTANT);
		for(int i =0; i < this.getDimensionOfModelSpace(); i++)
		{
			Debug.println(paramNames[i]+" bounds [ "+bounds[i][0]+" , "+bounds[i][1]+" ]", Debug.IMPORTANT);
		}
		Debug.println("", Debug.IMPORTANT);
	}



	







	/* This method is called by the application to inform the
	 * users code the base directory in which it resides.
	 * So, if the users code lives in "/tmp/caditmp/blah",
	 * then this string is passed in as the argument to this
	 * function, which then calls the users initialize_ method
	 * 
	 */
	private static native void initialize(char[] path, int pathlen);

	

	/* Returns the number of dimensions, and
	 * initializes the provided arrays. 
	 *
	 * Jumps to glue code that calls the actual users code 
	 * and then massages the results back into a form that 
	 * Java can deal with.
	 */
	private static native int userinit(float[][] ranges, float[] scales);

	/* Calculate the error for 'model', given the
	 * number of dimensions
	 *
	 * Jumps to glue code that calls the actual users code 
	 * and then massages the results back into a form that 
	 * Java can deal with.
	 */
	private static native float forward(int numDimensions, float[] model);




	/* These cover the functionality of user_init 
	 *
	 * These methods are how java code gets hold of the
	 * values that the native user code in user_init 
	 * returns
	 */
	public int getDimensionOfModelSpace()
	{
		return numDimensions; 
	}

	public double[][] getBoundsOnModelSpace()
	{	
		return bounds; 
	}

	/* calculate the error of the specified model */
	public double getErrorForModel(double[] model)
	{
		float[] fmodel = new float[model.length];
		for(int i = 0; i < fmodel.length; i++)
			fmodel[i] = (float) model[i];
		
		/* jump to the JNI code, which calls
		 * the users native code, and gives us the
		 * result */
		float error = forward(model.length, fmodel);
		
		return (double) error;
	}


	
	
	/* calculate the prior for the model.
	 *
	 * For hostorical reasons, this has to be done in a bit of a 
	 * hacky way. Specifically, we call the forward_() function,
	 * but with a negative argument as the length of the array.
	 * This indicates to the users code that it should return
	 * the prior for the model, not the error.
	 * 
	 */
	public double getPriorForModel(double[] model)
	{
		float[] fmodel = new float[model.length];
		for(int i = 0; i < fmodel.length; i++)
			fmodel[i] = (float) model[i];
		
		/* jump to the JNI code, which calls
		 * the users native code, and gives us the
		 * result */
		float error = forward(-model.length, fmodel);
		
		return (double) error;
	}







	/* Take the user's object file, and link it
	 * with the glue object file to make a shared
	 * library that we can use to go between native
	 * and java code
	 */
	private String buildGlueLibrary(String libdir, int libID)
	{
		String gluelibname="lib"+libID+"userGlue.so";

		Debug.println("glue lib name is "+gluelibname, Debug.INFO);

		try {
			//build the glue library and stick it in the
			//rsesToolkit library directory.
			//When building it, we need to link with the
			//user's code.
			String legacyhandleimppath = new File(libdir, "LegacyUserFunctionHandleImp.o").getAbsolutePath();
			String gluelibpath = new File(libdir, gluelibname).getAbsolutePath();
			String ld_cmd = PlatformInfo.getLinker()+" "+PlatformInfo.getCCLibdirFlag()+libdir+" "+PlatformInfo.getLDSharedFlag();
			//String cmd = "ld -L"+libdir+" -rpath "+libdir+" -shared "+this.objectfilepath+" "+
			//legacyhandleimppath+" -o "+gluelibpath;
			String cmd = ld_cmd+" "+this.objectfilepath+" "+legacyhandleimppath+" "+
				PlatformInfo.getCCOutputFlag()+gluelibpath;
			Debug.println("Glue lib build command is "+cmd, Debug.INFO);
			Process p = Util.executeProcess(cmd);
			p.waitFor();
			if(p.exitValue() != 0)
				throw new RuntimeException("couldn't build glue library!");
		}
		catch(Exception e)
		{	throw new RuntimeException(e);}

		return gluelibname;
	}



	public String getParameterName(int pnum)
	{
		return paramNames[pnum];
	}



	public void setParameterName(int pnum, String name)
	{
		paramNames[pnum] = name;
	}

	/* for testing */
	public static void main(String[] args) throws Exception
	{
		Debug.setVerbosityLevel(Debug.MAX_VERBOSITY);
		
		if(!(args == null || args.length == 0))
		{
			System.out.println("You must invoke this program with no arguments");
			System.exit(1);
		}

		BufferedReader rdr = new BufferedReader(new InputStreamReader(System.in));
		System.out.println("Please enter the full path to user object file");
		System.out.print("user path? ");
		String userPath = rdr.readLine();

		System.out.println("Please enter the full path to the cadi server");
		System.out.print("toolkit path? ");
		String toolkitPath = rdr.readLine();

		System.out.println("loading LegacyUserFunctionHandle");
		LegacyUserFunctionHandle handle = new LegacyUserFunctionHandle(new File(userPath),toolkitPath, true);
		
		System.out.println("recreating LegacyUserFunctionHandle from zipped data stream");
		LegacyUserFunctionHandle h2 = LegacyUserFunctionHandle.recreate(handle.directoryData, new File(userPath).getName(), toolkitPath);
		
		System.out.println("FINAL TEST.... reloading recreated handle");
		LegacyUserFunctionHandle h3 = new LegacyUserFunctionHandle(h2.getUserLib(), h2.getGlueLib(), userPath);
		h3.printParamInfo();
	}	
}



