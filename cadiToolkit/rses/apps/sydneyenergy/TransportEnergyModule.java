package rses.apps.sydneyenergy;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashMap;
import java.util.Random;

import rses.Debug;
import rses.util.Util;



/** A module to calculate the transport energy
 *  for an urban area.
 * 
 * @author peterr
 *
 */
public class TransportEnergyModule extends DisplayableModel
{
	private DataStore database;
	private Integer[] regions; 	
	private HashMap tenergy = null;
	private static double annualvkttomjconversionfactor = Double.NaN;
	private static double mjperkmonpublictransport = Double.NaN;
	private static double carembodiedmjperannum = Double.NaN;
	public static final String databasename = "TransportEnergyModel";
	 
	
	public TransportEnergyModule(DataStore database, Integer[] regionids)
	throws IOException
	{
		this.database = database;
		this.regions = regionids;
		
		String annualvkttomjconversionfactorstr = (String) database.lookupArbitrary("globals", "annualvkttomjconversionfactor");
		
		try {
		annualvkttomjconversionfactor = Double.parseDouble(annualvkttomjconversionfactorstr);
		mjperkmonpublictransport = Double.parseDouble((String) database.lookupArbitrary("globals", "mjperpassengerkm"));
		carembodiedmjperannum = Double.parseDouble((String) database.lookupArbitrary("globals", "carembodiedmjperannum"));
		}
		catch(Exception e) {
			Debug.print("Error reading conversion factors from file... press ENTER to continue anyway ", Debug.CRITICAL);
			new BufferedReader(new InputStreamReader(System.in)).readLine();
		}
		
		HashMap nullmodel = new HashMap();
		for(int i = 0; i < regionids.length; i++)
			nullmodel.put(""+regionids[i], new Double(Double.NaN));
		
		//add a table to the database with NaN values
		database.addTable(databasename, nullmodel);
	}

	
	
	
	
	public void recalculate()
	{
		//keep a cached copy of the current model
		//to save a lookup
		this.tenergy = calcEnergyByRegion();
		
		//now go through and update the database
		database.replaceTable(databasename, tenergy);
		
		//invalidate cached display of data
		bim = null;
	}
	
	
	
	
	
	private BufferedImage bim = null;
	public BufferedImage getDisplay(int[][] membership, int[][] placemarks)
	{
		if(bim == null)
		{
			bim = displayByRegion(membership[0].length, 
					membership.length, 
					tenergy,
					membership, placemarks, Color.blue, Color.red);
		}
		return bim;
		
	}
	
	
	
	
	
	/** Return a HashMap that maps region identifiers
	 * to total energy use in that region.
	 * 
	 * i.e. each key is an Integer (region identifier), and
	 * each value is a Double (total transport energy use in
	 * that region). Note that this method does <i>not</i>
	 * calculate per-capita transport energy use. Divide
	 * by region populations to do that.
	 * 
	 * @return
	 */
	public HashMap calcEnergyByRegion()
	{
		HashMap result = new HashMap();
		//go through each region and calculate the energy
		for(int i =0; i < regions.length; i++)
		{
			Integer key = regions[i];
			double transportenergy = calcEnergyForRegion(key);
			result.put(""+key, new Double(transportenergy));			
		}
		return result;

	}


	public static double calcEnergyForRegion(Integer regionid, DataStore database)
	{
		//return calcEnergyForRegion_modelwettpt(regionid);
		double res = Double.NaN;
		try { res = calcEnergyForRegion_simple(regionid, database); }
		catch(NoSuchEntryException e) {	
			Debug.println("No entry for region "+regionid+" found", Debug.EXTRA_INFO);
		}
		return res;		
	}
	
	public double calcEnergyForRegion(Integer regionid)
	{
		return calcEnergyForRegion(regionid, database);
	}
	
	
	
	
	
	/** Reruns the household car ownership model, and updates that
	 *  table in the database for this region only.
	 *  
	 *  Also returns the new value for this region (i.e. the value just
	 *  put in the database)
	 * 
	 * @param regionid
	 * @return
	 */
	public double updateCarsPerHH_simple(Integer regionid)
	{
		double tzcarsperhh = getCarsPerHH_simple(regionid, database);
		String key = regionid.toString();
		//dont barf if there isnt already a carsperhh value
		database.replaceValue("tz_carsperhh", key, new Double(tzcarsperhh), false);
		return tzcarsperhh;
	}
	
	
	
	
	
	public static double getSimpleModeSharePrediction(double logpopden, double distcbd)
	{
		double carpct = Math.log(2.2236 -0.0678*logpopden + 0.0106*distcbd);
		carpct = Math.max(0.01, Math.min(1, carpct));
		return carpct;
	}
	
	
	
	/** Calculate transport energy use for the household, given that they are of this type,
	 * on this income, in this dwelling, in this travelzone, with this number of cars, etc etc
	 * 
	 * @param hhtype
	 * @param ibrak
	 * @param dwtype
	 * @param tz
	 * @param incomebraks
	 * @param numcars
	 * @param database
	 * @return
	 * 
	 * return values are [petrol, public-transport, embodied]
	 */
	public static double[] calcTransportEnergyForHousehold(int hhtype, int ibrak, int dwtype, int tz, double[] incomebraks, int numcars, DataStore database)
	{
		//numcarsboostfact
		//the boost factor to get to 2006 aggregates reported by the HTS
		double numcarsboostfact = 1.08;
		
		//
		double vktboostfact = 1.27;
		double pubtranboostfact = 2.4;
		
		String key = ""+tz;		
		double logpopden = Math.log(database.lookup("tzpopdensity", key)+1);
		double distcbd_km = database.lookup("distcbd",key);
		
		//first of all, work out the no-trip percentage
		double notripexpfact = 2.399+0.004*Math.sqrt(incomebraks[ibrak])+0.663*numcars*numcarsboostfact;
		if(dwtype == 0) notripexpfact += -0.509;
		else if(dwtype == 1) notripexpfact += -0.590;
		if(hhtype == 0 || hhtype == 1) notripexpfact += -0.498;
		else if(hhtype == 2 || hhtype == 3) notripexpfact += 0.249;
		else if(hhtype == 4) notripexpfact += 0.734;
		else if(hhtype == 5) notripexpfact += 0.046;
		else if(hhtype == 6) notripexpfact += 0.249;
		double notripprob = 1/(1+Math.exp(notripexpfact));
		
		Debug.println("no-trip % for "+numcars+" is "+notripprob, Debug.EXTRA_INFO);
		
		double numcarsfullownership = 1.0;
		if(hhtype == 0 || hhtype == 1) /* dont adjust */;
		else if(hhtype == 2 || hhtype == 3 || hhtype == 4) numcarsfullownership = 2.0;
		else if(hhtype == 5) numcarsfullownership = 3.0;
		else if(hhtype == 6) numcarsfullownership = 1.33333;
		else if(hhtype == 7) numcarsfullownership = 2.7;
		
			
		//first lets use the vkt model				

		double sqrtvkt = 2.574+0.044*distcbd_km-0.373*logpopden+0.006*Math.sqrt(incomebraks[ibrak])+1.702*numcarsboostfact*numcars;
		double sqrtvktfullcarownership = 2.574+0.044*distcbd_km-0.373*logpopden+0.006*Math.sqrt(incomebraks[ibrak])+1.702*numcarsboostfact*numcarsfullownership;
		if(hhtype == 0 || hhtype == 1) sqrtvkt +=  0.271;
		else if(hhtype == 2 || hhtype == 3) sqrtvkt += 0.543;
		else if(hhtype == 4) sqrtvkt += 1.101;
		else if(hhtype == 5) sqrtvkt += 0.689;
		else if(hhtype == 6) sqrtvkt += 0.318;
			
		 
		double meankm = 0.0;
		if(numcars > 0)
			meankm = getSqrtDistMean(sqrtvkt, 3.08)*vktboostfact;
		double meankmfullownership = getSqrtDistMean(sqrtvktfullcarownership, 3.08)*vktboostfact;
		double petrolmj = meankm*365*annualvkttomjconversionfactor*(1-notripprob);
		double carembodiedmj = numcars*numcarsboostfact*carembodiedmjperannum;

		
		//And, now, if we are applying a city-wide transit infrastructure
		//correction, we apply that.
		double modeshareshift = 0.0;
		String modeshareshiftstr = (String) database.lookupArbitrary("globals", "transitmodeshareshift");
		if(modeshareshiftstr != null)
			modeshareshift = Double.parseDouble(modeshareshiftstr);
		double vktreducepct = modeshareshift;
		meankm *= (1-vktreducepct);
		petrolmj *= (1-vktreducepct);
		

		

		//now do public transport... this needs to include a correction
		//factor to bring the private/public modesplit back to what
		//it is in the data.
		double kmdeficit = Math.max(meankmfullownership-meankm, 0);
		double predmodesplit = getSimpleModeSharePrediction(logpopden, distcbd_km);
		double pubtrankm = kmdeficit*(1-predmodesplit)*pubtranboostfact;  
		double ptmj = pubtrankm*365*mjperkmonpublictransport*(1-notripprob);
		
		
		
		
		return new double[] {petrolmj, ptmj, carembodiedmj, meankm*365, pubtrankm*365, numcars*numcarsboostfact};
		
	}
	
	
	public static final int NUMSAMPLESFORSQRTDISTAVG = 2000; //50000;
	public static double getSqrtDistMean(double sqrtmean, double sqrtstddev)
	{
		double tot = 0.0;
		Random r = new Random();
		for(int i = 0; i < NUMSAMPLESFORSQRTDISTAVG; i++)
		{
			double val = Math.pow(Math.max(0, (sqrtmean + r.nextGaussian()*sqrtstddev)), 2);
			tot += val;
		}
		return tot/NUMSAMPLESFORSQRTDISTAVG;
	}
	
	
	
	
	/** Get the household transport energy given we dont know how many cars they have.
	 * This means taking the estimate over all the possible levels of car ownership.
	 * 
	 * @param hhtype
	 * @param ibrak
	 * @param dwtype
	 * @param tz
	 * @param incomebraks
	 * @param database
	 * @return an array with [petrol, public transport, embodied] energy use
	 * 
	 */
	public static double[] calcTransportEnergyForHousehold(int hhtype, int ibrak, int dwtype, int tz, double[] incomebraks, DataStore database)
	{
		//work out car ownership probs for the household
		double[] carownprobs = getcarownershipprobs(hhtype, ibrak, dwtype, incomebraks, new Integer(tz), database);
		
		//and now, calculate transport energy for each of those eventualities
		double[] totals = new double[6];
		for(int numcars = 0; numcars < carownprobs.length; numcars++)
		{
			double[] mjperannum = calcTransportEnergyForHousehold(hhtype, ibrak, dwtype, tz, incomebraks, numcars, database);
			if(Debug.equalOrMoreVerbose(Debug.EXTRA_INFO)) {
				Debug.println("with "+numcars+" cars ("+Util.getTruncatedReal(carownprobs[numcars]*100, 4)+"%):   "+mjperannum[0]+" "+mjperannum[1]+" "+mjperannum[2], Debug.EXTRA_INFO);
			}
			for(int i = 0; i < totals.length; i++)
				totals[i] += mjperannum[i]*carownprobs[numcars];
		}
		
		return totals;
	}

	
	//truncate car ownership at this level
	public static final int MAXCARS = 6;
	public static final double[] carownconsts = {
		-2.549,
		0.679,
		3.336,
		4.894,
		6.218,
		7.432,
		8.914,
		9.540,
		10.500,
		10.812
	};
	
	public static double[] getcarownershipprobs(int hhtype, int ibrak, int dwtype, double[] incomebraks, Integer regionid, DataStore database)
	{
		String key = regionid.toString();
		
		//this is the ordinal logistic regression model
		//of car ownership from HTS data.
		double logpopden = Math.log(database.lookup("tzpopdensity", key)+1);
		double distcbd_km = database.lookup("distcbd",key);
		
		
		double[] cumulativeprobs = new double[MAXCARS+1];
		
		
		for(int numcars = 0; numcars < cumulativeprobs.length; numcars++)
		{
			double constant = carownconsts[numcars];
			double distcbdfact = distcbd_km*0.009;
			double popdenfact = logpopden*-0.289;
			double dwellfact = dwtype == 0 ? -0.467: dwtype == 1? -1.229 : -1.696;
			double incomefact = 0.006*Math.sqrt(incomebraks[ibrak]);
			double hhfact = hhtype == 0 ? 0.131 :
				            hhtype == 1 ? 0.131 :
				            hhtype == 2 ? 1.541 :
				            hhtype == 3 ? 1.541 :
				            hhtype == 4 ? 1.856 :
				            hhtype == 5 ? 3.184 :
				            hhtype == 6 ? 1.188 : 
				            /* hhtype == 7 */	1.975;
				            
			double result = 1/(1+Math.exp(-constant + distcbdfact + popdenfact + dwellfact + incomefact + hhfact));
			cumulativeprobs[numcars] = result;
		}
		
		//ok, since we truncate, we need to make sure that the 
		//pdf sums to 1
		cumulativeprobs[cumulativeprobs.length-1] = 1.0;
		
		//now translate the cumulative probs into the marginal probs
		for(int i = cumulativeprobs.length-1; i > 0; i--)
			cumulativeprobs[i] = cumulativeprobs[i]-cumulativeprobs[i-1];
		
		//and we are done!
		return cumulativeprobs;
	}
	
	
	public static double getCarsPerHH_simple(Integer regionid, DataStore database)
	{	
		/* THIS MODEL IS BASED ON THE TDC DATASET USED FOR VKT 
		double constant = 0.88;
		double distcbd_km = database.lookup("htsbaseddata/tz_distcbd",key);
		double prop_singlepersonhh = database.lookup("tz_prop_singlepersonhh", key);
		double propcouplechhild = database.lookup("htsbaseddata/tz_propcouplewchild.tab", key);
		double medianincome_100k = database.lookup("htsbaseddata/tz_medianhouseholdincome", key)/100000.0;
		double logpopden = Math.log(database.lookup("tzpopdensity", key)+1);
		double tzcarsperhh = constant+distcbd_km*0.009+prop_singlepersonhh*-.696+
		propcouplechhild*0.873+medianincome_100k*0.816+logpopden*-.052;
		*/
		
		
		//this model is based on ABS data (2001) aggregated up to the TZ level
		//from CD level, with an r^2 of 0.899.
		String key = regionid.toString();
		double constant = 0.964;
		//double distcbd_km = database.lookup("htsbaseddata/tz_distcbd",key);
		double distcbd_km = database.lookup("distcbd",key);
		double propcouplechhild = database.lookup("tz_propcouplewkids", key);
		double medianincome_100k = (database.lookup("tz_medhhincome", key)*52)/100000.0;
		double logpopden = Math.log(database.lookup("tzpopdensity", key)+1);
		double tzcarsperhh = constant + distcbd_km*0.003 + propcouplechhild*1.628
		+ -0.088*logpopden + medianincome_100k*0.803;
		
		
		return tzcarsperhh;
	}
	
	
	//a simple model that relies only on easily available
	//and updatable data
	public static double calcEnergyForRegion_simple(Integer regionid, DataStore database)
	{
		String key = regionid.toString();
	
		/*
		
		//r^2 is 0.822
		double constant = 2.289;
		//double distcbd_km = database.lookup("htsbaseddata/tz_distcbd",key);
		double distcbd_km = database.lookup("distcbd",key);
		double propcouplewchild = database.lookup("tz_propcouplewkids", key);
		double medianincome_100k = (database.lookup("tz_medhhincome", key)*52)/100000.0;
		double logpopden = Math.log(database.lookup("tzpopdensity", key)+1);
		double tzcarsperhh = database.lookup("tz_carsperhh", key);
		
		tzcarsperhh = getCarsPerHH_simple(regionid, database);
		
		double sqrtvkt = constant+medianincome_100k*0.486+
		logpopden*-0.083+distcbd_km*0.042+tzcarsperhh*2.171+
		propcouplewchild*1.163;
		
		double vkt = sqrtvkt*sqrtvkt;
		if(Double.isNaN(vkt)) {
			Debug.println("NaN for VKT figure in zone "+key+" vals are:", Debug.IMPORTANT);
			Debug.println("distcbd: "+distcbd_km, Debug.IMPORTANT);
			Debug.println("propcouplewchild: "+propcouplewchild, Debug.IMPORTANT);
			Debug.println("medianincome: "+medianincome_100k, Debug.IMPORTANT);
			Debug.println("logpopden: "+logpopden, Debug.IMPORTANT);
			Debug.println("carsperhh: "+tzcarsperhh, Debug.IMPORTANT);
		}
		
		
		//return (vkt*3.65*9*1.5)/(medianincome_100k*1000.0); //this is prop of income in petrol
		//return vkt*365; 
		
		//TROY ESTIMATE FROM SA STUDY, 1.17 converts back to primary
		//double energy = vkt*365*4.07*1.17;
		
		
		//GG ESTIMATE FOR SYDNEY, with Sydney PER VEHICLE OCCUPANCY
		//REFS ARE Travel in Newcastle and Woolongong, TPDC 2002.
		//3.97 value is from Glazebrook's study, which is a bit old now
		//double energy = vkt*365*3.97*1.45;
		//3.66 value is from Kenworthya and Laube 2001, with 1.45 being
		//GLazebrook's conversion factor for extraction, refining, transport,
		//and other losses.
		
		//CURRENT
		double energy = vkt*365*annualvkttomjconversionfactor;
		return energy;
		*/
		
		return 1;
			
	}
	
	
	//this model uses ettpt and a few other variables that really arent
	//that useful in a full blown model
	public double calcEnergyForRegion_modelwettpt(Integer regionid)
	{
		//just run the vkt regression model at the moment.
		//
		//TODO could read the model from a file instead of
		//hard coding it here.
		//DEBUG -- these are all hts/tpdc variables, not abs derived ones
		String key = regionid.toString();
		double constant = 1.86935042470475;
		double nvehicles = database.lookup("tz_carsperhousehold", key);
		double income = database.lookup("tz_medianhouseholdincome", key);
		double propcouplechild = database.lookup("tz_propcouplewchild", key);
		double hdensity = database.lookup("tz_tpdchdensity", key);
		double distcbd = database.lookup("tz_distcbd", key);
		double comprox = database.lookup("tz_comproxinvsqlogscale", key);
		double ettall = database.lookup("tz_ettallpt", key);
		
		double hhvkt = Math.pow(constant + 1.88949890796981*nvehicles +
			5.93558123703205E-006*income +
			1.11900262539602*propcouplechild +
			-0.00301482135480792*hdensity +
			0.0333740376144108*distcbd +
			-0.14413552863011*comprox +
			0.00768681065528899*ettall, 2.0);
		
		//now convert to MJ
		//
		//first times by 365 to get yearly km's
		//(this is an approximation, perhaps as
		//I dont know if TPDC data was for all week)
		//
		//convert to primary energy per annum
		return ((hhvkt*365))*annualvkttomjconversionfactor;
		//return hhvkt;
		
	}
	
	
	
	private static void testDisaggregateTransportEnergy(String[] args) throws IOException
	{
		//BufferedReader rdr = new BufferedReader(new InputStreamReader(System.in));
		//System.out.println("Enter hhtype dwtype income distcbd popden");
		//String line = rdr.readLine();
		//String[] words = Util.getWords(line);
		String[] words = args;
		int hhtype = Integer.parseInt(words[0]);
		int dwtype = Integer.parseInt(words[1]);
		int income = Integer.parseInt(words[2]);
		double distcbd = Double.parseDouble(words[3]);
		double popden = Double.parseDouble(words[4]);
		
		DataStore database = new DataStore();
		database.replaceValue("distcbd", "0", new Double(distcbd), false);
		database.replaceValue("tzpopdensity", "0", new Double(popden), false);
		
		Debug.setVerbosityLevel(Debug.EXTRA_INFO);
		//ok, now do the calculations for this household
		TransportEnergyModule m = new TransportEnergyModule(database, new Integer[] {new Integer(0)});
		double[] mjpa = calcTransportEnergyForHousehold(hhtype, 0, dwtype, 0, new double[] {income}, database);
		
		System.out.println("TOTAL PETROL MJ p.a. == "+mjpa[0]);
		System.out.println("TOTAL PUBTRAN MJ p.a. == "+mjpa[1]);
		System.out.println("TOTAL EMBODIED MJ p.a. == "+mjpa[2]);
		System.out.println("TOTAL VKT p.a. == "+mjpa[3]);
		System.out.println("TOTAL PASSENGER-KM p.a. == "+mjpa[4]);
		System.out.println("TOTAL CARS == "+mjpa[5]);
		System.out.println("% PT KM == "+mjpa[4]/(mjpa[4]+mjpa[3]));
		System.out.println("TOTAL OPERATIONAL TRANSPORT ENERGY "+(mjpa[0]+mjpa[1]));
		
		//now get in-dwelling energy use
		HousingEnergyModule he = new HousingEnergyModule(database, new Integer[] {new Integer(0)});
		int ibrak = 5;
		if(income < 33800) ibrak = 0;
		else if(income < 52000) ibrak = 1;
		else if(income < 72800) ibrak = 2;
		else if(income < 104000) ibrak = 3;
		else if(income < 156000) ibrak = 4;
		
		double[] mjpa_house = HousingEnergyModule.calcEnergyForHousehold_roughguessonly(hhtype, ibrak, dwtype, new double[] {income}, 0.0, database);
		System.out.println("TOTAL EMBODIED ENERGY p.a. == "+mjpa_house[1]);
		System.out.println("TOTAL DELIVERED ENERGY p.a. == "+mjpa_house[0]);
		
		System.out.println("TOTAL ENERGY TRANSPORT AND INDWELL, OP+EMB "+(mjpa[0]+mjpa[1]+mjpa[2]+mjpa_house[0]+mjpa_house[1]));
		
		System.out.println(mjpa[5]+" "+mjpa[3]+" "+mjpa[4]+" "+(mjpa[0]+mjpa[1])+" "+mjpa_house[0]+" "+mjpa[2]+" "+mjpa_house[1]+" "+(mjpa[0]+mjpa[1]+mjpa[2]+mjpa_house[0]+mjpa_house[1]));
	}
	
	
	public static void calculatePublicTransportSubstitutionFactor() throws IOException
	{
		DataStore database = new DataStore();
		TransportEnergyModule tm = new TransportEnergyModule(database, new Integer[] {new Integer(0)});
		HousingEnergyModule he = new HousingEnergyModule(database, new Integer[] {new Integer(0)});
		
		double[] incomes = new double[6];
		double[][] ranges = new double[6][2];
		for(int i = 0; i < incomes.length; i++) 
			incomes[i] = Double.parseDouble((String) database.lookupArbitrary("globals", "ibracket"+i));
		
		
		//actually need to read in the hhbytzfile, and calculate
		//for all households..... from this, can check if the
		//overall mode split is right, and if the vkt result
		//looks right (should be right to within a few percent).

		double distcbdsum = 0.0;
		double popdensum = 0.0;
		double[] transportsums = new double[6];
		double[] indwellsums = new double[2];
		double totalcount = 0.0;
		BufferedReader rdr = new BufferedReader(new FileReader("hhbytz_FULL.dat"));
		String line = rdr.readLine();
		
		while(line != null)
		{
			String[] words = Util.getWords(line);
			
			int tz = Integer.parseInt(words[0]);
			int hhtype = Integer.parseInt(words[1]);
			int dwtype = Integer.parseInt(words[2]);
			int ibrak = Integer.parseInt(words[3]);
			double count = Double.parseDouble(words[4]);
			if(count < 0.01)
			{
				line = rdr.readLine();
				continue;
			}
			
			//ok, now work out vkt and passenger km
			double[] result = calcTransportEnergyForHousehold(hhtype, ibrak, dwtype, tz, incomes, database);
			for(int i = 0; i < 6; i ++)
				transportsums[i] += result[i]*count;
			
			double pcthighrise = database.lookup("pctofflatsthatarehighrise", tz+"");
			System.err.println("%highrise flats for this are "+pcthighrise);
			double[] houseresult = HousingEnergyModule.calcEnergyForHousehold_roughguessonly(hhtype, ibrak, dwtype, incomes, pcthighrise, database);
			indwellsums[0] += houseresult[0]*count;
			indwellsums[1] += houseresult[1]*count;
			
			double distcbd = database.lookup("distcbd", ""+tz);
			double popden = database.lookup("tzpopdensity", ""+tz);
			distcbdsum += distcbd*count;
			popdensum += popden*count;
			
			totalcount += count;
			
			
			line = rdr.readLine();
			System.out.println(totalcount);
		}
		
		System.out.println("TOTAL VKT IS "+transportsums[3]);
		System.out.println("TOTAL PASSENGER KM IS "+transportsums[4]);
		System.out.println("TOTAL CARS IS "+transportsums[5]);
		System.out.println();
		System.out.println("PER HH VKT IS "+transportsums[3]/totalcount);
		System.out.println("PER HH PASS-KM IS "+transportsums[4]/totalcount);
		System.out.println("CARS PER HH IS "+transportsums[5]/totalcount);
		System.out.println();
		System.out.println("DISTCBD "+distcbdsum/totalcount);
		System.out.println("POPDEN  "+popdensum/totalcount);
		System.out.println();
		System.out.println("MJ TRANSPORT OPERATIONAL ENERGY PER HOUSEHOLD "+(transportsums[0]+transportsums[1])/totalcount);
		System.out.println("MJ INDWELL OPERATIONAL PER HOUSEHOLD "+indwellsums[0]/totalcount);
		System.out.println("MJ TRANSPORT PER HOUSEHOLD "+(transportsums[0]+transportsums[1]+transportsums[2])/totalcount);
		System.out.println("MJ DWELL ENERGY PER HOUSEHOLD "+(indwellsums[0]+indwellsums[1])/totalcount);
		System.out.println("TOTAL MJ PER HOUSEHOLD "+(transportsums[0]+transportsums[1]+transportsums[2]+indwellsums[0]+indwellsums[1])/totalcount);
		
	}
	
	
	public static void main(String[] args) throws Exception
	{
		//testDisaggregateTransportEnergy(args);
		calculatePublicTransportSubstitutionFactor();
	}
	
}


