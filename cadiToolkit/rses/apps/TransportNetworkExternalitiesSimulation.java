package rses.apps;

import rses.Debug;
import rses.math.*;
import rses.util.Util;
import rses.visualisation.ColourGradient;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class TransportNetworkExternalitiesSimulation
{
	public static int[] getxy(int nodea, int citysize)
	{
		int xa = nodea % citysize;
		int ya = nodea / citysize;
		return new int[] {xa, ya}; 
	}
	
	public static double getDistance(int nodea, int nodeb, int citysize)
	{
		//if(citysize % 2 != 1)
		//	throw new RuntimeException("Citysize must be odd!");
		
		int xa = nodea % citysize;
		int ya = nodea / citysize;
		int xb = nodeb % citysize;
		int yb = nodeb / citysize;
		
		double dist = Math.sqrt((xa-xb)*(xa-xb)+(ya-yb)*(ya-yb));
		return dist;		
	}
	
	/*public static double getDistanceFromCBD(int nodeid, int citysize)
	{
		return getDistance(nodeid, (citysize*citysize)/2, citysize);
	}*/
	
	
	
	
	public static double getValueOfNetwork(HashMap graph, 
			int citysize, double cbdscaleparam, double tripsum,
			double popsum, double[][] tripcounts, double[] popden,
			double[] modeshare_outparam)
	{
		//the value of the network is the sum of the value from
		//all destinations to all destinations.
		//
		//value from a->b is given by a gravity model like formula
		//
		// Pa . Pb / dist(Pa->Pb)
		//
		//Where Pa and Pb are themselves just determined
		//by distance from the cbd
		
		Object[] keys = graph.keySet().toArray();

		double pubsum = 0.0;
		double privsum = 0.0;
		
		double publiconlysum = 0.0; //assume everyone uses public transport
		double popcovered = 0.0;
		
		
		//for each origin node
		for(int i = 0; i < keys.length; i++)
		{
			//get the shortest path tree from that node
			int startnodeid = ((Integer) keys[i]).intValue();
			GraphNode pathtree = GraphUtil.getShortestPathTree(startnodeid, graph);
			
			//now traverse the shortest path tree and get the shortest
			//path to each node
			java.util.Map<Integer, Double> distances = GraphUtil.getPathLengthsFromPathTree(pathtree);
			
			
			//double distcbd_a = getDistanceFromCBD(startnodeid, citysize);
			//double cityradius = Math.sqrt(2*Math.pow(citysize/2, 2.0));
			//double Pa = 1.0/(distcbd_a/cityradius + cbdscaleparam);
			double Pa = popden[startnodeid];
			popcovered += Pa;
			
			//and for each node we can reach, calculate the utility
			double ptcount = 0.0;
			double carcount = 0.0;
			Iterator keyit = distances.keySet().iterator();
			while(keyit.hasNext())
			{
				Integer key = (Integer) keyit.next();
				if(key.intValue() == startnodeid)
					continue; //dont count travel to/from start node
				Debug.println("Value of "+startnodeid+" -> "+key.intValue(), Debug.EXTRA_INFO);
				int cbd = Util.getMaxIndex(popden);
				double distcbd = getDistance(startnodeid, cbd, citysize);
				int nlinks = ((GraphNode) graph.get(new Integer(startnodeid))).getEdges().size();
				
				double basedist = distances.get(key);
				//double dist = basedist + (1+(2*distcbd/(citysize*nlinks)))*Math.log(1+basedist);
				//double dist = basedist + Math.log(basedist) + 1.0/nlinks + 4*distcbd/citysize;
				//double dist = basedist + 2.0*Util.log_2(1+basedist)/nlinks + 2*distcbd/citysize;
				//double dist = Math.pow(basedist, 1.333)+0.5; //tmpfl2
				double dist = basedist*(1+2.0/nlinks);
				
				//now adjust for mode split, because only some people
				//will use public transport
				double privtt = getDistance(startnodeid, key.intValue(),citysize);
				double pubtt = dist;
				double ratio = pubtt/privtt;
				Debug.println("pubtt is "+pubtt+" from a network distance of "+basedist, Debug.EXTRA_INFO);
				Debug.println("priv route length is "+privtt, Debug.EXTRA_INFO);
				Debug.println("TT ratio is "+ratio+" with tripsum of "+tripcounts[startnodeid][key.intValue()], Debug.EXTRA_INFO);
				//work out mode split
				double z = 0.5312*.835 + -.486*7.887 -.853*7.898 + 9.606 + .423*ratio;
				double modesplit = 1/(1+Math.exp(-z));
				double ptmodeshare = 0.5*(1-modesplit); //adjust because peak hour mode split is less than off-peak.
				
				//work out mode split *from* this node
				ptcount += (ptmodeshare)*tripcounts[startnodeid][key.intValue()];
				carcount += (1-ptmodeshare)*tripcounts[startnodeid][key.intValue()];
				
				pubsum += (ptmodeshare)*tripcounts[startnodeid][key.intValue()];
				privsum += (1-ptmodeshare)*tripcounts[startnodeid][key.intValue()];
				publiconlysum += tripcounts[startnodeid][key.intValue()];
				Debug.println("Private car mode split on network nodes for this ratio is "+(1-ptmodeshare), Debug.EXTRA_INFO);
			}	
			
			modeshare_outparam[startnodeid] = ptcount/(ptcount+carcount);
		}	
		
		Debug.println("Gravity-weighted pub/priv ratio for network is "+pubsum/(pubsum+privsum), Debug.INFO);
		Debug.println("Gravity-weighted pub/priv ratio for whole city is "+pubsum/(tripsum), Debug.INFO);
		Debug.println("Proportion of city population/jobs covered is "+popcovered/popsum, Debug.INFO);
		Debug.println("Unweighted city population/jobs covered is "+publiconlysum/tripsum, Debug.INFO);
		Debug.println("ALL (network/city/propofpopulation/city_pubtransalwayspreferred): "+pubsum/(pubsum+privsum)+" "+pubsum/(tripsum)+" "+popcovered/popsum+" "+publiconlysum/tripsum, Debug.INFO);
		
		return pubsum/tripsum;
		//return popcovered;
	}
	
	
	
	
	//the user can state which links are not allowed
	public static HashMap disallowedlinks = new HashMap();
	
	public static double[] getPopdensity(int citysize, double scaleparam, String file) throws Exception
	{
		double[] result = new double[citysize*citysize];
		if(file == null) 
		{
			double cityradius = Math.sqrt(2*Math.pow(citysize/2, 2.0));
			for(int i = 0; i < result.length; i++)
			{
				double distcbd = getDistance(i, (citysize*citysize)/2, citysize);
				double popden = 1.0/(distcbd/cityradius + scaleparam);
				result[i] = popden;
			}
			return result;
		}
		
		//otherwise, read it from file.
		//file is printed out from minlat/minlong to maxlat/maxlong
		//(the default printout of GISLayer) so we need to rearrange a bit
		BufferedReader rdr = new BufferedReader(new FileReader(file));
		String line = rdr.readLine();
		String[] bits = Util.getWords(line);
		if(bits.length != result.length) throw new Exception("there are "+bits.length+" entries in file, expected "+result.length);
		for(int i = 0, x=0, y=citysize-1; i < result.length; 
		    i++, x=(x+1) % citysize, y=citysize-1-i/citysize) 
		{
			int realindex = y*citysize+x;
			Debug.println("popdenread (i,x,y): "+i+" "+x+" "+y+" :realindex:: "+realindex, Debug.INFO);
			result[realindex] = Double.parseDouble(bits[i]);
		}
		
		//print it out
		for(int i = 0; i < result.length; i++)
		{
			System.out.print(result[i]+" ");
			if((i+1) % citysize == 0)
				System.out.println();
		}
		
		String nextline = rdr.readLine();
		while(nextline != null) {
			bits = Util.getWords(nextline);
			if(!bits[0].equalsIgnoreCase("DISALLOW") || bits.length != 3)
				throw new RuntimeException("Each subsequent line in density file must be of the form \"DISALLOW fromid toid\"");
			int fromid = Integer.parseInt(bits[1]);
			int toid = Integer.parseInt(bits[2]);
			disallowedlinks.put(fromid+" "+toid, null);
			disallowedlinks.put(toid+" "+fromid, null);
			nextline = rdr.readLine();
		}
		
		return result;
	}
	
	
	
	
	
	//build network
	private static double MINVAL = Double.NaN;
	private static double MAXVAL = Double.NaN;
	public static void main(String[] args) throws Exception
	{
		Debug.setDebugLevel(Debug.DEBUG_PARANOID);
		Debug.setVerbosityLevel(Debug.INFO);
		Debug.println("REM: usage:", Debug.INFO);
		Debug.println("arg1: citysize", Debug.INFO);
		Debug.println("arg2: scalefact", Debug.INFO);
		Debug.println("arg3: linkstoadd", Debug.INFO);
		Debug.println("arg4: ntries", Debug.INFO);
		Debug.println("arg5: maxlinklength", Debug.INFO);
		Debug.println("arg6: (if present) 'b+w' or 'colour' (without quotes)", Debug.INFO);
		Debug.println("arg7: (if present) population density file", Debug.INFO);
		Debug.println("arg8: (if present) bottom of scale", Debug.INFO);
		Debug.println("arg9: (if present) max of scale", Debug.INFO);

		
		int citysize = Integer.parseInt(args[0]);
		double scalefact = Double.parseDouble(args[1]);
		int linkstoadd = Integer.parseInt(args[2]);
		int ntries = Integer.parseInt(args[3]);
		double maxlinklength = Double.parseDouble(args[4]);
		
		
		String popdenfile = null;
		if(args.length > 5)  popdenfile = args[5];
		boolean grayscale = false;
		if(args.length > 6)
			if(args[6].equalsIgnoreCase("b+w"))
				grayscale = true;

		if(args.length > 7) {
			MINVAL = Double.parseDouble(args[7]);
			MAXVAL = Double.parseDouble(args[8]);
		}
		double[] popden = getPopdensity(citysize, scalefact, popdenfile);

		
		if(popdenfile == null && citysize % 2 != 1)
			throw new RuntimeException("City size must be odd");

		
		//calculate the overall trip-count from all destinations
		//to all destinations
		double[][] tripcounts = new double[citysize*citysize][citysize*citysize];
		double popsum = 0.0;
		double tripsum = 0.0;
		double cityradius = Math.sqrt(2*Math.pow(citysize/2, 2.0));
		for(int i = 0; i < citysize*citysize; i++)
		{
			//double distcbd_a = getDistanceFromCBD(i, citysize);
			//double Pa = 1.0/(distcbd_a/cityradius + scalefact);
			double Pa = popden[i];
			popsum += Pa;
			
			double destsum = 0.0;
			for(int j = 0; j < citysize*citysize; j++)
			{
				if(i==j) continue;
				
				//double distcbd_b = getDistanceFromCBD(j, citysize);
				//double Pb = 1.0/(distcbd_b/cityradius + scalefact);
				double Pb = popden[j];
				double dist = getDistance(i, j, citysize);
				destsum += Pb/dist;
				tripcounts[i][j] = Pb/dist;
			}
			
			//normalize so that there are Pa trips from the origin node,
			//with destinations determined by the weighting Pb/dist
			for(int j = 0; j < citysize*citysize; j++)
			{
				if(i==j) continue;
				tripcounts[i][j] = Pa*(tripcounts[i][j]/destsum);
				tripsum += tripcounts[i][j];
			}
 
		}
		
		Debug.println("popsum and tripsum (should be equal) are "+popsum+" "+tripsum, Debug.INFO);
		Debug.println("citysize: "+citysize+" scalefact: "+scalefact+" linkstoadd: "+linkstoadd+" ntries: "+ntries+" maxlinklength: "+maxlinklength, Debug.INFO);

		//the CBD node is the one with maximum pop density
		int cbdid = Util.getMaxIndex(popden);
		Debug.println("CBD id is "+cbdid, Debug.IMPORTANT);
		
		GraphNode cbdnode = new GraphNode(cbdid);
		HashMap graph = new HashMap();
		graph.put(new Integer(cbdnode.getId()), cbdnode);
		
		//now keep adding links to the graph
		for(int i = 0; i < linkstoadd; i++)
		{
			Debug.println("Adding link number "+(i+1), Debug.INFO);
			Debug.println("There are currently "+graph.size()+" nodes in the graph", Debug.INFO);
			
			//try a few links and pick the one that
			//increases utility the most
			double bestutil = -1.0;
			GraphEdge[] bestedges = null;
			for(int j = 0; j < ntries; j++)
			{
				Debug.println("Trying link "+(j+1)+" of "+ntries, Debug.INFO);
				GraphEdge[] edgesadded = addLink(graph, citysize, maxlinklength);
				Debug.println("Link is from "+edgesadded[0].leadsfrom.getId()+" -> "+edgesadded[0].leadsto.getId(), Debug.INFO);
				//make sure the edges are mirrors of each other
				if(edgesadded[0].leadsfrom != edgesadded[1].leadsto ||
				   edgesadded[0].leadsto != edgesadded[1].leadsfrom)
					throw new RuntimeException("Impossible case");
					
				double util = getValueOfNetwork(graph, citysize, scalefact, tripsum, popsum, tripcounts, popden, new double[citysize*citysize]);
				Debug.println("Utility of network with this edge added is "+util, Debug.EXTRA_INFO);
				if(util > bestutil) {
					Debug.println("This is the best edge found so far for this start node", Debug.INFO);
					bestutil = util;
					bestedges = edgesadded;  
				}
				
				//now remove the added links from the graph
				edgesadded[0].leadsfrom.removeEdge(edgesadded[0]);
				edgesadded[1].leadsfrom.removeEdge(edgesadded[1]);
				
				//if the new node linked to had no other links, we
				//remove that node also
				if(edgesadded[0].leadsto.getEdges().size() == 0) {
					graph.remove(new Integer(edgesadded[0].leadsto.getId()));
					Debug.println("Node "+edgesadded[0].leadsto.getId()+" was added to the graph to evaluate this link. Now removing", Debug.INFO);
				}

			}
			
			//ok, we found the best edges from all our trials.
			//so we add them
			GraphNode nodea = bestedges[0].leadsfrom;
			GraphNode nodeb = bestedges[0].leadsto;
			Debug.println("Best edge found in trials is from "+nodea.getId()+" -> "+nodeb.getId()+"  (dist "+bestedges[0].weight+")", Debug.INFO);
			if(!graph.containsKey(new Integer(nodeb.getId())))
			{
				Debug.println("New destination node ("+nodeb.getId()+") is new to graph, adding it", Debug.INFO);
				graph.put(new Integer(nodeb.getId()), nodeb);
			}
			
			nodea.addEdge(bestedges[0]);
			nodeb.addEdge(bestedges[1]);

			Debug.println("Utility of network is "+getValueOfNetwork(graph, citysize, scalefact, tripsum, popsum, tripcounts, popden, new double[citysize*citysize]), Debug.INFO);

			//now print out the network
			if(i % 10 == 9) //print every 10th iteration
				showNetwork(graph, citysize, true, "network_it"+(i+1)+".png");
			
		}
		
		showNetwork(graph, citysize, true, "finalnetwork.png");
		
		//print the shortest path tree from a randomly chosen node
		Object[] keys = graph.keySet().toArray();
		Object key = keys[(int) (Math.random()*keys.length)];
		GraphNode pathtree = GraphUtil.getShortestPathTree(((Integer) key).intValue(), graph);
		Debug.println("PATH TREE FROM "+((Integer) key).intValue()+" is: ", Debug.INFO);
		if(pathtree != null)
			GraphUtil.printGraph(pathtree, System.out);
		Debug.println("END OF PATH TREE FROM "+((Integer) key).intValue(), Debug.INFO);
		
		
		
		//now print out the network
		Debug.setVerbosityLevel(Debug.EXTRA_INFO);
		double[] originmodesplit = new double[citysize*citysize];
		if(linkstoadd > 0)
			getValueOfNetwork(graph, citysize, scalefact, tripsum, popsum, tripcounts, popden, originmodesplit);
		Util.printarray(originmodesplit, System.out);
		
		//print out population density and mode share by node
		if(grayscale)
			generateImage(popden, citysize, graph, "popden.png", Color.lightGray, Color.black);
		else
			generateImage(popden, citysize, graph, "popden.png", Color.blue, Color.red);
		generateImage(originmodesplit, citysize, graph, "modesplitbyorigin.png", Color.blue, Color.red);
	}
	
	
	private static void showNetwork(HashMap graph, int citysize, boolean dumptofile, String filename) throws Exception
	{
		generateImage(null, citysize, graph, filename, Color.blue, Color.red);
	}

	
	
	
	private static void drawNetwork(Graphics g, HashMap graph, int citysize, int mulfact)
	{
		Object[] keys = graph.keySet().toArray();
		java.util.ArrayList edges = ((GraphNode) graph.get(keys[0])).getAllEdgesInGraph();
		
		for(int i = 0; i < edges.size(); i++)
		{
			GraphEdge edge = (GraphEdge) edges.get(i);
			int from = edge.leadsfrom.getId();
			int[] xy = getxy(from, citysize);
			int to = edge.leadsto.getId();
			int[] xy2 = getxy(to, citysize);
		

			//now draw the link
			g.setColor(java.awt.Color.DARK_GRAY);
			g.drawLine(xy[0]*mulfact+mulfact/2, xy[1]*mulfact+mulfact/2, xy2[0]*mulfact+mulfact/2, xy2[1]*mulfact+mulfact/2);
			g.fillOval(xy[0]*mulfact+mulfact/2-3, xy[1]*mulfact+mulfact/2-3, 6, 6);
			g.fillOval(xy2[0]*mulfact+mulfact/2-3, xy2[1]*mulfact+mulfact/2-3, 6, 6);
		}		
	}
	
	//generate an image (of pop density, whatever)
	public static void generateImage(double[] data, int citysize, HashMap graph, String filename, Color lowercolor, Color uppercolor)
	throws IOException
	{		
		int mulfact = 400/citysize;
		int size = citysize*mulfact+1;
		BufferedImage bim = new BufferedImage(size,size,BufferedImage.TYPE_3BYTE_BGR);
		java.awt.Graphics g = bim.getGraphics();
		
		//create white background
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, size, size);
		
		g.setColor(Color.BLACK);
		//first draw the grid
		for(int x = 0; x <= size; x+=mulfact)
			g.drawLine(x, 0, x, size);
		for(int y = 0; y <= size; y+=mulfact)
			g.drawLine(0, y, size, y);
		


		
		
		ColourGradient grad = null;
		//draw the data
		if(data != null)
		{
			//work out max and min, but exclude 0 values
			double max = Double.NEGATIVE_INFINITY;
			double min = Double.POSITIVE_INFINITY;
			for(int i = 0; i < data.length; i++)
				if(data[i] > 0.0) {
					max = Math.max(data[i], max);
					min = Math.min(data[i], min);
				}
			
			//unless user has specified min/max values to
			//use in scale. In which case, we overwrite
			//with them.
			if(!Double.isNaN(MINVAL)) {
				min = MINVAL;
				max = MAXVAL;
			}
			

			grad = new ColourGradient(lowercolor, min, uppercolor, max);
			for(int i = 0; i < data.length; i++)
			{
				int[] xy = getxy(i, citysize);
				g.setColor(grad.getColour(data[i]));
				if(data[i] > 0.0)
					g.fillRect(xy[0]*mulfact+1, xy[1]*mulfact+1, mulfact-1, mulfact-1);
			}
		}

		//now draw the network
		if(graph != null)
			drawNetwork(g, graph, citysize, mulfact);

		
		
		//now draw the image
		class tmpJPanel extends JPanel {
			BufferedImage img = null;
			tmpJPanel(BufferedImage bim) { img = bim; }
			
			public void paintComponent(Graphics g) {
				super.paintComponent(g);
				g.drawImage(img, 0, 0, null);
			}
		}
		
		JFrame frame = new JFrame();
		frame.setSize(500,500);
		frame.getContentPane().add(new tmpJPanel(bim));
		frame.setVisible(true);
		
		/*try {
		new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
		}
		catch(IOException ioe) {}*/
		
		ImageIO.write(bim, "png", new java.io.File(filename));
		
		frame.dispose();
		
		
		if(grad != null)
		{
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, 400, 400);
			g.setColor(Color.BLACK);
			g = g.create(150, 150, 200, 100);
			grad.paintColourScale(g, 180, 75, false, 5);
			JFrame frame2 = new JFrame();
			frame2.setSize(500,500);
			frame2.getContentPane().add(new tmpJPanel(bim));
			frame2.setVisible(true);
			ImageIO.write(bim, "png", new java.io.File(filename+"_scale.png"));
			frame2.dispose();
		}
		
	}
	
	
	
	//add a link to the graph
	//actually, add 2, because its a bi-directional link
	public static GraphEdge[] addLink(HashMap graph, int citysize, double maxlinklength)
	{
		//first of all, pick a start node at random
		//this is already connected to the network
		Object[] keys = graph.keySet().toArray();
		int nkeys = keys.length;
		int rnd = (int) (Math.random()*nkeys);
		int fromid = ((Integer) keys[rnd]).intValue();

		GraphNode nodea = (GraphNode) graph.get(new Integer(fromid));
		
		//get a list of all the nodes that are close to this one
		java.util.ArrayList candidates = new java.util.ArrayList();
		int[] origxy = getxy(fromid, citysize);
		int radiustoconsider = (int) maxlinklength;
		for(int xd = -radiustoconsider; xd <= radiustoconsider; xd++)
		{
			for(int yd = -radiustoconsider;yd <= radiustoconsider; yd++)
			{
				if(xd == 0 && yd == 0)
					continue; //dont link to self
				
				int destx = origxy[0]+xd;
				int desty = origxy[1]+yd;
				
				//make sure its within city bounds 
				if(destx < 0 || destx >= citysize || desty < 0 || desty >= citysize)
					continue;
				
				//make sure it is within maxlinklength
				int destid = desty*citysize+destx;
				double dist = getDistance(fromid, destid, citysize);
				if(dist > maxlinklength)
					continue;
				
				//make sure it is not in the dissalowed links
				if(disallowedlinks.containsKey(fromid+" "+destid))
					continue;
				
				if(!nodea.isLinkedTo(destid))
					candidates.add(new Integer(destid));
			}
		}
		
		if(candidates.size() == 0) 
		{
			Debug.println("No new links possible from origin node "+fromid+", trying again...", Debug.IMPORTANT);
			//try another origin node
			return addLink(graph, citysize, maxlinklength);
		}
				
		
		//now pick a node to link to. This must be within
		//a distance of maxlinklength, and the link must not yet exist
		int toid = ((Integer) candidates.get((int) (Math.random()*(candidates.size())))).intValue();
		Debug.println("Examining link from "+fromid+" -> "+toid, Debug.INFO);
		if(getDistance(fromid, toid, citysize) > maxlinklength || nodea.isLinkedTo(toid) || fromid == toid)
			throw new IllegalStateException("Impossible case reached! This should have already been checked for!");
		
		
		GraphNode nodeb = null;
		if(graph.containsKey(new Integer(toid)))
			nodeb = (GraphNode) graph.get(new Integer(toid));
		else {
			nodeb = new GraphNode(toid);
			graph.put(new Integer(toid), nodeb);
		}
			
		//now create the edges
		double dist = getDistance(fromid, toid, citysize);
		GraphEdge[] result = new GraphEdge[2];
		result[0] = new GraphEdge(nodea, nodeb, dist, null);
		result[1] = new GraphEdge(nodeb, nodea, dist, null);
		
		//now add them to the nodes
		nodea.addEdge(result[0]);
		nodeb.addEdge(result[1]);
		
		return result;
	}
	
	
}