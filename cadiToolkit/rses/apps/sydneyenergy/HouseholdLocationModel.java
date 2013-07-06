package rses.apps.sydneyenergy;



import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import rses.Debug;
import rses.math.MathUtil;
import rses.util.Util;





/** This is the model that will take a demographic projection of
 *  households and sort those households into the available dwelling
 *  stock.
 * 
 * @author peterr
 *
 */
public class HouseholdLocationModel 
{
	//projected housing stock and projected desire for that stock
	//and the projection population
	double[][] housingstock; //REGION by DWELLTYPE count of housing stock
	double dwellingcount = 0;
	
	//this is the probability of selection of a *single*
	//dwelling of this type in this area by this household type
	double[][][] desire;     //HHTYPE by REGION by DWELLTYPE desirability of each option
	
	
	double[][] population;   //HHTYPE by IBRAK
	
	int maxhh;
	int maxregionid;
	int maxdwelltype;
	int maxibrak;
	double[] ibraks;
	double[] buildingembodiedGJ; //BY DWELLTYPE
	double[] buildinglife; //BY DWELLTYPE

	
	public HouseholdLocationModel(DataStore database)
	{		
		try {
			//read in globals
			maxhh = Integer.parseInt((String) database.lookupArbitrary("globals", "maxhhtype"));
			Debug.println("max hh index is "+maxhh, Debug.IMPORTANT);
			maxregionid = Integer.parseInt((String) database.lookupArbitrary("globals", "maxregionid"));
			Debug.println("max region index is "+maxregionid, Debug.IMPORTANT);
			maxdwelltype = Integer.parseInt((String) database.lookupArbitrary("globals", "maxdwelltype"));
			Debug.println("max dwelltype index is "+maxdwelltype, Debug.IMPORTANT);
			maxibrak = Integer.parseInt((String) database.lookupArbitrary("globals", "maxibracket"));
			Debug.println("max income bracket is "+maxibrak, Debug.IMPORTANT);
			ibraks = new double[maxibrak+1];
			for(int i = 0; i < maxibrak+1; i++)
				ibraks[i] = Double.parseDouble((String) database.lookupArbitrary("globals", "ibracket"+i));
			Debug.println("Income brackets are: ", Debug.IMPORTANT);
			Util.printarray(ibraks, Debug.getPrintStream(Debug.IMPORTANT));
				
		}
		catch(NoSuchEntryException nsee) {
			Debug.println("Global required for household location model is not defined", Debug.CRITICAL);
			System.exit(1);
		}

		
		
		//read in (optional) embodied energy globals.
		//go with 0.0 as the default for building embodied,
		//and then try and read in values (they are optional)
		buildingembodiedGJ = new double[maxdwelltype+1];
		buildinglife = new double[maxdwelltype+1];
		for(int i = 0; i < buildinglife.length; i++)
			buildinglife[i] = 1.0;
		try {
			for(int i = 0; i <= maxdwelltype; i++)
			{
				buildingembodiedGJ[i] = Double.parseDouble((String) database.lookupArbitrary("globals", "buildingembodiedGJ_"+i));
				buildinglife[i] = Double.parseDouble((String) database.lookupArbitrary("globals", "buildinglife_"+i));
			}
		}
		catch(NoSuchEntryException nsee0) {
			Debug.println("No values found for building embodied energy. Not including embodied energy", Debug.IMPORTANT);
		}

		
		
		getHousingProjections(database);
		//housing projections are needed in order to do population projections
		getPopulationProjections(database);
		getHousingDesire(database);
	}
	
	
	
		
	/* For the moment I just combine old and new stock into
	 * a single count to give the total housing stock, but a
	 * more complex location model may later require the
	 * separate estimates of old and new stock.
	 * 
	 */
	private void getHousingProjections(DataStore database) 
	{
		//now read in the housing stock from the database
		housingstock = new double[maxregionid+1][maxdwelltype+1];
		Debug.println("Initializing projected housing stock", Debug.IMPORTANT);
		
		
		double[] dwcountsbydwtype = new double[maxdwelltype+1];
		
		for(int dw = 0; dw <= maxdwelltype; dw++)
		{
			String tabname = "scenario/newSupply."+dw;			
			if(!database.hasTable(tabname))
				throw new RuntimeException("Missing housing supply table: "+tabname);
		
		
			double[] counts = new double[maxregionid+1];
			boolean nonzeroacked = false;
			for(int i = 0; i <= maxregionid; i++) {
				try { 
					counts[i] = database.lookup(tabname, ""+i);
					if(counts[i] > 0.0001 && !nonzeroacked) {
						Debug.println("WARNING: At the moment, all dwelling stock, new AND old, should be specified in oldStock files. newSupply should be all 0. This is because at the moment, I dont treat new supply differently from old. But you have non-zero entries in your newSupply files.... I havent even tested this thoroughly... though it should be handled OK. Hit enter to acknowledge you have read this", Debug.IMPORTANT);
						try { 
							new java.io.BufferedReader(new InputStreamReader(System.in)).readLine(); 
							nonzeroacked = true;
						}
						catch(IOException ioe) {}
					}
				}
				catch(NoSuchEntryException nsee) { } //just leave it at 0
			}
			
			for(int i = 0; i <= maxregionid; i++) {
				housingstock[i][dw] = counts[i];
				dwellingcount += counts[i];
				dwcountsbydwtype[dw] += counts[i];
			}
			
			
			tabname = "scenario/oldStock."+dw;			
			if(!database.hasTable(tabname))
				throw new RuntimeException("Missing housing supply table: "+tabname);
		
		
			for(int i = 0; i <= maxregionid; i++) 
			{
				double count = 0.0;
				try { count = database.lookup(tabname, ""+i);}
				catch(NoSuchEntryException nsee) { } //just leave it at 0
				
				housingstock[i][dw] += count;
				dwellingcount += count;
				dwcountsbydwtype[dw] += count;
			}
		}
		
		
		
		//work out proportion of flats that are high-rise
		double numhighrise = 0.0;
		for(int i = 0; i <= maxregionid; i++)
		{
			try { 
				double pcthighrise = database.lookup("scenario/pctofflatsthatarehighrise", ""+i);
				numhighrise += pcthighrise*housingstock[i][2];
			}
			catch(NoSuchEntryException nsee) {}
		}
		
		//now, print out total dwelling proportions
		Debug.println("Total % of dwellings that are detached "+dwcountsbydwtype[0]/dwellingcount, Debug.IMPORTANT);
		Debug.println("Total % of dwellings that are semi-detached "+dwcountsbydwtype[1]/dwellingcount, Debug.IMPORTANT);
		Debug.println("Total % of dwellings that are apartments "+dwcountsbydwtype[2]/dwellingcount, Debug.IMPORTANT);
		Debug.println("Total % of dwellings that are low-rise apartments "+(dwcountsbydwtype[2]-numhighrise)/dwellingcount, Debug.IMPORTANT);
		Debug.println("Total % of dwellings that are high-rise apartments "+numhighrise/dwellingcount, Debug.IMPORTANT);

		
		
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
		{
			//double check dwelling counts
			double dwcount2 = 0;
			for(int i = 0; i < housingstock.length; i++)
				for(int j = 0; j < housingstock[0].length; j++)
					dwcount2 += housingstock[i][j];
			if(Math.round(dwcount2) != Math.round(this.dwellingcount))
				throw new IllegalStateException("Dwelling counts were incorrectly done... got "+this.dwellingcount+" in initial calculation and "+dwcount2+" on 2nd check");
		}
	}
	
	
	private void getPopulationProjections(DataStore database)
	{
		//now read in the population projections from the database
		Debug.println("Initializing projected population demographics", Debug.IMPORTANT);
		if(!database.hasTable("scenario/populationProjection"))
			throw new RuntimeException("No population projections available");
		
		this.population = new double[maxhh+1][maxibrak+1];
		double popsum = 0.0;
		for(int hh = 0; hh <= maxhh; hh++)
			for(int i = 0; i <= maxibrak; i++) {
				population[hh][i] = database.lookup("scenario/populationProjection", hh+"_"+i);
				popsum += population[hh][i];
			}

		//now normalize, just in case it wasnt already
		for(int hh = 0; hh <= maxhh; hh++)
			for(int i = 0; i <= maxibrak; i++)
				population[hh][i] /= popsum;
		
		//now convert to actual numbers
		for(int hh = 0; hh <= maxhh; hh++)
			for(int i = 0; i <= maxibrak; i++)
				population[hh][i] *= this.dwellingcount;
		
		//and we're done!
		
	}

	
	private void getHousingDesire(DataStore database)
	{
		//now read in the desire for each hhtype
		Debug.println("Initializing location model", Debug.IMPORTANT);
		
		this.desire = new double[maxhh+1][maxregionid+1][maxdwelltype+1];
	
		
		for(int hh = 0; hh <= maxhh; hh++)
		{
			double probsum = 0.0;
			for(int dw = 0; dw <= maxdwelltype; dw++)
			{
				String tabname = "scenario/desire."+hh+"."+dw;
				if(!database.hasTable(tabname))
					throw new RuntimeException("Missing housing desire table: "+tabname);
				
				String[] keys = new String[maxregionid+1];
				for(int i = 0; i <= maxregionid; i++)
					keys[i] = ""+i;
				
				double[] desire = new double[maxregionid+1];
				for(int i = 0; i <= maxregionid; i++) {
					try {
						desire[i] = database.lookup(tabname, keys[i]);
					}
					catch(NoSuchEntryException nse) {}
				}
				
				
				//now, the value specified in the desire file is
				//for a single dwelling, so we need to correct for
				//the dwelling numbers in each area
				for(int i = 0; i <= maxregionid; i++) {
					this.desire[hh][i][dw] = desire[i]*this.housingstock[i][dw];
					probsum += this.desire[hh][i][dw];
				}
				
			}
			
			Debug.println("Initial normalization divisor required for desire for household "+hh+" is "+probsum, Debug.IMPORTANT);
			//now rescale the probabilities so that they sum to 1
			for(int i = 0; i <= maxregionid; i++)
				for(int d = 0; d <= maxdwelltype; d++)
					this.desire[hh][i][d] /= probsum;
		}		
	}
	
	
	
	public void run(DataStore database)
	{
		//ok, just run the model.
		this.placeHouseholds(database);
	}
	
	
	
	
	/** Place the given population of households into the available 
	 *  housing stock. This method then updates the database so that
	 *  the demographic information reflects the new houshold distribution.
	 *  Also invokes other models that need to be updated in response
	 *  to the change in households. This includes energy models and
	 *  car ownership models and the like.
	 * 
	 * @param projected
	 */
	public void placeHouseholds(DataStore database)
	{
		int ntz = this.maxregionid+1;
		int ndw = this.maxdwelltype+1;
		int nhh = this.maxhh+1;
		int ninc = this.maxibrak+1;
		int totalpop = 0;
		int[][] hhcounts = new int[ninc][nhh];
		for(int i = 0; i < ninc; i++)
			for(int j = 0; j < nhh; j++)
			{
				hhcounts[i][j] = (int) Math.rint(this.population[j][i]);
				totalpop += hhcounts[i][j];
			}
		
		Debug.println("# of households in projected population is "+totalpop, Debug.IMPORTANT);
		Debug.println("# of dwellings in projected population is "+this.dwellingcount, Debug.IMPORTANT);

		
		//distribute everyone into the available housing stock 
		double[][][][] dist = ResidentialModel.distributeHouseholds(desire, 
				this.housingstock, hhcounts, ntz, ndw, nhh, ninc);
		
		
		Debug.println("Sorted everyone into housing... now running behavioural models and calculating statistics", Debug.IMPORTANT);
		
		double[][][] distwithoutincome = new double[ntz][nhh][ndw];
		double[][] distwithoutincomeordwelltype = new double[ntz][nhh];
		for(int z = 0; z < ntz; z++)
			for(int hh = 0; hh < nhh; hh++)
				for(int dw = 0; dw < ndw; dw++)
					for(int i = 0; i < ninc; i++) {
						distwithoutincome[z][hh][dw] += dist[z][hh][dw][i];
						distwithoutincomeordwelltype[z][hh] += dist[z][hh][dw][i];
					}
		
		
		//now calculate mean and median income
		double[] tzmeanincome = new double[ntz];
		double[] tzmedianincome = new double[ntz];
		if(!database.hasTable("tz_meanhhincome"))
			database.addTable("tz_meanhhincome", new java.util.HashMap());
		for(int z =0; z < ntz; z++)
		{
			ArrayList incomes = new ArrayList();
			double count = 0;
			for(int hh=0; hh < nhh; hh++)
				for(int dw = 0; dw < ndw; dw++)
					for(int inc = 0; inc < ninc; inc++) 
					{
						double lwr = ibraks[inc]/52.0;
						double upr = lwr;
						//get lower and upper bounds on this income bracket, if
						//they exist
						String boundsstring = (String) database.lookupArbitrary("globals", "ibracket"+inc+"bounds");
						if(boundsstring != null) {
							lwr = Double.parseDouble(boundsstring.split("_")[0])/52.0;
							upr = Double.parseDouble(boundsstring.split("_")[1])/52.0;
						}
						double stepsize = (upr-lwr)/(Math.ceil(dist[z][hh][dw][inc])+1);
						stepsize = Math.min(stepsize, (upr-lwr)/2);
						double startval = lwr+stepsize;
						//Debug.println("lwr/upr/stepsize for income are "+lwr+"/"+upr+"/"+stepsize, Debug.INFO);
						
						
						//INCOME IS STORED IN THE DATABASE AS WEEKLY HH INCOME
						count += dist[z][hh][dw][inc];
						for(int i = 0; i < ((int)Math.ceil(dist[z][hh][dw][inc]))+1; i++)
						{
							if(startval > upr+0.01) throw new IllegalStateException("Impossible!  "+lwr+"/"+upr+"/"+stepsize+" "+i+" "+dist[z][hh][dw][inc]);
							//incomes.add(new Double(ibraks[inc]/52.0));
							incomes.add(new Double(startval));
							startval += stepsize;
						}
						
						tzmeanincome[z] += dist[z][hh][dw][inc]*(ibraks[inc]/52.0);
					}
			
			if(count > 0)
				tzmeanincome[z] /= count;
			else
				tzmeanincome[z] = Double.NaN;
			
			if(incomes.size() > 0) 
			{
				double[] vals = new double[incomes.size()];
				for(int i = 0; i < vals.length; i++) vals[i] = ((Double) incomes.get(i)).doubleValue();
				Arrays.sort(vals);
				if(vals.length%2 == 0)
					tzmedianincome[z] = (vals[vals.length/2-1]+vals[vals.length/2])/2;
				else
					tzmedianincome[z] = vals[vals.length/2];
			}
			else
				tzmedianincome[z] = Double.NaN;
			
			database.replaceValue("tz_medhhincome", ""+z, new Double(tzmedianincome[z]), false);
			database.replaceValue("tz_meanhhincome", ""+z, new Double(tzmeanincome[z]), false);
		}
		

		Debug.println("Worked out mean and median household income", Debug.IMPORTANT);
		
		
		
		//Work out proportion of couple w. kids by combining the two groups		
		int numcouplekidsindices = Integer.parseInt((String) database.lookupArbitrary("globals", "numcouplekidsindices"));
		int[] couplekidsindices = new int[numcouplekidsindices];
		for(int i = 0; i < couplekidsindices.length; i++)
			couplekidsindices[i] = Integer.parseInt((String) database.lookupArbitrary("globals", "couplekidindex"+i));
		
		double[] tzpropcouplewkids = new double[ntz];
		for(int z =0; z < ntz; z++)
		{
			double totalzonehh = 0;
			for(int dw = 0; dw < ndw; dw++)
				for(int inc = 0; inc < ninc; inc++) {
					for(int hh = 0; hh < nhh; hh++)
						totalzonehh += dist[z][hh][dw][inc];
					for(int i = 0; i < couplekidsindices.length; i++)
						tzpropcouplewkids[z] += (dist[z][couplekidsindices[i]][dw][inc]);
				}
			
			if(totalzonehh > 0)
				database.replaceValue("tz_propcouplewkids", ""+z, new Double(tzpropcouplewkids[z]/totalzonehh), false);
			else
				database.replaceValue("tz_propcouplewkids", ""+z, new Double(Double.NaN), false);
		}
					

		//now we calculate population densities
		double[] personcountbytz = new double[ntz];
		double[] nbrhh = new double[ntz];

		double[] ppmul = new double[nhh];
		for(int i = 0; i < nhh; i++)
			ppmul[i] = Double.parseDouble((String) database.lookupArbitrary("globals", "personsperhhtype"+i));

		double totalpeopleincity = 0.0;
		for(int z = 0; z < ntz; z++)
		{
			for(int hh = 0; hh < nhh; hh++)
				for(int dw = 0; dw < ndw; dw++)
					for(int inc = 0; inc < ninc; inc++)
					{
						nbrhh[z] += dist[z][hh][dw][inc];
						personcountbytz[z] += dist[z][hh][dw][inc]*ppmul[hh];
					}
			totalpeopleincity += personcountbytz[z];
		}
		
		
		if(!database.hasTable("results/pctdwellingsthatarehighrise"))
			database.addTable("results/pctdwellingsthatarehighrise", new HashMap());
		if(!database.hasTable("tz_totalhouseholds"))
			database.addTable("tz_totalhouseholds", new java.util.HashMap());
		if(!database.hasTable("tz_population"))
			database.addTable("tz_population", new HashMap());
		for(int z = 0; z < ntz; z++)
		{
			database.replaceValue("tz_totalhouseholds", ""+z, new Double(nbrhh[z]), false);
			database.replaceValue("tz_population", ""+z, new Double(personcountbytz[z]), false);
			double highrisepct = 0.0;
			try { 
				highrisepct = database.lookup("scenario/pctofflatsthatarehighrise", ""+z);
			}
			catch(NoSuchEntryException nsee) {}
			
			double pctofdwellthatarehighrise = 0.0;
			double numflats = this.housingstock[z][2];
			if(nbrhh[z] > 0.0001)
				pctofdwellthatarehighrise = (highrisepct*numflats)/nbrhh[z];
			database.replaceValue("results/pctdwellingsthatarehighrise", ""+z, new Double(pctofdwellthatarehighrise), false);
			
			try {
				double zonearea = database.lookup("tz_area_sqkm", ""+z);
				database.replaceValue("tzpopdensity", ""+z, new Double(personcountbytz[z]/zonearea), false);
			}
			catch(NoSuchEntryException nsee) { /**/ }
		}

		
		Debug.println("Worked out population densities", Debug.IMPORTANT);
		

		//calculate income per person as well.
		if(!database.hasTable("tz_meanincomeperperson"))
			database.addTable("tz_meanincomeperperson", new java.util.HashMap());
		if(!database.hasTable("tz_personsperdwelling"))
			database.addTable("tz_personsperdwelling", new java.util.HashMap());
		for(int i = 0; i < nhh; i++)
		{
			if(!database.hasTable("results/tz_"+i+"_proportion"))
				database.addTable("results/tz_"+i+"_proportion", new HashMap());
			//if(!database.hasTable("tz_"+i+"_count"))
			//	database.addTable("tz_"+i+"_count", new HashMap());
		}

		
		//now work out the proportion of each household type in each zone
		for(int z = 0; z < ntz; z++)
		{
			double numberofhouseholds = nbrhh[z];
			try {
				for(int hh = 0; hh < nhh; hh++)
				{
					double thishhcount = distwithoutincomeordwelltype[z][hh];
					database.replaceValue("results/tz_"+hh+"_proportion", z+"", new Double(thishhcount/numberofhouseholds), false);
					//database.replaceValue("tz_"+hh+"_count", z+"", new Double(thishhcount), false);
				}
					
				double meanhouseholdincome = database.lookup("tz_meanhhincome", ""+z);
				double pop = database.lookup("tz_population", ""+z);
				if(pop > 0.0 && numberofhouseholds > 0.0) {	
					double incomeperhousehold = (meanhouseholdincome*numberofhouseholds)/pop;
					database.replaceValue("tz_meanincomeperperson", ""+z, new Double(incomeperhousehold), false);
					double personsperdwelling = pop/numberofhouseholds;
					database.replaceValue("tz_personsperdwelling", ""+z, new Double(personsperdwelling), false);
				}
			}
			catch(NoSuchEntryException nsee) { /**/ }
		}

		

		Debug.println("Worked out household proportions in each zone", Debug.IMPORTANT);
		Debug.println("Now running energy models.. this may take a while", Debug.IMPORTANT);
		
		//now we can calculate transport and housing energy and gge		
		//(i.e. the main deal)
		String[] resultstables = new String[] {"results/2031indwelling", 
				"results/2031transportoperational", "results/2031transitoperational",
				"results/2031caroperational", "results/2031transportall", "results/2031dwellingall",
				"results/2031totalenergy", "results/2031carembodied"};
		for(int i = 0; i < resultstables.length; i++)
		{
			if(database.hasTable(resultstables[i]+"_phh") || 
			   database.hasTable(resultstables[i]+"_pp")  ||
			   database.hasTable(resultstables[i]+"_gge_phh"))
				throw new RuntimeException("Table already exists that shouldnt, stem: "+resultstables[i]);
			database.addTable(resultstables[i]+"_phh", new java.util.HashMap());
			database.addTable(resultstables[i]+"_pp", new java.util.HashMap());
			database.addTable(resultstables[i]+"_gge_phh", new java.util.HashMap());
		}
		
		

		double totalenergy = 0.0;
		double totalgge = 0.0;
		double totalhouseholds = 0.0;
		double totalpeople = 0.0;
		for(int z =0; z < ntz; z++)
		{
			if(nbrhh[z] < 0.0000001) 
				continue;
			
			if((z % (ntz/10)) == (ntz/10 -1))
				Debug.println("Done "+z+" of "+ntz+" zones", Debug.IMPORTANT);
			
			double[] zonedwelltotals = new double[2];
			double[] zonetransporttotals = new double[6];
			double zonehhcount = 0.0; 
			for(int hh=0; hh < nhh; hh++)
			{
				for(int dw = 0; dw < ndw; dw++) 
				{
					
					for(int inc = 0; inc < ninc; inc++) 
					{
						if(dist[z][hh][dw][inc] == 0.0)
							continue;
						
						double pcthighrise = 0.0;
						if(dw == maxdwelltype) 
							pcthighrise = database.lookup("scenario/pctofflatsthatarehighrise", ""+z);
						
						//ok, get energy measures
						double[] dwellenergy = HousingEnergyModule.calcEnergyForHousehold_roughguessonly(hh, inc, dw, ibraks, pcthighrise, database);
						double[] transportindicators = TransportEnergyModule.calcTransportEnergyForHousehold(hh, inc, dw, z, ibraks, database);
						
						//multiply them out by the number of households of this type/bracket in this zone/dwelltype
						for(int i = 0; i < dwellenergy.length; i++)
							zonedwelltotals[i] += dwellenergy[i]*dist[z][hh][dw][inc];
						for(int i = 0; i < transportindicators.length; i++)
							zonetransporttotals[i] += transportindicators[i]*dist[z][hh][dw][inc];

						//keep track of number of houseolds
						zonehhcount += dist[z][hh][dw][inc];
					}
				}
			}
			if(Math.abs(zonehhcount-nbrhh[z]) > 0.001) 
				throw new RuntimeException("Recalculated what should be the same value, and its different!!!!!");
			
			if(zonehhcount > 0) 
			{
				totalhouseholds += zonehhcount;
				
				for(int i = 0; i < zonedwelltotals.length; i++) 
					zonedwelltotals[i] /= zonehhcount;
				for(int i =0; i < zonetransporttotals.length; i++) 
					zonetransporttotals[i] /= zonehhcount;

				double personsperhh = database.lookup("tz_personsperdwelling", ""+z);
				
				//"2031transitoperational", "2031caroperational", 
				//"2031transportall", "2031dwellingall", "2031totalenergy"

				//first in-dwelling operational only
				database.replaceValue("results/2031indwelling_phh", ""+z, new Double(zonedwelltotals[0]), false);
				database.replaceValue("results/2031indwelling_pp", ""+z, new Double(zonedwelltotals[0]/personsperhh), false);
				//report gge in kg emissions per annum
				double ggeconvfact = Double.parseDouble((String) database.lookupArbitrary("globals", "deliveredmjtoco2"));
				database.replaceValue("results/2031indwelling_gge_phh", ""+z, new Double(zonedwelltotals[0]*ggeconvfact), false);

				//now transport (public and private), operational only
				double vktmjtoconvfact = Double.parseDouble((String) database.lookupArbitrary("globals", "vktmjtoco2"));
				double passkmmjtoconvfact = Double.parseDouble((String) database.lookupArbitrary("globals", "passkmmjtoco2"));
				database.replaceValue("results/2031transportoperational_phh", ""+z, new Double(zonetransporttotals[0]+zonetransporttotals[1]), false);
				database.replaceValue("results/2031transportoperational_pp", ""+z, new Double((zonetransporttotals[0]+zonetransporttotals[1])/personsperhh), false);				
				database.replaceValue("results/2031transportoperational_gge_phh", ""+z, new Double(zonetransporttotals[0]*vktmjtoconvfact+zonetransporttotals[1]*passkmmjtoconvfact), false);
				database.replaceValue("results/2031transitoperational_phh", ""+z, new Double(zonetransporttotals[1]), false);
				database.replaceValue("results/2031transitoperational_pp", ""+z, new Double(zonetransporttotals[1]/personsperhh), false);
				database.replaceValue("results/2031transitoperational_gge_phh", ""+z, new Double(zonetransporttotals[1]*passkmmjtoconvfact), false);
				database.replaceValue("results/2031caroperational_phh", ""+z, new Double(zonetransporttotals[0]), false);
				database.replaceValue("results/2031caroperational_pp", ""+z, new Double(zonetransporttotals[0]/personsperhh), false);
				database.replaceValue("results/2031caroperational_gge_phh", ""+z, new Double(zonetransporttotals[0]*vktmjtoconvfact), false);
				double embodiedmjtogge = Double.parseDouble((String) database.lookupArbitrary("globals", "embodiedmjtoco2"));
				database.replaceValue("results/2031transportall_phh", ""+z, new Double(zonetransporttotals[0]+zonetransporttotals[1]+zonetransporttotals[2]), false);
				database.replaceValue("results/2031transportall_pp", ""+z, new Double((zonetransporttotals[0]+zonetransporttotals[1]+zonetransporttotals[2])/personsperhh), false);
				database.replaceValue("results/2031transportall_gge_phh", ""+z, new Double(zonetransporttotals[0]*vktmjtoconvfact+zonetransporttotals[1]*passkmmjtoconvfact+zonetransporttotals[2]*embodiedmjtogge), false);

				//do car embodied
				database.replaceValue("results/2031carembodied_phh", ""+z, new Double(zonetransporttotals[2]), false);
				database.replaceValue("results/2031carembodied_pp", ""+z, new Double(zonetransporttotals[2]/personsperhh), false);
				database.replaceValue("results/2031carembodied_gge_phh", ""+z, new Double(zonetransporttotals[2]*embodiedmjtogge), false);
				
				
				//now do all indwelling
				database.replaceValue("results/2031dwellingall_phh", ""+z, new Double(zonedwelltotals[0]+zonedwelltotals[1]), false);
				database.replaceValue("results/2031dwellingall_pp", ""+z, new Double((zonedwelltotals[0]+zonedwelltotals[1])/personsperhh), false);
				database.replaceValue("results/2031dwellingall_gge_phh", ""+z, new Double(zonedwelltotals[0]*ggeconvfact+zonedwelltotals[1]*embodiedmjtogge), false);

				//and now do total energy and gge
				double totaltzenergy = database.lookup("results/2031transportall_phh", ""+z)+database.lookup("results/2031dwellingall_phh", ""+z);
				database.replaceValue("results/2031totalenergy_phh", ""+z, new Double(totaltzenergy), false);
				database.replaceValue("results/2031totalenergy_pp", ""+z, new Double(totaltzenergy/personsperhh), false);
				double totaltzgge = database.lookup("results/2031transportall_gge_phh", ""+z)+database.lookup("results/2031dwellingall_gge_phh", ""+z);
				database.replaceValue("results/2031totalenergy_gge_phh", ""+z, new Double(totaltzgge), false);
							
				
				//keep track of citywide gge and energy 
				totalgge += totaltzgge*zonehhcount;
				totalenergy += totaltzenergy*zonehhcount; 				
			}
			else { //dont include zones without any people
				for(int i = 0; i < resultstables.length; i++) {
					database.replaceValue(resultstables[i]+"_phh", ""+z, new Double(Double.NaN), false);
					database.replaceValue(resultstables[i]+"_pp", ""+z, new Double(Double.NaN), false);
					database.replaceValue(resultstables[i]+"_gge_phh", ""+z, new Double(Double.NaN), false);
				}
			}
			
		}
		
				

		
		//print out total energy for the city and per person
		Debug.println("Total households "+totalhouseholds, Debug.IMPORTANT);
		Debug.println("Total population "+totalpeopleincity, Debug.IMPORTANT);
		Debug.println("Persons per household "+totalpeopleincity/totalhouseholds, Debug.IMPORTANT);
		for(int i = 0; i < resultstables.length; i++)
		{
			double tot = 0.0;
			double ggephh = 0.0;
			for(int z = 0; z <= maxregionid; z++)
			{
				if(nbrhh[z] < 0.00001)
					continue;
				tot += database.lookup(resultstables[i]+"_phh", ""+z)*nbrhh[z];
				ggephh += database.lookup(resultstables[i]+"_gge_phh", ""+z)*nbrhh[z];
			}
			
			Debug.println(resultstables[i]+"_phh  "+tot/totalhouseholds, Debug.IMPORTANT);
			Debug.println(resultstables[i]+"_pp  "+tot/totalpeopleincity, Debug.IMPORTANT);
			Debug.println(resultstables[i]+"_gge_phh  "+ggephh/totalhouseholds, Debug.IMPORTANT);
		}
		
		
		
		//now work out how satisfied people are with their housing
		//choices.
		reportHousingSatisfactionNumbers(dist, database);
		

		//now dump everything from the database out
		try {
			PrintWriter pw = new PrintWriter(new java.io.FileWriter(new File("db/scenario/resultfile.txt")));
			database.dumpOutEverything(pw);
			pw.close();
		}
		catch(IOException ioe)
		{ 
			System.err.println("IOException");
			throw new RuntimeException(ioe);
		}
		
		

		
		
		//now we would like to work out the marginal effect of moving a fixed 
		//household from one region to another 
		
		
		
		
		
	}
	
	
	private void reportHousingSatisfactionNumbers(double[][][][] popdist, DataStore database)
	{
		/*double[] mediandesirevals = new double[] {
				1.20505e-06, 8.96404e-07, 1.1475e-06,7.50081e-07,
				9.41839e-07,6.9299e-07,1.10097e-06,9.7522e-07
		};*/
			
		
		for(int hh = 0; hh <= maxhh; hh++)
		{
			reportHousingSatisfactionNumbers2(hh, popdist, database);
			//-1 indicates all ibraks
			/*for(int i =-1; i <= maxibrak; i++)
				reportHousingSatisfactionNumbers(hh, i, mediandesirevals[hh], popdist, database);*/
		}
		
		//now go through each TZ and work out the overall pct satisfied with
		//their choice
		database.addTable("results/pctsatisfied", new HashMap());
		for(int tz = 0; tz <= maxregionid; tz++)
		{
			double pctsatisfied = 0.0;
			for(int hh = 0; hh <= maxhh; hh++)
			{
				try {
					double prop = database.lookup("results/tz_"+hh+"_proportion", ""+tz);
					double satisfiedpct = database.lookup("results/satisfaction_"+hh, ""+tz);
					pctsatisfied += prop*satisfiedpct;
				}
				catch(NoSuchEntryException nsee)
				{}
			}
			database.replaceValue("results/pctsatisfied", ""+tz, new Double(pctsatisfied), false);
		}
		
	}

	
	//get all the households of this type in this income bracket,
	//rank all their housing allocations according to desirability
	//for that household, and then take the middle one. Then work
	//out how close this is to the middle desire value for all choices
	//for that household
	private void reportHousingSatisfactionNumbers(int hhtype, int ibrak, double medianchoicedesireforthishousehold, double[][][][] popdist, DataStore database)
	{
		//ok, lets work out the median value of the choice got by this
		//hh type on this ibrak
		double numunder = 0.0;
		double numover = 0.0;
		for(int tz = 0; tz <= maxregionid; tz++)
		{
			for(int dw = 0; dw <= maxdwelltype; dw++)
			{
				//desire array holds the region desire, so we need to
				//get back to dwelling desire per dwelling by reading
				//from the database. This is not properly normalized,
				//but is ok for my purposes here
				double p = 0.0;
				
				try { p = database.lookup("scenario/desire."+hhtype+"."+dw, ""+tz); }
				catch(NoSuchEntryException nsee) {}
				
				if(p == 0.0 || Double.isNaN(p))
					continue;

				double count = 0.0;
				if(ibrak != -1)
					count = popdist[tz][hhtype][dw][ibrak];
				else for(int i = 0; i <= maxibrak; i++)
					count += popdist[tz][hhtype][dw][i];
				
				if(p > medianchoicedesireforthishousehold)
					numover += count;
				else
					numunder += count;
			}
		}
		
		//ok, now sort through this and find out how many households
		//get into accomodation better than the 50\% accomodation
		//benchmark
		double pctthatgotbetterthanmedian = numover/(numover+numunder);
		Debug.println("SATISFACTION "+hhtype+" "+ibrak+" "+pctthatgotbetterthanmedian, Debug.IMPORTANT);
		
		
	}
	
	
	//Idea for this one is a bit different.... there is 
	//a distribution of preferences, and the optimal
	//housing mix is one where the desired distribution matched
	//the actual. So.... work out how closely the housing mix
	//they end up in matches their preferences
	//
	//
	private void reportHousingSatisfactionNumbers2(int hhtype, double[][][][] popdist, DataStore database)
	{
		database.addTable("results/satisfaction_"+hhtype, new HashMap());
		
		double totalpopforthishhtype = 0.0;
		for(int i = 0; i <= maxibrak; i++)
			totalpopforthishhtype += population[hhtype][i];
		
		double numsatisfied = 0.0; 
		for(int tz = 0; tz <= maxregionid; tz++)
		{
			double hhofthistypeinthiszone = 0.0;
			double satisfiedhhofthistypeinthiszone = 0.0;
			for(int dw = 0; dw <= maxdwelltype; dw++)
			{
				//desire holds tz/dw probability, so we use that directly
				double p = this.desire[hhtype][tz][dw];
				double optimalnumber = p*totalpopforthishhtype;
				double actualnumber = 0.0;
				for(int i = 0; i <= maxibrak; i++) {
					actualnumber += popdist[tz][hhtype][dw][i];
					hhofthistypeinthiszone += popdist[tz][hhtype][dw][i];
				}
				numsatisfied += Math.min(actualnumber, optimalnumber);
				satisfiedhhofthistypeinthiszone += Math.min(actualnumber, optimalnumber);
			}
			database.replaceValue("results/satisfaction_"+hhtype, ""+tz, new Double(satisfiedhhofthistypeinthiszone/hhofthistypeinthiszone), false);
		}
		
		//ok, now sort through this and find out how many households
		//get into accomodation better than the 50\% accomodation
		//benchmark
		double pctsatisfied = numsatisfied/totalpopforthishhtype;
		Debug.println("SATISFACTIONPCT "+hhtype+" "+pctsatisfied, Debug.IMPORTANT);
		
	}
	
	
	
	
	
	
	
	
	
	
	
	
/*
	//the old version with aggregate models
	public void placeHouseholds_old(DataStore database)
	{
		int ntz = this.maxregionid+1;
		int ndw = this.maxdwelltype+1;
		int nhh = this.maxhh+1;
		int ninc = this.maxibrak+1;
		int totalpop = 0;
		int[][] hhcounts = new int[ninc][nhh];
		for(int i = 0; i < ninc; i++)
			for(int j = 0; j < nhh; j++)
			{
				hhcounts[i][j] = (int) Math.rint(this.population[j][i]);
				totalpop += hhcounts[i][j];
			}
		
		Debug.println("# of households in projected population is "+totalpop, Debug.IMPORTANT);
		Debug.println("# of dwellings in projected population is "+this.dwellingcount, Debug.IMPORTANT);

		
		//distribute everyone into the available housing stock 
		double[][][][] dist = ResidentialModel.distributeHouseholds(desire, 
				this.housingstock, hhcounts, ntz, ndw, nhh, ninc);
		
		double[][][] distwithoutincome = new double[ntz][nhh][ndw];
		double[][] distwithoutincomeordwelltype = new double[ntz][nhh];
		for(int z = 0; z < ntz; z++)
			for(int hh = 0; hh < nhh; hh++)
				for(int dw = 0; dw < ndw; dw++)
					for(int i = 0; i < ninc; i++) {
						distwithoutincome[z][hh][dw] += dist[z][hh][dw][i];
						distwithoutincomeordwelltype[z][hh] += dist[z][hh][dw][i];
					}
		
		
		//now calculate mean and median income
		double[] tzmeanincome = new double[ntz];
		double[] tzmedianincome = new double[ntz];
		if(!database.hasTable("tz_meanhhincome"))
			database.addTable("tz_meanhhincome", new java.util.HashMap());
		for(int z =0; z < ntz; z++)
		{
			ArrayList incomes = new ArrayList();
			double count = 0;
			for(int hh=0; hh < nhh; hh++)
				for(int dw = 0; dw < ndw; dw++)
					for(int inc = 0; inc < ninc; inc++) 
					{
						double lwr = ibraks[inc]/52.0;
						double upr = lwr;
						//get lower and upper bounds on this income bracket, if
						//they exist
						String boundsstring = (String) database.lookupArbitrary("globals", "ibracket"+inc+"bounds");
						if(boundsstring != null) {
							lwr = Double.parseDouble(boundsstring.split("_")[0])/52.0;
							upr = Double.parseDouble(boundsstring.split("_")[1])/52.0;
						}
						double stepsize = (upr-lwr)/(Math.ceil(dist[z][hh][dw][inc])+1);
						stepsize = Math.min(stepsize, (upr-lwr)/2);
						double startval = lwr+stepsize;
						Debug.println("lwr/upr/stepsize for income are "+lwr+"/"+upr+"/"+stepsize, Debug.INFO);
						
						
						//INCOME IS STORED IN THE DATABASE AS WEEKLY HH INCOME
						count += dist[z][hh][dw][inc];
						for(int i = 0; i < ((int)Math.ceil(dist[z][hh][dw][inc]))+1; i++)
						{
							if(startval > upr+0.01) throw new IllegalStateException("Impossible!  "+lwr+"/"+upr+"/"+stepsize+" "+i+" "+dist[z][hh][dw][inc]);
							//incomes.add(new Double(ibraks[inc]/52.0));
							incomes.add(new Double(startval));
							startval += stepsize;
						}
						
						tzmeanincome[z] += dist[z][hh][dw][inc]*(ibraks[inc]/52.0);
					}
			
			if(count > 0)
				tzmeanincome[z] /= count;
			else
				tzmeanincome[z] = Double.NaN;
			
			if(incomes.size() > 0) 
			{
				double[] vals = new double[incomes.size()];
				for(int i = 0; i < vals.length; i++) vals[i] = ((Double) incomes.get(i)).doubleValue();
				Arrays.sort(vals);
				if(vals.length%2 == 0)
					tzmedianincome[z] = (vals[vals.length/2-1]+vals[vals.length/2])/2;
				else
					tzmedianincome[z] = vals[vals.length/2];
			}
			else
				tzmedianincome[z] = Double.NaN;
			
			database.replaceValue("tz_medhhincome", ""+z, new Double(tzmedianincome[z]), false);
			database.replaceValue("tz_meanhhincome", ""+z, new Double(tzmeanincome[z]), false);
		}
		

		
		
		//ok, now work out the proportion of households of each type in each 
		//zone. Currently just do proportion of couple w kids, because
		//thats all we use.
		
		int numcouplekidsindices = Integer.parseInt((String) database.lookupArbitrary("globals", "numcouplekidsindices"));
		int[] couplekidsindices = new int[numcouplekidsindices];
		for(int i = 0; i < couplekidsindices.length; i++)
			couplekidsindices[i] = Integer.parseInt((String) database.lookupArbitrary("globals", "couplekidindex"+i));
		
		double[] tzpropcouplewkids = new double[ntz];
		for(int z =0; z < ntz; z++)
		{
			double totalzonehh = 0;
			for(int dw = 0; dw < ndw; dw++)
				for(int inc = 0; inc < ninc; inc++) {
					for(int hh = 0; hh < nhh; hh++)
						totalzonehh += dist[z][hh][dw][inc];
					for(int i = 0; i < couplekidsindices.length; i++)
						tzpropcouplewkids[z] += (dist[z][couplekidsindices[i]][dw][inc]);
				}
			
			if(totalzonehh > 0)
				database.replaceValue("tz_propcouplewkids", ""+z, new Double(tzpropcouplewkids[z]/totalzonehh), false);
			else
				database.replaceValue("tz_propcouplewkids", ""+z, new Double(Double.NaN), false);
		}
					

		//now we calculate population densities
		double[] personcountbytz = new double[ntz];
		double[] nbrhh = new double[ntz];

		double[] ppmul = new double[nhh];
		for(int i = 0; i < nhh; i++)
			ppmul[i] = Double.parseDouble((String) database.lookupArbitrary("globals", "personsperhhtype"+i));
		
		for(int z = 0; z < ntz; z++)
			for(int hh = 0; hh < nhh; hh++)
				for(int dw = 0; dw < ndw; dw++)
					for(int inc = 0; inc < ninc; inc++)
					{
						nbrhh[z] += dist[z][hh][dw][inc];
						personcountbytz[z] += dist[z][hh][dw][inc]*ppmul[hh];
					}
		
		if(!database.hasTable("tz_totalhouseholds"))
			database.addTable("tz_totalhouseholds", new java.util.HashMap());
		if(!database.hasTable("tz_population"))
			database.addTable("tz_population", new HashMap());
		for(int z = 0; z < ntz; z++)
		{
			database.replaceValue("tz_totalhouseholds", ""+z, new Double(nbrhh[z]), false);
			database.replaceValue("tz_population", ""+z, new Double(personcountbytz[z]), false);
			try {
				double zonearea = database.lookup("tz_area_sqkm", ""+z);
				database.replaceValue("tzpopdensity", ""+z, new Double(personcountbytz[z]/zonearea), false);
			}
			catch(NoSuchEntryException nsee) {  }
		}

		

		//calculate income per person as well.
		if(!database.hasTable("tz_meanincomeperperson"))
			database.addTable("tz_meanincomeperperson", new java.util.HashMap());
		if(!database.hasTable("tz_personsperdwelling"))
			database.addTable("tz_personsperdwelling", new java.util.HashMap());
		for(int i = 0; i < nhh; i++)
		{
			if(!database.hasTable("tz_"+i+"_proportion"))
				database.addTable("tz_"+i+"_proportion", new HashMap());
			//if(!database.hasTable("tz_"+i+"_count"))
			//	database.addTable("tz_"+i+"_count", new HashMap());
		}
		
		for(int z = 0; z < ntz; z++)
		{
			double numberofhouseholds = nbrhh[z];
			try {
				for(int hh = 0; hh < nhh; hh++)
				{
					double thishhcount = distwithoutincomeordwelltype[z][hh];
					database.replaceValue("tz_"+hh+"_proportion", z+"", new Double(thishhcount/numberofhouseholds), false);
					//database.replaceValue("tz_"+hh+"_count", z+"", new Double(thishhcount), false);
				}
					
				double meanhouseholdincome = database.lookup("tz_meanhhincome", ""+z);
				double pop = database.lookup("tz_population", ""+z);
				if(pop > 0.0 && numberofhouseholds > 0.0) {	
					double incomeperhousehold = (meanhouseholdincome*numberofhouseholds)/pop;
					database.replaceValue("tz_meanincomeperperson", ""+z, new Double(incomeperhousehold), false);
					double personsperdwelling = pop/numberofhouseholds;
					database.replaceValue("tz_personsperdwelling", ""+z, new Double(personsperdwelling), false);
				}
			}
			catch(NoSuchEntryException nsee) {  }
		}

		

		
		//calculate embodied energy in buildings
		if(!database.hasTable("tz_housingembodiedperperson"))
			database.addTable("tz_housingembodiedperperson", new java.util.HashMap());
		if(!database.hasTable("tz_housingembodiedperdwelling"))
			database.addTable("tz_housingembodiedperdwelling", new java.util.HashMap());
		for(int z = 0; z < ntz; z++)
		{
			//count the number of dwellings of each type
			double pop = database.lookup("tz_population", ""+z);
			double numberofhouseholds = nbrhh[z];
			
			if(pop > 0.0 && numberofhouseholds > 0.0)
			{
				double pcthighrise = database.lookup("scenario/pctofflatsthatarehighrise", ""+z);
				double bldg_embodiedmj = 0.0;
				for(int dw = 0; dw <= maxdwelltype; dw++)
					bldg_embodiedmj += housingstock[z][dw]*(buildingembodiedGJ[dw]/buildinglife[dw]);
				//convert to MJ, as base numbers are stated in GJ  
				bldg_embodiedmj *= 1000.0;
				
				//adjust for high rise if we are doing that
				double origembodied = housingstock[z][ndw-1]*(buildingembodiedGJ[ndw-1]/buildinglife[ndw-1]);
				double adjfact = 1.0; //DEBUG TODO pcthighrise*highriseembodiedadjust+(1-pcthighrise);
				double newembodied = origembodied*adjfact;
				double toadd = (newembodied-origembodied)*1000.0;
				bldg_embodiedmj += toadd;
				
				
				database.replaceValue("tz_housingembodiedperperson", ""+z, new Double(bldg_embodiedmj/pop), false);
				database.replaceValue("tz_housingembodiedperdwelling", ""+z, new Double(bldg_embodiedmj/numberofhouseholds), false);
			}
		}

		
	 
		//NOTE: Hack no longer needed. New land release now done properly.
		//special HACK to scale back densities in Rouse Hill and 
		//Leppington, because I just shove all fringe development there
		//assume 15 people per hectare for the moment in these new areas
		
		//double peopleha = 15.0;
		//Debug.println("HACK -- scaling back Leppington and Rouse Hill to "+peopleha+" people/ha, hit enter to acknowledge that you know this hack is still in operation", Debug.CRITICAL);
		//try { new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine(); }
		//catch(IOException ioe) { throw new RuntimeException(ioe); }
		//database.replaceValue("tzpopdensity", "583", new Double(peopleha*100), true);
		//database.replaceValue("tzpopdensity", "750", new Double(peopleha*100), true);
		
		
		
		//now we can calculate car ownership
		if(!database.hasTable("tz_carsperperson"))
			database.addTable("tz_carsperperson", new HashMap());
		for(int z = 0; z < ntz; z++)
		{
			try {
				double numberofhouseholds = nbrhh[z];
				double tzcarsperhh = TransportEnergyModule.getCarsPerHH_simple(new Integer(z), database);
				double pop = database.lookup("tz_population", ""+z);
				database.replaceValue("tz_carsperhh", ""+z, new Double(tzcarsperhh), false);
				database.replaceValue("tz_carsperperson", ""+z, new Double((tzcarsperhh*numberofhouseholds)/pop), false);
			}
			catch(NoSuchEntryException nsee) {
				//stick values in even for invalid regions. Cant hurt
				database.replaceValue("tz_carsperhh", ""+z, new Double(Double.NaN), false);
			}
		}


		
		//now calculate projected transport energy
		if(!database.hasTable("ProjectedTransportEnergy_phh"))
			database.addTable("ProjectedTransportEnergy_phh", new java.util.HashMap());
		if(!database.hasTable("ProjectedTransportEnergy_pp"))
			database.addTable("ProjectedTransportEnergy_pp", new java.util.HashMap());
		for(int z = 0; z < ntz; z++)
		{
			try {
				double transportmj = TransportEnergyModule.calcEnergyForRegion(new Integer(z), database);
				database.replaceValue("ProjectedTransportEnergy_phh", ""+z, new Double(transportmj), false);
				double personsperhh = database.lookup("tz_personsperdwelling", ""+z);
				database.replaceValue("ProjectedTransportEnergy_pp", ""+z, new Double(transportmj/personsperhh), false);
			}
			catch(NoSuchEntryException nsee) {
				//stick values in even for invalid regions. Cant hurt
				database.replaceValue("ProjectedTransportEnergy_phh", ""+z, new Double(Double.NaN), false);
			}
		}
		
		
		//now we calculate projected housing energy
		if(!database.hasTable("ProjectedInDwellingEnergy_phh"))
			database.addTable("ProjectedInDwellingEnergy_phh", new java.util.HashMap());
		if(!database.hasTable("ProjectedInDwellingEnergy_pp"))
			database.addTable("ProjectedInDwellingEnergy_pp", new java.util.HashMap());
		if(!database.hasTable("ProjectedInDwellingWater_phh"))
			database.addTable("ProjectedInDwellingWater_phh", new java.util.HashMap());
		if(!database.hasTable("ProjectedInDwellingWater_pp"))
			database.addTable("ProjectedInDwellingWater_pp", new java.util.HashMap());
		for(int z =0; z < ntz; z++)
		{
			double zonetotal = 0.0;
			double zonecount = 0.0;
			double zonewatertotal = 0.0;
			for(int hh=0; hh < nhh; hh++)
				for(int dw = 0; dw < ndw; dw++) 
				{
					double pcthighrise = 0.0;
					if(dw == maxdwelltype) {
						try {
							pcthighrise = database.lookup("scenario/pctofflatsthatarehighrise", ""+z);
						}
						catch(NoSuchEntryException nsee) {}
					}
					
					for(int inc = 0; inc < ninc; inc++) 
					{
						if(dist[z][hh][dw][inc] == 0.0)
							continue;
						
						//DEBUG TODO FIXME
						double openergy = Double.NaN;// DEBUG HousingEnergyModule.calcEnergyForHousehold(hh, inc, dw, z, ibraks)*dist[z][hh][dw][inc];
						//adjust for high rise, if we are doing that
						if(dw == maxdwelltype)
							openergy *= Double.NaN; // FIXME pcthighrise*this.highriseoperationaladjust+(1-pcthighrise);
						zonetotal += openergy;
							
						zonecount += dist[z][hh][dw][inc];
						zonewatertotal += HousingEnergyModule.calcWaterForHousehold(hh, inc, dw, z, ibraks)*dist[z][hh][dw][inc];
					}
				}
			if(Math.abs(zonecount-nbrhh[z]) > 0.001) 
				throw new RuntimeException("Recalculated what should be the same value, and its different!!!!!");
			
			if(zonecount > 0) {
				zonetotal /= zonecount;
				zonewatertotal /= zonecount;
			}
			
			if(zonetotal > 0.0) {
				database.replaceValue("ProjectedInDwellingEnergy_phh", ""+z, new Double(zonetotal), false);
				double personsperhh = database.lookup("tz_personsperdwelling", ""+z);
				database.replaceValue("ProjectedInDwellingEnergy_pp", ""+z, new Double(zonetotal/personsperhh), false);

				database.replaceValue("ProjectedInDwellingWater_phh", ""+z, new Double(zonewatertotal), false);
				database.replaceValue("ProjectedInDwellingWater_pp", ""+z, new Double(zonewatertotal/personsperhh), false);
			}
			else { //dont include zones without any people
				database.replaceValue("ProjectedInDwellingEnergy_phh", ""+z, new Double(Double.NaN), false);
				database.replaceValue("ProjectedInDwellingEnergy_pp", ""+z, new Double(Double.NaN), false);
				database.replaceValue("ProjectedInDwellingWater_phh", ""+z, new Double(Double.NaN), false);
				database.replaceValue("ProjectedInDwellingWater_pp", ""+z, new Double(Double.NaN), false);
			}
			
		}
		
		
		
		
		

		//now we add operational and transport together for a total 
		if(!database.hasTable("ProjectedTotalEnergy_phh"))
			database.addTable("ProjectedTotalEnergy_phh", new java.util.HashMap());
		if(!database.hasTable("ProjectedTotalEnergy_pp"))
			database.addTable("ProjectedTotalEnergy_pp", new java.util.HashMap());
		if(!database.hasTable("PropTransportEnergyOfTotal_phh"))
			database.addTable("PropTransportEnergyOfTotal_phh", new java.util.HashMap());
		double totalenergy = 0.0;
		double totaltransport = 0.0;
		double totalindwelling = 0.0;
		double totalhouseholds = 0.0;
		for(int z =0; z < ntz; z++)
		{
			double indwell = database.lookup("ProjectedInDwellingEnergy_phh", ""+z);
			double transport = database.lookup("ProjectedTransportEnergy_phh", ""+z);
			Debug.println("tz: "+z+"  indwell: "+indwell+" transport: "+transport, Debug.IMPORTANT);
			double hh = nbrhh[z];
			if(Double.isNaN(indwell) && Double.isNaN(transport))
				continue;
			else if((Double.isNaN(indwell) || Double.isNaN(transport)) && hh > 0)
					throw new RuntimeException("Missing indwell or transport energy estimate for zone "+z+", which has "+nbrhh[z]+" households");
			
			if(hh <= 0.0001) //dont bother about one thousandth of a household or less
				continue;
			
			double combined = indwell+transport;
			Debug.println("tz: "+z+" combined: "+combined+" hh: "+hh, Debug.IMPORTANT);
			if(Double.isNaN(hh))
				throw new RuntimeException("NaN value for nhh in tz "+z);
			totalhouseholds += hh;
			totaltransport += hh*transport;
			totalindwelling += hh*indwell;
			totalenergy += hh*combined;
			double personsperhh = database.lookup("tz_personsperdwelling", ""+z); 
			database.replaceValue("ProjectedTotalEnergy_phh", ""+z, new Double(combined), false);
			database.replaceValue("ProjectedTotalEnergy_pp", ""+z, new Double(combined/personsperhh), false);
			database.replaceValue("PropTransportEnergyOfTotal_phh", ""+z, new Double(transport/combined), false);
			
		}
		
		//print out total energy for the city and per person
		Debug.println("Total energy (indwell) for the city is "+totalindwelling, Debug.IMPORTANT);
		Debug.println("Total energy (transport) for the city is "+totaltransport, Debug.IMPORTANT);
		Debug.println("Total energy (combined) for the city is "+totalenergy, Debug.IMPORTANT);
		Debug.println("per household in-dwelling energy is "+totalindwelling/totalhouseholds, Debug.IMPORTANT);
		Debug.println("per household transport energy is "+totaltransport/totalhouseholds, Debug.IMPORTANT);
		Debug.println("per household energy (combined) is "+totalenergy/totalhouseholds, Debug.IMPORTANT);
		
		
		//now we would like to work out the marginal effect of moving a fixed 
		//household from one region to another, but we cannot do this because
		//we dont have a household travel model. 
		
		
		
		
		
		//TODO other measures could be calculated
		//like embodied and the like
		
		
		
	}*/
	

}
