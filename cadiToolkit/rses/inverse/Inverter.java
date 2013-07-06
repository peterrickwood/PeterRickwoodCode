package rses.inverse;


public interface Inverter extends Runnable 
{
	/** Get the UserFunctionHandle being used to drive this inversion
	 * 
	 * @return the UserFunctionHandle being used to drive this inversion
	 */
	public UserFunctionHandle getUserFunctionHandle();
	
}