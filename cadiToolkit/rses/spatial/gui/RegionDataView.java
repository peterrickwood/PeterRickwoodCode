package rses.spatial.gui;

import java.awt.BorderLayout;
import java.awt.Event;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import rses.Debug;
import rses.spatial.util.DataStore;
import rses.spatial.util.GeoDataTable;


/** View (and change) the variables associated with a
 *  particular region.
 * 
 * @author peterr
 *
 */
public class RegionDataView implements ActionListener
{
	//the name of the membership layer that defines the membership that defines this
	//region
	private String membershipLayerName;
	
	private String region_name; //the name of the region this we are displaying the data for
	
	private ArrayList labels = new ArrayList();
	private ArrayList fields = new ArrayList();
	private DataStore database;
	
	public RegionDataView(String layername, String regionname, DataStore database)
	{
		this.membershipLayerName = layername;
		this.database = database;
		this.region_name = regionname;
		
		//go through and add entries for each variable
		JFrame window = new JFrame();
		window.setSize(500,450);
		
		
		JPanel content = new JPanel();
		content.setSize(500,450);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		
		
		JScrollPane pane = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		window.getContentPane().add(pane, BorderLayout.CENTER);

		//add a non-editable field for the region name
		JLabel label1 = new JLabel("Region name");
		JLabel valfield1 = new JLabel(regionname);
		JPanel idpanel1 = new JPanel();
		idpanel1.add(label1);
		idpanel1.add(valfield1);
		content.add(idpanel1);
				

		//add the rest of the fields (from database)
		java.util.HashMap<String, GeoDataTable> tables = database.getTablesForLayer(this.membershipLayerName);
		String[] tablenames = new String[tables.size()];
		java.util.Iterator<String> keyit = tables.keySet().iterator();
		int tabcount = 0;
		while(keyit.hasNext()) 
			tablenames[tabcount++] = keyit.next();
		
		Arrays.sort(tablenames);
		for(int i =0; i < tablenames.length; i++)
		{
			labels.add(tablenames[i]);
			Object v = null;
			try { v = database.lookupArbitrary(this.membershipLayerName, tablenames[i], regionname); }
			catch(NoSuchEntryException nsee) {} 
			fields.add(addEntry(content, tablenames[i], ""+v));
		}
		
		JButton button = new JButton("update");
		button.addActionListener(this);
		content.add(button);
		
		//show the window and wait for it to close
		//the rest of the processing is handled in the
		//WindowEventHandler section
		window.setVisible(true);
		
	}
	
	
	private JTextField addEntry(JPanel frame, String key, String val)
	{
		Debug.println("Adding entry for table "+key, Debug.INFO);
		JLabel label = new JLabel(key);
		JTextField valfield = new JTextField(""+val);
		JPanel panel = new JPanel();
		panel.add(label);
		panel.add(valfield);
		frame.add(panel);
		return valfield; 
	}
	
	
	
	
	public void actionPerformed(ActionEvent e)
	{
		Debug.println("Updating datastore...", Debug.INFO);
		for(int i =0; i < labels.size(); i++)
		{
			String tabname = (String) labels.get(i);
			String valstr = ((JTextField) fields.get(i)).getText();
			
			//the value could be a string or a double. Try and make sure that the type does not change
			Object oldval = database.lookupArbitrary(this.membershipLayerName, tabname, region_name, false);
			Object newval = null;
			
			if(oldval == null) //if there was no old value, try to get a double and failing that, use the string 
			{
				try { newval = new Double(valstr); } 
				catch(NumberFormatException nfe) { newval = valstr; }
			}
			//old value was a double, so make sure new one is too.
			else if(oldval instanceof Double) 
			{
				if(valstr.length() != 0 && !valstr.equalsIgnoreCase("null"))
					newval = new Double(valstr);
			}
			else //old value was a string or something else. Just replace it with a string
				newval = valstr;

			Debug.println("oldval: "+oldval+"      newval: "+newval, Debug.INFO);
			
			//first check to see if the user wants to replace an existing value with a null one
			if(oldval != null && (valstr.length() == 0 || valstr.equalsIgnoreCase("null")))
			{
				//user may purposely want to get rid of old value, so do that
				Debug.println("Removing mapping for key "+region_name+" in table "+tabname, Debug.INFO);
				database.getTable(this.membershipLayerName, tabname).removeMapping(region_name);
				continue;
			}
			
			//make sure we dont add a null entry if there was no entry to begin with
			//So check we have something sensible for the new value
			if(newval instanceof String && oldval == null)
				if(((String) newval).length() == 0  || ((String) newval).equalsIgnoreCase("null"))
					continue; //dont add null mappings
			
			Debug.println("In table "+tabname+" for key "+region_name+", replacing "+oldval+" with "+newval, Debug.INFO);
			database.replaceValue(this.membershipLayerName, tabname, region_name, newval, false);
			
		}
	}
	
	
}
