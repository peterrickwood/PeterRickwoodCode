package rses.CoSpedestriantracking;

import java.util.Calendar;


public class RSSObservation 
{
	public double ss; //the received signal strength
	public Observer observer; //the observer who received that reading
	public java.util.Calendar time;
	public String sourceId;
	public boolean fromPacketInjection;
		
	public RSSObservation(double ss, Observer obs, java.util.Calendar time, String sourceID, boolean fromInjection)
	{
		this.ss = ss;
		this.observer = obs;
		this.time = time;
		this.sourceId = sourceID;
		this.fromPacketInjection = fromInjection;
	}
	
	public static RSSObservation[] readObservations(String filename, Observer[] observers) throws Exception
	{
		java.io.BufferedReader rdr = new java.io.BufferedReader(new java.io.FileReader(filename));
		String line = rdr.readLine();
		java.util.ArrayList<RSSObservation> obs = new java.util.ArrayList<RSSObservation>();
		java.util.Calendar firstDay = null;
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
			
			//get the time. Which is a date-time
			java.util.Calendar cal = java.util.Calendar.getInstance();
			java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			boolean gotdatetime = false;

			java.util.Date day = sdf.parse(bits[2]+" "+bits[3]);
			gotdatetime = true;
			cal.setTime(day);
			if(firstDay == null) firstDay = cal;
			else if(firstDay.get(Calendar.MONTH) != cal.get(Calendar.MONTH) ||
					firstDay.get(Calendar.DAY_OF_MONTH) != cal.get(Calendar.DAY_OF_MONTH))
				throw new RuntimeException("All observations must be on the same day!");
			
			double signalstrength;
			boolean fromPacketInj = false;
			if(bits[4].toLowerCase().endsWith("_p")) //from packet injection
			{
				fromPacketInj = true;
				signalstrength = Double.parseDouble(bits[4].split("_")[0]);
			}
			else 
				signalstrength = Double.parseDouble(bits[4]);

			RSSObservation rss;
			if(gotdatetime)
				rss = new RSSObservation(signalstrength, observer, cal, sourceId, fromPacketInj);
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
