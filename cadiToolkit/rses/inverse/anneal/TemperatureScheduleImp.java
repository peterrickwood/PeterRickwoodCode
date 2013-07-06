package rses.inverse.anneal;



/** A class to implement {@link rses.inverse.mcmc.TemperatureSchedule}
 * 
 * @author peterr
 */
public class TemperatureScheduleImp implements TemperatureSchedule, java.util.Iterator
{
	
	private java.util.ArrayList temps = new java.util.ArrayList(); 
	private int iterationsPerTemp = -1;
	
	public TemperatureScheduleImp(double startTemp, double endTemp, 
				double multiplier, int iterationsPerTemp)
	{
		double temp = startTemp;
		
		while(temp >= endTemp)
		{
			temps.add(new Double(temp));
			temp *= multiplier;
		}
		this.iterationsPerTemp = iterationsPerTemp;
	}
	
	public java.util.Iterator getTemperatures()
	{
		return this;
	}
	
	public int getIterationsPerTemperature()
	{
		return this.iterationsPerTemp;
	}
	
	int index = 0;
	public boolean hasNext()
	{
		return index < this.temps.size();
	}
	
	public Object next()
	{
		Double res = (Double) temps.get(index);
		index++;
		return res;
	}
	
	public void remove()
	{	throw new UnsupportedOperationException(); }
	
}