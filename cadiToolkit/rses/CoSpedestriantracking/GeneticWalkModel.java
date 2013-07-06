package rses.CoSpedestriantracking;

import rses.Debug;
import rses.inverse.UserFunctionHandle;
import rses.inverse.genetic.GeneticSearch;
import rses.inverse.genetic.Organism;

import java.util.ArrayList;
import java.util.List;
import rses.math.GraphEdge;
import rses.math.GraphNode;

public class GeneticWalkModel extends rses.inverse.genetic.Organism  
{
	/* The parameterization for this model is as follows. 
	 * 
	 * 1st parameter gives x position.
	 * 2nd parameter gives y position.
	 * 3rd parameter gives time offset.
	 * 
	 * SPECIAL CASES -- the first three parameters ALWAYS ARE FOR TIME 0, and the last 3 always for last timestep
	 * 
	 */

	private PMF[] pmfsbytime;
	private PMF prior;
	
	public GeneticWalkModel(double[] params, double misfit, PMF[] pmfbytime, PMF prior)
	{
		super(params, misfit);
		this.pmfsbytime = pmfbytime;
		this.prior = prior;
	}

	public GeneticWalkModel(double[] params, PMF[] pmfbytime, PMF prior)
	{
		super(params);
		this.pmfsbytime = pmfbytime;
		this.prior = prior;
	}
	
	public GeneticWalkModel(rses.inverse.UserFunctionHandle h, PMF[] pmfbytime, PMF prior)
	{
		super(h);
		this.pmfsbytime = pmfbytime;
		this.prior = prior;
	}
	
		
	@Override
	public Organism breed(Organism partner, double[][] parambounds)
	{
		//Debug.println("GeneticWalkModelBreed", Debug.INFO);
		double[] dna1 = this.getModelParameters();
		double[] dna2 = partner.getModelParameters();
		if(dna1.length != dna2.length)
			throw new IllegalStateException("Attempt to breed Organisms of different species....");
		
		double[] child_dna = new double[dna1.length];
		
		double rnd = rand.nextDouble();
		double child_mutation_prob = rnd*partner.getMutationProb()+(1-rnd)*this.mutationProb;
		rnd = rand.nextDouble();
		double child_gauss = rnd*partner.getMutationProb() + (1-rnd)*this.gauss_divide;
		
		for(int i = 0; i < child_dna.length; i+=3)
		{
			if(rand.nextDouble() < 0.5) {
				child_dna[i] = partner.getModelParameter(i);
				child_dna[i+1] = partner.getModelParameter(i+1);
				child_dna[i+2] = partner.getModelParameter(i+2);
			}
			else {
				child_dna[i] = this.getModelParameter(i);
				child_dna[i+1] = this.getModelParameter(i+1);
				child_dna[i+2] = this.getModelParameter(i+2);
			}

			//mutate the time parameter
			if(rand.nextDouble() < child_mutation_prob)
			{
				double upr = parambounds[i+2][1];
				double lwr = parambounds[i+2][0];
				double range=upr-lwr;
				if(range == 0.0) {
					child_dna[i+2] = upr;
					continue;
				}

				double trydna = child_dna[i+2];
				//take a gaussian walk (but stay within bounds)
				do {
						  trydna = (child_dna[i+2] + rand.nextGaussian()*(range/2)*child_gauss);
				}
				while(trydna > upr || trydna < lwr);

				child_dna[i+2] = trydna;
			 }
		}
		

		//now mutate the x/y positions
		double xrange=parambounds[0][1]-parambounds[0][0];
		double yrange=parambounds[1][1]-parambounds[1][0];
		for(int i = 0; i < child_dna.length; i+=3)
		{
			boolean mutatex = rand.nextDouble() < child_mutation_prob;
			boolean mutatey = rand.nextDouble() < child_mutation_prob;
			double tryx = child_dna[i];
			double tryy = child_dna[i+1];
			if(prior.getProbByIndices((int) tryx, (int) tryy) <= 0) throw new RuntimeException("Un-mutated x/y position is zero prior? Should be impossible");
			
			if(mutatex) tryx += rand.nextGaussian()*(xrange/2)*child_gauss;
			if(mutatey) tryy += rand.nextGaussian()*(yrange/2)*child_gauss;
			
			//while we are out of bounds OR at a zero prior position, we try again
			while(tryx < parambounds[0][0] || tryx > parambounds[0][1] ||
				  tryy < parambounds[1][0] || tryy > parambounds[1][1] ||
				  prior.getProbByIndices((int) tryx, (int) tryy) <= 0) 
			{
				//Debug.println(((int) tryx)+" "+((int) tryy)+" is bad x/y pos, trying again", Debug.INFO);
				tryx = child_dna[i] + rand.nextGaussian()*(xrange/2)*child_gauss;
				tryy  = child_dna[i+1] + rand.nextGaussian()*(yrange/2)*child_gauss;
			}
			child_dna[i] = tryx;
			child_dna[i+1] = tryy;
		}
		

		//lastly, we change the meta parameters
		if(rand.nextDouble() < child_mutation_prob)
			child_gauss = rand.nextDouble()*child_gauss*2;
		if(rand.nextDouble() < child_mutation_prob)
			child_mutation_prob = rand.nextDouble()*2*child_mutation_prob;		

		Debug.println("child_gauss "+child_gauss+"      child_mutation "+child_mutation_prob, Debug.EXTRA_INFO);

		
		GeneticWalkModel result = new GeneticWalkModel(child_dna, pmfsbytime, prior);
		
		result.mutationProb = child_mutation_prob;
		result.gauss_divide = child_gauss;
		return result;
	}

	
	
	public static GeneticWalkModel getNewGeneticWalkModel(int numpositions, PMF[] pmfsbytime, PMF prior)
	{
		java.util.Random rand = new java.util.Random();
		
		int[] times = new int[numpositions];
		for(int i = 0; i < numpositions; i++)
			times[i] = rand.nextInt(pmfsbytime.length);
		java.util.Arrays.sort(times);

		double[] xys = new double[numpositions*3];
		
		for(int i = 0; i < numpositions; i++) 
		{
			int time = times[i];
			int[] xy = pmfsbytime[time].sampleFrom(); //question is whether to sample from or draw from prior?
			//int[] xy = prior.sampleFrom(); //question is whether to sample from or draw from prior?
			xys[i*3] = xy[0];
			xys[i*3+1] = xy[1];
			xys[i*3+2] = times[i];
		}	
		
		return new GeneticWalkModel(xys, pmfsbytime, prior);
	}
	
	
	
	/**
	 * 
	 * @param popsize
	 * @param runtime
	 * @param pmfsbytime
	 * @param sentinels
	 * @param prior
	 * @param pathtrees
	 * @param speeddist
	 * @param startpopulation
	 * @return A 2-element array. The first of which is a PMF[] (giving the best position over time). The second is
	 *                             the population at end time
	 */
	public static Object[] getBest(int popsize, double runtime, PMF[] pmfsbytime, PMF[] sentinels,
			PMF prior, PathNode[][] pathtrees, Distribution speeddist, rses.inverse.genetic.Organism[] startpopulation)
	{
		class UFH implements UserFunctionHandle {

			PMF[] pmfsbytime;
			PMF[] sentinels;
			PMF prior;
			PathNode[][] pathtrees;
			Distribution speeddist;
			public UFH(PMF[] pmfsbytime, PMF[] sentinels, PMF prior, PathNode[][] pathtrees, Distribution speeddist) {
				this.pmfsbytime = pmfsbytime;
				this.prior = prior;
				this.pathtrees = pathtrees;
				this.speeddist = speeddist;
				this.sentinels = sentinels;
			}
			
			@Override
			public int getDimensionOfModelSpace() {
				return 3*Math.max(2, pmfsbytime.length/30); //one waypoint every 30 seconds, and we have x,y,time for each waypoint
			}

			@Override
			public double[][] getBoundsOnModelSpace() {
				double[] boundsx = new double[] {0,prior.getDimensions()[0]-0.000001}; 
				double[] boundsy = new double[] {0,prior.getDimensions()[1]-0.000001};
				double[] boundstime = new double[] {0,pmfsbytime.length-0.000001};
				double[][] bounds = new double[getDimensionOfModelSpace()][];
				for(int i = 0; i < getDimensionOfModelSpace(); i++)
				{
					if(i%3 == 0) bounds[i] = boundsx;
					else if(i%3 == 1) bounds[i] = boundsy;
					else bounds[i] = boundstime;
				}
				return bounds;
			}

			/** OK, this is where the real work happens. We need to work out the log probability
			 * of a model. This is based on the lowest cost path between each waypoint
			 */
			@Override
			public double getErrorForModel(double[] model) 
			{
				java.util.List<Double> speeds = new java.util.ArrayList<Double>();
				int[][] posbytime = getPosByTime(model, pmfsbytime, pathtrees, prior, speeds);
				if(posbytime.length != pmfsbytime.length) throw new RuntimeException("Impossible?!"+posbytime.length+" "+pmfsbytime.length);
				double err = 0.0;
				for(int t = 0; t < posbytime.length; t++)
				{
					int[] pos = posbytime[t];
					//TODO There is a question here whether we use the projected pmfs or
					//whether we just should go back and use the sentinel pmfs with the prior
					//for all the gaps. 
					double lp = 0.0;
					
					//lp = pmfsbytime[t].getLogProbByIndices(pos[0], pos[1]);   //use projected PMFS
					if(sentinels[t] != null) lp = sentinels[t].getLogProbByIndices(pos[0], pos[1]);   //use only sentinels

					err -= lp;
				}
				Debug.println("Error after positions is "+err, Debug.INFO);
				
				//now include speeds error
				for(double speed : speeds) 
					err -= Math.log(speeddist.pdf(speed));
				Debug.println("Error after speeds is "+err, Debug.INFO);
				
				
				return err;
			}

			@Override
			public double getPriorForModel(double[] model) {
				throw new UnsupportedOperationException("Not implemented. And you should never call this.");
			}

			@Override
			public String getParameterName(int pnum) {
				if(pnum % 3 == 0)
					return "Px_"+(pnum/3);
				if(pnum % 3 == 1)
					return "Py_"+(pnum/3);
				else
					return "Ptime_"+(pnum/3);
			}

			@Override
			public void setParameterName(int pnum, String name) {
				throw new UnsupportedOperationException("Not implemented");
			}
			
		}
		
		
		UFH handle = new UFH(pmfsbytime, sentinels, prior, pathtrees, speeddist);
		for(PMF pmf : pmfsbytime) pmf.cacheLogProbs(); //saves a lot of calls to Math.log
		if(startpopulation == null)
		{
			startpopulation = new rses.inverse.genetic.Organism[popsize];
			for(int i = 0; i < startpopulation.length; i++)
				startpopulation[i] = GeneticWalkModel.getNewGeneticWalkModel(handle.getDimensionOfModelSpace()/3, pmfsbytime, prior);
		}
		
		rses.inverse.genetic.GeneticSearch geneticAlg = new GeneticSearch(startpopulation, handle);
		geneticAlg.setTimeToRun((long) (runtime*1000));
		geneticAlg.run();
		GeneticWalkModel best = (GeneticWalkModel) geneticAlg.getBestModel();
		
				
		//Now we need to extract from that best model the actual path over time
		//REMEMBER TO GET RID OF ANY DUPLICATE TIMES (i.e. where the time is the same from one to the next)
		for(int i = 0; i < best.parameter.length; i+=3)
			Debug.println("PARAMTIME "+(int)best.parameter[i+2]+" lat="+(int)best.parameter[i]+" lon="+(int)best.parameter[i+1], Debug.INFO);
		int[][] posbytime = getPosByTime(best.parameter, best.pmfsbytime, pathtrees, prior, new ArrayList<Double>()); 
		for(int i = 0; i < posbytime.length; i++)
			Debug.println("posbytime "+i+" lat="+posbytime[i][0]+" lon="+posbytime[i][1], Debug.INFO);
		
		PMF[] mlpmf = new PMF[posbytime.length];
		for(int i = 0; i < mlpmf.length; i++)
			mlpmf[i] = new PMF(prior.getGeography(), posbytime[i][0], posbytime[i][1]);
		return new Object[] {startpopulation, mlpmf};

	}
	
	
	private static int[][] getPosByTime(double[] model, PMF[] pmfsbytime, PathNode[][] pathtrees, PMF prior,
			java.util.List<Double> speeds) 
	{
		if(!speeds.isEmpty()) throw new RuntimeException("Speeds list must be empty! It is an input parameter");
		int[][] posbytime = new int[pmfsbytime.length][];
			
		rses.util.Heap<int[]> heap = new rses.util.Heap<int[]>(model.length/3);
		
		for(int i = 0; i < model.length; i += 3)
		{
			int time = (int) model[i+2];
			
			//first and last waypoints are special -- they specify position
			//at beginning and end of time
			if(i == 0) time = 0;
			else if(i >= model.length-3) time = pmfsbytime.length-1;
			
			int[] xytime = new int[] {(int) model[i], (int) model[i+1], time};
			heap.insert(new rses.util.HeapElement<int[]>(time, xytime));
		}

		//ok, pop them off in order
		java.util.ArrayList<int[]> timepos = new java.util.ArrayList<int[]>();
		while(!heap.isEmpty()) timepos.add(heap.extractMin().getObject());	

		
		//ok, now lets get the path from go to whoa
		int[] xyt1 = timepos.get(0);
		for(int i = 1; i < timepos.size(); i++)
		{
			int[] xyt2 = timepos.get(i);
			int x1 = xyt1[0];
			int y1 = xyt1[1];
			int t1 = xyt1[2];
			int x2 = xyt2[0];
			int y2 = xyt2[1];
			int t2 = xyt2[2];
			if(t2 < t1) throw new RuntimeException("Impossible!");
			//Debug.println("getPosByTime, pass 1, t="+t1+" to t="+t2+". Moving from "+x1+","+y1+" to "+x2+","+y2, Debug.INFO);
			
			if(t2 == t1) 
				continue; //MUST HAVE THIS TO STOP 'CHEAT' WHERE THERE ARE TWO POSITIONS AT ONE TIME
			
			
			//special case where origin and destination are the same. In this case
			//we spend all our time at the origin (and we dont try and find a path tree
			//because there isnt one)
			if(x1==x2 && y1==y2) {
				for(int t = t1; t <= t2; t++) {
					posbytime[t] = new int[] {x1,y1};
					speeds.add(0.0);
				}
				continue;
			}

			List<GraphEdge> edges = rses.math.GraphUtil.getShortestPathFromPathTree(pathtrees[x1][y1], pathtrees[x2][y2].getId());
			List<GraphNode> nodes = rses.math.GraphUtil.getShortestPathNodesFromPathTree(pathtrees[x1][y1], pathtrees[x2][y2].getId());
			for(int n = 0; n < nodes.size(); n++)
			{
				PathNode node = (PathNode) nodes.get(n);
				//Debug.println("Node "+n+" on path: "+node.lati+","+node.loni, Debug.INFO);
			}
			
			//work out total distance travelled
			double disttot = 0.0;
			double sqsz = prior.getSquareSize();
			for(int s = 0; s < edges.size(); s++)
			{
				GraphEdge e = edges.get(s);
				int prex = ((PathNode) e.leadsfrom).lati;
				int prey = ((PathNode) e.leadsfrom).loni;
				int postx = ((PathNode) e.leadsto).lati;
				int posty = ((PathNode) e.leadsto).loni;
				
				//weight by the distance travelled
				double dist = Math.sqrt(Math.pow(sqsz*(prex-postx), 2) + Math.pow(sqsz*(prey-posty), 2));
				disttot += dist; 
			}
			//Debug.println("Total distance travelled is "+disttot+" over "+(t2-t1)+" seconds", Debug.INFO);

			//THIS IS NOT QUITE RIGHT BECAUSE IT MEANS THAT WE ONLY *JUST* END UP
			//AT THE LAST NODE (right at the last second) SO WE EFFECTIVELY SPEND NO TIME THERE
			//ALSO NEED TO INCLUDE PRIORS ON SPEED SOMEHOW
			
			
			//now go back and work out position at each timepoint
			double distpertimestep = disttot/(t2-t1);
			//Debug.println("Thats "+distpertimestep+" metres per second", Debug.INFO);
			int curi = 0;
			PathNode current = (PathNode) nodes.get(curi);
			PathNode next = (PathNode) nodes.get(curi+1);
			double lefttomove = distpertimestep;
			for(int t = t1; t < t2; t++)
			{
				posbytime[t] = new int[] {current.lati, current.loni};
				speeds.add(distpertimestep);
				
				//Debug.println("At time "+t+" we are at "+current.lati+","+current.loni, Debug.INFO);
				double disttonext = Math.sqrt(Math.pow(sqsz*(next.lati-current.lati), 2)+Math.pow(sqsz*(next.loni-current.loni), 2));
				while(disttonext <= lefttomove) 
				{
					lefttomove -= disttonext;
					current = next;
					curi++;
					if(curi == nodes.size()-1) {
						next = null;
						disttonext = Double.POSITIVE_INFINITY;
					}
					else {
						next = (PathNode) nodes.get(curi+1);
						disttonext = Math.sqrt(Math.pow(sqsz*(next.lati-current.lati), 2)+Math.pow(sqsz*(next.loni-current.loni), 2));
					}
				}
				lefttomove += distpertimestep;
			}
			PathNode last = (PathNode) nodes.get(nodes.size()-1);
			posbytime[t2] = new int[] {last.lati, last.loni};

			//Debug.println("At time "+t2+" we are at "+last.lati+","+last.loni, Debug.INFO);
			
			
			xyt1 = xyt2; //move to next stage
		}
		
		return posbytime;
	}
	
	
	
	
}


