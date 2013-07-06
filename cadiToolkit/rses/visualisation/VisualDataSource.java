package rses.visualisation;






/** An interface to enable the getting of visual data from some
 *  data source.
 * 
 * @author peterr
 *
 */
public interface VisualDataSource
{
	public void draw(java.awt.Graphics g, int xpixels, int ypixels);
	
	/** Has the data source changed since the last time its
	 *  draw method was called.
	 * @return
	 */
	public boolean hasChanged();
	
}