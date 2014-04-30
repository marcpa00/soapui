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

package com.eviware.soapui.support.components;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.impl.support.ContentInExternalFileSupport;
import com.eviware.soapui.impl.support.actions.ShowOnlineHelpAction;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.WsdlTestSuite;
import com.eviware.soapui.impl.wsdl.actions.request.ConfigureExternalFileAction;
import com.eviware.soapui.impl.wsdl.actions.request.ReloadExternalFileAction;
import com.eviware.soapui.impl.wsdl.panels.teststeps.support.GroovyEditor;
import com.eviware.soapui.impl.wsdl.panels.teststeps.support.GroovyEditorModel;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.model.testsuite.LoadTest;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.action.swing.SwingActionDelegate;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static com.eviware.soapui.impl.wsdl.teststeps.Script.*;

public class GroovyEditorComponent extends JPanel implements PropertyChangeListener {
    private GroovyEditor editor;
    private JButton insertCodeButton;
    private Action runAction;
    private JXToolBar toolBar;
    private final GroovyEditorModel editorModel;
    private final String helpUrl;

    public GroovyEditorComponent(GroovyEditorModel editorModel, String helpUrl) {
        super(new BorderLayout());
        this.editorModel = editorModel;
        this.helpUrl = helpUrl;

        editor = new GroovyEditor(editorModel);
        editor.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3),
                editor.getBorder()));
        add(editor, BorderLayout.CENTER);
        buildToolbar(editorModel, helpUrl);

        editorModel.addPropertyChangeListener(this);
    }

    public GroovyEditor getEditor() {
        return editor;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        editor.setEnabled(enabled);
        if (runAction != null) {
            runAction.setEnabled(enabled);
        }

        insertCodeButton.setEnabled(enabled);
    }

    protected void buildToolbar(GroovyEditorModel editorModel, String helpUrl) {
        if (toolBar == null) {
            toolBar = UISupport.createSmallToolbar();
        } else {
            remove(toolBar);
            toolBar.removeAll();
        }

        runAction = editorModel.getRunAction();
        if (runAction != null) {
            JButton runButton = UISupport.createToolbarButton(runAction);
            if (runButton.getIcon() == null) {
                runButton.setIcon(UISupport.createImageIcon("/run_testcase.gif"));
            }

            if (runButton.getToolTipText() == null) {
                runButton.setToolTipText("Runs this script");
            }

            toolBar.add(runButton);
            toolBar.addRelatedGap();
        }

        ContentInExternalFileSupport contentInExternalFileSupport = getContentInExternalFileSupportForEditorModel(editorModel);

        JButton configureExternalFileButton = createActionButton(SwingActionDelegate.createDelegate(
                ConfigureExternalFileAction.SOAPUI_ACTION_ID, contentInExternalFileSupport, null, "/options.gif"), true);
        JButton reloadExternalFileButton = createActionButton(SwingActionDelegate.createDelegate(
                ReloadExternalFileAction.SOAPUI_ACTION_ID, contentInExternalFileSupport, null, "/arrow_refresh.png"), true);

        toolBar.add(configureExternalFileButton);
        toolBar.add(reloadExternalFileButton);

        toolBar.addRelatedGap();

        if (insertCodeButton == null) {
            insertCodeButton = new JButton(new InsertCodeAction());
            insertCodeButton.setIcon(UISupport.createImageIcon("/down_arrow.gif"));
            insertCodeButton.setHorizontalTextPosition(SwingConstants.LEFT);
        }

        toolBar.addFixed(insertCodeButton);

        toolBar.add(Box.createHorizontalGlue());

        String[] args = editorModel.getKeywords();
        if (args != null && args.length > 0)

        {
            String scriptName = editorModel.getScriptName();
            if (scriptName == null) {
                scriptName = "";
            } else {
                scriptName = scriptName.trim() + " ";
            }

            StringBuilder text = new StringBuilder("<html>" + scriptName + "Script is invoked with ");
            for (int c = 0; c < args.length; c++) {
                if (c > 0) {
                    text.append(", ");
                }

                text.append("<font face=\"courier\">").append(args[c]).append("</font>");
            }
            text.append(" variables</html>");

            JLabel label = new JLabel(text.toString());
            label.setToolTipText(label.getText());
            label.setMaximumSize(label.getPreferredSize());

            toolBar.addFixed(label);
            toolBar.addUnrelatedGap();
        }

        if (helpUrl != null)

        {
            toolBar.addFixed(UISupport.createToolbarButton(new ShowOnlineHelpAction(helpUrl)));
        }

        add(toolBar, BorderLayout.NORTH);

        revalidate();

        repaint();
    }

    public class InsertCodeAction extends AbstractAction {
        public InsertCodeAction() {
            super("Edit");
            putValue(Action.SHORT_DESCRIPTION, "Inserts code at caret");
        }

        public void actionPerformed(ActionEvent e) {
            JPopupMenu popup = editor.getEditArea().getComponentPopupMenu();
            popup.show(insertCodeButton, insertCodeButton.getWidth() / 2, insertCodeButton.getHeight() / 2);
        }

    }

    public void release() {
        editorModel.removePropertyChangeListener(this);
        getEditor().release();
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if (!evt.getPropertyName().equals(SCRIPT_PROPERTY)) {
            buildToolbar(editorModel, helpUrl);
        }

        // also delegate event to editor
        if (eventIsApplicableToEditorModel(evt)) {
            editor.propertyChange(evt);
        }
    }

    private boolean eventIsApplicableToEditorModel(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();

        boolean isApplicable = false;
        if (propertyName.equals(WsdlProject.AFTER_LOAD_SCRIPT_PROPERTY_RELOAD) && editorModel.getScriptName().equals("Load")) {
            isApplicable = true;
        }
        if (propertyName.equals(WsdlProject.BEFORE_SAVE_SCRIPT_PROPERTY_RELOAD) && editorModel.getScriptName().equals("Save")) {
            isApplicable = true;
        }
        if (propertyName.equals(WsdlProject.BEFORE_RUN_SCRIPT_PROPERTY_RELOAD) && editorModel.getScriptName().equals("Setup")) {
            isApplicable = true;
        }
        if (propertyName.equals(WsdlProject.AFTER_RUN_SCRIPT_PROPERTY_RELOAD) && editorModel.getScriptName().equals("TearDown")) {
            isApplicable = true;
        }
        if (propertyName.equals(WsdlTestSuite.SETUP_SCRIPT_PROPERTY_RELOAD) && editorModel.getScriptName().equals("Setup")) {
            isApplicable = true;
        }
        if (propertyName.equals(WsdlTestSuite.TEARDOWN_SCRIPT_PROPERTY_RELOAD) && editorModel.getScriptName().equals("TearDown")) {
            isApplicable = true;
        }
        if (propertyName.equals(WsdlTestCase.SETUP_SCRIPT_PROPERTY_RELOAD) && editorModel.getScriptName().equals("Setup")) {
            isApplicable = true;
        }
        if (propertyName.equals(WsdlTestCase.TEARDOWN_SCRIPT_PROPERTY_RELOAD) && editorModel.getScriptName().equals("TearDown")) {
            isApplicable = true;
        }
        if (propertyName.equals(SCRIPT_PROPERTY) && editorModel.getScriptName().equals("Assertion")) {
            isApplicable = true;
        }

        SoapUI.log.debug("eventIsApplicableToEditorModel( evt : { propName : " + propertyName + ", editorModel.scriptName : " + editorModel.getScriptName() + " } ) : " + isApplicable);

        return isApplicable;
    }

    public static JButton createActionButton(Action action, boolean enabled) {
        JButton button = UISupport.createToolbarButton(action, enabled);
        action.putValue(Action.NAME, null);
        return button;
    }

    private ContentInExternalFileSupport getContentInExternalFileSupportForEditorModel(GroovyEditorModel editorModel) {
        if (editorModel == null) {
            return null;
        }
        if (editorModel.getScriptName().equals("Load")) {
            if (editorModel.getModelItem() instanceof WsdlProject) {
                return ((WsdlProject) editorModel.getModelItem()).getAfterLoadContentInExternalFile();
            }
        }
        if (editorModel.getScriptName().equals("Save")) {
            if (editorModel.getModelItem() instanceof WsdlProject) {
                return ((WsdlProject) editorModel.getModelItem()).getBeforeSaveContentInExternalFile();
            }
        }
        if (editorModel.getScriptName().equals("Setup")) {
            if (editorModel.getModelItem() instanceof WsdlProject) {
                return ((WsdlProject) editorModel.getModelItem()).getBeforeRunContentInExternalFile();
            }
            if (editorModel.getModelItem() instanceof WsdlTestSuite) {
                return ((WsdlTestSuite) editorModel.getModelItem()).getSetupScriptContentInExternalFile();
            }
            if (editorModel.getModelItem() instanceof WsdlTestCase) {
                return ((WsdlTestCase) editorModel.getModelItem()).getSetupScriptContentInExternalFile();
            }
            //if( editorModel.getModelItem() instanceof LoadTest )
            //{
            //	return ( ( LoadTest)editorModel.getModelItem() ).getSetupScriptContentInExternalFile();
            //}
            //if( editorModel.getModelItem() instanceof SecurityTest )
            //{
            //	return ( ( SecurityTest)editorModel.getModelItem() ).getSetupScriptContentInExternalFile();
            //}
        }
        if (editorModel.getScriptName().equals("TearDown")) {
            if (editorModel.getModelItem() instanceof WsdlProject) {
                return ((WsdlProject) editorModel.getModelItem()).getAfterRunContentInExternalFile();
            }

            if (editorModel.getModelItem() instanceof WsdlTestSuite) {
                return ((WsdlTestSuite) editorModel.getModelItem()).getTearDownScriptContentInExternalFile();
            }
            if (editorModel.getModelItem() instanceof WsdlTestCase) {
                return ((WsdlTestCase) editorModel.getModelItem()).getTearDownScriptContentInExternalFile();
            }
            //if( editorModel.getModelItem() instanceof LoadTest )
            //{
            //	return ( ( LoadTest)editorModel.getModelItem() ).getTearDownScriptContentInExternalFile();
            //}
            //if( editorModel.getModelItem() instanceof SecurityTest )
            //{
            //	return ( ( SecurityTest)editorModel.getModelItem() ).getTearDownScriptContentInExternalFile();
            //}
        }
        SoapUI.log.debug("Don't know how to get to the ContentInExternalFile for an editor with script name '" + editorModel.getScriptName() + "'");
        return null;
    }
}
