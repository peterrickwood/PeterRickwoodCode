package rses.util.distributed;



import java.io.Serializable;





public interface DistributableTask extends Serializable
{
	public Object execute();	
	
	public boolean containsNativeMethods();
}