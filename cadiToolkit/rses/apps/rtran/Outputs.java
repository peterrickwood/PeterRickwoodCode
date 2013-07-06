package rses.apps.rtran;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import javax.swing.JOptionPane;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import rses.Debug;

import rses.util.Util;

public class Outputs 
{
	private Map<String, String> namesVals = new HashMap<String, String>();
	
	
	public Outputs()
	{}
	
	
	public void addVal(String name, String val)
	{
		addVal(true, name, val);
	}
	
	
	public void addVal(boolean barfIfAlreadyPresent, String name, String val)
	{
		String old = namesVals.put(name, val);
		if(old != null && barfIfAlreadyPresent) throw new RuntimeException("Already have a value for "+name); 
	}
	
	
	public void write(String spreadsheetname)
	throws java.io.IOException
	{
		List<String> expmodes = getExpandedModes();
		org.apache.poi.ss.usermodel.Workbook wb = XLSXUtils.getWorkBook(spreadsheetname);
		
		//do overall statistics
		rses.Debug.println("Doing general output sheet", rses.Debug.IMPORTANT);
		doGeneralSheet(wb);
		
		//do Trip km and Trip km mode share sheet
		rses.Debug.println("Doing Trip km output sheet", rses.Debug.IMPORTANT);
		doTripKM(expmodes, wb);
		
		//do accessibility sheet
		rses.Debug.println("Doing Accessibility output sheet", rses.Debug.IMPORTANT);
		doAccessibility(wb);
		
		//do Revenue and Costs
		rses.Debug.println("Doing Financials output sheet", rses.Debug.IMPORTANT);
		doFinancials(expmodes, wb);
		
		
		String outfile = getOutputFileName();
		Debug.println("Saving output. Spreadsheet name is "+outfile, Debug.IMPORTANT);
		Globals.warning("Saved outputs to "+outfile);
		wb.write(new java.io.FileOutputStream(new java.io.File(outfile)));
	}

	private String getOutputFileName()
	{
		int i = 1;
		while(true)
		{
			String tryname = "Output_"+i+".xlsx";
			if(!new java.io.File(tryname).exists())
				return tryname;
			i++;
		}
	}
	
	private void doAccessibility(Workbook workbook)
	{
		Sheet sheet = workbook.getSheet("Accessibility");
		if(sheet == null) {
			JOptionPane.showMessageDialog(null, "Output workbook does not contain mandatory 'Accessibility' worksheet");
			System.exit(1);
		}

		for(Row r : sheet)
		{
			if(r.getCell(0) == null) continue;
			String celltext = XLSXUtils.getTextFromCell(sheet, r.getCell(0)); 
			Debug.println("read cell text "+celltext+" from accessibility sheet", Debug.INFO);
			
			if(celltext.startsWith("Accessibility_PT") ||
			   celltext.startsWith("Accessibility_CarAvail") ||
			   celltext.startsWith("Accessibility_") && celltext.endsWith("minsVoT"))
			{
				String mins = celltext.split("_")[celltext.split("_").length-1].split("mins")[0];
				double mean = Double.parseDouble(namesVals.get("Mean accessibility (reachable within generalized cost of "+mins+" mins)"));
				if(celltext.startsWith("Accessibility_PT"))
					mean = Double.parseDouble(namesVals.get("Mean PT accessibility (reachable within generalized cost of "+mins+" mins)"));
				if(celltext.startsWith("Accessibility_CarAvail"))
					mean = Double.parseDouble(namesVals.get("Mean CarAvail accessibility (reachable within generalized cost of "+mins+" mins)"));
				
				if(r.getCell(1) == null || !XLSXUtils.getTextFromCell(sheet, r.getCell(1)).equalsIgnoreCase("mean"))
					Globals.err("Expected cell with \"Mean\" in 2nd column but could find it");
				
				if(r.getCell(2) == null) r.createCell(2);
				r.getCell(2).setCellValue(mean);
				int rnum = r.getRowNum();
				for(int q = 0; q < 5; q++)
				{
					Row row = sheet.getRow(rnum+1+q); 
					if(row == null) Globals.err("Output spreadsheet in incorrect format (Accessibility Sheet)");
					if(row.getCell(1) == null) Globals.err("Output spreadsheet in incorrect format (Accessibility Sheet)");
					if(!XLSXUtils.getTextFromCell(sheet, row.getCell(1)).equalsIgnoreCase("Q"+(q+1)))
						Globals.err("Output spreadsheet in incorrect format (Accessibility Sheet). Expected Q"+(q+1)+" but got "+XLSXUtils.getTextFromCell(sheet, row.getCell(1)));
					
					if(row.getCell(2) == null) row.createCell(2);
					row.getCell(2).setCellValue(Double.parseDouble(namesVals.get(celltext+"_Q"+(q+1))));
				}
			}	
		}
		
		
	}
	
	
	
	private void doGeneralSheet(Workbook workbook)
	{
		Sheet sheet = workbook.getSheet("General");
		if(sheet == null) {
			JOptionPane.showMessageDialog(null, "Output workbook does not contain mandatory 'General' worksheet");
			System.exit(1);
		}
		
		/*Total trips
		Mean trip length (km)
		Mean accessibility (reachable within generalized cost of 120 mins)

		Mode share by Car
		Mode share by Walk
		Mode share by Public Transport
		Mode share by Bike*/
		boolean firstRow = true;
		for(Row r : sheet)
		{
			if(firstRow) {
				firstRow = false;
				continue;
			}
			Cell c = r.getCell(1);
			//Debug.println("Read row..... 2nd column cell is "+c, Debug.INFO);
			if(c == null) continue; //second column is blank
			String key = XLSXUtils.getTextFromCell(sheet, c);
			//Debug.println("Text in cell is "+key, Debug.INFO);
			double val = Double.parseDouble(this.namesVals.get(key));
			//Debug.println("Setting cell value to  "+val, Debug.INFO);
			if(r.getCell(2) == null) r.createCell(2);
			r.getCell(2).setCellValue(val);
		}
	}
	
	private List<String> getExpandedModes()
	{
		List<String> expmodes = new java.util.ArrayList<String>();
		for(String key : namesVals.keySet())
			if(key.startsWith("Total trip km by "))
				expmodes.add(Util.getWords(key)[Util.getWords(key).length-1]);
		java.util.Collections.sort(expmodes);
		return expmodes;
	}
	
	public void doTripKM(List<String> expmodes, org.apache.poi.ss.usermodel.Workbook workbook)
	throws java.io.IOException
	{
		Sheet sheet = workbook.getSheet("Trip KM");
		if(sheet == null) {
			JOptionPane.showMessageDialog(null, "Output workbook does not contain mandatory 'Trip KM' worksheet");
			System.exit(1);
		}
		
		for(int i = 0; i < expmodes.size(); i++)
		{
			if(sheet.getRow(i+1) == null) sheet.createRow(i+1);
			Row r = sheet.getRow(i+1);
			if(r.getCell(1) == null) r.createCell(1);
			if(r.getCell(2) == null) r.createCell(2);
			if(r.getCell(3) == null) r.createCell(3);
			String key = "Total trip km by "+expmodes.get(i);
			r.getCell(1).setCellValue(key);
			r.getCell(2).setCellValue(Double.parseDouble(namesVals.get(key)));			
			key = "Trip km share by "+expmodes.get(i);
			r.getCell(3).setCellValue(Double.parseDouble(namesVals.get(key)));			
		}
	}

	
	public void doFinancials(List<String> expmodes, org.apache.poi.ss.usermodel.Workbook workbook)
	throws java.io.IOException
	{
		Sheet sheet = workbook.getSheet("Financials");
		if(sheet == null) {
			JOptionPane.showMessageDialog(null, "Output workbook does not contain mandatory 'Financials' worksheet");
			System.exit(1);
		}
		
		double totcost = 0.0;
		double totrevenue = 0.0;
		for(int i = 0; i < expmodes.size(); i++)
		{
			if(sheet.getRow(i+2) == null) sheet.createRow(i+2);
			Row r = sheet.getRow(i+2);
			if(r.getCell(1) == null) r.createCell(1);
			if(r.getCell(2) == null) r.createCell(2);
			if(r.getCell(3) == null) r.createCell(3);
			if(r.getCell(5) == null) r.createCell(5);
			if(r.getCell(7) == null) r.createCell(7);
			
			String key = "OperatingCosts "+expmodes.get(i);
			double cost = 0.0;
			double revenue = 0.0;
			double maxpassengers = 0.0;
			double freq = 0.0;
			if(namesVals.containsKey(key))
				cost = Double.parseDouble(namesVals.get(key));
			key = "Max passengers by "+expmodes.get(i);
			maxpassengers = Double.parseDouble(namesVals.get(key));
			key = "Frequency for "+expmodes.get(i);
			if(namesVals.containsKey(key))
				freq = Double.parseDouble(namesVals.get(key));
			key = "Revenue from "+expmodes.get(i);
			if(namesVals.containsKey(key))
				revenue = Double.parseDouble(namesVals.get(key));
			
			totcost += cost;
			totrevenue += revenue;
			r.getCell(1).setCellValue(key);
			r.getCell(2).setCellValue(cost);
			r.getCell(3).setCellValue(revenue);
			r.getCell(5).setCellValue(maxpassengers);
			r.getCell(7).setCellValue(freq);
		}
		if(sheet.getRow(1) == null) sheet.createRow(1);
		Row r = sheet.getRow(1);
		if(r.getCell(1) == null) r.createCell(1);
		if(r.getCell(2) == null) r.createCell(2);
		if(r.getCell(3) == null) r.createCell(3);
		r.getCell(2).setCellValue(totcost);
		r.getCell(3).setCellValue(totrevenue);
		
		sheet.setForceFormulaRecalculation(true);
	}

}
