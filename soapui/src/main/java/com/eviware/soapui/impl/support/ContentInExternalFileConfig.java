package com.eviware.soapui.impl.support;

import com.eviware.soapui.config.*;
import com.eviware.soapui.impl.wsdl.support.PathUtils;
import com.eviware.soapui.impl.wsdl.teststeps.Script;
import com.eviware.soapui.support.xml.XmlObjectConfigurationBuilder;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;

import javax.xml.namespace.QName;

import static com.eviware.soapui.impl.support.ContentInExternalFile.*;
import static com.eviware.soapui.impl.support.ContentInExternalFileCategory.*;

/**
 * Façade on the actual config object, according to the actual category.
 *
 * @author Marc Paquette
 */
public class ContentInExternalFileConfig {
    public ContentInExternalFileCategory getContentInExternalFileCategory() {
        return contentInExternalFileCategory;
    }

    public void setContentInExternalFileCategory(ContentInExternalFileCategory contentInExternalFileCategory) {
        this.contentInExternalFileCategory = contentInExternalFileCategory;
    }

    private ContentInExternalFileCategory contentInExternalFileCategory;

    // for ContentInExternalFileCategory.WSDL_STEP
    private WsdlRequestConfig wsdlRequestConfig;

    // for ContentInExternalFileCategory.GROOVY_STEP
    private TestStepConfig testStepConfig;

    // for ContentInExternalFileCaterogy.GROOVY_ASSERTION
    private TestAssertionConfig testAssertionConfig;

    // for ContentInExternalFileCategory.SCRIPT and also composition in GROOVY_STEP
    private ScriptConfig scriptConfig;

    public XmlObject getConfig() {
        if (WSDL_STEP.equals(contentInExternalFileCategory)) {
            return wsdlRequestConfig;
        }
        if (GROOVY_STEP.equals(contentInExternalFileCategory)) {
            return testStepConfig;
        }
        if (GROOVY_ASSERTION.equals(contentInExternalFileCategory)) {
            return testAssertionConfig;
        }
        if (SCRIPT.equals(contentInExternalFileCategory)) {
            return scriptConfig;
        }
        return null;
    }

    public XmlObject getWrappedConfig() {
        if (WSDL_STEP.equals(contentInExternalFileCategory)) {
            return wsdlRequestConfig;
        }
        if (SCRIPT.equals(contentInExternalFileCategory)) {
            return scriptConfig;
        }
        if (GROOVY_STEP.equals(contentInExternalFileCategory)) {
            return testStepConfig.getConfig();
        }
        if (GROOVY_ASSERTION.equals(contentInExternalFileCategory)) {
            return testAssertionConfig.getConfiguration();
        }
        return null;
    }

    public String getScriptPrefixForConfig() {
        if (WSDL_STEP.equals(contentInExternalFileCategory)) {
            return null;
        }
        if (SCRIPT.equals(contentInExternalFileCategory)) {
            return "/" + Script.SCRIPT_PROPERTY;
        }
        if (GROOVY_STEP.equals(contentInExternalFileCategory)) {
            return "/" + Script.SCRIPT_PROPERTY;
        }
        if (GROOVY_ASSERTION.equals(contentInExternalFileCategory)) {
            return "/" + Script.SCRIPT_ALT_PROPERTY;
        }
        return null;
    }

    public QName getScriptQNameForConfig() {
        if (WSDL_STEP.equals(contentInExternalFileCategory)) {
            return null;
        }
        if (SCRIPT.equals(contentInExternalFileCategory)) {
            return SCRIPT_QNAME;
        }
        if (GROOVY_STEP.equals(contentInExternalFileCategory)) {
            return SCRIPT_QNAME;
        }
        if (GROOVY_ASSERTION.equals(contentInExternalFileCategory)) {
            return SCRIPT_ALT_QNAME;
        }
        return null;
    }

    public String getExternalFilenameSuffix() {
        if (WSDL_STEP.equals(contentInExternalFileCategory)) {
            return WSDL_REQUEST_SUFFIX;
        }
        if (SCRIPT.equals(contentInExternalFileCategory) || GROOVY_STEP.equals(contentInExternalFileCategory) || GROOVY_ASSERTION.equals(contentInExternalFileCategory)) {
            return GROOVY_SCRIPT_SUFFIX;
        }
        return DEFAULT_SUFFIX;
    }

    public ContentInExternalFileConfig(WsdlRequestConfig wsdlRequestConfig) {
        this.contentInExternalFileCategory = ContentInExternalFileCategory.WSDL_STEP;
        this.wsdlRequestConfig = wsdlRequestConfig;
    }

    public ContentInExternalFileConfig(TestAssertionConfig testAssertionConfig) {
        this.contentInExternalFileCategory = ContentInExternalFileCategory.GROOVY_ASSERTION;
        this.testAssertionConfig = testAssertionConfig;
    }

    public ContentInExternalFileConfig(TestStepConfig testStepConfig) {
        this.contentInExternalFileCategory = ContentInExternalFileCategory.GROOVY_STEP;
        this.testStepConfig = testStepConfig;
    }

    public ContentInExternalFileConfig(ScriptConfig scriptConfig) {
        this.contentInExternalFileCategory = ContentInExternalFileCategory.SCRIPT;
        this.scriptConfig = scriptConfig;
    }

    // externalFilename

    /**
     * True if has "externalFilename" attribute
     */
    public boolean isSetExternalFilename() {
        if (WSDL_STEP.equals(this.contentInExternalFileCategory)) {
            return wsdlRequestConfig == null ? false : wsdlRequestConfig.isSetExternalFilename();
        }
        if (GROOVY_STEP.equals(this.contentInExternalFileCategory)) {
            return (testStepConfig == null || scriptConfig == null) ? false : (scriptConfig == null ? false : scriptConfig.isSetExternalFilename());
        }
        if (GROOVY_ASSERTION.equals(this.contentInExternalFileCategory)) {
            return (testAssertionConfig == null || scriptConfig == null) ? false : (scriptConfig == null ? false : scriptConfig.isSetExternalFilename());
        }
        if (SCRIPT.equals(this.contentInExternalFileCategory)) {
            return scriptConfig == null ? false : scriptConfig.isSetExternalFilename();
        }
        return false;
    }

    public String getExternalFilename() {
        if (WSDL_STEP.equals(this.contentInExternalFileCategory) && wsdlRequestConfig != null) {
            return wsdlRequestConfig.getExternalFilename();
        }
        if (GROOVY_STEP.equals(this.contentInExternalFileCategory) && testStepConfig != null && scriptConfig != null) {
            return scriptConfig.getExternalFilename();
        }
        if (GROOVY_ASSERTION.equals(this.contentInExternalFileCategory) && testAssertionConfig != null && scriptConfig != null) {
            return scriptConfig.getExternalFilename();
        }
        if (SCRIPT.equals(this.contentInExternalFileCategory) && scriptConfig != null) {
            return scriptConfig.getExternalFilename();
        }
        return null;
    }

    public void setExternalFilename(String externalFilename) {
        if (WSDL_STEP.equals(this.contentInExternalFileCategory) && wsdlRequestConfig != null) {
            wsdlRequestConfig.setExternalFilename(externalFilename);
        }
        if (GROOVY_STEP.equals(this.contentInExternalFileCategory) && testStepConfig != null && scriptConfig != null) {
            scriptConfig.setExternalFilename(externalFilename);
        }
        if (GROOVY_ASSERTION.equals(this.contentInExternalFileCategory) && testAssertionConfig != null && scriptConfig != null) {
            scriptConfig.setExternalFilename(externalFilename);
        }
        if (SCRIPT.equals(this.contentInExternalFileCategory) && scriptConfig != null) {
            scriptConfig.setExternalFilename(externalFilename);
        }
    }

    // externalFilenameBuildMode

    /**
     * True if has "externalFilenameBuildMode" attribute
     */
    public boolean isSetExternalFilenameBuildMode() {
        if (WSDL_STEP.equals(this.contentInExternalFileCategory)) {
            return wsdlRequestConfig == null ? false : wsdlRequestConfig.isSetExternalFilenameBuildMode();
        }
        if (GROOVY_STEP.equals(this.contentInExternalFileCategory)) {
            return (testStepConfig == null || scriptConfig == null) ? false : (scriptConfig == null ? false : scriptConfig.isSetExternalFilenameBuildMode());
        }
        if (GROOVY_ASSERTION.equals(this.contentInExternalFileCategory)) {
            return (testAssertionConfig == null || scriptConfig == null) ? false : (scriptConfig == null ? false : scriptConfig.isSetExternalFilenameBuildMode());
        }
        if (SCRIPT.equals(this.contentInExternalFileCategory)) {
            return scriptConfig == null ? false : scriptConfig.isSetExternalFilenameBuildMode();
        }
        return false;
    }

    public ExternalFilenameBuildModeConfig.Enum getExternalFilenameBuildMode() {
        if (WSDL_STEP.equals(this.contentInExternalFileCategory) && wsdlRequestConfig != null) {
            return wsdlRequestConfig.getExternalFilenameBuildMode();
        }
        if (GROOVY_STEP.equals(this.contentInExternalFileCategory) && testStepConfig != null && scriptConfig != null) {
            return scriptConfig.getExternalFilenameBuildMode();
        }
        if (GROOVY_ASSERTION.equals(this.contentInExternalFileCategory) && testAssertionConfig != null && scriptConfig != null) {
            return scriptConfig.getExternalFilenameBuildMode();
        }
        if (SCRIPT.equals(this.contentInExternalFileCategory) && scriptConfig != null) {
            return scriptConfig.getExternalFilenameBuildMode();
        }
        return ExternalFilenameBuildModeConfig.NONE;
    }

    public void setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.Enum externalFilenameBuildMode) {
        if (WSDL_STEP.equals(this.contentInExternalFileCategory) && wsdlRequestConfig != null) {
            wsdlRequestConfig.setExternalFilenameBuildMode(externalFilenameBuildMode);
        }
        if (GROOVY_STEP.equals(this.contentInExternalFileCategory) && testStepConfig != null && scriptConfig != null) {
            scriptConfig.setExternalFilenameBuildMode(externalFilenameBuildMode);
        }
        if (GROOVY_ASSERTION.equals(this.contentInExternalFileCategory) && testAssertionConfig != null && scriptConfig != null) {
            scriptConfig.setExternalFilenameBuildMode(externalFilenameBuildMode);
        }
        if (SCRIPT.equals(this.contentInExternalFileCategory) && scriptConfig != null) {
            scriptConfig.setExternalFilenameBuildMode(externalFilenameBuildMode);
        }
    }

    public void setContent(String content) {
        if (WSDL_STEP.equals(contentInExternalFileCategory)) {
            wsdlRequestConfig.getRequest().setStringValue(content);
        }
        if (SCRIPT.equals(contentInExternalFileCategory)) {
            scriptConfig.setStringValue(content);
        }
        if (GROOVY_STEP.equals(contentInExternalFileCategory)) {
            scriptConfig.setStringValue(content);
        }
        if (GROOVY_ASSERTION.equals(contentInExternalFileCategory)) {
            scriptConfig.setStringValue(content);
        }
    }

    public void updateConfig() {
        if (WSDL_STEP.equals(contentInExternalFileCategory)) {
            return;
        }
        if (SCRIPT.equals(contentInExternalFileCategory)) {
            return;
        }
        if (GROOVY_STEP.equals(contentInExternalFileCategory)) {
            if (testStepConfig.getConfig() == null) {
                XmlObjectConfigurationBuilder builder = new XmlObjectConfigurationBuilder();
                builder.add(Script.SCRIPT_PROPERTY, scriptConfig.getStringValue());
                testStepConfig.setConfig(builder.finish());
            } else {
                updateTestStepConfig(testStepConfig.getConfig(), scriptConfig);
            }
        }
        if (GROOVY_ASSERTION.equals(contentInExternalFileCategory)) {
            if (testAssertionConfig.getConfiguration() == null) {
                XmlObjectConfigurationBuilder builder = new XmlObjectConfigurationBuilder();
                builder.add(Script.SCRIPT_ALT_PROPERTY, scriptConfig.getStringValue());
                testAssertionConfig.setConfiguration(builder.finish());
            } else {
                updateTestStepConfig(testAssertionConfig.getConfiguration(), scriptConfig);
            }
        }
    }

    private void updateTestStepConfig(XmlObject configToUpdate, ScriptConfig scriptConfig) {
        if (configToUpdate == null) {
            return;
        }
        XmlCursor cursor = configToUpdate.newCursor();
        if (cursor.toChild(Script.SCRIPT_PROPERTY) || cursor.toChild(Script.SCRIPT_ALT_PROPERTY)) {
            cursor.setAttributeText(EXTERNAL_FILENAME_BUILD_MODE_QNAME, getExternalFilenameBuildMode().toString());
            cursor.setAttributeText(EXTERNAL_FILENAME_QNAME, PathUtils.normalizePath(getExternalFilename()));
            cursor.toFirstContentToken();
            cursor.removeXml();
            if (scriptConfig == null) {
                cursor.insertChars("");
            } else {
                cursor.insertChars(scriptConfig.getStringValue());
            }
            cursor.dispose();
        }
    }


    public String getExtensionForFileType() {
        if (WSDL_STEP.equals(contentInExternalFileCategory)) {
            return WSDL_REQUEST_EXTENSION;
        }
        if (SCRIPT.equals(contentInExternalFileCategory)) {
            return GROOVY_SCRIPT_SUFFIX;
        }
        if (GROOVY_STEP.equals(contentInExternalFileCategory)) {
            return GROOVY_SCRIPT_SUFFIX;
        }
        if (GROOVY_ASSERTION.equals(contentInExternalFileCategory)) {
            return GROOVY_SCRIPT_SUFFIX;
        }
        return Character.toString('*');
    }

    public String getFileTypeDescription() {
        if (WSDL_STEP.equals(contentInExternalFileCategory)) {
            return "XML Files (*." + WSDL_REQUEST_EXTENSION + ")";
        }
        if (SCRIPT.equals(contentInExternalFileCategory)) {
            return "Groovy Files (*." + GROOVY_SCRIPT_SUFFIX + ")";
        }
        if (GROOVY_STEP.equals(contentInExternalFileCategory)) {
            return "Groovy Files (*." + GROOVY_SCRIPT_SUFFIX + ")";
        }
        if (GROOVY_ASSERTION.equals(contentInExternalFileCategory)) {
            return "Groovy Files (*." + GROOVY_SCRIPT_SUFFIX + ")";
        }
        return "Any File";
    }

    public ScriptConfig getScriptConfig() {
        if (WSDL_STEP.equals(contentInExternalFileCategory)) {
            return null;
        }
        if (SCRIPT.equals(contentInExternalFileCategory)) {
            return scriptConfig;
        }
        if (GROOVY_STEP.equals(contentInExternalFileCategory)) {
            return scriptConfig;
        }
        if (GROOVY_ASSERTION.equals(contentInExternalFileCategory)) {
            return scriptConfig;
        }
        return null;
    }

    public void setScriptConfig(ScriptConfig scriptConfig) {
        if (WSDL_STEP.equals(contentInExternalFileCategory)) {
            return;
        }
        if (SCRIPT.equals(contentInExternalFileCategory)) {
            this.scriptConfig = scriptConfig;
        }
        if (GROOVY_STEP.equals(contentInExternalFileCategory)) {
            this.scriptConfig = scriptConfig;
        }
        if (GROOVY_ASSERTION.equals(contentInExternalFileCategory)) {
            this.scriptConfig = scriptConfig;
        }
    }

}
