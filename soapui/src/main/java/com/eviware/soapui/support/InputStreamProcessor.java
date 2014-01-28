package com.eviware.soapui.support;

import java.io.IOException;
import java.io.InputStream;

/**
 * Interface to implements when using the InputStreamProcessingTemplate.
 * From http://tutorials.jenkov.com/java-exception-handling/exception-handling-templates.html
 */
public interface InputStreamProcessor {
	public void process(InputStream input) throws IOException;

	public String getResult();
}
