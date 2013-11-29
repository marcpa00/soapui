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
import com.eviware.soapui.impl.wsdl.teststeps.TestRequestStepInExternalFileSupport;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlGroovyScriptTestStep;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestRequestStep;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestStep;
import com.eviware.soapui.support.action.support.AbstractSoapUIAction;

public class ReloadExternalFileAction extends AbstractSoapUIAction<WsdlTestStep>
{
	public static final String SOAPUI_ACTION_ID = "ReloadExternalFileAction";
    private TestRequestStepInExternalFileSupport testRequestStepInExternalFileSupport = null;

    public ReloadExternalFileAction()
	{
		super( "Reload external file for step", "Synchronize content with external file" );
	}

	public void perform( WsdlTestStep wsdlTestStep, Object param )
	{

        WsdlGroovyScriptTestStep wsdlGroovyScriptTestStep = null;
        WsdlTestRequestStep wsdlTestRequestStep = null;
        if (wsdlTestStep instanceof WsdlGroovyScriptTestStep) {
            wsdlGroovyScriptTestStep = (WsdlGroovyScriptTestStep) wsdlTestStep;
            testRequestStepInExternalFileSupport = wsdlGroovyScriptTestStep.getTestRequestStepInExternalFileSupport();
        } else if (wsdlTestStep instanceof WsdlTestRequestStep) {
            wsdlTestRequestStep = (WsdlTestRequestStep) wsdlTestStep;
            testRequestStepInExternalFileSupport = wsdlTestRequestStep.getTestRequestStepInExternalFileSupport();
        }
        if (testRequestStepInExternalFileSupport != null && reloadExternalFile()) {
            if (wsdlGroovyScriptTestStep != null) {
                wsdlGroovyScriptTestStep.setScript(testRequestStepInExternalFileSupport.getStepContent());
            }
            if (wsdlTestRequestStep != null) {
                wsdlTestRequestStep.getTestRequest().setRequestContent(testRequestStepInExternalFileSupport.getRequestContent());
            }
        }
	}

	protected boolean reloadExternalFile()
	{
        String currentContent = testRequestStepInExternalFileSupport.getStepContent();
        if (testRequestStepInExternalFileSupport.maybeReloadStepContent()) {
            if (! currentContent.equals(testRequestStepInExternalFileSupport.getStepContent())) {
                testRequestStepInExternalFileSupport.updateConfig();
                SoapUI.log.debug("reloadExternalFile() : external file reloaded.");
                return true;
            } else {
                SoapUI.log.debug("reloadExternalFile() : reloaded but content did not changed.");
                return false;
            }
        } else {
            SoapUI.log.debug("reloadExternalFile() : external file not reloaded.");
            return false;
        }
	}
}
