package rses.apps.eternity2;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

public class Piece
{
	//the board position of the piece. This is 
	//just an ugly hack to let pieces be assigned to
	//board squares as they are read in.
	int pos = -1;
	
	public static Color[] piececolours = new Color[] {
		Color.blue, Color.orange, Color.magenta, Color.pink, Color.cyan, 
		Color.green, Color.red, Color.yellow, Color.white, Color.black
	};
	
	int[] faces; //-1 is an edge face, otherwise, its a colour
	int rotation = 0; //current rotation
	
	public static final int NORTH = 0;
	public static final int EAST = 1;
	public static final int SOUTH = 2;
	public static final int WEST = 3;
	

	public Piece(int[] colours)
	{
		this.faces = new int[] {colours[0], colours[1], colours[2], colours[3]};		
	}
	
	
	/** Rotate the square anti-clockwise
	 */
	public void rotate()
	{
		this.rotation = (rotation + 1) % 4;
	}	
	
	
	public int getNorth()
	{
		return faces[(rotation+NORTH) % 4];
	}

	public int getSouth()
	{
		return faces[(rotation+SOUTH) % 4];
	}

	public int getEast()
	{
		return faces[(rotation+EAST) % 4];
	}

	public int getWest()
	{
		return faces[(rotation+WEST) % 4];
	}

	public static final Color[] specCols = new Color[] {null, Color.gray, Color.darkGray};
	public void paint(Graphics g, int width, int height, Color[] colours)
	{
		
		g.setColor(getNorth() < 0 ? specCols[-getNorth()] : colours[getNorth() % colours.length]);
		g.fillPolygon(new int[] {0, width/2, width}, new int[] {0, height/2, 0}, 3);
		g.setColor(getSouth() < 0 ? specCols[-getSouth()] : colours[getSouth()% colours.length]);
		g.fillPolygon(new int[] {0, width/2, width}, new int[] {height, height/2, height}, 3);
		g.setColor(getEast() < 0 ? specCols[-getEast()] : colours[getEast()% colours.length]);
		g.fillPolygon(new int[] {width, width/2, width}, new int[] {0, height/2, height}, 3);
		g.setColor(getWest() < 0 ? specCols[-getWest()] : colours[getWest()% colours.length]);
		g.fillPolygon(new int[] {0, width/2, 0}, new int[] {0, height/2, height}, 3);
		g.setColor(Color.BLACK);
		g.drawRect(0, 0, width, height);
		g.drawRect(1, 1, width-2, height-2);
		g.drawLine(0, 0, width, height);
		g.drawLine(width, 0, 0, height);
	}
	
	
	public String toString()
	{
		return ""+getNorth()+" "+getEast()+" "+getSouth()+" "+getWest();
	}
}