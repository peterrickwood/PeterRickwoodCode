package rses.apps.eternity2;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import rses.Debug;
import rses.util.ArgumentParser;
import rses.util.Heap;
import rses.util.HeapElement;



public class BruteSolver 
{
	public static final int COUNT = 0;
	public static final int HALT = 1;
	public static final int PRINTALL = 2;
	
	
	static int globsol = 0;
	static long searchcount = 0;
	
	public static void main(String[] args) throws Exception
	{
		Debug.setDebugLevel(Debug.DEBUG_SUSPICIOUS);
		Debug.setVerbosityLevel(Debug.EXTRA_INFO);
		
		int mode = COUNT;
		ArgumentParser parser = new ArgumentParser(args);
		
		if(parser.isOption("halt"))
			mode = HALT;
		else if(parser.isOption("printall"))
			mode = PRINTALL;

		int boardx = -1;
		int boardy = -1;
		String boardstr = parser.getValue("--board");
		if(boardstr != null) {
			String[] bits = boardstr.split("x");
			boardx = Integer.parseInt(bits[0]);
			boardy = Integer.parseInt(bits[1]);
		}
		else
			Debug.println("No --board option specified, assuming square board", Debug.IMPORTANT);
		
		
		Piece[] pieces = null;
		
		
		if(boardx == -1) { //no boardsize specified
			pieces = E2Util.readPiecesFromFile(args[0]);
			int boardsize = (int) Math.round(Math.sqrt(pieces.length));
			boardx = boardsize;
			boardy = boardsize;
			if(boardsize*boardsize != pieces.length)
				throw new RuntimeException("# of pieces mean that board cannot be square!");
		}
		else { //boardsize specified
			pieces = E2Util.readPiecesFromFile(args[0], false);
			if(pieces.length < boardx*boardy)
				throw new RuntimeException("There are not enough pieces to fill a board of the specified size!!");
		}

		boolean[] isplaced = new boolean[pieces.length];

		
		
		Board b = new Board(boardx, boardy);

		//now place all the pieces in the specified squares,
		//if they have starting squares specified
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
		
		
		int nsol = getSolutions(b, pieces, isplaced, mode);
		if(mode == COUNT) 
		{
			Debug.println("There are "+nsol+" solutions to this board", Debug.IMPORTANT);
			Debug.println("Searched "+searchcount+" nodes in complete search tree", Debug.IMPORTANT);
		}
	}
	
	
	

	//get all the solutions with this initial board state
	public static int getSolutions(Board b, Piece[] p, boolean[] isplaced, int mode)
	{		
		LinkedList unplacedpieces = new LinkedList();

		for(int i = 0; i < isplaced.length; i++)
			if(!isplaced[i])
				unplacedpieces.add(p[i]);
		
		LinkedList emptysquares = new LinkedList();
		Heap<int[]> heap = new Heap<int[]>(p.length);
		for(int x = 0; x < b.board.length; x++)
			for(int y = 0; y < b.board[0].length; y++)
				if(b.board[x][y] == null)
				{
					int[] boardpos = new int[] {x, y};
					
					//work out how constrained the square is
					int constraintcount = 0;
					if(x == 0 || b.board[x-1][y] != null)
						constraintcount++;
					if(x == b.board.length-1 || b.board[x+1][y] != null)
						constraintcount++;
					if(y == 0 || b.board[x][y-1] != null)
						constraintcount++;
					if(y == b.board.length-1 || b.board[x][y+1] != null)
						constraintcount++;
					
					//when constraints are tied, make sure we go from
					//adjacent square to adjacent square. This is a
					//fudge to do this.
					double squareindex = E2Util.calcLinearizedBoardIndex(b, x, y)-0.1;
					squareindex /= b.board.length*b.board.length;
					
					//4-constraintcount because heap is in ascending order
					heap.insert(new HeapElement<int[]>(4-constraintcount+squareindex,boardpos));
				}

		
		//now order board squares so that more tightly constrained
		//elements are first
		while(!heap.isEmpty())
		{
			int[] pos = heap.extractMin().getObject();
			emptysquares.add(pos);
		}
		
		
		//now look at all combinations
		int nsols = getSolutions(b, unplacedpieces, emptysquares, mode);
		return nsols;
	}
	
	
	
	public static int getSolutions(Board b, List pieces, List squares, int mode)
	{
		//special base case, no squares left to put pieces in.
		//This is here because sometimes we want to run the
		//solver with more pieces than there are squares on the board.
		if(squares.size() == 0) {  //board is full
			globsol++;
			if(mode == HALT || mode == PRINTALL)
				printSolution(b, globsol);
			if(mode == HALT)
				System.exit(1);
			return 1; //yes, indicate that this is a solution
		}
		
		
		if(pieces.size() == 1) //base case, only 1 piece left
		{ 
			Piece p = (Piece) pieces.get(0);
			int[] xy = (int[]) squares.get(0);
			int nsol = 0;
			for(int r = 0; r < 4; r++, p.rotate())
			{
				searchcount++;
				if(b.doesPieceFit(p, xy[0], xy[1]))
				{
					nsol++; //got a solution
					globsol++; //a hack.

					if(mode == HALT || mode == PRINTALL) 
					{
						//print out the solution
						b.board[xy[0]][xy[1]] = p;
						printSolution(b, globsol);
						
						if(mode == HALT)
							System.exit(1);
						else 
							b.board[xy[0]][xy[1]] = null; //remove piece
					}
				}
			}
			return nsol;
		}
		
		int nsol = 0;
		//in non-base case. Just try all pieces in
		//the first square
		int[] xy = (int[]) squares.get(0);
		int ncombs = pieces.size();
		for(int i = 0; i < ncombs; i++)
		{
			Piece p = (Piece) pieces.get(i);
			
			for(int r = 0; r < 4; r++, p.rotate())
			{
				searchcount++;
				if(b.doesPieceFit(p, xy[0], xy[1]))
				{
					b.board[xy[0]][xy[1]] = p; //place the piece
					//now see if we can get a solution with remaining pieces
					List newpieces = new LinkedList(pieces);
					newpieces.remove(i);
					nsol += getSolutions(b, newpieces, squares.subList(1, squares.size()), mode);
					b.board[xy[0]][xy[1]] = null; //undo the move
				}
				else  
					/* It doesnt fit, so we try the next piece */;
			}
		}
		
		return nsol;
	}

	
	
	
	public static void printSolution(Board b, int solnum)
	{
		System.out.println("#SOLUTION "+solnum);
		for(int x = 0; x < b.board.length; x++)
		{
			for(int y = 0; y < b.board[0].length; y++)
			{
				int index = E2Util.calcLinearizedBoardIndex(b, x, y);
				Piece p = b.board[x][y];
				System.out.println(p.toString()+" "+index);
			}
		}
	}
	
	
	
}
