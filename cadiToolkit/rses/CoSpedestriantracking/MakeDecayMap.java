package rses.CoSpedestriantracking;

import rses.Debug;
import rses.math.MathUtil;
import rses.spatial.GISLayer;

public class MakeDecayMap 
{
	
	
	public static double decayFunc(double dist, boolean hasLineOfSight)
	{
		return decayFunc(dist, hasLineOfSight, false);
	}

	/** 
	 * 
	 * @param dist
	 * @param hasLineOfSight
	 * @param inverse If true, returns the effective distance (rather than the expected signal strength)
	 * @return
	 */
	private static double decayFunc(double dist, boolean hasLineOfSight, boolean inverse)
	{
		return decayFunc_fittedcombined(dist, hasLineOfSight, inverse);
	}
	
	private static double decayFunc_theory(double dist, boolean hasLineOfSight, boolean inverse)
	{
		double alpha = 2.0;
		double fixedloss = 10;
		double wallloss = 15;
		
		double decay = fixedloss+10*alpha*rses.util.Util.log_n(dist, 10.0);
		if(!hasLineOfSight) decay += wallloss;
		if(!inverse)
			return decay;
		
		//if in inverse mode we return the effective distance
		if(hasLineOfSight) throw new RuntimeException("Asking for effective distance for a given decay only really makes sense for locations without line of sight");
		double effectivedist = Math.pow(10, (decay-fixedloss)/(10*alpha));
		return effectivedist;
		
	}



	//fitted on data over both calibration days (16th and 30th May)
	private static double decayFunc_fittedcombined(double dist, boolean hasLineOfSight, boolean inverse)
	{
		double alpha = 0.88;
		double fixedloss = 5.1;
		double wallloss = 12;
		
		double decay = fixedloss+10*alpha*rses.util.Util.log_n(dist, 10.0);
		if(!hasLineOfSight) decay += wallloss;
		if(!inverse)
			return decay;
		
		//if in inverse mode we return the effective distance
		if(hasLineOfSight) throw new RuntimeException("Asking for effective distance for a given decay only really makes sense for locations without line of sight");
		double effectivedist = Math.pow(10, (decay-fixedloss)/(10*alpha));
		return effectivedist;
		
	}

	
	
	private static double decayFunc_fitted(double dist, boolean hasLineOfSight, boolean inverse)
	{
		double alpha = 0.72;
		double fixedloss = 10;
		double wallloss = 15;
		
		double decay = fixedloss+10*alpha*rses.util.Util.log_n(dist, 10.0);
		if(!hasLineOfSight) decay += wallloss;
		if(!inverse)
			return decay;
		
		//if in inverse mode we return the effective distance
		if(hasLineOfSight) throw new RuntimeException("Asking for effective distance for a given decay only really makes sense for locations without line of sight");
		double effectivedist = Math.pow(10, (decay-fixedloss)/(10*alpha));
		return effectivedist;
		
	}

	public static double detectFunc(double dist, boolean hasLineOfSight)
	{
		double effectivedist = dist;
		//if we dont have line of sight, what is the chance of detection?
		//we work out the 'effective' distance and use that
		if(!hasLineOfSight) {
			effectivedist = decayFunc(dist, hasLineOfSight, true);
			//Debug.println("In detectFunc, effective distance for dist of "+dist+" is "+effectivedist, Debug.IMPORTANT);
		}
		
		//These from the combined regression on 16th May and 30th May
		//		Coefficients:
		//            Estimate Std. Error z value Pr(>|z|)    
		//(Intercept) -1.34888    0.07106  -18.98   <2e-16 ***
		//DIST         0.02715    0.00101   26.88   <2e-16 ***

		
		if(effectivedist >= 250)
			return 0;
		else {
			double logit = Math.exp(-1.34888+0.02715*effectivedist);
			if(logit > Float.MAX_VALUE) return 0.0;
			return 1-logit/(1+logit);
		}
	}

	
	public static double detectFunc_may16thonly(double dist, boolean hasLineOfSight)
	{
		double effectivedist = dist;
		//if we dont have line of sight, what is the chance of detection?
		//we work out the 'effective' distance and use that
		if(!hasLineOfSight) {
			effectivedist = decayFunc(dist, hasLineOfSight, true);
			//Debug.println("In detectFunc, effective distance for dist of "+dist+" is "+effectivedist, Debug.IMPORTANT);
		}
		
		//ok, probability of detection
		/*if(effectivedist < 6.0)
			return 1-(0.1+0.02*effectivedist);
		else*/ if(effectivedist >= 250)
			return 0;
		else {
			double logit = Math.exp(-0.775908+0.019742*effectivedist);
			if(logit > Float.MAX_VALUE) return 0.0;
			return 1-logit/(1+logit);
		}
	}

	
	
	/** Make a decay map based on line of sight, and a formula
	 * 
	 * @param args
	 */
	public static void main(String[] args) throws Exception
	{
		Debug.setVerbosityLevel(Debug.INFO);
		Debug.println("args: ", Debug.INFO);
		Debug.println("        ", Debug.INFO);
		Debug.println("    observers file", Debug.INFO);
		Debug.println("    bounds file", Debug.INFO);
		Debug.println("    spatial prior", Debug.INFO);
		
		//first step is to read in the spatial prior and observers files
		//get Observers
		Observer[] obs = Main.readObservers(args[0]);
		Debug.println("Read observers file "+args[0], Debug.INFO);
		
		//get GIS bounds and create a GIS layer that covers these bounds
		GISLayer gis = Main.getGISLayer(args[1], "noname");
		Debug.println("Read bounds file "+args[1]+" and created geography", Debug.INFO);
		
		//get the spatial prior
		PMF prior = PMF.read(args[2], gis);
		Debug.println("Initialized spatial prior", Debug.INFO);
		
		//ok, now, for each of the observers, generate a 0/1 line-of-sight map
		PMF[] lineofsightemaps = getLineOfSightMaps(prior, obs);
		
		//write them out
		for(int i = 0; i < lineofsightemaps.length; i++)
		{
			lineofsightemaps[i].writeImage(obs[i].id);
			lineofsightemaps[i].printToFile("LOS_"+obs[i].id+".txt");

			double startlat = lineofsightemaps[i].getGeography().getMinLat()+lineofsightemaps[i].getGeography().getLatStepSize()*0.5;
			double startlon = lineofsightemaps[i].getGeography().getMinLong()+lineofsightemaps[i].getGeography().getLongStepSize()*0.5;
			
			double obslat = obs[i].lat;
			double obslon = obs[i].lon;

			

			
			
			//ok, now turn it into distance decay and probability of detection maps
			PMF decaymap = new PMF(lineofsightemaps[i]);
			PMF detectmap = new PMF(lineofsightemaps[i]);
			for(double lat = startlat; lat < lineofsightemaps[i].getGeography().getMaxLat(); lat += lineofsightemaps[i].getGeography().getLatStepSize())
			{
				for(double lon = startlon; lon < lineofsightemaps[i].getGeography().getMaxLong(); lon += lineofsightemaps[i].getGeography().getLongStepSize())
				{
					double dist = MathUtil.getDistanceBetweenPointsOnEarth(lat, lon, obslat, obslon);
					dist = Math.sqrt(dist*dist+obs[i].z_metres*obs[i].z_metres);
					
					if(lineofsightemaps[i].getProb(lat, lon) > 0) {
						decaymap.setValue(lat, lon, decayFunc(dist, true));
						detectmap.setValue(lat, lon, detectFunc(dist, true));
					}
					else {
						decaymap.setValue(lat, lon, decayFunc(dist, false));
						detectmap.setValue(lat, lon, detectFunc(dist, false));
					}
				}
			}
			
			//write out decay map
			decaymap.printToFile("decayMap_"+obs[i].id+".txt");
			decaymap.writeImage("decayMap_"+obs[i].id+".png");


			//write out detect map
			detectmap.printToFile("detectMap_"+obs[i].id+".txt");
			detectmap.writeImage("detectMap_"+obs[i].id+".png");

			
			
			
			
		}
		
		
	}

	
	
	private static PMF[] getLineOfSightMaps(PMF prior, Observer[] observers)
	{
		PMF[] results = new PMF[observers.length];
		
		for(int i = 0; i < results.length; i++)
		{
			results[i] = getLineOfSightMap(prior, observers[i]);
		}
		return results;
	}

	/** Get line of sight map between observer and all other squares.
	 * The PMF returned has a value of 1 if there is line of sight, and 
	 * a value of 0 if not
	 * 
	 * @param prior
	 * @param observer
	 * @return
	 */
	private static PMF getLineOfSightMap(PMF prior, Observer observer)
	{
		PMF result = new PMF(prior.getGeography());
		
		//ok, for a bunch of points (at a finer resolution than the prior)
		//work out if we have line of sight to the observer. If we do,
		//tag that square as having line of sight
		double minlat = prior.getGeography().getMinLat();
		double minlon = prior.getGeography().getMinLong();
		double maxlat = prior.getGeography().getMaxLat();
		double maxlon = prior.getGeography().getMaxLong();
		double latstep = 0.5*prior.getGeography().getLatStepSize();
		double lonstep = 0.5*prior.getGeography().getLongStepSize();
		
		double startlat = minlat+latstep/2;
		double startlon = minlon+lonstep/2;
		
		
		Debug.println("Getting line of sight map for "+observer.id, Debug.INFO);
		Debug.println("Startlat is "+startlat, Debug.INFO);
		Debug.println("Startlon is "+startlon, Debug.INFO);
		
		//first lets check to see if the observer is in a reachable square
		//(i.e. a square with probability > 0)
		int[] obsind = prior.getIndices(observer.lat, observer.lon);
		if(prior.getProb(observer.lat, observer.lon) == 0.0)
		{
			throw new RuntimeException("Prior at "+(obsind[1]+1)+" "+(obsind[0]+1)+" is zero, but this is where a tracking station is!");
		}
		
		
		for(double lat = startlat; lat < maxlat; lat += latstep)
		{
			for(double lon = startlon; lon < maxlon; lon += lonstep)
			{
				int[] ind = prior.getIndices(lat, lon);
				//Debug.println("Looking at line of sight between "+(ind[1]+1)+","+(ind[0]+1)+" and "+(obsind[1]+1)+","+(obsind[0]+1), Debug.INFO);

				if(hasLineOfSight(lat, lon, observer.lat, observer.lon, prior))
					result.setValue(lat, lon, 1.0);
			}
		}
		
		
		return result;
	}

	
	
	public static boolean hasLineOfSight(double lat1, double lon1, 
			double lat2, double lon2, 
			PMF prior)
	{
		double dist = rses.math.MathUtil.getDistanceBetweenPointsOnEarth(lat1, lon1, lat2, lon2);
		double nsteps = dist/prior.getSquareSize();
		nsteps = Math.round(nsteps*4);
		
		//Debug.println("Between "+lat1+" "+lon1+" and "+lat2+" "+lon2+", there are "+nsteps, Debug.INFO);
		
		double latstep = (lat2-lat1)/nsteps;
		double lonstep = (lon2-lon1)/nsteps;
		//Debug.println("Lat step is "+latstep, Debug.INFO);
		//Debug.println("Long step is "+lonstep, Debug.INFO);
		double lat = lat1;
		double lon = lon1;
		for(int step = 0; step <= nsteps; step++, lat += latstep, lon += lonstep)
		{
			if(prior.getProb(lat, lon) == 0) 
			{
				//Debug.println("Line of sight blocked at step "+step+" ("+lat+","+lon+")", Debug.INFO);
				return false;
			}
		}
		//Debug.println("We have line of sight between "+lat1+" "+lon1+" and "+lat2+" "+lon2+" !! ", Debug.INFO);
		return true;
	}
	
}
