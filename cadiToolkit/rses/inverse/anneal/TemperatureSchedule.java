package rses.inverse.anneal;







public interface TemperatureSchedule
{
	/** An iteration is a perturbation through each
	 *  axis. A certain number of iterations are
	 *  performed for every temperature.
	 * 
	 * @return
	 */
	public int getIterationsPerTemperature();


	/**
	 * 
	 * <b><i>NB:</i></b> A temperature of -INFINITY
	 * can be used to indicate that all changes should 
	 * be rejected. 
	 * 
	 * @return An Iterator for the temperatures in the 
	 *         cooling schedule.
	 * 
	 */
	public java.util.Iterator getTemperatures();
}



