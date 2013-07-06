package rses.CoSpedestriantracking;

import rses.Debug;



/* This doesnt track individual phones so much as try and extract information
 * about how many phones are around, where they are concentrated, etc.
 */
public class DensityMapping 
{

	
	
	
	
	
	
	public static void main(String[] args) throws Exception
	{
		Debug.setVerbosityLevel(Debug.INFO);
		//args[0] == observations file (receiverid, sourceid, time, dbm)
		//args[1] == observers file (id,lat,lon,height)
		//args[2] == gis bounds (minlat, minlon, maxlat, maxlon, latsteps,lonsteps)
		//args[3] == "noprior" or the name of a file which specifies the prior
		//args[4] == the 'tag'/name to use when saving the density statistics to file
		Main.initInfo inf = Main.initialize(args);
		String tag = args[4];
		
		//get a unique list of the sourceid's
		java.util.HashMap<String, java.util.List<RSSObservation>> srcid_to_obs = new java.util.HashMap<String, java.util.List<RSSObservation>>();
		for(int i =0; i < inf.rss.length; i++)
		{
			if(!srcid_to_obs.containsKey(inf.rss[i].sourceId))
				srcid_to_obs.put(inf.rss[i].sourceId, new java.util.ArrayList<RSSObservation>());
			srcid_to_obs.get(inf.rss[i].sourceId).add(inf.rss[i]);
		}
		
		Debug.println(srcid_to_obs.keySet().size()+" unique devices detected", Debug.IMPORTANT);
		Debug.println(inf.rss.length+" packets observed", Debug.IMPORTANT);
		
		
		PMF total = new PMF(inf.gis);
		
		//ok, now we solve for each source id
		for(String srcid : srcid_to_obs.keySet())
		{
			Debug.println("Solving for source "+srcid+" with "+srcid_to_obs.get(srcid).size()+" observations", Debug.IMPORTANT);
			CosProbModel m = new CosProbModel(inf.gis, 
					srcid_to_obs.get(srcid).toArray(new RSSObservation[srcid_to_obs.get(srcid).size()]), 
					inf.P0prior, 
					inf.prior);
			
			//for each sentinal probability, we add the final (smoothed) probability
			//we do this because we want to include the constraints that are included
			//for the constrained/smoothed probability estimates, but doing this for
			//every timestep means we would just be multi-counting some observations
			for(int i = 0; i < m.sentinalpmfs.length; i++) 
				total.add(m.finalpmfs[i]);
		}
		
		total.normalize();
		total.writeImage("Counts_"+tag);
	}
}
