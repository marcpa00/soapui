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

import com.eviware.soapui.config.ExternalFilenameBuildModeConfig;
import com.eviware.soapui.config.ModelItemConfig;
import com.eviware.soapui.config.ScriptConfig;
import com.eviware.soapui.config.TestStepConfig;
import com.eviware.soapui.config.WsdlRequestConfig;
import com.eviware.soapui.impl.wsdl.support.HelpUrls;
import com.eviware.soapui.impl.wsdl.teststeps.TestRequestStepInExternalFileSupport;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestRequest;
import com.eviware.soapui.model.ModelItem;
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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class ConfigureExternalFileAction extends AbstractSoapUIAction<ModelItem>
{
	public static final String SOAPUI_ACTION_ID = "ConfigureExternalFileAction";
	private XFormDialog dialog;
    private ComposedFilenameObject composedFilenameObject;
    private TestRequestStepInExternalFileSupport testRequestStepInExternalFileSupport = null;

    public ConfigureExternalFileAction()
	{
		super( "Configure external file settings for step", "Specify which file to load, if content should be saved to external file and how to name the file." );
	}

	public void perform( ModelItem modelItem, Object param )
	{

        if (modelItem instanceof TestRequestStepInExternalFileSupport) {
            testRequestStepInExternalFileSupport = (TestRequestStepInExternalFileSupport) modelItem;
        } else if (modelItem instanceof WsdlTestRequest) {
            testRequestStepInExternalFileSupport = ((WsdlTestRequest)modelItem).getTestStep().getTestRequestStepInExternalFileSupport();
        }
        if (testRequestStepInExternalFileSupport != null) {
            configureExternalFile();
        }
	}

	protected boolean configureExternalFile()
	{
        if( dialog == null ) {
            dialog = ADialogBuilder.buildDialog( Form.class );
        }

        // Initialize flags to default values
        Boolean useExternalStepFile = testRequestStepInExternalFileSupport.getSettings().getBoolean(UISettings.STEP_IN_EXTERNAL_FILE);
        Boolean useAutomaticFilename = true;
        Boolean useComposedFilename = false;
        Boolean useManualFilename = false;
        Boolean useProjectName = false;
        Boolean useTestSuiteName = true;
        Boolean useTestCaseName = true;
        Boolean useTestStepName = true;

        // ... then override them from config
        ModelItemConfig modelItemConfig = testRequestStepInExternalFileSupport.getConfig();

        ExternalStepFileSelector externalStepFileSelector = new ExternalStepFileSelector(testRequestStepInExternalFileSupport, modelItemConfig).invoke();
        useExternalStepFile = externalStepFileSelector.getUseExternalStepFile();
        useAutomaticFilename = externalStepFileSelector.getUseAutomaticFilename();
        useComposedFilename = externalStepFileSelector.getUseComposedFilename();
        useManualFilename = externalStepFileSelector.getUseManualFilename();

        composedFilenameObject = new ComposedFilenameObject(externalStepFileSelector, useProjectName, useTestSuiteName, useTestCaseName, useTestStepName, modelItemConfig).invoke();

        dialog.setValue( Form.SUMMARY,  buildSummary() );
        dialog.setBooleanValue(Form.USE_EXTERNAL_STEP_FILE, useExternalStepFile);

        dialog.setBooleanValue( Form.USE_PROJECT_NAME, composedFilenameObject.getUseProjectName() );
        dialog.setBooleanValue( Form.USE_TEST_SUITE_NAME, composedFilenameObject.getUseTestSuiteName() );
        dialog.setBooleanValue( Form.USE_TEST_CASE_NAME, composedFilenameObject.getUseTestCaseName() );
        dialog.setBooleanValue( Form.USE_TEST_STEP_NAME,  composedFilenameObject.getUseTestStepName() );

        dialog.setValue( Form.PROJECT_NAME, testRequestStepInExternalFileSupport.getTestCase().getTestSuite().getProject().getName() );
        dialog.setValue( Form.TEST_SUITE_NAME, testRequestStepInExternalFileSupport.getTestCase().getTestSuite().getName() );
        dialog.setValue( Form.TEST_CASE_NAME, testRequestStepInExternalFileSupport.getTestCase().getName() );
        dialog.setValue( Form.TEST_STEP_NAME, testRequestStepInExternalFileSupport.getName() );

        String automaticFilename = testRequestStepInExternalFileSupport.buildExternalFilenameForMode(ExternalFilenameBuildModeConfig.AUTO);
        String composedFilename = testRequestStepInExternalFileSupport.buildExternalFilenameForMode(ExternalFilenameBuildModeConfig.COMPOSED);
        String manualFilename = testRequestStepInExternalFileSupport.buildExternalFilenameForMode(ExternalFilenameBuildModeConfig.MANUAL);

        dialog.setValue( Form.AUTOMATIC_FILENAME, automaticFilename );
        dialog.setValue( Form.COMPOSED_FILENAME, composedFilename );
        dialog.setValue( Form.MANUAL_FILENAME, manualFilename );
        FileFormField fileFormField = (FileFormField) dialog.getFormField( Form.MANUAL_FILENAME );
        fileFormField.setProperty( FileFormField.CURRENT_DIRECTORY, testRequestStepInExternalFileSupport.getExternalFileRootPath() );

        dialog.setBooleanValue( Form.USE_AUTOMATIC_FILENAME, useAutomaticFilename );
        dialog.setBooleanValue( Form.USE_COMPOSED_FILENAME, useComposedFilename );
        dialog.setBooleanValue( Form.USE_MANUAL_FILENAME, useManualFilename );

        setupFieldsStateAndVisibility(useExternalStepFile, useAutomaticFilename, useComposedFilename, useManualFilename);

        if (useExternalStepFile) {
            if (useAutomaticFilename) {
                dialog.setValue( Form.FILENAME, automaticFilename );
            } else if (useComposedFilename) {
                dialog.setValue( Form.FILENAME, composedFilename );
            } else if (useManualFilename) {
                dialog.setValue( Form.FILENAME, manualFilename );
            }
        }

        setupListeners();

        String previousFilename = dialog.getValue( Form.FILENAME );
        if (previousFilename == null) {
            previousFilename = "";
        }

		if( !dialog.show() )
			return false;

        // change test step with values from dialog
        if (! dialog.getBooleanValue( Form.USE_EXTERNAL_STEP_FILE )) {
            testRequestStepInExternalFileSupport.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.NONE);
            testRequestStepInExternalFileSupport.updateConfig();
        } else {
            if (dialog.getBooleanValue( Form.USE_AUTOMATIC_FILENAME )) {
                testRequestStepInExternalFileSupport.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.AUTO);
            } else if (dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME )) {
                testRequestStepInExternalFileSupport.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.COMPOSED);
                testRequestStepInExternalFileSupport.setComposeWithProjectName(dialog.getBooleanValue(Form.USE_PROJECT_NAME));
                testRequestStepInExternalFileSupport.setComposeWithTestSuiteName(dialog.getBooleanValue(Form.USE_TEST_SUITE_NAME));
                testRequestStepInExternalFileSupport.setComposeWithTestCaseName(dialog.getBooleanValue(Form.USE_TEST_CASE_NAME));
                testRequestStepInExternalFileSupport.setComposeWithTestStepName(dialog.getBooleanValue(Form.USE_TEST_STEP_NAME));
            } else if (dialog.getBooleanValue( Form.USE_MANUAL_FILENAME )) {
                testRequestStepInExternalFileSupport.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.MANUAL);
            }

            String newFilename = dialog.getValue( Form.FILENAME );
            if ( previousFilename.equals( newFilename ) ) {
                testRequestStepInExternalFileSupport.maybeReloadStepContent();
            } else {
                testRequestStepInExternalFileSupport.setExternalFilename( newFilename );
                testRequestStepInExternalFileSupport.loadStepContent();
                testRequestStepInExternalFileSupport.updateTestStepContent();
            }
            testRequestStepInExternalFileSupport.updateConfig();
            testRequestStepInExternalFileSupport.saveToExternalFile(testRequestStepInExternalFileSupport.updateConfigWithExternalFilePath(), false);

        }

		return true;
	}

    private String buildSummary() {
        String sep = File.separator;
        String currentFilename = dialog.getValue( Form.FILENAME );
        if (currentFilename == null || currentFilename.isEmpty() ) {
            currentFilename = testRequestStepInExternalFileSupport.getExternalFilename();
        }
        if (currentFilename != null && ! currentFilename.isEmpty()) {
            StringBuilder summary = new StringBuilder("Root path for relative path names : ");
            summary.append("\n   ");
            summary.append(testRequestStepInExternalFileSupport.getExternalFileRootPath()).append(sep);
            summary.append("\n");
            summary.append("Effective external filename : ");
            summary.append("\n   ");
            summary.append( currentFilename );
            summary.append("\n");
            File f = new File(currentFilename);
            if (! f.isAbsolute() ) {
                f = new File(testRequestStepInExternalFileSupport.getExternalFileRootPath() + sep + currentFilename);
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

    private void setupFieldsStateAndVisibility(Boolean useExternalStepFile, Boolean useAutomaticFilename, Boolean useComposedFilename, Boolean useManualFilename) {
        // by default, fields are enabled : need to disable them according to flag USE_EXTERNAL_STEP_FILE
        if (useExternalStepFile == Boolean.FALSE) {
            // disable fields because USE_EXTERNAL_STEP_FILE is false
            dialog.getFormField( Form.USE_AUTOMATIC_FILENAME).setEnabled( false );
            dialog.getFormField( Form.USE_COMPOSED_FILENAME).setEnabled( false );
            dialog.getFormField( Form.USE_MANUAL_FILENAME).setEnabled( false );

            dialog.getFormField( Form.USE_PROJECT_NAME ).setEnabled( false );
            dialog.getFormField( Form.USE_TEST_SUITE_NAME ).setEnabled( false );
            dialog.getFormField( Form.USE_TEST_CASE_NAME ).setEnabled( false );
            dialog.getFormField( Form.USE_TEST_STEP_NAME ).setEnabled( false );

            dialog.getFormField( Form.AUTOMATIC_FILENAME ).setEnabled( false );
            dialog.getFormField( Form.COMPOSED_FILENAME ).setEnabled( false );
            dialog.getFormField( Form.MANUAL_FILENAME ).setEnabled( false );

            dialog.getFormField( Form.SUMMARY ).setEnabled( false );
        } else {
            if ( useAutomaticFilename ) {
                dialog.getFormField( Form.USE_PROJECT_NAME ).setEnabled( false );
                dialog.getFormField( Form.USE_TEST_SUITE_NAME ).setEnabled( false );
                dialog.getFormField( Form.USE_TEST_CASE_NAME ).setEnabled( false );
                dialog.getFormField( Form.USE_TEST_STEP_NAME ).setEnabled( false );

                dialog.getFormField( Form.COMPOSED_FILENAME ).setEnabled( false );
                dialog.getFormField( Form.MANUAL_FILENAME ).setEnabled( false );
            }

            if ( useComposedFilename ) {
                dialog.getFormField( Form.AUTOMATIC_FILENAME ).setEnabled( false );
                dialog.getFormField( Form.MANUAL_FILENAME).setEnabled( false );
            }
            if ( useManualFilename ) {
                dialog.getFormField( Form.USE_PROJECT_NAME ).setEnabled( false );
                dialog.getFormField( Form.USE_TEST_SUITE_NAME ).setEnabled( false );
                dialog.getFormField( Form.USE_TEST_CASE_NAME ).setEnabled( false );
                dialog.getFormField( Form.USE_TEST_STEP_NAME ).setEnabled( false );

                dialog.getFormField( Form.AUTOMATIC_FILENAME ).setEnabled( false );
                dialog.getFormField( Form.COMPOSED_FILENAME ).setEnabled( false );
            }
        }
        XFormField formField = dialog.getFormField(Form.AUTOMATIC_FILENAME);
        if (formField instanceof JTextFieldFormField) {
            ((JTextFieldFormField)formField).getComponent().setEditable(false);
        }
        formField = dialog.getFormField(Form.COMPOSED_FILENAME);
        if (formField instanceof JTextFieldFormField) {
            ((JTextFieldFormField)formField).getComponent().setEditable(false);
        }

        String invisibleFormFields[] = { Form.PROJECT_NAME, Form.TEST_SUITE_NAME, Form.TEST_CASE_NAME, Form.TEST_STEP_NAME, Form.FILENAME };
        for (String invisibleFormField : Arrays.asList(invisibleFormFields)) {
            ((AbstractSwingXFormField)dialog.getFormField( invisibleFormField )).getComponent().setVisible(false);
        }
    }


    private void setupListeners() {
        dialog.getFormField( Form.USE_EXTERNAL_STEP_FILE ).addFormFieldListener( new XFormFieldListener()
        {
            public void valueChanged( XFormField sourceField, String newValue, String oldValue )
            {

                dialog.getFormField( Form.USE_AUTOMATIC_FILENAME).setEnabled( Boolean.valueOf(newValue));
                dialog.getFormField( Form.USE_COMPOSED_FILENAME).setEnabled( Boolean.valueOf(newValue));
                dialog.getFormField( Form.USE_MANUAL_FILENAME).setEnabled( Boolean.valueOf(newValue));

                if ( Boolean.valueOf( newValue ) == false ) {
                    dialog.getFormField( Form.USE_PROJECT_NAME ).setEnabled( false );
                    dialog.getFormField( Form.USE_TEST_SUITE_NAME ).setEnabled( false );
                    dialog.getFormField( Form.USE_TEST_CASE_NAME ).setEnabled( false );
                    dialog.getFormField( Form.USE_TEST_STEP_NAME ).setEnabled( false );
                    dialog.getFormField( Form.MANUAL_FILENAME ).setEnabled( false );
                    dialog.getFormField( Form.COMPOSED_FILENAME ).setEnabled( false );
                    dialog.getFormField( Form.AUTOMATIC_FILENAME ).setEnabled( false );
                } else {

                    dialog.getFormField( Form.AUTOMATIC_FILENAME ).setEnabled( dialog.getBooleanValue( Form.USE_AUTOMATIC_FILENAME) );

                    dialog.getFormField( Form.USE_PROJECT_NAME ).setEnabled( dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME) );
                    dialog.getFormField( Form.USE_TEST_SUITE_NAME ).setEnabled( dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME) );
                    dialog.getFormField( Form.USE_TEST_CASE_NAME ).setEnabled( dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME) );
                    dialog.getFormField( Form.USE_TEST_STEP_NAME ).setEnabled( dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME) );
                    dialog.getFormField( Form.COMPOSED_FILENAME ).setEnabled( dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME) );

                    dialog.getFormField( Form.MANUAL_FILENAME).setEnabled( dialog.getBooleanValue( Form.USE_MANUAL_FILENAME) ) ;
                }
                dialog.setValue( Form.SUMMARY,  buildSummary() );
            }
        } );

        dialog.getFormField( Form.USE_AUTOMATIC_FILENAME).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                if ( Boolean.valueOf(newValue) == true && sourceField.equals( dialog.getFormField( Form.USE_AUTOMATIC_FILENAME))) {
                    if (dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME)) {
                        dialog.setBooleanValue( Form.USE_COMPOSED_FILENAME, false );
                    }
                    if (dialog.getBooleanValue( Form.USE_MANUAL_FILENAME)) {
                        dialog.setBooleanValue( Form.USE_MANUAL_FILENAME, false );
                    }
                } else if ( Boolean.valueOf( newValue ) == false && sourceField.equals(dialog.getFormField( Form.USE_AUTOMATIC_FILENAME))) {
                    if (dialog.getBooleanValue(Form.USE_COMPOSED_FILENAME) == false && dialog.getBooleanValue(Form.USE_MANUAL_FILENAME) == false) {
                        dialog.setBooleanValue( Form.USE_AUTOMATIC_FILENAME, true );
                    }
                }

                dialog.getFormField( Form.AUTOMATIC_FILENAME ).setEnabled( Boolean.valueOf( newValue ));

                Boolean newEnabledValue = !Boolean.valueOf(newValue);
                dialog.getFormField( Form.USE_PROJECT_NAME ).setEnabled( newEnabledValue );
                dialog.getFormField( Form.USE_TEST_SUITE_NAME ).setEnabled( newEnabledValue );
                dialog.getFormField( Form.USE_TEST_CASE_NAME ).setEnabled( newEnabledValue );
                dialog.getFormField( Form.USE_TEST_STEP_NAME ).setEnabled( newEnabledValue );
                dialog.getFormField( Form.COMPOSED_FILENAME ).setEnabled( newEnabledValue );
                dialog.getFormField( Form.MANUAL_FILENAME ).setEnabled( newEnabledValue );

                Boolean useExternalStepFile = dialog.getBooleanValue( Form.USE_EXTERNAL_STEP_FILE );
                Boolean useAutomaticFilename = dialog.getBooleanValue( Form.USE_AUTOMATIC_FILENAME );
                Boolean useComposedFilename = dialog.getBooleanValue(Form.USE_COMPOSED_FILENAME);
                Boolean useManualFilename = dialog.getBooleanValue(Form.USE_MANUAL_FILENAME);

                if (useExternalStepFile) {
                    if (useAutomaticFilename) {
                        dialog.setValue( Form.FILENAME, dialog.getValue( Form.AUTOMATIC_FILENAME ));
                    } else if (useComposedFilename) {
                        dialog.setValue( Form.FILENAME, dialog.getValue( Form.COMPOSED_FILENAME ));
                    } else if (useManualFilename) {
                        dialog.setValue( Form.FILENAME, dialog.getValue( Form.MANUAL_FILENAME ));
                    }
                }
                dialog.setValue( Form.SUMMARY,  buildSummary() );
            }
        });

        dialog.getFormField( Form.USE_COMPOSED_FILENAME).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                if ( Boolean.valueOf(newValue) == true && sourceField.equals( dialog.getFormField( Form.USE_COMPOSED_FILENAME))) {
                    if (dialog.getBooleanValue( Form.USE_AUTOMATIC_FILENAME)) {
                        dialog.setBooleanValue( Form.USE_AUTOMATIC_FILENAME, false );
                    }
                    if (dialog.getBooleanValue( Form.USE_MANUAL_FILENAME)) {
                        dialog.setBooleanValue( Form.USE_MANUAL_FILENAME, false );
                    }
                } else if ( Boolean.valueOf( newValue ) == false  && sourceField.equals(dialog.getFormField( Form.USE_COMPOSED_FILENAME))) {
                    if (dialog.getBooleanValue(Form.USE_AUTOMATIC_FILENAME) == false && dialog.getBooleanValue(Form.USE_MANUAL_FILENAME) == false) {
                        dialog.setBooleanValue( Form.USE_COMPOSED_FILENAME, true );
                    }
                }

                dialog.getFormField( Form.COMPOSED_FILENAME ).setEnabled( Boolean.valueOf( newValue ));
                dialog.getFormField( Form.USE_PROJECT_NAME ).setEnabled( Boolean.valueOf( newValue ) );
                dialog.getFormField( Form.USE_TEST_SUITE_NAME ).setEnabled( Boolean.valueOf( newValue ) );
                dialog.getFormField( Form.USE_TEST_CASE_NAME ).setEnabled( Boolean.valueOf( newValue ) );
                dialog.getFormField( Form.USE_TEST_STEP_NAME ).setEnabled( Boolean.valueOf( newValue ) );

                Boolean newEnabledValue = !Boolean.valueOf(newValue);
                dialog.getFormField( Form.AUTOMATIC_FILENAME ).setEnabled( newEnabledValue );
                dialog.getFormField( Form.MANUAL_FILENAME ).setEnabled( newEnabledValue );
                Boolean useExternalStepFile = dialog.getBooleanValue( Form.USE_EXTERNAL_STEP_FILE );
                Boolean useAutomaticFilename = dialog.getBooleanValue( Form.USE_AUTOMATIC_FILENAME );
                Boolean useComposedFilename = dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME );
                Boolean useManualFilename = dialog.getBooleanValue( Form.USE_MANUAL_FILENAME );

                if (useExternalStepFile) {
                    if (useAutomaticFilename) {
                        dialog.setValue( Form.FILENAME, dialog.getValue( Form.AUTOMATIC_FILENAME ));
                    } else if (useComposedFilename) {
                        dialog.setValue( Form.FILENAME, dialog.getValue( Form.COMPOSED_FILENAME ));
                    } else if (useManualFilename) {
                        dialog.setValue( Form.FILENAME, dialog.getValue( Form.MANUAL_FILENAME ));
                    }
                }
                dialog.setValue( Form.SUMMARY,  buildSummary() );
            }
        });

        dialog.getFormField( Form.USE_MANUAL_FILENAME).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                if ( Boolean.valueOf(newValue) == true && sourceField.equals( dialog.getFormField( Form.USE_MANUAL_FILENAME))) {
                    if (dialog.getBooleanValue( Form.USE_AUTOMATIC_FILENAME)) {
                        dialog.setBooleanValue( Form.USE_AUTOMATIC_FILENAME, false );
                    }
                    if (dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME)) {
                        dialog.setBooleanValue( Form.USE_COMPOSED_FILENAME, false );
                    }
                } else if ( Boolean.valueOf( newValue ) == false  && sourceField.equals(dialog.getFormField( Form.USE_MANUAL_FILENAME))) {
                    if (dialog.getBooleanValue(Form.USE_AUTOMATIC_FILENAME) == false && dialog.getBooleanValue(Form.USE_COMPOSED_FILENAME) == false) {
                        dialog.setBooleanValue( Form.USE_MANUAL_FILENAME, true );
                    }
                }

                dialog.getFormField( Form.MANUAL_FILENAME ).setEnabled( Boolean.valueOf( newValue ) );

                Boolean newEnabledValue = !Boolean.valueOf(newValue);
                dialog.getFormField( Form.USE_PROJECT_NAME ).setEnabled( newEnabledValue );
                dialog.getFormField( Form.USE_TEST_SUITE_NAME ).setEnabled( newEnabledValue );
                dialog.getFormField( Form.USE_TEST_CASE_NAME ).setEnabled( newEnabledValue );
                dialog.getFormField( Form.USE_TEST_STEP_NAME ).setEnabled( newEnabledValue );
                dialog.getFormField( Form.AUTOMATIC_FILENAME ).setEnabled( newEnabledValue );
                dialog.getFormField( Form.COMPOSED_FILENAME ).setEnabled( newEnabledValue );
                Boolean useExternalStepFile = dialog.getBooleanValue( Form.USE_EXTERNAL_STEP_FILE );
                Boolean useAutomaticFilename = dialog.getBooleanValue( Form.USE_AUTOMATIC_FILENAME );
                Boolean useComposedFilename = dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME );
                Boolean useManualFilename = dialog.getBooleanValue( Form.USE_MANUAL_FILENAME );

                if (useExternalStepFile) {
                    if (useAutomaticFilename) {
                        dialog.setValue( Form.FILENAME, dialog.getValue( Form.AUTOMATIC_FILENAME ));
                    } else if (useComposedFilename) {
                        dialog.setValue( Form.FILENAME, dialog.getValue( Form.COMPOSED_FILENAME ));
                    } else if (useManualFilename) {
                        dialog.setValue( Form.FILENAME, dialog.getValue( Form.MANUAL_FILENAME ));
                    }
                }
                dialog.setValue( Form.SUMMARY,  buildSummary() );
            }
        });

        dialog.getFormField( Form.USE_PROJECT_NAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                composedFilenameObject.setUseProjectName(Boolean.getBoolean(newValue));
                String composedFilename = composedFilenameObject.build();
                dialog.setValue( Form.COMPOSED_FILENAME, composedFilename );
                dialog.setValue( Form.FILENAME, composedFilename );
                dialog.setValue( Form.SUMMARY,  buildSummary() );
            }
        });
        dialog.getFormField( Form.USE_TEST_SUITE_NAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                composedFilenameObject.setUseTestSuiteName(Boolean.getBoolean(newValue));
                String composedFilename = composedFilenameObject.build();
                dialog.setValue( Form.COMPOSED_FILENAME, composedFilename );
                dialog.setValue( Form.FILENAME, composedFilename );
                dialog.setValue( Form.SUMMARY,  buildSummary() );
            }
        });
        dialog.getFormField( Form.USE_TEST_CASE_NAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                composedFilenameObject.setUseTestCaseName(Boolean.getBoolean(newValue));
                String composedFilename = composedFilenameObject.build();
                dialog.setValue( Form.COMPOSED_FILENAME, composedFilename );
                dialog.setValue( Form.FILENAME, composedFilename );
                dialog.setValue( Form.SUMMARY,  buildSummary() );
            }
        });
        dialog.getFormField( Form.USE_TEST_STEP_NAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                composedFilenameObject.setUseTestStepName(Boolean.getBoolean(newValue));
                String composedFilename = composedFilenameObject.build();
                dialog.setValue( Form.COMPOSED_FILENAME, composedFilename );
                dialog.setValue( Form.FILENAME, composedFilename );
                dialog.setValue( Form.SUMMARY,  buildSummary() );
            }
        });
        dialog.getFormField( Form.AUTOMATIC_FILENAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                dialog.setValue( Form.FILENAME, newValue );
                dialog.setValue( Form.SUMMARY,  buildSummary() );
            }
        });
        dialog.getFormField( Form.COMPOSED_FILENAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                dialog.setValue( Form.FILENAME, newValue );
                dialog.setValue( Form.SUMMARY,  buildSummary() );
            }
        });
        dialog.getFormField( Form.MANUAL_FILENAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                dialog.setValue( Form.FILENAME, newValue );
                dialog.setValue( Form.SUMMARY,  buildSummary() );
            }
        });
    }



    @AForm( name = "Configure external file settings for step", description = "Specify which file to load, if content should be saved to external file and how to name the file.", helpUrl = HelpUrls.USE_EXT_FILE_FOR_STEP_HELP_URL)
	private interface Form
	{
        @AField( name = "Use External Step File", description = "Use an external file to store the request content of this step.", type = AFieldType.BOOLEAN )
        public final static String USE_EXTERNAL_STEP_FILE = "Use External Step File";

        @AField( name = "separator-1", description = "", type = AFieldType.SEPARATOR)
        public final static String SEPARATOR_1 = "separator-1";

        public final static String USE_AUTOMATIC_FILENAME_LABEL = "Use a filename automatically computed based on the path of step in project" ;
        public final static String USE_COMPOSED_FILENAME_LABEL = "Compose file with selected parts";
        public final static String USE_MANUAL_FILENAME_LABEL = "Use manually provided filename";

        @AField( name = "###" + USE_AUTOMATIC_FILENAME_LABEL, description = USE_AUTOMATIC_FILENAME_LABEL, type = AFieldType.BOOLEAN )
        public final static String USE_AUTOMATIC_FILENAME = "###" + USE_AUTOMATIC_FILENAME_LABEL;

        @AField( name = "Automatic filename computed", description = "", type = AFieldType.LABEL)
        public final static String AUTOMATIC_FILENAME_LABEL = "Automatic filename computed";
        @AField( name = "###result-of-expansion-value", description = "Filename where to read and save step content, automatically computed from the path of step in project.", type = AFieldType.STRING)
        public final static String AUTOMATIC_FILENAME = "###result-of-expansion-value";

        @AField( name = "separator-2", description = "", type = AFieldType.SEPARATOR )
        public final static String SEPARATOR_2 = "separator-2";

        @AField( name = "###" + USE_COMPOSED_FILENAME_LABEL, description = USE_COMPOSED_FILENAME_LABEL, type = AFieldType.BOOLEAN )
        public final static String USE_COMPOSED_FILENAME = "###" + USE_COMPOSED_FILENAME_LABEL;

        @AField( name = "Use project name", description = "", type = AFieldType.BOOLEAN)
        public final static String USE_PROJECT_NAME = "Use project name";
        @AField( name = "###projectName", description = "")
        public final static String PROJECT_NAME = "###projectName";

        @AField( name = "Use test suite name", description = "", type = AFieldType.BOOLEAN)
        public final static String USE_TEST_SUITE_NAME = "Use test suite name";
        @AField( name = "###testSuiteName", description = "")
        public final static String TEST_SUITE_NAME = "###testSuiteName";

        @AField( name = "Use test case name", description = "", type = AFieldType.BOOLEAN)
        public final static String USE_TEST_CASE_NAME = "Use test case name";
        @AField( name = "###testCaseName", description = "")
        public final static String TEST_CASE_NAME = "###testCaseName";

        @AField( name = "Use test step name", description = "", type = AFieldType.BOOLEAN)
        public final static String USE_TEST_STEP_NAME = "Use test step name";
        @AField( name = "###testStepName", description = "")
        public final static String TEST_STEP_NAME = "###testStepName";


        @AField( name = "Composed filename computed", description = "", type = AFieldType.LABEL)
        public final static String COMPOSED_FILENAME_LABEL = "Composed filename computed";
        @AField( name = "###result-of-composition-value", description = "Filename where to read and save step content, automatically computed from the path of step in project.", type = AFieldType.STRING)
        public final static String COMPOSED_FILENAME = "###result-of-composition-value";

        @AField( name = "separator-3", description = "", type = AFieldType.SEPARATOR )
        public final static String SEPARATOR_3 = "separator-3";

        @AField( name = "###" + USE_MANUAL_FILENAME_LABEL, description = USE_MANUAL_FILENAME_LABEL, type = AFieldType.BOOLEAN )
        public final static String USE_MANUAL_FILENAME = "###" + USE_MANUAL_FILENAME_LABEL;

        // no label for this field because it feels redundant since the checkbox above clearly states what this is for
        @AField( name = "###manual-filename", description = "Explicit filename where to read and save step content.  Absolute path or relative to 'Root Path'.", type = AFieldType.FILE)
		public final static String MANUAL_FILENAME = "###manual-filename";

        @AField( name = "###filename", description = "")
        public final static String FILENAME = "###filename";

        @AField( name = "separator-4", description = "", type = AFieldType.SEPARATOR )
        public final static String SEPARATOR_4 = "separator-4";

        @AField( name = "External filename summary", description = "", type = AFieldType.LABEL)
        public final static String SUMMARY_LABEL = "External filename summary";
        @AField( name = "###Summary", description = "Final result of external filename specification.", type = AFieldType.INFORMATION )
        public final static String SUMMARY = "###Summary";


    }

    private class ComposedFilenameObject {
        private ExternalStepFileSelector externalStepFileSelector;
        private Boolean useProjectName;
        private Boolean useTestSuiteName;
        private Boolean useTestCaseName;
        private Boolean useTestStepName;


        public ComposedFilenameObject(ExternalStepFileSelector externalStepFileSelector, Boolean useProjectName, Boolean useTestSuiteName, Boolean useTestCaseName, Boolean useTestStepName, ModelItemConfig modelItemConfig) {
            this.externalStepFileSelector = externalStepFileSelector;
            this.useProjectName = useProjectName;
            this.useTestSuiteName = useTestSuiteName;
            this.useTestCaseName = useTestCaseName;
            this.useTestStepName = useTestStepName;
        }

        public Boolean getUseProjectName() {
            return useProjectName;
        }

        public Boolean getUseTestSuiteName() {
            return useTestSuiteName;
        }

        public Boolean getUseTestCaseName() {
            return useTestCaseName;
        }

        public Boolean getUseTestStepName() {
            return useTestStepName;
        }
        private void setUseProjectName(Boolean useProjectName) {
            this.useProjectName = useProjectName;
        }

        private void setUseTestSuiteName(Boolean useTestSuiteName) {
            this.useTestSuiteName = useTestSuiteName;
        }

        private void setUseTestCaseName(Boolean useTestCaseName) {
            this.useTestCaseName = useTestCaseName;
        }

        private void setUseTestStepName(Boolean useTestStepName) {
            this.useTestStepName = useTestStepName;
        }

        public ComposedFilenameObject invoke() {
            if (externalStepFileSelector.wsdlRequestConfig != null) {
                if (externalStepFileSelector.wsdlRequestConfig.isSetComposeWithProjectName()) {
                    useProjectName = externalStepFileSelector.wsdlRequestConfig.getComposeWithProjectName();
                }
                if (externalStepFileSelector.wsdlRequestConfig.isSetComposeWithTestSuiteName()) {
                    useTestSuiteName = externalStepFileSelector.wsdlRequestConfig.getComposeWithTestSuiteName();
                }
                if (externalStepFileSelector.wsdlRequestConfig.isSetComposeWithTestCaseName()) {
                    useTestCaseName = externalStepFileSelector.wsdlRequestConfig.getComposeWithTestCaseName();
                }
                if (externalStepFileSelector.wsdlRequestConfig.isSetComposeWithTestStepName()) {
                    useTestStepName = externalStepFileSelector.wsdlRequestConfig.getComposeWithTestStepName();
                }
            } else if (externalStepFileSelector.scriptConfig != null) {
                if (externalStepFileSelector.scriptConfig.isSetComposeWithProjectName()) {
                    useProjectName = externalStepFileSelector.scriptConfig.getComposeWithProjectName();
                }
                if (externalStepFileSelector.scriptConfig.isSetComposeWithTestSuiteName()) {
                    useTestSuiteName = externalStepFileSelector.scriptConfig.getComposeWithTestSuiteName();
                }
                if (externalStepFileSelector.scriptConfig.isSetComposeWithTestCaseName()) {
                    useTestCaseName = externalStepFileSelector.scriptConfig.getComposeWithTestCaseName();
                }
                if (externalStepFileSelector.scriptConfig.isSetComposeWithTestStepName()) {
                    useTestStepName = externalStepFileSelector.scriptConfig.getComposeWithTestStepName();
                }
            }

            return this;
        }

        /**
         * build composed filename value from dialog according to our current selection
         * @return the string resulting of the composition of dialog field values controlled by which of our use* flags are set.
         */
        public String build() {
            StringBuilder stringBuilder = new StringBuilder();

            if (dialog.getBooleanValue( Form.USE_PROJECT_NAME )) {
                stringBuilder.append(File.separator).append( dialog.getValue(Form.PROJECT_NAME) );
            }
            if (dialog.getBooleanValue( Form.USE_TEST_SUITE_NAME )) {
                stringBuilder.append(File.separator).append(dialog.getValue(Form.TEST_SUITE_NAME));
            }
            if (dialog.getBooleanValue( Form.USE_TEST_CASE_NAME )) {
                stringBuilder.append(File.separator).append(dialog.getValue(Form.TEST_CASE_NAME));
            }
            if (dialog.getBooleanValue( Form.USE_TEST_STEP_NAME )) {
                stringBuilder.append(File.separator).append(dialog.getValue(Form.TEST_STEP_NAME));
            } else {
                // no step name, get the default step name.
                stringBuilder.append(File.separator).append( TestRequestStepInExternalFileSupport.DEFAULT_STEP_FILENAME );
            }
            if (stringBuilder.charAt(0) == File.separatorChar) {
                stringBuilder.deleteCharAt(0);
            }
            return externalStepFileSelector.testRequestStepInExternalFileSupport.finishBuildExternalFilename(stringBuilder);
        }
    }

    private class ExternalStepFileSelector {
        private TestRequestStepInExternalFileSupport testRequestStepInExternalFileSupport;
        private WsdlRequestConfig wsdlRequestConfig;
        private ScriptConfig scriptConfig;
        private Boolean useExternalStepFile;
        private Boolean useAutomaticFilename;
        private Boolean useComposedFilename;
        private Boolean useManualFilename;

        public ExternalStepFileSelector(TestRequestStepInExternalFileSupport testRequestStepInExternalFileSupport, ModelItemConfig modelItemConfig) {
            if (modelItemConfig instanceof WsdlRequestConfig) {
                wsdlRequestConfig = (WsdlRequestConfig) modelItemConfig;
            } else if (modelItemConfig instanceof TestStepConfig) {
                scriptConfig = testRequestStepInExternalFileSupport.getScriptConfig();
            }

            this.testRequestStepInExternalFileSupport = testRequestStepInExternalFileSupport;
        }

        public Boolean getUseExternalStepFile() {
            return useExternalStepFile;
        }

        public Boolean getUseAutomaticFilename() {
            return useAutomaticFilename;
        }

        public Boolean getUseComposedFilename() {
            return useComposedFilename;
        }

        public Boolean getUseManualFilename() {
            return useManualFilename;
        }

        public ExternalStepFileSelector invoke() {
            if (wsdlRequestConfig != null) {
                if (testRequestStepInExternalFileSupport.getSettings().getBoolean(UISettings.AUTO_CONVERT_STEP_TO_USE_EXTERNAL_FILE) && ! wsdlRequestConfig.isSetExternalFilenameBuildMode()) {
                    wsdlRequestConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.AUTO);
                }
                if (wsdlRequestConfig.isSetExternalFilenameBuildMode()) {
                    if (wsdlRequestConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.NONE) {
                        // All out
                        useExternalStepFile = false;
                        useAutomaticFilename = false;
                        useComposedFilename = false;
                        useManualFilename = false;
                    } else {
                        useExternalStepFile = true;
                        useAutomaticFilename = (wsdlRequestConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.AUTO);
                        useComposedFilename = (wsdlRequestConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.COMPOSED);
                        useManualFilename = (wsdlRequestConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.MANUAL);
                    }
                } else {
                    useExternalStepFile = false;
                    useAutomaticFilename = false;
                    useComposedFilename = false;
                    useManualFilename = false;
                }
            } else if (scriptConfig != null) {
                if (testRequestStepInExternalFileSupport.getSettings().getBoolean(UISettings.AUTO_CONVERT_STEP_TO_USE_EXTERNAL_FILE) && ! scriptConfig.isSetExternalFilenameBuildMode()) {
                    scriptConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.AUTO);
                }
                if (scriptConfig.isSetExternalFilenameBuildMode()) {
                    if (scriptConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.NONE) {
                        // All out
                        useExternalStepFile = false;
                        useAutomaticFilename = false;
                        useComposedFilename = false;
                        useManualFilename = false;
                    } else {
                        useExternalStepFile = true;
                        useAutomaticFilename = (scriptConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.AUTO);
                        useComposedFilename = (scriptConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.COMPOSED);
                        useManualFilename = (scriptConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.MANUAL);
                    }
                } else {
                    useExternalStepFile = false;
                    useAutomaticFilename = false;
                    useComposedFilename = false;
                    useManualFilename = false;
                }
            } else {
                useExternalStepFile = false;
                useAutomaticFilename = false;
                useComposedFilename = false;
                useManualFilename = false;
            }

            return this;
        }
    }
}
