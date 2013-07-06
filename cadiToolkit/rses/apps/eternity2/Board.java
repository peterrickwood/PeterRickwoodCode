package rses.apps.eternity2;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JFrame;
import javax.swing.JPanel;

import rses.Debug;
import rses.util.Util;





public class Board extends JPanel
{
	Piece[][] board; //null if no piece has been placed
	
	
	
	
	public Board(int x, int y)
	{
		board = new Piece[x][y];
	}
	
	public Board(int n)
	{
		board = new Piece[n][n];
	}
	
	
	public boolean doesPieceFit(Piece p, int x, int y)
	{
		if(board[x][y] != null)
			return false; //there is already a piece here!
				
		//first we check if the piece is an edge piece. If
		//it is, then it cannot fit in the center
		if(x > 0 && x < board.length-1 && y > 0 && y < board[0].length-1 &&
		   Util.getIndex(-1, p.faces) != -1)
			return false;
		
		//find out if its a corner or edge piece
		int edgecount = 0;
		for(int i = 0; i < p.faces.length; i++)
			if(p.faces[i] == -1)
				edgecount++;
		
		//a non edge piece cannot fit on an edge or corner
		if(edgecount == 0 && (x == 0 || x == board.length-1 || y == 0 || y == board[0].length-1))
			return false;
		//an edge piece cannot go in a corner
		if(edgecount == 1)
		{
			if(x==0 && (y == 0 || y == board[0].length-1))
				return false;
			if(x == board.length-1 && (y == 0 || y == board[0].length-1))
				return false;
		}
		//a corner piece cannot go on an edge
		else if(edgecount == 2)
		{
			if(x != 0 && x != board.length-1)
				return false;
			if(y != 0 && y != board[0].length-1)
				return false;
		}
		
		
		
		//check each face to make sure colours match
		
		//check west face
		if(x == 0) {
			if(p.getWest() != -1) return false; //must be an edge piece
		}
		else if(board[x-1][y] != null && board[x-1][y].getEast() != -2 &&
				p.getWest() != -2 && board[x-1][y].getEast() != p.getWest())
			return false;
		
		//check east face
		if(x == board.length-1) {
			if(p.getEast() != -1) return false; //must be an edge piece
		}
		else if(board[x+1][y] != null && board[x+1][y].getWest() != -2 &&
				p.getEast() != -2 && board[x+1][y].getWest() != p.getEast())
			return false;
		
		//check north face
		if(y == board[0].length-1) {
			if(p.getNorth() != -1) return false; //must be an edge piece
		}
		else if(board[x][y+1] != null && board[x][y+1].getSouth() != -2 && 
				p.getNorth() != -2 && board[x][y+1].getSouth() != p.getNorth())
			return false;
		
		//check south face
		if(y == 0) {
			if(p.getSouth() != -1) return false; //must be an edge piece
		}
		else if(board[x][y-1] != null && board[x][y-1].getNorth() != -2 &&
				p.getSouth() != -2 && board[x][y-1].getNorth() != p.getSouth())
			return false;
		
		
		//ok, all faces match, so it fits
		return true;
	}	
	
	public static final int displaysize = 600;
	
	public void showBoard(int x, int y, String name)
	{
		JFrame f = new JFrame();
		f.setTitle(name);
		f.setSize((int) (displaysize*1.1), (int) (displaysize*1.1));
		f.setLocation(x, y);
		this.setSize(displaysize, displaysize);
		f.getContentPane().add(this);
		f.setVisible(true);
		//this.paintBoard(p.getGraphics(), 600, 600, Piece.piececolours);
	}
	
	
	/** paint the current board on the screen
	 * 
	 * @param g
	 */
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		
		int squaresize = displaysize/this.board.length;
		
		g.setFont(g.getFont().deriveFont(12.0f));
		
		for(int x = 0; x < board.length; x++)
		{
			for(int y = 0; y < board[0].length; y++)
			{
				int xpix = x*squaresize;
				int ypix = displaysize-(y+1)*squaresize;
				if(board[x][y] == null) {
					g.setColor(Color.gray);
					g.fillRect(xpix, ypix, squaresize, squaresize);
				}
				else
				{
					board[x][y].paint(g.create(xpix, ypix, squaresize, squaresize), squaresize, squaresize, Piece.piececolours);
				}
				
				g.setColor(Color.lightGray);
				g.drawString(""+E2Util.calcLinearizedBoardIndex(this, x, y), xpix+squaresize/4, ypix+squaresize/2);
			}
		}
	}
	
	
	
	/** Just show the board
	 * 
	 * @param args
	 */
	public static void main(String[] args) throws Exception
	{
		Debug.setDebugLevel(Debug.DEBUG_SUSPICIOUS);
		Debug.setVerbosityLevel(Debug.EXTRA_INFO);
		
		Piece[] pieces = E2Util.readPiecesFromFile(args[0]);
		boolean[] isplaced = new boolean[pieces.length];
		int boardsize = (int) Math.round(Math.sqrt(pieces.length));
		
		if(boardsize*boardsize != isplaced.length)
			throw new RuntimeException("# of pieces mean that board cannot be square!");
		
		Board b = new Board(boardsize);

		
		//now place all the pieces in the specified squares
		for(int i = 0; i < pieces.length; i++)
			if(pieces[i].pos >= 0)
			{
				if(pieces[i].pos > pieces.length)
					throw new RuntimeException("Specified board position for piece does not exist (out of range)");
				isplaced[i] = true;
				int[] xy = E2Util.calcXYfromSquareNum(b, pieces[i].pos);
				if(!b.doesPieceFit(pieces[i], xy[0], xy[1]))
					throw new RuntimeException("Piece does not fit in specified position...");
				b.board[xy[0]][xy[1]] = pieces[i];
			}
		
		//a board where piece i is in square i,
		//unless it is placed
		Board unplaced = new Board(boardsize);
		for(int i =0; i < pieces.length; i++)
		{
			if(!isplaced[i])
			{
				int[] xy = E2Util.calcXYfromSquareNum(unplaced, i);
				unplaced.board[xy[0]][xy[1]] = pieces[i];
			}
		}
		

		b.showBoard(0, 0, "Board");
		unplaced.showBoard(600, 0, "Unplaced pieces");
	}
}

