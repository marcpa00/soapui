/*
 *  SoapUI, copyright (C) 2004-2012 smartbear.com
 *
 *  SoapUI is free software; you can redistribute it and/or modify it under the
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  SoapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */

package com.eviware.soapui.impl.wsdl.actions.project;

import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.model.project.SaveStatus;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.action.support.AbstractSoapUIAction;

import java.io.File;
import java.io.IOException;

/**
 * Prompts to Save a WsdlProject to a new file, including content usually stored in external files (step content
 * when CONTENT_IN_EXTERNAL_FILE is activated), regardless of the ALSO_KEEP_IN_PROJECT_WHEN_CONTENT_IN_EXTERNAL_FILE setting.
 * This action is used to save a XML document of project that can be consumed by a version of soapui where the
 * CONTENT_IN_EXTERNAL_FILE is not present, i.e. it saves the project file in backward compatible mode.
 *
 * @author Marc Paquette
 */

public class SaveProjectAsIncludingContentAction extends AbstractSoapUIAction<WsdlProject> {
	public static final String SOAPUI_ACTION_ID = "SaveProjectAsIncludingContentAction";

	public SaveProjectAsIncludingContentAction() {
		super("Save Project As (with step content)", "Saves this project to a new file");
	}

	public void perform(WsdlProject project, Object param) {
		try {
			String path = project.getPath();
			if(path == null) {
				project.save(null, true);
			} else {
				File file = UISupport.getFileDialogs().saveAs(this, "Select soapui project file", "xml", "XML",
						new File(path));
				if(file == null) {
					return;
				}

				String fileName = file.getAbsolutePath();
				if(fileName == null) {
					return;
				}

				if(project.saveAs(fileName, true) == SaveStatus.SUCCESS) {
					project.getWorkspace().save(true);
				}
			}
		} catch(IOException e1) {
			UISupport.showErrorMessage("Failed to save project; " + e1);
		}
	}
}
