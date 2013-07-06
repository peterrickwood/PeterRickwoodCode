package rses.inverse;





public interface ModelObserver
{
	public void newModelFound(rses.Model m);	
	
	public void newModelsFound(rses.Model[] models);
}