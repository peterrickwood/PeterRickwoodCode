package rses.util;


import java.sql.*;

import rses.Debug;

/**
 *
 * @author peter
 */
public class Database 
{    
     private String url;
     private String serverName;
     private String portNumber;
     private String databaseName;
     private String userName;
     private String password;
     
     // Constructor
     /**
      * Examples of the arguments you need to pass in are:
      * 
      * url = "jdbc:postgresql://";
      * serverName= "149.171.158.32";
      * portNumber = 5432;
      * databaseName= "urbanit_1";
      * userName = "postgres";
      * password = "randolph2010!";
      * 
      * 
      * 
      */

     public Database(String url, String servername,
    		 int portnumber, String database_name,
    		 String userName, String password)
     {
    	 this.url = url;
    	 this.serverName = servername;
    	 this.portNumber = ""+portnumber;
    	 this.databaseName = database_name;
    	 this.userName = userName;
    	 this.password = password;
    	 
     }
     
     private String getConnectionUrl(){
          String conurl = url+serverName+":"+portNumber+"/"+databaseName;
          Debug.println(conurl, Debug.INFO);
          return conurl;
     }
    
     public void getMetaData(String tablename)
     {
          DatabaseMetaData dm = null;
          ResultSet rs = null;
          try{
               Connection con= this.getConnection();
               if(con!=null){
                    dm = con.getMetaData();
                    rs = dm.getTables(databaseName, null, tablename, null);
                    while(rs.next())
                    {
                      String tname = rs.getString("TABLE_NAME");
                      if(tablename == null || tname.equals(tablename))
                      {
                        //print out the table information
                        PreparedStatement pstat = con.prepareStatement("SELECT TOP 5 * FROM "+tname);
                        ResultSet result = pstat.executeQuery();
                        ResultSetMetaData rsetmeta = result.getMetaData();
                        int ncols = rsetmeta.getColumnCount();
                        System.out.println(ncols+" columns in table "+tname);
                        for(int i =1; i <= ncols; i++)
                          System.out.println("\t"+rsetmeta.getColumnTypeName(i)+"   "+rsetmeta.getColumnName(i));
                        result.close();
                      }
                    }
                    rs.close();
                    rs = null;
                    closeConnection(con);
               }else System.out.println("Error: No active Connection");
          }catch(Exception e){
               e.printStackTrace();
          }
          dm=null;
     }     


     
     public java.util.Map[] sendQuery_withRetry(String sqltext, long starttimeout, long maxtimeout) throws SQLException
     {
    	java.util.Map[] res = null;
    	SQLException except = null;
    	long timeout = starttimeout;
    	
    	do {
    		try {
    			res = sendQuery(sqltext);
    			break;
    		}	
    		catch(java.sql.SQLException sqle) { except = sqle; }
    		
    		timeout *= 2;
    	}
    	while(timeout <= maxtimeout);
    	
    	if(res != null) return res;
    	
    	if(except == null) throw new RuntimeException("Impossible case reached");
    	throw except;
     }

     





     /**
      *
      * returns a HashMap with the fieldname as the key and the field value as the value
      */
     public java.util.Map[] sendQuery(String sqltext) throws SQLException
     {
       Debug.println("Building query", Debug.EXTRA_INFO);
       java.sql.Connection sqlcon = getConnection();
       java.sql.Statement pstat = sqlcon.createStatement();
       Debug.println("Executing query", Debug.EXTRA_INFO);
       java.sql.ResultSet result = pstat.executeQuery(sqltext);
       Debug.println("Got query results.. "+result.toString(), Debug.EXTRA_INFO);
       Debug.println("building hashmaps", Debug.EXTRA_INFO);
       java.sql.ResultSetMetaData rsetmeta = result.getMetaData();
       int ncols = rsetmeta.getColumnCount();

       java.util.ArrayList result_al = new java.util.ArrayList();
      

       int count = 1; 
       while(result.next()) //go through each row
       {
           java.util.HashMap m = new java.util.HashMap();
           for(int i =1; i <= ncols; i++)
           {
               String key = rsetmeta.getColumnName(i);
               String value = result.getString(i);
               m.put(key, value);
           }
           result_al.add(m);
           Debug.print((count++)+"..", Debug.EXTRA_INFO);
       }
       Debug.println("", Debug.EXTRA_INFO);
       Debug.println("Done building hashmaps", Debug.EXTRA_INFO);
       
       result.close();
       closeConnection(sqlcon);
       
       
       java.util.HashMap[] res = new java.util.HashMap[result_al.size()];
       for(int i =0; i < res.length; i++)
           res[i] = (java.util.HashMap) result_al.get(i);
       return res;
     }

 
     private java.sql.Connection getConnection()
     {
          java.sql.Connection  con = null;

          try{
               Class.forName("org.postgresql.Driver"); 
               con = java.sql.DriverManager.getConnection(getConnectionUrl(),userName,password);
               if(con!=null) System.out.println("Connection Successful!");
          }catch(Exception e){
               e.printStackTrace();
               System.out.println("Error Trace in getConnection() : " + e.getMessage());
         }
          return con;
      }

     /*
          Display the driver properties, database details 
     */ 

     public void displayDbProperties(){
          java.sql.DatabaseMetaData dm = null;
          java.sql.ResultSet rs = null;
          try{
               java.sql.Connection con= this.getConnection();
               if(con!=null){
                    dm = con.getMetaData();
                    System.out.println("Driver Information");
                    System.out.println("\tDriver Name: "+ dm.getDriverName());
                    System.out.println("\tDriver Version: "+ dm.getDriverVersion ());
                    System.out.println("\nDatabase Information ");
                    System.out.println("\tDatabase Name: "+ dm.getDatabaseProductName());
                    System.out.println("\tDatabase Version: "+ dm.getDatabaseProductVersion());
                    System.out.println("Avalilable Catalogs ");
                    rs = dm.getCatalogs();
                    while(rs.next()){
                         System.out.println("\tcatalog: "+ rs.getString(1));
                    } 
                    rs.close();
                    rs = null;
                    closeConnection(con);
               }else System.out.println("Error: No active Connection");
          }catch(Exception e){
               e.printStackTrace();
          }
          dm=null;
     }     
     
     private void closeConnection(java.sql.Connection con)
     {
          try{
               if(con!=null)
                    con.close();
               con=null;
          }catch(Exception e){
               e.printStackTrace();
          }
     }
}
