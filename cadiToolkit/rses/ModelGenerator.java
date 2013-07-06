package rses;

import rses.inverse.UserFunctionHandle;







public abstract class ModelGenerator implements java.io.Serializable
{
	public final Model generateNewModel()
	{
		Model m = this.generateModel();
		if(m == null) return null;
		if(!m.isMisfitAvailable() && this.evaluator != null)
			m.setMisfit(evaluator.getErrorForModel(m.getModelParameters()));
		return m;
	}
	
	
	/** This is the method that subclasses must override,
	 *  and does the actual generation of the model
	 * 
	 * @return
	 */
	protected abstract Model generateModel();
	
	
	
	private transient UserFunctionHandle evaluator = null;
	/** Setting the misfit evaluator of a ModelGenerater 
	 *  indicates to the model generator that you would like
	 *  it to not only generate the models, but evaluate them
	 *  as well.
	 *  <p>
	 *  However, be warned that such an evaluator is <i>not</i>
	 *  serialized when the ModelGenerater is serialized  
	 *  (i.e. it is transient) .
	 * 
	 * @param h
	 */
	public void setMisfitEvaluator(UserFunctionHandle h)
	{
		this.evaluator = h;
	}
	
	
	public UserFunctionHandle getMisfitEvaluator()
	{
		return evaluator;
	}
	
	
	
	public static ModelGenerator getOneShotModelGenerator(Model model)
	{
		return new OneShotModelGenerator(model);
	}
	
	
	public static ModelGenerator getOneShotModelGenerator(ModelGenerator inner)
	{
		return new NShotModelGenerator(inner, 1);
	}
	
	
	/** If you already have a ModelGenerator, and wish to create an instance
	 *   that can only be called sucessfully <code>n</code> times, you can
	 *   use this method to obtain just such an instance.
	 * 
	 * @param mg
	 * @param numtimes
	 * @return
	 */
	public static ModelGenerator getNShotModelGenerator(ModelGenerator mg, int numtimes)
	{
		return new NShotModelGenerator(mg, numtimes);
	}
	
	
	public static ModelGenerator getModelGenerator(Model[] models)
	{
		return new PreComputedModelGenerator(models);
	}	
}




final class NShotModelGenerator extends ModelGenerator
{
	private int count = 0;
	private int max;
	private ModelGenerator mg = null;
		
	NShotModelGenerator(ModelGenerator mg, int numtimes)
	{	
		this.mg = mg; 
		this.max = numtimes;
	}
		
		
	protected Model generateModel()
	{
		if(count == max) return null;
		count++;
		return this.mg.generateNewModel();
	}
	
	
	//let the underlying evaluater also know about
	//the misfit evaluator
	public void setMisfitEvaluator(UserFunctionHandle h)
	{
		this.mg.setMisfitEvaluator(h);
	}
	
}



final class OneShotModelGenerator extends ModelGenerator
{
	private boolean used = false;
	private Model m = null;
		
	OneShotModelGenerator(Model m)
	{	
		this.m = m; 
	}
	
	protected Model generateModel()
	{
		if(used) return null;
		used = true;
		return m;
	}
}

final class PreComputedModelGenerator extends ModelGenerator
{
	private Model[] m;
	PreComputedModelGenerator(Model[] models)
	{
		m = models;
	}
	
	private int index = 0;
	protected Model generateModel()
	{
		if(index >= m.length)
			return null;
		return m[index++];
	}
}
