package com.Allen.Finances.Bean;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class CatalinaSimpleLog {

	//Prints to Tomcat Catalina.out file
	public static void log (String level, String className, String msg ) {
		final SimpleDateFormat format = new SimpleDateFormat( "dd-MMM-yyyy HH:mm:ss.SSS");

		final StringBuilder builder = new StringBuilder( 1000 );
		
		builder.append(format.format(Calendar.getInstance().getTime())).append( " " );
		builder.append(level).append(" [").append(className).append( "] " );
		builder.append(msg);
		
		String logMsg = builder.toString();
		System.out.println( logMsg );
	}
	public static void log (String level, String className, String methodName, String msg ) {
		final SimpleDateFormat format = new SimpleDateFormat( "dd-MMM-yyyy HH:mm:ss.SSS");

		final StringBuilder builder = new StringBuilder( 1000 );
		
		builder.append(format.format(Calendar.getInstance().getTime())).append( " " );
		builder.append(level).append(" [").append(className).append( "] " );
		builder.append(methodName).append(" - ");
		builder.append(msg);
		
		String logMsg = builder.toString();
		System.out.println( logMsg );
	}
}
