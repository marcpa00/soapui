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

package com.eviware.soapui.impl.wsdl.actions.request;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.*;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.WsdlTestSuite;
import com.eviware.soapui.impl.wsdl.support.HelpUrls;
import com.eviware.soapui.impl.wsdl.support.PathUtils;
import com.eviware.soapui.impl.support.ContentInExternalFileSupport;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestRequest;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestStep;
import com.eviware.soapui.model.ModelItem;
import com.eviware.soapui.model.project.Project;
import com.eviware.soapui.settings.UISettings;
import com.eviware.soapui.support.action.support.AbstractSoapUIAction;
import com.eviware.x.form.XFormDialog;
import com.eviware.x.form.XFormField;
import com.eviware.x.form.XFormFieldListener;
import com.eviware.x.form.support.ADialogBuilder;
import com.eviware.x.form.support.AField;
import com.eviware.x.form.support.AField.AFieldType;
import com.eviware.x.form.support.AForm;
import com.eviware.x.impl.swing.AbstractSwingXFormField;
import com.eviware.x.impl.swing.FileFormField;
import com.eviware.x.impl.swing.JTextFieldFormField;
import org.apache.xmlbeans.XmlObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import static com.eviware.soapui.impl.support.ContentInExternalFile.DEFAULT_STEP_FILENAME;

public class ConfigureExternalFileAction extends AbstractSoapUIAction<ModelItem> {
    public static final String SOAPUI_ACTION_ID = "ConfigureExternalFileAction";
    private XFormDialog dialog;
    private ContentInExternalFileSupport contentInExternalFileSupport = null;

    public ConfigureExternalFileAction() {
        super("Configure external file settings for content", "Specify which file to load, if content should be saved to external file and how to name the file.");
    }

    public void perform(ModelItem modelItem, Object param) {
        if (modelItem == null) {
            SoapUI.log.debug("ConfigureExternalFileAction.perform() called on a null modelItem");
            return;
        }

        if (modelItem instanceof ContentInExternalFileSupport) {
            contentInExternalFileSupport = (ContentInExternalFileSupport) modelItem;
        } else if (modelItem instanceof WsdlTestRequest) {
            contentInExternalFileSupport = ((WsdlTestRequest) modelItem).getTestStep().getContentInExternalFileSupport();
        }
        if (contentInExternalFileSupport != null) {
            configureExternalFile();
        }
    }

    protected boolean configureExternalFile() {
        if (dialog == null) {
            dialog = ADialogBuilder.buildDialog(Form.class);
        }

        // Initialize flags to default values
        Boolean useExternalStepFile = contentInExternalFileSupport.getSettings().getBoolean(UISettings.CONTENT_IN_EXTERNAL_FILE);
        Boolean useAutomaticFilename = true;
        Boolean useManualFilename = false;

        // ... then override them from config
        XmlObject config = contentInExternalFileSupport.getConfig();

        ExternalStepFileSelector externalStepFileSelector = new ExternalStepFileSelector(contentInExternalFileSupport, config).invoke();
        useExternalStepFile = externalStepFileSelector.getUseExternalStepFile();
        useAutomaticFilename = externalStepFileSelector.getUseAutomaticFilename();
        useManualFilename = externalStepFileSelector.getUseManualFilename();

        dialog.setBooleanValue(Form.USE_EXTERNAL_STEP_FILE, useExternalStepFile);

        String automaticFilename = contentInExternalFileSupport.buildExternalFilenameForMode(ExternalFilenameBuildModeConfig.AUTO);
        String manualFilename = contentInExternalFileSupport.buildExternalFilenameForMode(ExternalFilenameBuildModeConfig.MANUAL);

        dialog.setValue(Form.AUTOMATIC_FILENAME, automaticFilename);
        dialog.setValue(Form.MANUAL_FILENAME, manualFilename);
        FileFormField fileFormField = (FileFormField) dialog.getFormField(Form.MANUAL_FILENAME);
        fileFormField.setProperty(FileFormField.CURRENT_DIRECTORY, contentInExternalFileSupport.getExternalFileRootPath());

        dialog.setBooleanValue(Form.USE_AUTOMATIC_FILENAME, useAutomaticFilename);
        dialog.setBooleanValue(Form.USE_MANUAL_FILENAME, useManualFilename);

        setupFieldsStateAndVisibility(useExternalStepFile, useAutomaticFilename, useManualFilename);

        if (useExternalStepFile) {
            if (useAutomaticFilename) {
                dialog.setValue(Form.FILENAME, automaticFilename);
            } else if (useManualFilename) {
                dialog.setValue(Form.FILENAME, manualFilename);
            }
        }

        dialog.setValue(Form.SUMMARY, buildSummary());

        setupListeners();

        String previousFilename = contentInExternalFileSupport.getExternalFilename();
        if (previousFilename == null) {
            previousFilename = "";
        }

        if (!dialog.show()) {
            return false;
        }

        // change test step with values from dialog
        if (!dialog.getBooleanValue(Form.USE_EXTERNAL_STEP_FILE)) {
            contentInExternalFileSupport.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.NONE);
            contentInExternalFileSupport.updateConfig();
        } else {
            if (dialog.getBooleanValue(Form.USE_AUTOMATIC_FILENAME)) {
                contentInExternalFileSupport.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.AUTO);
            } else if (dialog.getBooleanValue(Form.USE_MANUAL_FILENAME)) {
                contentInExternalFileSupport.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.MANUAL);
            }

            String newFilename = dialog.getValue(Form.FILENAME);
            if (previousFilename.equals(newFilename)) {
                contentInExternalFileSupport.maybeReloadStepContent();
            } else {
                contentInExternalFileSupport.setExternalFilename(newFilename);
                contentInExternalFileSupport.loadContent();
                //contentInExternalFileSupport.updateTestStepContent();
                contentInExternalFileSupport.updateScript();
            }
            contentInExternalFileSupport.updateConfig();
            contentInExternalFileSupport.saveToExternalFile(contentInExternalFileSupport.updateConfigWithExternalFilePath(), false);

        }

        return true;
    }

    private String buildSummary() {
        String sep = File.separator;
        String currentFilename = dialog.getValue(Form.FILENAME);
        if (currentFilename == null || currentFilename.isEmpty()) {
            currentFilename = contentInExternalFileSupport.getExternalFilename();
        }
        if (currentFilename != null && !currentFilename.isEmpty()) {
            StringBuilder summary = new StringBuilder("Root path for relative path names : ");
            summary.append("\n   ");
            summary.append(contentInExternalFileSupport.getExternalFileRootPath()).append(sep);
            summary.append("\n");
            summary.append("Effective external filename : ");
            summary.append("\n   ");
            summary.append(currentFilename);
            summary.append("\n");
            File f = new File(currentFilename);
            if (!f.isAbsolute()) {
                f = new File(contentInExternalFileSupport.getExternalFileRootPath() + sep + currentFilename);
            }
            if (f.exists()) {
                summary.append("File exists (").append(f.length()).append(" bytes), last modified on ").append(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date(f.lastModified())));
            } else {
                summary.append("File does not exists yet.");
            }
            return summary.toString();
        } else {
            return "";
        }
    }

    private void setupFieldsStateAndVisibility(Boolean useExternalStepFile, Boolean useAutomaticFilename, Boolean useManualFilename) {
        // by default, fields are enabled : need to disable them according to flag USE_EXTERNAL_STEP_FILE
        if (useExternalStepFile == Boolean.FALSE) {
            // disable fields because USE_EXTERNAL_STEP_FILE is false
            dialog.getFormField(Form.USE_AUTOMATIC_FILENAME).setEnabled(false);
            dialog.getFormField(Form.USE_MANUAL_FILENAME).setEnabled(false);

            dialog.getFormField(Form.AUTOMATIC_FILENAME).setEnabled(false);
            dialog.getFormField(Form.MANUAL_FILENAME).setEnabled(false);

            dialog.getFormField(Form.SUMMARY).setEnabled(false);
        } else {
            if (useAutomaticFilename) {
                dialog.getFormField(Form.MANUAL_FILENAME).setEnabled(false);
            }
            if (useManualFilename) {
                dialog.getFormField(Form.AUTOMATIC_FILENAME).setEnabled(false);
            }
        }
        XFormField formField = dialog.getFormField(Form.AUTOMATIC_FILENAME);
        if (formField instanceof JTextFieldFormField) {
            ((JTextFieldFormField) formField).getComponent().setEditable(false);
        }
        String invisibleFormFields[] = {Form.FILENAME};
        for (String invisibleFormField : Arrays.asList(invisibleFormFields)) {
            ((AbstractSwingXFormField) dialog.getFormField(invisibleFormField)).getComponent().setVisible(false);
        }
    }


    private void setupListeners() {
        dialog.getFormField(Form.USE_EXTERNAL_STEP_FILE).addFormFieldListener(new XFormFieldListener() {
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {

                dialog.getFormField(Form.USE_AUTOMATIC_FILENAME).setEnabled(Boolean.valueOf(newValue));
                dialog.getFormField(Form.USE_MANUAL_FILENAME).setEnabled(Boolean.valueOf(newValue));

                if (Boolean.valueOf(newValue) == false) {
                    dialog.getFormField(Form.MANUAL_FILENAME).setEnabled(false);
                    dialog.getFormField(Form.AUTOMATIC_FILENAME).setEnabled(false);
                } else {
                    dialog.getFormField(Form.AUTOMATIC_FILENAME).setEnabled(dialog.getBooleanValue(Form.USE_AUTOMATIC_FILENAME));
                    dialog.getFormField(Form.MANUAL_FILENAME).setEnabled(dialog.getBooleanValue(Form.USE_MANUAL_FILENAME));
                }
                dialog.setValue(Form.SUMMARY, buildSummary());
            }
        });

        dialog.getFormField(Form.USE_AUTOMATIC_FILENAME).addFormFieldListener(new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                if (Boolean.valueOf(newValue) == true && sourceField.equals(dialog.getFormField(Form.USE_AUTOMATIC_FILENAME))) {
                    if (dialog.getBooleanValue(Form.USE_MANUAL_FILENAME)) {
                        dialog.setBooleanValue(Form.USE_MANUAL_FILENAME, false);
                    }
                } else if (Boolean.valueOf(newValue) == false && sourceField.equals(dialog.getFormField(Form.USE_AUTOMATIC_FILENAME))) {
                    if (dialog.getBooleanValue(Form.USE_MANUAL_FILENAME) == false) {
                        dialog.setBooleanValue(Form.USE_AUTOMATIC_FILENAME, true);
                    }
                }

                dialog.getFormField(Form.AUTOMATIC_FILENAME).setEnabled(Boolean.valueOf(newValue));

                Boolean newEnabledValue = !Boolean.valueOf(newValue);
                dialog.getFormField(Form.MANUAL_FILENAME).setEnabled(newEnabledValue);

                Boolean useExternalStepFile = dialog.getBooleanValue(Form.USE_EXTERNAL_STEP_FILE);
                Boolean useAutomaticFilename = dialog.getBooleanValue(Form.USE_AUTOMATIC_FILENAME);
                Boolean useManualFilename = dialog.getBooleanValue(Form.USE_MANUAL_FILENAME);

                if (useExternalStepFile) {
                    if (useAutomaticFilename) {
                        dialog.setValue(Form.FILENAME, dialog.getValue(Form.AUTOMATIC_FILENAME));
                    } else if (useManualFilename) {
                        dialog.setValue(Form.FILENAME, dialog.getValue(Form.MANUAL_FILENAME));
                    }
                }
                dialog.setValue(Form.SUMMARY, buildSummary());
            }
        });

        dialog.getFormField(Form.USE_MANUAL_FILENAME).addFormFieldListener(new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                if (Boolean.valueOf(newValue) == true && sourceField.equals(dialog.getFormField(Form.USE_MANUAL_FILENAME))) {
                    if (dialog.getBooleanValue(Form.USE_AUTOMATIC_FILENAME)) {
                        dialog.setBooleanValue(Form.USE_AUTOMATIC_FILENAME, false);
                    }
                } else if (Boolean.valueOf(newValue) == false && sourceField.equals(dialog.getFormField(Form.USE_MANUAL_FILENAME))) {
                    if (dialog.getBooleanValue(Form.USE_AUTOMATIC_FILENAME) == false) {
                        dialog.setBooleanValue(Form.USE_MANUAL_FILENAME, true);
                    }
                }

                dialog.getFormField(Form.MANUAL_FILENAME).setEnabled(Boolean.valueOf(newValue));

                Boolean newEnabledValue = !Boolean.valueOf(newValue);
                dialog.getFormField(Form.AUTOMATIC_FILENAME).setEnabled(newEnabledValue);
                Boolean useExternalStepFile = dialog.getBooleanValue(Form.USE_EXTERNAL_STEP_FILE);
                Boolean useAutomaticFilename = dialog.getBooleanValue(Form.USE_AUTOMATIC_FILENAME);
                Boolean useManualFilename = dialog.getBooleanValue(Form.USE_MANUAL_FILENAME);

                if (useExternalStepFile) {
                    if (useAutomaticFilename) {
                        dialog.setValue(Form.FILENAME, dialog.getValue(Form.AUTOMATIC_FILENAME));
                    } else if (useManualFilename) {
                        dialog.setValue(Form.FILENAME, dialog.getValue(Form.MANUAL_FILENAME));
                    }
                }
                dialog.setValue(Form.SUMMARY, buildSummary());
            }
        });

        dialog.getFormField(Form.AUTOMATIC_FILENAME).addFormFieldListener(new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                dialog.setValue(Form.FILENAME, newValue);
                dialog.setValue(Form.SUMMARY, buildSummary());
            }
        });
        dialog.getFormField(Form.MANUAL_FILENAME).addFormFieldListener(new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                dialog.setValue(Form.FILENAME, newValue);
                dialog.setValue(Form.SUMMARY, buildSummary());
            }
        });
    }


    @AForm(name = "Configure external file settings for step", description = "Specify which file to load, if content should be saved to external file and how to name the file.", helpUrl = HelpUrls.USE_EXT_FILE_FOR_STEP_HELP_URL)
    private interface Form {
        @AField(name = "Use External Step File", description = "Use an external file to store the request content of this step.", type = AFieldType.BOOLEAN)
        public final static String USE_EXTERNAL_STEP_FILE = "Use External Step File";

        @AField(name = "separator-1", description = "", type = AFieldType.SEPARATOR)
        public final static String SEPARATOR_1 = "separator-1";

        public final static String USE_AUTOMATIC_FILENAME_LABEL = "Use a filename automatically computed based on the path of step in project";
        public final static String USE_MANUAL_FILENAME_LABEL = "Use manually provided filename";

        @AField(name = "###" + USE_AUTOMATIC_FILENAME_LABEL, description = USE_AUTOMATIC_FILENAME_LABEL, type = AFieldType.BOOLEAN)
        public final static String USE_AUTOMATIC_FILENAME = "###" + USE_AUTOMATIC_FILENAME_LABEL;

        @AField(name = "Automatic filename computed", description = "", type = AFieldType.LABEL)
        public final static String AUTOMATIC_FILENAME_LABEL = "Automatic filename computed";
        @AField(name = "###result-of-expansion-value", description = "Filename where to read and save step content, automatically computed from the path of step in project.", type = AFieldType.STRING)
        public final static String AUTOMATIC_FILENAME = "###result-of-expansion-value";

        @AField(name = "separator-2", description = "", type = AFieldType.SEPARATOR)
        public final static String SEPARATOR_2 = "separator-2";

        @AField(name = "###" + USE_MANUAL_FILENAME_LABEL, description = USE_MANUAL_FILENAME_LABEL, type = AFieldType.BOOLEAN)
        public final static String USE_MANUAL_FILENAME = "###" + USE_MANUAL_FILENAME_LABEL;

        // no label for this field because it feels redundant since the checkbox above clearly states what this is for
        @AField(name = "###manual-filename", description = "Explicit filename where to read and save step content.  Absolute path or relative to 'Root Path'.", type = AFieldType.FILE)
        public final static String MANUAL_FILENAME = "###manual-filename";

        @AField(name = "###filename", description = "")
        public final static String FILENAME = "###filename";

        @AField(name = "separator-3", description = "", type = AFieldType.SEPARATOR)
        public final static String SEPARATOR_3 = "separator-3";

        @AField(name = "External filename summary", description = "", type = AFieldType.LABEL)
        public final static String SUMMARY_LABEL = "External filename summary";
        @AField(name = "###Summary", description = "Final result of external filename specification.", type = AFieldType.INFORMATION)
        public final static String SUMMARY = "###Summary";


    }

    private class ExternalStepFileSelector {
        private ContentInExternalFileSupport contentInExternalFileSupport;
        private WsdlRequestConfig wsdlRequestConfig;
        private ScriptConfig scriptConfig;
        private Boolean useExternalStepFile;
        private Boolean useAutomaticFilename;
        private Boolean useManualFilename;

        public ExternalStepFileSelector(ContentInExternalFileSupport contentInExternalFileSupport, XmlObject xmlObject) {
            if (xmlObject instanceof WsdlRequestConfig) {
                wsdlRequestConfig = (WsdlRequestConfig) xmlObject;
            } else {
                scriptConfig = contentInExternalFileSupport.getScriptConfig();
            }

            this.contentInExternalFileSupport = contentInExternalFileSupport;
        }

        public Boolean getUseExternalStepFile() {
            return useExternalStepFile;
        }

        public Boolean getUseAutomaticFilename() {
            return useAutomaticFilename;
        }

        public Boolean getUseManualFilename() {
            return useManualFilename;
        }

        // TODO (marcpa00) : refactor this to use ContentInExternalFileCategory
        public ExternalStepFileSelector invoke() {
            if (wsdlRequestConfig != null) {
                if (contentInExternalFileSupport.getSettings().getBoolean(UISettings.AUTO_CONVERT_CONTENT_TO_USE_EXTERNAL_FILE) && !wsdlRequestConfig.isSetExternalFilenameBuildMode()) {
                    wsdlRequestConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.AUTO);
                }
                if (wsdlRequestConfig.isSetExternalFilenameBuildMode()) {
                    if (wsdlRequestConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.NONE) {
                        // All out
                        useExternalStepFile = false;
                        useAutomaticFilename = false;
                        useManualFilename = false;
                    } else {
                        useExternalStepFile = true;
                        useAutomaticFilename = (wsdlRequestConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.AUTO);
                        useManualFilename = (wsdlRequestConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.MANUAL);
                    }
                } else {
                    useExternalStepFile = false;
                    useAutomaticFilename = false;
                    useManualFilename = false;
                }
            } else if (scriptConfig != null) {
                if (contentInExternalFileSupport.getSettings().getBoolean(UISettings.AUTO_CONVERT_CONTENT_TO_USE_EXTERNAL_FILE) && !scriptConfig.isSetExternalFilenameBuildMode()) {
                    scriptConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.AUTO);
                }
                if (scriptConfig.isSetExternalFilenameBuildMode()) {
                    if (scriptConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.NONE) {
                        // All out
                        useExternalStepFile = false;
                        useAutomaticFilename = false;
                        useManualFilename = false;
                    } else {
                        useExternalStepFile = true;
                        useAutomaticFilename = (scriptConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.AUTO);
                        useManualFilename = (scriptConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.MANUAL);
                    }
                } else {
                    useExternalStepFile = false;
                    useAutomaticFilename = false;
                    useManualFilename = false;
                }
            } else {
                useExternalStepFile = false;
                useAutomaticFilename = false;
                useManualFilename = false;
            }

            return this;
        }
    }
}
