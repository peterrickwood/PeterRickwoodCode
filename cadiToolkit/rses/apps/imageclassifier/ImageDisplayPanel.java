package rses.apps.imageclassifier;



import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;


import javax.imageio.ImageIO;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import rses.Debug;
import rses.regression.Datum;
import rses.regression.randomforest.RandomForestRegression;
import rses.regression.randomforest.RegressionTree;
import rses.util.Util;


/** A panel to display an image and allow the user to
 *  select regions that are all of the same type
 * 
 * @author peterr
 */
public class ImageDisplayPanel extends JPanel 
{
	public boolean latlongdatabasemode = false;
	public static final int IMGSIZE=500;
	
	private static final Color[] colours = new Color[] 
		{Color.green, Color.lightGray, Color.pink, Color.yellow, Color.darkGray, Color.blue, 
		Color.cyan, Color.orange, Color.black, Color.red};
	//private static final String[] catnames = new String[]
	//    {"houses", "grass", "trees", "dirt", "ind/com", "water", "pool"};
	private static final String[] catnames = new String[]
	    {"trees","grass", "nontrees1", "nontrees2",  "nontrees3", "nontrees4", "nontrees5"};
	
	
	
	public static final int CLASSONLY = -10;
	public static final int IMGONLY = -20;
	public static final int BLENDED = -30;
	private int paintmode = BLENDED;
	
	

	//the information associated with the sattelite image	
	private int[][] red;
	private int[][] green;
	private int[][] blue;
	
	private int[][] classification;
	

	//the information associated with the transport image
	private int[][] trans_red;
	private int[][] trans_green;
	private int[][] trans_blue;



	
	private String fileroot = "";
	private int imgx = 0;
	private int imgy = 0;
	
	//private PartitionTreeForest classifier = null; 
	private RegressionTree[] classifier = null;
	//private int currenttree = 0;
	private int nodecapacity = -1;
	
	private int ncategories = -1;
	
	private Point up = null;
	private Point down = null;
	
	class ClickHandler implements ActionListener, MouseListener, KeyListener
	{
		//only allow 1 event at a time
		public synchronized void keyReleased(KeyEvent e)
		{
			Debug.println("Key typed", Debug.INFO);
			int code = e.getKeyCode();
			
			if(code == KeyEvent.VK_RIGHT)
				ImageDisplayPanel.this.setImageToDisplay(imgx+1, imgy);
			else if(code == KeyEvent.VK_LEFT)
				ImageDisplayPanel.this.setImageToDisplay(imgx-1, imgy);
			else if(code == KeyEvent.VK_UP)
				ImageDisplayPanel.this.setImageToDisplay(imgx, imgy+1);
			else if(code == KeyEvent.VK_DOWN)
				ImageDisplayPanel.this.setImageToDisplay(imgx, imgy-1);
			else if(code == KeyEvent.VK_SLASH)
			{
				//pick a random x,y
				//until we get a valid image
				int x = (int) (236+Math.random()*(336-236));
				int y = (int) (180+Math.random()*(276-180));
				BufferedImage img = ImageDisplayPanel.this.extractAerialImage(x, y);
				while(img == null)
				{
					x = (int) (1+Math.random()*512);
					y = (int) (1+Math.random()*512);
					img = ImageDisplayPanel.this.extractAerialImage(x, y);
				}
				ImageDisplayPanel.this.setImageToDisplay(x, y);	
			}
			
			//reclassify();
		}
		
		public void keyPressed(KeyEvent e)
		{}
		
		public void keyTyped(KeyEvent e)
		{}

		
		public void mouseEntered(MouseEvent e)
		{}
		
		public void mouseExited(MouseEvent e)
		{}

		public void mousePressed(MouseEvent e)
		{
			if(e.getButton() == MouseEvent.BUTTON1)
			{
				down = e.getPoint();
				up = null;
			}
		}
		

		public void mouseReleased(MouseEvent e)
		{
			if(e.getButton() != MouseEvent.BUTTON1)
			{
				JPopupMenu jpop = new JPopupMenu();
				JMenuItem menuitem = jpop.add("Composite");
				menuitem.addActionListener(this);
				menuitem = jpop.add("Image only");
				menuitem.addActionListener(this);
				menuitem = jpop.add("Type only");
				menuitem.addActionListener(this);
				menuitem = jpop.add("CLASSIFY ALL");
				menuitem.addActionListener(this);
				menuitem = jpop.add("RECLASSIFY");
				menuitem.addActionListener(this);
				menuitem = jpop.add("NEXT TREE");
				menuitem.addActionListener(this);
				jpop.show(ImageDisplayPanel.this, e.getX(), e.getY());
				return;
			}
			else if(ImageDisplayPanel.this.latlongdatabasemode)
			{
				int xpos = ImageDisplayPanel.this.imgx;
				int ypos = ImageDisplayPanel.this.imgy;
				String fname = xpos+"x"+ypos+".jpg";
				int xpix = xpos*IMGSIZE+e.getX();
				int ypix = ypos*IMGSIZE+IMGSIZE-e.getY();
				double[] latlon = LatLongConversion.getLatLong(xpix, ypix);
				
				

				Point p = e.getPoint(); //the point where the mouse was released
				String input = JOptionPane.showInputDialog(ImageDisplayPanel.this, "Enter NAME lat long", (""+latlon[0]).substring(0, 10)+(" "+latlon[1]).substring(0, 10));
				if(input == null) return;
				String[] words = Util.getWords(input);
				if(words.length != 3)
				{
					System.err.println("INVALID LAT/LONG INPUT, IGNORING");
					return;
				}
				
				String name = words[0];
				double lat = Double.parseDouble(words[1]);
				double lon = Double.parseDouble(words[2]);
				
				
				
				Debug.println("PLACEMARK: "+name+" "+fname+" "+e.getX()+" "+e.getY()+" "+lat+" "+lon, Debug.IMPORTANT);
				return;
			}
			
			up = e.getPoint();
			Debug.println("mouse released", Debug.EXTRA_INFO);
			ImageDisplayPanel.this.repaint();
			
			//get the current classification
			//we just take the majority class within the
			//bounding box
			int startx = Math.min(down.x, up.x);
			int starty = Math.min(down.y, up.y);
			int endx = startx+Math.abs(up.x - down.x);
			int endy = starty+Math.abs(up.y - down.y);
			int[] cdist = new int[ncategories];
			
			int curclass = 0;
			if(classification != null)
			{
				for(int y = starty; y <= endy && y < classification.length; y++)
					for(int x = startx; x <= endx && x < classification[0].length; x++)
						cdist[classification[y][x]]++;
				curclass = Util.getMaxIndex(cdist);
			}
			
	
			
			//ask the user what the correct classification should be
			JPopupMenu jpop = new JPopupMenu();
			int ncat = ncategories;
			java.awt.Component current = null;
			for(int i =0; i < ncat; i++) 
			{
				String menustring = i+" "+catnames[i];
				if(i == curclass)
					menustring += " *";
				JMenuItem menuitem = jpop.add(menustring);
				menuitem.addActionListener(this);
				if(i == curclass)
					current = menuitem;
			}
			jpop.setSelected(current);
			jpop.show(ImageDisplayPanel.this, e.getX(), e.getY());
		}
		
		public void mouseClicked(MouseEvent event)
		{
		}
		
		
		
		//process the users selection
		public void actionPerformed(java.awt.event.ActionEvent e)
		{
			if(e.getActionCommand().equals("Composite"))
			{
				paintmode = BLENDED; offscreen = null; repaint(); return;				
			}
			else if(e.getActionCommand().equals("Image only"))
			{
				paintmode = IMGONLY;  offscreen = null; repaint(); return;
			}
			else if(e.getActionCommand().equals("Type only"))
			{
				paintmode = CLASSONLY;  offscreen = null; repaint(); return;				
			}
			else if(e.getActionCommand().equals("RECLASSIFY"))
			{
				//use our new classifier to reclassify
				reclassify();
				return;
			}
			else if(e.getActionCommand().equals("CLASSIFY ALL"))
			{
				for(int i = 0; i < classifier.length; i++)
					classifier[i].compress();
				try { classifyall(); }
				catch(IOException ioe) {throw new RuntimeException(ioe);}
				return;
			}
			//else if(e.getActionCommand().equals("NEXT TREE"))
			//{
			//	if(currenttree == classifier.length)
			//		Debug.println("FINISHED LAST TREE.. CANNOT GO TO NEXT TREE", Debug.IMPORTANT);
			//	else
			//		classifier[currenttree++].compress();
			//	return;
			//}
			
			
			String intstr = Util.getWords(e.getActionCommand())[0];
			int cat = Integer.parseInt(intstr);
			
			//now tell the classifier to update itself
			int startx = Math.min(down.x, up.x);
			int starty = Math.min(down.y, up.y);
			int endx = startx+Math.abs(down.x-up.x);
			int endy = starty+Math.abs(down.y-up.y);
			Debug.print("updating classifier...", Debug.INFO);
			Datum[] train = new Datum[(endy-starty+1)*(endx-startx+1)];
			int tcount = 0;
			for(int y = starty; y <= endy; y++)
			{
				for(int x = startx; x <= endx; x++) 
				{
					Datum d = getDatum(red, green, blue, y, x, cat);
					//Datum d = new Datum(new double[] {red[y][x], green[y][x], blue[y][x], ranges[0], ranges[1], ranges[2], cat});
					train[tcount++] = d;
					//classifier.updateClassifier(red[y][x], green[y][x], blue[y][x], cat);
				}
				Debug.print(".", Debug.INFO);
			}
			//if(currenttree == classifier.length)
			//{
			//	Debug.println("YOU ARE TRYING TO TRAIN BUT THERE ARE NO UNTRAINED TREES LEFT", Debug.CRITICAL);
			//	return;
			//}
			//Debug.println("Updating tree "+currenttree+" of "+classifier.length, Debug.INFO);
			for(int i =0; i < classifier.length; i++)
				classifier[i/*currenttree*/].train(train, nodecapacity, -ncategories);
			double coremem = classifier[0].getEstimateOfCompressedMemoryRequired()/1000000.0;
			double tmpmem = classifier[0].getEstimateOfMemoryRequired()/1000000.0;
			Debug.println("done", Debug.INFO);
			Debug.println("... core memory for this tree is "+coremem*classifier.length+" MB ", Debug.INFO);
			Debug.println("... total (core+tmp) memory for this tree is "+(tmpmem+coremem)+" MB ", Debug.INFO);
		}
	}
	
	

	private static Datum getDatum(int[][] red, int[][] green, int[][] blue,
			int y, int x, int cat)
	{
		
		int minred = Integer.MAX_VALUE;
		int maxred = Integer.MIN_VALUE;
		int mingreen = Integer.MAX_VALUE;
		int maxgreen = Integer.MIN_VALUE;
		int minblue = Integer.MAX_VALUE;
		int maxblue = Integer.MIN_VALUE;
		
		for(int i =-1; i <= 1; i++)
			for(int j = -1; j <= 1; j++)
			{
				if(y+i < 0 || y+i >= red.length || x+j < 0 || x+j >= red[0].length)
					continue;
				
				//ok, indices are valid, we look for extreme values
				if(red[y+i][x+j] < minred) minred = red[y+i][x+j];
				if(red[y+i][x+j] > maxred) maxred = red[y+i][x+j];
				if(green[y+i][x+j] < mingreen) mingreen = green[y+i][x+j];
				if(green[y+i][x+j] > maxgreen) maxgreen = green[y+i][x+j];
				if(blue[y+i][x+j] < minblue) minblue = green[y+i][x+j];
				if(blue[y+i][x+j] > maxblue) maxblue = green[y+i][x+j];
					
			}
		
		//use HSB as well as RGB colour space model
		float[] hsb = Color.RGBtoHSB(red[y][x],green[y][x],blue[y][x],null);
		
		
		return new Datum(new double[] {red[y][x], green[y][x], blue[y][x], 
				maxred-minred, maxgreen-mingreen,maxblue-minblue, 
				red[y][x]/(1.0+blue[y][x]), red[y][x]/(1.0+green[y][x]),
				blue[y][x]/(1.0+green[y][x]), hsb[0], hsb[1], hsb[2], cat});
				

	}
	
	
	
	
	//classify every pixel in every map, based on
	//the current classification tree.
	//
	//Results are written to a new category image file.
	//
	//This can take a long time.
	//
	//
	private void classifyall() throws IOException
	{
		//go and get each x/y image and do the classification
		for(int x = 236; x <= 335; x++)
		{
			for(int y = 180; y <= 275; y++)
			{
				BufferedImage img = extractAerialImage(x, y);
				
				extractElevationImage(x,y); //updates red,green,blue
				extractTransportImage(x,y); //updates red,green.blue
				
				Graphics imgg = null;
				
				//go and get the image
				if(img == null)
				{
					img = new BufferedImage(500,500,BufferedImage.TYPE_BYTE_INDEXED);
					imgg = img.getGraphics();
					imgg.setColor(Color.white);
					imgg.fillRect(0,0,500,500);
				}
				else //got image, do classification
				{
					//create a copy of the raw image we are classifying from
					this.paintGraphics(img.getGraphics());
					ImageIO.write(img, "png", new java.io.File(x+"x"+y+".png"));
					if(this.classifier[0].isLeaf()) //A HACK
						continue; //no classifier yet, just dump out raw images
					
					imgg = img.getGraphics();
					//for(int i = 0; i < this.classification.length; i++)
						//for(int j = 0; j < this.classification[0].length; j++)
							//classification[i][j] = classifier[currenttree].getMostProbableClass(getDatum(red, green, blue, i, j, 0));

					//now write over the image with the class image and save that
					for(int yp = 0; yp < red.length; yp++)
					{
						for(int xp = 0; xp < red[0].length; xp++)
						{
							double[] cdist = RegressionTree.getClassDistribution(classifier, getDatum(red, green, blue, yp, xp, 0));
							classification[yp][xp] = Util.getMaxIndex(cdist);

							//HACK HACK HACK DEBUG
							//PUT IN FOR ONCE OFF TREE/GRASS/NONTREEGRASS CLASSIFICATION
							//IF WE ARENT SURE ITS A TREE OR GRASS, WE COUNT IT AS SOMETHING ELSE
							if(classification[yp][xp] > 1 || cdist[classification[yp][xp]] < 0.5)
								classification[yp][xp] = 2;

							
							int c = 0;
							c = classification[yp][xp];
							int cr = colours[c].getRed();
							int cg = colours[c].getGreen();
							int cb = colours[c].getBlue();
								
							int rd = cr;
							int grn = cg;
							int bl = cb;
							
							imgg.setColor(new Color(rd, grn, bl));
							imgg.fillRect(xp, yp, 1, 1);
						}
						Debug.print(".", Debug.INFO);
					}
					Debug.println("done", Debug.INFO);
				}

					
				ImageIO.write(img, "png", new java.io.File("class"+x+"x"+y+".png"));
			}
		}
	}
	

	private void reclassify()
	{
		//now reclassify the image
		Debug.print("reclassifying... please wait...", Debug.INFO);
		for(int y = 0; y < classification.length; y++)
		{
			for(int x = 0; x < classification[0].length; x++)
			{
				Datum d = getDatum(red, green, blue, y, x, 0);
				//if(currenttree == 0)
					/* No built and compressed classifiers yet, so we skip */;
					//classification[y][x] = classifier[currenttree].getMostProbableClass(d);					
				//else
				//{
					//RegressionTree[] tmpforest = new RegressionTree[currenttree];
					//System.arraycopy(classifier, 0, tmpforest, 0, currenttree); 

					double[] cdist = RegressionTree.getClassDistribution(classifier /*tmpforest*/, d);
					classification[y][x] = Util.getMaxIndex(cdist);
					
					//HACK HACK HACK DEBUG
					//PUT IN FOR ONCE OFF TREE/NONTREE CLASSIFICATION
					//IF WE ARENT SURE ITS A TREE, WE COUNT IT AS SOMETHING ELSE
					if(classification[y][x] > 1 || cdist[classification[y][x]] < 0.5)
						classification[y][x] = 2;

				//}
			}
			Debug.print(".", Debug.INFO);
		}
		Debug.println("done", Debug.INFO);
		
		Debug.println("Displaying classifier:", Debug.INFO);
		Debug.println(this.classifier.toString(), Debug.INFO);
		
		//we need to redraw our screen image
		offscreen = null;
		
		//force a repaint
		repaint();
	}
	
	public ImageDisplayPanel(int terminalNodeSize, int numclasses, String fileroot)
	{
		ClickHandler handle = new ClickHandler();
		this.addMouseListener(handle);
		this.addKeyListener(handle);
		this.setFocusable(true);
		this.fileroot = fileroot;
		this.ncategories = numclasses;
		
		//this.classifier = new PartitionTreeForest(50, classifierdepth, numclasses);
		this.classifier = new RegressionTree[12];
		for(int i =0; i < classifier.length; i++)
			classifier[i] = new RegressionTree(RegressionTree.CONSTANT_SPLITFUNCTION, 4.0);
		this.nodecapacity = terminalNodeSize;
	}
	
	
	
	
	public void setImageToDisplay(int xpos, int ypos)
	{
		this.imgx = xpos;
		this.imgy = ypos;
		extractAerialImage(xpos, ypos);
		extractElevationImage(xpos, ypos);
		//extractTransportImage(xpos, ypos);

		offscreen = null; //force redraw
		repaint();
	}






	private BufferedImage extractAerialImage(int xpos, int ypos)
	{
		
		BufferedImage image = null;
		String imgfile = "aerial"+System.getProperty("file.separator")+xpos+"x"+ypos+".jpg";
		Debug.println("looking for file "+imgfile, Debug.INFO);
		
		try {
			image = ImageIO.read(new java.io.File(fileroot, imgfile));
		}
		catch(java.io.IOException ioe) {
			image = null;
			Debug.println("Could not load image "+imgfile, Debug.IMPORTANT);
			try {
				imgfile = "aerialJfromI/"+xpos+"x"+ypos+".jpg";
				image = ImageIO.read(new java.io.File(fileroot, imgfile));
			}
			catch(Exception ioe2) {
				Debug.println("Could not load even BACKUP image "+imgfile, Debug.CRITICAL);
				image = null;
			}
		}
		
		
		//special case when the image is null. We
		//just draw white
		if(image == null)
		{
			Debug.println("filling in with blank image", Debug.INFO);
			int height = this.getHeight();
			int width = this.getWidth();
			this.red = new int[height][width];
			this.green = new int[height][width];
			this.blue = new int[height][width];
			this.classification = new int[height][width];
			return null;
		}
		
		Debug.println("Loading image. please wait", Debug.IMPORTANT);
		
		int width = image.getWidth();
		int height = image.getHeight();
		
		this.setSize(width, height);

		
		this.red = new int[height][width];
		this.green = new int[height][width];
		this.blue = new int[height][width];
		this.classification = new int[height][width];

		ColorModel cm = ColorModel.getRGBdefault(); 
		
		for(int y = 0; y < height; y++)
		{
			for(int x =0; x < width; x++)
			{
				int rgb = image.getRGB(x, y);
				red[y][x] = cm.getRed(rgb);
				green[y][x] = cm.getGreen(rgb);
				blue[y][x] = cm.getBlue(rgb);
			}
		}
		
		Debug.println("Finished loading image", Debug.IMPORTANT);
		return image;
	}





	public static final int ELEVSCALE = 8;	
	private void extractElevationImage(int xpos, int ypos)
	{
		
		BufferedImage image = null;
		String imgfile = "elev"+System.getProperty("file.separator")+(xpos/ELEVSCALE)+"x"+(ypos/ELEVSCALE)+".jpg";
		Debug.println("looking for file "+imgfile, Debug.INFO);
		
		try {
			image = ImageIO.read(new java.io.File(fileroot, imgfile));
		}
		catch(java.io.IOException ioe) {
			image = null;
			Debug.println("Could not load image "+imgfile, Debug.IMPORTANT);
		}
		
		
		//special case when the image is null. We
		//dont draw anything
		if(image == null)
		{
			Debug.println("blank image", Debug.INFO);
			return;
		}
		
		Debug.println("Loading image. please wait", Debug.IMPORTANT);
		
		int width = image.getWidth();
		int height = image.getHeight();
		ColorModel cm = ColorModel.getRGBdefault(); 

		if(width != red[0].length || height != red.length)
			throw new RuntimeException("Elevation image does not match other images.... code to handle irregular overlay needs to be written");	

		//int xoffset = (int) Math.round((xpos % ELEVSCALE * width)/((double)ELEVSCALE));
		//int yoffset = (int) Math.round((ypos % ELEVSCALE * height)/((double)ELEVSCALE));
		//Debug.println("in elev, x offset is "+xoffset, Debug.INFO);
		//Debug.println("in elev, y offset is "+yoffset, Debug.INFO);
	

		int elevxpos = xpos/ELEVSCALE;
		int elevypos = ypos/ELEVSCALE;

		int eoffy =ypos*height-elevypos*height*ELEVSCALE; 
		int eoffx =xpos*width -elevxpos*width *ELEVSCALE; 
		Debug.println("elev x offset is ~"+eoffx/ELEVSCALE, Debug.INFO);
		Debug.println("elev y offset is ~"+eoffy/ELEVSCALE, Debug.INFO);
		for(int y = 0; y < height; y++)
		{
			for(int x = 0; x < width; x++)
			{
				int ey = (eoffy+y)/ELEVSCALE;
				int ex = (eoffx+x)/ELEVSCALE;
			
				int rgb = image.getRGB(ex, height-ey-1);
				if(cm.getRed(rgb) < 20 && cm.getBlue(rgb) < 20 && cm.getGreen(rgb) < 20)
				{
					red[height-y-1][x] = cm.getRed(rgb);
					green[height-y-1][x] = cm.getGreen(rgb);
					blue[height-y-1][x] = cm.getBlue(rgb);
				}
				
			}
		}

		Debug.println("Finished loading image", Debug.IMPORTANT);
	}



	
	private void extractTransportImage(int xpos, int ypos)
	{
		
		BufferedImage image = null;
		String imgfile = "transport"+System.getProperty("file.separator")+"transport_"+xpos+"x"+ypos+".png";
		Debug.println("looking for file "+imgfile, Debug.INFO);
		
		try {
			image = ImageIO.read(new java.io.File(fileroot, imgfile));
		}
		catch(java.io.IOException ioe) {
			image = null;
			Debug.println("Could not load image "+imgfile, Debug.IMPORTANT);
			
			//try a backup image of lower resolution
			imgfile = "transportJfromI/transport_"+xpos+"x"+ypos+".png";
			try {
				image = ImageIO.read(new java.io.File(fileroot, imgfile));
			}
			catch(IOException ioe2) {
				Debug.println("Could not load even backup image "+imgfile, Debug.IMPORTANT);
			}
		}
		
		
		//special case when the image is null. We
		//dont draw anything
		if(image == null)
		{
			Debug.println("blank image", Debug.INFO);
			return;
		}
		
		Debug.println("Loading image. please wait", Debug.IMPORTANT);
		
		int width = image.getWidth();
		int height = image.getHeight();
	
		this.trans_red = new int[height][width];
		this.trans_green = new int[height][width];
		this.trans_blue = new int[height][width];

		if(height != this.red.length || width != this.red[0].length)
		{
			//try a backup image of lower resolution
			imgfile = "transportJfromI/transport_"+xpos+"x"+ypos+".png";
			try {
				image = ImageIO.read(new java.io.File(fileroot, imgfile));
				Debug.println("Found backup image of height/width "+image.getHeight()+"/"+image.getWidth(), Debug.IMPORTANT);
			}
			catch(IOException ioe2) {
				Debug.println("Could not load even backup image "+imgfile, Debug.IMPORTANT);
			}
			if(image == null || image.getWidth() != this.red[0].length || image.getHeight() != this.red.length)
				throw new RuntimeException("transport image does not match aerial image, and cant find appropriate backup");

			width = image.getWidth();
			height = image.getHeight();
		
			this.trans_red = new int[height][width];
			this.trans_green = new int[height][width];
			this.trans_blue = new int[height][width];
		}

		ColorModel cm = ColorModel.getRGBdefault(); 
		
		for(int y = 0; y < height; y++)
		{
			for(int x =0; x < width; x++)
			{
				int rgb = image.getRGB(x, y);
				if(cm.getRed(rgb) > 220 && cm.getGreen(rgb) < 30 && cm.getBlue(rgb) < 30)
					/* do nothing... no road information */;
				else
				{
					red[y][x] = cm.getRed(rgb);
					green[y][x] = cm.getGreen(rgb);
					blue[y][x] = cm.getBlue(rgb);
				}
			}
		}
		
		Debug.println("Finished loading image", Debug.IMPORTANT);
	}



	
	private BufferedImage offscreen = null;
	
	
	//draws the current image into this graphics object
	public void paintGraphics(Graphics g)
	{
		for(int y = 0; y < red.length; y++)
		{
			for(int x = 0; x < red[0].length; x++)
			{
				int rd = red[y][x];
				int grn = green[y][x];
				int bl = blue[y][x];
				if(paintmode != IMGONLY)
				{
					int c = 0;
					if(classification != null) c = classification[y][x];
					int cr = colours[c].getRed();
					int cg = colours[c].getGreen();
					int cb = colours[c].getBlue();
					
					if(paintmode == BLENDED)
					{
						//now do the alpha blending
				
						rd = (int) (rd*0.7+cr*0.3);
						grn = (int) (grn*0.7+cg*0.3);
						bl = (int) (bl*0.7+cb*0.3);
					}
					else if(paintmode == CLASSONLY)
					{
						rd = cr;
						grn = cg;
						bl = cb;
					}
				}
				
				g.setColor(new Color(rd, grn, bl));
				g.fillRect(x, y, 1, 1);
			}
			Debug.print(".", Debug.INFO);
		}
		Debug.println("done", Debug.INFO);
	}
	
	
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);

		//generate offscreen if we need to
		if(this.offscreen == null && this.red != null)
		{
			Debug.print("Creating offscreen buffered image...", Debug.INFO);
			this.offscreen = new BufferedImage(this.getWidth(), this.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
			//this.offscreen = this.createImage(this.getWidth(), this.getHeight());
			
			//create our offscreen image
			Graphics bufg = offscreen.getGraphics();
			//draw into it 
			paintGraphics(bufg);
		}
		
		//update offscreen image
		if(offscreen != null)
			g.drawImage(offscreen, 0, 0, this);
		
		
		
		//now draw the selection rectangle, if there is one
		Point u = up;
		Point d = down;
		if(u != null && d != null)
		{
			int x = Math.min(down.x, up.x);
			int y = Math.min(down.y, up.y);
			g.setColor(Color.RED);
			g.drawRect(x, y, Math.abs(up.x-down.x), Math.abs(up.y-down.y));
		}
	}
	
	
	
	public static void main(String[] args) throws Exception
	{
		main1(args);
		main4(args);
	}
	
	
	//go and split up all the level I images in the aerialI directory
	public static void main1(String[] args) throws Exception
	{
		File[] files = new java.io.File("aerialI").listFiles();
		
		for(int i = 0; i < files.length; i++)
		{			
			//try a higher level
			String imgfile = files[i].getName();
			String[] bits = imgfile.split("\\.");
			if(bits.length < 2 || !bits[1].equals("jpg")) 
			{
				for(int j =0; j < bits.length; j++)
					Debug.print(bits[j]+" ", Debug.IMPORTANT);
				Debug.println("", Debug.IMPORTANT);
				Debug.println(imgfile+" is not an image file, skipping", Debug.IMPORTANT);
				continue; //not an image file
			}
			String[] xystr = imgfile.split("\\.")[0].split("x");
			int x = Integer.parseInt(xystr[0]);
			int y = Integer.parseInt(xystr[1]);
			try {
				BufferedImage image = ImageIO.read(new java.io.File("aerialI", imgfile));
				//now create the sub-images
				int jx = x*2;
				int jy = y*2;
				ImageIO.write(image.getSubimage(0, 0, 250, 250), "jpg", new File("aerialJfromI/"+jx+"x"+(jy+1)+".jpg"));
				ImageIO.write(image.getSubimage(250, 0, 250, 250), "jpg", new File("aerialJfromI/"+(jx+1)+"x"+(jy+1)+".jpg"));
				ImageIO.write(image.getSubimage(0, 250, 250, 250), "jpg", new File("aerialJfromI/"+jx+"x"+jy+".jpg"));
				ImageIO.write(image.getSubimage(250, 250, 250, 250), "jpg", new File("aerialJfromI/"+(jx+1)+"x"+jy+".jpg"));
			}
			catch(IOException ioe2) {
				throw new RuntimeException("Couldnt even get backup image "+imgfile);
			}
			
		}
		
		

	}
	
	
	//go and work out which images we dont have level J versions of
	public static void main2(String[] args)
	{
		//go and get each x/y image and do the classification
		for(int x = 236; x <= 335; x++)
		{
			for(int y = 180; y <= 275; y++)
			{
				//go and get the image
				BufferedImage image = null;
				String imgfile = "aerial"+System.getProperty("file.separator")+x+"x"+y+".jpg";
				Debug.println("looking for file "+imgfile, Debug.INFO);
				
				try {
					image = ImageIO.read(new java.io.File(".", imgfile));
				}
				catch(java.io.IOException ioe) {
					image = null;
					Debug.println("Could not load image "+imgfile, Debug.IMPORTANT);
				}
			}
		}
	}

	
	//	go and work out which images we dont have level J versions of
	public static void main3(String[] args)
	{
		//go and get each x/y image and do the classification
		for(int x = 236; x <= 335; x++)
		{
			for(int y = 180; y <= 275; y++)
			{
				//go and get the image
				BufferedImage image = null;
				String imgfile = "transport"+System.getProperty("file.separator")+"transport_"+x+"x"+y+".png";
				Debug.println("looking for file "+imgfile, Debug.INFO);
				
				try {
					image = ImageIO.read(new java.io.File(".", imgfile));
				}
				catch(java.io.IOException ioe) {
					image = null;
					Debug.println("Could not load image "+imgfile, Debug.IMPORTANT);
				}
			}
		}
	}

	
	
	
	
	

	//go and split up all the level I images in the transportI directory
	public static void main4(String[] args) throws Exception
	{
		File[] files = new java.io.File("transportI").listFiles();
		
		for(int i = 0; i < files.length; i++)
		{			
			//try a higher level
			String imgfile = files[i].getName();
			String[] bits = imgfile.split("\\.");
			if(bits.length < 2 || !bits[1].equals("png")) 
			{
				for(int j =0; j < bits.length; j++)
					Debug.print(bits[j]+" ", Debug.IMPORTANT);
				Debug.println("", Debug.IMPORTANT);
				Debug.println(imgfile+" is not an image file, skipping", Debug.IMPORTANT);
				continue; //not an image file
			}
			String[] xystr = imgfile.split("\\.")[0].split("x");
			int x = Integer.parseInt(xystr[0]);
			int y = Integer.parseInt(xystr[1]);
			try {
				BufferedImage image = ImageIO.read(new java.io.File("transportI", imgfile));
				//now create the sub-images
				int jx = x*2;
				int jy = y*2;
				ImageIO.write(image.getSubimage(0, 0, 250, 250), "png", new File("transportJfromI/transport_"+jx+"x"+(jy+1)+".png"));
				ImageIO.write(image.getSubimage(250, 0, 250, 250), "png", new File("transportJfromI/transport_"+(jx+1)+"x"+(jy+1)+".png"));
				ImageIO.write(image.getSubimage(0, 250, 250, 250), "png", new File("transportJfromI/transport_"+jx+"x"+jy+".png"));
				ImageIO.write(image.getSubimage(250, 250, 250, 250), "png", new File("transportJfromI/transport_"+(jx+1)+"x"+jy+".png"));
			}
			catch(IOException ioe2) {
				throw new RuntimeException("Couldnt even get backup image "+imgfile);
			}
			
		}
		
		

	}

	
	
	
	
	
	
	
	
}





