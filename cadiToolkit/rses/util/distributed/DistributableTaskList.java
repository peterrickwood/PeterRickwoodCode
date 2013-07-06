package rses.util.distributed;




public interface DistributableTaskList extends java.io.Serializable
{
	public DistributableTask nextTask();
	
	public boolean hasMoreTasks();
}