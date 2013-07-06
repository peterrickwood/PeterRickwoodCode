package rses.math;

import rses.inverse.UserFunctionHandle;







public abstract class RealFunction
{
	/** Return the upper/lower bounds on each dimension 
	 *  in the domain. Can be infinite.
	 * 
	 * @return An array of length getDimensionOfDomain(),
	 * where each array element is itself an array of length
	 * 2, with the first element specifying the lower bound 
	 * the second the upper
	 */
	public abstract double[][] getDomain();
	
	public abstract double invoke(double[] arguments);

	public abstract double invoke(int[] arguments);

	public abstract int getDimensionOfDomain();
	
	
	
	public static RealFunction generateRealFunction(UserFunctionHandle handle)
	{
		class RealFunctionFromUserFuncHandleImpl extends RealFunction {
			
			private UserFunctionHandle handle;
			RealFunctionFromUserFuncHandleImpl(UserFunctionHandle handle)
			{
				this.handle = handle;
			}
			
			public double[][] getDomain() {
				return handle.getBoundsOnModelSpace();
			}
			
			public double invoke(double[] arguments) {
				return handle.getErrorForModel(arguments);
			}

			public double invoke(int[] arguments) {
				double[] darr = new double[arguments.length];
				for(int i =0; i < darr.length; i++)
					darr[i] = arguments[i];
				return handle.getErrorForModel(darr);
			}

			public int getDimensionOfDomain() {
				return handle.getDimensionOfModelSpace();
			}
			
		}
		return new RealFunctionFromUserFuncHandleImpl(handle);
	}
}
