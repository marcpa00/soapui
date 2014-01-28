package com.eviware.soapui.impl.wsdl.teststeps;

import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.WsdlTestSuite;

/**
 * Enum for script category, i.e. on what this script is applied.
 */
public enum ScriptCategory {
	PROJECT_AFTER_LOAD, PROJECT_AFTER_RUN, PROJECT_BEFORE_SAVE, PROJECT_BEFORE_RUN,
	PROJECT_TEST_SUITE_SETUP, PROJECT_TEST_SUITE_TEARDOWN,
	TEST_SUITE_SETUP, TEST_SUITE_TEARDOWN,
	TEST_CASE_TEARDOWN, TEST_CASE_SETUP,
	TEST_STEP, TEST_STEP_ASSERTION;

	public String getDefaultName() {
		switch(this) {
			case PROJECT_AFTER_LOAD:
				return "afterLoadScript";
			case PROJECT_AFTER_RUN:
				return "afterRunScript";
			case PROJECT_BEFORE_SAVE:
				return "beforeSaveScript";
			case PROJECT_BEFORE_RUN:
				return "beforeRunScript";
			case PROJECT_TEST_SUITE_SETUP:
			case TEST_SUITE_SETUP:
			case TEST_CASE_SETUP:
				return "setupScript";
			case PROJECT_TEST_SUITE_TEARDOWN:
			case TEST_SUITE_TEARDOWN:
			case TEST_CASE_TEARDOWN:
				return "tearDownScript";
			case TEST_STEP:
				return "step";
			case TEST_STEP_ASSERTION:
				return "assertionScript";
			default:
				return "script";
		}
	}

	public String getPropertyNameForScriptCategory() {
		switch(this) {
			case PROJECT_AFTER_LOAD:
				return WsdlProject.AFTER_LOAD_SCRIPT_PROPERTY;
			case PROJECT_AFTER_RUN:
				return WsdlProject.AFTER_RUN_SCRIPT_PROPERTY;
			case PROJECT_BEFORE_SAVE:
				return WsdlProject.BEFORE_SAVE_SCRIPT_PROPERTY;
			case PROJECT_BEFORE_RUN:
				return WsdlProject.BEFORE_RUN_SCRIPT_PROPERTY;
			case PROJECT_TEST_SUITE_SETUP:
			case TEST_SUITE_SETUP:
			case TEST_CASE_SETUP:
				return "setupScript";
			case PROJECT_TEST_SUITE_TEARDOWN:
			case TEST_SUITE_TEARDOWN:
			case TEST_CASE_TEARDOWN:
				return "tearDownScript";
			case TEST_STEP:
				return "step";
			case TEST_STEP_ASSERTION:
				return "assertionScript";
			default:
				return "script";
		}
	}

}
