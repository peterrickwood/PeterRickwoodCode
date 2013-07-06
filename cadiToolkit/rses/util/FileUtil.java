package rses.util;


import javax.swing.*;
import javax.swing.filechooser.FileFilter;

import rses.inverse.UserFunctionHandle;
import rses.inverse.util.JavaUserFunctionHandle;
import rses.Debug;
import rses.PlatformInfo;


import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;





public final class FileUtil
{
	private static java.util.Vector tmpfiles = new java.util.Vector();
	
	
	
	
	public static java.util.Map<String, String> readKeyValueMappingsFromFile(String fname)
	throws java.io.IOException, java.io.FileNotFoundException
	{
		java.util.HashMap<String, String> keyvaluemap = new java.util.HashMap<String, String>();
		BufferedReader rdr = new BufferedReader(new FileReader(fname));
		
		String line = rdr.readLine();
		while(line != null) 
		{
			String[] words = Util.getWords(line);
			String key = words[0];
			String val = "";
			for(int i = 1; i < words.length-1; i++)
				val = val + words[i] + " ";
			val += words[words.length-1];
			keyvaluemap.put(key, val);
			
			line = rdr.readLine();
		}
		return keyvaluemap;
	}
	
	/** Get the next 8 booleans all packed into a single byte
	 *  (actually an int, but only the 8 least significant digits
	 *  have any non-zero values).
	 * 
	 * @param boolarray
	 * @param start
	 * @return
	 */
	public static int getPackedBooleans(boolean[] boolarray, int start)
	{
		int result = boolarray[start]?1:0;
		
		
		for(int i = start+1; i < start+8; i++)
		{
			result = result << 1;
			if(i < boolarray.length && boolarray[i]) 
				result++;
		}
		
		return result;
	}
	
	
	
	
	
	
	public static void addTmpFile(File f)
	{
		tmpfiles.add(f);
	}
	
	public static void deleteTmpFiles()
	{
		int ndirs = tmpfiles.size();
		for(int i =0; i < ndirs; i++)
		{
			File todelete = (File) tmpfiles.get(i);
			if(todelete.exists())
			{
				if(todelete.isDirectory())
				{
					try { deleteDirectory(todelete); }
					catch(IOException ioe) 
					{ Debug.println("Could not delete "+todelete.getAbsolutePath(), Debug.CRITICAL);}
				}
				else
					todelete.delete();				
			}
		}
	}
	
	/** Get a list of the currently registered temporary files that
	 *   still exist.
	 * 
	 * @return
	 */
	public static File[] getTmpFiles()
	{
		ArrayList existingfiles = new ArrayList(tmpfiles.size());
		int nfiles = tmpfiles.size();
		for(int i = 0; i < nfiles; i++)
		{
			File f = (File) tmpfiles.get(i);
			if(f.exists())
				existingfiles.add(f); 
		}
		nfiles = existingfiles.size();
		File[] res = new File[nfiles];
		for(int i =0; i < res.length; i++)
			res[i] = (File) existingfiles.get(i);
		
		return res;
	}
	
	
	private FileUtil() {} //cant be instantiated
	

	public static void deleteDirectory(File dir)
	throws IOException
	{
		if(!dir.exists() || !dir.isDirectory())
			throw new IllegalArgumentException("argument is not a directory!");
		
		
		File[] files = dir.listFiles();
		for(int i =0; i < files.length; i++)
		{
			if(files[i].isFile())
				files[i].delete();
			else
				deleteDirectory(files[i]);
		}
		
		dir.delete();
		
	}
	
	
	
	public static void copyDirectory(File origdir, File tocopyto)
		throws IOException
	{
		copyDirectory(origdir, tocopyto, false);
	}
	
	/** Has the same semantics as the UNIX 'cp' command, when used
	 *   on directories.
	 * 
	 * @param origdir
	 * @param tocopyto
	 * @param overwrite
	 * @throws IOException
	 */
	public static File copyDirectory(File origdir, File tocopyto, boolean overwrite)
	throws IOException
	{
		File[] files = origdir.listFiles();
		if(!tocopyto.exists())
			tocopyto.mkdir();
		else {
			tocopyto = new File(tocopyto, origdir.getName());
			if(!tocopyto.exists())
				tocopyto.mkdir();
		}
			 
		 	
		for(int i =0; i < files.length; i++)
		{
			if(files[i].isFile()) 
			{
				File newfile = new File(tocopyto.getAbsolutePath(), files[i].getName());
				if(newfile.exists())
				{
					if(overwrite) newfile.delete();
					else throw new RuntimeException("file "+newfile+" already exists! Aborting copy");
				}
				new CopyableFile(files[i].getAbsolutePath()).copyTo(newfile);
			}
			else
			{
				java.io.File newdir = new java.io.File(tocopyto.getAbsolutePath(), files[i].getName());
				Debug.println("copying directory "+files[i].getAbsolutePath()+" to "+newdir.getAbsolutePath(), Debug.EXTRA_INFO);
				copyDirectory(files[i], newdir);	
			}
		}
		
		return tocopyto;
	}
	



	public static File copyFile(File from, File to)
	throws IOException
	{
		File newfile = to;
		if(to.isDirectory())
			newfile = new File(to.getAbsolutePath(), from.getName());
				
		if(newfile.exists())
			throw new RuntimeException("file "+newfile+" already exists! Aborting copy");

		new CopyableFile(from.getAbsolutePath()).copyTo(newfile);
		return newfile;
	}
	
	

	
	/** Pop up a dialog and get the user to provide (select) a file that
	 *   can be turned into a UserFunctionHandle.
	 * 
	 *  If the user cancels, this method returns null
	 * 
	 * @param msg If non-null, a message box is popped up with this 
	 *                  message before the user is asked to select a file. You may
	 *                  with to provide some instructions to the user, for example.
	 * @param parent  The parent frame that the dialog 'belongs' to. null is ok.
	 * @param dir         The directory to start looking for files in.
	 * @param rsesToolkitPath      The path to the rses toolkit (required for
	 *                                building LegacyUserFunctionHandles.
	 * @param copyFiles Do we need to make a copy of the users files? If so we make sure
	 *                       that the user is not running in the same directory 
	 *                       (or a sub-directory) of the one where the users code lives. 
	 *                       If false the user is allowed to run in the codedir, and the code dir
	 *                       is not zipped.   
	 * @return null, if the user cancels the operation, or if excludecodedir is true and
	 *               the current directory is the same (or a subdirectory) as the one
	 *               in which the user selects their code file. Otherwise, returns a 
	 *                  UserFunctionHandle
	 * 
	 * @throws IOException An IO error occurred trying to find or read the file
	 * @throws FileNotFoundException The selected file was not found
	 * @throws ClassNotFoundException If the guessed class name is not the same as the one in the file
	 * @throws IllegalAccessException If the user has got the visibility wrong
	 * @throws InstantiationException User class is in fact an interface or is abstract
	 * @throws InvocationTargetException User class constructor throws an exception
	 * @throws NoSuchMethodException User class does not implement the required methods
	 * @throws Exception Anything else goes wrong :-)
	 */
	public static rses.inverse.UserFunctionHandle guiGetUserFunctionHandle(
	             String msg, java.awt.Component parent, File dir, String rsesToolkitPath,
				 boolean copyFiles)
	throws IOException, FileNotFoundException, ClassNotFoundException, IllegalAccessException,
	InstantiationException, InvocationTargetException, NoSuchMethodException, Exception
	{
		if(msg != null)
			JOptionPane.showMessageDialog(parent, msg);

		JFileChooser chooser = new JFileChooser(dir);
		javax.swing.filechooser.FileFilter filter = FileUtil.getFileFilterBySuffixes(
			new String[] {".class", ".o", ".obj", ".so", ".a", ".dll"});
		chooser.setFileFilter(filter);
		chooser.setSelectedFile(new File("nativeUser.obj"));

		JPanel accessoryPanel = new JPanel();
		javax.swing.JCheckBox loadNames = new javax.swing.JCheckBox("Load parameter names?");
		accessoryPanel.add(loadNames);
		chooser.setAccessory(accessoryPanel);

		int ret = chooser.showOpenDialog(parent);
		if(ret != JFileChooser.APPROVE_OPTION) 
			return null;
		File f = chooser.getSelectedFile();
		if(!f.exists()) 
			throw new FileNotFoundException("Could not find file "+f.getAbsolutePath());


		//we prevent users from running the toolkit from the
		//same directory as their code, because otherwise
		//we are likely to get a bunch of crap unnecessarily
		//added to the jar file
		String codedir = f.getParentFile().getCanonicalPath();
		String cwd = new File(System.getProperty("user.dir")).getCanonicalPath();
		if(copyFiles && cwd.startsWith(codedir)) 
		{
			JOptionPane.showMessageDialog(parent,
					"When running in distributed (client/server) mode, do not"+PlatformInfo.nl+
					"run the Toolkit in the same directory (or a subdirectory)"+PlatformInfo.nl+
					"as the one where your code is", "Warning", JOptionPane.WARNING_MESSAGE);
			return null;
		}
		
		UserFunctionHandle result = null;
		if(f.getName().endsWith(".class")) 
		{
			Debug.println("User chose class file "+f.getAbsolutePath(), Debug.INFO);
			String fname = f.getName();
			String clazzname = f.getParentFile().getName()+"."+fname.substring(0, fname.indexOf(".class"));
			Debug.println("Guessed class name is "+clazzname, Debug.INFO);

			result = new JavaUserFunctionHandle(f.getParentFile(), clazzname, copyFiles);
		}
		else if(f.getName().endsWith(".o") || f.getName().endsWith(".obj") 
			|| f.getName().endsWith(".so") || f.getName().endsWith(".a")
			|| f.getName().endsWith(".dll")) 
			result = new rses.inverse.util.LegacyUserFunctionHandle(f, rsesToolkitPath, copyFiles); //load native code
		else
			throw new IllegalStateException("Impossible case reached..... selected file has unknown extension");
		
		if(loadNames.isSelected()) 
		{
			java.io.File paramFile = rses.util.FileUtil.guiSelectFile(parent, "Select parameter name file");
			if(paramFile == null) return result;
			String[] pn = rses.util.FileUtil.guiReadParameterNames(parent, paramFile, result.getDimensionOfModelSpace());
			for(int i =0; i < pn.length; i++)
				result.setParameterName(i, pn[i]);
		}
		return result;
	}
	
	

	/** Pop up a dialog prompting the user to select a model file.
	 * 
	 * This is provided for backward compatibility only, and is exactly
	 * equivalent to 
	 * <code>guiSelectFile(parent, "Choose result file to view");</code>
	 * 
	 * @param parent The parent component that 'owns' the dialog.
	 * @return
	 */	
	public static File guiSelectModelFile(java.awt.Component parent)
	{
		return guiSelectFile(parent, "Choose result file to view");
	}
	

	/** Pop up a dialog prompting the user to select a file.
	 * 
	 * @param parent The parent compnent that 'owns' the dialog.
	 * @param dialogTitle The title of the dialog box.
	 * @return The File object selected, or null if the user cancelled.
	 */
	public static File guiSelectFile(java.awt.Component parent, String dialogTitle)
	{
		//choose the logfile or nadfile
		JFileChooser chooser = new JFileChooser(new java.io.File(System.getProperty("user.dir")));
		chooser.setDialogTitle(dialogTitle);
		int ret = chooser.showOpenDialog(parent);
		if (ret == JFileChooser.CANCEL_OPTION || ret == JFileChooser.ERROR_OPTION) 
			return null;
		File f = chooser.getSelectedFile();
		if(f == null) return null;
		if(!f.exists()) throw new RuntimeException("Could not find "+f.getAbsolutePath());
		return f;
	}


	/** Pop up a dialog prompting the user to select a file name to save some information
	 *   into. The file may already exist, or may be entered by the user (a new file)
	 * 
	 * @param parent The parent compnent that 'owns' the dialog.
	 * @param dialogTitle The title of the dialog box.
	 * @return The File object selected, or null if the user cancelled.
	 */	
	public static File guiSaveFile(java.awt.Component parent, String dialogTitle)
	{
		return guiSaveFile(parent, dialogTitle, null, null);
	}


	/**Pop up a dialog prompting the user to select a file name to save some information
	 *   into. The file may already exist, or may be entered by the user (a new file)
	 * 
	 * @param parent The parent compnent that 'owns' the dialog.
	 * @param dialogTitle The title of the dialog box. 
	 * @param filter the FileFilter to use, or null if no filter is to be used
	 * @param suggestedFileName The file name to suggest to the user as default, or null if no suggestion 
	 * @return The File object selected, or null if the user cancelled.
	 */
	public static File guiSaveFile(java.awt.Component parent, String dialogTitle,FileFilter filter,
		String suggestedFileName)
	{
		//choose the logfile or nadfile
		JFileChooser chooser = new JFileChooser(new java.io.File(System.getProperty("user.dir")));
		chooser.setDialogTitle(dialogTitle);
		
		if(suggestedFileName != null)
			chooser.setSelectedFile(new File(suggestedFileName));
		if(filter != null)
			chooser.setFileFilter(filter);

		int ret = chooser.showSaveDialog(parent);
		if (ret == JFileChooser.CANCEL_OPTION || ret == JFileChooser.ERROR_OPTION) 
			return null;
		File f = chooser.getSelectedFile();
		if(f == null) return null;
		return f;
	}



	public static FileFilter getFileFilterBySuffixes(String[] suffixes)
	{
		class FFIMP extends FileFilter  { 
			private String[] acceptable = null;
				
			FFIMP(String[] acceptable)
			{	this.acceptable = acceptable; }
			
			public boolean accept(File f)  {
				if(f.isDirectory()) return true;
				for(int i = 0; i < acceptable.length; i++)
					if(f.getName().endsWith(acceptable[i])) return true;
				return false;
			}
			public String getDescription() { 
				String desc = "";
				for(int i =0; i < acceptable.length; i++)
					desc = desc+"*"+acceptable[i]+"  ";
					return desc; 
			}
		};
				
		javax.swing.filechooser.FileFilter filter = new FFIMP(suffixes); 
		return filter;
	}
	
	
	
	
	
	
	/** See if the user has a file <code>paramFile</code> to use for parameter axes labels
	 * and, if so, use this file to give meaningful names to parameters.
	 * 
	 * @param parent The parent component
	 * @param paramfile The file that parameters are stored in
	 * @param maxdimensions maximum number of dimensions
	 * @return A Map that maps Integers (starting at 1) onto the string name for that parameter. 
	 *             There is always a complete set of such mappings.
	 */
	public static String[] guiReadParameterNames(java.awt.Component parent, File paramfile, int maxdimensions)
	{
		return guiReadParameterNames(parent, paramfile, maxdimensions, "parameter ");
	}
	
	
	
	
	
	
	/** See if the user has a file <code>paramFile</code> to use for parameter axes labels
	 * and, if so, use this file to give meaningful names to parameters.
	 *
	 * this routine creates mappings of the type
	 *             Integer(1) --&gt name of parameter 1
	 *	            Integer(2) --&gt name of parameter 2
	 *				etc
	 *
	 * If there is no mapping for a particular parameter, it is given the default mapping
	 *             Integer(n) --&gt "DEFAULT_PREFIX n"
	 * where DEFAULT_PREFIX is specified in the argument list to the function.
	 * 
	 * If the specified file is not present, then all parameters are given this default mapping.
	 * 
	 * @author peterr
	 * @param parent
	 * @param paramfile
	 * @param maxdimensions
	 * @param defaultPrefix
	 */
	public static String[] guiReadParameterNames(java.awt.Component parent, File paramfile, int maxdimensions, String defaultPrefix)
	{
		String[] paramNames = new String[maxdimensions];
				
		
		if(paramfile.exists()) 
		{
			try {
				java.io.BufferedReader rdr = new java.io.BufferedReader(new java.io.FileReader(paramfile));
				for(int i = 0; i < maxdimensions; i++)
				{
					String val = rdr.readLine();
					if(val != null) 
						paramNames[i] = val;
				}
			}
			catch(java.io.IOException ioe) {
				javax.swing.JOptionPane.showMessageDialog(parent, "Error reading file "+paramfile.getAbsolutePath()+"... reverting to default parameter naming");
				paramNames = null;
			}
		}
		
		//now make sure we have a mapping for every parameter
		for(int i =0; i < maxdimensions; i++)
		{
			if(paramNames[i] == null) {
				Debug.println("inserting default name for parameter "+(i+1), Debug.INFO);
				paramNames[i] = defaultPrefix+(i+1);
			}
		}
		
		return paramNames;
	}

	
	
	
	public static Properties getPropertiesFromFile(File f)
	throws IOException
	{
		Properties result = new Properties();
		BufferedReader rdr = new BufferedReader(new FileReader(f));
		String line = rdr.readLine();
		while(line != null)
		{
			String[] words = Util.getWords(line);
			if(words.length < 3 || !words[1].equals("="))
				Debug.println("Invalid line in .caditk file... skipping", Debug.IMPORTANT);
			String key = words[0];
			String val = line.substring(line.indexOf('=')+1, line.length());
			result.put(key, val);
			line = rdr.readLine();
		}
		return result;
	}
	
	
	
	
	
	public static double[][] readVectorsFromFile(File f) throws IOException
	{
		return readVectorsFromFile(f, false);
	}
	
	
	public static double[][] readVectorsFromFile(File f, boolean allowjagged) throws IOException
	{
		ArrayList list = new ArrayList();
		int veclength = -1;
		BufferedReader rdr = new BufferedReader(new FileReader(f)); 
		for(String line = rdr.readLine(); line != null; line = rdr.readLine())
		{
			if(line.startsWith("#")) //comment lines
				continue;
			String[] words = line.split(" ");
			
			//first time through, we remember the vector length
			if(veclength == -1)
				veclength = words.length;
			
			if(words.length != veclength)
				throw new IOException("Invalid file format... line: "+line);
			
			double[] vect = new double[veclength];
			for(int i = 0; i < veclength; i++) 
			{
				try { 
					if(words[i].equalsIgnoreCase("nan"))
						vect[i] = Double.NaN;
					else
						vect[i] = Double.parseDouble(words[i]); 
				}
				catch(NumberFormatException e) { throw new IOException("Invalid file format... expected a number on line: "+line); }
			}
			list.add(vect);
			
			if(allowjagged)
				veclength = -1; //reset veclength
		}
		if(list.size() == 0)
			throw new IOException("File contains no data");
		double[][] res = new double[list.size()][];
		for(int i =0; i < res.length; i++)
			res[i] = (double[]) list.get(i);
		return res;
	}
	


	
	public static int[][] readIntVectorsFromFile(File f, boolean allowjagged) throws IOException
	{
		ArrayList list = new ArrayList();
		int veclength = -1;
		BufferedReader rdr = new BufferedReader(new FileReader(f)); 
		for(String line = rdr.readLine(); line != null; line = rdr.readLine())
		{
			if(line.startsWith("#")) //comment lines
				continue;
			String[] words = line.split(" ");
			
			//first time through, we remember the vector length
			if(veclength == -1)
				veclength = words.length;
			
			if(words.length != veclength)
				throw new IOException("Invalid file format... line: "+line);
			
			int[] vect = new int[veclength];
			for(int i = 0; i < veclength; i++) 
			{
				try { vect[i] = Integer.parseInt(words[i]); }
				catch(NumberFormatException e) { throw new IOException("Invalid file format... expected a number on line: "+line); }
			}
			list.add(vect);
			
			if(allowjagged)
				veclength = -1; //reset veclength
		}
		if(list.size() == 0)
			throw new IOException("File contains no data");
		int[][] res = new int[list.size()][];
		for(int i =0; i < res.length; i++)
			res[i] = (int[]) list.get(i);
		return res;
	}

	
	
	
	
	
	/**
	 * 
	 * @param zipstream
	 * @param directoryToUnzipIn
	 * @throws IOException
	 */
	public static void unzip(ZipInputStream zipstream, File directoryToUnzipIn) throws IOException
	{
		ZipEntry zentry = zipstream.getNextEntry();
		byte[] buf = new byte[8192];
		if(zentry == null) throw new IOException("Empty zip archive!");
		while(zentry != null)
		{
			if(zentry.isDirectory())
			{
				File tocreate = new File(directoryToUnzipIn, zentry.getName());
				Debug.println("creating "+tocreate.getPath(), Debug.EXTRA_INFO);
				tocreate.mkdir();
			}
			else
			{
				File tocreate = new File(directoryToUnzipIn, zentry.getName());
				Debug.println("unzipping "+tocreate.getPath(), Debug.EXTRA_INFO);
				tocreate.createNewFile();
				FileOutputStream fout = new FileOutputStream(tocreate);
				long entrysize = zentry.getSize();
				if(entrysize >= Integer.MAX_VALUE) throw new IOException("Entry in zip file is too big... cannot handle...aborting");
				int nread = 0;
				while((nread = zipstream.read(buf)) > -1)
				{
					if(nread < 0) throw new IOException("Invalid Zip file!");
					Debug.println("read "+nread+" bytes", Debug.EXTRA_INFO);
					fout.write(buf, 0, nread);
				}
				fout.close();
			}
			zentry = zipstream.getNextEntry();
		}
		zipstream.close();
	}
	
	
	
	
	

	
	
	
	
	
	//create a zip file from a directory 
	public static void zipDir(File zipDir, java.util.zip.ZipOutputStream zos) throws java.io.IOException 
	{ 
		zipDir(zipDir.getParent(), zipDir.getName(), zos);
	}
	

	
	
	
	
	private static void zipDir(String prefixpath, String dir2zip, java.util.zip.ZipOutputStream zos) throws java.io.IOException
	{
		File zipDir = new File(prefixpath, dir2zip);
		if(!zipDir.isDirectory())
			throw new IllegalArgumentException(zipDir.getName()+" is not a directory!");  
		String[] dirList = zipDir.list();
		
		java.util.zip.ZipEntry anEntry = null;

		//create top level directory entry
		//NB: note that this '/' is NOT unix specific, it's what the zip format requires
		anEntry = new java.util.zip.ZipEntry(dir2zip+"/");
		
		zos.putNextEntry(anEntry); 

		
		byte[] readBuffer = new byte[4096]; 
		int bytesIn = 0; 
		//loop through director, and zip the files 
		for(int i=0; i< dirList.length; i++) 
		{ 
			File f = new File(zipDir, dirList[i]);
			File frel = new File(dir2zip, dirList[i]);
			
			Debug.println("zipping "+frel.getPath(), Debug.IMPORTANT);
			if(f.isDirectory()) 
			{ 
				//if the File object is a directory, call this 
				//function again to add its content recursively 
				String filePath = frel.getPath();
 
				zipDir(prefixpath, filePath, zos); 
			} 
			else //ordinary file
			{ 
				FileInputStream fis = new FileInputStream(f); 
					
				//create a new zip entry 
				anEntry = new java.util.zip.ZipEntry(frel.getPath());
				
				//put the zip entry in the ZipOutputStream object 
				zos.putNextEntry(anEntry); 
					
				//write the file to the ZipOutputStream 
				while((bytesIn = fis.read(readBuffer)) != -1)  
					zos.write(readBuffer, 0, bytesIn);
					
				fis.close(); 					
			}
		}		
	}
	
	

	/** Create a file by writing an array of bytes to that file.
	 * 
	 * @param prefix The prefix to use for th files name
	 * @param suffix The suffix to use for the files name
	 * @param content the data to write to the file
	 * @return The created file
	 * @throws IOException If any IO exceptions occur
	 */
	public static File createFile(String prefix, String suffix, File dir, byte[] content) throws IOException
	{
		File f = File.createTempFile(prefix, suffix, dir);
		FileOutputStream fout = new FileOutputStream(f);
		fout.write(content);
		fout.close();
		return f;
	}
	
	
	
	
	
	public static byte[] getFileBytes(java.io.File f) throws IOException
	{
		ByteArrayOutputStream bout = new ByteArrayOutputStream(); 
		FileInputStream fin = new FileInputStream(f);
		
		byte[] buf = new byte[8192];
		int nread = 0;
		while((nread = fin.read(buf)) > 0)
			bout.write(buf, 0, nread);
		bout.flush();
		bout.close();
		return bout.toByteArray();
	}
	
	
	
	
	
	
	
	
	public static String[] getLines(String filename) throws IOException
	{
		BufferedReader rdr = new BufferedReader(new FileReader(new File(filename)));
		ArrayList lines = new ArrayList();
		String line = rdr.readLine();
		while(line != null)
		{
			lines.add(line);
			line = rdr.readLine();
		}
		String[] res = new String[lines.size()];
		for(int i =0; i < res.length; i++)
			res[i] = (String) lines.get(i);
		rdr.close();
		return res;
	}
	
	
	
	/** Pop up a dialog asking the user which file to save to,
	 *  and then save the specified image to that file.
	 * 
	 * @param img
	 */
	public static void saveImageToFile(BufferedImage img)
	{
		//get the save file by asking the user
		java.io.File savefile = FileUtil.guiSaveFile(null, "Save image", 
			FileUtil.getFileFilterBySuffixes(new String[] {".png", ".PNG"}), "myimage.png");
		if(savefile == null)
			return; //user must have cancelled
				
		try { saveImageToFile(img, savefile); }
		catch(java.io.IOException ioe) {
			javax.swing.JOptionPane.showMessageDialog(null, "Error saving image.");
		}
	}
	

	
	
	public static void saveImageToFile(BufferedImage img, File savefile)
	throws java.io.IOException
	{
		javax.imageio.ImageIO.write(img, "png", savefile); 
	}

	
	
	
	
	public static void main(String[] args) throws Exception
	{
		Debug.setVerbosityLevel(Debug.MAX_VERBOSITY);
		if(args[0].equalsIgnoreCase("zip"))
		{
			String zipdir = args[1];
			ZipOutputStream zout = new ZipOutputStream(new FileOutputStream("tmp.zip"));
			FileUtil.zipDir(new File(zipdir), zout);
			zout.close();
		}
		else if(args[0].equalsIgnoreCase("unzip"))
		{
			String zipfile = args[1];
			String tounzipin = args[2];
			ZipInputStream zin = new ZipInputStream(new FileInputStream(zipfile));
			FileUtil.unzip(zin, new File(tounzipin));
		}
		
	}
}