package rses.util.gui;





/** A JPanel that will reset its own preferred size so that its width
 *  is always the same as its parent container. This behaviour is typically
 *  only useful when the Panel is inside a ScrollPane that you want to
 *  expand vertically but not really horizontally.
 * 
 * @author peterr
 *
 */
public class VerticallyResizingJPanel extends javax.swing.JPanel
{
	public java.awt.Dimension getPreferredSize()
	{
		java.awt.Component[] components = this.getComponents();
		int width = this.getParent().getWidth();
		int height = 0;
		for(int i =0; i < components.length; i++) 
		{
			int x = components[i].getLocation().x + components[i].getWidth();
			int y = components[i].getLocation().y + components[i].getHeight();
			if(y > height)
				height = y;
			if(x > width)
				width = x;
		}
		return new java.awt.Dimension(width, height);
		
	}
	
	
}