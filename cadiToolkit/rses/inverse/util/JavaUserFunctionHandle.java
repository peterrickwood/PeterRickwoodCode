package rses.inverse.util;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Map;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import rses.Debug;
import rses.inverse.UserFunctionHandle;
import rses.util.FileUtil;




/** A user functionhandle that is built from a java class
 *  (or java classes)
 * 
 *
 * @author peter rickwood
 *
 */
public class JavaUserFunctionHandle implements UserFunctionHandle, Serializable
{
	private String[] paramNames;
	private String implementationClassname;
	private String origClassPath;
	private byte[] data;
	
	/** These must be declared transient because 
	 *   we cannot serialize them.
	 *   Instead, we have to be able to recreate them as
	 *   needed after being serialized/deserialized
	 */
	private transient Method getBoundsMethod = null;
	private transient Method getDimensionMethod = null;
	private transient Method getErrorMethod = null;
	private transient Method getPriorMethod = null; //optional
	private transient Object implementorObject = null;
	

	
	static Map revivedclazzes = Collections.synchronizedMap(new java.util.HashMap());
	static Map cachedObjects = Collections.synchronizedMap(new java.util.HashMap());
	
	
	/** Create a JavaUserFunctionHandle from a directory of java class files
	 *  (with the base class being 'clazzname'. If zip is true, all these
	 *  files are zipped up and written into a byte array for serializing.
	 *  If zip is not true, then the object cannot later be serialized.
	 * 
	 * @param dir
	 * @param clazzname
	 * @param zip
	 * @throws IOException
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchMethodException
	 * @throws java.lang.reflect.InvocationTargetException
	 */
	public JavaUserFunctionHandle(File dir, String clazzname, boolean zip)
	throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException,
	NoSuchMethodException, java.lang.reflect.InvocationTargetException
	{
		//zip up the directory and write it to a byte array
		if(zip)
		{
			Debug.println("zipping up class and other files in "+dir.getAbsolutePath()+" for relocation", Debug.INFO);
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			ZipOutputStream zout = new ZipOutputStream(bout);
			FileUtil.zipDir(dir, zout);
			zout.flush();
			zout.close();
		
			//now get the underlying byte array
			this.data = bout.toByteArray();
			Debug.println("zipped files are "+data.length+" bytes", Debug.INFO);
			bout = null;
		}
		
		this.origClassPath = dir.getAbsolutePath(); 
		this.implementationClassname = clazzname;
		
		String basedir = dir.getAbsolutePath();
		revivedclazzes.put(clazzname, basedir);
		Class implclass = this.initImplementationClass(basedir);
		this.initMethods(basedir, implclass);
		if(Debug.equalOrMoreVerbose(Debug.INFO) || Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
		{
			int dim = this.getDimensionOfModelSpace();
			double[][] bounds = this.getBoundsOnModelSpace();
			for(int i =0; i < dim; i++)
				Debug.println(i+": ["+bounds[i][0]+"]  --->  ["+bounds[i][1]+"]", Debug.INFO);
		}
		
		paramNames = new String[this.getDimensionOfModelSpace()];
		for(int i=0; i < paramNames.length; i++)
			paramNames[i] = "parameter "+(i+1);
	}
	
	
	
	private JavaUserFunctionHandle(String clazzname, byte[] data)
	{
		this.implementationClassname = clazzname;
		this.data = data;
	}
	
	
	
	public static JavaUserFunctionHandle recreate(byte[] data, String implementationClassName)
	throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException,
	NoSuchMethodException, java.lang.reflect.InvocationTargetException
	{
		JavaUserFunctionHandle result = new JavaUserFunctionHandle(implementationClassName, data);
		
		//if we havent copied the classfile data across, we do it now,
		//and remember where we put it
		if(revivedclazzes.get(implementationClassName) == null)
		{
			//unzip the data to a known location
			ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data));
			File tmpdir = File.createTempFile("cadi_", ".tmp");
			tmpdir.delete();
			tmpdir.mkdir();
			Debug.println("unzipping class and other file data and storing in "+tmpdir.getAbsolutePath(), Debug.INFO);
			rses.util.FileUtil.addTmpFile(tmpdir);
			rses.util.FileUtil.unzip(zin, tmpdir);
			zin.close();
			
			File[] userdirs = tmpdir.listFiles();
			if(userdirs.length != 1 || !userdirs[0].isDirectory())
				throw new RuntimeException("unzipped archive does not contain single user directory!!! Aborting");
			File userdir = userdirs[0];
			
			//remember where we put the revived class
			revivedclazzes.put(implementationClassName, userdir.getAbsolutePath());
	
			//now load the class and create a backing object of that type
			Class implclass = result.initImplementationClass(userdir.getAbsolutePath());
			Debug.println("initializing methods in recreated handle", Debug.INFO);
			result.initMethods(userdir.getAbsolutePath(), implclass);
			
			//remember the implementing object for future reference
			Debug.println("remembering cached implementer object so we dont have to load it again", Debug.INFO);
			cachedObjects.put(implementationClassName, result.implementorObject);
		}
		else //we already know about the class and should have created an implementer object already
		{
			Debug.println("We already have created an implementer object... reusing it....", Debug.INFO);
			Object implementer = cachedObjects.get(implementationClassName);
			result.implementorObject = implementer;
			String basedir = (String) revivedclazzes.get(implementationClassName);
			Debug.println("initializing methods for JavaUserFunctionHandle with base "+basedir, Debug.INFO);
			result.initMethods(basedir, implementer.getClass());
		}
			
		result.paramNames = new String[result.getDimensionOfModelSpace()];
		for(int i=0; i < result.paramNames.length; i++)
			result.paramNames[i] = "parameter "+(i+1);

		return result;
	}
	
	
	
	public byte[] getClassData()
	{
		return this.data;
	}
	
	
	public String getImplementationClassname()
	{
		return this.implementationClassname;
	}


	public String getOrigClasspath()
	{
		return this.origClassPath;
	}

	
	/** 
	 * 
	 * @param implementationBasedir Is the top level directory that contains the 
	 *                              directory that has all the users stuff (class files,
	 *                              data files, etc) in it
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	private Class initImplementationClass(String implementationBasedir)
	throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException
	{
		File dir = new File(implementationBasedir).getParentFile();
		File subdir = new File(implementationBasedir);
		java.net.URL dirurl = null;
		java.net.URL subdirurl = null; //we need this for accompanying classes
		try { 
			dirurl = dir.getAbsoluteFile().toURL();
			subdirurl = subdir.getAbsoluteFile().toURL();
		}
		catch(java.net.MalformedURLException mfue) {throw new RuntimeException(mfue);}		
		
		return new URLClassLoader(new URL[] {dirurl, subdirurl}, ClassLoader.getSystemClassLoader()).loadClass(this.implementationClassname);
	}
	

	/**
	 * 
	 * @param implementationBasedir Is the top level directory that contains the 
	 *                              directory that has all the users stuff (class files,
	 *                              data files, etc) in it
	 * @param implementerClass
	 * @throws NoSuchMethodException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws java.lang.reflect.InvocationTargetException
	 */
	private void initMethods(String implementationBasedir, Class implementerClass) 
	throws NoSuchMethodException, InstantiationException, IllegalAccessException, java.lang.reflect.InvocationTargetException
	{
		Constructor constructor = implementerClass.getConstructor(new Class[] {String.class});
		
		if(!implementationBasedir.endsWith(File.separator))
			implementationBasedir += File.separator;
		
		//create the object that implements the necessary methods
		this.implementorObject = constructor.newInstance(new Object[] {implementationBasedir});
		this.getBoundsMethod = implementerClass.getDeclaredMethod("getBoundsOnModelSpace", null);
		this.getDimensionMethod = implementerClass.getDeclaredMethod("getDimensionOfModelSpace", null);
		this.getErrorMethod = implementerClass.getDeclaredMethod("getErrorForModel", new Class[] { (new double[1]).getClass() });
		try { this.getPriorMethod = implementerClass.getDeclaredMethod("getPriorForModel", new Class[] { (new double[1]).getClass() }); }
		catch(Exception e) { /* this method is optional, so we just swallow any error */; }
	}
	
	
		
	
	
	public double[][] getBoundsOnModelSpace()
	{
		if(this.implementorObject == null)
			throw new IllegalStateException("Call to method when class is not initialized!!");
			
		double[][] res = null;
		try {
			res = (double[][]) this.getBoundsMethod.invoke(this.implementorObject, null);
			if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
			{
				for(int i=0; i < res.length; i++)
					if(res[i][0] > res[i][1])
						throw new RuntimeException("User-specified lower bound > upper bound");
			}
		}
		catch(Exception e) {
			if(!(e instanceof RuntimeException)) throw new RuntimeException(e);
			else throw (RuntimeException) e;
		}
		return res;
	}


	
	
	public int getDimensionOfModelSpace()
	{
		if(this.implementorObject == null)
			throw new IllegalStateException("Call to method when class is not initialized!!");

		int res;
		try {
			res = ((Integer) this.getDimensionMethod.invoke(this.implementorObject, null)).intValue();
		}
		catch(Exception e) {
			if(!(e instanceof RuntimeException)) throw new RuntimeException(e);
			else throw (RuntimeException) e;
		}
		return res;		
	}
	
	
	
	public double getErrorForModel(double[] params)
	{
		if(this.implementorObject == null)
			throw new IllegalStateException("Call to method when class is not initialized!!");

		double res;
		try {
			res = ((Double) this.getErrorMethod.invoke(this.implementorObject, new Object[] {params})).doubleValue();
		}
		catch(Exception e) {
			if(!(e instanceof RuntimeException)) throw new RuntimeException(e);
			else throw (RuntimeException) e;
		}
		return res;		
	}
	
	
	
	
	public double getPriorForModel(double[] params)
	{
		if(this.implementorObject == null)
			throw new IllegalStateException("Call to method when class is not initialized!!");
		else if(this.getPriorMethod == null)
			throw new UnsupportedOperationException();
		
		double res;
		try {
			res = ((Double) this.getPriorMethod.invoke(this.implementorObject, new Object[] {params})).doubleValue();
		}
		catch(Exception e) {
			if(!(e instanceof RuntimeException)) throw new RuntimeException(e);
			else throw (RuntimeException) e;
		}
		return res;				
	}
	
	
	
	
	public String getParameterName(int pnum)
	{
		return paramNames[pnum];
	}
	
	
	public void setParameterName(int pnum, String name)
	{
		paramNames[pnum] = name;
	}
	
}
	
	
	
	
