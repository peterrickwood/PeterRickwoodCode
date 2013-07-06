package rses.CoSpedestriantracking;


public class RSSObservation 
{
	public double ss; //the received signal strength
	public Observer observer; //the observer who received that reading
	public java.util.Calendar time;
	public String sourceId;
		
	public RSSObservation(double ss, Observer obs, java.util.Calendar time, String sourceID)
	{
		this.ss = ss;
		this.observer = obs;
		this.time = time;
		this.sourceId = sourceID;
	}
	
	public static RSSObservation[] readObservations(String filename, Observer[] observers) throws Exception
	{
		java.io.BufferedReader rdr = new java.io.BufferedReader(new java.io.FileReader(filename));
		String line = rdr.readLine();
		java.util.ArrayList<RSSObservation> obs = new java.util.ArrayList<RSSObservation>();
		while(line != null)
		{
			String[] bits = rses.util.Util.getWords(line);
			String receiverid = bits[0];
			Observer observer = null;
			for(int i = 0; i < observers.length; i++)
				if(observers[i].id.equals(receiverid)) {
					observer = observers[i]; break;
				}
			if(observer == null)
				throw new RuntimeException("Cannot find observer with ID "+receiverid);
			
			String sourceId = bits[1];
			
			//get the time. Which can either be a double, or a date-time
			java.util.Calendar cal = java.util.Calendar.getInstance();
			java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			boolean gotdatetime = false;

			java.util.Date day = sdf.parse(bits[2]+" "+bits[3]);
			gotdatetime = true;
			cal.setTime(day);
			
			/*
				rses.Debug.println("Couldnt parse date-time object, using current day and assuming plain second offset expressed as float", rses.Debug.IMPORTANT);
				cal.setTime(new java.util.Date());
				cal.set(java.util.Calendar.HOUR_OF_DAY, 1);
				cal.set(java.util.Calendar.MINUTE, 0);
				cal.set(java.util.Calendar.SECOND, 0);
				cal.set(java.util.Calendar.MILLISECOND, 0);
				cal.add(java.util.Calendar.MILLISECOND, (int) (Math.round(Double.parseDouble(bits[2])*1000)));
			 */				

			RSSObservation rss;
			if(gotdatetime)
				rss = new RSSObservation(Double.parseDouble(bits[4]), observer, cal, sourceId);
			else //should be impossible
				throw new IllegalStateException("Should be impossible!!!");
				//rss = new RSSObservation(Double.parseDouble(bits[3]), observer, cal, sourceId);
				
			obs.add(rss);
			line = rdr.readLine();
		}
		
		RSSObservation[] toreturn = new RSSObservation[obs.size()];
		for(int i=0; i < toreturn.length; i++)
			toreturn[i] = obs.get(i);
		return toreturn;
		
	}

}
