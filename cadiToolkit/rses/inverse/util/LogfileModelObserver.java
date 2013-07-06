package rses.inverse.util;

import rses.inverse.ModelObserver;
import rses.inverse.UserFunctionHandle;

import java.io.*;






public class LogfileModelObserver implements ModelObserver
{
	private java.io.PrintWriter logwriter;

	public LogfileModelObserver(java.io.File filename, UserFunctionHandle h) throws IOException
	{
		java.io.PrintWriter logfile = null;

		logwriter = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
		FakeUserFunctionHandle.write(h, logwriter);
	}
	
	
	public void newModelsFound(rses.Model[] models)
	{
		for(int i =0; i < models.length; i++)
			logwriter.println(models[i].toString());
		logwriter.flush();
	}
	
	public void newModelFound(rses.Model model)
	{
		this.newModelsFound(new rses.Model[] {model});
	}
	
	
	public void close()
	{
		logwriter.close();
	}

	
	public void finalize()
	{
		this.close();
	}
}