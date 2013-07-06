package rses.apps;

public class MPMC_test 
{

	
	public static double getErr(double[] p, double[] c)
	{
		int monthindex = (int) p[0]+1;  //the program start month, we add 1 because the first data element is in fact the start month
		int predatastart = monthindex-14;
		if(predatastart < 1)
			throw new RuntimeException("Impossible predatastart");
		
		double errsum = 0.0;
		for(int i = predatastart; i < predatastart+12; i++)
		{
			if(Double.isNaN(p[i]))
				throw new RuntimeException("Impossible... nan in pre data for participant.. should have checked this...");
			
			double pv = p[i];
			double cv = c[i];
			if(Double.isNaN(cv))
				return Double.POSITIVE_INFINITY;
			errsum += (pv-cv)*(pv-cv);
		}
		return errsum;
	}
	
	

	public static double getSavingEst(double[] p, double[] c)
	{
		int monthindex = (int) p[0]+1;  //the program start month, we add 1 because the first data element is in fact the start month
		int postdatastart = monthindex+3;

		double diff = 0.0;
		int diffcount = 0;
		for(int i = postdatastart; i < postdatastart+12 && i < p.length; i++)
		{
			double pv = p[i];
			double cv = c[i];
			if(Double.isNaN(pv) || Double.isNaN(cv))
				continue;
			
			diff += pv-cv;
			diffcount += 1;
		}
		
		if(diffcount == 0)
			throw new IllegalStateException();
		return diff/diffcount;
	}
	
	
	
	
	
	
	public static void main(String[] args) throws Exception
	{
		double[][] pdata = rses.util.FileUtil.readVectorsFromFile(new java.io.File(args[0]));
		double[][] cdata = rses.util.FileUtil.readVectorsFromFile(new java.io.File(args[1]));
		double totaldiff = 0.0;
		int diffcount = 0;
		
		for(int i = 0; i < pdata.length; i++)
		{
			double besterr = Double.POSITIVE_INFINITY;
			int besti = -1;
			for(int j = 0; j < cdata.length; j++)
			{
				double err = getErr(pdata[i], cdata[j]);
				if(err < besterr)
				{
					besterr = err;
					besti = j;
				}
			}
			
			double sav = 0.0;
			try {
				sav = getSavingEst(pdata[i], cdata[besti]);
				totaldiff += sav;
				diffcount += 1;
			}
			catch(IllegalStateException ise)
			{
				
			}
			
			System.out.println("After "+(i+1)+" matches avg saving is "+(totaldiff/(diffcount))+"  (from "+diffcount+" valid savings estimates)");
		}
	
	}
	
	
	
}
