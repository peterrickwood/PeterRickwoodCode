package rses.apps.sydneyenergy;


import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.HashMap;

import rses.Debug;




public class RegionalEnergyModel extends DisplayableModel
{
	//the internal resolution we work with
	private Integer[] regionids;
	public static final String databasename = "Combined_Housing_Transport";
	private DataStore database;
	
	private HashMap regionIdToEnergyMapping;
	private HousingEnergyModule housing;
	private TransportEnergyModule transport;
	
	
	public RegionalEnergyModel(
			HousingEnergyModule housing, 
			TransportEnergyModule transport,
			Integer[] regionids, DataStore database)
	{
		this.housing = housing;
		this.transport = transport;
		this.regionids = regionids;
		this.database = database;
		
		//add an initial table
		this.recalculate();
	}
	
	
	
	public void recalculate()
	{
		this.housing.recalculate();
		this.transport.recalculate();
		HashMap newmapping = new HashMap();
		for(int i =0; i < regionids.length; i++)
		{
			double henergy = housing.calcEnergyForRegion(regionids[i]);
			double tenergy = transport.calcEnergyForRegion(regionids[i]);
			//Debug.println("transport energy for region "+regionids[i]+" is "+tenergy, Debug.INFO);
			double combinedenergy = henergy+tenergy;
			newmapping.put(""+regionids[i], new Double(combinedenergy));
		}
		this.regionIdToEnergyMapping = newmapping;
		if(!this.database.hasTable(databasename))
			this.database.addTable(databasename, this.regionIdToEnergyMapping);
		else
			this.database.replaceTable(databasename, this.regionIdToEnergyMapping);
		bim = null;
	}
	
	
	
	
	
	
	
	
	
	
	
	private BufferedImage bim = null;
	public BufferedImage getDisplay(int[][] membership, int[][] placemarks)
	{
		//Debug.println("getDisplay() called for regional model", Debug.INFO);
		if(bim == null)
		{
			Debug.println("recalculating display for regional model", Debug.INFO);
			bim = displayByRegion(membership[0].length, 
					membership.length, 
					regionIdToEnergyMapping,
					membership, placemarks, Color.blue, Color.red);
		}
		return bim;
		
	}
	
	
	
	
	/*public void paintComponent(Graphics g)
	{
		//create the offscreen buffer if we need to
		if(bim == null) {
			bim = new BufferedImage(lonsteps, latsteps, BufferedImage.TYPE_3BYTE_BGR);
			this.paintEnergyMap_internal(bim.getGraphics(), lonsteps, latsteps);
		}
		
		//clear the background
		g.setColor(Color.black);
		g.fillRect(0, 0, this.getWidth(), this.getHeight());

		//draw the offscreen buffer
		g.drawImage(bim, 0, 0, null);
	}*/
	
	
	
	
}