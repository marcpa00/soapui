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
    def "project with a #name script get auto-converted"(String name, String type, String content) {
        given: "a project with a #name script and no external content existing"

        Map binding = [ name : name, type : type, content : content ]

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

        def engine = new groovy.text.SimpleTemplateEngine()
        String projectXml = engine.createTemplate(projectXmlTemplate).make(binding).toString()

        ensureWorkDirIsClean()
        InputStream projectInputStream = new ByteArrayInputStream(projectXml.stripIndent().getBytes("UTF-8"))
        WsdlProject wsdlProject = new WsdlProject(projectInputStream, null)

        when: "converted to use content in external file with project listener"
        ContentInExternalFileProjectListener contentInExternalFileProjectListener = new ContentInExternalFileProjectListener()
        contentInExternalFileProjectListener.convertToContentInExternalFileIfNeeded(wsdlProject, wsdlProject.getSettings())
        ContentInExternalFileSupport contentInExternalFileSupport
        ScriptCategory scriptCategory
        switch (type) {
            case 'beforeRunScript':
                contentInExternalFileSupport = wsdlProject.beforeRunContentInExternalFile
                scriptCategory = ScriptCategory.PROJECT_BEFORE_RUN
                break;
            case 'afterRunScript':
                contentInExternalFileSupport = wsdlProject.afterRunContentInExternalFile
                scriptCategory = ScriptCategory.PROJECT_AFTER_RUN
                break;
            case 'afterLoadScript':
                contentInExternalFileSupport = wsdlProject.afterLoadContentInExternalFile
                scriptCategory = ScriptCategory.PROJECT_AFTER_LOAD
                break;
            case 'beforeSaveScript':
                contentInExternalFileSupport = wsdlProject.beforeSaveContentInExternalFile
                scriptCategory = ScriptCategory.PROJECT_BEFORE_SAVE
                break;
        }

        then: "content support is properly initialized"
        contentInExternalFileSupport
        contentInExternalFileSupport.contentInExternalFileCategory == ContentInExternalFileCategory.SCRIPT
        contentInExternalFileSupport.scriptCategory == scriptCategory
        contentInExternalFileSupport.content == content
        contentInExternalFileSupport.externalFilename == "a project with a ${name} script-${type}.groovy"
        contentInExternalFileSupport.externalFilenameBuildMode == ExternalFilenameBuildModeConfig.AUTO

        and: "content in external file attributes are added to project config"
        contentInExternalFileSupport.actualConfig
        contentInExternalFileSupport.actualConfig.config
        contentInExternalFileSupport.actualConfig.config.externalFilename == "a project with a ${name} script-${type}.groovy"
        contentInExternalFileSupport.actualConfig.config.externalFilenameBuildMode == ExternalFilenameBuildModeConfig.AUTO

        where:
        name         | type               | content
        'beforeRun'  | 'beforeRunScript'  | 'log.info("in beforeRun script")'
        'afterRun'   | 'afterRunScript'   | 'log.info("in afterRun script")'
        'afterLoad'  | 'afterLoadScript'  | 'log.info("in afterLoad script")'
        'beforeSave' | 'beforeSaveScript' | 'log.info("in beforeSave script")'

    }
}
