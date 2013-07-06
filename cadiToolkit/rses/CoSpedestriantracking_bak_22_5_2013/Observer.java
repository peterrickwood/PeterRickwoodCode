package rses.CoSpedestriantracking;

public class Observer 
{
	double lat;
	double lon;
	double z_metres;
	String id;
	
	public Observer(double lat, double lon, double z_metres, String observerId)
	{
		this.lat = lat;
		this.lon = lon;
		this.z_metres = z_metres;
		this.id = observerId;
	}
	

}
