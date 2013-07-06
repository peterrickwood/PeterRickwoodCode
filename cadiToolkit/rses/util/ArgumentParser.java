package rses.util;

import java.util.*;
import java.io.*;


/** Just a utility class to help make parsing command line
 * arguments a little bit easier.
 *
 * @author	 Peter Rickwood
 */


public class ArgumentParser
{
	private String[] options;
	private String[] values;
	private int num_args;

	/**
	 *
	 * @param	arguments
	 *<p>
	 * 		Some command line arguments that we wish to
	 *		parse. Currently, the argument parser
	 *		considers only arguments of the form
	 *		{flag [= value]}*
	 *<p>
	 *		So 
	 *		<br><br>
	 *		"-f1 -f2 -f3 = v1 -f4 -f5 = v2 -f6 = v3"
	 *		<br><br>
	 *		and 
	 *		<br><br>
	 *		"option1 = value1 option2 option3 = value3"
	 * 		<br><br>
	 *		are handled, but<br><br>
	 *		"-abcd" (where a,b,c,d are all separate options)
	 *		<br><br>
	 *		is not.
	 *		<p>
	 *		For any argument array of the handled type
	 *		(ie. opt [= val] opt [=val] ....), all
	 *		the options can be retrieved by 
	 *		calling getOptions(), 
	 *		and the specified
	 * 		value (if any) for each option can be
	 *		obtained by calling getValues().
	 *
	 * <p>
	 * @exception InstantiationException If the arguments
	 * are not of the type specified above.
	 * <p>
	 * @see soundstat.util.ArgumentParser#getOptions
	 * @see soundstat.util.ArgumentParser#getValues
	 */
	public ArgumentParser(String[] arguments)
	{
		int i = 0;
		int count = 0;
		String[] args = splitEquals(arguments);
		boolean done = (args.length == 0);
		options = new String[args.length];
		values = new String[args.length];
		
		while(!done)
		{
			options[count] = args[i++];
			if(i == args.length)
				done = true;
			else if(args[i].equals("="))
			{
				i++; //skip '='
				if(i == args.length)
					values[count] = null;
				else
					values[count] = args[i++];
			}
			count++;
			if(i == args.length)
				done = true;
		}
		num_args = count;
	}


	/** Was the option <code>opt</code> one of the
	 *  specified options?
	 * 
	 * @param opt
	 * @return
	 */
	public boolean isOption(String opt)
	{
		int ind = getIndex(opt, options);
		if(ind < 0)
			return false;
		return true;
	}
	
	/** What value was given to option <code>opt</code>?
	 *  null is returned if no value was given, or if 
	 *  the options was never specified.
	 * 
	 * @param opt
	 * @return
	 */
	public String getValue(String opt)
	{
		int ind = getIndex(opt, options);
		if(ind < 0)
			return null;
		return values[ind];
	}
	
	
	
	private static int getIndex(String str, String[] array)
	{
		for(int i = 0; i < array.length; i++)
		{
			if(array[i] != null && array[i].equals(str))
				return i;
		}
		return -1;
	}
	
	
	/** @return	The entire list of options */
	public String[] getOptions()
	{	return options; }
	
	/** @return	The entire list of values (one for each
	 *		option). If an option had no value specified,
	 *		the value for that option is 'null'
	 */
	public String[] getValues()
	{	return values; }

	/** @return 	The number of options parsed.
	 */
	public int getNumArgs()
	{	return num_args; }
	

	public void print(PrintStream pstream)
	{
		pstream.println("there are "+num_args+" options");
		for(int i = 0; i < num_args; i++)
			pstream.println(options[i] + "     " + values[i]);
	}
	

	private static String[] splitEquals(String[] strings)
	{
		Vector res = new Vector();
		int index;
		for(int i = 0; i < strings.length; i++)
		{
			index = strings[i].indexOf('=');

			if(index < 0)
				res.addElement(strings[i]);
			else if(strings[i].equals("="))
				res.addElement("=");
			else if(index == 0)
			{
				res.addElement("=");
				res.addElement(strings[i].substring(1, strings[i].length()));
			}
			else if(index == strings[i].length() -1)
			{
				res.addElement(strings[i].substring(0, index));
				res.addElement("=");
			}
			else
			{
				String var = strings[i].substring(0, index);
				res.addElement(var); //add var
				res.addElement("=");
				String val = strings[i].substring(index+1, 
					strings[i].length());
				res.addElement(val); //add value
			}
		}
		String[] result = new String[res.size()];
		for(int i = 0; i < result.length; i++)
			result[i] = (String) res.elementAt(i);
		return result;
	}


	/* for testing purposes only */
	public static void main(String[] args) throws Exception
	{
		ArgumentParser ap = new ArgumentParser(args);
		ap.print(System.out);
	}

}





