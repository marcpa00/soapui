package com.eviware.soapui.impl.support;

import javax.xml.namespace.QName;

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

    public final static String PATH_TO_AFTER_LOAD_SCRIPT_IN_CONFIG = "con:soapui-project/con:afterLoadScript";
    public final static String PATH_TO_BEFORE_SAVE_SCRIPT_IN_CONFIG = "con:soapui-project/con:beforeSaveScript";
    public final static String PATH_TO_REPORT_SCRIPT_IN_CONFIG = "con:soapui-project/con:reportScript";
    public final static String PATH_TO_BEFORE_RUN_SCRIPT_IN_CONFIG = "con:soapui-project/con:beforeRunScript";
    public final static String PATH_TO_AFTER_RUN_SCRIPT_IN_CONFIG = "con:soapui-project/con:afterRunScript";

    public final static String PATH_TO_SCRIPT_IN_CONFIG = "con:soapui-project/con:testSuite/con:testCase/con:testStep/con:config/script";
    public final static String PATH_TO_REQUEST_IN_CONFIG     = "con:soapui-project/con:testSuite/con:testCase/con:testStep/con:config/con:request";
    public final static String PATH_TO_IFC_REQUEST_IN_CONFIG = "con:soapui-project/con:interface/con:operation/con:call/con:request";

    public final static String PATH_TO_TESTSUITE_SETUP_SCRIPT_IN_CONFIG = "con:soapui-project/con:testSuite/con:setupScript";
    public final static String PATH_TO_TESTSUITE_TEARDOWN_SCRIPT_IN_CONFIG = "con:soapui-project/con:testSuite/con:tearDownScript";
    public final static String PATH_TO_TESTSUITE_REPORT_SCRIPT_IN_CONFIG = "con:soapui-project/con:testSuite/con:reportScript";

    public final static String PATH_TO_TESTCASE_SETUP_SCRIPT_IN_CONFIG = "con:soapui-project/con:testSuite/con:testCase/con:setupScript";
    public final static String PATH_TO_TESTCASE_TEARDOWN_SCRIPT_IN_CONFIG = "con:soapui-project/con:testSuite/con:testCase/con:tearDownScript";
    public final static String PATH_TO_TESTCASE_REPORT_SCRIPT_IN_CONFIG = "con:soapui-project/con:testSuite/con:testCase/con:reportScript";

    public final static String PATH_TO_LOADTEST_SETUP_SCRIPT_IN_CONFIG = "con:soapui-project/con:loadTest/con:setupScript";
    public final static String PATH_TO_LOADTEST_TEARDOWN_SCRIPT_IN_CONFIG = "con:soapui-project/con:loadTest/con:tearDownScript";
    public final static String PATH_TO_LOADTEST_REPORT_SCRIPT_IN_CONFIG = "con:soapui-project/con:loadTest/con:reportScript";

    public final static String PATH_TO_SECURITY_SETUP_SCRIPT_IN_CONFIG = "con:soapui-project/con:testSuite/con:testCase/con:securityTest/con:setupScript";
    public final static String PATH_TO_SECURITY_TEARDOWN_SCRIPT_IN_CONFIG = "con:soapui-project/con:testSuite/con:testCase/con:securityTest/con:tearDownScript";
    public final static String PATH_TO_SECURITY_REPORT_SCRIPT_IN_CONFIG = "con:soapui-project/con:testSuite/con:testCase/con:securityTest/con:reportScript";

    public final static String PATH_TO_MOCK_SERVICE_START_SCRIPT_IN_CONFIG = "con:soapui-project/con:mockService/con:startScript";
    public final static String PATH_TO_MOCK_SERVICE_STOP_SCRIPT_IN_CONFIG = "con:soapui-project/con:mockService/con:stopScript";
    public final static String PATH_TO_MOCK_SERVICE_ON_REQUEST_SCRIPT_IN_CONFIG = "con:soapui-project/con:mockService/con:onRequestScript";
    public final static String PATH_TO_MOCK_SERVICE_AFTER_REQUEST_SCRIPT_IN_CONFIG = "con:soapui-project/con:mockService/con:afterRequestScript";

    public final static String PATH_TO_MOCK_RESPONSE_SCRIPT_IN_CONFIG = "con:soapui-project/con:testSuite/con:testCase/con:testStep/con:config/con:response/con:script";

    public final static String PATH_TO_EVENT_HANDLER_SCRIPT_IN_CONFIG = "con:soapui-project/con:eventHandlerType/con:script";

    public final static String PATH_TO_AMF_REQUEST_SCRIPT_IN_CONFIG = "con:soapui-project/con:testSuite/con:testCase/con:testStep/con:config/con:request/script";

    public final static String PATH_TO_GROOVY_SCRIPT_ASSERTION_IN_CONFIG = "con:soapui-project/con:testSuite/con:testCase/con:testStep/con:config/con:assertion/con:configuration/scriptText";
    public final static String PATH_TO_GROOVY_SCRIPT_ASSERTION_2_IN_CONFIG = "con:soapui-project/con:testSuite/con:testCase/con:testStep/con:config/con:request/con:assertion/con:configuration/scriptText";

    public final static String[] ALL_PATHS_IN_CONFIG = {
            PATH_TO_AFTER_LOAD_SCRIPT_IN_CONFIG,
            PATH_TO_BEFORE_SAVE_SCRIPT_IN_CONFIG,
            PATH_TO_REPORT_SCRIPT_IN_CONFIG,
            PATH_TO_BEFORE_RUN_SCRIPT_IN_CONFIG,
            PATH_TO_AFTER_RUN_SCRIPT_IN_CONFIG,
            PATH_TO_SCRIPT_IN_CONFIG,
            PATH_TO_REQUEST_IN_CONFIG,
            PATH_TO_IFC_REQUEST_IN_CONFIG,
            PATH_TO_TESTSUITE_SETUP_SCRIPT_IN_CONFIG,
            PATH_TO_TESTSUITE_TEARDOWN_SCRIPT_IN_CONFIG,
            PATH_TO_TESTSUITE_REPORT_SCRIPT_IN_CONFIG,
            PATH_TO_TESTCASE_SETUP_SCRIPT_IN_CONFIG,
            PATH_TO_TESTCASE_TEARDOWN_SCRIPT_IN_CONFIG,
            PATH_TO_TESTCASE_REPORT_SCRIPT_IN_CONFIG,
            PATH_TO_LOADTEST_SETUP_SCRIPT_IN_CONFIG,
            PATH_TO_LOADTEST_TEARDOWN_SCRIPT_IN_CONFIG,
            PATH_TO_LOADTEST_REPORT_SCRIPT_IN_CONFIG,
            PATH_TO_SECURITY_SETUP_SCRIPT_IN_CONFIG,
            PATH_TO_SECURITY_TEARDOWN_SCRIPT_IN_CONFIG,
            PATH_TO_SECURITY_REPORT_SCRIPT_IN_CONFIG,
            PATH_TO_MOCK_SERVICE_START_SCRIPT_IN_CONFIG,
            PATH_TO_MOCK_SERVICE_STOP_SCRIPT_IN_CONFIG,
            PATH_TO_MOCK_SERVICE_ON_REQUEST_SCRIPT_IN_CONFIG,
            PATH_TO_MOCK_SERVICE_AFTER_REQUEST_SCRIPT_IN_CONFIG,
            PATH_TO_MOCK_RESPONSE_SCRIPT_IN_CONFIG,
            PATH_TO_EVENT_HANDLER_SCRIPT_IN_CONFIG,
            PATH_TO_AMF_REQUEST_SCRIPT_IN_CONFIG,
            PATH_TO_GROOVY_SCRIPT_ASSERTION_IN_CONFIG,
            PATH_TO_GROOVY_SCRIPT_ASSERTION_2_IN_CONFIG
    };

    public final static QName SCRIPT_QNAME = new QName("", SCRIPT_PROPERTY);
    public final static QName SCRIPT_ALT_QNAME = new QName("", SCRIPT_ALT_PROPERTY);
    public final static QName NAME_QNAME = new QName("", "name");
    public final static QName TYPE_QNAME = new QName("", "type");
    public final static QName EXTERNAL_FILENAME_BUILD_MODE_QNAME = new QName("", "externalFilenameBuildMode");
    public final static QName EXTERNAL_FILENAME_QNAME = new QName("", "externalFilename");

    public final static String REQUEST_TYPE = "request";
    public final static String GROOVY_TYPE = "groovy";
}
