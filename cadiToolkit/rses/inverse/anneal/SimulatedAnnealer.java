package rses.inverse.anneal;

import rses.inverse.ObservableInverter;




/** Interface that describes the contract that a Simulated Annealer
 *  must implement.
 * 
 * @author peterr
 */
public interface SimulatedAnnealer extends ObservableInverter
{
	/** Perturb the current model, and 
	 * 
	 * @return true if the model has changed, false otherwise
	 */
	public boolean doNextPerturbation();
	

	/** Returns the current temperature of this annealer. 
	 *  <p>
	 *  If no temperature schedule has been set, and
	 *  this annealer does not implement a default, 
	 *  this method must return Double.NaN.
	 */
	public double getCurrentTemperature();

	/** Sets the current temperature schedule, overwriting any
	 *  existing schedule.
	 *  <p>
	 *  The annealer then continues annealing accoring to the
	 *  new schedule, beginning at the first temperature
	 *  specified by the new schedule.
	 */
	public void setTemperatureSchedule(TemperatureSchedule ts);

	/** Returns the current temperature schedule. 
	 *  <p>
	 *  If no schedule has been set, this method should 
	 *  return the default temperature schedule, or null (if 
	 *  the SimmulatedAnnealer does not implement a default).
	 */
	public TemperatureSchedule getTemperatureSchedule();
	
}





