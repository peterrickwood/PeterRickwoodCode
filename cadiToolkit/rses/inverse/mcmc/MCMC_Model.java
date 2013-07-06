package rses.inverse.mcmc;




import rses.Debug;
import rses.Model;

/** A Markov Chain Monte Carlo Model. 
 * 
 *  The same as a regular { @link rses.Model}, except for the following:
 * 
 *  <ol>
 *  <li>Models in
 *  an MCMC setting have the implicit assumption that its 
 *  { @link rses.Model#getMisfit() } method
 *  will return the Math.log(likelihood * prior) for the model. 
 *  </li> 
 *  <li> The getLikelihood() and getPrior() methods for
 *  obtaining separately the liklehood and/or prior -- but
 *  note that these are <i>not</i> scaled, unlike the 
 *  total error, which is the logarithm of the prior * likelihood. 
 *  </li>
 *  <li>
 *  Information about the `evidence' (i.e. P(d), the normalization
 *  factor in Bayes rule) can be added to the model as well.
 *  </li>
 *  </ol>
 * 
 * @author peterr
 */
public class MCMC_Model extends Model
{	
	private int axischanged = -1;

	private boolean hasevidence = false;
	
	private double pd = Double.NaN;
	
	private boolean priorset = false;
	private double prior = Double.NaN; //the prior on the model
	
	//evidence summation
	private double evd = Double.NaN;
	//#of samples for estimating evidence
	private int numevd = 0;
	
	
	/** we can piggyback information about the 
	 * value of the evidence on the back of 
	 * each model. This is the number of samples
	 * drawn from the uniform distribution that 
	 * have been drawn to estimate the evidence. 
	 */
	public int getNumberOfEvidenceSamples()
	{
		return numevd;
	}
	
	/** Returns true if information about the
	 *  evidence has been piggybacked on this
	 *  model. Use the {@link #getEvidenceSum()}
	 *  and {@link #getNumberOfEvidenceSamples()}
	 *  methods to obtain this information.
	 * 
	 * @return
	 */
	public boolean hasEvidence()
	{
		return this.hasevidence;
	}
	
	/** The sum of all the Probabilities (likelihood * prior)
	 *  of samples drawn from a uniform distribution.
	 * 
	 * @return
	 */
	public double getEvidenceSum()
	{
		if(!this.hasevidence)
			throw new RuntimeException("getEvidence(): no estimate of evidence available...");
		else if(Double.isNaN(evd))
			throw new IllegalStateException("internal error. evidence estimation is set, but has no value");
		return evd;
	}

	/** Set the evidence information to carry with this model.
	 * 
	 * @param sum
	 * @param numvals
	 */
	public void setEvidenceInformation(double sum, int numvals)
	{
		this.hasevidence = true;
		this.evd = sum;
		this.numevd = numvals;
	}
	
	
	/** Get the conditional likelihood for thi model.
	 * 
	 * @return
	 */
	public double getLikelihood()
	{
		if(Double.isNaN(pd))
			throw new RuntimeException("pd not set, or set to NaN");
		if(Double.isNaN(prior))
			throw new RuntimeException("prior not set, or set to NaN");

		double res = pd/prior;
		//rses.Debug.println("liklihood is "+res, rses.Debug.IMPORTANT);
		return res;
	}
	
	/** Return the un-scaled (i.e. non-log-transformed)
	 *  prior*likelihood. This is just Math.exp(getMisfit())
	 * 
	 * @return
	 */
	public double getLikelihoodTimesPrior()
	{
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID))
			if(Math.exp(this.getMisfit()) != pd)
				throw new IllegalStateException("scaled and unscaled probabilities do not match!! Should never happen");
			
		return pd;
	}
	
	/** Has the prior been set for this model.
	 * 
	 * @return
	 */
	public boolean isPriorSet()
	{
		return this.priorset;
	}
	
	/** Get the prior for this model. If it has not been
	 *  set, a RuntimeException is thrown.
	 * 
	 * @return
	 */
	public double getPrior()
	{
		if(!this.priorset)
			throw new RuntimeException("Attempt to obtain prior for a model which has not had its prior set");
		return prior;
	}
	
	public void setPrior(double p)
	{
		this.priorset = true;
		this.prior = p;
	}
	
	public void setMisfit(double mf)
	{
		pd = Math.exp(-mf);
		super.setMisfit(mf);
	}
	
	public void setMisfit(double mf, double prior)
	{
		this.setMisfit(mf);
		this.prior = prior;
	}
	
	public MCMC_Model(double[] params, int axischanged)
	{
		super(params);
		this.axischanged = axischanged;
	}
	
	public MCMC_Model(Model m, int axischanged)
	{
		this(m.getModelParameters(), axischanged);
	}

	/** Which axis was perturbed in this model, compared to
	 *  the model it was derived from. This only applies in
	 *  some circumstances, so in other circumstances this
	 *  method makes no sense, in which case it returns -1.
	 *  For example, a MCMC_Model that is not derived from
	 *  another MCMC_Model by simply altering a single axis
	 *  cannot return a sensible value from this method. 
	 * 
	 * @return The axis which has changed, or -1 if the question
	 *  makes no sense.
	 */
	public int whichAxisChanged()
	{
		return axischanged;
	}
	
	/** 
	 * 
	 * @param i
	 * @see #whichAxisChanged()
	 */
	public void setChangedAxis(int i)
	{
		if(this.axischanged != -1)
			throw new IllegalStateException("Attempt to set which axis changed on model that already has this value set");
		if(i > this.parameter.length)
			throw new IllegalArgumentException("Argument is greater than dimension of model");
		this.axischanged = i;
	}
	
	private boolean accepted = false;
	/** Is this model accepted? -- that is, having had
	 *  the Metropolis-Hastings (or some other) test 
	 *  applied, did it pass that test. 
	 * 
	 * 
	 * @return
	 */
	public boolean isAccepted()
	{
		return this.accepted;
	}
	
	public void setAccepted(boolean b)
	{
		this.accepted = b;
	}

	
	
	private boolean isinvalid = false;
	/**
	 * Some models are outside the bounds of the parameter space
	 * and they are deemed invalid
	 *
	 */
	public void setInvalid()
	{
		this.isinvalid = true;
	}

	/**
	 * Some models are outside the bounds of the parameter space
	 * and they are deemed invalid
	 *
	 */
	public boolean isValid()
	{
		return !this.isinvalid;
	}
	
	public String toString()
	{
		String res = super.toString()+" accepted: "+this.accepted;
		if(host != null)
			res += " chain_id: "+host;
		return res;
	}
	
	private String host = null;
	/** The name of the computer on which this MCMC_Model was
	 *  generated or evaluated. Setting this information can
	 *  be useful for debugging and informational purposes.
	 * 
	 * @param hostname
	 */
	public void setHostname(String hostname)
	{
		this.host = hostname;
	}
	
}

