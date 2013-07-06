package rses.inverse.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rses.Model;
import rses.inverse.ModelObserver;
import rses.inverse.ObservableInverter;
import rses.inverse.UserFunctionHandle;
import rses.util.Heap;
import rses.Debug;
import rses.util.HeapElement;





public class ModelFile implements ObservableInverter
{
	private boolean isFinished = false;

	private ArrayList observers = new ArrayList();

	private BufferedReader modelFile;
	private File modfile;
	private double besterr = Double.POSITIVE_INFINITY;
	private rses.Model bestModel = null;
	private int numModels = 0;
	private long latestModel = 0L;
	
	private UserFunctionHandle handle = null;




	public ModelFile(File f) throws IOException
	{
		this(f, false);
	}
	
	
	
	private ModelFile(File f, boolean storeModelsInMemory) throws IOException
	{
		this.modfile = f;
		this.cacheModels = storeModelsInMemory;
		modelFile = new BufferedReader(new java.io.FileReader(f));
		 
		try { 
			this.handle = FakeUserFunctionHandle.read(modelFile); 
		}
		catch(Exception e) 
		{ 
			if(modelFile != null) modelFile.close();
			throw new IOException("Error reading from file.... "+e.getMessage());
		}
	}
	
	
	/** Get the model with rank <code>rank</code>
	 * 
	 *   This method gets less and less efficient the larger rank is.
	 *   (I havent worked out the actual average case computational 
	 *   complexity, but it is worst-case rank*Log(rank) complexity.
	 *   Probably average case is similar, but with much smaller 
	 *   coefficients. Worst case occurs when models are already
	 *   sorted in reverse order (worst misfit to best misfit)).
	 *   Memory requirement scale linearly in 
	 *   <code>rank</code>.   
	 * 
	 * @param rank
	 * @return
	 */
	public Model getModelByRank(int rank) throws IOException
	{
		//if the models have already been read in, then we
		//just use them.
		if(this.models != null && this.models.size() > 0)
		{ 
			Heap<Model> heap = new Heap<Model>(rank);
			double maxval = Double.NEGATIVE_INFINITY;
			for(int i =0; i < models.size(); i++)
			{
				Model m = (Model) models.get(i);
				if(heap.getHeapSize() < rank)
				{
					if(m.getMisfit() > maxval) maxval = m.getMisfit();
					heap.insert(new HeapElement<Model>(m.getMisfit(), m));
				}
				else if(m.getMisfit() < maxval)
				{
					maxval = m.getMisfit();
					heap.insert(new HeapElement<Model>(m.getMisfit(), m));
				}
			}
			if(heap.getHeapSize() < rank)
				return null;
			for(int i=0; i < rank-1; i++)
				heap.extractMin();
			return heap.extractMin().getObject();
		}
		else //otherwise, we read them in
		{
			//start reading from the start of the file
			modelFile = new BufferedReader(new java.io.FileReader(this.modfile));
			
			//get rid of the functionhandle stuff
			FakeUserFunctionHandle.read(modelFile);
			
			Heap<Model> heap = new Heap<Model>(rank+1);
			double maxval = Double.NEGATIVE_INFINITY;
			int count = 0;
			int numread = 0;
									
			String line = modelFile.readLine();
			while(line != null)
			{
				rses.Model model = null;
				try { model = rses.Model.fromString(line); }
				catch(RuntimeException e) { }
				if(model == null)
				{
					Debug.println("[Error]... could not read model from line: ", Debug.CRITICAL);
					Debug.println(line, Debug.CRITICAL);
					Debug.println("skipping line  [End Error]", Debug.CRITICAL);
				}
				else if(count < rank) {
					if(model.getMisfit() > maxval) maxval = model.getMisfit();
					heap.insert(new HeapElement<Model>(model.getMisfit(), model));
					count++;
				}
				else if(model.getMisfit() < maxval)
				{
					if(rank == 1) { //special case for rank == 1
						heap.extractMin();
						heap.insert(new HeapElement<Model>(model.getMisfit(), model));
						maxval = model.getMisfit();
					}
					else if(rank <= 32) { //special case for *small* values of rank
						heap.insert(new HeapElement<Model>(model.getMisfit(), model));
						
						//do a heapsort and take the best 'rank' models
						Heap<Model> newheap = new Heap<Model>(rank+1);
						for(int i = 0; i < rank-1; i++)
							newheap.insert(heap.extractMin());
						HeapElement<Model> lastmodelem = heap.extractMin();
						maxval = lastmodelem.getObject().getMisfit();
						newheap.insert(lastmodelem);
						heap = newheap;	
					}
					else { //general case for non-small values of
						heap.insert(new HeapElement<Model>(model.getMisfit(), model)); 
					}
				} 
				
				numread++;
				if(numread % 1000 == 0)
					Debug.println("processed "+numread+" lines", Debug.IMPORTANT);
				line = modelFile.readLine();
			}
			
			if(heap.getHeapSize() < rank)
				return null;
			for(int i =0; i < rank-1; i++) 
				System.out.println(heap.extractMin());
			return heap.extractMin().getObject();
		}
	}
	
	
	
	
	
	public void addModelObserver(rses.inverse.ModelObserver m)
	{
		observers.add(m);
	}
	
	
	public void removeModelObserver(rses.inverse.ModelObserver observer)
	{
		this.observers.remove(observer);
	}

	
	
	public void run()
	{
		this.isFinished = false;
		try {internalRun(); }
		catch(Exception e)
		{
			if(e instanceof RuntimeException)
				throw (RuntimeException) e;
			else
				throw new RuntimeException(e);
		}
		finally
		{
			this.isFinished = true;
		}
	}

	
	
	
	private void internalRun() throws Exception
	{
		//start reading from the start of the file
		modelFile = new BufferedReader(new java.io.FileReader(this.modfile));
		
		//skip header
		FakeUserFunctionHandle.read(modelFile);
		
		String line = modelFile.readLine();
		
		StringBuffer sbl = new StringBuffer(4096);
		while(true)
		{
			//read in the next model and notify all observers
			readModel(line);
			
			while(true)
			{
				if(!modelFile.ready()) {
					Thread.sleep(1000);
					continue;
				}
				int c = modelFile.read();
				if(c == '\n')
					break;
				else
					sbl.append((char) c); 
			}
			line = sbl.toString();
			sbl.delete(0, sbl.length()); //clear our stringbuffer 
		}
	}
	
	
	
	
	
	private boolean cacheModels = false;
	private List models = Collections.synchronizedList(new ArrayList());
	
	private void readModel(String line)
	{
		rses.Model model = null;
		try { model = rses.Model.fromString(line); } catch(RuntimeException e) {}
		if(model == null)
		{
			Debug.println("[Error]... could not read model from line: ", Debug.CRITICAL);
			Debug.println(line, Debug.CRITICAL);
			Debug.println("skipping line  [End Error]", Debug.CRITICAL);
			return;
		}
		
		//we got a valid model.... so tell all observers
		numModels++;
		if(model.getDiscoveryTime() > latestModel)
			latestModel = model.getDiscoveryTime();
		if(model.getMisfit() < besterr)
		{
			besterr = model.getMisfit();
			bestModel = model;
		}
		
		for(int i = 0; i < observers.size(); i++)
			((ModelObserver) observers.get(i)).newModelFound(model);
		if(cacheModels)
			models.add(model);
			
		//and we're done!
	}
	
	
	
	
	
	public rses.Model getBestModel()
	{
		return bestModel;
	}
	
	public UserFunctionHandle getUserFunctionHandle()
	{
		return handle;
	}
	
	
	public String getStageName()
	{
		return "Number of Models";
	}
	
	public double getStage()
	{
		return numModels;
	}
	
	
	public long getRunningTime()
	{
		return latestModel;
	}
	
	
	public boolean isFinished()
	{
		return this.isFinished;
	}
	
}