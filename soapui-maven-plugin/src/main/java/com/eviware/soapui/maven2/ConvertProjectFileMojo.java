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

package com.eviware.soapui.maven2;

import com.eviware.soapui.SoapUI;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.eviware.soapui.tools.SoapUIConvertToFatProjectRunner;

/**
 * Convert a soapui project file from a XML document having contents externalized (groovy scripts and wsdl requests) to a XML document having all contents inline.
 *
 * @goal convert
 */

public class ConvertProjectFileMojo extends AbstractMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {
        SoapUIConvertToFatProjectRunner runner = new SoapUIConvertToFatProjectRunner("SoapUI " + SoapUI.SOAPUI_VERSION + " Maven2 ConvertToFatProject Runner");

        try {

            if (sourceFolder != null) {
                runner.setProjectFileList(computeProjectFileList(sourceFolder));
            }

            if (outputFolder != null) {
                runner.setOutputFolder(outputFolder);
            }

            if (settingsFile != null) {
                runner.setSettingsFile(settingsFile);
            }

            if (soapuiProperties != null && soapuiProperties.size() > 0) {
                for (Object key : soapuiProperties.keySet()) {
                    System.out.println("Setting " + (String) key + " value " + soapuiProperties.getProperty((String) key));
                    System.setProperty((String) key, soapuiProperties.getProperty((String) key));
                }
            }

            runner.run();
        } catch (Exception e) {
            getLog().error(e.toString());
            throw new MojoFailureException(this, "SoapUI Project Conversion failed", e.getMessage());
        }
    }

    /**
     * Sets the source folder containing projects to convert
     *
     * @parameter expression="${soapui.sourceFolder}"
     */

    private String sourceFolder;

    /**
     * Sets the output folder for converted files
     *
     * @parameter expression="${soapui.outputFolder}"
     */

    private String outputFolder;

    /**
     * Specifies SoapUI settings file to use
     *
     * @parameter expression="${soapui.settingsFile}"
     */

    private String settingsFile;

    /**
     * SoapUI Properties.
     *
     * @parameter expression="${soapuiProperties}"
     */
    private Properties soapuiProperties;


    private List<String> computeProjectFileList(String sourceFolder) throws Exception {
        File sourceDir = new File(sourceFolder);
        if (!sourceDir.exists()) {
            throw new Exception("sourceFolder does not exist, no files to convert !");
        }
        if (!sourceDir.isDirectory()) {
            sourceDir = sourceDir.getParentFile();
        }
        List<String> projectFileList = new ArrayList<String>();
        for (String filename : sourceDir.list()) {
            if (filename.matches(".*-soapui-project.xml")) {
                projectFileList.add(FilenameUtils.normalize(sourceDir.getAbsolutePath() + File.separator + filename));
            }
        }
        return projectFileList;
    }

}
