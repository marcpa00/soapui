package com.eviware.soapui.impl.support;

import static com.eviware.soapui.impl.support.ContentInExternalFileCategory.*;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.*;
import com.eviware.soapui.impl.settings.XmlBeansSettingsImpl;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.WsdlTestSuite;
import com.eviware.soapui.impl.wsdl.support.PathUtils;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.impl.wsdl.teststeps.*;
import com.eviware.soapui.impl.wsdl.teststeps.assertions.basic.GroovyScriptAssertion;
import com.eviware.soapui.model.ModelItem;
import com.eviware.soapui.model.TestModelItem;
import com.eviware.soapui.model.iface.Request;
import com.eviware.soapui.model.mock.MockService;
import com.eviware.soapui.model.project.Project;
import com.eviware.soapui.model.project.ProjectListener;
import com.eviware.soapui.model.support.ProjectListenerAdapter;
import com.eviware.soapui.model.support.TestSuiteListenerAdapter;
import com.eviware.soapui.model.testsuite.*;
import com.eviware.soapui.security.SecurityTest;
import com.eviware.soapui.settings.UISettings;
import com.eviware.soapui.support.InputStreamProcessingTemplate;
import com.eviware.soapui.support.InputStreamProcessor;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.xml.XmlObjectConfigurationReader;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.eviware.soapui.impl.support.ContentInExternalFile.*;

/**
 * Class for supporting content in external file : store the actual content of a wsdl request test step,
 * groovy test step script, groovy assertion, event script (after*, setup*, teardown*, etc) used in a project
 * into a file outside of the project XML document file.
 * <p>
 * Attributes are added to the schema of WsdlRequest and Script :
 * <ul>
 * <li><dt>externalFilename</dt>
 * <dd>pathname to the content on filesystem; when relative, it is based on directory of project file
 * (project.path)</dd>
 * </li>
 * <li><dt>externalFilenameBuildMode</dt>
 * <dd>how the content in external file feature is used for this content : NONE, AUTO or MANUAL (see below)</dd>
 * </li>
 * </ul>
 * These attributes are also used for script contents that are not formalized in the XSD, for example, 'script' for
 * groovy test step
 * and 'scriptText' for groovy assertion.
 * </p>
 * <p>
 * The filename can be manually specified (there is a ui configuration panel for steps that supports the current
 * feature) or it can be automatically computed by SoapUI when a project file is saved.  This is controlled by the
 * externalFilenameBuildMode attribute, which can take the values NONE, MANUAL or AUTO.
 * <ul>
 * <li>NONE : filename will be ignored for this, the content will be whatever there is in
 * the XML project file, no external file will be read or saved.</li>
 * <li>MANUAL : filename is an absolute or relative pathname provided by the user.</li>
 * <li>AUTO : filename is a relative pathname automatically built by SoapUI using the content location in its
 * project.  For example, a request test step will get a pathname build with :
 * project name + test suite name + test case name + test step name, each mapped to a directory
 * except of the test step name, which is mapped to a file.</li>
 * </ul>
 * <p>
 * Global settings for "content in external file" :
 * <ul>
 * <li>UISettings.CONTENT_IN_EXTERNAL_FILE : use the content in external file support if true, ignore attributes for
 * this feature in config even if they are specified.</li>
 * <li>UISettings.AUTO_CONVERT_CONTENT_TO_USE_EXTERNAL_FILE : when configuring a content or saving it, add the
 * attributes for "content in external file" support, defaulting to AUTO mode.</li>
 * <li>UISettings.ALSO_KEEP_IN_PROJECT_WHEN_CONTENT_IN_EXTERNAL_FILE : the request text will be also saved in project
 * XML document, for backward compatibility.</li>
 * </ul>
 * </p>
 */
public class ContentInExternalFileSupport implements ModelItem {
    private ContentInExternalFileCategory contentInExternalFileCategory;
    private ScriptCategory scriptCategory;
    private String scriptCategoryName;
    private ContentInExternalFileConfig actualConfig;

    private XmlBeansSettingsImpl settings;

    private Project project;
    private TestSuite testSuite;
    private TestCase testCase;
    private WsdlTestStep testStep;
    private WsdlTestRequest testRequest;
    private TestAssertion testAssertion;

    private PropertyChangeListener propertyChangeListener = new InternalPropertyChangeListener();
    private TestSuiteListener testSuiteListener = new InternalTestSuiteListener();
    private ProjectListener projectListener = new InternalProjectListener();
    private AssertionsListener assertionsListener = new InternalAssertionsListener();

    private String content;

    private String externalFileRootPath;
    private String externalFilename;
    private long lastLoadedFromExternalFile;
    private long lastModified;

    private ExternalFilenameBuildModeConfig.Enum externalFilenameBuildMode = ExternalFilenameBuildModeConfig.NONE;
    private Boolean alwaysPreferContentFromProject = null;
    private Boolean alwaysPreferContentFromExternalFile = null;

    private ContentInExternalFileSupport() {
        // prevent instantiation without required field values
    }

    /**
     * Utility static method that checks if the content in external file feature is selected by user settings.  Enabled mean the feature should be used
     * globally, but individual content holders (groovy test steps, groovy assertion, wsdl test steps, groovy scripts) may override it.
     *
     * @return true if it is enabled, false otherwise.
     */
    public static boolean isEnabled() {
        return SoapUI.getSettings().getBoolean(UISettings.CONTENT_IN_EXTERNAL_FILE, false);
    }

    /**
     * Base common constructor
     *
     * @param baseModelItem
     * @param settings
     */
    private ContentInExternalFileSupport(ModelItem baseModelItem, XmlBeansSettingsImpl settings) {
        if (baseModelItem instanceof WsdlTestStep) {
            this.testStep = (WsdlTestStep) baseModelItem;
            this.testCase = this.testStep.getTestCase();
            this.testSuite = this.testCase.getTestSuite();
            this.project = this.testSuite.getProject();
        }
        if (baseModelItem instanceof WsdlTestCase) {
            this.testCase = (WsdlTestCase) baseModelItem;
            this.testSuite = this.testCase.getTestSuite();
            this.project = this.testSuite.getProject();
        }
        if (baseModelItem instanceof WsdlTestSuite) {
            this.testSuite = (WsdlTestSuite) baseModelItem;
            this.project = this.testSuite.getProject();
        }
        if (baseModelItem instanceof WsdlProject) {
            this.project = (WsdlProject) baseModelItem;
        }
        if (this.project == null) {
            SoapUI.log.error("ContentInExternalFileSupport constructor failed to set 'project' field : instance is incomplete.");
            // TODO : throw an exception instead ?
            return;
        }
        initExternalFileRootPath();

        this.settings = settings;
        this.alwaysPreferContentFromProject = project.getAlwaysPreferContentFromProject();
        this.alwaysPreferContentFromExternalFile = project.getAlwaysPreferContentFromExternalFile();
        if (this.alwaysPreferContentFromProject != null && this.alwaysPreferContentFromExternalFile != null
                && this.alwaysPreferContentFromProject && this.alwaysPreferContentFromExternalFile) {
            // conflict, prefer project
            this.alwaysPreferContentFromExternalFile = Boolean.FALSE;
            SoapUI.log.debug("Forcing alwaysPreferContentFromExternalFile to false.");
        }

        addPropertyChangeListener(ModelItem.NAME_PROPERTY, propertyChangeListener);
        if (testStep != null) {
            testStep.addPropertyChangeListener(Request.REQUEST_PROPERTY, propertyChangeListener);
        }
        if (testSuite != null) {
            testSuite.addTestSuiteListener(testSuiteListener);
        }
        if (project != null) {
            project.addProjectListener(projectListener);
        }


    }

    /**
     * Constructor for saving content to external file on creating new element or when auto-converting.
     */
    public ContentInExternalFileSupport(Project project, String filename, XmlBeansSettingsImpl settings) {
        this(project, settings);
        this.externalFilename = externalFileRootPath + File.separator + filename;
        this.lastLoadedFromExternalFile = 0;
        this.lastModified = new Date().getTime();
    }

    /**
     * Constructor for WsdlTest based steps.
     *
     * @param wsdlTestStep
     * @param wsdlTestRequest
     * @param requestStepConfig
     * @param settings
     */
    public ContentInExternalFileSupport(WsdlTestStep wsdlTestStep, WsdlTestRequest wsdlTestRequest, RequestStepConfig requestStepConfig, XmlBeansSettingsImpl settings) {
        this(wsdlTestStep, settings);
        this.contentInExternalFileCategory = ContentInExternalFileCategory.WSDL_STEP;
        this.testRequest = wsdlTestRequest;
        this.actualConfig = new ContentInExternalFileConfig(requestStepConfig.getRequest());
    }

    /**
     * Constructor for GroovyScript steps.
     *
     * @param wsdlTestStep
     * @param testStepConfig
     * @param settings
     */
    public ContentInExternalFileSupport(WsdlTestStep wsdlTestStep, TestStepConfig testStepConfig, XmlBeansSettingsImpl settings) {
        this(wsdlTestStep, settings);
        this.contentInExternalFileCategory = ContentInExternalFileCategory.GROOVY_STEP;
        this.scriptCategory = ScriptCategory.TEST_STEP;

        this.actualConfig = new ContentInExternalFileConfig(testStepConfig);
        addPropertyChangeListener(Script.SCRIPT_PROPERTY, propertyChangeListener);
    }

    /**
     * Constructor for GroovyAssertion script.
     *
     * @param wsdlTestStep
     * @param testAssertionConfig
     * @param settings
     */
    public ContentInExternalFileSupport(WsdlTestStep wsdlTestStep, TestAssertion groovyTestAssertion, TestAssertionConfig testAssertionConfig, XmlBeansSettingsImpl settings) {
        this(wsdlTestStep, settings);
        this.contentInExternalFileCategory = ContentInExternalFileCategory.GROOVY_ASSERTION;
        this.scriptCategory = ScriptCategory.TEST_STEP_ASSERTION;
        this.scriptCategoryName = testAssertionConfig.getDomNode().getLocalName();
        if (this.scriptCategoryName == null) {
            this.scriptCategoryName = scriptCategory.getDefaultName();
        }
        this.testAssertion = groovyTestAssertion;
        this.actualConfig = new ContentInExternalFileConfig(testAssertionConfig);
        addPropertyChangeListener(TestAssertion.NAME_PROPERTY, propertyChangeListener);
        addPropertyChangeListener(GroovyScriptAssertion.GROOVY_ASSERTION_SCRIPT_PROPERTY, propertyChangeListener);
    }

    /**
     * Constructor for common scripts at project level.
     *
     * @param project
     * @param scriptConfig
     * @param settings
     */
    public ContentInExternalFileSupport(Project project, ScriptCategory scriptCategory, ScriptConfig scriptConfig, XmlBeansSettingsImpl settings) {
        this(project, settings);
        this.contentInExternalFileCategory = ContentInExternalFileCategory.SCRIPT;
        this.scriptCategory = scriptCategory;
        this.scriptCategoryName = scriptConfig.getDomNode().getLocalName();
        if (this.scriptCategoryName == null) {
            this.scriptCategoryName = scriptCategory.getDefaultName();
        }

        this.actualConfig = new ContentInExternalFileConfig(scriptConfig);
        addPropertyChangeListener(Script.SCRIPT_PROPERTY, propertyChangeListener);
        addPropertyChangeListener(scriptCategory.getPropertyNameForScriptCategory(), propertyChangeListener);
    }

    /**
     * Constructor for common scripts at test suite level.
     *
     * @param testSuite
     * @param scriptConfig
     * @param settings
     */
    public ContentInExternalFileSupport(TestSuite testSuite, ScriptCategory scriptCategory, ScriptConfig scriptConfig, XmlBeansSettingsImpl settings) {
        this(testSuite, settings);
        this.contentInExternalFileCategory = ContentInExternalFileCategory.SCRIPT;
        this.scriptCategory = scriptCategory;
        this.scriptCategoryName = scriptConfig.getDomNode().getLocalName();
        if (this.scriptCategoryName == null) {
            this.scriptCategoryName = scriptCategory.getDefaultName();
        }
        this.actualConfig = new ContentInExternalFileConfig(scriptConfig);
        addPropertyChangeListener(Script.SCRIPT_PROPERTY, propertyChangeListener);
    }

    public ContentInExternalFileSupport(TestCase testCase, ScriptCategory scriptCategory, ScriptConfig scriptConfig, XmlBeansSettingsImpl settings) {
        this(testCase, settings);
        this.contentInExternalFileCategory = ContentInExternalFileCategory.SCRIPT;
        this.scriptCategory = scriptCategory;
        this.scriptCategoryName = scriptConfig.getDomNode().getLocalName();
        if (this.scriptCategoryName == null) {
            this.scriptCategoryName = scriptCategory.getDefaultName();
        }
        this.actualConfig = new ContentInExternalFileConfig(scriptConfig);
        addPropertyChangeListener(Script.SCRIPT_PROPERTY, propertyChangeListener);
    }

    public void initExternalFilenameSupport() {
        if (actualConfig == null || actualConfig.getContentInExternalFileCategory() == null
                ||
                (!actualConfig.getContentInExternalFileCategory().equals(WSDL_STEP)
                        && !actualConfig.getContentInExternalFileCategory().equals(SCRIPT)
                        && !actualConfig.getContentInExternalFileCategory().equals(GROOVY_STEP)
                        && !actualConfig.getContentInExternalFileCategory().equals(GROOVY_ASSERTION))) {
            SoapUI.log.error("ContentInExternalFileSupport : initExternalFilenameSupport() called but we have no config to work on.");
            return;
        }

        // prepare a scriptConfig object if type of config does not naturally have one
        if (GROOVY_STEP.equals(actualConfig.getContentInExternalFileCategory()) || GROOVY_ASSERTION.equals(actualConfig.getContentInExternalFileCategory())) {
            // A groovy ad-hoc script content does not have a specific XmlBean class generated for, it is an XSD anyType and
            // needs to be parsed with basic XmlObject / XmlCursor api via XmlObjectConfigurationReader...
            XmlObjectConfigurationReader reader = new XmlObjectConfigurationReader(actualConfig.getWrappedConfig());
            String scriptPrefix = actualConfig.getScriptPrefixForConfig();
            String configExternalFilename = reader.readString(scriptPrefix + "/@externalFilename", null);
            String configExternalFilenameBuildMode = reader.readString(scriptPrefix + "/@externalFilenameBuildMode", null);
            String configContent = reader.readString(scriptPrefix, null);

            // ... however, we can create an instance of the XmlBeans generated class ScriptConfig and populate it ourselves
            ScriptConfig scriptConfig = ScriptConfig.Factory.newInstance();
            if (configExternalFilename != null) {
                scriptConfig.setExternalFilename(configExternalFilename);
            }
            if (configExternalFilenameBuildMode != null) {
                scriptConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.Enum.forString(configExternalFilenameBuildMode));
            }
            if (configContent != null) {
                scriptConfig.setStringValue(configContent);
            }
            actualConfig.setScriptConfig(scriptConfig);
        }

        if (actualConfig.isSetExternalFilename()) {
            // start with both filename values being what is in the config
            this.externalFilename = actualConfig.getExternalFilename();
        }

        boolean autoConvert = getSettings().getBoolean(UISettings.AUTO_CONVERT_CONTENT_TO_USE_EXTERNAL_FILE);
        if (!actualConfig.isSetExternalFilenameBuildMode() && autoConvert) {
            actualConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.AUTO);
            externalFilenameBuildMode = ExternalFilenameBuildModeConfig.AUTO;
        } else if (actualConfig.isSetExternalFilenameBuildMode()) {
            externalFilenameBuildMode = actualConfig.getExternalFilenameBuildMode();
        } else {
            externalFilenameBuildMode = ExternalFilenameBuildModeConfig.NONE;
        }

        String contentFromProjectDocument = null;
        if (testRequest != null) {
            contentFromProjectDocument = testRequest.getRequestContent();
        } else if (actualConfig.getScriptConfig() != null) {
            contentFromProjectDocument = actualConfig.getScriptConfig().getStringValue();
        }

        if (externalFilenameBuildMode != ExternalFilenameBuildModeConfig.NONE) {
            ContentInExternalFileLoadStatus loadStatus;
            // this will adjust externalFilename
            buildExternalFilenameForCurrentMode();
            loadStatus = loadContent();
            if (loadStatus.equals(ContentInExternalFileLoadStatus.NOT_LOADED) && !StringUtils.isNullOrEmpty(contentFromProjectDocument)) {
                content = contentFromProjectDocument;

                ContentInExternalFileSaveStatus saveStatus = saveFile();
                if (saveStatus == ContentInExternalFileSaveStatus.SAVED || saveStatus == ContentInExternalFileSaveStatus.RELOADED) {
                    SoapUI.log.debug("Element (" + getName() + ") content exists in project document but no external file exists : created file '" + externalFilename + "' and saved content to it.");
                } else {
                    SoapUI.log.debug("Element (" + getName() + ") content exists in project document but no external file exists : tried to save it to '" + externalFilename + "' but failed : content is in projectDocument but without an external file.");
                }
            } else {
                if (getSettings().getBoolean(UISettings.ALSO_KEEP_IN_PROJECT_WHEN_CONTENT_IN_EXTERNAL_FILE)
                        && !StringUtils.isNullOrEmpty(contentFromProjectDocument)
                        && !StringUtils.equalsIgnoringLineEndings(contentFromProjectDocument, content)) {
                    // content from project document differs with content from external file : which one to use ?
                    // use project content if external file does not exists yet or is empty, otherwise, ask user.
                    if (content == null || content.isEmpty()) {
                        content = contentFromProjectDocument;
                    } else {
                        if (alwaysPreferContentFromProject == null && alwaysPreferContentFromExternalFile == null) {
                            String prefix = "Step";
                            if (ContentInExternalFileCategory.GROOVY_ASSERTION.equals(contentInExternalFileCategory)) {
                                prefix = "Assertion";
                            }
                            if (ContentInExternalFileCategory.SCRIPT.equals(contentInExternalFileCategory)) {
                                prefix = "Script";
                            }
                            int choice = UISupport.yesYesToAllNoNoToAll(prefix + " \n\n[" + getPathInProject() + "]\n\ncontent from project document is different than the content of external file.\n\nUse project content and ignore external file for this " + prefix + " ?",
                                    "Conflicting changes detected while loading project !", "Yes, use project content for all", "No, use external file content for all");
                            if (choice == 0 || choice == 1) {
                                content = contentFromProjectDocument;
                                lastModified = new Date().getTime();
                                saveToExternalFile();
                                if (choice == 1) {
                                    alwaysPreferContentFromProject = Boolean.TRUE;
                                    alwaysPreferContentFromExternalFile = Boolean.FALSE;
                                }
                            } else if (choice == 2) {
                                lastLoadedFromExternalFile = new Date().getTime();
                                updateConfig();
                            } else if (choice == 3) {
                                alwaysPreferContentFromProject = Boolean.FALSE;
                                alwaysPreferContentFromExternalFile = Boolean.TRUE;
                                lastLoadedFromExternalFile = new Date().getTime();
                                updateConfig();
                            }
                        } else if (alwaysPreferContentFromProject) {
                            content = contentFromProjectDocument;
                            lastModified = new Date().getTime();
                            saveToExternalFile();
                        } else if (alwaysPreferContentFromExternalFile) {
                            lastLoadedFromExternalFile = new Date().getTime();
                            updateConfig();
                        }
                    }
                } else {
                    if (content == null) {
                        content = "";
                    }
                    if (!StringUtils.isNullOrEmpty(content) && StringUtils.isNullOrEmpty(contentFromProjectDocument)) {
                        // neeed to update config with external content
                        updateConfig();
                    }
                }
            }

            if (alwaysPreferContentFromProject != null) {
                getProject().setAlwaysPreferContentFromProject(alwaysPreferContentFromProject);
            }
            if (alwaysPreferContentFromExternalFile != null) {
                getProject().setAlwaysPreferContentFromExternalFile(alwaysPreferContentFromExternalFile);
            }
            if (externalFilenameBuildMode != ExternalFilenameBuildModeConfig.NONE) {
                actualConfig.setExternalFilename(PathUtils.normalizePath(externalFilename));
                SoapUI.log.debug("attribute externalFilename of XML config object set to '" + actualConfig.getExternalFilename() + "'");
            }

            if (SCRIPT.equals(actualConfig.getContentInExternalFileCategory())) {
                updateScript();
            } else if (GROOVY_ASSERTION.equals(actualConfig.getContentInExternalFileCategory())) {
                actualConfig.getScriptConfig().setStringValue(content);
            } else if (WSDL_STEP.equals(actualConfig.getContentInExternalFileCategory())) {
                WsdlRequestConfig wsdlRequestConfig = (WsdlRequestConfig) actualConfig.getConfig().changeType(WsdlRequestConfig.type);
                wsdlRequestConfig.getRequest().setStringValue(content);
            }
        }
    }

    public void initExternalFileRootPath() {
        if (externalFileRootPath == null || externalFileRootPath.isEmpty()) {
            externalFileRootPath = getProject().getPath();
            if (externalFileRootPath == null || externalFileRootPath.isEmpty()) {
                externalFileRootPath = System.getProperty("user.dir");
            }
        }
        if (externalFileRootPath != null && externalFileRootPath.endsWith(PROJECT_FILE_SUFFIX)) {
            externalFileRootPath = externalFileRootPath.replaceAll(PROJECT_FILE_SUFFIX, EXTERNAL_FILE_ROOT_PATH_SUFFIX);
        } else {
            externalFileRootPath = externalFileRootPath + EXTERNAL_FILE_ROOT_PATH_SUFFIX;
        }
    }

    public PropertyChangeListener getPropertyChangeListener() {
        return propertyChangeListener;
    }


    public String buildExternalFilenameForMode(ExternalFilenameBuildModeConfig.Enum externalFilenameBuildMode) {

        if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.MANUAL) {
            if (externalFilename == null || externalFilename.isEmpty()) {
                return finishBuildExternalFilename(null, externalFilenameBuildMode);
            }
            StringBuilder buf = new StringBuilder(new File(externalFilename).isAbsolute() ? File.separator : "");
            for (String p : PathUtils.asfilePartList(externalFilename)) {
                buf.append(PathUtils.replaceProblematicCharactersWithUnderscore(p)).append(File.separator);
            }
            return finishBuildExternalFilename(buf, externalFilenameBuildMode);
        }

        String projectName;
        String testSuiteName, testCaseName, testStepName, testAssertionName;
        String sep = File.separator;
        projectName = PathUtils.replaceProblematicCharactersWithUnderscore(getProject().getName());
        testSuiteName = (testSuite == null ? null : PathUtils.replaceProblematicCharactersWithUnderscore(testSuite.getName()));
        testCaseName = (testCase == null ? null : PathUtils.replaceProblematicCharactersWithUnderscore(testCase.getName()));
        testStepName = (testStep == null ? null : PathUtils.replaceProblematicCharactersWithUnderscore(testStep.getName()));
        testAssertionName = (testAssertion == null ? null : PathUtils.replaceProblematicCharactersWithUnderscore(testAssertion.getName()));
        StringBuilder stringBuilder = new StringBuilder();

        if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.AUTO) {
            stringBuilder.append(projectName);
            if (testSuiteName != null) {
                stringBuilder.append(sep).append(testSuiteName);
            }
            if (testCaseName != null) {
                stringBuilder.append(sep).append(testCaseName);
            }
            if (testStepName != null) {
                stringBuilder.append(sep).append(testStepName);
            }
            if (testAssertionName != null) {
                stringBuilder.append(sep).append(testAssertionName);
            }
            if (scriptCategoryName != null) {
                stringBuilder.append("-").append(scriptCategoryName);
            }
        }
        return finishBuildExternalFilename(stringBuilder, externalFilenameBuildMode);
    }

    public String finishBuildExternalFilename(StringBuilder stringBuilder, ExternalFilenameBuildModeConfig.Enum externalFilenameBuildMode) {
        if (stringBuilder == null) {
            stringBuilder = new StringBuilder();
        }
        if (stringBuilder.length() == 0) {
            stringBuilder.append(DEFAULT_STEP_FILENAME);
        } else {
            // remove the trailing slash
            if (File.separatorChar == stringBuilder.charAt(stringBuilder.length() - 1)) {
                stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            }
        }
        if (externalFilenameBuildMode != ExternalFilenameBuildModeConfig.MANUAL) {
            stringBuilder.append(actualConfig.getExternalFilenameSuffix());
        }
        return stringBuilder.toString();
    }

    private void buildExternalFilenameForCurrentMode() {
        // build mode is either AUTO, MANUAL or NONE, but only the first two requires adjustment.
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
        if (externalFilename == null) {
            return false;
        }
        File contentFile = new File(toAbsolutePath(externalFilename));
        if (contentFile.exists() && lastModified > lastLoadedFromExternalFile && contentFile.lastModified() > lastModified) {
            if (UISupport.confirm("Step content for \n\n[" + getPathInProject() + "]\n\nmodified both in memory and in external file, reload ?",
                    "Conflicting changes : reload from external file?")) {
                loadContent();
                return true;
            }
        } else if (contentFile.exists() && contentFile.lastModified() > lastModified) {
            if (lastModified <= lastLoadedFromExternalFile || UISupport.confirm("File for \n\n[" + getPathInProject() + "]\n\nhave been modified externally, reload ?",
                    "Confirm reload from external file?")) {
                loadContent();
                return true;
            }
        } else if (contentFile.exists() && lastModified > lastLoadedFromExternalFile) {
            if (UISupport.confirm("Discard changes made for \n\n[" + getPathInProject() + "]\n\n and reload from external file?",
                    "Confirm reload from external file?")) {
                loadContent();
                return true;
            }
        }
        SoapUI.log.debug("maybeReloadStepContent() : no change to reload.");
        return false;
    }


    /**
     * Load content from external file into this element.
     * Use this.externalFilename to check if a File of that name exists and load the content from it.
     * In any case, when this.externalFilename has a non-null, non-empty value at entry, it will have the same value
     * at exit.  If it was null or empty, it will be set to the default filename value.
     */
    public ContentInExternalFileLoadStatus loadContent() {
        if (externalFilename == null || externalFilename.isEmpty()) {
            externalFilename = DEFAULT_STEP_FILENAME + actualConfig.getExternalFilenameSuffix();
        }

        SoapUI.log.debug("loadContent() : current dir (user.dir) is : " + System.getProperty("user.dir"));

        File contentFile = new File(toAbsolutePath(externalFilename));
        String externalContentFilename = toAbsolutePath(externalFilename);

        File file = new File(externalContentFilename);
        if (file.exists()) {
            contentFile = file;
        } else {
            // Maybe the filename on filesystem is encoded with a different unicode normalization form : try with explicit normalization to NFC then NFD before giving up
            externalContentFilename = Normalizer.normalize(toAbsolutePath(externalFilename), Normalizer.Form.NFC);
            file = new File(externalContentFilename);
            if (file.exists()) {
                contentFile = file;
            } else {
                externalContentFilename = Normalizer.normalize(toAbsolutePath(externalFilename), Normalizer.Form.NFD);
                file = new File(externalContentFilename);
                if (file.exists()) {
                    contentFile = file;
                } else {
                    SoapUI.log.debug("File with external filename '" + externalFilename + "' does not exists, tried with unicode normalization forms C and D.");
                }
            }
        }

        if (!contentFile.exists()) {
            SoapUI.log.debug("file referenced by externalFilename does not exists...");
            if (content == null) {
                return ContentInExternalFileLoadStatus.NOT_LOADED;
            } else {
                // had some existing content from config but external file does not exists
                SoapUI.log.debug("Element (" + getName() + ") content exists in memory but no external file exists : creating file '" + externalFilename + "' and saving content to it.");
                ContentInExternalFileSaveStatus saveStatus = saveFile();
                if (saveStatus == ContentInExternalFileSaveStatus.SAVED || saveStatus == ContentInExternalFileSaveStatus.RELOADED) {
                    return ContentInExternalFileLoadStatus.SAVED_ON_LOAD;
                } else {
                    SoapUI.log.debug("   save failed : content is in memory but without an external file.");
                    return ContentInExternalFileLoadStatus.NOT_LOADED;
                }
            }
        } else {
            content = readFile(contentFile);
            return ContentInExternalFileLoadStatus.LOADED;
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
     * Update config with current externalFilename.  If config already have a 'externalFilename' element equals to
     * this.externalFilename, no change to config is made and false is returned, otherwise config is updated
     * and true is returned.
     *
     * @return true if the config have been updated, false otherwise.
     */
    public boolean updateConfigWithExternalFilePath() {
        if (!getSettings().getBoolean(UISettings.CONTENT_IN_EXTERNAL_FILE)) {
            return false;
        }

        if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.NONE) {
            return false;
        }

        if (externalFilename == null || externalFilename.isEmpty()) {
            return false;
        }

        String currentFilename = actualConfig.getExternalFilename();

        // TODO (marcpa00) : refactor this to have a single block using actualConfig abstraction
        if (WSDL_STEP.equals(actualConfig.getContentInExternalFileCategory()) || SCRIPT.equals(actualConfig.getContentInExternalFileCategory())) {
            //currentFilename = wsdlRequestConfig.getExternalFilename();
            if (currentFilename != null && currentFilename.equals(PathUtils.normalizePath(externalFilename))) {
                return false;
            }
            actualConfig.setExternalFilename(PathUtils.normalizePath(externalFilename));
            SoapUI.log.debug("request externalFilename attribute set to '" + actualConfig.getExternalFilename() + "'");
        } else if (GROOVY_STEP.equals(actualConfig.getContentInExternalFileCategory()) || GROOVY_ASSERTION.equals(actualConfig.getContentInExternalFileCategory())) {
            // TODO (marcpa00) : debug this and check if we really need to read the XmlConfiguration or couldn't we use the actualConfig.scriptConfig instead ?
            XmlObjectConfigurationReader reader = new XmlObjectConfigurationReader(actualConfig.getWrappedConfig());
            currentFilename = reader.readString(actualConfig.getScriptPrefixForConfig() + "/@externalFilename", null);
            if (currentFilename != null && currentFilename.equals(PathUtils.normalizePath(externalFilename))) {
                return false;
            }
            XmlCursor cursor = actualConfig.getWrappedConfig().newCursor();
            if (GROOVY_STEP.equals(actualConfig.getContentInExternalFileCategory())) {
                if (cursor.toChild(Script.SCRIPT_PROPERTY)) {
                    cursor.setAttributeText(EXTERNAL_FILENAME_QNAME, PathUtils.normalizePath(externalFilename));
                    SoapUI.log.debug("script externalFilename attribute set to '" + cursor.getAttributeText(EXTERNAL_FILENAME_QNAME));
                } else {
                    SoapUI.log.error("could not get to 'script' element while trying to set the externalFileName attribute for step '" + getName() + "'");
                }
            } else if (GROOVY_ASSERTION.equals(actualConfig.getContentInExternalFileCategory())) {
                if (cursor.toChild(Script.SCRIPT_ALT_PROPERTY)) {
                    cursor.setAttributeText(EXTERNAL_FILENAME_QNAME, PathUtils.normalizePath(externalFilename));
                    SoapUI.log.debug("script externalFilename attribute set to '" + cursor.getAttributeText(EXTERNAL_FILENAME_QNAME));
                } else {
                    SoapUI.log.error("could not get to 'script' element while trying to set the externalFileName attribute for step '" + getName() + "'");
                }
            }
            cursor.dispose();
        }
        return true;
    }

    /**
     * Update config object, to reflect current state
     */
    public void updateConfig() {
        if (actualConfig != null) {
            if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.NONE) {
                if (getSettings().getBoolean(UISettings.AUTO_CONVERT_CONTENT_TO_USE_EXTERNAL_FILE)) {
                    actualConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.NONE);
                }
                actualConfig.setContent(content);
            } else {
                if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.AUTO) {
                    actualConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.AUTO);
                } else if (externalFilenameBuildMode == ExternalFilenameBuildModeConfig.MANUAL) {
                    actualConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.MANUAL);
                }
                actualConfig.setExternalFilename(PathUtils.normalizePath(externalFilename));
                SoapUI.log.debug("request externalFilename attribute set to '" + actualConfig.getExternalFilename() + "'");

                actualConfig.setContent(content);
                actualConfig.updateConfig();
            }
        } else {
            SoapUI.log.error("updateConfig called but we have no config to work on ?");
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
                        BufferedReader reader = new BufferedReader(new InputStreamReader(input, System.getProperty("file.encoding")));
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
     * Save content to external file.
     * Returns ContentInExternalFileSaveStatus.SAVED when it was saved successfully and nothing has to be done
     * by caller to get the latest content.
     * Returns ContentInExternalFileSaveStatus.NOT_SAVED when it could not be saved.
     * Returns ContentInExternalFileSaveStatus.RELOADED when content was reloaded from file instead of saved
     * (usually under user's instruction in face of a conflict).
     *
     * @return one of the Enum value of ContentInExternalFileSaveStatus
     */
    public ContentInExternalFileSaveStatus saveToExternalFile() {
        return saveToExternalFile(false, true);
    }

    /**
     * Save content to external file.
     * Returns ContentInExternalFileSaveStatus.SAVED when it was saved successfully and nothing has to be done
     * by caller to get the latest content.
     * Returns ContentInExternalFileSaveStatus.NOT_SAVED when it could not be saved.
     * Returns ContentInExternalFileSaveStatus.RELOADED when content was reloaded from file instead of saved
     * (usually under user's instruction in face of a conflict).
     *
     * @return one of the Enum value of ContentInExternalFileSaveStatus
     */
    public ContentInExternalFileSaveStatus saveToExternalFile(Boolean configChanged, Boolean forceSave) {
        if (externalFilename != null && getSettings().getBoolean(UISettings.CONTENT_IN_EXTERNAL_FILE)) {
            // true when config is being changed in this method, i.e. if user chooses another file to save to
            boolean localConfigChanged = false;
            File file = new File(externalFilename);
            boolean originalFileExists = file.exists();
            if (originalFileExists && configChanged && !UISupport.confirm("File \n\n[" + file.getName() + "]\n\n exists, overwrite?",
                    "Overwrite File?")) {
                file = UISupport.getFileDialogs().saveAs(this, "Save test step external file " + this.testStep.getName(), ".xml", "XML Files (*.xml)",
                        new File(externalFilename).getAbsoluteFile());
                if (file != null) {
                    externalFilename = file.getAbsolutePath();
                    localConfigChanged = updateConfigWithExternalFilePath();
                } else {
                    localConfigChanged = false;
                }
            }
            if ((localConfigChanged || (!originalFileExists && configChanged) || forceSave)) {
                ContentInExternalFileSaveStatus saveStatus = saveFile();
                if (saveStatus == ContentInExternalFileSaveStatus.SAVED) {
                    SoapUI.log.debug("content '" + getName() + "' saved to " + externalFilename);
                } else if (saveStatus == ContentInExternalFileSaveStatus.NOT_SAVED) {
                    SoapUI.log.debug("content '" + getName() + "' NOT saved to " + externalFilename);
                } else if (saveStatus == ContentInExternalFileSaveStatus.RELOADED) {
                    SoapUI.log.debug("content '" + getName() + "' was reloaded from external file '" + externalFilename + "' instead.");
                }
                return saveStatus;
            }
        }
        return ContentInExternalFileSaveStatus.NOT_SAVED;
    }

    /**
     * Save content to external file.
     * Returns ContentInExternalFileSaveStatus.SAVED when it was saved successfully and nothing has to be done
     * by caller to get the latest content.
     * Returns ContentInExternalFileSaveStatus.NOT_SAVED when it could not be saved.
     * Returns ContentInExternalFileSaveStatus.RELOADED when content was reloaded from file instead of saved
     * (usually under user's instruction in face of a conflict).
     *
     * @return one of the Enum value of ContentInExternalFileSaveStatus
     */
    private ContentInExternalFileSaveStatus saveFile() {
        if (this.externalFilename == null) {
            return ContentInExternalFileSaveStatus.NOT_SAVED;
        }
        StringBuffer pathBuffer = new StringBuffer();
        // TODO (marcpa) : use a portable way to do this (maybe create a File and check isAbsolutePath() ?
        if (!externalFilename.startsWith(File.separator)) {
            // is relative
            if (externalFileRootPath == null) {
                initExternalFileRootPath();
            }
            pathBuffer.append(externalFileRootPath).append(File.separator).append(externalFilename);
        } else {
            // is absolute path
            pathBuffer.append(externalFilename);
        }

        File f = new File(pathBuffer.toString());
        try {
            if (!f.exists()) {
                File parent = f.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                f.createNewFile();
            } else if (lastLoadedFromExternalFile != 0 && lastModified > lastLoadedFromExternalFile && f.lastModified() > lastLoadedFromExternalFile) {
                // both in-memory step and external file were modified since step content was loaded in project file : let user
                // decide which one should win
                if (!UISupport.confirm("Both step \n\n[" + getPathInProject() + "]\n\nand external file have been modified since external file content was loaded in project.\n  Overwrite file with in-memory step content ?",
                        "Conflicting changes detected !")) {
                    if (UISupport.confirm("Reload step content from external file, discarding unsaved changes to step \n\n[" + getPathInProject() + "] ?", "Resolve conflict with external file.")) {
                        loadContent();
                        SoapUI.log.debug("saveFile() : file reloaded succesfully.");
                        return ContentInExternalFileSaveStatus.RELOADED;
                    } else {
                        SoapUI.log.debug("saveFile() : file not saved.");
                        return ContentInExternalFileSaveStatus.NOT_SAVED;
                    }
                }
            } else if (!isDirty() && !getSettings().getBoolean(UISettings.LINEBREAK)) {
                SoapUI.log.debug("OPTIMIZATION : file " + f.getName() + " is not modified, writing to disk skipped.");

                // TODO (marcpa00) : make a new status SAVED_NOT_NEEDED ?
                return ContentInExternalFileSaveStatus.SAVED;
            }
            if (getSettings().getBoolean(UISettings.LINEBREAK)) {
                content = StringUtils.stringNormalizeLineBreak(content);
            }

            // TODO (marcpa00) : force "UTF-8" or use platform default charset or file.encoding or a UIPrefs ?
            Files.write(content, f, Charset.forName(System.getProperty("file.encoding")));
            lastModified = f.lastModified();
            lastLoadedFromExternalFile = lastModified;
        } catch (IOException e) {
            e.printStackTrace();
            SoapUI.log.debug("saveFile() : file not saved.");
            return ContentInExternalFileSaveStatus.NOT_SAVED;
        }
        SoapUI.log.debug("saveFile() : file saved successfully.");
        return ContentInExternalFileSaveStatus.SAVED;
    }

    private void renameExternalFile(String original, String target, String topDir) {
        File originalFile = new File(toAbsolutePath(original));
        File targetFile = new File(toAbsolutePath(target));

        if (originalFile.exists() && !targetFile.exists()) {
            moveFileAndDeleteOriginalParentIfEmpty(originalFile, targetFile, topDir);
        } else if (originalFile.exists() && targetFile.exists()) {
            if (UISupport.confirm("File [" + targetFile.getName() + "] exists, overwrite?", "Overwrite File?")) {
                moveFileAndDeleteOriginalParentIfEmpty(originalFile, targetFile, topDir);
            } else {
                String extensionForFileType, fileTypeDescription;
                extensionForFileType = actualConfig.getExtensionForFileType();
                fileTypeDescription = actualConfig.getFileTypeDescription();

                // TODO (marcpa00) : fix this.testStep.getName() : might not be a step...
                targetFile = UISupport.getFileDialogs().saveAs(this, "Save test step external file " + this.testStep.getName(), extensionForFileType, fileTypeDescription,
                        new File(targetFile.getAbsolutePath()));
                if (targetFile != null) {
                    moveFileAndDeleteOriginalParentIfEmpty(originalFile, targetFile, topDir);
                    lastModified = targetFile.lastModified();
                    lastLoadedFromExternalFile = lastModified;
                    if (!targetFile.getName().equals(originalFile.getName())) {
                        // renaming the new name of the file : need to change the step's name also
                        String stepName = targetFile.getName().replaceAll(extensionForFileType, "");
                        // TODO (marcpa00) : fix testStep.setName() : might not be a step...
                        testStep.setName(stepName);
                        buildExternalFilenameForCurrentMode();
                    }
                }
            }
        }
        // if original file does not exist, then target file will be created at save time.
    }

    private void moveFileAndDeleteOriginalParentIfEmpty(File originalFile, File targetFile, String topDir) {
        String projectParentName = null;
        if (project != null && project.getPath() != null) {
            projectParentName = new File(project.getPath()).getParentFile().getName();
        }
        try {
            FileUtils.moveFile(originalFile, targetFile);
            File dir = originalFile;
            do {
                dir = dir.getParentFile();
                if (dir != null && dir.exists() && dir.isDirectory() && dir.list() != null && dir.list().length == 0) {
                    FileUtils.deleteDirectory(dir);
                }
            }
            while (dir != null && !dir.getName().equals(topDir) && !dir.getName().equals(projectParentName));
        } catch (IOException ioe) {
            SoapUI.log.error("IOException while trying to rename '" + originalFile.getName() + "' into '" + targetFile.getName() + "'", ioe);
        }

    }

    public boolean isDirty() {
        return lastModified > lastLoadedFromExternalFile;
    }

    public void makeDirty() {
        lastModified = new Date().getTime();
        if (lastModified == lastLoadedFromExternalFile) {
            lastModified++;
        }
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        if (this.content == null || (this.content != null && !this.content.equals(content))) {
            this.lastModified = new Date().getTime();
        }
        this.content = content;
    }

    public Project getProject() {
        return project;
    }

    public String getProjectName() {
        if (project != null) {
            return project.getName();
        } else {
            return "";
        }
    }

    public TestSuite getTestSuite() {
        return testSuite;
    }

    public String getTestSuiteName() {
        if (testSuite != null) {
            return testSuite.getName();
        } else {
            return "";
        }
    }

    public TestCase getTestCase() {
        return testCase;
    }

    public String getTestCaseName() {
        if (testCase != null) {
            return testCase.getName();
        } else {
            return "";
        }
    }

    public TestStep getTestStep() {
        return testStep;
    }

    public String getStepName() {
        if (testStep != null) {
            return testStep.getName();
        } else {
            return "";
        }
    }

    public TestAssertion getTestAssertion() {
        return testAssertion;
    }

    public String getRequestContent() {
        return content;
    }

    public XmlObject getConfig() {
        return actualConfig.getConfig();
    }

    public String getPathInProject() {
        if (ContentInExternalFileCategory.SCRIPT.equals(contentInExternalFileCategory)) {
            return externalFilename;
        }
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

    // Delegate ModelItem methods to first non-null model item
    @Override
    public String getName() {
        // TODO (marcpa00) : find another way to handle this special case, like returning scriptCategoryName and requiring caller to prefix project name itself
        if (scriptCategoryName != null) {
            return project.getName() + "-" + scriptCategoryName;
        }

        if (testAssertion != null) {
            return testAssertion.getName();
        }
        if (testStep != null) {
            return testStep.getName();
        }
        if (testCase != null) {
            return testCase.getName();
        }
        if (testSuite != null) {
            return testSuite.getName();
        }
        if (project != null) {
            return project.getName();
        }
        return null;
    }

    @Override
    public String getId() {
        if (testAssertion != null) {
            return testAssertion.getId();
        }
        if (testStep != null) {
            return testStep.getId();
        }
        if (testCase != null) {
            return testCase.getId();
        }
        if (testSuite != null) {
            return testSuite.getId();
        }
        if (project != null) {
            return project.getId();
        }
        return null;
    }

    @Override
    public ImageIcon getIcon() {
        if (testAssertion != null) {
            return testAssertion.getIcon();
        }
        if (testStep != null) {
            return testStep.getIcon();
        }
        if (testCase != null) {
            return testCase.getIcon();
        }
        if (testSuite != null) {
            return testSuite.getIcon();
        }
        if (project != null) {
            return project.getIcon();
        }
        return null;
    }

    @Override
    public String getDescription() {
        if (testAssertion != null) {
            return testAssertion.getDescription();
        }
        if (testStep != null) {
            return testStep.getDescription();
        }
        if (testCase != null) {
            return testCase.getDescription();
        }
        if (testSuite != null) {
            return testSuite.getDescription();
        }
        if (project != null) {
            return project.getDescription();
        }
        return null;
    }

    public XmlBeansSettingsImpl getSettings() {
        return this.settings;
    }

    @Override
    public List<? extends ModelItem> getChildren() {
        if (testAssertion != null) {
            return testAssertion.getChildren();
        }
        if (testStep != null) {
            return testStep.getChildren();
        }
        if (testCase != null) {
            return testCase.getChildren();
        }
        if (testSuite != null) {
            return testSuite.getChildren();
        }
        if (project != null) {
            return project.getChildren();
        }
        return null;
    }

    @Override
    public ModelItem getParent() {
        if (testAssertion != null) {
            return testAssertion.getParent();
        }
        if (testStep != null) {
            return testStep.getParent();
        }
        if (testCase != null) {
            return testCase.getParent();
        }
        if (testSuite != null) {
            return testSuite.getParent();
        }
        if (project != null) {
            return project.getParent();
        }
        return null;
    }

    @Override
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        if (testAssertion != null) {
            testAssertion.addPropertyChangeListener(propertyName, listener);
        }
        if (testStep != null) {
            testStep.addPropertyChangeListener(propertyName, listener);
        }
        if (testCase != null) {
            testCase.addPropertyChangeListener(propertyName, listener);
        }
        if (testSuite != null) {
            testSuite.addPropertyChangeListener(propertyName, listener);
        }
        if (project != null) {
            project.addPropertyChangeListener(propertyName, listener);
        }
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        if (testAssertion != null) {
            testAssertion.addPropertyChangeListener(listener);
        }
        if (testStep != null) {
            testStep.addPropertyChangeListener(listener);
        }
        if (testCase != null) {
            testCase.addPropertyChangeListener(listener);
        }
        if (testSuite != null) {
            testSuite.addPropertyChangeListener(listener);
        }
        if (project != null) {
            project.addPropertyChangeListener(listener);
        }
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        if (testAssertion != null) {
            testAssertion.removePropertyChangeListener(listener);
        }
        if (testStep != null) {
            testStep.removePropertyChangeListener(listener);
        }
        if (testCase != null) {
            testCase.removePropertyChangeListener(listener);
        }
        if (testSuite != null) {
            testSuite.removePropertyChangeListener(listener);
        }
        if (project != null) {
            project.removePropertyChangeListener(listener);
        }
    }

    @Override
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        if (testAssertion != null) {
            testAssertion.removePropertyChangeListener(propertyName, listener);
        }
        if (testStep != null) {
            testStep.removePropertyChangeListener(propertyName, listener);
        }
        if (testCase != null) {
            testCase.removePropertyChangeListener(propertyName, listener);
        }
        if (testSuite != null) {
            testSuite.removePropertyChangeListener(propertyName, listener);
        }
        if (project != null) {
            project.removePropertyChangeListener(propertyName, listener);
        }
    }
    // END of delegation to first non-null model item


    public ScriptConfig getScriptConfig() {
        return actualConfig.getScriptConfig();
    }

    public void clearExternalFileRootPath() {
        externalFileRootPath = null;
    }

    // Listeners

    // Property Listener
    private class InternalPropertyChangeListener implements PropertyChangeListener {
        @Override
        public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
            String propertyName = propertyChangeEvent.getPropertyName();
            Object eventSource = propertyChangeEvent.getSource();
            if (propertyName.equals(ModelItem.NAME_PROPERTY)) {
                // we receive the event for every assertion on test step, must check if it is the one for us
                if (eventSource instanceof GroovyScriptAssertion && scriptCategory != null && scriptCategory.equals(ScriptCategory.TEST_STEP_ASSERTION)
                        && ((GroovyScriptAssertion) eventSource).hashCode() == testAssertion.hashCode()) {
                    renameFileAndUpdateConfig(propertyChangeEvent);
                } else if (!(eventSource instanceof GroovyScriptAssertion)) {
                    renameFileAndUpdateConfig(propertyChangeEvent);
                }
            }
            if (propertyName.equals(GroovyScriptAssertion.GROOVY_ASSERTION_SCRIPT_PROPERTY)) {
                // we receive the event for every assertion on test step, must check if it is the one for us
                if (eventSource instanceof GroovyScriptAssertion && scriptCategory.equals(ScriptCategory.TEST_STEP_ASSERTION)
                        && ((GroovyScriptAssertion) eventSource).hashCode() == testAssertion.hashCode()) {
                    setContent((String) propertyChangeEvent.getNewValue());
                }
            }
            if (propertyName.equals(WsdlProject.AFTER_LOAD_SCRIPT_PROPERTY)
                    || propertyName.equals(WsdlProject.BEFORE_SAVE_SCRIPT_PROPERTY)
                    || propertyName.equals(WsdlProject.BEFORE_RUN_SCRIPT_PROPERTY)
                    || propertyName.equals(WsdlProject.AFTER_RUN_SCRIPT_PROPERTY)
                    || propertyName.equals(WsdlTestSuite.SETUP_SCRIPT_PROPERTY)
                    || propertyName.equals(WsdlTestSuite.TEARDOWN_SCRIPT_PROPERTY)
                    || propertyName.equals(WsdlTestCase.SETUP_SCRIPT_PROPERTY)
                    || propertyName.equals(WsdlTestCase.TEARDOWN_SCRIPT_PROPERTY)) {
                setContent((String) propertyChangeEvent.getNewValue());
            }
        }

        private void renameFileAndUpdateConfig(PropertyChangeEvent propertyChangeEvent) {
            String originalFilename = externalFilename;
            buildExternalFilenameForCurrentMode();
            String targetFilename = externalFilename;
            if (targetFilename != null && !targetFilename.equals(originalFilename)) {
                updateConfigWithExternalFilePath();
                // name of file where to save content changed, also rename the physical file
                renameExternalFile(originalFilename, targetFilename, (String) propertyChangeEvent.getOldValue());
            }
        }
    }

    // Assertions listener
    public void addAssertionsListener(TestRequest testRequest) {
        if (testRequest != null) {
            testRequest.addAssertionsListener(this.assertionsListener);
        }
    }

    private class InternalAssertionsListener implements AssertionsListener {

        @Override
        public void assertionAdded(TestAssertion assertion) {
            // NoOp
        }

        @Override
        public void assertionRemoved(TestAssertion assertion) {
            modelItemRemoved(assertion);
            assertion.getAssertable().removeAssertionsListener(this);
        }

        @Override
        public void assertionMoved(TestAssertion assertion, int ix, int offset) {
            // NoOp
        }
    }


    // utilities for listeners
    boolean isApplicable(ModelItem modelItem) {
        boolean applicable = getSettings().getBoolean(UISettings.CONTENT_IN_EXTERNAL_FILE);

        if (applicable) {
            if (modelItem instanceof TestSuite) {
                applicable = (modelItem == getTestSuite());
            } else if (modelItem instanceof TestCase) {
                applicable = (modelItem == getTestCase());
            } else if (modelItem instanceof TestStep) {
                applicable = (modelItem == getTestStep());
            } else if (modelItem instanceof WsdlMessageAssertion) {
                applicable = (modelItem == getTestAssertion());
            } else {
                applicable = false;
            }
        }
        return applicable && externalFilename != null && ExternalFilenameBuildModeConfig.AUTO.equals(externalFilenameBuildMode);
    }

    void removeExternalFile() {
        String projectParentName = new File(getProject().getPath()).getParentFile().getName();
        File file = new File(toAbsolutePath(externalFilename));
        try {
            if (file.exists()) {
                file.delete();
                File dir = file;
                do {
                    dir = dir.getParentFile();
                    if (dir != null && dir.exists() && dir.isDirectory() && dir.list() != null && dir.list().length == 0) {
                        FileUtils.deleteDirectory(dir);
                    }
                }
                while (dir != null && !dir.getName().equals(projectParentName));
            }
        } catch (IOException ioe) {
            SoapUI.log.error("IOException while trying to remove '" + externalFilename + "'");
        }
    }

    void modelItemRemoved(ModelItem modelItem) {
        if (isApplicable(modelItem)) {
            removeExternalFile();
        }
    }

    // Listeners through adapters

    // Project Listener
    private class InternalProjectListener extends ProjectListenerAdapter {
        @Override
        public void afterLoad(Project project) {
            // reset conflict resolution booleans
            alwaysPreferContentFromProject = null;
            alwaysPreferContentFromExternalFile = null;
        }

        @Override
        public void testSuiteRemoved(TestSuite testSuite) {
            modelItemRemoved(testSuite);
        }

    }

    // TestSuite Listener
    private class InternalTestSuiteListener extends TestSuiteListenerAdapter {
        @Override
        public void testCaseRemoved(TestCase testCase) {
            modelItemRemoved(testCase);
        }

        @Override
        public void testStepRemoved(TestStep testStep, int index) {
            modelItemRemoved(testStep);
        }

        @Override
        public void loadTestRemoved(LoadTest loadTest) {
            modelItemRemoved(loadTest);
        }

        @Override
        public void securityTestRemoved(SecurityTest securityTest) {
            modelItemRemoved(securityTest);
        }
    }

    public ScriptCategory getScriptCategory() {
        return scriptCategory;
    }

    public void setScriptCategory(ScriptCategory scriptCategory) {
        this.scriptCategory = scriptCategory;
    }

    public void updateScript() {
        if (getScriptCategory() == null && testStep != null && testStep instanceof WsdlTestRequestStep) {
            WsdlTestRequestStep wsdlTestRequestStep = (WsdlTestRequestStep) testStep;
            wsdlTestRequestStep.getTestRequest().setRequestContent(getContent());
            return;
        }

        if (project != null && project instanceof WsdlProject) {
            WsdlProject wsdlProject = (WsdlProject) project;
            switch (getScriptCategory()) {
                case PROJECT_AFTER_LOAD:
                    wsdlProject.setAfterLoadScript(getContent());
                    wsdlProject.notifyPropertyChanged(WsdlProject.AFTER_LOAD_SCRIPT_PROPERTY_RELOAD, null, getContent());
                    break;
                case PROJECT_AFTER_RUN:
                    wsdlProject.setAfterRunScript(getContent());
                    wsdlProject.notifyPropertyChanged(WsdlProject.AFTER_RUN_SCRIPT_PROPERTY_RELOAD, null, getContent());
                    break;
                case PROJECT_BEFORE_RUN:
                    wsdlProject.setBeforeRunScript(getContent());
                    wsdlProject.notifyPropertyChanged(WsdlProject.BEFORE_RUN_SCRIPT_PROPERTY_RELOAD, null, getContent());
                    break;
                case PROJECT_BEFORE_SAVE:
                    wsdlProject.setBeforeSaveScript(getContent());
                    wsdlProject.notifyPropertyChanged(WsdlProject.BEFORE_SAVE_SCRIPT_PROPERTY_RELOAD, null, getContent());
                    break;
                default:
                    break;
            }
        }
        if (testSuite != null && testSuite instanceof WsdlTestSuite) {
            WsdlTestSuite wsdlTestSuite = (WsdlTestSuite) testSuite;
            switch (getScriptCategory()) {
                case TEST_SUITE_SETUP:
                    wsdlTestSuite.setSetupScript(getContent());
                    wsdlTestSuite.notifyPropertyChanged(WsdlTestSuite.SETUP_SCRIPT_PROPERTY_RELOAD, null, getContent());
                    break;
                case TEST_SUITE_TEARDOWN:
                    wsdlTestSuite.setTearDownScript(getContent());
                    wsdlTestSuite.notifyPropertyChanged(WsdlTestSuite.TEARDOWN_SCRIPT_PROPERTY_RELOAD, null, getContent());
                    break;
                default:
                    break;
            }
        }
        if (testCase != null && testCase instanceof WsdlTestCase) {
            WsdlTestCase wsdlTestCase = (WsdlTestCase) testCase;
            switch (getScriptCategory()) {
                case TEST_CASE_SETUP:
                    wsdlTestCase.setSetupScript(getContent());
                    wsdlTestCase.notifyPropertyChanged(WsdlTestCase.SETUP_SCRIPT_PROPERTY_RELOAD, null, getContent());
                    break;
                case TEST_CASE_TEARDOWN:
                    wsdlTestCase.setTearDownScript(getContent());
                    wsdlTestCase.notifyPropertyChanged(WsdlTestCase.TEARDOWN_SCRIPT_PROPERTY_RELOAD, null, getContent());
                    break;
                default:
                    break;
            }
        }
        if (testStep != null && testStep instanceof WsdlGroovyScriptTestStep && ScriptCategory.TEST_STEP.equals(getScriptCategory())) {
            WsdlGroovyScriptTestStep wsdlGroovyScriptTestStep = (WsdlGroovyScriptTestStep) testStep;
            wsdlGroovyScriptTestStep.setScript(getContent());
        }
        if (testAssertion != null && testAssertion instanceof GroovyScriptAssertion && ScriptCategory.TEST_STEP_ASSERTION.equals(getScriptCategory())) {
            GroovyScriptAssertion groovyScriptAssertion = (GroovyScriptAssertion) testAssertion;
            groovyScriptAssertion.setScriptText(getContent());
            groovyScriptAssertion.notifyPropertyChanged(GroovyScriptAssertion.GROOVY_ASSERTION_SCRIPT_PROPERTY_RELOAD, null, getContent());
        }
    }

    /**
     * Visit every instance of ContentInExternalFileSupport in this project and clear its externalFileRootPath.  Useful when moving project to
     * some other place, such as when we do a saveAs().
     */
    public static void resetExternalFileRootPath(WsdlProject project) {
        List<ContentInExternalFileSupport> externalContents = new ArrayList<ContentInExternalFileSupport>();

        externalContents.add(project.getAfterLoadContentInExternalFile());
        externalContents.add(project.getAfterRunContentInExternalFile());
        externalContents.add(project.getBeforeSaveContentInExternalFile());
        externalContents.add(project.getBeforeRunContentInExternalFile());

        for (TestSuite testSuite : project.getTestSuiteList()) {
            if (testSuite instanceof WsdlTestSuite) {
                WsdlTestSuite wsdlTestSuite = (WsdlTestSuite) testSuite;
                externalContents.add(wsdlTestSuite.getSetupScriptContentInExternalFile());
                externalContents.add(wsdlTestSuite.getTearDownScriptContentInExternalFile());
            }
            for (TestCase testCase : testSuite.getTestCaseList()) {
                if (testCase instanceof WsdlTestCase) {
                    WsdlTestCase wsdlTestCase = (WsdlTestCase) testCase;
                    externalContents.add(wsdlTestCase.getSetupScriptContentInExternalFile());
                    externalContents.add(wsdlTestCase.getTearDownScriptContentInExternalFile());
                }
                for (TestStep testStep : testCase.getTestStepList()) {
                    if (testStep instanceof WsdlGroovyScriptTestStep) {
                        WsdlGroovyScriptTestStep step = (WsdlGroovyScriptTestStep) testStep;
                        externalContents.add(step.getContentInExternalFileSupport());
                    } else if (testStep instanceof WsdlTestRequestStep) {
                        WsdlTestRequestStep step = (WsdlTestRequestStep) testStep;
                        externalContents.add(step.getContentInExternalFileSupport());

                        WsdlTestRequest wsdlTestRequest = step.getTestRequest();
                        for (TestAssertion testAssertion : wsdlTestRequest.getAssertionList()) {
                            if (testAssertion instanceof GroovyScriptAssertion) {
                                GroovyScriptAssertion groovyScriptAssertion = (GroovyScriptAssertion) testAssertion;
                                externalContents.add(groovyScriptAssertion.getContentInExternalFileSupport());
                            }
                        }
                    } else if (testStep instanceof HttpTestRequestStep) {
                        HttpTestRequestStep httpTestRequestStep = (HttpTestRequestStep) testStep;
                        for (TestAssertion testAssertion : httpTestRequestStep.getTestRequest().getAssertionList()) {
                            if (testAssertion instanceof GroovyScriptAssertion) {
                                GroovyScriptAssertion groovyScriptAssertion = (GroovyScriptAssertion) testAssertion;
                                externalContents.add(groovyScriptAssertion.getContentInExternalFileSupport());
                            }
                        }
                    }
                }
            }
        }
        for (ContentInExternalFileSupport contentInExternalFileSupport : externalContents) {
            if (contentInExternalFileSupport != null) {
                contentInExternalFileSupport.clearExternalFileRootPath();
                contentInExternalFileSupport.makeDirty();
            }
        }
    }

}
