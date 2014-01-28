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

package com.eviware.soapui.impl.wsdl.teststeps.assertions.basic;

import javax.xml.namespace.QName;
import com.eviware.soapui.impl.support.ContentInExternalFileSupport;
import com.eviware.soapui.impl.wsdl.actions.request.ConfigureExternalFileAction;
import com.eviware.soapui.impl.wsdl.actions.request.ReloadExternalFileAction;
import com.eviware.soapui.impl.wsdl.teststeps.*;
import com.eviware.soapui.settings.UISettings;
import com.eviware.soapui.support.action.swing.SwingActionDelegate;
import org.apache.xmlbeans.XmlCursor;
import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.TestAssertionConfig;
import com.eviware.soapui.impl.rest.RestRequestInterface;
import com.eviware.soapui.impl.support.actions.ShowOnlineHelpAction;
import com.eviware.soapui.impl.support.http.HttpRequest;
import com.eviware.soapui.impl.wsdl.panels.assertions.AssertionCategoryMapping;
import com.eviware.soapui.impl.wsdl.panels.assertions.AssertionListEntry;
import com.eviware.soapui.impl.wsdl.panels.mockoperation.WsdlMockResponseMessageExchange;
import com.eviware.soapui.impl.wsdl.panels.teststeps.support.AbstractGroovyEditorModel;
import com.eviware.soapui.impl.wsdl.panels.teststeps.support.GroovyEditor;
import com.eviware.soapui.impl.wsdl.support.HelpUrls;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestRunContext;
import com.eviware.soapui.impl.wsdl.teststeps.assertions.AbstractTestAssertionFactory;
import com.eviware.soapui.model.TestPropertyHolder;
import com.eviware.soapui.model.iface.MessageExchange;
import com.eviware.soapui.model.iface.SubmitContext;
import com.eviware.soapui.model.testsuite.Assertable;
import com.eviware.soapui.model.testsuite.AssertionError;
import com.eviware.soapui.model.testsuite.AssertionException;
import com.eviware.soapui.model.testsuite.RequestAssertion;
import com.eviware.soapui.model.testsuite.ResponseAssertion;
import com.eviware.soapui.model.testsuite.TestStep;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.components.JXToolBar;
import com.eviware.soapui.support.log.JLogList;
import com.eviware.soapui.support.scripting.SoapUIScriptEngine;
import com.eviware.soapui.support.scripting.SoapUIScriptEngineRegistry;
import com.eviware.soapui.support.xml.XmlObjectConfigurationBuilder;
import com.eviware.soapui.support.xml.XmlObjectConfigurationReader;
import com.jgoodies.forms.builder.ButtonBarBuilder;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlObject;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Assertion performed by a custom Grooy Script
 *
 * @author ole.matzura
 */

public class GroovyScriptAssertion extends WsdlMessageAssertion implements RequestAssertion, ResponseAssertion {
	public final static String GROOVY_ASSERTION_SCRIPT_PROPERTY = GroovyScriptAssertion.class.getName() + "@script";
	public final static String GROOVY_ASSERTION_SCRIPT_PROPERTY_RELOAD = GROOVY_ASSERTION_SCRIPT_PROPERTY + "Reload";
    public static final String ID = "GroovyScriptAssertion";
    public static final String LABEL = "Script Assertion";
    public static final String DESCRIPTION = "Runs a custom script to perform arbitrary validations. Applicable to any Property.";
    private String scriptText;
    private SoapUIScriptEngine scriptEngine;
    private JDialog dialog;
    private GroovyScriptAssertionPanel groovyScriptAssertionPanel;
    private String oldScriptText;

	private ContentInExternalFileSupport contentInExternalFileSupport;

        super(assertionConfig, modelItem, true, true, true, false);
	public GroovyScriptAssertion( TestAssertionConfig assertionConfig, Assertable modelItem )
	{
		super( assertionConfig, modelItem, true, true, true, false );

		if( modelItem instanceof WsdlTestStep)
		{
			contentInExternalFileSupport = new ContentInExternalFileSupport( (WsdlTestStep)modelItem, this, assertionConfig, ( ( WsdlTestStep )modelItem ).getSettings() );
			contentInExternalFileSupport.initExternalFilenameSupport();
			if( getSettings().getBoolean( UISettings.CONTENT_IN_EXTERNAL_FILE ) )
			{
				scriptText = contentInExternalFileSupport.getContent();
			}
			else
			{
				XmlObjectConfigurationReader reader = new XmlObjectConfigurationReader( getConfiguration() );
				scriptText = reader.readString( "scriptText", "" );
			}
		}
		else
		{
			XmlObjectConfigurationReader reader = new XmlObjectConfigurationReader( getConfiguration() );
			scriptText = reader.readString( "scriptText", "" );
		}

        scriptEngine = SoapUIScriptEngineRegistry.create(this);
        scriptEngine.setScript(scriptText);
    }

    @Override
    protected String internalAssertRequest(MessageExchange messageExchange, SubmitContext context)
            throws AssertionException {
        return assertScript(messageExchange, context, SoapUI.ensureGroovyLog());
    }

    private String assertScript(MessageExchange messageExchange, SubmitContext context, Logger log)
            throws AssertionException {
        try {
            scriptEngine.setVariable("context", context);
            scriptEngine.setVariable("messageExchange", messageExchange);
            scriptEngine.setVariable("log", log);
            scriptEngine.setVariable("assertion", this);

            Object result = scriptEngine.run();
            return result == null ? null : result.toString();
        } catch (Throwable e) {
            throw new AssertionException(new AssertionError(e.getMessage()));
        } finally {
            scriptEngine.clearVariables();
        }
    }

    @Override
    protected String internalAssertResponse(MessageExchange messageExchange, SubmitContext context)
            throws AssertionException {
        return assertScript(messageExchange, context, SoapUI.ensureGroovyLog());
    }

    @Override
    protected String internalAssertProperty(TestPropertyHolder source, String propertyName,
                                            MessageExchange messageExchange, SubmitContext context) throws AssertionException {
        return null;
    }

    @Override
    public boolean configure() {
        if (dialog == null) {
            buildDialog();
			this.addPropertyChangeListener( GROOVY_ASSERTION_SCRIPT_PROPERTY_RELOAD, groovyScriptAssertionPanel.editor );
        }

        oldScriptText = scriptText;
        UISupport.showDialog(dialog);
        return true;
    }

    protected void buildDialog() {
        dialog = new JDialog(UISupport.getMainFrame(), "Script Assertion", true);
        groovyScriptAssertionPanel = new GroovyScriptAssertionPanel();
        dialog.setContentPane(groovyScriptAssertionPanel);
        UISupport.initDialogActions(dialog, groovyScriptAssertionPanel.getShowOnlineHelpAction(),
                groovyScriptAssertionPanel.getDefaultButton());
        dialog.setSize(600, 500);
        dialog.setModal(true);
        dialog.pack();
    }

    protected GroovyScriptAssertionPanel getScriptAssertionPanel() {
        return groovyScriptAssertionPanel;
    }

    protected XmlObject createConfiguration() {
        XmlObjectConfigurationBuilder builder = new XmlObjectConfigurationBuilder();
        builder.add("scriptText", scriptText);
        return builder.finish();
    }

	protected XmlObject updateOrCreateConfiguration()
	{
		XmlObject configuration = getConfiguration();
		if( configuration == null )
		{
			return createConfiguration();
		}
		XmlCursor cursor = configuration.newCursor();
		String namespaceUri = cursor.namespaceForPrefix( "" );
		QName scriptTextQName = new QName( namespaceUri, "scriptText" );
		if( cursor.toChild( scriptTextQName ) )
		{
			cursor.setTextValue( scriptText );
			return configuration;
		}
		else
		{
			return createConfiguration();
		}
	}

        return scriptText;
    }

    public void setScriptText(String scriptText) {
        this.scriptText = scriptText;
        scriptEngine.setScript(scriptText);
		setConfiguration( updateOrCreateConfiguration() );

		notifyPropertyChanged( GROOVY_ASSERTION_SCRIPT_PROPERTY, oldScriptText, scriptText );
    }

    protected class GroovyScriptAssertionPanel extends JPanel {
        private GroovyEditor editor;
        private JSplitPane mainSplit;
        private JLogList logArea;
        private RunAction runAction = new RunAction();
        private Logger logger;
        private JButton okButton;
        private ShowOnlineHelpAction showOnlineHelpAction;

        public GroovyScriptAssertionPanel() {
            super(new BorderLayout());

            buildUI();
            setPreferredSize(new Dimension(600, 440));

            logger = Logger.getLogger("ScriptAssertion." + getName());
            editor.requestFocusInWindow();
        }

        public GroovyEditor getGroovyEditor() {
            return editor;
        }

        public void release() {
            editor.release();
            logger = null;
        }

        private void buildUI() {
            editor = new GroovyEditor(new ScriptStepGroovyEditorModel());

            logArea = new JLogList("Groovy Test Log");
            logArea.addLogger("ScriptAssertion." + getName(), true);
            logArea.getLogList().addMouseListener(new MouseAdapter() {

                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() < 2) {
                        return;
                    }

                    String value = logArea.getLogList().getSelectedValue().toString();
                    if (value == null) {
                        return;
                    }

                    editor.selectError(value);
                }
            });

            editor.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3),
                    editor.getBorder()));

            mainSplit = UISupport.createVerticalSplit(editor, logArea);
            mainSplit.setDividerLocation(280);
            mainSplit.setResizeWeight(0.8);
            add(mainSplit, BorderLayout.CENTER);
            add(buildToolbar(), BorderLayout.NORTH);
            add(buildStatusBar(), BorderLayout.SOUTH);
        }

        public JButton getDefaultButton() {
            return okButton;
        }

        public ShowOnlineHelpAction getShowOnlineHelpAction() {
            return showOnlineHelpAction;
        }

        private Component buildStatusBar() {
            ButtonBarBuilder builder = new ButtonBarBuilder();

            showOnlineHelpAction = new ShowOnlineHelpAction(HelpUrls.GROOVYASSERTION_HELP_URL);
            builder.addFixed(UISupport.createToolbarButton(showOnlineHelpAction));
            builder.addGlue();
            okButton = new JButton(new OkAction());
            builder.addFixed(okButton);
            builder.addRelatedGap();
            builder.addFixed(new JButton(new CancelAction()));
            builder.setBorder(BorderFactory.createEmptyBorder(0, 3, 3, 3));
            return builder.getPanel();
        }

        private JComponent buildToolbar() {
            JXToolBar toolBar = UISupport.createToolbar();
            JButton runButton = UISupport.createToolbarButton(runAction);
			JButton configureExternalFileButton = createActionButton( SwingActionDelegate.createDelegate(
					ConfigureExternalFileAction.SOAPUI_ACTION_ID, getContentInExternalFileSupport(), null, "/options.gif" ), true );
			JButton reloadExternalFileButton = createActionButton( SwingActionDelegate.createDelegate(
					ReloadExternalFileAction.SOAPUI_ACTION_ID, getContentInExternalFileSupport(), null, "/arrow_refresh.png" ), true );
            toolBar.add(runButton);
			toolBar.add( configureExternalFileButton );
			toolBar.add( reloadExternalFileButton );
            toolBar.add(Box.createHorizontalGlue());
            JLabel label = new JLabel("<html>Script is invoked with <code>log</code>, <code>context</code> "
                    + "and <code>messageExchange</code> variables</html>");
            label.setToolTipText(label.getText());
            label.setMaximumSize(label.getPreferredSize());

            toolBar.addFixed(label);
            toolBar.addSpace(3);

            return toolBar;
        }

        private final class OkAction extends AbstractAction {
            public OkAction() {
                super("OK");
            }

            public void actionPerformed(ActionEvent e) {
                dialog.setVisible(false);
                setScriptText(editor.getEditArea().getText());
            }
        }

        private final class CancelAction extends AbstractAction {
            public CancelAction() {
                super("Cancel");
            }

            public void actionPerformed(ActionEvent e) {
                dialog.setVisible(false);
                editor.getEditArea().setText(oldScriptText);
            }
        }

        private class ScriptStepGroovyEditorModel extends AbstractGroovyEditorModel {
            public ScriptStepGroovyEditorModel() {
                super(new String[]{"log", "context", "messageExchange"}, getAssertable().getModelItem(), "Assertion");
            }

            public Action getRunAction() {
                return runAction;
            }

            public String getScript() {
                return getScriptText();
            }

            public void setScript(String text) {

            }
        }

        private class RunAction extends AbstractAction {
            public RunAction() {
                putValue(Action.SMALL_ICON, UISupport.createImageIcon("/run_groovy_script.gif"));
                putValue(Action.SHORT_DESCRIPTION,
                        "Runs this assertion script against the last messageExchange with a mock testContext");
            }

            public void actionPerformed(ActionEvent event) {
                TestStep testStep = getAssertable().getTestStep();
                MessageExchange exchange = null;

                if (testStep instanceof WsdlTestRequestStep) {
                    WsdlTestRequestStep testRequestStep = (WsdlTestRequestStep) testStep;
                    exchange = new WsdlResponseMessageExchange(testRequestStep.getTestRequest());
                    ((WsdlResponseMessageExchange) exchange).setResponse(testRequestStep.getTestRequest().getResponse());
                } else if (testStep instanceof RestTestRequestStepInterface) {
                    RestTestRequestStepInterface testRequestStep = (RestTestRequestStepInterface) testStep;
                    exchange = new RestResponseMessageExchange((RestRequestInterface) testRequestStep.getTestRequest());
                    ((RestResponseMessageExchange) exchange)
                            .setResponse(((HttpRequest) testRequestStep.getTestRequest()) /* cast makes code compile with Java 6 */
                                    .getResponse());
                } else if (testStep instanceof HttpTestRequestStepInterface) {
                    HttpTestRequestStepInterface testRequestStep = (HttpTestRequestStepInterface) testStep;
                    exchange = new HttpResponseMessageExchange(testRequestStep.getTestRequest());
                    ((HttpResponseMessageExchange) exchange)
                            .setResponse(((HttpRequest) testRequestStep.getTestRequest()) /* cast makes code compile with Java 6 */
                                    .getResponse());
                } else if (testStep instanceof WsdlMockResponseTestStep) {
                    WsdlMockResponseTestStep mockResponseStep = (WsdlMockResponseTestStep) testStep;
                    exchange = new WsdlMockResponseMessageExchange(mockResponseStep.getMockResponse());
                }

                try {
                    setScriptText(editor.getEditArea().getText());
                    String result = assertScript(exchange, new WsdlTestRunContext(testStep), logger);
                    UISupport
                            .showInfoMessage("Script Assertion Passed" + ((result == null) ? "" : ": [" + result + "]"));
                } catch (AssertionException e) {
                    UISupport.showErrorMessage(e.getMessage());
                } catch (Exception e) {
                    SoapUI.logError(e);
                    UISupport.showErrorMessage(e.getMessage());
                }

                editor.requestFocusInWindow();
            }
        }
	}

	public static JButton createActionButton( Action action, boolean enabled )
	{
		JButton button = UISupport.createToolbarButton( action, enabled );
		action.putValue( Action.NAME, null );
		return button;
    }

    @Override
    public void release() {
        super.release();
        scriptEngine.release();

        if (groovyScriptAssertionPanel != null) {
            groovyScriptAssertionPanel.release();
        }
    }

    public static class Factory extends AbstractTestAssertionFactory {
        public Factory() {
            super(GroovyScriptAssertion.ID, GroovyScriptAssertion.LABEL, GroovyScriptAssertion.class);
        }

        @Override
        public String getCategory() {
            return AssertionCategoryMapping.SCRIPT_CATEGORY;
        }

        @Override
        public Class<? extends WsdlMessageAssertion> getAssertionClassType() {
            return GroovyScriptAssertion.class;
        }

        @Override
        public AssertionListEntry getAssertionListEntry() {
            return new AssertionListEntry(GroovyScriptAssertion.ID, GroovyScriptAssertion.LABEL,
                    GroovyScriptAssertion.DESCRIPTION);
        }
	}

	public ContentInExternalFileSupport getContentInExternalFileSupport()
	{
		return contentInExternalFileSupport;
	}

	public void setContentInExternalFileSupport( ContentInExternalFileSupport contentInExternalFileSupport )
	{
		this.contentInExternalFileSupport = contentInExternalFileSupport;
    }
}
