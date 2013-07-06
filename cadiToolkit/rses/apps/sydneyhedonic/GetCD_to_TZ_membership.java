package rses.apps.sydneyhedonic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import rses.Debug;
import rses.spatial.GISLayer;


/** Work out how much of each CD lies within
 *  each travelzone.
 * 
 * @author peterr
 *
 */
public class GetCD_to_TZ_membership 
{
	public static void main(String[] args) throws Exception
	{
		Debug.setVerbosityLevel(Debug.INFO);
		Debug.setDebugLevel(Debug.DEBUG_SUSPICIOUS);
		
		GISLayer cdgis = GISLayer.readFromFile("cdmembership.gis");
		GISLayer tzgis = GISLayer.readFromFile("tzmembership.gis");
		
		//get the underlying raw data
		float[][] cdraw = cdgis.continuousdata;
		float[][] tzraw = tzgis.continuousdata;
		
		//make sure they are of the same size, and cover
		//the same area
		if(cdraw.length != tzraw.length || cdraw[0].length != tzraw[0].length
		   || cdgis.getMinLat() != tzgis.getMinLat() || cdgis.getMaxLat() != tzgis.getMaxLat()
		   || cdgis.getMinLong() != tzgis.getMinLong() || cdgis.getMaxLong() != tzgis.getMaxLong())
			throw new RuntimeException("layers dont match");
		
		
		//keep total area count for each cd,
		//and also count how many squares are in each CD
		//
		//to do this, we use 2 Maps. 
		//1 Map maps CD_id --> list of tuples of (TZid, count)
		//other maps CD_id --> total area
		
		HashMap membercount = new HashMap();
		HashMap totalcount = new HashMap();
		
		
		
		for(int i =0; i < cdraw.length; i++)
		{
			for(int j = 0; j < cdraw[0].length; j++)
			{
				if(Float.isNaN(cdraw[i][j]))
					continue;
				
				int cd = (int) (Math.round(cdraw[i][j]));
				int tz = -1; 
				if(Float.isNaN(tzraw[i][j])) 
					/*not in a valid TZ. just update CD area count */;
				else
					tz = (int) (Math.round(tzraw[i][j]));
				
				
				Integer key = new Integer(cd);
				if(membercount.containsKey(key))
				{
					//first we update count of area of that CD
					int[] count = (int[]) totalcount.get(key);
					count[0]++;
					
					if(tz == -1) //nothing left to do, not in valid TZ
						continue;
					
					//get the list of TZs that are coincident with
					//this CD
					ArrayList tlist = (ArrayList) membercount.get(key);
					
					//if we already know they are coincident,
					//just update the counter
					boolean found = false;
					for(int t =0; t < tlist.size() && !found; t++)
					{
						Tuple tup = (Tuple) tlist.get(t);
						if(tup.id == tz) 
						{
							//update count of overlap between cd and tz
							tup.count++;
							found = true;
						}
					}
					
					//otherwise, we need to add an entry to the list
					if(!found) {
						tlist.add(new Tuple(tz));
					}
				}
				else //create the list with 1 element and add that 
				{ 
					totalcount.put(key, new int[] {1});
					ArrayList tlist = new ArrayList();
					if(tz != -1) 
						tlist.add(new Tuple(tz));
					membercount.put(key, tlist);
				}
				
			}
			Debug.println("done line "+i+" of "+cdraw.length, Debug.INFO);
		}
		
			
		
		//ok, we have done all the counting, print out results
		//
		//for each cd, print out total count and proportions in each TZ
		Iterator keyit = membercount.keySet().iterator();
		while(keyit.hasNext())
		{
			Integer key = (Integer) keyit.next();
			ArrayList tzlist = (ArrayList) membercount.get(key);
			
			int sum = 0;
			String printstr = ""+key.intValue();
			for(int t = 0; t < tzlist.size(); t++)
			{
				Tuple tup = (Tuple) tzlist.get(t);
				printstr += " , "+tup;
				sum += tup.count;
			}
			printstr += " , "+((int[])totalcount.get(key))[0];
			if(sum < 0.85*((int[])totalcount.get(key))[0]) {
				double pct = ((double) sum)/(((int[])totalcount.get(key))[0]);
				Debug.println("WARNING... only "+pct+" of the following CD belongs somewhere", Debug.IMPORTANT);
			}
				//throw new RuntimeException("sums dont match.... should be impossible");
			
			System.out.println(printstr);
		}
		
	}
	
	
	private static class Tuple {
		Tuple(int id) { this.id = id; this.count = 1;}
		int id = -1;
		int count = 0;
		
		public String toString()
		{
			return id+" : "+count;
		}
	}

}





