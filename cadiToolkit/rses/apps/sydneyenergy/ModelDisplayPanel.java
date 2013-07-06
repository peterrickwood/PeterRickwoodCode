package rses.apps.sydneyenergy;

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
	
	public ModelDisplayPanel(DisplayableModel model, int[][] membership,
			int[][] placemarks)
	{
		this.model = model;
		this.membership = membership;
		this.placemarks = placemarks;
	}
	
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Image img = model.getDisplay(membership, placemarks);
		g.drawImage(img, 0, 0, null);
	}
}

