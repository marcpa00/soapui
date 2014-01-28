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
import com.eviware.soapui.impl.support.ContentInExternalFileSupport;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlGroovyScriptTestStep;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestRequestStep;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestStep;
import com.eviware.soapui.impl.wsdl.teststeps.assertions.basic.GroovyScriptAssertion;
import com.eviware.soapui.model.ModelItem;
import com.eviware.soapui.model.support.AbstractModelItem;
import com.eviware.soapui.support.action.support.AbstractSoapUIAction;

public class ReloadExternalFileAction extends AbstractSoapUIAction<ModelItem> {
	public static final String SOAPUI_ACTION_ID = "ReloadExternalFileAction";
	private ContentInExternalFileSupport contentInExternalFileSupport = null;

	public ReloadExternalFileAction() {
		super("Reload external file for step", "Synchronize content with external file");
	}

	public void perform(ModelItem modelItem, Object param) {
		WsdlGroovyScriptTestStep wsdlGroovyScriptTestStep = null;
		WsdlTestRequestStep wsdlTestRequestStep = null;
		GroovyScriptAssertion groovyScriptAssertion = null;
		if(modelItem instanceof ContentInExternalFileSupport) {
			contentInExternalFileSupport = (ContentInExternalFileSupport)modelItem;
		} else if(modelItem instanceof WsdlGroovyScriptTestStep) {
			wsdlGroovyScriptTestStep = (WsdlGroovyScriptTestStep)modelItem;
			contentInExternalFileSupport = wsdlGroovyScriptTestStep.getContentInExternalFileSupport();
		} else if(modelItem instanceof WsdlTestRequestStep) {
			wsdlTestRequestStep = (WsdlTestRequestStep)modelItem;
			contentInExternalFileSupport = wsdlTestRequestStep.getContentInExternalFileSupport();
		} else if(modelItem instanceof GroovyScriptAssertion) {
			groovyScriptAssertion = (GroovyScriptAssertion)modelItem;
			contentInExternalFileSupport = groovyScriptAssertion.getContentInExternalFileSupport();
		}
		if(contentInExternalFileSupport != null) {
			contentInExternalFileSupport.loadContent();
			contentInExternalFileSupport.updateScript();
			contentInExternalFileSupport.updateConfig();
			SoapUI.log.debug("ReloadExternalFileAction.perform() : external file reloaded.");
		}
	}
}
