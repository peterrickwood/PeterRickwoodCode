package rses.apps.sydneyenergy;
import java.io.*;
import java.util.*;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane.MaximizeAction;

import rses.Debug;
import rses.util.FileUtil;
import rses.util.Util;



/** This is the main current Household Location Model code, that
 *  implements what is in the paper Rickwood & Sambridge.
 * 
 * @author peterr
 *
 */


public class ResidentialModel2
{
	private String datadir = ".";
	private int NDWELL = -1; //# dwelling types
	private int NHH = -1; //# household types
	private int NINCOME = -1; //# income ranges
	private int NTZ = -1; //# of travel zones (including missing ones)
	
	//The number of tranches per income bracket
	private int NTRANCHE = -1;
	
	//public static final String[] HHTYPES = {"single", "couplenokids", "singleparent", "couplewkidslt15", "couplewkidsgt15", "othergroup", "otherfamily", "other"};
	//public static final String[] DWELLTYPES = {"house", "semi", "lowriseunit", "unit"};

	private int[][][][] data; //format is [tz,hhtype,dwelltype,income,count]
	private int[][] dwellings; //dwellings[tz][dwelltype] == # of dwellings of that type
	private int[][] households; //households[income][hhtype] == # of households of that type
	private int[][][] householdsInDwellingsCount; //households[hhtype][tz][dw] == # of hh of this type in this dw/tz combo
	private boolean[] validtzs; //which tzs are valid
	
	//private double minp = 0.0;

	

	//public ResidentialModel2(String path, int maxtz, int nhh, int ndw, int ninc, int ntranche) throws IOException 
	//{
	//	this(path, maxtz, nhh, ndw, ninc, ntranche, 0.0);
	//}
	
	/** Reads in the households within each TZ from a file, and
	 * calculates household and dwelling information from that.
	 * 
	 * Goes with defaults for all the parameters.
	 * 
	 * @param path
	 * @throws IOException
	 */
	public ResidentialModel2(String path, int maxtz, int nhh, int ndw, int ninc, int ntranche/*, double minprob*/) throws IOException 
	{		
		Debug.setVerbosityLevel(Debug.INFO);
		this.datadir = path;
		this.NHH = nhh;
		this.NDWELL = ndw;
		this.NINCOME = ninc;
		this.NTZ = maxtz+1;
		this.NTRANCHE = ntranche;
		//this.minp = minprob;
		
		//read in households and their locations
		Debug.println("reading in household data file", Debug.IMPORTANT);
		int[][] tmpdata = FileUtil.readIntVectorsFromFile(new File(path, "hhbytz.dat"), false);
		Debug.println("Finished reading in household data file", Debug.IMPORTANT);
		
		validtzs = new boolean[NTZ];
		
		//construct dwellings data
		this.dwellings = new int[NTZ][NDWELL];
		for(int i = 0; i < tmpdata.length; i++) 
		{
			int tz = tmpdata[i][0];
			int dwelltype = tmpdata[i][2];
			dwellings[tz][dwelltype] += tmpdata[i][4];
			if(!validtzs[tz] && dwellings[tz][dwelltype] > 0.0) 
				validtzs[tz] = true;
		}
		Debug.println("Constructed dwelling data", Debug.IMPORTANT);
		for(int z = 0; z < NTZ; z++)
		{
			for(int d = 0; d < NDWELL; d++)
				Debug.println("DWELLCOUNT "+z+" "+d+" = "+dwellings[z][d], Debug.INFO);
		}
		

		//construct household data
		this.households = new int[NINCOME][NHH];
		for(int i = 0; i < tmpdata.length; i++) {
			int hhtype = (int) Math.round(tmpdata[i][1]);
			int ibrak = (int) Math.round(tmpdata[i][3]);
			households[ibrak][hhtype] += tmpdata[i][4];
		}
		Debug.println("Constructed household data", Debug.IMPORTANT);

		
		Debug.println("Reprocessing placed household data", Debug.IMPORTANT);
		//now process the tmpdata into a format that
		//is more useful (multidimensional array)
		this.data = new int[NTZ][NHH][NDWELL][NINCOME];
		this.householdsInDwellingsCount = new int[NHH][NTZ][NDWELL];
		for(int i = 0; i < tmpdata.length; i++)
		{
			int tz = tmpdata[i][0];
			int hhtype = tmpdata[i][1];
			int dtype = tmpdata[i][2];
			int ibrak = tmpdata[i][3];
			int count = tmpdata[i][4];
			this.data[tz][hhtype][dtype][ibrak] += count;
			this.householdsInDwellingsCount[hhtype][tz][dtype] += count;
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

	/** Just gives something close to uniform probabilities
 	 * 
	 * @return
	 * @throws IOException
	 */
	private double[][][] getInitialGuessOfProbabilities(Random random)
	throws IOException
	{
		//see if there is a file in the datadir that specified initial
		//probs
		if(new File(this.datadir, "initialguess.dat").exists())
		{
			Debug.println("Using user-supplied file of initial probabilities -- 'initial.guess'", Debug.IMPORTANT);
			return getInitialGuessOfProbabilities(new File(this.datadir, "initialguess.dat"));
		}
		
		
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
					//introduce a little random noise into the initial estimates
					//res[hh][tz][dw] = (1+0.1*random.nextDouble())*dwellings[tz][dw];
					
					//guess that the choice probability is proportional to the total number of
					//households of this type that have chosen this zone/dwelling combination
					
					//try1
					//res[hh][tz][dw] = dwellings[tz][dw];
					
					//try2
					//if(dwellings[tz][dw] > 0)
					//	res[hh][tz][dw] = householdsInDwellingsCount[hh][tz][dw]+1; 

					res[hh][tz][dw] = householdsInDwellingsCount[hh][tz][dw]; 

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
	
	
	
	

	
	
	
	/** Get an ordering of household and an allocation of households
	 * that is consistent with the constraints imposed by 
	 * <code>observed</code>.
	 * 
	 * Now, there are many of these, but my claim is that 
	 * the allowable within-bracket variation does not
	 * make a large enough difference to be important. So
	 * different orderings consistent with the constraints
	 * should result in similar likelihood values.
	 * 
	 * 
	 * @param observed [tz,hh,dw,ibrak] The observed allocation of households
	 * @param rand     Random number generator to use. 
	 * @return A PlacedHousehold3 array object array (from 1..NHH) 
	 */
	private PlacedHousehold3[] getRandomAllocationConsistentWithConstraints(int[][][][] observed, Random rand)
	{
		Debug.println("Getting random allocation consistent with constraints", Debug.IMPORTANT);
		int numhouseholds = 0;
		for(int i = 0; i < NINCOME; i++)
			for(int h = 0; h < NHH; h++) 
				numhouseholds += this.households[i][h];
		Debug.println("There are "+numhouseholds+" households to be put into the queue", Debug.IMPORTANT);
		
		PlacedHousehold3[] hharray = new PlacedHousehold3[numhouseholds];
		int hharrcount = 0;
		
		//ok, now go through and populate the array
		//start with highest income households. To start
		//with, we do this deterministically, and then we jumble
		//
		for(int i = (NINCOME*NTRANCHE-1); i >= 0; i--)
		{
			int tranche = i % NTRANCHE;
			
			//the households just within this income bracket.
			//We build this up in the loop, below
			ArrayList withinbrackethouseholds = new ArrayList();
						
			//ok, we work through each household type within this bracket
			for(int hh = 0; hh < NHH; hh++)
			{
				//get all non-zero TZ/DW combos for this ibrak & hhtype
				for(int tz = 0; tz < NTZ; tz++)
					for(int dw = 0; dw < NDWELL; dw++)
					{
						Debug.println("HH "+hh+" TZ "+tz+" DW "+dw+" ibrak "+i/NTRANCHE, Debug.INFO);
						int hhthistranche = observed[tz][hh][dw][i/NTRANCHE]/NTRANCHE;
						if(tranche == 0) //last tranche gets leftover households
							hhthistranche += observed[tz][hh][dw][i/NTRANCHE] % NTRANCHE;
						for(int c=0; c < hhthistranche; c++)
							withinbrackethouseholds.add(new PlacedHousehold3(hh, i/NTRANCHE, tz, dw));
					}
			}
			
			
			//ok, now jumble these up into a random order
			Object[] jumbled = Util.jumble(withinbrackethouseholds.toArray(), rand);
			for(int n = 0; n < jumbled.length; n++)
			{
				hharray[hharrcount++] = (PlacedHousehold3) jumbled[n];
				Debug.println("Household "+hharrcount+" HHTYPE "+hharray[hharrcount-1].hhtype+" TZ "+hharray[hharrcount-1].tz+" IBRAK "+hharray[hharrcount-1].ibrak, Debug.INFO);
			}
			
		}
		
		if(hharrcount != hharray.length)
			throw new RuntimeException("Mismatch in total number of households and number of households in jumbled queue... hharrcount is "+hharrcount+" and hharray.length is "+hharray.length);
		
		return hharray;
	}
	
	

	/** This is the new calcerror where we estimate the likelihood in eqn 11
	 * directly, using a random ordering of households consistent with
	 * income constraints. See eqn 11 in the paper Rickwood & Sambridge.
	 * 
	 * @param probs      probs[hh][tz][dw] gives the free-choice probability for tz,dw combo for household group hh
	 * @param observed   observed[tz][hh][dw][ibrak] gives the actual number of households observed 
	 *                   THIS VARIABLE IS READ-ONLY. DONT STUFF AROUND WITH IT.
	 * @param placedhouseholds      The ordering of allocated households consistent
	 *                   with the observed variable
	 * @return
	 */
	public double calcError_new(double[][][] probs, int[][][][] observed, PlacedHousehold3[] placedhouseholds)
	{		
		//the number of dwellings of each type that are still available
		int[][] dwellingsleft = Util.copy(this.dwellings);
		int totaldwellingsleft = 0;
		for(int n = 0; n < dwellingsleft.length; n++)
			totaldwellingsleft += Util.getSum(dwellingsleft[n]);
		
		
		//now go through and calculate the probability
		double error = 0.0;
		
		
		//need to keep track of how much we need to boost probabilities 
		//by so that they sum to 1 for each household group
		double[] probabilitylost = new double[NHH];
		
		//where possible, use the same indicies as in the paper, to keep things clear
		for(int j = 0; j < placedhouseholds.length; j++) //loop over households
		{
			int hhtype = placedhouseholds[j].hhtype;
			int tz = placedhouseholds[j].tz;
			int dwtype = placedhouseholds[j].dwelltype;
			double freechoiceprob = probs[hhtype][tz][dwtype];
		
			double boostfact = 1/(1-probabilitylost[hhtype]);
			int origdwellings = this.dwellings[tz][dwtype];
			int numleft = dwellingsleft[tz][dwtype];
			if(numleft == 0) throw new IllegalStateException("Household allocated to non-existant dwelling!");
		
			//probability is the per-dwelling probability by the number of dwellings
			double perdwellprob = freechoiceprob/origdwellings;
			double prob = numleft*perdwellprob;
			
			//which then needs to be adjusted for the total probability left
			//for this household group
			prob *= boostfact;
			
			//ok, now remember that we have lost a dwelling and some probability
			dwellingsleft[tz][dwtype]--;
			for(int hh = 0; hh < NHH; hh++) {
				perdwellprob = probs[hh][tz][dwtype]/origdwellings;
				probabilitylost[hh] += perdwellprob;
			}
			
			error += Math.log(prob);
		}		
		
		return -error;  //(return -error so that larger errors are worse)	
	}

	
		
	
	
	
	
	
	
	
	
	
	
	
	
	/** This is only used now to print out the actual allocation.
	 * IT SHOULD NEVER BE USED TO ACTUALLY CALCULATE AN ERROR, AS
	 * IT IS AN OLD VERY AD-HOC ERROR FUNCTION THAT HAS BEEN
	 * ABANDONED LONG AGO.
	 * 
	 * @param dist
	 * @param printallocation
	 * @return
	 */
	private void printallocation(double[][][][] dist, boolean printallocation)
	{
		if(!printallocation)
			throw new RuntimeException("You have called a super-old crappy error function for something OTHER than just printing allocation.... are you SURE you want to do this?");
		
		
		//OK, we've placed all households, and counted up
		//where, and in what dwelling type, everyone is
		//living in. So now we can calculate the error.
		for(int tz = 0; tz < NTZ; tz++)
			for(int dw = 0; dw < NDWELL; dw++)
				for(int hh = 0; hh < NHH; hh++)
				{
					for(int i = 0; i < NINCOME; i++)
					{
						double actual = data[tz][hh][dw][i];
						double pred = dist[tz][hh][dw][i];
						double diff = actual-pred;
						if(printallocation)
						{
							Debug.println("For tz/dw/hh/i "+tz+"/"+dw+"/"+hh+"/"+i+"  pred/actual/diff is "+pred+"/"+actual+"/"+diff, Debug.IMPORTANT);
							Debug.println("MODELPLACEMENT "+tz+" "+dw+" "+hh+" "+i+" "+pred, Debug.IMPORTANT);
						}
					}
				}
	}
	

		
	
	
	
	/** Do a really simple hill-climbing optimization. No gradients,
	 *  nothing, just really simple. 
	 *  
	 *  TODO improve me, or write a better one
	 * 
	 * @param seed
	 * @throws IOException
	 */
	private void dumbOptimize(int seed) throws IOException
	{
		Random rand = new Random(seed);
		
		//have an initial stab. REM: we are working with region probabilities
		//NOT dwelling probabilities, so we need to remember this throughout.
		double[][][] probs = this.getInitialGuessOfProbabilities(rand);
		//get the 'queue' of households and their allocations, which are
		//consistent with the observed data
		PlacedHousehold3[] placedhouseholds = 
			this.getRandomAllocationConsistentWithConstraints(this.data, rand);
		
		double error = calcError_new(probs, this.data, placedhouseholds);
		double errorimp = error; //improvement in error
		
		Debug.println("Before starting, error of initial guess is "+error, Debug.IMPORTANT);
		
			
		//now optimize, until we reach some minimal improvement. 
		//We optimize by just bumping up or down the probabilities a little bit
		int iteration = 0;
		double MINIMP = 0.2;
		while(errorimp > MINIMP)
		{
			iteration++;
			errorimp = 0.0;
			
			//alter zone probabilities in a random order
			Integer[] tzlist = new Integer[NTZ];
			for(int tz = 0; tz < NTZ; tz++)	
				tzlist[tz] = tz;
			Object[] jumbled = Util.jumble(tzlist, rand);
			for(int tz = 0; tz < NTZ; tz++)	
				tzlist[tz] = (Integer) jumbled[tz];
			
			
			//now go through and fiddle with the probabilities			
			for(int i = 0; i < NTZ; i++)
			{
				int tz = tzlist[i].intValue(); //get next tz to fiddle with
				
				//if this is no a valid tz, we skip it
				if(!validtzs[tz])
					continue;

				for(int hh = 0; hh < NHH; hh++)
				{				
					int lastdw = -1;
					for(int dw = 0; dw < NDWELL; dw++)
					{
						if(dwellings[tz][dw] <= 0) {
							if(probs[hh][tz][dw] > 0) throw new RuntimeException("Non-zero probability for tz/dw commbo with 0 dwellings....");
							else continue; //zero dwellings means zero prob, so dont bother adjusting
						}
						
						//check if probability is zero, and if so, skip it
						if(probs[hh][tz][dw] == 0.0)
						{
							//this is ok, so long as there are no households of this type in this tz/dw combo
							if(this.householdsInDwellingsCount[hh][tz][dw] != 0)
								throw new RuntimeException("! Zero probability for hh "+hh+" zone "+tz+" dwelltype "+dw+" when there are households of this type in this zone/dwelling combo!!!! should never happen....");
							continue; //ok, skip this one
						}
						
						int updown = 1; //always try up first
						if(lastdw == dw) updown = -1; //unless we are repeating 
						
						lastdw = dw;
						double startupdatemul = 0.01;
						double updatemul = startupdatemul;
					
						while(updatemul < 0.5) //dont allow steps that are too big
						{
							double probdelta = updown*updatemul*probs[hh][tz][dw];
							if(Math.abs(probdelta) == 0.0) throw new IllegalStateException("Trying to adjust a zero probability! tz "+tz+" dw "+dw+" hh "+hh+" desire "+probs[hh][tz][dw]);
							
							probs[hh][tz][dw] += probdelta;
							
					
							//now adjust (across this hh only) so that probabilities sum to 1
							for(int tz2 = 0; tz2 < NTZ; tz2++)
								if(validtzs[tz2])
									for(int dw2 = 0; dw2 < NDWELL; dw2++)
										probs[hh][tz2][dw2] /= 1+probdelta; 
					
							double err = calcError_new(probs, this.data, placedhouseholds);
					
							if(err >= error) //its not a better fit.
							{
								//adjust probabilities back
								for(int tz2 = 0; tz2 < NTZ; tz2++)
									if(validtzs[tz2])
										for(int dw2 = 0; dw2 < NDWELL; dw2++)
											probs[hh][tz2][dw2] *= 1+probdelta;
								probs[hh][tz][dw] -= probdelta;
								
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
							System.out.println("IT"+iteration+" REGIONPROB "+hh+" "+tz+" "+dw+" "+probs[hh][tz][dw]);
							if(dwellings[tz][dw] <= 0.0)
								System.out.println("IT"+iteration+" DWELLPROB "+hh+" "+tz+" "+dw+" 0.0");
							else 
								System.out.println("IT"+iteration+" DWELLPROB "+hh+" "+tz+" "+dw+" "+probs[hh][tz][dw]/dwellings[tz][dw]);
						}

		}
		
		
		//print out the estimated desire for each tz/dw combo, for each hh type
		for(int hh = 0; hh < NHH; hh++)
		{
			double probsum = 0.0;
			for(int tz = 0; tz < NTZ; tz++)
			{
				if(probs[hh][tz] == null)
					continue;
				
				for(int dw = 0; dw < NDWELL; dw++)
				{
					if(dwellings[tz][dw] <= 0.0)
						System.out.println("FINAL DWELLPROB "+hh+" "+tz+" "+dw+" 0.0 from 0 dwellings");
					else 
						System.out.println("FINAL DWELLPROB "+hh+" "+tz+" "+dw+" "+probs[hh][tz][dw]/dwellings[tz][dw]+" from "+dwellings[tz][dw]+" dwellings");
					Debug.println("REGIONPROB "+tz+" "+hh+" "+dw+" "+probs[hh][tz][dw], Debug.IMPORTANT);
					probsum += probs[hh][tz][dw];
				}
			}
			
			Debug.println("Prob sum is for hh type "+hh+" is "+probsum, Debug.IMPORTANT);
		}
		
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
		Debug.println("      arg7 == random seed to use", Debug.IMPORTANT);
		//Debug.println("      arg8 == minimum dwelling probability", Debug.IMPORTANT);
		
		
		//ResidentialModel resmod = new ResidentialModel("/home/peterr/papers/authoredpapers/predictLocationFromCensus");
		int nhh = Integer.parseInt(args[1]);
		int ndwell = Integer.parseInt(args[2]);
		int ninc  = Integer.parseInt(args[3]);
		int maxtz = Integer.parseInt(args[4]);
		int ntranche = Integer.parseInt(args[5]);
		int seed = Integer.parseInt(args[6]);
		//double minprob = Double.parseDouble(args[7]);
		ResidentialModel2 resmod = new ResidentialModel2(args[0], maxtz, nhh, ndwell, ninc, ntranche);
		
		resmod.dumbOptimize(seed);		
	}


}


class PlacedHousehold3 
{
	int hhtype;
	int ibrak;
	int tz;
	int dwelltype;
	
	public PlacedHousehold3(int hh, int i, int tz, int dwtype) {
		this.hhtype = hh;
		this.ibrak = i;
		this.tz = tz;
		this.dwelltype = dwtype;
	}
}

