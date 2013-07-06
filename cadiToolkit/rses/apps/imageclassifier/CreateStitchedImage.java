package rses.apps.imageclassifier;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.File;

import javax.imageio.ImageIO;

import rses.Debug;



public final class CreateStitchedImage
{
	private CreateStitchedImage() {}
	
	public static void main(String[] args) throws Exception
	{
		//hard coded values
		int imgsize=500;

		Debug.setVerbosityLevel(Debug.MAX_VERBOSITY);	
		Debug.println("REMEMBER: This program assumes all images are 500 by 500 pixels", Debug.IMPORTANT);
	
		if(args.length != 4)
		{
			Debug.println("Usage: ", Debug.CRITICAL);
			Debug.println("arg1: path to image directories", Debug.CRITICAL);
			Debug.println("arg2: shrinkfactor (1-"+imgsize+")", Debug.CRITICAL);
			Debug.println("arg3: image layer (either J or H  is supported)", Debug.CRITICAL);
			Debug.println("arg4: image extension (jpg or png)", Debug.CRITICAL);
			System.exit(0);
		}

		
		char layer=args[2].charAt(0);
		String ext = args[3];
		
		/* for layer J */
		int startx = 236;
		int endx = 335;
		int starty = 180;
		int endy = 275;
		if(layer == 'H') 
		{
			startx = 59;
			endx = 83;
			starty = 45;
			endy = 68;
		}
		
		
		
		String imageroot = args[0];
		int shrinkfactor = Integer.parseInt(args[1]);
		if(imgsize % shrinkfactor != 0)
			throw new RuntimeException("shrinkfactor does not allow easy shrinking of "+imgsize+" pixel image");
		int patchsize = imgsize/shrinkfactor;
		int xsize = (endx-startx+1)*patchsize;
		int ysize = (endy-starty+1)*patchsize;
		
		Debug.println("patch size is "+patchsize, Debug.INFO);
		Debug.println("total x pixels in stitched image is "+xsize, Debug.INFO);
		
		//create our image
		BufferedImage bim = new BufferedImage(xsize, ysize, BufferedImage.TYPE_3BYTE_BGR);
		//create a graphics 
		java.awt.Graphics2D g2d = bim.createGraphics();
		Color c = Color.white;
		float[] comps = c.getComponents(null);
		comps[3] = 0.0f;
		
		for(int x = startx; x <= endx; x++)
		{
			for(int y = starty; y <= endy; y++)
			{
				BufferedImage patch = extractImage(new File(imageroot, x+"x"+y+"."+ext));
				//BufferedImage patch2 = extractImage(new File(imageroot, "transport_"+x+"x"+y+"."+ext));
				BufferedImage patch2 = extractImage(new File(imageroot, "class"+x+"x"+y+"."+ext));
				if(patch == null && patch2 != null)
					patch = patch2;
				if(patch != null) {
					Debug.println("creating scaled image", Debug.INFO);
					Image scaled = patch.getScaledInstance(imgsize/shrinkfactor, imgsize/shrinkfactor, BufferedImage.SCALE_FAST);
					g2d.drawImage(scaled, (x-startx)*patchsize, ysize-(y-starty+1)*patchsize, null);
				}
			}
		}
		

		//now dump out the image
		javax.imageio.ImageIO.write(bim, "png", new File("stitched.png"));

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
	
}