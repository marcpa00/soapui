package com.eviware.soapui.impl.wsdl.teststeps;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.ExternalFilenameBuildModeConfig;
import com.eviware.soapui.config.RequestStepConfig;
import com.eviware.soapui.config.ScriptConfig;
import com.eviware.soapui.config.TestStepConfig;
import com.eviware.soapui.config.WsdlRequestConfig;
import com.eviware.soapui.impl.settings.XmlBeansSettingsImpl;
import com.eviware.soapui.model.project.Project;
import com.eviware.soapui.settings.UISettings;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.xml.XmlObjectConfigurationReader;
import com.google.common.io.Files;
import org.apache.xmlbeans.XmlCursor;

import javax.xml.namespace.QName;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;

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
public class TestRequestStepInExternalFileSupport {
    private WsdlRequestConfig wsdlRequestConfig;
    private TestStepConfig testStepConfig;
    private XmlBeansSettingsImpl settings;
    private WsdlTestStep testStep;
    private WsdlTestRequest testRequest;

    private String stepContent;

    private String requestRootPath;
    private String stepExternalFilePath;

    private Boolean composeWithProjectName;

    private Boolean composeWithTestSuiteName;
    private Boolean composeWithTestCaseName;
    private Boolean composeWithTestStepName;
    private ExternalFilenameBuildModeConfig.Enum externalFilenameBuildMode = ExternalFilenameBuildModeConfig.NONE;

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
    }

    public XmlBeansSettingsImpl getSettings() {
        return this.settings;
    }

    public void initExternalFilenameSupport() {
        externalFilenameBuildMode = wsdlRequestConfig.getExternalFilenameBuildMode();
        composeWithProjectName = wsdlRequestConfig.isSetComposeWithProjectName() ? wsdlRequestConfig.getComposeWithProjectName() : false;
        composeWithTestSuiteName = wsdlRequestConfig.isSetComposeWithTestSuiteName() ? wsdlRequestConfig.getComposeWithTestSuiteName() : false;
        composeWithTestCaseName = wsdlRequestConfig.isSetComposeWithTestCaseName() ? wsdlRequestConfig.getComposeWithTestCaseName() : false;
        composeWithTestStepName = wsdlRequestConfig.isSetComposeWithTestStepName() ? wsdlRequestConfig.getComposeWithTestStepName() : false;

        if ((externalFilenameBuildMode == ExternalFilenameBuildModeConfig.AUTO || externalFilenameBuildMode == ExternalFilenameBuildModeConfig.COMPOSED)
                && wsdlRequestConfig.isSetExternalFilename()
                && ! autoComputedFile().exists() && ! externalFile().exists() && ! getSettings().getBoolean(UISettings.ALSO_KEEP_IN_PROJECT_WHEN_STEP_IN_EXTERNAL_FILE)
                || (getSettings().getBoolean( UISettings.ALSO_KEEP_IN_PROJECT_WHEN_STEP_IN_EXTERNAL_FILE ) && wsdlRequestConfig.getRequest().isNil())) {
            // Step content initially empty

            // instruct @externalFilename value to be set to computed filename at save time

            // save file name target is autocomputed filename
        }

        if (getSettings().getBoolean(UISettings.STEP_IN_EXTERNAL_FILE)) {
            stepExternalFilePath = wsdlRequestConfig.isSetExternalFilename() ? computeExternalFilePathFromConfig() : "new-request.xml";
        } else {
            stepExternalFilePath = null;
        }

        stepContent = testRequest.getRequestContent();
    }

    public void initExternalFilenameSupportForScript() {
        ScriptConfig scriptConfig = ScriptConfig.Factory.newInstance();

        XmlObjectConfigurationReader reader = new XmlObjectConfigurationReader( testStepConfig.getConfig() );

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
            projectName = this.testStep.getTestCase().getTestSuite().getProject().getName();
            testSuiteName = this.testStep.getTestCase().getTestSuite().getName();
            testCaseName = this.testStep.getTestCase().getName();
            testStepName = this.testStep.getName();
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
            stepExternalFilePath = externalFilename;
            StringBuffer pathBuffer = new StringBuffer();
            if (!stepExternalFilePath.startsWith("/")) {
                // is relative
                if (requestRootPath == null) {
                    requestRootPath = ".";
                }
                pathBuffer.append(requestRootPath).append(System.getProperty("file.separator")).append(stepExternalFilePath);
            } else {
                // is absolute path
                pathBuffer.append(stepExternalFilePath);
            }

            stepContent = readFile(pathBuffer.toString());
        }

        XmlCursor scriptCursor = testStepConfig.getConfig().newCursor();
        if (scriptCursor.toChild(new QName("", "script"))) {
            scriptCursor.getObject().set(scriptConfig);
            SoapUI.log.info("In WsdlGroovyScriptTestStep.readConfig() : replaced the script child element with compute script config.");
        } else {
            SoapUI.log.info("In WsdlGroovyScriptTestStep.readConfig() : no script child element found, weird...");
        }
        scriptCursor.dispose();
    }

    private File autoComputedFile() {
        return new File(computeExternalFilePathFromConfig());
    }

    private File externalFile() {
        return new File(".");
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

    /**
     * Update config with current stepExternalFilePath.  If config already have a 'file' element equals to
     * this.stepExternalFilePath, no change to config is made and false is returned, otherwise config is updated
     * and true is returned.
     *
     * @return true if the config have been updated, false otherwise.
     */
    public boolean updateConfigWithExternalFilePath() {
        if (!getSettings().getBoolean(UISettings.STEP_IN_EXTERNAL_FILE)) {
            return false;
        }

        if (stepExternalFilePath == null || stepExternalFilePath.isEmpty()) {
            return false;
        }

        String currentFileValue = wsdlRequestConfig.getExternalFilename();
        if (currentFileValue != null && currentFileValue.equals(stepExternalFilePath)) {
            return false;
        }

        wsdlRequestConfig.setExternalFilename(stepExternalFilePath);
        SoapUI.log.info("set file element of wsdl request to '" + stepExternalFilePath + "'");
        return true;
    }

    private String computeExternalFilePathFromConfig() {


        String pathname = wsdlRequestConfig.getExternalFilename();

        if (pathname != null) {
            stepExternalFilePath = pathname;
            StringBuffer pathBuffer = new StringBuffer();
            if (!stepExternalFilePath.startsWith("/")) {
                // is relative
                if (requestRootPath == null) {
                    requestRootPath = new File(((Project)this.testStep.getTestCase().getParent().getParent()).getPath()).getParent();
                }
                pathBuffer.append(requestRootPath).append(System.getProperty("file.separator")).append(stepExternalFilePath);
            } else {
                // is absolute path
                pathBuffer.append(stepExternalFilePath);
            }

            if (this.testRequest != null) {
                testRequest.setRequestContent(readFile(pathBuffer.toString()));
            }
        }
        return pathname;
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
        if (stepExternalFilePath != null && getSettings().getBoolean(UISettings.STEP_IN_EXTERNAL_FILE)) {
            saveToExternalFile(false, true);
        }
    }

    public void saveToExternalFile(Boolean configChanged, Boolean forceSave) {
        if (getSettings().getBoolean(UISettings.STEP_IN_EXTERNAL_FILE)) {
            if (this.stepExternalFilePath != null) {
                SoapUI.log.info("*** this step has an externalFilePath : '" + this.stepExternalFilePath + "'");

                // true when config is being changed in this method, i.e. if user chooses another file to save to
                boolean localConfigChanged = false;
                File file = new File(stepExternalFilePath);
                boolean originalFileExists = file.exists();
                if ( originalFileExists && configChanged && !UISupport.confirm("File [" + file.getName() + "] exists, overwrite?",
                        "Overwrite File?") ) {
                    file = UISupport.getFileDialogs().saveAs( this, "Save test step external file " + this.testStep.getName(), ".xml", "XML Files (*.xml)",
                            new File(stepExternalFilePath).getAbsoluteFile() );
                    if (file != null) {
                        stepExternalFilePath = file.getAbsolutePath();
                        localConfigChanged = updateConfigWithExternalFilePath();
                    } else {
                        localConfigChanged = false;
                    }
                }
                if ((localConfigChanged || (!originalFileExists && configChanged) || forceSave) && saveStepToFile()) {
                    SoapUI.log.info("step '" + this.testStep.getName() + "' saved to " + stepExternalFilePath);
                }
            }
        }
    }

    private boolean saveStepToFile() {
        if (this.stepExternalFilePath == null) {
            return false;
        }
        StringBuffer pathBuffer = new StringBuffer();
        // TODO (marcpa) : use a portable way to do this (maybe create a File and check isAbsolutePath() ?
        if (!stepExternalFilePath.startsWith( System.getProperty("file.separator")) ) {
            // is relative
            if (requestRootPath == null) {
                requestRootPath = ".";
            }
            pathBuffer.append(requestRootPath).append(System.getProperty("file.separator")).append(stepExternalFilePath);
        } else {
            // is absolute path
            pathBuffer.append(stepExternalFilePath);
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

    public String getStepExternalFilePath() {
        return stepExternalFilePath;
    }

    public void setStepExternalFilePath(String stepExternalFilePath) {
        this.stepExternalFilePath = stepExternalFilePath;
    }

    public String getRequestRootPath() {
        return requestRootPath;
    }

    public void setRequestRootPath(String requestRootPath) {
        this.requestRootPath = requestRootPath;
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


}
