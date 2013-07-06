package rses;




public abstract class Debug
{	
	public static final int DEBUG_PARANOID = -5;
	public static final int DEBUG_SUSPICIOUS = -6;
	public static final int DEBUG_NORMAL = -7;
	public static final int DEBUG_CAREFREE = -8;

	public static final int MAX_PARANOIA = DEBUG_PARANOID;
	public static final int MIN_PARANOIA = DEBUG_CAREFREE; 
	private static int debugLevel = DEBUG_NORMAL;
	
	
	public static final int CRITICAL = 0;
	public static final int IMPORTANT = 1;
	public static final int INFO = 2;
	public static final int EXTRA_INFO = 3;
	private static int verbosityLevel = IMPORTANT;
	
	
	public static final int MAX_VERBOSITY = EXTRA_INFO;
	public static final int MIN_VERBOSITY = CRITICAL;
	
	
	
	private static int getDebugLevel()
	{
		return debugLevel;
	}
	

	/** tests current debugging level
	 * 
	 * @param debuglvl
	 * @return true if current debug level is as paranoid,
	 * or more paranoid, than debuglvl
	 */
	public static boolean equalOrMoreParanoid(int debuglvl)
	{
		if(debuglvl < MIN_PARANOIA || debuglvl > MAX_PARANOIA)
		{
			String msg = "attempt to test debug against invalid debug level...";
			if(Debug.getDebugLevel() >= Debug.DEBUG_NORMAL)
				throw new RuntimeException(msg);
			else
				Debug.println(msg, Debug.IMPORTANT);
		}
		return Debug.getDebugLevel() >= debuglvl;
	}

	
	public static void setDebugLevel(int lvl)
	{
		if(lvl > MAX_PARANOIA || lvl < MIN_PARANOIA)
		{
			String msg = "Attempt to set Debug level to invalid value.....";
			if(debugLevel >= Debug.DEBUG_NORMAL)
				throw new RuntimeException(msg);
			else
				Debug.println(msg, Debug.IMPORTANT);
			debugLevel = MAX_PARANOIA;
		}
		else
			debugLevel = lvl;
	}
	

	public static void moreParanoid()
	{
		if(debugLevel != Debug.MAX_PARANOIA)
			setDebugLevel(debugLevel+1);
	}
	

	public static void lessParanoid()
	{
		if(debugLevel != Debug.MIN_PARANOIA)
			setDebugLevel(debugLevel-1);
	}

	
	/** tests current verbosity level
	 * 
	 * @param verblvl
	 * @return true if current verbosity is as verbose,
	 * or more verbose, than verblvl
	 */
	public static boolean equalOrMoreVerbose(int verblvl)
	{
		if(verblvl < MIN_VERBOSITY || verblvl > MAX_VERBOSITY)
		{
			String msg = "attempt to test verbosity level against invalid verbosity...";
			if(Debug.getDebugLevel() >= Debug.DEBUG_NORMAL)
				throw new RuntimeException(msg);
			else
				Debug.println(msg, Debug.IMPORTANT);
		}
		return Debug.getVerbosityLevel() >= verblvl;
	}
	
	private static int getVerbosityLevel()
	{
		return verbosityLevel;
	}
	
	public static void moreVerbose()
	{
		setVerbosityLevel(verbosityLevel+1);
	}
	
	public static void lessVerbose()
	{
		setVerbosityLevel(verbosityLevel-1);
	}


	public static java.io.PrintStream getPrintStream(int lvl)
	{
		java.io.PrintStream ps = System.out;
		if(lvl <= Debug.CRITICAL)
			ps = System.err;
		return ps;
	}
	
	public synchronized static void setVerbosityLevel(int lvl)
	{
		if(lvl < MIN_VERBOSITY)
			verbosityLevel = MIN_VERBOSITY;
		else if(lvl > MAX_VERBOSITY)
			verbosityLevel = MAX_VERBOSITY;
		else
			verbosityLevel = lvl;
	}
	
	
	public static void print(String s, int lvl)
	{
		java.io.PrintStream ps = getPrintStream(lvl);
		
		if(verbosityLevel >= lvl)
			ps.print(s);
	}

	public static void println(String s, int lvl)
	{
		java.io.PrintStream ps = getPrintStream(lvl);
		
		if(verbosityLevel >= lvl)
			ps.println(s);
	}	


	public static void println(Exception excpt, int lvl)
	{
		java.io.PrintStream ps = getPrintStream(lvl);
		
		println(ps, excpt, lvl);		
	}
	
	
	public static void println(java.io.PrintStream ps, Exception excpt, int lvl)
	{
		if(verbosityLevel >= lvl) {
			ps.println(excpt);
			ps.println("Message: "+excpt.getMessage());
			StackTraceElement[] trace = excpt.getStackTrace();
			if(trace != null) for(int i =0; i < trace.length; i++)
				ps.println(trace[i]);
		}
	}

	
	

	public static void print(java.io.PrintStream ps, String s, int lvl)
	{
		if(verbosityLevel >= lvl)
			ps.print(s);
	}

	public static void println(java.io.PrintStream ps, String s, int lvl)
	{
		if(verbosityLevel >= lvl)
			ps.println(s);
	}	
}