package rses.inverse.powell;


import rses.inverse.ModelObserver;
import rses.inverse.UserFunctionHandle;
import rses.util.Util;
import rses.Debug;
import rses.Model;



/** A modified version of Powell's direction set method.
 *  Modifications are to enable to method to perform some
 *  automatic adaptation to try and get a 'good' step size.
 * 
 * 
 * @author peterr
 */
public class PowellMin implements rses.inverse.ObservableInverter
{
	private double precision = Float.MIN_VALUE;
	private double minchange;
	private boolean isFinished = false;
	private double[] axesScaleVector = null;


	public double setPrecision(double p)
	{
		double old = this.precision;
		this.precision = p;
		return old;
	}
	
	private UserFunctionHandle handle = null;

	public UserFunctionHandle getUserFunctionHandle()
	{
		return handle;
	}
	
	  

	private boolean adaptive = false;
	
	/** An Adaptive powell inverter starts off with large
	 *  steps (thus, possibly stepping over local minima),
	 *  and reduces this step size as it runs. Thus, it is 
	 *  performing multiple optimizations at different
	 *  scale-lengths. 
	 * 
	 * @param handle
	 * @param minchange
	 * @param runtimemillisecs
	 * @return
	 */
	public static PowellMin getAdaptivePowell(UserFunctionHandle handle, double minchange, long runtimemillisecs)
	{
		double[] scalevect = new double[handle.getDimensionOfModelSpace()];
		Util.initializeArray(scalevect, 0.25);
		PowellMin adaptivep = new PowellMin(handle, scalevect, minchange, new rses.Model(handle.getBoundsOnModelSpace()), runtimemillisecs);
		adaptivep.adaptive = true;
		return adaptivep;
	}
	
	/** Non-adaptive Powell method. You must specify the step size to use.
	 *  The method still makes some effort to adapt the step size as it goes.
	 * 
	 * @param handle
	 * @param axesScaleVector
	 * @param minchange
	 * @param runtimemillisecs
	 */
	public PowellMin(UserFunctionHandle handle, double[] axesScaleVector,
			double minchange, long runtimemillisecs)
	{
		this(handle, axesScaleVector, minchange, new rses.Model(handle.getBoundsOnModelSpace()), runtimemillisecs);
	}
	
	

	/** Non-adaptive Powell method. You must specify the step size to use.
	 *  The method still makes some effort to adapt the step size as it goes.
	 * 
	 * @param handle
	 * @param axesScaleVector
	 * @param minSignificantChange
	 * @param startMod
	 */
	public PowellMin(UserFunctionHandle handle, double[] axesScaleVector,
			double minSignificantChange, rses.Model startMod)
	{
		this(handle, axesScaleVector, minSignificantChange, startMod, 0L);
	}
	
	
	/**
	 * 
	 * 	Non-adaptive Powell method. You must specify the step size to use.
	 *  The method still makes some effort to adapt the step size as it goes.
	 * 
	 * 
 	 * @param handle
	 * @param axesScaleVector
	 * @param minSignificantChange
	 * @param startMod
	 * @param runtimemillis
	 */
	public PowellMin(UserFunctionHandle handle, double[] axesScaleVector,
		double minSignificantChange, rses.Model startMod, long runtimemillis)
	{
		for(int i = 0; i < axesScaleVector.length; i++)
			if(Math.abs(axesScaleVector[i]) > 1.0)
				throw new IllegalArgumentException("axes scale factor has magnitude > 1. This makes no sense");
		this.axesScaleVector = Util.copy(axesScaleVector);
		
		this.handle = handle;
		this.minchange = minSignificantChange;
		startMod.setDiscoveryTime(0);
		if(!startMod.isMisfitAvailable()) 
		{
			double err = 	handle.getErrorForModel(startMod.getModelParameters());
			startMod.setMisfit(err);
		}
		bestmod = new Model(Util.copy(startMod.getModelParameters()), startMod.getMisfit());
		this.finishTime = System.currentTimeMillis()+runtimemillis;
	}
	
	
	
	private rses.Model bestmod = null;
	public rses.Model getBestModel()
	{
		return new Model(bestmod.getModelParameters(), bestmod.getMisfit());
	}


	public double getStage()
	{
		throw new UnsupportedOperationException();
	}
	
	public String getStageName()
	{
		throw new UnsupportedOperationException();
	}
	

	private java.util.ArrayList observers = new java.util.ArrayList(); 

	public void addModelObserver(ModelObserver obs)
	{
		observers.add(obs);
	}
	
	public void removeModelObserver(ModelObserver obs)
	{
		observers.remove(obs);
	}
	

	public boolean isFinished()
	{
		return this.isFinished;
	}

	public long getRunningTime()
	{
		if(this.isFinished())
			return this.finishTime-this.startTime;
		else
			return System.currentTimeMillis()-this.startTime;
	}

	
	


	private long startTime = -1L;
	private long finishTime = -1L;
	public void run()
	{

		this.startTime = System.currentTimeMillis();
		if(adaptive) 
		{
			//gradually refine step size, doing minimizations
			//at different scales
			do 
			{
				Debug.println("Starting adaptive powell run", Debug.INFO);
				Model m = new Model(handle.getBoundsOnModelSpace());
				m.setMisfit(handle.getErrorForModel(m.getModelParameters()));
				double[] origscale = Util.copy(this.axesScaleVector);
				doAdaptive(m);
				this.axesScaleVector = origscale;
			}
			while(System.currentTimeMillis() < finishTime);
		}
		//keep doing standard powellmins until time runs out
		//This 'normal' powell still has some adaptive steps,
		//but see the code for the powellMin method for this.
		else  
		{
			int dim = handle.getDimensionOfModelSpace();
			double[][] bounds = handle.getBoundsOnModelSpace();
			double[] model = Util.copy(bestmod.getModelParameters());
			double mf = bestmod.getMisfit();
			this.newModelFound(model, mf);
			double[][] axes = new double[dim][dim];
			for(int i =0; i < dim; i++) 
				axes[i][i] =  (bounds[i][1]-bounds[i][0])*axesScaleVector[i];

			do
			{
				this.powellMin(model, axes, mf, handle);
				//generate new random model
				model = new rses.Model(bounds).getModelParameters();
				mf = handle.getErrorForModel(model);
				this.newModelFound(model, mf);
			}
			while(System.currentTimeMillis() < finishTime);
		}
		
		this.finishTime = System.currentTimeMillis();
		this.isFinished = true;
		Debug.println("Powell's method finished", Debug.IMPORTANT);
	}
	


	
	//try and adaptively work out scale factors
	//by starting big and shrinking. Also tries to 
	//adapt minchange
	private void doAdaptive(Model m)
	{
		double origminchange = this.minchange;
		minchange = Math.max(origminchange, bestmod.getMisfit()/handle.getDimensionOfModelSpace());
				
		while(true)
		{
			double before = m.getMisfit();
			m = doAdaptiveIteration(m);
			double after = m.getMisfit();
			if((before-after) < origminchange)
				break;

			//make our step size smaller
			for(int i =0; i < this.axesScaleVector.length; i++)
				this.axesScaleVector[i] /= 2;

			//make our required change smaller
			minchange = Math.min(minchange/2, (before-after)/handle.getDimensionOfModelSpace());
			minchange = Math.max(origminchange, minchange);
		}
		
		//one last iteration with high precision and tight scale
		Debug.println("Doing final optimization at maximum precision", Debug.INFO);
		minchange = origminchange;
		doAdaptiveIteration(m);
	}
	
	private Model doAdaptiveIteration(Model m)
	{
		int dim = handle.getDimensionOfModelSpace();
		double[][] bounds = handle.getBoundsOnModelSpace();
		double[] model = Util.copy(m.getModelParameters());
		double mf = m.getMisfit();
		double[][] axes = new double[dim][dim];
		for(int i =0; i < dim; i++) 
			axes[i][i] =  (bounds[i][1]-bounds[i][0])*axesScaleVector[i];
		double newmf = this.powellMin(model, axes, mf, handle);
		Model res = new Model(model, newmf);
		return res;
	}
	
	
	private void newModelFound(double[] params, double misfit)
	{
		rses.Model m = new rses.Model(Util.copy(params), misfit);
		m.setDiscoveryTime(System.currentTimeMillis()-this.startTime);
		if(misfit < this.bestmod.getMisfit())
			this.bestmod = m;
		int numobs = observers.size();
		for(int i =0; i < numobs; i++)
		{
			ModelObserver modobs = (ModelObserver) this.observers.get(i);
			modobs.newModelFound(m);
		}
	}





	/** Minimize along a particular direction.
	 * 
	 * @param startModel
	 * @param startErr
	 * @param bounds
	 * @param direction
	 * @param handle
	 * @return
	 */
	public double lineMin(double[] startModel, double startErr,
		double[] direction, UserFunctionHandle handle)
	{
		double[][] bounds = handle.getBoundsOnModelSpace();
		int dim = startModel.length;
		double[] mid = Util.copy(startModel);
		double midscore = startErr;
		
		double[] best = startModel;
		double besterr = midscore;

		Debug.print("minimizing in direction: ", Debug.INFO);
		Debug.println(Util.arrayToString(direction), Debug.INFO);
		Debug.println("ERROR before minimization in that direction is "+startErr, Debug.INFO);
		
		double[] leftMod = new double[startModel.length];
		double[] rightMod = new double[startModel.length];
		
		double[] newtry = new double[startModel.length];
		double[] tmpa;
	
		//take up positions to the left and right of our midpoint
		for(int i = 0; i < dim; i++) 
		{
			if(mid[i] < bounds[i][0] || mid[i] > bounds[i][1]) {
				Debug.println("bounds on paramter "+i+" are "+bounds[i][0]+" --- "+bounds[i][1], Debug.CRITICAL);
				Debug.println("but VALUE of parameter "+i+" in provided model is "+mid[i], Debug.CRITICAL);
				throw new RuntimeException("specified model (parameter "+i+") is outside specified bounds.. aborting");
			}
			
			leftMod[i] = mid[i]+direction[i];
			
			//now make sure that leftmod is actually within parameter bounds
			if(leftMod[i] < bounds[i][0]) {
				leftMod[i] = bounds[i][0];
				Debug.println("left model["+i+"] below boundary, boosting to boundary", Debug.EXTRA_INFO);
			}
			else if(leftMod[i] > bounds[i][1]) {
				leftMod[i] = bounds[i][1];
				Debug.println("left model ["+i+"] above boundary, reducing to boundary", Debug.EXTRA_INFO);
			}
			
			//now do the same for rightmod
			rightMod[i] = mid[i]-direction[i];
			if(rightMod[i] < bounds[i][0]) {
				rightMod[i] = bounds[i][0];
				Debug.println("right model ["+i+"] below boundary, boosting to boundary", Debug.EXTRA_INFO);
			}
			else if(rightMod[i] > bounds[i][1]) {
				rightMod[i] = bounds[i][1];
				Debug.println("right model ["+i+"] above boundary, reducing to boundary", Debug.EXTRA_INFO);
			}
		}
	
		//ok, we have a left model, a right model, and a midpoint.
		//we work out the error for each.
		double leftscore = handle.getErrorForModel(leftMod);
		double rightscore = handle.getErrorForModel(rightMod);	
		this.newModelFound(leftMod, leftscore);
		this.newModelFound(rightMod, rightscore);
		
		//failed to bracket a minima, head left in search of one
		if(leftscore < midscore && leftscore < rightscore) 
		{
			Debug.println("LEFT BRACKETING l,m,r "+leftscore+" "+midscore+" "+rightscore, Debug.INFO);
			
			tmpa = rightMod; rightMod = mid;
			mid = leftMod; leftMod = tmpa;

			for(int i = 0; i < dim; i++) {
				leftMod[i] = mid[i]+direction[i];
				leftMod[i] = Math.max(leftMod[i],bounds[i][0]);
				leftMod[i] = Math.min(leftMod[i],bounds[i][1]);
			}
			
			rightscore = midscore;
			midscore = leftscore;
			leftscore = handle.getErrorForModel(leftMod);
			this.newModelFound(leftMod, leftscore);
		}
		//failed to bracket a minima, head right in search of one
		else if(rightscore < midscore && rightscore < leftscore) 
		{
			Debug.println("RIGHT BRACKETING l,m,r "+leftscore+" "+midscore+" "+rightscore, Debug.INFO);
			
			tmpa = leftMod; leftMod = mid;
			mid = rightMod; rightMod = tmpa;

			for(int i = 0; i < dim; i++) {
				rightMod[i] = mid[i]-direction[i];
				rightMod[i] = Math.max(rightMod[i],bounds[i][0]);
				rightMod[i] = Math.min(rightMod[i],bounds[i][1]);
			}
			
			leftscore = midscore;
			midscore = rightscore;
			rightscore = handle.getErrorForModel(rightMod);
			this.newModelFound(rightMod, rightscore);
		}

		
		//If we are not doing an adaptive powell, which 
		//decreases step size from large to small, then 
		//we include this attempt to rescale our step size
		//(either smaller or larger) as seems appropriate.
		//Basically, if it looks like we are on a linear segment
		//then we increase step size, and if we bracket a minima
		//then we decrease our step size
		//
		//note that we do not do this in the adaptive case because
		//in that case, we often want (at large step sizes) to 
		//step over local minima, so trying to adapt the scale
		//to bracket local minima is a bad idea in that case
		if(!adaptive)
		{
			if(leftscore <  midscore && leftscore < rightscore ||
			rightscore < midscore && rightscore < leftscore)
			{
				//If it looks like we are on a linear segment,
				//we increase the step size....
				double leftpred = midscore-(rightscore-midscore);
				if(Math.abs(leftpred-leftscore) < minchange) 
				{
					Debug.println("Looks like we are on a linear segment... increasing step size", Debug.INFO);
					for(int i =0; i < direction.length; i++)
						direction[i]*=2;
				}
			}
			else //not on a linear segment, and we bracket a minima
			{
				//if we've bracketed a minima, and the minima is far enough away,
				//we decrease our step size
				if(Math.abs(midscore - Math.min(leftscore, rightscore)) > minchange)
				{
					Debug.println("Bracketed minima...decreasing step size", Debug.INFO);
					for(int i =0; i < direction.length; i++)
						direction[i] /= 2;
				}
			}
		}
		
		

		
		
		//failed to bracket a minima, so just return the smallest
		//one
		if(leftscore < midscore && leftscore < rightscore) {
			besterr = leftscore;
			System.arraycopy(leftMod, 0, best, 0, best.length);
			return besterr;
		}
		else if(rightscore < midscore && rightscore < leftscore) {
			besterr = rightscore;
			System.arraycopy(rightMod, 0, best, 0, best.length);
			return besterr;
		}
		else { //bracketed a minima.
			besterr = midscore;
			System.arraycopy(mid, 0, best, 0, best.length);
		}
		
		
		//now that we have bracketed a minima, we find it.
		while(true)
		{	
			Debug.println("in linemin l,m,r "+leftscore+" "+midscore+" "+rightscore, Debug.INFO);
				
			if(Math.abs(leftscore-midscore) < minchange && Math.abs(rightscore-midscore) < minchange)
			{
				Debug.println("close enough to a min! (i.e on a plateua) ending linemin on this axis", Debug.INFO);
				break;
			}
			
			double ldist = Util.distance(mid, leftMod);
			double rdist = Util.distance(mid, rightMod);
			Debug.println("in linemin ldist, rdist "+ldist+" "+rdist, Debug.INFO);

			if((ldist+rdist) < precision) {
				Debug.println("close to maximum precision, ending linemin on this axis", Debug.INFO);
				break;
			}
			
			if(ldist > rdist) //look left
			{
				Util.midpoint(leftMod, mid, newtry);
				if(Util.equal(newtry, leftMod))
				{
					Debug.println("reached maximum precision along this axis....", Debug.IMPORTANT);
					break;
				}
				
				double newerr = handle.getErrorForModel(newtry);
				this.newModelFound(newtry, newerr);
				if(newerr < midscore) 
				{
					tmpa = rightMod;
					rightMod = mid;
					mid = newtry;

					rightscore = midscore;
					midscore = newerr;
				}
				else 
				{
					tmpa = leftMod;
					leftMod = newtry;

					leftscore = newerr;
				}
				newtry = tmpa;
			}
			else //look right
			{
				Util.midpoint(rightMod, mid, newtry);
				if(Util.equal(newtry, rightMod))
				{
					Debug.println("reached maximum precision along this axis....", Debug.IMPORTANT);
					break;
				}

				double newerr = handle.getErrorForModel(newtry);
				this.newModelFound(newtry, newerr);
				if(newerr < midscore) 
				{
					tmpa = leftMod;
					leftMod = mid;
					mid = newtry;

					leftscore = midscore;
					midscore = newerr;
				}
				else 
				{
					tmpa = rightMod;
					rightMod = newtry;

					rightscore = newerr;
				}
				newtry = tmpa;
			}
			
			if(midscore < besterr) 
			{
				besterr = midscore;
				for(int i =0; i < dim; i++)
					best[i] = mid[i];			
				Debug.println("in linemin -- found new best error at midpoint", Debug.INFO);
			}
		}	
		
		Debug.println("ERROR after minimization in that direction is "+besterr, Debug.INFO);
		return besterr;		
	}


	/** powell min will alter startModel
	 * 
	 * @param startModel Where to start. Note that this will be altered
	 * @param userAxes
	 * @param startModelErr
	 * @param handle
	 * @return
	 */
	public double powellMin(double[] startModel, 
		double[][] userAxes, double startModelErr, 
		rses.inverse.UserFunctionHandle handle)
	{
		Debug.println("Begining powell direction set minimization", Debug.IMPORTANT);
		int dim = startModel.length;
		double[][] axes = new double[dim][];
		
		for(int i =0; i < dim; i++)
			axes[i] = Util.copy(userAxes[i]);
			
		//now jumble up the axes, so that we go through them in a random order
		for(int i=0; i < dim; i++) 
		{
			int toMoveTo = (int) (Math.random()*dim);
			double[] tmpa = axes[toMoveTo];
			axes[toMoveTo] = axes[i];
			axes[i] = tmpa;
		}
			
		double err = startModelErr;
		double[] beforeMin = new double[dim];
		double beforeminerr;
		double errchange;
		double[] potentialNewAxis = new double[dim];
		double[] extrapolatedPoint = new double[dim];
		double[][] bounds = handle.getBoundsOnModelSpace();
		
		do
		{
			errchange = 0.0;
			int best = -1;
			double besterrimp = Double.NEGATIVE_INFINITY;
			System.arraycopy(startModel, 0, beforeMin, 0, dim);
			beforeminerr = err;
			
			for(int i = 0; i < dim; i++)
			{
				double newerr = lineMin(startModel, err, axes[i], handle);
				
				errchange = errchange + (err-newerr);
				if(err-newerr > besterrimp)
				{
					best = i;
					besterrimp = err-newerr;
				}
				err = newerr;
			}
		
			//Special case for 1D problems, we can stop here, because we know we are at the local minima
			if(dim == 1)
				break; 
			
			//work out the average direction we just went in
			//and save the current point
			for(int i =0; i < dim; i++) {
				potentialNewAxis[i] = startModel[i]-beforeMin[i];
				extrapolatedPoint[i] = 2*startModel[i]-beforeMin[i];
			}
			

			boolean inbounds = true;
			for(int i =0; i < extrapolatedPoint.length; i++) {
				if(extrapolatedPoint[i] < bounds[i][0] || extrapolatedPoint[i] > bounds[i][1]) 
				{ inbounds = false; break; }
			}
			
			//take a 'peek' along the new axis
			if(inbounds) {
				double extraperr = handle.getErrorForModel(extrapolatedPoint);
				this.newModelFound(extrapolatedPoint, extraperr);
				
				if(extraperr < err)
				{
					double t = 2*(beforeminerr-2*err+extraperr)*
						Math.pow(beforeminerr-err-besterrimp,2)
						-besterrimp*
						Math.pow(beforeminerr-extraperr,2);
				
					if(t < 0)
					{
						for(int i =0; i < dim; i++)
							axes[best][i] = potentialNewAxis[i];
			
						Debug.print("new direction in direction set is ", Debug.INFO);
						Debug.println(Util.arrayToString(axes[best]), Debug.INFO);
						Debug.println("(replaced direction with index "+best+")", Debug.EXTRA_INFO);
					}
					else	
						Debug.println("New direction is good, but bad as a new axis", Debug.INFO);
				}
				else {
					Debug.println("peeking along new axis shows it probably isnt good as a new axis", Debug.INFO);
					Debug.println("Still.... lets do a quick minimize along it anyway :-)", Debug.INFO);
				}
			}

			
			//now minimize along that axis
			double tmperr = lineMin(startModel, err, potentialNewAxis, handle);
			errchange += (err-tmperr);
			Debug.println("minimization along new axis improved by "+(err-tmperr), Debug.INFO);
			err = tmperr;
			
			Debug.println("completed iteration through all axes with improvement "+errchange+" misfit of improved model is "+tmperr, Debug.INFO);
			Debug.println("Best model so far: "+rses.util.Util.arrayToString(startModel), Debug.INFO);
		}
		while(errchange > minchange);
		
		Debug.println("FINISHED POWELLS METHOD DECENT -- change in misfit is "+(startModelErr-err)+" bestmisfit="+err, Debug.IMPORTANT);
		
		
		return err;
		
	}

}
