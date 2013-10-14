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
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.WsdlRequest;
import com.eviware.soapui.impl.wsdl.support.HelpUrls;
import com.eviware.soapui.support.action.support.AbstractSoapUIAction;
import com.eviware.soapui.ui.desktop.SoapUIDesktop;
import com.eviware.x.form.XFormDialog;
import com.eviware.x.form.XFormField;
import com.eviware.x.form.XFormFieldListener;
import com.eviware.x.form.support.ADialogBuilder;
import com.eviware.x.form.support.AField;
import com.eviware.x.form.support.AField.AFieldType;
import com.eviware.x.form.support.AForm;

import java.io.File;

public class ConfigureExternalFileAction extends AbstractSoapUIAction<WsdlRequest>
{
	public static final String SOAPUI_ACTION_ID = "ConfigureExternalFileAction";
	private XFormDialog dialog;

	public ConfigureExternalFileAction()
	{
		super( "Configure external file settings for step", "Specify which file to load, if content should be saved to external file and how to name the file." );
	}

	public void perform( WsdlRequest request, Object param )
	{
        configureExternalFile(request);
	}

	protected boolean configureExternalFile( WsdlRequest request )
	{
		String title = getName();
		boolean create = false;

		if( dialog == null ) {
			dialog = ADialogBuilder.buildDialog( Form.class );
        }

        dialog.setBooleanValue(Form.USE_EXTERNAL_STEP_FILE, false);

		dialog.setValue( Form.LOAD_FROM_FILE, null );

        dialog.setBooleanValue(Form.USE_AUTOMATIC_FILENAME, true);
        dialog.setValue(Form.AUTOMATIC_FILENAME, "${#Project#name}/${#TestSuite#name/${#TestCase#name}/" + request.getName() + "-request.xml");

        dialog.setValue(Form.ROOT_PATH, new File(((WsdlProject) request.getParent().getParent().getParent().getParent()).getPath()).getParent());
        dialog.setValue(Form.CUSTOM_FILENAME, request.getName() + "-request.xml");

        dialog.getFormField( Form.USE_EXTERNAL_STEP_FILE ).addFormFieldListener( new XFormFieldListener()
        {
            public void valueChanged( XFormField sourceField, String newValue, String oldValue )
            {
                String fieldNames[] = { Form.LOAD_FROM_FILE, Form.USE_AUTOMATIC_FILENAME, Form.ROOT_PATH };
                for (String fieldName : fieldNames) {
                    dialog.getFormField( fieldName ).setEnabled( Boolean.valueOf( newValue ) );
                }
                if ( Boolean.valueOf( newValue ) == false ) {
                    dialog.getFormField( Form.CUSTOM_FILENAME ).setEnabled( false );
                    dialog.getFormField( Form.AUTOMATIC_FILENAME ).setEnabled( false );
                } else {
                    dialog.getFormField( Form.CUSTOM_FILENAME ).setEnabled( ! Boolean.valueOf( dialog.getBooleanValue( Form.USE_AUTOMATIC_FILENAME ) ));
                    dialog.getFormField( Form.AUTOMATIC_FILENAME ).setEnabled( Boolean.valueOf( dialog.getBooleanValue( Form.USE_AUTOMATIC_FILENAME ) ));
                }

            }
        } );

        dialog.getFormField( Form.USE_AUTOMATIC_FILENAME ).addFormFieldListener( new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField sourceField, String newValue, String oldValue) {
                if ( dialog.getBooleanValue( Form.USE_EXTERNAL_STEP_FILE ) ) {
                    dialog.getFormField( Form.CUSTOM_FILENAME ).setEnabled( ! Boolean.valueOf(newValue ));
                    dialog.getFormField( Form.AUTOMATIC_FILENAME ).setEnabled( Boolean.valueOf(newValue ));
                }
            }
        });

        // by default, fields are enabled
        if (! dialog.getBooleanValue( Form.USE_EXTERNAL_STEP_FILE ) ) {
            // fields disabled because USE_EXTERNAL_STEP_FILE is false
            dialog.getFormField( Form.LOAD_FROM_FILE).setEnabled( false );
            dialog.getFormField( Form.USE_AUTOMATIC_FILENAME ).setEnabled( false );
            dialog.getFormField( Form.AUTOMATIC_FILENAME ).setEnabled( false );
            dialog.getFormField( Form.ROOT_PATH ).setEnabled( false );
            dialog.getFormField( Form.CUSTOM_FILENAME ).setEnabled( false );
        } else if ( dialog.getBooleanValue( Form.USE_AUTOMATIC_FILENAME ) ) {
            // fields to disable when automatic filename is enabled
            dialog.getFormField( Form.CUSTOM_FILENAME ).setEnabled( false );
        } else {
            // fields to disable when automatic filename is not enabled
            dialog.getFormField( Form.AUTOMATIC_FILENAME ).setEnabled( false );
        }

		SoapUIDesktop desktop = SoapUI.getDesktop();

		if( !dialog.show() )
			return false;

        WsdlRequestConfig config = request.getConfig();

        SoapUI.log.info( Form.USE_EXTERNAL_STEP_FILE + " is : " + dialog.getFormField( Form.USE_AUTOMATIC_FILENAME ).getValue() );
        SoapUI.log.info( Form.LOAD_FROM_FILE + " is : " + dialog.getFormField( Form.LOAD_FROM_FILE).getValue() );
        SoapUI.log.info( Form.USE_AUTOMATIC_FILENAME + " is : " + dialog.getFormField( Form.USE_AUTOMATIC_FILENAME ).getValue() );
        SoapUI.log.info( Form.AUTOMATIC_FILENAME + " is : " + dialog.getFormField( Form.AUTOMATIC_FILENAME ).getValue() );
        SoapUI.log.info( Form.ROOT_PATH + " is : " + dialog.getFormField( Form.ROOT_PATH ).getValue() );
        SoapUI.log.info( Form.CUSTOM_FILENAME + " is : " + dialog.getFormField( Form.CUSTOM_FILENAME ).getValue() );

/*
		config.set( dialog.getValue( Form.STEP_NAME ) );
		mockResponseStepConfig.setPath( dialog.getValue( Form.PATH ) );
		mockResponseStepConfig.setPort( dialog.getIntValue( Form.PORT, 8181 ) );
		CompressedStringConfig responseContent = mockResponseStepConfig.getResponse().getResponseContent();

		if( request.getResponse() == null && !request.getOperation().isOneWay() )
		{
			create = UISupport.confirm( "Request is missing response, create default mock response instead?", title );
		}

		if( create )
		{
			String response = operation.createResponse( operation.getSettings().getBoolean(
					WsdlSettings.XML_GENERATION_ALWAYS_INCLUDE_OPTIONAL_ELEMENTS ) );
			CompressedStringSupport.setString( responseContent, response );
		}
		else if( request.getResponse() != null )
		{
			String response = request.getResponse().getContentAsString();
			CompressedStringSupport.setString( responseContent, response );
		}

		WsdlMockResponseTestStep testStep = ( WsdlMockResponseTestStep )testCase.addTestStep( config );

		if( dialog.getBooleanValue( Form.ADD_SCHEMA_ASSERTION ) )
			testStep.addAssertion( SchemaComplianceAssertion.ID );

		UISupport.selectAndShow( testStep );

		if( dialog.getBooleanValue( Form.CLOSE_REQUEST ) && desktop != null )
		{
			desktop.closeDesktopPanel( request );
		}

		if( dialog.getBooleanValue( Form.SHOW_TESTCASE ) )
		{
			UISupport.selectAndShow( testCase );
		}
*/
		return true;
	}

	@AForm( name = "Configure external file settings for step", description = "Specify which file to load, if content should be saved to external file and how to name the file.", helpUrl = HelpUrls.USE_EXT_FILE_FOR_STEP_HELP_URL)
	private interface Form
	{
        @AField( name = "Use External Step File", description = "Use an external file to store the request content of this step.", type = AFieldType.BOOLEAN )
        public final static String USE_EXTERNAL_STEP_FILE = "Use External Step File";

		@AField( name = "Load From", description = "Browse and choose a file from which step content wil be read.", type = AFieldType.FILE )
		public final static String LOAD_FROM_FILE = "Load From";

		@AField( name = "Root Path", description = "Path from where to resolve relative file names.", type = AFieldType.FOLDER )
		public final static String ROOT_PATH = "Root Path";

		@AField( name = "File to use (manual)", description = "Explicit filename where to read and save step content.")
		public final static String CUSTOM_FILENAME = "File to use (manual)";

        @AField( name = "File to use (automatic)", description = "Filename where to read and save step content, automatically computed from the path of step in project.")
        public final static String AUTOMATIC_FILENAME = "File to use (automatic)";

		@AField( name = "Use Automatic Filename", description = "Use a filename automatically computed based on the path of step in project.", type = AFieldType.BOOLEAN )
		public final static String USE_AUTOMATIC_FILENAME = "Use Automatic Filename";


	}
}
