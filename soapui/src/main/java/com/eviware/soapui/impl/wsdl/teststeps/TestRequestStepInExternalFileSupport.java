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
import com.eviware.soapui.model.project.Project;
import com.eviware.soapui.model.testsuite.TestCase;
import com.eviware.soapui.model.testsuite.TestStep;
import com.eviware.soapui.settings.UISettings;
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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
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
public class TestRequestStepInExternalFileSupport implements ModelItem, PropertyChangeListener  {
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

    private Boolean composeWithProjectName;

    private Boolean composeWithTestSuiteName;
    private Boolean composeWithTestCaseName;
    private Boolean composeWithTestStepName;
    private ExternalFilenameBuildModeConfig.Enum externalFilenameBuildMode = ExternalFilenameBuildModeConfig.NONE;
    private String alternateFilenameForContent;

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

        addPropertyChangeListener( ModelItem.NAME_PROPERTY, this);
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

        addPropertyChangeListener( ModelItem.NAME_PROPERTY, this);
    }



    public void initExternalFilenameSupport() {

        initExternalFileRootPath();
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

        if (testRequest != null) {
            stepContent = testRequest.getRequestContent();
        }

        if (externalFilenameBuildMode != ExternalFilenameBuildModeConfig.NONE) {
            // this will adjust externalFilename
            buildExternalFilenameForCurrentMode();
            loadStepContent();
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
        String configContent = reader.readString("script", null);

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
        stepContent = configContent;
        composeWithProjectName = scriptConfig.isSetComposeWithProjectName() ? scriptConfig.getComposeWithProjectName() : false;
        composeWithTestSuiteName = scriptConfig.isSetComposeWithTestSuiteName() ? scriptConfig.getComposeWithTestSuiteName() : false;
        composeWithTestCaseName = scriptConfig.isSetComposeWithTestCaseName() ? scriptConfig.getComposeWithTestCaseName() : false;
        composeWithTestStepName = scriptConfig.isSetComposeWithTestStepName() ? scriptConfig.getComposeWithTestStepName() : false;


        if (scriptConfig.getExternalFilenameBuildMode() != ExternalFilenameBuildModeConfig.NONE) {
            this.externalFilename = scriptConfig.getExternalFilename();
            this.alternateFilenameForContent = configExternalFilename;
            // this will adjust externalFilename
            buildExternalFilenameForCurrentMode();
            loadStepContent();
        }
        scriptConfig.setStringValue(stepContent);

        // finally, regenerate the element in XML project document
        XmlCursor scriptCursor = testStepConfig.getConfig().newCursor();
        if (scriptCursor.toChild(new QName("", "script"))) {
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
     * Load content from external file into this step.
     *
     * Use this.externalFilename to check if a File of that name exists and load the content from it.
     * If no such file is found, use this.alternateFilenameForContent and try to load content from it.  If this fails
     * also, content will be empty.
     *
     * In any case, when this.externalFilename has a non-null, non-empty value at entry, it will have the same value
     * at exit.  If it was null or empty, it will be set to the default filename value.
     */
    private void loadStepContent() {
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
            if (stepContent == null) {
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
        if (!pathBuffer.toString().startsWith("/")) {
            // is relative, prepend project's parent dir
            pathBuffer.insert(0, System.getProperty("file.separator")).insert(0, new File(((Project) this.testStep.getTestCase().getParent().getParent()).getPath()).getParent());
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
            if (cursor.toChild("script")) {
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
            }
        }
        else {
            SoapUI.log.error("updateConfig called but we have no config to work on ?");
        }
    }

    private String readFile(File f) {
        if (f.exists()) {
            SoapUI.log.debug("Reading file '" + f.getAbsolutePath() + "'...");
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
            SoapUI.log.debug("File with path " + f.getAbsolutePath() + " does not exists");
            SoapUI.log.debug("  current working directory is " + System.getProperty("user.dir"));
        }
        return "";
    }

    public void saveToExternalFile() {
        if (externalFilename != null && getSettings().getBoolean(UISettings.STEP_IN_EXTERNAL_FILE)) {
            saveToExternalFile(false, true);
        }
    }

    public void saveToExternalFile(Boolean configChanged, Boolean forceSave) {
        if (getSettings().getBoolean(UISettings.STEP_IN_EXTERNAL_FILE)) {
            if (this.externalFilename != null) {
                SoapUI.log.debug("saveToExternalFile(configChanged:" + configChanged + ", forceSave:" + forceSave + "), externalFilename : '" + this.externalFilename + "'");

                // true when config is being changed in this method, i.e. if user chooses another file to save to
                boolean localConfigChanged = false;
                File file = new File(externalFilename);
                boolean originalFileExists = file.exists();
                if ( originalFileExists && configChanged && !UISupport.confirm("File [" + file.getName() + "] exists, overwrite?",
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
                if ((localConfigChanged || (!originalFileExists && configChanged) || forceSave) && saveStepToFile()) {
                    SoapUI.log.debug("step '" + this.testStep.getName() + "' saved to " + externalFilename);
                }
            }
        }
    }

    private boolean saveStepToFile() {
        if (this.externalFilename == null) {
            return false;
        }
        StringBuffer pathBuffer = new StringBuffer();
        // TODO (marcpa) : use a portable way to do this (maybe create a File and check isAbsolutePath() ?
        if (!externalFilename.startsWith( System.getProperty("file.separator")) ) {
            // is relative
            if (externalFileRootPath == null) {
                initExternalFileRootPath();
            }
            pathBuffer.append(externalFileRootPath).append(System.getProperty("file.separator")).append(externalFilename);
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
            }
            Files.write(stepContent, f, Charset.forName("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void renameExternalFile(String original, String target) {
        File originalFile = new File(original);
        File targetFile = new File(target);

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
                // name of file where to save test step content changed, also rename the physical file
                renameExternalFile(originalFilename, targetFilename);
            }
        }
    }
}
