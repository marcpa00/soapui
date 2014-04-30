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

package com.eviware.soapui.actions;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.model.settings.Settings;
import com.eviware.soapui.settings.UISettings;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.components.SimpleForm;
import com.eviware.soapui.support.types.StringToStringMap;
import com.eviware.soapui.ui.desktop.DesktopRegistry;

import javax.swing.JCheckBox;
import javax.swing.JTextField;
import javax.swing.ToolTipManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Preferences class for UISettings
 *
 * @author ole.matzura
 */

public class UIPrefs implements Prefs {
    public static final String CLOSE_PROJECTS = "Close Projects";
    public static final String ORDER_PROJECTS = "Order Projects";
    public static final String ORDER_SERVICES = "Order Services";
    public static final String ORDER_REQUESTS = "Order Requests";
    public static final String SHOW_DESCRIPTIONS = "Show Descriptions";
    public static final String CREATE_BACKUP = "Create Backup";
    public static final String BACKUP_FOLDER = "Backup Folder";
    public static final String DESKTOP_TYPE = "Desktop Type";
    public static final String MRU_PANEL_SELECTOR = "Select most recently used desktop panel on close";
    public static final String NATIVE_LAF = "Native LF";
    public static final String ENABLE_GROOVY_LOG = "Do not disable Groovy Log";
    public static final String SHOW_LOGS_AT_STARTUP = "Show Log Tabs";
    public static final String AUTOSAVE_INTERVAL = "AutoSave Interval";
    public static final String AUTOSAVE_ONEXIT = "Save projects on exit";
    public static final String SHOW_STARTUP_PAGE = "Show Startup Page";
    public static final String LINEBREAK = "Normalize line-break";
    public static final String GC_INTERVAL = "Garbage Collection Interval (s)";
    public static final String RAW_RESPONSE_MESSAGE_SIZE = "Size of Raw Response Message to Show";
    public static final String RAW_REQUEST_MESSAGE_SIZE = "Size of Raw Request Message to Show";
    public static final String WRAP_RAW_MESSAGES = "Wrap content in Raw Message Viewers";
    public static final String DISABLE_TOOLTIPS = "Disable Tooltips";
    public static final String CONTENT_IN_EXTERNAL_FILE = "Content in external file";
    public static final String ALSO_KEEP_IN_PROJECT_WHEN_CONTENT_IN_EXTERNAL_FILE = "Also keep content in project file";
    public static final String AUTO_CONVERT_CONTENT_TO_USE_EXTERNAL_FILE = "Convert to content in external file";

    private SimpleForm editorForm;
    private final String title;
    private JCheckBox backupCheckBox;
    private JTextField backupFolder;

    public UIPrefs(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public SimpleForm getForm() {
        if (editorForm == null) {
            editorForm = new SimpleForm();
            editorForm.addSpace(5);
            editorForm.appendCheckBox(CLOSE_PROJECTS, "Close all projects on startup", false);
            editorForm.appendSeparator();
            editorForm.appendCheckBox(ORDER_PROJECTS, "Order Projects alphabetically in tree", false);
            editorForm.appendCheckBox(ORDER_SERVICES, "Order Services alphabetically in tree", false);
            editorForm.appendCheckBox(ORDER_REQUESTS, "Order Requests alphabetically in tree", false);
            editorForm.appendCheckBox(SHOW_DESCRIPTIONS, "Show description content when available", false);
            editorForm.appendSeparator();

            editorForm.appendCheckBox(AUTOSAVE_ONEXIT, "Save all projects on exit", true);
            backupCheckBox = editorForm.appendCheckBox(CREATE_BACKUP, "Backup project files before they are saved", true);
            backupFolder = editorForm.appendTextField(BACKUP_FOLDER,
                    "Folder to backup to (can be both relative or absolute)");
            backupCheckBox.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    backupFolder.setEnabled(backupCheckBox.isSelected());
                }
            });

            editorForm.appendTextField(AUTOSAVE_INTERVAL,
                    "Sets the autosave interval in minutes (0 means autosave is off)");

            editorForm.appendSeparator();
            editorForm.appendCheckBox(CONTENT_IN_EXTERNAL_FILE, "Save content of test steps in a file, outside the project file", false);
            editorForm.appendCheckBox(ALSO_KEEP_IN_PROJECT_WHEN_CONTENT_IN_EXTERNAL_FILE, "Also keep step content in project file when saving (for backward compatibility)", true);
            editorForm.appendCheckBox(AUTO_CONVERT_CONTENT_TO_USE_EXTERNAL_FILE, "Automatically convert steps to use an external file for content.", false);

            if (SoapUI.isStandalone()) {
                editorForm.appendSeparator();
                editorForm.appendComboBox(DESKTOP_TYPE, DesktopRegistry.getInstance().getNames(),
                        "Select the type of desktop to use");
                editorForm.appendCheckBox(MRU_PANEL_SELECTOR, "Show most recently used panel on close (requires restart)", true);
                JCheckBox cb = editorForm.appendCheckBox(NATIVE_LAF, "Use native Look & Feel (requires restart)", true);
                if (UISupport.isMac()) {
                    cb.setSelected(true);
                }
            }

            editorForm.appendSeparator();
            editorForm.appendCheckBox(ENABLE_GROOVY_LOG, "Do not disable the groovy log when running load tests", true);

            if (SoapUI.isStandalone()) {
                editorForm.appendCheckBox(SHOW_LOGS_AT_STARTUP, "Shows log tabs when starting SoapUI", false);
                editorForm.appendCheckBox(SHOW_STARTUP_PAGE, "Opens startup web page when starting SoapUI", false);
                editorForm.appendCheckBox(DISABLE_TOOLTIPS, "Disables all tooltips", false);
            }

            editorForm.appendSeparator();
            editorForm.appendCheckBox(LINEBREAK, "Normalize line-breaks when saving project", false);
            editorForm.appendSeparator();
            editorForm.appendTextField(GC_INTERVAL,
                    "Sets the Garbage Collector interval in seconds (0 means garbage collection is only performed by JRE)");
            editorForm.appendSeparator();
            editorForm.appendTextField(RAW_RESPONSE_MESSAGE_SIZE, "Sets the size of raw response mesage to show.");
            editorForm.appendTextField(RAW_REQUEST_MESSAGE_SIZE, "Sets the size of raw request mesage to show.");
            editorForm.appendCheckBox(WRAP_RAW_MESSAGES, "Wraps content in Raw Message Viewers", false);
        }

        return editorForm;
    }

    public void getFormValues(Settings settings) {
        StringToStringMap values = new StringToStringMap();
        editorForm.getValues(values);
        storeValues(values, settings);
    }

    public void storeValues(StringToStringMap values, Settings settings) {
        settings.setBoolean(UISettings.CLOSE_PROJECTS, values.getBoolean(CLOSE_PROJECTS));
        settings.setBoolean(UISettings.ORDER_PROJECTS, values.getBoolean(ORDER_PROJECTS));
        settings.setBoolean(UISettings.ORDER_SERVICES, values.getBoolean(ORDER_SERVICES));
        settings.setBoolean(UISettings.ORDER_REQUESTS, values.getBoolean(ORDER_REQUESTS));
        settings.setBoolean(UISettings.SHOW_DESCRIPTIONS, values.getBoolean(SHOW_DESCRIPTIONS));
        settings.setBoolean(UISettings.CREATE_BACKUP, values.getBoolean(CREATE_BACKUP));
        settings.setString(UISettings.BACKUP_FOLDER, values.get(BACKUP_FOLDER));
        settings.setString(UISettings.AUTO_SAVE_INTERVAL, values.get(AUTOSAVE_INTERVAL));
        settings.setBoolean(UISettings.AUTO_SAVE_PROJECTS_ON_EXIT, values.getBoolean(AUTOSAVE_ONEXIT));
        settings.setBoolean(UISettings.LINEBREAK, values.getBoolean(LINEBREAK));
        settings.setBoolean(UISettings.CONTENT_IN_EXTERNAL_FILE, values.getBoolean(CONTENT_IN_EXTERNAL_FILE));
        settings.setBoolean(UISettings.ALSO_KEEP_IN_PROJECT_WHEN_CONTENT_IN_EXTERNAL_FILE, values.getBoolean(ALSO_KEEP_IN_PROJECT_WHEN_CONTENT_IN_EXTERNAL_FILE));
        settings.setBoolean(UISettings.AUTO_CONVERT_CONTENT_TO_USE_EXTERNAL_FILE, values.getBoolean(AUTO_CONVERT_CONTENT_TO_USE_EXTERNAL_FILE));

        if (SoapUI.isStandalone()) {
            settings.setString(UISettings.DESKTOP_TYPE, values.get(DESKTOP_TYPE));
            settings.setBoolean(UISettings.NATIVE_LAF, values.getBoolean(NATIVE_LAF));
            settings.setBoolean(UISettings.MRU_PANEL_SELECTOR, values.getBoolean(MRU_PANEL_SELECTOR));
        }

        settings.setBoolean(UISettings.DONT_DISABLE_GROOVY_LOG, values.getBoolean(ENABLE_GROOVY_LOG));
        if (SoapUI.isStandalone()) {
            settings.setBoolean(UISettings.SHOW_LOGS_AT_STARTUP, values.getBoolean(SHOW_LOGS_AT_STARTUP));
            settings.setBoolean(UISettings.SHOW_STARTUP_PAGE, values.getBoolean(SHOW_STARTUP_PAGE));
            settings.setBoolean(UISettings.DISABLE_TOOLTIPS, values.getBoolean(DISABLE_TOOLTIPS));

            ToolTipManager.sharedInstance().setEnabled(!values.getBoolean(DISABLE_TOOLTIPS));
        }

        settings.setString(UISettings.GC_INTERVAL, values.get(GC_INTERVAL));

        settings.setString(UISettings.RAW_RESPONSE_MESSAGE_SIZE, values.get(RAW_RESPONSE_MESSAGE_SIZE));
        settings.setString(UISettings.RAW_REQUEST_MESSAGE_SIZE, values.get(RAW_REQUEST_MESSAGE_SIZE));
        settings.setBoolean(UISettings.WRAP_RAW_MESSAGES, values.getBoolean(WRAP_RAW_MESSAGES));

        SoapUI.initAutoSaveTimer();
        SoapUI.initGCTimer();
    }

    public void setFormValues(Settings settings) {
        editorForm.setValues(getValues(settings));
        backupFolder.setEnabled(settings.getBoolean(UISettings.CREATE_BACKUP));
    }

    public StringToStringMap getValues(Settings settings) {
        StringToStringMap values = new StringToStringMap();
        values.put(CLOSE_PROJECTS, settings.getBoolean(UISettings.CLOSE_PROJECTS));
        values.put(ORDER_PROJECTS, settings.getBoolean(UISettings.ORDER_PROJECTS));
        values.put(ORDER_SERVICES, settings.getBoolean(UISettings.ORDER_SERVICES));
        values.put(ORDER_REQUESTS, settings.getBoolean(UISettings.ORDER_REQUESTS));
        values.put(SHOW_DESCRIPTIONS, settings.getBoolean(UISettings.SHOW_DESCRIPTIONS));
        values.put(CREATE_BACKUP, settings.getBoolean(UISettings.CREATE_BACKUP));
        values.put(BACKUP_FOLDER, settings.getString(UISettings.BACKUP_FOLDER, ""));
        values.put(AUTOSAVE_INTERVAL, settings.getString(UISettings.AUTO_SAVE_INTERVAL, "0"));
        values.put(AUTOSAVE_ONEXIT, settings.getBoolean(UISettings.AUTO_SAVE_PROJECTS_ON_EXIT));
        values.put(LINEBREAK, settings.getBoolean(UISettings.LINEBREAK));
        values.put(CONTENT_IN_EXTERNAL_FILE, settings.getBoolean(UISettings.CONTENT_IN_EXTERNAL_FILE));
        values.put(ALSO_KEEP_IN_PROJECT_WHEN_CONTENT_IN_EXTERNAL_FILE, settings.getBoolean(UISettings.ALSO_KEEP_IN_PROJECT_WHEN_CONTENT_IN_EXTERNAL_FILE));
        values.put(AUTO_CONVERT_CONTENT_TO_USE_EXTERNAL_FILE, settings.getBoolean(UISettings.AUTO_CONVERT_CONTENT_TO_USE_EXTERNAL_FILE));

        if (SoapUI.isStandalone()) {
            values.put(DESKTOP_TYPE, settings.getString(UISettings.DESKTOP_TYPE, SoapUI.DEFAULT_DESKTOP));
            values.put(MRU_PANEL_SELECTOR, settings.getBoolean(UISettings.MRU_PANEL_SELECTOR));
            values.put(NATIVE_LAF, settings.getBoolean(UISettings.NATIVE_LAF));
        }

        values.put(ENABLE_GROOVY_LOG, settings.getBoolean(UISettings.DONT_DISABLE_GROOVY_LOG));
        if (SoapUI.isStandalone()) {
            values.put(SHOW_LOGS_AT_STARTUP, settings.getBoolean(UISettings.SHOW_LOGS_AT_STARTUP));
            values.put(SHOW_STARTUP_PAGE, settings.getBoolean(UISettings.SHOW_STARTUP_PAGE));
            values.put(DISABLE_TOOLTIPS, settings.getBoolean(UISettings.DISABLE_TOOLTIPS));
        }

        values.put(GC_INTERVAL, settings.getString(UISettings.GC_INTERVAL, "0"));
        values.put(RAW_RESPONSE_MESSAGE_SIZE, settings.getString(UISettings.RAW_RESPONSE_MESSAGE_SIZE, "10000"));
        values.put(RAW_REQUEST_MESSAGE_SIZE, settings.getString(UISettings.RAW_REQUEST_MESSAGE_SIZE, "10000"));
        values.put(WRAP_RAW_MESSAGES, settings.getBoolean(UISettings.WRAP_RAW_MESSAGES));

        return values;
    }
}
