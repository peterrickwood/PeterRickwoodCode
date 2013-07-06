package rses.util.gui;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Window;

import javax.swing.JPanel;

import rses.Debug;
import rses.visualisation.FunctionPlotter;







public final class GuiUtil
{
	private GuiUtil() {} //cant instantiate this class
	
	
	/** Calculate the x,y position that this window needs
	 *  to be positioned at so that it is centered on the
	 *  screen
	 * 
	 * @param window
	 * @return
	 */
	public static Point getCentredWindowPoint(Window window)
	{
		Point p = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
		p.x -= window.getWidth()/2;
		p.y -= window.getHeight()/2;
		if(p.x < 0) p.x = 0;
		if(p.y < 0) p.y = 0;
		return p;
	}
	
	
	
	public static void showHistogram(int[] hist, double min, double max, int width, int height, String graphname)
	{
		javax.swing.JFrame f = new javax.swing.JFrame();
		f.setSize(width, height);
		class PlotPanel extends JPanel 
		{
			int[] hist;
			double min, max;
			int width, height;
			String graphname;
			public void paintComponent(java.awt.Graphics g)
			{
				super.paintComponent(g);
				FunctionPlotter.drawHistogramGraph(g, hist, false, min, (max-min)/hist.length, width, height, graphname, 8);				
			}
		};
		PlotPanel panel = new PlotPanel();
		{ panel.hist = hist; panel.min = min; panel.max = max;
		  panel.width = width; panel.height = height; panel.graphname = graphname;
		}
		
		panel.setSize(width, height);
		f.getContentPane().add(panel);
		f.setVisible(true);

	}
	
	
	
	/** Get a font that is a particular number of pixels high.
	 * 
	 * @param basegraphics
	 * @param pixelheight
	 * @return
	 */
	public static void setFontToSpecificHeight(java.awt.Graphics basegraphics, int pixelheight)
	{
		FontMetrics tryfont = basegraphics.getFontMetrics();
		int maxtries = 100;
		for(int i =0; i < maxtries; i++)
		{
			int height = tryfont.getHeight();
			if(Math.abs(height-pixelheight) < pixelheight/5+1)
				return;
			else {
				double error = 1+(height-pixelheight)/(double) pixelheight;
				Font curfont = basegraphics.getFont();
				int size = curfont.getSize();
				float newsize = (float) Math.round(size/error);
				basegraphics.setFont(curfont.deriveFont(newsize));
				tryfont = basegraphics.getFontMetrics();
			}
		}
		Debug.println("Warning: Cannot get font of specified height", Debug.IMPORTANT);
	}
	
}