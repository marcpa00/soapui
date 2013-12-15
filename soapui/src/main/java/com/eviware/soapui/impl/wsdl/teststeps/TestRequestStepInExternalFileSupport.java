package com.eviware.soapui.impl.wsdl.teststeps;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.ExternalFilenameBuildModeConfig;
import com.eviware.soapui.config.ModelItemConfig;
import com.eviware.soapui.config.RequestStepConfig;
import com.eviware.soapui.config.ScriptConfig;
import com.eviware.soapui.config.TestStepConfig;
import com.eviware.soapui.config.WsdlRequestConfig;
import com.eviware.soapui.impl.settings.XmlBeansSettingsImpl;
import com.eviware.soapui.model.ModelItem;
import com.eviware.soapui.model.iface.Request;
import com.eviware.soapui.model.iface.Script;
import com.eviware.soapui.model.testsuite.LoadTest;
import com.eviware.soapui.model.testsuite.TestCase;
import com.eviware.soapui.model.testsuite.TestStep;
import com.eviware.soapui.model.testsuite.TestSuiteListener;
import com.eviware.soapui.security.SecurityTest;
import com.eviware.soapui.settings.UISettings;
import com.eviware.soapui.support.InputStreamProcessingTemplate;
import com.eviware.soapui.support.InputStreamProcessor;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.xml.XmlObjectConfigurationReader;
import com.google.common.io.Files;
import org.apache.xmlbeans.XmlCursor;

import javax.swing.*;
import javax.xml.namespace.QName;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;

/**
 * Class for supporting step request content in external file : store the actual content of a request used in a test
 * step into a file outside of the project XML document file.
 * <p>
 * An attribute, externalFilename, is added to the schema of request step (currently for WsdlRequest and Script,
 * although the latter is not strictly a test request) and accepts the filename of the file where to read and save the
 * request of step.  If the filename is relative, it is resolved against the rootPath of the containing project.
 *</p>
 * <p>
 * The filename can be manually specified (there is a ui configuration panel for steps that supports the current
 * feature) or it can be automatically computed by SoapUI when a project file is saved.  This is controlled by the
 * externalFilenameBuildMode attribute, which can take the values NONE, MANUAL, AUTO, COMPOSED.
 *
 * <ul>
 *     <li>NONE : filename will be ignored for this test step request, the content will be whatever there is in
 *     the XML project file, no external file will be read or saved for this step.</li>
 *     <li>MANUAL : filename is an absolute or relative pathname provided by the user.</li>
 *     <li>AUTO : filename is a relative pathname automatically built by SoapUI using the test step path in its
 *     project : project name + test suite name + test case name + test step name, each mapped to a directory
 *     except of the test step name, which is mapped to a file.</li>
 *     <li>COMPOSED : filename is a relative pathname automatically built by SoapUI according to selected parts;
 *     extra (boolean) attributes select some pre-defined parts when composing the filename : composeWithProjectName,
 *     composeWithTestSuiteName, composeWithTestCaseName, composeWithTestStepName.</li>
 * </ul>
 *<p>
 * Global settings for "step in external file" :
 * <ul>
 *     <li>UISettings.STEP_IN_EXTERNAL_FILE : use the step in external file support if true, ignore attributes for
 *     this feature in config even if they are specified.</li>
 *     <li>UISettings.AUTO_CONVERT_STEP_TO_USE_EXTERNAL_FILE : when configuring a step or saving it, add the
 *     attributes for "step in external file" support, defaulting to AUTO mode.</li>
 *     <li>UISettings.ALSO_KEEP_IN_PROJECT_WHEN_STEP_IN_EXTERNAL_FILE : the request text will be also saved in project
 *     XML document, for backward compatibility.</li>
 * </ul>
 * </p>
 * <p>
 * User: marcpa
 * Date: 2013-11-02
 * Time: 08:41
 * To change this template use File | Settings | File Templates.
 * </p>
 */
public class TestRequestStepInExternalFileSupport implements ModelItem, PropertyChangeListener, TestSuiteListener {
    static public final String DEFAULT_STEP_FILENAME = "new-testStep";
    static public final String DEFAULT_SUFFIX = ".txt";
    static public final String WSDL_REQUEST_SUFFIX = "-request.xml";
    static public final String GROOVY_SCRIPT_SUFFIX = ".groovy";

    // for the WsldRequest personnality
    private WsdlRequestConfig wsdlRequestConfig;

    // for the GroovyTestStep personnality
    private TestStepConfig testStepConfig;

    private ScriptConfig scriptConfig;


    private XmlBeansSettingsImpl settings;
    private WsdlTestStep testStep;
    private WsdlTestRequest testRequest;

    private String stepContent;

    private String externalFileRootPath;
    private String externalFilename;
    private long lastLoadedFromExternalFile;
    private long lastModified;

    private Boolean composeWithProjectName;

    private Boolean composeWithTestSuiteName;
    private Boolean composeWithTestCaseName;
    private Boolean composeWithTestStepName;
    private ExternalFilenameBuildModeConfig.Enum externalFilenameBuildMode = ExternalFilenameBuildModeConfig.NONE;
    private String alternateFilenameForContent;
    private Boolean alwaysPreferContentFromProject = null;
    private Boolean alwaysPreferContentFromExternalFile = null;

    private TestRequestStepInExternalFileSupport() {
        // prevent instantiation without required field values
    }

    /**
     * Constructor for WsdlTest based steps.
     *
     * @param wsdlTestStep
     * @param wsdlTestRequest
     * @param requestStepConfig
     * @param settings
     */
    public TestRequestStepInExternalFileSupport(WsdlTestStep wsdlTestStep, WsdlTestRequest wsdlTestRequest, RequestStepConfig requestStepConfig, XmlBeansSettingsImpl settings) {
        this.testStep = wsdlTestStep;
        this.testRequest = wsdlTestRequest;
        this.wsdlRequestConfig = requestStepConfig.getRequest();
        this.settings = settings;

        this.alwaysPreferContentFromProject = getTestCase().getTestSuite().getProject().getAlwaysPreferContentFromProject();
        this.alwaysPreferContentFromExternalFile = getTestCase().getTestSuite().getProject().getAlwaysPreferContentFromExternalFile();
        if (this.alwaysPreferContentFromProject != null && this.alwaysPreferContentFromExternalFile != null
                && this.alwaysPreferContentFromProject && this.alwaysPreferContentFromExternalFile) {
            // conflict, prefer project
            this.alwaysPreferContentFromExternalFile = Boolean.FALSE;
            SoapUI.log.debug("Forcing alwaysPreferContentFromExternalFile to false.");
        }

        addPropertyChangeListener( ModelItem.NAME_PROPERTY, this);
        this.testStep.addPropertyChangeListener( Request.REQUEST_PROPERTY, this);
        this.testStep.getTestCase().getTestSuite().addTestSuiteListener(this);
    }

    /**
     * Constructor for GroovyScript steps.
     * @param wsdlTestStep
     * @param testStepConfig
     * @param settings
     */
    public TestRequestStepInExternalFileSupport(WsdlTestStep wsdlTestStep, TestStepConfig testStepConfig, XmlBeansSettingsImpl settings) {
        this.testStep = wsdlTestStep;
        this.testStepConfig = testStepConfig;
        this.settings = settings;

        this.alwaysPreferContentFromProject = getTestCase().getTestSuite().getProject().getAlwaysPreferContentFromProject();
        this.alwaysPreferContentFromExternalFile = getTestCase().getTestSuite().getProject().getAlwaysPreferContentFromExternalFile();
        if (this.alwaysPreferContentFromProject != null && this.alwaysPreferContentFromExternalFile != null
                && this.alwaysPreferContentFromProject && this.alwaysPreferContentFromExternalFile) {
            // conflict, prefer project
            this.alwaysPreferContentFromExternalFile = Boolean.FALSE;
            SoapUI.log.debug("Forcing alwaysPreferContentFromExternalFile to false.");
        }

        addPropertyChangeListener( ModelItem.NAME_PROPERTY, this);
        addPropertyChangeListener( Script.SCRIPT_PROPERTY, this);
        this.testStep.getTestCase().getTestSuite().addTestSuiteListener(this);
    }



    public void initExternalFilenameSupport() {

        initExternalFileRootPath();
        // TODO (marcpa00) : still not so sure about this, where we dispatch on wsdl or script config processing based on non-null state of 2 variables
        //                   Consider using a marker field instead, like a stepConfigType, which could then be type checked, or a marker interface which could lead to polymorphism processing.
        if (wsdlRequestConfig != null) {
            initExternalFilenameSupportForWsdlRequest();
        } else if (testStepConfig != null) {
            initExternalFilenameSupportForScript();
        } else {
            SoapUI.log.error("TestRequestStepInExternalFileSupport : initExternalFilenameSupport() called but we have no config to work on.");
            return;
        }

    }

    public void initExternalFileRootPath() {

        if (externalFileRootPath == null || externalFileRootPath.isEmpty()) {
            externalFileRootPath = getTestStep().getTestCase().getTestSuite().getProject().getPath();
            if (externalFileRootPath == null || externalFileRootPath.isEmpty()) {
                externalFileRootPath = System.getProperty("user.dir");
            }
        }
        if (externalFileRootPath != null && externalFileRootPath.endsWith(".xml")) {
            externalFileRootPath = externalFileRootPath.replaceAll(".xml", ".resources");
        } else {
            externalFileRootPath = externalFileRootPath + ".resources";
        }

    }

    private void initExternalFilenameSupportForWsdlRequest() {
        if (wsdlRequestConfig.isSetExternalFilename()) {
            // start with both filename values being what is in the config
            this.externalFilename = wsdlRequestConfig.getExternalFilename();
            this.alternateFilenameForContent = wsdlRequestConfig.getExternalFilename();
        }

        if (! wsdlRequestConfig.isSetExternalFilenameBuildMode()) {
            boolean autoConvert = getSettings().getBoolean(UISettings.AUTO_CONVERT_STEP_TO_USE_EXTERNAL_FILE);
            wsdlRequestConfig.setExternalFilenameBuildMode(autoConvert ? ExternalFilenameBuildModeConfig.AUTO : ExternalFilenameBuildModeConfig.NONE);
        }
        externalFilenameBuildMode = wsdlRequestConfig.getExternalFilenameBuildMode();

        composeWithProjectName = wsdlRequestConfig.isSetComposeWithProjectName() ? wsdlRequestConfig.getComposeWithProjectName() : false;
        composeWithTestSuiteName = wsdlRequestConfig.isSetComposeWithTestSuiteName() ? wsdlRequestConfig.getComposeWithTestSuiteName() : false;
        composeWithTestCaseName = wsdlRequestConfig.isSetComposeWithTestCaseName() ? wsdlRequestConfig.getComposeWithTestCaseName() : false;
        composeWithTestStepName = wsdlRequestConfig.isSetComposeWithTestStepName() ? wsdlRequestConfig.getComposeWithTestStepName() : false;

        String contentFromProjectDocument = null;
        if (testRequest != null) {
            contentFromProjectDocument = testRequest.getRequestContent();
        }

        if (externalFilenameBuildMode != ExternalFilenameBuildModeConfig.NONE) {
            // this will adjust externalFilename
            buildExternalFilenameForCurrentMode();
            loadStepContent();
            if (contentFromProjectDocument != null && !contentFromProjectDocument.equals(stepContent)) {
                // content from project document differs with content from external file : which one to use ?  Only the user can tell.

                if (stepContent == null || stepContent.isEmpty()) {
                    stepContent = contentFromProjectDocument;
                } else {

                    if (alwaysPreferContentFromProject == null && alwaysPreferContentFromExternalFile == null) {
                        int choice = UISupport.yesYesToAllNoNoToAll("Step \n\n[" + getPathInProject() + "]\n\ncontent from project document is different than the content of external file.\n\nUse project content and ignore external file for this step ?",
                                "Conflicting changes detected while loading project !", "Yes, use project content for all", "No, use external file content for all");
                        if (choice == 0 || choice == 1) {
                            stepContent = contentFromProjectDocument;
                            if (choice == 1) {
                                alwaysPreferContentFromProject = Boolean.TRUE;
                                alwaysPreferContentFromExternalFile = Boolean.FALSE;
                            }
                        } else if (choice == 3) {
                            alwaysPreferContentFromProject = Boolean.FALSE;
                            alwaysPreferContentFromExternalFile = Boolean.TRUE;
                        }
                    } else if (alwaysPreferContentFromProject) {
                        stepContent = contentFromProjectDocument;
                    }
                }
            }
        }

        if (alwaysPreferContentFromProject != null) {
            getTestCase().getTestSuite().getProject().setAlwaysPreferContentFromProject(alwaysPreferContentFromProject);
        }
        if (alwaysPreferContentFromExternalFile != null) {
            getTestCase().getTestSuite().getProject().setAlwaysPreferContentFromExternalFile(alwaysPreferContentFromExternalFile);
        }
        wsdlRequestConfig.setExternalFilename(externalFilename);
    }


    private void initExternalFilenameSupportForScript() {

        // A groovy script test step does not have a specific XmlBean class generated for, it is an XSD anyType and
        // needs to be parsed with basic XmlObject / XmlCursor api via XmlObjectConfigurationReader...
        XmlObjectConfigurationReader reader = new XmlObjectConfigurationReader( testStepConfig.getConfig() );
        String configExternalFilename = reader.readString("script/@externalFilename", null);
        String configExternalFilenameBuildMode = reader.readString("script/@externalFilenameBuildMode", null);
        String configComposeWithProjectName = reader.readString("script/@composeWithProjectName", "false");
        String configComposeWithTestSuiteName = reader.readString("script/@composeWithTestSuiteName", "false");
        String configComposeWithTestCaseName = reader.readString("script/@composeWithTestCaseName", "false");
        String configComposeWithTestStepName = reader.readString("script/@composeWithTestStepName", "true");
        String configContent = reader.readString(Script.SCRIPT_PROPERTY, null);

        // ... however, we can create an instance of the XmlBeans generated class ScriptConfig and populate it ourselves
        scriptConfig = ScriptConfig.Factory.newInstance();

        if (configExternalFilenameBuildMode == null) {
            boolean autoConvert = getSettings().getBoolean(UISettings.AUTO_CONVERT_STEP_TO_USE_EXTERNAL_FILE);
            scriptConfig.setExternalFilenameBuildMode(autoConvert ? ExternalFilenameBuildModeConfig.AUTO : ExternalFilenameBuildModeConfig.NONE);
        } else {
            scriptConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.Enum.forString(configExternalFilenameBuildMode));
        }
        if (scriptConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.COMPOSED) {
            scriptConfig.setComposeWithProjectName(Boolean.parseBoolean(configComposeWithProjectName));
            scriptConfig.setComposeWithTestSuiteName(Boolean.parseBoolean(configComposeWithTestSuiteName));
            scriptConfig.setComposeWithTestCaseName(Boolean.parseBoolean(configComposeWithTestCaseName));
            scriptConfig.setComposeWithTestStepName(Boolean.parseBoolean(configComposeWithTestStepName));
        }

        // here scriptConfig represents the script test step config with step in external file support added (if
        // it was already in config or if auto conversion is set in global settings).
        externalFilenameBuildMode = scriptConfig.getExternalFilenameBuildMode();
        composeWithProjectName = scriptConfig.isSetComposeWithProjectName() ? scriptConfig.getComposeWithProjectName() : false;
        composeWithTestSuiteName = scriptConfig.isSetComposeWithTestSuiteName() ? scriptConfig.getComposeWithTestSuiteName() : false;
        composeWithTestCaseName = scriptConfig.isSetComposeWithTestCaseName() ? scriptConfig.getComposeWithTestCaseName() : false;
        composeWithTestStepName = scriptConfig.isSetComposeWithTestStepName() ? scriptConfig.getComposeWithTestStepName() : false;

        String contentFromProjectDocument = null;
        contentFromProjectDocument = configContent;

        if (scriptConfig.getExternalFilenameBuildMode() != ExternalFilenameBuildModeConfig.NONE) {
            this.externalFilename = configExternalFilename;
            this.alternateFilenameForContent = configExternalFilename;
            // this will adjust externalFilename
            buildExternalFilenameForCurrentMode();
            scriptConfig.setExternalFilename(this.externalFilename);
            loadStepContent();
            if (contentFromProjectDocument != null && !contentFromProjectDocument.equals(stepContent)) {
                // content from project document differs with content from external file : which one to use ?
                // use project content if external file does not exists yet or is empty, otherwise, ask user.
                if (stepContent == null || stepContent.isEmpty()) {
                    stepContent = contentFromProjectDocument;
                } else {
                    if (alwaysPreferContentFromProject == null && alwaysPreferContentFromExternalFile == null) {
                        int choice = UISupport.yesYesToAllNoNoToAll("Step \n\n[" + getPathInProject() + "]\n\ncontent from (in memory) project document is different than the content of external file.\n\nUse project content and ignore external file for this step ?",
                                "Conflicting changes detected while loading project !", "Yes, use project content for all", "No, use external file content for all");
                        if (choice == 0 || choice == 1) {
                            stepContent = contentFromProjectDocument;
                            if (choice == 1) {
                                alwaysPreferContentFromProject = Boolean.TRUE;
                                alwaysPreferContentFromExternalFile = Boolean.FALSE;
                            }
                        } else if (choice == 3) {
                            alwaysPreferContentFromProject = Boolean.FALSE;
                            alwaysPreferContentFromExternalFile = Boolean.TRUE;
                        }
                    } else if (alwaysPreferContentFromProject) {
                        stepContent = contentFromProjectDocument;
                    }
                }
            }
        }
        if (alwaysPreferContentFromProject != null) {
            getTestCase().getTestSuite().getProject().setAlwaysPreferContentFromProject(alwaysPreferContentFromProject);
        }
        if (alwaysPreferContentFromExternalFile != null) {
            getTestCase().getTestSuite().getProject().setAlwaysPreferContentFromProject(alwaysPreferContentFromExternalFile);
        }
        scriptConfig.setStringValue(stepContent);

        // finally, regenerate the element in XML project document
        XmlCursor scriptCursor = testStepConfig.getConfig().newCursor();
        if (scriptCursor.toChild(new QName("", Script.SCRIPT_PROPERTY))) {
            scriptCursor.getObject().set(scriptConfig);
        } else {
            SoapUI.log.debug("Step " + getName() + " have no script child element, this could be a bug...");
        }
        scriptCursor.dispose();
    }

    public String buildExternalFilenameForMode(ExternalFilenameBuildModeConfig.Enum externalFilenameBuildMode) {

        if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.MANUAL) {
            return externalFilename;
        }

        String projectName, testSuiteName, testCaseName, testStepName;
        String sep = File.separator;
        projectName = this.testStep.getTestCase().getTestSuite().getProject().getName();
        testSuiteName = this.testStep.getTestCase().getTestSuite().getName();
        testCaseName = this.testStep.getTestCase().getName();
        testStepName = this.testStep.getName();
        StringBuilder stringBuilder = new StringBuilder();

        if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.AUTO) {
            stringBuilder.append(projectName).append(sep)
                    .append(testSuiteName).append(sep)
                    .append(testCaseName).append(sep)
                    .append(testStepName);
        } else if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.COMPOSED) {
            if (composeWithProjectName)   { stringBuilder.append(projectName).append(sep);   }
            if (composeWithTestSuiteName) { stringBuilder.append(testSuiteName).append(sep); }
            if (composeWithTestCaseName)  { stringBuilder.append(testCaseName).append(sep);  }
            if (composeWithTestStepName)  { stringBuilder.append(testStepName).append(sep);  }
        }
        return finishBuildExternalFilename(stringBuilder);
    }

    public String finishBuildExternalFilename(StringBuilder stringBuilder) {
        if (stringBuilder.length() == 0) {
            stringBuilder.append(DEFAULT_STEP_FILENAME);
        } else {
            // remove the trailing slash
            if (File.separatorChar == stringBuilder.charAt(stringBuilder.length()-1)) {
                stringBuilder.deleteCharAt(stringBuilder.length()-1);
            }
        }
        if (wsdlRequestConfig != null) {
            stringBuilder.append(WSDL_REQUEST_SUFFIX);
        } else if (testStepConfig != null) {
            stringBuilder.append(GROOVY_SCRIPT_SUFFIX);
        } else {
            SoapUI.log.error("TestRequestStepInExternalFileSupport : buildExternalFilenameForCurrentMode() called but we don't know which config to use : this is a bug.");
            return null;
        }
        return stringBuilder.toString();
    }

    private void buildExternalFilenameForCurrentMode() {
        // build mode is either AUTO, COMPOSED, MANUAL or NONE, but only the first two requires adjustment.
        if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.NONE || externalFilenameBuildMode == ExternalFilenameBuildModeConfig.MANUAL) {
            return;
        }

        setExternalFilename(buildExternalFilenameForMode(externalFilenameBuildMode));
    }

    /**
     * Reload content from external file if it needs to be refreshed
     *
     * @return true if content was reloaded from file
     */
    public boolean maybeReloadStepContent() {
        File contentFile = new File(toAbsolutePath(externalFilename));
        if (contentFile.exists() && lastModified > lastLoadedFromExternalFile && contentFile.lastModified() > lastModified) {
            if( UISupport.confirm( "Step content for \n\n[" + getPathInProject() + "]\n\nmodified both in memory and in external file, reload ?",
                    "Conflicting changes : reload from external file?" ) ) {
                loadStepContent();
                return true;
            }
        } else if (contentFile.exists() && contentFile.lastModified() > lastModified) {
            if (lastModified <= lastLoadedFromExternalFile || UISupport.confirm( "File for \n\n[" + getPathInProject() + "]\n\nhave been modified externally, reload ?",
                    "Confirm reload from external file?" ) ) {
                loadStepContent();
                return true;
            }
        } else if (contentFile.exists() && lastModified > lastLoadedFromExternalFile) {
            if( UISupport.confirm( "Discard changes made for \n\n[" + getPathInProject() + "]\n\n and reload from external file?",
                    "Confirm reload from external file?" ) ) {
                loadStepContent();
                return true;
            }
        }
        SoapUI.log.debug("maybeReloadStepContent() : no change to reload.");
        return false;
    }

    /**
     * Load content from external file into this step.
     *
     * Use this.externalFilename to check if a File of that name exists and load the content from it.
     * If no such file is found, use this.alternateFilenameForContent and try to load content from it.  If this fails
     * also, content will be empty.
     *
     * In any case, when this.externalFilename has a non-null, non-empty value at entry, it will have the same value
     * at exit.  If it was null or empty, it will be set to the default filename value.
     */
    public void loadStepContent() {
        if (externalFilename == null || externalFilename.isEmpty()) {
            externalFilename = DEFAULT_STEP_FILENAME;
            if (wsdlRequestConfig != null) {
                externalFilename = externalFilename + WSDL_REQUEST_SUFFIX;
            } else if (testStepConfig != null) {
                externalFilename = externalFilename + GROOVY_SCRIPT_SUFFIX;
            } else {
                externalFilename = externalFilename + DEFAULT_SUFFIX;
            }
        }

        File contentFile = new File(toAbsolutePath(externalFilename));
        if (! contentFile.exists()) {
            if (stepContent == null && alternateFilenameForContent != null) {
                contentFile = new File(toAbsolutePath(alternateFilenameForContent));
                if (! contentFile.exists()) {
                    if (stepContent == null) {
                        SoapUI.log.debug("Step (" + getName() + ") content set to empty string in TestRequestStepInExternalFileSupport.loadStepContent().");
                        stepContent = "";
                        return;
                    }
                } else {
                    if (stepContent == null) {
                        stepContent = readFile(contentFile);
                    }
                }
            }
        } else {
            stepContent = readFile(contentFile);
        }
    }

    private String toAbsolutePath(String filename) {
        StringBuffer pathBuffer = new StringBuffer(filename);
        if (!pathBuffer.toString().startsWith(File.separator)) {
            // is relative, prepend project's parent dir
            pathBuffer.insert(0, File.separator).insert(0, getExternalFileRootPath());
        }
        return pathBuffer.toString();
    }

    /**
     * Update config with current externalFilename.  If config already have a 'file' element equals to
     * this.externalFilename, no change to config is made and false is returned, otherwise config is updated
     * and true is returned.
     *
     * @return true if the config have been updated, false otherwise.
     */
    public boolean updateConfigWithExternalFilePath() {
        if (!getSettings().getBoolean(UISettings.STEP_IN_EXTERNAL_FILE)) {
            return false;
        }

        if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.NONE) {
            return false;
        }

        if (externalFilename == null || externalFilename.isEmpty()) {
            return false;
        }

        String currentFilename;

        if (wsdlRequestConfig != null) {
            currentFilename = wsdlRequestConfig.getExternalFilename();
            if (currentFilename != null && currentFilename.equals(externalFilename)) {
                return false;
            }
            wsdlRequestConfig.setExternalFilename(externalFilename);
        } else if (testStepConfig != null) {
            XmlObjectConfigurationReader reader = new XmlObjectConfigurationReader( testStepConfig.getConfig() );
            currentFilename = reader.readString("script/@externalFilename", null);
            if (currentFilename != null && currentFilename.equals(externalFilename)) {
                return false;
            }
            XmlCursor cursor = testStepConfig.getConfig().newCursor();
            if (cursor.toChild(Script.SCRIPT_PROPERTY)) {
                cursor.setAttributeText(new QName("", "externalFilename"), externalFilename);
            } else {
                SoapUI.log.error("could not get to 'script' element while trying to set the externalFileName attribute for step '" + getName() + "'");
            }
            cursor.dispose();
        }
        return true;
    }

    /**
     * Update config object, either wsdlRequestConfig or scriptConfig, to reflect current state
     *
     */
    public void updateConfig() {
        // TODO (marcpa) : these two blocks of code are so similar, there must be a way to refactor this.
        if (wsdlRequestConfig != null) {
            if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.NONE) {
                if (getSettings().getBoolean(UISettings.AUTO_CONVERT_STEP_TO_USE_EXTERNAL_FILE)) {
                    wsdlRequestConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.NONE);
                }
                if (wsdlRequestConfig.isSetComposeWithProjectName()) { wsdlRequestConfig.unsetComposeWithProjectName(); }
                if (wsdlRequestConfig.isSetComposeWithTestSuiteName()) { wsdlRequestConfig.unsetComposeWithTestSuiteName(); }
                if (wsdlRequestConfig.isSetComposeWithTestCaseName() ) { wsdlRequestConfig.unsetComposeWithTestCaseName(); }
                if (wsdlRequestConfig.isSetComposeWithTestStepName() ) { wsdlRequestConfig.unsetComposeWithTestStepName(); }
                wsdlRequestConfig.getRequest().setStringValue(stepContent);
            } else {
                if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.AUTO) {
                    wsdlRequestConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.AUTO);
                    if (wsdlRequestConfig.isSetComposeWithProjectName()) { wsdlRequestConfig.unsetComposeWithProjectName(); }
                    if (wsdlRequestConfig.isSetComposeWithTestSuiteName()) { wsdlRequestConfig.unsetComposeWithTestSuiteName(); }
                    if (wsdlRequestConfig.isSetComposeWithTestCaseName() ) { wsdlRequestConfig.unsetComposeWithTestCaseName(); }
                    if (wsdlRequestConfig.isSetComposeWithTestStepName() ) { wsdlRequestConfig.unsetComposeWithTestStepName(); }
                } else if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.COMPOSED) {
                    wsdlRequestConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.COMPOSED);
                    wsdlRequestConfig.setComposeWithProjectName( composeWithProjectName );
                    wsdlRequestConfig.setComposeWithTestSuiteName( composeWithTestSuiteName );
                    wsdlRequestConfig.setComposeWithTestCaseName( composeWithTestCaseName );
                    wsdlRequestConfig.setComposeWithTestStepName( composeWithTestStepName );
                } else if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.MANUAL) {
                    wsdlRequestConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.MANUAL);
                    if (wsdlRequestConfig.isSetComposeWithProjectName()) { wsdlRequestConfig.unsetComposeWithProjectName(); }
                    if (wsdlRequestConfig.isSetComposeWithTestSuiteName()) { wsdlRequestConfig.unsetComposeWithTestSuiteName(); }
                    if (wsdlRequestConfig.isSetComposeWithTestCaseName() ) { wsdlRequestConfig.unsetComposeWithTestCaseName(); }
                    if (wsdlRequestConfig.isSetComposeWithTestStepName() ) { wsdlRequestConfig.unsetComposeWithTestStepName(); }
                }
                wsdlRequestConfig.setExternalFilename( externalFilename );
            }

        } else if (scriptConfig != null) {
            if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.NONE) {
                if (getSettings().getBoolean(UISettings.AUTO_CONVERT_STEP_TO_USE_EXTERNAL_FILE)) {
                    scriptConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.NONE);
                }
                if (scriptConfig.isSetComposeWithProjectName()) { scriptConfig.unsetComposeWithProjectName(); }
                if (scriptConfig.isSetComposeWithTestSuiteName()) { scriptConfig.unsetComposeWithTestSuiteName(); }
                if (scriptConfig.isSetComposeWithTestCaseName() ) { scriptConfig.unsetComposeWithTestCaseName(); }
                if (scriptConfig.isSetComposeWithTestStepName() ) { scriptConfig.unsetComposeWithTestStepName(); }
                scriptConfig.setStringValue(stepContent);
            } else {
                if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.AUTO) {
                    scriptConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.AUTO);
                    if (scriptConfig.isSetComposeWithProjectName()) { scriptConfig.unsetComposeWithProjectName(); }
                    if (scriptConfig.isSetComposeWithTestSuiteName()) { scriptConfig.unsetComposeWithTestSuiteName(); }
                    if (scriptConfig.isSetComposeWithTestCaseName() ) { scriptConfig.unsetComposeWithTestCaseName(); }
                    if (scriptConfig.isSetComposeWithTestStepName() ) { scriptConfig.unsetComposeWithTestStepName(); }
                } else if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.COMPOSED) {
                    scriptConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.COMPOSED);
                    scriptConfig.setComposeWithProjectName( composeWithProjectName );
                    scriptConfig.setComposeWithTestSuiteName( composeWithTestSuiteName );
                    scriptConfig.setComposeWithTestCaseName( composeWithTestCaseName );
                    scriptConfig.setComposeWithTestStepName( composeWithTestStepName );
                } else if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.MANUAL) {
                    scriptConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.MANUAL);
                    if (scriptConfig.isSetComposeWithProjectName()) { scriptConfig.unsetComposeWithProjectName(); }
                    if (scriptConfig.isSetComposeWithTestSuiteName()) { scriptConfig.unsetComposeWithTestSuiteName(); }
                    if (scriptConfig.isSetComposeWithTestCaseName() ) { scriptConfig.unsetComposeWithTestCaseName(); }
                    if (scriptConfig.isSetComposeWithTestStepName() ) { scriptConfig.unsetComposeWithTestStepName(); }
                }
                scriptConfig.setExternalFilename( externalFilename );
                scriptConfig.setStringValue(stepContent);
                updateTestStepConfig(scriptConfig);
            }
        }
        else {
            SoapUI.log.error("updateConfig called but we have no config to work on ?");
        }
    }

    private void updateTestStepConfig(ScriptConfig scriptConfig) {
        XmlCursor cursor = testStepConfig.getConfig().newCursor();
        if (cursor.toChild(Script.SCRIPT_PROPERTY)) {
            cursor.setAttributeText(new QName("", "externalFilenameBuildMode"), externalFilenameBuildMode.toString());
            if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.COMPOSED) {
                if (scriptConfig.isSetComposeWithProjectName()) {
                    cursor.setAttributeText(new QName("", "composeWithProjectName"), Boolean.toString(scriptConfig.getComposeWithProjectName()));
                }
                if (scriptConfig.isSetComposeWithTestSuiteName()) {
                    cursor.setAttributeText(new QName("", "composeWithTestSuiteName"), Boolean.toString(scriptConfig.getComposeWithTestSuiteName()));
                }
                if (scriptConfig.isSetComposeWithTestCaseName()) {
                    cursor.setAttributeText(new QName("", "composeWithTestCaseName"), Boolean.toString(scriptConfig.getComposeWithTestCaseName()));
                }
                if (scriptConfig.isSetComposeWithTestStepName()) {
                    cursor.setAttributeText(new QName("", "composeWithTestStepName"), Boolean.toString(scriptConfig.getComposeWithTestStepName()));
                }
            }
            cursor.setAttributeText(new QName("", "externalFilename"), externalFilename);
            cursor.toFirstContentToken();
            cursor.removeXml();
            cursor.insertChars(stepContent);
            cursor.dispose();
        }
    }

    public void updateTestStepContent() {
        if (wsdlRequestConfig != null && testStep != null && testStep instanceof WsdlTestRequestStep) {
            WsdlTestRequestStep wsdlTestRequestStep = (WsdlTestRequestStep) testStep;
            wsdlTestRequestStep.getTestRequest().setRequestContent(stepContent);
        }
        if (scriptConfig != null && testStep != null && testStep instanceof WsdlGroovyScriptTestStep) {
            WsdlGroovyScriptTestStep wsdlGroovyScriptTestStep = (WsdlGroovyScriptTestStep) testStep;
            wsdlGroovyScriptTestStep.setScript(stepContent);
        }
    }

    private String readFile(File f) {
        if (f.exists()) {
            SoapUI.log.debug("Reading file '" + f.getAbsolutePath() + "'...");
            InputStreamProcessor processor = null;
            try {
                processor = new InputStreamProcessor() {
                    String fileContent;

                    @Override
                    public String getResult() {
                        return fileContent;
                    }

                    @Override
                    public void process(InputStream input) throws IOException {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
                        StringBuffer buf = new StringBuffer();
                        int data = reader.read();
                        while (data != -1) {
                            buf.append((char) data);
                            data = reader.read();
                        }
                        reader.close();
                        fileContent = buf.toString();
                    }
                };

                InputStreamProcessingTemplate.process(f.getAbsolutePath(), processor);
            } catch (Exception e) {
                SoapUI.logError(e);
            }
            if (processor != null) {
                lastLoadedFromExternalFile = new Date().getTime();
                lastModified = f.lastModified();
                return processor.getResult();
            } else {
                return "";
            }
        }
        return "";
    }

    /**
     * Save step content to external file.
     * Returns TestRequestStepExternalFileSaveStatus.SAVED when it was saved successfuly and nothing has to be done
     * by caller to get the latest content.
     * Returns TestRequestStepExternalFileSaveStatus.NOT_SAVED when it could not be saved.
     * Returns TestRequestStepExternalFileSaveStatus.RELOADED when content was reloaded from file instead of saved
     * (usually under user's instruction in face of a conflict).
     *
     * @return one of the Enum value of TestRequestStepExternalFileSaveStatus
     */
    public TestRequestStepExternalFileSaveStatus saveToExternalFile() {
        if (externalFilename != null && getSettings().getBoolean(UISettings.STEP_IN_EXTERNAL_FILE)) {
            return saveToExternalFile(false, true);
        }
        return TestRequestStepExternalFileSaveStatus.NOT_SAVED;
    }

    /**
     * Save step content to external file.
     * Returns TestRequestStepExternalFileSaveStatus.SAVED when it was saved successfuly and nothing has to be done
     * by caller to get the latest content.
     * Returns TestRequestStepExternalFileSaveStatus.NOT_SAVED when it could not be saved.
     * Returns TestRequestStepExternalFileSaveStatus.RELOADED when content was reloaded from file instead of saved
     * (usually under user's instruction in face of a conflict).
     *
     * @return one of the Enum value of TestRequestStepExternalFileSaveStatus
     */
    public TestRequestStepExternalFileSaveStatus saveToExternalFile(Boolean configChanged, Boolean forceSave) {
        if (getSettings().getBoolean(UISettings.STEP_IN_EXTERNAL_FILE)) {
            if (this.externalFilename != null) {
                SoapUI.log.debug("saveToExternalFile(configChanged:" + configChanged + ", forceSave:" + forceSave + "), externalFilename : '" + this.externalFilename + "'");

                // true when config is being changed in this method, i.e. if user chooses another file to save to
                boolean localConfigChanged = false;
                File file = new File(externalFilename);
                boolean originalFileExists = file.exists();
                if ( originalFileExists && configChanged && !UISupport.confirm("File \n\n[" + file.getName() + "]\n\n exists, overwrite?",
                        "Overwrite File?") ) {
                    file = UISupport.getFileDialogs().saveAs( this, "Save test step external file " + this.testStep.getName(), ".xml", "XML Files (*.xml)",
                            new File(externalFilename).getAbsoluteFile() );
                    if (file != null) {
                        externalFilename = file.getAbsolutePath();
                        localConfigChanged = updateConfigWithExternalFilePath();
                    } else {
                        localConfigChanged = false;
                    }
                }
                if ((localConfigChanged || (!originalFileExists && configChanged) || forceSave)) {
                    TestRequestStepExternalFileSaveStatus saveStatus = saveStepToFile();
                    if (saveStatus == TestRequestStepExternalFileSaveStatus.SAVED) {
                        SoapUI.log.debug("step '" + this.testStep.getName() + "' saved to " + externalFilename);
                    } else if (saveStatus == TestRequestStepExternalFileSaveStatus.NOT_SAVED) {
                        SoapUI.log.debug("step '" + this.testStep.getName() + "' NOT saved to " + externalFilename);
                    } else if (saveStatus == TestRequestStepExternalFileSaveStatus.RELOADED) {
                        SoapUI.log.debug("step '" + this.testStep.getName() + "' was reloaded from external file '" + externalFilename + "' instead.");
                    }
                    return saveStatus;
                }
            }
        }
        return TestRequestStepExternalFileSaveStatus.NOT_SAVED;
    }

    /**
     * Save step content to external file.
     * Returns TestRequestStepExternalFileSaveStatus.SAVED when it was saved successfuly and nothing has to be done
     * by caller to get the latest content.
     * Returns TestRequestStepExternalFileSaveStatus.NOT_SAVED when it could not be saved.
     * Returns TestRequestStepExternalFileSaveStatus.RELOADED when content was reloaded from file instead of saved
     * (usually under user's instruction in face of a conflict).
     *
     * @return one of the Enum value of TestRequestStepExternalFileSaveStatus
     */
    private TestRequestStepExternalFileSaveStatus saveStepToFile() {
        if (this.externalFilename == null) {
            return TestRequestStepExternalFileSaveStatus.NOT_SAVED;
        }
        StringBuffer pathBuffer = new StringBuffer();
        // TODO (marcpa) : use a portable way to do this (maybe create a File and check isAbsolutePath() ?
        if (!externalFilename.startsWith( File.separator ) ) {
            // is relative
            if (externalFileRootPath == null) {
                initExternalFileRootPath();
            }
            pathBuffer.append(externalFileRootPath).append( File.separator ).append(externalFilename);
        } else {
            // is absolute path
            pathBuffer.append(externalFilename);
        }

        File f = new File(pathBuffer.toString());
        try {
            if (! f.exists()) {
                File parent = f.getParentFile();
                if (! parent.exists()) {
                    parent.mkdirs();
                }
                f.createNewFile();
            } else if ( lastModified > lastLoadedFromExternalFile && f.lastModified() > lastLoadedFromExternalFile ) {
                // both in-memory step and external file were modified since step content was loaded in project file : let user
                // decide which one should win
                if (! UISupport.confirm("Both step \n\n[" + getPathInProject() + "]\n\nand external file have been modified since external file content was loaded in project.\n  Overwrite file with in-memory step content ?",
                        "Conflicting changes detected !")) {
                    if (UISupport.confirm("Reload step content from external file, discarding unsaved changes to step \n\n[" + getPathInProject() + "] ?", "Resolve conflict with external file.")) {
                        loadStepContent();
                        return TestRequestStepExternalFileSaveStatus.RELOADED;
                    } else {
                        return TestRequestStepExternalFileSaveStatus.NOT_SAVED;
                    }
                }
            }
            Files.write(stepContent, f, Charset.forName("UTF-8"));
            lastModified = f.lastModified();
            lastLoadedFromExternalFile = lastModified;
        } catch (IOException e) {
            e.printStackTrace();
            return TestRequestStepExternalFileSaveStatus.NOT_SAVED;
        }
        return TestRequestStepExternalFileSaveStatus.SAVED;
    }

    private void renameExternalFile(String original, String target) {
        File originalFile = new File(toAbsolutePath(original));
        File targetFile = new File(toAbsolutePath(target));

        if (originalFile.exists() && ! targetFile.exists()) {
            // it is safe to simply rename the file
            originalFile.renameTo(targetFile);
        } else if (originalFile.exists() && targetFile.exists()) {
            if (UISupport.confirm("File [" + targetFile.getName() + "] exists, overwrite?", "Overwrite File?") ) {
                originalFile.renameTo(targetFile);
            } else {
                String extensionForFileType, fileTypeDescription;
                if (wsdlRequestConfig != null) {
                    extensionForFileType = ".xml";
                    fileTypeDescription = "XML Files (*.xml)";
                } else if (testStepConfig != null) {
                    extensionForFileType = ".groovy";
                    fileTypeDescription = "Groovy Files (*.groovy)";
                } else {
                    extensionForFileType = Character.toString('*');
                    fileTypeDescription = "Any File";
                }
                targetFile = UISupport.getFileDialogs().saveAs( this, "Save test step external file " + this.testStep.getName(), extensionForFileType, fileTypeDescription,
                        new File(targetFile.getAbsolutePath()) );
                if (targetFile != null) {
                    originalFile.renameTo(targetFile);
                    lastModified = targetFile.lastModified();
                    lastLoadedFromExternalFile = lastModified;
                    if (! targetFile.getName().equals(originalFile.getName())) {
                        // renaming the new name of the file : need to change the step's name also
                        String stepName = targetFile.getName().replaceAll(extensionForFileType, "");
                        testStep.setName(stepName);
                        buildExternalFilenameForCurrentMode();
                    }
                }
            }
        }
        // if original file does not exist, then target file will be created at save time.
    }

    public String getExternalFilename() {
        return externalFilename;
    }

    public void setExternalFilename(String externalFilename) {
        this.externalFilename = externalFilename;
    }

    public String getExternalFileRootPath() {
        return externalFileRootPath;
    }

    public ExternalFilenameBuildModeConfig.Enum getExternalFilenameBuildMode() {
        return externalFilenameBuildMode;
    }

    public void setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.Enum externalFilenameBuildMode) {
        this.externalFilenameBuildMode = externalFilenameBuildMode;
    }
    public String getStepContent() {
        return stepContent;
    }

    public void setStepContent(String stepContent) {
        if (this.stepContent == null || (this.stepContent != null && ! this.stepContent.equals(stepContent))) {
            this.lastModified = new Date().getTime();
        }
        this.stepContent = stepContent;
    }

    public Boolean getComposeWithProjectName() {
        return composeWithProjectName;
    }

    public void setComposeWithProjectName(Boolean composeWithProjectName) {
        this.composeWithProjectName = composeWithProjectName;
    }

    public Boolean getComposeWithTestStepName() {
        return composeWithTestStepName;
    }

    public void setComposeWithTestStepName(Boolean composeWithTestStepName) {
        this.composeWithTestStepName = composeWithTestStepName;
    }

    public Boolean getComposeWithTestSuiteName() {
        return composeWithTestSuiteName;
    }

    public void setComposeWithTestSuiteName(Boolean composeWithTestSuiteName) {
        this.composeWithTestSuiteName = composeWithTestSuiteName;
    }

    public Boolean getComposeWithTestCaseName() {
        return composeWithTestCaseName;
    }

    public void setComposeWithTestCaseName(Boolean composeWithTestCaseName) {
        this.composeWithTestCaseName = composeWithTestCaseName;
    }

    public TestStep getTestStep() {
        return testStep;
    }

    public TestCase getTestCase() {
        if (testStep == null) {
            return null;
        }

        return testStep.getTestCase();
    }

    public String getRequestContent() {
        return stepContent;
    }

    public ModelItemConfig getConfig() {
        if (wsdlRequestConfig != null) {
            return wsdlRequestConfig;
        }
        if (testStepConfig != null) {
            return testStepConfig;
        }
        return null;
    }

    public String getPathInProject() {
        if (testStep == null) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder(testStep.getName());
        ModelItem modelItem = this;
        while (modelItem.getParent() != null) {
            modelItem = modelItem.getParent();
            stringBuilder.insert(0, "/").insert(0, modelItem.getName());
        }
        return stringBuilder.toString();
    }

    // Delegate ModelItem methods to testStep
    @Override
    public String getName() {
        if (testStep == null) {
            return null;
        }
        return testStep.getName();
    }

    @Override
    public String getId() {
        if (testStep == null) {
            return null;
        }
        return testStep.getId();
    }

    @Override
    public ImageIcon getIcon() {
        if (testStep == null) {
            return null;
        }
        return testStep.getIcon();
    }

    @Override
    public String getDescription() {
        if (testStep == null) {
            return null;
        }
        return testStep.getDescription();
    }

    public XmlBeansSettingsImpl getSettings() {
        if (testStep == null) {
            return null;
        }
        return this.settings;
    }

    @Override
    public List<? extends ModelItem> getChildren() {
        if (testStep == null) {
            return null;
        }
        return testStep.getChildren();
    }

    @Override
    public ModelItem getParent() {
        if (testStep == null) {
            return null;
        }

        return testStep.getParent();
    }

    @Override
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        testStep.addPropertyChangeListener(propertyName, listener);

    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        testStep.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        testStep.removePropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        testStep.removePropertyChangeListener(propertyName, listener);
    }
    // END of delegation to testStep


    public ScriptConfig getScriptConfig() {
        return scriptConfig;
    }

    @Override
    public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
        if (propertyChangeEvent.getPropertyName().equals(ModelItem.NAME_PROPERTY)) {
            String originalFilename = externalFilename;
            buildExternalFilenameForCurrentMode();
            String targetFilename = externalFilename;
            if (targetFilename != null && ! targetFilename.equals(originalFilename)) {
                updateConfigWithExternalFilePath();
                // name of file where to save test step content changed, also rename the physical file
                renameExternalFile(originalFilename, targetFilename);
            }
        } else if (propertyChangeEvent.getPropertyName().equals(Script.SCRIPT_PROPERTY)) {
            lastModified = new Date().getTime();
        } else if (propertyChangeEvent.getSource() == testStep && propertyChangeEvent.getPropertyName().equals(Request.REQUEST_PROPERTY)) {
            lastModified = new Date().getTime();
        }
    }

    @Override
    public void testCaseAdded(TestCase testCase) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void testCaseRemoved(TestCase testCase) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void testCaseMoved(TestCase testCase, int index, int offset) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void loadTestAdded(LoadTest loadTest) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void loadTestRemoved(LoadTest loadTest) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void testStepAdded(TestStep testStep, int index) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void testStepRemoved(TestStep testStep, int index) {
        if (testStep == getTestStep()) {
            File file = new File(toAbsolutePath(externalFilename));
            if (file.exists()) {
                file.delete();
            }
        }

    }

    @Override
    public void testStepMoved(TestStep testStep, int fromIndex, int offset) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void securityTestAdded(SecurityTest securityTest) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void securityTestRemoved(SecurityTest securityTest) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
