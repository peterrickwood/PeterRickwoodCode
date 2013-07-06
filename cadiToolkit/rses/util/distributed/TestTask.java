package rses.util.distributed;

public class TestTask implements DistributableTask
{	
	public Object execute()
	{
		return "TEST OK";
	}
	
	public boolean containsNativeMethods()
	{
		return false;
	}
	
}
