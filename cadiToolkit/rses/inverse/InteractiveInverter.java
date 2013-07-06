package rses.inverse;





public interface InteractiveInverter extends Inverter
{
	/** How long should the inversion run before stopping?
	 *  calling this method tells the inverter to stop running
	 *  at time System.currentTimeMillis()+millisecs.
	 * 
	 *  Calling it multiple times will reset the running time
	 *  (so you can interactively adjust the end time,
	 *   depending on how the inversion is going).
	 * 
	 *  The Inverter is not guaranteed to end at this time
	 *  (that is, it may run a bit longer or a bit shorter),
	 *  but will make an effort to end around about this
	 *  time. Some inverters, for example, may choose
	 *  to finish an iteration before exiting (rather than
	 *  exiting mid-iteration).
	 * 
	 * @param millisecs How much longer to run for
	 */
	public void setTimeToRun(long millisecs);
	
	
	/** Stop the inversion at the next opportune moment
	 *   (this may not be immediately), but only
	 *   if the inversion is running (that is, if the inverters
	 *   run method has been called).
	 * 
	 *  The inverter is <i>not</i> guaranteed to be stopped
	 *  after this method returns.
	 */
	public void stopInversion();
	
}