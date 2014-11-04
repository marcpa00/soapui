package com.eviware.soapui.impl.wsdl;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.SoapuiProjectDocumentConfig;
import com.eviware.soapui.impl.WorkspaceImpl;
import com.eviware.soapui.impl.support.ContentInExternalFile;
import com.eviware.soapui.impl.wsdl.support.PathUtils;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.impl.wsdl.teststeps.*;
import com.eviware.soapui.impl.wsdl.teststeps.assertions.basic.GroovyScriptAssertion;
import com.eviware.soapui.model.project.Project;
import com.eviware.soapui.model.project.SaveStatus;
import com.eviware.soapui.model.testsuite.TestAssertion;
import com.eviware.soapui.model.testsuite.TestCase;
import com.eviware.soapui.model.testsuite.TestStep;
import com.eviware.soapui.model.testsuite.TestSuite;
import com.eviware.soapui.settings.UISettings;
import com.eviware.soapui.support.SoapUIException;
import com.eviware.soapui.utils.StubbedDialogsTestBase;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.eviware.soapui.impl.support.ContentInExternalFile.*;


/**
 * Load and save unit tests for the case where "content in external file" feature is enabled.
 */
public class WsdlProjectContentInExternalFileLoadAndSaveTest extends StubbedDialogsTestBase {
    private static final String PROJECT_NAME = "Project";
    private static final File TEMPORARY_FOLDER = Files.createTempDir();
    private static final String SAMPLE_PROJECT_RELATIVE_PATH = "/sample-soapui-project.xml";
    private static final String SAMPLE_PROJECT_ABSOLUTE_PATH
            = WsdlProjectContentInExternalFileLoadAndSaveTest.class.getResource(SAMPLE_PROJECT_RELATIVE_PATH).getPath();

    private final InputStream sampleProjectInputSteam = getClass().getResourceAsStream(SAMPLE_PROJECT_RELATIVE_PATH);

    @Before
    public void setup() throws IOException {
        resetSampleProjectFileToWritable();
        FileUtils.copyFile(new File(SAMPLE_PROJECT_ABSOLUTE_PATH), new File(SAMPLE_PROJECT_ABSOLUTE_PATH + "-backup"), true);
        SoapUI.getSettings().setBoolean(UISettings.CONTENT_IN_EXTERNAL_FILE, true);
        SoapUI.getSettings().setBoolean(UISettings.ALSO_KEEP_IN_PROJECT_WHEN_CONTENT_IN_EXTERNAL_FILE, true);
        SoapUI.getSettings().setBoolean(UISettings.AUTO_CONVERT_CONTENT_TO_USE_EXTERNAL_FILE, true);
    }

    @AfterClass
    public static void teardown() throws IOException {
        FileUtils.deleteDirectory(TEMPORARY_FOLDER);
        File projectFile = new File(SAMPLE_PROJECT_ABSOLUTE_PATH);
        File backupFile = new File(SAMPLE_PROJECT_ABSOLUTE_PATH + "-backup");
        if (backupFile.exists()) {
            if (projectFile.exists()) {
                FileUtils.deleteQuietly(projectFile);
            }
            FileUtils.moveFile(backupFile, projectFile);
        }
        File contentTopDir = computeContentTopDir();
        if (contentTopDir.exists()) {
            FileUtils.deleteQuietly(contentTopDir);
        }
    }

    @Test
    public void projectLoadedFromInputStreamCanBeSaved() throws IOException {
        Project project = new WsdlProject(sampleProjectInputSteam, null);
        answerYesWhenTheOverwriteDialogIsShown();
        SaveStatus status = project.save();
        assertThat(status, is(SaveStatus.SUCCESS));
    }

    @Test
    public void projectLoadedFromFileCanBeSaved() throws IOException {
        Project project = new WsdlProject(SAMPLE_PROJECT_ABSOLUTE_PATH, (WorkspaceImpl) null);
        SaveStatus status = project.save();
        assertThat(status, is(SaveStatus.SUCCESS));
    }

    @Test
    public void projectLoadedFromFileCreatesExternalContentWhenSaved() throws IOException {
        Project project = new WsdlProject(SAMPLE_PROJECT_ABSOLUTE_PATH, (WorkspaceImpl) null);
        SaveStatus status = project.save();
        File contentTopDir = computeContentTopDir();
        assertTrue(contentTopDir.exists());
        assertTrue(contentTopDir.isDirectory());

        File expectedFile;
        String projectName = PathUtils.replaceProblematicCharactersWithUnderscore(project.getName());

        if (project instanceof WsdlProject) {
            WsdlProject wsdlProject = (WsdlProject)project;
            if (wsdlProject.getBeforeRunScript() != null && !wsdlProject.getBeforeRunScript().isEmpty()) {
                expectedFile = new File(contentTopDir, projectName + File.separator + projectName + ScriptCategory.PROJECT_BEFORE_RUN.getDefaultName() + ContentInExternalFile.GROOVY_SCRIPT_SUFFIX);
                assertTrue(expectedFile.exists());
            }
            if (wsdlProject.getAfterLoadScript() != null && !wsdlProject.getAfterLoadScript().isEmpty()) {
                expectedFile = new File(contentTopDir, projectName + File.separator + projectName + ScriptCategory.PROJECT_AFTER_LOAD.getDefaultName() + ContentInExternalFile.GROOVY_SCRIPT_SUFFIX);
                assertTrue(expectedFile.exists());
            }

            for (TestSuite suite : project.getTestSuiteList()) {
                if (suite instanceof WsdlTestSuite) {
                    String suiteName = PathUtils.replaceProblematicCharactersWithUnderscore(suite.getName());
                    WsdlTestSuite wsdlTestSuite = (WsdlTestSuite)suite;
                    if (wsdlTestSuite.getSetupScript() != null && ! wsdlTestSuite.getSetupScript().isEmpty()) {
                        expectedFile = new File(contentTopDir, projectName + File.separator + suiteName + File.separator + suiteName + ScriptCategory.TEST_SUITE_SETUP.getDefaultName() + ContentInExternalFile.GROOVY_SCRIPT_SUFFIX);
                        assertTrue(expectedFile.exists());
                    }
                    if (wsdlTestSuite.getTearDownScript() != null && ! wsdlTestSuite.getTearDownScript().isEmpty()) {
                        expectedFile = new File(contentTopDir, projectName + File.separator + suiteName + File.separator + suiteName + ScriptCategory.TEST_SUITE_TEARDOWN.getDefaultName() + ContentInExternalFile.GROOVY_SCRIPT_SUFFIX);
                        assertTrue(expectedFile.exists());
                    }

                    for (TestCase testCase : suite.getTestCaseList()) {
                        if (testCase instanceof WsdlTestCase) {
                            String testCaseName = PathUtils.replaceProblematicCharactersWithUnderscore(testCase.getName());
                            WsdlTestCase wsdlTestCase = (WsdlTestCase) testCase;
                            if (wsdlTestCase.getSetupScript() != null && !wsdlTestCase.getSetupScript().isEmpty()) {
                                expectedFile = new File(contentTopDir, projectName + File.separator + suiteName + File.separator + testCaseName + File.separator + testCaseName + ScriptCategory.TEST_CASE_SETUP.getDefaultName() + ContentInExternalFile.GROOVY_SCRIPT_SUFFIX);
                                assertTrue(expectedFile.exists());
                            }
                            if (wsdlTestCase.getTearDownScript() != null && !wsdlTestCase.getTearDownScript().isEmpty()) {
                                expectedFile = new File(contentTopDir, projectName + File.separator + suiteName + File.separator + testCaseName + File.separator + testCaseName + ScriptCategory.TEST_CASE_TEARDOWN.getDefaultName() + ContentInExternalFile.GROOVY_SCRIPT_SUFFIX);
                                assertTrue(expectedFile.exists());
                            }

                            for (TestStep testStep : testCase.getTestStepList()) {
                                if (testStep instanceof WsdlGroovyScriptTestStep) {
                                    WsdlGroovyScriptTestStep wsdlGroovyScriptTestStep = (WsdlGroovyScriptTestStep)testStep;
                                    String testStepName = PathUtils.replaceProblematicCharactersWithUnderscore(wsdlGroovyScriptTestStep.getName());
                                    expectedFile = new File(contentTopDir, projectName + File.separator + suiteName + File.separator + testCaseName + File.separator + testStepName + ContentInExternalFile.GROOVY_SCRIPT_SUFFIX);
                                    assertTrue(expectedFile.exists());
                                }
                                if (testStep instanceof WsdlTestRequestStep) {
                                    WsdlTestRequestStep wsdlTestRequestStep = (WsdlTestRequestStep)testStep;
                                    String testStepName = PathUtils.replaceProblematicCharactersWithUnderscore(wsdlTestRequestStep.getName());
                                    expectedFile = new File(contentTopDir, projectName + File.separator + suiteName + File.separator + testCaseName + File.separator + testStepName + ContentInExternalFile.WSDL_REQUEST_SUFFIX);
                                    assertTrue(expectedFile.exists());

                                    for (TestAssertion testAssertion : wsdlTestRequestStep.getAssertionList()) {
                                        if (testAssertion instanceof GroovyScriptAssertion) {
                                            GroovyScriptAssertion groovyScriptAssertion = (GroovyScriptAssertion)testAssertion;
                                            String assertionName = PathUtils.replaceProblematicCharactersWithUnderscore(groovyScriptAssertion.getName());
                                            expectedFile = new File(contentTopDir, projectName + File.separator + suiteName + File.separator + testCaseName + File.separator + testStepName + File.separator + assertionName + ContentInExternalFile.GROOVY_SCRIPT_SUFFIX);
                                            assertTrue(expectedFile.exists());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void newlyCreatedProjectCanBeSaved() throws XmlException, IOException, SoapUIException {
        WsdlProject project = createTemporaryProject();
        SaveStatus status = project.saveIn(createTemporaryProjectFile());
        assertThat(status, is(SaveStatus.SUCCESS));
    }

    @Test
    public void userIsPromptedForSaveLocationWhenSavingProjectLoadedFromInputStream() throws IOException {
        Project project = new WsdlProject(sampleProjectInputSteam, null);
        answerYesWhenTheOverwriteDialogIsShown();
        project.save();
        verifyThatTheSaveAsDialogIsShown();
    }

    @Test
    public void newlyCreatedProjectIsNotSavedIfUserOptsNotToSave() throws XmlException, IOException, SoapUIException {
        Project project = createTemporaryProject();
        cancelWhenTheSaveAsFileDialogIsShown();
        SaveStatus status = project.save();
        assertThat(status, is(not(SaveStatus.SUCCESS)));
    }

    @Test
    public void projectIsNotSavedIfSaveAsDialogIsCancelled() throws IOException {
        Project project = new WsdlProject(sampleProjectInputSteam, null);
        answerYesWhenTheOverwriteDialogIsShown();
        cancelWhenTheSaveAsFileDialogIsShown();
        SaveStatus status = project.save();
        assertThat(status, is(SaveStatus.CANCELLED));
    }

    @Test
    public void existingFileIsNotSavedIfNotWritableAndWeDontWantToSave() throws IOException {
        setFileWritePermission(SAMPLE_PROJECT_ABSOLUTE_PATH, false);
        Project project = new WsdlProject(SAMPLE_PROJECT_ABSOLUTE_PATH, (WorkspaceImpl) null);
        answerNoWhenTheDoYouWantToWriteToNewFileDialogIsShown();
        SaveStatus status = project.save();
        assertThat(status, is(SaveStatus.DONT_SAVE));
    }

    @Test
    public void existingFileIsCancelledIfNotWritableAndNoNewFileSelected() throws IOException {
        setFileWritePermission(SAMPLE_PROJECT_ABSOLUTE_PATH, false);
        Project project = new WsdlProject(SAMPLE_PROJECT_ABSOLUTE_PATH, (WorkspaceImpl) null);
        answerYesWhenTheDoYouWantToWriteToNewFileDialogIsShown();
        cancelWhenTheSaveAsFileDialogIsShown();
        SaveStatus status = project.save();
        assertThat(status, is(SaveStatus.CANCELLED));
    }

    @Test
    public void savingWithoutAutoConvertDoesNotAddAttributesInXmlProject() throws IOException {
        SoapUI.getSettings().setBoolean(UISettings.AUTO_CONVERT_CONTENT_TO_USE_EXTERNAL_FILE, false);

        WsdlProject wsdlProject  = new WsdlProject(sampleProjectInputSteam, null);
        answerYesWhenTheOverwriteDialogIsShown();
        wsdlProject.save();
        String savedPath = wsdlProject.getPath();
        wsdlProject.release();

        WsdlProject wsdlProjectAfterSave = new WsdlProject(savedPath, (WorkspaceImpl)null);

        SoapuiProjectDocumentConfig projectDocumentConfig = wsdlProjectAfterSave.getProjectDocument();

        List<XmlObject> xmlObjects = new ArrayList<XmlObject>();
        for (String path : ALL_PATHS_IN_CONFIG) {
            xmlObjects.addAll(Arrays.asList(projectDocumentConfig.selectPath(CONFIG_NAMESPACE + "$this/" + path)));
        }

        int nodesHavingExternalFilenameAttribute = 0;
        for (XmlObject xmlObject : xmlObjects) {
            XmlCursor xmlCursor = xmlObject.newCursor();
            String externalFilenameBuildModeValue = xmlCursor. getAttributeText(EXTERNAL_FILENAME_BUILD_MODE_QNAME);
            if (externalFilenameBuildModeValue != null) {
                nodesHavingExternalFilenameAttribute++;
            }
        }
        // No nodes should have 'externalFilenameMode' attribute when auto-convert is false
        assertThat(nodesHavingExternalFilenameAttribute, is(0));

    }

    private void answerYesWhenTheOverwriteDialogIsShown() {
        stubbedDialogs.mockConfirmWithReturnValue(true);
    }

    private void answerYesWhenTheDoYouWantToWriteToNewFileDialogIsShown() {

        stubbedDialogs.mockConfirmWithReturnValue(true);
    }

    private void answerNoWhenTheDoYouWantToWriteToNewFileDialogIsShown() {
        stubbedDialogs.mockConfirmWithReturnValue(false);
    }

    private void cancelWhenTheSaveAsFileDialogIsShown() {
        when(mockedFileDialogs.saveAs(anyObject(), anyString(), anyString(), anyString(), isA(File.class))).thenReturn(null);
    }

    private void verifyThatTheSaveAsDialogIsShown() {
        verify(mockedFileDialogs).saveAs(anyObject(), anyString(), anyString(), anyString(), isA(File.class));
    }

    private File createTemporaryProjectFile() {
        return new File(TEMPORARY_FOLDER + File.separator + UUID.randomUUID() + "-soapui-project.xml");
    }

    private WsdlProject createTemporaryProject() throws XmlException, IOException, SoapUIException {
        WsdlProject project = new WsdlProject();
        project.setName(PROJECT_NAME);
        return project;
    }

    private void setFileWritePermission(String projectFilePath, boolean writable) throws IOException {
        boolean couldSetWritable = new File(projectFilePath).setWritable(writable);
        if (!couldSetWritable) {
            throw new IOException("Can't set project file '" + projectFilePath + "' to writable");
        }
    }

    private void resetSampleProjectFileToWritable() throws IOException {
        setFileWritePermission(SAMPLE_PROJECT_ABSOLUTE_PATH, true);
    }

    private static File computeContentTopDir() {
        return new File(SAMPLE_PROJECT_ABSOLUTE_PATH.replaceAll("\\.xml$", ContentInExternalFile.EXTERNAL_FILE_ROOT_PATH_SUFFIX));
    }

}