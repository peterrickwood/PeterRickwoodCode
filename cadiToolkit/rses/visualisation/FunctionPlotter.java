package rses.visualisation;

import java.awt.*;


import rses.math.MathUtil;
import rses.math.RealFunction;
import rses.util.Util;

import java.awt.geom.Rectangle2D;

public abstract class FunctionPlotter
{
	/** The size (in pixels) of a point in any of the drawPointsXX() methods. 
	 */
	public static int POINT_SIZE = 4;


	public static void setPointSize(int ps)
	{
		POINT_SIZE = ps;
	}
	
	
	public static int getPointSize()
	{
		return POINT_SIZE;
	}

	
	public static Rectangle drawHistogramGraph(
			java.awt.Graphics g,
			double[] points, boolean plotdensity,
			int numbins, int xpixels, int ypixels,
			String graphname, int labelwidth)
	{
		double xlower = Util.getMin(points);
		double xupper = Util.getMax(points);
		double binwidth = (xupper-xlower)/numbins;
		int[] bins = MathUtil.getHistogram(points, numbins);
		return drawHistogramGraph(g, bins, plotdensity, xlower, binwidth, xpixels, ypixels, graphname, labelwidth);
	}
	
	
	
	
	
	public static Rectangle drawHistogramGraph(
			java.awt.Graphics g,
			int[] bins, boolean plotdensity,
			double xlower, double binwidth,
			int xpixels, int ypixels,
			String graphname, int labelwidth)
	{
		double min = 0;
		double max = 1.0;
		Color oldcol = g.getColor();
		if(!plotdensity)
			max = Util.getMax(bins);
		else
			max = Util.getMax(bins)/((double) Util.getSum(bins));
		g.setColor(Color.black); //always draw axes in black
		Rectangle graphrect = FunctionPlotter.drawAxesAndTitle(g, 
				graphname, xlower, xlower+bins.length*binwidth, 
		        min, max, xpixels, ypixels, labelwidth);
		g.setColor(oldcol);
		//now draw the histogram
		Graphics graphgraphics = g.create(graphrect.x, graphrect.y, graphrect.width, graphrect.height);
		drawHistogram(graphgraphics, bins, plotdensity, graphrect.width, graphrect.height);
		
		return graphrect;
	}
	

	public static Rectangle drawGraph2D(
			java.awt.Graphics g,
			java.util.List points,
			double xlower, double xupper,
			double ylower, double yupper,
			int xpixels, int ypixels,
			String graphname
		)
	{
		return drawGraph2D(g, points, xlower, xupper, ylower, yupper, xpixels, ypixels, graphname, Integer.MAX_VALUE);
	}


	/** Plots a bunch of points on a graph, and also draws the axes and a rudimentary scale for
	 *   the axes. Returns a Rectangle object representing the area that is occupied by the 
	 *   points alone (excluding the axes and other border stuff)
	 * 
	 * @param g
	 * @param points
	 * @param xlower
	 * @param xupper
	 * @param ylower
	 * @param yupper
	 * @param xpixels
	 * @param ypixels
	 * @param graphname
	 * @param axislabelwidth
	 * @return
	 */
	public static Rectangle drawGraph2D(
			java.awt.Graphics g,
			java.util.List points,
			double xlower, double xupper,
			double ylower, double yupper,
			int xpixels, int ypixels,
			String graphname, int axislabelwidth
		)
	{
		Rectangle newbounds = drawAxesAndTitle(g, graphname, xlower, xupper, ylower, yupper, xpixels, ypixels, axislabelwidth);
		//System.err.println("new bounds are "+newbounds);
			
		Graphics g2 = g.create(newbounds.x, newbounds.y, newbounds.width, newbounds.height);
			
		if(points.size() != 0)
			plotPoints2D(g2, points, xlower, xupper, ylower, yupper, newbounds.width, 
			                 newbounds.height);
		return newbounds;
	}
	
	
	
	

	public static Rectangle drawGraph3D(
			java.awt.Graphics g,
			java.util.List points,
			double xlower, double xupper,
			double ylower, double yupper,
			double zlower, double zupper,
			int xpixels, int ypixels,
			String graphname
		)
	{
		return drawGraph3D(g, points, xlower, xupper, ylower, yupper, zlower, zupper, xpixels, ypixels,
			graphname, Integer.MAX_VALUE);
	}

	/** Plots a bunch of points on a graph, and also draws the axes and a rudimentary scale for
	 *   the axes. Returns a Rectangle object representing the area that is occupied by the 
	 *   points alone (excluding the axes and other border stuff)
	 * 
	 * @param g
	 * @param points
	 * @param xlower
	 * @param xupper
	 * @param ylower
	 * @param yupper
	 * @param zlower
	 * @param zupper
	 * @param xpixels
	 * @param ypixels
	 * @param graphname
	 * @param axislabelwidth
	 * @return
	 */
	public static Rectangle drawGraph3D(
			java.awt.Graphics g,
			java.util.List points,
			double xlower, double xupper,
			double ylower, double yupper,
			double zlower, double zupper,
			int xpixels, int ypixels,
			String graphname, int axislabelwidth
		)
	{
		Rectangle newbounds = drawAxesAndTitle(g, graphname, xlower, xupper, ylower, yupper, xpixels, ypixels, axislabelwidth);
			
		Graphics g2 = g.create(newbounds.x, newbounds.y, newbounds.width, newbounds.height);
		
		if(points.size() != 0)	
			plotPoints3D(g2, points, xlower, xupper, ylower, yupper, zlower, zupper, 
			     newbounds.width, newbounds.height);
		
		return newbounds;
	}

		
	
	
	public static void drawHistogram(Graphics g, 
			int[] bins, boolean plotdensity,
			int xpixels, int ypixels)
	{
		double binpixelwidth = ((double) xpixels)/bins.length;
		int height = 0;
		double max = -1;
		max = Util.getMax(bins);
		Color oldcol = g.getColor();

		int lastend = 0;
		
		for(int i = 0; i < bins.length; i++)
		{
			height = (int) ((bins[i]/max)*ypixels); 
			double end = (i+1)*binpixelwidth;
			
			g.fillRect(lastend, ypixels-height, (int)Math.floor(end-lastend+0.5), height);
			g.setColor(Color.BLACK);
			g.drawRect(lastend, ypixels-height, (int)Math.floor(end-lastend+0.5), height);
			g.setColor(oldcol);
			lastend = lastend + (int)Math.floor(end-lastend+0.5);
		}
	}
	

	
	
	
	public static void plotPoints2D(java.awt.Graphics g,
		java.util.List points,
		double xlower, double xupper,
		double ylower, double yupper,
		int xpixels, int ypixels
	)
	{
		plotPoints2D(g, points, xlower, xupper, ylower, yupper, 
				xpixels, ypixels, Color.red, Color.blue);
	}

	
	public static void plotPoints2D(java.awt.Graphics g,
			java.util.List points,
			double xlower, double xupper,
			double ylower, double yupper,
			int xpixels, int ypixels,
			Color lowerColour, Color upperColour
		)
	{
		int numpoints = points.size();
		double xrangeinv = 1.0/(xupper-xlower);
		double yrangeinv = 1.0/(yupper-ylower);
		ColourGradient cg = new ColourGradient(lowerColour, ylower, upperColour, yupper);
		Color oldcol = g.getColor();

		for(int i =0; i < numpoints; i++)
		{
			double[] point = (double[]) points.get(i);
			if(point.length != 2)
				throw new IllegalArgumentException("points have wrong dimension... expected 2, got "+point.length);
			
			//now plot that point
			int xpos = (int) Math.round(((point[0]-xlower)*xrangeinv*xpixels)-POINT_SIZE/2);
			int ypos = (int) Math.round(((ypixels-POINT_SIZE)-(point[1]-ylower)*yrangeinv*ypixels)-POINT_SIZE/2);
			
			if(xpos >= xpixels || ypos >= ypixels)
				throw new RuntimeException("xpos/xpixels "+xpos+"/"+xpixels+" ypos/ypixels "+ypos+"/"+ypixels+" "+point[0]+","+point[1]+"  ["+xlower+" , "+xupper+"]"+" ["+ylower+" , "+yupper+"]");
			
			g.setColor(Color.black);
			g.drawRect(xpos, ypos, POINT_SIZE, POINT_SIZE);
			g.setColor(cg.getColour(point[1]));
			g.fillRect(xpos+1, ypos+1, 2, 2);
		}
		
		g.setColor(oldcol);
	}
	
	
	public static void plotPoints3D(java.awt.Graphics g,
			java.util.List points,
			double xlower, double xupper,
			double ylower, double yupper,
			double zlower, double zupper,
			int xpixels, int ypixels
		)
	{
		plotPoints3D(g, points, xlower, xupper, ylower, yupper, 
				zlower, zupper, xpixels, ypixels, Color.red, Color.blue);
	}
	
	public static void plotPoints3D(java.awt.Graphics g,
		java.util.List points,
		double xlower, double xupper,
		double ylower, double yupper,
		double zlower, double zupper,
		int xpixels, int ypixels,
		Color lwrcolour, Color uppercolour
	)
	{
		int numpoints = points.size();
		double xrangeinv = 1.0/(xupper-xlower);
		double yrangeinv = 1.0/(yupper-ylower);
		
		ColourGradient cg = new ColourGradient(lwrcolour, zlower, uppercolour, zupper);
		Color oldcol = g.getColor();
				
		for(int i =0; i < numpoints; i++)
		{
			double[] point = (double[]) points.get(i);
			if(point.length != 3)
				throw new IllegalArgumentException("points have wrong dimension... expected 3, got "+point.length);
			
			//now plot that point
			//int xpos = (int) ((point[0]-xlower)*xrangeinv*(xpixels-POINT_SIZE));
			//int ypos = (int) (ypixels-(point[1]-ylower)*yrangeinv*(ypixels-POINT_SIZE));
		
			int xpos = (int) Math.round(((point[0]-xlower)*xrangeinv*xpixels)-POINT_SIZE/2);
			int ypos = (int) Math.round((ypixels-(point[1]-ylower)*yrangeinv*ypixels)-POINT_SIZE/2);
			//System.out.println("xpos,ypos in plotting for point "+point[0]+" "+point[1]+" are "+xpos+" "+ypos);
			
			Color newcol = cg.getColour(point[2]);
			g.setColor(newcol);
			
			g.fillRect(xpos, ypos, POINT_SIZE, POINT_SIZE);
		}
		
		g.setColor(oldcol);		
	}





	

	public static Rectangle drawAxesAndTitle(java.awt.Graphics g,
		String title, double xlower, double xupper, 
		double ylower, double yupper,
		int xpixels, int ypixels, int axislabelwidth)
	{
		java.awt.FontMetrics fm = g.getFontMetrics();
		int xinset = fm.stringWidth("0.000");
		int lineheight =  fm.getMaxAscent()+fm.getMaxDescent()+fm.getLeading(); 
		int yinset = lineheight*2;
			
		//draw axis labels
		g.drawString(Util.safeGetTruncatedReal(yupper, axislabelwidth, "XXX"), 0, fm.getMaxAscent());
		g.drawString(Util.safeGetTruncatedReal(ylower, axislabelwidth, "XXX"), 0, ypixels-yinset);
		g.drawString(Util.safeGetTruncatedReal(xlower, axislabelwidth, "XXX"), xinset, ypixels-lineheight-fm.getMaxDescent());
		String labstr = Util.safeGetTruncatedReal(xupper, axislabelwidth, "XXX");
		g.drawString(labstr, xpixels-fm.stringWidth(labstr), ypixels-lineheight-fm.getMaxDescent());
			
		//draw axes
		g.drawLine(xinset, 0, xinset, ypixels-yinset); //yaxis
		g.drawLine(xinset, ypixels-yinset, xpixels, ypixels-yinset); //xaxis
		
		java.awt.Font oldfont = g.getFont();
		int stringwidth = g.getFontMetrics().stringWidth(title);
		while(stringwidth > ypixels)
		{
			Font curfont = g.getFont();
			g.setFont(curfont.deriveFont((float) (curfont.getSize()-1.0)));
			stringwidth = g.getFontMetrics().stringWidth(title);
		}
		g.drawString(title, (xpixels-stringwidth)/2, ypixels-fm.getMaxDescent());
		g.setFont(oldfont);
		
		return new Rectangle(xinset, 0, xpixels-xinset, ypixels-yinset);
	}




	/**  Plot a real function of 2 real variables  f(x,y)
	 * 
	 *    The domain of this function is assumed to be rectangular.
	 * 
	 *    This function will start near xlower/ylower, and go up in lots
	 *    of xgranularity/ygranularity, until it reaches xupper/yupper.
	 *  
	 * 
	 * @param g2 The graphics object to plot to
	 * @param func The function to plot
	 * @param xbins The number of discrete sampling points in the x direction
	 * @param ybins The number of discrete sampling points in the y direction
	 * @param xlower  The lower bound on x (where we start plotting from)
	 * @param xupper The upper bound on x (we finish plotting at or before this value)
	 * @param ylower The lower bound on y (where we start plotting from)
	 * @param yupper The upper bound on y (we finish plotting at or before this value)
	 * @param xpixelstart The start x coordinate of grid to plot
	 * @param xwidth The total width of the area to plot in
	 * @param ypixelstart The start y coordinate of grid to plot
	 * @param ywidth The total height of the area to plot in
	 * @param minval The minimum value of the function over the region
	 * @param maxval The maximum value of the function over the region
	 */
	public static void plot2D(Graphics2D g2, 
		RealFunction func,
		int xsteps, int ysteps,
		double xlower, double xupper,
		double ylower, double yupper,
		int xpixelstart, int xwidth,
		int ypixelstart, int ywidth,
		double minval, double maxval
		)
	{
		if(func.getDimensionOfDomain() != 2)
			throw new RuntimeException("FunctionPlotter does not support functions of more than 2 variables");
	
		//work out how big our grid cells are
		int pixelwidth = xwidth/xsteps;
		int pixelheight = ywidth/ysteps;

		int xpos = xpixelstart;

		double xgranularity = (xupper-xlower)/xsteps;
		double[] args = new double[2];
		
	
		Color c1 = Color.blue; //new Color(0.0f,0.0f,0.0f,1.0f);
		Color c2 = Color.red; //new Color(1.0f,1.0f,1.0f,1.0f);
		ColourGradient cgrad = new ColourGradient(c1, minval, c2, maxval);
		
		double x = xlower + (xupper-xlower)/(xsteps*2);
		for(; x < xupper; x+= xgranularity)
		{
			double ygranularity = (yupper-ylower)/ysteps;
			int ypos = ypixelstart+ywidth-pixelheight; 
			args[0] = x;
			double y = ylower + (yupper-ylower)/(ysteps*2);
			for(; y < yupper; y += ygranularity)
			{
				args[1] = y;
				double fval = func.invoke(args);
				if(Double.isNaN(fval))
					g2.setColor(Color.white);
				else if(fval > maxval)
					g2.setColor(cgrad.getColour(maxval));
				else
					g2.setColor(cgrad.getColour(fval));
				//g2.setColor(new Color(
				//        (float)((fval-minval)/(maxval-minval)), 
				//        0.5f, 
				//        0.5f));
				g2.fill(new Rectangle2D.Double(xpos, ypos,
				        pixelwidth, pixelheight));
				ypos -= pixelheight;
			}
			xpos += pixelwidth;
		}

	}






	/**  Plot a real function of 1 real variable  f(x)
	 * 
	 *    The domain of this function is assumed to be a single
	 *    contiguous interval.
	 * 
	 *    This function will start near xlower, and go up in lots
	 *    of xgranularity, until it reaches xupper.
	 *  
	 * 
	 * @param g2 The graphics object to plot to
	 * @param func The function to plot
	 * @param xbins The number of discrete sampling points in the x direction
	 * @param xlower  The lower bound on x (where we start plotting from)
	 * @param xupper The upper bound on x (we finish plotting at or before this value)
	 * @param xpixelstart The start x coordinate of grid to plot
	 * @param xwidth The total width of the area to plot in
	 * @param ypixelstart The start y coordinate of grid to plot
	 * @param ywidth The total height of the area to plot in
	 * @param minval The minimum value of the function over the region
	 * @param maxval The maximum value of the function over the region
	 */
	public static void plot1D(Graphics2D g2, 
		RealFunction func,
		int xsteps, int ysteps,
		double xlower, double xupper,
		double ylower, double yupper,
		int xpixelstart, int xwidth,
		int ypixelstart, int ywidth,
		double minval, double maxval
		)
	{
		if(func.getDimensionOfDomain() != 1)
			throw new RuntimeException("plot1D called on function that is not a 1D function.....");
	
		//work out how big our grid cells are
		int pixelwidth = xwidth/xsteps;
		int pixelheight = ywidth/ysteps;

		int xpos = xpixelstart;

		double xgranularity = (xupper-xlower)/xsteps;
		double[] args = new double[2];
		
	
		Color c1 = Color.blue; //new Color(0.0f,0.0f,0.0f,1.0f);
		Color c2 = Color.red; //new Color(1.0f,1.0f,1.0f,1.0f);
		ColourGradient cgrad = new ColourGradient(c1, minval, c2, maxval);
		
		double x = xlower + (xupper-xlower)/(xsteps*2);
		for(; x < xupper; x+= xgranularity)
		{
			double ygranularity = (yupper-ylower)/ysteps;
			int ypos = ypixelstart+ywidth-pixelheight; 
			args[0] = x;
			double y = ylower + (yupper-ylower)/(ysteps*2);
			for(; y < yupper; y += ygranularity)
			{
				args[1] = y;
				double fval = func.invoke(args);
				if(Double.isNaN(fval))
					g2.setColor(Color.white);
				else if(fval > maxval)
					g2.setColor(cgrad.getColour(maxval));
				else
					g2.setColor(cgrad.getColour(fval));
				//g2.setColor(new Color(
				//        (float)((fval-minval)/(maxval-minval)), 
				//        0.5f, 
				//        0.5f));
				g2.fill(new Rectangle2D.Double(xpos, ypos,
					pixelwidth, pixelheight));
				ypos -= pixelheight;
			}
			xpos += pixelwidth;
		}

	}
















	public static void main(String[] args)
	throws Exception
	{
	
		javax.swing.JFrame f = new javax.swing.JFrame();
		f.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);

		f.setSize(600, 600);
		f.setVisible(true);
		f.show();

		System.out.println("Calling 'plot' at 1 second intervals");

		while(true)
		{
			FunctionPlotter.plot2D((Graphics2D) f.getGraphics(), 
			new TestFuncImp(),
			20, 20,
			0.0, 2.0,
			0.0, 2.0,
			10, 500,
			10, 500,
			0.0, 4.0);

			Thread.sleep(1000);
		}

	}
}



class TestFuncImp extends RealFunction
{
	public double[][] getDomain()
	{
		return new double[][] {{Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY},
				{Double.NEGATIVE_INFINITY}, {Double.POSITIVE_INFINITY}};
	}
	
	private double f(double a, double b)
	{
		return a*b;
	}
		
	public double invoke(double[] args)
	{
		return f(args[0], args[1]);
	}

	public double invoke(int[] args)
	{
		return f((double) args[0], (double) args[1]);
	}

	public int getDimensionOfDomain()
	{
		return 2;
	}
}
