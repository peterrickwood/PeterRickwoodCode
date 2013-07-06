package rses.apps.eternity2;
/** Represents a move.
 * 
 *  That is, a placement of a particular piece
 *  at a particular square on the board.
 *  Can carry a number indicating how 'good' the move is. 
 * 
 * @author peterr
 *
 */
public class Move 
{
	int x;
	int y;
	Piece p;
	double val;
	public Move(int x, int y, Piece p, double val)
	{
		this.x = x; this.y = y; this.p = p; this.val = val;
	}
	

}
