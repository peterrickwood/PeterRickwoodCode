package rses.apps.imageclassifier;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import rses.Debug;
import rses.spatial.GISLayer;
import rses.util.Util;





/** Create GIS layer from iplan images
 * 
 * @author peterr
 *
 */
abstract class CreateLayerFromImages
{
	
	
	
	private static double[][] getMinMaxLatLong(int width, int height,
			int startx, int endx, int starty, int endy)
	{
		double minlat = Double.POSITIVE_INFINITY;
		double maxlat = Double.NEGATIVE_INFINITY;
		double minlong = Double.POSITIVE_INFINITY;
		double maxlong = Double.NEGATIVE_INFINITY;
		
		for(int x = 0; x < width; x+= (width-1))
		{
			for(int y = 0; y < height; y+= (height-1))
			{
		double[] ll = getLatLon(x, y, width, height, startx, endx, starty, endy);
		if(ll[0] < minlat) minlat = ll[0];
		if(ll[0] > maxlat) maxlat = ll[0];
		if(ll[1] < minlong) minlong = ll[1];
		if(ll[1] > maxlong) maxlong = ll[1];
			}
		}
		
		/*
		for(int x = 0; x < width; x++)
		{
			double[] ll = getLatLon(x, 0, width, height, startx, endx, starty, endy);
			if(ll[0] < minlat) minlat = ll[0];
			if(ll[0] > maxlat) maxlat = ll[0];
			if(ll[1] < minlong) minlong = ll[1];
			if(ll[1] > maxlong) maxlong = ll[1];
			ll = getLatLon(x, height-1, width, height, startx, endx, starty, endy);
			if(ll[0] < minlat) minlat = ll[0];
			if(ll[0] > maxlat) maxlat = ll[0];
			if(ll[1] < minlong) minlong = ll[1];
			if(ll[1] > maxlong) maxlong = ll[1];

		}
		for(int y = 0; y < height; y++)
		{
			double[] ll = getLatLon(0, y, width, height, startx, endx, starty, endy);
			if(ll[0] < minlat) minlat = ll[0];
			if(ll[0] > maxlat) maxlat = ll[0];
			if(ll[1] < minlong) minlong = ll[1];
			if(ll[1] > maxlong) maxlong = ll[1];
			ll = getLatLon(width-1, y, width, height, startx, endx, starty, endy);
			if(ll[0] < minlat) minlat = ll[0];
			if(ll[0] > maxlat) maxlat = ll[0];
			if(ll[1] < minlong) minlong = ll[1];
			if(ll[1] > maxlong) maxlong = ll[1];
		}*/
		double realminlat = minlat-(0.5*(maxlat-minlat))/height;
		double realmaxlat = maxlat+(0.5*(maxlat-minlat))/height;
		double realminlong = minlong-(0.5*(maxlong-minlong))/width;
		double realmaxlong = maxlong+(0.5*(maxlong-minlong))/width;
		
		
		Debug.println("Min/Max lat are: "+minlat+" "+maxlat, Debug.IMPORTANT);
		Debug.println("Min/Max long are: "+minlong+" "+maxlong, Debug.IMPORTANT);

		return new double[][] {{realminlat, realmaxlat},{realminlong, realmaxlong}};
	}
	
	
	
	


	private static boolean[][] extractClassInfo(BinaryCategoryRules rules, String imgfile)
	{
		if(imgfile.equalsIgnoreCase("PATCHES"))
		{
			boolean[][] tl = extractClassInfo(rules, "tl.png");
			boolean[][] tr = extractClassInfo(rules, "tr.png");
			boolean[][] bl = extractClassInfo(rules, "bl.png");
			boolean[][] br = extractClassInfo(rules, "br.png");
			Debug.println("Finished with sub-images...now combining", Debug.IMPORTANT);
			boolean[][] combined = new boolean[tl.length+bl.length][tl[0].length+tr[0].length];
			for(int i =0; i < combined.length; i++)
				for(int j = 0; j < combined[0].length; j++)
				{
					if(i < tl.length) //top of image
					{
						if(j < tl[0].length)
							combined[i][j] = tl[i][j];
						else
							combined[i][j] = tr[i][j-tl[0].length];
					}
					else
					{
						if(j < bl[0].length)
							combined[i][j] = bl[i-tl.length][j];
						else
							combined[i][j] = br[i-tl.length][j-tl[0].length];
					}
					
				}
			return combined;
		}
		else
		{	
			Debug.println("Reading in image", Debug.INFO);
			BufferedImage patch = extractImage(new File(imgfile));
			Debug.println("Finished reading image...", Debug.INFO);
		
			int width = patch.getWidth();
			int height = patch.getHeight();
			Debug.println("Image width/height is "+width+"/"+height, Debug.IMPORTANT);

		
			Debug.println("Extracting class information from image....", Debug.IMPORTANT);
			//get all the boolean values for the pixels first
			boolean[][] pixelbooleans = new boolean[height][width];
			for(int y = 0; y < height; y++)
			{
				for(int x = 0; x < width; x++)
				{
					int[] rgb = getRGBComponents(patch, x, y);
					if(rules.isInCategory(rgb)) 
						pixelbooleans[y][x] = true;				
				}
				Debug.println("Finished line "+y+" of "+(height-1), Debug.IMPORTANT);
			}
		
			//now turf the image, since we have all the info we need from it
			patch = null;
			System.gc();
			Debug.println("Finished with image... reclaimed memory", Debug.IMPORTANT);
			return pixelbooleans;
		}	
	}






	
	/* arg1 -- the image file to use
	 * arg2 -- the text file which gives all the colour-cubes within which the
	 *         layer has the value 'true'. Everywhere else is assumed false.
	 *         Each line must consist of minr maxr ming maxg minb maxb
	 *       
	 *  startx/endx starty/endy
	 *  The start and end x/y values of the underlying images that were used to
	 *  create this image in 'imgfile'. This must be expressed in layer 'J' terms
	 *  The values are inclusive on both ends.
	 *  
	 *   The GIS layer produced will by the same size as the image (i.e.
	 *   it will have the same number of entries in x/y as the image has
	 *   pixels). This does not mean there is a 1-1 correspondance 
	 *   between pixels and gis points, though.
	 */	
	public static GISLayer getBinaryLayerFromImage(String layername,
			String imgfile, String configfile, 
			int startx, int endx, int starty, int endy,
			int latsteps, int longsteps) throws IOException
	{
		BinaryCategoryRules rules = BinaryCategoryRules.readCategoryRules(configfile);
		
		boolean[][] pixelbooleans = extractClassInfo(rules, imgfile);
		int width = pixelbooleans[0].length;
		int height = pixelbooleans.length;
		
		
		
		//work out the min/max lat and long
		double minlat, maxlat, minlong, maxlong;
		double[][] minmax = getMinMaxLatLong(width, height, startx, endx, starty, endy);
		minlat = minmax[0][0]; maxlat = minmax[0][1];
		minlong = minmax[1][0]; maxlong = minmax[1][1];
		
		
		
		//now we convert each pixel into lat/long and classify it
		byte[][] gisdata = new byte[latsteps][longsteps];
		
				
		double latrange = maxlat-minlat;
		double longrange = maxlong-minlong;
		int pixcount = 0;
		for(int y = 0; y < height; y++)
		{
			for(int x = 0; x < width; x++)
			{
				double[] latlon = getLatLon(x, y, width, height, startx, endx, starty, endy);

				double latcoord = ((latlon[0]-minlat)/latrange)*latsteps;
				int gislat = (int) Math.floor(latcoord);
				if(gislat == latsteps) gislat--;
				
				double loncoord = ((latlon[1]-minlong)/longrange)*longsteps;
				int gislong = (int) Math.floor(loncoord);
				if(gislong == longsteps) gislong--;

				//Debug.println("img "+x+","+y+" goes to giscoords "+latcoord+" "+loncoord, Debug.IMPORTANT);
				//Debug.println("img "+x+","+y+" goes to giscoords "+gislat+" "+gislong, Debug.IMPORTANT);
				//double apprlat = minlat+((gislat+0.5)/height)*(maxlat-minlat);
				//double apprlon = minlong+((gislong+0.5)/width)*(maxlong-minlong);
				//Debug.println("thats original lat/long "+latlon[0]+" "+latlon[1] , Debug.IMPORTANT);
				//Debug.println("and converted lat/lon: "+apprlat+" "+apprlon , Debug.IMPORTANT);
				
				
				
				/*int[] rgb = getRGBComponents(patch, x, y);

				if(rules.isInCategory(rgb)) {
					gisdata[gislat][gislong]++;
					pixcount++;
				}
				else
					gisdata[gislat][gislong]--;
					*/
				if(pixelbooleans[y][x]) {
					gisdata[gislat][gislong]++;
					pixcount++;
				}
				else
					gisdata[gislat][gislong]--;
			}
			Debug.println("Completed "+(100.0*y+100)/height+" percent of inital data gathering", Debug.IMPORTANT);
			Debug.println("Found "+pixcount+" pixels (of "+width*(y+1)+") that satisfied a 'true' rule", Debug.IMPORTANT);
		}


		
		Debug.println("Filling in data gaps....", Debug.IMPORTANT);
		boolean[][] booleandata = new boolean[latsteps][longsteps];
		
		int truecount=0;
		//now go through and classify any square that has no classification
		for(int lat = 0; lat < gisdata.length; lat++)
		{
			for(int lon = 0; lon < gisdata[0].length; lon++)
			{
				if(gisdata[lat][lon] > 0) 
					booleandata[lat][lon] = true;
				else if(gisdata[lat][lon] < 0)
					booleandata[lat][lon] = false;
				else
				{
					int count = 0;
					for(int i = -1; i <= 1; i++)
						for(int j = -1; j <= 1; j++) {
							if(lat+i >= 0 && lat+i < latsteps && lon+j >= 0 && lon+j < longsteps)
								count += gisdata[lat+i][lon+j];
						}
					if(count > 0)
						booleandata[lat][lon] = true;
					else
						booleandata[lat][lon] = false;
				}
				
				if(booleandata[lat][lon])
					truecount++;
				
			}
		}
		
		Debug.println("There are a total of "+truecount+" true cells in this binary layer", Debug.IMPORTANT);
		return new GISLayer(layername, minlat, maxlat, minlong, maxlong, booleandata);
	}

	
	
	
	
	
	
	
	
	
	
	
	
	public static GISLayer getCategoricalLayerFromImage(String layername,
			String imgfile, String configfile, 
			int startx, int endx, int starty, int endy,
			int latsteps, int longsteps) throws IOException
	{
		BufferedImage patch = extractImage(new File(imgfile));
		CategoryRules rules = CategoryRules.readCategoryRules(configfile);
		
		int width = patch.getWidth();
		int height = patch.getHeight();
		
		//work out the min/max lat and long
		double minlat, maxlat, minlong, maxlong;
		double[][] minmax = getMinMaxLatLong(width, height, startx, endx, starty, endy);
		minlat = minmax[0][0]; maxlat = minmax[0][1];
		minlong = minmax[1][0]; maxlong = minmax[1][1];
		
		//now we convert each pixel into lat/long and classify it
		int[][] gisdata = new int[latsteps][longsteps];
		for(int i = 0; i < gisdata.length; i++)
			for(int j = 0; j < gisdata[i].length; j++)
				gisdata[i][j] = -1;
		
				
		double latrange = maxlat-minlat;
		double longrange = maxlong-minlong;
		for(int y = 0; y < height; y++)
		{
			for(int x = 0; x < width; x++)
			{
				double[] latlon = getLatLon(x, y, width, height, startx, endx, starty, endy);

				double latcoord = ((latlon[0]-minlat)/latrange)*latsteps;
				int gislat = (int) Math.floor(latcoord);
				if(gislat == latsteps) gislat--;
				
				double loncoord = ((latlon[1]-minlong)/longrange)*longsteps;
				int gislong = (int) Math.floor(loncoord);
				if(gislong == longsteps) gislong--;
				
				int[] rgb = getRGBComponents(patch, x, y);
				Debug.println("PIXINFO: "+rgb[0]+" "+rgb[1]+" "+rgb[2], Debug.EXTRA_INFO);
				gisdata[gislat][gislong] = rules.getCategory(rgb);
			}
			Debug.println("Completed "+(100.0*y+100)/height+" percent of inital data gathering", Debug.IMPORTANT);
		}

		
		return new GISLayer(layername, minlat, maxlat, minlong, maxlong, gisdata, rules.catnames);
	}

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	//work out the lat/long of a particular pixel
	//
	//pixels (x,y) in this image are labelled from left to right
	//and top to bottom, but we always work left to right and
	//bottom to top 
	private static double[] getLatLon(int x, int y, int width, int height, 
			int startx, int endx, int starty, int endy)
	{
		//work out how many pixels at the J level are in the combined image
		int xpixels = (endx-startx+1)*500;
		int ypixels = (endy-starty+1)*500;

		//now work out x and y at the J level 
		double xtojx = Math.round(xpixels*((x+0.5)/((double)width)));
		int jx = (int) (startx*500+xtojx);
		double ytojy = Math.round(ypixels*((y+0.5)/((double)height)));
		int jy = (int) (starty*500+ypixels-ytojy);
		
		
		return LatLongConversion.getLatLong(jx, jy);
	}
	
	
	private static int[] getRGBComponents(BufferedImage bim, int x, int y) 
	{
		int[] res = new int[3];
		ColorModel cm = ColorModel.getRGBdefault(); 
		
		int rgb = bim.getRGB(x, y);
		res[0] = cm.getRed(rgb);
		res[1] = cm.getGreen(rgb);
		res[2] = cm.getBlue(rgb);
		
		return res;
	}
	
	//return the rgb components of the image
	private static BufferedImage extractImage(File imgfile)
	{
		BufferedImage image = null;
		Debug.println("looking for file "+imgfile.getAbsolutePath(), Debug.INFO);
		
		try {
			image = ImageIO.read(imgfile);
		}
		catch(java.io.IOException ioe) {
			image = null;
			Debug.println("Could not load image "+imgfile, Debug.IMPORTANT);
		}

		return image;
		
	}
	
	
	
	
	public static void usage()
	{
		System.err.println("usage:");
		System.err.println("");
		System.err.println("arg0: type of layer Bin(ary)/Cat(egorical)/Cont(inuous)");
		System.err.println("arg1: data file");
		System.err.println("arg2: rule file");
		System.err.println("arg3: startx (at J level), usually 236");
		System.err.println("arg4: endx (inclusive, at J level), usually 335");
		System.err.println("arg5: starty (at J level), usually 180");
		System.err.println("arg6: endy (inclusive, at J level), usually 275");
		System.err.println("arg7: latsteps in GIS layer");
		System.err.println("arg8: longsteps in GIS layer");
		System.err.println("arg9: name of layer (must contain no whitespace)");
		System.exit(-1);
	}

	
	
	//for testing
	public static void main2(String[] args)
	{
		//
		//
		
		
		
		double[] ll1 = getLatLon(4000,2000,5000,5000,236,335,180,275);
		double[] ll2 = getLatLon(1500,2000,2500,2500,286, 335, 228, 275);
		
		Debug.println(ll1[0]+","+ll1[1]+"   "+ll2[0]+","+ll2[1], Debug.IMPORTANT);
		
		
	}

	
	public static void main(String[] args) throws Exception
	{
		Debug.setVerbosityLevel(Debug.INFO);
		
		if(args.length != 10)
			usage();
		String layertype = args[0];
		String datafile = args[1];
		String rulefile = args[2];
		int startx = Integer.parseInt(args[3]); 
		int endx = Integer.parseInt(args[4]);
		int starty = Integer.parseInt(args[5]);
		int endy = Integer.parseInt(args[6]);
		int latsteps = Integer.parseInt(args[7]);
		int longsteps = Integer.parseInt(args[8]);
		String name = args[9];
		
		GISLayer gislayer = null;
		if(layertype.toLowerCase().startsWith("bin"))
			gislayer = getBinaryLayerFromImage(name, datafile, rulefile, startx, endx, starty, endy, latsteps, longsteps);
		else if(layertype.toLowerCase().startsWith("cat"))
			gislayer = getCategoricalLayerFromImage(name, datafile, rulefile, startx, endx, starty, endy, latsteps, longsteps);
		else if(layertype.toLowerCase().startsWith("con"))
			throw new UnsupportedOperationException("Not yet implemented");
		else
			throw new RuntimeException("Unknown layer type specified -- "+layertype);
		
		//gislayer.dumpAsciiLatLongVal();
		gislayer.saveToFile("layer.gis");
	}
	
	
}







class CategoryRules
{
	private int[][][] redbounds;
	private int[][][] greenbounds;
	private int[][][] bluebounds;
	String[] catnames;
	
	private CategoryRules() {}
	
	public static CategoryRules readCategoryRules(String file) throws IOException
	{
		CategoryRules result = new CategoryRules();
		
		BufferedReader rdr = new BufferedReader(new java.io.FileReader(file));
		ArrayList lines = new ArrayList();
		String line = rdr.readLine();
		ArrayList categories = new ArrayList();
		while(line != null) {
			lines.add(line);
			if(!categories.contains(Util.getWords(line)[0]))
				categories.add(Util.getWords(line)[0]);
			line = rdr.readLine();
		}
		
		int ncat = categories.size();
		result.catnames = new String[ncat];
		for(int i =0; i < ncat; i++)
			result.catnames[i] = (String) categories.get(i);
		
		result.redbounds = new int[ncat][][];
		result.greenbounds = new int[ncat][][];
		result.bluebounds = new int[ncat][][];
		
		//for each category, we find the rules for that category
		for(int i =0; i < ncat; i++)
		{
			ArrayList rules = new ArrayList();
			for(int j = 0; j < lines.size(); j++)
			{
				line = (String) lines.get(j);
				String[] words = Util.getWords(line);
				if(words[0].equals(categories.get(i))) {
					int[] redrule = new int[] {Integer.parseInt(words[1]), Integer.parseInt(words[2])};
					int[] greenrule = new int[] {Integer.parseInt(words[3]), Integer.parseInt(words[4])};
					int[] bluerule = new int[] {Integer.parseInt(words[5]), Integer.parseInt(words[6])};
					rules.add(new int[][] {redrule, greenrule, bluerule});
				}
			}
			
			//ok, now add all the rules
			result.redbounds[i] = new int[rules.size()][];
			result.greenbounds[i] = new int[rules.size()][];
			result.bluebounds[i] = new int[rules.size()][];
			for(int j = 0; j < rules.size(); j++)
			{
				int[][] ruleinfo = (int[][]) rules.get(j);
				result.redbounds[i][j] = ruleinfo[0];
				result.greenbounds[i][j] = ruleinfo[1];
				result.bluebounds[i][j] = ruleinfo[2];
			}
		}
		
			
		return result;
	}
	
	
	
	public byte getCategory(int[] rgb)
	{
		for(int i =0; i < this.redbounds.length; i++)
		{
			for(int j = 0; j < this.redbounds[i].length; j++)
			{
				if(rgb[0] >= redbounds[i][j][0] && rgb[0] <= redbounds[i][j][1] &&
				   rgb[1] >= greenbounds[i][j][0] && rgb[1] <= greenbounds[i][j][1] &&
				   rgb[2] >= bluebounds[i][j][0] && rgb[2] <= bluebounds[i][j][1])
				{
					return (byte) i;
				}
			}
				
		}
		
		return -1; //no category
	}
	
	
}





class BinaryCategoryRules
{
	private int[][] redbounds;
	private int[][] greenbounds;
	private int[][] bluebounds;
	
	private BinaryCategoryRules() {}
	
	public static BinaryCategoryRules readCategoryRules(String file) throws IOException
	{
		BinaryCategoryRules result = new BinaryCategoryRules();
		
		BufferedReader rdr = new BufferedReader(new java.io.FileReader(file));
		ArrayList lines = new ArrayList();
		String line = rdr.readLine();
		while(line != null) {
			lines.add(line);
			line = rdr.readLine();
		}
		
		int nrules = lines.size(); 
		result.redbounds = new int[nrules][2];
		result.greenbounds = new int[nrules][2];
		result.bluebounds = new int[nrules][2];
		
		for(int i =0; i < nrules; i++)
		{
			line = (String) lines.get(i);
			Debug.println("Found rule: "+line, Debug.IMPORTANT);
			String[] words = Util.getWords(line);
			result.redbounds[i][0] = Integer.parseInt(words[0]);
			result.redbounds[i][1] = Integer.parseInt(words[1]);
			result.greenbounds[i][0] = Integer.parseInt(words[2]);
			result.greenbounds[i][1] = Integer.parseInt(words[3]);
			result.bluebounds[i][0] = Integer.parseInt(words[4]);
			result.bluebounds[i][1] = Integer.parseInt(words[5]);
		}
		
		return result;
	}
	
	
	
	public boolean isInCategory(int[] rgb)
	{
		for(int i =0; i < this.redbounds.length; i++)
		{
			if(rgb[0] >= redbounds[i][0] && rgb[0] <= redbounds[i][1] &&
			   rgb[1] >= greenbounds[i][0] && rgb[1] <= greenbounds[i][1] &&
			   rgb[2] >= bluebounds[i][0] && rgb[2] <= bluebounds[i][1])
			{
				return true;
			}
				
		}
		
		return false;
	}
	
	
}
