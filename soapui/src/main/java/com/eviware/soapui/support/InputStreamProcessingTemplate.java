package com.eviware.soapui.support;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Class that encapsulates exception handling for file IO.
 * From http://tutorials.jenkov.com/java-exception-handling/exception-handling-templates.html
 */
public class InputStreamProcessingTemplate {

	public static void process(String fileName,
										InputStreamProcessor processor) throws Exception {
		IOException processException = null;
		InputStream input = null;
		try {
			input = new FileInputStream(fileName);

			processor.process(input);
		} catch(IOException e) {
			processException = e;
		} finally {
			if(input != null) {
				try {
					input.close();
				} catch(IOException e) {
					if(processException != null) {
						Exception wrappedException = new Exception("IO Exception thrown from processor.process()", processException);
						throw new Exception(e.getMessage() + fileName, wrappedException);
					} else {
						throw new Exception("Error closing InputStream for file " + fileName, e);
					}
				}
			}
			if(processException != null) {
				throw new Exception("Error processing InputStream for file " + fileName, processException);
			}
		}
	}
}
