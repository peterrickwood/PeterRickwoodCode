package rses.apps.sydneyenergy;
import java.io.*;
import java.util.*;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane.MaximizeAction;

import rses.Debug;
import rses.util.FileUtil;
import rses.util.Util;



/** This is the OLD Household Location Model code, that
 * uses the binomial distribution in the allocation.
 * It is (sort of) described in the saved version of 
 *  the paper Rickwood, Sambridge & Glazebrook
 *  called Rickwoodetal_ResidentialChoice_original_binomial.pdf
 *  
 *  This is what has been used in my PhD, but the actual main paper
 *  has moved a little away from this. For this, go and look at
 *  ResidentialModel2.java
 * 
 * @author peterr
 *
 */


public class ResidentialModel
{
	private String datadir = ".";
	private int NDWELL = 3; //either 3 or 4 dwelling types
	private int NHH = 6; //6 household types
	private int NINCOME = 6; //6 income ranges
	private int NTZ = 994; //# of travel zones (including missing ones)
	
	//The number of tranches per income bracket
	private int NTRANCHE = 5;
	
	//the minimum income level that we try and fit
	private int MININCOME = 0;
	
	//public static final String[] HHTYPES = {"single", "couplenokids", "singleparent", "couplewkidslt15", "couplewkidsgt15", "othergroup", "otherfamily", "other"};
	//public static final String[] DWELLTYPES = {"house", "semi", "lowriseunit", "unit"};

	private double[][][][] data; //format is [tz,hhtype,dwelltype,income,count]
	private double[][] dwellings; //dwellings[tz][dwelltype] == # of dwellings of that type
	private int[][] households; //households[income][hhtype] == # of households of that type
	private boolean[] validtzs; //which tzs are valid
	

	

	/** Reads in the households within each TZ from a file, and
	 * calculates household and dwelling information from that.
	 * 
	 * Goes with defaults for all the parameters.
	 * 
	 * @param path
	 * @throws IOException
	 */
	public ResidentialModel(String path, int maxtz, int nhh, int ndw, int ninc, int ntranche) throws IOException 
	{		
		this.datadir = path;
		this.NHH = nhh;
		this.NDWELL = ndw;
		this.NINCOME = ninc;
		this.NTZ = maxtz+1;
		this.NTRANCHE = ntranche;
		
		//read in households and their locations
		Debug.println("reading in household data file", Debug.IMPORTANT);
		double[][] tmpdata = readVectorsFromFile(new File(path, "hhbytz.dat"));
		Debug.println("Finished reading in household data file", Debug.IMPORTANT);
		
		validtzs = new boolean[NTZ];
		
		//construct dwellings data
		double[][] ddwellings = new double[NTZ][NDWELL];
		for(int i = 0; i < tmpdata.length; i++) {
			int tz = (int) Math.round(tmpdata[i][0]);
			int dwelltype = (int) Math.round(tmpdata[i][2]);
			ddwellings[tz][dwelltype] += tmpdata[i][4];
			if(!validtzs[tz] && ddwellings[tz][dwelltype] > 0.0) 
				validtzs[tz] = true;
		}
		dwellings = new double[NTZ][NDWELL];
		for(int i = 0; i < NTZ; i++)
			for(int j = 0; j < NDWELL; j++) 
				dwellings[i][j] = ddwellings[i][j];
		Debug.println("Constructed dwelling data", Debug.IMPORTANT);


		//construct household data
		double[][] dhouseholds = new double[NINCOME][NHH];
		for(int i = 0; i < tmpdata.length; i++) {
			int hhtype = (int) Math.round(tmpdata[i][1]);
			int ibrak = (int) Math.round(tmpdata[i][3]);
			dhouseholds[ibrak][hhtype] += tmpdata[i][4];
		}
		households = new int[NINCOME][NHH];
		for(int i = 0; i < NINCOME; i++)
			for(int j = 0; j < NHH; j++)
				households[i][j] = (int) Math.round(dhouseholds[i][j]);
		Debug.println("Constructed household data", Debug.IMPORTANT);

		
		Debug.println("Reprocessing placed household data", Debug.IMPORTANT);
		//now process the tmpdata into a format that
		//is more useful (multidimensional array)
		this.data = new double[NTZ][NHH][NDWELL][NINCOME];
		for(int i = 0; i < tmpdata.length; i++)
		{
			int tz = (int) Math.rint(tmpdata[i][0]);
			int hhtype = (int) Math.rint(tmpdata[i][1]);
			int dtype = (int) Math.rint(tmpdata[i][2]);
			int ibrak = (int) Math.rint(tmpdata[i][3]);
			double count = tmpdata[i][4];
			this.data[tz][hhtype][dtype][ibrak] += count;
		}
		Debug.println("Finished reprocessing placed household data. Done in constructor", Debug.IMPORTANT);

	}

	
	
	private double[][][] getInitialGuessOfProbabilities(File f) throws IOException
	{
		System.err.println("Getting inital probabilities from user input file... press RETURN to confirm");
		new BufferedReader(new InputStreamReader(System.in)).readLine();
		
		//NHH,NTZ,NDW
		double[][][] res = new double[NHH][NTZ][NDWELL];

		double[][] data = FileUtil.readVectorsFromFile(f);

		for(int tz = 0; tz < NTZ; tz++)
		{
			for(int hh=0; hh < NHH; hh++)
				if(!validtzs[tz]) 
					res[hh][tz] = null; //set to null to make sure we dont use it elsewhere	
		}

		for(int i = 0; i < data.length; i++)
		{
			int hh = (int) Math.round(data[i][0]);
			int tz = (int) Math.round(data[i][1]);
			int dw = (int) Math.round(data[i][2]);
			double val = data[i][3];
			res[hh][tz][dw] = val;
			if(val > 0 && dwellings[tz][dw] == 0.0) {
				System.err.println("You have specified a non-zero probability for a region/dwelltype combo that has no dwellings...(tz="+tz+" , dw="+dw+") .... setting to 0 instead");
				res[hh][tz][dw] = 0.0;
			}
		}
		
		for(int hh = 0; hh < NHH; hh++)
		{
			double count = 0.0;
			for(int tz = 0; tz < NTZ; tz++)
			{
				if(!validtzs[tz]) 
					continue; //not a valid tz, skip it

				for(int dw = 0; dw < NDWELL; dw++)
					count += res[hh][tz][dw];
			}
			
			//normalize
			for(int tz = 0; tz < NTZ; tz++)
				if(validtzs[tz])
					for(int dw = 0; dw < NDWELL; dw++) {
						res[hh][tz][dw] /= count;
						Debug.println("InitialGuessOfRegionProb "+hh+" "+tz+" "+dw+" "+res[hh][tz][dw], Debug.INFO);
					}
		}
	
		
		return res;
		
	}

	
	private double[][][] getInitialGuessOfProbabilities()
	throws IOException
	{
		//see if there is a file in the datadir that specified initial
		//probs
		if(new File(this.datadir, "initialguess.dat").exists())
			return getInitialGuessOfProbabilities(new File(this.datadir, "initialguess.dat"));
		
		
		
		//NHH,NTZ,NDW
		double[][][] res = new double[NHH][NTZ][NDWELL];
		
		for(int hh = 0; hh < NHH; hh++)
		{
			double count = 0.0;
			for(int tz = 0; tz < NTZ; tz++)
			{
				if(!validtzs[tz]) {
					res[hh][tz] = null; //set to null to make sure we dont use it elsewhere
					continue; //not a valid tz, skip it
				}
				
				for(int dw = 0; dw < NDWELL; dw++)
				{
					double wsum = 0.0;
					for(int inc = 1; inc < NINCOME; inc++)
						wsum += data[tz][hh][dw][inc]*(1.0/(NINCOME-inc));
						//wsum += data[tz][hh][dw][inc]*inc;
						//wsum += data[tz][hh][dw][inc];
					
					//assume a minimum level of desire for all combinations
					wsum = wsum + 1.0;

					wsum = 1.0; //HACK -- ignore all above. Everywhere is equally desirable 
					res[hh][tz][dw] = wsum*dwellings[tz][dw];
					
					count += res[hh][tz][dw];
				}
			}
			
			//normalize
			for(int tz = 0; tz < NTZ; tz++)
				if(validtzs[tz])
					for(int dw = 0; dw < NDWELL; dw++) {
						res[hh][tz][dw] /= count;
						Debug.println("InitialGuessOfRegionProb "+hh+" "+tz+" "+dw+" "+res[hh][tz][dw], Debug.INFO);
					}
		}
	
		
		return res;
		
	}
	
	
	
	

	
	
	//work out the probability distribution over tz/dwelltypes,
	//given desirability and counts for each tz/dwelltype
	private static PlacedHousehold2 placeHouseHold(int hhtype, int income, 
			double[][] tzbydwelldist, double[][] origdwellcounts,
			double[][] dwellcounts, int nzones, int ndwell)
	{		
		
		//this is where the household is actually allocated
		double[][] tzbydwellallocation = new double[nzones][ndwell];
		
		double sum = 0.0; //for normalizing afterwards
		for(int i = 0; i < nzones; i++)
		{
			//dont bother doing TZs that we dont have an estimate of
			//desire for. This can be either because the TZ doesnt
			//exist, or is outside the study area, or because
			//there werent enough familes of the right type in any
			//dwelling type in that TZ.
			//
			//whatever the reason, we just skip to the next TZ
			if(tzbydwelldist[i] == null) 
				continue;
			
			
			for(int d = 0; d < ndwell; d++)
			{
				//this is the unconstrained probability for the region
				double desire = tzbydwelldist[i][d];
				double nonnegdwellcounts = Math.max(0.0, dwellcounts[i][d]);
				
				//we actually want, though, the constrained probability 
				//for the region, which is proportional to 
				//the per-dwelling probability times the number of remaining
				//dwellings
				double desireperdwell = 0.0;
				if(origdwellcounts[i][d] > 0) desireperdwell = desire/origdwellcounts[i][d];
				desire = desireperdwell;
				
				desire *= nonnegdwellcounts;
				
				
				if(desire > 0.0 && desire > dwellcounts[i][d]) //need the Math.max because dwellcounts can be slightly negative
					throw new IllegalStateException("allocation > 0 when there is no stock!");
				sum += desire;
				tzbydwellallocation[i][d] = desire;				
			}
		}
		
		//we fail to place the household when every tz/dwelltype 
		//combination that we have an estimated desire for is
		//used up. 
		//
		//Currently, we just leave the households unallocated in this
		//case
		//TODO FIXME
		if(sum == 0.0) 
			return null;
			//throw new IllegalStateException("No dwellings left in placehousehold, so I cant place them");
		
		
		//now normalize. This normalization can result in allocations
		//that are greater than the available dwelling stock, but the
		//effect is insignificant
		for(int i = 0; i < nzones; i++)
			if(tzbydwelldist[i] != null)
				//in the case where it is null, no need to normalize, as its all 0.0
				for(int d = 0; d < ndwell; d++) 
					tzbydwellallocation[i][d] /= sum;
				
		
		PlacedHousehold2 phh2 = new PlacedHousehold2(hhtype, income, tzbydwellallocation);
		return phh2;
	}
	
	

	
	private static double[] logfactorial = null;
	
	
	//get the probability of numsucesses from trials trials, with a trialprob of trialprob
	public static double binomiallogprob(int trials, double trialprob, int numsuccess)
	{
		if(trials == 0 || trialprob == 0.0 || numsuccess > trials)
			throw new RuntimeException("You asked for the log-prob of a zero probability event... trials="+trials+"  p="+trialprob+" k="+numsuccess);
		
		if(logfactorial == null) { 
			logfactorial = new double[1024];
			for(int i = 1; i < logfactorial.length; i++)
				logfactorial[i] = logfactorial[i-1]+Math.log(i);
		}
		if(trials >= logfactorial.length) 
		{
			//use poisson approximation
			double np = trials*trialprob;
			double poisson = Math.log(np)*numsuccess + (-np) - logfactorial[numsuccess];
			return poisson;
		}
		
		double nchooseklogprob = logfactorial[trials]-logfactorial[trials-numsuccess]-logfactorial[numsuccess];
		double ksuccess = Math.log(trialprob)*numsuccess;
		double nminuskfail = Math.log(1-trialprob)*(trials-numsuccess);
		
		double np = trials*trialprob;
		//double poisson = Math.log(np)*numsuccess + (-np) - logfactorial[numsuccess];
		double binomial = nchooseklogprob+ksuccess+nminuskfail;
		//Debug.println("Binomial: "+binomial+"  Poisson: "+poisson, Debug.INFO);
		return binomial;
	}

	
	
	
	
	
	

	public double calcError(double[][][] probs)
	{
		double error = 0.0;
		
		//Traches basically expand the number of income brackets we have
		double[][] hhallocated = new double[NINCOME*NTRANCHE][NHH];
		
		//approximate # dwellings already allocated to higher-income
		//households already in each tz/dw combo
		double[][] dwellingsallocated = new double[NTZ][NDWELL];
		
		//We need to keep track of how much probability 
		//is lost through dwelling allocations throughout
		//all dwelling allocations in previous income brackets,
		//so that we can boost the next income bracket.
		double[] problost = new double[NHH];  //Initially zero
		
		
		for(int i = (NINCOME*NTRANCHE-1); i >= NTRANCHE-1; i--)
		{
			//as we step through tz/dw combos, we need to temorarily
			//boost probabilities for allocating the rest of this income 
			//braket, so the tz/dw combos still to be allocated always sum to 1.0
			double[] ibrakproblost = new double[NHH];
			
			//additional prob lost this iteration through allocation to
			//dwellings. Used to update problost at end of iteration
			//through all tz/dw combos
			double[] additionalproblost = new double[NHH];
			
			for(int tz = 0; tz < NTZ; tz++)
			{
				if(!validtzs[tz])
					continue; 
			
				for(int dw = 0; dw < NDWELL; dw++)
				{					
					//if there are no dwellings left, dont bother
					if(dwellings[tz][dw]-dwellingsallocated[tz][dw] <= 0.001) 
						continue;
								
					//keep track of the proportion of remaining dwellings allocated
					//this iteration to households in the same income bracket
					double startdwell = dwellings[tz][dw]-dwellingsallocated[tz][dw];
					
					for(int hh = 0; hh < NHH; hh++)
					{
						if(dwellings[tz][dw] == 0.0 && probs[hh][tz][dw] > 0.0)
							throw new RuntimeException("Non-zero probability for region/dw combo that has no dwellings!");

						//if there are no dwellings left in this
						//tz/dw combo, we still need to proceed, because 
						//we have to keep track of adjustments to 
						//probabilities
						
						
						//we need to boost for dwellings allocated in prior ibraks
						//this boosting makes sure the tz/dw probabilities
						//sum to 1.0 across all tz/dw combos
						double boostfact1 = 1/(1-problost[hh]);

						//AND for tz/dw combos already done in this ibrak sweep
						//this boosting makes sure it sums to 1.0 across all the
						//remaining tz/dw combos that we are still to look at
						double boostfact2 = 1/(1-ibrakproblost[hh]);

						//AND by other households already in this ibrak
						//this adjusts so that we take account of 
						//dwellings in this tz/dw combo already allocated 
						//to other households in the same income bracket.
						//So, for example, if we have lost N dwellings
						//in this tz/dw combo to other households, we
						//need to boost to account for the lost probability
						//associated with that loss of available dwellings
						double remainingdwellings = dwellings[tz][dw]-dwellingsallocated[tz][dw];
						double dwellingpropremaining = (remainingdwellings)/dwellings[tz][dw];
						double boostfact3 = 1/(1-(startdwell-remainingdwellings)/startdwell);
						
						double boostfact = boostfact1*boostfact2*boostfact3;
						
						//the actual number of households observed
						double actual = data[tz][hh][dw][i/NTRANCHE]/NTRANCHE;
						
						//number of households of this income/type still looking for a dwelling 
						double n = ((double) households[i/NTRANCHE][hh])/NTRANCHE-hhallocated[i][hh];
						
						double p = probs[hh][tz][dw]*dwellingpropremaining*boostfact;
						
						double pred = n*p;
						//System.err.println("TZ "+tz+" DW "+dw+" I "+i+" HH "+hh+" N "+n+" ACT "+actual+" NDWELL "+dwellings[tz][dw]+" HHINIBRAK "+households[i/NTRANCHE][hh]+" HHALLOC "+hhallocated[i][hh]+" DWPROP "+dwellingpropremaining+" BOOST123ALL "+boostfact1+" "+boostfact2+" "+boostfact3+" "+boostfact+" RAWPROBS "+probs[hh][tz][dw]+" P "+p+" PRED "+pred);

						
						if(p == 0.0 && actual > 0) //this should be impossible
							throw new IllegalStateException("Impossible case?");	
						if(p == 0.0 && actual == 0.0)
							continue; //probability 1 of observing no successes
						if(p > 1.0) {
							if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID))
								if((n-actual) > 1 && p > 1.01) //p > 1 means we arent adjusting our probabilities correctly
									throw new IllegalStateException("p > 1.0 !!! This should only happen due to rounding error,and even then, only in final allocation.... TZ "+tz+" DW "+dw+" I "+i+" HH "+hh+" N "+n+" ACT "+actual+" NDWELL "+dwellings[tz][dw]+" DWPROP "+dwellingpropremaining+" BOOST "+boostfact+" RAWPROBS "+probs[hh][tz][dw]+" P "+p+" PRED "+pred);
							p = 1.0;
						}
						
						//unless we are at the very last allocation in this
						//ibrak (which is forced, and so, by definition probability 1)
						//then we calculate the probability of observing this data
						if(n > 0.00001 && p <= 0.99999) 
						{
							//get the probability of observing 'actual'
							//given the postulated probability
							double blprob = binomiallogprob((int) Math.round(n), p, (int) Math.round(actual));
							error += blprob;
							
							if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID)) {
								if(Double.isNaN(error))
									throw new RuntimeException("GOT NaN as error! TZ "+tz+" DW "+dw+" I "+i+" HH "+hh+" N "+n+" ACT "+actual+" NDWELL "+dwellings[tz][dw]+" DWPROP "+dwellingpropremaining+" BOOST "+boostfact+" RAWPROBS "+probs[hh][tz][dw]+" P "+p+" PRED "+pred);
								if(Double.isInfinite(error))
									throw new RuntimeException("GOT Infinity as error! TZ "+tz+" DW "+dw+" I "+i+" HH "+hh+" N "+n+" ACT "+actual+" NDWELL "+dwellings[tz][dw]+" DWPROP "+dwellingpropremaining+" BOOST "+boostfact+" RAWPROBS "+probs[hh][tz][dw]+" P "+p+" PRED "+pred);
							}
						}
						
						//keep track of the probs we have already cycled through
						//for this income bracket and this hhtype. Need this to boost
						double dwpr = startdwell/dwellings[tz][dw];
						ibrakproblost[hh] += probs[hh][tz][dw]*dwpr*boostfact1;
						
						
						
						//keep track of the total probability lost due to 
						//dwelling allocations. This is a little tricky
						//because we need to boost only those households
						//that are allocated *after* the current batch.
						//
						//So
						// (1) we remember the probability lost by this
						//hhtype and prior household types (already allocated)
						//so that we can add it in the next incomebrak allocation
						//for(int hh2 = 0; hh2 <= hh; hh2++)
						//	additionalproblost[hh2] += probs[hh2][tz][dw]*(actual/dwellings[tz][dw]);
						//  AND
						// (2) all the household types still to be allocated
						// need to have their probabilities boosted in THIS iteration,
						// so we make the adjustment now.
						//for(int hh2 = hh+1; hh2 < NHH; hh2++)
						//	problost[hh2] += probs[hh2][tz][dw]*(actual/dwellings[tz][dw]);
						//
						//SCRAP THAT. TRY THIS INSTEAD
						for(int hh2 = 0; hh2 < NHH; hh2++)
							additionalproblost[hh2] += probs[hh2][tz][dw]*(actual/dwellings[tz][dw]);
						
						
						//and now deduct the number of dwellings actually allocated
						//to this income group
						dwellingsallocated[tz][dw] += actual;
						hhallocated[i][hh] += actual;
						
					} //end loop over HH
				} //end loop over DWELL
			} //end loop over TZ
			
			//add the probability lost in this iteration over income to
			//that which was lost in prior iterations
			for(int hh = 0; hh < NHH; hh++)
				problost[hh] += additionalproblost[hh];
			
		} //end (reverse) loop over income bracket
		
		
		return -error;  //(return -error so that larger errors are worse)	
	}
	
		
	
	
	public double calcError_1(double[][][] probs)
	{
		double error = 0.0;
		
		//Traches basically expand the number of income brackets we have
		double[][] hhallocated = new double[NINCOME*NTRANCHE][NHH];
		
		//approximate # dwellings already allocated to higher-income
		//households already in each tz/dw combo
		double[][] dwellingsallocated = new double[NTZ][NDWELL];
		
		//We need to keep track of how much probability 
		//is lost through dwelling allocations throughout
		//all dwelling allocations in previous income brackets,
		//so that we can boost the next income bracket.
		double[] problost = new double[NHH];  //Initially zero
		
		
		for(int i = (NINCOME*NTRANCHE-1); i > NTRANCHE-1; i--)
		{
			//as we step through tz/dw combos, we need to temorarily
			//boost probabilities for allocating the rest of this income 
			//braket, so the tz/dw combos still to be allocated always sum to 1.0
			double[] ibrakproblost = new double[NHH];
			
			//additional prob lost this iteration through allocation to
			//dwellings. Used to update problost at end of iteration
			//through all tz/dw combos
			double[] additionalproblost = new double[NHH];
			
			for(int tz = 0; tz < NTZ; tz++)
			{
				if(!validtzs[tz])
					continue; 
			
				for(int dw = 0; dw < NDWELL; dw++)
				{					
					if(dwellings[tz][dw]-dwellingsallocated[tz][dw] <= 0.001) 
						continue;
								
					//keep track of the proportion of remaining dwellings allocated
					//this iteration to households in the same income bracket
					double startdwell = dwellings[tz][dw]-dwellingsallocated[tz][dw];
					
					for(int hh = 0; hh < NHH; hh++)
					{
						if(dwellings[tz][dw] == 0.0 && probs[hh][tz][dw] > 0.0)
							throw new RuntimeException("Non-zero probability for region/dw combo that has no dwellings!");
						
						//if there are no dwellings left, dont bother
						if(dwellings[tz][dw]-dwellingsallocated[tz][dw] <= 0.001) 
							break; 
						
						//otherwise, dwellings left, so lets allocate them
						
						//we need to boost for dwellings allocated in prior ibraks
						//this boosting makes sure the tz/dw probabilities
						//sum to 1.0 across all tz/dw combos
						double boostfact = 1/(1-problost[hh]);

						//AND for tz/dw combos already done in this ibrak sweep
						//this boosting makes sure it sums to 1.0 across all the
						//remaining tz/dw combos that we are still to look at
						boostfact *= 1/(1-ibrakproblost[hh]);

						//AND by other households already in this ibrak
						//this adjusts so that we take account of 
						//dwellings in this tz/dw combo already allocated 
						//to other households in the same income bracket.
						//So, for example, if we have lost N dwellings
						//in this tz/dw combo to other households, we
						//need to boost to account for the lost probability
						//associated with that loss of available dwellings
						double remainingdwellings = dwellings[tz][dw]-dwellingsallocated[tz][dw];
						double dwellingpropremaining = (remainingdwellings)/dwellings[tz][dw];						
						boostfact *= 1/(1-(startdwell-remainingdwellings)/startdwell);
						
						//the actual number of households observed
						//(can be fractional because we work in tranches)
						double actual = data[tz][hh][dw][i/NTRANCHE]/NTRANCHE;
						
						//number of households of this income/type still looking for a dwelling
						double n = households[i/NTRANCHE][hh]/NTRANCHE-hhallocated[i][hh];
						
						double p = probs[hh][tz][dw]*dwellingpropremaining*boostfact;
						
						double pred = n*p;
						//System.err.println("TZ "+tz+" DW "+dw+" I "+i+" HH "+hh+" N "+n+" ACT "+actual+" NDWELL "+dwellings[tz][dw]+" DWPROP "+dwellingpropremaining+" BOOST "+boostfact+" RAWPROBS "+probs[hh][tz][dw]+" P "+p+" PRED "+pred);

						
						if(p == 0.0 && actual > 0) //this should be impossible
							throw new IllegalStateException("Impossible case?");	
						if(p == 0.0 && actual == 0.0)
							continue; //probability 1 of observing no successes
						if(p > 1.0) {
							if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID))
								if((n-actual) > 1 && p > 1.01) //p > 1 means we arent adjusting our probabilities correctly
									throw new IllegalStateException("p > 1.0 !!! This should only happen due to rounding error,and even then, only in final allocation.... TZ "+tz+" DW "+dw+" I "+i+" HH "+hh+" N "+n+" ACT "+actual+" NDWELL "+dwellings[tz][dw]+" DWPROP "+dwellingpropremaining+" BOOST "+boostfact+" RAWPROBS "+probs[hh][tz][dw]+" P "+p+" PRED "+pred);
							p = 1.0;
						}
						
						//unless we are at the very last allocation in this
						//ibrak (which is forced, and so, by definition probability 1)
						//then we calculate the probability of observing this data
						if((n-actual) >= 0.99999) 
						{
							//get the probability of observing 'actual'
							//given the postulated probability
							double blprob = binomiallogprob((int) Math.round(n), p, (int) Math.round(actual));
							error += blprob;
							
							if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID)) {
								if(Double.isNaN(error))
									throw new RuntimeException("GOT NaN as error! TZ "+tz+" DW "+dw+" I "+i+" HH "+hh+" N "+n+" ACT "+actual+" NDWELL "+dwellings[tz][dw]+" DWPROP "+dwellingpropremaining+" BOOST "+boostfact+" RAWPROBS "+probs[hh][tz][dw]+" P "+p+" PRED "+pred);
								if(Double.isInfinite(error))
									throw new RuntimeException("GOT Infinity as error! TZ "+tz+" DW "+dw+" I "+i+" HH "+hh+" N "+n+" ACT "+actual+" NDWELL "+dwellings[tz][dw]+" DWPROP "+dwellingpropremaining+" BOOST "+boostfact+" RAWPROBS "+probs[hh][tz][dw]+" P "+p+" PRED "+pred);
							}
						}
						
						//keep track of the probs we have already cycled through
						//for this income bracket and this hhtype. Need this to boost
						ibrakproblost[hh] += probs[hh][tz][dw];
						 
						
						
						//keep track of the total probability lost due to 
						//dwelling allocations. This is a little tricky
						//because we need to boost only those households
						//that are allocated *after* the current batch.
						//
						//So
						// (1) we remember the probability lost by this
						//hhtype and prior household types (already allocated)
						//so that we can add it in the next incomebrak allocation
						//for(int hh2 = 0; hh2 <= hh; hh2++)
						//	additionalproblost[hh2] += probs[hh2][tz][dw]*(actual/dwellings[tz][dw]);
						//  AND
						// (2) all the household types still to be allocated
						// need to have their probabilities boosted in THIS iteration,
						// so we make the adjustment now.
						//for(int hh2 = hh+1; hh2 < NHH; hh2++)
						//	problost[hh2] += probs[hh2][tz][dw]*(actual/dwellings[tz][dw]);
						//
						//SCRAP THAT. TRY THIS INSTEAD
						for(int hh2 = 0; hh2 < NHH; hh2++)
							additionalproblost[hh2] += probs[hh2][tz][dw]*(actual/dwellings[tz][dw]);
						
						
						//and now deduct the number of dwellings actually allocated
						//to this income group
						dwellingsallocated[tz][dw] += actual;
						hhallocated[i][hh] += actual;
						
					} //end loop over HH
				} //end loop over DWELL
			} //end loop over TZ
			
			//add the probability lost in this iteration over income to
			//that which was lost in prior iterations
			for(int hh = 0; hh < NHH; hh++)
				problost[hh] += additionalproblost[hh];
			
		} //end (reverse) loop over income bracket
		
		
		return -error;  //(return -error so that larger errors are worse)	
	}

	

	
	
	
	
	
	public double calcError_pretranching(double[][][] probs)
	{
		double error = 0.0;
		
		//the number of households allocated in each ibrak/hh category
		double[][] hhallocated = new double[NINCOME][NHH];
		
		//approximate # dwellings already allocated to higher-income
		//households already in each tz/dw combo
		double[][] dwellingsallocated = new double[NTZ][NDWELL];

		
		//We need to keep track of how much probability 
		//is lost through dwelling allocations throughout
		//all dwelling allocations in previous income brackets,
		//so that we can boost the next income bracket.
		double[] problost = new double[NHH];  //Initially zero
		
		
		for(int i = NINCOME-1; i > 0; i--)
		{
			//as we step through tz/dw combos, we need to temorarily
			//boost probabilities for allocating the rest of this income 
			//braket, so the tz/dw combos still to be allocated always sum to 1.0
			double[] ibrakproblost = new double[NHH];
			
			//additional prob lost this iteration through allocation to
			//dwellings. Used to update problost at end of iteration
			double[] additionalproblost = new double[NHH];
			
			for(int tz = 0; tz < NTZ; tz++)
			{
				if(!validtzs[tz])
					continue; 
			
				for(int dw = 0; dw < NDWELL; dw++)
				{
					if(dwellings[tz][dw] == 0.0) 
						continue;
									
					for(int hh = 0; hh < NHH; hh++)
					{						
						//we need to boost for dwellings allocated in prior ibraks
						double boostfact = 1/(1-problost[hh]);
						//AND for tz/dw combos already done in this ibrak sweep
						boostfact *= 1/(1-ibrakproblost[hh]);
						
						double actual = data[tz][hh][dw][i];
						
						double n = households[i][hh]-hhallocated[i][hh];
						double dwellingpropremaining = (dwellings[tz][dw]-dwellingsallocated[tz][dw])/dwellings[tz][dw];
						
						if(dwellingpropremaining < 0.0) {
							if(dwellingpropremaining < -0.00000001) 
								throw new IllegalStateException("dwpropremaining (tz/dw/hh/i): "+tz+" "+dw+" "+hh+" "+i+" :: "+dwellingpropremaining);
							else 
								dwellingpropremaining = 0.0;
						}

						double p = probs[hh][tz][dw]*dwellingpropremaining*boostfact;

						double pred = n*p;
						System.err.println("TZ "+tz+" DW "+dw+" I "+i+" HH "+hh+" N "+n+" ACT "+actual+" NDWELL "+dwellings[tz][dw]+" DWPROP "+dwellingpropremaining+" BOOST "+boostfact+" RAWPROBS "+probs[hh][tz][dw]+" P "+p+" PRED "+pred);

						
						if(p == 0.0 && actual > 0) //this should be impossible
							throw new IllegalStateException("Impossible case?");	
						if(p == 0.0 && actual == 0.0)
							continue; //probability 1 of observing no successes

						//unless we are at the very last allocation in this
						//ibrak (which is forced, and so, by definitionm probability 1
						//then we calculate the probability of observing this data
						if((int) Math.round(n-actual) > 0) 
						{
							//get the probability of observing 'actual'
							//given the postulated probability
							error += binomiallogprob((int) Math.round(n), p, (int) Math.round(actual));
							System.err.println("ERROR NOW "+error);
						}
						
						//keep track of the probs we have already cycled through
						//for this income bracket and this hhtype. Need this to boost
						ibrakproblost[hh] += probs[hh][tz][dw];
						
						//keep track of the total probability lost due to 
						//dwelling allocations. This is a little tricky
						//because we need to boost only those households
						//that are allocated *after* the current batch.
						//
						//So
						// (1) we remember the probability lost by this
						//hhtype and prior household types (already allocated)
						//so that we can add it in the next incomebrak allocation
						for(int hh2 = 0; hh2 <= hh; hh2++)
							additionalproblost[hh2] += probs[hh2][tz][dw]*(actual/dwellings[tz][dw]);
						//  AND
						// (2) all the household types still to be allocated
						// need to have their probabilities boosted in THIS iteration,
						// so we make the adjustment now.
						for(int hh2 = hh+1; hh2 < NHH; hh2++)
							problost[hh2] += probs[hh2][tz][dw]*(actual/dwellings[tz][dw]);
						
						
						//and now deduct the number of dwellings actually allocated
						//to this income group
						dwellingsallocated[tz][dw] += actual;
						hhallocated[i][hh] += actual;
						
					} //end loop over HH
				} //end loop over DWELL
			} //end loop over TZ
			
			//add the probability lost in this iteration over income to
			//that which was lost in prior iterations
			for(int hh = 0; hh < NHH; hh++)
				problost[hh] += additionalproblost[hh];
			
		} //end (reverse) loop over income bracket
		
		return -error;  //(return -error so that larger errors are worse)	
	}

	
	
	
	
	
	
	
	
	

	public double calcError_orig(double[][][][] dist, boolean printallocation)
	{
		//OK, we've placed all households, and counted up
		//where, and in what dwelling type, everyone is
		//living in. So now we can calculate the error.
		double error = 0.0;
		for(int tz = 0; tz < NTZ; tz++)
			for(int dw = 0; dw < NDWELL; dw++)
				for(int hh = 0; hh < NHH; hh++)
				{
					double wpredsum = 0.0;
					double wactsum = 0.0;
					for(int i = MININCOME; i < NINCOME; i++)
					{
						double actual = data[tz][hh][dw][i];
						double pred = dist[tz][hh][dw][i];
						wpredsum += pred*(1.0/(NINCOME-i));
						wactsum += actual*(1.0/(NINCOME-i));
						double diff = actual-pred;
						if(printallocation)
						{
							Debug.println("For tz/dw/hh/i "+tz+"/"+dw+"/"+hh+"/"+i+"  pred/actual/diff is "+pred+"/"+actual+"/"+diff, Debug.IMPORTANT);
							Debug.println("MODELPLACEMENT "+tz+" "+dw+" "+hh+" "+i+" "+pred, Debug.IMPORTANT);
						}
						//error += errtoadd;
					}
					double diff = wactsum-wpredsum;
					double errtoadd = diff*diff;
					error += errtoadd;
				}
		
		return error;	
	}
	

	
	/** Calculate the distribution of households into dwellings
	 * 
	 * @param desire  The desire distribution, which must already have been calculated
	 * @param origdwellcounts Dwelling stock
	 * @param householdstodistribute Households to place in that stock
	 * @param numzones Number of zones
	 * @param numdwell Number of dwelling types
	 * @param numhh Number of households
	 * @param numibrackets Number of income brackets  
	 * @return The distribution of households into zones/dwellings, in the format
	 *         result[zones][hhtype][dwelltype][income]
	 */
	public static double[][][][] distributeHouseholds(
			double[][][] desire, double[][] origdwellcounts,
			int[][] householdstodistribute, int numzones, int numdwell,
			int numhh, int numibrackets)
	{
		return distributeHouseholds(desire, origdwellcounts, householdstodistribute, 
				numzones, numdwell, numhh, numibrackets, 0);
	}
	
	
	/** Calculate the distribution of households into dwellings
	 * 
	 * @param desire  The desire distribution, which must alreday have been calculated
	 * @param origdwellcounts Dwelling stock
	 * @param householdstodistribute Households to place in that stock
	 * @param numzones Number of zones
	 * @param numdwell Number of dwelling types
	 * @param numhh Number of households
	 * @param numibrackets Number of income brackets 
	 * @param minincome Minimum income to include in 
	 * @return
	 */
	public static double[][][][] distributeHouseholds(
			double[][][] desire, double[][] origdwellcounts,
			int[][] householdstodistribute, int numzones, int numdwell,
			int numhh, int numibrackets, int minincome)
	{
		//make a copy of the dwelling counts
		double[][] dwellcounts = new double[numzones][numdwell];
		double totaldwellings = 0;
		for(int i = 0; i < numzones; i++)
			for(int j = 0; j < numdwell; j++) {
				dwellcounts[i][j] = origdwellcounts[i][j];
				totaldwellings += dwellcounts[i][j];
			}
		Debug.println("Total dwelling stock in household sorting comprises of "+totaldwellings+" dwellings", Debug.IMPORTANT);
		
		
		//format is [tz][hhtype][dwelltype][income] == count
		double[][][][] newdata = new double[numzones][numhh][numdwell][numibrackets];
		
		
		//there is no need to redo calculations until we start
		//running out of dwellings. It is only when a new 
		//tz/dwelltype category runs out that we need to
		//recalculate these
		//FORGET THIS CACHING BUSINESS... IT DOESNT WORK BECAUSE WE 
		//NEED TO RENORMALIZE PROBABILITIES ANYWAY
		//PlacedHousehold2[] cached = new PlacedHousehold2[numhh];
		
		
		int totalhh = 0;
		//ok, now go through in income brackets and sort
		int mininc = minincome;
		for(int i = numibrackets-1; i >= mininc; i--)
		{
			Debug.println("In getErrorForModel()... doing income bracket "+i, Debug.IMPORTANT);
			ArrayList hhonthisincome = new ArrayList();
			for(int h = 0; h < numhh; h++)
			{
				int[] hharr = new int[] {h, i}; //we can just reuse this
				int count = householdstodistribute[i][h];
				for(int j = 0; j < count; j++)
					hhonthisincome.add(hharr);
			}
			
			//jumble up into random order
			Object[] arr = hhonthisincome.toArray();
			arr = Util.jumble(arr);
			Debug.println("In income bracket "+i+" finished shuffling households", Debug.IMPORTANT);

			//and now sort them into travelzones and dwelltypes
			for(int j = 0; j < arr.length; j++, totalhh++)
			{
				//Debug.println("Doing household #"+j+" in this income bracket", Debug.IMPORTANT);
				if(totalhh+1 > totaldwellings)
				{
					Debug.println("Stopping sorting households at "+totalhh+", because I've run out of dwellings.", Debug.IMPORTANT);
					Debug.println("There are "+(arr.length-j)+" (homeless) households left to place in this income bracket "+i, Debug.IMPORTANT);
					break;
				}
							
				int[] elem = (int[]) arr[j];
				int income = elem[1];
				int hhtype = elem[0];
		
				//place household
				//
				//Until we hit at least some constraint, we dont actually need
				//to redo the calculation for each hhtype, we can just use the last one
				//OH YES WE DO..... NEED TO RENORMALIZE...
				PlacedHousehold2 res = null; //cached[hhtype];
				if(res == null) { 
					res = placeHouseHold(hhtype, income, desire[hhtype], origdwellcounts,
							dwellcounts, numzones, numdwell);
					//cached[hhtype] = res;
				}
				
				//income does not have to match, because we may have a cached
				//household of an old type.
				if(res != null)
				{
					if(res.hhtype != hhtype)
						throw new IllegalStateException("returned/placed household has wrong type or income!");
					if(res.ibrak != income)
						res.ibrak = income; //old (cached) household has wrong income. Correct it.
				}
				
					
				//Debug.println("Placed household, updating distributions", Debug.IMPORTANT);
				//update residential distribution and update dwellcounts
				boolean invalidatecache = false;
				if(res != null) for(int tz = 0; tz < numzones; tz++) 
				{
					for(int dw = 0; dw < numdwell; dw++)
					{
						newdata[tz][hhtype][dw][income] += res.tzbydwelldist[tz][dw];
						
						//check if this household causes us to run out of a tz/dwelltype. If so
						//we invalidate the cache
						if(res.tzbydwelldist[tz][dw] > 0.0 && dwellcounts[tz][dw] <= res.tzbydwelldist[tz][dw])
							invalidatecache = true;
						
						//this can result in a (small) negative dwellcount
						dwellcounts[tz][dw] -= res.tzbydwelldist[tz][dw];
					}
				}
				
				//if(invalidatecache) {
					//Debug.println("Invalidating cache at hh # "+totalhh, Debug.IMPORTANT);
				//	cached = new PlacedHousehold2[numhh];
				//}
				
				if(totalhh % 20000 == 0) {
					Debug.println("Done "+totalhh+" households in sorting", Debug.IMPORTANT);
				}
			}//finished sorting households in this income bracket
		}//finished all income brackets
		
		return newdata;
	}


	private double[][][] copy(double[][][] arr)
	{
		double[][][] res = new double[NHH][NTZ][];
		for(int hh = 0; hh < NHH; hh++)
		{
			for(int tz = 0; tz < NTZ; tz++)
			{
				if(arr[hh][tz] != null)
				{
					res[hh][tz] = new double[NDWELL];
					for(int dw = 0; dw < NDWELL; dw++)
					{
						res[hh][tz][dw] = arr[hh][tz][dw];
					}
				}
			}
		}
		
		return res;
	}

	

	
	
		
	
	
	
	
	
	
	
	
	
	private void cleverOptimize() throws IOException
	{
		
		//have an initial stab. REM: we are working with region probabilities
		//NOT dwelling probabilities, so we need to remember this throughout.
		double[][][] desire = this.getInitialGuessOfProbabilities();
		double error = calcError(desire);
		double errorimp = error; //improvement in error
		
		Debug.println("Before starting, error of initial guess is "+error, Debug.IMPORTANT);
		//double error2 = calcError(hhcounts);
		//Debug.println("Duplicate run produced error for same model of "+error2+" vs "+error, Debug.IMPORTANT);
		
		
		
		//now optimize, forever. We optimize by just bumping up or
		//down the probabilities a little bit
		int iteration = 0;
		double MINIMP = 1.0;
		while(errorimp > MINIMP)
		{
			iteration++;
			errorimp = 0.0;
			
			//now go through and fiddle with the probabilities
				
			for(int tz = 0; tz < NTZ; tz++)
			{
				if(!validtzs[tz])
					continue;

				for(int hh = 0; hh < NHH; hh++)
				{				
					int lastdw = -1;
					for(int dw = 0; dw < NDWELL; dw++)
					{
						if(dwellings[tz][dw] <= 0) {
							if(desire[hh][tz][dw] > 0) throw new RuntimeException("Non-zero probability for tz/dw commbo with 0 dwellings....");
							else continue; //zero dwellings means zero prob, so dont bother adjusting
						}
						
						int updown = 1; //always try up first
						if(lastdw == dw) updown = -1; //unless we are repeating 
						
						lastdw = dw;
						double startupdatemul = 0.01;
						double updatemul = startupdatemul;
					
						while(updatemul < 1.0) //dont allow steps that are too big
						{
							double desiredelta = updown*updatemul*desire[hh][tz][dw];
							if(Math.abs(desiredelta) == 0.0) throw new IllegalStateException("Trying to adjust a zero probability! tz "+tz+" dw "+dw+" hh "+hh+" desire "+desire[hh][tz][dw]);
							
							desire[hh][tz][dw] += desiredelta;
					
							//now adjust (across this hh only) so that probabilities sum to 1
							for(int tz2 = 0; tz2 < NTZ; tz2++)
								if(validtzs[tz2])
									for(int dw2 = 0; dw2 < NDWELL; dw2++)
										desire[hh][tz2][dw2] /= 1+desiredelta; 
					
							//Debug.println("calling calcerr "+hh+" "+tz+" "+dw, Debug.INFO);
							double err = calcError(desire);
					
							if(err >= error) //its not a better fit.
							{
								//adjust probabilities back
								for(int tz2 = 0; tz2 < NTZ; tz2++)
									if(validtzs[tz2])
										for(int dw2 = 0; dw2 < NDWELL; dw2++)
											desire[hh][tz2][dw2] *= 1+desiredelta;
								desire[hh][tz][dw] -= desiredelta;
								
								//now, if we were trying up, try down instead
								if(updown == 1 && updatemul == startupdatemul) 
									dw--; //redo this dwelltype with a downbump
								
								break;
							}
							else { //it *is* an improvement
								errorimp += error-err;
								error = err;
								Debug.println("A "+updown+" bump with updatemul "+updatemul+" for hh "+hh+" in tz "+tz+" dw "+dw+" got error "+err, Debug.INFO);
								//go for gold
								updatemul = updatemul*2.0+0.01;
							}
						}
					}
				}
			}
			
			Debug.println("Model has error "+error, Debug.IMPORTANT);
			Debug.println("Improvement in error from previous best is "+errorimp, Debug.IMPORTANT);
			
			//DEBUG print out the model after each iteration
			System.out.println("REGION AND DWELL PROBABILITY RESULTS AT END OF ITERATION "+iteration);
			for(int hh =0; hh < NHH; hh++)
				for(int tz=0; tz < NTZ; tz++)
					if(validtzs[tz])
						for(int dw=0; dw < NDWELL; dw++) 
						{
							System.out.println("IT"+iteration+" REGIONPROB "+hh+" "+tz+" "+dw+" "+desire[hh][tz][dw]);
							if(dwellings[tz][dw] <= 0.0)
								System.out.println("IT"+iteration+" DWELLPROB "+hh+" "+tz+" "+dw+" 0.0");
							else 
								System.out.println("IT"+iteration+" DWELLPROB "+hh+" "+tz+" "+dw+" "+desire[hh][tz][dw]/dwellings[tz][dw]);
						}

		}
			
		
		//print out the estimated desire for each tz/dw combo, for each hh type
		for(int hh = 0; hh < NHH; hh++)
		{
			double desiresum = 0.0;
			for(int tz = 0; tz < NTZ; tz++)
			{
				if(desire[hh][tz] == null)
					continue;
				
				for(int dw = 0; dw < NDWELL; dw++)
				{
					if(dwellings[tz][dw] <= 0.0)
						System.out.println("FINAL DWELLPROB "+hh+" "+tz+" "+dw+" 0.0");
					else 
						System.out.println("FINAL DWELLPROB "+hh+" "+tz+" "+dw+" "+desire[hh][tz][dw]/dwellings[tz][dw]);
					Debug.println("MODELDESIRE "+tz+" "+hh+" "+dw+" "+desire[hh][tz][dw], Debug.IMPORTANT);
					desiresum += desire[hh][tz][dw];
				}
			}
			
			Debug.println("Desire sum is for hh type "+hh+" is "+desiresum, Debug.IMPORTANT);
		}
		
		//work out the distribution arising from the best model, including
		//all the low income households that may not contribute to the
		//weighting function
		double[][][][] hhcounts = distributeHouseholds(desire, dwellings, households, NTZ, NDWELL, NHH, NINCOME, 0);
		
		//now print out the actual allocation
		calcError_orig(hhcounts, true);
		
		
	}

	
	
	
	
	
	
	
	
	private void cleverOptimize_orig() throws IOException
	{
		
		//have an initial stab
		double[][][] desire = this.getInitialGuessOfProbabilities();
		double[][][] bestdesiresofar = null;
		double[][][][] besthhcountssofar = null;
		double[][][][] hhcounts = distributeHouseholds(desire, dwellings, households, NTZ, NDWELL, NHH, NINCOME, MININCOME);
		double error = calcError_orig(hhcounts, false);
		double errorimp = error; //improvement in error
		
		Debug.println("Before starting, error of initial guess is "+error, Debug.IMPORTANT);
		//double error2 = calcError(hhcounts);
		//Debug.println("Duplicate run produced error for same model of "+error2+" vs "+error, Debug.IMPORTANT);
		
		double updatemul = 0.05;

		
		
		
		//now optimize, forever
		while(errorimp > 0.0)
		{
			bestdesiresofar = copy(desire);
			besthhcountssofar = hhcounts; 
			
			//now go through and fiddle with the probabilities
			for(int hh = 0; hh < NHH; hh++)
			{
				double desiresum = 0.0;
				for(int tz = 0; tz < NTZ; tz++)
				{
					for(int dw = 0; dw < NDWELL; dw++)
					{
						double predhh = 0.0;
						double acthh = 0.0;
						for(int i = MININCOME; i < NINCOME; i++)
						{
							predhh += hhcounts[tz][hh][dw][i]; //*(1.0/(NINCOME-i));
							acthh += data[tz][hh][dw][i]; //*(1.0/(NINCOME-i));
						}
							
						//how much to modify desire by?
						double newval = updatemul*acthh*(desire[hh][tz][dw]/(predhh+1))+(1-updatemul)*desire[hh][tz][dw];
						
						desire[hh][tz][dw] = newval;
						desiresum += newval;
					}
				}
				for(int tz = 0; tz < NTZ; tz++)
					for(int dw = 0; dw < NDWELL; dw++)
						desire[hh][tz][dw] /= desiresum;
			}
			
			//ok, we have our new estimates, so lets obtain a new error
			//Hopefully it is better than before
			hhcounts = distributeHouseholds(desire, dwellings, households, NTZ, NDWELL, NHH, NINCOME, MININCOME);
			double newerr = this.calcError_orig(hhcounts, false);
			errorimp = error-newerr;
			error = newerr;
			Debug.println("Model has error "+error, Debug.IMPORTANT);
			Debug.println("Improvement in error from previous best is "+errorimp, Debug.IMPORTANT);
		}
		
		
		desire = bestdesiresofar;
		hhcounts = besthhcountssofar;
		
		//print out the estimated desire for each tz/dw combo, for each hh type
		for(int hh = 0; hh < NHH; hh++)
		{
			double desiresum = 0.0;
			for(int tz = 0; tz < NTZ; tz++)
			{
				if(desire[hh][tz] == null)
					continue;
				
				for(int dw = 0; dw < NDWELL; dw++)
				{
					Debug.println("MODELDESIRE "+tz+" "+hh+" "+dw+" "+desire[hh][tz][dw], Debug.IMPORTANT);
					desiresum += desire[hh][tz][dw];
				}
			}
			
			Debug.println("Desire sum is for hh type "+hh+" is "+desiresum, Debug.IMPORTANT);
		}
		
		//work out the distribution arising from the best model, including
		//all the low income households that may not contribute to the
		//weighting function
		hhcounts = distributeHouseholds(desire, dwellings, households, NTZ, NDWELL, NHH, NINCOME, 0);
		this.calcError_orig(hhcounts, true);
		
	}
	
	
	
	
	
	public static double[][] readVectorsFromFile(File f) throws IOException
	{
		return readVectorsFromFile(f, false);
	}
	

	public static double[][] readVectorsFromFile(File f, boolean allowjagged) throws IOException
	{
		ArrayList list = new ArrayList();
		int veclength = -1;
		BufferedReader rdr = new BufferedReader(new FileReader(f)); 
		for(String line = rdr.readLine(); line != null; line = rdr.readLine())
		{
			if(line.startsWith("#")) //comment lines
				continue;
			String[] words = line.split(" ");
			
			//first time through, we remember the vector length
			if(veclength == -1)
				veclength = words.length;
			
			if(words.length != veclength)
				throw new IOException("Invalid file format... line: "+line);
			
			double[] vect = new double[veclength];
			for(int i = 0; i < veclength; i++) 
			{
				try { vect[i] = Double.parseDouble(words[i]); }
				catch(NumberFormatException e) { throw new IOException("Invalid file format... expected a number on line: "+line); }
			}
			list.add(vect);
			//System.out.println("Got vector");
			
			if(allowjagged)
				veclength = -1; //reset veclength
		}
		if(list.size() == 0)
			throw new IOException("File contains no data");
		double[][] res = new double[list.size()][];
		for(int i =0; i < res.length; i++)
			res[i] = (double[]) list.get(i);
		return res;
	}
	


	
	public static void main(String[] args) throws Exception
	{
		Debug.setVerbosityLevel(Debug.INFO);
		Debug.setDebugLevel(Debug.MAX_PARANOIA);
		
		Debug.println("REM: usage is: ", Debug.IMPORTANT);
		Debug.println("      arg1 == directory where data files live ", Debug.IMPORTANT);
		Debug.println("      arg2 == number of hh types", Debug.IMPORTANT);
		Debug.println("      arg3 == number of dwelltypes", Debug.IMPORTANT);
		Debug.println("      arg4 == number of income brackets", Debug.IMPORTANT);
		Debug.println("      arg5 == MAXIMUM zone number", Debug.IMPORTANT);
		Debug.println("      arg6 == number of tranches", Debug.IMPORTANT);
		
		//ResidentialModel resmod = new ResidentialModel("/home/peterr/papers/authoredpapers/predictLocationFromCensus");
		int nhh = Integer.parseInt(args[1]);
		int ndwell = Integer.parseInt(args[2]);
		int ninc  = Integer.parseInt(args[3]);
		int maxtz = Integer.parseInt(args[4]);
		int ntranche = Integer.parseInt(args[5]);
		ResidentialModel resmod = new ResidentialModel(args[0], maxtz, nhh, ndwell, ninc, ntranche);
		
		resmod.cleverOptimize();
		
		//get an idea of how much the misfit for an identical
		//distribution will vary -- i.e. what the random component
		//to the misfit function is
		//Model m = Model.readFromUserFile(new File(args[0]), resmod.getDimensionOfModelSpace());
		
		//double mf1 = resmod.getErrorForModel(m.getModelParameters());
		//double mf2 = resmod.getErrorForModel(m.getModelParameters());
		
		//System.out.println("effect of random orderingn on misfit is "+mf1+" -- "+mf2);
		
		
		
		
	}


}



class PlacedHousehold2 {
	int hhtype;
	int ibrak;
	double[][] tzbydwelldist;
	int dwelltype;
	
	public PlacedHousehold2(int hh, int i, double[][] tzbydw) {
		this.hhtype = hh;
		this.ibrak = i;
		this.tzbydwelldist = tzbydw;
	}
}

