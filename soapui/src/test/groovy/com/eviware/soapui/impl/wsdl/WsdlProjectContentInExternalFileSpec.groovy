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

    def "project with a beforeRun script get auto-converted"() {
        given: "a project with a beforeRun script and no external content existing"

        String projectXml = '''\
            <?xml version="1.0" encoding="UTF-8"?>
            <con:soapui-project name="a project with a beforeRun script"
                    soapui-version="5.2.0-SNAPSHOT"
                    abortOnError="false"
                    runType="SEQUENTIAL"
                    resourceRoot=""
                    activeEnvironment="Default"
                    xmlns:con="http://eviware.com/soapui/config">
                <con:settings/>
                <con:beforeRunScript><![CDATA[log.info("in beforeRun script")]]></con:beforeRunScript>
            </con:soapui-project>'''

        ensureWorkDirIsClean()
        InputStream projectInputStream = new ByteArrayInputStream(projectXml.stripIndent().getBytes("UTF-8"))
        WsdlProject wsdlProject = new WsdlProject(projectInputStream, null)

        when: "converted to use content in external file with project listener"
        ContentInExternalFileProjectListener contentInExternalFileProjectListener = new ContentInExternalFileProjectListener()
        contentInExternalFileProjectListener.convertToContentInExternalFileIfNeeded(wsdlProject, wsdlProject.getSettings())
        ContentInExternalFileSupport contentInExternalFileSupport = wsdlProject.beforeRunContentInExternalFile

        then: "content support is properly initialized"
        contentInExternalFileSupport
        contentInExternalFileSupport.contentInExternalFileCategory == ContentInExternalFileCategory.SCRIPT
        contentInExternalFileSupport.scriptCategory == ScriptCategory.PROJECT_BEFORE_RUN
        contentInExternalFileSupport.content == 'log.info("in beforeRun script")'
        contentInExternalFileSupport.externalFilename == "a project with a beforeRun script-beforeRunScript.groovy"
        contentInExternalFileSupport.externalFilenameBuildMode == ExternalFilenameBuildModeConfig.AUTO

        and: "content in external file attributes are added to project config"
        contentInExternalFileSupport.actualConfig
        contentInExternalFileSupport.actualConfig.config
        contentInExternalFileSupport.actualConfig.config.externalFilename == "a project with a beforeRun script-beforeRunScript.groovy"
        contentInExternalFileSupport.actualConfig.config.externalFilenameBuildMode == ExternalFilenameBuildModeConfig.AUTO


    }
}
