package com.eviware.soapui.impl.support;

import javax.xml.namespace.QName;

import java.util.ArrayList;
import java.util.List;

import static com.eviware.soapui.impl.wsdl.teststeps.Script.*;

/**
 * Constants for feature "storing content in external file".
 * <p>
 * <i>Content</i> that can be stored in external file :
 * <ul>
 * <li>WSDL Request Test Step</li>
 * <li>Groovy Script Test Step</li>
 * <li>any script (afterLoadScript, beforeSaveScript, reportScript, setupScript, tearDownScript, etc)</li>
 * <li>Groovy Assertion</li>
 * </ul>
 * </p>
 *
 * @author Marc Paquette
 */
public final class ContentInExternalFile {
    private ContentInExternalFile() {
    }

    //
    // File name related constants
    //
    public final static String DEFAULT_STEP_FILENAME = "new-testStep";
    public final static String DEFAULT_SUFFIX = ".txt";
    public final static String WSDL_REQUEST_SUFFIX = "-request.xml";
    public final static String GROOVY_SCRIPT_SUFFIX = ".groovy";
    public final static String EXTERNAL_FILE_ROOT_PATH_SUFFIX = ".resources";
    public final static String PROJECT_FILE_SUFFIX = ".xml";
    public final static String WSDL_REQUEST_EXTENSION = ".xml";

    //
    // XML processing constants
    //
    public final static String CONFIG_NAMESPACE = "declare namespace con='http://eviware.com/soapui/config';";
    public final static String WSDL_NAMESPACE = "declare namespace wsdl='http://schemas.xmlsoap.org/wsdl/';";

    //
    // paths to implicit groovy script : GROOVY_SCRIPT_PATHS
    //   * external file attributes are on last level of path
    //   * content is the child node of last level of path
    //   * content container name is name of last level of path
    //   * content type is implicitly GROOVY_TYPE
    //
    // one-level to script
    public final static String PATH_TO_AFTER_LOAD_SCRIPT_IN_CONFIG                 = "con:soapui-project/con:afterLoadScript";
    public final static String PATH_TO_BEFORE_SAVE_SCRIPT_IN_CONFIG                = "con:soapui-project/con:beforeSaveScript";
    public final static String PATH_TO_REPORT_SCRIPT_IN_CONFIG                     = "con:soapui-project/con:reportScript";
    public final static String PATH_TO_BEFORE_RUN_SCRIPT_IN_CONFIG                 = "con:soapui-project/con:beforeRunScript";
    public final static String PATH_TO_AFTER_RUN_SCRIPT_IN_CONFIG                  = "con:soapui-project/con:afterRunScript";
    // two-levels to script
    public final static String PATH_TO_TESTSUITE_SETUP_SCRIPT_IN_CONFIG            = "con:soapui-project/con:testSuite/con:setupScript";
    public final static String PATH_TO_TESTSUITE_TEARDOWN_SCRIPT_IN_CONFIG         = "con:soapui-project/con:testSuite/con:tearDownScript";
    public final static String PATH_TO_TESTSUITE_REPORT_SCRIPT_IN_CONFIG           = "con:soapui-project/con:testSuite/con:reportScript";
    public final static String PATH_TO_LOADTEST_SETUP_SCRIPT_IN_CONFIG             = "con:soapui-project/con:loadTest/con:setupScript";
    public final static String PATH_TO_LOADTEST_TEARDOWN_SCRIPT_IN_CONFIG          = "con:soapui-project/con:loadTest/con:tearDownScript";
    public final static String PATH_TO_LOADTEST_REPORT_SCRIPT_IN_CONFIG            = "con:soapui-project/con:loadTest/con:reportScript";
    public final static String PATH_TO_MOCK_SERVICE_START_SCRIPT_IN_CONFIG         = "con:soapui-project/con:mockService/con:startScript";
    public final static String PATH_TO_MOCK_SERVICE_STOP_SCRIPT_IN_CONFIG          = "con:soapui-project/con:mockService/con:stopScript";
    public final static String PATH_TO_MOCK_SERVICE_ON_REQUEST_SCRIPT_IN_CONFIG    = "con:soapui-project/con:mockService/con:onRequestScript";
    public final static String PATH_TO_MOCK_SERVICE_AFTER_REQUEST_SCRIPT_IN_CONFIG = "con:soapui-project/con:mockService/con:afterRequestScript";
    // three-levels to script
    public final static String PATH_TO_TESTCASE_SETUP_SCRIPT_IN_CONFIG             = "con:soapui-project/con:testSuite/con:testCase/con:setupScript";
    public final static String PATH_TO_TESTCASE_TEARDOWN_SCRIPT_IN_CONFIG          = "con:soapui-project/con:testSuite/con:testCase/con:tearDownScript";
    public final static String PATH_TO_TESTCASE_REPORT_SCRIPT_IN_CONFIG            = "con:soapui-project/con:testSuite/con:testCase/con:reportScript";
    // four-levels to script
    public final static String PATH_TO_SECURITY_SETUP_SCRIPT_IN_CONFIG             = "con:soapui-project/con:testSuite/con:testCase/con:securityTest/con:setupScript";
    public final static String PATH_TO_SECURITY_TEARDOWN_SCRIPT_IN_CONFIG          = "con:soapui-project/con:testSuite/con:testCase/con:securityTest/con:tearDownScript";
    public final static String PATH_TO_SECURITY_REPORT_SCRIPT_IN_CONFIG            = "con:soapui-project/con:testSuite/con:testCase/con:securityTest/con:reportScript";

    public final static String[] GROOVY_SCRIPT_PATHS = {
            PATH_TO_AFTER_LOAD_SCRIPT_IN_CONFIG,
            PATH_TO_BEFORE_SAVE_SCRIPT_IN_CONFIG,
            PATH_TO_REPORT_SCRIPT_IN_CONFIG,
            PATH_TO_BEFORE_RUN_SCRIPT_IN_CONFIG,
            PATH_TO_AFTER_RUN_SCRIPT_IN_CONFIG,
            PATH_TO_TESTSUITE_SETUP_SCRIPT_IN_CONFIG,
            PATH_TO_TESTSUITE_TEARDOWN_SCRIPT_IN_CONFIG,
            PATH_TO_TESTSUITE_REPORT_SCRIPT_IN_CONFIG,
            PATH_TO_LOADTEST_SETUP_SCRIPT_IN_CONFIG,
            PATH_TO_LOADTEST_TEARDOWN_SCRIPT_IN_CONFIG,
            PATH_TO_LOADTEST_REPORT_SCRIPT_IN_CONFIG,
            PATH_TO_MOCK_SERVICE_START_SCRIPT_IN_CONFIG,
            PATH_TO_MOCK_SERVICE_STOP_SCRIPT_IN_CONFIG,
            PATH_TO_MOCK_SERVICE_ON_REQUEST_SCRIPT_IN_CONFIG,
            PATH_TO_MOCK_SERVICE_AFTER_REQUEST_SCRIPT_IN_CONFIG,
            PATH_TO_TESTCASE_SETUP_SCRIPT_IN_CONFIG,
            PATH_TO_TESTCASE_TEARDOWN_SCRIPT_IN_CONFIG,
            PATH_TO_TESTCASE_REPORT_SCRIPT_IN_CONFIG,
            PATH_TO_SECURITY_SETUP_SCRIPT_IN_CONFIG,
            PATH_TO_SECURITY_TEARDOWN_SCRIPT_IN_CONFIG,
            PATH_TO_SECURITY_REPORT_SCRIPT_IN_CONFIG
    };

    //
    // path to groovy script test steps : GROOVY_SCRIPT_TESTSTEP_PATHS
    //   * external file attributes are on last level of path
    //   * content is the child node of last level of path
    //   * content container name is name of grand-parent of last level of path
    //   * content type is the type attribute of grand-parent of last level of path
    public final static String PATH_TO_SCRIPT_IN_CONFIG                    = "con:soapui-project/con:testSuite/con:testCase/con:testStep/con:config/script";
    public final static String PATH_TO_GROOVY_SCRIPT_ASSERTION_IN_CONFIG   = "con:soapui-project/con:testSuite/con:testCase/con:testStep/con:config/con:assertion/con:configuration/scriptText";
    public final static String PATH_TO_GROOVY_SCRIPT_ASSERTION_2_IN_CONFIG = "con:soapui-project/con:testSuite/con:testCase/con:testStep/con:config/con:request/con:assertion/con:configuration/scriptText";
    public final static String[] GROOVY_SCRIPT_TESTSTEP_PATHS = {
            PATH_TO_SCRIPT_IN_CONFIG,
            PATH_TO_GROOVY_SCRIPT_ASSERTION_IN_CONFIG,
            PATH_TO_GROOVY_SCRIPT_ASSERTION_2_IN_CONFIG
    };

    //
    // path to wsdl request test steps : REQUEST_TESTSTEP_PATHS
    //   * external file attributes are on last level of path
    //   * content is the grand-child of path through another 'con:request' level
    //   * content container name is name of grand-parent of last level of path
    //   * content type is the type attribute of grand-parent of last level of path
    public final static String PATH_TO_REQUEST_IN_CONFIG = "con:soapui-project/con:testSuite/con:testCase/con:testStep/con:config/con:request";
    public final static String[] REQUEST_TESTSTEP_PATHS = {
            PATH_TO_REQUEST_IN_CONFIG
    };

    //
    // path to request interface
    public final static String PATH_TO_IFC_REQUEST_IN_CONFIG = "con:soapui-project/con:interface/con:operation/con:call/con:request";
    public final static String[] REQUEST_IFC_PATHS = {
            PATH_TO_IFC_REQUEST_IN_CONFIG
    };

    //
    // path to external data : operation documentation and reporting
    public final static String PATH_TO_IFC_OPERATION_DOCUMENTATION_IN_CONFIG = "con:soapui-project/con:interface/con:definitionCache/con:part/con:content/wsdl:definitions/wsdl:portType/wsdl:operation/documentation";
    public final static String PATH_TO_REPORTING_DATA = "con:soapui-project/con:reporting/con:reportTemplates/con:data";
    public final static String[] EXTERNAL_DATA_PATHS = {
            PATH_TO_IFC_OPERATION_DOCUMENTATION_IN_CONFIG,
            PATH_TO_REPORTING_DATA
    };

    //
    // path to mock responsecontent
    public final static String PATH_TO_MOCK_RESPONSECONTENT_IN_CONFIG = "con:soapui-project/con:mockService/con:mockOperation/con:response/con:responseContent";
    public final static String[] RESPONSECONTENT_MOCK_PATHS = {
            PATH_TO_MOCK_RESPONSECONTENT_IN_CONFIG
    };

    //
    // path to response or request script
    public final static String PATH_TO_MOCK_RESPONSE_SCRIPT_IN_CONFIG = "con:soapui-project/con:testSuite/con:testCase/con:testStep/con:config/con:response/con:script";
    public final static String PATH_TO_AMF_REQUEST_SCRIPT_IN_CONFIG   = "con:soapui-project/con:testSuite/con:testCase/con:testStep/con:config/con:request/script";
    public final static String[] RESPONSE_OR_REQUEST_PATHS = {
            PATH_TO_MOCK_RESPONSE_SCRIPT_IN_CONFIG,
            PATH_TO_AMF_REQUEST_SCRIPT_IN_CONFIG
    };

    //
    // path to event handler script
    public final static String PATH_TO_EVENT_HANDLER_SCRIPT_IN_CONFIG = "con:soapui-project/con:eventHandlerType/con:script";
    public final static String[] EVENT_HANDLER_PATHS = {
        PATH_TO_EVENT_HANDLER_SCRIPT_IN_CONFIG
    };


    public final static String[][] ALL_PATHS_IN_CONFIG_BY_CATEGORY = {
            GROOVY_SCRIPT_PATHS,
            GROOVY_SCRIPT_TESTSTEP_PATHS,
            REQUEST_TESTSTEP_PATHS,
            REQUEST_IFC_PATHS,
            EXTERNAL_DATA_PATHS,
            RESPONSECONTENT_MOCK_PATHS,
            RESPONSE_OR_REQUEST_PATHS,
            EVENT_HANDLER_PATHS
    };

    public final static QName SCRIPT_QNAME = new QName("", SCRIPT_PROPERTY);
    public final static QName SCRIPT_ALT_QNAME = new QName("", SCRIPT_ALT_PROPERTY);
    public final static QName NAME_QNAME = new QName("", "name");
    public final static QName TYPE_QNAME = new QName("", "type");
    public final static QName EXTERNAL_FILENAME_BUILD_MODE_QNAME = new QName("", "externalFilenameBuildMode");
    public final static QName EXTERNAL_FILENAME_QNAME = new QName("", "externalFilename");

    public final static String REQUEST_TYPE = "request";
    public final static String GROOVY_TYPE = "groovy";
    public final static String REQUEST_RESPONSE_TYPE = "Request-Response";
    public final static String RESPONSE_CONTENT_TYPE = "responseContent";
    public final static String DOCUMENTATION_CONTENT_TYPE = "documentation";
    public final static String EXTERNAL_DATA_TYPE = "data";

    public final static String CONFIG_NODENAME = "config";
    public final static String CONFIGURATION_NODENAME = "configuration";
    public final static String SCRIPT_NODENAME = "script";
    public final static String ASSERTION_NODENAME = "assertion";
    public final static String CALL_NODENAME = "call";


    public final static String[] allPathsInConfig() {
        List<String> allPathsInConfig = new ArrayList<String>();
        for (String[] pathByCategoryList : ALL_PATHS_IN_CONFIG_BY_CATEGORY) {
            for (String path : pathByCategoryList) {
                allPathsInConfig.add(path);
            }
        }
        return allPathsInConfig.toArray(new String[allPathsInConfig.size()]);
    }
}
