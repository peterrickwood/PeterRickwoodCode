package rses.inverse.na;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import rses.inverse.ObservableInverter;
import rses.inverse.UserFunctionHandle;
import rses.Debug;







public class NadFile implements ObservableInverter
{
	private java.util.ArrayList observers = new java.util.ArrayList();
	private static final String nl = System.getProperty("line.separator");
	
	
	public NadFile(java.io.File nadfile) throws IOException
	{
		if(!nadfile.exists())
			throw new FileNotFoundException("Specified Nad file does not exist");
		
		RandomAccessFile file = new RandomAccessFile(nadfile, "r");
		
		int headerInt = file.readInt();
		if(headerInt >= 0)
			throw new IOException("This nad file seems to be an older style nad file (or else it is corrupted)"+nl
			                                         +"Unfortunately, old style nad files are not supported");
		
		int mul = -headerInt; //length of initial header chunk
		Debug.println("mul is "+mul, Debug.INFO);
		int nd = file.readInt();	
		Debug.println("nd is "+nd, Debug.INFO);
		int ne = file.readInt();
		Debug.println("ne is "+ne, Debug.INFO);
		int nh = file.readInt();
		Debug.println("nh is "+nh, Debug.INFO);
		int nhu = file.readInt();
		Debug.println("nhu is "+nhu, Debug.INFO);
		
		StringBuffer sb = new StringBuffer(nh);
		for(int i =0; i < nh; i++)
			sb.append( (char) file.readByte());
		String userheader = sb.toString();
		Debug.println("userheader is: ", Debug.INFO);
		Debug.println(userheader, Debug.INFO);
		


		int lenh  = 4*5+nh;
		int len   = 4*(nd+1);

		Debug.println("record length "+len, Debug.EXTRA_INFO);
		Debug.println("header length "+lenh, Debug.EXTRA_INFO);
	 	Debug.println("Number of records in header "+mul, Debug.EXTRA_INFO);



		file.close();
		
	}
	
	
	public void addModelObserver(rses.inverse.ModelObserver m)
	{
		observers.add(m);
		
		//now notify the observer about all the models we've seen
		
	}
	
	
	public void removeModelObserver(rses.inverse.ModelObserver observer)
	{
		this.observers.remove(observer);
	}

	
	
	public void run()
	{}
	
	
	public rses.Model getBestModel()
	{
		return null;
	}
	
	public UserFunctionHandle getUserFunctionHandle()
	{
		return null;
	}
	
	
	public String getStageName()
	{
		return null;
	}
	
	public double getStage()
	{
		return Double.NaN;
	}
	
	
	public long getRunningTime()
	{
		return 0L;
	}
	
	
	public boolean isFinished()
	{
		return true;
	}
	
}