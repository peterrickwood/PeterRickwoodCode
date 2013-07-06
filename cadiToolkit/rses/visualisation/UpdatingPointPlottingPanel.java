package rses.visualisation;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import rses.Debug;
import rses.util.FileUtil;







/** A Panel that plots a function over a particular domain, This can be for either
 * a 1D domain or a 2D domain that updates in real time
 */
public class UpdatingPointPlottingPanel extends JPanel implements ActionListener, KeyListener, MouseListener
{
	//dimension of domain, either 1 or 2
	private int nd;
	
	//name for x and y and z axes
	private String xname, yname, zname;	
	
	//do we do dynamic scaling based on min/max values? 
	private boolean scalexlocally = false;
	private boolean scaleylocally = false;
	private boolean scalezlocally = false;
	
	//show y/z colour gradient scale?
	private boolean showColourGradient = true;
	
	//variables either have bounds, or scale dynamically with the observed range 
	private double boundx_lwr = Double.POSITIVE_INFINITY;
	private double boundx_upr = Double.NEGATIVE_INFINITY;
	private double boundy_lwr = Double.POSITIVE_INFINITY;
	private double boundy_upr = Double.NEGATIVE_INFINITY;
	private double boundz_lwr = Double.POSITIVE_INFINITY;
	private double boundz_upr = Double.NEGATIVE_INFINITY;
	

	private JPopupMenu popmenu = new JPopupMenu();
	
	private List values = new java.util.ArrayList(); //the points that are plotted in this window
	private Object valuesLock = new Object();
	private int currentIndex = 0; //the number of models we have currently plotted

	//we need to show how the colour gradient that we are using varies	
	private GradientPanel gradientPanel = new GradientPanel(0, Double.MAX_VALUE);
	
	private Dimension basesize = null;

	//do we plot the axes or just the body of the graph
	private boolean plotAxes = true;
	
	public UpdatingPointPlottingPanel(String[] dimNames, double[][] dimbounds, Dimension basesize)
	{
		this.basesize = basesize;
		this.nd = dimNames.length-1;
		this.xname = dimNames[0];
		this.yname = dimNames[1];
		if(this.nd == 2)
			this.zname = dimNames[2];
		
		if(dimbounds[0] != null) {
			this.boundx_lwr = dimbounds[0][0];
			this.boundx_upr = dimbounds[0][1];
			scalexlocally = false;
		}
		else scalexlocally = true;
		
		if(dimbounds[1] != null) {
			this.boundy_lwr = dimbounds[1][0];
			this.boundy_upr = dimbounds[1][1];
			scaleylocally = false;
		}
		else scaleylocally = true;
		
		if(nd == 2 && dimbounds[2] != null) {
			this.boundz_lwr = dimbounds[2][0];
			this.boundz_upr = dimbounds[2][1];
			scalezlocally = false;
		}
		else scalezlocally = true;

		
		//only 2 dimensional plots with misfit can be viewed sensibly in 3D
		if(this.nd == 2)
		{
			popmenu.add("view in 3D").addActionListener(this);
			popmenu.add("view in 3D (logscaled Z)").addActionListener(this);
		}

		
		popmenu.add("zoom").addActionListener(this);
		popmenu.add("unzoom").addActionListener(this);
		javax.swing.JMenu savemenu = new javax.swing.JMenu("Save...");
		savemenu.add("save image").addActionListener(this);
		savemenu.add("save data (comma delimited)").addActionListener(this);
		savemenu.add("save data (space delimited)").addActionListener(this);
		popmenu.add(savemenu);
		this.setFocusable(true);
		this.addMouseListener(this);
		this.addKeyListener(this);		
		
		setupGradientPanel();
	}
	

	public void plotAxes()
	{
		this.plotAxes = true;
	}
	
	public void dontPlotAxes()
	{
		this.plotAxes = false;
	}
	
	
	public JPopupMenu getPopupMenu()
	{
		return this.popmenu;
	}



	private void setupGradientPanel()
	{
		gradientPanel.setMinimumSize(new java.awt.Dimension(basesize.width, basesize.height/6));
		gradientPanel.setPreferredSize(new java.awt.Dimension(basesize.width, basesize.height/6));
		gradientPanel.setBackground(java.awt.Color.white);
	}

	
	
	
	public void setXbounds(double[] bounds)
	{
		this.boundx_lwr = bounds[0];
		this.boundx_upr = bounds[1];
		this.scalexlocally = false;
		this.invalidateOffscreenBuffer();
	}
	
	public void setYbounds(double[] bounds)
	{
		this.boundy_lwr = bounds[0];
		this.boundy_upr = bounds[1];
		this.scaleylocally = false;
		this.invalidateOffscreenBuffer();
	}

	public void setZbounds(double[] bounds)
	{
		this.boundz_lwr = bounds[0];
		this.boundz_upr = bounds[1];
		this.scalezlocally = false;
		this.invalidateOffscreenBuffer();
	}


	/** Get the min/max values seen so far for either x,y,z
	 * 
	 * 
	 * @param index 0 for x, 1 for y, 2 for z
	 * @return
	 */
	private double[] getminmax(int index)
	{
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
	
		synchronized(valuesLock)
		{
			int nval = values.size();
			for(int i =0; i < nval; i++)
			{
				double[] point = (double[]) values.get(i);
				if(point[index] < min) min = point[index];
				if(point[index] > max) max = point[index];
			}
		}
		
		return new double[] {min, max};
	}

	
	public boolean isXscaledLocally()
	{
		return this.scalexlocally;
	}

	public boolean isYscaledLocally()
	{
		return this.scaleylocally;
	}

	public boolean isZscaledLocally()
	{
		return this.scalezlocally;
	}

	public void scaleXlocally()
	{
		double[] res = this.getminmax(0);
		this.boundx_lwr = res[0];
		this.boundx_upr = res[1];
		this.invalidateOffscreenBuffer();
		this.scalexlocally = true;
	}

	public void scaleYlocally()
	{
		double[] res = this.getminmax(1);
		this.boundy_lwr = res[0];
		this.boundy_upr = res[1];
		this.invalidateOffscreenBuffer();
		this.scaleylocally = true;
	}

	public void scaleZlocally()
	{
		double[] res = this.getminmax(2);
		this.boundz_lwr = res[0];
		this.boundz_upr = res[1];
		this.invalidateOffscreenBuffer();
		this.scalezlocally = true;
	}

	
	

	public double[][] getParamBounds()
	{
		return new double[][] 
		{
				{this.boundx_lwr, this.boundx_upr},
				{this.boundy_lwr, this.boundy_upr},
				{this.boundz_lwr, this.boundz_upr},
		};
		
	}
	
	

	private double[][] getValuesAsDoubleArray()
	{
		synchronized(valuesLock)
		{
			int nval = values.size();
			double[][] res = new double[nval][4];
			for(int i = 0; i < nval; i++)
			{
				double[] tmp = (double[]) values.get(i);
				for(int j = 0; j < tmp.length; j++)
					res[i][j] = tmp[j];
				res[i][3] = Math.log(res[i][2]+1);
			}
			return res;
		}
	}



	private boolean needredraw = false;
	private void invalidateOffscreenBuffer()
	{
		this.needredraw = true;
	}
	
	


	public void keyPressed(KeyEvent keyevent) {}
	public void keyReleased(KeyEvent keyevent) {}
	public void keyTyped(KeyEvent keyevent)
	{
		if(keyevent.getKeyChar() == '+') 
		{	
			this.zoomfact++;
			this.invalidateOffscreenBuffer();
			this.revalidate();
		}
		else if(keyevent.getKeyChar() == '-') 
		{
			if(this.zoomfact > 1)
			{
				this.zoomfact--;
				this.invalidateOffscreenBuffer();
				this.revalidate();
			}
		}
		
		//not interested in other events.. see if our parent is interested
		this.getParent().dispatchEvent(keyevent);
	}








	public void actionPerformed(ActionEvent event)
	{
		if(event.getActionCommand().equals("save image"))
			this.saveAsImage();
		else if(event.getActionCommand().equals("save data (comma delimited)")) {
			java.io.File f = rses.util.FileUtil.guiSaveFile(this, "Save to file...");
			try { this.saveAsData(f, " , "); }
			catch(java.io.IOException ioe) {
				javax.swing.JOptionPane.showMessageDialog(this, "Error saving file... IO Error");
			}
		}
		else if(event.getActionCommand().equals("save data (space delimited)")) {
			java.io.File f = rses.util.FileUtil.guiSaveFile(this, "Save to file...");
			try { this.saveAsData(f, "   "); }
			catch(java.io.IOException ioe) {
				javax.swing.JOptionPane.showMessageDialog(this, "Error saving file... IO Error");
			}
		}
		else if(event.getActionCommand().equals("zoom")) {
			this.invalidateOffscreenBuffer(); 
			this.zoomfact++;
			this.revalidate();
		}
		else if(event.getActionCommand().equals("unzoom")) { 
			if(this.zoomfact > 1) {
				this.invalidateOffscreenBuffer();
				this.zoomfact--;
			}			
			this.revalidate();
		}
		else if(event.getActionCommand().startsWith("view in 3D")) 
		{
			double[][] vals = getValuesAsDoubleArray();
			double[][] bounds = new double[nd+2][2];
			bounds[0][0] = this.boundx_lwr; bounds[0][1] = this.boundx_upr;
			bounds[1][0] = this.boundy_lwr; bounds[1][1] = this.boundy_upr;
			bounds[2][0] = this.boundz_lwr; bounds[2][1] = this.boundz_upr;
 
			//we always log scale the z axis for the purposes of determining colour
			bounds[3][0] = Math.log(bounds[2][0]+1); 
			bounds[3][1] = Math.log(bounds[2][1]+1);
			
			String zaxisname = this.zname;
			
			//but we only sometimes scale the misfit axis itself (depending on whether the user asked for it)
			if(event.getActionCommand().equals("view in 3D (logscaled Z)"))
			{
				//we need to log-scale the Z axis value
				for(int i = 0; i < vals.length; i++)
					vals[i][2] = Math.log(vals[i][2] + 1);
					
				 bounds[2][0] = bounds[3][0]; 
				 bounds[2][1] = bounds[3][1];
				 zaxisname = "ln("+zname+"+1)"; 
			}
			
			throw new UnsupportedOperationException();
			/* ThreeDimensionalPlotWindow pw = new ThreeDimensionalPlotWindow(vals, bounds,
				xname, yname, zaxisname);
			ThreeDimensionalPlotWindow.displayPlotWindow(pw, 600,600);*/
		}
			
		this.repaint();
	}

	public void mouseReleased(MouseEvent e) 
	{
		maybeShowPopup(e);
	} 
	public void mousePressed(MouseEvent e)
	{
		maybeShowPopup(e);
	}


	private void maybeShowPopup(MouseEvent e) 
	{
		if (e.isPopupTrigger())
			popmenu.show(e.getComponent(),e.getX(), e.getY());
	}

	public void mouseEntered(MouseEvent e)
	{
		this.requestFocusInWindow();
	} 


	//not interested in these events
	public void mouseClicked(MouseEvent e) 
	{}
	public void mouseExited(MouseEvent e)
	{}


	private java.awt.Dimension oldsize = null;
	private Image offscreen = null;
	private java.awt.Graphics bufferGraphics = null;
	
	
	
	public void paintComponent(java.awt.Graphics g)
	{
		//Check if we have changed size, or new best/worst model found, or
		//we are now scaling differently, since in these cases 
		//we need to throw away buffered image and repaint/recalculate everything
		if(offscreen == null || currentIndex == 0 || needredraw) 
		{ 
			//Debug.println("oldsize = "+oldsize+" this.getSize() = "+this.getSize()+" maxmfbase = "+maxmfbase+" mfmax = "+mfmax+" minmfbase = "+minmfbase+" mfmin = "+mfmin+" currentIndex = "+currentIndex, Debug.EXTRA_INFO);
			Debug.println("Doing complete redraw of panel "+this.xname+" vs "+this.yname, Debug.EXTRA_INFO);
			oldsize = this.getSize(); 
			offscreen = createImage(oldsize.width, oldsize.height);
			bufferGraphics = offscreen.getGraphics();
						
			java.awt.Color oldcol = bufferGraphics.getColor();
			bufferGraphics.setColor(java.awt.Color.white); 
			bufferGraphics.fillRect(0,0,oldsize.width, oldsize.height);
			bufferGraphics.setColor(oldcol);
			currentIndex = 0;

			//draw the colour gradient
			if(this.showColourGradient)
			{
				if(this.nd == 1)
					this.gradientPanel.setBounds(this.boundy_lwr, this.boundy_upr);
				else if(this.nd == 2)
					this.gradientPanel.setBounds(this.boundz_lwr, this.boundz_upr);
				else throw new IllegalStateException("Impossible number of dimensions in plot!");
				gradientPanel.setSize(oldsize.width, oldsize.height/6);
				gradientPanel.paintComponent(bufferGraphics.create(0,oldsize.height-oldsize.height/6,
						oldsize.width,oldsize.height));
			
				//fool the rest of the drawing code about the size of the panel, so that
				//it leaves space at the bottom for the colour gradient
				oldsize.height -= oldsize.height/6;
			}
			needredraw = false;
		}
		
		//super.paint(bufferGraphics);
		
		List modelsToPlot = filterPoints(values); 
		int numvals = modelsToPlot.size();
		if(currentIndex == numvals) //no new models need to be drawn
		{
			//draw our cached image and return
			g.drawImage(offscreen, 0, 0, this);
			return ;
		}

		//if we have to draw more than 1/2 the values anyway, we just
		//do a full redraw, otherwise we do a partial redraw		
		Debug.println("currentIndex is "+currentIndex+" , numvals is "+numvals, Debug.EXTRA_INFO);
		if(currentIndex > numvals/2) {
			Debug.println("Doing partial redraw of panel "+this.xname+" vs "+this.yname, Debug.EXTRA_INFO);
			List partialList = new java.util.ArrayList(numvals-currentIndex+1);
			for(int i = currentIndex; i < numvals; i++)
				partialList.add(modelsToPlot.get(i));
			modelsToPlot = partialList;
		}
		else
			Debug.println("Could do partial redraw of panel "+this.xname+" vs "+this.yname+", but doing full redraw anyway", Debug.EXTRA_INFO);
		
		currentIndex = numvals;
							
		//now go through all the points and paint them
		java.awt.Rectangle gbounds = null;
		if(this.nd == 1)
		{
			//Debug.println("doing 2d plot in param plotter for param "+this.d1);
			//java.awt.Insets insets = this.getInsets();
			if(this.plotAxes)
				gbounds = FunctionPlotter.drawGraph2D(bufferGraphics, modelsToPlot, 
				                               this.boundx_lwr, this.boundx_upr, 
				                               this.boundy_lwr, this.boundy_upr, 
			                                   oldsize.width, oldsize.height, 
			                                   xname+" (x) vs "+yname+" (y)", 5);
			else
				FunctionPlotter.plotPoints2D(bufferGraphics, modelsToPlot,
                        this.boundx_lwr, this.boundx_upr, 
                        this.boundy_lwr, this.boundy_upr, 
                        oldsize.width, oldsize.height);
		}
		else if(this.nd == 2) 
		{
			if(this.plotAxes) 
				gbounds = FunctionPlotter.drawGraph3D(bufferGraphics, modelsToPlot, 
				          this.boundx_lwr, this.boundx_upr, this.boundy_lwr, this.boundy_upr, 
				          this.boundz_lwr, this.boundz_upr, oldsize.width, oldsize.height, 
				          xname+" (x) vs "+yname+" (y) vs "+zname+" (z)", 5);
			else
				FunctionPlotter.plotPoints3D(bufferGraphics, modelsToPlot, 
				          this.boundx_lwr, this.boundx_upr, this.boundy_lwr, this.boundy_upr, 
				          this.boundz_lwr, this.boundz_upr, oldsize.width, oldsize.height);
		}
		else
			throw new RuntimeException("I dont know how to plot "+nd+" dimensions!!!");
		
		
		if(this.plotAxes) 
		{
			int nlines = this.xlines.size();
			for(int i =0; i < nlines; i++)
			{
				Double xv = (Double) xlines.get(i);
				if(xv == null) continue;
				double xpct = (xv.doubleValue()-boundx_lwr)/(boundx_upr-boundx_lwr);
				int xpos = (int) (gbounds.x+gbounds.width*xpct);
				bufferGraphics.drawLine(xpos, gbounds.y, xpos, gbounds.y+gbounds.height);
			}
			nlines = this.ylines.size();
			for(int i =0; i < nlines; i++)
			{
				Double yv = (Double) ylines.get(i);
				if(yv == null) continue;
				double ypct = (yv.doubleValue()-boundy_lwr)/(boundy_upr-boundy_lwr);
				int ypos = (int) (gbounds.y+gbounds.height*(1-ypct));
				bufferGraphics.drawLine(ypos, gbounds.x, ypos, gbounds.x+gbounds.width);
			}
			nlines = this.crosses.size();
			for(int i =0; i < nlines; i++)
			{
				double[] xy = (double[]) crosses.get(i);
				if(xy == null) continue;
				double x = xy[0]; double y = xy[1];
				double xpct = (x-boundx_lwr)/(boundx_upr-boundx_lwr);
				int xpos = (int) (gbounds.x+gbounds.width*xpct);
				double ypct = (y-boundy_lwr)/(boundy_upr-boundy_lwr);
				int ypos = (int) (gbounds.y+gbounds.height*(1-ypct));
			
				bufferGraphics.drawLine(xpos-FunctionPlotter.POINT_SIZE, ypos-FunctionPlotter.POINT_SIZE, 
			               xpos+FunctionPlotter.POINT_SIZE, ypos+FunctionPlotter.POINT_SIZE);
				bufferGraphics.drawLine(xpos-FunctionPlotter.POINT_SIZE, ypos+FunctionPlotter.POINT_SIZE, 
				       xpos+FunctionPlotter.POINT_SIZE, ypos-FunctionPlotter.POINT_SIZE);
			}
		}
		

	
		g.drawImage(offscreen, 0, 0, this);

	}
		
		
	
	public int getPlotDimension()
	{
		return this.nd+1;
	}

	
	private List filterPoints(List points)
	{
		synchronized(valuesLock) {
		List res = new ArrayList();
		int numorig = points.size();
		for(int i =0; i < numorig; i++)
		{
			double[] point = (double[]) points.get(i);
			boolean isvalidpoint = true;
			
			if(point[0] < this.boundx_lwr || point[0] > this.boundx_upr) 
			{
				isvalidpoint = false;
				if(!this.scalexlocally)
					throw new IllegalStateException("We are doing global scaling in the X coord but a point is out of bounds!!!  lwr/upr "+this.boundx_lwr+"/"+this.boundx_upr+"  x: "+point[0]);
			}
			if(point[1] < this.boundy_lwr || point[1] > this.boundy_upr) 
			{
				isvalidpoint = false;
				if(!this.scaleylocally)
					throw new IllegalStateException("We are doing global scaling in the Y coord but a point is out of bounds!!!  lwr/upr "+this.boundy_lwr+"/"+this.boundy_upr+"  y: "+point[1]);
			}
			if(this.nd == 2)
			{
				if(point[2] < this.boundz_lwr || point[2] > this.boundz_upr) 
				{
					isvalidpoint = false;
					if(!this.scalezlocally)
						throw new IllegalStateException("We are doing global scaling in the Z coord but a point is out of bounds!!!  lwr/upr "+this.boundz_lwr+"/"+this.boundz_upr+"  z: "+point[2]);
				}
			}
			if(isvalidpoint)
				res.add(point);
		}
		return res;
		}
	}
	
	

	
	public void removeXlines()
	{
		this.xlines = new ArrayList();
		this.invalidateOffscreenBuffer();
		repaint();
	}
	

	public void removeYlines()
	{
		this.ylines = new ArrayList();
		this.invalidateOffscreenBuffer();
		repaint();
	}

	public void removeCrosses()
	{
		this.crosses = new ArrayList();
		this.invalidateOffscreenBuffer();
		repaint();
	}

	
	private ArrayList xlines = new ArrayList();
	private ArrayList ylines = new ArrayList();
	private ArrayList crosses = new ArrayList();
	
	
	/** draw a thin black line perpendicular to the x axis 
	 * 
	 *
	 */
	public void addXLine(double xval)
	{
		xlines.add(new Double(xval));
		this.invalidateOffscreenBuffer();
		repaint();
	}
	
	/** draw a thin black line perpendicular to the y axis 
	 * 
	 *
	 */
	public void addYLine(double yval)
	{
		ylines.add(new Double(yval));
		this.invalidateOffscreenBuffer();
		repaint();
	}
	
	public void addCross(double xval, double yval)
	{
		crosses.add(new double[] {xval, yval});
		this.invalidateOffscreenBuffer();
		repaint();
	}
	
	

	
	public void newPointsFound(double[][] points)
	{
		for(int i = 0; i < points.length; i++)
			this.newPointFound(points[i]);
	}
	
	
	
	
	
	public void newPointFound(double[] point)
	{
		boolean needredraw = false;
		if(scalexlocally) {
			if(point[0] < this.boundx_lwr) {
				this.boundx_lwr = point[0];
				needredraw = true;
			}
			if(point[0] > this.boundx_upr) {
				this.boundx_upr = point[0];
				needredraw = true;
			}
		}
		if(scaleylocally) {
			if(point[1] < this.boundy_lwr) {
				this.boundy_lwr = point[1];
				needredraw = true;
			}
			if(point[1] > this.boundy_upr) {
				this.boundy_upr = point[1];
				needredraw = true;
			}
		}
		if(scalezlocally && this.nd == 2) {
			if(point[2] < this.boundz_lwr) {
				this.boundz_lwr = point[2];
				needredraw = true;
			}
			if(point[2] > this.boundz_upr) {
				this.boundz_upr = point[2];
				needredraw = true;
			}
		}
		
		synchronized(valuesLock) { this.values.add(point); }
		
		if(needredraw)
			this.invalidateOffscreenBuffer();
	}
	
	
	void unzoom()
	{
		this.zoomfact = 1;
		this.invalidateOffscreenBuffer(); //need to redraw
	}
	
	
	private int zoomfact = 1;
	public java.awt.Dimension getPreferredSize()
	{
		java.awt.Dimension thissize = this.getSize();
		if(this.showColourGradient)
			return new java.awt.Dimension(basesize.width*zoomfact,basesize.height*zoomfact);
		else
			return new java.awt.Dimension(basesize.width*zoomfact,basesize.height*zoomfact);
	}
		
	public java.awt.Dimension getMinimumSize()
	{
		return this.getPreferredSize();
	}


	private void saveAsImage() 
	{
		//get the save file by asking the user
		java.io.File savefile = FileUtil.guiSaveFile(this, "Save image", 
			FileUtil.getFileFilterBySuffixes(new String[] {".png", ".PNG"}), "myimage.png");
		if(savefile == null)
			return; //user must have cancelled

		//create our image
		BufferedImage bim = new BufferedImage(this.getWidth(), this.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
			
		//create a graphics object and paint it
		java.awt.Graphics2D g2d = bim.createGraphics();
		this.paint(g2d);
				
		try { javax.imageio.ImageIO.write(bim, "png", savefile); }
		catch(java.io.IOException ioe) {
			javax.swing.JOptionPane.showMessageDialog(this, "Error saving image.");
		}
	}



	public void saveAsImage(java.io.File filename) throws java.io.IOException 
	{
		//create our image
		BufferedImage bim = new BufferedImage(this.getWidth(), this.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
			
		//create a graphics object and paint it
		java.awt.Graphics2D g2d = bim.createGraphics();
		this.paint(g2d);
			
		//now save the image
		javax.imageio.ImageIO.write(bim, "png", filename);		
	}
	
	
		
	public void saveAsData(java.io.File file, String delimiter) throws java.io.IOException
	{
		java.io.PrintStream ps = new java.io.PrintStream(new java.io.FileOutputStream(file));
		ps.println("# "+this.xname+"  "+this.yname+"  "+((this.nd == 2)?(""+zname):""));
		
		synchronized(valuesLock)
		{
			int nv = values.size();
			for(int i =0; i < nv; i++) 
			{
				double[] v = (double[]) values.get(i);
				for(int j = 0; j < v.length-1; j++)
					ps.print(v[j]+delimiter);
				ps.println(v[v.length-1]);
			}
		}
		
		ps.close();
	}

}
