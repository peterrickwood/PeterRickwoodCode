package rses.spatial.gui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import rses.Debug;
import rses.math.MathUtil;
import rses.spatial.GISLayer;
import rses.spatial.util.DataStore;
import rses.util.Util;



/** Load up the geodatabase, including layer membership and the like
 * 
 * 
 * Structure assumed is as follows:
 * 
 * There is a directory called "regions" that contains all the region membership gis files
 * 
 * There is a directory called "db" that contains the following:
 * 
 *                    EITHER
 *                    
 *       files that are simple region_id --> value mappings.
 *       these filenames must end in ".tab"
 *       THE FIRST LINE IN THESE FILES MUST START WITH A LINE LIKE THIS:
 *       #layer LAYERNAME
 *       where LAYERNAME is the file stem of the gis membership layer it refers to
 * 
 *                     OR
 *                     
 *       files that are raster layers
 *       these must end in ".gis"
 * 	    
 *      
 * 
 * 
 * 
 * 
 * 
 * 
 * 
 * 
 * @author peterr
 *
 */
public final class Main
{
	private Main() throws Exception {
		throw new IllegalAccessException("Cannot be instantiated");
	}
	
	
	
	
	
	public static void main(String[] args) throws Exception
	{
		Debug.setDebugLevel(Debug.DEBUG_SUSPICIOUS);
		Debug.setVerbosityLevel(Debug.EXTRA_INFO);
		
		//first we create the data store
		DataStore database = new DataStore();

		
		//get a membership matrix for each layer
		HashMap<String, int[][]> membershipPerLayer = new HashMap<String, int[][]>();
		
		java.util.List<String> layernames = database.getVectorLayers();
		
		java.util.Iterator<String> layerit = layernames.iterator();
		while(layerit.hasNext())
		{
			String name = layerit.next();
			GISLayer regionmembership = database.getVectorLayer(name);
			
			//int[][] membership = DisplayableModel.getMembershipMatrix(regionmembership, regionmembership.getLongSteps(), regionmembership.getLatSteps());
			
			//order our membership data so that it matches the coordinate system for graphics layout.
			int[][] membership = new int[regionmembership.getLatSteps()][];
			for(int i = 0; i < membership.length; i++)
				membership[i] = regionmembership.categoricaldata[membership.length-i-1];
			membershipPerLayer.put(name, membership);
		}
		
		//ok, now launch the main window
		MainWindow mainwindow = new MainWindow(800, 800, database, layernames, membershipPerLayer);
		mainwindow.setVisible(true);
	}
}
