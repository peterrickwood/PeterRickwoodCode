package rses.util;

import rses.Debug;


public class VerbosityListener extends java.awt.event.KeyAdapter implements java.awt.event.ActionListener
{
	private static VerbosityListener singleton = new VerbosityListener();
	
	private VerbosityListener()
	{}
	
	public static VerbosityListener getVerbosityListener()
	{
		return singleton;
	}
	
	public synchronized void keyTyped(java.awt.event.KeyEvent ke) {
		if(ke.getKeyChar() == 'v') {
			if(!Debug.equalOrMoreVerbose(Debug.MAX_VERBOSITY)) Debug.print("OK, I will be more verbose : ", Debug.IMPORTANT);
			Debug.moreVerbose();
		}
		else if(ke.getKeyChar() == 'q') {
			Debug.println("OK, I will be more less verbose", Debug.IMPORTANT);
			Debug.lessVerbose();
		}
	}
	
	public synchronized void actionPerformed(java.awt.event.ActionEvent action)
	{
		if(action.getActionCommand().trim().equalsIgnoreCase("more verbose"))
		{
			if(!Debug.equalOrMoreVerbose(Debug.MAX_VERBOSITY)) Debug.print("OK, I will be more verbose : ", Debug.IMPORTANT);
			Debug.moreVerbose();
		}
		else if(action.getActionCommand().trim().equalsIgnoreCase("less verbose"))
		{
			Debug.println("OK, I will be more less verbose", Debug.IMPORTANT);
			Debug.lessVerbose();
		}
	}
}

