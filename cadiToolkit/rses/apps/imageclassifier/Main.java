package rses.apps.imageclassifier;


import java.io.File;

import javax.swing.JFrame;

import rses.Debug;








public abstract class Main
{
	
	
	
	
	
	
	
	//args[0] = classifierdepth
	//args[1] = numclasses
	//args[2] = img x
	//args[3] = img y
	//args[4] = path to directory with image files
	public static void main(String[] args) 
	{
		Debug.setVerbosityLevel(Debug.MAX_VERBOSITY);
	
		Debug.println("REMEMBER: This program assumes aerial is 512x512 and transport is 512x512 and elevation is 64x64", Debug.IMPORTANT);
	

		if(args.length != 6)
		{
			Debug.println("Usage: ", Debug.CRITICAL);
			Debug.println("arg1: terminalnodesize", Debug.CRITICAL);
			Debug.println("arg2: number of classes", Debug.CRITICAL);
			Debug.println("arg3: image x", Debug.CRITICAL);
			Debug.println("arg4: image y", Debug.CRITICAL);
			Debug.println("arg5: path to image directories", Debug.CRITICAL);
			Debug.println("arg6: 0 if in normal mode, 1 if in lat/long point gathering mode", Debug.CRITICAL);
			System.exit(0);
		}

		int cdepth = Integer.parseInt(args[0]);
		int nclass = Integer.parseInt(args[1]);
		int x = Integer.parseInt(args[2]);
		int y = Integer.parseInt(args[3]);
		boolean latlongmode = Integer.parseInt(args[5]) != 0;
		ImageDisplayPanel idp = new ImageDisplayPanel(cdepth, nclass, args[4]);
		idp.latlongdatabasemode = latlongmode;
		idp.setImageToDisplay(x, y);
		
		JFrame jframe = new JFrame();
		jframe.setSize(600,600);
		jframe.getContentPane().add(idp);
		jframe.setVisible(true);
		
		
		
	}
	
}
