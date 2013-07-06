package rses.apps;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import rses.Debug;
import rses.PlatformInfo;
import rses.math.MathUtil;
import rses.util.FileUtil;
import rses.util.Util;
import rses.visualisation.FunctionPlotter;








/** Generate a image file representing a 2D scalar field.
 *  Each data point in the input file is plotted. No
 *  interpolation is done, so the data file must have
 *  a large number of samples.
 * 
 * @author peterr
 *
 */
public class GenerateFieldFile
{
	private GenerateFieldFile() {}
	
	public static void generateFieldFile(String datfile, int width, int height, boolean planar)
	throws IOException
	{
		Debug.setVerbosityLevel(Debug.EXTRA_INFO);
		
		double[][] data = readDataFile(datfile);
		if(!planar)
			MathUtil.planarizeData(data);
		
		double minx = Double.POSITIVE_INFINITY;
		double maxx = Double.NEGATIVE_INFINITY;
		double miny = Double.POSITIVE_INFINITY;
		double maxy = Double.NEGATIVE_INFINITY;
		double minz = Double.POSITIVE_INFINITY;
		double maxz = Double.NEGATIVE_INFINITY;
		for(int i = 0; i < data.length; i++)
		{
			if(data[i][0] < minx) minx = data[i][0];
			if(data[i][0] > maxx) maxx = data[i][0];
			if(data[i][1] < miny) miny = data[i][1];
			if(data[i][1] > maxy) maxy = data[i][1];
			if(data[i][2] < minz) minz = data[i][2];
			if(data[i][2] > maxz) maxz = data[i][2];
		}
		
		
		ArrayList datalist = new ArrayList();
		for(int i = 0; i < data.length; i++)
			datalist.add(data[i]);
			
		
		//create our image
		BufferedImage bim = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		//create a graphics 
		java.awt.Graphics2D g2d = bim.createGraphics();
		Color c = Color.white;
		float[] comps = c.getComponents(null);
		comps[3] = 0.0f;
		g2d.setBackground(new Color(comps[0], comps[1], comps[2], comps[3]));
		
		FunctionPlotter.setPointSize(2);
		
		//paint it
		g2d.clearRect(0,0,width, height);
		FunctionPlotter.plotPoints3D(g2d, datalist, minx, maxx, miny, maxy, minz, maxz, width, height, Color.red, Color.blue);		
		//now write the image file
		javax.imageio.ImageIO.write(bim, "png", new File("field_redblue.png"));
		if(!planar) writeKMLForOverlay("field_redblue.png");
		
		g2d.clearRect(0,0,width, height);
		FunctionPlotter.plotPoints3D(g2d, datalist, minx, maxx, miny, maxy, minz, maxz, width, height, Color.blue, Color.red);		
		//now write the image file
		javax.imageio.ImageIO.write(bim, "png", new File("field_bluered.png")); 		
		if(!planar) writeKMLForOverlay("field_bluered.png");
		
		g2d.clearRect(0,0,width, height);
		FunctionPlotter.plotPoints3D(g2d, datalist, minx, maxx, miny, maxy, minz, maxz, width, height, Color.white, Color.black);		
		//now write the image file
		javax.imageio.ImageIO.write(bim, "png", new File("field_whiteblack.png")); 
		if(!planar) writeKMLForOverlay("field_whiteblack.png");	
		
		
		

	}
	
	
	private static void writeKMLForOverlay(String imgname)
	throws IOException
	{
		String filename = imgname+".kml";
		FileWriter file = new FileWriter(filename);
		String nl = PlatformInfo.nl;
		
		
		
		file.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+nl+
		"<kml xmlns=\"http://earth.google.com/kml/2.0\">"+nl+
		"<GroundOverlay>"+nl+
		"<description>Overlay</description>"+nl+
		"<name>Overlay</name>"+nl+
		"<visibility>1</visibility>"+nl+
		"<Icon>"+nl+
		"<href>http://localhost/~peterr/kml/"+imgname+"</href>"+nl+
		"</Icon>"+nl+
		"<LatLonBox id=\""+imgname+"\">"+nl+
		"   <north></north>"+nl+
	    "  <south></south>"+nl+
		"    <east></east>"+nl+
		"    <west></west>"+nl+
		"    <rotation>0</rotation>"+nl+
		"  </LatLonBox>"+nl+
		"</GroundOverlay>"+nl+
		"</kml>"+nl);
		
		file.close();

				
	}

	
	private static double[][] readDataFile(String datfile) throws IOException
	{
		File f = new File(datfile);
		return FileUtil.readVectorsFromFile(f);
	}
	

	
	
	public static void main(String[] args) throws IOException
	{
		//args[0] = data file with x/y/val tuples
		//args[1] = width (in pixels) of image to create
		//args[2] = height (in pixels) of image to create
		//args[3] = planar or spherical
		
		if(args.length < 3 || args.length > 4)
		{
			help();
			System.exit(1);
		}
		boolean planar = true;
		String datfile = args[0];
		int width = Integer.parseInt(args[1]);
		int height = Integer.parseInt(args[2]);
		if(args.length == 4)
		{
			if(args[3].equals("planar"))
				planar = true;
			else if(args[3].equals("spherical"))
				planar = false;
			else {
				help();
				System.exit(1);
			}	
		}
		
		generateFieldFile(datfile, width, height, planar);
		
	}
	
	
	
	private static void help()
	{
		System.err.println("Usage:");
		System.err.println("argument 1 must be the data file");
		System.err.println("argument 2 must be the width of the image");
		System.err.println("argument 3 must be the height of the image");
		System.err.println("argument 4, if it exists, must be 'planar' or 'spherical'");
		System.err.println("");
		System.err.println("If argument 4 is absent, then 'planar' is assumed");
	}
	
}