package com.Allen.SpringFinancesServer.Utils;

//Class handles issues caused when passing a null boolean value into Oracle DB (pl/sql will parse a null value to false)
//The methods will only pass 0, 1, or 2 as int's. The stored procedures in the database are written to not insert into a
//column if a 2 is passed in. 


public class BooleanValidator {

	public static int boolValidate(boolean bool) {
		if(bool == false) {
			return 0;
		} 
		else if(bool == true) {
			return 1;
		}
		else
		return 2;
	}
	
	public static int intValidate(int bool) {
		if(bool == 0) {
			return 0;
		} 
		else if(bool == 1) {
			return 1;
		}
		else
		return 2;
	}
	
	public static int stringToInt ( String boolVal ) {
		if( boolVal == null) {
		return 2;
		} 
		 if(boolVal.equalsIgnoreCase("1")|| boolVal.equalsIgnoreCase("true") )
		{
		return 1;
		}
		else if(boolVal.equalsIgnoreCase("0") || boolVal.equalsIgnoreCase("false") )
		{
		return 0;
		}
		else{
		return 2;
		}
	}
}
