package rses.util;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;














public class StreamGobbler extends Thread
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
