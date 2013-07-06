package rses.apps.rtran;
import java.util.Map;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import rses.Debug;
import rses.util.Util;

public class Globals 
{
	//at the moment, we have to have non-zero car speeds everywhere because
	//otherwise this creates problems when we do our skim trees. 
	//This should be fixed later, for the moment the workaround is just
	//to set speeds to a very low number FIXME
	public static final double MINCARSPEED = 0.001;
	
	//at more than 45 services an hour we issue a warning
	public static final double WARNFREQ = 45.01;
	
	public final double gridsquare_edgekm;
	
	public final double walkbonus;
	public final double bikebonus;

	public final double car_dollarsperkm;
	public final double valueoftime_dollarsperhour;
	public final double carcosts_pertrip;
	
	public final double carRevenuePerKM;
	public final double carRevenuePerTrip;

	
	
	//aux distance assumed to be associated with walking or biking to a PT stop
	public final double busaux_km;
	public final double railaux_km;
	
	public final double railinvweight;
	public final double businvweight;
	
	public final double extraTransferPenalty;
	
	public final double busboarddollars;
	public final double trainboarddollars;
	public final double busfareperkm;
	public final double trainfareperkm;
	public final boolean integratedFares;
	public final double perTripPTpenalty;	
	
	//Trip destinations are chosen according to the net utilty they offer.
	//Specifically, the probably of travelling to a destination is proportional to:
	//
	//	tripprob = numberofactivitiesatdestination*exp(-weightedcost/K)
	//
	// where 'weightedcost' is the cost of getting to the square (and back again)
	//   and K determines how much people prefer 'better' destinations. (So high K will mean that bad locations
	//   still get a decent number of trips.)
	public final double K;
	public final int stochasticPTruns; //0 implies deterministic
	public final int maxODmatchiterations; //this is to stop us taking forever to match O/D -- we allow a maximum
	
	//these should all sum to 1
	public final double captivetravelshare;
	public final double caronlyavailable;
					
	public final boolean modelCongestion;	
	public final boolean forceDestProportionalToActivities;
	
	public final double busdelaypersquare_hrs;
	public final double traindelaypersquare_hrs;
	
	public final double busOperatingCostPerBus;
	public final double trainOperatingCostPerTrain;
	
	
	
	//now the spatial inputs. 
	//these are trip origins
	public double[][] tripsbyXY;


	//activities is the measure of trip attraction
	public final double[][] activitiesXY;
	
	//car speeds (km/h)
	public double[][] carSpeedsbyXY;
	public final double[][] origCarSpeedsbyXY;

	public final double[][] roadCapacityXY;
	
	//The cost (in minutes) of getting onto/off bus or rail. In other words the
	//waiting time. 
	public final Map<String, double[][]> BusWaitTimes; 
	public final Map<String, double[][]> RailWaitTimes; 
	public final Map<String, Double> frequencies = new java.util.HashMap<String, Double>();
	public final double[][] PTwaitWeights; 
		
	public final Map<String, double[][]> TrainSpeeds;
	
	
	
	//note that these may not capture all costs, because not all costs are per-km.
	//For instance, PT fare costs are not linear in distance.
	//Parking costs are incurred at destination
	//etc etc
	
	public final double[][] walkCostPerKMbyXY;
	public final double[][] bikeCostPerKMbyXY;
	public final double[][] parkingCostsbyXY_1stleg;
	public final double[][] parkingCostsbyXY_returnleg;
	public final double[][] parkingRevenue_1stleg;
	public final double[][] carRevenuePerKMbyXY;
	
	
	
	
	
	public Globals(String spreadsheetname)
	{
		org.apache.poi.ss.usermodel.Workbook book = null;
		try { book = XLSXUtils.getWorkBook(spreadsheetname); }
		catch(Exception e) {
			javax.swing.JOptionPane.showMessageDialog(null, "Could not read input spreadsheet '"+spreadsheetname+"'. This spreadsheet must exist and must be in the same folder you run the simulation from. Please double-check");
			System.exit(1);
		}
		
		//first thing is to read the base inputs
		String coresheetname = "Core Inputs";
		org.apache.poi.ss.usermodel.Sheet coresheet = book.getSheet(coresheetname);
		if(coresheet == null)
		{
			javax.swing.JOptionPane.showMessageDialog(null, "Input spreadsheet '"+spreadsheetname+"' must have a sheet called '"+coresheetname+"' that contains the core non-spatial inputs, but I cannot find this sheet. Please double-check");
			System.exit(1);
		}		
		Debug.println("Reading core input parameters",Debug.INFO);
		java.util.Map<String, String> corekeyvals = XLSXUtils.getKeyValueMappingsFromSheet(coresheet);
		
		//now read in the key values
		gridsquare_edgekm = getDoubleValue("gridsquare_edgekm", corekeyvals);
		
		walkbonus = getDoubleValue("walkbonus", corekeyvals);
		bikebonus = getDoubleValue("bikebonus", corekeyvals);
		
		railaux_km = getDoubleValue("Rail aux distance (in km)", corekeyvals);
		busaux_km = getDoubleValue("Bus aux distance (in km)", corekeyvals);
		
		//car generalized costs
		car_dollarsperkm = getDoubleValue("car_dollarsperkm", corekeyvals);
		valueoftime_dollarsperhour = getDoubleValue("valueoftime", corekeyvals);
		carcosts_pertrip = getDoubleValue("carcosts_pertrip", corekeyvals);

		carRevenuePerKM = getDoubleValue("Car revenue (per km)", corekeyvals);
		carRevenuePerTrip = getDoubleValue("Car revenue (per trip)", corekeyvals);
		
		railinvweight = getDoubleValue("railinvweight", corekeyvals);
		businvweight = getDoubleValue("businvweight", corekeyvals);
		
		extraTransferPenalty = getDoubleValue("Transfer penalty ($)", corekeyvals);
		perTripPTpenalty = getDoubleValue("Per trip penalty ($)", corekeyvals);
		
		//for how many trips is a car available?
		captivetravelshare = getDoubleValue("captivetravelshare", corekeyvals);
		caronlyavailable = 1-captivetravelshare;		
		
		K = getDoubleValue("Dollar/Utility conversion denominator", corekeyvals);
		stochasticPTruns = (int) Math.round(getDoubleValue("PT segments", corekeyvals));
		maxODmatchiterations = (int) Math.round(getDoubleValue("Max O/D match iterations", corekeyvals));
		
		busboarddollars = getDoubleValue("Bus Fare per boarding", corekeyvals);
		trainboarddollars  = getDoubleValue("Train Fare per boarding", corekeyvals);
		busfareperkm = getDoubleValue("Bus Fare per km", corekeyvals);
		trainfareperkm = getDoubleValue("Train Fare per km", corekeyvals);
		integratedFares = getBooleanValue("Integrated PT Fares", corekeyvals);
		
		busdelaypersquare_hrs = getDoubleValue("Bus stopping time per square (seconds)", corekeyvals)/(60.0*60);
		traindelaypersquare_hrs = getDoubleValue("Rail stopping time per square (seconds)", corekeyvals)/(60.0*60);
		
		modelCongestion = getBooleanValue("Model congestion?", corekeyvals);
		//if(modelCongestion) err("Congestion modelling not enabled in this software");
		
		forceDestProportionalToActivities = getBooleanValue("Force destinations proportional to activities?", corekeyvals);
		
		busOperatingCostPerBus = getDoubleValue("Bus operating costs per bus", corekeyvals);
		trainOperatingCostPerTrain = getDoubleValue("Train operating costs per train", corekeyvals);
		
		//trips by x,y. This is trips from and trip to (we require symmetry)
		Debug.println("Reading Trip generation sheet",Debug.INFO);
		tripsbyXY = readMatrix("Trips", book);
		Debug.println("Total trips from input trip generation sheet is "+rses.util.Util.getSum(tripsbyXY), Debug.IMPORTANT);
		
		Debug.println("Reading Activities sheet",Debug.INFO);
		activitiesXY = readMatrix("Activities", book);
		
		Debug.println("Reading capacities sheet", Debug.INFO);
		roadCapacityXY = readMatrix("Road Capacity", book);
		
		//car speeds
		Debug.println("Reading Car speed sheet",Debug.INFO);
		origCarSpeedsbyXY = readMatrix("CarSpeeds", book);
		for(int x =0; x < origCarSpeedsbyXY.length; x++) 
			for(int y = 0; y < origCarSpeedsbyXY[0].length; y++) 
			{
				if(origCarSpeedsbyXY[x][y] < MINCARSPEED) 
				{
					Debug.println("Replacing car speed of 0 with minimum speed of "+MINCARSPEED, Debug.INFO);
					origCarSpeedsbyXY[x][y] = MINCARSPEED;
				}
			}
		
		carSpeedsbyXY = Util.copy(origCarSpeedsbyXY);
		
		Debug.println("Reading Bus Frequency/Wait times",Debug.INFO);
		BusWaitTimes = readFrequencies(book, "bus", "frequency"); 

		Debug.println("Reading Rail Wait Times",Debug.INFO);
		RailWaitTimes = readFrequencies(book, "train", "frequency");

		Debug.println("Reading Train Speeds",Debug.INFO);
		TrainSpeeds = new java.util.HashMap<String, double[][]>();
		double[][] defaultSpeeds = null;
		for(String id : RailWaitTimes.keySet()) 
		{
			String specificName = "Train_"+id+"_Speeds";
			if(book.getSheet(specificName) == null)
			{
				if(defaultSpeeds == null) defaultSpeeds = readMatrix("TrainSpeeds", book);
				TrainSpeeds.put(id, defaultSpeeds);
			}
			else
				TrainSpeeds.put(id, readMatrix(specificName, book));
		}
		

		//how to weight weighting time at this x/y
		Debug.println("Reading Public Transport interchange weights",Debug.INFO);
		PTwaitWeights = readMatrix("PT Waiting Time Weight", book);
		
		//walk and bike costs
		Debug.println("Reading Walk Costs per km",Debug.INFO);
		walkCostPerKMbyXY = readMatrix("WalkCostsPerKM", book);

		Debug.println("Reading Bike Costs per km",Debug.INFO);
		bikeCostPerKMbyXY = readMatrix("BikeCostsPerKM", book);
		
		//parking costs
		Debug.println("Reading parking costs", Debug.INFO);
		parkingCostsbyXY_1stleg = readMatrix("ParkingCost", book);
		parkingCostsbyXY_returnleg = readMatrix("ParkingCosts Return", book);

		Debug.println("Reading car-based revenue (parking and per-km)", Debug.INFO);
		parkingRevenue_1stleg = readMatrix("ParkingRevenue", book);
		carRevenuePerKMbyXY = readMatrix("CarRevenuePerKM", book);

	}

	
	private double[] getCongestionSlices(String cellval)
	{
		//cellval must be a comma separated list of values between 0 and 1
		//must sum to 1
		if(cellval.trim().length() == 0) err("Congestion slices not correctly specified");
		String[] bits = cellval.trim().split(",");
		if(bits.length == 0) err("Congestion slices not correctly specified");
		double[] congslices = new double[bits.length];
		for(int i = 0; i < bits.length; i++)
		{
			congslices[i] = Double.parseDouble(bits[i]);
			if(congslices[i] <= 0.0) err("Non-positive congestion slice makes no sense in user input sheet");
		}
		if(Math.abs(Util.getSum(congslices)-1.0) > Main.SMALL)
			err("Congestion slices must sum to 1. Please specify congestion slices correctly");
		
		return congslices;
	}
	
	/** Make sure frequencies along each route are all the same, and that they are not
	 *  too frequent that they are unrealistic
	 * 
	 */
	private void checkBusAndRailFrequencies(double[][] waitTimes, String id, double minwait)
	{
		java.util.Set<String> visited = new java.util.HashSet<String>();
		int X = this.carSpeedsbyXY.length;
		int Y = this.carSpeedsbyXY[0].length;
		boolean warned = false;
		for(int x = 0; x < X; x++)
			for(int y = 0; y < Y; y++)
			{
				if(waitTimes[x][y] > 1000)
					continue;
				else if(!visited.contains(x+" "+y))
				{
					if(waitTimes[x][y] < minwait && !warned) {
						warning("Warning: Public transport service "+id+" runs VERY frequently. Are you sure that you can run at more than "+(60.0/(2*minwait))+" hourly frequency without any issues?");
						warned = true;
					}
					checkBusAndRailFrequencies(x, y, waitTimes, visited, waitTimes[x][y], id);
				}
			}
	}
	
	
	
	private static javax.swing.JFrame baseframe = new javax.swing.JFrame(); 
	public static void err(String errstring)
	{
		javax.swing.JOptionPane.showMessageDialog(baseframe, errstring);
		System.exit(1);
	}

	public static void warning(String errstring)
	{
		javax.swing.JOptionPane.showMessageDialog(baseframe, errstring);
	}
	
	//starting at x,y check all reachable squares to make sure that they have the same wait time
	private void checkBusAndRailFrequencies(int x, int y, double[][] waitTimes, 
			java.util.Set<String> visited, double waitTime, String id)
	{
		if(visited.contains(x+" "+y))
			return;
		else
			visited.add(x+" "+y);

		
		int X = this.carSpeedsbyXY.length;
		int Y = this.carSpeedsbyXY[0].length;

		//check N,S,E,W to make sure that they have same wait time
		//TODO need to get rid of constant 1000 here and make it a parameter
		if(x > 0 && waitTimes[x-1][y] < 1000 && waitTimes[x-1][y] != waitTime) err(id+" has a service that does not have a fixed frequency across its route");
		if(x < X-1 && waitTimes[x+1][y] < 1000 && waitTimes[x+1][y] != waitTime) err(id+" has a service that does not have a fixed frequency across its route");
		if(y > 0 && waitTimes[x][y-1] < 1000 && waitTimes[x][y-1] != waitTime) err(id+" has a service that does not have a fixed frequency across its route");
		if(y < Y-1 && waitTimes[x][y+1] < 1000 && waitTimes[x][y+1] != waitTime) err(id+" has a service that does not have a fixed frequency across its route");

		//ok, all adjacent squares have the same frequency. Now make sure that there are no more than 2
		//adjacent squares
		int adjcount = 0;
		if(x > 0 && waitTimes[x-1][y] < 1000) adjcount++;
		if(x < X-1 && waitTimes[x+1][y] < 1000) adjcount++;
		if(y > 0 && waitTimes[x][y-1] < 1000) adjcount++;
		if(y < Y-1 && waitTimes[x][y+1] < 1000) adjcount++;
		if(adjcount > 2) err(id+" has a service that branches, or is in some other way ill-configured. Services must be single contiguous lines");
		
		//ok, done this square, go on to adjacent ones!
		if(x > 0 && waitTimes[x-1][y] < 1000) checkBusAndRailFrequencies(x-1, y, waitTimes, visited, waitTime, id);
		if(x < X-1 && waitTimes[x+1][y] < 1000) checkBusAndRailFrequencies(x+1, y, waitTimes, visited, waitTime, id);
		if(y > 0 && waitTimes[x][y-1] < 1000) checkBusAndRailFrequencies(x, y-1, waitTimes, visited, waitTime, id);
		if(y < Y-1 && waitTimes[x][y+1] < 1000) checkBusAndRailFrequencies(x, y+1, waitTimes, visited, waitTime, id);
		
	}

	
	//Each square either has a '-' character in it, or it has a comma separated list of routes 
	//
	//Each route has an identifier (which MUST start with a character), and (optionally)
	//a frequency.
	//
	//The frequency cannot change across a route.
	private Map<String, double[][]> readFrequencies(Workbook book, String prefix, String suffix)
	{
		java.util.Map<String, double[][]> routes = new java.util.HashMap<String, double[][]>();
		for(int i = 0; i < book.getNumberOfSheets(); i++)
		{
			Sheet sheet = book.getSheetAt(i);
			if(sheet.getSheetName().toLowerCase().startsWith(prefix.toLowerCase()) && sheet.getSheetName().toLowerCase().endsWith(suffix.toLowerCase()))
			{
				String[][] info = readStringMatrix(sheet.getSheetName(), book);
				//first lets check it to make sure it's valid
				for(int x = 0; x < info.length; x++)
					for(int y = 0; y < info[0].length; y++)
					{
						//you can have a dash '-' or an 'X' to indicate no route
						if(info[x][y].equals("-") || info[x][y].equals("X")) { info[x][y] = null; continue; }
						if(info[x][y].indexOf(",") == -1 && Util.getWords(info[x][y]).length <= 2) 
						{
							//no comma, just one or two elements. 
							String[] elems = Util.getWords(info[x][y]);
							if(!Character.isLetter(elems[0].charAt(0))) err("In sheet "+sheet.getSheetName()+" at x/y location "+x+" "+y+" you have a route that does not start with a character. ALL routes must start with a character");
							if(elems.length == 2) { try {Double.parseDouble(elems[1]); } catch(Exception e) { err("In sheet "+sheet.getSheetName()+" at x/y location "+x+" "+y+" cant parse string "+elems[1]); }}
						}
						else //a comma, so we have a list of services 
						{
							String[] chunks = info[x][y].split(",");
							String[] elems = Util.getWords(chunks[0]);
							if(!Character.isLetter(elems[0].charAt(0))) err("In sheet "+sheet.getSheetName()+" at x/y location "+x+" "+y+" you have a route that does not start with a character. ALL routes must start with a character");
							if(elems.length == 2) { try {Double.parseDouble(elems[1]); } catch(Exception e) { err("In sheet "+sheet.getSheetName()+" at x/y location "+x+" "+y+" cant parse string "+elems[1]); }}
						}
					}
				
				
				//ok, now get routes and frequencies
				while(extractRouteInfo(info, routes))
					/* do nothing. returns false when done */;
			}
		}
		
		
		return routes;
	}
	
	
	
	private boolean extractRouteInfo(String[][] sheetinfo, Map<String, double[][]> routes)
	{
		//go through and "extract" each route in turn
		
		//first we find the first route left
		String route = null;
		for(int x = 0; x < sheetinfo.length; x++)
			for(int y = 0; y < sheetinfo[0].length; y++)
				if(sheetinfo[x][y] != null) {
					route = Util.getWords(sheetinfo[x][y].split(",")[0])[0]; 
				}
		if(route == null) return false; //nothing left to extract

		Debug.println("    Extracting route "+route, Debug.INFO);
		
		double[][] waitTimes = new double[sheetinfo.length][sheetinfo[0].length];
		//OK, we go through and get this route
		java.util.Set<String> freqPrinted = new java.util.HashSet<String>(); 
		boolean warned = false;
		Double firstwait = null;
		for(int x = 0; x < sheetinfo.length; x++)
		{
			for(int y = 0; y < sheetinfo[0].length; y++)
			{
				//System.out.println("At "+x+" "+y+" sheet info is "+sheetinfo[x][y]);
				if(sheetinfo[x][y] == null) {
					waitTimes[x][y] = Double.POSITIVE_INFINITY;
					continue;
				}
				
				String[] routeInfoList = sheetinfo[x][y].split(",");
				String newInfoList = "";
				
				//go through each route in the list of routes, and if its the one
				//we are after, we remember that info
				for(String routeInfo : routeInfoList) 
				{
					String trimmed = routeInfo.trim();
					if(trimmed.length() == 0) err("empty element in public transport route list at "+x+" "+y);
					String[] bits = Util.getWords(trimmed);
					if(bits.length > 2) err("At "+x+" "+y+" malformed route list element (too long): "+routeInfo);
					String id = bits[0];
					if(bits[0] == null || bits[0].length() == 0) err("At "+x+" "+y+" incorrectly specified square: "+trimmed);
					if(!Character.isLetter(id.charAt(0))) err("At "+x+" "+y+" route ID does not start with a letter: "+id);
					if(!id.equals(route)) 
					{
						//not the route we are interested in so we keep it and move on
						newInfoList = newInfoList+","+trimmed;
						continue;
					}
					if(bits.length == 2) 
					{
						double freq = Double.parseDouble(bits[1]);
						if(freq > WARNFREQ && !warned) {
							warning("You have specified a very high frequency (of "+freq+" per hour) for route "+id+". Are you sure you can do this?");
							warned = true;
						}
						if(!freqPrinted.contains(id)) {
							freqPrinted.add(id);
							Debug.println("    Frequency of service "+id+" is "+freq, Debug.INFO);
							frequencies.put(id, freq);
						}
						if(freq <= 0) err("You specified a non-positive service frequency!");
						double wait = 0.5*60/freq;
						if(firstwait == null) firstwait = wait;
						waitTimes[x][y] = wait;
					}
					else
						waitTimes[x][y] = Double.NaN; //flag that the route goes to this square. We fill in frequency later
				}
				if(newInfoList.length() == 0)
					sheetinfo[x][y] = null;
				else if(!newInfoList.startsWith(",")) 
					throw new IllegalStateException("Should be impossible");
				else
					sheetinfo[x][y] = newInfoList.substring(1);
			}
		}
		
		if(firstwait == null) throw new RuntimeException("No frequency information for Service "+route);
		
		//ok, go through and replace any zero's with the wait time, and check if any
		//other frequencies dont match the first one found
		for(int x = 0; x < sheetinfo.length; x++)
			for(int y = 0; y < sheetinfo[0].length; y++)
			{
				//we flagged this square as having a service
				if(Double.isNaN(waitTimes[x][y])) waitTimes[x][y] = firstwait;
				//there were some services on this square but not matching the id we are interested in
				else if(waitTimes[x][y] == 0) waitTimes[x][y] = Double.POSITIVE_INFINITY;
				else if(waitTimes[x][y] < Double.POSITIVE_INFINITY && waitTimes[x][y] != firstwait)
					err("For public transport service "+route+", you have specified multiple service frequencies that dont match ("+firstwait+" and "+waitTimes[x][y]+")");
				//System.out.println("At "+x+" "+y+" wait time is "+waitTimes[x][y]);
			}
		
		routes.put(route, waitTimes);
		return true;
	}

	
	
	//this was when we had the old way of specifying bus/train routes where we had frequencies on
	//each square and no route could overlap
	private double[][][] readTimes_old(org.apache.poi.ss.usermodel.Workbook book, String prefix, String suffix)
	{
		java.util.List<double[][]> times = new java.util.ArrayList<double[][]>();
		for(int i = 0; i < book.getNumberOfSheets(); i++)
		{
			Sheet sheet = book.getSheetAt(i);
			if(sheet.getSheetName().toLowerCase().startsWith(prefix.toLowerCase()) && sheet.getSheetName().toLowerCase().endsWith(suffix.toLowerCase()))
			{
				double[][] freqs = readMatrix(sheet.getSheetName(), book);
				//now turn freqs per hour into waiting times
				double[][] wait = new double[freqs.length][freqs[0].length];
				for(int a = 0; a < freqs.length; a++)
					for(int b = 0; b < freqs[0].length; b++)
						//remember that waiting time is 1/2 frequency on average
						wait[a][b] = 0.5*60.0/freqs[a][b];
						
				times.add(wait);
			}
		}
		
		double[][][] res = new double[times.size()][][];
		for(int i = 0; i < times.size(); i++)
			res[i] = times.get(i);
			
		return res;
	}
	
	
	
	
	
	private boolean[][] readBoolMatrix(String sheetname, org.apache.poi.ss.usermodel.Workbook book)
	{
		double[][] numvals = readMatrix(sheetname, book);
		boolean[][] res = new boolean[numvals.length][numvals[0].length];
		for(int i = 0; i < numvals.length; i++)
			for(int j =0; j < numvals[i].length; j++)
				res[i][j] = numvals[i][j] > 0.0;
		return res;
	}

	
	private double[][] readMatrix(String sheetname, org.apache.poi.ss.usermodel.Workbook book)
	{
		Double[][] obj = null;
		
		org.apache.poi.ss.usermodel.Sheet sheet = book.getSheet(sheetname);
		if(sheet == null) {
			javax.swing.JOptionPane.showMessageDialog(null, "There is no sheet called '"+sheetname+"' in input spreadsheet, but you MUST define this for the simulation to run.");
			System.exit(1);						
		}
		
		try { 
			obj = XLSXUtils.readMatrix(sheet);
		}
		catch(Exception e) {
			javax.swing.JOptionPane.showMessageDialog(null, "Could not read key spatial matrix '"+sheet.getSheetName()+"' from input spreadsheet. "+e.getMessage());
			System.exit(1);			
		}
		
		//Rearrange because we want it in X,Y format, but we get it in the order we read it from the spreadsheet.
		//So the first row is the row in the spreadsheet..
		//In other words, we get it in [-Y,X] format but we want it in [X,Y] format 
		//
		//Also convert to primitive type because it matters for number-crunching efficiency later
		double[][] res = new double[obj[0].length][obj.length];
		for(int y = 0; y < res.length; y++)
		{
			for(int x = 0; x < res[y].length; x++)
				res[x][y] = obj[res.length-y-1][x];
		}

		return res;
	}


	private String[][] readStringMatrix(String sheetname, org.apache.poi.ss.usermodel.Workbook book)
	{
		String[][] obj = null;
		
		org.apache.poi.ss.usermodel.Sheet sheet = book.getSheet(sheetname);
		if(sheet == null) {
			javax.swing.JOptionPane.showMessageDialog(null, "There is no sheet called '"+sheetname+"' in input spreadsheet, but you MUST define this for the simulation to run.");
			System.exit(1);						
		}
		
		try { 
			obj = XLSXUtils.readStringMatrix(sheet);
		}
		catch(Exception e) {
			javax.swing.JOptionPane.showMessageDialog(null, "Could not read key spatial matrix '"+sheet.getSheetName()+"' from input spreadsheet. "+e.getMessage());
			System.exit(1);			
		}
		
		//Rearrange because we want it in X,Y format, but we get it in the order we read it from the spreadsheet.
		//So the first row is the row in the spreadsheet..
		//In other words, we get it in [-Y,X] format but we want it in [X,Y] format 
		//
		//Also convert to primitive type because it matters for number-crunching efficiency later
		String[][] res = new String[obj[0].length][obj.length];
		try {
			for(int y = 0; y < res.length; y++)
			{
				for(int x = 0; x < res[y].length; x++)
					res[x][y] = obj[res.length-y-1][x];
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			err("Sheet "+sheetname+" is incorrectly specified/formatted. Aborting.");
		}

		return res;
	}

	
	private static double getDoubleValue(String key, java.util.Map<String, String> keysvals)
	{
		if(!keysvals.containsKey(key)) 
		{
			javax.swing.JOptionPane.showMessageDialog(null, "Could not find key parameter '"+key+"' defined in input spreadsheet. You MUST define this parameter. Aborting");
			System.exit(1);
		}
		String val = keysvals.get(key);
		double dval = Double.NaN;
		try {
			dval = Double.parseDouble(val);
		}
		catch(Exception e) {
			javax.swing.JOptionPane.showMessageDialog(null, "Key parameter '"+key+"' is present in input spreadsheet, but has no value, or else the value is not a proper number. You MUST define this parameter. Aborting");
			System.exit(1);			
		}
		
		Debug.println("Got value of "+dval+" for key "+key, Debug.INFO);
		
		return dval;
	}


	private static boolean getBooleanValue(String key, java.util.Map<String, String> keysvals)
	{
		if(!keysvals.containsKey(key)) 
		{
			javax.swing.JOptionPane.showMessageDialog(null, "Could not find key parameter '"+key+"' defined in input spreadsheet. You MUST define this parameter. Aborting");
			System.exit(1);
		}
		String val = keysvals.get(key);
		Boolean bval = null;
		try {
			bval = Boolean.parseBoolean(val);
		}
		catch(Exception e) {
			javax.swing.JOptionPane.showMessageDialog(null, "Key parameter '"+key+"' is present in input spreadsheet, but has no TRUE/FALSE value. You MUST define this parameter as either TRUE or FALSE. Aborting");
			System.exit(1);			
		}
		
		return bval;
	}

	
}
