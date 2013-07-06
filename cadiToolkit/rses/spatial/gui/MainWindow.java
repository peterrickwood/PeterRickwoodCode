package rses.spatial.gui;


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
import java.util.List;
import java.util.Map;
import java.util.Iterator;


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
import rses.PlatformInfo;
import rses.spatial.util.DataStore;
import rses.spatial.util.GeoDataTable;
import rses.util.FileUtil;
import rses.util.Util;










public class MainWindow extends JFrame implements MouseListener, ActionListener
{
	private DataBaseBackedDisplayableModel databasemodel;
	private ModelDisplayPanel displaypanel = null;
	private DataStore database;
	private Map<String, int[][]> memberships;
	
	
	private JMenu mainmenu;
	private JMenu displaymenu;
	private JMenu zoommenu;
	
	private JMenu viewmenu;
	private String viewtable;
	
	private JMenu layermenu; //the region that has been selected
	private String viewlayer;
	
	
	private Map<JRadioButtonMenuItem, String> viewmenubuttontocanonical = new HashMap<JRadioButtonMenuItem, String>();
	
	public MainWindow(int width, int height, DataStore database, List<String> layernames, Map<String, int[][]> mbrships)
	{
		//pick first layer with at least one table;
		String layername = layernames.get(0);
		String tablename = null;
		HashMap<String, GeoDataTable> tables = database.getTablesForLayer(layername);
		if(tables.size() > 0) 
			tablename = tables.keySet().iterator().next();
		
		this.initialize(width, height, database, mbrships, layername, tablename);
	}
	
	

	
	
	private void initialize(int width, int height, DataStore database, Map<String, int[][]> mbrships, String defaultlayer, String defaulttable)
	{
		this.getContentPane().removeAll();
		this.viewmenu = null;
		this.layermenu = null;
		
		this.database = database;		
		this.memberships = mbrships;
		

		if(defaultlayer != null)
			this.databasemodel = new DataBaseBackedDisplayableModel(database, defaultlayer, defaulttable);
		else
			this.databasemodel = null;
		
		this.setSize(width, height);

		//now add the menu and menuitems
		JMenuBar menubar = new JMenuBar();

		setupMainMenu(menubar);	
		setupLayerMenu(menubar, defaultlayer);
		setupViewMenu_new(menubar, defaulttable);		
		setupDisplayMenu(menubar);
		setupZoomMenu(menubar);

		this.setJMenuBar(menubar);

		
		//now set up the display
		int[][] membership = memberships.get(defaultlayer);
		setupDisplayPanel(width, height, membership, 0);
		
		//jsp.setPreferredSize(new java.awt.Dimension(width,height));
		//jsp.setMaximumSize(new java.awt.Dimension(width, height));
		
		
	}
	
	private void setupDisplayPanel(int width, int height, int[][] membership, int zoom)
	{
		if(this.displaypanel != null)
			this.getContentPane().removeAll();
		
		this.displaypanel = new ModelDisplayPanel(databasemodel, membership, null, zoom);
		this.displaypanel.setSize(width, height);
		this.displaypanel.setMaximumSize(new Dimension((int) (membership[0].length*(zoom+1)*1.15), (int) (membership.length*(zoom+1)*1.15)));
		this.displaypanel.setMinimumSize(new Dimension(width, height));
		this.displaypanel.setPreferredSize(new Dimension(membership[0].length*(zoom+1), membership.length*(zoom+1)));
		displaypanel.addMouseListener(this);
		JScrollPane jsp = new JScrollPane(displaypanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		this.getContentPane().add(jsp);
	}
	

	private void setupMainMenu(JMenuBar menubar)
	{
		//add all the menus and menuitems
		mainmenu = new JMenu("Main");

		JMenuItem item = new JMenuItem("Recalculate");
		mainmenu.add(item);
		item.addActionListener(this);
		
		item = new JMenuItem("Save image to file...");
		mainmenu.add(item);
		item.addActionListener(this);
		
		item = new JMenuItem("Dump data to file...");
		mainmenu.add(item);
		item.addActionListener(this);
		
		menubar.add(mainmenu);
	}

	private void setupDisplayMenu(JMenuBar menubar)
	{
		displaymenu = new JMenu("Display");

		JMenuItem item = new JMenuItem("Set Colour scheme to Blue/Red");
		displaymenu.add(item);
		item.addActionListener(this);		
		item = new JMenuItem("Set Colour scheme to Red/Blue");
		displaymenu.add(item);
		item.addActionListener(this);
		item = new JMenuItem("Set Colour scheme to White/Dark-Gray");
		displaymenu.add(item);
		item.addActionListener(this);
		item = new JMenuItem("Set Colour scheme to Dark-Gray/White");
		displaymenu.add(item);
		item.addActionListener(this);

		
		//now allow user to choose continuous or discrete display,
		//and, if discrete, how to discretize the data.
		item = new JMenuItem("Set data display method...");
		displaymenu.add(item);
		item.addActionListener(this);
		
		menubar.add(displaymenu);
	}
	
	
	private void setupZoomMenu(JMenuBar menubar)
	{
		zoommenu = new JMenu("Zoom");

		JMenuItem item = new JMenuItem("Zoom In");
		zoommenu.add(item);
		item.addActionListener(this);		
		item = new JMenuItem("Zoom Out");
		zoommenu.add(item);
		item.addActionListener(this);
		
		menubar.add(zoommenu);
	}
	

	
	private rses.math.GraphNode makeDirectoryTree(String[] paths)
	{
		String[] tablenames = new String[paths.length];
		for(int i = 0; i < tablenames.length; i++) tablenames[i] = paths[i];
		Arrays.sort(tablenames); //sort alphabetically
		
		rses.math.GraphNode root = new rses.math.GraphNode("");
		
		for(String path : paths)
		{
			insert(path, root);
		}	
		
		//remove all the leading slashes from everything
		Map<String, rses.math.GraphNode> graph = rses.math.GraphUtil.getGraph(root);
		for(rses.math.GraphNode n : graph.values()) 
		{
			if(n.getId().startsWith("/"))
				n.setId(n.getId().substring(1));
			else if(n != root)
				throw new RuntimeException("Node id doesnt start with a slash??? Should be impossible..: "+n.getId());
		}
		
		
		return root;
	}
	
	
	
	private void insert(String path, rses.math.GraphNode root)
	{
		String[] bits = path.split("/");
		if(bits.length == 1) 
			root.addEdge(new rses.math.GraphNode(root.getId()+"/"+bits[0]));
		else
		{
			//two cases, either the current directory is attached to its parent, or its not.
			//lets work that out first
			rses.math.GraphNode subdir = null;
			for(rses.math.GraphEdge e : root.getEdges()) {
				if(e.leadsto.getId().equals(root.getId()+"/"+bits[0])) {
					subdir = e.leadsto;
					break;
				}
			}
			
			if(subdir == null) { //directory doesnt exist, so we create it
				subdir = new rses.math.GraphNode(root.getId()+"/"+bits[0]);
				root.addEdge(subdir);
			}
			
			//ok, it now either existed alredy, or we created it, so now lets recurse
			insert(Util.join("/", Arrays.copyOfRange(bits, 1, bits.length)), subdir);

		}
		
	}
	
	
	
	
	
	private void createDirectoryMenu(JMenu curmenu, 
			rses.math.GraphNode dirtree, ViewMenuListener listener, 
			ButtonGroup buttongroup, String curtable)
	{
		Debug.println("creating view menu "+dirtree.getId(), Debug.EXTRA_INFO);
		
		//for each child
		for(rses.math.GraphEdge e : dirtree.getEdges())
		{
			rses.math.GraphNode child = e.leadsto;
			if(child.getEdges().size() == 0) //it is a leaf node, so a file
			{
				Debug.println("Adding "+child.getId()+" to view menu", Debug.EXTRA_INFO);
				String localname = child.getId().split("/")[child.getId().split("/").length-1];
				JRadioButtonMenuItem ritem = new JRadioButtonMenuItem(localname);
					
				//if the button we are adding is the currently selected one we need to mark it selected
				if(curtable != null && child.getId().equals(curtable)) {
					ritem = new JRadioButtonMenuItem(localname, true);
					Debug.println("Adding checked radiobutton "+curtable, Debug.EXTRA_INFO);
				}
				
				
				this.viewmenubuttontocanonical.put(ritem, child.getId());
				buttongroup.add(ritem);
				
				
				ritem.addActionListener(listener);
				ritem.addChangeListener(listener);
				curmenu.add(ritem);
			}
			else //it is not a leaf, so we create a submeu and continue 
			{
				String localname = child.getId().split("/")[child.getId().split("/").length-1];
				//create the submenu and do DFS
				JMenu newsubmenu = new JMenu(localname);
				curmenu.add(newsubmenu);
				createDirectoryMenu(newsubmenu, child, listener, buttongroup, curtable);
			}
		}		
	}

	
	
	private void setupViewMenu_new(JMenuBar menubar, String table)
	{		
		Debug.println("Setting up view menu with table "+table, Debug.INFO);
		this.viewtable = table;
		
		
		//remove the old view menu if there is one already there
		boolean needtoadd = this.viewmenu == null;
		if(this.viewmenu != null)
			this.viewmenu.removeAll();
		else
			this.viewmenu = new JMenu("View");
		
		Map<String, GeoDataTable> tables = database.getTablesForLayer(databasemodel.getUnderlyingLayerName());
		String[] tablenames = new String[tables.size()];
		Iterator<String> nameit = tables.keySet().iterator();
		for(int i = 0; i < tablenames.length; i++)
			tablenames[i] = nameit.next();
		
		Debug.println("Building directory tree from table names", Debug.INFO);
		rses.math.GraphNode dirtree = makeDirectoryTree(tablenames);
		Debug.println("Directory tree built", Debug.INFO);
		if(Debug.equalOrMoreVerbose(Debug.INFO)) rses.math.GraphUtil.printGraph(dirtree, System.out);
		
		
		ViewMenuListener listener = new ViewMenuListener();
		ButtonGroup buttongroup = new ButtonGroup();
		Debug.println("Laying out view menu and submenus", Debug.INFO);
		createDirectoryMenu(viewmenu, dirtree, listener, buttongroup, table);
		Debug.println("Finished laying out view menu and submenus", Debug.INFO);
		Debug.println("Default table is "+table, Debug.EXTRA_INFO);
		

		if(needtoadd)
			menubar.add(viewmenu);
		
		//need to redo layout
		this.invalidate();
		this.repaint();
	}

	
	
	private void setupViewMenu(JMenuBar menubar, String table)
	{		

		Debug.println("Setting up view menu with table "+table, Debug.INFO);
		this.viewtable = table;
		
		ButtonGroup buttongroup = new ButtonGroup();
		
		//remove the old view menu if there is one already there
		boolean needtoadd = this.viewmenu == null;
		if(this.viewmenu != null)
			this.viewmenu.removeAll();
		else
			this.viewmenu = new JMenu("View");
		
		Map<String, GeoDataTable> tables = database.getTablesForLayer(databasemodel.getUnderlyingLayerName());
		String[] tablenames = new String[tables.size()];
		Iterator<String> nameit = tables.keySet().iterator();
		for(int i = 0; i < tablenames.length; i++)
			tablenames[i] = nameit.next();
		
		Arrays.sort(tablenames); //sort alphabetically
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
						
			buttongroup.add(ritem);
			if(table != null && name.equals(table))  {
				ritem = new JRadioButtonMenuItem(table, true);
				buttongroup.add(ritem);
			}
			else
				buttongroup.add(ritem);

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

	
	
	private void setupLayerMenu(JMenuBar menubar, String layer)
	{
		Debug.println("Setting up layer menu with layer "+layer, Debug.INFO);

		this.viewlayer = layer;
		
		ButtonGroup buttongroup = new ButtonGroup();
		
		//remove the old layer menu if there is one already there
		boolean needtoadd = this.layermenu == null;
		if(this.layermenu != null)
			this.layermenu.removeAll();
		else
			this.layermenu = new JMenu("Region");
		
		List<String> layernameslist = database.getVectorLayers();
		String[] layernames = new String[layernameslist.size()];
		int namec = 0;
		Iterator<String> nameit = layernameslist.iterator();
		while(nameit.hasNext())
			layernames[namec++] = nameit.next();
				
		Arrays.sort(layernames); //sort alphabetically
		LayerMenuListener listener = new LayerMenuListener();
		
		for(int i =0; i < layernames.length; i++)
		{
			JMenu curmenu = layermenu;
			String name = layernames[i];
			
			JRadioButtonMenuItem ritem = new JRadioButtonMenuItem(name);
			
			if(layer != null && name.equals(layer)) {
				ritem = new JRadioButtonMenuItem(layer, true);
				buttongroup.add(ritem);
			}
			else {
				buttongroup.add(ritem);
			}
			
			ritem.addActionListener(listener);
			ritem.addChangeListener(listener);
			curmenu.add(ritem);
		}

		if(needtoadd)
			menubar.add(layermenu);
		
		//need to redo layout
		this.invalidate();
		this.repaint();
	}

	
	public void addRunnableComponent(Runnable component, String name)
	{
		
		class OneShotActionListener implements ActionListener {
			String nm;
			Runnable torun;
			public OneShotActionListener(String actionname, Runnable torun) { this.nm = actionname; this.torun = torun; }
			public void actionPerformed(ActionEvent event) 
			{
				if(event.getActionCommand().equals(nm))
					torun.run(); 
				else
					MainWindow.this.actionPerformed(event); //pass on event to main listener
			}
		}

		String menuitemname = "Run: "+name;
		JMenuItem item = new JMenuItem(menuitemname);
		//TODO need to check that there isn't already a component with this name
		item.addActionListener(new OneShotActionListener(menuitemname, component));
		mainmenu.add(item);

	}
	
	
	
	public int[][] getCurrentMembershipMap()
	{
		return memberships.get(this.viewlayer);
	}
	
	public String getCurrentMembershipLayerName()
	{
		return this.viewlayer;
	}
	
	public rses.spatial.GISLayer getCurrentMembershipLayer()
	{
		return database.getVectorLayer(this.viewlayer);
	}
	
	public void actionPerformed(ActionEvent e) 
	{
		if(e.getActionCommand().equalsIgnoreCase("recalculate"))
		{
			//need to rerun the model on the current database
			//(which may have been changed by the user)
			this.databasemodel.recalculate();
			this.repaint();
		}
		else if(e.getActionCommand().equalsIgnoreCase("zoom in"))
		{
			int curzoom = this.displaypanel.getZoomFact();
			int zoom = curzoom + 1;
			int[][] membership = memberships.get(this.viewlayer);
			this.setupDisplayPanel(displaypanel.getWidth(), displaypanel.getHeight(), membership, zoom);
			this.databasemodel.recalculate();
			this.paintAll(this.getGraphics());
		}
		else if(e.getActionCommand().equalsIgnoreCase("zoom out"))
		{
			int curzoom = this.displaypanel.getZoomFact();
			if(curzoom == 0)
				return; //cant zoom out any more
			
			int zoom = curzoom - 1;
			int[][] membership = memberships.get(this.viewlayer);
			this.setupDisplayPanel(displaypanel.getWidth(), displaypanel.getHeight(), membership, zoom);
			this.databasemodel.recalculate();
			this.paintAll(this.getGraphics());
		}
		else if(e.getActionCommand().equalsIgnoreCase("Save image to file..."))
		{
			BufferedImage img = this.databasemodel.getDisplay(memberships.get(databasemodel.getUnderlyingLayerName()), null, 0);
			FileUtil.saveImageToFile(img);
		}
		else if(e.getActionCommand().equalsIgnoreCase("Dump data to file..."))
		{
			HashMap data = this.databasemodel.getDisplayTable();
			String tablename = this.databasemodel.getUnderlyingTableName();
			if(tablename.indexOf(PlatformInfo.sep) >= 0) {
				String[] tablenamebits = Util.getWords(tablename, PlatformInfo.sep);
				tablename = tablenamebits[tablenamebits.length-1];
			}
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
		int zoom = displaypanel.getZoomFact();
		
		//get the region that was clicked on
		String[] catnames = database.getVectorLayer(databasemodel.getUnderlyingLayerName()).getCategoryNames();
		int[][] mbr = memberships.get(databasemodel.getUnderlyingLayerName());
		int region = DisplayableModel.getRegion(mbr, x/(zoom+1), y/(zoom+1));
		if(region < 0)
			return;
		Debug.println("Region is "+region+" with name "+catnames[region], Debug.INFO);
		
		if(e.getButton() == MouseEvent.BUTTON1)
		{
			//now pop up a window that is a view of the database
			Integer regionint = new Integer(region);
			new RegionDataView(databasemodel.getUnderlyingLayerName(), catnames[region], database);
		}
		else if(e.getButton() == MouseEvent.BUTTON2)
			Debug.println("Button 2 clicked @ "+x+" "+y, Debug.INFO);
		else
			Debug.println("Other Button clicked @ "+x+" "+y, Debug.INFO);
		
		

		
		
	}
	
	
	
	class ViewMenuListener implements javax.swing.event.ChangeListener, ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			Debug.println("Action event happened: "+event.getActionCommand(),Debug.EXTRA_INFO);
			
			String viewlay = MainWindow.this.viewlayer;
			JRadioButtonMenuItem button = (JRadioButtonMenuItem) event.getSource();
			String viewtab = MainWindow.this.viewmenubuttontocanonical.get(button);
			Debug.println("View Table selected is "+viewtab, Debug.EXTRA_INFO);
			MainWindow.this.viewtable = viewtab;
			
			MainWindow.this.databasemodel.setDisplayTable(viewlay, viewtab);
			MainWindow.this.repaint();
		}
		
		public void stateChanged(javax.swing.event.ChangeEvent event) 
		{
			//view selected
		}
		
	}

	
	
	
	class LayerMenuListener implements javax.swing.event.ChangeListener, ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			Debug.println("Action event happened: "+event.getActionCommand(),Debug.EXTRA_INFO);
			
			String viewlay = ((JRadioButtonMenuItem) event.getSource()).getActionCommand();
			Debug.println("selected view layer is "+viewlay, Debug.INFO);
			
			MainWindow.this.viewlayer = viewlay;

			//now, we need to change the view table menu, because the old tables are no longer valid
			HashMap<String, GeoDataTable> tables = MainWindow.this.database.getTablesForLayer(viewlay);
			if(tables.size() > 0)
				MainWindow.this.viewtable = tables.keySet().iterator().next();
			else
				MainWindow.this.viewtable = null;
			
			Debug.println("Set view table to "+MainWindow.this.viewtable, Debug.INFO);
			
			int w = MainWindow.this.getWidth();
			int h = MainWindow.this.getHeight();
			MainWindow.this.setVisible(false);
			MainWindow.this.initialize(w, h, database, MainWindow.this.memberships, viewlay, MainWindow.this.viewtable);
			MainWindow.this.setVisible(true);
			
			
			MainWindow.this.databasemodel.setDisplayTable(viewlay, MainWindow.this.viewtable);
			MainWindow.this.repaint();
		}
		
		public void stateChanged(javax.swing.event.ChangeEvent event) 
		{
			//view selected
			
		}
		
	}

	
}


