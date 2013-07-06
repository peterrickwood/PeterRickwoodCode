package rses.apps.eternity2;




public class ForcedMoveException extends RuntimeException
{
	public int boardindex;
	public int pieceindex;
	public double[][][] pmatrix;
	public ForcedMoveException(int boardindex, int pieceindex, double[][][] pmatrix)
	{
		this.boardindex = boardindex;
		this.pieceindex = pieceindex;
		this.pmatrix = pmatrix;
	}
}