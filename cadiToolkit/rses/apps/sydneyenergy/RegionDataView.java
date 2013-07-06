package rses.apps.sydneyenergy;

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


/** View (and change) the variables associated with a
 *  particular region.
 * 
 * @author peterr
 *
 */
public class RegionDataView implements ActionListener
{
	private Integer region;
	private ArrayList labels = new ArrayList();
	private ArrayList fields = new ArrayList();
	private DataStore database;
	
	public RegionDataView(Integer region, String regionname, DataStore database)
	{
		this.region = region;
		this.database = database;
		String key = region.toString();
		
		//go through and add entries for each variable
		JFrame window = new JFrame();
		window.setSize(500,300);
		
		
		JPanel content = new JPanel();
		content.setSize(500,300);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		
		
		JScrollPane pane = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		window.getContentPane().add(pane, BorderLayout.CENTER);

		//add a non-editable field for the region id
		JLabel label = new JLabel("Region ID");
		JLabel valfield = new JLabel(key);
		JPanel idpanel = new JPanel();
		idpanel.add(label);
		idpanel.add(valfield);
		content.add(idpanel);
		
		//add a non-editable field for the region name
		label = new JLabel("Region Name");
		valfield = new JLabel(regionname);
		idpanel = new JPanel();
		idpanel.add(label);
		idpanel.add(valfield);
		content.add(idpanel);
		

		//add the rest of the fields (from database)
		String[] tablenames = database.getRegionTableNames();
		Arrays.sort(tablenames);
		for(int i =0; i < tablenames.length; i++)
		{
			labels.add(tablenames[i]);
			double v = Double.NaN;
			try { v = database.lookup(tablenames[i], key); }
			catch(NoSuchEntryException nsee) {} 
			fields.add(addEntry(content, tablenames[i], v));
		}
		
		JButton button = new JButton("update");
		button.addActionListener(this);
		content.add(button);
		
		//show the window and wait for it to close
		//the rest of the processing is handled in the
		//WindowEventHandler section
		window.setVisible(true);
		
	}
	
	
	private JTextField addEntry(JPanel frame, String key, double val)
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
		String key = ""+region;
		for(int i =0; i < labels.size(); i++)
		{
			String tabname = (String) labels.get(i);
			String valstr = ((JTextField) fields.get(i)).getText();
			Double val = new Double(valstr);
			database.replaceValue(tabname, key, val, false);
		}
	}
	
	
}