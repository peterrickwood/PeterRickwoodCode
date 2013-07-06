package rses.math;


import rses.Model;
import rses.util.Util;
import rses.util.gui.GuiUtil;
import rses.visualisation.DataSourcePanel;
import rses.visualisation.FunctionPlotter;
import rses.visualisation.VisualDataSource;

import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.FileReader;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;






/** A class to represent the marginals of a multidimensional pdf
 * 
 * @author peterr
 */
public class DensityFunction extends RealFunction  
{
	public static DensityFunction readFromFile(java.io.File f) throws java.io.IOException
	{
		return readFromFile(f, false, -1);
	}
	
	
	public static DensityFunction readFromFileInDensityFormat(java.io.File f, int nummods) throws java.io.IOException
	{
		return readFromFile(f, true, nummods);
	}
	
	private static DensityFunction readFromFile(java.io.File f, boolean densityFormat, int nummods) throws java.io.IOException
	{
		BufferedReader rdr = new BufferedReader(new FileReader(f));
		String line = rdr.readLine();
		
		//skip initial comment lines, if any
		while(line != null && line.startsWith("#"))
			line = rdr.readLine();
			
		if(line == null || !line.startsWith("DENSITY FUNCTION"))
			throw new java.io.IOException("Incorect format to file... expected string 'DENSITY FUNCTION ?????   samples' in header");
			
		//now read in bounds information
		line = rdr.readLine();
		String[] words = rses.util.Util.getWords(line);
		int nd = -1; 
		if(words.length != 2)
			throw new java.io.IOException("could not parse line describing number of dimensions... expected '???   dimensions'"); 
		try { nd = Integer.parseInt(words[0]);}
		catch(NumberFormatException nfe)
		{ throw new java.io.IOException("could not parse line describing number of dimensions... expected '???   dimensions'"); }
		double[][] b = new double[nd][2];
		
		line = rdr.readLine();
		int count = 0;
		for(int i = 0; line != null && i < nd; i++, line = rdr.readLine())
		{
			if(!line.startsWith("#"))
			{
				words = Util.getWords(line);
				if(words.length != 4)
					throw new java.io.IOException("Expected line with 'N: LOWBOUND ... HIGHBOUND'");
				double low = 0;
				double high = 0;
				try {
					low = Double.parseDouble(words[1]);
					high = Double.parseDouble(words[3]);		 
				}
				catch(NumberFormatException nfe) {
					throw new java.io.IOException("Expected line with 'N: LOWBOUND ... HIGHBOUND'");
				}
				b[count][0] = low;
				b[count++][1] = high;
			}
		}
		if(line == null)
			throw new java.io.IOException("Unexpected end of file");
		
		
		//now read the actual density information
		int[][] df = new int[nd][];
		count = 0;
		for(int i =0 ; line != null && i < nd; i++, line = rdr.readLine())
		{
			if(!line.startsWith("#"))
			{
				if(line.trim().length() == 0)
					continue; //try skipping blank lines
				if(densityFormat)
				{
					double[] arr = (double[]) Util.parseArray(line, double.class);
					df[count] = new int[arr.length];
					for(int j =0; j < arr.length; j++)
						df[count][j] = (int) Math.round(arr[j]*nummods);
					count++;
				}
				else
				{
					int[] arr = (int[]) Util.parseArray(line, int.class);
					df[count++] = arr;
				}
			}
		}
		if(line != null)
			System.out.println("WARNING -- extra lines at end of file... ignoring....");
		
		//now collect all our lines and put them in an array		
		return new DensityFunction(df, b);
	}








	private int[][] count;
	//private double[][] density;
	private double[][] bounds;
	private int ybins;
	
	
	public DensityFunction(int[][] densitycount, double[][] bounds)
	{
		this.count = new int[densitycount.length][densitycount[0].length];
		//this.density = new double[densitycount.length][densitycount[0].length];
		this.bounds = rses.util.Util.copy(bounds);
		
		for(int i = 0; i < densitycount.length; i++) 
		{
			for(int j = 0; j < densitycount[0].length; j++)
				this.count[i][j] = densitycount[i][j];
		}
		this.ybins = densitycount[0].length;
	}
	
	public DensityFunction(int numDimensions, double[][] bounds, int ybins)
	{
		if(numDimensions != bounds.length)
			throw new IllegalArgumentException("numDimensiosn and bounds do not match");
		count = new int[numDimensions][ybins];
		this.bounds = rses.util.Util.copy(bounds);
		this.ybins = ybins;
	}

	
	public void reinitialize()
	{
		count = new int[count.length][ybins]; 
	}
	
	public void updateDimension(int dim, double paramval)
	{
		double norm_pval = (paramval-bounds[dim][0])/(bounds[dim][1]-bounds[dim][0]);
		int binnum = (int) Math.floor(ybins*norm_pval);
		
		//if the value is right on the edge of bounds, we have to handle this
		if(binnum == ybins) binnum--;
		
		if(binnum >= ybins)
			throw new IllegalStateException("Impossible case reached.. bad bin number -- "+binnum+" for value "+paramval+" in dimension "+dim+" with ybins = "+ybins);
		else if(binnum < 0)
			throw new IllegalStateException("Impossible case reached... negative bin number");
		
		count[dim][binnum]++;
	}
	
	
	
	public int getNumBins()
	{
		return this.ybins;
	}
	
	
	public void update(Model m)
	{
		if(m.getNumParameters() != count.length)
			throw new IllegalArgumentException("specified model has bad number of parameters for this DensityFunction");
		
		//for each dimension
		for(int d = 0; d < count.length; d++)
			this.updateDimension(d, m.getModelParameter(d));		
	}
	
	
	public void update(rses.Model[] models)
	{		
		for(int i =0; i < models.length; i++)
		{
			this.update(models[i]);
		}		
	}
	
	

	public void rescaleParam(int paramNum, double newlower, double newupper)
	{
		if(newupper <= newlower || newlower > bounds[paramNum][1] || newupper < bounds[paramNum][0])
			throw new IllegalArgumentException("Rescale arguments do not make sense given current bounds.... ignoring request to rescale");
		
		int[] newcount = new int[count[paramNum].length];
		double[] newdensity = new double[newcount.length];
		int sum = Util.getSum(count[paramNum]);
		double range = newupper-newlower;
		double low = newlower;
		double up = low + range/ybins;
		
		for(int i =0; low < bounds[paramNum][1] && i < newcount.length; low = up, up = up + range/ybins, i++)
		{
			double dest = getDensityEstimate(paramNum, low, up);
			newcount[i] = (int) Math.round(dest*sum);
		}
		
		count[paramNum] = newcount;
		bounds[paramNum][0] = newlower;
		bounds[paramNum][1] = newupper;
	}

	
	private double getDensityEstimate(int d, double lower, double upper)
	{
		double range = bounds[d][1]-bounds[d][0];
		double start = ((lower-bounds[d][0])/range)*ybins;
		int startbin =  (int) Math.ceil(start); //start bin, excluding initial part-bin)
		if(lower == bounds[d][0]) startbin = 0; 
		double end = ((upper-bounds[d][0])/range)*ybins;
		int endbin = (int) Math.floor(end); //end bin, excluding final part-bin
		if(upper == bounds[d][1]) endbin = count[d].length;
		double estimate = 0.0;
		
		double[] marginal = getMarginal(d);
		for(int i =0; i < marginal.length; i++) marginal[i] = count[d][i];
		Util.normalize(marginal);
		for(int s = startbin; s >= 0 && s < count[d].length && s < endbin; s++)
			 estimate += marginal[s];
		
		//now add on the end bits
		if(startbin <= endbin)
		{
			if(startbin > 0)
				estimate += (startbin-start)*marginal[startbin-1];
			if(endbin < count[d].length && endbin >= 0)
				estimate += (end-endbin)*marginal[endbin];
		}
		else //boundary lies completely within a bin
			estimate += (end-start)*marginal[endbin];
			
		return estimate;
	}
	
	
	
	
	public int getNumParams()
	{
		return bounds.length;
	}
	

	
	public double[][] getDomain()
	{
		return Util.copy(this.bounds);
	}
	
	public double[] getBounds(int param)
	{
		return Util.copy(this.bounds[param]);
	}


	public double invoke(int[] args)
	{
			throw new RuntimeException("not supported");
	}
	

	public double[] getMarginal(int dim)
	{
		double[] res = new double[count[0].length];
		for(int i =0; i < res.length; i++)
			res[i] = count[dim][i];
		Util.normalize(res);
		return res;
	}
	
	public double invoke(double[] args)
	{
		if(args.length != 2)
			throw new RuntimeException("illegal number of arguments");
		int x = (int) Math.floor(args[0]);
		double yscaledval = (args[1]-bounds[x][0])/(bounds[x][1]-bounds[x][0]);
		int y = (int) Math.floor(ybins*yscaledval);
		
		return getMarginal(x)[y];
	}
	
	public int getDimensionOfDomain()
	{
		return 2;
	}
	
	
	public int[] getDensityCountHistogram(int pnum)
	{
		return Util.copy(this.count[pnum]);
	}
	
	
	public double getMode(int paramNum)
	{
		int modebin = -1;
		int modeval = Integer.MIN_VALUE;
		for(int i = 0; i < count[paramNum].length; i++)
		{
			if(count[paramNum][i] > modeval)
			{
				modebin = i;
				modeval = count[paramNum][i];
			}
		}
		
		double binsize = (bounds[paramNum][1]-bounds[paramNum][0])/ybins;
		return bounds[paramNum][0]+(modebin+0.5)*binsize; 
	}
	
	
	
	
	
	/** Give the upper/lower bounds that contain 'percent' 
	 *   of each marginal.
	 * 
	 *   For example, calling getConfidenceBounds(0.5)
	 *   will return an aray of upper/lower bounds that contain
	 *   50% of the distribution.
	 * 
	 *   This is worked out by working in from top/bottom
	 *   until the specified density is contained.
	 * 
	 * @param percent
	 * @return
	 */
	public double[][] getConfidenceBounds(double percent)
	{
		if(percent > 1 || percent < 0)
			throw new IllegalArgumentException("argument must be between 0 and 1");

		double[][] cbounds = new double[bounds.length][2];
		double[][] marginals = new double[count[0].length][];
		for(int d = 0; d < cbounds.length; d++)
		{
			marginals[d] = getMarginal(d);
			
			double range = bounds[d][1]-bounds[d][0];
			if(this.ybins == 1) { //special case for only 1 bin
				cbounds[d][0] = bounds[d][0]+((1-percent)/2)*range;
				cbounds[d][1] = bounds[d][1]-((1-percent)/2)*range;
				continue;
			}
				
			int upper = ybins-1;
			int lower = 0;
			double contained = 1.0;
			
			//move in upper and lower until we cant do any more
			boolean upperdone = false;
			boolean lowerdone = false;
			while(!upperdone || !lowerdone)
			{
				if(!upperdone)
				{
					if(contained - marginals[d][upper] < percent)
						upperdone = true;
					else {
						contained -= marginals[d][upper];
						upper--;
					}
				}
				if(!lowerdone)
				{
					if(contained - marginals[d][lower] < percent)
						lowerdone = true;
					else {
						contained -= marginals[d][lower];
						lower++;
					}
				}
			}
			
			double leftover = contained-percent;
			double lowbit = (leftover/2)/marginals[d][lower];
			double highbit = (leftover/2)/marginals[d][upper];
			double binsize = range/ybins;
			
			cbounds[d][0] = bounds[d][0]+lower*binsize+lowbit*binsize;
			cbounds[d][1] = bounds[d][1]-(ybins-1-upper)*binsize-highbit*binsize;
		}
		return cbounds;
	}
	
	
	
	public String toDensityString()
	{
		return this.getStringRepresentation(true);
	}
	
	
	public String toString()
	{
		return this.getStringRepresentation(false);
	}
	
	
	private String getStringRepresentation(boolean densityInsteadOfCount)
	{
		String nl = rses.PlatformInfo.nl;
		String result = "DENSITY FUNCTION"+nl;
		result += bounds.length+" dimensions"+nl;
		for(int i =0; i < count.length; i++)
			result = result+(i+1)+":  "+bounds[i][0]+" ... "+bounds[i][1]+nl;
		
		for(int i = 0; i < count.length; i++)
		{
			double[] marginal = null;
			if(densityInsteadOfCount) marginal = this.getMarginal(i);
			for(int j = 0; j < count[0].length; j++) {
				if(densityInsteadOfCount)
					result += marginal[j] + " ";
				else
					result = result +count[i][j] + " ";
			}
			result += nl;
		}
		
		return result.substring(0, result.length()-nl.length()); //cut off last newline character
	}	



	
	






	public static void main(String[] args) throws Exception
	{
		
		
		
		/*
		System.err.println("testing confidence bounds method on uniform density...");
		testgetConfidenceBounds();
		*/
		
		/*
		System.err.println();
		System.err.println();
		System.err.println("testing writing to/reading from file");
		testreadFromFile();
		*/

		/*System.err.println();
		System.err.println();
		System.err.println("testing rescaling parameters");
		testrescaleParam();

		
		System.err.println();*/
	}


	public static void testgetConfidenceBounds()
	{
		DensityFunction dfunc = utilfortesting_getDensityFunction(100000, 100, true);
		double conf = 0.5;
		System.out.println();
		System.out.println((conf*100)+" % confidence bounds");
		double[][] cbounds = dfunc.getConfidenceBounds(conf);
		for(int i =0; i < cbounds.length; i++)
			rses.util.Util.printarray(cbounds[i], System.out);
	}	


	private static DensityFunction utilfortesting_getDensityFunction(int nsamp, int numbins, boolean normalizedBounds)
	{
		double[][] b = new double[(int) (Math.random()*10+1)][2];
		for(int i =0; i < b.length; i++)
		{
			if(normalizedBounds) {
				b[i][0] = 0.0;
				b[i][1] = 1.0;
			}
			else { //pick random bounds
				b[i][0] = Integer.MAX_VALUE/2-Math.random()*Integer.MAX_VALUE;
				b[i][1] = b[i][0] + Math.random()*Integer.MAX_VALUE;
			}
		}
		DensityFunction dfunc = new DensityFunction(b.length, b, numbins);
		rses.ModelGenerator mg = rses.Model.getUniformModelGenerator(b); 
		for(int i = 0; i < nsamp; i++) 
		{
			rses.Model m = mg.generateNewModel();
			dfunc.update(new rses.Model[] {m});
		}	
		return dfunc;
	}



	public static void testrescaleParam() throws Exception
	{
		System.out.println();
		System.out.println("TRYING RESCALING INTO SMALLER BOUNDS");
		System.out.println();

		//get function with normalized bounds
		DensityFunction dfunc = utilfortesting_getDensityFunction(100000, 10, true);
		System.out.println(dfunc.toDensityString());
		
		//now rescale it a few times into a smaller range (results should still be uniform)
		for(int i =0; i < dfunc.getNumParams(); i++)
		{
			double d1 = Math.random();
			double d2 = Math.random();
			dfunc.rescaleParam(i, Math.min(d1, d2), Math.max(d1, d2));
			System.out.println("rescaling param "+i+" between "+Math.min(d1, d2)+" and "+Math.max(d1, d2));
			System.out.println(dfunc.toDensityString());
		}
		
		System.out.println();
		System.out.println("NOW TRYING RESCALING INTO LARGER BOUNDS");
		System.out.println();
		
		//now generate a new one and rescale into wider bounds
		dfunc = utilfortesting_getDensityFunction(100000, 10, true);
		//now rescale it a few times into a larger range (results should still be uniform with a subinterval
		//of the new range, and 0 elsewhere)
		for(int i =0; i < dfunc.getNumParams(); i++)
		{
			double d1 = 0-Math.random();
			double d2 = 1+Math.random();
			dfunc.rescaleParam(i, d1, d2);
			System.out.println("rescaling param "+i+" between "+d1+" and "+d2);
			System.out.println(dfunc.toDensityString());
		}
		
	}


	public static void testreadFromFile() throws Exception
	{
		DensityFunction dfunc = utilfortesting_getDensityFunction(100000, 75, false);
		
		//write out to file
		java.io.FileOutputStream fout = new java.io.FileOutputStream(new java.io.File("tmpfl"));
		java.io.PrintStream ps = new java.io.PrintStream(fout);
		ps.println(dfunc.toString());
		ps.close();
		
		//print it out
		System.out.println(dfunc.toString());
		
		//now read it back in from file
		System.out.println(DensityFunction.readFromFile(new java.io.File("tmpfl")).toString());
	}
	
	
	
	/** Obtain a histogram that is the summation of other histograms.
	 *  This would typically not make sense, but can make sense in
	 *  some (for example) mixture modelling cases.
	 * 
	 * @param params
	 * @return
	 */
	public int[] getCombinedHistogram(int[] params)
	{
		int[] combined = new int[ybins];
		for(int i =0; i < params.length; i++)
			for(int j =0; j < ybins; j++)
				combined[j] += count[params[i]][j]; 
		return combined;
	}
	
	/** Obtain a histogram that is the summation of all other histograms.
	 *  This would typically not make sense, but can make sense in
	 *  some (for example) mixture modelling cases.
	 * 
	 * @return
	 */
	public int[] getCombinedHistogram()
	{
		int[] params = new int[bounds.length];
		for(int i =0; i < bounds.length; i++)
			params[i] = i;
		return this.getCombinedHistogram(params);
	}
	

	/** Obtain a visual data source that displays the <i>combined</i>
	 *  histogram of all specified dimensions all rolled into one. This makes
	 *  sense, for example, in a mixture modelling example, but not
	 *  in many others.  
	 *  <p>
	 *  All specified parameters <i>must</i> have the same bounds

	 * @param params 
	 * @return a VisualDataSource for the combined histogram, if
	 *         all specified parameters have equal bounds. 
	 */
	public VisualDataSource getCombinedDataSource(int[] params)
	{
		//first make sure all bounds are the same, otherwise
		//this does not make sense
		
		class CombinedVisualDataSource implements VisualDataSource 
		{
			private int[] params;
			CombinedVisualDataSource(int[] params) {
				this.params = params;
			}
			public void draw(java.awt.Graphics g, int xpixels, int ypixels)
			{
				double range = bounds[0][1]-bounds[0][0];
				int[] combined = getCombinedHistogram(params); 
				FunctionPlotter.drawHistogramGraph(g, combined, false, bounds[0][0], range/ybins, xpixels, ypixels, "combined density", 5);
			}
			
			//since we have no way of knowing this, we just say that
			//it has changed, and force a redraw
			public boolean hasChanged() {
				return true;
			}
		};	
		return new CombinedVisualDataSource(params);
	}
	
	
	public VisualDataSource getDataSourceForParameter(int param, String paramname)
	{
		class DensityFuncDataSource implements VisualDataSource 
		{
			int param; String pname; 
			DensityFuncDataSource(int param, String pname)
			{	this.param = param; this.pname = pname; }
			
			public void draw(java.awt.Graphics g, int xpixels, int ypixels)
			{
				double range = bounds[param][1]-bounds[param][0];
				FunctionPlotter.drawHistogramGraph(g, count[param], false, bounds[param][0], range/ybins, xpixels, ypixels, pname+" density", 5);
			}
			
			//since we have no way of knowing this, we just say that
			//it has changed, and force a redraw
			public boolean hasChanged()
			{
				return true;
			}
		};
		
		return new DensityFuncDataSource(param, paramname);
	}	
	

	public JFrame getDisplayFrame(int updatemillisecs)
	{
		JFrame histframe = new JFrame(); 
		JPanel plotpanel = new JPanel();
		histframe.setSize(650,250);
		histframe.setLocation(GuiUtil.getCentredWindowPoint(histframe));
		for(int i =0; i < getNumParams(); i++)
		{
			VisualDataSource vds = getDataSourceForParameter(i, "param "+i);
			DataSourcePanel dsp = new DataSourcePanel(vds, updatemillisecs);
			dsp.setPreferredSize(new Dimension(200,200));
			plotpanel.add(dsp);
		}
		
		//add the combined histogram, if that seems to make sense
		boolean makessense = true;
		int[] paramnums = new int[this.getNumParams()];
		for(int i =0; i < paramnums.length; i++) {
			paramnums[i] = i;
			if(i > 0 && ((bounds[i][1] != bounds[i-1][1]) || (bounds[i][0] != bounds[i-1][0])))
			{	makessense = false; break; }
		}
		if(makessense) {
			DataSourcePanel dsp = new DataSourcePanel(getCombinedDataSource(paramnums), updatemillisecs);
			dsp.setPreferredSize(new Dimension(200,200));
			plotpanel.add(dsp);
		}

		JScrollPane jsp = new JScrollPane(plotpanel);
		histframe.getContentPane().add(jsp);
		
		
		return histframe;
	}

}

