package rses.apps.sydneyenergy;


import java.awt.Color;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;


import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.DefaultButtonModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.event.MenuEvent;

import rses.Debug;
import rses.util.FileUtil;
import rses.util.Util;










public class MainWindow extends JFrame implements MouseListener, ActionListener
{
	private RegionalEnergyModel model;
	private HouseholdLocationModel locationmodel;
	private DataBaseBackedDisplayableModel databasemodel;
	private javax.swing.JPanel displaypanel = null;
	private DataStore database;
	private int[][] membership;
	private int[][] placemarks;
	private HashMap regionnames = null;
	
	
	
	private JMenu viewmenu;
	
	//the database table that is being viewed. i.e. the
	//one that backs the current display.
	private String viewtable;  
	
	public MainWindow(int width, int height, HouseholdLocationModel hh,
			RegionalEnergyModel model, DataStore database, int[][] membership, 
			int[][] placemarks, HashMap regionnames)
	{
		this.locationmodel = hh;
		this.regionnames = regionnames;
		this.database = database;
		this.model = model;
		this.membership = membership;
		this.placemarks = placemarks;
		this.databasemodel = new DataBaseBackedDisplayableModel(database, RegionalEnergyModel.databasename);
		this.setSize(width, height);

		Debug.println("membership matrix is "+membership.length+"x"+membership[0].length, Debug.INFO);
		
		//now add the menu and menuitems
		JMenuBar menubar = new JMenuBar();

		setupMainMenu(menubar);		
		setupViewMenu(menubar);		
		setupDisplayMenu(menubar);

		this.setJMenuBar(menubar);

		//now set up the display
		this.displaypanel = new ModelDisplayPanel(databasemodel, membership, placemarks);
		this.displaypanel.setSize(width, height);
		this.displaypanel.setMaximumSize(new Dimension((int) (membership[0].length*1.15), (int) (membership.length*1.15)));
		this.displaypanel.setMinimumSize(new Dimension(width, height));
		this.displaypanel.setPreferredSize(new Dimension(membership[0].length, membership.length));
		displaypanel.addMouseListener(this);
		JScrollPane jsp = new JScrollPane(displaypanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		//jsp.setPreferredSize(new java.awt.Dimension(width,height));
		//jsp.setMaximumSize(new java.awt.Dimension(width, height));
		this.getContentPane().add(jsp);
		
	}
	

	private void setupMainMenu(JMenuBar menubar)
	{
		//add all the menus and menuitems
		JMenu menu = new JMenu("Main");

		JMenuItem item = new JMenuItem("Recalculate");
		menu.add(item);
		item.addActionListener(this);
		
		item = new JMenuItem("Run...");
		menu.add(item);
		item.addActionListener(this);
		
		item = new JMenuItem("Save image to file...");
		menu.add(item);
		item.addActionListener(this);
		
		item = new JMenuItem("Dump data to file...");
		menu.add(item);
		item.addActionListener(this);

		menubar.add(menu);
	}

	private void setupDisplayMenu(JMenuBar menubar)
	{
		JMenu menu = new JMenu("Display");

		JMenuItem item = new JMenuItem("Set Colour scheme to Blue/Red");
		menu.add(item);
		item.addActionListener(this);		
		item = new JMenuItem("Set Colour scheme to Red/Blue");
		menu.add(item);
		item.addActionListener(this);
		item = new JMenuItem("Set Colour scheme to White/Dark-Gray");
		menu.add(item);
		item.addActionListener(this);
		item = new JMenuItem("Set Colour scheme to Dark-Gray/White");
		menu.add(item);
		item.addActionListener(this);

		
		//now allow user to choose continuous or discrete display,
		//and, if discrete, how to discretize the data.
		item = new JMenuItem("Set data display method...");
		menu.add(item);
		item.addActionListener(this);
		
		
		
		
		menubar.add(menu);
	}
	
	
	public static final int MAXMENUITEMS = 30;
	private void setupViewMenu(JMenuBar menubar)
	{
		ButtonGroup buttongroup = new ButtonGroup();
		
		//remove the old view menu if there is one already there
		boolean needtoadd = this.viewmenu == null;
		if(this.viewmenu != null)
			this.viewmenu.removeAll();
		else
			this.viewmenu = new JMenu("View");
		
		String[] tablenames = database.getRegionTableNames();
		
		Arrays.sort(tablenames); //sort alphabetically
		viewtable = RegionalEnergyModel.databasename;
		ViewMenuListener listener = new ViewMenuListener();
		
		HashMap submenus = new HashMap(); //sub-directory menus, with the key being their subdirectory

		for(int i =0; i < tablenames.length; i++)
		{
			JMenu curmenu = viewmenu;
			String name = tablenames[i];
			if(name.indexOf('/') != -1) //need a submenu
			{
				String submenuname = name.substring(0, name.indexOf('/'));
				if(!submenus.containsKey(submenuname)) {
					JMenu newsubmenu = new JMenu(submenuname);
					submenus.put(submenuname, newsubmenu);
					viewmenu.add(newsubmenu);
				}
				JMenu submenu = (JMenu) submenus.get(submenuname);
				curmenu = submenu;
			}
			
			JRadioButtonMenuItem ritem = new JRadioButtonMenuItem(name);
			ButtonModel buttonmodel = new DefaultButtonModel();
			if(tablenames[i].equals(viewtable)) {
				ritem = new JRadioButtonMenuItem(viewtable, true);
				buttongroup.add(ritem);
				buttongroup.setSelected(buttonmodel, true);
			}
			else {
				buttongroup.add(ritem);
				buttongroup.setSelected(buttonmodel, false);
			}
			ritem.addActionListener(listener);
			ritem.addChangeListener(listener);
			curmenu.add(ritem);
		}

		if(needtoadd)
			menubar.add(viewmenu);
		
		//need to redo layout
		this.invalidate();
		this.repaint();
	}
	
	
	public void actionPerformed(ActionEvent e) 
	{
		if(e.getActionCommand().equalsIgnoreCase("recalculate"))
		{
			//need to rerun the model on the current database
			//(which may have been changed by the user)
			this.model.recalculate();
			this.databasemodel.recalculate();
			this.repaint();
		}
		else if(e.getActionCommand().equalsIgnoreCase("run..."))
		{
			this.locationmodel.run(database); //run the model
			this.setupViewMenu(this.getJMenuBar()); //redo the view menu
		}
		else if(e.getActionCommand().equalsIgnoreCase("Save image to file..."))
		{
			BufferedImage img = this.databasemodel.getDisplay(membership, placemarks);
			FileUtil.saveImageToFile(img);
		}
		else if(e.getActionCommand().equalsIgnoreCase("Dump data to file..."))
		{
			HashMap data = this.databasemodel.getDisplayTable();
			String tablename = this.databasemodel.getUnderlyingTableName();
			File f = new File(tablename+".dump");
			PrintWriter writer = null;
			try {writer = new PrintWriter(new java.io.FileWriter(f));}
			catch(IOException ioe) { throw new RuntimeException(ioe); }
			Object[] keys = data.keySet().toArray();
			for(int i = 0; i < keys.length; i++) {
				String regionid = keys[i].toString();
				Object value = data.get(keys[i]);
				if(value != null)
					writer.println(regionid+"  "+value);
			}
			writer.close();
		}
		else if(e.getActionCommand().startsWith("Set Colour scheme to"))
		{
			if(e.getActionCommand().equalsIgnoreCase("Set Colour scheme to Blue/Red"))
				this.databasemodel.setColourRange(Color.BLUE, Color.RED);
			else if(e.getActionCommand().equalsIgnoreCase("Set Colour scheme to Red/Blue"))
				this.databasemodel.setColourRange(Color.RED, Color.BLUE);	
			else if(e.getActionCommand().equalsIgnoreCase("Set Colour scheme to White/Dark-Gray"))
				this.databasemodel.setColourRange(Color.WHITE, Color.DARK_GRAY);
			else if(e.getActionCommand().equalsIgnoreCase("Set Colour scheme to Dark-Gray/White"))
				this.databasemodel.setColourRange(Color.DARK_GRAY, Color.WHITE);
			this.repaint();
		}
		else if(e.getActionCommand().equalsIgnoreCase("Set data display method...")) {
			setDataDisplayMethod();
		}
		else
			throw new IllegalStateException("Unknown/Impossible action command");
	}
	
	
	private void setDataDisplayMethod()
	{
		String[] options = new String[] {"continuous", "kmeans (discrete)", "logkmeans (discrete)", "equalnumbers (discrete)", "equalranges (discrete)", "custom (discrete)"};
		int ret = JOptionPane.showOptionDialog(this, "Select a method of displaying the underlying data", "Choose Data Display Method", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
		if(ret == JOptionPane.CLOSED_OPTION)
			return; //do nothing

		if(ret == 0) { //continuous 
			this.databasemodel.setDisplayToContinuous();
			return;
		}
		if(ret == options.length-1) { //custom discrete
			String res = JOptionPane.showInputDialog(this, "Select separating values (space delimited)", "1 2 3");
			if(res == null || res.trim().length() == 0)
				return;
			String[] words = Util.getWords(res);
			double[] custombounds = new double[words.length];
			for(int i = 0; i < custombounds.length; i++)
				custombounds[i] = Double.parseDouble(words[i]);  
			this.databasemodel.setDisplayToCustomDiscrete(custombounds);
			return;
		}
		
		//get K, the number of centroids
		String res = JOptionPane.showInputDialog(this, "Select number of discrete categories", "3");
		int k = -1;
		try { k = Integer.parseInt(res); }
		catch(NumberFormatException nfe) {};
		if(k < 0) 
			return;
		
		
		if(ret == 1) //kmeans
			this.databasemodel.setDisplayToDiscrete(DisplayableModel.KMEANS, k);
		else if(ret == 2) //logkmeans
			this.databasemodel.setDisplayToDiscrete(DisplayableModel.LOGKMEANS, k);
		else if(ret == 3) //equal numbers
			this.databasemodel.setDisplayToDiscrete(DisplayableModel.EQUALNUMBERS, k);
		else if(ret == 4) //equal ranges
			this.databasemodel.setDisplayToDiscrete(DisplayableModel.EQUALRANGES, k);
		else throw new RuntimeException("unexpected option value of "+ret);
		
	}
	
	public void mouseEntered(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) 
	{
		int x = e.getX();
		int y = e.getY();
		//get the region that was clicked on
		int region = DisplayableModel.getRegion(membership, x, y);
		Debug.println("Region is "+region, Debug.INFO);
		if(region < 0)
			return;
		
		if(e.getButton() == MouseEvent.BUTTON1)
		{
			//now pop up a window that is a view of the database
			Integer regionint = new Integer(region);
			new RegionDataView(regionint, (String) regionnames.get(regionint), database);
		}
		else if(e.getButton() == MouseEvent.BUTTON2)
			Debug.println("Button 2 clicked @ "+x+" "+y, Debug.INFO);
		else
			Debug.println("Other Button clicked @ "+x+" "+y, Debug.INFO);
		
		

		
		
	}
	
	/*public void paint(java.awt.Graphics g)
	{
		super.paint(g);
		model.paintEnergyMap(displaypanel.getGraphics(), 1000, 1000);
	}*/
	
	
	class ViewMenuListener implements javax.swing.event.ChangeListener, ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			Debug.println("Action event happened: "+event.getActionCommand(),Debug.IMPORTANT);
			String viewtab = ((JRadioButtonMenuItem) event.getSource()).getActionCommand();
			MainWindow.this.databasemodel.setDisplayTable(viewtab);
			MainWindow.this.repaint();
		}
		
		public void stateChanged(javax.swing.event.ChangeEvent event) 
		{
			//view selected
			
		}
		
	}
	
}


