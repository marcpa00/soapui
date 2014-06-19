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

import com.eviware.soapui.*;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.support.PathUtils;
import com.eviware.soapui.model.ModelItem;
import com.eviware.soapui.model.project.Project;
import com.eviware.soapui.model.project.ProjectFactoryRegistry;
import com.eviware.soapui.model.project.SaveStatus;
import com.eviware.soapui.model.propertyexpansion.PropertyExpander;
import com.eviware.soapui.model.propertyexpansion.PropertyExpansionUtils;
import com.eviware.soapui.settings.UISettings;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import org.apache.commons.cli.*;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.File;
import java.io.OutputStreamWriter;
import java.util.*;


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

// NOTE : need to support many project files at command-line, that is why we are not extending AbstractSoapUIRunner : the latter forces only one command line parameter.
public class SoapUIConvertToFatProjectRunner implements CmdLineRunner {
    private boolean groovyLogInitialized;
    private String projectFile;
    protected final Logger log = Logger.getLogger(getClass());
    private String settingsFile;
    private String soapUISettingsPassword;
    private String projectPassword;

    private boolean enableUI;
    private String outputFolder;
    private String[] projectProperties;
    private Map<String, String> runnerGlobalProperties = new HashMap<String, String>();

    private String outputProjectFile;
    private String outputProjectFilePrefix;
    private List<String> projectFileList;

    public static String TITLE = "SoapUI " + SoapUI.SOAPUI_VERSION + " Project File Converter";

    public SoapUIConvertToFatProjectRunner(String title) {
        if (title != null) {
            System.out.println(title);
        }

        SoapUI.setCmdLineRunner(this);
    }

    protected void initGroovyLog() {
        if (!groovyLogInitialized) {
            Logger logger = Logger.getLogger("groovy.log");

            ConsoleAppender appender = new ConsoleAppender();
            appender.setWriter(new OutputStreamWriter(System.out));
            appender.setLayout(new PatternLayout("%d{ABSOLUTE} %-5p [%c{1}] %m%n"));
            logger.addAppender(appender);

            groovyLogInitialized = true;
        }
    }

    public int runFromCommandLine(String[] args) {
        try {
            if (initFromCommandLine(args, true)) {
                if (run()) {
                    return 0;
                }
            }
        } catch (Throwable e) {
            log.error(e);
            SoapUI.logError(e);
        }

        return -1;
    }

    public boolean initFromCommandLine(String[] args, boolean printHelp) throws Exception {
        SoapUIOptions options = initCommandLineOptions();

        CommandLineParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, args);

        if (options.requiresProject()) {
            args = cmd.getArgs();

            if (args.length < 1) {
                if (printHelp) {
                    HelpFormatter formatter = new HelpFormatter();
                    formatter.printHelp(options.getRunnerName() + " [options] <soapui-project-file> [ <soapui-project-file>, ... ]", options);
                }

                System.err.println("Missing SoapUI project file..");
                return false;
            }

            if (args.length == 1) {
                setProjectFile(args[0]);
            } else {
                setProjectFileList(Arrays.asList(args));
                System.out.println("project file list : " + Arrays.asList(args).toString());
            }
        }

        return processCommandLine(cmd);
    }

    /**
     * Main method to use for running the configured tests. Call after setting
     * properties, etc as desired.
     *
     * @return true if execution should be blocked
     * @throws Exception if an error or failure occurs during test execution
     */

    public final boolean run() throws Exception {
        if (SoapUI.getSoapUICore() == null) {
            SoapUI.setSoapUICore(createSoapUICore(), true);
            SoapUI.initGCTimer();
        }
        for (String name : runnerGlobalProperties.keySet()) {
            PropertyExpansionUtils.getGlobalProperties().setPropertyValue(name, runnerGlobalProperties.get(name));
        }

        SoapUIExtensionClassLoader.SoapUIClassLoaderState state = SoapUIExtensionClassLoader.ensure();

        try {
            return runRunner();
        } finally {
            state.restore();
        }
    }

    protected SoapUICore createSoapUICore() {
        if (enableUI) {
            StandaloneSoapUICore core = new StandaloneSoapUICore(settingsFile);
            log.info("Enabling UI Components");
            core.prepareUI();
            UISupport.setMainFrame(null);
            return core;
        } else {
            return new DefaultSoapUICore(null, settingsFile, soapUISettingsPassword);
        }
    }

    protected String getCommandLineOptionSubstSpace(CommandLine cmd, String key) {
        return cmd.getOptionValue(key).replaceAll("%20", " ");
    }

    /*
     * (non-Javadoc)
     *
     * @see com.eviware.soapui.tools.CmdLineRunner#getProjectFile()
     */
    @Override
    public String getProjectFile() {
        return projectFile;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.eviware.soapui.tools.CmdLineRunner#getSettingsFile()
     */
    @Override
    public String getSettingsFile() {
        return settingsFile;
    }

    public void setOutputFolder(String outputFolder) {
        this.outputFolder = outputFolder;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.eviware.soapui.tools.CmdLineRunner#getOutputFolder()
     */
    @Override
    public String getOutputFolder() {
        return this.outputFolder;
    }

    public String getAbsoluteOutputFolder(ModelItem modelItem) {
        String folder = PropertyExpander.expandProperties(modelItem, outputFolder);

        if (StringUtils.isNullOrEmpty(folder)) {
            folder = PathUtils.getExpandedResourceRoot(modelItem);
        } else if (PathUtils.isRelativePath(folder)) {
            folder = PathUtils.resolveResourcePath(folder, modelItem);
        }

        return folder;
    }

    public String getModelItemOutputFolder(ModelItem modelItem) {
        List<ModelItem> chain = new ArrayList<ModelItem>();
        ModelItem p = modelItem;

        while (!(p instanceof Project)) {
            chain.add(0, p);
            p = p.getParent();
        }

        File dir = new File(getAbsoluteOutputFolder(modelItem));
        dir.mkdir();

        for (ModelItem item : chain) {
            dir = new File(dir, StringUtils.createFileName(item.getName(), '-'));
            dir.mkdir();
        }

        return dir.getAbsolutePath();
    }

    protected void ensureOutputFolder(ModelItem modelItem) {
        ensureFolder(getAbsoluteOutputFolder(modelItem));
    }

    public void ensureFolder(String path) {
        if (path == null) {
            return;
        }

        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory()) {
            folder.mkdirs();
        }
    }

    /**
     * Sets the SoapUI project file containing the tests to run
     *
     * @param projectFile the SoapUI project file containing the tests to run
     */

    public void setProjectFile(String projectFile) {
        this.projectFile = projectFile;
    }

    public List<String> getProjectFileList() {
        return projectFileList;
    }

    public void setProjectFileList(List<String> projectFileList) {
        this.projectFileList = projectFileList;
    }

    public String getOutputProjectFilePrefix() {
        return outputProjectFilePrefix;
    }

    public void setOutputProjectFilePrefix(String outputProjectFilePrefix) {
        this.outputProjectFilePrefix = outputProjectFilePrefix;
    }

    /**
     * Sets the SoapUI settings file containing the tests to run
     *
     * @param settingsFile the SoapUI settings file to use
     */

    public void setSettingsFile(String settingsFile) {
        this.settingsFile = settingsFile;
    }

    public void setEnableUI(boolean enableUI) {
        this.enableUI = enableUI;
    }

    public static class SoapUIOptions extends Options {
        private final String runnerName;

        public SoapUIOptions(String runnerName) {
            this.runnerName = runnerName;
        }

        public String getRunnerName() {
            return runnerName;
        }

        public boolean requiresProject() {
            return true;
        }
    }

    public String getSoapUISettingsPassword() {
        return soapUISettingsPassword;
    }

    public void setSoapUISettingsPassword(String soapUISettingsPassword) {
        this.soapUISettingsPassword = soapUISettingsPassword;
    }

    public void setSystemProperties(String[] optionValues) {
        for (String option : optionValues) {
            int ix = option.indexOf('=');
            if (ix != -1) {
                System.setProperty(option.substring(0, ix), option.substring(ix + 1));
            }
        }
    }

    public void setGlobalProperties(String[] optionValues) {
        for (String option : optionValues) {
            int ix = option.indexOf('=');
            if (ix != -1) {
                String name = option.substring(0, ix);
                String value = option.substring(ix + 1);
                log.info("Setting global property [" + name + "] to [" + value + "]");
                //				PropertyExpansionUtils.getGlobalProperties().setPropertyValue( name, value );
                runnerGlobalProperties.put(name, value);
            }
        }
    }

    public void setProjectProperties(String[] projectProperties) {
        this.projectProperties = projectProperties;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.eviware.soapui.tools.CmdLineRunner#getLog()
     */
    @Override
    public Logger getLog() {
        return log;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.eviware.soapui.tools.CmdLineRunner#getProjectProperties()
     */
    @Override
    public String[] getProjectProperties() {
        return projectProperties;
    }

    protected void initProjectProperties(WsdlProject project) {
        if (projectProperties != null) {
            for (String option : projectProperties) {
                int ix = option.indexOf('=');
                if (ix != -1) {
                    String name = option.substring(0, ix);
                    String value = option.substring(ix + 1);
                    log.info("Setting project property [" + name + "] to [" + value + "]");
                    project.setPropertyValue(name, value);
                }
            }
        }
    }

    public boolean isEnableUI() {
        return enableUI;
    }

    public String getProjectPassword() {
        return projectPassword;
    }

    public void setProjectPassword(String projectPassword) {
        this.projectPassword = projectPassword;
    }

    /**
     * Runs the conversion for the specified soapUI project file or files.
     *
     * @param args
     * @throws Exception
     */

    public static void main(String[] args) {


        System.exit(new SoapUIConvertToFatProjectRunner(TITLE).runFromCommandLine(args));
    }

    protected boolean processCommandLine(CommandLine cmd) {
        String message = "";

        // -d and -o are exclusive : with -d, output file will have same name than input, but in directory given with -d.
        // This is useful when processing many files
        if (cmd.hasOption("d")) {
            setOutputFolder(cmd.getOptionValue("d"));
        } else {
            if (cmd.hasOption("o")) {
                setOutputProjectFile(cmd.getOptionValue("o"));
            } else {
                setOutputProjectFilePrefix("fat-");
            }
        }

        if (message.length() > 0) {
            log.error(message);
            return false;
        }


        return true;
    }

    protected SoapUIOptions initCommandLineOptions() {
        SoapUIOptions options = new SoapUIOptions("projectconverter");
        options.addOption("o", true, "Sets the output project file (ignored if -d is set)");
        options.addOption("d", true, "Sets the target directory where converted files will be saved.");

        return options;
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

        boolean singleFileMode = true;
        List<String> projectFiles = new ArrayList<String>();
        if (projectFileList != null) {
            singleFileMode = false;
            projectFiles = projectFileList;
        } else {
            projectFiles.add(getProjectFile());
        }

        for (String projectFile : projectFiles) {
            System.out.println("... processing project " + projectFile);

            WsdlProject project = (WsdlProject) ProjectFactoryRegistry.getProjectFactory("wsdl").createNew(projectFile,
                    getProjectPassword());

            if (project.isDisabled()) {
                throw new Exception("Failed to load SoapUI project file [" + projectFile + "]");
            }

            String target;
            if (singleFileMode) {
                target = outputProjectFile;
            } else {
                if (getOutputFolder() != null) {
                    ensureFolder(getOutputFolder());
                    File f = new File(projectFile);
                    if (f.isAbsolute()) {
                        target = getOutputFolder() + File.separator + f.getName();
                    } else {
                        target = getOutputFolder() + File.separator + projectFile;
                    }
                } else if (outputProjectFilePrefix != null) {
                    File f = new File(projectFile);
                    if (f.isAbsolute()) {
                        target = f.getParent() + File.separator + outputProjectFilePrefix + f.getName();
                    } else {
                        target = outputProjectFilePrefix + projectFile;
                    }
                } else {
                  target = projectFile;
                }
            }

            SaveStatus saveStatus = project.saveAs(target, true);
            if (saveStatus == SaveStatus.SUCCESS) {
                log.info("Project '" + project.getName() + "' converted to single XML document containing all contents and saved in '" + target + "'");
            } else {
                log.warn("Failed to convert and save project '" + project.getName() + "'");
                throw new Exception("project saveAs including content failed : " + saveStatus);
            }
        }

        return true;
    }

}
