package rses.apps.rtran;
import rses.Debug;
import rses.PlatformInfo;
import rses.math.GraphNode;
import rses.math.GraphEdge;
import rses.math.GraphUtil;
import rses.math.MathUtil;
import rses.util.HeapElement;
import rses.util.Util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;



/**
 * 
 * @author peterr
 *
 *
 *
 */
public class Main 
{		
	public static final double SMALL = 0.000001;
	
	private Globals globals;
	private Outputs outputs = new Outputs();
	private Random rand = new Random(270975);
	
	public enum Mode {
	    CAR, WALK, BIKE, PT 
	}
	
	
	
	public Main(String inputfile) throws java.io.IOException
	{
		//next read in all the Globals from the database, and/or barf if any of them are missing
		this.globals = new Globals(inputfile);
				
	}

	
	
	
	
	
	
		
	
	
	
	/** Build the car path tree. Each link is the total cost of travelling that link by car
	 * 
	 * Does NOT include costs incurred at origin and destination (because that doesn't affect the shortest path)
	 * so you have to add them in later (e.g. parking).
	 * 
	 * @return
	 */
	private rses.math.GraphNode[][] getCarPathTrees()
	{
		//first we build the car graph
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		rses.math.GraphNode[][] city = new rses.math.GraphNode[X][Y];
		for(int x = 0; x < X; x++)
			for(int y = 0; y < Y; y++)
				if(globals.carSpeedsbyXY[x][y] > 0) 
					city[x][y] = new rses.math.GraphNode(x+" "+y);
				else
					throw new RuntimeException("Car speeds at "+x+" "+y+" are zero... but at the moment this is not allowed... they can be very small but should never be zero...");
		//now add edges
		double stepsize = globals.gridsquare_edgekm;
		//we dont add spatially specific revenue to the revenue edge because we
		//need to calculate that at the end based on equilibrated traffic volumes,
		//not just the shortest path on a particular run. Even without congestion,
		//we calculate spatially specific revenue at the end based on traffic volume
		double[] defaultdistanceandrevenueedge = new double[] {stepsize, globals.carRevenuePerKM*stepsize};
		
		for(int x = 0; x < X; x++)
			for(int y = 0; y < Y; y++)
			{
				if(city[x][y] == null)
					continue;
				
				double thisspeed = globals.carSpeedsbyXY[x][y];
				double kmcosts = globals.car_dollarsperkm * stepsize;
				if(x > 0 && globals.carSpeedsbyXY[x-1][y] > 0) 
				{
					double timeinhours = 0.5*(stepsize/thisspeed+stepsize/globals.carSpeedsbyXY[x-1][y]);
					double cost = timeinhours*globals.valueoftime_dollarsperhour + kmcosts;
					//add location-specific per-km charges
					double extracharges = globals.carRevenuePerKMbyXY[x][y]*0.5*stepsize +
										  globals.carRevenuePerKMbyXY[x-1][y]*0.5*stepsize;
					cost += extracharges;

					city[x][y].addEdge(city[x-1][y], cost, defaultdistanceandrevenueedge);
				}
				if(x < X-1 && globals.carSpeedsbyXY[x+1][y] > 0) 
				{
					double timeinhours = 0.5*(stepsize/thisspeed+stepsize/globals.carSpeedsbyXY[x+1][y]);
					double cost = timeinhours*globals.valueoftime_dollarsperhour + kmcosts;
					//add location-specific per-km charges
					double extracharges = globals.carRevenuePerKMbyXY[x][y]*0.5*stepsize +
										  globals.carRevenuePerKMbyXY[x+1][y]*0.5*stepsize;
					cost += extracharges;

					city[x][y].addEdge(city[x+1][y], cost, defaultdistanceandrevenueedge);
				}
				if(y > 0 && globals.carSpeedsbyXY[x][y-1] > 0) 
				{
					double timeinhours = 0.5*(stepsize/thisspeed+stepsize/globals.carSpeedsbyXY[x][y-1]);
					double cost = timeinhours*globals.valueoftime_dollarsperhour + kmcosts;
					//add location-specific per-km charges
					double extracharges = globals.carRevenuePerKMbyXY[x][y]*0.5*stepsize +
					  			globals.carRevenuePerKMbyXY[x][y-1]*0.5*stepsize;
					cost += extracharges;

					city[x][y].addEdge(city[x][y-1], cost, defaultdistanceandrevenueedge);
				}
				if(y < Y-1 && globals.carSpeedsbyXY[x][y+1] > 0) 
				{
					double timeinhours = 0.5*(stepsize/thisspeed+stepsize/globals.carSpeedsbyXY[x][y+1]);
					double cost = timeinhours*globals.valueoftime_dollarsperhour + kmcosts;
					//add location-specific per-km charges
					double extracharges = globals.carRevenuePerKMbyXY[x][y]*0.5*stepsize +
		  			globals.carRevenuePerKMbyXY[x][y+1]*0.5*stepsize;
					cost += extracharges;

					city[x][y].addEdge(city[x][y+1], cost, defaultdistanceandrevenueedge);
				}
			}
		
		
		//ok, we've built the graph, so let's get the shortest path tree from each origin
		rses.math.GraphNode[][] pathtrees = new rses.math.GraphNode[X][Y];
		java.util.Map<String, GraphNode> graph = rses.math.GraphUtil.getGraph(city[0][0]);
		for(int x = 0; x < X; x++)
			for(int y = 0; y < Y; y++) 
				if(city[x][y] != null)
					pathtrees[x][y] = rses.math.GraphUtil.getShortestPathTree(city[x][y].getId(), graph);
			

		return pathtrees;
		
	}
	
	




	private double[][][][] getCarTravelGeneralizedCosts(GraphNode[][] carPathTrees)
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		
		double[][][][] result = new double[X][Y][][];
				
		//now get shortest path (by generalized cost) for each origin
		for(int x = 0; x < X; x++)
			for(int y = 0; y < Y; y++)
			{
				result[x][y] = new double[X][Y];
				Util.initializeArray(result[x][y], Double.POSITIVE_INFINITY); //
				
				GraphNode shortestpathtree = carPathTrees[x][y];
				if(shortestpathtree == null) {
					Debug.println(x+","+y+" has no path tree by car. Assuming infinite costs", Debug.INFO);
					continue;
				}

				//now traverse the tree and get the shortest path to every other node
				
				Debug.println("Got shortest path tree from "+x+" , "+y, Debug.EXTRA_INFO);
				//rses.math.GraphUtil.printGraph(shortestpathtree, System.out);
				
				//get the path cost to each reachable destination 
				Map<String, Double> costs = GraphUtil.getPathLengthsFromPathTree(shortestpathtree);
				
				//we need to do some final adjustment, because we have per-trip costs to add
				for(String dest : costs.keySet())
				{
					double cost = costs.get(dest);
					String[] words = Util.getWords(dest);
					int destx = Integer.parseInt(words[0]);
					int desty = Integer.parseInt(words[1]);
					
					//add per trip costs
					cost += globals.carcosts_pertrip;
					
					//add parking costs at destination
					cost += globals.parkingCostsbyXY_1stleg[destx][desty];

					//special case -- travel within a zone should have non-zero distance.
					//we assume 1/2 a gridsquare
					if(x == destx && y == desty) 
					{
						double dist = 0.5*globals.gridsquare_edgekm;
						cost += globals.valueoftime_dollarsperhour*(dist/globals.carSpeedsbyXY[x][y]);
						cost += dist*globals.car_dollarsperkm;
					}
										
					result[x][y][destx][desty] = cost;
				}
			}
		
		return result;
	}

	
	private double getReturnJourneyGC(Map<Mode, double[][][][]> gencosts, Mode m, int origx, int origy, int destx, int desty)
	{
		double onewaycost = gencosts.get(m)[origx][origy][destx][desty];
		double returngencost = onewaycost+gencosts.get(m)[destx][desty][origx][origy];
			
		//TODO NB: If this is a car mode, we need to adjust this because gencosts are all 
		//worked out using park1 as the parking costs. But we need to use different
		//parking costs at the origin and destination leg of the trip. We can adjust for
		//this here because changing the parking at the destination doesn't change
		//the path taken (because we dont allow park-walk). If we allowed park-walk
		//at each end of the trip then we would have to have separate path trees for
		//1st and return legs.
		if(m == Mode.CAR)
			returngencost += (globals.parkingCostsbyXY_returnleg[origx][origy]-globals.parkingCostsbyXY_1stleg[origx][origy]);
	
		return returngencost;
		
	}
	
	
	
	/** Build the PT path tree. Each link is the total cost of traveling that link by PT
	 * 
	 * Does NOT include costs incurred at origin and destination (because that doesn't affect the shortest path)
	 * so you have to add them in later.
	 * 
	 * @return
	 */
	private rses.math.GraphNode[][][] getPTPathTrees()
	{		
		//first we build the PT graph. This is actually a graph that has both 
		//Bus and Rail and Walk links, but there is a cost for going between different
		//modes		
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;

				
		
		rses.math.GraphNode[][][][] city = 
			new rses.math.GraphNode[Math.max(1, globals.stochasticPTruns)][][][];
		
		
		for(int i = 0; i < Math.max(globals.stochasticPTruns, 1); i++)
			city[i] = createPTGraph();
		
		
		//
		if(Debug.equalOrMoreVerbose(Debug.EXTRA_INFO))
		{
			for(int i = 0; i < Math.max(globals.stochasticPTruns, 1); i++) 
			{
				Debug.println("Printing PT graph for PT segment run "+i+" (NOT shortest path tree, the whole graph) reachable from Walk 0 0 ", Debug.EXTRA_INFO);
				GraphUtil.printGraph(city[i][0][0][0], System.out);
			}
		}
		
		//ok, we've built the graph, so let's get the shortest path tree from each origin
		rses.math.GraphNode[][][] pathtrees = new rses.math.GraphNode[city.length][X][Y];
		//get a path tree for each run
		for(int i = 0; i < city.length; i++)
		{
			//get the graph from [0][0][WALK]
			java.util.Map<String, GraphNode> graph = rses.math.GraphUtil.getGraph(city[i][0][0][0]);
			for(int x = 0; x < X; x++)
				for(int y = 0; y < Y; y++)
				{
					pathtrees[i][x][y] = rses.math.GraphUtil.getShortestPathTree("WALK "+x+" "+y, graph);
					if(Debug.equalOrMoreVerbose(Debug.EXTRA_INFO)) 
					{
						Debug.println("Printing PT path tree for run "+i+" from Walk "+x+","+y, Debug.EXTRA_INFO);
						GraphUtil.printGraph(pathtrees[i][x][y], System.out);
					}
				}
		}
		
		
		//TODO finally, for efficiency purposes, we might like to prune the shortest path tree 
		//to get rid of all the redundant nodes, because we dont care about the shortest path 
		//to nodes other than DEST nodes. Pruning all those might be a good idea here.
		//TODO later :-)
		
		return pathtrees;
		
	}
	
	
	private GraphNode[][][] createPTGraph()
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;

		//we have these graphs because:
		//1 is the walk graph, 1 is the train graph, and there are 1 or more bus graphs, and
		//we also need a *special* walkAfterPT graph to ensure that PT is taken
		//at least once, and a special DEST square where we are at our destination
		//We need this DEST one in particular because our destination may involve some
		//final auxiliary walk time, whereas walkafter is assumed to be at a bus/rail stop
		final int WALK = 0; //Walk MUST always be zero 
		final int WALKAFTER = 1;
		final int DEST = 2;
		final int BUSMIN = 3; //there can be many bus services
		final int NUMBUSROUTES = globals.BusWaitTimes.size();
		final int BUSMAX = BUSMIN+NUMBUSROUTES-1;
		final int TRAINMIN = BUSMAX+1;
		final int NUMTRAINROUTES = globals.RailWaitTimes.size();
		final int TRAINMAX = TRAINMIN+NUMTRAINROUTES-1;
		String[] trainids = globals.RailWaitTimes.keySet().toArray(new String[0]);
		String[] busids = globals.BusWaitTimes.keySet().toArray(new String[0]);

		GraphNode[][][] city = new GraphNode[X][Y][TRAINMAX+1];
		
		for(int x = 0; x < city.length; x++)
		{
			for(int y = 0; y < city[0].length; y++)
			{
				city[x][y][WALK] = new rses.math.GraphNode("WALK "+x+" "+y);
				for(int tr = 0; tr < NUMTRAINROUTES; tr++)
					city[x][y][TRAINMIN+tr] = new rses.math.GraphNode("TRAIN_"+trainids[tr]+" "+x+" "+y);
				city[x][y][WALKAFTER] = new rses.math.GraphNode("WALKAFTER "+x+" "+y);
				city[x][y][DEST] = new rses.math.GraphNode("DEST "+x+" "+y);
				for(int b = 0; b < NUMBUSROUTES; b++)
					city[x][y][BUSMIN+b] = new rses.math.GraphNode("BUS_"+busids[b]+" "+x+" "+y);
			}
		}
	

		

		
		
		double stepsize = globals.gridsquare_edgekm;
		double[] zerozero = new double[] {0.0, 0.0};
		double[] zerobus = new double[] {0.0, globals.busboarddollars};
		double[] zerotrain = new double[] {0.0, globals.trainboarddollars};
		double[] squarezero = new double[] {stepsize, 0.0};
		double[] squarebus = new double[] {stepsize, stepsize*globals.busfareperkm};
		double[] squaretrain = new double[] {stepsize, stepsize*globals.trainfareperkm};
		double[] busauxbus = new double[] {globals.busaux_km, globals.busboarddollars };
		double[] railauxrail = new double[] {globals.railaux_km, globals.trainboarddollars};
		
		//now add edges
		for(int x = 0; x < city.length; x++)
		{
			for(int y = 0; y < city[0].length; y++)
			{
				//first we do all the modes that actually take you to
				//a neighbouring square
				for(int d = 0; d < 4; d++)
				{
					int xoff = 0;
					int yoff = 0;
					if(d == 0) xoff = -1;
					else if(d == 1) xoff = 1;
					else if(d == 2) yoff = -1;
					else if(d == 3) yoff = 1;

					if(x+xoff < 0 || x+xoff >= X || y+yoff < 0 || y+yoff >= Y)
						continue;

					//walk->walk
					double cost = stepsize*0.5*(globals.walkCostPerKMbyXY[x][y]+globals.walkCostPerKMbyXY[x+xoff][y+yoff]);
					city[x][y][WALK].addEdge(city[x+xoff][y+yoff][WALK], cost, squarezero);
					//walkafter->walkafter
					city[x][y][WALKAFTER].addEdge(city[x+xoff][y+yoff][WALKAFTER], cost, squarezero);

					//bus->bus (staying on the SAME bus route)
					for(int b = 0; b < NUMBUSROUTES; b++)
					{
						if(globals.BusWaitTimes.get(busids[b])[x][y] < 1000 && globals.BusWaitTimes.get(busids[b])[x+xoff][y+yoff] < 1000)
						{
							cost = stepsize*globals.busfareperkm; //fare
							double timetaken = stepsize*0.5/globals.carSpeedsbyXY[x][y]+stepsize*0.5/globals.carSpeedsbyXY[x+xoff][y+yoff];
							cost +=  timetaken*globals.valueoftime_dollarsperhour*globals.businvweight; //in-vehicle
							cost += globals.busdelaypersquare_hrs*globals.valueoftime_dollarsperhour; //include time to stop at bus stops
							city[x][y][BUSMIN+b].addEdge(city[x+xoff][y+yoff][BUSMIN+b], cost, squarebus);
						}
					}

					//train->train (staying on the same route)
					for(int tr = 0; tr < NUMTRAINROUTES; tr++)
					{
						if(globals.RailWaitTimes.get(trainids[tr])[x][y] < 1000 && globals.RailWaitTimes.get(trainids[tr])[x+xoff][y+yoff] < 1000)
						{
							cost = stepsize*globals.trainfareperkm; //fare
							double timetaken = stepsize*0.5/globals.TrainSpeeds.get(trainids[tr])[x][y]+stepsize*0.5/globals.TrainSpeeds.get(trainids[tr])[x+xoff][y+yoff];
							cost +=  timetaken*globals.valueoftime_dollarsperhour*globals.railinvweight; //in-vehicle
							cost += globals.traindelaypersquare_hrs*globals.valueoftime_dollarsperhour; //include time to stop at train stations
							city[x][y][TRAINMIN+tr].addEdge(city[x+xoff][y+yoff][TRAINMIN+tr], cost, squaretrain);
						}
					}
				}
				
				//now we do all the 'change modes' edges. Need to make sure here that
				//you can't get infinite zero cost change loops. 

				//walk->bus   
				for(int b = 0; b < NUMBUSROUTES; b++)
				{
					if(globals.BusWaitTimes.get(busids[b])[x][y] >= 1000)
						continue;
					
					double wait = (globals.BusWaitTimes.get(busids[b])[x][y]/60.0)*globals.valueoftime_dollarsperhour*globals.PTwaitWeights[x][y];
					if(globals.stochasticPTruns != 0) // stochastic
						wait *= rand.nextDouble()*2;
					double cost = globals.busaux_km*globals.walkCostPerKMbyXY[x][y] + //aux
						globals.busboarddollars + //fare
						wait; //wait
					city[x][y][WALK].addEdge(city[x][y][BUSMIN+b], cost, busauxbus);
				}

				//walkafter->bus
				for(int b = 0; b < NUMBUSROUTES; b++)
				{
					if(globals.BusWaitTimes.get(busids[b])[x][y] >= 1000)
						continue;
					//NB: We do NOT pay aux time if we go from walkafter to bus, because we paid 
					//aux time when we LEFT the bus (bus-->walkafter), and that covers all aux time
					//otherwise if you went to (x,y) by bus, walked to (x+1, y), and then boarded
					//a bus there, you would pay the aux penalty twice
					double wait =  (globals.BusWaitTimes.get(busids[b])[x][y]/60.0)*globals.valueoftime_dollarsperhour*globals.PTwaitWeights[x][y]; 
					if(globals.stochasticPTruns != 0) // stochastic
						wait *= rand.nextDouble()*2;
					double cost = globals.extraTransferPenalty + wait;
					if(!globals.integratedFares) {
						cost += globals.busboarddollars;
						city[x][y][WALKAFTER].addEdge(city[x][y][BUSMIN+b], cost, zerobus);
					}
					else
						city[x][y][WALKAFTER].addEdge(city[x][y][BUSMIN+b], cost, zerozero);
					
				}
				
				//walk->train
				for(int tr = 0; tr < NUMTRAINROUTES; tr++)
				{
					if(globals.RailWaitTimes.get(trainids[tr])[x][y] < 1000)
					{
						double wait = (globals.RailWaitTimes.get(trainids[tr])[x][y]/60.0)*globals.valueoftime_dollarsperhour*globals.PTwaitWeights[x][y]; //wait
						if(globals.stochasticPTruns != 0) // stochastic
							wait *= rand.nextDouble()*2;
						double cost = globals.railaux_km*globals.walkCostPerKMbyXY[x][y] + //aux
							globals.trainboarddollars +  //fare
							wait;

						city[x][y][WALK].addEdge(city[x][y][TRAINMIN+tr], cost, railauxrail);
					}
				}
				
				//walkafter->train
				for(int tr = 0; tr < NUMTRAINROUTES; tr++)
				{
					if(globals.RailWaitTimes.get(trainids[tr])[x][y] < 1000)
					{
						//as above in walkafter-bus, we do not pay aux time here, because we are already
						//at the transit stop and have paid the walk time cost to get there
						double wait = (globals.RailWaitTimes.get(trainids[tr])[x][y]/60.0)*globals.valueoftime_dollarsperhour*globals.PTwaitWeights[x][y]; //wait
						if(globals.stochasticPTruns != 0)
							wait *= rand.nextDouble()*2;
						double cost = globals.extraTransferPenalty +  wait; 
						if(!globals.integratedFares) { 
							cost += globals.trainboarddollars;
							city[x][y][WALKAFTER].addEdge(city[x][y][TRAINMIN+tr], cost, zerotrain);
						}
						else
							city[x][y][WALKAFTER].addEdge(city[x][y][TRAINMIN+tr], cost, zerozero);
						
					}
				}
					
				//bus->walkafter
				for(int b = 0; b < NUMBUSROUTES; b++)
				{
					if(globals.BusWaitTimes.get(busids[b])[x][y] >= 1000)
						continue;
					//No aux timehere because we assume that walkafter is at the bus stop,
					//and they need to go to the 'DEST' graph node to get to the destination
					//and they pay the aux time when they do that
					city[x][y][BUSMIN+b].addEdge(city[x][y][WALKAFTER], 0.0, zerozero); 
				}
								
				//train->walkafter
				for(int tr = 0; tr < NUMTRAINROUTES; tr++)
				{
					if(globals.RailWaitTimes.get(trainids[tr])[x][y] > 1000)
						continue;
					//No aux time here because we assume that walkafter is at the train stop,
					//and they need to go to the 'DEST' graph node to get to the destination
					//and they pay the aux time when they do that
					city[x][y][TRAINMIN+tr].addEdge(city[x][y][WALKAFTER], 0.0, zerozero); 
				}
				
				
				
				
				//Walkafter --> Dest
				//
				//TODO the one distortion here is that we dont know what mode of transport they got
				//off (i.e. how they got to walkafter), so we dont know what the 'right' auxiliary 
				//time to add is to get to destination. We just assume bus for now
				double cost = globals.walkCostPerKMbyXY[x][y]*globals.busaux_km + globals.perTripPTpenalty;
				city[x][y][WALKAFTER].addEdge(city[x][y][DEST], cost, new double[] {globals.busaux_km, 0.0});

			} //end for y
		} //end for x

		
		return city;
	}
	



	private double[][][][] getPTGeneralizedCosts(GraphNode[][] PTpathTrees)
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		
		double[][][][] result = new double[X][Y][][];
		
		//now get shortest path (by generalized cost) for each origin
		for(int x = 0; x < X; x++)
			for(int y = 0; y < Y; y++)
			{
				result[x][y] = new double[X][Y];
				Util.initializeArray(result[x][y], Double.POSITIVE_INFINITY); //
				
				GraphNode shortestpathtree = PTpathTrees[x][y];
				if(shortestpathtree == null) {
					Debug.println(x+","+y+" has no path tree by PT. Assuming infinite costs", Debug.INFO);
					continue;
				}

				
				//now traverse the tree and get the shortest path to every other node
				
				Debug.println("Got shortest path tree from "+x+" , "+y, Debug.EXTRA_INFO);
				//rses.math.GraphUtil.printGraph(shortestpathtree, System.out);
				
				//get the path cost to each reachable destination 
				Map<String, Double> costs = GraphUtil.getPathLengthsFromPathTree(shortestpathtree);
				
				//we need to do some final adjustment, because we have per-trip costs to add
				for(String dest : costs.keySet())
				{
					String[] words = Util.getWords(dest);
					String mode = words[0];
					if(!mode.equals("DEST")) //all destination nodes must be Dest. Otherwise we are either still in transit or else never boarded any transit
						continue;
					double cost = costs.get(dest);
					int destx = Integer.parseInt(words[1]);
					int desty = Integer.parseInt(words[2]);
					
					//add per trip costs or costs at destination..
					//but really there are none because we take that all into accont in the path graph already
														
					result[x][y][destx][desty] = cost;
				}
			}
		
		return result;
	}

	
	
	
	private rses.math.GraphNode[][] getWalkPathTrees()
	{
		//first we build the walk graph
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		rses.math.GraphNode[][] city = new rses.math.GraphNode[X][Y];
		for(int x = 0; x < city.length; x++)
			for(int y = 0; y < city[0].length; y++)
				city[x][y] = new rses.math.GraphNode(x+" "+y);
		//now add edges
		double stepsize = globals.gridsquare_edgekm;
		double[] defaultEdgeInfo = new double[] {stepsize, 0.0};
		for(int x = 0; x < city.length; x++)
			for(int y = 0; y < city[0].length; y++)
			{
				if(x > 0) 
				{
					double cost = stepsize*0.5*(globals.walkCostPerKMbyXY[x][y]+globals.walkCostPerKMbyXY[x-1][y]);
					city[x][y].addEdge(city[x-1][y], cost, defaultEdgeInfo);
				}
				if(x < X-1) 
				{
					double cost = stepsize*0.5*(globals.walkCostPerKMbyXY[x][y]+globals.walkCostPerKMbyXY[x+1][y]);
					city[x][y].addEdge(city[x+1][y], cost, defaultEdgeInfo);
				}
				if(y > 0) 
				{
					double cost = stepsize*0.5*(globals.walkCostPerKMbyXY[x][y]+globals.walkCostPerKMbyXY[x][y-1]);
					city[x][y].addEdge(city[x][y-1], cost, defaultEdgeInfo);
				}
				if(y < Y-1) 
				{
					double cost = stepsize*0.5*(globals.walkCostPerKMbyXY[x][y]+globals.walkCostPerKMbyXY[x][y+1]);
					city[x][y].addEdge(city[x][y+1], cost, defaultEdgeInfo);
				}
			}
		
		
		//ok, we've built the graph, so let's get the shortest path tree from each origin
		rses.math.GraphNode[][] pathtrees = new rses.math.GraphNode[X][Y];
		java.util.Map<String, GraphNode> graph = rses.math.GraphUtil.getGraph(city[0][0]);
		for(int x = 0; x < X; x++)
			for(int y = 0; y < Y; y++)
				pathtrees[x][y] = rses.math.GraphUtil.getShortestPathTree(city[x][y].getId(), graph);

		return pathtrees;
		
	}
	
	

	
	
	private double[][][][] getWalkGeneralizedCosts(GraphNode[][] walkPathTrees)
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		
		double[][][][] result = new double[X][Y][][];
				
		//now get shortest path (by generalized cost) for each origin
		for(int x = 0; x < X; x++)
			for(int y = 0; y < Y; y++)
			{
				result[x][y] = new double[X][Y];
				Util.initializeArray(result[x][y], Double.POSITIVE_INFINITY); //
				
				GraphNode shortestpathtree = walkPathTrees[x][y];
				if(shortestpathtree == null) {
					Debug.println(x+","+y+" has no path tree by walk. Assuming infinite costs", Debug.INFO);
					continue;
				}

				//now traverse the tree and get the shortest path to every other node
				
				Debug.println("Got shortest path tree from "+x+" , "+y, Debug.EXTRA_INFO);
				//rses.math.GraphUtil.printGraph(shortestpathtree, System.out);
				
				//get the path cost to each reachable destination 
				Map<String, Double> costs = GraphUtil.getPathLengthsFromPathTree(shortestpathtree);
				
				//we need to do some final adjustment, because we have per-trip costs to add
				for(String dest : costs.keySet())
				{
					double cost = costs.get(dest);
					String[] words = Util.getWords(dest);
					int destx = Integer.parseInt(words[0]);
					int desty = Integer.parseInt(words[1]);
					
					//add per trip costs
					cost -= globals.walkbonus;
					
					//special case -- travel within a zone should have non-zero distance.
					//we assume 1/2 a gridsquare
					if(x == destx && y == desty) 
						cost += 0.5*globals.gridsquare_edgekm*globals.walkCostPerKMbyXY[x][y];
										
					result[x][y][destx][desty] = cost;
				}
			}
		
		return result;
	}


	
	
	
	
	private rses.math.GraphNode[][] getBikePathTrees()
	{
		//first we build the walk graph
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		rses.math.GraphNode[][] city = new rses.math.GraphNode[X][Y];
		for(int x = 0; x < city.length; x++)
			for(int y = 0; y < city[0].length; y++)
				city[x][y] = new rses.math.GraphNode(x+" "+y);
		//now add edges
		double stepsize = globals.gridsquare_edgekm;
		double[] defaultEdgeInfo = new double[] {stepsize, 0.0};
		for(int x = 0; x < city.length; x++)
			for(int y = 0; y < city[0].length; y++)
			{
				if(x > 0) 
				{
					double cost = stepsize*0.5*(globals.bikeCostPerKMbyXY[x][y]+globals.bikeCostPerKMbyXY[x-1][y]);
					city[x][y].addEdge(city[x-1][y], cost, defaultEdgeInfo);
				}
				if(x < X-1) 
				{
					double cost = stepsize*0.5*(globals.bikeCostPerKMbyXY[x][y]+globals.bikeCostPerKMbyXY[x+1][y]);
					city[x][y].addEdge(city[x+1][y], cost, defaultEdgeInfo);
				}
				if(y > 0) 
				{
					double cost = stepsize*0.5*(globals.bikeCostPerKMbyXY[x][y]+globals.bikeCostPerKMbyXY[x][y-1]);
					city[x][y].addEdge(city[x][y-1], cost, defaultEdgeInfo);
				}
				if(y < Y-1) 
				{
					double cost = stepsize*0.5*(globals.bikeCostPerKMbyXY[x][y]+globals.bikeCostPerKMbyXY[x][y+1]);
					city[x][y].addEdge(city[x][y+1], cost, defaultEdgeInfo);
				}
			}
		
		
		//ok, we've built the graph, so let's get the shortest path tree from each origin
		rses.math.GraphNode[][] pathtrees = new rses.math.GraphNode[X][Y];
		java.util.Map<String, GraphNode> graph = rses.math.GraphUtil.getGraph(city[0][0]);
		for(int x = 0; x < X; x++)
			for(int y = 0; y < Y; y++)
				pathtrees[x][y] = rses.math.GraphUtil.getShortestPathTree(city[x][y].getId(), graph);

		return pathtrees;
		
	}
	
	

	
	
	private double[][][][] getBikeGeneralizedCosts(GraphNode[][] bikePathTrees)
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		
		double[][][][] result = new double[X][Y][][];
				
		//now get shortest path (by generalized cost) for each origin
		for(int x = 0; x < X; x++)
			for(int y = 0; y < Y; y++)
			{
				result[x][y] = new double[X][Y];
				Util.initializeArray(result[x][y], Double.POSITIVE_INFINITY); //
				
				GraphNode shortestpathtree = bikePathTrees[x][y];
				if(shortestpathtree == null) {
					Debug.println(x+","+y+" has no path tree by bike. Assuming infinite costs", Debug.INFO);
					continue;
				}

				
				//now traverse the tree and get the shortest path to every other node
				
				Debug.println("Got shortest path tree from "+x+" , "+y, Debug.EXTRA_INFO);
				//rses.math.GraphUtil.printGraph(shortestpathtree, System.out);
				
				//get the path cost to each reachable destination 
				Map<String, Double> costs = GraphUtil.getPathLengthsFromPathTree(shortestpathtree);
				
				//we need to do some final adjustment, because we have per-trip costs to add
				for(String dest : costs.keySet())
				{
					double cost = costs.get(dest);
					String[] words = Util.getWords(dest);
					int destx = Integer.parseInt(words[0]);
					int desty = Integer.parseInt(words[1]);
					
					//add per trip costs
					cost -= globals.bikebonus;
					
					//special case -- travel within a zone should have non-zero distance.
					//we assume 1/2 a gridsquare
					if(x == destx && y == desty) 
						cost += 0.5*globals.gridsquare_edgekm*globals.bikeCostPerKMbyXY[x][y];
										
					result[x][y][destx][desty] = cost;
				}
			}
		
		return result;
	}

	
	
	
	
	
	
	
	public double getWalkDist(int x, int y, int x2, int y2)
	{
		return globals.gridsquare_edgekm*(Math.abs(x-x2)+Math.abs(y-y2));
	}

	
	private void makeGISMembershipFile()
	{
		Debug.println("Generating GIS membership file", Debug.INFO);

		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		
		String sep = PlatformInfo.sep;
		String basedir = "tinygis"+sep+"gis"+sep+"simcity";
		if(!new java.io.File(basedir).exists()) 
			Globals.err("Could not find base directory "+basedir+" for storing GIS membership file. Aborting");
		
		//now create a membership file in that directory
		double latdiststep_km = rses.math.MathUtil.getDistanceBetweenPointsOnEarth(-35, 150, -35.01, 150)/1000.0;
		double londiststep_km = rses.math.MathUtil.getDistanceBetweenPointsOnEarth(-35, 150, -35, 150.01)/1000.0;
		double latstep = (globals.gridsquare_edgekm/latdiststep_km)*0.01;
		double lonstep = (globals.gridsquare_edgekm/londiststep_km)*0.01;
		
		int[][] mbr0 = new int[Y][X];
		String[] catnames = new String[X*Y];
		for(int y = 0; y < Y; y++)
			for(int x = 0; x < X; x++) {
				mbr0[y][x] = y*X+x;
				catnames[mbr0[y][x]] = "gridsquare_"+(x+1)+"_"+(y+1);
			}
		//expand it larger so that we get a decent size map to display
		int expfact = 800/Math.max(X, Y);
		int[][] mbr = new int[Y*expfact][X*expfact];
		for(int y = 0; y < Y*expfact; y++)
			for(int x = 0; x < X*expfact; x++) 
				mbr[y][x] = (y/expfact)*X+x/expfact;		
				
		rses.spatial.GISLayer membership = 	new rses.spatial.GISLayer(-35, 150, latstep*Y, lonstep*X, "SimCity", mbr, catnames);
		try {
			membership.saveToFile(basedir+sep+"membership.gis");
		}
		catch(Exception e) {
			Globals.warning("Could not create membership/GIS file. This means you wont be able to look at results afterwards with tinygis. Specific error "+e.getMessage());
			Debug.println(e, Debug.IMPORTANT);
		}
	}

	
	private void writeDataToFile(double[][] data, String[] dirs, String filestem) throws java.io.IOException
	{		
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		
		String sep = PlatformInfo.sep;
		String basedir = "tinygis"+sep+"gis"+sep+"simcity";
		if(!new java.io.File(basedir).exists()) 
			Globals.err("Could not find base directory "+basedir+" for storing results files. Aborting");
		
		
		String path = basedir;
		for(int i = 0; i < dirs.length; i++)
		{
			path += "/"+dirs[i];
			if(!new java.io.File(path).exists()) 
			{
				if(!new java.io.File(path).mkdir()) {
					Globals.warning("Could not create directory "+path+" for storing results files, saving in default directory");
					path = basedir;
					break;
				}
			}
		}
		
		path += sep+filestem+".tab";
		
		java.io.PrintWriter f = new java.io.PrintWriter(new java.io.FileWriter(path));
		for(int destx = 0; destx < X; destx++)
			for(int desty = 0; desty < Y; desty++)				
			{
				f.println("gridsquare_"+(destx+1)+"_"+(desty+1)+" "+data[destx][desty]);
			}
		
		f.close();
	}

	
	


	private void printData(double[][][][] data, java.io.PrintStream ps, boolean toprint) throws java.io.IOException
	{		
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		
		for(int x = 0; x < X; x++)
			for(int y = 0; y < Y; y++)
				if(toprint)
				{
					for(int destx = 0; destx < X; destx++)
						for(int desty = 0; desty < Y; desty++)				
							ps.println(x+" "+y+" --> "+destx+" "+desty+" = "+data[x][y][destx][desty]);
				}
		
	}
	
	private void printPathTrees(GraphNode[][] pathtrees)
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;

		for(int x = 0; x < X; x++)
		{
			for(int y = 0; y < Y; y++)
			{
				Debug.println("PRINTING SHORTEST PATH TREE FROM "+x+" "+y, Debug.EXTRA_INFO);
				GraphUtil.printGraph(pathtrees[x][y], System.out);
			}
		}
	}

	
	
	
	private void saveKeyInputs()
	throws java.io.IOException
	{
		this.writeDataToFile(globals.activitiesXY, new String[] {"Inputs"}, "activities");
		this.writeDataToFile(globals.origCarSpeedsbyXY, new String[] {"Inputs"}, "carSpeed");
		this.writeDataToFile(globals.parkingCostsbyXY_1stleg, new String[] {"Inputs"}, "parkingCost_main");
		this.writeDataToFile(globals.parkingCostsbyXY_returnleg, new String[] {"Inputs"}, "parkingCost_return");
		this.writeDataToFile(globals.bikeCostPerKMbyXY, new String[] {"Inputs"}, "bikeCostPerKM");
		this.writeDataToFile(globals.PTwaitWeights, new String[] {"Inputs"}, "PTwaitWeights");
		for(String trainid : globals.RailWaitTimes.keySet())
		{
			this.writeDataToFile(globals.RailWaitTimes.get(trainid), new String[] {"Inputs", "Rail"}, trainid+"_waitTime");
			this.writeDataToFile(globals.TrainSpeeds.get(trainid), new String[] {"Inputs", "Rail"}, trainid+"_speed");
		}
		this.writeDataToFile(globals.tripsbyXY, new String[] {"Inputs"}, "tripsFrom");
		this.writeDataToFile(globals.walkCostPerKMbyXY, new String[] {"Inputs"}, "walkCostsPerKM");
		for(String busid : globals.BusWaitTimes.keySet())
			this.writeDataToFile(globals.BusWaitTimes.get(busid), new String[] {"Inputs", "Bus"}, busid+"_waitTime");
	}
	
	private void saveGenCosts(Map<Mode, double[][][][]> gencosts)
	throws java.io.IOException
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;

		for(Mode m : gencosts.keySet())
		{
			for(int x = 0; x < X; x++)
				for(int y = 0; y < Y; y++)
				{
					//if there are no trips from here we dont save generalized costs from here
					//(partly because they could well be infinite)
					if(globals.tripsbyXY[x][y] < SMALL) continue;
					
					this.writeDataToFile(gencosts.get(m)[x][y], new String[] {"generalizedCosts", ""+m, "x="+(x+1)}, "gencost_"+(x+1)+"_"+(y+1));

					//now get return costs from x,y to all other squares
					double[][] rcosts = Util.copy(gencosts.get(m)[x][y]);
					double[][] ratio = new double[X][Y];
					for(int destx = 0; destx < X; destx++)
						for(int desty = 0; desty < Y; desty++)
						{
							rcosts[destx][desty] = getReturnJourneyGC(gencosts, m, x, y, destx, desty);
							if(m == Mode.CAR) ratio[destx][desty] = getReturnJourneyGC(gencosts, Mode.PT, x, y, destx, desty)/rcosts[destx][desty];
						}

					this.writeDataToFile(rcosts, new String[] {"returnGenCosts", ""+m, "x="+(x+1)}, "gencost_"+(x+1)+"_"+(y+1));
					
					//special case, we write out the car/pt ratio for return costs
					if(m == Mode.CAR) 
						this.writeDataToFile(ratio, new String[] {"returnGenCosts", "Car-to-PT ratio", "x="+(x+1)}, "GCratio_"+(x+1)+"_"+(y+1));					
				}
		}
	}


	
	/**
	 * Work out the probability of travel to each destination, which is a function of the 
	 * generalized cost to each destination
	 * 
	 * We assume the trip is from A to B and back again. In this case
	 * the value of the trip is the destination, and the cost is the total of from and to
	 * legs.
	 * 
	 * @return
	 */
	public double[][][][] getTripProbs(
			double[][] destNorm,
			Map<Mode, double[][][][]> gencosts,
			double[][][] activitiesreachablebyfixedbudget,
			double weight)
	throws java.io.IOException
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		Debug.println("Calculating Trip probabilities", Debug.INFO);
		//probability of going from [a][b] to [a2][b2]
		double[][][][] result = new double[X][Y][][];
		

		for(int x = 0; x < X; x++)
		{
			for(int y = 0; y < Y; y++)
			{
				result[x][y] = new double[X][Y];
				
				//if there are no trips from here we dont bother calculating trip probs
				if(globals.tripsbyXY[x][y] < SMALL) {
					Util.initializeArray(result[x][y], 1.0/(X*Y));
					continue;
				}

					
				double probsum = 0.0;
				
				//work out the cost from this square to every other square
				for(int destx = 0; destx < X; destx++)
				{
					for(int desty = 0; desty < Y; desty++)
					{						
						//if there are no activities we assume no trips to here 
						if(globals.activitiesXY[destx][desty] < SMALL)
							continue;
						
						//get mode split for trip between origin an destination
						Map<Mode, Double> modesplit = getModeSplitForTrip(x, y, destx, desty, gencosts);
						if(gencosts.size() != modesplit.size()) throw new IllegalStateException("Gencost and mode split array are not the same length!!! Should be impossible. Internal Error");
												
						//work out the weighted cost based on that mode split
						double weightedcost = 0.0;
						Mode bestmode = null;
						double bestgc = Double.POSITIVE_INFINITY;
						for(Mode m : modesplit.keySet()) 
						{
							double returngencost = getReturnJourneyGC(gencosts, m, x, y, destx, desty);
							if(returngencost < bestgc) { //remember what the best mode was
								bestgc = returngencost;
								bestmode = m;
							}
							weightedcost += modesplit.get(m)*returngencost;
						}
						
						double activities = globals.activitiesXY[destx][desty];
						
						if(Debug.equalOrMoreVerbose(Debug.EXTRA_INFO)) Debug.println("From "+(x+1)+","+(y+1)+" to "+(destx+1)+","+(desty+1)+" weighted cost is "+weightedcost, Debug.EXTRA_INFO);
						
						if(activities > 0) 
						{
							//TODO maybe we should not use a weighted cost but 
							//do the weighted sum across modes??
							//so, something like:
							//for(Mode m)
							//  result[x][y][destx][desty] += activities*Math.exp(-gcforthatmode/globals.K);
							result[x][y][destx][desty] = activities*Math.exp(-weightedcost/globals.K);

							
							//now, we want to count accessibility by the BEST mode available
							//(because otherwise you can get strange effects when you weight by mode share, 
							//such as overall accessibility decreasing when you increase public transport,
							//because the mode shift to public transport is a accessibility loss (if it is
							//not as fast as car) and this offsets the fact that public transport accessibility
							//has increased). Basically, if someone is taking a 'higher' generalized cost
							//mode, then they are doing that because, for them, the mode is at least as 
							//good as the 'best' mode.
							if(bestgc < globals.valueoftime_dollarsperhour) //1 hour return
								activitiesreachablebyfixedbudget[0][x][y] += activities*weight;
							if(bestgc < globals.valueoftime_dollarsperhour*2) //2 hour return
								activitiesreachablebyfixedbudget[1][x][y] += activities*weight;
							if(bestgc < globals.valueoftime_dollarsperhour*4) //4 hour return
								activitiesreachablebyfixedbudget[2][x][y] += activities*weight;

								if(destNorm != null) result[x][y][destx][desty] *= destNorm[destx][desty];
							if(Debug.equalOrMoreVerbose(Debug.EXTRA_INFO)) Debug.println("From "+(x+1)+","+(y+1)+" to "+(destx+1)+","+(desty+1)+" trip prob is  "+weightedcost, Debug.EXTRA_INFO);
						}
						
						probsum += result[x][y][destx][desty];
					}
				}
				
				
				//now we need to normalize trip probabilities
				if(probsum > SMALL)
					Util.normalize(result[x][y]);
				else
					throw new RuntimeException("There dont appear to be any reachable destinations from "+x+","+y+", even though there are meant to be trips from here....");



				//DONE!!!!				
			}
		}
		Debug.println("Finished Calculating Trip probabilities", Debug.INFO);
		
		return result;
	}
		
	
	




	

	
	
	private Map<Mode, Double> getModeSplitForTrip(int origx, int origy, int destx, int desty, 
			Map<Mode, double[][][][]> gencostsbymode)
	{
		//at the moment this is easy because we assume all modes are independent.
		//but we may want to layer later
		Map<Mode, Double> split = new java.util.EnumMap<Mode, Double>(Mode.class);
		double splitsum = 0.0;
		//have to use the keys in the keyset rather than Mode.values() because sometimes we have
		//modes excluded (for example, if it is a captive travel segment)
		for(Mode m : gencostsbymode.keySet()) 
		{
			double returngc = getReturnJourneyGC(gencostsbymode, m, origx, origy, destx, desty);
			
			double val = Math.exp(-returngc/globals.K);
			split.put(m, val);
			splitsum += val;
		}
		
		//if we cannot travel between x,y by any mode (because costs are too high) then 
		//we can have all modes with zero probability. In that case we should return null
		if(splitsum == 0.0)  
			return null;
		
		//now normalize
		for(Mode m : gencostsbymode.keySet()) 
			split.put(m, split.get(m)/splitsum);
				
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS) && Math.abs(Util.getSum(split.values().toArray(new Double[0]))-1.0) > SMALL)
			throw new RuntimeException("Probabilties are not normalized -- have got out of whack somehow and sum to "+Util.getSum(split.values().toArray(new Double[0])));
		
		return split;
		
	}

	
	/** Calculate the trips to each square, given the specified trip probs and weight.
	 * (note this only looks at the first leg, so only counts those trips, no the return ones).
	 * 
	 * @param tripprobs
	 * @param weight
	 * @return
	 */
	private double[][] getTripsTo(double[][][][] tripprobs, double weight)
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		
		double[][] res = new double[X][Y];
		for(int origx = 0; origx < X; origx++)
			for(int origy = 0; origy < Y; origy++)
			{
				if(Util.getSum(tripprobs[origx][origy]) < SMALL)
					continue;
				
				for(int destx = 0; destx < X; destx++)
					for(int desty = 0; desty < Y; desty++)
						res[destx][desty] += globals.tripsbyXY[origx][origy]*weight*tripprobs[origx][origy][destx][desty];
					
			}
			
		return res;
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
	private double[][] guessOptimalAdjustFactors(double[][][][] tripprobs)
	{
		double totalActivities = Util.getSum(globals.activitiesXY);
		double trips = Util.getSum(globals.tripsbyXY);		
		double tripsToPerActivity = trips/totalActivities;

		//ok, work out what the normalization values need to be to reach what we need to
		double[][] adjfact = new double[globals.carSpeedsbyXY.length][globals.carSpeedsbyXY[0].length];
		
		for(int x = 0; x < adjfact.length; x++) for(int y = 0; y < adjfact[0].length; y++) adjfact[x][y] = 1.0; 		
		
		while(true)
		{
			double[][] adjcounts = new double[adjfact.length][adjfact[0].length]; //this is what we get with current weights in normalize		
			double[][] newadjfact = Util.copy(adjfact);
			
			for(int origx = 0; origx < adjfact.length; origx++)
				for(int origy = 0; origy < adjfact[0].length; origy++)
				{
					//first of all work out what the adjusted trip probs are if we adjust
					//using the current adjustment factors
					double tripsfrom = globals.tripsbyXY[origx][origy];
					double[][] probs = new double[adjfact.length][adjfact[0].length];
					for(int destx = 0; destx < adjfact.length; destx++)
						for(int desty = 0; desty < adjfact[0].length; desty++)
							probs[destx][desty] = adjfact[destx][desty]*tripprobs[origx][origy][destx][desty];
					Util.normalize(probs);

					//work out the adjusted counts
					for(int destx = 0; destx < adjfact.length; destx++)
						for(int desty = 0; desty < adjfact[0].length; desty++)
							adjcounts[destx][desty] += probs[destx][desty]*tripsfrom;
				}
			
			//now work out how far we are from where we would like to be, and adjust accordingly
			double toterr = 0.0;
			double maxerr = 0.0;
			for(int destx = 0; destx < adjfact.length; destx++)
				for(int desty = 0; desty < adjfact[0].length; desty++)			
				{
					//ok, so how far are we from where we should be?
					double target = globals.activitiesXY[destx][desty]*tripsToPerActivity;
					double err = adjcounts[destx][desty]-target;
					toterr += (err*err);
					maxerr = Math.max(Math.abs(err), maxerr);
			
					//ok, adjust our adjustment factors to correct for this error
					if(adjcounts[destx][desty] > 0)
						newadjfact[destx][desty] *= target/adjcounts[destx][desty];
				}
			
			Debug.println("    Enforcing destination counts: total error is "+toterr, Debug.IMPORTANT);
			adjfact = newadjfact;	
			
			if(maxerr < 0.1 && toterr < adjfact.length*adjfact[0].length*0.01) //off by 0.01 in each square
				break;
		}
		
		
		//now, we dont want to just the gun and adjust ALL the way to the optimal, so we only go part way there
		for(int x = 0; x < adjfact.length; x++)
			for(int y = 0; y < adjfact[0].length; y++)
				adjfact[x][y] = 1+0.2*(1-adjfact[x][y]); //just take a 1/2-step there
		
		return adjfact;
	}

	
	
	private double calcMaxErrAndNewNormFact(double[][][][] tripProbs, double[][] newadjfacts, double stepsize)
	{
		double totalActivities = Util.getSum(globals.activitiesXY);
		double trips = Util.getSum(globals.tripsbyXY);		
		double tripsToPerActivity = trips/totalActivities;

		double maxerr = 0.0;
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		for(int destx = 0; destx < X; destx++)
			for(int desty = 0; desty < Y; desty++)
			{
				double tripsTo = 0.0;
				for(int origx = 0; origx < X; origx++)
					for(int origy = 0; origy < Y; origy++)
						tripsTo += tripProbs[origx][origy][destx][desty]*globals.tripsbyXY[origx][origy];

				double target = tripsToPerActivity*globals.activitiesXY[destx][desty];
				double err = tripsTo-target;
				//Debug.println("Target at "+destx+" "+desty+" is "+target+", actual is "+tripsTo, Debug.INFO);
				maxerr = Math.max(maxerr, Math.abs(err));
				
				if(err < SMALL && tripsTo < SMALL)
					newadjfacts[destx][desty] *= 1.0; //leave as is
				else
					newadjfacts[destx][desty] *= 1+stepsize*((target/tripsTo)-1); //adjust a bit
				//Debug.println("adjfact at "+destx+" "+desty+" is "+newadjfacts[destx][desty], Debug.INFO);
				
			}
		
		return maxerr;

	}
	
	
	
	
	public void runSimulation(boolean toprint)
	throws java.io.IOException
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		double[][] normfact = new double[X][Y];
		for(int x = 0; x < X; x++) 
			for(int y = 0; y < Y; y++) normfact[x][y] = 1.0;
		
		//run the simulation once with no normalization
		double[][][][] tripprobs = runSim(false, normfact);
		
		//then run it again if we are fixing trips to destinations to be proportional to activities
		if(globals.forceDestProportionalToActivities)
		{
			//calculate how far we are from matched activities at destination, and
			//get back the adjustment factors we need to move towards a 'fix' 
			double err = calcMaxErrAndNewNormFact(tripprobs, normfact, 0.5);
			Debug.println("O/D matrix matching maximum error is "+err, Debug.IMPORTANT);

			int itcount = 0;
			while(err > 1)
			{
				//run with the current best guess adjustment factors
				tripprobs = runSim(false, normfact); 
				//and guess again
				err = calcMaxErrAndNewNormFact(tripprobs, normfact, 0.5);
				Debug.println("O/D matrix matching maximum error is "+err, Debug.IMPORTANT);

				itcount++;
				if(itcount >= globals.maxODmatchiterations) {
					Debug.println("Reached maximum iteration count (of "+globals.maxODmatchiterations+"), exiting O/D matching loop with error "+err, Debug.IMPORTANT);
					break;
				}
			}
			
			//go again, with the calculated adjustment factors
			Debug.println("Doing final run", Debug.IMPORTANT);
			tripprobs = runSim(toprint, normfact); //do a final run with our best guess normalization factors
			err = calcMaxErrAndNewNormFact(tripprobs, normfact, 0.5);
			Debug.println("FINAL O/D matrix matching maximum error is "+err, Debug.IMPORTANT);
		}
		
		if(toprint)
		{
			Debug.println("Writing outputs to output spreadsheet", Debug.IMPORTANT);
			outputs.write("Output_Template.xlsx");
		}
		
	}
	
	private double[][][][] runSim(boolean toprint, double[][] normfact) 
	throws java.io.IOException
	{
		//each time we start a new sim we re-initialize our random number generator so that
		//we get the same results. If we DONT do this, we cant guarantee that the
		//number of trips per activity to each square will converge, because our generalized
		//costs are changing a fair bit
		this.rand = new Random(270975);

		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;

		//make sure we start with fresh car speeds
		globals.carSpeedsbyXY = Util.copy(globals.origCarSpeedsbyXY);
		
		double[][][][] tripProbs = null; 
		if(!globals.modelCongestion)
		{
			double[][] totalVolume = new double[X][Y];
			tripProbs = runSimSub(toprint, normfact, totalVolume);
			//we need to base car revenue on the traffic volumes
			double[][] carrevenuebyxy = MathUtil.multiplyMatrices(totalVolume, globals.carRevenuePerKMbyXY);
			writeDataToFile(carrevenuebyxy, new String[] {"Revenue"}, "Car (Location Specific)");
			double revenue = globals.gridsquare_edgekm*Util.getSum(carrevenuebyxy);
			outputs.addVal(false, "Revenue from spatially specific car charges", ""+revenue);
		}
		else //get trip probs with congestion
		{			
			tripProbs = MSA(toprint, normfact);
		}
		
		//write out information on congestion in this run
		/*this.writeDataToFile(totalVolume, new String[] {"Traffic (vehicles)"}, "NumberOfCars");
		this.writeDataToFile(totalVolume, new String[] {"Calibration", "CarTrafficVolume", "run_"+runid}, "NumberOfCars");
		this.writeDataToFile(globals.carSpeedsbyXY, new String[] {"Calibration", "Congestion", "run_"+runid}, "CongestedSpeeds");
		this.writeDataToFile(MathUtil.subtractMatrices(globals.origCarSpeedsbyXY, globals.carSpeedsbyXY), new String[] {"Calibration", "Congestion", "run_"+runid}, "CongestionSlowDown");
		this.writeDataToFile(MathUtil.divideMatrices(totalVolume, globals.roadCapacityXY), new String[] {"Calibration", "Congestion", "run_"+runid}, "VolumeCapacity_Ratio");
		*/
		
		//make sure we have normalized the trip probs 
		for(int x = 0; x < X; x++)
			for(int y = 0; y < Y; y++)
				Util.normalize(tripProbs[x][y]);
		
		return tripProbs;		
	}
	
	/**
	 * 
	 * @param toprint
	 * @param normfact
	 * @param totalVolume
	 * @return
	 * @throws java.io.IOException
	 */
	private double[][][][] MSA(boolean toprint, double[][] normfact) 
	throws java.io.IOException
	{
		//Debug.println("In MSA...", Debug.INFO);
		//MathUtil.printMatrix(globals.carSpeedsbyXY, System.out);

		
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		double[][][][] tripProbs0 = null;
		double[][][][] tripProbs1 = null;
		double[][] vol0 = new double[X][Y]; //our current best guess of traffic volume in each square
		
		//first lets just do the thing with no congestion, and get the volume in each square assuming
		//free flow
		tripProbs0 = runSimSub(false, normfact, vol0);
		int n = 0;
		
		while(true)
		{
			if(n == 12) {
				Debug.println("Did not converge in congestion equilibration after 12 iterations. Exiting now", Debug.IMPORTANT);
				break;
			}
			
			n++;
			
			//update speeds to reflect our current guess of the equilibrium traffic volume
			//Debug.println("In MSA pre update...", Debug.INFO);
			//MathUtil.printMatrix(globals.carSpeedsbyXY, System.out);
			globals.carSpeedsbyXY = updateCarSpeeds(vol0);
			//Debug.println("In MSA post update...", Debug.INFO);
			//MathUtil.printMatrix(globals.carSpeedsbyXY, System.out);
			
			//now do the next run to get volumes under congested speeds
			double[][] vol1 = new double[X][Y];
			tripProbs1 = runSimSub(false, normfact, vol1);
 			
			//work out the average of our current guess (vol0) and the new volumes we just worked out (vol1)
			double[][] avg = avg(vol0, vol1, 1.0/(n+1));
			
			//if our trip probs have not changed much, then we stop
			double err = getCongestionError(tripProbs0, tripProbs1, avg, n);
			tripProbs0 = tripProbs1;
			Debug.println("Congestion convergence indicator is "+err, Debug.INFO);
			//we insist on at least 4 iterations before we accept a solution
			//we also insist on an even number of iterations (this tends to work better, as it counteracts
			//the importance of the first run, because off iterations will have a pattern more similart to
			
			if(err < 0.02 && n >= 4 && n % 2 == 0)  
			{
				Debug.println("Convergence reached in inner (congestion) loop "+n+"  Exiting MSA", Debug.INFO);
				vol0 = avg; //use our final average (of the two that didnt differ much) as equilibrium volume
				break;
			}
			
			//otherwise we go again, using the 'averaged' volumes we just worked out
			vol0 = avg;
		}
	
		//set the 'final' car speeds to be based on our (hopefully) converged volumes
		Debug.println("Setting equilibrium congested road speeds", Debug.INFO);
		globals.carSpeedsbyXY = updateCarSpeeds(vol0);

		//and, if we are printing out results, do a final run to output results
		if(toprint) 
		{
			//we need to base car revenue on the converged traffic volumes, not the ones from any
			//individual run (which are unstable)
			double[][] carrevenuebyxy = MathUtil.multiplyMatrices(vol0, globals.carRevenuePerKMbyXY);
			writeDataToFile(carrevenuebyxy, new String[] {"Revenue"}, "Car (Location Specific)");
			double revenue = globals.gridsquare_edgekm*Util.getSum(carrevenuebyxy);
			outputs.addVal(false, "Revenue from spatially specific car charges", ""+revenue);
			
			Debug.println("Doing final congested run to get final trip statistics", Debug.IMPORTANT);
			vol0 = new double[X][Y];
			tripProbs0 = runSimSub(toprint, normfact, vol0);
		}
		
		
		
		return tripProbs0;
	}

	
	private double getCongestionError(double[][][][] tripProbs1, double[][][][] tripProbs2, double[][] vol, int id)
	throws IOException
	{		
		int count = 0;
		double maxdiff = 0.0;
		double avgdiff = 0.0;
		double weightedavg = 0.0;
		double wdenom = 0.0;
		
		//work out the maximum, average, and weighted average change in trip prob
		for(int i = 0; i < tripProbs1.length; i++)
			for(int j = 0; j < tripProbs1[0].length; j++)
			{
				for(int k = 0; k < tripProbs1.length; k++)
					for(int l = 0; l < tripProbs1[0].length; l++)
					{
						if(tripProbs1[i][j][k][l] < SMALL && tripProbs2[i][j][k][l] < SMALL)
							continue;
						//how important is this particular link, in the grand scheme of things?
						//(the more trips along that link, the more important it is)
						double avgtripprob = globals.tripsbyXY[i][j]*(tripProbs1[i][j][k][l]+tripProbs2[i][j][k][l]);
						
						//what is the difference
						double diff = Math.abs(tripProbs1[i][j][k][l]-tripProbs2[i][j][k][l]);
						double diffpct = diff/(0.5*(tripProbs1[i][j][k][l]+tripProbs2[i][j][k][l]));
						avgdiff += diffpct;
						maxdiff = Math.max(diffpct, maxdiff);
						weightedavg += diffpct*avgtripprob; //weight it according to the importance
						wdenom += avgtripprob;
						count++;
					}
			}
		
		//save the current volume guess to file
		writeDataToFile(vol, new String[] {"Congestion"}, "Volume_iter_"+id);
		
		
		Debug.println("Weighted % change in trip probs is "+weightedavg/wdenom, Debug.INFO);
		Debug.println("Average % change in trip probs is "+avgdiff/count, Debug.INFO);
		Debug.println("Max % change in trip probs is "+maxdiff, Debug.INFO);
		return weightedavg/wdenom;
	}

	
	private double getVolError(double[][] vol1, double[][] vol2, int id)
	throws IOException
	{
		double[][] speeds1 = updateCarSpeeds(vol1);
		double[][] speeds2 = updateCarSpeeds(vol2);
		double[][] diffpctm = new double[vol1.length][vol1[0].length];
		double maxdiff = 0.0;
		double avgdiff = 0.0;
		double weighteddiff = 0.0;
		double weighteddiffdenom = 0.0;
		int count = 0;
		
		//work out the maximum change in speed, in percentage terms
		for(int i = 0; i < speeds1.length; i++)
			for(int j = 0; j < speeds1[0].length; j++)
			{
				if(globals.origCarSpeedsbyXY[i][j] == 0.0) continue; //no road here
				
				double s1 = speeds1[i][j];
				double s2 = speeds2[i][j];
				double diff = Math.abs(s1-s2);
				double diffpct = diff/(0.5*s1+0.5*s2);
				diffpctm[i][j] = diffpct;
				weighteddiff += diffpct*(s1+s2);
				weighteddiffdenom += (s1+s2);
				maxdiff = Math.max(maxdiff, diffpct);
				avgdiff += diffpct;
				count++;
			}
			
		//save the % change map to file
		writeDataToFile(diffpctm, new String[] {"Congestion"}, "iter_"+id);
		
		
		Debug.println("Weighted % change in traffic volume is "+weighteddiff/weighteddiffdenom, Debug.INFO);
		Debug.println("Average % change in traffic volume is "+avgdiff/count, Debug.INFO);
		Debug.println("Max % change in traffic volume is "+maxdiff, Debug.INFO);
		return weighteddiff/weighteddiffdenom;
	}
	
	private double[][] avg(double[][] a, double[][] b, double vol2weight)
	{
		if(vol2weight < 0 || vol2weight > 1)
			throw new RuntimeException("Nonsensical argument to avg");
		double[][] res = new double[a.length][a[0].length];
		for(int i = 0; i < a.length; i++)
			for(int j = 0; j < a[0].length; j++)
				res[i][j] = a[i][j]*(1-vol2weight)+b[i][j]*vol2weight;
		return res;
	}
	
	/** 
	 * 
	 * @param trafficVolumeByXY
	 * @return
	 */
	private double[][] updateCarSpeeds(double[][] trafficVolumeByXY)
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;

		double[][] newspeeds = new double[X][Y];
		for(int x = 0; x < X; x++)
			for(int y = 0; y < X; y++)
			{
				//Congested Speed = (Free-Flow Speed)/(1+0.20[volume/capacity]10)
				//MTC 1994 function
				double volume = trafficVolumeByXY[x][y];
				double capacity = globals.roadCapacityXY[x][y];
				//Debug.println(x+","+y+" volume is "+trafficVolumeByXY[x][y]+" capacity is "+capacity, Debug.INFO);
				
				if(capacity == 0.0) throw new RuntimeException("Capacity of zero is not allowed.... This issue will be fixed, but until then, just choose a low capacity, like 0.001");
				
				double speed = Globals.MINCARSPEED;
				if(capacity > 0)
					speed = globals.origCarSpeedsbyXY[x][y]/(1+0.2*Math.pow(volume/capacity, 10));
				if(speed < Globals.MINCARSPEED)
				{
					//Debug.println("Congested speed less than "+Globals.MINCARSPEED+" km/h.... setting to minimum", Debug.INFO);
					speed = Globals.MINCARSPEED;
				}
				//Debug.println("New speed is "+speed, Debug.INFO);
				newspeeds[x][y] = speed;
			}
		
		return newspeeds;
	}

	/** Do the main work of the simulation
	 * 
	 * @param toprint: do we want to print out results to file/
	 * @param normfact: the scale factors to use for destinations
	 * @param volumebyXY: An OUTPUT parameter which gets ADDED to. This tells you what the volume of traffic was on each x,y link
	 * @return The trip probabilities between all origins and destinations
	 * @throws java.io.IOException
	 */
	private double[][][][] runSimSub(boolean toprint, double[][] normfact, double[][] volumebyXY) 
	throws java.io.IOException
	{
		//Debug.println("In runSimSub...", Debug.INFO);
		//MathUtil.printMatrix(globals.carSpeedsbyXY, System.out);
		
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
				
		//now, work out the travel time (by car) from every square to every other square
		//this requires in the order of ~100MB of storage
		Debug.println("Getting car generalized costs", Debug.INFO);
		GraphNode[][] carpathtrees = getCarPathTrees();
		//printPathTrees(carpathtrees);
		double[][][][] cargencosts = getCarTravelGeneralizedCosts(carpathtrees);
		if(toprint && Debug.equalOrMoreVerbose(Debug.EXTRA_INFO)) printData(cargencosts, System.out, toprint);
		
		Debug.println("Getting PT generalized costs", Debug.INFO);
		//we need to get path trees and gen costs for each stochastic run
		GraphNode[][][] ptpathtrees = new GraphNode[Math.max(globals.stochasticPTruns, 1)][][];
		ptpathtrees = getPTPathTrees();
		double[][][][][] ptgencosts = new double[Math.max(globals.stochasticPTruns, 1)][][][][];
		for(int i = 0; i < Math.max(globals.stochasticPTruns, 1); i++)
		{
			ptgencosts[i] = getPTGeneralizedCosts(ptpathtrees[i]);
			if(toprint && Debug.equalOrMoreVerbose(Debug.EXTRA_INFO)) printData(ptgencosts[i], System.out, toprint);
		}
		
		Debug.println("Getting walk generalized costs", Debug.INFO);
		GraphNode[][] walkpathtrees = getWalkPathTrees();
		double[][][][] walkgencosts = getWalkGeneralizedCosts(walkpathtrees);
		if(toprint && Debug.equalOrMoreVerbose(Debug.EXTRA_INFO)) printData(walkgencosts, System.out, toprint);

		Debug.println("Getting bike generalized costs", Debug.INFO);
		GraphNode[][] bikepathtrees = getBikePathTrees();
		double[][][][] bikegencosts = getBikeGeneralizedCosts(bikepathtrees);
		if(toprint && Debug.equalOrMoreVerbose(Debug.EXTRA_INFO)) printData(bikegencosts, System.out, toprint);
		
		@SuppressWarnings("unchecked")
		Map<Mode, GraphNode[][]>[] pathtrees = new java.util.EnumMap[Math.max(globals.stochasticPTruns, 1)];
		@SuppressWarnings("unchecked")
		Map<Mode, double[][][][]>[] gencosts = new java.util.EnumMap[Math.max(globals.stochasticPTruns, 1)];
		
		for(int i = 0; i < Math.max(globals.stochasticPTruns, 1); i++)
		{
			pathtrees[i] = new java.util.EnumMap<Mode, GraphNode[][]>(Mode.class);
			pathtrees[i].put(Mode.CAR, carpathtrees);
			pathtrees[i].put(Mode.BIKE, bikepathtrees);
			pathtrees[i].put(Mode.WALK, walkpathtrees);
			pathtrees[i].put(Mode.PT, ptpathtrees[i]);

			gencosts[i] = new java.util.EnumMap<Mode, double[][][][]>(Mode.class);
			gencosts[i].put(Mode.CAR, cargencosts);
			gencosts[i].put(Mode.BIKE, bikegencosts);
			gencosts[i].put(Mode.WALK, walkgencosts);
			gencosts[i].put(Mode.PT, ptgencosts[i]);
			if(toprint) saveGenCosts(gencosts[i]);
		}

		
		
		if(toprint) {
			saveKeyInputs(); //saves the key inputs like trips, activities, etc
			this.makeGISMembershipFile(); //make the GIS membership file so that we can look at results afterwards
		}

		//ok, so next what we need to do is work out the trip matrix from A to B.
		Debug.println("Getting trip counts", Debug.INFO);
		
		double[][][][] cartripprobs = new double[X][Y][X][Y];
		double[][][][] nocartripprobs = new double[X][Y][X][Y];

				
		//activities reachable by public transport and overall,
		//within fixed budget of 1,2 and 4 times VoT. Remember the cost
		//of the trip is the cost of the RETURN trip.
		//2 types (with car avail and without), 3 VoTs
		double[][][][] activitiesreachablebyfixedbudget = new double[2][3][X][Y];
		
		//captive market
		if(globals.captivetravelshare > 0.0) 
		{
			int nruns = Math.max(globals.stochasticPTruns, 1);
			for(int i = 0; i < nruns; i++)
			{
				gencosts[i].remove(Mode.CAR);
				double[][][][] nocartripprobs0 = getTripProbs(normfact, gencosts[i], activitiesreachablebyfixedbudget[0], 1.0/nruns);
				
				nocartripprobs = MathUtil.addMatrices(nocartripprobs, nocartripprobs0);
				gencosts[i].put(Mode.CAR, cargencosts);
			}
			for(int x = 0; x < X; x++)
				for(int y = 0; y < Y; y++)
					if(Util.getSum(nocartripprobs[x][y]) > SMALL) 
					{
						Util.normalize(nocartripprobs[x][y]);
						if(toprint) this.writeDataToFile(nocartripprobs[x][y], new String[] {"Destination Choice", "nocar", "x="+(x+1)},"destChoice_from_"+(x+1)+"_"+(y+1));
					}
		}
		
		Debug.println("Finished captive travel market, now doing car", Debug.IMPORTANT);
		
		
		//normal car
		if(globals.caronlyavailable > 0.0) 
		{
			int nruns = Math.max(globals.stochasticPTruns, 1);
			for(int i = 0; i < Math.max(globals.stochasticPTruns, 1); i++)
			{
				double[][][][] cartripprobs0 = getTripProbs(normfact, gencosts[i], activitiesreachablebyfixedbudget[1], 1.0/nruns);
				cartripprobs = MathUtil.addMatrices(cartripprobs, cartripprobs0);
			}
			//print out trip probs 
			for(int x = 0; x < X; x++)
				for(int y = 0; y < Y; y++)
					if(Util.getSum(cartripprobs[x][y]) > SMALL)
					{
						Util.normalize(cartripprobs[x][y]);
						if(toprint) this.writeDataToFile(cartripprobs[x][y], new String[] {"Destination Choice", "caravailable", "x="+(x+1)},"destChoice_from_"+(x+1)+"_"+(y+1));
					}
		}
		
		double[][][][] combined = getCombinedTripProbs(cartripprobs, nocartripprobs);
		if(toprint) for(int x = 0; x < X; x++)
			for(int y = 0; y < Y; y++)
				if(Util.getSum(combined[x][y]) > SMALL)
					if(toprint) this.writeDataToFile(combined[x][y], new String[] {"Destination Choice", "combined", "x="+(x+1)},"destChoice_from_"+(x+1)+"_"+(y+1));			
		
		
		//calculate trips to. We need this if we fix the trips to each square, so we calculate these
		//separately prior to the other statistics
		double[][][] tripsToRes = calculateTripsTo(toprint, cartripprobs, nocartripprobs);
		double[][] tripsToWithCar = tripsToRes[0];
		double[][] tripsToNoCar = tripsToRes[1];
		double[][] tripsToTotal = tripsToRes[2];
		double[][] tripsToPerActivity = tripsToRes[3];
		
			
		//write out all the other key statistics. This requires some work....
		//so we only do it if we are writing out results OR if we need to do it for congestion
		if(toprint || globals.modelCongestion)
		{
			Map<String, double[][]> tripkmpersquare_leg_dir_mode = new HashMap<String, double[][]>();
			double[][] volByXY = outputStatistics(pathtrees, cartripprobs, nocartripprobs, 
					gencosts, toprint, tripsToTotal, tripkmpersquare_leg_dir_mode);
			for(int x = 0; x < X; x++) for(int y = 0; y < Y; y++) {
				if(volumebyXY[x][y] > 0) throw new RuntimeException("Volume is non-zero even before I started adding volumes in....internal RTran error?");
				volumebyXY[x][y] += volByXY[x][y];
			}
		}
				
		//print out activities reachable. We do this always as a cross check, because it
		//is collected more or less free when we do trip probs (which we always have to do,
		//whether we are in a final run or in a calibration/convergence run)
		doActivitiesReachable(activitiesreachablebyfixedbudget);
		
		return combined;
	}


	
	private double[][][][] getCombinedTripProbs(double[][][][] cartripprobs, double[][][][] nocartripprobs)
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		double[][][][] tripProbsTotal = new double[X][Y][][];
		for(int origx = 0; origx < X; origx++)
			for(int origy = 0; origy < Y; origy++)
			{
				tripProbsTotal[origx][origy] = new double[X][Y];
				if(Util.getSum(nocartripprobs[origx][origy])+Util.getSum(cartripprobs[origx][origy]) < 2*SMALL)
					continue;
				
				for(int destx = 0; destx < X; destx++)
					for(int desty = 0; desty < Y; desty++)
					{
						tripProbsTotal[origx][origy][destx][desty] = 
							cartripprobs[origx][origy][destx][desty]*(1-globals.captivetravelshare)+
							nocartripprobs[origx][origy][destx][desty]*globals.captivetravelshare;
					}
				Util.normalize(tripProbsTotal[origx][origy]);
			}	
		return tripProbsTotal;

	}
	
	private void doActivitiesReachable(double[][][][] activitiesreachablebyfixedbudget)
	throws java.io.IOException
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		
		for(int mins = 60; mins <= 240; mins *= 2)
		{	
			int index = 0; 
			if(mins == 120) index = 1;
			else if(mins == 240) index = 2;
				
			this.writeDataToFile(activitiesreachablebyfixedbudget[0][index], new String[] {"accessibility", "WithoutCar"}, "reachable"+mins+"mins");
			this.writeDataToFile(activitiesreachablebyfixedbudget[1][index], new String[] {"accessibility", "WithCar"}, "reachable"+mins+"mins");
			double[][] ptquintiles = getAccessibilityQuintiles(activitiesreachablebyfixedbudget[0][index]);
			double[][] caravailaccessquintiles = getAccessibilityQuintiles(activitiesreachablebyfixedbudget[1][index]);
			for(int q = 0; q < ptquintiles[1].length; q++) {
				outputs.addVal(false, "Accessibility_PT_"+mins+"minsVoT_Q"+(q+1), ptquintiles[1][q]+"");
				outputs.addVal(false, "Accessibility_CarAvail_"+mins+"minsVoT_Q"+(q+1), caravailaccessquintiles[1][q]+"");
			}
			
			//combine them into one (by public transport and by car)
			for(int x = 0; x < X; x++)
				for(int y = 0; y < Y; y++)
					activitiesreachablebyfixedbudget[0][index][x][y] = 
						activitiesreachablebyfixedbudget[0][index][x][y]*globals.captivetravelshare+
						activitiesreachablebyfixedbudget[1][index][x][y]*(1-globals.captivetravelshare); 
			double[][] overallquintiles = getAccessibilityQuintiles(activitiesreachablebyfixedbudget[0][index]);
			for(int q = 0; q < ptquintiles[1].length; q++) 
				outputs.addVal(false, "Accessibility_"+mins+"minsVoT_Q"+(q+1), overallquintiles[1][q]+"");
			
			this.writeDataToFile(activitiesreachablebyfixedbudget[1][index], new String[] {"accessibility", "Overall"}, "reachable"+mins+"mins");
					
			double avgreachable = 0.0;
			double normfact = 0.0;
			for(int x = 0; x < X; x++)
				for(int y = 0; y < Y; y++)
				{
					double tripsfrom = globals.tripsbyXY[x][y];
					if(tripsfrom > 0.0) {
						avgreachable += activitiesreachablebyfixedbudget[0][index][x][y]*tripsfrom;
						normfact += tripsfrom;
					}
				}
			avgreachable /= normfact;

			Debug.println("Activities reachable with generalized cost less than "+mins+" minutes (of VoT): "+avgreachable, Debug.IMPORTANT);
			//outputs.addVal(false, "Accessibility_"+mins+"minsVoT_Q1", ""+avgreachable);
			outputs.addVal(false, "Mean accessibility (reachable within generalized cost of "+mins+" mins)", ""+overallquintiles[0][0]);
			outputs.addVal(false, "Mean PT accessibility (reachable within generalized cost of "+mins+" mins)", ""+ptquintiles[0][0]);
			outputs.addVal(false, "Mean CarAvail accessibility (reachable within generalized cost of "+mins+" mins)", ""+caravailaccessquintiles[0][0]);
		}
	}

	
	
	private double[][] getAccessibilityQuintiles(double[][] accessbyxy)
	{
		double[] quintiles = new double[5];
		double[][] res = new double[][] { {0.0} , quintiles};

		rses.util.Heap<Double> heap = new rses.util.Heap<Double>(accessbyxy.length*accessbyxy[0].length);
		double totalTrips = Util.getSum(globals.tripsbyXY);
		
		double avg = 0.0;
		for(int x = 0; x < accessbyxy.length; x++)
			for(int y = 0; y < accessbyxy[x].length; y++)
			{
				double accessible = accessbyxy[x][y];
				double tripsFrom = globals.tripsbyXY[x][y]/totalTrips;
				avg += accessible*tripsFrom;
				heap.insert(new HeapElement<Double>(accessible, tripsFrom));
			}
		res[0][0] = avg;
		
		//ok, we've inserted them all in order, now pop them off and remember the quintiles
		//we also calculate the average accessibility for each quintile
		double[] weightsum = new double[5];
		int q = 0;
		boolean donelastquintile = false;
		while(!heap.isEmpty())
		{
			HeapElement<Double> elem = heap.extractMin();
			double accessibility = elem.getHeapValue();
			double weight = elem.getObject();
			quintiles[q] += accessibility*weight;
			weightsum[q] += weight;
			if(weightsum[q] > 0.2) {
				if(q == 4) donelastquintile = true;
				quintiles[q] /= weightsum[q];
				q++;
			}
		}
		if(!donelastquintile)
			quintiles[4] /= weightsum[4];
		
		return res;
	}
	
	
	
	
	private double[][][] calculateTripsTo(boolean toprint, double[][][][] cartripprobs, double[][][][] nocartripprobs)
	throws java.io.IOException
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		//work out trips to each destination. We only consider the first leg here when talking 
		//about trips to and from
		double[][] tripsToWithCar = this.getTripsTo(cartripprobs, 1-globals.captivetravelshare);
		double[][] tripsToNoCar = this.getTripsTo(nocartripprobs, globals.captivetravelshare);
		double[][] tripsToTotal = rses.math.MathUtil.addMatrices(tripsToNoCar, tripsToWithCar);
		if(Math.abs(Util.getSum(tripsToTotal)-Util.getSum(globals.tripsbyXY)) > SMALL) throw new RuntimeException("Total TripsTo calculated as "+Util.getSum(tripsToTotal)+" but simple sum of input trip matrix is "+Util.getSum(globals.tripsbyXY));
		if(toprint) this.writeDataToFile(tripsToWithCar, new String[] {"TripsTo"}, "CarAvailable");
		if(toprint) this.writeDataToFile(tripsToNoCar, new String[] {"TripsTo"}, "NoCarAvailable");
		if(toprint) this.writeDataToFile(tripsToTotal, new String[] {"TripsTo"}, "Total");
		//again, this is trip to in the sense of a 1-way trip
		double[][] tripsToPerActivity = new double[X][Y];
		for(int x = 0; x < X; x++) for(int y = 0; y < Y; y++) tripsToPerActivity[x][y] = tripsToTotal[x][y]/globals.activitiesXY[x][y];
		if(toprint) this.writeDataToFile(tripsToPerActivity, new String[] {"TripsTo"}, "TotalPerActivity");

		return new double[][][] {
				tripsToWithCar, tripsToNoCar, tripsToTotal, tripsToPerActivity
		};
	}
	

	/** 
	 * 
	 * @param pathtrees
	 * @param cartripprobs
	 * @param nocartripprobs
	 * @param gencosts
	 * @param toprint
	 * @param tripsToTotal
	 * @throws java.io.IOException
	 */
	private double[][] outputStatistics(Map<Mode, GraphNode[][]>[] pathtrees, 
			double[][][][] cartripprobs, 
			double[][][][] nocartripprobs, 
			Map<Mode, double[][][][]>[] gencosts,
			boolean toprint,
			double[][] tripsToTotal,
			Map<String, double[][]> tripkmpersquareby_expandedmode_direction_leg)
	throws java.io.IOException
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;

		
		//work out mode split by origin and destination
		//again, all these below are only talking about the 1st leg of the trip
		if(toprint)
		{
			Map<Mode, double[][]> tripsfrombymode = new java.util.EnumMap<Mode, double[][]>(Mode.class);
			Map<Mode, double[][]> tripstobymode = new java.util.EnumMap<Mode, double[][]>(Mode.class);
			Map<Mode, double[][]> modesharebyorig = new java.util.EnumMap<Mode, double[][]>(Mode.class);
			Map<Mode, double[][]> modesharebydest = new java.util.EnumMap<Mode, double[][]>(Mode.class);
			Map<Mode, Double> tripsbymode = new java.util.EnumMap<Mode, Double>(Mode.class);
			for(Mode m : Mode.values()) {
				tripsfrombymode.put(m, new double[X][Y]);
				tripstobymode.put(m, new double[X][Y]);
				modesharebydest.put(m, new double[X][Y]);
				modesharebyorig.put(m, new double[X][Y]);
				tripsbymode.put(m, 0.0);
			}
		
		
			//total trips is the total number of RETURN trips made
			double totalTrips = calcModeShareByOriginAndDest(modesharebyorig, modesharebydest, tripsfrombymode,
					tripstobymode, tripsToTotal, tripsbymode, toprint, cartripprobs, nocartripprobs,
					gencosts);
			if(Math.abs(totalTrips-Util.getSum(globals.tripsbyXY)) > SMALL) throw new RuntimeException("total trips calculated as "+totalTrips+" but just summing input trip matrix gets us "+Util.getSum(globals.tripsbyXY));
		}
		
		//Now the hard part. Trip km and congestion, and bus farebox revenue and rail farebox revenue
		//
		//Also throughput per square by Service (i.e. we need to know Bus0 and Bus1 differently)
		//
		//For these we need to go back to the path trees and work out the actual route taken
		Map<String, double[][]> tripkmbymodebyorigin = new HashMap<String, double[][]>();
		Map<String, Double> tripkmbymode = new HashMap<String, Double>();
		Map<String, double[][]> tripkmpersquarebyexpandedmode = new HashMap<String, double[][]>();
		Map<String, Double> revenueByMode = new java.util.HashMap<String, Double>();
		Map<String, double[][]> tripsbymodebyorigin = new java.util.HashMap<String, double[][]>();

		
		Debug.println("Collating final trip statistics... this can take some time", Debug.IMPORTANT);
		//need to do this for congestion
		updateTripStats(gencosts, pathtrees, cartripprobs, nocartripprobs,   
				tripkmbymodebyorigin, tripkmbymode, tripkmpersquarebyexpandedmode, 
				tripkmpersquareby_expandedmode_direction_leg, revenueByMode,
				tripsbymodebyorigin);
		
		//do some checking
		double totalCarKM = Util.getSum(tripkmpersquarebyexpandedmode.get(Mode.CAR+""));
		Debug.println("Total car km is "+totalCarKM, Debug.INFO);
		if(Math.abs(totalCarKM-tripkmbymode.get(Mode.CAR+"")) > 10000*SMALL)
			throw new RuntimeException("Trip KM by car totals from different methods disagree -- "+totalCarKM+" against "+tripkmbymode.get(Mode.CAR+""));
		else Debug.println("Trip KM and car totals cross check error is "+Math.abs(totalCarKM-tripkmbymode.get(Mode.CAR+"")), Debug.INFO);
			
		
		//ok, now write those out!
		if(toprint)
		{
			double totaltripkm = 0.0;
			for(Double d : tripkmbymode.values()) totaltripkm += d;

			double meantriplength = 0.5*totaltripkm/Util.getSum(globals.tripsbyXY);
			Debug.println("Average (1-way) trip length is "+meantriplength+" km", Debug.IMPORTANT);
			outputs.addVal("Mean trip length (km)", ""+meantriplength);
			
			//do average trip length by origin 
			//TODO Need to clarify here that trips by origin is quite complicated when
			//you do it by expanded trip type. For instance, if you walk for part of
			//the way, and then catch a bus for part of the way, and then walk,
			//and then catch a train, this gets counted 3 times at the origin
			//(because there are 3 expanded modes used).
			doTripLengthsByOrigin(tripkmbymodebyorigin, tripsbymodebyorigin);

			//do all public transport related accounting 
			java.util.Map<String, double[][]> passCounts = doPassengerCounts(tripkmbymodebyorigin,
					tripkmpersquarebyexpandedmode, tripkmpersquareby_expandedmode_direction_leg,
					tripkmbymode, totaltripkm);
		
			doCombinedPassengerCounts(passCounts); //Combine all the bus and train counts
			//print out revenue
			for(String mstr : revenueByMode.keySet()) {
				Debug.println("Revenue from mode "+mstr+" is "+revenueByMode.get(mstr), Debug.IMPORTANT);
				outputs.addVal(false, "Revenue from "+mstr, ""+revenueByMode.get(mstr));
			}
			calculateBusAndTrainOperatingCosts();
		}
		
		if(toprint)
		{
			//write out the final congested speeds
			writeDataToFile(globals.carSpeedsbyXY, new String[] {"CongestedSpeeds"}, "carSpeed");
			//write out the slowdown relative to free-flow
			writeDataToFile(MathUtil.subtractMatrices(globals.origCarSpeedsbyXY, globals.carSpeedsbyXY), new String[] {"CongestedSpeeds"}, "kmh_lossFromCongestion");
			for(String key : globals.frequencies.keySet())
			{
				if(getExpandedModes().contains("BUS_"+key)) 
					outputs.addVal(false, "Frequency for BUS_"+key, ""+globals.frequencies.get(key));
				else if(getExpandedModes().contains("TRAIN_"+key)) 
					outputs.addVal(false, "Frequency for TRAIN_"+key, ""+globals.frequencies.get(key));
			}
		}

		
		//now work out what the volume is by X/Y
		double[][] volByXY = new double[X][Y];
		double[][] carkmbyxy = tripkmpersquarebyexpandedmode.get(Mode.CAR+"");
		for(int x = 0; x < X; x++)
			for(int y = 0; y < Y; y++) {
				volByXY[x][y] = carkmbyxy[x][y]/globals.gridsquare_edgekm;
				//Debug.println("Calculated carkm at "+x+","+y+" is "+carkmbyxy[x][y]+" using this to update volumes", Debug.INFO);
			}
		
		/*TODO I no longer write these out if we are doing congestion modelling
		 * because they produce misleading outputs.
		 * To understand why, think of this example: Let us suppose that there are only 2 
		 * bridges over a river. We might do our first run and we find that both of the
		 * bridges are congested, but one of the bridges is more congested. So
		 * on the next iteration, most of the traffic goes over the other bridge.
		 * But that makes THAT bridge more congested, so on the next iteration they
		 * all switch back to the other bridge. And so on it goes, with all the
		 * traffic oscillating between the two bridges. Now this is OK from a congestion
		 * modelling POV, because we average over iterations, and so we end up with 
		 * reasonable volumes on both bridges, but in any single run, everyone goes by the
		 * shortest path, even if it is only slightly shorter (by 0.001 second), and
		 * so we can't properly report the results from any iteration  
		 */
		if(!globals.modelCongestion)
		{
			writeDataToFile(volByXY, new String[] {"Traffic", "vehicle count"}, "CAR");
			double[][] vc = MathUtil.divideMatrices(volByXY, globals.roadCapacityXY);
			writeDataToFile(vc, new String[] {"Traffic"}, "VolumeCapacityRatio");
		}
		
		return volByXY;
	}

	private java.util.Map<String, double[][]> doPassengerCounts(
			Map<String, double[][]> tripkmbymodebyorigin,
			Map<String, double[][]> tripkmpersquarebyexpandedmode,
			Map<String, double[][]> tripkmpersquareby_expandedmode_direction_leg,
			Map<String, Double> tripkmbymode,
			double totaltripkm
			)
	throws java.io.IOException
	{
		Map<String, double[][]> passCounts = new java.util.HashMap<String, double[][]>();
		Map<String, Double> maxpermode = new HashMap<String, Double>();
		for(String expmode : getExpandedModes()) 
		{
			this.writeDataToFile(tripkmbymodebyorigin.get(expmode), new String[] {"Trip KM"}, expmode);
			
			if(!globals.modelCongestion)
				this.writeDataToFile(tripkmpersquarebyexpandedmode.get(expmode), new String[] {"Traffic", "km per square"}, expmode);

			//saved by modestr+" "+l+" "+d
			for(String d : new String[] {"N", "S", "E", "W"})
				for(String l : new String[] {"1", "2"})
				{
					if(!globals.modelCongestion)
						writeDataToFile(tripkmpersquareby_expandedmode_direction_leg.get(l+d+expmode), new String[] {"Traffic", "km per square", "Leg "+l, d+"-bound"}, expmode);			
					savePassengerCounts(expmode, d, l, tripkmpersquareby_expandedmode_direction_leg.get(l+d+expmode), passCounts, maxpermode);
				}
			
			double tkmbymode = tripkmbymode.get(expmode);
			double share = (100.0*tkmbymode/totaltripkm);
			Debug.println("Total trip km by mode "+expmode+" is "+tkmbymode+"  ( thats "+share+" %)", Debug.IMPORTANT);
			outputs.addVal(false, "Total trip km by "+expmode,""+tkmbymode);
			outputs.addVal(false, "Trip km share by "+expmode,""+share/100.0);
			outputs.addVal(false, "Max passengers by "+expmode, ""+maxpermode.get(expmode));
		}
		
		return passCounts;
	}
	
	
	
	private void savePassengerCounts(String modestr, String dir, String leg,  double[][] tripkmbysquare, 
			java.util.Map<String, double[][]> save, Map<String, Double> maxpermode)
	throws java.io.IOException
	{
		if(!maxpermode.containsKey(modestr)) maxpermode.put(modestr, 0.0);
			
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
				
		double[][] passengers = new double[X][Y];
		for(int x = 0; x < X; x++)
			for(int y = 0; y < Y; y++) {
				passengers[x][y] = tripkmbysquare[x][y]/globals.gridsquare_edgekm;
				if(passengers[x][y] > maxpermode.get(modestr)) maxpermode.put(modestr, passengers[x][y]);
			}

		this.writeDataToFile(passengers, new String[] {"Passengers", "leg-"+leg, dir+"-bound"}, modestr);
		save.put(modestr+" "+leg+" "+dir, passengers);
	}
	
	
	//we have each key being: modestr+" "+leg+" "+dir
	private void doCombinedPassengerCounts(java.util.Map<String, double[][]> counts)
	throws java.io.IOException
	{
		for(String d : new String[] {"N", "S", "E", "W"})
			for(String l : new String[] {"1", "2"})
			{
				double[][] buscounts = new double[globals.carSpeedsbyXY.length][globals.carSpeedsbyXY[0].length];
				for(String busid : globals.BusWaitTimes.keySet())
					buscounts = MathUtil.addMatrices(buscounts, counts.get("BUS_"+busid+" "+l+" "+d));
				this.writeDataToFile(buscounts, new String[] {"Passengers", "leg-"+l, d+"-bound"}, "BUS_Combined");
			}

		for(String d : new String[] {"N", "S", "E", "W"})
			for(String l : new String[] {"1", "2"})
			{
				double[][] traincounts = new double[globals.carSpeedsbyXY.length][globals.carSpeedsbyXY[0].length];
				for(String trainid : globals.RailWaitTimes.keySet())
					traincounts = MathUtil.addMatrices(traincounts, counts.get("TRAIN_"+trainid+" "+l+" "+d));
				this.writeDataToFile(traincounts, new String[] {"Passengers", "leg-"+l, d+"-bound"}, "TRAIN_Combined");
			}
	}
	
	private void calculateBusAndTrainOperatingCosts()
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		for(String busid : globals.BusWaitTimes.keySet())
		{
			double buses = 0.0; //buses operating
			for(int x = 0; x < X; x++)
				for(int y = 0; y < Y; y++)
				{
					if(globals.BusWaitTimes.get(busid)[x][y] > 1000) continue;
					
					//how long to transit the square? Say it is 0.05 hours
					double time = globals.gridsquare_edgekm/globals.carSpeedsbyXY[x][y] + globals.busdelaypersquare_hrs;
					//so what frequency (buses per hour) does 1 bus give us. In this case 20
					double freq = 1/time;
					//and what is the frequency that we actually have. (Say it is 5  [frequency of 12 mins, wait time of 6])
					double actfreq = 60/(2*globals.BusWaitTimes.get(busid)[x][y]);
					//so how many buses required to provide the specified frequency. 1/4 in this case
					double buscount = actfreq/freq;
					//(remember to times by two because all services are bi-directional)
					buses += 2*buscount;
				}
			Debug.println("Bus "+busid+" operating cost is "+buses*globals.busOperatingCostPerBus+" from "+buses+" buses", Debug.IMPORTANT);
			outputs.addVal(false, "OperatingCosts BUS_"+busid, ""+buses*globals.busOperatingCostPerBus);
		}

		for(String trainid : globals.RailWaitTimes.keySet())
		{
			double trains = 0.0; 
			for(int x = 0; x < X; x++)
				for(int y = 0; y < Y; y++)
				{
					if(globals.RailWaitTimes.get(trainid)[x][y] > 1000) continue;
					
					//how long to transit the square? Say it is 0.05 hours
					double time = globals.gridsquare_edgekm/globals.TrainSpeeds.get(trainid)[x][y] + globals.traindelaypersquare_hrs;
					//so what frequency (buses per hour) does 1 bus give us. In this case 20
					double freq = 1/time;
					//and what is the frequency that we actually have. (Say it is 5  [frequency of 12 mins, wait time of 6])
					double actfreq = 60/(2*globals.RailWaitTimes.get(trainid)[x][y]);
					//so how many buses required to provide the specified frequency. 1/4 in this case
					double traincount = actfreq/freq;
					//(remember to times by two because all services are bi-directional)
					trains += 2*traincount;
				}
			Debug.println("Train "+trainid+" operating cost is "+trains*globals.trainOperatingCostPerTrain+" from "+trains+" trains", Debug.IMPORTANT);
			outputs.addVal(false, "OperatingCosts TRAIN_"+trainid, ""+trains*globals.trainOperatingCostPerTrain);
		}

	}
	
	
	//Trip lengths are worked out in a compicated way. For trips where you shift modes,
	//we count that as multiple trips from that origin. So, for example, if you 
	//walked for part of a PT trip, we count that as a walk trip as well, and then
	//divide the total WALK km by the number of walk trips.
	private void doTripLengthsByOrigin(Map<String, double[][]> tripkmbymodebyorigin,
			Map<String, double[][]> tripsByModeByOrigin)
	throws java.io.IOException
	{
		int X = globals.tripsbyXY.length;
		int Y = globals.tripsbyXY[0].length;
		
		
		double[][] triplengthbyorigin = new double[X][Y];
		
		for(int x = 0; x < X; x++) 
			for(int y = 0; y < Y; y++)
			{
				double totalkm = 0.0;
				for(String m : tripkmbymodebyorigin.keySet())
				{
					double km = tripkmbymodebyorigin.get(m)[x][y];
					totalkm += km;
				}
				triplengthbyorigin[x][y] += totalkm;
			}

		for(int x = 0; x < X; x++) 
			for(int y = 0; y < Y; y++)
			{
				if(globals.tripsbyXY[x][y] < SMALL) {
					triplengthbyorigin[x][y] = Double.NaN;
					continue;
				}
				
				//denominator times by 2  because we want the single leg trip length
				triplengthbyorigin[x][y] /= 2*globals.tripsbyXY[x][y];
			}
		
		writeDataToFile(triplengthbyorigin, new String[] {"Trip Length (1-way, multimodal)"}, "Combined");
		
		for(String m : tripkmbymodebyorigin.keySet())
			writeDataToFile(tripsByModeByOrigin.get(m), new String[] {"Trip Counts (incl. multimodal)", "By Expanded Modes"}, m);

		for(String m : getExpandedModes()) 
		{
			double[][] triplengthbyoriginbymode = new double[X][Y];
			for(int x = 0; x < X; x++) 
				for(int y = 0; y < Y; y++) {
					if(tripsByModeByOrigin.get(m)[x][y] > 0)
						triplengthbyoriginbymode[x][y] = tripkmbymodebyorigin.get(m)[x][y]/(2*tripsByModeByOrigin.get(m)[x][y]);
				}
			
			writeDataToFile(triplengthbyoriginbymode, new String[] {"Trip Length (1-way, multimodal)"}, m);
		}

	}
	
	
	//This function is just to cross check the different stats against one another
	//to be extra sure that we have done all our accounting properly when we did it the first time
	private static void crossCheckStats(double[][] tripsToWithCar,
			double[][] tripsToNoCar,
			double[][] tripsToTotal,
			double[][] tripsToPerActivity,
			Map<Mode, double[][]> tripsfrombymode,
			Map<Mode, double[][]> tripstobymode,
			Map<Mode, double[][]> modesharebyorig,
			Map<Mode, double[][]> modesharebydest,
			Map<Mode, Double> tripsbymode,
			Map<String, double[][]> tripkmbymodebyorigin,
			Map<String, Double> tripkmbymode,
			Map<String, double[][]> tripkmpersquarebyexpandedmode,
			Map<String, double[][]> tripkmpersquareby_expandedmode_direction_leg)
	{
		//TODO
	}
	
	
	private double calcModeShareByOriginAndDest(Map<Mode, double[][]> modesharebyorig, 
			Map<Mode, double[][]> modesharebydest,
			Map<Mode, double[][]> tripsfrombymode,
			Map<Mode, double[][]> tripstobymode,
			double[][] tripsToTotal,
			Map<Mode, Double> tripsbymode,
			boolean toprint,
			double[][][][] cartripprobs,
			double[][][][] nocartripprobs,
			Map<Mode, double[][][][]>[] gencosts)
	throws java.io.IOException
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;

		//first lets calculate trips to and from by mode
		int nruns = Math.max(1, globals.stochasticPTruns);
		for(int i = 0; i < nruns; i++)
		{
			for(int origx = 0; origx < X; origx++)
				for(int origy = 0; origy < Y; origy++)
				{
					if(Util.getSum(nocartripprobs[origx][origy]) < SMALL && Util.getSum(cartripprobs[origx][origy]) >= SMALL) throw new RuntimeException("Car trip probs and no car trip probs do not match when null... but this should be impossibe -- one should only be null when tripsFromXY is zero, and in that case the other should be zero"); 
					if(Util.getSum(nocartripprobs[origx][origy]) < SMALL) continue;
					
					//if there are no trips from here there is nothing to do.
					if(globals.tripsbyXY[origx][origy] < SMALL) continue;

					for(int destx = 0; destx < X; destx++)
						for(int desty = 0; desty < Y; desty++) 
						{			
							if(globals.activitiesXY[destx][desty] < SMALL) continue;
							
							double[][][][] carcosts = gencosts[i].remove(Mode.CAR);
							Map<Mode, Double> modesplit = getModeSplitForTrip(origx, origy, destx, desty, gencosts[i]);
							if(modesplit == null) throw new RuntimeException("trying to calculate mode split between "+origx+" "+origy+" and "+destx+" "+desty+" but no mode is possible");
							for(Mode m : modesplit.keySet()) 
							{
								Debug.println("Nocar Mode split of "+m+" from "+origx+" "+origy+" to "+destx+" "+desty+" is "+modesplit.get(m)+" (gencost "+getReturnJourneyGC(gencosts[i], m, origx, origy, destx, desty)+")", Debug.EXTRA_INFO);
								double numtripsbythismodebetweentheselocations = modesplit.get(m)*globals.captivetravelshare*(1.0/nruns)*nocartripprobs[origx][origy][destx][desty]*globals.tripsbyXY[origx][origy];
								tripsfrombymode.get(m)[origx][origy] += numtripsbythismodebetweentheselocations; 
								tripstobymode.get(m)[destx][desty] += numtripsbythismodebetweentheselocations;
							}
							gencosts[i].put(Mode.CAR, carcosts);

							//now do it again but with car available
							modesplit = getModeSplitForTrip(origx, origy, destx, desty, gencosts[i]);
							if(modesplit == null) throw new RuntimeException("trying to calculate mode split between "+origx+" "+origy+" and "+destx+" "+desty+" but no mode is possible");
							for(Mode m : modesplit.keySet()) 
							{
								Debug.println("CarAvail Mode split of "+m+" from "+origx+" "+origy+" to "+destx+" "+desty+" is "+modesplit.get(m)+" (gencost "+getReturnJourneyGC(gencosts[i], m, origx, origy, destx, desty)+")", Debug.EXTRA_INFO);
								double numtripsbythismodebetweentheselocations = modesplit.get(m)*(1-globals.captivetravelshare)*(1.0/nruns)*cartripprobs[origx][origy][destx][desty]*globals.tripsbyXY[origx][origy];
								tripsfrombymode.get(m)[origx][origy] += numtripsbythismodebetweentheselocations; 
								tripstobymode.get(m)[destx][desty] += numtripsbythismodebetweentheselocations;
							}						

						}
				}
		}
		
		//now calculate mode share by origin and dest, and overall mode share
		double totalTrips = 0.0;
		for(int x = 0; x < X; x++)
			for(int y = 0; y < Y; y++)
			{
				double tripsfromcheck = 0.0;
				for(Mode m : Mode.values())
				{
					if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS) && tripsfrombymode.get(m)[x][y] > globals.tripsbyXY[x][y]+SMALL)
						throw new IllegalStateException("we have trips from "+(x+1)+" "+(y+1)+" by mode "+m+" but in the input there were fewer total trips (i.e. by all modes)..... should be impossible");
					
					if(globals.tripsbyXY[x][y] > 0) 
					{
						double tripsfrom = tripsfrombymode.get(m)[x][y];
						modesharebyorig.get(m)[x][y] = tripsfrom/globals.tripsbyXY[x][y];
						tripsfromcheck += tripsfrom;
						tripsbymode.put(m, tripsbymode.get(m)+tripsfrom);
						totalTrips += tripsfrom;
					}
					if(tripsToTotal[x][y] > 0)
						modesharebydest.get(m)[x][y] = tripstobymode.get(m)[x][y]/tripsToTotal[x][y];
				}
				if(Math.abs(globals.tripsbyXY[x][y]-tripsfromcheck) > SMALL)
					throw new RuntimeException("Trips from "+(x+1)+" "+(y+1)+" = "+globals.tripsbyXY[x][y]+" specified in input does not match that calculated, of "+tripsfromcheck);
			}

		//times by 2 here because we want to report 1-way trips
		Debug.println("Total (1-way) trips: "+2*totalTrips, Debug.IMPORTANT);
		outputs.addVal(false, "Total trips", ""+2*totalTrips);
		
		if(toprint) for(Mode m : Mode.values()) 
		{
			this.writeDataToFile(modesharebyorig.get(m), new String[] {"ModeShare", "ByOrigin"}, m+"");
			this.writeDataToFile(modesharebydest.get(m), new String[] {"ModeShare", "ByDestination"}, m+"");
			if(m == Mode.CAR) doParkingRevenue(modesharebydest.get(m), tripsToTotal);
			double modeshare = 100*tripsbymode.get(m)/totalTrips;
			//again, times by two because we want to report unlinked trips, not return ones
			Debug.println("Total (1-way) trips by mode "+m+" is "+2*tripsbymode.get(m)+" ( "+Util.safeGetTruncatedReal(modeshare, 5, "ERR")+" %)", Debug.IMPORTANT);
			outputs.addVal(false, "Mode share by "+m, ""+modeshare/100.0);
		}

		return totalTrips;
	}
	
	private void doParkingRevenue(double[][] carmodesharebydest, double[][] tripsToTotal)
	throws java.io.IOException
	{
		//ok, first turn the mode share into an actual number of car trips
		double[][] tripsToByCar = MathUtil.multiplyMatrices(carmodesharebydest, tripsToTotal);
		
		//then calculate revenue per square
		double[][] revenuePerSquare = MathUtil.multiplyMatrices(tripsToByCar, globals.parkingRevenue_1stleg);
		
		//save revenue per square
		writeDataToFile(revenuePerSquare, new String[] {"Revenue"}, "Parking");
		
		//save total revenue
		double revenue = Util.getSum(revenuePerSquare); 
		
		outputs.addVal(false, "Parking Revenue", ""+revenue);
	}
	
	
	private java.util.List<String> getExpandedModes()
	{
		java.util.List<String> expmodes = new java.util.ArrayList<String>();
		expmodes.add(""+Mode.CAR);
		expmodes.add(""+Mode.BIKE);
		expmodes.add(""+Mode.WALK);
		for(String s : globals.BusWaitTimes.keySet())
			expmodes.add("BUS_"+s);
		for(String s : globals.RailWaitTimes.keySet())
			expmodes.add("TRAIN_"+s);
		return expmodes;
	}
	
	
	
	/** Do the 'hard' trip stats work, that actually involves path trees
	 * 
	 * @param pathtrees
	 * @param cartripprobs
	 * @param nocartripprobs
	 * @param toprint
	 * @param modesharebyorig
	 * @param tripkmbymodebyorigin_out
	 * @param tripkmbymode_out
	 * @param tripkmbysquarebymode_out
	 */
	private void updateTripStats(Map<Mode, double[][][][]>[] gencostsbymode,
			Map<Mode, GraphNode[][]>[] pathtrees, 
			double[][][][] cartripprobs, 
			double[][][][] nocartripprobs, 
			Map<String, double[][]> tripkmbymodebyorigin_out, 
			Map<String, Double> tripkmbymode_out, 
			Map<String, double[][]> tripkmbysquarebymode_out,
			Map<String, double[][]> tripkmpersquareby_expandedmode_direction_leg_out,
			Map<String, Double> revenueByMode_out,
			Map<String, double[][]> tripsbymodebyorigin_out)
	{
		int X = globals.carSpeedsbyXY.length;
		int Y = globals.carSpeedsbyXY[0].length;
		
		//we get a list of expanded modes because PT is a core Mode, but we do care about
		//the breakdown beneath that (bus, train, etc etc)
		java.util.List<String> expandedmodes = getExpandedModes();
		for(String expmode : expandedmodes)
		{
			tripkmbysquarebymode_out.put(expmode, new double[X][Y]);
			for(String d : new String[] {"N", "S", "E", "W"})
				for(String l : new String[] {"1", "2"})
					tripkmpersquareby_expandedmode_direction_leg_out.put(l+d+expmode, new double[X][Y]);
			tripkmbymodebyorigin_out.put(expmode, new double[X][Y]);
			tripkmbymode_out.put(expmode, 0.0);
			revenueByMode_out.put(expmode, 0.0);
			tripsbymodebyorigin_out.put(expmode, new double[X][Y]);
		}

		
		//just go through all trips and do the accounting. Need to do this in two stages
		//(1 for each leg of the trip)
		int nruns = Math.max(1, globals.stochasticPTruns);
		double totaltripscheck = 0.0;
		double totaltransfers = 0.0;
		for(int i = 0; i < nruns; i++)
		{
			for(Mode m : Mode.values())
			{
				Debug.println("Collating trip statistics for mode "+m, Debug.INFO);

				//lets get all the path trees from every origin. This can be a BIG memory footprint
				@SuppressWarnings("unchecked")
				Map<String, GraphEdge>[][] pathsfromorig = new Map[X][Y];
				for(int origx = 0; origx < X; origx++)
					for(int origy = 0; origy < Y; origy++)
						pathsfromorig[origx][origy] = GraphUtil.getAllPathsFromPathTree(pathtrees[i].get(m)[origx][origy]);

				for(int origx = 0; origx < X; origx++)
				{
					for(int origy = 0; origy < Y; origy++)
					{
						if(globals.tripsbyXY[origx][origy] < SMALL) continue;
						
						Debug.println("Collating final trip statistics from "+(origx+1)+" "+(origy+1), Debug.EXTRA_INFO);
						for(int destx = 0; destx < X; destx++)
						{
							for(int desty = 0; desty < Y; desty++)
							{
								if(globals.activitiesXY[destx][desty] < SMALL) continue;

								//work out number of trips between origin and destination by mode m
								
								//this is the number by this mode when car is available
								Map<Mode, Double> msplit = getModeSplitForTrip(origx, origy, destx, desty, gencostsbymode[i]);
								if(msplit == null) //no way to travel between these squares by ANY mode
									throw new RuntimeException("zero probability of all modes between "+origx+" "+origy+" "+destx+" "+desty+". Have you put trips or activities in a zone that is unreachable?");
								
								double trips = globals.tripsbyXY[origx][origy]*(1-globals.captivetravelshare)*cartripprobs[origx][origy][destx][desty]*msplit.get(m);
								//Debug.println("Trips between "+(origx+1)+" "+(origy+1)+" and "+(destx+1)+" "+(desty+1)+" by mode "+m+" in car-avail market are: "+trips, Debug.INFO);
								//and this is when it is not 
								if(m != Mode.CAR) { //non-car modes only for this segment
									double[][][][] cargc = gencostsbymode[i].remove(Mode.CAR);
									trips += globals.tripsbyXY[origx][origy]*(globals.captivetravelshare)*nocartripprobs[origx][origy][destx][desty]*getModeSplitForTrip(origx, origy, destx, desty, gencostsbymode[i]).get(m);
									gencostsbymode[i].put(Mode.CAR, cargc);
								}
								//Debug.println("Total trips between "+(origx+1)+" "+(origy+1)+" and "+(destx+1)+" "+(desty+1)+" by mode "+m+": "+trips, Debug.INFO);
								//adjust because we may be doing multiple stochastic runs and 
								//we dont want to double/triple count 
								if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID) && Double.isNaN(trips)) throw new RuntimeException("Number of trips is NaN!");
								trips *= 1.0/nruns;
								totaltripscheck += trips;

								//do main and return legs
								for(boolean isreturn : new boolean[] {true, false})
								{
									java.util.Map<String, GraphEdge> paths;
									if(isreturn) paths = pathsfromorig[destx][desty];
									else paths = pathsfromorig[origx][origy];

									int transfers = updateTripStats(paths, cartripprobs, nocartripprobs,  
											tripkmbymodebyorigin_out, 
											tripkmbymode_out, 
											tripkmbysquarebymode_out, 
											tripkmpersquareby_expandedmode_direction_leg_out,
											origx, origy, destx, desty, m, trips, isreturn, 
											revenueByMode_out, tripsbymodebyorigin_out);
									totaltransfers += transfers/((double) nruns);
								}

							}
						}
					}
				}
			}
		}

		Debug.println("in upper updateTripStats(), total trips is "+totaltripscheck, Debug.INFO);
		Debug.println("in upper updateTripStats(), total transfers in the public transport system: "+(int) Math.round(totaltransfers), Debug.INFO);
		
		//actually we need to give a *little* more leeway here because rounding errors
		//can actually be significant in this calculation
		//Debug.println("Total trips check is "+totaltripscheck, Debug.INFO);
		if(Math.abs(totaltripscheck-Util.getSum(globals.tripsbyXY)) > 100*SMALL)
			throw new IllegalStateException("internal accounting error? totaltripscheck is "+totaltripscheck+" but trip sum from inputs is "+Util.getSum(globals.tripsbyXY));
	}
	
	
	//ntrips is the number of trips between origin and destination that are made by mode m	
	private int updateTripStats(java.util.Map<String, GraphEdge> paths, 
			double[][][][] cartripprobs, 
			double[][][][] nocartripprobs, 
			Map<String, double[][]> tripkmbymodebyorigin_out, 
			Map<String, Double> tripkmbymode_out, 
			Map<String, double[][]> tripkmbysquarebymode_out,
			Map<String, double[][]> tripkmpersquareby_expandedmode_direction_leg,
			int origx, int origy, int destx, int desty, Mode m,
			double ntrips,
			boolean isReturnLeg,
			Map<String, Double> revenueByMode,
			Map<String, double[][]> tripsbymodebyorigin) 
	{
				
		//add revenue per trip by car
		if(m == Mode.CAR)
			revenueByMode.put(m+"", revenueByMode.get(""+m)+globals.carRevenuePerTrip*ntrips);
		
		//first of all, we need to handle the special case where origin and destination are the
		//same. Note that we dont need to worry about this for PublicTransport (mode PT) because
		//even if origin and destination nodes are the same, the transport Graph gets built and
		//that forces some 'moves' through the graph even when origin and destination are the same.
		if(origx == destx && origy == desty && m != Mode.PT)
		{
			double km = 0.5*globals.gridsquare_edgekm;
			String mstr = m+"";
			tripkmbymodebyorigin_out.get(mstr)[origx][origy] += km*ntrips;
			tripsbymodebyorigin.get(mstr)[origx][origy] += ntrips;
			tripkmbymode_out.put(mstr, tripkmbymode_out.get(mstr)+km*ntrips);
			tripkmbysquarebymode_out.get(mstr)[origx][origy] += km*ntrips;
			
			//add revenue perkm by car
			if(m == Mode.CAR)
				revenueByMode.put(m+"", revenueByMode.get(""+m)+globals.carRevenuePerKM*ntrips*km);
			
			//NB: Note we dont do any directional stuff (N,S,E,W). We assume any
			//intra-square trips use local roads or something like that,
			//so we effectively dont count it in congestion calcs.
			//TODO need to add revenue here for walk and bike if we ever get any by these modes
			return 0;
		}
		
		//OK, now we do the regular cases
		
		
		//ok, lets get the actual path taken between origin and destination by mode m
		//then work out our destination node id (it depends on the mode)
		String destid = "";
		int x = destx;
		int y = desty;
		if(isReturnLeg) {
			x = origx; y = origy;
		}
			
		if(m == Mode.CAR) destid = x+" "+y;
		else if(m == Mode.BIKE) destid = x+" "+y;
		else if(m == Mode.WALK) destid = x+" "+y;
		else if(m == Mode.PT) destid = "DEST "+x+" "+y;
		else throw new RuntimeException("I dont know how to calculate trip stats for mode "+m);
		
		
		GraphEdge lastedge = paths.get(destid);
		if(!lastedge.leadsto.getId().equals(destid)) throw new IllegalStateException("Impossible case");
		
		//keep track of the number of modes used on the path
		Set<String> modesUsed = new java.util.HashSet<String>();
		
		//saves time if we keep track of what the last mode was, because often
		//they are the same from link to link, and if so we save some time
		String currentmodestr = "";
		double currentRev = 0.0;
		double[][] tkmbyorig = null;
		double[][] throughput = null;
		int pt_transfers = 0;
		
		//ok, now collect statistics for each link in the trip
		do
		{			
			//this is ugly as all hell. The info/hook object for each path holds the km distance as 
			//well as the total path length to that point. So we resort to this sort of ugliness to
			//pull out the distance
			double[] info = (double[]) (((Object[]) (lastedge.info))[0]);
			double distkm = info[0];
			double revenue = info[1]; //this is the revenue for this leg/segment
						
			distkm *= ntrips; //multiply by the number of trips from by this mode
			revenue *= ntrips;
			
			//get the expanded mode, and the x,y coords of the from square and the to square
			String mstr = ""+m;
			int fromx;
			int fromy;
			int tox;
			int toy;
			if(m == Mode.PT) {
				String[] bits = destid.split(" ");
				mstr = bits[0];
				if(mstr.equals("DEST")) mstr = Mode.WALK+"";
				else if(mstr.equals("WALKAFTER")) mstr = Mode.WALK+"";
				
				tox = Integer.parseInt(bits[1]);
				toy = Integer.parseInt(bits[2]);
				bits = lastedge.leadsfrom.getId().split(" ");
				String frommode = bits[0];
				if(frommode.equals("WALKAFTER") && !mstr.equals("WALK")) 
				{
					if(!mstr.startsWith("BUS") && !mstr.startsWith("TRAIN"))
						throw new RuntimeException("Going from WALKAFTER to a non-WALK public transport node that is neither BUS nor TRAIN? Isnt this impossible?");
					pt_transfers+=ntrips;
				}
				fromx = Integer.parseInt(bits[1]);
				fromy = Integer.parseInt(bits[2]);
			}
			else {
				String[] bits = destid.split(" ");
				tox = Integer.parseInt(bits[0]);
				toy = Integer.parseInt(bits[1]);				
				bits = lastedge.leadsfrom.getId().split(" ");
				fromx = Integer.parseInt(bits[0]);
				fromy = Integer.parseInt(bits[1]);
			}

			modesUsed.add(mstr); //remember that we used this mode *somewhere* on the trip
			
			if(mstr.equals(currentmodestr))
				currentRev += revenue;
			else //we changed modes, so we save what we have and get all the right objects to update
			{
				if(currentRev > 0) //save what we have
					revenueByMode.put(currentmodestr, revenueByMode.get(currentmodestr)+currentRev);
				currentRev = revenue;
				currentmodestr = mstr;
				tkmbyorig = tripkmbymodebyorigin_out.get(mstr);
				throughput = tripkmbysquarebymode_out.get(mstr);				
			}

			if(Debug.equalOrMoreParanoid(Debug.DEBUG_PARANOID)) if(!tripkmbymodebyorigin_out.containsKey(mstr)) throw new IllegalStateException("Failed to find key for mode "+mstr);

			//update trip km by origin
			if(tkmbyorig == null) throw new IllegalStateException("For mode "+m+" mstr "+mstr+" got null tkmbyorig");
			tkmbyorig[origx][origy] += distkm;
			
			//update trip km by mode
			tripkmbymode_out.put(mstr, tripkmbymode_out.get(mstr)+distkm);
									
			//update trip km by square being transited
			//half the trip takes place in the 'from' square
			//and half takes place in the 'to' square
			throughput[tox][toy] += distkm/2;
			throughput[fromx][fromy] += distkm/2;
			
			//now do the leg/direction specific stuff
			if(fromx == tox && fromy == toy)
				/*Dont do anything if we are moving within a square. We assume its a transfer or else on local streets so doesnt really count*/;
			else
			{
				String dir = "N";
				if(fromx < tox) dir = "E";
				else if(fromx > tox) dir = "W";
				else if(fromy > toy) dir = "S";
				double[][] throughput2;
				if(isReturnLeg) throughput2 = tripkmpersquareby_expandedmode_direction_leg.get("2"+dir+mstr);
				else throughput2 = tripkmpersquareby_expandedmode_direction_leg.get("1"+dir+mstr);
				throughput2[tox][toy] += distkm/2;
				throughput2[fromx][fromy] += distkm/2;
			}
			 
			
			//back up a node in the path and keep going until we get to the root of the tree
			GraphNode prevnode = lastedge.leadsfrom;
			destid = prevnode.getId();
			lastedge = paths.get(prevnode.getId());
		}
		while(lastedge != null);
		
		if(currentRev > 0) //save what we had when we exited the loop
			revenueByMode.put(currentmodestr, revenueByMode.get(currentmodestr)+currentRev);

		//we only count number of trips on the first leg because we only
		//want the number of 1-way trips
		if(!isReturnLeg) for(String mstr : modesUsed)
			tripsbymodebyorigin.get(mstr)[origx][origy] += ntrips;

		return pt_transfers;
		
	}
	

	public static void main(String[] args) throws Exception
	{
		//Debug.setVerbosityLevel(Debug.EXTRA_INFO);
		Debug.setVerbosityLevel(Debug.INFO);
		Debug.setDebugLevel(Debug.DEBUG_SUSPICIOUS);
		
		Debug.println("Instantiating RTran Main", Debug.INFO);
		Main unsw = new Main("UserInput.xlsx");
		Debug.println("Done instantiating RTran Main, starting simulation", Debug.INFO);
		
		//need to run a few times to make sure that destinations all receive their fair share of trips
		unsw.runSimulation(true);
		System.exit(0); //clean up GUI resources

		
	}
	
	

}
