package rses.spatial.util;

import rses.spatial.GISLayer;

public class GeoDataTable 
{
	private String tablename;
	private GISLayer membership;
	private java.util.Map<String, Object> regionid_to_value_mapping;
	
	
	public GeoDataTable(String tablename, GISLayer mbr, java.util.Map<String, Object> mappings)
	{
		this.tablename = tablename;
		this.membership = mbr;
		this.regionid_to_value_mapping = mappings;
	}
	
	
	public GISLayer getMembershipLayer()
	{
		return this.membership;
	}
	
	
	public String getName()
	{
		return this.tablename;
	}
	
	public Object lookup(String key)
	{
		return regionid_to_value_mapping.get(key);
	}
	
	public Object set(String key, Object val)
	{
		return regionid_to_value_mapping.put(key, val);
	}
	

	public void removeMapping(String key)
	{
		regionid_to_value_mapping.remove(key);
	}
	
	
	java.util.Map<String, Object> getunderlyingMappings()
	{
		return regionid_to_value_mapping;
	}
	

}
