package rses.apps;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;

import rses.Debug;
import rses.spatial.util.GoogleEarthPolygon;
import rses.spatial.util.GoogleEarthUtils;

public class CollectionDistrictAreaCalculator 
{

	/**
	 * @param args
	 * 
	 * 
	 * arg1 == the KML file name which contains the state CDs 
	 *  
	 *         
	 * 
	 * 
	 */
	public static void main(String[] args) throws Exception
	{
		//Debug.setVerbosityLevel(Debug.EXTRA_INFO);
		
		GoogleEarthPolygon[] polys = GoogleEarthUtils.getPolygonsFromKMLFile(args[0], "name");
		
		
		for(int i = 0; i < polys.length; i++) {
			System.out.println(polys[i].name.substring(1, polys[i].name.length()-1)+" "+polys[i].getArea(10000, false));
			polys[i] = null;
		}
		
	}
		
	
}
