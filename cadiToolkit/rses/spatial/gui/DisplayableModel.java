package rses.spatial.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.swing.JScrollPane;

import rses.Debug;
import rses.math.MathUtil;
import rses.spatial.GISLayer;
import rses.util.Util;
import rses.visualisation.ColourGradient;





public abstract class DisplayableModel implements Model
{
	public abstract BufferedImage getDisplay(int[][] membership, int[][] placemarks, int zoomfact);
	
	
	public static final int KMEANS = 324;
	public static final int LOGKMEANS = 325;
	public static final int EQUALRANGES = 326;
	public static final int EQUALNUMBERS = 327;
	public static final int CUSTOM = 328;
	private int method = KMEANS;
	private boolean discrete = false; //continuous by default
	private int k = 4;
	private double[] custombounds;
	
	
	public void setDisplayToCustomDiscrete(double[] bounds)
	{
		this.discrete = true;
		this.method = CUSTOM;
		this.k = bounds.length+1;
		this.custombounds = rses.util.Util.copy(bounds);
	}
	
	public void setDisplayToDiscrete(int method, int k)
	{
		if(method != KMEANS && method != LOGKMEANS && method != EQUALRANGES && method != EQUALNUMBERS)
			throw new IllegalArgumentException("Unknown discretization method specified");
		this.discrete = true;
		this.method = method;
		this.k = k;
	}
	
	
	public void setDisplayToContinuous()
	{
		this.discrete = false;
	}
	

	public static HashMap getClusterMembershipMapping(HashMap<String, Double> regionIdToRegionValueMapping, 
			int clustermethod, int numcentroids, String[] clusterbounds)
	{
		ArrayList values = new ArrayList();
		Iterator valit = regionIdToRegionValueMapping.values().iterator();
		while(valit.hasNext()) {
			Double val = (Double) valit.next();
			if(!val.isNaN())
			{
				if(clustermethod == LOGKMEANS)
					values.add(new Double(Math.log(1+val.doubleValue())));
				else
					values.add(val);
			}
		}

		//first, lets spew if there are more centroids than regions
		if(numcentroids > values.size()) {
			if(Debug.equalOrMoreParanoid(Debug.DEBUG_SUSPICIOUS))
				throw new RuntimeException("More centroids than data points. This will give silly results");
			numcentroids = values.size();
		}
		
		int[] membership = null; 
		
		//now do the clustering
		if(clustermethod == KMEANS || clustermethod == LOGKMEANS)
		{
			double[][] valvects = new double[values.size()][1];
			for(int i = 0; i < valvects.length; i++)
				valvects[i][0] = ((Double) values.get(i)).doubleValue();
			membership = MathUtil.kmeans(valvects, numcentroids);
			
			//now rearrange membership so that values increase with increasing membership
			double[] means = new double[numcentroids];
			int[] counts = new int[numcentroids];
			for(int i = 0; i < membership.length; i++) {
				means[membership[i]] += valvects[i][0];
				counts[membership[i]]++;
			}
			for(int i =0; i < means.length; i++)
				if(counts[i] > 0) means[i] /= counts[i];
			
			Arrays.sort(means);
			for(int i = 0; i < membership.length; i++)
			{
				double mindist = Double.POSITIVE_INFINITY;
				int mbr = -1;
				for(int c = 0; c < means.length; c++)
				{
					double dist = Math.abs(valvects[i][0]-means[c]);
					if(dist < mindist) {
						mindist = dist;
						mbr = c;
					}
				}
				membership[i] = mbr;
			}
			
		}
		else if(clustermethod == EQUALNUMBERS)
		{
			//sort
			Object[] doubleArray = values.toArray();
			Arrays.sort(doubleArray);
			
			//pick out `boundary' cases
			int numpercategory = doubleArray.length/numcentroids;
			double[] boundary = new double[numcentroids];
			for(int i = 1; i <= numcentroids; i++)
				boundary[i-1] = ((Double) doubleArray[i*numpercategory-1]).doubleValue();
			boundary[numcentroids-1] = ((Double) doubleArray[doubleArray.length-1]).doubleValue();
			
			//make sure that each boundary is distinct
			for(int i = 1; i < boundary.length; i++)
			{
				if(boundary[i] == boundary[i-1]) {
					boundary[i-1] = Double.NEGATIVE_INFINITY; //wipe out duplicate boundary
					Debug.println("Eliminating duplicate membership cateory in EQUALNUMBERS cluster", Debug.IMPORTANT);
				}
					
			}
			
			//now work out membership
			
			membership = new int[values.size()];
			for(int i = 0; i < membership.length; i++)
			{
				double val = ((Double) values.get(i)).doubleValue();
				int c = 0;
				while(val > boundary[c])
					c++;
				if(c >= numcentroids)
					throw new RuntimeException("value is greater than expected.... exceeded max boundary case");
				membership[i] = c;

			}			
		}
		else if(clustermethod == EQUALRANGES)
		{
			//throw new UnsupportedOperationException("Not implemented yet");
			
			//sort
			Object[] doubleArray = values.toArray();
			Arrays.sort(doubleArray);
			
			//pick out `boundary' cases
			double min = ((Double) doubleArray[0]).doubleValue();
			double max = ((Double) doubleArray[doubleArray.length-1]).doubleValue();
			double[] boundary = new double[numcentroids];
			boundary[0] = min+(max-min)/numcentroids;
			for(int i = 1; i < numcentroids-1; i++)
				boundary[i] = boundary[i-1]+(max-min)/numcentroids;
			boundary[numcentroids-1] = max;
			
			//now work out membership
			
			membership = new int[values.size()];
			for(int i = 0; i < membership.length; i++)
			{
				double val = ((Double) values.get(i)).doubleValue();
				int c = 0;
				while(val > boundary[c])
				{
					c++;
					if(c >= numcentroids)
						throw new RuntimeException("value is greater than expected.... exceeded max boundary case");
				}
				membership[i] = c;

			}			
		}
		else if(clustermethod == CUSTOM)
			throw new RuntimeException("CUSTOM clustering method not supported in getClusterMembershipFunction()... call getCustomClusterMembership function instead");
		else
			throw new RuntimeException("Invalid/Unknown clustering method chosen for discretizing....");
	
		
		
		//ok, we have membership now. we need to create an
		//intermediate hashmap that maps values to membership
		
		//also work out cluster ranges while we are at it
		double[][] bounds0 = new double[numcentroids][];
		for(int i = 0; i < numcentroids; i++)
			bounds0[i] = new double[] {Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
		
		HashMap valuetomembershipmap = new HashMap();
		for(int i = 0; i < membership.length; i++)
		{
			Double val = (Double) values.get(i);
			Integer mbr = new Integer(membership[i]);
			
			if(val.doubleValue() < bounds0[mbr.intValue()][0])
				bounds0[mbr.intValue()][0] = val.doubleValue();
			if(val.doubleValue() > bounds0[mbr.intValue()][1])
				bounds0[mbr.intValue()][1] = val.doubleValue();
			
			Object old = valuetomembershipmap.put(val, mbr);
			//make sure that multiple equal values all have the same membership
			if(old != null && !((Integer) old).equals(mbr))
				throw new RuntimeException("Equal values have seperate membership");
		}
		
		//print out bounds
		if(bounds0.length != clusterbounds.length) throw new IllegalStateException();
		for(int i = 0; i < bounds0.length; i++)
		{
			clusterbounds[i] = bounds0[i][0]+" -- "+bounds0[i][1];
			Debug.println("Bounds for cluster "+i+" are "+bounds0[i][0]+" - "+bounds0[i][1], Debug.IMPORTANT);
		}
		
		//ok, now create the resulting map of regionids to values
		HashMap result = new HashMap();
		Object[] keys = regionIdToRegionValueMapping.keySet().toArray();
		for(int i = 0; i < keys.length; i++)
		{
			Object key = keys[i];
			Double val = (Double) regionIdToRegionValueMapping.get(key);
			if(val == null || val.isNaN()) {
				result.put(key, val);
				continue;
			}
			
			Integer mbrval = null;
			if(clustermethod == LOGKMEANS)
				mbrval = (Integer) valuetomembershipmap.get(new Double(Math.log(1+val.doubleValue()))); 
			else
				mbrval = (Integer) valuetomembershipmap.get(val);
			result.put(key, new Double(mbrval.doubleValue()));
		}
		
		return result;
		
		
	}

	
	
	
	public static HashMap<String, Double> getCustomClusterMembershipMapping(HashMap<String, Double> regionIdToRegionValueMapping, double[] bounds)
	{
		//a map of regionIds (Integers) to cluster membership values (Doubles)
		HashMap<String, Double> result = new HashMap<String, Double>();
		
		
		
		for(String key : regionIdToRegionValueMapping.keySet())
		{
			Double val = (Double) regionIdToRegionValueMapping.get(key);
			if(val == null || val.isNaN()) {
				result.put(key, val);
				continue;
			}
			
			int membership;
			for(membership = 0; membership < bounds.length; membership++)
			{
				if(val.doubleValue() < bounds[membership])
					break;
			}
			
			
			
			result.put(key, new Double(membership));			
		}
		return result;
	}


	
	/** A utility method to draw a regional display.
	 *   Usefull if all that is wanted is to colour
	 *   in each region a uniform colour.
	 * 
	 * @param xres
	 * @param yres
	 * @param regionIdToRegionValueMapping
	 * @return
	 */
	public BufferedImage displayByRegion(int zoomfact, 
			java.util.HashMap<String, Double> regionIdToRegionValueMapping,
			int[][] regionmembership, String[] regionnames,
			int[][] placemarks, Color lwrcolour, Color uprcolour)
	{
		//Debug.println("In displayByRegion(), placemarks is "+placemarks, Debug.INFO);
		
		if(zoomfact < 0) throw new RuntimeException("zoomfact is les than 0. Not supported");
		
		int xres = regionmembership[0].length*(zoomfact+1);
		int yres = regionmembership.length*(zoomfact+1);
		
		BufferedImage bim = new BufferedImage(xres, yres, BufferedImage.TYPE_3BYTE_BGR);
		java.awt.Graphics g = bim.getGraphics(); 
		
		//work out min.max values
		double minval = Double.POSITIVE_INFINITY;
		double maxval = Double.NEGATIVE_INFINITY;
		Iterator valit = regionIdToRegionValueMapping.values().iterator();
		while(valit.hasNext()) 
		{
			double v = ((Double) valit.next()).doubleValue();
			if(Double.isNaN(v))
				continue;
			if(v < minval)
				minval = v;
			if(v > maxval)
				maxval = v;
		}
		
		
		Debug.println("min/max values are "+minval+"/"+maxval, Debug.INFO);

		//if we are in discrete mode, we need a mapping from region ids
		//to cluster membership
		HashMap clustermembership = null;
		String[] clusterbounds = null;
		if(this.discrete) 
		{
			Debug.println("Discretizing values... ", Debug.INFO);
			if(this.method == CUSTOM) {
				clusterbounds = new String[custombounds.length+1];
				clusterbounds[0] = "< "+custombounds[0];
				for(int i = 1; i < custombounds.length; i++) clusterbounds[i] = custombounds[i-1]+" -- "+custombounds[i];
				clusterbounds[custombounds.length] = "> "+custombounds[custombounds.length-1];
				clustermembership = getCustomClusterMembershipMapping(regionIdToRegionValueMapping, this.custombounds);
				minval = 0;
				maxval = custombounds.length;
			}
			else { 
				clusterbounds = new String[k];
				clustermembership = getClusterMembershipMapping(regionIdToRegionValueMapping, this.method, this.k, clusterbounds);
				minval = 0;
				maxval = k-1;
			}
		}
		
		//work out the colour gradient. 
		ColourGradient grad = new ColourGradient(lwrcolour, minval, uprcolour, maxval);
				
		
		int latsteps = regionmembership.length;
		int lonsteps = regionmembership[0].length;
		//now draw the map
		int curregion = -1;
		double curval = Double.NaN;
		for(int i =0; i < latsteps; i++)
		{
			for(int j = 0; j < lonsteps; j++)
			{
				//check whether we should draw a boundary
				boolean drawboundary = false;
				if(j < lonsteps-1) {
					if(regionmembership[i][j] != regionmembership[i][j+1])
						drawboundary = true;
					else if(i < latsteps-1 && regionmembership[i][j] != regionmembership[i+1][j])
						drawboundary = true;
				}
				if(drawboundary) {
					Color oldcol = g.getColor();
					g.setColor(Color.black);
					g.fillRect(j*(zoomfact+1), i*(zoomfact+1), 1, 1);
					g.setColor(oldcol);
					continue;
				}
				if(placemarks != null) 
				{
					//Debug.println("Checking placemark layer....", Debug.IMPORTANT);
					if(placemarks[i][j] > 0) 
					{
						//Debug.println("At placemark!", Debug.IMPORTANT);
						Color oldcol = g.getColor();
						g.setColor(Color.black);
						g.fillRect(j*(zoomfact+1), (i*zoomfact+1), zoomfact+1, zoomfact+1);
						g.setColor(oldcol);
						continue;
					}
				}
				
				
				//no boundary.. we actually need to plot the point
				
				int regioni = regionmembership[i][j];
				if(regioni < 0) { //draw non-regions as black
					Color oldcol = g.getColor();
					g.setColor(Color.WHITE); //used to be black
					g.fillRect(j*(zoomfact+1), i*(zoomfact+1), zoomfact+1, zoomfact+1);
					g.setColor(oldcol);
					continue;					
				}
				else if(regioni != curregion) 
				{
					String region = regionnames[regioni];
					curregion = regioni;
					String key = ""+region;
					Double Dval = null;
					if(this.discrete)
						Dval = (Double) clustermembership.get(key);
					else
						Dval = (Double) regionIdToRegionValueMapping.get(key);
					
					if(Dval == null) {
						curval = Double.NaN;
						//Debug.println("No mapping found for key "+key, Debug.INFO);
					}
					else
						curval = Dval.doubleValue();
					
					if(Double.isNaN(curval))
						g.setColor(Color.WHITE);
					else 
						g.setColor(grad.getColour(curval));
					
					//Debug.println("set colour to "+g.getColor()+" for value "+curval, Debug.INFO);
				}
				
				//now plot the point
				g.fillRect(j*(zoomfact+1), i*(zoomfact+1), zoomfact+1, zoomfact+1);
			}
			//Debug.println("Finished lat row "+i, Debug.INFO);
		}
		
		//now paint the scale in a separate window
		class scalePanel extends javax.swing.JPanel 
		{
			boolean discrete; int k; ColourGradient grad; String[] clusterbounds;
			scalePanel(boolean discrete, int k, ColourGradient grad, String[] cb) 
			{
				this.setBackground(Color.BLACK);
				//this.setMinimumSize(new Dimension(400, 200));
				this.setPreferredSize(new Dimension(400, 200));
				this.setSize(new Dimension(400, 200));
				this.discrete = discrete;
				this.k = k;
				this.grad = grad;
				this.clusterbounds = cb;
			}
			
			public void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				if(this.discrete) {
					g.setColor(Color.WHITE);
					grad.paintDiscreteColourScale(g, this.getWidth(), this.getHeight(), false, this.k, clusterbounds);
				}
				else {
					Font f = g.getFont();
					g.setFont(f.deriveFont(20f));
					g.setColor(Color.WHITE);
					grad.paintColourScale(g, this.getWidth(), this.getHeight(), false, 
							4*(this.getWidth()/200));
					g.setFont(f);
				}
			}
		}
		javax.swing.JFrame scaleframe = new javax.swing.JFrame();
		scaleframe.setSize(440,220);
		javax.swing.JPanel displaypanel = new scalePanel(discrete, k, grad, clusterbounds);
		JScrollPane jsp = new JScrollPane(displaypanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scaleframe.getContentPane().add(jsp);
		scaleframe.setVisible(true);
		
		/*if(this.discrete) {
			java.awt.Graphics scg = scalepanel.getGraphics();
			java.awt.Graphics colourscaleg = g.create(xres-125, (int) (yres*0.6), 125, (int) (yres*0.4));
			colourscaleg.setColor(Color.WHITE);
			grad.paintDiscreteColourScale(colourscaleg, 125, (int) (yres*0.4), false, this.k);
		}
		else {
			java.awt.Graphics colourscaleg = g.create((int) (xres*0.6), yres-60, (int) (xres*0.4), 60);
			Font f = colourscaleg.getFont();
			colourscaleg.setFont(f.deriveFont(20f));
			colourscaleg.setColor(Color.WHITE);
			grad.paintColourScale(colourscaleg, (int) (xres*0.4), 50, false, 4);
			colourscaleg.setFont(f);
		}*/
		
		//and we're done
		return bim;

	}
	
	
	
	
	
	
	public static int[][] getMembershipMatrix(GISLayer regiongis, 
			int xres, int yres)
	{
		int latsteps = yres;
		int lonsteps = xres;
		int[][] result = new int[yres][xres];
		double maxlat = regiongis.getMaxLat();
		double minlat = regiongis.getMinLat();
		double maxlong = regiongis.getMaxLong();
		double minlong = regiongis.getMinLong();
		double latstep = (maxlat-minlat)/latsteps;
		double lonstep = (maxlong-minlong)/lonsteps;
		
		Debug.println("caching region membership data.. this may take some time", Debug.IMPORTANT);
		int lati = 0;
		for(double lat = minlat+latstep/2; lat < maxlat; lat += latstep, lati++)
		{
			int loni = 0;
			for(double lon = minlong+lonstep/2; lon < maxlong; lon += lonstep, loni++)
			{
				float val = regiongis.getValue(lat, lon);
				if(Float.isNaN(val))
					result[latsteps-lati-1][loni] = -1;
				else //flip lat around so that it corresponds with graphics coord system
					result[latsteps-lati-1][loni] = (int) val;
			}
		}
		
		return result;

	}
	
		
	
	
	public static int getRegion(int[][] regionmembership, int xpix, int ypix)
	{
		if(ypix >= regionmembership.length || 
		   xpix >= regionmembership[0].length)
			return -1;
		else
			return regionmembership[ypix][xpix];
	}
	

	
	
	
}
