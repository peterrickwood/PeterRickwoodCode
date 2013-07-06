package rses.regression.geneticprog.testing;

import java.util.*;
import java.io.*;

import rses.regression.geneticprog.*;
import rses.util.Util;


public class StockDatum implements Datum
{
	public static StockDatum[] getData(java.io.File f)
	throws IOException
	{
		BufferedReader rdr = new BufferedReader(new FileReader(f));
		String line = rdr.readLine();

		ArrayList datums = new ArrayList();

		int index = 0;
		while(line != null)
		{
			String[] words = Util.getWords(line);
			StockDatum d = new StockDatum(words[2], //year
				words[1], //month
				words[0], //day
				index,
				Double.parseDouble(words[3]), //open
				Double.parseDouble(words[8]), //adjusted close
				Double.parseDouble(words[4]), //high
				Double.parseDouble(words[5]), //low
				Integer.parseInt(words[7]), //volume
				null
			);
			datums.add(d);

			index++;
			line = rdr.readLine();
		}

		//we skip the first 2 elements anfd the last element, so
		//that all our predictions can be checked, and so that
		//all node types are defined.
		StockDatum[] chain = new StockDatum[datums.size()];
		double[] closechain = new double[chain.length]; 
		StockDatum[] result = new StockDatum[datums.size()-3];
		for(int i=0; i < chain.length; i++) {
			chain[i] = (StockDatum) datums.get(i);
			closechain[i] = chain[i].close;
		}
		for(int i=2; i < chain.length-1; i++)
		{
			result[i-2] = chain[i]; 
			result[i-2].chain = chain;
			result[i-2].closechain = closechain;
		}
		return result;
	}


	public static int getMonth(String monthstr)
	{
		if(monthstr.toLowerCase().startsWith("jan")) return 0;
		else if(monthstr.toLowerCase().startsWith("feb")) return 1;
		else if(monthstr.toLowerCase().startsWith("mar")) return 2;
		else if(monthstr.toLowerCase().startsWith("apr")) return 3;
		else if(monthstr.toLowerCase().startsWith("may")) return 4;
		else if(monthstr.toLowerCase().startsWith("jun")) return 5;
		else if(monthstr.toLowerCase().startsWith("jul")) return 6;
		else if(monthstr.toLowerCase().startsWith("aug")) return 7;
		else if(monthstr.toLowerCase().startsWith("sep")) return 8;
		else if(monthstr.toLowerCase().startsWith("oct")) return 9;
		else if(monthstr.toLowerCase().startsWith("nov")) return 10;
		else if(monthstr.toLowerCase().startsWith("dec")) return 11;
		else
			throw new IllegalStateException();
	}
	

	private String datestr;
	private GregorianCalendar date;
	private StockDatum[] chain;
	private double[] closechain;
	private int index;
	private double open;
	private double close;
	private double high;
	private double low;
	private int volume;


	
	public int getIndex()
	{
		return this.index;
	}
	
	public boolean isLastInChain()
	{
		return (index == (this.chain.length-1));
	}
	
	public double getClose()
	{
		return this.close;
	}
	
	public StockDatum getPrevious()
	{
		if(this.index == 0)
			return null;
		return this.chain[index-1];
	}
	

	public StockDatum getNext()
	{
		if(this.isLastInChain())
			return null;
		
		return this.chain[index+1];
	}

	
	private StockDatum(String yrstr, String monthstr, String datestr, 
		int index, double open, double close, double high, double low, 
		int volume, StockDatum[] chain)
	{
		this.datestr = datestr+"_"+monthstr+"_"+yrstr;
		int year = Integer.parseInt("20"+yrstr);
		int month = getMonth(monthstr);
		int date = Integer.parseInt(datestr);
		GregorianCalendar cal = new GregorianCalendar(year, month, date);
		this.date = cal;

		this.index = index;
		this.chain = chain;

		this.open = open;
		this.close = close;
		this.high = high;
		this.low = low;
		this.volume = volume;
	}




	public static double[] smooth(double alpha, double[] unsmoothed)
	{
		double[] smoothed = new double[unsmoothed.length];
		smoothed[0] = unsmoothed[0];
		for(int i = 1; i < unsmoothed.length; i++)
			smoothed[i] = alpha*unsmoothed[i] + (1-alpha)*smoothed[i-1];
		return smoothed;
	}



	//is this stockdatum day the next trading day after
	//the previous one
	public boolean isConsecutiveDay()
	{
		if(this.index == 0)
			return false;
		int day = this.date.get(Calendar.DAY_OF_WEEK);
		int prev = this.chain[this.index-1].date.get(Calendar.DAY_OF_WEEK);
	
		switch(day)
		{
			case Calendar.MONDAY:
				if(prev == Calendar.FRIDAY)
					return true;	
			case Calendar.TUESDAY:
				if(prev == Calendar.MONDAY)
					return true;	
			case Calendar.WEDNESDAY:
				if(prev == Calendar.TUESDAY)
					return true;	
			case Calendar.THURSDAY:
				if(prev == Calendar.WEDNESDAY)
					return true;	
			case Calendar.FRIDAY:
				if(prev == Calendar.THURSDAY)
					return true;	
			default:
				throw new IllegalStateException();
		}
	}




	//get a previous index value of the stock
	//i.e. some time before now
	//values closer to 'now' are more likely
	public int getIndex(double val)
	{
		int previndex = (int) Math.round(Util.squash(Math.abs(val))*this.index);
		return this.index-previndex;
	}



	//calculate the average value of the stock over some time
	//period
	public double calculateMean(int start, int end)
	{
		if(start > end)
			throw new IllegalArgumentException();

		double sum = 0.0;
		for(int i =start; i <= end; i++)
			sum += closechain[i];

		return sum/(end-start+1);
	}

	public double calculateMeanChange(int start, int end)
	{
		if(start == end)
			return 0.0;
		return (closechain[end]-closechain[start])/(end-start);
	}


	public double calculateVolatility(int start, int end)
	{
		return calculateVolatility(start, end, calculateMeanChange(start, end));
	}

	public double calculateVolatility(int start, int end, double meanchange)
	{
		if(end-start < 0) throw new IllegalStateException();
		
		if((end-start) < 2)
			start = end-2;

		double sumsq = 0.0;
		for(int i =start+1; i <= end ; i++)
		{
			double dif = (closechain[i]-closechain[i-1]-meanchange);
			sumsq += dif*dif;
		}
		return Math.sqrt(sumsq/(end-start-1));
	}


	public double calculateMeanChangePct(int start, int end)
	{
		if(start > end) throw new IllegalStateException();
		if(start == end)
			start--;
		
		double meanchangepct = 0.0;
		for(int i = start+1; i <= end; i++)
		{
			double pctchange = (closechain[i]-closechain[i-1])/closechain[i-1];
			meanchangepct += pctchange;
		}
		return meanchangepct/(end-start);
	}


	public double calculatePctVolatility(int start, int end)
	{
		if(end-start < 0) throw new IllegalStateException();
		
		if((end-start) < 2)
			start = end-2;

		double meanpct = calculateMeanChangePct(start, end);
		
		double sumsq = 0.0;
		for(int i =start+1; i <= end ; i++)
		{
			double dif = (closechain[i]-closechain[i-1])/closechain[i-1]-meanpct;
			sumsq += dif*dif;
		}
		return Math.sqrt(sumsq/(end-start-1));
	}


	public String toString()
	{
		return "date: "+datestr+" adjclose: "+close+" high: "+high+" low: "+low+" volume: "+volume;
	}

	
	
	
	static NodeGenerater getNodeGenerater()
	{
		return new SDNodeGen();
	}
	
	
	
	static class SDNodeGen extends NodeGenerater
	{
		private Random nodegenrand = new Random();
		public NumericNode generateNode()
		{
			int rind = nodegenrand.nextInt(getNumNodeTypes());
			
			if(rind < super.getNumNodeTypes())
				return super.generateNode();
			
			rind -= super.getNumNodeTypes();
			
			switch(rind)
			{
				case 0: return new AverageClose();
				case 1: return new AverageMovementPct();
				case 2: return new Close();
				case 3: return new FridayWeekdaySplit();
				case 4:	return new PreviousClose();
				case 5:	return new PreviousRange();
				case 6:	return new Range();
				case 7:	return new Volatility();
				case 8:	return new VolatilityPct();
				case 9:	return new WeightedAverageClose();
				case 10: return new WeightedAverageRange();
				case 11: return new WeightedAverageVolume();
				case 12: return new WeightedVolatility();
				case 13: return new WeightedVolatilityPct();
				default: throw new IllegalStateException("impossible case reached");
			}
		}
	
	
		public int getNumNodeTypes()
		{
			return 14+super.getNumNodeTypes();
		}
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
//////////////////////////////////////////////////////////////	
//////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////	
//////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////
//
//   NumericNodes specific to StockDatum are defined here
//
//////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////
	
	
	
	
	
	
	
	
	
	static class FridayWeekdaySplit extends NumericNode
	{
		public int getNumChildren()
		{
			return 2;
		}

		public double evaluate(Datum dat) 
		{
			StockDatum d = (StockDatum) dat;
			if(d.date.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY)
				return this.children[0].evaluate(d);
			else
				return this.children[1].evaluate(d);
		}

		public String toString()
		{
			return "(isfriday? "+children[0]+" : "+children[1]+")";
		}		
	}


//	get the value of the node. note we give the unscaled
//	value here (since the scaled value is by definition 1
//	and hence makes no sense). So this measure is a measure
//	of the absolute share price, and may be useful in
//	determining if (for example) high priced shares move
//	differently to low prices shares
	static class Close extends NumericNode
	{
		public int getNumChildren()
		{
			return 0;
		}

		public double evaluate(Datum dat) 
		{
			StockDatum d = (StockDatum) dat;
			return d.close;
		}

		public String toString()
		{
			return "close";
		}		
		
	}




	static class Range extends NumericNode
	{
		public int getNumChildren()
		{
			return 0;
		}

		//express the range as a percentage of the stock price
		//at the close of the previous day
		public double evaluate(Datum dat) 
		{
			StockDatum d = (StockDatum) dat;
			if(d.index == 0 || d.open <= 0.0)
				throw new IllegalStateException("division by zero in Range NumericNode");
			return (d.high-d.low)/d.open;
		}

		public String toString()
		{
			return "range";
		}		
	}



//	what was the range on some prior day
//	range is expressed as a percentage of the open price on
//	that day
	static class PreviousRange extends NumericNode
	{
		public int getNumChildren()
		{
			return 1;
		}


		public double evaluate(Datum dat)
		{
			StockDatum d = (StockDatum) dat;
			int pi = d.getIndex(children[0].evaluate(d));
			double prevrange = (d.chain[pi].high-d.chain[pi].low);
			double prevval = d.chain[pi].open;
			if(prevrange <= 0.0)
				return 0.0;
			if(prevval <= 0.0)
				throw new IllegalStateException("cannot compute range of 0 priced stock");
			return prevrange/prevval;
		}

		public String toString()
		{
			return "range("+children[0]+")";
		}		

	}


//	here we give the value of the previous close
//	scaled in relation to the current close
	static class PreviousClose extends NumericNode
	{
		public int getNumChildren()
		{
			return 1;
		}

		public double evaluate(Datum dat) 
		{
			StockDatum d = (StockDatum) dat;
			int pi = d.getIndex(children[0].evaluate(d));
			return d.closechain[pi]/d.close;
		}

		public String toString()
		{
			return "close("+children[0]+")";
		}		
	}




//	use simple exponential smoothing to calculate the next value
//	normalized w.r.t the current price
	static class WeightedAverageClose extends NumericNode
	{
		public int getNumChildren()
		{
			return 1; //the alpha parameter
		}

		public double evaluate(Datum dat)  
		{
			StockDatum d = (StockDatum) dat;
			//alpha is between 0 and 1
			double alpha = Math.abs(Util.squash(this.children[0].evaluate(d)));
			
			double[] unsmoothed = new double[d.index+1];
			for(int i =0; i < unsmoothed.length; i++)
				unsmoothed[i] = d.closechain[i];
			
			//now smooth the entire series up to the
			//current point
			double[] smoothed = smooth(alpha, unsmoothed);

			return smoothed[smoothed.length-1]/d.close;
		}		

		public String toString()
		{
			return "weightedclose("+children[0]+")";
		}		
	}



	static class WeightedAverageVolume extends NumericNode
	{
		public int getNumChildren()
		{
			return 1; //the alpha parameter
		}

		private double volavg = -1;
		public double evaluate(Datum dat)  
		{
			StockDatum d = (StockDatum) dat;
			if(volavg < 0)
			{
				volavg = 0;
				for(int i =0; i <= d.index; i++)
					volavg += d.chain[i].volume;
				volavg /= (d.index+1);
			}

			//alpha is between 0 and 1
			double alpha = Math.abs(Util.squash(this.children[0].evaluate(d)));
			
			//now smooth the entire series up to the
			//current point
			double[] unsmoothed = new double[d.index+1];
			for(int i = 0; i < unsmoothed.length; i++)
				unsmoothed[i] = d.chain[i].volume;
			double[] smoothed = smooth(alpha, unsmoothed);

			return smoothed[smoothed.length-1]/volavg;
		}		

		public String toString()
		{
			return "weightedvolume("+children[0]+")";
		}		
	}
		


	static class WeightedAverageRange extends NumericNode
	{
		public int getNumChildren()
		{
			return 1; //the alpha parameter
		}

		public double evaluate(Datum dat)  
		{
			StockDatum d = (StockDatum) dat;
			if(d.index <= 1)
				throw new IllegalStateException("too early to calculate weighted range..."+d.index);

			//alpha is between 0 and 1
			double alpha = Math.abs(Util.squash(this.children[0].evaluate(d)));
			
			//now smooth the entire series up to the
			//current point
			double[] unsmoothed = new double[d.index+1];
			for(int i = 0; i <= d.index; i++) {
				double movement = d.chain[i].high-d.chain[i].low;
				if(movement <= 0.0)
					unsmoothed[i] = 0.0;
				else if(d.chain[i].open <= 0.0)
					throw new RuntimeException("open <= 0.0!");
				else
					unsmoothed[i] = movement/d.chain[i].open;
			}
			
			double[] smoothed = smooth(alpha, unsmoothed);

			return smoothed[smoothed.length-1];
		}		

		public String toString()
		{
			return "weightedrange("+children[0]+")";
		}

	}


//	volatility just being the standard deviation 
//	of the returns over some time period
	static class Volatility extends NumericNode
	{
		public int getNumChildren()
		{
			return 1; //the time horizon we are interested in
		}


		public double evaluate(Datum dat)
		{
			StockDatum d = (StockDatum) dat;
			int pi = d.getIndex(children[0].evaluate(d));
			return d.calculateVolatility(pi, d.index)/d.close;
		}

		public String toString()
		{
			return "volatility("+children[0]+")";
		}

	}


	static class WeightedVolatility extends NumericNode
	{
		public int getNumChildren()
		{
			return 2; //the alpha parameter and the time horizon
		}

		public double evaluate(Datum dat)  
		{
			StockDatum d = (StockDatum) dat;
			int pi = d.getIndex(children[1].evaluate(d));		

			//alpha is between 0 and 1
			double alpha = Math.abs(Util.squash(this.children[0].evaluate(d)));

			double[] unsmoothed = new double[d.index-pi+1];
			for(int i =pi; i <= d.index; i++)
				unsmoothed[i-pi] = d.calculateVolatility(i, d.index);
			
			double[] smoothed = smooth(alpha, unsmoothed);

			return smoothed[smoothed.length-1]/d.close;
		}		

		public String toString()
		{
			return "weightedvolatility("+children[0]+" , "+children[1]+")";
		}

	}



	static class VolatilityPct extends NumericNode
	{
		public int getNumChildren()
		{
			return 1; //the time horizon we are interested in
		}


		public double evaluate(Datum dat)
		{
			StockDatum d = (StockDatum) dat;
			int pi = d.getIndex(children[0].evaluate(d));
			return d.calculatePctVolatility(pi, d.index);
		}

		public String toString()
		{
			return "volatilitypct("+children[0]+")";
		}

	}

	static class WeightedVolatilityPct extends NumericNode
	{
		public int getNumChildren()
		{
			return 2; //the alpha parameter and the time horizon
		}

		public double evaluate(Datum dat)  
		{
			StockDatum d = (StockDatum) dat;
			int pi = d.getIndex(children[1].evaluate(d));		

			//alpha is between 0 and 1
			double alpha = Math.abs(Util.squash(this.children[0].evaluate(d)));

			double[] unsmoothed = new double[d.index-pi+1];
			for(int i =pi; i <= d.index; i++)
				unsmoothed[i-pi] = d.calculatePctVolatility(i, d.index);
			
			//now smooth the entire series
			double[] smoothed = smooth(alpha, unsmoothed);

			return smoothed[smoothed.length-1];
		}		

		public String toString()
		{
			return "weightedvolatilitypct("+children[0]+" , "+children[1]+")";
		}

	}


//	the average close price over some time horizon,
//	scaled according to the current close
	static class AverageClose extends NumericNode
	{
		public int getNumChildren()
		{
			return 1; //the time horizon we are interested in
		}


		public double evaluate(Datum dat)
		{
			StockDatum d = (StockDatum) dat;
			int pi = d.getIndex(this.children[0].evaluate(d));
			
			double sum = 0.0;
			for(int i =pi;i <= d.index; i++)
				sum += d.closechain[i];
			
			return sum / ((d.index-pi+1)*d.close);
		}

		public String toString()
		{
			return "averageclose("+children[0]+")";
		}

	}



//	The average movement of the stock over some
//	time horizon. 
	static class AverageMovementPct extends NumericNode
	{
		public int getNumChildren()
		{
			return 1; //the time horizon we are interested in
		}


		public double evaluate(Datum dat)
		{
			StockDatum d = (StockDatum) dat;
			int pi = d.getIndex(this.children[0].evaluate(d));
			return d.calculateMeanChangePct(pi, d.index);
		}

		public String toString()
		{
			return "avgmovepct("+children[0]+")";
		}

	}



	
	
	
	
	
}







