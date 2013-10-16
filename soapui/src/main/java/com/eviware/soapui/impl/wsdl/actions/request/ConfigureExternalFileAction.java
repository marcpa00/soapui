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
import com.eviware.soapui.config.WsdlRequestConfig;
import com.eviware.soapui.impl.wsdl.support.HelpUrls;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestRequest;
import com.eviware.soapui.support.action.support.AbstractSoapUIAction;
import com.eviware.soapui.ui.desktop.SoapUIDesktop;
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
		String title = getName();
		boolean create = false;


        String rootPath = request.getTestCase().getTestSuite().getProject().getPath();
        if (rootPath != null && rootPath.endsWith(".xml")) {
            rootPath = rootPath.replaceAll(".xml", ".resources");
        } else {
            rootPath = rootPath + ".resources";
        }

        if( dialog == null ) {
			dialog = ADialogBuilder.buildDialog( Form.class );
        }

        dialog.setBooleanValue(Form.USE_EXTERNAL_STEP_FILE, false);

        dialog.setValue( Form.ROOT_PATH, rootPath );

        dialog.setBooleanValue( Form.USE_PROJECT_NAME, true );
        dialog.setBooleanValue( Form.USE_TEST_SUITE_NAME, true );
        dialog.setBooleanValue( Form.USE_TEST_CASE_NAME, true );
        dialog.setBooleanValue( Form.USE_TEST_STEP_NAME, true );

        // Do not use the project name because by default it is part of root path
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(request.getTestCase().getTestSuite().getName());
        stringBuilder.append("/").append(request.getTestCase().getName());
        stringBuilder.append("/").append(request.getName()).append("-request.xml");
        String automaticFilename = stringBuilder.toString();

        String projectName = request.getTestCase().getTestSuite().getProject().getName();
        String testSuiteName = request.getTestCase().getTestSuite().getName();
        String testCaseName = request.getTestCase().getName();
        String testStepName = request.getName();

        dialog.setValue( Form.PROJECT_NAME, projectName );
        dialog.setValue( Form.TEST_SUITE_NAME, testSuiteName );
        dialog.setValue( Form.TEST_CASE_NAME, testCaseName );
        dialog.setValue( Form.TEST_STEP_NAME, testStepName );
        String composedFilename = recomputeComposedFilename();
        dialog.setValue( Form.AUTOMATIC_FILENAME, automaticFilename );
        dialog.setValue( Form.COMPOSED_FILENAME, composedFilename);
        dialog.setValue(Form.MANUAL_FILENAME, request.getName() + "-request.xml");

        // by default, fields are enabled : need to disable them according to flag USE_EXTERNAL_STEP_FILE
        if (dialog.getBooleanValue(Form.USE_EXTERNAL_STEP_FILE) == Boolean.FALSE) {
            // disable fields because USE_EXTERNAL_STEP_FILE is false

            dialog.getFormField( Form.USE_AUTOMATIC_FILENAME_SECTION ).setValue( "true" );
            dialog.getFormField( Form.USE_AUTOMATIC_FILENAME_SECTION ).setEnabled( false );
            dialog.getFormField( Form.USE_COMPOSED_FILENAME_SECTION ).setEnabled( false );
            dialog.getFormField( Form.USE_MANUAL_FILENAME_SECTION ).setEnabled( false );

            dialog.getFormField( Form.AUTOMATIC_FILENAME ).setEnabled( false );
            dialog.getFormField( Form.USE_PROJECT_NAME ).setEnabled( false );
            dialog.getFormField( Form.USE_TEST_SUITE_NAME ).setEnabled( false );
            dialog.getFormField( Form.USE_TEST_CASE_NAME ).setEnabled( false );
            dialog.getFormField( Form.USE_TEST_STEP_NAME ).setEnabled( false );
            dialog.getFormField( Form.COMPOSED_FILENAME ).setEnabled( false );
            dialog.getFormField( Form.MANUAL_FILENAME ).setEnabled( false );
            dialog.getFormField( Form.ROOT_PATH ).setEnabled( false );
        } else {
            if (! dialog.getBooleanValue( Form.USE_AUTOMATIC_FILENAME_SECTION ) && ! dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME_SECTION ) && ! dialog.getBooleanValue( Form.USE_MANUAL_FILENAME_SECTION ) ) {
                dialog.setBooleanValue( Form.USE_AUTOMATIC_FILENAME_SECTION, true );
            }
            if (dialog.getBooleanValue( Form.USE_AUTOMATIC_FILENAME_SECTION) ) {
                dialog.getFormField( Form.USE_PROJECT_NAME ).setEnabled( true );
                dialog.getFormField( Form.USE_TEST_SUITE_NAME ).setEnabled( true );
                dialog.getFormField( Form.USE_TEST_CASE_NAME ).setEnabled( true );
                dialog.getFormField( Form.USE_TEST_STEP_NAME ).setEnabled( true );
            }

            if (dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME_SECTION ) ) {
                dialog.getFormField( Form.MANUAL_FILENAME).setEnabled( true );
            }
            if (dialog.getBooleanValue( Form.USE_MANUAL_FILENAME_SECTION ) ) {
                dialog.getFormField( Form.AUTOMATIC_FILENAME ).setEnabled( true );
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

        String invisibleFormFields[] = { Form.PROJECT_NAME, Form.TEST_SUITE_NAME, Form.TEST_CASE_NAME, Form.TEST_STEP_NAME };
        for (String invisibleFormField : Arrays.asList(invisibleFormFields)) {
            ((AbstractSwingXFormField)dialog.getFormField( invisibleFormField )).getComponent().setVisible(false);
        }

        dialog.getFormField( Form.USE_EXTERNAL_STEP_FILE ).addFormFieldListener( new XFormFieldListener()
        {
            public void valueChanged( XFormField sourceField, String newValue, String oldValue )
            {
                dialog.getFormField( Form.ROOT_PATH ).setEnabled( Boolean.valueOf( newValue ) );

                dialog.getFormField( Form.USE_AUTOMATIC_FILENAME_SECTION ).setEnabled( Boolean.valueOf(newValue));
                dialog.getFormField( Form.USE_COMPOSED_FILENAME_SECTION ).setEnabled( Boolean.valueOf(newValue));
                dialog.getFormField( Form.USE_MANUAL_FILENAME_SECTION ).setEnabled( Boolean.valueOf(newValue));

                if ( Boolean.valueOf( newValue ) == false ) {
                    dialog.getFormField( Form.USE_PROJECT_NAME ).setEnabled( false );
                    dialog.getFormField( Form.USE_TEST_SUITE_NAME ).setEnabled( false );
                    dialog.getFormField( Form.USE_TEST_CASE_NAME ).setEnabled( false );
                    dialog.getFormField( Form.USE_TEST_STEP_NAME ).setEnabled( false );
                    dialog.getFormField( Form.MANUAL_FILENAME ).setEnabled( false );
                    dialog.getFormField( Form.COMPOSED_FILENAME ).setEnabled( false );
                    dialog.getFormField( Form.AUTOMATIC_FILENAME ).setEnabled( false );
                } else {

                    dialog.getFormField( Form.AUTOMATIC_FILENAME ).setEnabled( dialog.getBooleanValue( Form.USE_AUTOMATIC_FILENAME_SECTION) );

                    dialog.getFormField( Form.USE_PROJECT_NAME ).setEnabled( dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME_SECTION ) );
                    dialog.getFormField( Form.USE_TEST_SUITE_NAME ).setEnabled( dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME_SECTION ) );
                    dialog.getFormField( Form.USE_TEST_CASE_NAME ).setEnabled( dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME_SECTION ) );
                    dialog.getFormField( Form.USE_TEST_STEP_NAME ).setEnabled( dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME_SECTION ) );
                    dialog.getFormField( Form.COMPOSED_FILENAME ).setEnabled( dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME_SECTION ) );

                    dialog.getFormField( Form.MANUAL_FILENAME).setEnabled( dialog.getBooleanValue( Form.USE_MANUAL_FILENAME_SECTION ) ) ;
                }
            }
        } );

        dialog.getFormField( Form.USE_AUTOMATIC_FILENAME_SECTION ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                if ( Boolean.valueOf(newValue) == true && sourceField.equals( dialog.getFormField( Form.USE_AUTOMATIC_FILENAME_SECTION ))) {
                    if (dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME_SECTION )) {
                        dialog.setBooleanValue( Form.USE_COMPOSED_FILENAME_SECTION, false );
                    }
                    if (dialog.getBooleanValue( Form.USE_MANUAL_FILENAME_SECTION )) {
                        dialog.setBooleanValue( Form.USE_MANUAL_FILENAME_SECTION, false );
                    }
                } else if ( Boolean.valueOf( newValue ) == false && sourceField.equals(dialog.getFormField( Form.USE_AUTOMATIC_FILENAME_SECTION ))) {
                    if (dialog.getBooleanValue(Form.USE_COMPOSED_FILENAME_SECTION) == false && dialog.getBooleanValue(Form.USE_MANUAL_FILENAME_SECTION) == false) {
                        dialog.setBooleanValue( Form.USE_AUTOMATIC_FILENAME_SECTION, true );
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
            }
        });

        dialog.getFormField( Form.USE_COMPOSED_FILENAME_SECTION ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                if ( Boolean.valueOf(newValue) == true && sourceField.equals( dialog.getFormField( Form.USE_COMPOSED_FILENAME_SECTION ))) {
                    if (dialog.getBooleanValue( Form.USE_AUTOMATIC_FILENAME_SECTION )) {
                        dialog.setBooleanValue( Form.USE_AUTOMATIC_FILENAME_SECTION, false );
                    }
                    if (dialog.getBooleanValue( Form.USE_MANUAL_FILENAME_SECTION )) {
                        dialog.setBooleanValue( Form.USE_MANUAL_FILENAME_SECTION, false );
                    }
                } else if ( Boolean.valueOf( newValue ) == false  && sourceField.equals(dialog.getFormField( Form.USE_COMPOSED_FILENAME_SECTION ))) {
                    if (dialog.getBooleanValue(Form.USE_AUTOMATIC_FILENAME_SECTION) == false && dialog.getBooleanValue(Form.USE_MANUAL_FILENAME_SECTION) == false) {
                        dialog.setBooleanValue( Form.USE_COMPOSED_FILENAME_SECTION, true );
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
            }
        });

        dialog.getFormField( Form.USE_MANUAL_FILENAME_SECTION ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                if ( Boolean.valueOf(newValue) == true && sourceField.equals( dialog.getFormField( Form.USE_MANUAL_FILENAME_SECTION ))) {
                    if (dialog.getBooleanValue( Form.USE_AUTOMATIC_FILENAME_SECTION )) {
                        dialog.setBooleanValue( Form.USE_AUTOMATIC_FILENAME_SECTION, false );
                    }
                    if (dialog.getBooleanValue( Form.USE_COMPOSED_FILENAME_SECTION )) {
                        dialog.setBooleanValue( Form.USE_COMPOSED_FILENAME_SECTION, false );
                    }
                } else if ( Boolean.valueOf( newValue ) == false  && sourceField.equals(dialog.getFormField( Form.USE_MANUAL_FILENAME_SECTION ))) {
                    if (dialog.getBooleanValue(Form.USE_AUTOMATIC_FILENAME_SECTION) == false && dialog.getBooleanValue(Form.USE_COMPOSED_FILENAME_SECTION) == false) {
                        dialog.setBooleanValue( Form.USE_MANUAL_FILENAME_SECTION, true );
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
            }
        });

        dialog.getFormField( Form.USE_PROJECT_NAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                dialog.setValue( Form.COMPOSED_FILENAME, recomputeComposedFilename());
            }
        });
        dialog.getFormField( Form.USE_TEST_SUITE_NAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                dialog.setValue( Form.COMPOSED_FILENAME, recomputeComposedFilename());
            }
        });
        dialog.getFormField( Form.USE_TEST_CASE_NAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                dialog.setValue( Form.COMPOSED_FILENAME, recomputeComposedFilename());
            }
        });
        dialog.getFormField( Form.USE_TEST_STEP_NAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                dialog.setValue( Form.COMPOSED_FILENAME, recomputeComposedFilename());
            }
        });

		SoapUIDesktop desktop = SoapUI.getDesktop();

		if( !dialog.show() )
			return false;

        WsdlRequestConfig config = request.getConfig();

        // TODO (marcpa) set the config with selected options
		return true;
	}

    private String recomputeComposedFilename() {
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


    @AForm( name = "Configure external file settings for step", description = "Specify which file to load, if content should be saved to external file and how to name the file.", helpUrl = HelpUrls.USE_EXT_FILE_FOR_STEP_HELP_URL)
	private interface Form
	{
        @AField( name = "Use External Step File", description = "Use an external file to store the request content of this step.", type = AFieldType.BOOLEAN )
        public final static String USE_EXTERNAL_STEP_FILE = "Use External Step File";

        @AField( name = "top-separator", description = "", type = AFieldType.SEPARATOR)
        public final static String SEPARATOR_AT_TOP = "top-separator";

        @AField( name = "Root Path", description = "Path from where to resolve relative file names.", type = AFieldType.FOLDER )
		public final static String ROOT_PATH = "Root Path";

        @AField( name = "bottom-separator-1", description = "", type = AFieldType.SEPARATOR )
        public final static String SEPARATOR_AT_BOTTOM_1 = "bottom-separator-1";

        public final static String USE_AUTOMATIC_FILENAME = "Use a filename automatically computed based on the path of step in project" ;
        public final static String USE_COMPOSED_FILENAME = "Compose file with selected parts";
        public final static String USE_MANUAL_FILENAME = "Use manually provided filename";

        @AField( name = "###" + USE_AUTOMATIC_FILENAME, description = USE_AUTOMATIC_FILENAME, type = AFieldType.BOOLEAN )
        public final static String USE_AUTOMATIC_FILENAME_SECTION = "###" + USE_AUTOMATIC_FILENAME;

        @AField( name = "Automatic filename computed", description = "", type = AFieldType.LABEL)
        public final static String AUTOMATIC_FILENAME_LABEL = "Automatic filename computed";
        @AField( name = "###result-of-expansion-value", description = "Filename where to read and save step content, automatically computed from the path of step in project.", type = AFieldType.STRING)
        public final static String AUTOMATIC_FILENAME = "###result-of-expansion-value";

        @AField( name = "bottom-separator-2", description = "", type = AFieldType.SEPARATOR )
        public final static String SEPARATOR_AT_BOTTOM_2 = "bottom-separator-2";

        @AField( name = "###" + USE_COMPOSED_FILENAME, description = USE_COMPOSED_FILENAME, type = AFieldType.BOOLEAN )
        public final static String USE_COMPOSED_FILENAME_SECTION = "###" + USE_COMPOSED_FILENAME;

        @AField( name = "Use project name", description = "Use the project name containing this test step in the filename path.", type = AFieldType.BOOLEAN)
        public final static String USE_PROJECT_NAME = "Use project name";

        @AField( name = "###projectName", description = "")
        public final static String PROJECT_NAME = "###projectName";

        @AField( name = "Use test case name", description = "Use the test case name containing this test step in the filename path.", type = AFieldType.BOOLEAN)
        public final static String USE_TEST_CASE_NAME = "Use test case name";
        @AField( name = "###testCaseName", description = "")
        public final static String TEST_CASE_NAME = "###testCaseName";

        @AField( name = "Use test suite name", description = "Use the test suite name containing this test step in the filename path.", type = AFieldType.BOOLEAN)
        public final static String USE_TEST_SUITE_NAME = "Use test suite name";
        @AField( name = "###testSuiteName", description = "")
        public final static String TEST_SUITE_NAME = "###testSuiteName";

        @AField( name = "Use test step name", description = "Use the test step name as the last portion of filename path.", type = AFieldType.BOOLEAN)
        public final static String USE_TEST_STEP_NAME = "Use test step name";
        @AField( name = "###testStepName", description = "")
        public final static String TEST_STEP_NAME = "###testStepName";


        @AField( name = "Composed filename computed", description = "", type = AFieldType.LABEL)
        public final static String COMPOSED_FILENAME_LABEL = "Composed filename computed";
        @AField( name = "###result-of-composition-value", description = "Filename where to read and save step content, automatically computed from the path of step in project.", type = AFieldType.STRING)
        public final static String COMPOSED_FILENAME = "###result-of-composition-value";

        @AField( name = "bottom-separator-3", description = "", type = AFieldType.SEPARATOR )
        public final static String SEPARATOR_AT_BOTTOM_3 = "bottom-separator-3";

        @AField( name = "###" + USE_MANUAL_FILENAME, description = USE_MANUAL_FILENAME, type = AFieldType.BOOLEAN )
        public final static String USE_MANUAL_FILENAME_SECTION = "###" + USE_MANUAL_FILENAME;

        @AField( name = "Manually provided filename", description = "", type = AFieldType.LABEL)
        public final static String MANUAL_FILENAME_LABEL = "Manually provided filename";
        @AField( name = "###manual-filename", description = "Explicit filename where to read and save step content.")
		public final static String MANUAL_FILENAME = "###manual-filename";

	}
}
