package rses.apps.sydneyenergy;




public class Household
{
	int hhtype;
	int ibrak;
	

	
	public Household(int hhtype, int ibrak, int maxhhtype, int maxibrak)
	{
		if(ibrak < 0 || ibrak > maxibrak)
			throw new IllegalArgumentException("Income bracket out of range");
		
		if(hhtype < 0 || hhtype > maxhhtype)
			throw new IllegalArgumentException("Unknown hh type");
		
		this.hhtype = hhtype;
		this.ibrak = ibrak;
	}
}



