package rses.inverse;







public interface ObservableInverter extends Inverter
{	
	/** How long (in milliseconds) has the inversion been running? The granularity
	 *  of this timer isn't necessarily milliseconds.
	 * 
	 * @return The number of milliseconds that the inversion has been running.
	 */
	public long getRunningTime();

	
	/** <p>Generically, inverters are considered to go through stages. The term stage
	 *   for a particular inverter may mean 'iteration' or 'temperature' or 'generation'
	 *  or some other measure of inversion running time. 
	 * 
	 * <p> So, what <i>stage</i> means exactly is dependent on the particular inverter.
	 * It is typically a monotonically increasing/decreasing function of running time 
	 * (such as "number of iterations" or "number of models evaluated"), but it does not
	 * have to be.
	 * 
	 * @return a number that represents what stage the inverter is at.
	 */
	public double getStage();
	
	/** Get a string which gives the particular name given to the generic <code>stage</code>
	 *  concept by this particular inverter. 
	 * @see rses.inverse.ObservableInverter#getStage() for a description of inverter stages.
	 * 
	 * @return what a stage represents to this inverter
	 */
	public String getStageName();
	
	
	/** Tell the Inverter that we wish to be notifed each time a new model is
	 *   evaluated. 
	 * 
	 * @param listener
	 */
	public void addModelObserver(ModelObserver listener);
	
	
	/** Tell the Inverter that we're no longer interested in recieving 
	 * notification when new models are found.
	 * 
	 * @param listener
	 */
	public void removeModelObserver(ModelObserver listener);
	
	
	/** Get the best model seen by the inverter so far.
	 * 
	 * @return The best model so far, or null if no models have been
	 *  evaluated.
	 */
	public rses.Model getBestModel();
	
	
	/** Has this inverter finished doing it's inversion.
	 *  Specifically, has it returned from it's run() method.
	 *
	 *  If the inverter has not been started yet, 
	 *  (i.e it's run method has not been called)
	 *  then this method should return false.
	 * 
	 * @return true if this method has 
	 * returned from its run() method,
	 * false otherwise.
	 */
	public boolean isFinished();
	
	
}
