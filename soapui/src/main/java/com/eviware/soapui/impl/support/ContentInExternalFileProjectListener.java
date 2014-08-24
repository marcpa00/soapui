package com.eviware.soapui.impl.support;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.ExternalFilenameBuildModeConfig;
import com.eviware.soapui.config.SoapuiProjectDocumentConfig;
import com.eviware.soapui.config.WsdlRequestConfig;
import com.eviware.soapui.impl.settings.XmlBeansSettingsImpl;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.WsdlTestSuite;
import com.eviware.soapui.impl.wsdl.support.PathUtils;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.impl.wsdl.teststeps.HttpTestRequestStep;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlGroovyScriptTestStep;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestRequest;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestRequestStep;
import com.eviware.soapui.impl.wsdl.teststeps.assertions.basic.GroovyScriptAssertion;
import com.eviware.soapui.model.project.Project;
import com.eviware.soapui.model.support.ProjectListenerAdapter;
import com.eviware.soapui.model.testsuite.TestAssertion;
import com.eviware.soapui.model.testsuite.TestCase;
import com.eviware.soapui.model.testsuite.TestStep;
import com.eviware.soapui.model.testsuite.TestSuite;
import com.eviware.soapui.settings.UISettings;
import com.eviware.soapui.support.StringUtils;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;

import javax.xml.namespace.QName;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.eviware.soapui.impl.support.ContentInExternalFile.*;

/**
 * Listener to save content to external files when saving project.
 */
public class ContentInExternalFileProjectListener extends ProjectListenerAdapter {

    public void beforeSave(Project project) {
        SoapUI.log.info("In ContentInExternalFileProjectListener.beforeSave(" + project.getName() + ")");
        List<ContentInExternalFileSupport> externalContents = new ArrayList<ContentInExternalFileSupport>();
        ContentInExternalFileSaveStatus saveStatus;

        if (project instanceof WsdlProject) {
            WsdlProject wsdlProject = (WsdlProject) project;
            convertToContentInExternalFileIfNeeded(wsdlProject, wsdlProject.getSettings());

            addIfNeedsToBeSaved(externalContents, wsdlProject.getAfterLoadContentInExternalFile());
            addIfNeedsToBeSaved(externalContents, wsdlProject.getAfterRunContentInExternalFile());
            addIfNeedsToBeSaved(externalContents, wsdlProject.getBeforeSaveContentInExternalFile());
            addIfNeedsToBeSaved(externalContents, wsdlProject.getBeforeRunContentInExternalFile());
        }

        for (TestSuite testSuite : project.getTestSuiteList()) {
            if (testSuite instanceof WsdlTestSuite) {
                WsdlTestSuite wsdlTestSuite = (WsdlTestSuite) testSuite;
                addIfNeedsToBeSaved(externalContents, wsdlTestSuite.getSetupScriptContentInExternalFile());
                addIfNeedsToBeSaved(externalContents, wsdlTestSuite.getTearDownScriptContentInExternalFile());
            }
            for (TestCase testCase : testSuite.getTestCaseList()) {
                if (testCase instanceof WsdlTestCase) {
                    WsdlTestCase wsdlTestCase = (WsdlTestCase) testCase;
                    addIfNeedsToBeSaved(externalContents, wsdlTestCase.getSetupScriptContentInExternalFile());
                    addIfNeedsToBeSaved(externalContents, wsdlTestCase.getTearDownScriptContentInExternalFile());
                }
                for (TestStep testStep : testCase.getTestStepList()) {
                    if (testStep instanceof WsdlGroovyScriptTestStep) {
                        WsdlGroovyScriptTestStep step = (WsdlGroovyScriptTestStep) testStep;
                        addIfNeedsToBeSaved(externalContents, step.getContentInExternalFileSupport());
                    } else if (testStep instanceof WsdlTestRequestStep) {
                        WsdlTestRequestStep step = (WsdlTestRequestStep) testStep;
                        addIfNeedsToBeSaved(externalContents, step.getContentInExternalFileSupport());

                        WsdlTestRequest wsdlTestRequest = step.getTestRequest();
                        for (TestAssertion testAssertion : wsdlTestRequest.getAssertionList()) {
                            if (testAssertion instanceof GroovyScriptAssertion) {
                                GroovyScriptAssertion groovyScriptAssertion = (GroovyScriptAssertion) testAssertion;
                                addIfNeedsToBeSaved(externalContents, groovyScriptAssertion.getContentInExternalFileSupport());
                            }
                        }
                    } else if (testStep instanceof HttpTestRequestStep) {
                        HttpTestRequestStep httpTestRequestStep = (HttpTestRequestStep) testStep;
                        for (TestAssertion testAssertion : httpTestRequestStep.getTestRequest().getAssertionList()) {
                            if (testAssertion instanceof GroovyScriptAssertion) {
                                GroovyScriptAssertion groovyScriptAssertion = (GroovyScriptAssertion) testAssertion;
                                addIfNeedsToBeSaved(externalContents, groovyScriptAssertion.getContentInExternalFileSupport());
                            }
                        }
                    }
                }
            }
        }
        for (ContentInExternalFileSupport contentInExternalFileSupport : externalContents) {
            if (contentInExternalFileSupport != null) {
                saveStatus = contentInExternalFileSupport.saveToExternalFile();
                if (saveStatus == ContentInExternalFileSaveStatus.RELOADED) {
                    contentInExternalFileSupport.updateScript();
                }
            }
        }

    }

    private void addIfNeedsToBeSaved(List<ContentInExternalFileSupport> externalFileSupports, ContentInExternalFileSupport contentInExternalFileSupport) {
        if (contentInExternalFileSupport != null && contentInExternalFileSupport.isDirty()) {
            externalFileSupports.add(contentInExternalFileSupport);
        }
    }

    private void convertToContentInExternalFileIfNeeded(WsdlProject wsdlProject, XmlBeansSettingsImpl settings) {

        SoapuiProjectDocumentConfig projectDocumentConfig = wsdlProject.getProjectDocument();
        // When UISettings have USE_EXTERNAL_FILE and step does not override it to NONE ensure that  for every
        // wsdl request and groovy script, there is an externalFilename attribute present.
        List<XmlObject> xmlObjects = new ArrayList<XmlObject>();
        for (String path : ALL_PATHS_IN_CONFIG) {
            xmlObjects.addAll(Arrays.asList(projectDocumentConfig.selectPath(CONFIG_NAMESPACE + "$this/" + path)));
        }

        for (XmlObject xmlObject : xmlObjects) {
            boolean needsConversion = false;
            //
            // contentInExternalFileConfigurationCursor  is where the externalFilenameBuildMode and externalFilename attributes are to be found
            // contentCursor is where the text of content is to be found : this is what will be externalized
            // contentContainerCursor is where the type and name of element (for example, the testStep for a WsdlRequest) are to be found
            //
            XmlCursor contentInExternalFileConfigurationCursor = xmlObject.newCursor();
            XmlCursor contentCursor = xmlObject.newCursor();
            String namespaceUri = contentCursor.namespaceForPrefix("con");
            QName innerRequestQName = new QName(namespaceUri, "request");
            QName scriptRequestQName = new QName(namespaceUri, "script");

            XmlCursor parentCursor = xmlObject.selectPath(CONFIG_NAMESPACE + "$this/..")[0].newCursor();
            XmlCursor contentContainerCursor;

            if (contentCursor == null || parentCursor == null) {
                continue;
            }

            String externalizableContentName;
            String externalizableContentType;

            // TODO (marcpa00) : regroup this into static methods in ContentInExternalFileSupport so we don't have the logic at two different places
            if ("config".equals(parentCursor.getName().getLocalPart())) {
                contentContainerCursor = xmlObject.selectPath(CONFIG_NAMESPACE + "$this/../..")[0].newCursor();
                externalizableContentName = contentContainerCursor.getAttributeText(NAME_QNAME);
                externalizableContentType = contentContainerCursor.getAttributeText(TYPE_QNAME);
                if (REQUEST_TYPE.equals(contentCursor.getName().getLocalPart())) {
                    if (StringUtils.isNullOrEmpty(contentCursor.getTextValue())) {
                        // a request without content, nothing to do
                        continue;
                    }
                    if (!contentCursor.toChild(innerRequestQName)) {
                        SoapUI.log.debug("could not advance to child 'con:request' !");
                    }
                } else if ("script".equals(contentCursor.getName())) {
                    if (!contentCursor.toChild(scriptRequestQName)) {
                        SoapUI.log.debug("could not advance to child 'script' !");
                    }
                }
            } else if ("configuration".equals(parentCursor.getName().getLocalPart()) && "assertion".equals(xmlObject.selectPath(CONFIG_NAMESPACE + "$this/../..")[0].newCursor().getName().getLocalPart())) {
                contentContainerCursor = xmlObject.selectPath(CONFIG_NAMESPACE + "$this/../..")[0].newCursor();
                externalizableContentName = contentContainerCursor.getAttributeText(NAME_QNAME);
                externalizableContentType = GROOVY_TYPE;
            } else {
                contentContainerCursor = parentCursor;
                externalizableContentName = contentContainerCursor.getAttributeText(NAME_QNAME) + "-" + contentInExternalFileConfigurationCursor.getName().getLocalPart();
                externalizableContentType = GROOVY_TYPE;
            }


            String externalFilenameBuildModeValue = contentInExternalFileConfigurationCursor.getAttributeText(EXTERNAL_FILENAME_BUILD_MODE_QNAME);
            if (externalFilenameBuildModeValue != null && externalFilenameBuildModeValue.equals(ExternalFilenameBuildModeConfig.NONE.toString())) {
                // skip this element, it does not want to use external filename
                SoapUI.log.debug("Content '" + externalizableContentName + "' of type " + externalizableContentType + " : ");
                SoapUI.log.debug("   does not want to use content in external file (mode is NONE) : skipping it.");
                continue;
            }
            if (externalFilenameBuildModeValue == null && !settings.getBoolean(UISettings.AUTO_CONVERT_CONTENT_TO_USE_EXTERNAL_FILE)) {
                // skip this step, it does not use external filename and we are not in auto-convert mode
                continue;
            } else if (externalFilenameBuildModeValue == null) {
                needsConversion = true;
            }

            if (externalizableContentType != null) {
                StringBuilder stringBuilder = computePathInProject(contentContainerCursor, externalizableContentName);
                if (needsConversion) {
                    SoapUI.log.debug("AUTO-CONVERT to content in external file : content " + externalizableContentName + " will be stored in an external file.");
                }

                ContentInExternalFileSupport contentInExternalFileSupport;

                WsdlRequestConfig wsdlRequestConfig;
                if (externalizableContentType.equals(REQUEST_TYPE)) {
                    wsdlRequestConfig = (WsdlRequestConfig) xmlObject.changeType(WsdlRequestConfig.type);

                    if (!wsdlRequestConfig.isSetExternalFilename()) {
                        stringBuilder.append(WSDL_REQUEST_SUFFIX);

                        // step does not yet use a file element, add it
                        wsdlRequestConfig.setExternalFilename(PathUtils.normalizePath(stringBuilder.toString()));
                        contentInExternalFileSupport = new ContentInExternalFileSupport(wsdlProject, wsdlRequestConfig.getExternalFilename(), settings);
                        contentInExternalFileSupport.setContent(contentCursor.getTextValue());
                        contentInExternalFileSupport.saveToExternalFile(false, false);

                        SoapUI.log.debug("   external filename is '" + stringBuilder.toString() + "'");
                        SoapUI.log.debug("   request externalFilename attribute set to '" + wsdlRequestConfig.getExternalFilename());
                        if (needsConversion) {
                            wsdlRequestConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.AUTO);
                        }
                    } else {
                        // check that filename is normalized and update config if not
                        String normalizedFilename = PathUtils.normalizePath(wsdlRequestConfig.getExternalFilename());
                        if (!normalizedFilename.equals(wsdlRequestConfig.getExternalFilename())) {
                            wsdlRequestConfig.setExternalFilename(normalizedFilename);
                            SoapUI.log.debug("   normalized externalFilename to '" + wsdlRequestConfig.getExternalFilename() + "'");
                        }
                    }
                } else if (externalizableContentType.equals(GROOVY_TYPE)) {
                    String externalFilenameFromAttribute = contentInExternalFileConfigurationCursor.getAttributeText(EXTERNAL_FILENAME_QNAME);
                    if (externalFilenameFromAttribute == null) {
                        if (needsConversion) {
                            contentInExternalFileConfigurationCursor.setAttributeText(EXTERNAL_FILENAME_BUILD_MODE_QNAME, ExternalFilenameBuildModeConfig.AUTO.toString());
                        }
                        stringBuilder.append(GROOVY_SCRIPT_SUFFIX);
                        contentInExternalFileConfigurationCursor.setAttributeText(EXTERNAL_FILENAME_QNAME, PathUtils.normalizePath(stringBuilder.toString()));
                        contentInExternalFileSupport = new ContentInExternalFileSupport(wsdlProject, contentInExternalFileConfigurationCursor.getAttributeText(EXTERNAL_FILENAME_QNAME), settings);
                        contentInExternalFileSupport.setContent(contentCursor.getTextValue());
                        contentInExternalFileSupport.saveToExternalFile(false, false);

                        SoapUI.log.debug("    external filename is '" + stringBuilder.toString() + "'");
                        SoapUI.log.debug("    script externalFilename attribute set to '" + contentInExternalFileConfigurationCursor.getAttributeText(EXTERNAL_FILENAME_QNAME));

                    } else {
                        // check that filename is normalized and update config if not
                        String normalizedFilename = PathUtils.normalizePath(externalFilenameFromAttribute);
                        if (!normalizedFilename.equals(contentInExternalFileConfigurationCursor.getAttributeText(EXTERNAL_FILENAME_QNAME))) {
                            contentInExternalFileConfigurationCursor.setAttributeText(EXTERNAL_FILENAME_QNAME, normalizedFilename);
                            SoapUI.log.debug("   normalized externalFilename to '" + contentInExternalFileConfigurationCursor.getAttributeText(EXTERNAL_FILENAME_QNAME) + "'");
                        }
                    }
                }
            }
            contentCursor.dispose();
            contentInExternalFileConfigurationCursor.dispose();
            parentCursor.dispose();
            contentContainerCursor.dispose();
        }
    }

    public StringBuilder computePathInProject(XmlCursor contentContainerCursor, String externalizableContentName) {
        StringBuilder stringBuilder = new StringBuilder(PathUtils.replaceProblematicCharactersWithUnderscore(externalizableContentName));
        // Build the path to step by going up the hierarchy until name of parent is null, i.e. up to the project
        // We produce a relative path.
        while (contentContainerCursor.toParent()) {
            QName elementQName = contentContainerCursor.getName();

            if (elementQName == null) {
                // project level reached, remove leading slash if present
                if (stringBuilder.charAt(0) == File.separatorChar) {
                    stringBuilder.deleteCharAt(0);
                }
                break;
            } else {
                String name = contentContainerCursor.getAttributeText(NAME_QNAME);
                String elementName = contentContainerCursor.getName().getLocalPart();

                // skip unnamed levels or levels that are in-between and would contribute with a superfluous directory name
                if (name != null && !"request".equals(elementName)) {
                    stringBuilder.insert(0, File.separator).insert(0, PathUtils.replaceProblematicCharactersWithUnderscore(name));
                }
            }
        }
        return stringBuilder;
    }


}
