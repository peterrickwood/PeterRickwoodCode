package rses.util.distributed.server;



public interface ComputeComponent extends java.io.Serializable
{	
	public Object execute(Object[] args);
}