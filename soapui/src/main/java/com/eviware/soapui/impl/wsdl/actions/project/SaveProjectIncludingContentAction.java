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
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.action.support.AbstractSoapUIAction;

import java.io.IOException;

/**
 * Saves a WsdlProject, including content usually stored in external files (step content when CONTENT_IN_EXTERNAL_FILE
 * is activated), regardless of the ALSO_KEEP_IN_PROJECT_WHEN_CONTENT_IN_EXTERNAL_FILE setting.
 * This action is used to save a XML document of project that can be consumed by a version of soapui where the
 * CONTENT_IN_EXTERNAL_FILE is not present, i.e. it saves the project file in backward compatible mode.
 *
 * @author Marc Paquette
 */

public class SaveProjectIncludingContentAction extends AbstractSoapUIAction<WsdlProject> {
    public static final String SOAPUI_ACTION_ID = "SaveProjectIncludingContentAction";

    public SaveProjectIncludingContentAction() {
        super("Save Project (with step content)", "Saves this project");
    }

    public void perform(WsdlProject project, Object param) {
        try {
            if (StringUtils.hasContent(project.getPath()) || project.getWorkspace() == null) {
                project.save(null, true);
            } else {
                project.save(project.getWorkspace().getProjectRoot(), true);
            }
        } catch (IOException e1) {
            UISupport.showErrorMessage("Failed to save project; " + e1);
        }
    }
}
