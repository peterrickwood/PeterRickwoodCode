




package rses.inverse.mcmc;

import java.io.Serializable;




public interface MCMCModelGenerationMethods extends Serializable
{
	public MCMC_Model generateNewModel(MCMC_Model current, java.util.Random rand, 
			double[][] bounds, double[] stddevs);
	
	
	public boolean acceptChange(MCMC_Model orig, MCMC_Model newmod, java.util.Random rand);
}