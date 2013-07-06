package rses.apps.sydneyenergy;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Iterator;

import rses.Debug;




/** A model that just displays what is in the database but
 *  doesnt do any recalculation.
 * 
 * @author peterr
 *
 */
public class DataBaseBackedDisplayableModel extends DisplayableModel
{
	private DataStore database;
	private String displaytable;
	private Color lwr = Color.BLUE;
	private Color upr = Color.RED;
	
	public DataBaseBackedDisplayableModel(DataStore database, String displayvar)
	{
		this.database = database;
		this.displaytable = displayvar;
		if(!database.hasTable(displaytable))
			throw new RuntimeException("Database does not have table "+displaytable+", but this is the table specified as display");
	}
	
	public void setDisplayTable(String table)
	{
		if(!database.hasTable(table))
			throw new RuntimeException("Database does not have table "+table+", but this is the table specified as display");
		this.displaytable = table;
		this.bim = null;
	}
	
	public void recalculate()
	{
		this.bim = null;
		return;
	}
	
	
	public void setColourRange(Color lwr, Color upr)
	{
		this.lwr = lwr;
		this.upr = upr;
	}
	
	
	public String getUnderlyingTableName()
	{
		return this.displaytable;
	}
	
	public HashMap getDisplayTable()
	{
		Integer[] validregions = (Integer[]) database.lookupArbitrary("globals", "validregions");
		HashMap regionIdToValMapping = new HashMap();
		HashMap table = database.getTable(this.displaytable);
		for(int i = 0; i < validregions.length; i++)
		{
			String key = ""+validregions[i];
			Double res = (Double) table.get(key);
			if(res != null)
				regionIdToValMapping.put(key, res);
		}
		return regionIdToValMapping;
	}
	
	private BufferedImage bim = null;
	public BufferedImage getDisplay(int[][] membership, int[][] placemarks)
	{
		//Debug.println("getDisplay() called for DataBaseBackedModel, placemarks is "+placemarks, Debug.INFO);
		if(bim == null)
		{
			Debug.println("recalculating display for regional model", Debug.INFO);
			
			//build up a table of all the valid regions and their values
			//Need to go through this intermediate step because tables
			//can sometimes have values for regions that arent covered
			//by the study area
			Integer[] validregions = (Integer[]) database.lookupArbitrary("globals", "validregions");
			HashMap regionIdToValMapping = new HashMap();
			HashMap table = database.getTable(this.displaytable);
			for(int i = 0; i < validregions.length; i++)
			{
				String key = ""+validregions[i];
				Double res = (Double) table.get(key);
				if(res != null)
					regionIdToValMapping.put(key, res);
			}
			
			
			
			//now draw the image
			bim = displayByRegion(membership[0].length, 
					membership.length, 
					regionIdToValMapping,
					membership, placemarks, this.lwr, this.upr);
		}
		return bim;
		
	}
}

