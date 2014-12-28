package com.eviware.soapui.impl.wsdl

import com.eviware.soapui.SoapUI
import com.eviware.soapui.config.ExternalFilenameBuildModeConfig
import com.eviware.soapui.impl.support.ContentInExternalFile
import com.eviware.soapui.impl.support.ContentInExternalFileCategory
import com.eviware.soapui.impl.support.ContentInExternalFileProjectListener
import com.eviware.soapui.impl.support.ContentInExternalFileSupport
import com.eviware.soapui.impl.wsdl.teststeps.ScriptCategory
import com.eviware.soapui.settings.UISettings
import com.google.common.io.Files
import org.apache.commons.io.FileUtils
import spock.lang.*

import static com.eviware.soapui.impl.support.ContentInExternalFile.*


/**
 * Created with IntelliJ IDEA.
 * User: marcpa
 * Date: 2014-12-01
 * Time: 23:41
 * To change this template use File | Settings | File Templates.
 */
class WsdlProjectContentInExternalFileSpec extends Specification {

    @Shared
    File tempDir = Files.createTempDir()

    @Shared
    String projectDocumentFolder = "unittest-project-document${ContentInExternalFile.EXTERNAL_FILE_ROOT_PATH_SUFFIX}"

    @Shared
    String projectXmlTemplate = '''\
            <?xml version="1.0" encoding="UTF-8"?>
            <con:soapui-project name="a project with a ${name} script"
                    soapui-version="5.2.0-SNAPSHOT"
                    abortOnError="false"
                    runType="SEQUENTIAL"
                    resourceRoot=""
                    activeEnvironment="Default"
                    xmlns:con="http://eviware.com/soapui/config">
                <con:settings/>
                <con:${type}><![CDATA[${content}]]></con:${type}>
            </con:soapui-project>'''

    @Shared
    String projectSuiteXmlTemplate = '''\
             <?xml version="1.0" encoding="UTF-8"?>
             <con:soapui-project name="project, suite with a ${name} script"
                    soapui-version="5.2.0-SNAPSHOT"
                    abortOnError="false"
                    runType="SEQUENTIAL"
                    resourceRoot=""
                    activeEnvironment="Default"
                    xmlns:con="http://eviware.com/soapui/config">
                 <con:settings/>
                 <con:testSuite name="${name}">
                     <con:settings/>
                     <con:runType>SEQUENTIAL</con:runType>
                     <con:testCase/>
                     <con:properties/>
                     <con:${type}><![CDATA[${content}]]></con:${type}>
                 </con:testSuite>
             </con:soapui-project>'''


    def setup() {
        SoapUI.getSettings().setBoolean(UISettings.CONTENT_IN_EXTERNAL_FILE, true)
        SoapUI.getSettings().setBoolean(UISettings.ALSO_KEEP_IN_PROJECT_WHEN_CONTENT_IN_EXTERNAL_FILE, true)
        SoapUI.getSettings().setBoolean(UISettings.AUTO_CONVERT_CONTENT_TO_USE_EXTERNAL_FILE, true)

        System.setProperty("user.dir", tempDir.absolutePath)
    }

    def ensureWorkDirIsClean() {
        File externalFileRoot = new File(new File(System.getProperty('user.dir')), projectDocumentFolder)
        if (externalFileRoot.exists()) {
            if (externalFileRoot.isDirectory()) {
                FileUtils.deleteDirectory(externalFileRoot);
            } else {
                FileUtils.deleteQuietly(externalFileRoot);
            }
        }
    }

    @Unroll
    def "project with a #level level #name script, gets auto-converted"(String name, String type, String level, String content, String template) {
        given: "a project with a #name script and no external content existing"

        Map binding = [ name : name, type : type, content : content ]

        def engine = new groovy.text.SimpleTemplateEngine()
        String projectXml = engine.createTemplate(template).make(binding).toString()

        ensureWorkDirIsClean()
        InputStream projectInputStream = new ByteArrayInputStream(projectXml.stripIndent().getBytes("UTF-8"))
        WsdlProject wsdlProject = new WsdlProject(projectInputStream, null)

        when: "converted to use content in external file with project listener"
        ContentInExternalFileProjectListener contentInExternalFileProjectListener = new ContentInExternalFileProjectListener()
        contentInExternalFileProjectListener.convertToContentInExternalFileIfNeeded(wsdlProject, wsdlProject.getSettings())
        ContentInExternalFileSupport contentInExternalFileSupport
        ScriptCategory scriptCategory
        String externalFilename
        switch (type) {
            case 'beforeRunScript':
                contentInExternalFileSupport = wsdlProject.beforeRunContentInExternalFile
                scriptCategory = ScriptCategory.PROJECT_BEFORE_RUN
                externalFilename = "a project with a ${name} script-${type}.groovy"
                break;
            case 'afterRunScript':
                contentInExternalFileSupport = wsdlProject.afterRunContentInExternalFile
                scriptCategory = ScriptCategory.PROJECT_AFTER_RUN
                externalFilename = "a project with a ${name} script-${type}.groovy"
                break;
            case 'afterLoadScript':
                contentInExternalFileSupport = wsdlProject.afterLoadContentInExternalFile
                scriptCategory = ScriptCategory.PROJECT_AFTER_LOAD
                externalFilename = "a project with a ${name} script-${type}.groovy"
                break;
            case 'beforeSaveScript':
                contentInExternalFileSupport = wsdlProject.beforeSaveContentInExternalFile
                scriptCategory = ScriptCategory.PROJECT_BEFORE_SAVE
                externalFilename = "a project with a ${name} script-${type}.groovy"
                break;
            case 'reportScript':
                switch (level) {
                    case 'project' :
                        contentInExternalFileSupport = wsdlProject.reportContentInExternalFile
                        scriptCategory = ScriptCategory.PROJECT_REPORT
                        externalFilename = "a project with a ${name} script-${type}.groovy"
                        break;
                    case 'suite' :
                        contentInExternalFileSupport = wsdlProject.testSuiteList.first().reportScriptContentInExternalFile
                        scriptCategory = ScriptCategory.TEST_SUITE_REPORT
                        externalFilename = "project, suite with a ${name} script/${name}-${type}.groovy"
                        break;
                }
                break;
            case 'setupScript':
                switch (level) {
                    case 'suite':
                        contentInExternalFileSupport = wsdlProject.testSuiteList.first().setupScriptContentInExternalFile
                        scriptCategory = ScriptCategory.TEST_SUITE_SETUP
                        externalFilename = "project, suite with a ${name} script/${name}-${type}.groovy"
                        break;
                }
                break;
            case 'tearDownScript':
                switch (level) {
                    case 'suite':
                        contentInExternalFileSupport = wsdlProject.testSuiteList.first().tearDownScriptContentInExternalFile
                        scriptCategory = ScriptCategory.TEST_SUITE_TEARDOWN
                        externalFilename = "project, suite with a ${name} script/${name}-${type}.groovy"
                        break;
                }
                break;
        }

        then: "content support is properly initialized"
        contentInExternalFileSupport
        contentInExternalFileSupport.contentInExternalFileCategory == ContentInExternalFileCategory.SCRIPT
        contentInExternalFileSupport.scriptCategory == scriptCategory
        contentInExternalFileSupport.content == content
        contentInExternalFileSupport.externalFilename == externalFilename
        contentInExternalFileSupport.externalFilenameBuildMode == ExternalFilenameBuildModeConfig.AUTO

        and: "content in external file attributes are added to project config"
        contentInExternalFileSupport.actualConfig
        contentInExternalFileSupport.actualConfig.config
        contentInExternalFileSupport.actualConfig.config.externalFilename == externalFilename
        contentInExternalFileSupport.actualConfig.config.externalFilenameBuildMode == ExternalFilenameBuildModeConfig.AUTO

        where:
        name         | type               | level     | content                                      | template
        'beforeRun'  | 'beforeRunScript'  | 'project' | 'log.info("in beforeRun script")'            | projectXmlTemplate.stripIndent()
        'afterRun'   | 'afterRunScript'   | 'project' | 'log.info("in afterRun script")'             | projectXmlTemplate.stripIndent()
        'afterLoad'  | 'afterLoadScript'  | 'project' | 'log.info("in afterLoad script")'            | projectXmlTemplate.stripIndent()
        'beforeSave' | 'beforeSaveScript' | 'project' | 'log.info("in beforeSave script")'           | projectXmlTemplate.stripIndent()
        'report'     | 'reportScript'     | 'project' | 'log.info("in report script")'               | projectXmlTemplate.stripIndent()

        'setup'      | 'setupScript'      | 'suite'   | 'log.info("in setupScript of testSuite")'    | projectSuiteXmlTemplate.stripIndent()
        'tearDown'   | 'tearDownScript'   | 'suite'   | 'log.info("in tearDownScript of testSuite")' | projectSuiteXmlTemplate.stripIndent()
        'report'     | 'reportScript'     | 'suite'   | 'log.info("in reportScript of testSuite")'   | projectSuiteXmlTemplate.stripIndent()

    }
}
