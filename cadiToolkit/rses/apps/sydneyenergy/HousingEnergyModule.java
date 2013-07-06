package rses.apps.sydneyenergy;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

import rses.Debug;






public class HousingEnergyModule extends DisplayableModel
{
	private DataStore database;
	private Integer[] regions;
	private HashMap henergy = null;
	private static double airconpenetration = Double.NaN;
	private static double indwellprimaryconversionfactor = Double.NaN;
	private static double highrisembodiedadjustfact = Double.NaN;
	private static double highriseoperationaladjustfact  = Double.NaN;
	
	public static final String databasename = "HousingEnergyModel";
	
	public HousingEnergyModule(DataStore database, Integer[] regionids)
	throws IOException
	{
		try {
		airconpenetration = Double.parseDouble((String) database.lookupArbitrary("globals", "airconpenetration"));
		String conversionfactstr = (String) database.lookupArbitrary("globals", "deliveredtoprimaryindwellingconverter");
		indwellprimaryconversionfactor = Double.parseDouble(conversionfactstr);
		highrisembodiedadjustfact = Double.parseDouble((String) database.lookupArbitrary("globals", "highriseembodiedfactor"));
		highriseoperationaladjustfact = Double.parseDouble((String) database.lookupArbitrary("globals", "highriseoperationalfactor"));
		}
		catch(Exception e)
		{
			Debug.print("Error reading conversion factors from file... press ENTER to continue anyway ", Debug.CRITICAL);
			new BufferedReader(new InputStreamReader(System.in)).readLine();
		}
		
		this.database = database;
		this.regions = regionids;
		
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
		this.henergy = calcEnergyByRegion();
		
		//now go through and update the database
		database.replaceTable(databasename, henergy);
		
		//invalidate cached display of data
		bim = null;
	}
	
	
	public HashMap calcEnergyByRegion()
	{
		HashMap result = new HashMap();
		//go through each region and calculate the energy
		for(int i =0; i < regions.length; i++)
		{
			Integer key = regions[i];
			double housingenergy = calcEnergyForRegion(key);
			result.put(""+key, new Double(housingenergy));			
		}
		return result;

	}
	
	public double calcEnergyForRegion(Integer regionid)
	{
		//at the moment its just from a static file.
		double val = Double.NaN;
		if(!this.database.hasTable("tz_energyaustralia"))
			return Double.NaN;
		try {
			val = this.database.lookup("tz_energyaustralia", regionid.toString());
		}
		catch(NoSuchEntryException nsee) {
			Debug.println("No energy entry for region "+regionid, Debug.IMPORTANT);
			return Double.NaN;
		}
		
		//turn it into megajoules per household
		//return val*3.6*3.5; //3.6 MJ in a kwH, and use a conversion factor of 3.5 for electricity to primary energy...
		
		//turn it into megajoules per household
		return val*3.6*4.2; //3.6 MJ in a kwH, and use conversion of 4.2 for electric and gas. This should be modified.
		//thats 4.54 for electric/coal. ??? for gas. 
	}
	
	
	public static double calcWaterForHousehold(int hhtype, int ibrak, int dwtype, int tz, double[] incomebraks)
	{
		return calcWaterForHousehold_roughguessonly(hhtype, ibrak, dwtype, tz, incomebraks);
	}
	
	public static double[] calcEnergyForHousehold(int hhtype, int ibrak, int dwtype, int tz, double[] incomebraks, double pcthighrise, DataStore database)
	{
		return calcEnergyForHousehold_roughguessonly(hhtype, ibrak, dwtype, incomebraks, pcthighrise, database);
	}
	
	/** Calculate the total household MJ of delivered energy.
	 * 
	 * @param hhtype
	 * @param ibrak
	 * @param dwtype
	 * @param tz
	 * @param incomebraks
	 * @return
	 */
	public static double[] calcEnergyForHousehold_roughguessonly(int hhtype, int ibrak, int dwtype, double[] incomebraks, double pcthighrise, DataStore database)
	{
		if(airconpenetration < 0)
			throw new RuntimeException("Air con penetration not set! Cannot run household energy model");
		
		//DEBUG TODO These have hard coded values for hhtype... dangerous!
		
		//just make a rough stab
		double logmj = 10.311+0.049*(Math.max(ibrak*2, 1));
		logmj += airconpenetration*0.052; //air-con adjustment
		if(hhtype == 0 || hhtype == 1) logmj += -0.375; 
		else if(hhtype == 2 || hhtype == 3) logmj += -0.23;
		else if(hhtype == 6) logmj += -0.147;
		else if(hhtype == 4 || hhtype == 5) logmj += 0.06;
		
		if(dwtype == 1) logmj += -0.104;
		else if(dwtype == 2) logmj += -0.37;
		else if(dwtype != 0) throw new RuntimeException("Unknown dwell type in energy model");

		double delivered = getExpMean(logmj, 0.45766)*indwellprimaryconversionfactor;
		if(dwtype == 2)
			delivered = delivered*(1-pcthighrise)+ pcthighrise*delivered*highriseoperationaladjustfact;
		
		//now do embodied for dwellings.
		double embodied = Double.parseDouble((String) database.lookupArbitrary("globals", "buildingembodiedGJ_"+dwtype));
		double maintainpct = Double.parseDouble((String) database.lookupArbitrary("globals", "embodiedmaintainencepct"));
		double dwelllife = Double.parseDouble((String) database.lookupArbitrary("globals", "buildinglife_"+dwtype));
		
		double mjperannumembodied = (1+maintainpct)*((embodied*1000.0)/dwelllife);
		if(dwtype == 2)
			mjperannumembodied = mjperannumembodied*(1-pcthighrise)+ pcthighrise*mjperannumembodied*highrisembodiedadjustfact;
				
		
		
		return new double[] {delivered, mjperannumembodied};
	}
	
	private static double getExpMean(double mean, double stddev)
	{
		double total = 0.0;
		java.util.Random r = new java.util.Random();
		//just use the same number of samples as we use in the transport
		//module
		for(int i = 0; i < TransportEnergyModule.NUMSAMPLESFORSQRTDISTAVG; i++)
			total += Math.exp(mean + r.nextGaussian()*stddev);
		
		return total/TransportEnergyModule.NUMSAMPLESFORSQRTDISTAVG;
	}

	
	public static double calcWaterForHousehold_roughguessonly(int hhtype, int ibrak, int dwtype, int tz, double[] incomebraks)
	{
		//DEBUG TODO These have hard coded values for hhtype... dangerous!
		
		//just make a rough stab
		double logwater = 5.68+0.016*(ibrak*2);

		if(hhtype == 0 || hhtype == 1) logwater += -0.743; 
		else if(hhtype == 2 || hhtype == 3) logwater += -0.332;
		else if(hhtype == 6) logwater += -0.173;
		else if(hhtype == 4 || hhtype == 5) logwater += 0.0;
		
		if(dwtype == 1) logwater += -0.276;
		else if(dwtype == 2) logwater += -0.255;
		else if(dwtype != 0) throw new RuntimeException("Unknown dwell type in energy model");

		return Math.exp(logwater); 
	}

	
	
	private BufferedImage bim = null;
	public BufferedImage getDisplay(int[][] membership, int[][] placemarks)
	{
		if(bim == null)
		{
			bim = displayByRegion(membership[0].length,
					membership.length, henergy,
					membership, placemarks, Color.blue, Color.red);
		}
		return bim;
		
	}
	
}