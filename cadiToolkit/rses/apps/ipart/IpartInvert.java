package rses.apps.ipart;
import java.io.*;
import java.util.*;

import rses.Model;
import rses.inverse.UserFunctionHandle;
import rses.inverse.powell.PowellMin;
import rses.inverse.util.JavaUserFunctionHandle;
import rses.math.DistributionStatistics;
import rses.math.MathUtil;
import rses.util.Util;



public class IpartInvert implements UserFunctionHandle
{
	public static final String[] HHTYPES = {"single", "couplenokids", "singleparent", "couplewkidslt15", "couplewkidsgt15", "other"};
	//public static final String[] DWELLTYPES = {"semi", "lowriseunit", "unit"};
	public static final String[] DWELLTYPES = {"semi", "unit"};
	public static final String[] OTHERVARS = {"income", "long", "hasgas", "numbedrooms", "numpeople"};

	public static final int MAXPOSTCODE = 2240; //postcodes above this arent considered to be sydney

	double[][] households = null;
	//double[][] energy = null;

	public IpartInvert(String path) throws Exception
	{

		System.err.println("In constructor");

		String hhfile = "households_eleconly.dat";

		//read in household data
		if(new File(hhfile).exists())
			System.err.println("found household data file");
		else
			System.err.println("Did not find household data file");
	
		households = readVectorsFromFile(new File(hhfile));
		System.err.println("Read household file");
	
		//now massage it into a format that suits us
		//# lon hhtype income numbedrooms dwelltype postcode elec gas
		//#lon hhtype income numbedrooms dwelltype hasgas hasaircon region #people postcode elec gas
		for(int i = 0; i < households.length; i++)
		{
			String hhstr = "";
			for(int j = 0; j < households[i].length; j++)
				hhstr += (households[i][j]+" ");
			System.out.println("household "+i+" is "+hhstr);

			double lon = households[i][0];
			int hhtype = (int) Math.rint(households[i][1]);
			if(hhtype <= 0)
				throw new RuntimeException("unknown hh type");
			else if(hhtype >= 1 && hhtype <= 3)
				hhtype = 0; //single person hh
			else if(hhtype > 3 && hhtype <= 6)
				hhtype = 2; //single parent
			else if(hhtype > 6 && hhtype <= 8)
				hhtype = 3; //couple w young kids
			else if(hhtype == 9)
				hhtype = 4; //couple w older kids
			else if(hhtype == 10 || hhtype == 12)
				hhtype = 1; //couple w no kids
			else
				hhtype = 5;

			double income = households[i][2];
			double numbedrooms = households[i][3];
			int dwelltype = (int) Math.rint(households[i][4]);
			if(dwelltype < 1 || dwelltype > 7)
				throw new RuntimeException("unknown dwelltype");
			else if(dwelltype == 1)
				dwelltype = 0;
			else if(dwelltype == 2) //combined shop/house
				throw new RuntimeException("You need to filter out all combined shop dwellings");
			else if(dwelltype == 3) //semi
				dwelltype = 1;
			else if(dwelltype == 4) //granny flat. exclude
				throw new RuntimeException("You need to filter out all granny flats");
			else if(dwelltype == 5 || dwelltype == 6) //1 or 2 or 3 storey flat
				dwelltype = 2;
			else if(dwelltype == 7)
				dwelltype = 2;
			else
				throw new RuntimeException("Unknown dwelltype");
				
			
			boolean hasaircon = households[i][6] == 1.0;
			
			double numpeople = households[i][8];
			
			double elec = households[i][10];
			//convert kwh into MJ
			elec = elec*3.6;
			double gas = households[i][11]; //gas is already in MJ
			int hasgas = (households[i][5] == 3.0) ? 0 : 1;
			double totalenergy = elec+gas;

			//households[i] = new double[] {lon, hhtype, income, numbedrooms, dwelltype, hasgas, totalenergy};
			households[i] = new double[] {lon, hhtype, income, numbedrooms, dwelltype, hasgas, numpeople, elec};
			hhstr = "";
			for(int j = 0; j < households[i].length; j++)
				hhstr += households[i][j]+" ";
			System.out.println("converted household "+i+" is "+hhstr);
		}



		//We dont need to rescale it, as this is done in a python script

		System.err.println("Finished constructor");

	}

	
	public double getPriorForModel(double[] m)
	{
		throw new UnsupportedOperationException();
	}
	
	public int getDimensionOfModelSpace()
	{
		return HHTYPES.length+DWELLTYPES.length+OTHERVARS.length; 
	}



	public double[][] getBoundsOnModelSpace()
	{
		double[][] res = new double[getDimensionOfModelSpace()][2];
		
		//other types
		for(int i =0; i < HHTYPES.length; i++) {
			res[i][0] = 5000.0; 
			res[i][1] = 50000.0; 
		}

		//multipliers for dwelling type (non-house only)
		for(int i = 0; i < DWELLTYPES.length; i++) {
			res[HHTYPES.length+i][0] = 0.5; 
			res[HHTYPES.length+i][1] = 2.0;
		}

		int base = DWELLTYPES.length+HHTYPES.length;

		//constants for other factors
		res[base][0] = -1.0; //income
		res[base][1] = 2.0;
		res[base+1][0] = -0.0001; //lon
		res[base+1][1] = 0.0001;
		res[base+2][0] = -1.0; //hasgas
		res[base+2][1] = 2.0;
		res[base+3][0] = -1.0; //numbedrooms
		res[base+3][1] = 2.0;
		res[base+4][0] = -1.0; //numpeople
		res[base+4][1] = 2.0;

	

		return res;	
	}	

	/**
	 * 
	 * @param pnum The parameter number (from 0 to getDimensionOfModelSpace()-1, inclusive)
	 * @return The name of that parameter
	 */
	public String getParameterName(int pnum)
	{
		if(pnum < HHTYPES.length)
			return HHTYPES[pnum];
		else if(pnum < HHTYPES.length+DWELLTYPES.length)
			return DWELLTYPES[pnum-HHTYPES.length];
		else 
			return OTHERVARS[pnum-HHTYPES.length-DWELLTYPES.length];
	}
	
	
	public String getStringRepresentationOfModel(double[] m)
	{
		int base = HHTYPES.length+DWELLTYPES.length;
		double incomeparam = m[base];
		double lonparam = m[base+1];
		double hasgasparam = m[base+2];
		double numbedparam = m[base+3];
		double numpeople = m[base+4];
		return "hhconst*dwellconst*(income^"+incomeparam+")*("
		+"lon^"+lonparam+")*(numbed^"+numbedparam+")";
	}
	
	
	
	/**
	 * 
	 * @param names
	 */
	public void setParameterName(int pnum, String name)
	{
		throw new UnsupportedOperationException();
	}

	//{lon, hhtype, income, numbedrooms, dwelltype, hasgas, totalenergy};
	public double getPrediction(double[] household, double[] m)
	{
		

		int hhtype = (int) household[1];
		//if(hhtype == 4) hhtype = 3; //lump all couples with kids together
		int dwelltype = (int) household[4];
		double income = household[2];
		double lon = household[0];
		double numbed = household[3];
		double hasgas = household[5];
		double numpeople = household[6];

		//System.out.println(hhtype+" "+dwelltype+" "+income+" "+lon+" "+numbed+" "+hasgas+" (actual usage = "+household[household.length-1]+")");

		double hhtypeconst = m[hhtype];
		//if(hhtype != 0) hhtypeconst *= m[0];
		double dwelltypeconst = 1.0; //default for house
		if(dwelltype > 0)
			dwelltypeconst = m[HHTYPES.length+dwelltype-1]; //-1 because semi has value 1, unit has value 2 
		int base = HHTYPES.length+DWELLTYPES.length;
		double incomeparam = m[base];
		double lonparam = m[base+1];
		double hasgasparam = m[base+2];
		double numbedparam = m[base+3];
		double numpeopleparam = m[base+4];
		//if(hasgas > 0)
		//System.out.println("usage = "+hhtypeconst+"*"+dwelltypeconst+"*("+income+"^"+incomeparam+
		//	")*("+lon+"^"+lonparam+")*("+numbed+"^"+numbedparam+")*"+hasgasparam);
		//else
		//System.out.println("usage = "+hhtypeconst+"*"+dwelltypeconst+"*("+income+"^"+incomeparam+
		//	")*("+lon+"^"+lonparam+")*("+numbed+"^"+numbedparam+")");

		double pred = hhtypeconst*dwelltypeconst;
		pred *= Math.pow(income, incomeparam);
		pred *= Math.pow(lon, lonparam);
		if(hasgas > 0.0)
			pred *= (1+hasgasparam);
		pred *= Math.pow(numbed, numbedparam);
		pred *= Math.pow(numpeople, numpeopleparam);
		
		//System.out.println("prediction = "+pred);

		return pred;
	}



	public double errorFunction(double pred, double actual)
	{
		//return Math.pow((pred-actual)/actual, 2);
		//return Math.pow((pred-actual), 2);
		
		if(pred < 0) 
			throw new RuntimeException("Prediction is -ve... should be impossible");
		return Math.pow((Math.log(actual)-Math.log(pred)), 2);
	}

	public double[][][] filter(double[] m)
	{
		double[] errs = new double[households.length];
		double sumerr = 0.0;

		//go through and work out our prediction for
		//total house and unit energy. 
		//take the squared error as our error.
		for(int i = 0; i < households.length; i++)
		{
			double pred = getPrediction(households[i], m);
			double actual = households[i][households[i].length-1];
			double err = errorFunction(pred, actual);
			sumerr += err;
			errs[i] = err;
		}

		//now work out what the outliers are.
		DistributionStatistics diststats = MathUtil.calculateDistributionStatistics(errs);
		ArrayList valid = new ArrayList();
		ArrayList invalid = new ArrayList();
		for(int i = 0; i < errs.length; i++)
		{
			double stddevs = Math.abs((errs[i]-diststats.mean)/diststats.samplestddev);
			String hhstr = "";
			for(int j = 0; j < households[i].length; j++)
				hhstr = hhstr+households[i][j]+" ";
			
			if(stddevs > 3.0) {
				invalid.add(households[i]);
				hhstr = "OUTLIER "+hhstr;
			}
			else {
				valid.add(households[i]);
				hhstr = "VALID "+hhstr;
			}
			
			System.out.println(hhstr);
		}
		
		//now replace households with the filtered households
		double[][] filteredhh = new double[valid.size()][];
		double[][] removedhh = new double[invalid.size()][];
		for(int i = 0; i < valid.size(); i++) 
			filteredhh[i] = (double[]) valid.get(i);
		for(int i = 0; i < invalid.size(); i++) 
			removedhh[i] = (double[]) invalid.get(i);
		
		
		return new double[][][] {filteredhh, removedhh};
	}

	/*
	 * Our model for predicting the total energy in a postcode is:
	 *
	 *
	 * energy = sum over households in that housing type of function F()
	 *
	 *  where F is the prediction for the individual energy used by that
	 *  household.
	 * 
	 *  Here is the functional form of F:
	 *
	 * F = a_hhtype * b_dwelltype * income^x1 * distcbd^x2 * long^x3
	 *
	 * 
	//{lon, hhtype, income, numbedrooms, dwelltype, hasgas, totalenergy};
	 */
	public double getErrorForModel(double[] m, boolean print)
	{
		if(m.length != HHTYPES.length+DWELLTYPES.length+OTHERVARS.length)
			throw new RuntimeException("Stuffed up. Model doesnt make sense");


		double err = 0.0;


		//go through and work out our prediction for
		//total house and unit energy. 
		//take the squared error as our error.
		for(int i = 0; i < households.length; i++)
		{
			int dwelltype = (int) households[i][4];
			double pred = getPrediction(households[i], m);
			double actual = households[i][households[i].length-1];
			if(print)
				System.out.println("HH == "+Util.arrayToString(households[i])+" | MODEL == "+Util.arrayToString(m)+" "+pred+" "+actual+" "+(pred-actual));
			
			err += errorFunction(pred, actual);
		}

		return err;
	}


	public double getErrorForModel(double[] m)
	{
		return getErrorForModel(m, false);
	}



	public static double[][] readVectorsFromFile(File f) throws IOException
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
		}
		if(list.size() == 0)
			throw new IOException("File contains no data");
		double[][] res = new double[list.size()][veclength];
		for(int i =0; i < res.length; i++)
			res[i] = (double[]) list.get(i);
		return res;
	}
	



	/* Print cross tabulations.
	 *
	 */
	/*
	public void printCrossTabs(double[] model)
	{
		printByHHType(model);
		printByIncome(model);
		printByDwellType(model);
	}

	public void printByDwellType(double[] model)
	{
		double housetotal = 0.0;
		double unittotal = 0.0;
		double housecount = 0.0;
		double unitcount = 0.0;

		for(int i = 0; i < households.length; i++)
		{
			int dwelltype = (int) (households[i][2]);
			double weight = households[i][6];
			double val = getPrediction(households[i], model);
			if(dwelltype == 0) {
				housetotal += val*weight;
				housecount += weight;
			}	
			else if(dwelltype >= 2) {
				unittotal += val*weight;
				unitcount += weight;
			}	
		}

		System.out.println("HOUSE AVG "+housetotal/housecount);
		System.out.println("UNIT AVG "+unittotal/unitcount);
		

	}

	public void printByIncome(double[] model)
	{
		double[] usebyincome = new double[11];
		double[] count = new double[11];

		for(int i = 0; i < households.length; i++)
		{
			int incomebracket = (int) Math.round((households[i][3]-0.5)*10);
			double weight = households[i][6];
			double val = getPrediction(households[i], model);
			count[incomebracket] += weight;
			usebyincome[incomebracket] += val*weight; 
		}

		double total = usebyincome[0]+usebyincome[1]+usebyincome[2]+usebyincome[3];
		double countt = count[0]+count[1]+count[2]+count[3];
		System.out.println("$ 0-30K "+total/countt);

		total = usebyincome[4]+usebyincome[5]+usebyincome[6];
		countt = count[4]+count[5]+count[6];
		System.out.println("$ 30-50K "+total/countt);
		
		total = usebyincome[7]+usebyincome[8]+usebyincome[9];
		countt = count[7]+count[8]+count[9];
		System.out.println("$ 50-100K "+total/countt);

		total = usebyincome[10];
		countt = count[10];
		System.out.println("$ 100K "+total/countt);

	}
	



	public void printByHHType(double[] model)
	{
		//print energy use by hhtype
		double[] usebyhhtype = new double[HHTYPES.length];
		double[] count = new double[HHTYPES.length];

		for(int i =0; i < households.length; i++)
		{
			int hhtype = (int) households[i][1];
			double weight = households[i][6];
			double val = getPrediction(households[i], model);
			count[hhtype] += weight;
			usebyhhtype[hhtype] += val*weight;
		}
		for(int i = 0; i < HHTYPES.length; i++)
		{
			if(count[i] == 0.0)
				System.out.println(HHTYPES[i]+" 0.0");
			else
				System.out.println(HHTYPES[i]+" "+usebyhhtype[i]/count[i]);
		}

	}
	*/
	
	
	
	double getCVRMSError()
	{
		double[][] orighh = this.households;
		Object[][][] traintest = Util.getCrossValidationFolds(households, 10);
		double sumsquarederror = 0.0;
		int errcount = 0;
		
		for(int fold = 0; fold < 10; fold++)
		{
			Object[] train = traintest[fold][0];
			double[][] trainhh = new double[train.length][];
			for(int i = 0; i < train.length; i++)
				trainhh[i] = (double[]) train[i];
			this.households = trainhh;
			PowellMin powell = PowellMin.getAdaptivePowell(this, 0.01, 8000L);
			double[] best = powell.getBestModel().getModelParameters();
			
			//now get error via CROSS-VAL
			Object[] test = traintest[fold][1];
			for(int i = 0; i < test.length; i++)
			{
				double[] hh = (double[]) test[i];
				double pred = this.getPrediction(hh, best);
				double actual = hh[hh.length-1];
				sumsquarederror += Math.pow(Math.log(pred)-Math.log(actual), 2);
				errcount++;
			}
		}
		
		this.households = orighh;
		
		System.out.println("Worked out RMS error on "+errcount+" predictions");
		
		//ok, we have squared errors. Work out RMS error
		double rms = Math.sqrt(sumsquarederror/errcount);
		return rms;	
	}
	
	
	public static void main(String[] args) throws Exception
	{
		IpartInvert inv = new IpartInvert(null);
	
		//now estimate a model with Powell's method
		System.out.println("Estimating model");		
		PowellMin powell = PowellMin.getAdaptivePowell(inv, 0.01, 8000L);
		powell.run(); //call run() directly rather than starting a new thread

		//get best model
		Model best = powell.getBestModel();
		
		//print out best model
		System.out.println("Printing best model from initial run");
		System.out.println(""+best);
		
		//select a few random households and print out the predicted and actual
		System.out.println("Printing a few households and predicted/actual usage for this model ");
		System.out.println("hh: "+Util.arrayToString(inv.households[inv.households.length/4]));
		System.out.println("predicting.... ");
		double pred = inv.getPrediction(inv.households[inv.households.length/4], best.getModelParameters());
		System.out.println("prediction is "+pred);
		System.out.println("hh: "+Util.arrayToString(inv.households[3*inv.households.length/4]));
		System.out.println("predicting.... ");
		pred = inv.getPrediction(inv.households[3*inv.households.length/4], best.getModelParameters());
		System.out.println("prediction is "+pred);
		
		//work out CV RMS error
		double rms = inv.getCVRMSError();
		System.out.println("RMS ERROR IS "+rms);
		
		
		
		//now filter out outliers
		double[][][] inout = inv.filter(best.getModelParameters());
		double[][] in = inout[0];
		double[][] out = inout[1];

		
		//replace households with filtered households
		System.out.println("replacing "+inv.households.length+" households with "+in.length+" households after filtering");
		inv.households = in;
		
		
		//now reestimate the model
		System.out.println("Estimating second model");
		powell = PowellMin.getAdaptivePowell(inv, 0.01, 8000L);
		powell.run(); //call run() directly rather than starting a new thread
		Model best2 = powell.getBestModel();
		System.out.println("best model before filtering was: "+best);
		System.out.println("best model after filtering is: "+best2);
		
		
	

		


		//now go out and calculate cross-tabulations
		//inv.printCrossTabs(m.getModelParameters());
	}


}



