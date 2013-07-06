package rses.inverse.genetic;

import rses.ModelGenerator;
import rses.Debug;
import rses.inverse.UserFunctionHandle;



public class Organism extends rses.Model 
{
	protected double mutationProb;
	public double getMutationProb() { return mutationProb; }
	
	protected static java.util.Random rand = new java.util.Random();
	
	/** The default value of gauss_divide. When we are taking Gaussian 
	 * walks in parameter space, on average, we aim to take steps with a 
	 * mean size of: 
	 *    
	 *   (parameter_range/2)*gauss_divide
	 * 
	 *   So, each step would be     rand.nextGaussian()*(parameter_range*2)*gauss_divide;
	 */	
	protected double gauss_divide;
	public double getGaussDivide() { return gauss_divide; }
	
	
	public Organism breed(Organism partner, double[][] parambounds)
	{
		double[] dna1 = this.getModelParameters();
		double[] dna2 = partner.getModelParameters();
		if(dna1.length != dna2.length)
			throw new IllegalStateException("Attempt to breed Organisms of different species....");
		
		double[] child_dna = new double[dna1.length];
		
		double rnd = rand.nextDouble();
		double child_mutation_prob = rnd*partner.mutationProb+(1-rnd)*this.mutationProb;
		rnd = rand.nextDouble();
		double child_gauss = rnd*partner.gauss_divide + (1-rnd)*this.gauss_divide;
		
		//now change all the 'real' parameters
		for(int i = 0; i < child_dna.length; i++)
		{
			if(rand.nextDouble() < 0.5)
				child_dna[i] = partner.parameter[i];
			else
				child_dna[i] = this.parameter[i];

			if(rand.nextDouble() < child_mutation_prob)
			{
				double upr = parambounds[i][1];
				double lwr = parambounds[i][0];
				double range=upr-lwr;
				if(range == 0.0) {
					child_dna[i] = upr;
					continue;
				}

				double trydna = child_dna[i];
				//take a gaussian walk (but stay within bounds)
				do {
						  trydna = (child_dna[i] + rand.nextGaussian()*(range/2)*child_gauss);
				}
				while(trydna > upr || trydna < lwr);

				child_dna[i] = trydna;
			 }
		}

		//lastly, we change the meta parameters
		if(rand.nextDouble() < child_mutation_prob)
			child_gauss = rand.nextDouble()*child_gauss*2;
		if(rand.nextDouble() < child_mutation_prob)
			child_mutation_prob = rand.nextDouble()*2*child_mutation_prob;		

		Debug.println("child_gauss "+child_gauss+"      child_mutation "+child_mutation_prob, Debug.EXTRA_INFO);

		
		Organism result = new Organism(child_dna);
		
		result.mutationProb = child_mutation_prob;
		result.gauss_divide = child_gauss;
		return result;
	}

	
	
	public Organism(double[] params, double misfit)
	{
		super(params, misfit);
		this.mutationProb = rand.nextDouble();
		this.gauss_divide = rand.nextDouble();
	}

	public Organism(double[] params)
	{
		super(params);
		this.mutationProb = rand.nextDouble();
		this.gauss_divide = rand.nextDouble();
	}
	
	public Organism(rses.inverse.UserFunctionHandle h)
	{
		super(h);
		this.mutationProb = rand.nextDouble();
		this.gauss_divide = rand.nextDouble();
	}



	public String toString()
	{
		String modelString = super.toString();
		modelString += " MutationProb: "+this.mutationProb;
		modelString += " GaussRange: "+this.gauss_divide;
		return modelString;
	}




	public static ModelGenerator getChildGenerator(Organism parent1, Organism parent2, double[][] bounds)
	{
		return new OrganismGenerator(parent1, parent2, bounds);
	}


	
	public static OrganismFactory getFactory(UserFunctionHandle handle) {
		return new OrgFact(handle);
	}
	
}


final class OrgFact implements OrganismFactory
{
	UserFunctionHandle h;
	OrgFact(UserFunctionHandle h) { this.h = h;}
	
	public Organism getOrganism() { return new Organism(h); }
}






final class OrganismGenerator extends ModelGenerator
{
	private Organism p1;
	private Organism p2;
	private double[][] bounds;
	
	OrganismGenerator(Organism parent1, Organism parent2, double[][] bounds)
	{
		this.p1 = parent1;
		this.p2 = parent2;
		this.bounds = bounds;
	}
	
		
	protected rses.Model generateModel()
	{
		return p1.breed(p2, bounds);
	}
}




 
