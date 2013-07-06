package rses.visualisation;

import java.awt.Graphics;

import rses.Debug;






/** A derivative of JPanel that displays a data source 
 * 
 */
public class DataSourcePanel extends javax.swing.JPanel
{
	private VisualDataSource todisplay;
	private int updateinterval;
	
	
	/** Create a Panel that displays a VisualDataSource and updates
	 *  the display of that data source every updateinterval_ms
	 *  milliseconds.
	 * 
	 * @param todisplay
	 * @param updateinterval_ms
	 */
	public DataSourcePanel(VisualDataSource todisplay, int updateinterval_ms)
	{
		this.updateinterval = updateinterval_ms;
		this.todisplay = todisplay;
		
		//schedule updates
		if(updateinterval_ms > 0)
		{
			new Thread() 
			{ 
				public void run() 
				{
					long start = System.currentTimeMillis();
					long lastupdate = start;
					while(true) 
					{
						try { Thread.sleep(updateinterval); }
						catch(InterruptedException ie) {}
						if(DataSourcePanel.this.todisplay.hasChanged()) 
						{
							long cur = System.currentTimeMillis();
							if(cur - lastupdate > updateinterval) 
							{
								Debug.println("repainting DataSourcePanel at "+(((double) (cur-start))/1000), Debug.EXTRA_INFO);
								repaint();
								lastupdate = System.currentTimeMillis();
							}
						}
					}
				}
			}.start();
		}
	}
	
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		if(g == null) return;
			
		int width = this.getWidth();
		int height = this.getHeight();
		todisplay.draw(g, width, height);
	}
	
}
