package rses.util;

import rses.Debug;;






/** An ascending order heap
 * 
 * Implementation note:
 * 
 * If you instantiate this Heap with the Heap(int sizeHint) constructor, you get a vanilla 
 * heap implementation, which has the following complexity:
 *             insert, extractMin:   O(ln N)
 *             peek: O(1)
 *             remove: O(N)
 * 
 * If you instead instantiate with Heap(int sizeHint, true), then the Heap will support 'fast' [O(ln N)] removal,
 * but you will of course pay for this with slower operations (same complexity, but slower) and some 
 * additional memory consumption. 
 * 
 * 
 * 
 * 
 * @author peterr
 *
 */
public class Heap <A>
{
	//Map which keeps track of the location of each heap element in the heap
	//this allows element deletion to be done in O(ln N) time rather than linear time
	private java.util.Map<HeapElement<A>, Integer> heapIndices = null;
	
	private HeapElement<A>[] elements;
	private int heapsize = 0;

	public Heap(int sizeHint)
	{
		this(sizeHint, false);
	}
	
	public Heap(int sizeHint, boolean supportFastDeletion)
	{
		if(supportFastDeletion)
			heapIndices = new java.util.HashMap<HeapElement<A>, Integer>(sizeHint);
		elements = new HeapElement[sizeHint+1];
	}

	public boolean isEmpty()
	{
		return heapsize == 0;
	}
	
	private void heapify(int index)
	{
		int left = index*2;
		int right = left+1;
		int smallest = index;
		
		while(true)
		{		
			//work out whether the existing root, left, or right is the minimum
			if(left <= heapsize && elements[left].getHeapValue() < elements[index].getHeapValue())
				smallest = left;
		
			if(right <= heapsize && elements[right].getHeapValue() < elements[smallest].getHeapValue())
				smallest = right;
			
			if(smallest != index) //the existing root is not the smallest, so we need to swap
			{
				HeapElement<A> tmp = elements[index];
				elements[index] = elements[smallest];
				elements[smallest] = tmp;
				if(heapIndices != null) 
				{
					heapIndices.put(elements[index], index);
					heapIndices.put(elements[smallest], smallest);
				}
				index = smallest;
				left = index*2;
				right = left+1;
			}
			else 
				break;
		}
	}
	

	public void insert(HeapElement<A> elem)
	{
		//stick it at the end of the heap
		int i = heapsize+1;
		heapsize++;
		
		//check if we need to resize
		if(heapsize == elements.length) //no room for insert
		{
			//resize the heap
			HeapElement<A>[] newelements = new HeapElement[heapsize*2];
			for(int j = 1; j < heapsize; j++)
				newelements[j] = elements[j];
			elements = newelements;
		}
		
		//now shift it up the heap until the heap property is satisfied
		while(i > 1 && elements[i/2].getHeapValue() > elem.getHeapValue())
		{
			elements[i] = elements[i/2];
			if(heapIndices != null) heapIndices.put(elements[i], i);
			i /= 2;
		}
		
		elements[i] = elem;
		if(heapIndices != null) heapIndices.put(elements[i], i);
	}


	public HeapElement<A> extractMin()
	{
		if(heapsize < 1)
			throw new RuntimeException("Empty Heap");
			
		HeapElement<A> min = elements[1];
		elements[1] = elements[heapsize];
		if(heapIndices != null) heapIndices.put(elements[1], 1);
		heapsize--;
		heapify(1);
		return min;
	}



	public HeapElement<A> peekMin()
	{
		if(heapsize < 1)
			throw new RuntimeException("Empty Heap");

		return elements[1];		
	}

	
	/** 
	 * Remove an arbitrary element from the heap.
	 * 
	 * IMPORTANT NOTE: The removal of 'elem' is based on a primitive equality comparison (==),
	 * not on a  ".equals()" based comparison. Thus, while a.equals(b) may be true, 
	 * heap.remove(a) will ONLY remove a from the heap, not b, nor any other objects 
	 * with the same value as a. 
	 * 
	 * @param elem
	 */
	public void remove(HeapElement<A> elem)
	{
		if(heapsize == 0)
			throw new RuntimeException("Cannot remove element from empty heap");
		
		if(this.heapIndices != null) {
			throw new UnsupportedOperationException("Not enabled yet");
			//fastRemove(elem);
			//return;
		}
		
		//linear search till we find it
		int index = 1;
		while(index <= this.heapsize && elements[index] != elem)
			index++;
		
		//Debug.println("Removing element at index "+index+" from queue with "+heapsize+" elements", Debug.INFO);
		
		if(index == heapsize) 
		{
			//Debug.println("Removing last element in heap", Debug.INFO);
			//last element, easy case to delete
			elements[index] = null;
			heapsize--;
		}
		else if(index < heapsize)
		{	
			//Debug.println("Removing other element in heap", Debug.INFO);
			
			elements[index] = elements[heapsize]; //delete the element and fill it with the last element in the heap
			elements[heapsize] = null; //not really necessary
			heapsize--; 
			
			//now, there are 2 possibilities. Either the element we have used to fill the hole needs to move up the heap
			//or else it needs to move down the heap 
			
			//try bubbling the hole-filler up as high as it can go
			int i = index;
			while(i > 1 && elements[i/2].getHeapValue() > elements[i].getHeapValue()) 
			{
				HeapElement<A> tmp = elements[i/2];
				elements[i/2] = elements[i];
				elements[i] = tmp;
				i /= 2;
			}
			//if we didnt bubble, then we need to heapify instead (i.e. shift the filler down)
			if(i == index)
				heapify(index);
		}
		else
			throw new RuntimeException("Request to remove element from queue cannot be honoured because queue has no such element");
		
	}


	private void fastRemove(HeapElement<A> elem)
	{
		//we dont need to search to find where it is, we just look it up
		Integer indexi = heapIndices.get(elem);
		if(indexi == null)
			throw new RuntimeException("Cannot remove element from heap because heap contains no such element");
			

		throw new UnsupportedOperationException("Not implemented yet");
		
	}

	/* Assume that the internal structure of the heap is messed with.
	 * This operation reestablishes the heap from an arbitrary position
	 */
	public void fixHeap()
	{
		for(int i = elements.length/2; i >= 1; i--)
			heapify(i);
	}


	public void print()
	{
		for(int i = 1; i <= heapsize; i++)
		{
			System.out.println(i+" "+elements[i].getHeapValue()+": "+elements[i]);
		}
	}
	
	
	/** Returns the number of elements currently in the heap.
	 * 
	 * @return the number of elements in the heap.
	 */
	public int getHeapSize()
	{
		return this.heapsize;
	}
	
	
	//makes sure that the heap is in fact a heap
	public void checkHeap()
	{
		int left;
		int right;
		for(int i = 1; i <= heapsize; i++)
		{
			left = i*2;
			right = left+1;
			
			if(left <= heapsize && elements[left].getHeapValue() < elements[i].getHeapValue())
				throw new RuntimeException("heap is in illegal state -- item @ "+left+" has value "+elements[left].getHeapValue()+" but parent @ "+i+" has value "+elements[i].getHeapValue());
		
			if(right <= heapsize && elements[right].getHeapValue() < elements[i].getHeapValue())
				throw new RuntimeException("heap is in illegal state -- item @ "+right+" has value "+elements[right].getHeapValue()+" but parent @ "+i+" has value "+elements[i].getHeapValue());
		}
	}
	
	
	public static void main(String[] args)
	{
		java.util.Random rand = new java.util.Random();
		int testsize = (int) (rand.nextDouble()*20);
 		Heap h = new Heap(testsize);
		HeapElement[] elem = new HeapElement[20];

		for(int i = 0; i < 20; i++)
		{
			elem[i] = new rses.util.HeapElement((int) (rand.nextDouble()*20), null);
			h.insert(elem[i]);
		}
		
		h.checkHeap();
		System.out.println("printing heap in array order");
		h.print();
		System.out.println();
		System.out.println("now printing in heap order");
		for(int i = 0; i < 20; i++)
		{
			System.out.println("  val = "+h.extractMin().getHeapValue());
		}
		System.out.println();
	}
}



