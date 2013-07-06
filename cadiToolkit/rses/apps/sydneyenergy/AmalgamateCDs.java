package rses.apps.sydneyenergy;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane.MaximizeAction;

import rses.CADIMainFrame;
import rses.Debug;
import rses.spatial.GISLayer;
import rses.util.FileUtil;
import rses.util.Heap;
import rses.util.HeapElement;
import rses.util.Util;



/** Create regions that are amalgamations of collection districts
 * 
 * @author peterr
 *
 */
public class AmalgamateCDs
{
	
	
	/** 
	 * args[0] == cd region membership file, or adjacency list file
	 * args[1] == data file with each line in format [CD   VAL]
	 * args[2] == data file with each line in format [CD   count]
	 * args[3] == data file with each line in format [CD   area]
	 * args[4] == mincount
	 * args[5] == maxarea
	 * 
	 * This method amalgamates CDS so that each CD has at least `count'
	 * households in it.
	 * 
	 * it joins adjacent cds so until this is so, preferentially joining
	 * CDs that have close values of a user specified function (See arg[1]).
	 * This might be median household ioncome, for example
	 * 
	 * 
	 * 
	 */
	public static void main(String[] args) throws Exception
	{
		Debug.setVerbosityLevel(Debug.INFO);
		
		HashMap vals = new HashMap();
		HashMap counts = new HashMap();
		HashMap areas = new HashMap();
		double[][] valst = FileUtil.readVectorsFromFile(new java.io.File(args[1]));
		for(int i = 0; i < valst.length; i++)
			vals.put(new Integer((int) Math.rint(valst[i][0])), new Double(valst[i][1]));
		double[][] countst = FileUtil.readVectorsFromFile(new java.io.File(args[2]));
		for(int i = 0; i < countst.length; i++)
			counts.put(new Integer((int) Math.rint(countst[i][0])), new Double(countst[i][1]));
		double[][] areast = FileUtil.readVectorsFromFile(new java.io.File(args[3]));
		for(int i = 0; i < areast.length; i++)
			areas.put(new Integer((int) Math.rint(areast[i][0])), new Double(areast[i][1]));
		
		
		
		double mincount = Double.parseDouble(args[4]);
		double maxarea = Double.parseDouble(args[5]);
		
		
		ArrayList valuelist = null;
		HashMap adjacent = null;
		
		if(args[0].endsWith(".gis"))
		{
			GISLayer membership = GISLayer.readFromFile(args[0]);
			GISLayer shrunk = ((GISLayer) membership.clone());
			shrunk.shrink_modal(4);
			//ok, now work out adjacency lists
			adjacent = getAdjacencyLists(shrunk);

			//get a list of every CD in the layer
			valuelist = membership.getValues();

			//check if there are any orphans
			for(int i = 0; i < valuelist.size(); i++)
			{
				Integer cd = new Integer(((Float) valuelist.get(i)).intValue());
				if(!adjacent.containsKey(cd))
					Debug.println("Adjacency list derived from shrunk layer contains no mapping for CD "+cd.intValue(), Debug.CRITICAL);
			}
		}
		else
		{
			//just read from file instead
			adjacent = readAdjacencyList(args[0]);
			valuelist = new ArrayList(adjacent.keySet());
		}
		
		
		ArrayList groups = new ArrayList(adjacent.keySet());
		
		//join cds into groups
		cdnode[] cdgroups = amalgamate(groups, adjacent, vals, counts, areas, mincount, maxarea);
		
		
		
		
		
		//now print them out
		Iterator keyit = adjacent.keySet().iterator();
		while(keyit.hasNext())
		{
			Integer key = (Integer) keyit.next();
			int[] adj = (int[]) adjacent.get(key);
			System.out.print(key+" ");
			System.out.println(Util.arrayToString(adj));
		}
	}
	
	
	
	
	//just read the adjacency list from a file
	private static HashMap readAdjacencyList(String file) throws java.io.IOException
	{
		HashMap res = new HashMap();
		BufferedReader rdr = new BufferedReader(new FileReader(new java.io.File(file)));
		String line = rdr.readLine();
		
		while(line != null)
		{
			String[] bits = Util.getWords(line);
			int cd = Integer.parseInt(bits[0]);
			String restline = "";
			for(int i = 1; i < bits.length; i++)
				restline = restline+bits[i]+" ";
			int[] elems = (int[]) Util.parseArray(restline.substring(0, restline.length()-1), int.class);
			if(elems == null)
				elems = new int[0];
			res.put(new Integer(cd), elems);
			Debug.println("adjlist for cd "+cd+" : "+Util.arrayToString(elems), Debug.IMPORTANT);
			line = rdr.readLine();
		}
		
		return res;
	}
	
	//greedy algorithm, to join a CD to another cd 
	private static cdnode[] amalgamate(ArrayList groups, HashMap adjacent, HashMap vals, 
			HashMap counts, HashMap areas, double mincount, double maxarea)
	{
		HashMap cdtocdnodemap = new HashMap();
		ArrayList cdnodes = new ArrayList();
		for(int i = 0; i < groups.size(); i++)
		{
			Integer cd = (Integer) groups.get(i);
			Debug.println("Building cdnode for cd "+cd, Debug.INFO);
			int[] adj = (int[]) adjacent.get(cd);
			Double vd = (Double) vals.get(cd);
			if(vd == null) {
				System.out.println("No value for CD "+cd); 
				vd= new Double(1000.0);
			}
			Double countd = (Double) counts.get(cd);
			if(countd == null) {
				System.out.println("No count for CD "+cd); 
				countd = new Double(0.0);
			}
			Double aread = (Double) areas.get(cd);
			if(aread == null) {
				System.out.println("No area for CD "+cd); 
				aread = new Double(0.0);
			}
			double val = vd.doubleValue();
			double count = countd.doubleValue();
			double area = aread.doubleValue();
			cdnode cdn = new cdnode(cd, new ArrayList(), val*count, count, area);
			cdnodes.add(cdn);
			cdtocdnodemap.put(cd, cdn);
		}	
		
		Debug.println("Filling in adjacency list info", Debug.INFO);
		
		//ok, we need to go back and fill in adjacency list info
		Object[] cdkeys = cdtocdnodemap.keySet().toArray();
		for(int i = 0; i < cdkeys.length; i++)
		{
			Integer cd = (Integer) cdkeys[i];
			cdnode cdn = (cdnode) cdtocdnodemap.get(cd);
			int[] adj = (int[]) adjacent.get(cd);
			for(int j = 0; j < adj.length; j++) {
				cdnode adjcdnode = (cdnode) cdtocdnodemap.get(new Integer(adj[j]));
				cdn.adjacent.add(adjcdnode);
			}
		}
		
		Debug.println("Starting join operation", Debug.INFO);
		
		//ok, we have all cdnodes and we have filled in all adjacency list info
		//now lets keep joining
		boolean done = false;
		ArrayList orphans = new ArrayList();
		while(!done)
		{
			//order nodes by smallest
			cdnodes = orderbysmallest(cdnodes);
			
			int tojoin = -1;
			for(int i = 0; i < cdnodes.size(); i++)
			{
				cdnode cdn = (cdnode) cdnodes.get(i);
				if(cdn.count < mincount && cdn.area < maxarea) {
					tojoin = i;
					break;
				}
			}
			
			if(tojoin == -1)
				done = true;
			else 
			{
				cdnode cdn = (cdnode) cdnodes.get(tojoin);
				cdnode joinedcdnode = cdn.join(mincount, maxarea);
				if(joinedcdnode == null) {
					Debug.println("failed to join "+cdn.getcdliststr()+"... leaving it as orphan", Debug.IMPORTANT);
					cdnodes.remove(cdn);
					orphans.add(cdn);
				}
				else if(!cdnodes.remove(joinedcdnode))
				{
					if(orphans.contains(joinedcdnode)) while(orphans.contains(joinedcdnode)) {
						Debug.println("Rescued orphan node ... "+joinedcdnode.getcdliststr(), Debug.INFO);
						orphans.remove(joinedcdnode);
					}
					else
						throw new RuntimeException("Could not remove the node I just joined!");
				}
			}
			//Debug.println("BLIP", Debug.INFO);
		}

		//go through and join orphans to the smallest possible neighbouring node, 
		//provided that the orphan has non-zero count 
		boolean[] joined = new boolean[orphans.size()];
		for(int i =0; i < joined.length; i++)
		{
			if(joined[i])
				throw new RuntimeException();
			
			cdnode orph = (cdnode) orphans.get(i);
			if(orph.count == 0.0 || orph.count >= mincount || orph.area >= maxarea)
				continue;
			
			ArrayList adj = orph.adjacent;
			if(adj == null || adj.size() == 0)
				continue; //cant join this cdnode, remains an orphan
			
			int best = -1;
			double bestsum = Double.MAX_VALUE;
			for(int j = 0; j < adj.size(); j++) 
			{
				cdnode adjcdnode = (cdnode) adj.get(j);
				if(adjcdnode.count == 0.0 || adjcdnode.area+orph.area >= maxarea)
					continue;
				double sum = adjcdnode.count+orph.count;
				if(sum < bestsum) {
					bestsum = sum;
					best = j;
				}
			}
			
			if(best != -1) //found one to join to
			{
				cdnode tojointo = (cdnode) adj.get(best);
				tojointo.join(orph);
				joined[i] = true;
			}
		}
		
		
		
		
		Debug.println("printing results", Debug.INFO);
		
		//go through and print out all the joined nodes
		for(int i = 0; i < cdnodes.size(); i++)
		{
			cdnode cdn = (cdnode) cdnodes.get(i);
			Debug.println("ZONE "+cdn.getcdliststr()+" "+cdn.count+" "+(cdn.count > 0.0 ? cdn.val/cdn.count : 0.0), Debug.INFO);
		}
		for(int i =0; i < orphans.size(); i++)
		{
			if(joined[i])
				continue; //its been merged, so is no longer an orphan
			cdnode cdn = (cdnode) orphans.get(i);
			Debug.println("ORPHANZONE "+cdn.getcdliststr()+" "+cdn.count+" "+(cdn.count > 0.0 ? cdn.val/cdn.count : 0.0), Debug.INFO);
		}
		return null;
	}
	
	
	
	static ArrayList orderbysmallest(ArrayList items)
	{		
		Heap<cdnode> h = new Heap<cdnode>(items.size());
		for(int i = 0; i < items.size(); i++)
		{
			cdnode cdn = (cdnode) items.get(i);
			HeapElement<cdnode> he = new HeapElement<cdnode>(cdn.count, cdn);
			h.insert(he);
		}
		
		
		ArrayList<cdnode> res = new ArrayList<cdnode>();
		for(int i =0; i < items.size(); i++)
			res.add(h.extractMin().getObject());
		
		return res;
		
	}
	
	
	private static HashMap getAdjacencyLists(GISLayer membership)
	{
		HashMap res = new HashMap();
		
		float[][] mbrdata = membership.continuousdata;
		boolean[] done = new boolean[(int) Math.rint(membership.getMaxVal())+1];
		
		
		//boolean[][] totry = GISLayer.calcBoundaryLayerForContinuousData(membership);
		//for(int i = 0; i < totry.length; i++)
		//	for(int j = 0; j < totry[0].length; j++)
		//		totry[i][j] = !totry[i][j];
		boolean[][] visited = new boolean[mbrdata.length][mbrdata[0].length];
		
		
		for(int i = 0; i < mbrdata.length; i++)
		{
			for(int j = 0; j < mbrdata[0].length; j++)
			{
				float f = mbrdata[i][j];
				if(Float.isNaN(f))
					continue;
				int val = (int) Math.rint(f);
				if(done[val] || visited[i][j])
					continue;
				else {
					res.put(new Integer(val), extractAdjacencyList(mbrdata, i, j, visited));
					done[val] = true;
					//Debug.println("Done zone "+val, Debug.INFO);
				}
			}
		}
		
		return res;
	}
	
	
	public static int[] extractAdjacencyList(float[][] mbrdata, int i, int j, boolean[][] visited)
	{
		ArrayList adj = new ArrayList();
		extractAL(mbrdata, i, j, visited, adj);
		

		//stick values (including repeats) into array
		int[] adjlist = new int[adj.size()];
		for(int t = 0; t < adjlist.length; t++)
			adjlist[t] = ((Integer) adj.get(t)).intValue();
		Arrays.sort(adjlist);
		
		//now filter out repeat values in adj
		int count = 1;
		for(int t = 1; t < adjlist.length; t++)
			if(adjlist[t] == adjlist[t-1])
				adjlist[t-1] = -1;
			else
				count++;
		int[] res = new int[count];
		int count2 = 0;
		for(int t = 0; t < adjlist.length; t++)
		{
			if(adjlist[t] != -1)
				res[count2++] = adjlist[t];
		}
		
		if(count2 != count)
			throw new RuntimeException("Count is "+count+" and count2 is "+count2);
		
		if(res.length >= 2 && res[1] <= res[0])
			throw new RuntimeException("Something fishy going on....");
		
		//now get rid of 0, if there is a zero, because 0 represents
		//sea and other crap like that
		if(res[0] == 0)
		{
			int[] res2 = new int[res.length-1];
			System.arraycopy(res, 1, res2, 0, res.length-1);
			return res2;
		}
		
		
		return res;
		
	}
	
	
	
	
	//add all ids that are adjacent to this one and of a different type
	public static void extractAL(float[][] mbrdata, int i, int j, boolean[][] visited, ArrayList result)
	{
		if(visited[i][j])
			return; //we've already been here
		//Debug.println("extractAL "+i+" "+j, Debug.INFO);
		visited[i][j] = true; //mark that we have been here
		
		float zoneid = mbrdata[i][j];
		
		//look at neighbouring squares. If they are boundaries,
		//include them, otherwise, just call recursively
		if(i+1 < mbrdata.length)
		{
			if(mbrdata[i+1][j] != zoneid)
				result.add(new Integer((int) Math.rint(mbrdata[i+1][j])));
			else
				extractAL(mbrdata, i+1, j, visited, result);
		}
		if(i-1 >= 0)
		{
			if(mbrdata[i-1][j] != zoneid)
				result.add(new Integer((int) Math.rint(mbrdata[i-1][j])));			
			else
				extractAL(mbrdata, i-1, j, visited, result);
		}
		if(j+1 < mbrdata[0].length)
		{
			if(mbrdata[i][j+1] != zoneid)
				result.add(new Integer((int) Math.rint(mbrdata[i][j+1])));
			else
				extractAL(mbrdata, i, j+1, visited, result);
		}
		if(j-1 >= 0)
		{
			if(mbrdata[i][j-1] != zoneid)
				result.add(new Integer((int) Math.rint(mbrdata[i][j-1])));
			else
				extractAL(mbrdata, i, j-1, visited, result);
		}
	}
		
	
	
}











class cdnode
{
	ArrayList cds = new ArrayList();
	double count;
	double val;
	double area;
	ArrayList adjacent;
	
	
	static ArrayList globalladjlists = new ArrayList();
	
	
	cdnode(Integer cd, ArrayList adj, double val, double count, double area)
	{
		this.val = val;
		this.count = count;
		this.area = area;
		this.adjacent = adj;
		this.cds.add(cd);
		globalladjlists.add(adj);
	}
	
	//join to the best adjacent node. Return the cd node that was joined,
	//or null if none was
	cdnode join(double mincount, double maxarea)
	{
		if(adjacent == null || adjacent.size() == 0) 
		{
			Debug.println("Adjacent list is empty -- cannot join this to anything", Debug.IMPORTANT);
			return null; //nothing to join to
		}
		if(this.area >= maxarea)
			return null;
		
		//first, try to join only to groups that are under mincount
		ArrayList candidates = new ArrayList();
		for(int i = 0; i < adjacent.size(); i++)
		{
			cdnode adj = (cdnode) adjacent.get(i);
			if(adj.count >= mincount)
				continue;
			if(this.area + adj.area >= maxarea)
				continue;
			
			//ok, adj has < mincount, so its a candidate for joining
			candidates.add(adj);
		}
		
		
		if(candidates.size() == 0) //if we still have no potential zones to join with
			return null; //leave it as an orphan
		

		
		//ok, we now have the possibilities for joining. Choose the best one
		double thisval = this.count > 0.0 ? this.val/this.count : this.val;
		double bestdiff = Double.POSITIVE_INFINITY;
		cdnode best = null;
		
		for(int i = 0; i < candidates.size(); i++)
		{
			cdnode cur = (cdnode) candidates.get(i);
			if(cur.count == 0.0 && this.count != 0.0)
				continue; //dont join up empty cds except with other empty cds
			double cval = cur.count > 0.0 ? cur.val / cur.count : cur.val;
			double diff = Math.abs(cval - thisval);
			if(diff < bestdiff) {
				bestdiff = diff;
				best = cur;
			}
		}
		
		if(best == null) 
			return null; //no non-empty join possible that fits within area constraint
		
		this.join(best);
		
		return best;
	}
	
	
	String getcdliststr()
	{
		String res = "";
		for(int i = 0; i < cds.size(); i++)
			res = res + cds.get(i)+" ";
		return res;
	}
	
	/** merges tojointo into this node */
	void join(cdnode tojointo)
	{
		//sanity check before starting join
		if(this.adjacent.contains(this))
			throw new RuntimeException("cdnode is adjacent to itself even before join operation!");

		
		Debug.println("joining "+tojointo.getcdliststr()+" to "+this.getcdliststr(), Debug.INFO);
		
		//update val and count
		this.val += tojointo.val;
		this.count += tojointo.count;
		this.area += tojointo.area;
		
		//update the list of cds
		for(int i = 0; i < tojointo.cds.size(); i++)
			this.cds.add(tojointo.cds.get(i));
		
		//update this adjacency list, by removing the entry pointing
		//to the node to join, and adding all its adjacent members
		//(except this one)
		
		//first remove the node we are joining to from our adjacency list
		while(adjacent.contains(tojointo))
			adjacent.remove(tojointo);
		
		//now add all its members to this nodes adjacency list
		for(int i = 0; i < tojointo.adjacent.size(); i++)
			if(tojointo.adjacent.get(i) != this)
				this.adjacent.add(tojointo.adjacent.get(i));
		
		//finally, go and update all other nodes that point to the
		//joined node so that they point to the new merged entity
		for(int i = 0; i < globalladjlists.size(); i++)
		{
			ArrayList adjlist = (ArrayList) globalladjlists.get(i);
			int nadj = adjlist.size();
			int j = 0;
			while(j < nadj)
			{
				cdnode elem = (cdnode) adjlist.get(j);
				if(elem == tojointo) {
					adjlist.remove(j);
					adjlist.add(this);
					j=0; //have to start again, as our indexing is stuffed up
				}
				else
					j++;
			}
		}
		
		//and we're done! Check we are not adjacent to ourselves
		if(this.adjacent.contains(this))
			throw new RuntimeException("cdnode is adjacent to itself!");
		
	}	
}



