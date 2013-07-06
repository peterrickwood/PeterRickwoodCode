package rses.util;





public class HeapElement <A>
{
	A obj;
	Number val;
	
	public HeapElement(Number n, A thing) {
		this.val = n;
		this.obj = thing;
	}
	
	/** 
	 * Get the value of the element, to
	 * be used in the ordering of the heap
	 */
	public double getHeapValue() {
		return val.doubleValue();
	}
	
	public void setHeapValue(Number n) {
		val = n;
	}
	
	public A getObject() {
		return obj;
	}
	
	public void setObject(A thing) {
		obj = thing;
	}
}
