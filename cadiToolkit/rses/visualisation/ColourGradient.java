package rses.visualisation;



import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import rses.Debug;



public class ColourGradient
{
	private double huestart, hueend;
	private double satstart, satend;
	private double brightstart, brightend;

	private double start, end, width;
	
	public ColourGradient(Color colour1, double num1, Color colour2, double num2)
	{
		Color c1 = colour1;
		Color c2 = colour2;
		start = num1;
		end = num2;
		if(start > end) {
			c1 = colour2;
			c2 = colour1;
			start = num2;
			end = num1;
		}
		
		float[] hsbvals = Color.RGBtoHSB(c1.getRed(), c1.getGreen(), 
				c1.getBlue(), null);
		
		huestart = hsbvals[0];
		satstart = hsbvals[1];
		brightstart = hsbvals[2];

		Color.RGBtoHSB(c2.getRed(), c2.getGreen(), 
				c2.getBlue(), hsbvals);
		hueend = hsbvals[0];
		satend = hsbvals[1];
		brightend = hsbvals[2];
		

		width=end-start;
	}

	
	public Color getColour(double num)
	{
		if(num > this.end) {
			Debug.println("colour greater than range covered by colour gradient. Truncating at top value", Debug.INFO);
			num = this.end; 
		}
		else if (num < this.start) {
			Debug.println("colour less than range covered by colour gradient. Truncating at minimum value", Debug.INFO);
			num = this.start;
		}
		
		double hue = huestart + 
		((num-start)/width) * (hueend-huestart);
		
		double sat = satstart + 
		((num-start)/width) * (satend-satstart);
		
		double bright = brightstart + 
		((num-start)/width) * (brightend-brightstart);

		if(width == 0) { //special case if start and end of gradient are the same. We just take the middle colour
			hue = (huestart + hueend)/2;
			sat = (satstart + satend)/2;
			bright = (brightstart + brightend)/2;
		}
		
		return Color.getHSBColor((float)hue, (float)sat, (float)bright);
	}


	public Color[] getGradientColours(int numbins)
	{
		Color[] result = new Color[numbins];

		double d = start;
		double step = (end-start)/numbins;
		for(int i =0; i < numbins; i++)
		{
			result[i] = this.getColour(d);
			d += step;
		}

		return result;
	}
		

	public void printGradientToFile(String filename)
	throws IOException
	{
		BufferedImage bim2 = new BufferedImage(600, 200, BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g2d = bim2.createGraphics();
		Graphics g = g2d;
		
		g.setColor(Color.GRAY);
		g.fillRect(0, 0, 600, 200);
		g.setColor(Color.BLACK);
		this.paintColourScale(g, 600, 200, false);
	 
		ImageIO.write(bim2, "png", new File(filename));
	}
	
	
	/** Paint this Colour Gradient on a Graphics object, with the 
	 *  corresponding data values as well.
	 * 
	 * @param g
	 * @param width Width of the graphics object to draw on
	 * @param height Height of the graphics object to draw on 
	 * @param logscale If logscale is true, then we use the exponential
	 *                 transform to put data value (labels) on the scale,
	 *                 as plotting the log values is not that helpful.
	 *                 Thus, in order to use this method, you need to know
	 *                 if this ColourGradient object is a gradient of 
	 *                 log data or just the raw data.
	 */
	public void paintColourScale(Graphics g, int width, int height, boolean logscale)
	{
		paintColourScale(g, width, height, logscale, 8);
	}
	
	public void paintColourScale(Graphics g, int width, int height, boolean logscale, int printwidth)
	{
		Color oldcol = g.getColor();
		ColourGradient grad = new ColourGradient(this.getColour(this.start), 0, this.getColour(this.end), 3*width/4);
		for(int i = 0; i < 3*width/4; i++) {
			g.setColor(grad.getColour(i));
			g.drawLine(width/8+i, height/4, width/8+i, height/2);
		}


		java.awt.FontMetrics fm = g.getFontMetrics();
		int lineheight =  fm.getMaxAscent()+fm.getMaxDescent()+fm.getLeading(); 

		String start, mid, end, error = "";
		for(int i = 0; i < printwidth; i++) error += "X";
		if(logscale)
		{
			start = rses.util.Util.safeGetTruncatedReal(Math.exp(this.start)-1, printwidth, error);
			end = rses.util.Util.safeGetTruncatedReal(Math.exp(this.end)-1, printwidth, error);
			mid = rses.util.Util.safeGetTruncatedReal(Math.exp((this.start+this.end)/2)-1, printwidth, error);
		}
		else
		{
			start = rses.util.Util.safeGetTruncatedReal(this.start, printwidth, error);
			end = rses.util.Util.safeGetTruncatedReal(this.end, printwidth, error);
			mid = rses.util.Util.safeGetTruncatedReal((this.start+this.end)/2, printwidth, error);			
		}
		g.setColor(oldcol);
		g.drawString(start, width/8-fm.stringWidth(start)/2, height/2+lineheight+5); 
		g.drawString(end, width/8+3*width/4-fm.stringWidth(end)/2, height/2+lineheight+5);
		g.drawString(mid, width/2-fm.stringWidth(mid)/2, height/2+lineheight+5);
			

		g.drawLine(width/8, height/2, width/8, height/2+5);
		g.drawLine(width/8+3*width/4-1, height/2, width/8+3*width/4-1, height/2+5);
		g.drawLine(width/2, height/2, width/2, height/2+5);

		
	}
	
	
	/**
	 * 
	 * @param g
	 * @param width
	 * @param height
	 * @param logscale
	 * @param ntics  Number of tics.
	 */
	public void paintDiscreteColourScale(Graphics g, int width, int height, boolean logscale, int ntics, String[] clusterlabels)
	{
		Color oldcol = g.getColor();
		ColourGradient grad = new ColourGradient(this.getColour(this.start), 0, this.getColour(this.end), ntics-1);
		//draw the colour scale
		for(int i = 0; i < 3*height/4; i++) {
			int cluster = (ntics*i)/(3*height/4);
			g.setColor(grad.getColour(cluster));
			g.drawLine(width/8, height/8+i, 7*width/8, height/8+i);
		}
		
		//now draw the labels
		int printwidth = 4*(width/200);
		java.awt.FontMetrics fm = g.getFontMetrics();
		int lineheight =  fm.getMaxAscent()+fm.getMaxDescent()+fm.getLeading(); 
		boolean[] done = new boolean[clusterlabels.length];
		g.setFont(g.getFont().deriveFont(20f));
		for(int i = 0; i < 3*height/4; i++) 
		{
			int cluster = (ntics*i)/(3*height/4);
			if(done[cluster]) continue;
			done[cluster] = true;
			
			g.setColor(Color.white);
			g.drawString(clusterlabels[cluster], width/8, height/8+i+height/(2*ntics));
		}
		
		
		g.setColor(oldcol);
	}
	
	
	
	
	
	public static void main(String[] args) throws IOException
	{
		new ColourGradient(Color.blue, 0.0, Color.pink, 100.0).printGradientToFile("grad1.png");
		new ColourGradient(Color.blue, 0.0, Color.yellow, 100.0).printGradientToFile("grad2.png");
		new ColourGradient(Color.blue, 0.0, Color.green, 100.0).printGradientToFile("grad3.png");
		new ColourGradient(Color.pink, 0.0, Color.magenta, 100.0).printGradientToFile("grad4.png");
		new ColourGradient(Color.blue, 0.0, Color.magenta, 100.0).printGradientToFile("grad5.png");
		
	}
	
	public static void main2(String[] args)
	throws Exception
	{
	
		javax.swing.JFrame f = new javax.swing.JFrame();
		f.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);

		f.setSize(600, 600);
		f.setVisible(true);
		f.show();

		System.out.println("Calling 'plot' at 1 second intervals");

		ColourGradient cg = new ColourGradient(Color.blue, 0.0, 
				                       Color.red, 1.0);
		ColourGradient cg2 = new ColourGradient(Color.white, 0.0, 
				                       Color.black, 1.0);
		ColourGradient cg3 = new ColourGradient(Color.green, 0.0, 
				                       Color.red, 1.0);
		Color[] colours = cg.getGradientColours(100);
		Color[] colours2 = cg2.getGradientColours(100);
		Color[] colours3 = cg3.getGradientColours(100);
	
		
		while(true)
		{
			for(int i = 0; i < colours.length; i++) {
				java.awt.Graphics2D g2 = (java.awt.Graphics2D) f.getGraphics();
				g2.setColor(colours[i]);
				g2.fillRect(50+i*5, 50, 5, 100);
				g2.setColor(colours2[i]);
				g2.fillRect(50+i*5, 200, 5, 100);
				g2.setColor(colours3[i]);
				g2.fillRect(50+i*5, 350, 5, 100);
			}
		}

	}
}






