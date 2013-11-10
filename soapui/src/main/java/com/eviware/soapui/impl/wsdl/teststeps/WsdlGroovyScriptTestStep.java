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
import com.eviware.soapui.config.ScriptConfig;
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
import com.eviware.soapui.settings.UISettings;
import com.eviware.soapui.support.GroovyUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.scripting.SoapUIScriptEngine;
import com.eviware.soapui.support.scripting.SoapUIScriptEngineRegistry;
import com.eviware.soapui.support.xml.XmlObjectConfigurationBuilder;
import com.eviware.soapui.support.xml.XmlObjectConfigurationReader;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlCursor;

import javax.swing.*;

/**
 * TestStep that executes an arbitrary Groovy script
 * 
 * @author ole.matzura
 */

public class WsdlGroovyScriptTestStep extends WsdlTestStepWithProperties implements PropertyExpansionContainer
{
	private final static Logger logger = Logger.getLogger( "groovy.log" );
	private String scriptText = "";

    private TestRequestStepInExternalFileSupport testRequestStepInExternalFileSupport;
	private Object scriptResult;
	private ImageIcon failedIcon;
	private ImageIcon okIcon;
	private SoapUIScriptEngine scriptEngine;
    private ScriptConfig scriptConfig;

	public WsdlGroovyScriptTestStep( WsdlTestCase testCase, TestStepConfig config, boolean forLoadTest )
	{
		super( testCase, config, true, forLoadTest );

		if( !forLoadTest )
		{
			okIcon = UISupport.createImageIcon( "/groovy_script.gif" );
			setIcon( okIcon );
			failedIcon = UISupport.createImageIcon( "/groovy_script_failed.gif" );
		}

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

    /**
     * Read config to initialize current test step.
     *
     * This is a groovy script, but the 'script' element is *not* a 'Script' XML type as defined in the XSD, simply an instance of an anyType.
     *
     * However, when reading a config, we create an instance of ScriptConfig because its API is more natural than fiddling with XmlObject/XmlCursor
     * directly.  At the end, this test step config is set from the xml representation built up in ScriptConfig.
     *
     * Therefore, the ScriptConfig object created here does not live past beyond the return point of this method.
     *
     * @param config
     */
	private void readConfig( TestStepConfig config )
	{
        if (! getSettings().getBoolean(UISettings.STEP_IN_EXTERNAL_FILE)) {
            XmlObjectConfigurationReader reader = new XmlObjectConfigurationReader( config.getConfig() );
            scriptText = reader.readString( "script", "" );
            SoapUI.log.info("In WsdlGroovyScriptTestStep.readConfig() : got script content from 'script' element.");
        } else {
            testRequestStepInExternalFileSupport = new TestRequestStepInExternalFileSupport(this, config, getSettings());
            testRequestStepInExternalFileSupport.initExternalFilenameSupport();
            scriptText = testRequestStepInExternalFileSupport.getStepContent();
        }
    }

	private void saveScript( TestStepConfig config )
	{
        if (!getSettings().getBoolean(UISettings.STEP_IN_EXTERNAL_FILE) || (getSettings().getBoolean(UISettings.STEP_IN_EXTERNAL_FILE) && getSettings().getBoolean(UISettings.ALSO_KEEP_IN_PROJECT_WHEN_STEP_IN_EXTERNAL_FILE))) {

            // XmlObjectConfigurationBuilder is providing a simpler API that manipulating XmlObject directly, but it wipes out attributes on 'script' element :
            // that is why we resort to bits and pieces instead when script exists already
            XmlCursor cursor = config.getConfig().newCursor();
            if (cursor.toFirstChild()) {
                cursor.setTextValue(scriptText);
                cursor.dispose();
            } else {
                XmlObjectConfigurationBuilder builder = new XmlObjectConfigurationBuilder();
                builder.add( "script", scriptText );
                config.setConfig( builder.finish() );
            }

            //XmlObjectConfigurationBuilder builder = new XmlObjectConfigurationBuilder();
            //builder.add( "script", scriptText );
            //config.setConfig( builder.finish() );
        }
        // when in step in external file mode, no need to "save" the content into config, scriptText will be written to file at save time
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
        testRequestStepInExternalFileSupport.setStepContent(scriptText);
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

    public TestRequestStepInExternalFileSupport getTestRequestStepInExternalFileSupport() {
        return testRequestStepInExternalFileSupport;
    }
}
