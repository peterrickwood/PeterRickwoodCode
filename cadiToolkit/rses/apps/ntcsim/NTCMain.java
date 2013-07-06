package rses.apps.ntcsim;
import rses.Debug;
import rses.Model;
import rses.math.GraphNode;
import rses.math.GraphEdge;
import rses.math.GraphUtil;
import rses.util.Util;

import java.util.HashMap;
import java.util.Map;



/**
 * 
 * @author peterr
 *
 *
 *
 * TODO --- currently the distance calculations for cars nay be slightly wrong because they
 * assume manhattan distance by car, but this actually depends on the path tree for car.
 * All non-car modes are OK.
 *
 *
 *
 *
 */
public class NTCMain 
{
	/** REMEMBER:
	 * 
	 * The city grid is numbered from 0, with the top-left of the city being square 0
	 * 
	 */
	private GraphNode[][] city;
	
	private double[][][] cartripdistancesbycarmode = new double[3][][];
	private double[][][] congestionzonetripdistancesbycarmode = new double[3][][];
	private double[][][] congestionzone2tripdistancesbycarmode = new double[3][][];
	private double[][] odtripcounts;

	//This is a calibrated parameter -- it gets altered to make sure that congestion
	//stays below the specified maximum
	private double congestioncost_perkm = 0.0;
	private double congestioncost2_perkm = 0.07;
	
	
	private double mintripdist;
	
	private enum mode {
		CAR(0), CARWALK(1), WALK(2), BIKE(3), PT(4), EV(5), EVWALK(6), EVMINI(7), EVMINIWALK(8), 
		CARPT(9), BIKEPT(10), WALKPT(11), EVPT(12), EVMINIPT(13);
		
		static final int NUMMODES = 14;
		
		static java.util.List<mode> getAllModes() {
			return allmodes;
		}

		private static java.util.List<mode> allmodes;
		static {
			java.util.ArrayList<mode> l = new java.util.ArrayList<mode>();
			l.add(mode.CAR);
			l.add(mode.CARWALK);
			l.add(mode.WALK);
			l.add(mode.BIKE);
			l.add(mode.PT);
			l.add(mode.EV);
			l.add(mode.EVWALK);
			l.add(mode.EVMINI);
			l.add(mode.EVMINIWALK);
			l.add(mode.CARPT);
			l.add(mode.BIKEPT);
			l.add(mode.WALKPT);
			l.add(mode.EVPT);
			l.add(mode.EVMINIPT);
			if(l.size() != NUMMODES) throw new RuntimeException();
			allmodes = l;
		}
		
		private int index;
		mode(int i) { this.index = i; }
	}
	
	
	private rses.spatial.util.DataStore database = null;
	private Globals globals;
	

	private java.util.Random random = new java.util.Random(111211); //11/12/2011
	
	private double alpha;
	
	public NTCMain(double alpha) throws java.io.IOException
	{
		this.alpha = alpha;
		
		//read in the database
		this.database = new rses.spatial.util.DataStore();

		//next read in all the Globals from the database, and/or barf if any of them are missing
		this.globals = new Globals(database);
		mintripdist = globals.gridsquare_edgekm*0.5;

		//initialize city nodes (but edges are initialized later)
		this.city = new GraphNode[globals.citydiameter][globals.citydiameter];
		for(int x = 0; x < globals.citydiameter; x++) 
			for(int y = 0; y < globals.citydiameter; y++) 
				city[x][y] = new GraphNode(""+getID_forPosition(x, y));
		//initialize edges and edge weights to be travel times by car
		this.initializeCityGraphEdgesWithCarTravelTimes(); 
		
		Debug.println("Initial graph with car travel times built", Debug.INFO);
		if(Debug.equalOrMoreVerbose(Debug.INFO))
			rses.math.GraphUtil.printGraph(city[0][0], Debug.getPrintStream(Debug.INFO));
		Debug.println("Finishing printing initial car travel time graph", Debug.INFO);
	}

	
	
	private double[] actnorm = null; 
	public int selectCellWeightedByActivities()
	{
		if(actnorm == null)
		{
			actnorm = new double[globals.citydiameter*globals.citydiameter];
			for(int i = 0; i < globals.citydiameter*globals.citydiameter; i++)
				actnorm[i] = lookup("actvities", i);
			Util.normalize(actnorm);
		}
		
		return Util.getCummulativeIndex(actnorm, random.nextDouble());
	}
	

	/** Get distance (in km) from square (x,y) to square (x2,y2)
	 * 
	 * @param x
	 * @param y
	 * @param x2
	 * @param y2
	 * @return distance, in km
	 */
	public double getCrowFliesDistance(int x, int y, int x2, int y2)
	{
		double xdiff = (x-x2)*globals.gridsquare_edgekm;
		double ydiff = (y-y2)*globals.gridsquare_edgekm;
		return Math.sqrt(xdiff*xdiff+ydiff*ydiff);
	}

	
	public double getDistFromCBD(int x, int y)
	{
		return getCrowFliesDistance(x, y, globals.cbdx, globals.cbdy);
	}

	/**
	 * Get car speed at point x,y
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public double getCarSpeed(int x, int y)
	{
		double dist = getDistFromCBD(x, y);
		double speed;
		if(dist <= globals.congestionzoneradius)
			speed = globals.cbdcarspeed+dist*(globals.congestioncordoncarspeed-globals.cbdcarspeed)/globals.congestionzoneradius;
		else 
			speed = globals.congestioncordoncarspeed+globals.carspeedincreaseperkm*(dist-globals.congestionzoneradius);
		
		return Math.min(speed, globals.maxcarspeed);
	}
	
	
	public int[] getXYforID(int id)
	{
		int x = id % globals.citydiameter;
		int y = id / globals.citydiameter;
		return new int[] {x,y};
	}
	
	public int getID_forPosition(int x, int y)
	{
		return y*globals.citydiameter+x;
	}
	

	

	

	private void initializeCityGraphEdgesWithCarTravelTimes()
	{
		//initialize edges -- manhattan connectivity only
		for(int x = 0; x < globals.citydiameter; x++) 
			for(int y = 0; y < globals.citydiameter; y++) 
			{
				//remove any old edges
				city[x][y].removeAllEdges();
				
				//connect it to its neighbours
				if(x > 0) {
					double avgspeed = (this.getCarSpeed(x-1,y)+this.getCarSpeed(x, y))/2; //speed in km/h
					double time = (globals.gridsquare_edgekm*60.0)/avgspeed; //this many minutes to travel that distance
					city[x][y].addEdge(new GraphEdge(city[x][y], city[x-1][y], time)); 
				}
				if(x+1 < globals.citydiameter) {
					double avgspeed = (this.getCarSpeed(x+1,y)+this.getCarSpeed(x, y))/2; //speed in km/h
					double time = (globals.gridsquare_edgekm*60.0)/avgspeed; //this many minutes to travel that distance
					city[x][y].addEdge(new GraphEdge(city[x][y], city[x+1][y], time));
				}
				if(y > 0) {
					double avgspeed = (this.getCarSpeed(x,y-1)+this.getCarSpeed(x, y))/2; //speed in km/h
					double time = (globals.gridsquare_edgekm*60.0)/avgspeed; //this many minutes to travel that distance
					city[x][y].addEdge(new GraphEdge(city[x][y], city[x][y-1], time));
				}
				if(y+1 < globals.citydiameter) {
					double avgspeed = (this.getCarSpeed(x,y+1)+this.getCarSpeed(x, y))/2; //speed in km/h
					double time = (globals.gridsquare_edgekm*60.0)/avgspeed; //this many minutes to travel that distance
					city[x][y].addEdge(new GraphEdge(city[x][y], city[x][y+1], time));
				}
			}

		//DONE!!
		
	}
	
	
	/** Get the generalized cost of going from a to b by car. Do NOT include per-trip costs, only per km and per minute costs
	 * 
	 * @param x
	 * @param y
	 * @param xdelta
	 * @param ydelta
	 */
	private double getOneStepCarGeneralizedCost(int x, int y, int xdelta, int ydelta, mode m)
	{

		if(xdelta != 0 && ydelta != 0) throw new RuntimeException("Only support manhattan-block travel -- cant move diagonally");
		if(Math.abs(xdelta) > 1 || Math.abs(ydelta) > 1) throw new RuntimeException("Method only valid for movement of 1 square");

		double dist = Math.abs(xdelta)*globals.gridsquare_edgekm + Math.abs(ydelta)*globals.gridsquare_edgekm;
		double avgspeed = (this.getCarSpeed(x+xdelta,y+ydelta)+this.getCarSpeed(x, y))/2; //speed in km/h
		double time = globals.gridsquare_edgekm/avgspeed; //this many hours to travel that distance

		double cost = getCarCostForDistAndTimeOnly(dist, time, m);
			
		//lastly, do congestion
		double congestiondist = 0.0;		
		if(isInCongestionZone(x, y) && isInCongestionZone(x+xdelta, y+ydelta))
			congestiondist += dist;
		else if(!isInCongestionZone(x, y) && !isInCongestionZone(x+xdelta, y+ydelta))
			/* do nothing -- no congestion cost*/;
		else
			congestiondist += dist/2; //half the trip is in the congestion zone

		double congestiondist2 = 0.0;		
		if(isInCongestionZone2(x, y) && isInCongestionZone2(x+xdelta, y+ydelta))
			congestiondist2 += dist;
		else if(!isInCongestionZone2(x, y) && !isInCongestionZone2(x+xdelta, y+ydelta))
			/* do nothing -- no congestion cost*/;
		else
			congestiondist2 += dist/2; //half the trip is in the congestion zone

		
		if(m == mode.CAR)
			return cost+congestiondist*congestioncost_perkm+congestiondist2*congestioncost2_perkm;
		else if(m == mode.EV)
			return cost+congestiondist*congestioncost_perkm*globals.ev1congestionfact+congestiondist2*congestioncost2_perkm*globals.ev1congestionfact;
		else if(m == mode.EVMINI)
			return cost+congestiondist*congestioncost_perkm*globals.evsmallcongestionfact+congestiondist2*congestioncost2_perkm*globals.evsmallcongestionfact;
		else
			throw new RuntimeException("Cant calc car costs for mode "+m);
		
	}
	
	
	private double getCarCostForDistAndTimeOnly(double dist_in_km, double time_in_hours, mode m)
	{
		double cost;
		
		if(m == mode.CAR) 
		{
			//do distance based costs first
			cost = dist_in_km*(globals.accidentcosts_dollarsperkm + globals.carweartear_dollarsperkm + globals.petrolcost_dollarsperkm
				+ globals.noisecosts_dollarsperkm + globals.othercarcosts_perkm);
			//do time costs next
			cost += time_in_hours*(globals.valueoftime_dollarsperhour + globals.othercarcosts_perminute*60.0);
		}
		else if(m == mode.EV)
		{
			//do distance based costs first
			cost = dist_in_km*globals.ev1costperkm_dollars;
			//do time costs next
			cost += time_in_hours*(globals.ev1costperminute_dollars*60.0);
		}
		else if(m == mode.EVMINI)
		{
			//do distance based costs first
			cost = dist_in_km*globals.evsmallcostperkm_dollars;
			//do time costs next
			cost += time_in_hours*(globals.evsmallcostperminute_dollars*60.0);
		}
		else
			throw new RuntimeException("Cant calc car costs for mode "+m);
		
		
		
		return cost;
	}
	
	public boolean isInCongestionZone(int x, int y)
	{
		return this.getDistFromCBD(x, y) <= globals.congestionzoneradius;
	}
	
	public boolean isInCongestionZone2(int x, int y)
	{
		return this.getDistFromCBD(x, y) <= globals.congestionzone2radius;
	}
	
	private void initializeCityGraphWithCarGeneralizedCosts(mode m)
	{
		if(m != mode.CAR && m != mode.EV && m != mode.EVMINI)
			throw new RuntimeException("Cant Car-Walk with mode "+m);

		//initialize edges -- manhattan connectivity only
		for(int x = 0; x < globals.citydiameter; x++) 
			for(int y = 0; y < globals.citydiameter; y++) 
			{
				//remove any old edges
				city[x][y].removeAllEdges();
				        
				//connect it to its neighbours
				if(x > 0) 
					city[x][y].addEdge(new GraphEdge(city[x][y], city[x-1][y], getOneStepCarGeneralizedCost(x, y, -1, 0, m))); 
				if(x+1 < globals.citydiameter) 
					city[x][y].addEdge(new GraphEdge(city[x][y], city[x+1][y], getOneStepCarGeneralizedCost(x, y, 1, 0, m))); 				
				if(y > 0) 
					city[x][y].addEdge(new GraphEdge(city[x][y], city[x][y-1], getOneStepCarGeneralizedCost(x, y, 0, -1, m))); 
				if(y+1 < globals.citydiameter) 
					city[x][y].addEdge(new GraphEdge(city[x][y], city[x][y+1], getOneStepCarGeneralizedCost(x, y, 0, 1, m))); 
			}

		//DONE!!
		
	}
	
	
	private void writeTravelTimesToFile(Map<Integer, Double> times, int originid, String filestem) throws java.io.IOException
	{
		int[] origxy = getXYforID(originid);
		int origx = origxy[0];
		int origy = origxy[1];
		
		java.io.PrintWriter f = new java.io.PrintWriter(new java.io.FileWriter(filestem+"_"+origx+"_"+origy+".tab"));
		for(Integer dest : times.keySet())
		{
			int[] xy = getXYforID(dest);
			int x = xy[0];
			int y = xy[1];
			f.println("gridsquare_"+x+"_"+y+" "+times.get(dest));
		}
		
		f.close();
	}

	private void writeTravelTimesToFile(double[][] times, int originid, String filestem) throws java.io.IOException
	{
		int[] origxy = getXYforID(originid);
		int origx = origxy[0];
		int origy = origxy[1];
		
		java.io.PrintWriter f = new java.io.PrintWriter(new java.io.FileWriter(filestem+"_"+origx+"_"+origy+".tab"));
		for(int dest = 0; dest < globals.citydiameter*globals.citydiameter; dest++)
		{
			int[] xy = getXYforID(dest);
			int x = xy[0];
			int y = xy[1];
			f.println("gridsquare_"+x+"_"+y+" "+times[originid][dest]);
		}
		
		f.close();
	}

	
	private void writeDataToFile(double[] data, String filestem) throws java.io.IOException
	{		
		java.io.PrintWriter f = new java.io.PrintWriter(new java.io.FileWriter(filestem+".tab"));
		for(int dest = 0; dest < globals.citydiameter*globals.citydiameter; dest++)
		{
			int[] xy = getXYforID(dest);
			int x = xy[0];
			int y = xy[1];
			f.println("gridsquare_"+x+"_"+y+" "+data[dest]);
		}
		
		f.close();
	}

	
	
	
	private double[][] getCarTravelTimes() throws java.io.IOException
	{
		double[][] result = new double[globals.citydiameter*globals.citydiameter][globals.citydiameter*globals.citydiameter];
		
		
		java.util.Map<String, GraphNode> graph = new java.util.HashMap<String, GraphNode>();
		//initialize edges and edge weights to be travel times by car
		this.initializeCityGraphEdgesWithCarTravelTimes(); 
		for(int x = 0; x < globals.citydiameter; x++)
			for(int y = 0; y < globals.citydiameter; y++)
				graph.put(""+this.getID_forPosition(x, y), this.city[x][y]);
		
		for(int x = 0; x < globals.citydiameter; x++)
			for(int y = 0; y < globals.citydiameter; y++)
			{
				GraphNode shortestpathtree = rses.math.GraphUtil.getShortestPathTree(""+getID_forPosition(x, y), graph);
				//now traverse the tree and get the shortest path to every other node
				
				Debug.println("Got shortest path tree from "+x+" , "+y, Debug.INFO);
				//rses.math.GraphUtil.printGraph(shortestpathtree, System.out);
				
				//now get times
				Map<String, Double> times = GraphUtil.getPathLengthsFromPathTree(shortestpathtree);
				
				//if(Math.random() < 0.005) //print 0.5% of them to files
				//	writeTravelTimesToFile(dists, shortestpathtree.getId());
				for(String dest : times.keySet())
				{
					double time = times.get(dest);
					
					result[getID_forPosition(x, y)][Integer.parseInt(dest)] = time;
				}				
			}
		
		
		
		return result;
	}



	private double[][] getCarWalkGeneralizedCosts(double[][] basecosts) throws java.io.IOException
	{
		double[][] result = new double[globals.citydiameter*globals.citydiameter][globals.citydiameter*globals.citydiameter];
		
		for(int Orig = 0; Orig < globals.citydiameter*globals.citydiameter; Orig++)
			for(int destx = 0; destx < globals.citydiameter; destx++)
				for(int desty = 0; desty < globals.citydiameter; desty++)
				{
					double mincost = Double.MAX_VALUE;
					if(destx > 0) mincost = Math.min(mincost, basecosts[Orig][getID_forPosition(destx-1, desty)]);
					if(destx+1 < globals.citydiameter) mincost = Math.min(mincost, basecosts[Orig][getID_forPosition(destx+1, desty)]);
					if(desty > 0) mincost = Math.min(mincost, basecosts[Orig][getID_forPosition(destx, desty-1)]);
					if(desty+1 > globals.citydiameter) mincost = Math.min(mincost, basecosts[Orig][getID_forPosition(destx, desty+1)]);
					
					//ok, we park at lowest cost adjacent square and then just walk
					result[Orig][getID_forPosition(destx, desty)] = mincost + globals.walkcostperkm*globals.gridsquare_edgekm;
				}

		return result;
				
	}
	
	



	private double[][] getCarTravelGeneralizedCosts(mode m, boolean initializeDistancesOnlyThenAbort) throws java.io.IOException
	{
		if(m != mode.CAR && m != mode.EV && m != mode.EVMINI)
			throw new RuntimeException("Cant Car-Walk with mode "+m);

		double[][] result = new double[globals.citydiameter*globals.citydiameter][globals.citydiameter*globals.citydiameter];
		
		//do trip distances at the same time
		int tripdistindex;
		if(m == mode.CAR) tripdistindex = 0;
		else if(m == mode.EV) tripdistindex = 1;
		else if(m == mode.EVMINI) tripdistindex = 2;
		else throw new RuntimeException("Double-impossible!");
		
		this.cartripdistancesbycarmode[tripdistindex] = new double[globals.citydiameter*globals.citydiameter][globals.citydiameter*globals.citydiameter];
		this.congestionzonetripdistancesbycarmode[tripdistindex] = new double[globals.citydiameter*globals.citydiameter][globals.citydiameter*globals.citydiameter];
		this.congestionzone2tripdistancesbycarmode[tripdistindex] = new double[globals.citydiameter*globals.citydiameter][globals.citydiameter*globals.citydiameter];
		
		if(initializeDistancesOnlyThenAbort) return null;
		
		//build a new graph of the city, but this time we include all prices in edge weights, including congestion
		this.initializeCityGraphWithCarGeneralizedCosts(m);
		java.util.Map<String, GraphNode> graph = new java.util.HashMap<String, GraphNode>();
		for(int x = 0; x < globals.citydiameter; x++)
			for(int y = 0; y < globals.citydiameter; y++)
				graph.put(""+this.getID_forPosition(x, y), this.city[x][y]);
		
		//now get shortest path (by generalized cost) for each origin
		for(int x = 0; x < globals.citydiameter; x++)
			for(int y = 0; y < globals.citydiameter; y++)
			{
				GraphNode shortestpathtree = rses.math.GraphUtil.getShortestPathTree(""+getID_forPosition(x, y), graph);
				//now traverse the tree and get the shortest path to every other node
				
				Debug.println("Got shortest path tree from "+x+" , "+y, Debug.INFO);
				//rses.math.GraphUtil.printGraph(shortestpathtree, System.out);
				
				//we need to do some final adjustment, because we have per-trip costs to add 
				Map<String, Double> costs = GraphUtil.getPathLengthsFromPathTree(shortestpathtree);
				//if(Math.random() < 0.005) //print 0.5% of them to files
				//	writeTravelTimesToFile(dists, shortestpathtree.getId());

				for(String dest : costs.keySet())
				{
					double cost = costs.get(dest);
					int[] destxy = getXYforID(Integer.parseInt(dest));
					
					//add carauxcost
					cost += (globals.carauxtime/60.0)*globals.valueoftime_dollarsperhour;
					
					//add parking costs
					cost += getParkingCosts(destxy[0], destxy[1], m);

					//per-trip costs
					if(m == mode.CAR)
						cost += globals.othercarcosts_pertrip;
					else if(m == mode.EV)
						cost += globals.ev1costpertrip_dollars;
					else if(m == mode.EVMINI)
						cost += globals.evsmallcostpertrip_dollars;
					else
						throw new RuntimeException("Invalid mode "+m);
				
					//special case -- travel within a zone should have non-zero distance.
					if(getID_forPosition(x, y) == Integer.parseInt(dest)) 
					{
						cost += this.getCarCostForDistAndTimeOnly(mintripdist, mintripdist/getCarSpeed(x, y), m);
						
						if(isInCongestionZone(x, y)) {
							if(m == mode.EVMINI) cost += mintripdist*this.congestioncost_perkm*globals.evsmallcongestionfact;
							else if(m == mode.EV) cost += mintripdist*this.congestioncost_perkm*globals.ev1congestionfact;
							else cost += mintripdist*this.congestioncost_perkm;
						}
						if(isInCongestionZone2(x, y)) {
							if(m == mode.EVMINI) cost += mintripdist*this.congestioncost2_perkm*globals.evsmallcongestionfact;
							else if(m == mode.EV) cost += mintripdist*this.congestioncost2_perkm*globals.ev1congestionfact;
							else cost += mintripdist*this.congestioncost2_perkm;
						}
					}
					
					
					result[Integer.parseInt(shortestpathtree.getId())][Integer.parseInt(dest)] = cost;
				}
				
				
				//now do trip distances. First replace edge weights with distances
				for(GraphEdge e : shortestpathtree.getAllEdgesInGraph())
					e.weight = globals.gridsquare_edgekm;
				//then get the distances
				Map<String, Double> distances = GraphUtil.getPathLengthsFromPathTree(shortestpathtree);
				for(String dest : distances.keySet()) 
				{
					this.cartripdistancesbycarmode[tripdistindex][Integer.parseInt(shortestpathtree.getId())][Integer.parseInt(dest)] = distances.get(dest);
					//Debug.println("Trip distance from "+shortestpathtree.getId()+" to "+dest+" by carmode with index "+tripdistindex+" is "+distances.get(dest)+" km", Debug.INFO);
				}
				
				//now do congestion trip distances!
				for(GraphEdge e : shortestpathtree.getAllEdgesInGraph())
				{
					int[] fromxy = getXYforID(Integer.parseInt(e.leadsfrom.getId()));
					int[] toxy = getXYforID(Integer.parseInt(e.leadsto.getId()));
					
					e.weight = 0.0; //by default it is 0. Only non-zero for congestion zone
					if(isInCongestionZone(fromxy[0], fromxy[1])) e.weight += globals.gridsquare_edgekm/2.0;
					if(isInCongestionZone(toxy[0], toxy[1])) e.weight += globals.gridsquare_edgekm/2.0;
				}
				//get the distances for inner congestion zone
				Map<String, Double> congdistances = GraphUtil.getPathLengthsFromPathTree(shortestpathtree);
				for(String dest : congdistances.keySet())
					this.congestionzonetripdistancesbycarmode[tripdistindex][Integer.parseInt(shortestpathtree.getId())][Integer.parseInt(dest)] = congdistances.get(dest);

				//now do outer zone
				for(GraphEdge e : shortestpathtree.getAllEdgesInGraph())
				{
					int[] fromxy = getXYforID(Integer.parseInt(e.leadsfrom.getId()));
					int[] toxy = getXYforID(Integer.parseInt(e.leadsto.getId()));
					
					e.weight = 0.0; //by default it is 0. Only non-zero for congestion zone
					if(isInCongestionZone2(fromxy[0], fromxy[1])) e.weight += globals.gridsquare_edgekm/2.0;
					if(isInCongestionZone2(toxy[0], toxy[1])) e.weight += globals.gridsquare_edgekm/2.0;
				}
				//get the distances
				Map<String, Double> cong2distances = GraphUtil.getPathLengthsFromPathTree(shortestpathtree);
				for(String dest : cong2distances.keySet())
					this.congestionzone2tripdistancesbycarmode[tripdistindex][Integer.parseInt(shortestpathtree.getId())][Integer.parseInt(dest)] = cong2distances.get(dest);

				
				
			}
		return result;
	}

	
	private double getParkingCosts(int x, int y, mode m) 
	{
		double densityatdest = lookup("activities", x, y);
		densityatdest /= globals.gridsquare_edgekm*globals.gridsquare_edgekm;
		double padjust = 1.0;
		if(m == mode.EVMINI)
			padjust = globals.evsmallparkcostratio;
		
		if(densityatdest < globals.parkThresh0) return globals.baseParkingCost*padjust;
		else if(densityatdest < globals.parkThresh1) return globals.parkCost1*padjust;
		else if(densityatdest < globals.parkThresh2) return globals.parkCost2*padjust;
		else if(densityatdest < globals.parkThresh3) return globals.parkCost3*padjust;
		else return globals.parkCost4*padjust;
	}
	
	
	public double getManhattanDist(int x, int y, int x2, int y2)
	{
		return globals.gridsquare_edgekm*(Math.abs(x-x2)+Math.abs(y-y2));
	}


	
	
	
	/**
	 * Work out the probability of travel to each destination, which is a function of the generalized cost to each destination
	 * 
	 * 
	 * 
	 * @param cartraveltimes
	 * @param cargeneralizedcosts
	 * @param carwalkgeneralizedcost
	 * @return
	 */
	public double[][] getTripProbs(double[] destnormalizefact, double[][] cartraveltimes, double[][] cargeneralizedcosts, double[][] carwalkgeneralizedcost,
			double[][] evgencost, double[][] evwalkgencost, double[][] evsmallgencost, double[][] evsmallwalkgencost,
			double tripshare, double[][] activitiesreachablebyfixedbudget)
	throws java.io.IOException
	{
		Debug.println("Calculating Trip probabilities", Debug.INFO);
		double[][] result = new double[globals.citydiameter*globals.citydiameter][globals.citydiameter*globals.citydiameter];
		double[][] resultnondiscretionary = new double[globals.citydiameter*globals.citydiameter][globals.citydiameter*globals.citydiameter];
		

		for(int x = 0; x < globals.citydiameter; x++)
		{
			for(int y = 0; y < globals.citydiameter; y++)
			{
				int orig = getID_forPosition(x, y);
				
				double probsum = 0.0;
				double probsum2 = 0.0;
				
				//work out the cost from this square to every other square
				for(int destx = 0; destx < globals.citydiameter; destx++)
				{
					for(int desty = 0; desty < globals.citydiameter; desty++)
					{
						//work out all the generalized costs for every mode first
						int dest = getID_forPosition(destx, desty);
						
						double tripdist = this.getManhattanDist(x, y, destx, desty);
						double carcost = Double.MAX_VALUE;
						double carwalkcost = Double.MAX_VALUE;
						double walkcost = -globals.walkbonus+globals.walkcostperkm*getManhattanDist(x,y,destx,desty);
						double bikecost = -globals.bikebonus+globals.bikecostperkm*getManhattanDist(x,y,destx,desty);
						double[] components = new double[4]; //ptwait, ptaux, ptinv, ptfare
						double ptcost = getPTCost(cartraveltimes, x, y, destx, desty, components);
						
						double evcost = Double.MAX_VALUE;
						double evwalkcost = Double.MAX_VALUE;
						double evsmallcost = Double.MAX_VALUE;
						double evsmallwalkcost = Double.MAX_VALUE;

						double transfercostdollars = globals.valueoftime_dollarsperhour*((globals.miscpttransfercost-globals.ptaux*globals.ptauxweight)/60.0);
						double walkptcost = Double.MAX_VALUE;
						double bikeptcost = Double.MAX_VALUE;
						if(globals.allowtransferstopt  && tripdist > globals.gridsquare_edgekm) {
							walkptcost = this.getWalkPTcost(cartraveltimes, transfercostdollars, x, y, destx, desty)[0];
							bikeptcost = this.getBikePTcost(cartraveltimes, transfercostdollars, x, y, destx, desty)[0];
						}
						double carptcost = Double.MAX_VALUE;
						double evptcost = Double.MAX_VALUE;
						double evsmallptcost = Double.MAX_VALUE;
						
						if(cargeneralizedcosts != null) 
						{
							carcost = cargeneralizedcosts[orig][dest];
							carwalkcost = carwalkgeneralizedcost[orig][dest];
							if(globals.allowtransferstopt  && tripdist > globals.gridsquare_edgekm)
								carptcost = this.getMiscPTcost(mode.CAR, cargeneralizedcosts, cartraveltimes, transfercostdollars, x, y, destx, desty)[0];
						}
						
						if(evgencost != null) 
						{
							evcost = evgencost[orig][dest];
							evwalkcost = evwalkgencost[orig][dest];
							if(globals.allowtransferstopt  && tripdist > globals.gridsquare_edgekm)
								evptcost = this.getMiscPTcost(mode.EV, evgencost, cartraveltimes, transfercostdollars, x, y, destx, desty)[0];
						}
						
						if(evsmallgencost != null) 
						{
							evsmallcost = evsmallgencost[orig][dest];
							evsmallwalkcost = evsmallwalkgencost[orig][dest];
							if(globals.allowtransferstopt  && tripdist > globals.gridsquare_edgekm)
								evsmallptcost = this.getMiscPTcost(mode.EVMINI, evsmallgencost, cartraveltimes, transfercostdollars, x, y, destx, desty)[0];
						}
						
						double[][] modesplit = getModeSplitForTrip(orig, dest, cartraveltimes, cargeneralizedcosts, carwalkgeneralizedcost, evgencost, evwalkgencost, evsmallgencost, evsmallwalkgencost);
						double[] msplit = modesplit[0];
						if(Math.abs(Util.getSum(msplit)-1) > 0.0001) throw new RuntimeException("mode split doesnt add to 1!!!");
						
						double[] costs = new double[mode.NUMMODES];
						costs[mode.CAR.index] = carcost;
						costs[mode.BIKE.index] = bikecost;						                            
						costs[mode.WALK.index] = walkcost;
						costs[mode.PT.index] = ptcost;
						costs[mode.CARWALK.index] = carwalkcost;
						costs[mode.EV.index] = evcost;
						costs[mode.EVWALK.index] = evwalkcost;
						costs[mode.EVMINI.index] = evsmallcost;
						costs[mode.EVMINIWALK.index] = evsmallwalkcost;
						costs[mode.WALKPT.index] = walkptcost;
						costs[mode.BIKEPT.index] = bikeptcost;
						costs[mode.CARPT.index] = carptcost;
						costs[mode.EVPT.index] = evptcost;
						costs[mode.EVMINIPT.index] = evsmallptcost;
						
						
						double weightedcost = 0.0;
						for(mode m : mode.getAllModes())
							weightedcost += msplit[m.index]*costs[m.index];

						double activities = this.lookup("activities", destx, desty);

						if(this.lookup("activities", orig) > 0.0)
						{
							if(weightedcost < globals.valueoftime_dollarsperhour*0.5)
								activitiesreachablebyfixedbudget[0][orig] += tripshare*activities;
							if(weightedcost < globals.valueoftime_dollarsperhour)
								activitiesreachablebyfixedbudget[1][orig] += tripshare*activities;
							if(weightedcost < globals.valueoftime_dollarsperhour*2)
								activitiesreachablebyfixedbudget[2][orig] += tripshare*activities;
						}
						else 
						{
							for(int i = 0; i < 3; i++)
								activitiesreachablebyfixedbudget[i][orig] = Double.NaN;
						}
						

						//tripprob = numberofactivitiesatdestination*exp(-alpha*cost)
						//
						//where alpha = 0.044544406627742505
						//
						//BUT THIS WAS FROM A GENERALIZED COST EXPRESSED IN MINUTES
						//Now, to re-express this in dollars, we take the $ value of time used in Brisbane STM
						//of $11.2
						double dollarvalueperminutefromSTM = 11.2/60.0;
						//now convert the above coefficient into a pure per-$ coefficient using this VoT
						double workcoeff = 0.044544406627742505/dollarvalueperminutefromSTM;
						double othercoeff = workcoeff*1.7; //more sensitive for nonwork trips
						
						
						if(activities > 0) 
						{
							result[orig][dest] = Math.exp(globals.distanceadjustfact*othercoeff*(alpha*activities*globals.destutilityfact-weightedcost))*destnormalizefact[dest];
							//resultnondiscretionary[orig][dest] = (activities/1000.0)*destnormalizefact[dest];	
							resultnondiscretionary[orig][dest] = Math.exp(globals.distanceadjustfact*workcoeff*(alpha*activities*globals.destutilityfact-weightedcost))*destnormalizefact[dest];

						}
						
						
						
						/// gravity model doesnt work because this means that if all travel costs go up there 
						/// is no demand response. 
						///
						//result[orig][dest] = activities*destnormalizefact[dest]/Math.pow(Math.max(10.0, wcost_minutes), 2.0);

						probsum += result[orig][dest];
						probsum2 += resultnondiscretionary[orig][dest];
					}
				}
				
				
				//now we need to normalize trip probabilities
				for(int dest = 0; dest < globals.citydiameter*globals.citydiameter; dest++)
				{
					result[orig][dest] /= probsum;
					
					if(globals.nondiscretionarytrippct > 0.0)
					{
						resultnondiscretionary[orig][dest] /= probsum2;
					
						result[orig][dest] = result[orig][dest]*(1-globals.nondiscretionarytrippct)+
					                                         resultnondiscretionary[orig][dest]*(globals.nondiscretionarytrippct);
					}
				}	


				//DONE!!!!				
			}
		}
		Debug.println("Finished Calculating Trip probabilities", Debug.INFO);
		
		return result;
	}
		
	private double lookup(String tablename, int ID)
	{
		int[] xy = this.getXYforID(ID);
		return lookup(tablename, xy[0], xy[1]);
	}

	private double lookup(String tablename, int x, int y)
	{
		return (Double) database.lookupArbitrary("HypotheticalCity", tablename, "gridsquare_"+x+"_"+y);
	}
	
	
	private double[] getWalkPTcost(double[][] cartt, double transfercost, int origx, int origy, int destx, int desty)
	{
		return this.getMiscPTcost(mode.WALK, null, cartt, transfercost, origx, origy, destx, desty);
	}

	private double[] getBikePTcost(double[][] cartt, double transfercost, int origx, int origy, int destx, int desty)
	{
		return this.getMiscPTcost(mode.BIKE, null, cartt, transfercost, origx, origy, destx, desty);
	}

	
	/* go by some other mode and then go by public transport */
	private double[] getMiscPTcost(mode m, double[][] gencostleg1, double[][] cartt, double transfercost, int origx, int origy, int destx, int desty)
	{
		if((m == mode.WALK || m == mode.BIKE) && gencostleg1 != null) throw new RuntimeException("cant have walk and other mode as first mode of dual mode trip!");

		//make sure that we dont do silly things, so we restrict the length of the first leg to be 
		//less than the total trip distance minus one grid square.
		double totaltripdist = this.getManhattanDist(origx, origy, destx, desty);
		if(totaltripdist <= globals.gridsquare_edgekm) 
			throw new RuntimeException("MiscPT trip makes no sense for a trip of only 1 square!!!!!");
		int maxleg1off = (int) (Math.min(totaltripdist-globals.gridsquare_edgekm, globals.maxptauxdist)/globals.gridsquare_edgekm);
		if(gencostleg1 == null)
			maxleg1off = (int) (Math.min(totaltripdist-globals.gridsquare_edgekm, globals.maxactiveptauxdist)/globals.gridsquare_edgekm);
		if(maxleg1off == 0) throw new RuntimeException("Impossible!!!!!!"); 
		
		
		double mincost = Double.MAX_VALUE;
		double leg1dist = 0.0;
		double leg2dist = 0.0;
		int minx = origx;
		int miny = origy;
		int Orig = this.getID_forPosition(origx, origy);

		//We only consider moving at most 2 square away from the destination
		int xleft = destx > origx ? -2 : -maxleg1off;
		int xright = destx > origx ? maxleg1off: 2;
		int yleft = desty > origy ? -2 : -maxleg1off;
		int yright = desty > origy ? maxleg1off: 2;
		
		for(int xoff = xleft; xoff <= xright; xoff++)
		{
			for(int yoff = yleft; yoff <= yright; yoff++)
			{
				int x = origx+xoff;
				int y = origy+yoff;
				if(x < 0 || x >= globals.citydiameter || y < 0 || y >= globals.citydiameter || (xoff == 0 && yoff == 0))
					continue;
				
				int leg1dest = this.getID_forPosition(x, y);

				double cost;
				double dist = this.getManhattanDist(origx, origy, x,y);
				//car modes may not go by manhattan distance, because of congestion
				if(m == mode.CAR) dist = this.cartripdistancesbycarmode[0][Orig][leg1dest];
				else if(m == mode.EV) dist = this.cartripdistancesbycarmode[1][Orig][leg1dest];
				else if(m == mode.EVMINI) dist = this.cartripdistancesbycarmode[2][Orig][leg1dest];
				
				if(m == mode.WALK) cost = -globals.walkbonus+globals.walkcostperkm*dist + getPTCost(cartt, x, y, destx, desty, null);
				else if(m == mode.BIKE) cost = -globals.bikebonus+globals.bikecostperkm*dist + getPTCost(cartt, x, y, destx, desty, null);
				else cost = gencostleg1[Orig][leg1dest] + this.getPTCost(cartt, x, y, destx, desty, null);
				
				if(cost < mincost) 
				{
					mincost = cost;
					minx = x;
					miny = y;
					leg1dist = dist;
					leg2dist = this.getManhattanDist(x, y, destx, desty);
				}
			}
		}
		
		if(minx == origx && miny == origy)
			throw new RuntimeException("Impossible!!");
		
		
		return new double[] {mincost+transfercost, leg1dist, leg2dist};
	}
	
	
	private double getPTCost(double[][] cartraveltimes, int origx, int origy, int destx, int desty, double[] components)
	{
		//ptcost is
		//
		//ptaux
		//+ptwait
		//+ptinv
		//+ptfare
		//
		int Orig = this.getID_forPosition(origx, origy);
		int Dest = this.getID_forPosition(destx, desty);
		double origdistcbd = this.getDistFromCBD(origx, origy);
		double destdistcbd = this.getDistFromCBD(destx, desty);
		double origdensity = lookup("activities", origx, origy)/(globals.gridsquare_edgekm*globals.gridsquare_edgekm);
		double destdensity = lookup("activities", destx, desty)/(globals.gridsquare_edgekm*globals.gridsquare_edgekm);
		
		double ptwait = 28.40129923903088 + -1.2247770324324094*Math.log(origdensity+1) + -1.245377938541308*Math.log(destdensity+1) + 0.063920062790916*origdistcbd + 0.06386855997665401*destdistcbd;
		if(globals.modelhubs)
		{
			if(origx % 3 == 1 && origy % 3 == 1 && destx % 3 == 1 && desty % 3 == 1)
				ptwait = Math.min(ptwait*0.5, 5.0); //between hubs, at most 10 minute headway
			else if(origx % 3 == 1 && origy % 3 == 1 || destx % 3 == 1 && desty % 3 == 1)
				ptwait = Math.min(ptwait*0.5, 10.0); //from or to hubs, at most 20 minutes headway
		}
		
		if(ptwait < globals.minptwait) ptwait = globals.minptwait;
			
		double ptaux = globals.ptaux; //flat walk penalty
			
		double ptratio = -0.0078*Math.log(origdensity+1) -0.0178*Math.log(destdensity+1) -0.0048*origdistcbd + 0.0063*destdistcbd + 1.2659;
		if(globals.modelhubs)
		{
			if(origx % 3 == 1 && origy % 3 == 1 || destx % 3 == 1 && desty % 3 == 1)
				ptratio = Math.max(globals.minptratio, globals.ptinvadjust*ptratio);
			else
				ptratio = Math.max(globals.minptratio, (1-(1-globals.ptinvadjust)*0.5)*ptratio); //only half the improvement outside hubs
		}
		else
			ptratio = Math.max(globals.minptratio, ptratio);
			
		
		double VoT_perminute = globals.valueoftime_dollarsperhour/60.0;
		double dollarcost = (ptwait*globals.ptwaitweight+ptaux*globals.ptauxweight+ptratio*cartraveltimes[Orig][Dest]*globals.ptinvweight)*VoT_perminute;
		
		//add fare cost, from Tim Baynes
		double farecostperkm = 2.445*(Math.pow(Math.max(getManhattanDist(origx, origy, destx, desty),mintripdist),-0.903)); 
		double farecost = farecostperkm*getManhattanDist(origx, origy, destx, desty); 
		dollarcost += farecost*globals.ptfareadjust;
	
		if(components != null) {
			components[0] = ptwait; //wait
			components[1] = ptaux; //aux
			components[2] = ptratio*cartraveltimes[Orig][Dest]; //invehicle
			components[3] = farecost; //fare
		}
		
		return dollarcost*globals.ptadjust;
	}




	
	/** This calculates all the main trip stats
	 * 
	 * returns the number of trips per activity to each destination
	 * 
	 */
	private double[] calcTripStats(double tripshare, double[][] tripprobs, double[][] cartraveltimes, double[][] cargeneralizedcosts, double[][] carwalkgeneralizedcost,
			double[][] evgencost, double[][] evwalkgencost, double[][] evsmallgencost, double[][] evsmallwalkgencost, 
			java.util.List<Integer> toprint, 
			double[] tripkmbyorigin, 
			double[] tripsbyorigin, double[] onelemarray_tripkmpassingthroughcongestion, 
			double[][] tripsbymodebyorigin, 
			double[][] tripsbymodebydest, 
			double[] tripsbymode, 
			double[] tripkmbymode)
	throws java.io.IOException
	{
		//stats we calculate
		//
		//a) mode share for each mode by origin
		//b) mode share for each mode by destination (TODO later)
		//c) average trip length by origin
		//d) total number of kilometres per capita by mode
				
		
		Debug.println("Calculating trip stats for "+tripshare+" market segment", Debug.INFO);
		
		for(int x = 0; x < globals.citydiameter; x++)
		{
			for(int y = 0; y < globals.citydiameter; y++)
			{
				//calculates cumulative trip stats
				getTripStatsByOrigin(tripshare, x, y, tripprobs, cartraveltimes, cargeneralizedcosts, carwalkgeneralizedcost, evgencost, evwalkgencost, evsmallgencost, evsmallwalkgencost, toprint,
						tripkmbyorigin, tripsbyorigin, onelemarray_tripkmpassingthroughcongestion, 
						tripsbymodebyorigin, tripsbymodebydest, tripsbymode, tripkmbymode);
			}
		}

		//total activities in city        
		double totalactivities = 0.0;
		for(int A = 0; A < globals.citydiameter; A++)
			for(int B = 0; B < globals.citydiameter; B++)
				totalactivities += lookup("activities", A, B);
		double totalpop = totalactivities/globals.activitiestopopulationdivisor;
		
		Debug.println("HEADLINE RESULTS (CUMULATIVE) FOR TRIP TYPE", Debug.IMPORTANT);		
		Debug.println("", Debug.IMPORTANT);
		
		Debug.println("Total activities in city: "+totalactivities, Debug.IMPORTANT);
		Debug.println("Total population: "+totalpop, Debug.IMPORTANT);
		Debug.println("", Debug.IMPORTANT);
		
		double meantriplength = Util.getSum(tripkmbymode)/Util.getSum(tripsbymode);
		Debug.println("Mean trip length (in km) is "+meantriplength, Debug.IMPORTANT);
		Debug.println("", Debug.IMPORTANT);

		for(mode m : mode.getAllModes())
			Debug.println("Trip km by mode "+m+" is "+tripkmbymode[m.index], Debug.IMPORTANT);
		Debug.println("", Debug.IMPORTANT);
		
		for(mode m : mode.getAllModes())
			Debug.println("Trip km by mode "+m+" is "+tripkmbymode[m.index]/totalpop+" per capita ("+(100*tripkmbymode[m.index])/Util.getSum(tripkmbymode)+" %)", Debug.IMPORTANT);
		Debug.println("", Debug.IMPORTANT);

		for(mode m : mode.getAllModes())
			Debug.println("Trips by mode "+m+" is "+tripsbymode[m.index]/totalpop+" per capita ("+(100*tripsbymode[m.index])/Util.getSum(tripsbymode)+" %)", Debug.IMPORTANT);
		Debug.println("", Debug.IMPORTANT);

		Debug.println("Annual car-km (adjusted for congestion-contribution) passing through congestion zone is "+onelemarray_tripkmpassingthroughcongestion[0], Debug.IMPORTANT);
		Debug.println("Thats ~ "+onelemarray_tripkmpassingthroughcongestion[0]/(Math.PI*Math.pow(globals.congestionzoneradius, 2))+" per km^2", Debug.IMPORTANT);
		Debug.println("Annual car-km (adjusted for congestion-contribution) passing through outer congestion zone is "+onelemarray_tripkmpassingthroughcongestion[1], Debug.IMPORTANT);
		Debug.println("Thats ~ "+onelemarray_tripkmpassingthroughcongestion[1]/(Math.PI*Math.pow(globals.congestionzone2radius, 2))+" per km^2", Debug.IMPORTANT);
		Debug.println("", Debug.IMPORTANT);
		
		//write out mode share by origin
		for(mode m : mode.getAllModes()) 
		{
			double[] datatmp = new double[globals.citydiameter*globals.citydiameter];
			for(int Orig = 0; Orig < globals.citydiameter*globals.citydiameter; Orig++)
				datatmp[Orig] = tripsbymodebyorigin[m.index][Orig]/tripsbyorigin[Orig];
			this.writeDataToFile(datatmp, "modesharebyorigin_"+m);
		}

		//write out mode share by destination
		//but first get total trips to each destination
		double[] tripstodest = new double[globals.citydiameter*globals.citydiameter];
		for(int Dest = 0; Dest < globals.citydiameter*globals.citydiameter; Dest++)
			for(mode m : mode.getAllModes())
				tripstodest[Dest] += tripsbymodebydest[m.index][Dest];
		this.writeDataToFile(tripstodest, "tripsto");			
		//now get actual mode share	by destination
		for(mode m : mode.getAllModes()) 
		{
			double[] datatmp = new double[globals.citydiameter*globals.citydiameter];
			for(int Dest = 0; Dest < globals.citydiameter*globals.citydiameter; Dest++)
				datatmp[Dest] = tripsbymodebydest[m.index][Dest]/tripstodest[Dest];
			this.writeDataToFile(datatmp, "modesharebydest_"+m);
		}
		
		//trips to dest per activity at dest
		for(int Dest = 0; Dest < globals.citydiameter*globals.citydiameter; Dest++)
			tripstodest[Dest] /= lookup("activities", getXYforID(Dest)[0], getXYforID(Dest)[1]);
		this.writeDataToFile(tripstodest, "tripsto_peractivity");

		//trips per square (by origin)
		writeDataToFile(tripsbyorigin, "tripsbyorigin");
		for(int Orig = 0; Orig < globals.citydiameter*globals.citydiameter; Orig++)
		{
			double check = 0.0;
			for(mode m : mode.getAllModes()) check += tripsbymodebyorigin[m.index][Orig];
			if(Math.abs(tripsbyorigin[Orig]-check) > 0.0001)
				throw new RuntimeException("trips by origin and trips by mode by origin do not match!!!");
		}
		
		
		//trip length by origin
		double[] triplengthbyorigin = new double[globals.citydiameter*globals.citydiameter];
		for(int Orig = 0; Orig < triplengthbyorigin.length; Orig++)
			triplengthbyorigin[Orig] = tripkmbyorigin[Orig]/tripsbyorigin[Orig];
		this.writeDataToFile(triplengthbyorigin, "triplengthbyorigin");
		
		return tripstodest;
	}

	
	private void getTripStatsByOrigin(double tripshare, int x, int y, double[][] tripprobs, double[][] cartraveltimes,
			double[][] cargeneralizedcosts, 
			double[][] carwalkgeneralizedcost, double[][] evgencost, double[][] evwalkgencost, double[][] evsmallgencost, 
			double[][] evsmallwalkgencost, java.util.List<Integer> toprint,
			double[] tripkmbyorigin, 
			double[] tripsbyorigin, double[] onelemarray_tripkmpassingthroughcongestion, 
			double[][] tripsbymodebyorigin,  
			double[][] tripsbymodebydest, 
			double[] tripsbymode, 
			double[] tripkmbymode) throws java.io.IOException
	{
		int Orig = getID_forPosition(x, y);
		
		//this is how many annual trips there are from this origin
		//for this particular trip modechoice combo
		double tripsfromthisorigin = lookup("activities", x, y)*globals.tripsgeneratedperactivity*tripshare;
		tripsbyorigin[Orig] += tripsfromthisorigin;
		

		java.util.Map<Integer, Double>[] gencostmap = null; 
		java.util.Map<Integer, Double>[] modeshare = null;
		java.util.Map<Integer, Double>[] ptcomponents = null;
		if(toprint != null && toprint.contains(new Integer(Orig))) 
		{
			gencostmap = new java.util.Map[mode.NUMMODES];
			modeshare = new java.util.Map[mode.NUMMODES];
			ptcomponents = new java.util.Map[4];
			for(int i = 0; i < mode.NUMMODES; i++) {
				gencostmap[i] = new java.util.HashMap<Integer, Double>();
				modeshare[i] = new java.util.HashMap<Integer, Double>();
			}
			for(int i = 0; i < 4; i++) ptcomponents[i] = new java.util.HashMap<Integer, Double>();
		}
		
		//now loop through all possible destinations and calculate stats
		for(int destx = 0; destx < globals.citydiameter; destx++)
		{
			for(int desty = 0; desty < globals.citydiameter; desty++)
			{
				int Dest = getID_forPosition(destx, desty);
				double prob = tripprobs[Orig][Dest];
				if(prob == 0.0) continue;
				
				this.odtripcounts[Orig][Dest] += prob*tripsfromthisorigin;
				
				//work out mode split percentage for this trip
				double[][] modesplitgencosts = getModeSplitForTrip(Orig, Dest, cartraveltimes, cargeneralizedcosts, carwalkgeneralizedcost,
		 				evgencost, evwalkgencost, evsmallgencost, evsmallwalkgencost);
		 		double[] modesplit = modesplitgencosts[0];
				double[] gencosts = modesplitgencosts[1];
				double[] ptcomps = modesplitgencosts[2];
				double[] leg1dists = modesplitgencosts[3];
				double[] leg2dists = modesplitgencosts[4];
				       
				
				if(gencostmap != null) 
				{
					for(mode m : mode.getAllModes())
					{
						gencostmap[m.index].put(Dest, gencosts[m.index]);
						modeshare[m.index].put(Dest, modesplit[m.index]);						
					}
					for(int i = 0; i < 4; i++)
						ptcomponents[i].put(Dest, ptcomps[i]);
				}
				
				//count the trips
				for(mode m : mode.getAllModes())
				{
					tripsbymode[m.index] += modesplit[m.index]*prob*tripsfromthisorigin;
					tripsbymodebyorigin[m.index][Orig] += modesplit[m.index]*prob*tripsfromthisorigin;
					tripsbymodebydest[m.index][Dest] += modesplit[m.index]*prob*tripsfromthisorigin;
				}
				
				//count the km
				//
				double tripdist = getManhattanDist(x, y, destx, desty);
				if(cartripdistancesbycarmode[0][Orig][Dest] > tripdist*1.5) Debug.println("WARNING -- car trip distance is more than 1.5 times manhattan distance.... suspicious routing going on", Debug.IMPORTANT);
				if(cartripdistancesbycarmode[1][Orig][Dest] > tripdist*1.5) Debug.println("WARNING -- ev trip distance is more than 1.5 times manhattan distance.... suspicious routing going on", Debug.IMPORTANT);
				if(cartripdistancesbycarmode[2][Orig][Dest] > tripdist*1.5) Debug.println("WARNING -- evmini trip distance is more than 1.5 times manhattan distance.... suspicious routing going on", Debug.IMPORTANT);
					
				if(destx == x && desty == y) tripdist = this.mintripdist;

				
				//have to do these manually because some of them (CARWALK and the like) are fiddly
				tripkmbymode[mode.CAR.index] += modesplit[mode.CAR.index]*prob*tripsfromthisorigin*Math.max(cartripdistancesbycarmode[0][Orig][Dest], this.mintripdist);
				onelemarray_tripkmpassingthroughcongestion[0] += modesplit[mode.CAR.index]*prob*tripsfromthisorigin*this.congestionzonetripdistancesbycarmode[0][Orig][Dest];
				onelemarray_tripkmpassingthroughcongestion[1] += modesplit[mode.CAR.index]*prob*tripsfromthisorigin*this.congestionzone2tripdistancesbycarmode[0][Orig][Dest];
				tripkmbymode[mode.BIKE.index] += modesplit[mode.BIKE.index]*prob*tripsfromthisorigin*tripdist;
				tripkmbymode[mode.WALK.index] += modesplit[mode.WALK.index]*prob*tripsfromthisorigin*tripdist;
				tripkmbymode[mode.PT.index] += modesplit[mode.PT.index]*prob*tripsfromthisorigin*tripdist;
				tripkmbymode[mode.EV.index] += modesplit[mode.EV.index]*prob*tripsfromthisorigin*Math.max(cartripdistancesbycarmode[1][Orig][Dest], this.mintripdist);
				onelemarray_tripkmpassingthroughcongestion[0] += modesplit[mode.EV.index]*prob*tripsfromthisorigin*this.congestionzonetripdistancesbycarmode[1][Orig][Dest]*globals.ev1congestionfact;
				onelemarray_tripkmpassingthroughcongestion[1] += modesplit[mode.EV.index]*prob*tripsfromthisorigin*this.congestionzone2tripdistancesbycarmode[1][Orig][Dest]*globals.ev1congestionfact;
				tripkmbymode[mode.EVMINI.index] += modesplit[mode.EVMINI.index]*prob*tripsfromthisorigin*Math.max(cartripdistancesbycarmode[2][Orig][Dest], this.mintripdist);
				onelemarray_tripkmpassingthroughcongestion[0] += modesplit[mode.EVMINI.index]*prob*tripsfromthisorigin*this.congestionzonetripdistancesbycarmode[2][Orig][Dest]*globals.evsmallcongestionfact;
				onelemarray_tripkmpassingthroughcongestion[1] += modesplit[mode.EVMINI.index]*prob*tripsfromthisorigin*this.congestionzone2tripdistancesbycarmode[2][Orig][Dest]*globals.evsmallcongestionfact;

				tripkmbyorigin[Orig] += modesplit[mode.CAR.index]*prob*tripsfromthisorigin*Math.max(cartripdistancesbycarmode[0][Orig][Dest], this.mintripdist);
				tripkmbyorigin[Orig] += modesplit[mode.BIKE.index]*prob*tripsfromthisorigin*tripdist;
				tripkmbyorigin[Orig] += modesplit[mode.WALK.index]*prob*tripsfromthisorigin*tripdist;
				tripkmbyorigin[Orig] += modesplit[mode.PT.index]*prob*tripsfromthisorigin*tripdist;
				tripkmbyorigin[Orig] += modesplit[mode.EV.index]*prob*tripsfromthisorigin*Math.max(cartripdistancesbycarmode[1][Orig][Dest], this.mintripdist);
				tripkmbyorigin[Orig] += modesplit[mode.EVMINI.index]*prob*tripsfromthisorigin*Math.max(cartripdistancesbycarmode[2][Orig][Dest], this.mintripdist);

				//the congestion calculations for the CAR/EV -> PT 2-stage trips here are 
				//approximate only, because they are based solely on the origin.
				tripkmbymode[mode.WALK.index] += modesplit[mode.WALKPT.index]*prob*tripsfromthisorigin*leg1dists[0]; 
				tripkmbymode[mode.BIKE.index] += modesplit[mode.BIKEPT.index]*prob*tripsfromthisorigin*leg1dists[1]; 
				tripkmbymode[mode.CAR.index] += modesplit[mode.CARPT.index]*prob*tripsfromthisorigin*leg1dists[2]; 
				if(isInCongestionZone(x, y)) onelemarray_tripkmpassingthroughcongestion[0] += modesplit[mode.CARPT.index]*prob*tripsfromthisorigin*leg1dists[2]; 
				if(isInCongestionZone2(x, y)) onelemarray_tripkmpassingthroughcongestion[1] += modesplit[mode.CARPT.index]*prob*tripsfromthisorigin*leg1dists[2]; 
				tripkmbymode[mode.EV.index] += modesplit[mode.EVPT.index]*prob*tripsfromthisorigin*leg1dists[3]; 
				if(isInCongestionZone(x, y)) onelemarray_tripkmpassingthroughcongestion[0] += modesplit[mode.EVPT.index]*prob*tripsfromthisorigin*leg1dists[3]; 
				if(isInCongestionZone2(x, y)) onelemarray_tripkmpassingthroughcongestion[1] += modesplit[mode.EVPT.index]*prob*tripsfromthisorigin*leg1dists[3]; 
				tripkmbymode[mode.EVMINI.index] += modesplit[mode.EVMINIPT.index]*prob*tripsfromthisorigin*leg1dists[4]; 
				if(isInCongestionZone(x, y)) onelemarray_tripkmpassingthroughcongestion[0] += modesplit[mode.EVMINIPT.index]*prob*tripsfromthisorigin*leg1dists[4]; 
				if(isInCongestionZone2(x, y)) onelemarray_tripkmpassingthroughcongestion[1] += modesplit[mode.EVMINIPT.index]*prob*tripsfromthisorigin*leg1dists[4]; 
				tripkmbymode[mode.PT.index] += modesplit[mode.WALKPT.index]*prob*tripsfromthisorigin*leg2dists[0]; 
				tripkmbymode[mode.PT.index] += modesplit[mode.BIKEPT.index]*prob*tripsfromthisorigin*leg2dists[1]; 
				tripkmbymode[mode.PT.index] += modesplit[mode.CARPT.index]*prob*tripsfromthisorigin*leg2dists[2]; 
				tripkmbymode[mode.PT.index] += modesplit[mode.EVPT.index]*prob*tripsfromthisorigin*leg2dists[3]; 
				tripkmbymode[mode.PT.index] += modesplit[mode.EVMINIPT.index]*prob*tripsfromthisorigin*leg2dists[4]; 

				tripkmbyorigin[Orig] += modesplit[mode.WALKPT.index]*prob*tripsfromthisorigin*leg1dists[0]; 
				tripkmbyorigin[Orig] += modesplit[mode.BIKEPT.index]*prob*tripsfromthisorigin*leg1dists[1]; 
				tripkmbyorigin[Orig] += modesplit[mode.CARPT.index]*prob*tripsfromthisorigin*leg1dists[2]; 
				tripkmbyorigin[Orig] += modesplit[mode.EVPT.index]*prob*tripsfromthisorigin*leg1dists[3]; 
				tripkmbyorigin[Orig] += modesplit[mode.EVMINIPT.index]*prob*tripsfromthisorigin*leg1dists[4]; 
				tripkmbyorigin[Orig] += modesplit[mode.WALKPT.index]*prob*tripsfromthisorigin*leg2dists[0]; 
				tripkmbyorigin[Orig] += modesplit[mode.BIKEPT.index]*prob*tripsfromthisorigin*leg2dists[1]; 
				tripkmbyorigin[Orig] += modesplit[mode.CARPT.index]*prob*tripsfromthisorigin*leg2dists[2]; 
				tripkmbyorigin[Orig] += modesplit[mode.EVPT.index]*prob*tripsfromthisorigin*leg2dists[3]; 
				tripkmbyorigin[Orig] += modesplit[mode.EVMINIPT.index]*prob*tripsfromthisorigin*leg2dists[4]; 

				
				if(tripdist >= globals.gridsquare_edgekm) 
				{
					tripkmbymode[mode.CAR.index] += modesplit[mode.CARWALK.index]*prob*tripsfromthisorigin*(Math.max(cartripdistancesbycarmode[0][Orig][Dest], this.mintripdist)-globals.gridsquare_edgekm);
					onelemarray_tripkmpassingthroughcongestion[0] += modesplit[mode.CARWALK.index]*prob*tripsfromthisorigin*this.congestionzonetripdistancesbycarmode[0][Orig][Dest];
					onelemarray_tripkmpassingthroughcongestion[1] += modesplit[mode.CARWALK.index]*prob*tripsfromthisorigin*this.congestionzone2tripdistancesbycarmode[0][Orig][Dest];
					tripkmbymode[mode.WALK.index] += modesplit[mode.CARWALK.index]*prob*tripsfromthisorigin*(globals.gridsquare_edgekm);
					tripkmbymode[mode.EV.index] += modesplit[mode.EVWALK.index]*prob*tripsfromthisorigin*(Math.max(cartripdistancesbycarmode[1][Orig][Dest], this.mintripdist)-globals.gridsquare_edgekm);
					onelemarray_tripkmpassingthroughcongestion[0] += modesplit[mode.EVWALK.index]*prob*tripsfromthisorigin*this.congestionzonetripdistancesbycarmode[1][Orig][Dest];
					onelemarray_tripkmpassingthroughcongestion[1] += modesplit[mode.EVWALK.index]*prob*tripsfromthisorigin*this.congestionzone2tripdistancesbycarmode[1][Orig][Dest];
					tripkmbymode[mode.WALK.index] += modesplit[mode.EVWALK.index]*prob*tripsfromthisorigin*(globals.gridsquare_edgekm);
					tripkmbymode[mode.EVMINI.index] += modesplit[mode.EVMINIWALK.index]*prob*tripsfromthisorigin*(Math.max(cartripdistancesbycarmode[2][Orig][Dest], this.mintripdist)-globals.gridsquare_edgekm);
					onelemarray_tripkmpassingthroughcongestion[0] += modesplit[mode.EVMINIWALK.index]*prob*tripsfromthisorigin*this.congestionzonetripdistancesbycarmode[2][Orig][Dest];
					onelemarray_tripkmpassingthroughcongestion[1] += modesplit[mode.EVMINIWALK.index]*prob*tripsfromthisorigin*this.congestionzone2tripdistancesbycarmode[2][Orig][Dest];
					tripkmbymode[mode.WALK.index] += modesplit[mode.EVMINIWALK.index]*prob*tripsfromthisorigin*(globals.gridsquare_edgekm);

					tripkmbyorigin[Orig] += modesplit[mode.CARWALK.index]*prob*tripsfromthisorigin*(Math.max(cartripdistancesbycarmode[0][Orig][Dest], this.mintripdist)-globals.gridsquare_edgekm);
					tripkmbyorigin[Orig] += modesplit[mode.CARWALK.index]*prob*tripsfromthisorigin*(globals.gridsquare_edgekm);
					tripkmbyorigin[Orig] += modesplit[mode.EVWALK.index]*prob*tripsfromthisorigin*(Math.max(cartripdistancesbycarmode[1][Orig][Dest], this.mintripdist)-globals.gridsquare_edgekm);
					tripkmbyorigin[Orig] += modesplit[mode.EVWALK.index]*prob*tripsfromthisorigin*(globals.gridsquare_edgekm);
					tripkmbyorigin[Orig] += modesplit[mode.EVMINIWALK.index]*prob*tripsfromthisorigin*(Math.max(cartripdistancesbycarmode[2][Orig][Dest], this.mintripdist)-globals.gridsquare_edgekm);
					tripkmbyorigin[Orig] += modesplit[mode.EVMINIWALK.index]*prob*tripsfromthisorigin*(globals.gridsquare_edgekm);
				}
				else if(modesplit[mode.CARWALK.index] > 0.01 || modesplit[mode.EVWALK.index] > 0.01 || modesplit[mode.EVMINIWALK.index] >= 0.01) 
					throw new RuntimeException("CARWALK (or EVWALK or EVMINIWALK) has above 1% mode share for a trip that is less than  gridsquare..... should be impossible!");
				else //small biccies... just add it to walk
				{
					tripkmbymode[mode.WALK.index] += modesplit[mode.CARWALK.index]*prob*tripsfromthisorigin*tripdist;
					tripkmbymode[mode.WALK.index] += modesplit[mode.EVWALK.index]*prob*tripsfromthisorigin*tripdist;
					tripkmbymode[mode.WALK.index] += modesplit[mode.EVMINIWALK.index]*prob*tripsfromthisorigin*tripdist;

					tripkmbyorigin[Orig] += modesplit[mode.CARWALK.index]*prob*tripsfromthisorigin*tripdist;
					tripkmbyorigin[Orig] += modesplit[mode.EVWALK.index]*prob*tripsfromthisorigin*tripdist;
					tripkmbyorigin[Orig] += modesplit[mode.EVMINIWALK.index]*prob*tripsfromthisorigin*tripdist;
				}
					
				
			}
		}
		
		
		if(gencostmap != null) 
		{
			for(mode m : mode.getAllModes()) {
				this.writeTravelTimesToFile(gencostmap[m.index], Orig, "gencost_"+m);
				this.writeTravelTimesToFile(modeshare[m.index], Orig, "modeshare_"+m);
			}
			this.writeTravelTimesToFile(ptcomponents[0], Orig, "ptwait");
			this.writeTravelTimesToFile(ptcomponents[1], Orig, "ptaux");
			this.writeTravelTimesToFile(ptcomponents[2], Orig, "ptinv");
			this.writeTravelTimesToFile(ptcomponents[3], Orig, "ptfare");
		}
		
	}

	
	
	private double[][] getModeSplitForTrip(int Orig, int Dest, double[][] cartraveltimes, double[][] cargeneralizedcosts, 
			double[][] carwalkgeneralizedcost, double[][] evgencost, double[][] evwalkgencost, double[][] evsmallgencost,
			double[][] evsmallwalkgencost)
	{
		double[][] result = new double[5][];
		result[0] = new double[mode.NUMMODES]; //mode share by mode
		result[1] = new double[mode.NUMMODES]; //costs by mode
		result[2] = new double[4]; //pt cost components
		result[3] = new double[5]; //leg1 trip distances for the MiscPTmodes
		result[4] = new double[5]; //leg1 trip distances for the MiscPTmodes
		
		int[] origxy = getXYforID(Orig); 
		int[] destxy = getXYforID(Dest); 
		double tripdist = this.getManhattanDist(origxy[0], origxy[1], destxy[0], destxy[1]);
		
		double carcost = Double.MAX_VALUE;
		double carwalkcost = Double.MAX_VALUE;
		double walkcost = -globals.walkbonus+globals.walkcostperkm*getManhattanDist(origxy[0],origxy[1],destxy[0],destxy[1]);
		double bikecost = -globals.bikebonus+globals.bikecostperkm*getManhattanDist(origxy[0],origxy[1],destxy[0],destxy[1]);
		double ptcost = getPTCost(cartraveltimes, origxy[0], origxy[1], destxy[0], destxy[1], result[2]);
		double evcost = Double.MAX_VALUE;
		double evwalkcost = Double.MAX_VALUE;
		double evsmallcost = Double.MAX_VALUE;
		double evsmallwalkcost = Double.MAX_VALUE;
		double carptcost = Double.MAX_VALUE;
		
		double walkptcost = Double.MAX_VALUE;
		double[] costsanddists;
		if(globals.allowtransferstopt && tripdist > globals.gridsquare_edgekm)
		{
			costsanddists = getWalkPTcost(cartraveltimes, globals.valueoftime_dollarsperhour*((globals.miscpttransfercost-globals.ptaux*globals.ptauxweight)/60.0), origxy[0], origxy[1], destxy[0], destxy[1]);
			walkptcost = costsanddists[0];
			result[3][0] = costsanddists[1];
			result[4][0] = costsanddists[2];
		}
		
		double bikeptcost = Double.MAX_VALUE;
		if(globals.allowtransferstopt  && tripdist > globals.gridsquare_edgekm)
		{
			costsanddists = getBikePTcost(cartraveltimes, globals.valueoftime_dollarsperhour*((globals.miscpttransfercost-globals.ptaux*globals.ptauxweight)/60.0), origxy[0], origxy[1], destxy[0], destxy[1]);
			bikeptcost = costsanddists[0];
			result[3][1] = costsanddists[1];
			result[4][1] = costsanddists[2];		
		}
		
		double evptcost = Double.MAX_VALUE;
		double evsmallptcost = Double.MAX_VALUE;

		
		if(cargeneralizedcosts != null) 
		{
			carcost = cargeneralizedcosts[Orig][Dest];
			carwalkcost = carwalkgeneralizedcost[Orig][Dest];
			if(globals.allowtransferstopt  && tripdist > globals.gridsquare_edgekm) 
			{
				costsanddists = getMiscPTcost(mode.CAR, cargeneralizedcosts, cartraveltimes, 
					globals.valueoftime_dollarsperhour*((globals.miscpttransfercost-globals.ptaux*globals.ptauxweight)/60.0), 
					origxy[0], origxy[1], destxy[0], destxy[1]);
				carptcost = costsanddists[0];
				result[3][2] = costsanddists[1];
				result[4][2] = costsanddists[2];			
			}
		}
		
		if(evgencost != null) 
		{
			evcost = evgencost[Orig][Dest];
			evwalkcost = evwalkgencost[Orig][Dest];
			if(globals.allowtransferstopt  && tripdist > globals.gridsquare_edgekm)
			{
				costsanddists = getMiscPTcost(mode.EV, evgencost, cartraveltimes,
						globals.valueoftime_dollarsperhour*((globals.miscpttransfercost-globals.ptaux*globals.ptauxweight)/60.0), 
						origxy[0], origxy[1], destxy[0], destxy[1]);
				evptcost = costsanddists[0];
				result[3][3] = costsanddists[1];
				result[4][3] = costsanddists[2];
			}
		}
		
		if(evsmallgencost != null) 
		{
			evsmallcost = evsmallgencost[Orig][Dest];
			evsmallwalkcost = evsmallwalkgencost[Orig][Dest];
			if(globals.allowtransferstopt  && tripdist > globals.gridsquare_edgekm)
			{
				costsanddists = getMiscPTcost(mode.EVMINI, evsmallgencost, cartraveltimes,
						globals.valueoftime_dollarsperhour*((globals.miscpttransfercost-globals.ptaux*globals.ptauxweight)/60.0), 
						origxy[0], origxy[1], destxy[0], destxy[1]);
				evsmallptcost = costsanddists[0];
				result[3][4] = costsanddists[1];
				result[4][4] = costsanddists[2];
			}
		}
		
		//now, subtract mincost from them all to avoid underflow
		double[] costs = result[1];
		costs[mode.CAR.index] = carcost;   //motorized mode
		costs[mode.CARWALK.index] = carwalkcost; //motorized mode
		costs[mode.WALK.index] = walkcost; //WALK mode
		costs[mode.BIKE.index] = bikecost; //BIKE mode
		costs[mode.PT.index] = ptcost; //PT mode
		costs[mode.EV.index] = evcost; //motorized mode
		costs[mode.EVWALK.index] = evwalkcost; //motorized mode
		costs[mode.EVMINI.index] = evsmallcost; //motorized mode
		costs[mode.EVMINIWALK.index] = evsmallwalkcost; //motorized mode
		costs[mode.CARPT.index] = carptcost; //MOTORPT mode
		costs[mode.WALKPT.index] = walkptcost; //WALKPT mode
		costs[mode.BIKEPT.index] = bikeptcost; //BIKEPT mode
		costs[mode.EVPT.index] = evptcost; //MOTORPT mode
		costs[mode.EVMINIPT.index] = evsmallptcost; //MOTORPT mode
		
		
		//now, we need to make some adjustments because if carwalk and car cost are very similar,
		//we are introducing IIA-style bias in the mode choice. So we need to estimate mode splits
		//on the 'main' modes first and then split that up amongst the alternatives as necessary
		//
		//We consider WALKPT and BIKEPT as separate though

		//motorized mode
		costs[mode.CAR.index] = Util.getMin(new double[] { costs[mode.CAR.index], costs[mode.EV.index], costs[mode.EVMINI.index],
                costs[mode.CARWALK.index], costs[mode.EVWALK.index], costs[mode.EVMINIWALK.index]});
		//MOTORPT mode
		costs[mode.CARPT.index] = Util.getMin(new double[] {costs[mode.CARPT.index], costs[mode.EVPT.index], costs[mode.EVMINIPT.index]});
		
		//zero out the duplicates in the two above modes
		costs[mode.CARWALK.index] = costs[mode.EV.index] = costs[mode.EVMINI.index] = costs[mode.EVWALK.index] = costs[mode.EVMINIWALK.index] = Double.MAX_VALUE;
		costs[mode.EVPT.index] = costs[mode.EVMINIPT.index] = Double.MAX_VALUE;
		
		//other modes are all kosher, we dont need to join
		
		double mincost = Util.getMin(costs);
		
		for(mode m : mode.getAllModes())
			result[0][m.index] = Math.exp(-(costs[m.index]-mincost)*globals.dollartoutilityconversionfactor);
			//result[0][m.index] = Math.exp(-(costs[m.index])*globals.dollartoutilityconversionfactor);
		
		//make sure mode share sums to 1.0
		Util.normalize(result[0]);

		//now go and adjust again, splitting MOTORIZED out
		double motorizedshare = result[0][mode.CAR.index];
		double[] splitwithinthat = new double[] { carcost, carwalkcost < carcost ? carwalkcost: Double.MAX_VALUE, 
				                                  evcost, evwalkcost < evcost ? evwalkcost: Double.MAX_VALUE, 
				                                  evsmallcost, evsmallwalkcost < evsmallcost ? evsmallwalkcost: Double.MAX_VALUE};
		for(int i = 0; i < splitwithinthat.length; i++) splitwithinthat[i] = Math.exp(-splitwithinthat[i]*globals.dollartoutilityconversionfactor); 
		Util.normalize(splitwithinthat);
		result[0][mode.CAR.index] = motorizedshare*splitwithinthat[0];
		result[0][mode.CARWALK.index] = motorizedshare*splitwithinthat[1];
		result[0][mode.EV.index] = motorizedshare*splitwithinthat[2];
		result[0][mode.EVWALK.index] = motorizedshare*splitwithinthat[3];
		result[0][mode.EVMINI.index] = motorizedshare*splitwithinthat[4];
		result[0][mode.EVMINIWALK.index] = motorizedshare*splitwithinthat[5];
		
		//adjust again, splitting MOTORPT out
		double motorptshare = result[0][mode.CARPT.index];
		splitwithinthat = new double[] { carptcost, evptcost, evsmallptcost};
		for(int i = 0; i < splitwithinthat.length; i++) splitwithinthat[i] = Math.exp(-splitwithinthat[i]*globals.dollartoutilityconversionfactor); 
		Util.normalize(splitwithinthat);
		result[0][mode.CARPT.index] = motorptshare*splitwithinthat[0];
		result[0][mode.EVPT.index] = motorptshare*splitwithinthat[1];
		result[0][mode.EVMINIPT.index] = motorptshare*splitwithinthat[2];
		
		
		if(Math.abs(Util.getSum(result[0])-1.0) > 0.001)
			throw new RuntimeException("Probabilties are not normalized -- have got out of whack somehow");
		
		return result;
		
	}

	

	
	//run a simulation
	//
	//returns the matrix required to adjust all trips so that 
	//the trips to each destination (per activity) is equal for all squares
	public void runSimulation(double[] destnormalizefact, boolean printstuff) throws java.io.IOException
	{
		Debug.println("DELAY COSTS PER KM ARE "+this.congestioncost2_perkm, Debug.IMPORTANT);
		
		//need to zero out all these 
		cartripdistancesbycarmode = new double[3][][];
		congestionzonetripdistancesbycarmode = new double[3][][];
		congestionzone2tripdistancesbycarmode = new double[3][][];
		odtripcounts = new double[globals.citydiameter*globals.citydiameter][globals.citydiameter*globals.citydiameter];

		
		
		//now, work out the travel time (by car) from every square to every other square
		//this requires in the order of ~100MB of storage
		Debug.println("Getting car travel times", Debug.INFO);
		double[][] cartraveltimes = getCarTravelTimes();

		//next, work out the generalized cost by car (this includes travel time,
		//but also other costs such as petrol and the like
		//this is another ~100MB
		Debug.println("Getting car generalized costs", Debug.INFO);
		double[][] cargeneralizedcosts = getCarTravelGeneralizedCosts(mode.CAR, false);
		
		//all other generalized costs (barring drive-walk), 
		//and all other calculations, including mode choice,
		//can be derived from these.
		Debug.println("Getting carwalk generalized costs", Debug.INFO);
		double[][] carwalkgeneralizedcost = getCarWalkGeneralizedCosts(cargeneralizedcosts);

		double[][] evgencost = getCarTravelGeneralizedCosts(mode.EV, true);
		double[][] evwalkgencost = null; 
		if(globals.evonlyavailable > 0 || globals.evsmallevavailable > 0) {
			evgencost = getCarTravelGeneralizedCosts(mode.EV, false);
			evwalkgencost = getCarWalkGeneralizedCosts(evgencost);
		}
		

		double[][] evsmallgencost = getCarTravelGeneralizedCosts(mode.EVMINI, true);;
		double[][] evsmallwalkgencost = null;
		if(globals.evsmallevavailable > 0 || globals.smallevonlyavailable > 0) {
			evsmallgencost = getCarTravelGeneralizedCosts(mode.EVMINI, false);
			evsmallwalkgencost = getCarWalkGeneralizedCosts(evsmallgencost);
		}
		
		//ok, so next what we need to do is work out the trip matrix from A to B.
		Debug.println("Getting trip counts", Debug.INFO);
		
		//need to do this for 6 segments
		double[][] tripprobs;

		java.util.List<Integer> toprint = new java.util.ArrayList<Integer>();
		if(printstuff)
		{
			for(int i = 0; i < 5; i++) {
				toprint.add((int) (Math.random()*globals.citydiameter*globals.citydiameter));
				this.writeTravelTimesToFile(cartraveltimes, toprint.get(toprint.size()-1), "cartraveltime");
			}
		}
		
		
		double[] tripkmbyorigin = new double[globals.citydiameter*globals.citydiameter];
		double[] tripsbyorigin = new double[globals.citydiameter*globals.citydiameter];		
		double[] tripkmpassingthrough = new double[2]; //km through congestion cordon
		double[][] tripsbymodebyorigin = new double[mode.NUMMODES][globals.citydiameter*globals.citydiameter];
		double[][] tripsbymodebydest = new double[mode.NUMMODES][globals.citydiameter*globals.citydiameter];
		double[] tripsbymode = new double[mode.NUMMODES];
		double[] tripkmbymode = new double[mode.NUMMODES];
		double[][] activitiesreachablebyfixedbudget = new double[3][globals.citydiameter*globals.citydiameter];
		
		
		//TODO double[] travelenergypercapitabyorig
		//double[] dollarsspentontransportpercapitabyorig
		       
		

		double[] tripstodest_peractivity = null;
		
		//captive market
		if(globals.captivetravelshare > 0.0) {
			tripprobs = getTripProbs(destnormalizefact, cartraveltimes, null, null, null, null, null, null, globals.captivetravelshare, activitiesreachablebyfixedbudget);
			tripstodest_peractivity = calcTripStats(globals.captivetravelshare, tripprobs, cartraveltimes, null, null, null, null, null, null, null, tripkmbyorigin, tripsbyorigin, tripkmpassingthrough, tripsbymodebyorigin, tripsbymodebydest, tripsbymode, tripkmbymode);
		}
		
		//leave normal car for the moment because thats where we print out all the trip stats
		
		//ev but not smallev
		if(globals.evonlyavailable > 0.0) {
			tripprobs = getTripProbs(destnormalizefact, cartraveltimes, null, null, evgencost, evwalkgencost, null, null, globals.evonlyavailable, activitiesreachablebyfixedbudget);
			tripstodest_peractivity = calcTripStats(globals.evonlyavailable, tripprobs, cartraveltimes, null, null, evgencost, evwalkgencost, null, null, null, tripkmbyorigin, tripsbyorigin, tripkmpassingthrough, tripsbymodebyorigin, tripsbymodebydest, tripsbymode, tripkmbymode);
		}
		
		//normal car and small ev
		if(globals.carsmallevavailable > 0.0) {
			tripprobs = getTripProbs(destnormalizefact, cartraveltimes, cargeneralizedcosts, carwalkgeneralizedcost, null, null, evsmallgencost, evsmallwalkgencost, globals.carsmallevavailable, activitiesreachablebyfixedbudget);
			tripstodest_peractivity = calcTripStats(globals.carsmallevavailable, tripprobs, cartraveltimes, cargeneralizedcosts, carwalkgeneralizedcost, null, null, evsmallgencost, evsmallwalkgencost, null, tripkmbyorigin, tripsbyorigin, tripkmpassingthrough, tripsbymodebyorigin, tripsbymodebydest, tripsbymode, tripkmbymode);
		}
		
		//ev and small ev
		if(globals.evsmallevavailable > 0.0) {
			tripprobs = getTripProbs(destnormalizefact, cartraveltimes, null, null, evgencost, evwalkgencost, evsmallgencost, evsmallwalkgencost, globals.evsmallevavailable, activitiesreachablebyfixedbudget);
			tripstodest_peractivity = calcTripStats(globals.evsmallevavailable, tripprobs, cartraveltimes, null, null, evgencost, evwalkgencost, evsmallgencost, evsmallwalkgencost, null, tripkmbyorigin, tripsbyorigin, tripkmpassingthrough, tripsbymodebyorigin, tripsbymodebydest, tripsbymode, tripkmbymode);
		}
			
		//small ev only
		if(globals.smallevonlyavailable > 0.0) {
			tripprobs = getTripProbs(destnormalizefact, cartraveltimes, null, null, null, null, evsmallgencost, evsmallwalkgencost, globals.smallevonlyavailable, activitiesreachablebyfixedbudget);
			tripstodest_peractivity = calcTripStats(globals.smallevonlyavailable, tripprobs, cartraveltimes, null, null, null, null, evsmallgencost, evsmallwalkgencost, null, tripkmbyorigin, tripsbyorigin, tripkmpassingthrough, tripsbymodebyorigin, tripsbymodebydest, tripsbymode, tripkmbymode);
		}
		

		//normal car -- DO THIS LAST BECAUSE THIS IS WHERE WE DUMP OUT ALL THE TRIP STATS
		if(globals.caronlyavailable > 0.0) 
		{
			tripprobs = getTripProbs(destnormalizefact, cartraveltimes, cargeneralizedcosts, carwalkgeneralizedcost, null, null, null, null, globals.caronlyavailable, activitiesreachablebyfixedbudget);
			
			//print out trip probs 
			for(Integer i : toprint) 
				this.writeTravelTimesToFile(tripprobs, i, "tripprobs_caravail");
			
			tripstodest_peractivity = calcTripStats(globals.caronlyavailable, tripprobs, cartraveltimes, cargeneralizedcosts, carwalkgeneralizedcost, null, null, null, null, toprint, tripkmbyorigin, tripsbyorigin, tripkmpassingthrough, tripsbymodebyorigin, tripsbymodebydest, tripsbymode, tripkmbymode);
		}
		
		
		
		
		//print out activities reachable
		for(int mins = 15; mins <= 60; mins *= 2)
		{	
			int index = 0; 
			if(mins == 30) index = 1;
			else if(mins == 60) index = 2;
				
			this.writeDataToFile(activitiesreachablebyfixedbudget[index], "reachable"+mins+"mins");
			double avgreachable = 0.0;
			double normfact = 0.0;
			for(int i = 0; i < activitiesreachablebyfixedbudget[0].length; i++)
			{
				double act = (lookup("activities", i)/1000.0);
				if(act > 0.0) {
					avgreachable += activitiesreachablebyfixedbudget[index][i]*act;
					normfact += act;
				}
			}
			avgreachable /= normfact;
			Debug.println("Activities reachable with generalized cost less than "+mins+" minutes of full avg wage: "+avgreachable, Debug.IMPORTANT);			
		}

		
		if(tripstodest_peractivity == null) throw new RuntimeException("Impossible... no travel market segments at all!");
		
		
		//make sure our different counts of trips to each destination matches up
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS)) 
		{
			for(int i = 0; i < globals.citydiameter*globals.citydiameter; i++) {
				double tripsto = 0.0;
				for(int j = 0; j < globals.citydiameter*globals.citydiameter; j++) 
					tripsto += odtripcounts[j][i];
				double tripsto2 = tripstodest_peractivity[i]*lookup("activities", i);
				if(Math.abs(tripsto-tripsto2) > 0.01) 
					throw new RuntimeException("TRIPS TO "+i+" (WITH "+lookup("activities", i)+" ACTIVITIES) IS "+tripsto+" ACCORDING TO OD COUNTS, BUT "+tripsto2+" ACCORDING TO tripstodest_peractivity");
				else Debug.println("DESTCOUNTMATCH AOK"+i, Debug.IMPORTANT);
			}
		}
		
		
		//now modify dest normalization fact to indicate how they would need to be adjusted
		//to give the same trips per activity
		for(int i = 0; i < globals.citydiameter*globals.citydiameter; i++)
		{
			if(lookup("activities", i) <= 0.0001)
				continue;

			double actualtripsperactivity = tripstodest_peractivity[i];
			if(actualtripsperactivity <= 0.0) throw new RuntimeException("No trips at all (per activity) to zone "+i+" (x y  "+getXYforID(i)[0]+" "+getXYforID(i)[1]+") even though it has "+lookup("activities", i)+" activities");
			double targettripsperactivity = globals.tripsgeneratedperactivity; //trips to same as trips generated by, for symmetry
			double adjustfactor = targettripsperactivity/actualtripsperactivity;
			destnormalizefact[i] *= adjustfactor;
		}
		
		
		
		//work out what congestion costs should be
		double trafficvol = tripkmpassingthrough[1]/(this.globals.congestioncordon2_capacity*1000000000.0); 
		double delayminutesperkm = Math.max(0, 0.44*trafficvol-0.13);
		double delaycostperkm = (globals.valueoftime_dollarsperhour/60.0)*delayminutesperkm;
		this.congestioncost2_perkm = delaycostperkm;
		Debug.println("RECALCULATED DELAY COSTS PER KM ARE "+this.congestioncost2_perkm, Debug.IMPORTANT);
		
		
	}



	
	

	public static void main(String[] args) throws Exception
	{
		Debug.setVerbosityLevel(Debug.INFO);
		//Debug.setDebugLevel(Debug.DEBUG_SUSPICIOUS);
		
		Debug.println("Instantiating NTCMain", Debug.INFO);
		NTCMain ntc = new NTCMain(Double.parseDouble(args[0]));
		Debug.println("Done instantiating NTCMain, calculating base travel times", Debug.INFO);
		double[] before;
		double[] adjfact = new double[ntc.globals.citydiameter*ntc.globals.citydiameter];
		for(int i = 0; i < adjfact.length; i++) adjfact[i] = 1.0;
		
		//need to run a few times to make sure that destinations all receive their fair share of trips
		
		before = Util.copy(adjfact); ntc.runSimulation(adjfact, false); double normerr = calcNormError(before, adjfact);
		Debug.println("INITIAL ERROR IS "+normerr, Debug.IMPORTANT);
		
		while(normerr > 0.01) 
		{
			double[] newadjfact = ntc.guessOptimalAdjustFactors(); //ho much we need to adjust by
			for(int i = 0; i < adjfact.length; i++)
				adjfact[i] = before[i]*newadjfact[i];
			before = Util.copy(adjfact); ntc.runSimulation(adjfact, false); normerr = calcNormError(before, adjfact);
			Debug.println("ERROR IS "+normerr, Debug.IMPORTANT);
		}

		//ok do final run with the optimal weights
		before = Util.copy(adjfact); ntc.runSimulation(adjfact, true); Debug.println("FINAL DESTCOUNT ERROR IS "+calcNormError(before, adjfact), Debug.IMPORTANT);

	}
	
	
	/** We can only 'guess' the best adjustment factors because there are multuple trip segments (i.e. captive, non-capitive)
	 * 
	 *  So we cannot simply assume that the probability of a trip from A->B is proportional to the O/D trip counts from A->B
	 *  In fact, this is the 'average' probability across all trip segments. Now, this causes problems because of cases like
	 *  the following:
	 *  
	 *  	suppose that from A, in the captive segment (20% of market) 80% of trips are to B and 20% of trips are to C
	 *                           and in the non-captive segment (80% of market) 10% of trips are to B and 90% of trips are to C
	 *                           
	 *      So overall, proportion of trips to B is 0.2*0.8+0.8*0.1 = 0.24
	 *      
	 *      Now suppose further that there are NO other trips to B from any other origins, and that we need to boost trips to B by double.
	 *      Assume we don't care about C.
	 *      
	 *      So, we know that overall trip prob from A->B is 24%, and we can calculate that multiplying this by 2.93 gives us
	 *      a doubling in trips, because multiplying 0.24 by 2.93 and renormalizing gives us a 0.48 trip prob to B. But 
	 *      this only works if there is a single segment. In fact, these numbers are averages and what the code does is the 
	 *      following for each segment:
	 *      
	 *            0.2 segment ---     A->B prob = 0.8*2.93, A->C prob = 0.2
	 *                                renormalized these are 0.921 & 0.079
	 *            0.8 segment ---     A->B prob = 0.2*2.93, A->C prob = 0.8 
	 *                                renormalized these are 0.423 0.577
	 *            
	 *      So the total trip probability from A->B is now    0.2*0.921+0.8*0.423  ~= 0.523
	 *      
	 *      Thus, we have ended up with 52.3% of trips going from A->B when we were really aiming for 48%
	 *      
	 *  So, the numbers in this example are not important -- whats important to note is that you cannot, through a simple
	 *  calculation on total trip probabilities, work out what adjustment factor you need. Best you can do is a guess, unless
	 *  you want to calculate per segment, but we dont want to do this.
	 *  
	 * 
	 * @return
	 */
	private double[] guessOptimalAdjustFactors()
	{
		//ok, work out what the normalization values need to be to reach what we need to
		double toterr = Double.MAX_VALUE;
		double[] adjfact = new double[globals.citydiameter*globals.citydiameter];
		for(int i = 0; i < adjfact.length; i++) adjfact[i] = 1.0; 		
		
		while(true)
		{
			toterr = 0.0;
			double[] adjcounts = new double[adjfact.length]; //this is what we get with current weights in normalize		
			double[] newadjfact = Util.copy(adjfact);
			
			for(int Orig = 0; Orig < adjfact.length; Orig++) 
			{
				//first of all work out what the adjusted trip probs are if we adjust
				//using the current adjustment factors
				double tripsfrom = lookup("activities", Orig)*globals.tripsgeneratedperactivity;
				double[] probs = new double[adjfact.length];
				for(int Dest = 0; Dest < adjfact.length; Dest++) 
					probs[Dest] = adjfact[Dest]*odtripcounts[Orig][Dest];
				Util.normalize(probs);
				
				//work out the adjusted counts
				for(int Dest = 0; Dest < adjfact.length; Dest++)
					adjcounts[Dest] += probs[Dest]*tripsfrom;
			}
			
			//now work out how far we are from where we would like to be, and adjust accordingly
			for(int Dest = 0; Dest < adjfact.length; Dest++)
			{
				//ok, so how far are we from where we should be?
				double target = lookup("activities", Dest)*globals.tripsgeneratedperactivity;
				double err = adjcounts[Dest]-target;
				toterr += (err*err);
			
				//ok, adjust our adjustment factors to correct for this error
				if(adjcounts[Dest] > 0)
					newadjfact[Dest] *= target/adjcounts[Dest];
			}
			Debug.println("Enforcing dest counts: total error is "+toterr, Debug.IMPORTANT);
			
			if(toterr < globals.citydiameter*globals.citydiameter*0.01) //off by 0.1 in each square
				break;
			adjfact = newadjfact;	
		}
		
		return adjfact;
	}
	
		

	private static double calcNormError(double[] a, double[] b)
	{
		double tot = 0.0;
		for(int i = 0; i < a.length; i++) tot += (a[i]-b[i])*(a[i]-b[i]);
		return tot;
	}

}
