package rses.CoSpedestriantracking;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import rses.Debug;
import rses.Model;
import rses.inverse.UserFunctionHandle;
import rses.math.GraphNode;
import rses.math.GraphUtil;
import rses.spatial.GISLayer;
import rses.util.Util;



/** This class calculates the probability of a pedestrian's 'path' through space/time
 * given the observed RSS measurments that we have. 
 * 
 * 
 * @author peterr
 *
 */
public class CosProbModel 
{
	private rses.spatial.GISLayer geography;
	private RSSObservation[] observations;
	private Distribution P0prior;
	private PMF spatialPrior;
	
	private Map<String, PMF> decayByObserverMaps = null; //attenuation with distance
	private Map<String, PMF> detectByObserverMaps = null; //attenuation with distance
	private double sigma = Double.NaN; //noise
	private PMF[] sentinels = null;
	
	
	

	/**
	 * 
	 * @param geography
	 * @param observations
	 * @param P0prior dBm prior on signal strength at origin
	 * @param spatialPrior 
	 * @param pathlengths
	 * @param pathsteps
	 * @param pathdistances[a][b][c][d] The physical distance from [a,b] to [c,d]
	 * @param pathtrees 	this is the matrix of shortest path trees for each origin. So, for example,
	 *                  shortestpathtrees[a][b] gives the shortest path tree from a,b to all other nodes.
	 * @param speeddist
	 * @param attenuation
	 * @param noise
	 * @throws java.io.IOException
	 */
	public CosProbModel(rses.spatial.GISLayer geography, RSSObservation[] observations, 
			Distribution P0prior,
			PMF spatialPrior, double[][][][] pathlengths, int[][][][] pathsteps, 
			float[][][][] pathdistances, PathNode[][] pathtrees,
			Distribution speeddist, 
			Map<String, PMF> attenuation,
			Map<String, PMF> detectprobmap,
			double noise,
			boolean quickMode) 
	throws java.io.IOException
	{
		this.geography = geography;
		this.observations = observations;
		this.P0prior = P0prior;
		this.spatialPrior = spatialPrior;
		this.decayByObserverMaps = attenuation;
		this.detectByObserverMaps = detectprobmap;
		this.sigma = noise;
		
		//Make sure that observations are sorted by time, and all share the same ID
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_NORMAL))
			for(int i = 1; i < observations.length; i++) {
				if(observations[i].time.before(observations[i-1].time)) throw new RuntimeException("Observations must be sorted in chronological order!");
				if(!observations[i].sourceId.equals(observations[0].sourceId)) throw new RuntimeException("All observations must share the same source! "+observations[0].sourceId+" & "+observations[i].sourceId);
			}

		this.sentinels = this.solve(pathlengths, pathsteps, pathdistances, pathtrees, speeddist, quickMode);
		
	}
	
	private boolean gotSentinels = false;
	public PMF[] getSentinels()
	{
		if(gotSentinels) throw new RuntimeException("Trying to access sentinels twice, but this is not safe, as they have been got at before, and may have been modified");
		PMF[] res = this.sentinels;
		gotSentinels = true;
		return res;
	}
	
	
	private PMF[] solve(double[][][][] pathlengths, int[][][][] pathsteps, float[][][][] pathdistances, 
			PathNode[][] pathtrees, Distribution speeddist, boolean quickMode) 
	throws java.io.IOException
	{
		/* OK, we dont know P0, so we actually try and solve for this simultaneously with 
		 * position. We do this as follows:
		 * 
		 * Pick a bunch of 'trial' P0's and solve for x,y assuming P0 is known.
		 * Then, once we have solved for x,y, work out what that probability of observing our observations
		 * given the 'known' x,y and the 'trial' P0. 
		 * 
		 * We keep the P0 that maximizes the probability of observing the observations  
		 * 
		 */
		double[] P0s = new double[10000];
		for(int i = 0; i < P0s.length; i++)
			P0s[i] = P0prior.drawFrom(); //our trial P0's
		java.util.Arrays.sort(P0s);
		
		
		/* Now keep trialing P0's and find the one that best fits our observations
		 */
		
		class ufh implements rses.inverse.UserFunctionHandle 
		{
			RSSObservation[] rssobs = null;
			double[][] bounds = null;
			PMF spatialP = null;
			double[][][][] pathlengths;
			int[][][][] pathsteps;
			float[][][][] pathdists;
			PathNode[][] pathtrees;
			Distribution speeddist;
			ufh(RSSObservation[] robs, double[] bounds, PMF spatialP, double[][][][] pathlengths, 
					int[][][][] pathsteps, float[][][][] pathdists, PathNode[][] pathtrees, Distribution speeddist) 
			{
				this.rssobs = robs;
				this.bounds = new double[][] {{bounds[0], bounds[1]}};
				this.spatialP = spatialP;
				this.pathlengths = pathlengths;
				this.pathdists = pathdists;
				this.pathtrees = pathtrees;
				this.speeddist = speeddist;
				this.pathsteps = pathsteps;
			}
			public double getPriorForModel(double[] model) { throw new UnsupportedOperationException(); }
			public String getParameterName(int pnum) { return "P0"; }
			public void setParameterName(int pnum, String s) { throw new UnsupportedOperationException(); }
			public int getDimensionOfModelSpace() { return 1; }
			public double getErrorForModel(double[] m) {
				try { 
					//you can include the prior on P0 or not.... not sure what is best here
					
					//include prior
					return  -Math.log(P0prior.pdf(m[0]))-getLogProb(CosProbModel.this.geography, this.rssobs, m[0], spatialP, pathlengths, pathsteps, pathdists, pathtrees, speeddist);
					
					//dont include it
					//return  -getLogProb(CosProbModel.this.geography, this.rssobs, m[0], spatialP); 
				}
				catch(java.io.IOException ioe) { throw new RuntimeException(ioe); }
			}
			public double[][] getBoundsOnModelSpace() { return bounds; }
		}
		
		
		/*rses.Model startMod = new rses.Model(new double[] {P0s[P0s.length/2]});
		rses.inverse.powell.PowellMin powellmin = new rses.inverse.powell.PowellMin(new ufh(observations, new double[] {P0s[0], P0s[P0s.length-1]}, spatialPrior, pathlengths, pathsteps, pathdistances, pathtrees, speeddist), new double[] {1.0},
		1.0, startMod, 0L);
		powellmin.setPrecision(0.01);
		powellmin.run();
		double bestP0 = powellmin.getBestModel().getModelParameter(0);
		
		Debug.println("Best P0 found via PowellMin is "+bestP0, Debug.INFO);
		*/
		
		double bestP0 = -30;
		Debug.println("REM: Best P0 set (hard-coded) at "+bestP0, Debug.INFO);

		/*OK, we know which P0 fits best, so we do a final estimate using that P0 
		 * 
		 * This is, strictly, a bit of a waste of time, because we should/could just cache the
		 * finalpmfs while we are estimating the best P0, above. But it really isn't a large saving
		 * in time so its just a bit cleaner to reestimate it here rather than pulling up the previous
		 * solution.
		 * 
		 */
		Debug.println("Starting final estimation run with P0="+bestP0+"...", Debug.INFO);

		PMF[] sentinels = null;
		if(quickMode) {
			boolean print = false;
			sentinels = getPMFs_noprojecting(geography, observations, bestP0, spatialPrior, print, pathdistances);
		}
		else
		{
			PMF[][] pmfs = getPMFs_proj(geography, observations, bestP0, spatialPrior, true, pathdistances);
			//the pmfs at each timestep, which are the combined projections of all sentinels
			PMF[] finalpmfs = pmfs[1]; 
			//the sentinel pmfs (i.e. the observations), AFTER having been adjusted by the projections from the other sentinel pmfs.
			sentinels = pmfs[0]; 

			writePath("ML_path_raw.kml", finalpmfs, observations[0].time);		
			
			HashMap<String, LazyPathNode> existing = new HashMap<String, LazyPathNode>();
			
			//the choice here is to either use the sentinel probabilities, or to use the 
			//smoothed/projected probabilities (that already contain information about speed)
			//GraphNode falsestart = LazyPathNode.getSpecialStartNode(spatialPrior, finalpmfs, finalpmfs.length-1, existing);
			GraphNode falsestart = LazyPathNode.getSpecialStartNode(spatialPrior, sentinels, sentinels.length-1, existing);
			
			GraphNode shortestPathTree = rses.math.GraphUtil.getShortestPathTree(falsestart, "FINAL");
			//GraphUtil.printGraph(shortestPathTree, System.out);
			//ok, print out the shortest path to FINAL
			java.util.List<GraphNode> path = rses.math.GraphUtil.getShortestPathNodesFromPathTree(shortestPathTree, "FINAL");
			Debug.println("finalpmf length is "+finalpmfs.length, Debug.INFO);
			int count = 0;
			for(GraphNode n : path)
			{
				if(n.getId().equals("FINAL") || n.getId().equals("START"))
					continue;
				String[] bits = Util.getWords(n.getId());
				int time = Integer.parseInt(bits[1]);
				int lat = Integer.parseInt(bits[2]);
				int lon = Integer.parseInt(bits[3]);
				Debug.println("Setting final pmf "+count+" to be "+lat+" "+lon, Debug.INFO);
				finalpmfs[count++] = new PMF(geography, lat, lon);
			}

			writePath("ML_path_djikstra.kml", finalpmfs, observations[0].time);		
		}		

		
		return sentinels;
	}

	

	
	
	
	public static void writePath(String kmlfilename, PMF[] pmfs, java.util.Calendar startTime)
	throws java.io.IOException
	{
		java.util.Date[] dates0 = new java.util.Date[pmfs.length];
		double[] lats0 = new double[pmfs.length];
		double[] lons0 = new double[pmfs.length];
		int count = 0;
		
		for(int t = 0; t < pmfs.length; t++)
		{
			if(pmfs[t] == null)
				continue;
			else
				count++;
		
			//probably dont need to be this defensive, but performance is not an issue and I dont 
			//want to work out how safe it is to just clone.
			java.util.Calendar cal = Calendar.getInstance();
			cal.setTime((java.util.Date) startTime.getTime().clone());
			cal.add(java.util.Calendar.SECOND, t);
			dates0[t] = (java.util.Date) cal.getTime().clone();
			
			double[] latlon = pmfs[t].getMLlatlon();
			lats0[t] = latlon[0];
			lons0[t] = latlon[1];
		}
		
		//copy only the bits we have estimates for
		java.util.Date[] dates = new java.util.Date[count];
		double[] lats = new double[count];
		double[] lons = new double[count];
		count = 0;
		for(int i = 0; i < dates0.length; i++)
		{
			if(dates0[i] != null) {
				dates[count] = dates0[i];
				lats[count] = lats0[i];
				lons[count++] = lons0[i];
			}
		}

		
		
		rses.spatial.util.GoogleEarthUtils.writePathToKML(kmlfilename, dates, lats, lons);
	}
	
	

	/** Get the log-prob of observing 'observations' given a P0 value of P0
	 * 
	 * We do this by solving for location, and then working out likelihood of observing each observation given P0.
	 * 
	 * @param geography
	 * @param observations
	 * @param P0
	 * @return
	 * @throws java.io.IOException
	 */
	private double getLogProb(rses.spatial.GISLayer geography, RSSObservation[] observations, double P0, 
			PMF spatialPrior, double[][][][] pathlengths, int[][][][] pathsteps, float[][][][] pathdistances, PathNode[][] pathtrees,
			Distribution speeddist) throws java.io.IOException
	{
		Debug.println("Getting logprob for P0 "+P0, Debug.IMPORTANT);

		//first solve for location
		PMF[][] pmfsAndsentinels = getPMFs_proj(geography, observations, P0, spatialPrior, false, pathdistances);
		PMF[] proj = pmfsAndsentinels[1];
		PMF[] sentinels = pmfsAndsentinels[0];
		
			
		//now find likelihood of observations given these locations and hypothesized P0
		double logprob = 0.0;
		for(int obs = 0; obs < observations.length; obs++)
		{
			double prob = 0.0;
			int t = (int) ((observations[obs].time.getTimeInMillis()-observations[0].time.getTimeInMillis())/1000);
			if(sentinels[t] == null)
				throw new RuntimeException("No sentinel at spot where there should be one (t="+t+")");
			//get the probability of observing the observation
			for(double lat = geography.getMinLat()+geography.getLatStepSize()/2; lat < geography.getMaxLat(); lat += geography.getLatStepSize())
				for(double lon = geography.getMinLong()+geography.getLongStepSize()/2; lon < geography.getMaxLong(); lon += geography.getLongStepSize())
				{
					//first get probability of observing signal at this lat/lon
					double p = getConditionalProbabilityOfObservedSignalStrength_decayMapBased(geography, observations[obs], P0, lat, lon);
					if(true) throw new RuntimeException("this code shouldnt be used because we need to include the probabilities of NOT observing the signal (i.e. we dont observe the signal at some stations, and this itself is information about the likely location of the source. We DO include that in the getPMFs function, so look there for how to do it, but its not implemented in this function yet)");
					
					//Arent we double counting here? Not really. While it is true that we already
					//use the conditional probability while calculating pmfs, we do a bunch of other stuff too.
					//Whatever we do, we somehow get a probability distribution over space at each sentinel
					//timestep. What we are doing here is calculating the likelihood of getting this PMF
					//given this particular P0.
					//
					//So, it is not double-counting. We estimate the spatial probabilities (somehow),
				    //so we know the probability of being at x,y at time t. And we then just multiply that by
					//the probability of observing the signal strength we did at the x,y at time t.
					p *= sentinels[t].getProb(lat, lon);
					prob += p;
				}
			logprob += Math.log(prob);
		}
		return logprob;
	}
	
	
	/** Get the pmfs for each time step given observations and assumed/known P0
	 * 
	 * @param geography
	 * @param observations
	 * @param P0
	 * @param writeResults
	 * @return
	 * @throws java.io.IOException
	 */
	private PMF[] getPMFs_noprojecting(rses.spatial.GISLayer geography, RSSObservation[] observations, double P0, 
			PMF spatialPrior, boolean writeResults, float[][][][] pathdistances) 
	throws java.io.IOException
	{
		long starttime = observations[0].time.getTimeInMillis()/1000;
		long endtime = observations[observations.length-1].time.getTimeInMillis()/1000;

		//create a PMF for each observation (maps index in observations array to the PMF for that observation)
		java.util.Map<Integer, PMF> pmfs = new java.util.HashMap<Integer, PMF>();
		for(int i = 0; i < observations.length; i++)
		{
			//create pmf from observation
			PMF pmf = this.getProbabilityMap(geography, observations[i], P0);
						
			if(writeResults) {
				int timet = ((int)(observations[i].time.getTimeInMillis()/1000 - starttime));
				pmf.writeImage("_unconstrainedFromObs_"+observations[i].observer.id+"_t"+timet);
				pmf.printToFile("unconstrainedFromObs_"+observations[i].observer.id+"_t"+timet+".txt");
			}
			
			//pmf.print();
			pmfs.put(i, pmf);
			
		}
		
		//OK, we have PMFs at each of the 'snapshot/observation' points. What
		//we would like to do now is get a PMF for every timestep (regardless of 
		//whether we have an observation), that also takes into account some constraint
		//on how fast people can move. 
		
		//but before we do any of that, we 'combine' any observations that occur
		//within the same second. We do that in this loop
		PMF[] pmfbytimestep = new PMF[(int) (endtime-starttime+1)];
		boolean[] normalSignalReceived = new boolean[pmfbytimestep.length];
		Map<Integer, Set<String>> seenBy = new HashMap<Integer, Set<String>>();
		for(Integer i : pmfs.keySet()) //for each snapshot pmf
		{
			int t = (int) (observations[i].time.getTimeInMillis()/1000 - starttime);
			if(!observations[i].fromPacketInjection)
				normalSignalReceived[t] = true;
			
			if(pmfbytimestep[t] == null) 
			{
				pmfbytimestep[t] = pmfs.get(i);
				if(seenBy.get(t) == null) seenBy.put(t, new java.util.HashSet<String>());
				if(seenBy.get(t-1) == null) seenBy.put(t-1, new java.util.HashSet<String>());
				if(seenBy.get(t+1) == null) seenBy.put(t+1, new java.util.HashSet<String>());
			}
			else
			{
				PMF pmf = pmfs.get(i);
				
				//The big question, whether to add or multiply probabilities....
				//Bayes really says we should multiply, *but* not clear that this works
				//better than averaging. 
				
				//pmfbytimestep[t].add(pmf);
				pmfbytimestep[t].multiply(pmf, false);
			}

			Debug.println("Adding "+observations[i].observer.id+" to seenBy set at times "+(t-1)+" "+t+" "+(t+1), Debug.INFO);
			seenBy.get(t-1).add(observations[i].observer.id);
			seenBy.get(t).add(observations[i].observer.id);
			seenBy.get(t+1).add(observations[i].observer.id);
		}
		
		//go and include in the information we get from the fact that some
		//stations DID or DID NOT observe the signal
		for(int t = 0; t < pmfbytimestep.length; t++)
		{
			if(pmfbytimestep[t] == null) continue;
			if(!normalSignalReceived[t]) continue; //we only got packet injected packets here
			
			if(writeResults)
			{
				pmfbytimestep[t].writeImage("_unconstrainedFromAllObs_t"+t);
				pmfbytimestep[t].printToFile("unconstrainedFromAllObs_t"+t+".txt");
			}

			Debug.println("Seen by values for "+t+" include: ", Debug.INFO);
			for(String s : seenBy.get(t))
				Debug.println("    "+s, Debug.INFO);
				
			
			Set<String> observers = decayByObserverMaps.keySet();
			for(String obs : observers) 
			{
				if(seenBy.get(t).contains(obs)) {
					Debug.println("Taking into account the fact that "+obs+" saw a signal at t="+t, Debug.INFO);
					pmfbytimestep[t].multiply(detectByObserverMaps.get(obs), false);
				}
				else {
					//if we didnt see it, that in itself is some information about location
					Debug.println("Taking into account the fact that "+obs+" DIDNT see a signal at t="+t, Debug.INFO);
					pmfbytimestep[t].multiply(detectByObserverMaps.get(obs).not(), false);
				}
			}
						
			if(writeResults) {
				pmfbytimestep[t].writeImage("_unconstrainedFromObs_withDetectInfo_t"+t);
				pmfbytimestep[t].printToFile("unconstrainedFromObs_withDetectInfo_t"+t+".txt");
			}

		}
		

		
		
		//normalize the probabilities of the 'sentinel' pmfs (i.e. the ones 
		//we have actual data for)
		int numsentinels = 0;
		for(int t = 0; t < pmfbytimestep.length; t++)
		{
			if(pmfbytimestep[t] != null)
			{
				numsentinels++;				
				
				//We add in any 'prior' knowledge here. Adding it sooner means that
				//a non-uniform prior has more weight (because it is added to each
				//observation rather than each timestep). 
				//
				//We also HAVE to add it here (and not later), because we are
				//normalizing here, and we need to apply our prior before normalizing
				pmfbytimestep[t].multiply(spatialPrior, true);
								
				if(writeResults) pmfbytimestep[t].writeImage("sentinel_t"+t);
			}
		}
		
		return pmfbytimestep;

	}

	private PMF[][] getPMFs_proj(rses.spatial.GISLayer geography, RSSObservation[] observations, double P0, 
			PMF spatialPrior, boolean writeResults, float[][][][] pathdistances) 
	throws java.io.IOException
	{
		long starttime = observations[0].time.getTimeInMillis()/1000;
		long endtime = observations[observations.length-1].time.getTimeInMillis()/1000;

		//create a PMF for each observation (maps index in observations array to the PMF for that observation)
		java.util.Map<Integer, PMF> pmfs = new java.util.HashMap<Integer, PMF>();
		for(int i = 0; i < observations.length; i++)
		{
			//create pmf from observation
			PMF pmf = this.getProbabilityMap(geography, observations[i], P0);
						
			if(writeResults) {
				int timet = ((int)(observations[i].time.getTimeInMillis()/1000 - starttime));
				pmf.writeImage("_unconstrainedFromObs_"+observations[i].observer.id+"_t"+timet);
				pmf.printToFile("unconstrainedFromObs_"+observations[i].observer.id+"_t"+timet+".txt");
			}
			
			//pmf.print();
			pmfs.put(i, pmf);
			
		}
		
		//OK, we have PMFs at each of the 'snapshot/observation' points. What
		//we would like to do now is get a PMF for every timestep (regardless of 
		//whether we have an observation), that also takes into account some constraint
		//on how fast people can move. 
		
		//but before we do any of that, we 'combine' any observations that occur
		//within the same second. We do that in this loop
		PMF[] pmfbytimestep = new PMF[(int) (endtime-starttime+1)];
		Map<Integer, Set<String>> seenBy = new HashMap<Integer, Set<String>>();
		for(Integer i : pmfs.keySet()) //for each snapshot pmf
		{
			int t = (int) (observations[i].time.getTimeInMillis()/1000 - starttime);
			if(pmfbytimestep[t] == null) 
			{
				pmfbytimestep[t] = pmfs.get(i);
				if(seenBy.get(t) == null) seenBy.put(t, new java.util.HashSet<String>());
				if(seenBy.get(t-1) == null) seenBy.put(t-1, new java.util.HashSet<String>());
				if(seenBy.get(t+1) == null) seenBy.put(t+1, new java.util.HashSet<String>());
			}
			else
			{
				PMF pmf = pmfs.get(i);
				
				//The big question, whether to add or multiply probabilities....
				//Bayes really says we should multiply, *but* not clear that this works
				//better than averaging. 
				
				//pmfbytimestep[t].add(pmf);
				pmfbytimestep[t].multiply(pmf, false);
			}

			Debug.println("Adding "+observations[i].observer.id+" to seenBy set at times "+(t-1)+" "+t+" "+(t+1), Debug.INFO);
			seenBy.get(t-1).add(observations[i].observer.id);
			seenBy.get(t).add(observations[i].observer.id);
			seenBy.get(t+1).add(observations[i].observer.id);
		}
		
		//go and include in the information we get from the fact that some
		//stations DID or DID NOT observe the signal
		for(int t = 0; t < pmfbytimestep.length; t++)
		{
			if(pmfbytimestep[t] == null) continue;
			
			if(writeResults)
			{
				pmfbytimestep[t].writeImage("_unconstrainedFromAllObs_t"+t);
				pmfbytimestep[t].printToFile("unconstrainedFromAllObs_t"+t+".txt");
			}
			
			Debug.println("Seen by values for "+t+" include: ", Debug.INFO);
			for(String s : seenBy.get(t))
				Debug.println("    "+s, Debug.INFO);
				
			
			Set<String> observers = decayByObserverMaps.keySet();
			for(String obs : observers) 
			{
				if(seenBy.get(t).contains(obs)) {
					Debug.println("Taking into account the fact that "+obs+" saw a signal at t="+t, Debug.INFO);
					pmfbytimestep[t].multiply(detectByObserverMaps.get(obs), false);
				}
				else {
					//if we didnt see it, that in itself is some information about location
					Debug.println("Taking into account the fact that "+obs+" DIDNT see a signal at t="+t, Debug.INFO);
					pmfbytimestep[t].multiply(detectByObserverMaps.get(obs).not(), false);
				}
			}
						
			if(writeResults) {
				pmfbytimestep[t].writeImage("_unconstrainedFromObs_withDetectInfo_t"+t);
				pmfbytimestep[t].printToFile("unconstrainedFromObs_withDetectInfo_t"+t+".txt");
			}

		}
		

		
		
		//normalize the probabilities of the 'sentinel' pmfs (i.e. the ones 
		//we have actual data for)
		int numsentinels = 0;
		for(int t = 0; t < pmfbytimestep.length; t++)
		{
			if(pmfbytimestep[t] != null)
			{
				numsentinels++;				
				
				//We add in any 'prior' knowledge here. Adding it sooner means that
				//a non-uniform prior has more weight (because it is added to each
				//observation rather than each timestep). 
				//
				//We also HAVE to add it here (and not later), because we are
				//normalizing here, and we need to apply our prior before normalizing
				pmfbytimestep[t].multiply(spatialPrior, true);
								
				if(writeResults) pmfbytimestep[t].writeImage("sentinel_t"+t);
			}
		}

		
		
		//OK, now for each 'sentinel probability, we project it forward and backward until to becomes
		//uniform (un-informative)
		Debug.println("Projecting PMFs...", Debug.INFO);
		PMF[][] projectedpmfs = new PMF[numsentinels][pmfbytimestep.length];
		int scount = 0;
		for(int t0 = 0; t0 < pmfbytimestep.length; t0++)
		{
			if(pmfbytimestep[t0] != null)
			{
				//now project it forward/backward
				Debug.println("Projecting from t0="+t0, Debug.IMPORTANT);
				int maxtimestepstoproject = Math.max(spatialPrior.getDimensions()[0], spatialPrior.getDimensions()[1])*2;
				maxtimestepstoproject = Math.min(maxtimestepstoproject, pmfbytimestep.length);
				double[] speeds = new double[] {1.40000, 2.00001, 4.00001};
				PMF[] pmfdissapate = pmfbytimestep[t0].project(maxtimestepstoproject, spatialPrior, pathdistances, speeds);
				//PMF[] pmfdissapate = pmfbytimestep[t0].project_new(maxtimestepstoproject, spatialPrior, pathdistances, speedprobs);				
				
				for(int toffset = 0; toffset < pmfdissapate.length; toffset++) 
				{
					if(t0+toffset < pmfbytimestep.length)
						projectedpmfs[scount][t0+toffset] = pmfdissapate[toffset];
					if(t0-toffset >= 0)
						projectedpmfs[scount][t0-toffset] = pmfdissapate[toffset];
					
					//if(writeResults) pmfdissapate[toffset].writeImage("proj_from_"+t0+"_delta_"+toffset);
				}
				scount++;
			}
		}
		
		//At this point we have projected all the sentinel probabilities
		//backwards and forwards, and we need to combine them all
		Debug.println("Combining projected PMFs into final PMFs...", Debug.INFO);
		PMF[] finalest = new PMF[pmfbytimestep.length];
		PMF priorcopy = new PMF(spatialPrior); //copy just in case
		for(int t = 0; t < pmfbytimestep.length; t++) //for each time-slice
		{
			//combine all the estimates from each sentinel
			PMF base = null;
			for(int sentinel = 0; sentinel < projectedpmfs.length; sentinel++)
			{
				if(projectedpmfs[sentinel][t] == null) continue;
				
				if(base == null) 
					base = projectedpmfs[sentinel][t]; 
				else //its clear that we should multiply here, not add, or else uniform probs take over
					base.multiply(projectedpmfs[sentinel][t], true); 
			}
			
			//done for time t!!!
			if(base == null)
				base = priorcopy; //if there were no projected PMFs, we just use the prior
			//Otherwise, if we did get some projected PMFs, but we dont have a sentinal PMF/observation
			//here, then we multiply once by the prior, because we need to 
			//take that into account. If there is a sentinel PMF, we dont need to multiply because
			//that has already been taken into account
			else if(pmfbytimestep[t] == null)
				base.multiply(spatialPrior, true);
			

			finalest[t] = base;
			if(pmfbytimestep[t] != null) //update sentinels to reflect pre/post constraints from projection 
				pmfbytimestep[t] = base;
				
			
			if(writeResults) base.writeImage("final_"+len5int(t));
			
			//work out maximum likelihood guess
			int[] xy = base.getML();
			System.out.println("Final ML est at time "+t+" is x= "+xy[1]+" y= "+xy[0]);
		}
		
			
		
		return new PMF[][] {pmfbytimestep, finalest}; 
		//return pmfbytimestep;
		
	}
	
	
	

	private PMF[][] getPMFs_DjikstraBased(rses.spatial.GISLayer geography, RSSObservation[] observations, double P0, 
			PMF spatialPrior, boolean writeResults, double[][][][] pathdistances) 
	throws java.io.IOException
	{
		long starttime = observations[0].time.getTimeInMillis()/1000;
		long endtime = observations[observations.length-1].time.getTimeInMillis()/1000;

		//create a PMF for each observation (maps index in observations array to the PMF for that observation)
		java.util.Map<Integer, PMF> pmfs = new java.util.HashMap<Integer, PMF>();
		for(int i = 0; i < observations.length; i++)
		{
			//create pmf from observation
			PMF pmf = this.getProbabilityMap(geography, observations[i], P0);
						
			if(writeResults) {
				int timet = ((int)(observations[i].time.getTimeInMillis()/1000 - starttime));
				pmf.writeImage("_unconstrainedFromObs_t"+timet);
				pmf.printToFile("unconstrainedFromObs_t"+timet+".txt");
			}
			
			//pmf.print();
			pmfs.put(i, pmf);
			
		}
		
		//OK, we have PMFs at each of the 'snapshot/observation' points. What
		//we would like to do now is get a PMF for every timestep (regardless of 
		//whether we have an observation), that also takes into account some constraint
		//on how fast people can move. 
		
		//but before we do any of that, we 'combine' any observations that occur
		//within the same second. We do that in this loop
		PMF[] pmfbytimestep = new PMF[(int) (endtime-starttime+1)];
		for(Integer i : pmfs.keySet()) //for each snapshot pmf
		{
			int t = (int) (observations[i].time.getTimeInMillis()/1000 - starttime);
			if(pmfbytimestep[t] == null)
				pmfbytimestep[t] = pmfs.get(i);
			else
			{
				PMF pmf = pmfs.get(i);
				
				//The big question, whether to add or multiply probabilities....
				//Bayes really says we should multiply, *but* not clear that this works
				//better than averaging in practice. 
				
				//pmfbytimestep[t].add(pmf);
				pmfbytimestep[t].multiply(pmf, false);
			}
		}
		
		//normalize the probabilities of the 'sentinel' pmfs (i.e. the ones 
		//we have actual data for)
		int numsentinels = 0;
		for(int t = 0; t < pmfbytimestep.length; t++)
		{
			if(pmfbytimestep[t] != null)
			{
				numsentinels++;				
				
				//We add in any 'prior' knowledge here. Adding it sooner means that
				//a non-uniform prior has more weight (because it is added to each
				//observation rather than each timestep). 
				//
				//We also HAVE to add it here (and not later), because we are
				//normalizing here, and we need to apply our prior before normalizing
				pmfbytimestep[t].multiply(spatialPrior, true);
								
				if(writeResults) pmfbytimestep[t].writeImage("t"+t);
			}
		}

		
		
		//OK, now for each 'sentinel probabilities		
		//TODO implement a shortest path search that takes into account orientation and speed
		//and 
		
		
		return null /*pmfbytimestep*/;
		
	}
	

	
	
	
	
	
	
	
	
	
	public static String len5int(int x)
	{
		if(x < 0) throw new RuntimeException();
		if(x < 10) return "0000"+x;
		if(x < 100) return "000"+x;
		if(x < 1000) return "00"+x;
		if(x < 10000) return "0"+x;
		if(x < 100000) return ""+x;
		throw new RuntimeException();
	}



	
	
	
	

   /**
    Get a 2-d probability mass function, expressed as an array,
    where:
        pmf[x][y] is the probability of observing Pobs at receiver ri given P0,alpha,sigma, and
        pmf[x+1][y] is the probability of same at minx+xstep,miny 
        pmf[x][y] is the probability of same at minx,miny+ystep
        etc 
    */
	private PMF getProbabilityMap(rses.spatial.GISLayer geography, RSSObservation obs, double P0)
	{
		PMF pmf = new PMF(geography);
		
		for(double lat = geography.getMinLat()+geography.getLatStepSize()/2; lat < geography.getMaxLat(); lat += geography.getLatStepSize())
		{
			for(double lon = geography.getMinLong()+geography.getLongStepSize()/2; lon < geography.getMaxLong(); lon += geography.getLongStepSize())
			{
				double pval = getConditionalProbabilityOfObservedSignalStrength_decayMapBased(geography, obs, P0, lat, lon);
				//double pval = getConditionalProbabilityOfObservedSignalStrength_detectMapBased(geography, obs, P0, lat, lon);
				pmf.setValue(lat, lon, pval);
			}
		}
		pmf.normalize();
		return pmf;
	}
	

	private double getConditionalProbabilityOfObservedSignalStrength_decayMapBased(
			rses.spatial.GISLayer geography, 
			RSSObservation obs, double P0, double lat, double lon)
	{
		Observer observer = obs.observer;
		
		PMF decayMap = this.decayByObserverMaps.get(observer.id);
		double decay = decayMap.getProb(lat, lon);
		
	    //Debug.println("Distance from "+observer.id+" to "+lat+" "+lon+" is "+distance, Debug.INFO);
	    double pred = P0 - decay;
	    //Debug.println("Predicted signal strength at this distance is "+pred, Debug.INFO);
	    double actual = obs.ss;
	    //Debug.println("Actual observed signal strength at this distance is "+actual, Debug.INFO);
	    double pd = rses.math.MathUtil.getGaussPdf(pred-actual, 0, sigma);
	    //Debug.println("probability for this is "+pd, Debug.INFO);

	    return pd;
	}

	
	private double getConditionalProbabilityOfObservedSignalStrength_detectMapBased(
			rses.spatial.GISLayer geography, 
			RSSObservation obs, double P0, double lat, double lon)
	{
		Observer observer = obs.observer;
		
		double loss = P0 - obs.ss;
		double crowfliesdist = dist(lat, lon, observer.lat, observer.lon, observer.z_metres);
		
		//if loss < 0 then within 25m
		if(loss < 0 && crowfliesdist > 25)
			return 0.0;
		//if the loss is less than 10 then we assume within 50m
		if(loss < 10 && crowfliesdist > 50)
			return 0.0;
		
	    double pd = detectByObserverMaps.get(observer.id).getProb(lat, lon);
	    
	    return pd;
	}

	
	
	public static double dist(double lat1, double lon1, double lat2, double lon2, double hdiff)
	{
	    double distance = rses.math.MathUtil.getDistanceBetweenPointsOnEarth(lat1, lon1, lat2, lon2);
	    distance = Math.sqrt(hdiff*hdiff+distance*distance);
		return distance;
	}

	
	private static double getDecaydBm(rses.spatial.GISLayer geography, 
			Observer observer, double lat, double lon, 
			double alpha) //alpha is the decay in dbm per meter
	{
	    //get the distance in metres from the receiver to proposed deltax and deltay
	    double distance = rses.math.MathUtil.getDistanceBetweenPointsOnEarth(observer.lat, observer.lon, lat, lon);
	    distance = Math.sqrt(distance*distance+observer.z_metres*observer.z_metres);

	    double decay = 10*alpha*Math.log10(distance);
	    return decay;
	}
	
	/*THE OLD SIMPLE CONDITIONAL PROBABILITY MAP
	 private double getConditionalProbabilityOfObservedSignalStrength_simple(rses.spatial.GISLayer geography, 
			RSSObservation obs, double P0, double lat, double lon, 
			double alpha) //alpha is the decay in dbm per meter
	{
	    
	    //Debug.println("Distance from "+observer.id+" to "+lat+" "+lon+" is "+distance, Debug.INFO);
	    double pred = P0 - getDecaydBm(geography, obs.observer, lat, lon, alpha);
	    //Debug.println("Predicted signal strength at this distance is "+pred, Debug.INFO);
	    double actual = obs.ss;
	    //Debug.println("Actual observed signal strength at this distance is "+actual, Debug.INFO);
	    double pd = rses.math.MathUtil.getGaussPdf(pred-actual, 0, sigma);
	    //Debug.println("probability for this is "+pd, Debug.INFO);
	    return pd;

	}*/

	public static void usage()
	{
		System.err.println("Usage reminder:");
		System.err.println("");
		System.err.println("java rses.CoSpedestriantracking.CosProbModel BOUNDS SPATIALPRIOR DECAYALPHA OBSERVERS");
		System.err.println("");
		System.err.println("where:");
		System.err.println("");
		System.err.println("  BOUNDS is a file with one line of the form \"MINLAT MINLONG MAXLAT MAXLON STEPSIZEM\" where the first 4 specify the bounding box of the region, and the last specifies the size (in meters) of each grid cell");
		System.err.println("  ");
		System.err.println("  SPATIALPRIOR is either 'noprior' (in which case a uniform prior is used) or else is a spatial prior. This is used only to set the decay to infinity on unreachable squares (i.e. squares where the prior is 0)");
		System.err.println("  ");
		System.err.println("  DECAYALPHA is the decay in signal strength (in dBm/m). A value of 2-3 for outdoors is not unreasonable, but pick whatever you like....");
		System.err.println("  ");
		System.err.println("  OBSERVERS is a file of the observers (i.e. receiving stations). Each line in the file is of the form \"ID lat lon z\" where ID is the unique ID of the receiver, lat/lon is the location of the receiver, and z is the height (above ground level) of the receiver");
		System.err.println("  ");
		System.err.println("  ");
		System.err.println("  ");
		System.err.println("  ");
	}
	
	//used to create 'simple' decay maps that are just a function of distance
	public static void main(String[] args) throws Exception
	{
		usage();
		
		GISLayer geog = Main.getGISLayer(args[0], "noname");
		PMF spatialPrior = new PMF(geog, true);
		if(!args[1].equalsIgnoreCase("noprior"))
			spatialPrior = PMF.read(args[1], geog);
		double decayAlpha = Double.parseDouble(args[2]);
		Observer[] obs = Main.readObservers(args[3]);
		
		//ok, now generate a decay map from each observer
		for(Observer o : obs)
		{
			PMF decayMap = new PMF(geog, false);
			for(double lat = geog.getMinLat()+geog.getLatStepSize()/2; lat < geog.getMaxLat(); lat+=geog.getLatStepSize())
				for(double lon = geog.getMinLong()+geog.getLongStepSize()/2; lon < geog.getMaxLong(); lon+=geog.getLongStepSize())
				{
					if(spatialPrior.getProb(lat, lon) < 0.0) throw new RuntimeException("Spatial prior at "+lat+","+lon+" less than zero?!");
					else if(spatialPrior.getProb(lat, lon) == 0.0) decayMap.setValue(lat, lon, Double.POSITIVE_INFINITY);
					else {
						double val = getDecaydBm(geog, o, lat, lon, decayAlpha);
						decayMap.setValue(lat, lon, val);
					}
				}
			decayMap.printToFile("decayMap_"+o.id+".txt");
		}		
	}


}
