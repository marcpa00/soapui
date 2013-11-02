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
import com.eviware.soapui.config.WsdlRequestConfig;
import com.eviware.soapui.impl.wsdl.support.HelpUrls;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestRequest;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestRequestStep;
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
import com.eviware.x.impl.swing.JTextFieldFormField;

import java.util.Arrays;

public class ConfigureExternalFileAction extends AbstractSoapUIAction<WsdlTestRequest>
{
	public static final String SOAPUI_ACTION_ID = "ConfigureExternalFileAction";
	private XFormDialog dialog;
    private ComposedFilenameObject composedFilenameObject;

	public ConfigureExternalFileAction()
	{
		super( "Configure external file settings for step", "Specify which file to load, if content should be saved to external file and how to name the file." );
	}

	public void perform( WsdlTestRequest request, Object param )
	{
        configureExternalFile(request);
	}

	protected boolean configureExternalFile( WsdlTestRequest request )
	{
        if( dialog == null ) {
            dialog = ADialogBuilder.buildDialog( Form.class );
        }

        String rootPath = request.getTestCase().getTestSuite().getProject().getPath();
        if (rootPath == null || rootPath.isEmpty()) {
            // TODO (marcpa) : is this the right thing to do ?
            rootPath = System.getProperty("user.dir");
        }
        if (rootPath != null && rootPath.endsWith(".xml")) {
            rootPath = rootPath.replaceAll(".xml", ".resources");
        } else {
            rootPath = rootPath + ".resources";
        }

        // Initialize flags to default values
        Boolean useExternalStepFile = request.getSettings().getBoolean(UISettings.STEP_IN_EXTERNAL_FILE);
        Boolean useAutomaticFilename = true;
        Boolean useComposedFilname = false;
        Boolean useManualFilename = false;
        Boolean useProjectName = false;
        Boolean useTestSuiteName = true;
        Boolean useTestCaseName = true;
        Boolean useTestStepName = true;

        // ... then override them from config
        WsdlRequestConfig wsdlRequestConfig = request.getConfig();
        if (request.getSettings().getBoolean(UISettings.AUTO_CONVERT_STEP_TO_USE_EXTERNAL_FILE) && ! wsdlRequestConfig.isSetExternalFilenameBuildMode()) {
            wsdlRequestConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.AUTO);
        }
        if (wsdlRequestConfig.isSetExternalFilenameBuildMode()) {
            if (wsdlRequestConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.NONE) {
                // All out
                useExternalStepFile = false;
                useAutomaticFilename = false;
                useComposedFilname = false;
                useManualFilename = false;
            } else {
                useExternalStepFile = true;
                useAutomaticFilename = (wsdlRequestConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.AUTO);
                useComposedFilname = (wsdlRequestConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.COMPOSED);
                useManualFilename = (wsdlRequestConfig.getExternalFilenameBuildMode() == ExternalFilenameBuildModeConfig.MANUAL);
            }
        } else {
            useExternalStepFile = false;
            useAutomaticFilename = false;
            useComposedFilname = false;
            useManualFilename = false;
        }

        composedFilenameObject = new ComposedFilenameObject(useProjectName, useTestSuiteName, useTestCaseName, useTestStepName, wsdlRequestConfig).invoke();

        dialog.setValue( Form.ROOT_PATH, rootPath );
        dialog.setBooleanValue(Form.USE_EXTERNAL_STEP_FILE, useExternalStepFile);

        dialog.setBooleanValue( Form.USE_PROJECT_NAME, composedFilenameObject.getUseProjectName() );
        dialog.setBooleanValue( Form.USE_TEST_SUITE_NAME, composedFilenameObject.getUseTestSuiteName() );
        dialog.setBooleanValue( Form.USE_TEST_CASE_NAME, composedFilenameObject.getUseTestCaseName() );
        dialog.setBooleanValue( Form.USE_TEST_STEP_NAME,  composedFilenameObject.getUseTestStepName() );

        dialog.setValue( Form.PROJECT_NAME, request.getTestCase().getTestSuite().getProject().getName() );
        dialog.setValue( Form.TEST_SUITE_NAME, request.getTestCase().getTestSuite().getName() );
        dialog.setValue( Form.TEST_CASE_NAME, request.getTestCase().getName() );
        dialog.setValue( Form.TEST_STEP_NAME, request.getName() );

        String automaticFilename = buildAutomaticFilename(request);
        String composedFilename = composedFilenameObject.build(request);
        String manualFilename = buildManualFilename(request);

        dialog.setValue( Form.AUTOMATIC_FILENAME, automaticFilename );
        dialog.setValue( Form.COMPOSED_FILENAME, composedFilename );
        dialog.setValue( Form.MANUAL_FILENAME, manualFilename );

        dialog.setBooleanValue( Form.USE_AUTOMATIC_FILENAME, useAutomaticFilename );
        dialog.setBooleanValue( Form.USE_COMPOSED_FILENAME, useComposedFilname );
        dialog.setBooleanValue( Form.USE_MANUAL_FILENAME, useManualFilename );

        setupFieldsStateAndVisibility(useExternalStepFile, useAutomaticFilename, useComposedFilname, useManualFilename);

        if (useExternalStepFile) {
            if (useAutomaticFilename) {
                dialog.setValue( Form.FILENAME, automaticFilename);
            } else if (useComposedFilname) {
                dialog.setValue( Form.FILENAME, composedFilename);
            } else if (useManualFilename) {
                dialog.setValue( Form.FILENAME, manualFilename);
            }
        }

        setupListeners();

		if( !dialog.show() )
			return false;

        WsdlTestRequestStep testRequestStep = request.getTestStep();

        // change testRequestStep with values from dialog
        if (! dialog.getBooleanValue( Form.USE_EXTERNAL_STEP_FILE )) {
            testRequestStep.getTestRequestStepInExternalFileSupport().setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.NONE);
            if (request.getSettings().getBoolean(UISettings.AUTO_CONVERT_STEP_TO_USE_EXTERNAL_FILE)) {
                wsdlRequestConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.NONE);
            }
            if (wsdlRequestConfig.isSetComposeWithProjectName()) { wsdlRequestConfig.unsetComposeWithProjectName(); }
            if (wsdlRequestConfig.isSetComposeWithTestSuiteName()) { wsdlRequestConfig.unsetComposeWithTestSuiteName(); }
            if (wsdlRequestConfig.isSetComposeWithTestCaseName() ) { wsdlRequestConfig.unsetComposeWithTestCaseName(); }
            if (wsdlRequestConfig.isSetComposeWithTestStepName() ) { wsdlRequestConfig.unsetComposeWithTestStepName(); }
            wsdlRequestConfig.getRequest().setStringValue(request.getRequestContent());
        } else {
            if (dialog.getBooleanValue( Form.USE_AUTOMATIC_FILENAME )) {
                testRequestStep.getTestRequestStepInExternalFileSupport().setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.AUTO);
                wsdlRequestConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.AUTO);
                if (wsdlRequestConfig.isSetComposeWithProjectName()) { wsdlRequestConfig.unsetComposeWithProjectName(); }
                if (wsdlRequestConfig.isSetComposeWithTestSuiteName()) { wsdlRequestConfig.unsetComposeWithTestSuiteName(); }
                if (wsdlRequestConfig.isSetComposeWithTestCaseName() ) { wsdlRequestConfig.unsetComposeWithTestCaseName(); }
                if (wsdlRequestConfig.isSetComposeWithTestStepName() ) { wsdlRequestConfig.unsetComposeWithTestStepName(); }
            } else if (dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME )) {
                testRequestStep.getTestRequestStepInExternalFileSupport().setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.COMPOSED);
                testRequestStep.getTestRequestStepInExternalFileSupport().setComposeWithProjectName( dialog.getBooleanValue( Form.USE_PROJECT_NAME ) );
                testRequestStep.getTestRequestStepInExternalFileSupport().setComposeWithTestSuiteName( dialog.getBooleanValue( Form.USE_TEST_SUITE_NAME ) );
                testRequestStep.getTestRequestStepInExternalFileSupport().setComposeWithTestCaseName( dialog.getBooleanValue( Form.USE_TEST_CASE_NAME ) );
                testRequestStep.getTestRequestStepInExternalFileSupport().setComposeWithTestStepName( dialog.getBooleanValue( Form.USE_TEST_STEP_NAME ) );

                wsdlRequestConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.COMPOSED);
                wsdlRequestConfig.setComposeWithProjectName( dialog.getBooleanValue( Form.USE_PROJECT_NAME ) );
                wsdlRequestConfig.setComposeWithTestSuiteName( dialog.getBooleanValue( Form.USE_TEST_SUITE_NAME ) );
                wsdlRequestConfig.setComposeWithTestCaseName( dialog.getBooleanValue( Form.USE_TEST_CASE_NAME ) );
                wsdlRequestConfig.setComposeWithTestStepName( dialog.getBooleanValue( Form.USE_TEST_STEP_NAME ) );
            } else if (dialog.getBooleanValue( Form.USE_MANUAL_FILENAME )) {
                testRequestStep.getTestRequestStepInExternalFileSupport().setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.MANUAL);
                wsdlRequestConfig.setExternalFilenameBuildMode(ExternalFilenameBuildModeConfig.MANUAL);
                if (wsdlRequestConfig.isSetComposeWithProjectName()) { wsdlRequestConfig.unsetComposeWithProjectName(); }
                if (wsdlRequestConfig.isSetComposeWithTestSuiteName()) { wsdlRequestConfig.unsetComposeWithTestSuiteName(); }
                if (wsdlRequestConfig.isSetComposeWithTestCaseName() ) { wsdlRequestConfig.unsetComposeWithTestCaseName(); }
                if (wsdlRequestConfig.isSetComposeWithTestStepName() ) { wsdlRequestConfig.unsetComposeWithTestStepName(); }
            }
            testRequestStep.getTestRequestStepInExternalFileSupport().setStepExternalFilePath( dialog.getValue( Form.FILENAME ) );
            wsdlRequestConfig.setExternalFilename( dialog.getValue( Form.FILENAME ) );
            testRequestStep.getTestRequestStepInExternalFileSupport().saveToExternalFile(testRequestStep.getTestRequestStepInExternalFileSupport().updateConfigWithExternalFilePath(), false);
        }


		return true;
	}

    private void setupFieldsStateAndVisibility(Boolean useExternalStepFile, Boolean useAutomaticFilename, Boolean useComposedFilname, Boolean useManualFilename) {
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

            dialog.getFormField( Form.ROOT_PATH ).setEnabled( false );
        } else {
            if ( useAutomaticFilename ) {
                dialog.getFormField( Form.USE_PROJECT_NAME ).setEnabled( false );
                dialog.getFormField( Form.USE_TEST_SUITE_NAME ).setEnabled( false );
                dialog.getFormField( Form.USE_TEST_CASE_NAME ).setEnabled( false );
                dialog.getFormField( Form.USE_TEST_STEP_NAME ).setEnabled( false );

                dialog.getFormField( Form.COMPOSED_FILENAME ).setEnabled( false );
                dialog.getFormField( Form.MANUAL_FILENAME ).setEnabled( false );
            }

            if ( useComposedFilname ) {
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

    private String buildManualFilename(WsdlTestRequest request) {
        String manualFilename = request.getTestStep().getTestRequestStepInExternalFileSupport().getStepExternalFilePath();
        if (manualFilename == null || manualFilename.isEmpty()) {
            manualFilename = request.getName() + "-request.xml";
        }
        return manualFilename;
    }

    private String buildAutomaticFilename(WsdlTestRequest request) {
        StringBuilder automaticFilenameBuilder = new StringBuilder();
        automaticFilenameBuilder.append(request.getTestCase().getTestSuite().getName());
        automaticFilenameBuilder.append("/").append(request.getTestCase().getName());
        automaticFilenameBuilder.append("/").append(request.getName()).append("-request.xml");
        return automaticFilenameBuilder.toString();
    }

    private void setupListeners() {
        dialog.getFormField( Form.USE_EXTERNAL_STEP_FILE ).addFormFieldListener( new XFormFieldListener()
        {
            public void valueChanged( XFormField sourceField, String newValue, String oldValue )
            {
                dialog.getFormField( Form.ROOT_PATH ).setEnabled( Boolean.valueOf( newValue ) );

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
            }
        });

        dialog.getFormField( Form.USE_PROJECT_NAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                composedFilenameObject.setUseProjectName(Boolean.getBoolean(newValue));
                String composedFilename = composedFilenameObject.build();
                dialog.setValue( Form.COMPOSED_FILENAME, composedFilename );
                dialog.setValue( Form.FILENAME, composedFilename );
            }
        });
        dialog.getFormField( Form.USE_TEST_SUITE_NAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                composedFilenameObject.setUseTestSuiteName(Boolean.getBoolean(newValue));
                String composedFilename = composedFilenameObject.build();
                dialog.setValue( Form.COMPOSED_FILENAME, composedFilename );
                dialog.setValue( Form.FILENAME, composedFilename );
            }
        });
        dialog.getFormField( Form.USE_TEST_CASE_NAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                composedFilenameObject.setUseTestCaseName(Boolean.getBoolean(newValue));
                String composedFilename = composedFilenameObject.build();
                dialog.setValue( Form.COMPOSED_FILENAME, composedFilename );
                dialog.setValue( Form.FILENAME, composedFilename );
            }
        });
        dialog.getFormField( Form.USE_TEST_STEP_NAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                composedFilenameObject.setUseTestStepName(Boolean.getBoolean(newValue));
                String composedFilename = composedFilenameObject.build();
                dialog.setValue( Form.COMPOSED_FILENAME, composedFilename );
                dialog.setValue( Form.FILENAME, composedFilename );
            }
        });
        dialog.getFormField( Form.AUTOMATIC_FILENAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                dialog.setValue( Form.FILENAME, newValue );
            }
        });
        dialog.getFormField( Form.COMPOSED_FILENAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                dialog.setValue( Form.FILENAME, newValue );
            }
        });
        dialog.getFormField( Form.MANUAL_FILENAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                dialog.setValue( Form.FILENAME, newValue );
            }
        });
    }



    @AForm( name = "Configure external file settings for step", description = "Specify which file to load, if content should be saved to external file and how to name the file.", helpUrl = HelpUrls.USE_EXT_FILE_FOR_STEP_HELP_URL)
	private interface Form
	{
        @AField( name = "Use External Step File", description = "Use an external file to store the request content of this step.", type = AFieldType.BOOLEAN )
        public final static String USE_EXTERNAL_STEP_FILE = "Use External Step File";

        @AField( name = "top-separator", description = "", type = AFieldType.SEPARATOR)
        public final static String SEPARATOR_AT_TOP = "top-separator";

        @AField( name = "Root Path", description = "", type = AFieldType.LABEL)
        public final static String ROOT_PATH_LABEL = "Root Path";
        @AField( name = "###Root Path", description = "Path from where to resolve relative file names.", type = AFieldType.FOLDER )
		public final static String ROOT_PATH = "###Root Path";

        @AField( name = "bottom-separator-1", description = "", type = AFieldType.SEPARATOR )
        public final static String SEPARATOR_AT_BOTTOM_1 = "bottom-separator-1";

        public final static String USE_AUTOMATIC_FILENAME_LABEL = "Use a filename automatically computed based on the path of step in project" ;
        public final static String USE_COMPOSED_FILENAME_LABEL = "Compose file with selected parts";
        public final static String USE_MANUAL_FILENAME_LABEL = "Use manually provided filename";

        @AField( name = "###" + USE_AUTOMATIC_FILENAME_LABEL, description = USE_AUTOMATIC_FILENAME_LABEL, type = AFieldType.BOOLEAN )
        public final static String USE_AUTOMATIC_FILENAME = "###" + USE_AUTOMATIC_FILENAME_LABEL;

        @AField( name = "Automatic filename computed", description = "", type = AFieldType.LABEL)
        public final static String AUTOMATIC_FILENAME_LABEL = "Automatic filename computed";
        @AField( name = "###result-of-expansion-value", description = "Filename where to read and save step content, automatically computed from the path of step in project.", type = AFieldType.STRING)
        public final static String AUTOMATIC_FILENAME = "###result-of-expansion-value";

        @AField( name = "bottom-separator-2", description = "", type = AFieldType.SEPARATOR )
        public final static String SEPARATOR_AT_BOTTOM_2 = "bottom-separator-2";

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

        @AField( name = "bottom-separator-3", description = "", type = AFieldType.SEPARATOR )
        public final static String SEPARATOR_AT_BOTTOM_3 = "bottom-separator-3";

        @AField( name = "###" + USE_MANUAL_FILENAME_LABEL, description = USE_MANUAL_FILENAME_LABEL, type = AFieldType.BOOLEAN )
        public final static String USE_MANUAL_FILENAME = "###" + USE_MANUAL_FILENAME_LABEL;

        // no label for this field because it feels redundant since the checkbox above clearly states what this is for
        @AField( name = "###manual-filename", description = "Explicit filename where to read and save step content.  Absolute path or relative to 'Root Path' above.", type = AFieldType.FILE)
		public final static String MANUAL_FILENAME = "###manual-filename";

        @AField( name = "###filename", description = "")
        public final static String FILENAME = "###filename";
	}

    private class ComposedFilenameObject {
        private Boolean useProjectName;
        private Boolean useTestSuiteName;
        private Boolean useTestCaseName;
        private Boolean useTestStepName;
        private WsdlRequestConfig wsdlRequestConfig;

        public ComposedFilenameObject(Boolean useProjectName, Boolean useTestSuiteName, Boolean useTestCaseName, Boolean useTestStepName, WsdlRequestConfig wsdlRequestConfig) {
            this.useProjectName = useProjectName;
            this.useTestSuiteName = useTestSuiteName;
            this.useTestCaseName = useTestCaseName;
            this.useTestStepName = useTestStepName;
            this.wsdlRequestConfig = wsdlRequestConfig;
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
            if (wsdlRequestConfig.isSetComposeWithProjectName()) {
                useProjectName = wsdlRequestConfig.getComposeWithProjectName();
            }
            if (wsdlRequestConfig.isSetComposeWithTestSuiteName()) {
                useTestSuiteName = wsdlRequestConfig.getComposeWithTestSuiteName();
            }
            if (wsdlRequestConfig.isSetComposeWithTestCaseName()) {
                useTestCaseName = wsdlRequestConfig.getComposeWithTestCaseName();
            }
            if (wsdlRequestConfig.isSetComposeWithTestStepName()) {
                useTestStepName = wsdlRequestConfig.getComposeWithTestStepName();
            }
            return this;
        }

        /**
         * build composed filename value from request according to our current selection
         * @param request
         * @return the string resulting of the composition of request name parts controlled by which of our use* flags are set.
         */
        public String build( WsdlTestRequest request ) {
            StringBuilder stringBuilder = new StringBuilder();

            if (this.getUseProjectName()) {
                stringBuilder.append("/").append( request.getTestCase().getTestSuite().getProject().getName() );
            }
            if (this.getUseTestSuiteName()) {
                stringBuilder.append("/").append( request.getTestCase().getTestSuite().getName() );
            }
            if (this.getUseTestCaseName()) {
                stringBuilder.append("/").append( request.getTestCase().getName() );
            }
            if (this.getUseTestStepName()) {
                stringBuilder.append("/").append( request.getTestStep().getName() );
            }
            return finish(stringBuilder);
        }

        /**
         * build composed filename value from dialog according to our current selection
         * @return the string resulting of the composition of dialog field values controlled by which of our use* flags are set.
         */
        public String build() {
            StringBuilder stringBuilder = new StringBuilder();

            if (dialog.getBooleanValue( Form.USE_PROJECT_NAME )) {
                stringBuilder.append("/").append( dialog.getValue(Form.PROJECT_NAME) );
            }
            if (dialog.getBooleanValue( Form.USE_TEST_SUITE_NAME )) {
                stringBuilder.append("/").append( dialog.getValue( Form.TEST_SUITE_NAME ) );
            }
            if (dialog.getBooleanValue( Form.USE_TEST_CASE_NAME )) {
                stringBuilder.append("/").append(  dialog.getValue( Form.TEST_CASE_NAME ) );
            }
            if (dialog.getBooleanValue( Form.USE_TEST_STEP_NAME )) {
                stringBuilder.append("/").append(  dialog.getValue( Form.TEST_STEP_NAME ) );
            }
            return finish(stringBuilder);
        }

        private String finish(StringBuilder stringBuilder) {
            stringBuilder.append("-request.xml");

            String composedFilename = null;
            if (stringBuilder.indexOf("/") == 0) {
                composedFilename = stringBuilder.substring(1).toString();
            } else {
                composedFilename = stringBuilder.toString();
            }
            if (composedFilename.startsWith("-request.xml")) {
                composedFilename = "new" + composedFilename;
            }
            return composedFilename;
        }
    }
}
