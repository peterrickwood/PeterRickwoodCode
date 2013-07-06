package rses.spatial.gui;

import java.awt.Graphics;
import java.awt.Image;

import javax.swing.JPanel;




/** A Panel that displays a model
 * 
 * @author peterr
 *
 */
public class ModelDisplayPanel extends JPanel
{
	private DisplayableModel model;
	private int[][] membership;
	private int[][] placemarks;
	int zoomfact;
	
	public ModelDisplayPanel(DisplayableModel model, int[][] membership,
			int[][] placemarks, int zoomfact)
	{
		this.model = model;
		this.membership = membership;
		this.placemarks = placemarks;
		
		if(zoomfact < 0)
			throw new RuntimeException("negative zoom not supported");
		this.zoomfact = zoomfact;
	}
	
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Image img = model.getDisplay(membership, placemarks, zoomfact);
		g.drawImage(img, 0, 0, null);
	}
	
	
	public int getZoomFact()
	{
		return this.zoomfact;
	}
	
	
	
}

