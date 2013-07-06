package rses.inverse.anneal;



/** An eternal temperature schedule at a constant temperature 
 * 
 * @author peterr
 */
public class EndlessTemperatureSchedule implements TemperatureSchedule, java.util.Iterator
{
	private double temp;
	private Double tempD;
	/** An endless schedule.
	 * 
	 * @param temp The temperature to stay at.
	 */
	public EndlessTemperatureSchedule(double temp)
	{	
		this.temp = temp;
		this.tempD = new Double(temp);
	
	}
	
	/** Implements {@link java.util.Iterator}
	 * 
	 */
	public boolean hasNext() {return true;}

	/** Implements {@link java.util.Iterator}
	 * 
	 */
	public int getIterationsPerTemperature() {return Integer.MAX_VALUE; }
	
	/** Implements {@link java.util.Iterator}
	 * 
	 */
	public java.util.Iterator getTemperatures() {	return this; }
	
	/** Implements {@link java.util.Iterator}
	 * 
	 */
	public void remove() { throw new UnsupportedOperationException(); }
	
	/** Implements {@link java.util.Iterator}
	 * 
	 */
	public Object next() {	return tempD; }
	
}