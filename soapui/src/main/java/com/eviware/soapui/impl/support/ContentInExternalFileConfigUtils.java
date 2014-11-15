package com.eviware.soapui.impl.support;

import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import static com.eviware.soapui.impl.support.ContentInExternalFile.*;

/**
 * Utility methods for managing config of externalizable contents.
 */
public final class ContentInExternalFileConfigUtils {
    private ContentInExternalFileConfigUtils() {
        // NoOp
    }

    public static boolean nodeIs(XmlObject xmlObject, String relativePath, String nodeName) {
        if (xmlObject == null || relativePath == null || nodeName == null) {
            return false;
        }
        XmlCursor xmlCursor;
        XmlObject path[] = xmlObject.selectPath(CONFIG_NAMESPACE + "$this/" + relativePath);
        if (path != null && path.length > 0) {
            xmlCursor = path[0].newCursor();
        } else {
            return false;
        }
        return nodeName.equals(xmlCursor.getName().getLocalPart());
    }

    public static boolean cursorIsAt(XmlCursor xmlCursor, String nodeName) {
        if (xmlCursor == null || nodeName == null) {
            return false;
        }
        return nodeName.equals(xmlCursor.getName().getLocalPart());
    }

}
