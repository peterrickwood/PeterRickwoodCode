package rses.apps.sydneyenergy;




public class LocatedHousehold
{
	Household hh;
	int regionid;
	int dwellingid;
	
	
	public LocatedHousehold(Household h, int region, int dwelltype, int maxdwelltype)
	{
		if(dwelltype < 0 || dwelltype > maxdwelltype)
			throw new IllegalArgumentException("Invalid dwelling type");
		
		hh = h;
		this.regionid = region;
		this.dwellingid = dwelltype;
		
		
	}
}