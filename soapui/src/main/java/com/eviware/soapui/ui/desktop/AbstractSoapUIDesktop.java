/*
 *  soapUI, copyright (C) 2004-2012 smartbear.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */

package com.eviware.soapui.ui.desktop;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlGroovyScriptTestStep;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestRequestStep;
import com.eviware.soapui.model.ModelItem;
import com.eviware.soapui.model.iface.Interface;
import com.eviware.soapui.model.iface.Operation;
import com.eviware.soapui.model.iface.Request;
import com.eviware.soapui.model.mock.MockOperation;
import com.eviware.soapui.model.mock.MockResponse;
import com.eviware.soapui.model.mock.MockService;
import com.eviware.soapui.model.project.Project;
import com.eviware.soapui.model.support.InterfaceListenerAdapter;
import com.eviware.soapui.model.support.MockServiceListenerAdapter;
import com.eviware.soapui.model.support.ProjectListenerAdapter;
import com.eviware.soapui.model.support.TestSuiteListenerAdapter;
import com.eviware.soapui.model.support.WorkspaceListenerAdapter;
import com.eviware.soapui.model.testsuite.LoadTest;
import com.eviware.soapui.model.testsuite.TestCase;
import com.eviware.soapui.model.testsuite.TestStep;
import com.eviware.soapui.model.testsuite.TestSuite;
import com.eviware.soapui.model.workspace.Workspace;
import com.eviware.soapui.security.SecurityTest;
import com.eviware.soapui.support.action.swing.ActionList;

import java.util.HashSet;
import java.util.Set;

/**
 * Abstract SoapUIDesktop implementation for extension
 * 
 * @author ole.matzura
 */

public abstract class AbstractSoapUIDesktop implements SoapUIDesktop
{
	private final Workspace workspace;
	private final InternalProjectListener projectListener = new InternalProjectListener();
	private final InternalInterfaceListener interfaceListener = new InternalInterfaceListener();
	private final InternalTestSuiteListener testSuiteListener = new InternalTestSuiteListener();
	private final InternalMockServiceListener mockServiceListener = new InternalMockServiceListener();
	private Set<DesktopListener> listeners = new HashSet<DesktopListener>();
	private InternalWorkspaceListener workspaceListener = new InternalWorkspaceListener();

	public AbstractSoapUIDesktop( Workspace workspace )
	{
		this.workspace = workspace;

		initListeners();
	}

	private void initListeners()
	{
		workspace.addWorkspaceListener( workspaceListener );

		for( int c = 0; c < workspace.getProjectCount(); c++ )
		{
			listenToProject( workspace.getProjectAt( c ) );
		}
	}

	public ActionList getActions()
	{
		return null;
	}

	private void listenToProject( Project project )
	{
		project.addProjectListener( projectListener );

		for( int i = 0; i < project.getInterfaceCount(); i++ )
		{
			project.getInterfaceAt( i ).addInterfaceListener( interfaceListener );
		}

		for( int i = 0; i < project.getTestSuiteCount(); i++ )
		{
			project.getTestSuiteAt( i ).addTestSuiteListener( testSuiteListener );
		}

		for( int i = 0; i < project.getMockServiceCount(); i++ )
		{
			project.getMockServiceAt( i ).addMockServiceListener( mockServiceListener );
		}
	}

	public void addDesktopListener( DesktopListener listener )
	{
		listeners.add( listener );
	}

	public void removeDesktopListener( DesktopListener listener )
	{
		listeners.remove( listener );
	}

	public void closeDependantPanels( ModelItem modelItem )
	{
		DesktopPanel[] panels = getDesktopPanels();

		for( int c = 0; c < panels.length; c++ )
		{
			if( panels[c].dependsOn( modelItem ) )
			{
				closeDesktopPanel( panels[c] );
			}
		}
	}

	protected void fireDesktopPanelCreated( DesktopPanel desktopPanel )
	{
		if( !listeners.isEmpty() )
		{
			DesktopListener[] array = listeners.toArray( new DesktopListener[listeners.size()] );
			for( DesktopListener listener : array )
				listener.desktopPanelCreated( desktopPanel );
		}
	}

	protected void fireDesktopPanelSelected( DesktopPanel desktopPanel )
	{
		if( !listeners.isEmpty() )
		{
			DesktopListener[] array = listeners.toArray( new DesktopListener[listeners.size()] );
			for( DesktopListener listener : array )
				listener.desktopPanelSelected( desktopPanel );
		}
	}

	protected void fireDesktopPanelClosed( DesktopPanel desktopPanel )
	{
		if( !listeners.isEmpty() )
		{
			DesktopListener[] array = listeners.toArray( new DesktopListener[listeners.size()] );
			for( DesktopListener listener : array )
				listener.desktopPanelClosed( desktopPanel );
		}
	}

	private class InternalWorkspaceListener extends WorkspaceListenerAdapter
	{
		public void projectRemoved( Project project )
		{
			project.removeProjectListener( projectListener );
			closeDependantPanels( project );
		}

		public void projectAdded( Project project )
		{
			listenToProject( project );
		}
	}

	private class InternalProjectListener extends ProjectListenerAdapter
	{
		public void interfaceRemoved( Interface iface )
		{
			iface.removeInterfaceListener( interfaceListener );
			closeDependantPanels( iface );
		}

		public void testSuiteRemoved( TestSuite testSuite )
		{
			testSuite.removeTestSuiteListener( testSuiteListener );
			closeDependantPanels( testSuite );
		}

		public void interfaceAdded( Interface iface )
		{
			iface.addInterfaceListener( interfaceListener );
		}

		public void testSuiteAdded( TestSuite testSuite )
		{
			testSuite.addTestSuiteListener( testSuiteListener );
		}

		public void mockServiceAdded( MockService mockService )
		{
			mockService.addMockServiceListener( mockServiceListener );
		}

		public void mockServiceRemoved( MockService mockService )
		{
			mockService.removeMockServiceListener( mockServiceListener );
			closeDependantPanels( mockService );
		}

        public void beforeSave(Project project) {
            SoapUI.log.info("In beforeSave(" + project.getName()+")");
            for (TestSuite testSuite : project.getTestSuiteList()) {
                for (TestCase testCase : testSuite.getTestCaseList()) {
                    for (TestStep testStep : testCase.getTestStepList()) {
                        SoapUI.log.info("beforeSave() : checking test step '"+ testStep.getName() + "' for external file attribute...");
                        SoapUI.log.info("beforeSave() :    testStep.class : " + testStep.getClass().getSimpleName());
                        if (testStep instanceof WsdlGroovyScriptTestStep) {
                            WsdlGroovyScriptTestStep step = (WsdlGroovyScriptTestStep) testStep;
                            step.getTestRequestStepInExternalFileSupport().saveToExternalFile();
                        } else if (testStep instanceof WsdlTestRequestStep) {
                            WsdlTestRequestStep step = (WsdlTestRequestStep) testStep;
                            step.getTestRequestStepInExternalFileSupport().saveToExternalFile();
                        }
                    }
                }
            }
        }
    }

	private class InternalInterfaceListener extends InterfaceListenerAdapter
	{
		public void operationRemoved( Operation operation )
		{
			closeDependantPanels( operation );
		}

		public void requestRemoved( Request request )
		{
			closeDependantPanels( request );
		}
	}

	private class InternalTestSuiteListener extends TestSuiteListenerAdapter
	{
		public void testCaseRemoved( TestCase testCase )
		{
			closeDependantPanels( testCase );
		}

		public void testStepRemoved( TestStep testStep, int index )
		{
			closeDependantPanels( testStep );
		}

		public void loadTestRemoved( LoadTest loadTest )
		{
			closeDependantPanels( loadTest );
		}

		public void securityTestRemoved( SecurityTest securityTest )
		{
			closeDependantPanels( securityTest );
		}
	}

	private class InternalMockServiceListener extends MockServiceListenerAdapter
	{
		public void mockOperationRemoved( MockOperation operation )
		{
			closeDependantPanels( operation );
		}

		public void mockResponseRemoved( MockResponse request )
		{
			closeDependantPanels( request );
		}
	}

	public void release()
	{
		for( int c = 0; c < workspace.getProjectCount(); c++ )
		{
			Project project = workspace.getProjectAt( c );
			project.removeProjectListener( projectListener );

			for( int i = 0; i < project.getInterfaceCount(); i++ )
			{
				project.getInterfaceAt( i ).removeInterfaceListener( interfaceListener );
			}

			for( int i = 0; i < project.getTestSuiteCount(); i++ )
			{
				project.getTestSuiteAt( i ).removeTestSuiteListener( testSuiteListener );
			}

			for( int i = 0; i < project.getMockServiceCount(); i++ )
			{
				project.getMockServiceAt( i ).removeMockServiceListener( mockServiceListener );
			}
		}

		workspace.removeWorkspaceListener( workspaceListener );
	}

	public void init()
	{
	}
}
