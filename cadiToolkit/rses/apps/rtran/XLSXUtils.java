package rses.apps.rtran;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;

import rses.Debug;


public final class XLSXUtils 
{
	private XLSXUtils() { throw new RuntimeException(); }
	
	public static void main(String[] args)
	throws java.io.IOException
	{
		//read or write a hidden id to the spreadsheet
		if(args[0].equalsIgnoreCase("write"))
		{
			Workbook wb = getWorkBook(args[1]);
			String id = args[2];
			Sheet s = wb.getSheet("Core Inputs");
			Row r = s.getRow(5);
			Cell c = r.getCell(0);
			c.setCellValue(id);
			
			wb.write(new java.io.FileOutputStream(id+".xlsx"));
		}
		else if(args[0].equalsIgnoreCase("read"))
		{
			Workbook wb = getWorkBook(args[1]);
			Sheet s = wb.getSheet("Core Inputs");
			Row r = s.getRow(5);
			Cell c = r.getCell(0);
			System.out.println(getTextFromCell(s, c));
		}
		
	}
	
	public static Workbook getWorkBook(String name) throws java.io.IOException
	{
		if(new java.io.File(name).exists())
			return new XSSFWorkbook(new java.io.FileInputStream(name));
		else
		{
			Globals.err("Could not open required spreadsheet "+name+" Aborting...");
			return null; //not strictly necessary, but compiler complains
		}
	}
	
	/** By default, we assume the 2nd column contains the keys and the third the values
	 *  (1-based indexing)
	 * 
	 * @return
	 */
	public static java.util.Map<String, String> getKeyValueMappingsFromSheet(Sheet sheet)
	{
		return getKeyValueMappingsFromSheet(2, 3, sheet);
	}
	
	
	public static String getTextFromCell(Sheet sheet, Cell c)
	{
		org.apache.poi.ss.usermodel.DataFormatter fmt = new org.apache.poi.ss.usermodel.DataFormatter();
		org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator formeval = new org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator((XSSFWorkbook) sheet.getWorkbook());
		if(c.getCellType() == Cell.CELL_TYPE_FORMULA)
			return fmt.formatCellValue(c, formeval);
		else
			return fmt.formatCellValue(c);		
	}
	
	public static java.util.Map<String, String> getKeyValueMappingsFromSheet(int keycolumn, int valcolumn, Sheet sheet)
	{
		org.apache.poi.ss.usermodel.DataFormatter fmt = new org.apache.poi.ss.usermodel.DataFormatter();
		org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator formeval = new org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator((XSSFWorkbook) sheet.getWorkbook());

		
		java.util.Map<String, String> keysvals = new java.util.HashMap<String, String>();
		for(Row r : sheet)
		{
			String key = null;
			String val = null;
			if(r.getCell(keycolumn-1, Row.RETURN_BLANK_AS_NULL) != null) 
			{
				key = fmt.formatCellValue(r.getCell(keycolumn-1, Row.RETURN_BLANK_AS_NULL));
				Cell valcell = r.getCell(valcolumn-1, Row.RETURN_BLANK_AS_NULL);
				if(valcell != null)
				{
					if(valcell.getCellType() == Cell.CELL_TYPE_FORMULA)
						val = fmt.formatCellValue(valcell, formeval);
					else
						val = fmt.formatCellValue(valcell);
				}
				keysvals.put(key, val);
			}
		}
		
		return keysvals;
	}
	
	public static Double[][] readMatrix(XSSFWorkbook book, String sheetname)
	{
		if(book.getSheet(sheetname) == null)
			throw new RuntimeException("Sheet "+sheetname+" is not defined, but I was expecting it to be defined!");
			
		XSSFSheet sheet = book.getSheet(sheetname);
		Double[][] data = null;
		try { data = readMatrix(sheet); }
		catch(Exception e) { throw new RuntimeException("In sheet "+sheetname+": "+e.getMessage());}
		return data;
	}
	
	public static Double[][] readMatrix(Sheet sheet)
	{
		return readMatrix(2, 2, sheet);
	}

	public static String[][] readStringMatrix(Sheet sheet)
	{
		return readStringMatrix(2, 2, sheet);
	}

	
	/** startrow and startcol are both 1-based (not 0-based)
	 * 
	 * @param startrow
	 * @param startcol
	 * @param sheet
	 * @return
	 */
	public static Double[][] readMatrix(int startrow, int startcol, Sheet sheet)
	{
		String[][] strvals = readStringMatrix(startrow, startcol, sheet);
		Double[][] dvals = new Double[strvals.length][strvals[0].length];
		for(int i = 0; i < dvals.length; i++)
			for(int j = 0; j < dvals[0].length; j++)
				dvals[i][j] = Double.parseDouble(strvals[i][j]);
		return dvals;
		
	}

	
	
	/** startrow and startcol are both 1-based (not 0-based)
	 * 
	 * @param startrow
	 * @param startcol
	 * @param sheet
	 * @return
	 */
	public static String[][] readStringMatrix(int startrow, int startcol, Sheet sheet)
	{
		//Debug.println("ReadMatrix "+startrow+" "+startcol+" ", Debug.EXTRA_INFO);
		org.apache.poi.ss.usermodel.DataFormatter fmt = new org.apache.poi.ss.usermodel.DataFormatter();
		java.util.ArrayList<String[]> rows = new java.util.ArrayList<String[]>();
		
		org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator formeval = new org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator((XSSFWorkbook) sheet.getWorkbook());

		//now get the data
		int rowc = startrow-1;
		while(true)
		{
			//rses.Debug.println("Reading row of cells", rses.Debug.EXTRA_INFO);
			Row row = sheet.getRow(rowc++);
			if(row == null) {
				rses.Debug.println("Skipping blank row", rses.Debug.EXTRA_INFO);
				break;
			}

			java.util.ArrayList<String> vals = new java.util.ArrayList<String>();

			//read the row
			int c = startcol-1; //-1 because API is 0-based
			while(row.getCell(c, Row.RETURN_BLANK_AS_NULL) != null) //while we dont get a blank cell
			{
				//rses.Debug.println("Getting cell "+c+" in row", rses.Debug.EXTRA_INFO);
				
				Cell cell = row.getCell(c, Row.RETURN_BLANK_AS_NULL);
				String text = null;
				if(cell.getCellType() == Cell.CELL_TYPE_FORMULA)
					text = fmt.formatCellValue(cell, formeval);
				else
					text = fmt.formatCellValue(row.getCell(c));
				//rses.Debug.println("Read "+text, rses.Debug.EXTRA_INFO);
				vals.add(text.trim());					
				c++;
			}
			//Debug.println("Finished row", Debug.EXTRA_INFO);
			
			//if we didnt get any values from the row, then we assume we are finished
			if(vals.size() == 0) break;
				
			String[] arrvals = vals.toArray(new String[1]);
			rows.add(arrvals);
		}
				
		//now convert to a 2d double array. Also make sure we check that:
		//1) we got at least 1 row
		if(rows.size() == 0) throw new RuntimeException("Couldnt read any data from sheet....");
		
		
		String[][] data = new String[rows.size()][];
		for(int i = 0; i < rows.size(); i++) 
			data[i] = rows.get(i);
			
		
		
		//2) all the rows have the same length
		if(!isRectangular(data, null)) 
			throw new RuntimeException("The defined matrix is NOT rectangular, but it must be.");
		
		
		return data;
		
	}

	
	/**
	 * Will determine if a matrix is rectangular. If the matrix is NOT, will return 2 rows that differ in length 
	 * 
	 * @param matrix
	 * @param out_differingRows If the matrix is not rectangular, and this input parameter is not null, the method
	 *                          will put the row numbers of 2 rows that differ in length into this list
	 * @return
	 */
	public static boolean isRectangular(Object[][] matrix, java.util.List<Integer> out_differingRows)
	{
		int len = matrix[0].length;
		boolean square = true;
		for(int i = 1; i < matrix.length; i++)
		{
			if(matrix[i].length != len) {
				square = false;
				if(out_differingRows != null) {
					out_differingRows.add(0);
					out_differingRows.add(i);
					break;
				}
			}
		}
			
		return square;
	}
	                       
	                       

}
