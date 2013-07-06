package rses.regression.kriging;


import java.util.ArrayList;

import rses.Debug;
import rses.math.MathUtil;
import rses.regression.Datum;
import rses.util.FileUtil;
import rses.util.Heap;
import rses.util.HeapElement;





/**
 * TODO At the moment, this class only implements 2D Kriging.
 * @author peterr
 *
 */
public class Kriging
{
	private double r;
	private double sill;
	private Datum[] trainingData;
	private int maxdata;
	
	
	
	public Kriging(Datum[] trainingData, double r, double sill, int maxdata)
	{
		this.trainingData = trainingData;
		this.r = r;
		this.sill = sill;
		this.maxdata = Math.min(maxdata, trainingData.length);
	}
	

	
	
	/**
	 * 
	 * @param data
	 * @param rstep
	 * @param angle The angle (with 0 being E/W, i.e. no rotation)
	 * @return A new dataset reoriented and rescaled to the new basis
	 */
	public static Datum[][] estimateRadiusSillWithAnisotropy(Datum[] data, Datum[] testdata, double rstep, double majorangle, double norm)
	{
		Datum[] fakedata = new Datum[data.length];
		double[] Y_rs = null;
		double[] X_rs = null;
		
		double sintheta = Math.sin(majorangle);
		double costheta = Math.cos(majorangle);
		
		
		//anisptropy basically means that we differentially scale the x and y coordinates
		//so that x distances count less than (or more than) y distances
		
		
		//Fit Y axis
		for(int i =0; i < fakedata.length; i++)
		{
			double x = data[i].getParameter(1);
			double y = data[i].getParameter(0);
			//double newx = costheta*x-sintheta*y;
			double newy = sintheta*x+costheta*y;
			double[] params = new double[] {newy, 0};
			fakedata[i] = new Datum(params, data[i].getValue());
		}
		Y_rs = Kriging.estimateRadiusAndSill(fakedata, rstep/2, norm);
		//Fit X axis
		for(int i =0; i < fakedata.length; i++)
		{
			double x = data[i].getParameter(1);
			double y = data[i].getParameter(0);
			double newx = costheta*x-sintheta*y;
			//double newy = sintheta*x+costheta*y;
			double[] params = new double[] {0, newx};
			fakedata[i] = new Datum(params, data[i].getValue());
		}
		X_rs = Kriging.estimateRadiusAndSill(fakedata, rstep/2, norm);
		
		fakedata = null;
		
		//return the data all corrected for anisotropy
		//This data will no longer be in sensible lat/long coordinates,
		//but as long as we always scale down, the size of the error
		//should be small
		double scalefact = Y_rs[0]/X_rs[0];
		Datum[][] newdata = new Datum[2][];
		newdata[0] = new Datum[data.length];
		newdata[1] = new Datum[testdata.length];
		for(int i =0; i < data.length; i++)
		{
			double x = data[i].getParameter(1);
			double y = data[i].getParameter(0);
			double newx = costheta*x-sintheta*y;
			double newy = sintheta*x+costheta*y;
			if(scalefact < 1)
				newy *= scalefact;
			else
				newx /= scalefact;
			newdata[0][i]=new Datum(new double[] {newy, newx}, data[i].getValue());
		}
		for(int i =0; i < testdata.length; i++)
		{
			double x = testdata[i].getParameter(1);
			double y = testdata[i].getParameter(0);
			double newx = costheta*x-sintheta*y;
			double newy = sintheta*x+costheta*y;
			if(scalefact < 1)
				newy *= scalefact;
			else
				newx /= scalefact;
			newdata[1][i]=new Datum(new double[] {newy, newx}, testdata[i].getValue());
		}
		
		 
		return newdata;
			
	}
	
	
	public static double[] estimateRadiusAndSill(Datum[] data, double rstep, double norm)	
	{
		return estimateRadiusAndSill(data, rstep, Double.NaN, Double.NaN, Double.NaN, Double.NaN, norm);
	}
	
	
	public static double[] estimateRadiusAndSill(Datum[] data, double rstep, double min_r, double max_r, double min_sill, double max_sill, double norm)
	{
		double bestr = Double.NaN;
		double bestsill = Double.NaN;
		double besterr = Double.POSITIVE_INFINITY;
		double meandist = 0.0;
		double meanvar = 0.0;
		
		ArrayList distances = new ArrayList(data.length*data.length/2);
		for(int i =0; i < data.length; i++)
		{
			Debug.println("building distance matrix for variogram analysis... at row "+i+" of "+data.length, Debug.EXTRA_INFO);
			for(int j = i+1; j < data.length; j++)
			{
				double dist = MathUtil.getDistanceBetweenPointsOnEarth(
						data[i].getParameter(0), data[i].getParameter(1),
						data[j].getParameter(0), data[j].getParameter(1));
				meandist += dist;
				double variance = Math.pow(data[i].getValue()-data[j].getValue(), 2)/2;
				meanvar += variance;
				distances.add(new double[] {dist, variance});
			}
		}
		meandist /= distances.size();
		meanvar /= distances.size();
		Debug.println("Mean distance in variogram analysis is "+meandist, Debug.INFO);
		Debug.println("Mean variance in variogram analysis is "+meanvar, Debug.INFO);
		double minr = rstep;
		if(!Double.isNaN(min_r)) minr = Math.max(min_r, minr);
		double maxr = meandist;
		if(!Double.isNaN(max_r)) maxr = Math.min(max_r, maxr);
		double minsill = meanvar/2;
		if(!Double.isNaN(min_sill)) minsill = Math.max(min_sill, minsill);
		double maxsill = meanvar*1.5;
		if(!Double.isNaN(max_sill)) maxsill = Math.min(max_sill, maxsill);
		double sillstep = 0.005*meanvar;
		
		if(minr >= maxr)
			throw new RuntimeException("minr is greater than maxr");
		if(minsill >= maxsill)
			throw new RuntimeException("minsill is greater than maxsill");
		
		double r = minr;
		while(r <= maxr)
		{
			double sill = minsill;
			while(sill <= maxsill)
			{
				double err = getSphericalModelFit(distances, r, sill, norm);
				Debug.println("fit for r="+r+" sill="+sill+" is "+err, Debug.EXTRA_INFO);
				if(err < besterr) {
					bestr = r;
					bestsill = sill;
					besterr = err;
					Debug.println("best r/sill are "+bestr+"/"+bestsill, Debug.INFO);
				}
				sill += sillstep;
			}
			r += rstep;
		}
		
		return new double[] {bestr, bestsill};
	}
	
	
	private static double getSphericalModelFit(ArrayList distances, double r, double sill, double norm)
	{
		double err = 0.0;
		int ndat = distances.size();
		for(int i = 0; i < ndat; i++)
		{
			double[] distvar = (double[]) distances.get(i);
			double dist = distvar[0];
			double variance = distvar[1];
			double predictedvar = spherical(dist, r, sill);
			if(norm >= 1.0)
				err += Math.pow(variance-predictedvar, norm);
			else if(norm >= 0.0)
				err += Math.pow(Math.abs(variance-predictedvar), norm);
			else
				throw new IllegalArgumentException("Illegal value for Krig norm");
		}
		return err;
	}
	
	
	
	private static double spherical(double dist, double r, double sill)
	{
		double modelpredict;
		if(dist <= r)
			modelpredict = (sill/2)*(3*dist/r - Math.pow(dist/r, 3));
		else
			modelpredict = sill;
       return modelpredict;
	}
	
	public double getPrediction(Datum data)
	{
		Heap<Datum> h = new Heap<Datum>(maxdata);
		for(int i =0; i < trainingData.length; i++)
		{
			double dist = MathUtil.getDistanceBetweenPointsOnEarth(trainingData[i].getParameter(0),
					trainingData[i].getParameter(1), data.getParameter(0), 
					data.getParameter(1));
			h.insert(new HeapElement<Datum>(dist, trainingData[i]));
		}
		
		Datum[] datatouse = new Datum[maxdata];
		for(int i =0; i < maxdata; i++)
			datatouse[i] = h.extractMin().getObject();
		
		
		int rows = maxdata+1;
		int columns = maxdata+2; //1 extra for the solution vector
		double[][] A = new double[rows][columns];
		
		//fill in the bulk of the matrix
		for(int row = 0; row < rows-1; row++)
		{
			double lat1 = datatouse[row].getParameter(0);
			double lon1 = datatouse[row].getParameter(1);
			for(int col = 0; col < columns-2; col++)
			{
				double lat2 = datatouse[col].getParameter(0);
				double lon2 = datatouse[col].getParameter(1);
				double dist = MathUtil.getDistanceBetweenPointsOnEarth(lat1, lon1, lat2, lon2);
				A[row][col] = spherical(dist, this.r, this.sill);
			}
			A[row][columns-2] = 1.0; //the slack parameter
		}
		
		//add the last row (the constraint that the weights add to 1.0)
		for(int col = 0; col < columns-2; col++)
			A[rows-1][col] = 1.0;
		A[rows-1][columns-2] = 0.0; //no slack parameter for constraint equation
		A[rows-1][columns-1] = 1.0;
		
		
		//add the last (augmented) column
		for(int row = 0; row < rows-1; row++) //skip last row (constaint equation)
		{
			double lat1 = datatouse[row].getParameter(0);
			double lon1 = datatouse[row].getParameter(1);
			double dist = MathUtil.getDistanceBetweenPointsOnEarth(lat1, lon1, data.getParameter(0), data.getParameter(1));
			A[row][columns-1] = spherical(dist, this.r, this.sill);
		}


		//now solve the system. This gives us our weights
		if(Debug.equalOrMoreVerbose(Debug.EXTRA_INFO))
		{
			Debug.println("Matrix to solve for weights:", Debug.EXTRA_INFO);
			Debug.println(MathUtil.getMatrixStringRepresentation(A), Debug.EXTRA_INFO);
		}
		Debug.println("Solving matrix via gausselim", Debug.EXTRA_INFO);
		MathUtil.gaussElim(A);
		if(Debug.equalOrMoreVerbose(Debug.EXTRA_INFO))
		{
				Debug.println("Matrix after solving:", Debug.EXTRA_INFO);
				Debug.println(MathUtil.getMatrixStringRepresentation(A), Debug.EXTRA_INFO);
		}
		
		
		//now use the weights to come up with a prediction
		double prediction = 0.0;
		for(int i =0; i < datatouse.length; i++)
		{
			prediction += datatouse[i].getValue()*A[i][columns-1];
		}
		
		return prediction;
		
	}	

}


 


