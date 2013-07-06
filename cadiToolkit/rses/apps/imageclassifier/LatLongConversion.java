package rses.apps.imageclassifier;

import rses.Debug;




public abstract class LatLongConversion
{
	
	public static double[] getLatLong(int xpix, int ypix)
	{
		Debug.println("Getting LAT/LON for xpix/ypix "+xpix+" "+ypix, Debug.EXTRA_INFO);

		double xpixels = xpix/10000.0;
		double ypixels = ypix/10000.0;
		
		
		double lat=xpixels*-0.00192872553603 + ypixels*0.421979744096 + Math.sqrt(ypixels)*-1.04896883435 + Math.pow(ypixels,1.5)*-0.0308053587966 + xpixels*ypixels*-1.11299430796e-05 + -33.874;
		double lon=xpixels*0.546365468657 + ypixels*0.0052554170763 + Math.sqrt(xpixels)*-1.58405966937 + Math.pow(xpixels,1.5)*-0.0357416383587 + xpixels*ypixels*-0.000186417973905 + 151.0925;
		
		//double lat=xpixels*-0.00190771369651 + ypixels*0.412441116895 + Math.sqrt(ypixels)*-1.03716072122 + Math.pow(ypixels,1.5)*-0.0297840110923 + xpixels*ypixels*-8.82130068351e-06 + -33.874;
		//double lon=xpixels*0.533808840568 + ypixels*0.00503020150393 + Math.sqrt(xpixels)*-1.56551663871 + Math.pow(xpixels,1.5)*-0.034546485736 + xpixels*ypixels*-0.000171041558142 + 151.0925;
		
		//double lat=xpixels*-0.0019120829708 + ypixels*0.412630022879 + Math.sqrt(ypixels)*-1.03747019912 + Math.pow(ypixels,1.5)*-0.0298135245299 + xpixels*ypixels*-8.24042920169e-06 + -33.874;
		//double lon=lon=xpixels*0.533926775259 + ypixels*0.00502462433074 + Math.sqrt(xpixels)*-1.56573287034 + Math.pow(xpixels,1.5)*-0.034562588184 + xpixels*ypixels*-0.000170629518563 + 151.0925;
		//double lat=xpixels*-0.00191572404014 + ypixels*0.412672865632 + Math.sqrt(ypixels)*-1.03753097147 + Math.pow(ypixels,1.5)*-0.0298211516279 + xpixels*ypixels*-7.85155668766e-06 + -33.874;
		//double lon=xpixels*0.533938734951 + ypixels*0.00502245384779 + Math.sqrt(xpixels)*-1.56575256707 + Math.pow(xpixels,1.5)*-0.0345644303131 + xpixels*ypixels*-0.000170457112221 + 151.0925;
		//double lat=xpixels*-0.00197382248224 + ypixels*0.412492698953 + Math.sqrt(ypixels)*-1.03697123157 + Math.pow(ypixels,1.5)*-0.0298135802599 + xpixels*ypixels*-3.53136576467e-06 + -33.874;
		//double lon=xpixels*0.534037893806 + ypixels*0.00500686668451 + Math.sqrt(xpixels)*-1.56589502098 + Math.pow(xpixels,1.5)*-0.0345807038679 + xpixels*ypixels*-0.000169358090157 + 151.0925;
		//double lat=xpixels*-0.00180695841123 + ypixels*0.412678071281 + Math.sqrt(ypixels)*-1.03810576267 + Math.pow(ypixels,1.5)*-0.0297842463446 + xpixels*ypixels*-1.48586833574e-05 + -33.874;
		//double lon=xpixels*0.534053136839 + ypixels*0.00490994813506 + Math.sqrt(xpixels)*-1.56566324218 + Math.pow(xpixels,1.5)*-0.0345990958085 + xpixels*ypixels*-0.000163332117818 + 151.0925;
		
		//Debug.println("Lat is "+lat+"    Long is "+lon, Debug.EXTRA_INFO);
		//double lat=xpixels*-1.88127160508e-07 + ypixels*1.07598364743e-05 -35.0490582804;
		//double lon=xpixels*1.2890827043e-05 + ypixels*2.43877216751e-07 +149.096070296;

		return new double[] {lat, lon};
	}
	
	
	
}