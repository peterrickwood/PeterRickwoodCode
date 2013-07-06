package rses;


import java.io.FileNotFoundException;

import rses.util.FileUtil;





public class PlatformInfo
{
	
	private PlatformInfo() {}
	
	
	public static final String nl = System.getProperty("line.separator"); 
	public static final String sep = System.getProperty("file.separator");

	
	public  synchronized static String getCC()
	{
		String res = props.getProperty("cc");
		if(res == null) {				
			Debug.println("No C compiler defined... guessing cc is your C compiler", Debug.IMPORTANT);
			if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
				throw new RuntimeException("Forcing abort due to undefined value");
			res = "cc";
		}
		return res;
	}

	public  synchronized static String getLinker()
	{
		String res = props.getProperty("ld");
		if(res == null) {
			Debug.println("No linker defined... guessing "+getCC()+" is your linker", Debug.IMPORTANT);
			if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
				throw new RuntimeException("Forcing abort due to undefined value");
			res = getCC();
		}
		return res;
	}


	public  synchronized static String getCCLibdirFlag()
	{
		String res = props.getProperty("cc_libdirs");
		if(res == null) {
			Debug.println("No library search flag defined... guessing -L", Debug.IMPORTANT);
			if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
				throw new RuntimeException("Forcing abort due to undefined value");
			res = "-L";
		}
		return res;
	}

	public  synchronized static String getCCCompileFlag()
	{
		String res = props.getProperty("cc_compile");
		if(res == null) {
			Debug.println("No compile flag defined... guessing -c", Debug.IMPORTANT);
			if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
				throw new RuntimeException("Forcing abort due to undefined value");
			res = "-c ";
		}
		return res;
	}

	public  synchronized static String getCCIncludeFlag()
	{
		String res = props.getProperty("cc_include");
		if(res == null) {
			Debug.println("No include flag defined... guessing -I", Debug.IMPORTANT);
			if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
				throw new RuntimeException("Forcing abort due to undefined value");
			res = "-I";
		}
		return res;
	}

	public  synchronized static String getCCOutputFlag()
	{
		String res = props.getProperty("cc_out");
		if(res == null) {
			Debug.println("No output flag defined... guessing -o", Debug.IMPORTANT);
			if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
				throw new RuntimeException("Forcing abort due to undefined value");
			res = "-o ";
		}
		return res;
	}

	public  synchronized static String getLDSharedFlag()
	{
		String res = props.getProperty("ld_shared");
		if(res == null) {
			Debug.println("No linker shared library flag defined... guessing -shared", Debug.IMPORTANT);
			if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
				throw new RuntimeException("Forcing abort due to undefined value");
			res = "-shared ";
		}
		return res;
	}

	public  synchronized static String getJavaDir()
	{
		String res = props.getProperty("javadir");
		if(res == null) {
			Debug.println("No Java directory defined... guessing /usr/java/j2sdk", Debug.IMPORTANT);
			if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
				throw new RuntimeException("Forcing abort due to undefined value");
			res = "/usr/java/j2sdk";
		}
		return res;
	}


	private static java.util.Properties props = null;

	public static synchronized void readPlatformInfo(java.io.File ctkf)
	throws java.io.IOException
	{
		readPlatformInfo(ctkf, true);
	}

	public static synchronized void readPlatformInfo()
	throws java.io.IOException
	{
		readPlatformInfo(null, false);
	}

	
	public static synchronized void readPlatformInfo(java.io.File ctkf, boolean expectcaditkfile)
	throws java.io.IOException
	{ 
		if(ctkf == null || !ctkf.exists()) 
		{
			if(expectcaditkfile) 
			{
				String ctkfpath= ctkf == null ? ""+null : ctkf.getAbsolutePath(); 
				Debug.println("No .caditk file at "+ctkfpath+".... have you run the installation program??", Debug.CRITICAL);
				if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
					throw new FileNotFoundException(ctkfpath);
			}
			//we go with defaults and hope we are OK. We just initialize
			//an empty set of properties
			props = new java.util.Properties();
			return;
		}
	
		try { 
			java.util.Properties p = FileUtil.getPropertiesFromFile(ctkf);
			props = p;
		}
		catch(java.io.IOException ioe) {
			Debug.println("Error reading properties file "+ctkf.getAbsolutePath(), Debug.CRITICAL);
			System.exit(-1);
		}
	}
	
}