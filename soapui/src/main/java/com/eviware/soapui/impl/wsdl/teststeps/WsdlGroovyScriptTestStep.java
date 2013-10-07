/*
 *  soapUI, copyright (C) 2004-2012 smartbear.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */

package com.eviware.soapui.impl.wsdl.teststeps;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.TestStepConfig;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.model.propertyexpansion.PropertyExpansion;
import com.eviware.soapui.model.propertyexpansion.PropertyExpansionContainer;
import com.eviware.soapui.model.propertyexpansion.PropertyExpansionsResult;
import com.eviware.soapui.model.support.DefaultTestStepProperty;
import com.eviware.soapui.model.support.TestStepBeanProperty;
import com.eviware.soapui.model.testsuite.TestCaseRunContext;
import com.eviware.soapui.model.testsuite.TestCaseRunner;
import com.eviware.soapui.model.testsuite.TestRunner.Status;
import com.eviware.soapui.model.testsuite.TestStepResult;
import com.eviware.soapui.model.testsuite.TestStepResult.TestStepStatus;
import com.eviware.soapui.support.GroovyUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.scripting.SoapUIScriptEngine;
import com.eviware.soapui.support.scripting.SoapUIScriptEngineRegistry;
import com.eviware.soapui.support.xml.XmlObjectConfigurationReader;
import com.google.common.io.Files;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * TestStep that executes an arbitraty Groovy script
 * 
 * @author ole.matzura
 */

public class WsdlGroovyScriptTestStep extends WsdlTestStepWithProperties implements PropertyExpansionContainer
{
	private final static Logger logger = Logger.getLogger( "groovy.log" );
	private String scriptText = "";
    private String scriptRootPath;
    private String scriptExternalFilePath;
	private Object scriptResult;
	private ImageIcon failedIcon;
	private ImageIcon okIcon;
	private SoapUIScriptEngine scriptEngine;

	public WsdlGroovyScriptTestStep( WsdlTestCase testCase, TestStepConfig config, boolean forLoadTest )
	{
		super( testCase, config, true, forLoadTest );

		if( !forLoadTest )
		{
			okIcon = UISupport.createImageIcon( "/groovy_script.gif" );
			setIcon( okIcon );
			failedIcon = UISupport.createImageIcon( "/groovy_script_failed.gif" );
		}

        scriptRootPath = new File(getTestCase().getTestSuite().getProject().getPath()).getParent();

		if( config.getConfig() == null )
		{
			if( !forLoadTest )
				saveScript( config );
		}
		else
		{
			readConfig( config );
		}

		addProperty( new DefaultTestStepProperty( "result", true, new DefaultTestStepProperty.PropertyHandlerAdapter()
		{

			public String getValue( DefaultTestStepProperty property )
			{
				return scriptResult == null ? null : scriptResult.toString();
			}
		}, this ) );

		addProperty( new TestStepBeanProperty( "script", false, this, "script", this ) );

		scriptEngine = SoapUIScriptEngineRegistry.create( this );
		scriptEngine.setScript( getScript() );
		if( forLoadTest && !isDisabled() )
			try
			{
				scriptEngine.compile();
			}
			catch( Exception e )
			{
				SoapUI.logError( e );
			}
	}

    public Logger getLogger()
	{
		SoapUI.ensureGroovyLog();
		return logger;
	}

    private String readFile(String path) {
        File f = new File(path);
        if (f.exists()) {
            SoapUI.log.info("Reading file '" + path + "'...");
            try {
                StringBuilder sBuilder = new StringBuilder();
                String line;
                BufferedReader bufferedReader = new BufferedReader(new FileReader(f));
                while ((line = bufferedReader.readLine()) != null) {
                    sBuilder.append(line + "\n");
                }
                SoapUI.log.info(sBuilder.toString());
                return sBuilder.toString();
            } catch (FileNotFoundException fnfe) {
                fnfe.printStackTrace();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        } else {
            SoapUI.log.info("File with path " + path + " does not exists");
            SoapUI.log.info("  current working directory is " + System.getProperty("user.dir"));
        }
        return "";
    }

    public void saveToExternalFile() {
        if (this.scriptExternalFilePath != null) {
            SoapUI.log.info("*** this step has a scriptExternalFilePath : saving to file " + this.scriptExternalFilePath);
            if (saveScriptToFile()) {
                SoapUI.log.info("script '" + getConfig().newCursor().getName() + "' saved.");
            }
        }
    }

    private boolean saveScriptToFile() {
        if (this.scriptExternalFilePath == null) {
            return false;
        }
        StringBuffer pathBuffer = new StringBuffer();
        // TODO (marcpa) : use a portable way to do this (maybe create a File and check isAbsolutePath() ?
        if (!scriptExternalFilePath.startsWith("/")) {
            // is relative
            if (scriptRootPath == null) {
                scriptRootPath = ".";
            }
            pathBuffer.append(scriptRootPath).append(System.getProperty("file.separator")).append(scriptExternalFilePath);
        } else {
            // is absolute path
            pathBuffer.append(scriptExternalFilePath);
        }

        File f = new File(pathBuffer.toString());
        try {
            if (! f.exists()) {
                f.createNewFile();
            }
            Files.write(scriptText, f, Charset.forName("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

	private void readConfig( TestStepConfig config )
	{

        SoapUI.log.info("Script Root path is : " + scriptRootPath);

		XmlObjectConfigurationReader reader = new XmlObjectConfigurationReader( config.getConfig() );

        String externalFilePath = reader.readString("script/@file", null);
        SoapUI.log.info("script/@file : " + externalFilePath);
        if (externalFilePath != null) {
            scriptExternalFilePath = externalFilePath;
            StringBuffer pathBuffer = new StringBuffer();
            if (!scriptExternalFilePath.startsWith("/")) {
                // is relative
                if (scriptRootPath == null) {
                    scriptRootPath = ".";
                }
                pathBuffer.append(scriptRootPath).append(System.getProperty("file.separator")).append(scriptExternalFilePath);
            } else {
                // is absolute path
                pathBuffer.append(scriptExternalFilePath);
            }

            scriptText = readFile(pathBuffer.toString());
        } else {
            scriptText = reader.readString( "script", "" );
            SoapUI.log.info("In WsdlGroovyScriptTestStep.readConfig() : (config.config as XmlObjectConfigurationReader).readString('script') = ");
            SoapUI.log.info(scriptText);
        }
	}

	private void saveScript( TestStepConfig config )
	{
		//XmlObjectConfigurationBuilder builder = new XmlObjectConfigurationBuilder();
		//builder.add( "script", scriptText );
		//config.setConfig( builder.finish() );

        XmlObject xmlObject = XmlObject.Factory.newInstance();
        XmlCursor cursor = xmlObject.newCursor();
        cursor.toNextToken();
        cursor.beginElement("script");
        if (scriptExternalFilePath != null && !scriptExternalFilePath.isEmpty()) {
            cursor.insertAttributeWithValue("file", scriptExternalFilePath);
        }
        cursor.insertChars(scriptText);
        cursor.dispose();
        config.setConfig(xmlObject);
	}

	public void resetConfigOnMove( TestStepConfig config )
	{
		super.resetConfigOnMove( config );
		readConfig( config );
	}

	public String getDefaultSourcePropertyName()
	{
		return "result";
	}

	public TestStepResult run( TestCaseRunner testRunner, TestCaseRunContext context )
	{
        SoapUI.log.info("*******");
        SoapUI.log.info("*** In run() of WsdlGroovyScriptTestStep");
        SoapUI.log.info("*******");

		SoapUI.ensureGroovyLog();

		WsdlTestStepResult result = new WsdlTestStepResult( this );
		Logger log = ( Logger )context.getProperty( "log" );
		if( log == null )
			log = logger;

		try
		{
			if( scriptText.trim().length() > 0 )
				synchronized( this )
				{
					scriptEngine.setVariable( "context", context );
					scriptEngine.setVariable( "testRunner", testRunner );
					scriptEngine.setVariable( "log", log );

					result.setTimeStamp( System.currentTimeMillis() );
					result.startTimer();
					scriptResult = scriptEngine.run();
					result.stopTimer();

					if( scriptResult != null )
					{
						result.addMessage( "Script-result: " + scriptResult.toString() );
						// FIXME The property should not me hard coded
						firePropertyValueChanged( "result", null, String.valueOf( result ) );
					}

				}

			// testRunner status may have been changed by script..
			Status testRunnerStatus = testRunner.getStatus();
			if( testRunnerStatus == Status.FAILED )
				result.setStatus( TestStepStatus.FAILED );
			else if( testRunnerStatus == Status.CANCELED )
				result.setStatus( TestStepStatus.CANCELED );
			else
				result.setStatus( TestStepStatus.OK );
		}
		catch( Throwable e )
		{
			String errorLineNumber = GroovyUtils.extractErrorLineNumber( e );

			SoapUI.logError( e );
			result.stopTimer();
			result.addMessage( e.toString() );
			if( errorLineNumber != null )
				result.addMessage( "error at line: " + errorLineNumber );
			result.setError( e );
			result.setStatus( TestStepStatus.FAILED );
		}
		finally
		{
			if( !isForLoadTest() )
				setIcon( result.getStatus() == TestStepStatus.FAILED ? failedIcon : okIcon );

			if( scriptEngine != null )
				scriptEngine.clearVariables();
		}

		return result;
	}

	public String getScript()
	{
		return scriptText;
	}

	public void setScript( String scriptText )
	{
		if( scriptText.equals( this.scriptText ) )
			return;

		String oldScript = this.scriptText;
		this.scriptText = scriptText;
		scriptEngine.setScript( scriptText );
		saveScript( getConfig() );

		notifyPropertyChanged( "script", oldScript, scriptText );
	}

	@Override
	public void release()
	{
		super.release();
		scriptEngine.release();
	}

	public PropertyExpansion[] getPropertyExpansions()
	{
		PropertyExpansionsResult result = new PropertyExpansionsResult( this );

		result.extractAndAddAll( "script" );

		return result.toArray();
	}
}
