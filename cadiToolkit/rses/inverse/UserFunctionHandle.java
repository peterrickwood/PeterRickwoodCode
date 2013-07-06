package rses.inverse;


/**
 * <p>
 * This interface needs to be implemented by those who
 * want to use certain functions in the rses inversion
 * toolkit, that require the caller to pass in an
 * object that implements this interface.
 * 
 * <p>
 * Classes that implement this interface are, in effect,
 * saying that they will perform certain tasks in 
 * the inversion process.
 *
 *
 * <p>
 * @author Peter Rickwood, Research School Of Earth Sciences
 * Australian National University
 * email: peter.rickwood@anu.edu.au
 *
 * 
 */


public interface UserFunctionHandle
{
	/** @return  The number of parameters that
	 *  are required to specify each model.
	 * 
	 * If models are allowed to have varying numbers of parameters,
	 * then this method should return the MAXIMUM possible
	 * dimension of any particular model. 
	 */
	public int getDimensionOfModelSpace();

	/** @return The upper/lower bounds on each parameter
	 *  in model space. 
	 *
	 *  <blockquote>
	 *  The array must be laid out as follows:
	 *  array[0][0] -- holds lower bound on parameter 0
	 *  array[0][1] -- holds upper bound on parameter 0
	 *  array[1][0] -- holds lower bound on parameter 1
	 *  array[1][0] -- holds upper bound on parameter 1
	 *  ...
	 *  array[getDimensionOfModelSpace()-1][0]
	 *  array[getDimensionOfModelSpace()-1][1]
	 *  </blockquote>
	 *
	 *  <p>
	 *  This function is only typically called once
	 *  by inversion programs -- at the beginning,
	 *  but this is <i>not</i> guaranteed, so, if you
	 *  plan to implement this interface, you *must*
	 *  make sure that this function can be called
	 *  safely more than once.
	 *
	 */
	public double[][] getBoundsOnModelSpace();



	/** calculte the error of the specified model.
	 *
	 * Given a specified (<i>unscaled</i>) model,
	 * calculate the error/misfit for that model.
	 *
	 *  For certain Bayesian algorithms, the
	 *  getErrorForModel() function is required to 
	 *  return -Log(Prior(model)*Liklihood(Model)).
	 * @return the error/misfit for <code>model</code>
	 */
	public double getErrorForModel(double[] model);


	
	
	/** Some Bayesian estimaters/inverters need
	 *  to know the prior for a model.
	 * 
	 * @param model
	 * @return
	 */
	public double getPriorForModel(double[] model);
	


	/**
	 * 
	 * @param pnum The parameter number (from 0 to getDimensionOfModelSpace()-1, inclusive)
	 * @return The name of that parameter
	 */
	public String getParameterName(int pnum);
	
	
	
	/**
	 * 
	 * @param names
	 */
	public void setParameterName(int pnum, String name);
	
	
}











