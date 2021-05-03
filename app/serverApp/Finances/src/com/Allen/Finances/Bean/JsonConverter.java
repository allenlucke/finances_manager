package com.Allen.Finances.Bean;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class JsonConverter {
	
	public static final String CLASS_NAME = JsonConverter.class.getSimpleName();
	
	
	
	//Reads InputStream into a String Object
	public static String toString(InputStream inStream, final String charset) throws IOException {
		
		final String methodName = "toString()";
		
		try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			
			byte[] buffer = new byte[1024];
			int length;
			
			while((length = inStream.read(buffer)) != -1)
				baos.write(buffer, 0, length);
			
			return baos.toString(charset);
		}
	}	

}
