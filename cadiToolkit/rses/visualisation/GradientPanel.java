package rses.visualisation;

import java.awt.Color;



public class GradientPanel extends javax.swing.JPanel 
{
	private double besterr;
	private double worsterr;
	
	public GradientPanel(double best, double worst)
	{
		this.setBounds(best, worst);
	}
	
	public void setBounds(double besterr, double worsterr)
	{
		this.besterr = besterr;
		this.worsterr = worsterr;		
	}
	
	
	public void paintComponent(java.awt.Graphics g)
	{
		Color oldcol = g.getColor();
		super.paintComponent(g);
		double gradientStart = Math.log(besterr+1);
		double gradientEnd = Math.log(worsterr+1);
		int width = this.getWidth();
		int height = this.getHeight();
		ColourGradient grad = new ColourGradient(Color.red, 0, Color.blue, 3*width/4);
		for(int i = 0; i < 3*width/4; i++) {
			g.setColor(grad.getColour(i));
			g.drawLine(width/8+i, height/4, width/8+i, height/2);
		}

		//now draw the labels
		int printwidth = 8;

		java.awt.FontMetrics fm = g.getFontMetrics();
		int lineheight =  fm.getMaxAscent()+fm.getMaxDescent()+fm.getLeading(); 

		String start, mid, end, error = "";
		for(int i = 0; i < printwidth; i++) error += "X"; 
		start = rses.util.Util.safeGetTruncatedReal(Math.exp(gradientStart)-1, printwidth, error);
		end = rses.util.Util.safeGetTruncatedReal(Math.exp(gradientEnd)-1, printwidth, error);
		mid = rses.util.Util.safeGetTruncatedReal(Math.exp((gradientStart+gradientEnd)/2)-1, printwidth, error);
		g.setColor(oldcol);
		g.drawString(start, width/8-fm.stringWidth(start)/2, height/2+lineheight+5); 
		g.drawString(end, width/8+3*width/4-fm.stringWidth(end)/2, height/2+lineheight+5);
		g.drawString(mid, width/2-fm.stringWidth(mid)/2, height/2+lineheight+5);
			

		g.drawLine(width/8, height/2, width/8, height/2+5);
		g.drawLine(width/8+3*width/4-1, height/2, width/8+3*width/4-1, height/2+5);
		g.drawLine(width/2, height/2, width/2, height/2+5);
	}
}
