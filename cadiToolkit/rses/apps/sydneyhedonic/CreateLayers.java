package rses.apps.sydneyhedonic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;



import rses.Debug;
import rses.math.MathUtil;
import rses.spatial.GISLayer;
import rses.spatial.util.GoogleEarthPolygon;
import rses.spatial.util.GoogleEarthUtils;
import rses.util.FileUtil;
import rses.util.Util;








public class CreateLayers
{
	public static void createIndustrialAndCommercialLayers(String gisfile) throws Exception
	{
		//beyond this radius, things dont matter anymore
		double radius = 1.5; //1.5 km
		
		//BufferedReader console = new java.io.BufferedReader(new InputStreamReader(System.in));
		//System.out.print("Enter GIS layer with zone info: ");
		//String gisfile = console.readLine();
		
		Debug.println("reading in GIS layer", Debug.IMPORTANT);
		GISLayer gis = GISLayer.readFromFile(gisfile);
		Debug.println("shrinking GIS layer", Debug.IMPORTANT);
		int[][] shrunk = shrinkLayer(gis.categoricaldata, 4);
		//gis.shrink(2); //shrink it so that computation is tractable
		int latsteps = shrunk.length;
		int lonsteps = shrunk[0].length;
		Debug.println("There are "+latsteps+" latsteps and "+lonsteps+" longsteps in shrunk layer", Debug.IMPORTANT);
		
		Debug.println("read GIS layer, initializing data", Debug.IMPORTANT);
		float[][] indlayer = new float[latsteps][lonsteps];
		float[][] comlayer = new float[latsteps][lonsteps];
		double minlat = gis.getMinLat();
		double maxlat = gis.getMaxLat();
		double minlong = gis.getMinLong();
		double maxlong = gis.getMaxLong();
		double latstep = (maxlat-minlat)/latsteps;
		double lonstep = (maxlong-minlong)/lonsteps;

		//work out the radius of points we need to consider
		Debug.println("calculating radius", Debug.IMPORTANT);
		int r = 1;
		int rsquare = 1;
		while(true)
		{
			if(MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+r*latstep, minlong)/1000 > radius
				&& MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat, minlong+r*lonstep)/1000 > radius)
				break; //we are outside the circle
			if(MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+rsquare*latstep, minlong+rsquare*lonstep)/1000 > radius
					&& MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+rsquare*latstep, minlong+rsquare*lonstep)/1000 > radius)
				rsquare++;
			r++;
		}
		Debug.println("radius is "+r, Debug.IMPORTANT);
		
		
		//take a shortcut and pre-compute the distances. This
		//results in only slight innacuaries
		Debug.println("pre-computing distance matrix", Debug.IMPORTANT);
		double[][] distmatrix = new double[r][r];
		for(int i =0; i < r; i++)
			for(int j=0; j < r; j++)
				distmatrix[i][j] = MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+i*latstep, minlong+j*lonstep)/1000;
		
		
		
		//for each lat/long point, count all other points
		//within a particular radius, weighting for distance
		for(int lati = 0; lati < shrunk.length; lati ++)
		{
			for(int loni = 0; loni < shrunk[0].length; loni++)
			{
				double comsum = 0.0;
				double indsum = 0.0;
				//sweep through the circle and count each square
				for(int i =-r+1; i < r; i++)
				{
					for(int j = -r+1; j < r; j++)
					{
						if(lati+i >= 0 && lati+i < shrunk.length &&
						   loni+j >= 0 && loni+j < shrunk[0].length)
						{
							double dist = 0.1; //min of 100 metres (the grid res)
							//we have precomputed distances
							if(i!=0 || j!=0)
								dist = Math.max(distmatrix[Math.abs(i)][Math.abs(j)], dist);
							
							//if(i!=0 || j!=0)
							//MathUtil.getDistanceBetweenPointsOnEarth(lat, lon, latr, lonr);
							//dist = dist / 1000.0;
							
							if(dist < radius)
							{
								if(shrunk[lati+i][loni+j] == 0)
									comsum = comsum + 1/(dist*dist);
								else if(shrunk[lati+i][loni+j] == 1) 
									indsum = indsum + 1/(dist*dist); 
							}
						}
					}
				}
				
				//int[] ind = gis.getLatLongIndices(lat, lon);
				
				indlayer[lati][loni] = (float) indsum; 
				comlayer[lati][loni] = (float) comsum;
			}
			Debug.println("finished row at latitude "+lati+", "+(indlayer.length-lati-1)+" to go", Debug.IMPORTANT);
		}
		
		//now we shrink layers
		//Debug.println("Shrinking layers", Debug.IMPORTANT);
		//indlayer = shrinkLayer(indlayer, 2);
		//comlayer = shrinkLayer(indlayer, 2);
		
		//now we normalize the two layers
		Debug.println("Normalizing layers", Debug.IMPORTANT);
		normalize(indlayer); 
		normalize(comlayer);
		
		//now save the two layers
		Debug.println("Saving layers to file", Debug.IMPORTANT);
		new GISLayer("INDUSTRIAL", minlat, maxlat, minlong, maxlong, indlayer).saveToFile("industrial_proximity.gis");
		new GISLayer("COMMERCIAL", minlat, maxlat, minlong, maxlong, comlayer).saveToFile("commercial_proximity.gis");
		
	}


	//combine our two transport layers into a combined
	//layer that should be better than either one individually 
	//
	public static void createTransportLayer() throws IOException
	{
		GISLayer despeck = GISLayer.readFromFile("transport_wmanualm2m5_despeckle.gis");
		GISLayer jfromi = GISLayer.readFromFile("transport_withJfromI.gis");
		
		if(despeck.getLatSteps() != jfromi.getLatSteps() || despeck.getLongSteps() != jfromi.getLongSteps())
			throw new RuntimeException();
		
		int[][] combined = despeck.categoricaldata;
		
		//take either layer. If there is a disagreement, take the despeck layer
		for(int i = 0; i < combined.length; i++)
			for(int j =0; j < combined.length; j++)
			{
				int dsc = combined[i][j];
				int jic = jfromi.categoricaldata[i][j];
				if(dsc == jic) //all hunky-dory, they agree
					continue;
				
				//despeck has a classification, so we prefer it
				if(dsc >= 0)
					continue;
				
				//despeck has no classification, so we rely on jic instead
				combined[i][j] = jic;
			}
				
		despeck.saveToFile("transport_combined.gis");
	}
	
	
	
	public static void createIndustrialAndCommercialCategoricalLayers(String gisfile) throws Exception
	{
		//beyond this radius, things dont matter anymore
		double radius = 2.0; //2.0 km
		
		//BufferedReader console = new java.io.BufferedReader(new InputStreamReader(System.in));
		//System.out.print("Enter GIS layer with zone info: ");
		//String gisfile = console.readLine();
		
		Debug.println("reading in GIS layer", Debug.IMPORTANT);
		GISLayer gis = GISLayer.readFromFile(gisfile);
		Debug.println("shrinking GIS layer", Debug.IMPORTANT);
		int[][] shrunk = shrinkLayer(gis.categoricaldata, 4);
		//gis.shrink(2); //shrink it so that computation is tractable
		int latsteps = shrunk.length;
		int lonsteps = shrunk[0].length;
		Debug.println("There are "+latsteps+" latsteps and "+lonsteps+" longsteps in shrunk layer", Debug.IMPORTANT);
		
		Debug.println("read GIS layer, initializing data", Debug.IMPORTANT);
		int[][] indlayer = new int[latsteps][lonsteps];
		int[][] comlayer = new int[latsteps][lonsteps];
		double minlat = gis.getMinLat();
		double maxlat = gis.getMaxLat();
		double minlong = gis.getMinLong();
		double maxlong = gis.getMaxLong();
		double latstep = (maxlat-minlat)/latsteps;
		double lonstep = (maxlong-minlong)/lonsteps;

		//work out the radius of points we need to consider
		Debug.println("calculating radius", Debug.IMPORTANT);
		int r = 1;
		int rsquare = 1;
		while(true)
		{
			if(MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+r*latstep, minlong)/1000 > radius
				&& MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat, minlong+r*lonstep)/1000 > radius)
				break; //we are outside the circle
			if(MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+rsquare*latstep, minlong+rsquare*lonstep)/1000 > radius
					&& MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+rsquare*latstep, minlong+rsquare*lonstep)/1000 > radius)
				rsquare++;
			r++;
		}
		Debug.println("radius is "+r, Debug.IMPORTANT);
		
		
		//take a shortcut and pre-compute the distances. This
		//results in only slight innacuaries
		Debug.println("pre-computing distance matrix", Debug.IMPORTANT);
		double[][] distmatrix = new double[r][r];
		for(int i =0; i < r; i++)
			for(int j=0; j < r; j++)
				distmatrix[i][j] = MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+i*latstep, minlong+j*lonstep)/1000;
		
		
		
		//for each lat/long point, count all other points
		//within a particular radius, weighting for distance
		for(int lati = 0; lati < shrunk.length; lati ++)
		{
			for(int loni = 0; loni < shrunk[0].length; loni++)
			{
				double mincom = Double.MAX_VALUE;
				double minind = Double.MAX_VALUE;

				//sweep through the circle and count each square
				for(int i =-r+1; i < r; i++)
				{
					for(int j = -r+1; j < r; j++)
					{
						if(lati+i >= 0 && lati+i < shrunk.length &&
						   loni+j >= 0 && loni+j < shrunk[0].length)
						{
							double dist = 0.1; //min of 100 metres (the grid res)
							//we have precomputed distances
							if(i!=0 || j!=0)
								dist = Math.max(distmatrix[Math.abs(i)][Math.abs(j)], dist);
							
							//if(i!=0 || j!=0)
							//MathUtil.getDistanceBetweenPointsOnEarth(lat, lon, latr, lonr);
							//dist = dist / 1000.0;
							
							if(dist < radius)
							{
								if(shrunk[lati+i][loni+j] == 0)
									mincom = Math.min(mincom, dist);
								else if(shrunk[lati+i][loni+j] == 1)
									minind = Math.min(minind, dist); 
							}
						}
					}
				}
				
				//int[] ind = gis.getLatLongIndices(lat, lon);
				int comcat = 4;
				if(mincom < 0.2) comcat = 0;
				else if(mincom < 0.6) comcat = 1;
				else if(mincom < 1.2) comcat = 2;
				else if(mincom < 2) comcat = 3;
					

				int indcat = 4;
				if(minind < 0.2) indcat = 0;
				else if(minind < 0.6) indcat = 1;
				else if(minind < 1.2) indcat = 2;
				else if(minind < 2.0) indcat = 3;

				indlayer[lati][loni] = (byte) indcat; 
				comlayer[lati][loni] = (byte) comcat;
			}
			Debug.println("finished row at latitude "+lati+", "+(indlayer.length-lati-1)+" to go", Debug.IMPORTANT);
		}
		
		
		String[] cats = new String[] {"LT_200","LT_600","LT_1200","LT_2000","GT_2000"};
		
		//now save the two layers
		Debug.println("Saving layers to file", Debug.IMPORTANT);
		new GISLayer("INDUSTRIAL_PROX_CATEGORICAL", minlat, maxlat, minlong, maxlong, indlayer, cats).saveToFile("industrial_proximity_categorical.gis");
		new GISLayer("COMMERCIAL_PROX_CATEGORICAL", minlat, maxlat, minlong, maxlong, comlayer, cats).saveToFile("commercial_proximity_categorical.gis");
		
	}

	
	
	
	
	
	private static int[][] shrinkLayer(int[][] layer, int shrinkfactor)
	{
		//Debug.println("WARNING-- shrinking discards categories other than the first 2", Debug.IMPORTANT);
		if(layer.length % shrinkfactor != 0 ||
		   layer[0].length % shrinkfactor != 0)
			throw new RuntimeException("Cannot shrink layer. Not exactly divisible");
		int[][] res = new int[layer.length/shrinkfactor][layer[0].length/shrinkfactor];
		for(int i=0; i < layer.length; i+= shrinkfactor)
		{
			for(int j = 0; j < layer[0].length; j+= shrinkfactor)
			{
				//take the majority
				int[] count = new int[Byte.MAX_VALUE];
				for(int k=0; k < shrinkfactor; k++)
					for(int l = 0; l < shrinkfactor; l++)
					{
						int cat = layer[i+k][j+l];
						if(cat >= 0) //dont count 'dont knows'
							count[cat]++;
					}
				if(Util.getSum(count) > 0)
					res[i/shrinkfactor][j/shrinkfactor] = (byte) Util.getMaxIndex(count);
				else
					res[i/shrinkfactor][j/shrinkfactor] = -1;
			}
		}
		return res;
	}

	
	
	private static float[][] shrinkLayer(float[][] layer, int shrinkfactor)
	{
		if(layer.length % shrinkfactor != 0 ||
		   layer[0].length % shrinkfactor != 0)
			throw new RuntimeException("Cannot shrink layer. Not exactly divisible");
		float[][] res = new float[layer.length/shrinkfactor][layer[0].length/shrinkfactor];
		for(int i=0; i < layer.length; i+= shrinkfactor)
		{
			for(int j = 0; j < layer[0].length; j+= shrinkfactor)
			{
				double sum = 0.0;
				//just sum them all
				for(int k=0; k < shrinkfactor; k++)
					for(int l = 0; l < shrinkfactor; l++)
						sum += layer[i+k][j+l];
				res[i/shrinkfactor][j/shrinkfactor] = (float) sum;
			}
		}
		return res;
	}
	
	private static void normalize(float[][] layer)
	{
		double sum = 0.0;
		
		for(int i = 0; i < layer.length; i++)
			for(int j = 0; j < layer[0].length; j++)
				sum += layer[i][j];
		
		sum = sum/(layer.length*layer[0].length);
		
		for(int i = 0; i < layer.length; i++)
			for(int j = 0; j < layer[0].length; j++)
				layer[i][j] /= sum;

		
	}
	
	
	
	
	
	
	public static void createTreeAndGrassCoverageLayers() throws Exception
	{
		GISLayer tree = GISLayer.readFromFile("tree_combined.gis");
		GISLayer grass = GISLayer.readFromFile("grass_combined.gis");
		
		createTreeAndGrassCoverageLayer(tree, 0.1, "TREE_COVERAGE_100m");
		createTreeAndGrassCoverageLayer(tree, 0.5, "TREE_COVERAGE_500m");
		createTreeAndGrassCoverageLayer(tree, 1.0, "TREE_COVERAGE_1km");

		
		createTreeAndGrassCoverageLayer(grass, 0.1, "GRASS_COVERAGE_100m");
		createTreeAndGrassCoverageLayer(grass, 0.5, "GRASS_COVERAGE_500m");
		createTreeAndGrassCoverageLayer(grass, 1.0, "GRASS_COVERAGE_1km");		

		
	}
	


	
	public static void createTreeAndGrassCoverageLayer(GISLayer layer1,  
			double radius, String name) throws Exception
	{
		int shrinkfact = 2;
		short[][] count = new short[layer1.getLatSteps()/shrinkfact][layer1.getLongSteps()/shrinkfact];
		
		if(shrinkfact != 2)
			throw new RuntimeException("Shrinkfact is not 2. This will break te code directly below.");

		
		int latsteps = count.length;
		int lonsteps = count[0].length;
		Debug.println("There are "+latsteps+" latsteps and "+lonsteps+" longsteps in shrunk layer", Debug.IMPORTANT);
		
		Debug.println("Initializing data", Debug.IMPORTANT);
		float[][] coveragelayer = new float[latsteps][lonsteps];
		double minlat = layer1.getMinLat();
		double maxlat = layer1.getMaxLat();
		double minlong = layer1.getMinLong();
		double maxlong = layer1.getMaxLong();
		double latstep = (maxlat-minlat)/latsteps;
		double lonstep = (maxlong-minlong)/lonsteps;

		
		for(int i = 0; i < count.length; i++)
			for(int j = 0; j < count[0].length; j++)
			{
				if(layer1.binarycategoricaldata[i*2][j*2])
					count[i][j]++;
				if(layer1.binarycategoricaldata[i*2][j*2+1])
					count[i][j]++;
				if(layer1.binarycategoricaldata[i*2+1][j*2])
					count[i][j]++;
				if(layer1.binarycategoricaldata[i*2+1][j*2+1])
					count[i][j]++;
			}
				
		//work out the radius of points we need to consider
		Debug.println("calculating radius", Debug.IMPORTANT);
		int r = 1;
		int rsquare = 1;
		while(true)
		{
			if(MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+r*latstep, minlong)/1000 > radius
				&& MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat, minlong+r*lonstep)/1000 > radius)
				break; //we are outside the circle
			if(MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+rsquare*latstep, minlong+rsquare*lonstep)/1000 > radius
					&& MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+rsquare*latstep, minlong+rsquare*lonstep)/1000 > radius)
				rsquare++;
			r++;
		}
		Debug.println("radius is "+r, Debug.IMPORTANT);
		
		
		//take a shortcut and pre-compute the distances. This
		//results in only slight innaccuaries
		Debug.println("pre-computing distance matrix", Debug.IMPORTANT);
		double[][] distmatrix = new double[r][r];
		for(int i =0; i < r; i++)
			for(int j=0; j < r; j++)
				distmatrix[i][j] = MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+i*latstep, minlong+j*lonstep)/1000;
		
		double latdist = MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+latstep, minlong)/1000.0;
		double londist = MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat, minlong+lonstep)/1000.0;
		double areaperhex = latdist*londist; //sq km per hex 
		
		
		//for each lat/long point, work out area of 
		//major road or freeway or minor road within 1 km
		for(int lati = 0; lati < count.length; lati ++)
		{
			for(int loni = 0; loni < count[0].length; loni++)
			{
				double countd = 0; //# of square km of grass/trees within 1km radius
				
				//sweep through the circle and count each square
				for(int i =-r+1; i < r; i++)
				{
					for(int j = -r+1; j < r; j++)
					{
						if(lati+i >= 0 && lati+i < count.length &&
						   loni+j >= 0 && loni+j < count[0].length)
						{
							double dist = 0.05; //min of 50 metres (the grid res)
							//we have precomputed distances
							if(i!=0 || j!=0)
								dist = Math.max(distmatrix[Math.abs(i)][Math.abs(j)], dist);
							
							
							if(dist < radius)
							{
								//some grass or tree within radius
								if(count[lati+i][loni+j] > 0)
									countd += (areaperhex*count[lati+i][loni+j])/(shrinkfact*shrinkfact);
							}
						}
					}	
				}
				
				coveragelayer[lati][loni] = (float) countd;

			}
			Debug.println("finished row at latitude "+lati+", "+(coveragelayer.length-lati-1)+" to go", Debug.IMPORTANT);
		}
		
		//now save 
		Debug.println("Saving layer to file", Debug.IMPORTANT);
		new GISLayer(name, minlat, maxlat, minlong, maxlong, 
				coveragelayer).saveToFile(name+".gis");
				
	}
	

	
	//
	public static void createCombinedGreenSpaceLayer_stage1() throws Exception
	{
		GISLayer green1 = GISLayer.readFromFile("green1.gis");
		GISLayer green2 = GISLayer.readFromFile("green2.gis");
		GISLayer green3 = GISLayer.readFromFile("green3.gis");
		
		Debug.println("Finished reading greenspace layers, now processing", Debug.INFO);
		
		//create combined greenspace layer 
		//(best 2 out of 3 of greenspace layers)
		boolean[][] combgreen = green1.binarycategoricaldata;
		
		for(int i =0; i < combgreen.length; i++)
		{
			for(int j = 0; j < green1.getLongSteps(); j++)
			{
				int count = 0;
				if(green1.binarycategoricaldata[i][j]) count++;
				if(green2.binarycategoricaldata[i][j]) count++;
				if(count == 2) {
					combgreen[i][j] = true;
					continue;
				}
				if(green3.binarycategoricaldata[i][j]) count++;
				if(count >= 2)
					combgreen[i][j] = true;
				else
					combgreen[i][j]= false;
			}
			green2.binarycategoricaldata[i] = null;
			green3.binarycategoricaldata[i] = null;
			Debug.println("Finished lat row "+i+" of "+combgreen.length, Debug.INFO);
		}
		
		green2 = null; green3 = null; System.gc();
		Debug.println("Finished combining greenspace layers, saving to file", Debug.INFO);
		green1.saveToFile("green123combined.gis");
	}
	
	
	public static void createCombinedGreenSpaceLayer_stage1andahalf() throws Exception
	{
		GISLayer green123 = GISLayer.readFromFile("green123combined.gis");

		//ok, now go through and add the treeother layer
		GISLayer treeother = GISLayer.readFromFile("treeother.gis");
		
		for(int i =0; i < green123.binarycategoricaldata.length; i++)
		{
			for(int j = 0; j < green123.binarycategoricaldata[0].length; j++)
			{
				if(treeother.binarycategoricaldata[i][j])
					green123.binarycategoricaldata[i][j] = true;
			}
			treeother.binarycategoricaldata[i] = null;
		}
		
		green123.saveToFile("green123andtreeother_combined.gis");

		
	}
	
	public static void createCombinedGreenSpaceLayer_stage1andthreequarters() throws Exception
	{
		GISLayer green = GISLayer.readFromFile("green123andtreeother_combined.gis");
		addInNationalParksAndStateForests(green);
		green.saveToFile("green123andtreeother_combined_withnpsf.gis");
	}
	
	

	
	public static void maskOutWater(GISLayer layer) throws Exception
	{
		GISLayer water1 = GISLayer.readFromFile("water.gis");
		maskOut(layer, water1);
		water1 = null; System.gc();
		GISLayer water2 = GISLayer.readFromFile("water_fromiplanelev.gis");
		maskOut(layer, water2);		
		water2 = null; System.gc();
	}

	
	
	public static void createCombinedGreenSpaceLayer_stage2() throws Exception
	{
		GISLayer green123 = GISLayer.readFromFile("green123andtreeother_combined_withnpsf.gis");
		//GISLayer green123 = GISLayer.readFromFile("green123andtreeother_combined.gis");
		//GISLayer green123 = GISLayer.readFromFile("green123combined.gis");
		
		
		
		//ok, now we need to get rid of roads and water
		//do water first
		maskOutWater(green123);
				
		Debug.println("Masked out water.", Debug.INFO);
		
		green123.saveToFile("green123_nowater.gis");
	}
		
	
	
	public static boolean[][] expandLayer(boolean[][] layerdata)
	{
		boolean[][] newdata = new boolean[layerdata.length][layerdata[0].length];
		
		for(int i =0; i < layerdata.length; i++)
		{
			jloop: for(int j =0; j < layerdata[0].length; j++)
			{
				for(int k = -1; k <= 1; k++)
					for(int l = -1; l <= 1; l++)
						if((i+k) >= 0 && (i+k) < layerdata.length && 
						   (j+l) >= 0 && (j+l) < layerdata[0].length &&
						   layerdata[i+k][j+l])
						{
							newdata[i][j] = true;
							continue jloop;
						}
			}
			if(i > 0)
				layerdata[i-1] = null;
		}
		return newdata;
	}
	
	
	public static void createCombinedGreenSpaceLayer_stage3() throws Exception
	{
		//get the tree/other layer and expand it by 1 pixel
		Debug.println("Reading treeother layer.", Debug.INFO);
		GISLayer treeother = GISLayer.readFromFile("treeother.gis");
		Debug.println("Expanding trees.", Debug.INFO);
		boolean[][] treeotherexp = expandLayer(treeother.binarycategoricaldata);
		treeother.binarycategoricaldata = treeotherexp;
		System.gc();
		Debug.println("Finished expanding, saving to file.", Debug.INFO);
		treeother.saveToFile("treeother_expanded.gis");
	}

	
	public static void createCombinedGreenSpaceLayer_stage4() throws Exception
	{
		GISLayer treeother = GISLayer.readFromFile("treeother_expanded.gis");
		treeother.shrink(2);
		System.gc(); 
		
		Debug.println("Creating transport layer that does not mask out on-road trees", Debug.INFO);
		//now go through and mask treeother out of the roads layer
		//so that we have a (roads-trees) layer
		GISLayer transport = GISLayer.readFromFile("roadrail.gis");
		maskOut(transport, treeother);
		
		transport.saveToFile("transport_minustrees.gis");
	}
	
	
		
	public static void createCombinedGreenSpaceLayer_stage5() throws Exception
	{
		GISLayer transport = GISLayer.readFromFile("transport_minustrees.gis");
		GISLayer green = GISLayer.readFromFile("green123_nowater.gis");
		
		Debug.println("OK, now masking out roads from greenspace (except for valid trees that overhang roads)", Debug.INFO);
		//ok, transport is now (transport-trees). Mask it out of the
		//greenspace layer
		maskOut(green, transport);
		
		Debug.println("Done, now saving to file", Debug.INFO);
		//we are done (actually we should mask in national parks and state
		//forests, but do this later).
		green.saveToFile("greenspace.gis");
	}

	
	
	
	public static void createTreeLayer_stage1() throws Exception
	{
		GISLayer tree1 = GISLayer.readFromFile("tree3.gis");
		GISLayer tree2 = GISLayer.readFromFile("treeother_expanded.gis");
		//combine them
		for(int i =0; i < tree1.binarycategoricaldata.length; i++)
			for(int j = 0; j < tree1.binarycategoricaldata[0].length; j++)
				if(tree2.binarycategoricaldata[i][j])
					tree1.binarycategoricaldata[i][j] = true;
		
		tree1.saveToFile("treecombined.gis");
	}
	
	
	public static void addInNationalParksAndStateForests(GISLayer layer) throws Exception
	{
		GISLayer natpark = GISLayer.readFromFile("nationalpark.gis");
		layer.maskIn(natpark);
		natpark = null; System.gc();
		GISLayer stateforest = GISLayer.readFromFile("stateforest.gis");
		layer.maskIn(stateforest);
		stateforest = null; System.gc();
	}

	public static void createTreeLayer_stage2() throws Exception
	{
		GISLayer tree = GISLayer.readFromFile("treecombined.gis");
		addInNationalParksAndStateForests(tree);
		tree.saveToFile("treecombined_andnpsf.gis");
	}

	
	public static void createTreeLayer_stage3() throws Exception
	{
		GISLayer tree = GISLayer.readFromFile("treecombined_andnpsf.gis");
		maskOutWater(tree);
		
		//now mask out roads
		GISLayer transport = GISLayer.readFromFile("transport_minustrees.gis");
		
		maskOut(tree, transport);
		
		tree.saveToFile("trees.gis");
	}
	
	
	
	public static void createCombinedWaterLayer() throws Exception
	{
		GISLayer water1 = GISLayer.readFromFile("water.gis");
		GISLayer water2 = GISLayer.readFromFile("water_fromiplanelev.gis");
		
	}
	
	
	//create a layer which has all parks segmented by roads.
	//this is basically the greenspace layer with all the roads 
	//masked out.
	public static void createParksLayer() throws Exception
	{
		GISLayer greenspace = GISLayer.readFromFile("greenspace.gis");
		greenspace.shrink(2);
		System.gc();
		GISLayer transport = GISLayer.readFromFile("roadrail.gis");
		transport.binarycategoricaldata = expandLayer(transport.binarycategoricaldata);
		System.gc();
		
		//now remove transport from greenspace
		maskOut(greenspace, transport);

		
		greenspace.shrink(2);
		System.gc();
				
		//ok, now save parks layer
		greenspace.saveToFile("fragmentedgreenspace.gis");
	}
	
	
	
	
	
	
	
	
	
	
	//mask out everywhere where the mask layer is > 0
	//(or is true, in the case of binary layers)
	public static void maskOut(GISLayer layer1,	GISLayer mask) throws Exception
	{
		int latsteps = layer1.getLatSteps();
		int lonsteps = layer1.getLongSteps();
		
		double minlat = layer1.getMinLat();
		double maxlat = layer1.getMaxLat();
		double minlong = layer1.getMinLong();
		double maxlong = layer1.getMaxLong();
		double latstep = (maxlat-minlat)/latsteps;
		double lonstep = (maxlong-minlong)/lonsteps;

		
		for(int i = 0; i < latsteps; i++)
		{
			double lat = minlat+i*latstep+latstep/2;
			for(int j = 0; j < lonsteps; j++)
			{
				//check water layers. if they say it is water, we mask out trees/grass
				double lon = minlong+j*lonstep+lonstep/2;
				if(mask.getValue(lat, lon) > 0.0f)
				{
					if(layer1.binarycategoricaldata != null)
						layer1.binarycategoricaldata[i][j]= false;
					else if(layer1.categoricaldata != null)
						layer1.categoricaldata[i][j] = -1;
					else 
						layer1.continuousdata[i][j] = Float.NaN;
				}
			}
		}
	}

	

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	//	total road area within 1 km radius.
	public static void createRoadCoverageLayer(String gisfile) throws Exception
	{
		//beyond this radius, things dont matter anymore
		double radius = 1.0; //1.0 km
		
		//BufferedReader console = new java.io.BufferedReader(new InputStreamReader(System.in));
		//System.out.print("Enter GIS layer with road info: ");
		//String gisfile = console.readLine();
		
		Debug.println("reading in GIS layer", Debug.IMPORTANT);
		GISLayer gis = GISLayer.readFromFile(gisfile);
		Debug.println("shrinking GIS layer", Debug.IMPORTANT);
		int[][] shrunk = shrinkLayer(gis.categoricaldata, 2);
		//gis.shrink(2); //shrink it so that computation is tractable
		int latsteps = shrunk.length;
		int lonsteps = shrunk[0].length;
		Debug.println("There are "+latsteps+" latsteps and "+lonsteps+" longsteps in shrunk layer", Debug.IMPORTANT);
		
		Debug.println("read GIS layer, initializing data", Debug.IMPORTANT);
		float[][] roadlayer = new float[latsteps][lonsteps];
		double minlat = gis.getMinLat();
		double maxlat = gis.getMaxLat();
		double minlong = gis.getMinLong();
		double maxlong = gis.getMaxLong();
		double latstep = (maxlat-minlat)/latsteps;
		double lonstep = (maxlong-minlong)/lonsteps;

		//work out the radius of points we need to consider
		Debug.println("calculating radius", Debug.IMPORTANT);
		int r = 1;
		int rsquare = 1;
		while(true)
		{
			if(MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+r*latstep, minlong)/1000 > radius
				&& MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat, minlong+r*lonstep)/1000 > radius)
				break; //we are outside the circle
			if(MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+rsquare*latstep, minlong+rsquare*lonstep)/1000 > radius
					&& MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+rsquare*latstep, minlong+rsquare*lonstep)/1000 > radius)
				rsquare++;
			r++;
		}
		Debug.println("radius is "+r, Debug.IMPORTANT);
		
		
		//take a shortcut and pre-compute the distances. This
		//results in only slight innaccuaries
		Debug.println("pre-computing distance matrix", Debug.IMPORTANT);
		double[][] distmatrix = new double[r][r];
		for(int i =0; i < r; i++)
			for(int j=0; j < r; j++)
				distmatrix[i][j] = MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+i*latstep, minlong+j*lonstep)/1000;
		
		double latdist = MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+latstep, minlong)/1000.0;
		double londist = MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat, minlong+lonstep)/1000.0;
		double areaperhex = latdist*londist; //sq km per hex 
		
		
		//for each lat/long point, work out area of 
		//major road or freeway or minor road within 1 km
		for(int lati = 0; lati < shrunk.length; lati ++)
		{
			for(int loni = 0; loni < shrunk[0].length; loni++)
			{
				double count = 0; //# of square km of road within 1km radius
				double totalcount = 0; //#total area within approximate circle we are calculating
				
				//sweep through the circle and count each square
				for(int i =-r+1; i < r; i++)
				{
					for(int j = -r+1; j < r; j++)
					{
						if(lati+i >= 0 && lati+i < shrunk.length &&
						   loni+j >= 0 && loni+j < shrunk[0].length)
						{
							double dist = 0.05; //min of 50 metres (the grid res)
							//we have precomputed distances
							if(i!=0 || j!=0)
								dist = Math.max(distmatrix[Math.abs(i)][Math.abs(j)], dist);
							
							//if(i!=0 || j!=0)
							//MathUtil.getDistanceBetweenPointsOnEarth(lat, lon, latr, lonr);
							//dist = dist / 1000.0;
							
							if(dist < radius)
							{
								totalcount += areaperhex;
								//major road or highway/freeway or minor road
								if(shrunk[lati+i][loni+j] == 0 || shrunk[lati+i][loni+j] == 1 || shrunk[lati+i][loni+j] == 3)
									count += areaperhex;
							}
						}
					}	
				}
				
				//int[] ind = gis.getLatLongIndices(lat, lon);
				roadlayer[lati][loni] = (float) (count/totalcount);

			}
			Debug.println("finished row at latitude "+lati+", "+(roadlayer.length-lati-1)+" to go", Debug.IMPORTANT);
		}
		

		
		//now save 
		Debug.println("Saving layer to file", Debug.IMPORTANT);
		new GISLayer("ROAD_COVERAGE_1km", minlat, maxlat, minlong, maxlong, 
				roadlayer).saveToFile("road_coverage_1km.gis");
		
	}
	
	

	
	
	
	
	
	
	
	
	//categorical proximity to main roads
	public static void createCategoricalRoadLayer(String gisfile) throws Exception
	{
		//beyond this radius, things dont matter anymore
		double radius = 1.0; //1.0 km
		
		//BufferedReader console = new java.io.BufferedReader(new InputStreamReader(System.in));
		//System.out.print("Enter GIS layer with road info: ");
		//String gisfile = console.readLine();
		
		Debug.println("reading in GIS layer", Debug.IMPORTANT);
		GISLayer gis = GISLayer.readFromFile(gisfile);
		Debug.println("shrinking GIS layer", Debug.IMPORTANT);
		int[][] shrunk = shrinkLayer(gis.categoricaldata, 2);
		//gis.shrink(2); //shrink it so that computation is tractable
		int latsteps = shrunk.length;
		int lonsteps = shrunk[0].length;
		Debug.println("There are "+latsteps+" latsteps and "+lonsteps+" longsteps in shrunk layer", Debug.IMPORTANT);
		
		Debug.println("read GIS layer, initializing data", Debug.IMPORTANT);
		int[][] roadlayer = new int[latsteps][lonsteps];
		double minlat = gis.getMinLat();
		double maxlat = gis.getMaxLat();
		double minlong = gis.getMinLong();
		double maxlong = gis.getMaxLong();
		double latstep = (maxlat-minlat)/latsteps;
		double lonstep = (maxlong-minlong)/lonsteps;

		//work out the radius of points we need to consider
		Debug.println("calculating radius", Debug.IMPORTANT);
		int r = 1;
		int rsquare = 1;
		while(true)
		{
			if(MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+r*latstep, minlong)/1000 > radius
				&& MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat, minlong+r*lonstep)/1000 > radius)
				break; //we are outside the circle
			if(MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+rsquare*latstep, minlong+rsquare*lonstep)/1000 > radius
					&& MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+rsquare*latstep, minlong+rsquare*lonstep)/1000 > radius)
				rsquare++;
			r++;
		}
		Debug.println("radius is "+r, Debug.IMPORTANT);
		
		
		//take a shortcut and pre-compute the distances. This
		//results in only slight innaccuaries
		Debug.println("pre-computing distance matrix", Debug.IMPORTANT);
		double[][] distmatrix = new double[r][r];
		for(int i =0; i < r; i++)
			for(int j=0; j < r; j++)
				distmatrix[i][j] = MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+i*latstep, minlong+j*lonstep)/1000;
		
		
		
		//for each lat/long point, work out whether there is a 
		//major road or freeway within certain rages
		for(int lati = 0; lati < shrunk.length; lati ++)
		{
			for(int loni = 0; loni < shrunk[0].length; loni++)
			{
				double mindist = Double.MAX_VALUE; //distance to closest main road
				
				//sweep through the circle and count each square
				for(int i =-r+1; i < r; i++)
				{
					for(int j = -r+1; j < r; j++)
					{
						if(lati+i >= 0 && lati+i < shrunk.length &&
						   loni+j >= 0 && loni+j < shrunk[0].length)
						{
							double dist = 0.05; //min of 50 metres (the grid res)
							//we have precomputed distances
							if(i!=0 || j!=0)
								dist = Math.max(distmatrix[Math.abs(i)][Math.abs(j)], dist);
							
							//if(i!=0 || j!=0)
							//MathUtil.getDistanceBetweenPointsOnEarth(lat, lon, latr, lonr);
							//dist = dist / 1000.0;
							
							if(dist < radius)
							{
								//major road or highway/freeway
								if(shrunk[lati+i][loni+j] == 0 || shrunk[lati+i][loni+j] == 1)
									mindist = Math.min(dist, mindist);
							}
						}
					}	
				}
				
				//int[] ind = gis.getLatLongIndices(lat, lon);
				int distcat = 4; //default is 1km +
				if(mindist <= 0.1) distcat = 0; //within 100m
				else if(mindist <= 0.3) distcat = 1;
				else if(mindist <= 0.6) distcat = 2;
				else if(mindist <= 1.0) distcat = 3;
				roadlayer[lati][loni] = (byte) distcat;

			}
			Debug.println("finished row at latitude "+lati+", "+(roadlayer.length-lati-1)+" to go", Debug.IMPORTANT);
		}
		
		
		String[] catnames = new String[] {"LT_100m","LT_300m","LT_600m","LT_1000m","GT_1000m"};
		
		//now save 
		Debug.println("Saving layer to file", Debug.IMPORTANT);
		new GISLayer("ROAD_PROXIMITY_CATEGORICAL", minlat, maxlat, minlong, maxlong, 
				roadlayer, catnames).saveToFile("road_proximity_categorical.gis");
		
	}
	
	
	
	//proximity to main roads
	public static void createRoadLayer(String gisfile) throws Exception
	{
		//beyond this radius, things dont matter anymore
		double radius = 1.0; //1.0 km
		
		//BufferedReader console = new java.io.BufferedReader(new InputStreamReader(System.in));
		//System.out.print("Enter GIS layer with road info: ");
		//String gisfile = console.readLine();
		
		Debug.println("reading in GIS layer", Debug.IMPORTANT);
		GISLayer gis = GISLayer.readFromFile(gisfile);
		Debug.println("shrinking GIS layer", Debug.IMPORTANT);
		int[][] shrunk = shrinkLayer(gis.categoricaldata, 2);
		//gis.shrink(2); //shrink it so that computation is tractable
		int latsteps = shrunk.length;
		int lonsteps = shrunk[0].length;
		Debug.println("There are "+latsteps+" latsteps and "+lonsteps+" longsteps in shrunk layer", Debug.IMPORTANT);
		
		Debug.println("read GIS layer, initializing data", Debug.IMPORTANT);
		float[][] roadlayer = new float[latsteps][lonsteps];
		double minlat = gis.getMinLat();
		double maxlat = gis.getMaxLat();
		double minlong = gis.getMinLong();
		double maxlong = gis.getMaxLong();
		double latstep = (maxlat-minlat)/latsteps;
		double lonstep = (maxlong-minlong)/lonsteps;

		//work out the radius of points we need to consider
		Debug.println("calculating radius", Debug.IMPORTANT);
		int r = 1;
		int rsquare = 1;
		while(true)
		{
			if(MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+r*latstep, minlong)/1000 > radius
				&& MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat, minlong+r*lonstep)/1000 > radius)
				break; //we are outside the circle
			if(MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+rsquare*latstep, minlong+rsquare*lonstep)/1000 > radius
					&& MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+rsquare*latstep, minlong+rsquare*lonstep)/1000 > radius)
				rsquare++;
			r++;
		}
		Debug.println("radius is "+r, Debug.IMPORTANT);
		
		
		//take a shortcut and pre-compute the distances. This
		//results in only slight innacuaries
		Debug.println("pre-computing distance matrix", Debug.IMPORTANT);
		double[][] distmatrix = new double[r][r];
		for(int i =0; i < r; i++)
			for(int j=0; j < r; j++)
				distmatrix[i][j] = MathUtil.getDistanceBetweenPointsOnEarth(minlat, minlong, minlat+i*latstep, minlong+j*lonstep)/1000;
		
		
		
		//for each lat/long point, count all other points
		//within a particular radius, weighting for distance
		for(int lati = 0; lati < shrunk.length; lati ++)
		{
			for(int loni = 0; loni < shrunk[0].length; loni++)
			{
				double roadsum = 0.0;
				//sweep through the circle and count each square
				for(int i =-r+1; i < r; i++)
				{
					for(int j = -r+1; j < r; j++)
					{
						if(lati+i >= 0 && lati+i < shrunk.length &&
						   loni+j >= 0 && loni+j < shrunk[0].length)
						{
							double dist = 0.05; //min of 50 metres (the grid res)
							//we have precomputed distances
							if(i!=0 || j!=0)
								dist = Math.max(distmatrix[Math.abs(i)][Math.abs(j)], dist);
							
							//if(i!=0 || j!=0)
							//MathUtil.getDistanceBetweenPointsOnEarth(lat, lon, latr, lonr);
							//dist = dist / 1000.0;
							
							if(dist < radius)
							{
								//major road or highway/freeway
								if(shrunk[lati+i][loni+j] == 0 || shrunk[lati+i][loni+j] == 1)
									roadsum += 1/(dist*dist);
							}
						}
					}
				}
				
				//int[] ind = gis.getLatLongIndices(lat, lon);
				roadlayer[lati][loni] = (float) roadsum;

			}
			Debug.println("finished row at latitude "+lati+", "+(roadlayer.length-lati-1)+" to go", Debug.IMPORTANT);
		}
		
		//now we shrink layers
		//Debug.println("Shrinking layers", Debug.IMPORTANT);
		//indlayer = shrinkLayer(indlayer, 2);
		//comlayer = shrinkLayer(indlayer, 2);
		
		//now we normalize the two layers
		Debug.println("Normalizing layer", Debug.IMPORTANT);
		normalize(roadlayer);

		
		//now save the two layers
		Debug.println("Saving layer to file", Debug.IMPORTANT);
		new GISLayer("ROAD_PROXIMITY", minlat, maxlat, minlong, maxlong, roadlayer).saveToFile("road_proximity.gis");
		
	}
	
	
	
	/* Given a specified spatial aggregation
	 * (must be a GIS layer that is either categorical
	 * or continuous. If continuous, each distinct value
	 * is treated as a category), calculate the average
	 * value in each spatial region. A second GIS layer
	 * must be provided with the actual data. 
	 * 
	 * It only makes sense to do this if the second
	 * layer has data that can be interpreted as something
	 * like 'units of BLAH per unit area'. Housing density
	 * or population density are good examples that will work
	 */
	public static void createAggregateLayer(String gisregions, String gisdata) throws Exception
	{
		GISLayer regionlayer = GISLayer.readFromFile(gisregions);
		GISLayer datalayer = GISLayer.readFromFile(gisdata);

		
		//first we work out how many regions there are.
		//We keep a map of regionid (int) to 
		//a 2 element array with the first element being the
		//total and the second element being the number
		//of contributing values to that sum
		//HashMap map = new HashMap();
		
		double minlat = regionlayer.getMinLat();
		double maxlat = regionlayer.getMaxLat();
		double minlong = regionlayer.getMinLong();
		double maxlong = regionlayer.getMaxLong();
		double latstep = (maxlat-minlat)/regionlayer.getLatSteps();
		double lonstep = (maxlong-minlong)/regionlayer.getLongSteps();
		int rowcount = 1;
		int maxregions = (int) (regionlayer.getMaxVal()+1);
		double[] sums = new double[maxregions];
		double[] counts = new double[maxregions]; 
		for(double lat = minlat+latstep/2; lat < maxlat; lat += latstep) 
		{
			for(double lon = minlong + lonstep/2; lon < maxlong; lon += lonstep)
			{	
				float val = Float.NaN;
				if(regionlayer.categoricaldata != null) {
					val = regionlayer.getValue(lat, lon);
					if(val < 0) val = Float.NaN;
				}
				else
					val = regionlayer.getValue(lat, lon);
				
				if(Float.isNaN(val)) //ignore regions without an id
					continue;
				
				int regionid = (int) val;
				
				//Integer regionid = new Integer((int)Math.round(val));

				float datval = datalayer.getValue(lat, lon);
				if(Float.isNaN(datval)) 
					continue; //ignore regions without data
				
				sums[regionid] += datval;
				counts[regionid] += 1;
				
				//double[] v = (double[]) map.get(regionid);
				//if(v == null) {
				//	v = new double[2];
				//	map.put(regionid, v);
				//}
				
				//v[0] = v[0] + datval;
				//v[1] = v[1] + 1;
			}
			Debug.println("scanned values in row "+rowcount+" of "+regionlayer.getLatSteps(), Debug.INFO);
			rowcount++;
		}
		
		/*if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
		{
			int keys = map.keySet().size();
			int vals = map.values().size();
			if(keys != vals) 
				throw new RuntimeException("#keys and #vals do not match");
			Debug.println("There are "+keys+" regions in thhe region layer", Debug.INFO);
		}*/
		
		
		
		//work out the averages
		/*Iterator valit = map.values().iterator();
		while(valit.hasNext()) 
		{
			double[] v = (double[]) valit.next();
			if(v[1] > 0.0)
				v[0] = v[0] / v[1];
			else
				Debug.println("Region has no entries... this is a bit strange... setting average to 0", Debug.IMPORTANT);
		}*/
		for(int i =0; i < sums.length; i++)
			if(counts[i] > 0)
				sums[i] = sums[i]/counts[i];
		
		
		
		
		//ok, we have the regions and the average for each region.
		//now just create a new layer
		Debug.println("Worked out averages... initializing new layer", Debug.INFO);
		float[][] newdata = new float[regionlayer.getLatSteps()][regionlayer.getLongSteps()];
		int lati = 0;
		for(double lat = minlat+latstep/2; lat < maxlat; lat += latstep, lati++) 
		{
			int loni = 0;
			for(double lon = minlong + lonstep/2; lon < maxlong; lon += lonstep, loni++)
			{
				//ok, get the region
				float val = regionlayer.getValue(lat, lon);
				//what do we do with NaN's??
				//just preserve them I guess
				if(!Float.isNaN(val)) 
				{
					int regionid = (int) Math.round(val);
					//Integer regionid = new Integer((int)Math.round(val));
					//double avgval = ((double[]) map.get(regionid))[0];
					//val = (float) avgval;
					val = (float) sums[regionid];
				}
				newdata[lati][loni] = val;
			}
		}
		String savefile = "avgdata.gis";
		Debug.println("Saving averaged data to file "+savefile, Debug.IMPORTANT);
		new GISLayer("name", minlat, maxlat, minlong, maxlong, newdata).saveToFile(savefile);
		
	}
	
	
	
	
	
	
	/** Create a layer that specifies membership into regions
	 *  (CD's, TZ's, postcodes, whatever).
	 *  
	 * 
	 * 
	 * @param kmlfile
	 * @param excludeRegionsWithCentroidOutsideSydneyBoundingBox
	 * @throws Exception
	 */
	private static void createCDLayer(
			String kmlfile, boolean excludeRegionsWithCentroidOutsideSydneyBoundingBox) 
	throws Exception
	{
		int steps = 500;
		GoogleEarthPolygon[] polys = GoogleEarthUtils.getPolygonsFromKMLFile(kmlfile, "name");

		
		double minlat = Double.POSITIVE_INFINITY;
		double maxlat = Double.NEGATIVE_INFINITY;
		double minlong = Double.POSITIVE_INFINITY;
		double maxlong = Double.NEGATIVE_INFINITY;
		minlat = -34.10141604662701;
		maxlat = -33.54141728667299;
		minlong = 150.65141715537853;
		maxlong = 151.3614161776215;
		
		//small area for testing
		//minlat = -33.93;
		//maxlat = -33.86;
		//minlong = 151.00;
		//maxlong = 151.1;

		ArrayList newcentroids = new ArrayList();
		for(int i =0; i < polys.length; i++)
		{
			double[] centroid = polys[i].getCentroid();
			Debug.println("centroid is "+centroid[0]+" "+centroid[1], Debug.IMPORTANT);
			//Debug.println("CD "+polys[i].name+" has area "+polys[i].getArea(), Debug.IMPORTANT);
			if(polys[i].name.charAt(0) == '"')
				polys[i].name = polys[i].name.substring(1, polys[i].name.length()-1);
			
			if(excludeRegionsWithCentroidOutsideSydneyBoundingBox)
			{
				if(centroid[0] > minlat && centroid[0] < maxlat &&
				   centroid[1] > minlong && centroid[1] < maxlong)
					newcentroids.add(polys[i]);
			}
			else
				newcentroids.add(polys[i]);
		}
		GoogleEarthPolygon[] newpolys = new GoogleEarthPolygon[newcentroids.size()];
		for(int i =0; i < newpolys.length; i++)
			newpolys[i] = (GoogleEarthPolygon) newcentroids.get(i);

		polys = newpolys;
		Debug.println("There are "+newpolys.length+" valid districts", Debug.IMPORTANT);
		
		/*
		for(int i = 0; i < polys.length; i++)
		{
			//int cd = Integer.parseInt(polys[i].name);
			//if(cd < mincdval) mincdval = cd;
			//if(cd > maxcdval) maxcdval = cd;
			
			for(int j = 0; j < polys[i].latlongpoints.length; j++)
			{
				double lat = polys[i].latlongpoints[j][0];
				double lon = polys[i].latlongpoints[j][1];
				if(lat < minlat) minlat = lat;
				if(lat > maxlat) maxlat = lat;
				if(lon < minlong) minlong = lon;
				if(lon > maxlong) maxlong = lon;
				

			}
		}*/
		

		//remember which CDs have member points
		boolean[] hasmember = new boolean[polys.length];
		
		float[][] data = new float[steps][steps];
		double latstep = (maxlat-minlat)/steps;
		double longstep = (maxlong-minlong)/steps;
		int lati = 0;
		for(double lat = minlat+latstep/2; lat < maxlat; lat+=latstep, lati++)
		{
			int failedtofind = 0;
			Debug.println("Starting lat row "+(lati+1)+" of "+steps, Debug.IMPORTANT);
			int loni = 0;
			for(double lon = minlong+longstep/2; lon < maxlong; lon+=longstep, loni++)
			{
				int whichpoly = -1;
				for(int i = 0; i < polys.length; i++)
				{
					if(polys[i].isInPoly(lat, lon)) {
						//if(whichpoly != -1)
						//	Debug.println(lat+" "+lon+" is in polygon "+polys[whichpoly].name+" as well as "+polys[i].name, Debug.CRITICAL);
						whichpoly = i;
						//Debug.println(lat+","+lon+" is in CD "+polys[i].name+" (float "+((float) Integer.parseInt(polys[i].name))+")", Debug.INFO);
						break;
					}
				}	
				if(whichpoly == -1) {
					Debug.println("Found no polygon for "+lat+" , "+lon, Debug.INFO);
					failedtofind++;
					data[lati][loni] = Float.NaN;
				}
				else {
					if(!hasmember[whichpoly])
						hasmember[whichpoly] = true;
					int cd = Integer.parseInt(polys[whichpoly].name);
					data[lati][loni] = whichpoly; //(float) cd;
				}
			}
			Debug.println("Done "+(lati+1)+" of "+steps+" (failed to find "+failedtofind+" in this row)", Debug.IMPORTANT);
		}
		
		
		new GISLayer("COARSE_CD", minlat, maxlat, minlong, maxlong, data).saveToFile("tmpcd.gis");
		Debug.println("Produced coarse layer, now refining", Debug.IMPORTANT);
		
		//now we increase resolution and redo it in a smart way
		
		
		//first, we make a list of all the 'orphan' CDs that do not have
		//any points in them.
		ArrayList orphans = new ArrayList();
		for(int i =0; i < hasmember.length; i++)
			if(!hasmember[i]) {
				double[] centroid = polys[i].getCentroid();
				Debug.println("orphan centroid is "+centroid[0]+" "+centroid[1], Debug.IMPORTANT);
				if(centroid[0] > minlat && centroid[0] < maxlat
				   && centroid[1] > minlong && centroid[1] < maxlong)
				orphans.add(new Integer(i));
			}
		Debug.println("There are "+orphans.size()+" orphan CDs", Debug.IMPORTANT);
		
		//increase resolution
		int resmult = 10;
		steps *= resmult;
		float[][] finedata = new float[steps][steps];
		latstep = (maxlat-minlat)/steps;
		longstep = (maxlong-minlong)/steps;
		lati = 0;
		for(double lat = minlat+latstep/2; lat < maxlat; lat+=latstep, lati++)
		{
			Debug.println("Starting lat row "+(lati+1)+" of "+steps, Debug.IMPORTANT);
			int loni = 0;
			int failedtofind = 0;
			
			for(double lon = minlong+longstep/2; lon < maxlong; lon+=longstep, loni++)
			{
			
				int whichpoly = -1;
				int coarselati = lati/resmult;
				int coarseloni = loni/resmult;
				tryfindloop: for(int i = 0; i < 3; i++)
				{
					for(int j = 0; j < 3; j++)
					{
						if(coarselati+i >= 0 && coarselati+i < data.length &&
						   coarseloni+j >= 0 && coarseloni+j < data[0].length)
						{
							//get the coarse CD membership
							float trypolyf = data[coarselati+i][coarseloni+j];
							if(!Float.isNaN(trypolyf))
							{
								int trypoly = (int) Math.round(trypolyf);
								if(polys[trypoly].isInPoly(lat, lon)) {
									finedata[lati][loni] = Integer.parseInt(polys[trypoly].name);
									whichpoly = trypoly;
									break tryfindloop;
								}
							}
						}
						if(coarselati+i >= 0 && coarselati+i < data.length &&
								   coarseloni-j >= 0 && coarseloni-j < data[0].length)
						{
							float trypolyf = data[coarselati+i][coarseloni-j];
							if(!Float.isNaN(trypolyf))
							{
								int trypoly = (int) Math.round(trypolyf);

								if(polys[trypoly].isInPoly(lat, lon)) {
									finedata[lati][loni] = Integer.parseInt(polys[trypoly].name);
									whichpoly = trypoly;
									break tryfindloop;
								}
							}
						}
						if(coarselati-i >= 0 && coarselati-i < data.length &&
								   coarseloni+j >= 0 && coarseloni+j < data[0].length)
						{
							float trypolyf = data[coarselati-i][coarseloni+j];
							if(!Float.isNaN(trypolyf))
							{
							
								int trypoly = (int) Math.round(trypolyf);
	
								if(polys[trypoly].isInPoly(lat, lon)) {
									finedata[lati][loni] = Integer.parseInt(polys[trypoly].name);
									whichpoly = trypoly;
									break tryfindloop;
								}
							}
						}
						if(coarselati-i >= 0 && coarselati-i < data.length &&
								   coarseloni-j >= 0 && coarseloni-j < data[0].length)
						{
							float trypolyf = data[coarselati-i][coarseloni-j];
							if(!Float.isNaN(trypolyf))
							{	
								int trypoly = (int) Math.round(trypolyf);

								if(polys[trypoly].isInPoly(lat, lon)) {
									finedata[lati][loni] = Integer.parseInt(polys[trypoly].name);
									whichpoly = trypoly;
									break tryfindloop;
								}
							}
						}
					}
				}
			
				//if we havent found which CD we are in, we check the missing polygons
				if(whichpoly == -1)
				{
					for(int i =0; i < orphans.size(); i++)
					{
						int index = ((Integer) orphans.get(i)).intValue();
						double[] centroid = polys[index].getCentroid();
						if(MathUtil.getDistanceBetweenPointsOnEarth(lat, lon, centroid[0], centroid[1]) > 3000)
							continue; //dont bother checking if the centroid is too far away
						if(polys[index].isInPoly(lat, lon)) {
							//if(whichpoly != -1)
							//	Debug.println(lat+" "+lon+" is in polygon "+polys[index].name+" as well as "+polys[whichpoly].name, Debug.CRITICAL);
							whichpoly = index;
							finedata[lati][loni] = Integer.parseInt(polys[index].name);
							Debug.println(lat+","+lon+" is in orphan CD "+polys[index].name, Debug.INFO);
							break;
						}
					}
				}
				if(whichpoly == -1) {
					failedtofind++;
					finedata[lati][loni] = Float.NaN;
						Debug.println("Could not find a home for "+lat+" , "+lon, Debug.IMPORTANT);
				}
			}
			Debug.println("Done "+(lati+1)+" of "+steps, Debug.IMPORTANT);
			Debug.println("Failed to find "+failedtofind+" points (of "+steps+") in row at lat "+lat, Debug.IMPORTANT);
		}
		
		GISLayer cdlayer = new GISLayer("CD_MEMBERSHIP", minlat, maxlat, minlong, maxlong, finedata);
		cdlayer.saveToFile("cdmembership.gis");
	}
	

	//given a data file CDNUM val,
	//create a layer
	public static void getCDbasedData(String datfile) throws java.io.IOException
	{
		GISLayer.createRegionLayer(datfile, "cdmembership.gis", false);
	}
	
	
	
	
	
	
	
	
	
	//given a data file with CDNUM VALUE, create a layer
	/*public static void getCDbasedCategoricalData(String datfile) throws java.io.IOException
	{
		Map categories = new HashMap();
		//read in the file
		String[] lines = FileUtil.getLines(datfile);
		
		//work out how many unique categories there are
		for(int i =0; i < lines.length; i++)
		{
			String[] bits = Util.getWords(lines[i]);
			int cd = Integer.parseInt(bits[0]);
			String cat = bits[1];
			for(int j = 2; j < bits.length; j++)
				cat = cat + " " + bits[j];
			
		ONLY MADE A START ON THIS. DEBUG UP TO HERE.	
		}
		
		
		double[][] vects = FileUtil.readVectorsFromFile(new java.io.File(datfile));
		if(vects[0].length != 2)
			throw new RuntimeException("Expected file with 2 columns only: cdnum value");
		//make a table of CD --> VALUE mappings
		HashMap map = new HashMap(vects.length);
		for(int i =0; i < vects.length; i++)
		{
			int cd = (int) Math.round(vects[i][0]);
			float val = (float) vects[i][1];
			map.put(new Integer(cd), new Float(val));
		}
		
		//read in the CD layer
		GISLayer cdlayer = GISLayer.readFromFile("cdmembership.gis");
	
		//now create the layer
		float[][] cddata = cdlayer.continuousdata;
		float[][] newdata = new float[cddata.length][cddata[0].length];
		for(int i =0; i < newdata.length; i++)
			for(int j = 0; j < newdata[0].length; j++)
			{
				int cdnum =  (int) Math.round(cddata[i][j]);
				Integer key = new Integer(cdnum);
				float val = Float.NaN;
				if(map.containsKey(key))
					val = ((Float) map.get(key)).floatValue();
				newdata[i][j] = val;
			}

		new GISLayer("XXX", cdlayer.getMinLat(), cdlayer.getMaxLat(), 
				cdlayer.getMinLong(), cdlayer.getMaxLong(), newdata).saveToFile("layer.gis");
		
	}*/
	
	
	
	
	
	
	
	
	
	
	
	
	public static void main(String[] args) throws Exception
	{
		Debug.setVerbosityLevel(Debug.INFO);
		Debug.setDebugLevel(Debug.DEBUG_SUSPICIOUS);
		Debug.println("creating layer", Debug.INFO);
		
		/*if(args[0].equals("1"))
			createCombinedGreenSpaceLayer_stage1();
		else if(args[0].equals("1.5"))
			createCombinedGreenSpaceLayer_stage1andahalf(); 
		else if(args[0].equals("1.75"))
			createCombinedGreenSpaceLayer_stage1andthreequarters();
		else if(args[0].equals("2"))
			createCombinedGreenSpaceLayer_stage2();
		else if(args[0].equals("3"))
			createCombinedGreenSpaceLayer_stage3();
		else if(args[0].equals("4"))
			createCombinedGreenSpaceLayer_stage4();
		else if(args[0].equals("5"))
			createCombinedGreenSpaceLayer_stage5();
		else if(args[0].equals("t1"))
			createTreeLayer_stage1();
		else if(args[0].equals("t2"))
			createTreeLayer_stage2();
		else if(args[0].equals("t3"))
			createTreeLayer_stage3();
		else
			throw new RuntimeException();
		*/
		
		//createParksLayer();		
		//createTreeAndGrassCoverageLayers();
		//createTransportLayer();
		//createIndustrialAndCommercialLayers("zones.gis");
		//createRoadLayer("transport_combined.gis");
		//createCategoricalRoadLayer("transport_combined.gis");
		createRoadCoverageLayer("transport_combined.gis");
		//createIndustrialAndCommercialCategoricalLayers("zones.gis");
		//createCDLayer(args[0], false);
		//createCDLayer(args[0], true);
		//GISLayer.createRegionLayer(args[0], args[1], false);
		//GISLayer.createRegionLayer(args[0], args[1], true);
		//GISLayer.calculateIntegralAtRegionCentroids(args[0], args[1], Double.parseDouble(args[2]));
		//getCDbasedData(args[0]);
		//createAggregateLayer(args[0], args[1]);
	}
	
	
}

