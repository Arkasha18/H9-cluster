package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class PreviewActivityManifestTest {
    private static final String ANDROID_NAMESPACE =
            "http://schemas.android.com/apk/res/android";

    @Test
    public void previewActivityIsInternalSingleTask() throws Exception {
        Element activity = findPreviewActivity();

        assertNotNull(activity);
        assertEquals("false", activity.getAttributeNS(ANDROID_NAMESPACE, "exported"));
        assertEquals("singleTask", activity.getAttributeNS(ANDROID_NAMESPACE, "launchMode"));
    }

    private static Element findPreviewActivity() throws Exception {
        File manifest = new File("src/main/AndroidManifest.xml");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        NodeList activities = factory.newDocumentBuilder()
                .parse(manifest)
                .getElementsByTagName("activity");
        for (int index = 0; index < activities.getLength(); index++) {
            Element activity = (Element) activities.item(index);
            if (".PreviewActivity".equals(
                    activity.getAttributeNS(ANDROID_NAMESPACE, "name"))) {
                return activity;
            }
        }
        return null;
    }
}
