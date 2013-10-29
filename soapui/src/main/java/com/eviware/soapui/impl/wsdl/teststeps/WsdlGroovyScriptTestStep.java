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
import com.eviware.soapui.config.ExternalFilenameBuildModeConfig;
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
import com.google.common.io.Files;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlCursor;

import javax.swing.*;
import javax.xml.namespace.QName;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * TestStep that executes an arbitrary Groovy script
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
                File parent = f.getParentFile();
                if (! parent.exists()) {
                    parent.mkdirs();
                }
                f.createNewFile();
            }
            Files.write(scriptText, f, Charset.forName("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
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

        SoapUI.log.info("Script Root path is : " + scriptRootPath);

		XmlObjectConfigurationReader reader = new XmlObjectConfigurationReader( config.getConfig() );

        scriptConfig = ScriptConfig.Factory.newInstance();

        String externalFilename = reader.readString("script/@externalFilename", null);
        String externalFilenameBuildMode = reader.readString("script/@externalFilenameBuildMode", null);
        String composeWithProjectName = reader.readString("script/@composeWithProjectName", "false");
        String composeWithTestSuiteName = reader.readString("script/@composeWithTestSuiteName", "false");
        String composeWithTestCaseName = reader.readString("script/@composeWithTestCaseName", "false");
        String composeWithTestStepName = reader.readString("script/@composeWithTestSuiteName", "true");

        SoapUI.log.info("script/@externalFilename : " + externalFilename);

        if (externalFilenameBuildMode != null) {
            scriptConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.Enum.forString(externalFilenameBuildMode));
        }
        if (externalFilename == null || externalFilename.isEmpty()) {
            String projectName, testSuiteName, testCaseName, testStepName;
            String sep = System.getProperty("file.separator");
            projectName = getTestCase().getTestSuite().getProject().getName();
            testSuiteName = getTestCase().getTestSuite().getName();
            testCaseName = getTestCase().getName();
            testStepName = getName();
            if (externalFilenameBuildMode == null || externalFilenameBuildMode.isEmpty()) {
                externalFilenameBuildMode = ExternalFilenameBuildModeConfig.NONE.toString();
                scriptConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.NONE);
            }
            if (scriptConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.AUTO) {
                StringBuilder stringBuilder = new StringBuilder(projectName).append(sep)
                        .append(testSuiteName).append(sep)
                        .append(testCaseName).append(sep)
                        .append(testStepName).append(".groovy");
                externalFilename = stringBuilder.toString();
            } else if (scriptConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.COMPOSED) {
                scriptConfig.setComposeWithProjectName(Boolean.parseBoolean(composeWithProjectName));
                scriptConfig.setComposeWithTestSuiteName(Boolean.parseBoolean(composeWithTestSuiteName));
                scriptConfig.setComposeWithTestCaseName(Boolean.parseBoolean(composeWithTestCaseName));
                scriptConfig.setComposeWithTestStepName(Boolean.parseBoolean(composeWithTestStepName));

                StringBuilder stringBuilder = new StringBuilder();
                if (scriptConfig.getComposeWithProjectName()) {
                    stringBuilder.append(projectName);
                }
                if (scriptConfig.getComposeWithTestSuiteName()) {
                    stringBuilder.append(testSuiteName);
                }
                if (scriptConfig.getComposeWithTestCaseName()) {
                    stringBuilder.append(testCaseName);
                }
                if (scriptConfig.getComposeWithTestStepName()) {
                    stringBuilder.append(testStepName);
                }
                if (stringBuilder.length() == 0) {
                    stringBuilder.append("new-script");
                }
                stringBuilder.append(".groovy");
                externalFilename = stringBuilder.toString();
            } else if (scriptConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.MANUAL) {
                // MANUAL but attribute externalFilename was not specified or empty : set it to a default name
                externalFilename = "new-script.groovy";
            }
        } else {
            if (externalFilenameBuildMode == null || externalFilenameBuildMode.isEmpty()) {
                externalFilenameBuildMode = ExternalFilenameBuildModeConfig.MANUAL.toString();
                scriptConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.MANUAL);
            }
        }

        if (externalFilename != null) {
            scriptConfig.setExternalFilename(externalFilename);
            scriptExternalFilePath = externalFilename;
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

        XmlCursor scriptCursor = config.getConfig().newCursor();
        if (scriptCursor.toChild(new QName("", "script"))) {
            scriptCursor.getObject().set(scriptConfig);
            SoapUI.log.info("In WsdlGroovyScriptTestStep.readConfig() : replaced the script child element with compute script config.");
        } else {
            SoapUI.log.info("In WsdlGroovyScriptTestStep.readConfig() : no script child element found, weird...");
        }
        scriptCursor.dispose();
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
