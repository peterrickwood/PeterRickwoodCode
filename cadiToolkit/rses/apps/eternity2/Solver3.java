package rses.apps.eternity2;



import java.util.ArrayList;

import rses.Debug;
import rses.util.Util;


/** A solver that tries a probabilistic placement of pieces
 *  and then iterates.
 * 
 *  Based on Solver1, but doesnt choose the most probable piece,
 *  but instead the piece that results in the best board.
 * 
 * @author peterr
 *
 */
public class Solver3
{
	public static int convergeiterations = -1;
	public static final int normalizeiterations = 2;
	
	
	public static void main(String[] args) throws Exception
	{
		boolean pause = (args.length > 1);
			
		Debug.setDebugLevel(Debug.DEBUG_SUSPICIOUS);
		Debug.setVerbosityLevel(Debug.EXTRA_INFO);
		
		Piece[] pieces = E2Util.readPiecesFromFile(args[0]);
		boolean[] isplaced = new boolean[pieces.length];
		int boardsize = (int) Math.round(Math.sqrt(pieces.length));
		convergeiterations = boardsize; 
		
		if(boardsize*boardsize != isplaced.length)
			throw new RuntimeException("# of pieces mean that board cannot be square!");
		
		Board b = new Board(boardsize);
		//a board where piece i is in square i,
		//unless it is placed
		Board unplaced = new Board(boardsize);
		for(int i =0; i < pieces.length; i++)
		{
			int[] xy = calcXYfromSquareNum(unplaced, i);
			unplaced.board[xy[0]][xy[1]] = pieces[i];
		}
		

		b.showBoard(0, 0, "Board");
		unplaced.showBoard(700, 0, "Unplaced pieces");

		
		boolean userspecifiedmove = false;

		//now make moves
		for(int i = 0; i < pieces.length; i++)
		{
			if(userspecifiedmove)
				/* dont try an automatic move if the user entered the last one*/;
			else if(!doMove(b, pieces, isplaced))
			{
				Debug.println("Got to point where there is no legal move. Aborting game", Debug.IMPORTANT);
				System.exit(1);
			}
			
			//remove the placed piece
			for(int j = 0; j < isplaced.length; j++)
			{
				int[] xy = calcXYfromSquareNum(unplaced, j);
				if(isplaced[j] && unplaced.board[xy[0]][xy[1]] != null)
				{
					unplaced.board[xy[0]][xy[1]] = null;
					break;
				}
			}
			b.repaint();
			unplaced.repaint();
			
			if(pause) {
				java.io.BufferedReader rdr = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
				String line = rdr.readLine();
				if(line.trim().length() != 0) {
					userspecifiedmove = true;
					String[] words = Util.getWords(line);
					int bi = Integer.parseInt(words[0]);
					int pi = Integer.parseInt(words[1]);
					int rot = Integer.parseInt(words[2]);
					//place piece in square 
					Piece p = pieces[pi];
					if(p == null)
						throw new RuntimeException("Specified piece is already placed");
					for(int r =0; r < rot; r++) p.rotate();
					int[] bxy = calcXYfromSquareNum(b, bi);
					if(b.board[bxy[0]][bxy[1]] != null)
						throw new RuntimeException("Specified board square is already taken");
					b.board[bxy[0]][bxy[1]] = p;
					isplaced[pi] = true;
				}
				else 
					userspecifiedmove = false;
			}
		}
	}
	
	
	/** Make the next move on the board
	 * 
	 * @param b
	 */
	public static boolean doMove(Board b, Piece[] pieces, boolean[] isplaced)
	{
		double[][][] matrix = null;

		int boardindex = -1;
		int pieceindex = -1;

		
		//get the move matrix
		//the move matrix is [boardsquarenumber][piecenumber][rotation]
		try {
			matrix = calcProbabilityMatrix(b, pieces, isplaced);
		}
		catch(ForcedMoveException forced) {
			Debug.println("Forced Move found... doing that one", Debug.IMPORTANT);
			boardindex = forced.boardindex;
			pieceindex = forced.pieceindex;
			matrix = forced.pmatrix;
			Debug.println("(incomplete) matrix for forced move is printed below: ", Debug.EXTRA_INFO);
			printprobmatrix(matrix, b);
		}
		
		
		//only go looking for the best moves if there isnt a forced one
		if(boardindex == -1)
		{
			if(matrix == null) //there was at least one square where we could not put a piece
				return false;
		
			
			//find the board square that has the fewest 'possible'
			//pieces in it, and pick the max probability
			//piece from that
			int mincount = Integer.MAX_VALUE;
			for(int i = 0; i < matrix.length; i++)
			{
				int[] xy = calcXYfromSquareNum(b, i);
				
				//dont bother looking at squares that already have a piece
				if(b.board[xy[0]][xy[1]] != null)
					continue;
				
				int numnonzero = 0;
				for(int j = 0; j < matrix[0].length; j++)
				{
					//dont bother looking at pieces that
					//have already been placed
					if(isplaced[j])
						continue;
					
					boolean hasnonzero = false;
					for(int r = 0; r < 4; r++)
					{
						if(matrix[i][j][r] > 0.0) 
							hasnonzero = true;
					}
					if(hasnonzero)
						numnonzero++;
				}
				
				if(numnonzero < mincount) {//DEBUG what about ties?
					mincount = numnonzero;
					boardindex = i;
				}
			}
			
			//ok, we know which square has fewest possibilities.
			//Now we put in the piece that leaves us with the best board state
			double maxprob = Double.NEGATIVE_INFINITY;
			for(int j = 0; j < isplaced.length; j++)
			{
				if(isplaced[j]) continue;
				for(int r = 0; r < 4; r++)
					if(matrix[boardindex][j][r] > maxprob) {
						maxprob = matrix[boardindex][j][r];
						pieceindex = j;
					}
			}			
		}
		
		
		
		if(isplaced[pieceindex])
			throw new IllegalStateException("Best move is for a piece that is already placed. I think this should be impossible, because during probability calculation all placed pieces should have a probability of 1 in their chosen square and 0 everywhere else. But you'll need to double-check it now, obviously :-)");
		
		//ok, we know which square and we know which piece
		//so place it and return the board.
		
		//But we first calculate the best rotation of the piece
		//and rotate it to that position
		int bestr = Util.getMaxIndex(matrix[boardindex][pieceindex]);
		if(matrix[boardindex][pieceindex][bestr] == 0.0)
			throw new IllegalStateException("best move has probability 0.0. This should have been picked up by now, not detected here...");
		for(int i = 0; i < bestr; i++) pieces[pieceindex].rotate();
		
		//now place it
		int[] xy = calcXYfromSquareNum(b, boardindex);
		b.board[xy[0]][xy[1]] = pieces[pieceindex];
		isplaced[pieceindex] = true;
		
		Debug.println("Placed piece "+pieceindex+" in square "+boardindex, Debug.INFO);
		
		return true;
	}
	
	
	
	
	
	/** Calculate which pieces can go in which squares.
	 * 
	 *  Keep going as long as we can rule out possibilities.
	 * 
	 * @param b
	 * @param pieces
	 * @param isplaced
	 * @param numiterations
	 * @return
	 */
	public static double[][][] calcProbabilityMatrix(Board b, 
			Piece[] pieces, boolean[] isplaced)
	{
		double[][][] matrix = calcInitialProbabilityMatrix(b, pieces, isplaced);
		if(matrix == null)
			return null;
		
		Debug.println("In calcProbabilityMatrix()", Debug.INFO);
		printpieces(pieces, isplaced);
		printprobmatrix(matrix, b);


		//normalize the matrix of possibilities
		for(int j = 0; j < normalizeiterations; j++)
		{
			matrix = normalizeByRows(matrix); 
			matrix = normalizeByColumns(matrix);
		}
		matrix = normalizeByRows(matrix);
		printprobmatrix(matrix, b);

		
		//ok, keep going while we can rule out possibilities
		int[] premoved = (int[]) calcProbabilisticProbabilityMatrix(b, pieces, isplaced, matrix, true);
		while(true) 
		{
			if(premoved == null) //no possible moves... we failed
				return null;
			else if(premoved[1] > 0)
			{
				Debug.println("Removed "+premoved[0]+" piece probs and "+premoved[1]+" rotationalprobs", Debug.IMPORTANT);
				premoved = (int[]) calcProbabilisticProbabilityMatrix(b, pieces, isplaced, matrix, true);
			}
			else 
				break;
		}
		
		
		Debug.println("No more possibilities could be removed.. here is the matrix", Debug.IMPORTANT);
		printprobmatrix(matrix, b);
		Debug.println("Now calculating actual probabilities rather than just possibilities", Debug.IMPORTANT);
		

		//normalize the matrix of possibilities
		for(int j = 0; j < normalizeiterations; j++)
		{
			matrix = normalizeByRows(matrix); 
			matrix = normalizeByColumns(matrix);
		}
		matrix = normalizeByRows(matrix);
		printprobmatrix(matrix, b);
		
		
		//ok, now that we have normalized possibilities matrix,  we
		//iterate for a while
		for(int i =0; i < convergeiterations; i++)
		{
			matrix = (double[][][]) calcProbabilisticProbabilityMatrix(b, pieces, isplaced, matrix, false);
			if(matrix == null)
				throw new RuntimeException("Should have picked up impossible board before this stage");
			
			//normalize
			for(int j = 0; j < normalizeiterations; j++)
			{
				matrix = normalizeByRows(matrix); 
				matrix = normalizeByColumns(matrix);
			}
			matrix = normalizeByRows(matrix);
			printprobmatrix(matrix, b);
		}
		
		
		return matrix;
	}
	


	
	
	
	/** Calculate the probability that a particular piece
	 * goes in a particular square, given other placement
	 * probabilities.
	 * 
	 * This does not rotate the piece -- i.e. it only calculates
	 * the probability of the piece in the current rotation.
	 * 
	 * @param b the board
	 * @param x the x pos to place at
	 * @param y the y pos to place at
	 * @param pieces the pieces (placed or unplaced)
	 * @param toplace the piece to place
	 * @param probs the probabilities of piece placement
	 * @param binarymode. True if you just want to know if it is possible to place the piece
	 *                    false if you want the actual probabilities calculated.
	 * @return
	 */
	public static double calcProb(Board board, int x, int y, 
			Piece[] pieces, int toplace, double[][][] probs,
			boolean binarymode)
	{
		//count up possible placements on each face, for
		//all other possible placements
		double a = 0;
		double b = 0;
		double c = 0;
		double d = 0;
	
		
		
		if(x == 0)
			a = board.doesPieceFit(pieces[toplace], x, y) ? 1.0 : 0;
		else if(x > 0) 
		{
			if(board.board[x-1][y] != null)
				a = board.doesPieceFit(pieces[toplace], x, y) ? 1.0 : 0;
			else
			{
				int tryindex = calcLinearizedBoardIndex(board, x-1, y);
				//try all pieces in neighbouring squares
				for(int i = 0; i < pieces.length; i++)
				{
					if(i == toplace) continue;
					
					board.board[x-1][y] = pieces[i];
					for(int r = 0; r < 4; r++, pieces[i].rotate())
					{
						if(probs[tryindex][i][r] > 0.0)
						{
							if(board.doesPieceFit(pieces[toplace], x, y))
							{
								a += probs[tryindex][i][r];
							}
						}
					}
				}
				board.board[x-1][y] = null;
			}					
		}

		if(a == 0.0) return 0.0;
		
		if(x == board.board.length-1)
			b = board.doesPieceFit(pieces[toplace], x, y) ? 1.0 : 0;
		else if(x < board.board.length-1) 
		{
			if(board.board[x+1][y] != null)
				b = board.doesPieceFit(pieces[toplace], x, y) ? 1.0 : 0;
			else
			{
				int tryindex = calcLinearizedBoardIndex(board, x+1, y);
				//try all pieces in neighbouring squares
				for(int i = 0; i < pieces.length; i++)
				{
					if(i == toplace) continue;

					board.board[x+1][y] = pieces[i];
					for(int r = 0; r < 4; r++, pieces[i].rotate())
					{
						if(probs[tryindex][i][r] > 0.0)
						{
							if(board.doesPieceFit(pieces[toplace], x, y))
							{
								b += probs[tryindex][i][r];
							}
						}
					}
				}
				board.board[x+1][y] = null;
			}					
		}

		if(b == 0.0) return 0.0;
		
		if(y == 0)
			c = board.doesPieceFit(pieces[toplace], x, y) ? 1.0 : 0;
		else if(y > 0) 
		{
			if(board.board[x][y-1] != null)
				c = board.doesPieceFit(pieces[toplace], x, y) ? 1.0 : 0;
			else
			{
				int tryindex = calcLinearizedBoardIndex(board, x, y-1);
				//try all pieces in neighbouring squares
				for(int i = 0; i < pieces.length; i++)
				{
					if(i == toplace) continue;

					board.board[x][y-1] = pieces[i];
					for(int r = 0; r < 4; r++, pieces[i].rotate())
					{
						if(probs[tryindex][i][r] > 0.0)
						{
							if(board.doesPieceFit(pieces[toplace], x, y))
							{
								c += probs[tryindex][i][r];
							}
						}
					}
				}
				board.board[x][y-1] = null;
			}					
		}

		if(c == 0.0) return 0.0;
		
		if(y == board.board[0].length-1)
			d = board.doesPieceFit(pieces[toplace], x, y) ? 1.0 : 0;
		else if(y < board.board[0].length-1) 
		{
			if(board.board[x][y+1] != null)
				d = board.doesPieceFit(pieces[toplace], x, y) ? 1.0 : 0;
			else
			{
				int tryindex = calcLinearizedBoardIndex(board, x, y+1);
				//try all pieces in neighbouring squares
				for(int i = 0; i < pieces.length; i++)
				{
					if(i == toplace) continue;

					board.board[x][y+1] = pieces[i];
					for(int r = 0; r < 4; r++, pieces[i].rotate())
					{
						if(probs[tryindex][i][r] > 0.0)
						{
							if(board.doesPieceFit(pieces[toplace], x, y))
							{
								d += probs[tryindex][i][r];
							}
						}
					}
				}
				board.board[x][y+1] = null;
			}					
		}

		if(binarymode && a > 0 && b > 0 && c > 0 && d > 0)
			return 1.0;
			
		return a*b*c*d;
	}
	
	
	
	/** returns the total number of possible piece placements
	 * over all squares. 
	 * 
	 * @param b
	 * @param pieces
	 * @param isplaced
	 * @param probmatrix
	 * @return
	 */
	public static Object calcProbabilisticProbabilityMatrix(Board b, Piece[] pieces, boolean[] isplaced,
			double[][][] probmatrix, boolean binarymode)
	{
		Debug.println("In calcProbabilisticProbMatrix()", Debug.INFO);
		printpieces(pieces, isplaced);
		printprobmatrix(probmatrix, b);
		
		
			
		int numpieces = b.board.length*b.board[0].length;
		double[][][] res = new double[numpieces][numpieces][4];
		int rotationalpossibilitiesremoved = 0;
		int piecepossibiltiesremoved = 0;
		
		//go through and count which pieces fit in which squares
		//
		//loop over board squares
		for(int x =0; x < b.board.length; x++)
		{
			innerboard: for(int y = 0; y < b.board[0].length; y++)
			{	
				//calculate linearized board index
				int index = calcLinearizedBoardIndex(b, x, y);
				Debug.println("calculating probs for board index "+index, Debug.EXTRA_INFO);
				
				//if the board square is already
				//occupied, then all we need to do is
				//find out which placed piece is in that
				//board square
				if(b.board[x][y] != null)
				{
					for(int j = 0; j < numpieces; j++)
					{
						if(isplaced[j]) //piece is already placed
						{
							if(b.board[x][y] == pieces[j]) //at this square
							{
								res[index][j] = new double[] {1.0, 0.0, 0.0, 0.0};
								continue innerboard;
							}
							else 
								continue; //keep going until we find the piece that is in this square							
						}
					}
					throw new IllegalStateException("Cannot find piece to fit into board");
				}
								
				//loop over pieces
				int nonzerocount = 0;
				int nonzeroelem = -1;
				for(int j = 0; j < numpieces; j++)
				{					
					if(isplaced[j]) 
						continue; //piece already placed, so it cant go here
					
					int rotpossbefore = rotationalpossibilitiesremoved;
					boolean hasnonzero = false;
					//loop over rotations
					for(int r =0; r < 4; r++, pieces[j].rotate())
					{
						//calculate the probability that this
						//piece goes here, given the probabilities of
						//all the other piece placements
						double prob = calcProb(b, x, y, pieces, j, probmatrix, binarymode);
						res[index][j][r] = prob;
						if(prob == 0.0 && probmatrix[index][j][r] > 0.0)
							rotationalpossibilitiesremoved++;
						else if(prob > 0.0) 
						{
							if(probmatrix[index][j][r] == 0.0)
								throw new IllegalStateException("We just ruled IN a possibility....(piece "+j+" at square "+index+" at rotation "+r+")");
							hasnonzero = true;
						}
					}
					if(hasnonzero) {
						nonzerocount++;
						nonzeroelem = j;
					}
					else { //didnt have any nonzero rotational entries
						//see if this is a direct result of something we just did
						//(i.e. a rotational possibility we just ruled out that
						//resulted in the whole piece becoming invalid
						if(rotationalpossibilitiesremoved > rotpossbefore)
							piecepossibiltiesremoved++;
					}
				}
				
				//if there is only 1 unplaced piece that fits here,
				//then we should place it.... and can forgot
				//about looking at other squares
				if(nonzerocount == 0)
					return null;
				if(nonzerocount == 1) 
					throw new ForcedMoveException(index, nonzeroelem, probmatrix);
				
				
			}
		}
	
		if(binarymode)
		{
			for(int i = 0; i < probmatrix.length; i++)
				for(int j = 0; j < probmatrix[0].length; j++)
					for(int r = 0; r < 4; r++)
						probmatrix[i][j][r] = res[i][j][r];
			return new int[] {piecepossibiltiesremoved, rotationalpossibilitiesremoved};
		}
		else {
			//make sure we didnt rule out any possibilities
			if(piecepossibiltiesremoved > 0 || rotationalpossibilitiesremoved > 0)
				throw new RuntimeException("While trying to converge probabilities, I somehow ruled out a move that used to be possible.... should be impossible barring underflow errors....");
			
			return res;
		}
	}

	
	
	
	
	
	
	
	/** Calculate the probability of each piece going in to
	 *  each square.
	 *  
	 *  For efficiency reasons, you must also pass in an array 
	 *  specifying which pieces have already been placed (although
	 *  this could be worked out internally).
	 * 
	 *  The result is a 2d array, with board square number as
	 *  the first index and piece number as the second index.
	 *  The board is numbered as follows: 0: (0,0) , 1: (0,1), etc
	 *  (i.e. row-wise). 
	 * 
	 * 
	 *  Note that the returned matrix is unnormalized.
	 *  You have to decide on a normalization proceedure
	 *  (see the other functions in this class)
	 *  
	 * @param b
	 * @param pieces
	 * @param isplaced
	 * @return
	 */
	public static double[][][] calcInitialProbabilityMatrix(Board b, 
			Piece[] pieces, boolean[] isplaced)
	{
		int numpieces = b.board.length*b.board[0].length;
		double[][][] res = new double[numpieces][numpieces][4];
		
		//go through and count which pieces fit in which squares
		//
		//loop over board squares
		for(int x =0; x < b.board.length; x++)
		{
			innerboard: for(int y = 0; y < b.board[0].length; y++)
			{	
				//calculate linearized board index
				int index = calcLinearizedBoardIndex(b, x, y);

				//if the board square is already
				//occupied, then all we need to do is
				//find out which placed piece is in that
				//board square
				if(b.board[x][y] != null)
				{
					for(int j = 0; j < numpieces; j++)
					{
						if(isplaced[j]) //piece is already placed
						{
							if(b.board[x][y] == pieces[j]) //at this square
							{
								res[index][j] = new double[] {1.0, 0.0, 0.0, 0.0};
								//ok, we dont need to look at any other pieces
								continue innerboard;
							}
							else 
								continue; //keep going until we find the piece that fills the square
							
						}
					}
					throw new IllegalStateException("Cannot find piece to fit into board");
				}
				
				
				//loop over pieces
				int numnonzero = 0;
				int nonzeroelem = -1;
				for(int j = 0; j < numpieces; j++)
				{		
					if(isplaced[j])
						continue; //piece is placed, so it doesnt fit 
					
					//loop over rotations
					boolean hasnonzero = false;
					for(int r =0; r < 4; r++, pieces[j].rotate())
					{
						if(b.doesPieceFit(pieces[j], x, y))
						{
							hasnonzero = true;
							res[index][j][r] = 1;
						}
					}
					if(hasnonzero) {
						numnonzero++;
						nonzeroelem = j;
					}
				}
				if(numnonzero == 0)
					return null;
				
				if(numnonzero == 1)
					throw new ForcedMoveException(index, nonzeroelem, res);
			}
		}
	
		return res;
	}
	

	
	/** Calculate the linearized board index for a particular x,y
	 * 
	 * @param b
	 * @param x
	 * @param y
	 * @return
	 */
	public static int calcLinearizedBoardIndex(Board b, int x, int y)
	{
		return y*b.board.length+x;
	}
	
	
	public static int[] calcXYfromSquareNum(Board b, int num)
	{
		return new int[] {num % b.board.length, num / b.board.length};
	}
	
	
	/** Given a matrix [row,col], make sure each row
	 *  normalizes to 1.0
	 * 
	 *  This means that we are normalizing such
	 *  that the probability distribution across
	 *  all squares is 1. This means that the
	 *  distribution for each piece, though, 
	 *  will not be normalized.
	 * 
	 *  Returns the normalized matrix (NOT a copy
	 *  of the argument, actually the same thing
	 *  as the argument), or null if during the
	 *  normalization, we worked out that a piece
	 *  could not be placed or a square could not be
	 *  filled.
	 * 
	 * @param matrix
	 */
	public static double[][][] normalizeByRows(double[][][] matrix)
	{
		for(int i =0; i < matrix.length; i++)
		{
			double sum = 0.0;
			for(int j = 0; j < matrix[0].length; j++)
			{
				for(int r = 0; r < 4; r++) {
						sum += matrix[i][j][r];
				}
			}
						
			if(sum == 0.0) //no valid moves possible
				throw new IllegalStateException("normalization somehow made a valid move impossible...");
			
			for(int j = 0; j < matrix[0].length; j++)
			{
				for(int r = 0; r < 4; r++)
					matrix[i][j][r] /= sum;
			}
		}
		return matrix;
	}
	
	/** Given a matrix [row,col], make sure each column
	 *  normalizes to 1.0
	 * 
	 *  This means we are normalizing over pieces, so
	 *  that each piece has a normalized distribution over
	 *  all squares. So while for any individual piece,
	 *  the distribution across squares is 1.0, for any
	 *  particular square, the distribution across pieces
	 *  will not be normalized.
	 * 
	 *  Returns the normalized matrix (NOT a copy
	 *  of the argument, actually the same thing
	 *  as the argument), or null if during the
	 *  normalization, we worked out that a piece
	 *  could not be placed or a square could not be
	 *  filled.
	 * 
	 * @param matrix
	 */
	public static double[][][] normalizeByColumns(double[][][] matrix)
	{
		for(int i =0; i < matrix[0].length; i++) //loop over pieces
		{
			double sum = 0.0;
			for(int j = 0; j < matrix.length; j++) //loop over squares
			{
				for(int r = 0; r < 4; r++)
					sum += matrix[j][i][r];
			}
			
			if(sum == 0.0) //no valid moves possible
			{
				Debug.println("There are no possible moves for piece "+i, Debug.CRITICAL);
				throw new IllegalStateException("Found piece that cannot be placed during normalization across pieces...");
			}
			
			for(int j = 0; j < matrix.length; j++)
			{
				for(int r = 0; r < 4; r++)
					matrix[j][i][r] /= sum;
			}
		}
		return matrix;
	}

	

	/** Calculate the entropy of a particular piece's
	 * probability distribution across board squares.
	 *  
	 * 
	 * @param arr
	 * @return
	 */
	static double pieceentropy(double[][][] matrix, int piecenum)
	{
		//work out the entropy for a piece
		double[] arr1d = new double[matrix.length];
		double total = 0.0;
		for(int i = 0; i < matrix.length; i++)
		{
			for(int r = 0; r < 4; r++)
				arr1d[i] += matrix[i][piecenum][r];
			total += arr1d[i];
		}
		
		if(Math.abs(total-1.0) > 0.01)
			throw new RuntimeException("trying to calculate entropy of a non-normalized distribution with a sum of "+total);
		
		return Util.entropy(arr1d);
	}

	
	
	
	
	static void printpieces(Piece[] p, boolean[] isplaced)
	{
		if(Debug.equalOrMoreVerbose(Debug.EXTRA_INFO)) {
			for(int i =0; i < p.length; i++)
			{
				String pstr = isplaced[i] ? " (*) " : "( )";
				Debug.println("PIECE "+i+" "+pstr+" : r="+p[i].rotation+" "+Util.arrayToString(p[i].faces), Debug.EXTRA_INFO);
			}
		}
	}
	
	static void printprobmatrix(double[][][] probmatrix, Board b)
	{
		if(Debug.equalOrMoreVerbose(Debug.EXTRA_INFO)) 
		{
			//sum of probability distribution for each piece
			double[] pieceprobs = new double[probmatrix[0].length];
			
			//print out the probability of each piece in each square
			for(int i =0; i < probmatrix.length; i++)
			{
				double squareprob = 0.0; 
				ArrayList possibilities = new ArrayList();
				
				int[] xy = calcXYfromSquareNum(b, i);
				String isp = (b.board[xy[0]][xy[1]] == null) ? " " : "*";
				Debug.print("BOARD SQUARE "+i+" ("+isp+") ", Debug.EXTRA_INFO);
				for(int j = 0; j < probmatrix[0].length; j++)
				{
					Debug.print("(", Debug.EXTRA_INFO);
					boolean hasnonzero = false;
					for(int r = 0; r < 4; r++) {
						if(probmatrix[i][j][r] > 0.0) {
							hasnonzero = true;
							squareprob += probmatrix[i][j][r];
							pieceprobs[j] += probmatrix[i][j][r];
						}
						Debug.print(" "+probmatrix[i][j][r], Debug.EXTRA_INFO);
					}
					if(hasnonzero)
						possibilities.add(new Integer(j));
					Debug.print(")", Debug.EXTRA_INFO);
				}
				//print the possible pieces that can go in that square 
				//at the end of the line
				for(int p = 0; p < possibilities.size(); p++)
					Debug.print("  "+possibilities.get(p), Debug.EXTRA_INFO);
				Debug.println(" "+squareprob, Debug.EXTRA_INFO);
			}
			//now print out the piece probs
			Debug.print("PIECES| ", Debug.EXTRA_INFO);
			for(int i = 0; i < pieceprobs.length; i++)
				Debug.print(i+":("+pieceprobs[i]+") ", Debug.EXTRA_INFO);
			Debug.println("", Debug.EXTRA_INFO);
		}
	}
	
	
	/** Calculate the entropy of a particular board square's
	 * distribution across pieces.
	 *  
	 * 
	 * @param arr
	 * @return
	 */
	static double boardentropy(double[][] arr)
	{
		double[] arr1d = new double[arr.length];
		double total = 0.0;
		for(int i = 0; i < arr1d.length; i++)
		{
			if(arr[i].length != 4)
				throw new IllegalStateException("array must have dimension 4");
			
			for(int r = 0; r < 4; r++)
				arr1d[i] += arr[i][r];
			total += arr1d[i];
		}
		
		if(Math.abs(total-1.0) > 0.01)
			throw new RuntimeException("trying to calculate entropy of a non-normalized distribution with a sum of "+total);
		
		return Util.entropy(arr1d);
	}
	
}
