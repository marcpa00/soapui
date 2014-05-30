/*
 * Copyright 2004-2014 SmartBear Software
 *
 * Licensed under the EUPL, Version 1.1 or - as soon as they will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the Licence for the specific language governing permissions and limitations
 * under the Licence.
*/

package com.eviware.soapui.tools;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.loadtest.WsdlLoadTest;
import com.eviware.soapui.impl.wsdl.loadtest.data.actions.ExportLoadTestLogAction;
import com.eviware.soapui.impl.wsdl.loadtest.data.actions.ExportStatisticsAction;
import com.eviware.soapui.impl.wsdl.loadtest.log.LoadTestLog;
import com.eviware.soapui.impl.wsdl.loadtest.log.LoadTestLogEntry;
import com.eviware.soapui.model.project.ProjectFactoryRegistry;
import com.eviware.soapui.model.project.SaveStatus;
import com.eviware.soapui.model.testsuite.*;
import com.eviware.soapui.model.testsuite.TestRunner.Status;
import com.eviware.soapui.settings.UISettings;
import com.eviware.soapui.support.SoapUIException;
import com.eviware.soapui.support.StringUtils;
import org.apache.commons.cli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone runner for converting a project file from an exploded form (i.e. content in external files)
 * to a <i>fat</i> format where everything is included in the XML document.
 * <h2>Options</h2>
 * <ul>
 *     <li>-o output-project-file : filename of output project, will contain result of transformation</li>
 * </ul>
 * <h2>Parameter</h2>
 * Input project file is the only required parameter.
 * <p>
 *     When -o is omitted, the output project filename will be the input project filename prefixed with 'fat-'.
 * </p>
 * <p>
 * Can be used from maven-plugin, can also be used from
 * command-line (see xdocs) or directly from other classes.
 * </p>
 * <p>
 * For standalone usage, set the project file (with setProjectFile) and other
 * desired properties before calling run
 * </p>
 *
 * @author Ole.Matzura
 * @author Marc Paquette
 */

public class SoapUIConvertToFatProjectRunner extends AbstractSoapUIRunner {

    private String outputProjectFile;

    public static String TITLE = "SoapUI " + SoapUI.SOAPUI_VERSION + " Project File Converter";

    /**
     * Runs the conversion for the specified soapUI project file, see SoapUI xdocs
     * for details.
     *
     * @param args
     * @throws Exception
     */

    public static void main(String[] args) {
        System.exit(new SoapUIConvertToFatProjectRunner().runFromCommandLine(args));
    }

    protected boolean processCommandLine(CommandLine cmd) {
        String message = "";

        if (cmd.hasOption("o")) {
            setOutputProjectFile(cmd.getOptionValue("o"));
        } else {
            setOutputProjectFile("fat-" + getProjectFile());
        }

        if (message.length() > 0) {
            log.error(message);
            return false;
        }

        return true;
    }

    protected SoapUIOptions initCommandLineOptions() {
        SoapUIOptions options = new SoapUIOptions("projectconverter");
        options.addOption("o", true, "Sets the output project file");

        return options;
    }

    public SoapUIConvertToFatProjectRunner() {
        this(TITLE);
    }

    public SoapUIConvertToFatProjectRunner(String title) {
        super(title);
    }

    public void setOutputProjectFile(String outputProjectFile) {
        this.outputProjectFile = outputProjectFile;
    }

    /**
     * Convert input project to *fat* format.
     *
     * @throws Exception thrown if reading, converting or writing fails.
     */

    public boolean runRunner() throws Exception {
        if (SoapUI.getSettings().getBoolean(UISettings.DONT_DISABLE_GROOVY_LOG)) {
            initGroovyLog();
        }

        String projectFile = getProjectFile();

        WsdlProject project = (WsdlProject) ProjectFactoryRegistry.getProjectFactory("wsdl").createNew(projectFile,
                getProjectPassword());

        if (project.isDisabled()) {
            throw new Exception("Failed to load SoapUI project file [" + projectFile + "]");
        }

        SaveStatus saveStatus = project.saveAs(outputProjectFile, true);
        if (saveStatus == SaveStatus.SUCCESS) {
            log.info("Project '" + project.getName() + "' converted to single XML document containing all contents and saved in '" + outputProjectFile + "'");
        } else {
            log.warn("Failed to convert and save project '" + project.getName() + "'");
            throw new Exception("project saveAs including content failed : " + saveStatus);
        }

        return true;
    }
}
