package rses.spatial.gui;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Iterator;

import rses.Debug;
import rses.spatial.util.DataStore;
import rses.spatial.util.GeoDataTable;




/** A model that just displays what is in the database but
 *  doesnt do any recalculation.
 * 
 * @author peterr
 *
 */
public class DataBaseBackedDisplayableModel extends DisplayableModel
{
	private DataStore database;
	private String mbrlayer;
	private String displaytable;
	private Color lwr = Color.BLUE;
	private Color upr = Color.RED;
	
	public DataBaseBackedDisplayableModel(DataStore database, String layername, String displayvar)
	{
		this.database = database;
		this.mbrlayer = layername;
		this.displaytable = displayvar;

		if(displaytable != null && !database.hasTable(layername, displaytable))
			throw new RuntimeException("Database does not have table "+displaytable+", but this is the table specified as display");
	}
	
	public void setDisplayTable(String layername, String table)
	{
		if(table != null && !database.hasTable(layername, table))
			throw new RuntimeException("Database does not have table "+table+", but this is the table specified as display");
		this.mbrlayer = layername;
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
	
	
	public String getUnderlyingLayerName()
	{
		return this.mbrlayer;
	}
	
	public HashMap<String, Double> getDisplayTable()
	{
		if(this.displaytable == null)
			return null;
		
		String[] regionnames = database.getVectorLayer(this.mbrlayer).getCategoryNames();
		
		HashMap<String, Double> regionToValMapping = new HashMap<String, Double>();
		
		GeoDataTable table = database.getTable(this.mbrlayer, this.displaytable);
		
		for(int i = 0; i < regionnames.length; i++)
		{
			String key = regionnames[i];
			Double res = (Double) table.lookup(key);
			if(res != null)
				regionToValMapping.put(key, res);
		}
		return regionToValMapping;
	}
	
	private BufferedImage bim = null;
	public BufferedImage getDisplay(int[][] membership, int[][] placemarks, int zoomfact)
	{
		//Debug.println("getDisplay() called for DataBaseBackedModel, placemarks is "+placemarks, Debug.INFO);
		if(bim == null)
		{
			Debug.println("recalculating display for regional model with display table "+displaytable, Debug.INFO);
			HashMap<String, Double> regionIdToValMapping = new HashMap<String, Double>();
			
			if(this.displaytable != null)
			{
				//build up a table of all the valid regions and their values
				//Need to go through this intermediate step because tables
				//can sometimes have values for regions that arent covered
				//by the study area				
				String[] regions = database.getVectorLayer(this.mbrlayer).getCategoryNames();
				GeoDataTable table = database.getTable(this.mbrlayer, this.displaytable);
				for(int i = 0; i < regions.length; i++)
				{
					String key = regions[i]; 
					Object res = table.lookup(key);
					
					if(res != null && res instanceof Double) {
						regionIdToValMapping.put(regions[i], (Double) res);
						System.err.println(regions[i]+" "+res);
					}
				}
			}
			
			
			//now draw the image
			if(zoomfact < 0)
				throw new RuntimeException("Only positive zoom supported....");				
			else if(zoomfact >= 0)
				bim = displayByRegion(zoomfact, 
						regionIdToValMapping,
						membership, database.getVectorLayer(this.mbrlayer).getCategoryNames(),
						placemarks, this.lwr, this.upr);
			
				
		}
		return bim;
		
	}
}

