


package rses.apps.eternity2;




import java.io.*;
import java.util.ArrayList;

import rses.util.Util;



public class E2Util
{
	private E2Util() {}
	
	public static Piece[] readPiecesFromFile(String filename) throws IOException
	{
		return readPiecesFromFile(filename, true);
	}
	
	public static Piece[] readPiecesFromFile(String filename, boolean barfonnonsquareboard) throws IOException
	{
		ArrayList pieces = new ArrayList();
		BufferedReader rdr = new BufferedReader(new FileReader(filename));
		String line = rdr.readLine();
		while(line != null)
		{
			String[] bits = Util.getWords(line);
			if(bits[0].charAt(0) == '#') {
				line = rdr.readLine();
				continue; //comment line
			}
			if(bits.length < 4 || bits.length > 5)
				throw new RuntimeException("bad format for line: "+line);
			
			int face1 = Integer.parseInt(bits[0]);
			int face2 = Integer.parseInt(bits[1]);
			int face3 = Integer.parseInt(bits[2]);
			int face4 = Integer.parseInt(bits[3]);
			int pos = -1;
			if(bits.length == 5)
				pos = Integer.parseInt(bits[4]);
			
			Piece p = new Piece(new int[] {face1, face2, face3, face4});
			p.pos = pos;
			pieces.add(p);
			
			
			line = rdr.readLine();
		}
		
		
		Piece[] result = new Piece[pieces.size()];
		if(barfonnonsquareboard)
		{
			double sqrt = Math.sqrt(result.length);
			if(sqrt-Math.round(sqrt) != 0.0)
				throw new RuntimeException("Board cannot be square, because of # of pieces");
		}
		
		for(int i = 0; i < result.length; i++)
			result[i] = (Piece) pieces.get(i);
		
		return result;
		
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
	
	
	
	
	
}
