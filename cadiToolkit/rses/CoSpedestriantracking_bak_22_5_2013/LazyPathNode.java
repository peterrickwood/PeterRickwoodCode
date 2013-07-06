package rses.CoSpedestriantracking;

import java.nio.channels.UnsupportedAddressTypeException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import rses.Debug;
import rses.math.GraphEdge;
import rses.math.GraphNode;

public class LazyPathNode extends GraphNode 
{	
	private int lati;
	private int loni;
	private int orientation;
	private int speed;
	private int time;
	
	private static int lasttimestep;
	private static PMF prior;
	private static PMF[] sentinels;
	private static Map<String, LazyPathNode> alreadyCreated;
	
	
	public static double slowprob;
	public static double fastprob;
	public static double orientchangeprob;
	public static double speedchangeprob;
	public static double slowStationaryProb;
	public static double fast1stepProb;
	
	
	public static void init(Map<String, String> keysvals)
	{
		slowprob = Double.parseDouble(keysvals.get("SlowProb"));
		fastprob = Double.parseDouble(keysvals.get("FastProb"));
		orientchangeprob = Double.parseDouble(keysvals.get("OrientChangeProb"));
		speedchangeprob = Double.parseDouble(keysvals.get("SpeedChangeProb"));
		slowStationaryProb = Double.parseDouble(keysvals.get("SlowStationaryProb"));
		fast1stepProb = Double.parseDouble(keysvals.get("FastOneStepProb"));
	}
	

	/**
	 * 
	 * @param lati
	 * @param loni
	 * @param orientation  A value between 0 and 7 (inclusive), giving the orientations. 
	 *                     0 is north, and then clockwise from there
	 * @param speed A value between 0 and 1 (inclusive). 0 is slow, 1 is fast
	 * @param time An integer indicating the seconds from some starting time
	 * @param lasttimestep when the graph should end (all nodes in this time step have the same identifier)
	 */
	private LazyPathNode(String id, int lati, int loni, int orientation, int speed, int time)
	{
		super(id); 
		this.lati = lati;
		this.loni = loni;
		this.orientation = orientation;
		this.speed = speed;
		this.time = time;
	}
	
	static int maxt = 0;
	public static LazyPathNode getNode(int lati, int loni, int orientation, int speed, int time)
	{
		if(time > maxt) {
			maxt = time;
			Debug.println("Saw node at time "+maxt, Debug.INFO);
		}
		String id = "t "+time+" "+lati+" "+loni+" dir "+orientation+" sp "+speed;
		if(alreadyCreated.containsKey(id))
			return alreadyCreated.get(id);
		LazyPathNode newnode = new LazyPathNode(id, lati, loni, orientation, speed, time);
		alreadyCreated.put(id, newnode);
		return newnode;
	}
	
	
	public static GraphNode getSpecialStartNode(PMF prior, PMF[] sentinels, int lasttimestep, HashMap<String, LazyPathNode> existing)
	{
		if(LazyPathNode.alreadyCreated != null) throw new RuntimeException("You can only call getSpecialStartNode once!");
		LazyPathNode.prior = prior;
		LazyPathNode.sentinels = sentinels;
		LazyPathNode.lasttimestep = lasttimestep;
		LazyPathNode.alreadyCreated = existing;
		LazyPathNode.maxt = 0; //just for debugging purposes we use this variable to see how far we have expanded the graph
		
		GraphNode start = new GraphNode("START");
		int LATSIZE = prior.getDimensions()[0];
		int LONSIZE = prior.getDimensions()[1];
		
		//now for every speed and orientation and start position, add an edge
		for(int lati = 0; lati < LATSIZE; lati++)
			for(int loni = 0; loni < LONSIZE; loni++)
			{
				double lp = 0.0;
				if(sentinels[0] != null) lp = sentinels[0].getLogProbByIndices(lati, loni);
				else lp = prior.getLogProbByIndices(lati, loni);
				
				//all orientations are equally likely, so no need to update log prob there
				for(int orient = 0; orient < 8; orient++)
				{
					for(int speed = 0; speed <= 1; speed++)
					{
						if(speed == 0) lp += Math.log(slowprob); 
						else if(speed == 1) lp += Math.log(fastprob); 
						LazyPathNode actualstart = getNode(lati, loni, orient, speed, 0);
						start.addEdge(actualstart, -lp);
					}
				}
			}
		
		return start;
		
	}

	/*public static GraphNode getSpecialStartNode(int lati, int loni, PMF prior, PMF[] sentinels, int lasttimestep, HashMap<String, LazyPathNode> existing)
	{
		LazyPathNode.prior = prior;
		LazyPathNode.sentinels = sentinels;
		LazyPathNode.lasttimestep = lasttimestep;
		LazyPathNode.alreadyCreated = existing;
		
		GraphNode start = new GraphNode("START");
		
		//now for every speed and orientation and start position, add an edge
		double lp = 0.0;
		if(sentinels[0] != null) lp = sentinels[0].getLogProbByIndices(lati, loni);
		else lp = prior.getLogProbByIndices(lati, loni);

		//all orientations are equally likely, so no need to update log prob there
		for(int orient = 0; orient < 8; orient++)
		{
			for(int speed = 0; speed <= 1; speed++)
			{
				if(speed == 0) lp += Math.log(0.2); //50% chance of slow
				else if(speed == 1) lp += Math.log(0.5); //50% chance of fast
				LazyPathNode actualstart = getNode(lati, loni, orient, speed, 0);
				start.addEdge(actualstart, -lp);
			}
		}
		
		return start;
		
	}*/

	
	public boolean isLinkedTo(String nodeid)
	{
		for(GraphEdge edge : this.getEdges())
		{
			if(edge.leadsto.getId().equals(nodeid))
				return true;
		}
		return false;
		
	}
		
	
	
	private boolean firstCall = true;
	@Override
	public java.util.List<GraphEdge> getEdges()
	{
		//if getEdges has been called before, then we just return the edges
		if(!firstCall) return super.getEdges();
			
		//ok, we now add edges if it is the first time this is called
		firstCall = false;
		
		//special case. If we are in the final timestep, then there is just a single
		//edge going to the final state
		if(this.time == lasttimestep)
		{
			super.addEdge(new GraphNode("FINAL"), 0.0);
			Debug.println("Adding edge from "+getId()+" --> FINAL", Debug.EXTRA_INFO);
			return super.getEdges();
		}
		
		//otherwise, we need to go through and add edges with the edge weight being the
		//-log probability of the transition
		int LATSIZE = prior.getDimensions()[0];
		int LONGSIZE = prior.getDimensions()[1];
		
		for(int orientdelta = -1; orientdelta <= 1; orientdelta++) //can change by 45 degrees in a second
		{
			double logprob = 0.0;
			for(int speeddelta = -1; speeddelta <= 1; speeddelta++) //change speed by 1 slot in a second
			{
				if(speeddelta+speed < 0 || speeddelta+speed > 1) continue; //cant go outside set speeds
				
				if(orientdelta != 0) logprob += Math.log(orientchangeprob); 
				else logprob += Math.log(1-orientchangeprob);
				
				if(speeddelta != 0) logprob += Math.log(speedchangeprob); 
				else logprob += Math.log(1-speedchangeprob);
				
				//work out which 2 squares we can move to, and the -log prob of that move
				double lp0 = -logprob;
				int[] nextxyoffset0 = getxyForOrientation(orientation);
				double lp1 = -logprob;
				int[] nextxyoffset1 = new int[] {0,0}; //we can also stay at current position (the probability of doing this depends on speed)

				
				//if that next square is valid and we are at double speed, then we can go to another 
				//square. All the other speeds allow you to stay at the same square
				if(speed == 1 && 
				   nextxyoffset0[0]+lati >= 0 && nextxyoffset0[0]+lati < LATSIZE &&
				   nextxyoffset0[1]+loni >= 0 && nextxyoffset0[1]+loni < LONGSIZE &&
				   prior.getProbByIndices(lati+nextxyoffset0[0], loni+nextxyoffset0[1]) > 0)
				{
					nextxyoffset1 = getxyForOrientation(orientation);
					nextxyoffset1[0] += nextxyoffset0[0];
					nextxyoffset1[1] += nextxyoffset0[1];
				}

				//slow... stay put (40%) or move 1 (60%)
				if(speed == 0) { lp0 -= Math.log(0.6); lp1 -= Math.log(0.4); } 
				//(95% chance of 1 square, 5% of two)
				else if(speed == 1) { lp0 -= Math.log(0.95); lp1 -= Math.log(0.05); }
				else throw new IllegalStateException("illegal speed of "+speed);
				
				
				//ok, so we now have the probability of changing to the new speed, the
				//probability of changing to the new orientation, and the probability
				//of moving to each of the next 2 possible starting positions.
				//
				//so add the edges!!!
				if(nextxyoffset0 != null) //can be null if we are stationary
					addEdge(nextxyoffset0[0], nextxyoffset0[1], orientdelta, speeddelta, lp0);
				addEdge(nextxyoffset1[0], nextxyoffset1[1], orientdelta, speeddelta, lp1);
			}			
		}
		
		
		return super.getEdges();
	}

	
	private void addEdge(int latdelta, int londelta, int orientdelta, int speeddelta, double minuslogp)
	{
		int LATSIZE = prior.getDimensions()[0];
		int LONSIZE = prior.getDimensions()[1];
		
		int neworient = orientdelta+orientation;
		if(neworient < 0) neworient = 8+neworient;
		neworient = neworient % 8;
		
		int newspeed = speed+speeddelta;
		if(newspeed < 0 || newspeed > 1) return; //dont add this edge, illegal
		
		if(latdelta+lati >= 0 && londelta+lati < LATSIZE &&
		   londelta+loni >= 0 && londelta+loni < LONSIZE &&
		   prior.getProbByIndices(lati+latdelta, loni+londelta) > 0)
		{
			
			LazyPathNode next = getNode(lati+latdelta, //next lat 
					loni+londelta, //next lon
					neworient, //next orientation 
					newspeed, //next speed
					time+1); //next timestep
					
			double edgecost = minuslogp;
			//ok, we have the next node, and we know the cost, except for 
			//any sentinel probability, so we include that if we have one
			if(sentinels[time+1] != null)
			{
				edgecost -= sentinels[time+1].getLogProbByIndices(lati+latdelta, loni+londelta);
			}

			//ok, edge cost now includes:
			//
			//the sentinel probability (if there is one) for where we end up
			//the likelihood of the change in speed
			//the likelihood of the change in orientation
			//the likelihood of the lat and long offsets given the (old) speed
			
			super.addEdge(next, edgecost);
			Debug.println("Adding edge from "+getId()+" --> "+next.getId()+" with cost "+edgecost, Debug.EXTRA_INFO);
		}
	}
	
	
	private static int[] getxyForOrientation(int orientation)
	{
		if(orientation == 0) return new int[] {1,0};
		else if(orientation == 1) return new int[] {1,1};
		else if(orientation == 2) return new int[] {0,1};
		else if(orientation == 3) return new int[] {-1,1};
		else if(orientation == 4) return new int[] {-1,0};
		else if(orientation == 5) return new int[] {-1,-1};
		else if(orientation == 6) return new int[] {0,-1};
		else if(orientation == 7) return new int[] {1,-1};
		else throw new IllegalStateException("Illegal Orientation of "+orientation);
	}

	
	
	public GraphNode copyWithoutEdges()
	{
		throw new UnsupportedOperationException();
	}

}
