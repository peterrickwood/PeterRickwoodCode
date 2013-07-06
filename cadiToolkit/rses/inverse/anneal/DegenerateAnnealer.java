package rses.inverse.anneal;

import rses.util.distributed.NodeInfo;






/** This class implements a 'degenerate' annealer -- one that rejects
 *   every move that is proposed.
 * 
 *  By default the temperature schedule for this annealer is
 *  endless, at a fixed temperature of -INFINITY. It is considered
 *  illegal to set the temperature schedule to anything where the
 *  temperature us other than -INFINITY.
 * 
 * @author peter rickwood
 *
 */
public final class DegenerateAnnealer extends MetropolisHastingsAnnealer
{
	public static DegenerateAnnealer getDistributedInstance(
		NodeInfo[] nodelist, rses.inverse.UserFunctionHandle handle)
	{
		return new DegenerateAnnealer(nodelist, handle);
	}
	
	
	protected DegenerateAnnealer(NodeInfo[] nodelist, 
		rses.inverse.UserFunctionHandle handle)
	{
		super(nodelist, handle);
		this.setTemperatureSchedule(new EndlessTemperatureSchedule(Double.NEGATIVE_INFINITY));
	}


	
	public DegenerateAnnealer(rses.inverse.UserFunctionHandle handle)
	{
		super(handle);
		this.setTemperatureSchedule(new EndlessTemperatureSchedule(Double.NEGATIVE_INFINITY));
	}
	
	
	
	
	protected boolean acceptChange(rses.Model orig, rses.Model newmod)
	{
		return false;
	}
	
	/** A temperature of -INFINITY is used to indicate that all 
	 * models should be rejected. This method returns -INFINITY
	 * always.
	 * 
	 * 
	 * @see rses.inverse.anneal.SimulatedAnnealer#getCurrentTemperature()
	 */
	public double getCurrentTemperature()
	{
		if(super.getCurrentTemperature() != Double.NEGATIVE_INFINITY)
			throw new RuntimeException("Illegal Temperature schedule for Degenerate Annealer -- the temperature should be permanently at -INFINITY");
		return Double.NEGATIVE_INFINITY;
	}

}