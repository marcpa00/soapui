package com.eviware.soapui.impl.support;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.ExternalFilenameBuildModeConfig;
import com.eviware.soapui.config.ScriptConfig;
import com.eviware.soapui.config.SoapuiProjectDocumentConfig;
import com.eviware.soapui.config.WsdlInterfaceConfig;
import com.eviware.soapui.config.WsdlRequestConfig;
import com.eviware.soapui.impl.settings.XmlBeansSettingsImpl;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.WsdlRequest;
import com.eviware.soapui.impl.wsdl.WsdlTestSuite;
import com.eviware.soapui.impl.wsdl.support.PathUtils;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.impl.wsdl.teststeps.HttpTestRequestStep;
import com.eviware.soapui.impl.wsdl.teststeps.Script;
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

    private boolean featureIsDisabledOnNode(XmlCursor xmlCursor) {
        String externalFilenameBuildModeValue = xmlCursor.getAttributeText(EXTERNAL_FILENAME_BUILD_MODE_QNAME);
        if (externalFilenameBuildModeValue != null && externalFilenameBuildModeValue.equals(ExternalFilenameBuildModeConfig.NONE.toString())) {
            return true;
        }
        return false;
    }


    private boolean featureIsGloballyDisabled(XmlCursor xmlCursor, XmlBeansSettingsImpl settings) {
        String externalFilenameBuildModeValue = xmlCursor.getAttributeText(EXTERNAL_FILENAME_BUILD_MODE_QNAME);
        if (externalFilenameBuildModeValue == null && !settings.getBoolean(UISettings.AUTO_CONVERT_CONTENT_TO_USE_EXTERNAL_FILE)) {
            // skip this step, it does not use external filename and we are not in auto-convert mode
            return true;
        }
        return false;
    }

    private void updateConfigWithContentInExternalFileSupport(WsdlProject wsdlProject, XmlBeansSettingsImpl settings, XmlObject xmlObject, XmlCursor contentInExternalFileConfigurationCursor, XmlCursor contentCursor, XmlCursor contentContainerCursor,
                                                              String externalizableContentName, String externalizableContentType, boolean needsConversion) {
        StringBuilder stringBuilder = computePathInProject(contentContainerCursor, externalizableContentName);
        if (needsConversion) {
            SoapUI.log.debug("AUTO-CONVERT to content in external file : content " + externalizableContentName + " will be stored in an external file.");
        }

        ContentInExternalFileSupport contentInExternalFileSupport;

        WsdlRequestConfig wsdlRequestConfig;
        WsdlInterfaceConfig wsdlInterfaceConfig;
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
        } else if (externalizableContentType.equals(REQUEST_RESPONSE_TYPE)) {
            wsdlRequestConfig = (WsdlRequestConfig) contentInExternalFileConfigurationCursor.getObject().changeType(WsdlRequestConfig.type);

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


    /**
     * When feature is enabled, ensure the attributes externalFilenameBuildMode and externalFilename are present.
     *
     * @param wsdlProject
     * @param settings
     */
    void convertToContentInExternalFileIfNeeded(WsdlProject wsdlProject, XmlBeansSettingsImpl settings) {

        String namespace = CONFIG_NAMESPACE + " " + WSDL_NAMESPACE + " ";

        SoapuiProjectDocumentConfig projectDocumentConfig = wsdlProject.getProjectDocument();

        // Find the nodes having content and decorate the appropriate node with our attributes.
        // Nodes having content are separated by categories (cf. ContentInExternalFile.ALL_PATHS_IN_CONFIG_BY_CATEGORY)
        // and expressed as XPath expressions to the node in project document.

        // the cursor to the node to decorate is 'contentInExternalFileConfigurationCursor' : nodef (NODE with Filename attribute)
        // the cursor to the node with content to externalize is 'contentCursor' : c (node with Content)
        // the cursor to the node used to get the name of entity holding the content is 'contentContainerCursor' : ename (node with Entity NAME)
        // the cursor to the node used to get the type of entity holding the content is 'contentContainerTypeCursor' : etype (node with Entity TYPE)
        //
        // We use a cursor for each of the nodes because they differ for each category.
        // Each of the xpath expression leads to a 'contentInExternalFileConfiguration' node, the other nodes are
        // computed relative to that one.
        XmlCursor contentInExternalFileConfigurationCursor = null;
        XmlCursor contentCursor = null;
        XmlCursor contentContainerCursor = null;
        XmlCursor contentContainerTypeCursor = null;
        String externalizableContentName = null;
        String externalizableContentType = null;
        boolean needsConversion = false;

        List<XmlObject> xmlObjects = new ArrayList<XmlObject>();
        for (String path : GROOVY_SCRIPT_PATHS) {
            xmlObjects.addAll(Arrays.asList(projectDocumentConfig.selectPath(namespace + "$this/" + path)));
        }
        for (XmlObject xmlObject : xmlObjects) {
            // nodef = xmlObject
            // c = xmlObject.text()
            // ename = xmlObject.@name + "-" + xmlObject.nodename()
            // etype = GROOVY_SCRIPT
            if (((ScriptConfig)xmlObject).getStringValue().trim().isEmpty() && ! ((ScriptConfig) xmlObject).isSetExternalFilename()) {
                continue;
            }

            contentInExternalFileConfigurationCursor = xmlObject.newCursor();
            contentCursor = xmlObject.newCursor();
            contentContainerCursor = xmlObject.selectPath(namespace + "$this/..")[0].newCursor();
            externalizableContentName = contentContainerCursor.getAttributeText(NAME_QNAME) + "-" + contentInExternalFileConfigurationCursor.getName().getLocalPart();
            externalizableContentType = GROOVY_TYPE;


            // do the processing
            if (featureIsDisabledOnNode(contentInExternalFileConfigurationCursor)) {
                SoapUI.log.debug("Content '" + externalizableContentName + "' of type " + externalizableContentType + " : ");
                SoapUI.log.debug("   does not want to use content in external file (mode is NONE) : skipping it.");
                continue;
            } else if (featureIsGloballyDisabled(contentInExternalFileConfigurationCursor, settings)) {
                continue;
            } else {
                needsConversion = true;
            }
            if (externalizableContentType != null) {
                updateConfigWithContentInExternalFileSupport(wsdlProject, settings, xmlObject, contentInExternalFileConfigurationCursor, contentCursor, contentContainerCursor, externalizableContentName, externalizableContentType, needsConversion);
            }
            contentCursor.dispose();
            contentInExternalFileConfigurationCursor.dispose();
            contentContainerCursor.dispose();
            if (contentContainerTypeCursor != null) {
                contentContainerTypeCursor.dispose();
            }

        }

        xmlObjects.clear();
        for (String path : GROOVY_SCRIPT_TESTSTEP_PATHS) {
            xmlObjects.addAll(Arrays.asList(projectDocumentConfig.selectPath(namespace + "$this/" + path)));
        }
        for (XmlObject xmlObject : xmlObjects) {
            // nodef = xmlObject
            // c = xmlObject.text()
            // ename = (xmlObject/../..).@name
            // etype = (xmlObject/../..).@type
            if (((ScriptConfig)xmlObject).getStringValue().trim().isEmpty() && ! ((ScriptConfig) xmlObject).isSetExternalFilename()) {
                continue;
            }
            contentInExternalFileConfigurationCursor = xmlObject.newCursor();
            contentCursor = xmlObject.newCursor();
            contentContainerCursor = xmlObject.selectPath(namespace + "$this/../..")[0].newCursor();
            contentContainerTypeCursor = contentContainerCursor;

            externalizableContentName = contentContainerCursor.getAttributeText(NAME_QNAME);
            externalizableContentType = contentContainerTypeCursor.getAttributeText(TYPE_QNAME);
            // do the processing
            if (featureIsDisabledOnNode(contentInExternalFileConfigurationCursor)) {
                SoapUI.log.debug("Content '" + externalizableContentName + "' of type " + externalizableContentType + " : ");
                SoapUI.log.debug("   does not want to use content in external file (mode is NONE) : skipping it.");
                continue;
            } else if (featureIsGloballyDisabled(contentInExternalFileConfigurationCursor, settings)) {
                continue;
            } else {
                needsConversion = true;
            }
            if (externalizableContentType != null) {
                updateConfigWithContentInExternalFileSupport(wsdlProject, settings, xmlObject, contentInExternalFileConfigurationCursor, contentCursor, contentContainerCursor, externalizableContentName, externalizableContentType, needsConversion);
            }
            contentCursor.dispose();
            contentInExternalFileConfigurationCursor.dispose();
            contentContainerCursor.dispose();
            if (contentContainerTypeCursor != null) {
                contentContainerTypeCursor.dispose();
            }

        }

        xmlObjects.clear();
        for (String path : REQUEST_TESTSTEP_PATHS) {
            xmlObjects.addAll(Arrays.asList(projectDocumentConfig.selectPath(namespace + "$this/" + path)));
        }

        String namespaceUri = null;
        QName innerRequestQName = null;

        for (XmlObject xmlObject : xmlObjects) {
            // nodef = xmlObject
            // c = (xmlObject/request).text()
            // ename = (xmlObject/../..).@name
            // etype = (xmlObject/../..).@type
            if (((ScriptConfig)xmlObject).getStringValue().trim().isEmpty() && ! ((ScriptConfig) xmlObject).isSetExternalFilename()) {
                continue;
            }
            contentInExternalFileConfigurationCursor = xmlObject.newCursor();
            contentCursor = xmlObject.newCursor();
            if (namespaceUri == null) {
                namespaceUri = contentCursor.namespaceForPrefix("con");
                innerRequestQName = new QName(namespaceUri, REQUEST_TYPE);
            }
            if (!contentCursor.toChild(innerRequestQName)) {
                SoapUI.log.debug("could not advance to child '" + innerRequestQName + "' for xmlObject '" + xmlObject.toString() + "' !");
                continue;
            }

            contentContainerCursor = xmlObject.selectPath(namespace + "$this/../..")[0].newCursor();
            contentContainerTypeCursor = contentContainerCursor;
            externalizableContentName = contentContainerCursor.getAttributeText(NAME_QNAME);
            externalizableContentType = contentContainerTypeCursor.getAttributeText(TYPE_QNAME);
            // do the processing
            if (featureIsDisabledOnNode(contentInExternalFileConfigurationCursor)) {
                SoapUI.log.debug("Content '" + externalizableContentName + "' of type " + externalizableContentType + " : ");
                SoapUI.log.debug("   does not want to use content in external file (mode is NONE) : skipping it.");
                continue;
            } else if (featureIsGloballyDisabled(contentInExternalFileConfigurationCursor, settings)) {
                continue;
            } else {
                needsConversion = true;
            }
            if (externalizableContentType != null) {
                updateConfigWithContentInExternalFileSupport(wsdlProject, settings, xmlObject, contentInExternalFileConfigurationCursor, contentCursor, contentContainerCursor, externalizableContentName, externalizableContentType, needsConversion);
            }
            contentCursor.dispose();
            contentInExternalFileConfigurationCursor.dispose();
            contentContainerCursor.dispose();
            if (contentContainerTypeCursor != null) {
                contentContainerTypeCursor.dispose();
            }

        }

        xmlObjects.clear();
        for (String path : REQUEST_IFC_PATHS) {
            xmlObjects.addAll(Arrays.asList(projectDocumentConfig.selectPath(namespace + "$this/" + path)));
        }
        for (XmlObject xmlObject : xmlObjects) {
            // nodef = xmlObject/..
            // c = xmlObject.text()
            // ename = (xmlObject/..).@name
            // etype = (xmlObject/../..).@type)
            if (((ScriptConfig)xmlObject).getStringValue().trim().isEmpty() && ! ((ScriptConfig) xmlObject).isSetExternalFilename()) {
                continue;
            }
            contentInExternalFileConfigurationCursor = xmlObject.selectPath(namespace + "$this/..")[0].newCursor();
            contentCursor = xmlObject.newCursor();
            contentContainerCursor = xmlObject.selectPath(namespace + "$this/..")[0].newCursor();
            contentContainerTypeCursor = xmlObject.selectPath(namespace + "$this/../..")[0].newCursor();

            externalizableContentName = contentContainerCursor.getAttributeText(NAME_QNAME);
            externalizableContentType = contentContainerTypeCursor.getAttributeText(TYPE_QNAME);
            // do the processing
            if (featureIsDisabledOnNode(contentInExternalFileConfigurationCursor)) {
                SoapUI.log.debug("Content '" + externalizableContentName + "' of type " + externalizableContentType + " : ");
                SoapUI.log.debug("   does not want to use content in external file (mode is NONE) : skipping it.");
                continue;
            } else if (featureIsGloballyDisabled(contentInExternalFileConfigurationCursor, settings)) {
                continue;
            } else {
                needsConversion = true;
            }
            if (externalizableContentType != null) {
                updateConfigWithContentInExternalFileSupport(wsdlProject, settings, xmlObject, contentInExternalFileConfigurationCursor, contentCursor, contentContainerCursor, externalizableContentName, externalizableContentType, needsConversion);
            }
            contentCursor.dispose();
            contentInExternalFileConfigurationCursor.dispose();
            contentContainerCursor.dispose();
            if (contentContainerTypeCursor != null) {
                contentContainerTypeCursor.dispose();
            }

        }

        xmlObjects.clear();
        for (String path : EXTERNAL_DATA_PATHS) {
            xmlObjects.addAll(Arrays.asList(projectDocumentConfig.selectPath(namespace + "$this/" + path)));
        }
        for (XmlObject xmlObject : xmlObjects) {
            // nodef = xmlObject
            // c = xmlObject.text()
            // ename = (xmlObject/..).nodename()
            // etype = DATA
            if (((ScriptConfig)xmlObject).getStringValue().trim().isEmpty() && ! ((ScriptConfig) xmlObject).isSetExternalFilename()) {
                continue;
            }
            contentInExternalFileConfigurationCursor = xmlObject.newCursor();
            contentCursor = xmlObject.newCursor();
            contentContainerCursor = xmlObject.selectPath(namespace + "$this/..")[0].newCursor();

            externalizableContentName = contentContainerCursor.getName().getLocalPart();
            externalizableContentType = EXTERNAL_DATA_TYPE;
            // do the processing
            if (featureIsDisabledOnNode(contentInExternalFileConfigurationCursor)) {
                SoapUI.log.debug("Content '" + externalizableContentName + "' of type " + externalizableContentType + " : ");
                SoapUI.log.debug("   does not want to use content in external file (mode is NONE) : skipping it.");
                continue;
            } else if (featureIsGloballyDisabled(contentInExternalFileConfigurationCursor, settings)) {
                continue;
            } else {
                needsConversion = true;
            }
            if (externalizableContentType != null) {
                updateConfigWithContentInExternalFileSupport(wsdlProject, settings, xmlObject, contentInExternalFileConfigurationCursor, contentCursor, contentContainerCursor, externalizableContentName, externalizableContentType, needsConversion);
            }
            contentCursor.dispose();
            contentInExternalFileConfigurationCursor.dispose();
            contentContainerCursor.dispose();
            if (contentContainerTypeCursor != null) {
                contentContainerTypeCursor.dispose();
            }

        }

        xmlObjects.clear();
        for (String path : RESPONSECONTENT_MOCK_PATHS) {
            xmlObjects.addAll(Arrays.asList(projectDocumentConfig.selectPath(namespace + "$this/" + path)));
        }
        for (XmlObject xmlObject : xmlObjects) {
            // nodef = xmlObject
            // c = xmlObject.text()
            // ename = (xmlObject/..).nodename()
            // etype = RESPONSE_CONTENT
            if (((ScriptConfig)xmlObject).getStringValue().trim().isEmpty() && ! ((ScriptConfig) xmlObject).isSetExternalFilename()) {
                continue;
            }
            contentInExternalFileConfigurationCursor = xmlObject.newCursor();
            contentCursor = xmlObject.newCursor();
            contentContainerCursor = xmlObject.selectPath(namespace + "$this/..")[0].newCursor();

            externalizableContentName = contentContainerCursor.getName().getLocalPart();
            externalizableContentType = RESPONSE_CONTENT_TYPE;
            // do the processing
            if (featureIsDisabledOnNode(contentInExternalFileConfigurationCursor)) {
                SoapUI.log.debug("Content '" + externalizableContentName + "' of type " + externalizableContentType + " : ");
                SoapUI.log.debug("   does not want to use content in external file (mode is NONE) : skipping it.");
                continue;
            } else if (featureIsGloballyDisabled(contentInExternalFileConfigurationCursor, settings)) {
                continue;
            } else {
                needsConversion = true;
            }
            if (externalizableContentType != null) {
                updateConfigWithContentInExternalFileSupport(wsdlProject, settings, xmlObject, contentInExternalFileConfigurationCursor, contentCursor, contentContainerCursor, externalizableContentName, externalizableContentType, needsConversion);
            }
            contentCursor.dispose();
            contentInExternalFileConfigurationCursor.dispose();
            contentContainerCursor.dispose();
            if (contentContainerTypeCursor != null) {
                contentContainerTypeCursor.dispose();
            }

        }

        xmlObjects.clear();
        for (String path : RESPONSE_OR_REQUEST_PATHS) {
            xmlObjects.addAll(Arrays.asList(projectDocumentConfig.selectPath(namespace + "$this/" + path)));
        }
        for (XmlObject xmlObject : xmlObjects) {
            // nodef = xmlObject
            // c = xmlObject.text()
            // ename = (xmlObject/../../..).@name
            // etype = (xmlObject/../../..).@type
            if (((ScriptConfig)xmlObject).getStringValue().trim().isEmpty() && ! ((ScriptConfig) xmlObject).isSetExternalFilename()) {
                continue;
            }
            contentInExternalFileConfigurationCursor = xmlObject.newCursor();
            contentCursor = xmlObject.newCursor();
            contentContainerCursor = xmlObject.selectPath(namespace + "$this/../../..")[0].newCursor();
            contentContainerTypeCursor = contentContainerCursor;

            externalizableContentName = contentContainerCursor.getAttributeText(NAME_QNAME);
            externalizableContentType = contentContainerTypeCursor.getAttributeText(TYPE_QNAME);
            // do the processing
            if (featureIsDisabledOnNode(contentInExternalFileConfigurationCursor)) {
                SoapUI.log.debug("Content '" + externalizableContentName + "' of type " + externalizableContentType + " : ");
                SoapUI.log.debug("   does not want to use content in external file (mode is NONE) : skipping it.");
                continue;
            } else if (featureIsGloballyDisabled(contentInExternalFileConfigurationCursor, settings)) {
                continue;
            } else {
                needsConversion = true;
            }
            if (externalizableContentType != null) {
                updateConfigWithContentInExternalFileSupport(wsdlProject, settings, xmlObject, contentInExternalFileConfigurationCursor, contentCursor, contentContainerCursor, externalizableContentName, externalizableContentType, needsConversion);
            }
            contentCursor.dispose();
            contentInExternalFileConfigurationCursor.dispose();
            contentContainerCursor.dispose();
            if (contentContainerTypeCursor != null) {
                contentContainerTypeCursor.dispose();
            }

        }

        xmlObjects.clear();
        for (String path : EVENT_HANDLER_PATHS) {
            xmlObjects.addAll(Arrays.asList(projectDocumentConfig.selectPath(namespace + "$this/" + path)));
        }
        for (XmlObject xmlObject : xmlObjects) {
            // nodef = xmlObject
            // c = xmlObject.text()
            // ename = (xmlObject/..).nodename()
            // etype = (xmlObject/..).@type
            if (((ScriptConfig)xmlObject).getStringValue().trim().isEmpty() && ! ((ScriptConfig) xmlObject).isSetExternalFilename()) {
                continue;
            }
            contentInExternalFileConfigurationCursor = xmlObject.newCursor();
            contentCursor = xmlObject.newCursor();
            contentContainerCursor = xmlObject.selectPath(namespace + "$this/..")[0].newCursor();
            contentContainerTypeCursor = contentContainerCursor;

            externalizableContentName = contentContainerCursor.getName().getLocalPart();
            externalizableContentType = contentContainerTypeCursor.getAttributeText(TYPE_QNAME);
            // do the processing
            if (featureIsDisabledOnNode(contentInExternalFileConfigurationCursor)) {
                SoapUI.log.debug("Content '" + externalizableContentName + "' of type " + externalizableContentType + " : ");
                SoapUI.log.debug("   does not want to use content in external file (mode is NONE) : skipping it.");
                continue;
            } else if (featureIsGloballyDisabled(contentInExternalFileConfigurationCursor, settings)) {
                continue;
            } else {
                needsConversion = true;
            }

            if (externalizableContentType != null) {
                updateConfigWithContentInExternalFileSupport(wsdlProject, settings, xmlObject, contentInExternalFileConfigurationCursor, contentCursor, contentContainerCursor, externalizableContentName, externalizableContentType, needsConversion);
            }
            contentCursor.dispose();
            contentInExternalFileConfigurationCursor.dispose();
            contentContainerCursor.dispose();
            if (contentContainerTypeCursor != null) {
                contentContainerTypeCursor.dispose();
            }
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
