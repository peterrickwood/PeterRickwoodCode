package rses;

import java.io.Serializable;
import java.util.ArrayList;

import javax.swing.JOptionPane;

import rses.inverse.UserFunctionHandle;
import rses.util.HeapElement;



/** A Model in parameter space. 
 */
public class Model implements Serializable
{
	protected double[] parameter;
	
	protected double misfit = Double.NaN;	
	private boolean misfitCached = false;
	
	private long discoveryTime = -1L;
	
	public Model(UserFunctionHandle h)
	{
		this(h.getBoundsOnModelSpace());
	}
	
	
	public Model(double[][] bounds)
	{
		parameter = new double[bounds.length];
		for(int i =0; i < parameter.length; i++)
		{
			double uppr = bounds[i][1];
			double lwr = bounds[i][0];
			double range = uppr-lwr;
			parameter[i] = lwr+range*Math.random();
		}
	}
	
	
	public Model(double[] params, double misfit)
	{
		this.parameter = params;
		this.misfit = misfit;
		misfitCached = true;
	}

	public Model(double[] params)
	{
		this.parameter = params;
	}
	

	/** WARNING: Use with caution -- this <i>may</i> return the internal
	 *   array of model parameters, so do not modify this array, as it 
	 *   may stuff up the internal state of the model. You cannot assume,
	 *   though, that this method will return the internal model array,
	 *   as sub-classes are free to override this method, and it is not
	 *   part of the contract of this method that it must return the  
	 *   internal representation.
	 * 
	 * @return
	 */
	public double[] getModelParameters()
	{
		return parameter;
	}
		
	public double getModelParameter(int index)
	{
		return parameter[index];
	}
	
	public int getNumParameters()
	{
		return parameter.length;
	}
	
	
	public boolean isMisfitAvailable()
	{
		return misfitCached;
	}

	
	public double getMisfit()
	{
		if(misfitCached)
			return misfit;
		else
			throw new RuntimeException("getMisfit() called.... but misfit has not been set for this model");
	}
	
	public void setMisfit(double m)
	{
		if(Debug.equalOrMoreParanoid(Debug.DEBUG_NORMAL)) {
			if(this.isMisfitAvailable() && m != this.misfit)
				throw new RuntimeException("attempt to set *different* misfit of Model that already has its misfit set");
		}
		this.misfitCached = true;
		this.misfit = m;
	}


	public double distanceFrom(Model other)
	{
		if(other.getNumParameters() != this.getNumParameters())
			throw new IllegalArgumentException("distanceFrom() method called with mismatched Models");
		double dist = 0.0;
		for(int i =0; i < this.parameter.length; i++)
			dist += (other.parameter[i]-parameter[i])*(other.parameter[i]-parameter[i]);
		return Math.sqrt(dist);
	}

	public long getDiscoveryTime()
	{
		return this.discoveryTime;
	}
	
	public void setDiscoveryTime(long t)
	{
		this.discoveryTime = t;
	}
	
	
	public double getHeapValue()
	{
		return this.getMisfit();
	}

	
	/** Get an estimate of the number of bytes required
	 *  for this object... Dont rely on this being 
	 *  exact!
	 * 
	 * @return number of bytes to store this object
	 */
	public int getMemoryEstimate()
	{
		return this.parameter.length*8+16+20;
	}
	
	public String toString()
	{
		double[] nums = this.getModelParameters();
		StringBuffer res = new StringBuffer("PARAMETERS: "+nums[0]);
		for(int i = 1; i < nums.length; i++) {
			res.append("   ");
			res.append(nums[i]);
		}
		res.append("   TIME: ");
		res.append(this.discoveryTime);
		res.append("   MISFIT: ");
		if(this.isMisfitAvailable())
			res.append(getMisfit());
		else
			res.append(Double.NaN);

		return res.toString();
	}
	
	
	/** generate a model from a String. If the String is in the incorrect format, a
	 * RuntimeException is thrown
	 * 
	 * @param line
	 * @return A model, if the string is in the incorrect format
	 */
	public static Model fromString(String line)
	{
		int i = 1;
		String[] words = rses.util.Util.getWords(line);
		if(words.length < 6) //minimum of 6 fields: "PARAMETERS param TIME time MISFIT mf"
			throw new RuntimeException("incorrect format to string");
		if(!words[0].equals("PARAMETERS:"))
			throw new RuntimeException("incorrect format to string");
			
		ArrayList params = new ArrayList();
		for(i =1; i < words.length && !words[i].equals("TIME:"); i++)
		{
			Double param = null;
			try { param = new Double(words[i]); }
			catch(NumberFormatException nfe)
			{ throw new RuntimeException("incorrect format to string"); }
			
			params.add(param);
		}
		double[] paramarray = new double[params.size()];
		for(int j = 0; j < paramarray.length; j++)
			paramarray[j] = ((Double) params.get(j)).doubleValue();

		if(i >= words.length) throw new RuntimeException("incorrect format to string");
		else if(!words[i].equals("TIME:")) throw new RuntimeException("incorrect format to string");			
		i++; //skip past "TIME:" field
		if(i >= words.length) throw new RuntimeException("incorrect format to string");
		
		long time;
		try { time = Long.parseLong(words[i]); }
		catch(NumberFormatException nfe)
		{ throw new RuntimeException("incorrect format to string");}
		
		i++;
		if(i >= words.length || !words[i].equals("MISFIT:"))
			throw new RuntimeException("incorrect format to string");
		i++;
		if(i >= words.length)
			throw new RuntimeException("incorrect format to string");
		
		double mf;
		try { mf = Double.parseDouble(words[i]); }
		catch(NumberFormatException nfe)
		{ throw new RuntimeException("incorrect format to string");}
		
		Model result = new Model(paramarray, mf);
		result.setDiscoveryTime(time);
		return result;
	}
	
	
	
	public static ModelGenerator getUniformModelGenerator(double[][] bounds)
	{
		return new UniformModelGenerator(bounds);
	}
	
	
	
	
	
	
	/** Pop up a window giving the user a number of methods for generating
	 *  a new model (such as reading from file, etc)
	 * 
	 * @param parent
	 * @param bounds
	 * @return
	 * @throws Exception
	 */
	public static Model guiGetNewModel(java.awt.Component parent, double[][] bounds) throws Exception
	{
		String[] options = new String[] {"From log file", "From user (keyboard)", 
											"Generate random model", "From other file"};
		//read in a model, either from a file, or that the user types in
		String choice = (String) 
			JOptionPane.showInputDialog(parent, "How shall I get the new model?", "Get new model",
			JOptionPane.QUESTION_MESSAGE, null, options, options[2]);
		
		if(choice == null)
			return null;
		
		if(choice.equals(options[0])) //log file
		{
			choice = (String) 
				JOptionPane.showInputDialog(parent, "Enter the rank of the model to get (1=best, 2=second best, etc)", 
				"Choose rank", JOptionPane.PLAIN_MESSAGE, null, null, "");
			int rank = 1;
			try { rank = Integer.parseInt(choice); }
			catch(NumberFormatException nfe) { rank = -1; }
			if(rank <= 0)
				throw new RuntimeException("A negative model rank makes no sense");

			java.io.File modf = rses.util.FileUtil.guiSelectModelFile(parent);
			if(modf == null)
				return null;
			else if(!modf.exists())
				throw new RuntimeException("Model file does not exist!");

			Model m = null;
			m = new rses.inverse.util.ModelFile(modf).getModelByRank(rank);
			return m;
		}
		else if(choice.equals(options[1])) //from keyboard
			return guiPromptUserForModel(parent, bounds);
		else if(choice.equals(options[2])) { //Random
			Model m = getUniformModelGenerator(bounds).generateNewModel();
			return m;
		}
		else if(choice.equals(options[3])) //From user defined file
		{
			java.io.File f = rses.util.FileUtil.guiSelectFile(parent, "Select Model File");
			return readFromUserFile(f, bounds.length);
		}
		else
			throw new IllegalStateException("Unknown/Impossible choice made -- internal error");
		
	}

	
	
	
	
	
	
	public static Model readFromUserFile(java.io.File f, int nparam) throws java.io.IOException
	{
		java.io.BufferedReader rdr = new java.io.BufferedReader(new java.io.FileReader(f));
		String line = rdr.readLine();
		if(line == null)
			throw new java.io.IOException("Empty file!!!");
		Model m = null;
		
		try { m = Model.fromString(line);}
		catch(RuntimeException rte) {}
		if(m != null)
			return m; 
		
		//ok, not a model file, lets try a single line with just parameters,
		//or a multi-line file with 1 line per parameter
		String[] words = rses.util.Util.getWords(line);
		if(words.length == nparam || words.length == (nparam+1))
			/* do nothing, this looks correct */;
		else //try 1 param per line 
		{ 
			java.util.ArrayList lines = new java.util.ArrayList(); 
			while(line != null) {
				lines.add(line);
				line = rdr.readLine();
			}
			words = new String[lines.size()];
			for(int i =0; i < words.length; i++)
				words[i] = ((String) lines.get(i)).trim();
		}
		
		if(words.length == nparam || words.length == (nparam+1))
		{
			double[] vals = new double[nparam];
			double mf = Double.NaN;
			for(int i =0; i < words.length; i++) 
			{
				try {
					if(i == nparam)
						mf = Double.parseDouble(words[i]); 
					else
						vals[i] = Double.parseDouble(words[i]);
				}
				catch(NumberFormatException nfe) {
					vals = null;
					break;
				}
			}
			if(vals != null)
			{
				if(words.length == (nparam+1))
					return new Model(vals, mf);
				else
					return new Model(vals);
			}
		} 
		
		throw new RuntimeException("Unknown/Invalid file format");
	}
	
	
	
	
	
	public static Model guiPromptUserForModel(java.awt.Component parent, double[][] bounds) 
	{	
		int dim = bounds.length;
		javax.swing.JPanel[] panels = new javax.swing.JPanel[dim];
		javax.swing.JTextField[] textfields = new javax.swing.JTextField[dim];
		for(int i = 0; i < dim; i++)
		{
			javax.swing.JLabel l1 = new javax.swing.JLabel("Parameter "+(i+1));
			javax.swing.JLabel l2 = new javax.swing.JLabel("["+bounds[i][0]+","+bounds[i][1]+"]");
			textfields[i] = new javax.swing.JTextField();
			textfields[i].setEditable(true);
			textfields[i].setColumns(20);
			panels[i] = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
			panels[i].add(l1); panels[i].add(textfields[i]);  panels[i].add(l2);
		}
		JOptionPane.showConfirmDialog(null, panels, "Enter Model", JOptionPane.OK_CANCEL_OPTION);
		
		//now lets see what the user chose
		double[] params = new double[dim];
		for(int i =0; i < textfields.length; i++)
		{
			try {
				params[i] = Double.parseDouble(textfields[i].getText());
				if(params[i] < bounds[i][0] ||
				   params[i] > bounds[i][1])
				{
					JOptionPane.showMessageDialog(null, "Parameter "+(i+1)+" is out of range "+bounds[i][0]+" -- "+bounds[i][1]);
					return null;
				}
			}
			catch(NumberFormatException nfe) {
				JOptionPane.showMessageDialog(null, "Invalid number entered for parameter "+(i+1)+" -- "+textfields[i].getText()); 
				return null;
			}
		}
	

		
		return new Model(params);
		
	}

}



final class UniformModelGenerator extends ModelGenerator
{
	private double[][] bounds;
	UniformModelGenerator(double[][] bounds)
	{
		this.bounds = bounds;
	}
	
	protected Model generateModel()
	{
		return new Model(bounds);
	}
}

