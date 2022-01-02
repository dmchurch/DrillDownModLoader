package de.dakror.modding;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

@ModLoader.Enabled
public class XMLResourceEditor implements ModLoader.IResourceMod {
    protected Map<String, List<Editor>> xmlEditors = DefaultingHashMap.using(ArrayList::new);
    protected DocumentBuilder docBuilder;
    protected Transformer transformer;

    public void addEditor(String resourceName, Editor editor) {
        xmlEditors.get(resourceName).add(editor);
    }

    public XMLResourceEditor() {
        try {
            var dbFactory = DocumentBuilderFactory.newDefaultInstance();
            dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            docBuilder = dbFactory.newDocumentBuilder();
            transformer = TransformerFactory.newDefaultInstance().newTransformer();
        } catch (ParserConfigurationException|TransformerConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hooksResource(String resourceName) {
        return xmlEditors.containsKey(resourceName);
    }

    @Override
    public InputStream redefineResourceStream(String resourceName, InputStream stream, ClassLoader loader) {
        var editors = xmlEditors.get(resourceName);
        Document doc;
        try {
            doc = docBuilder.parse(stream);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        for (var editor: editors) {
            editor.edit(doc);
        }
        var baos = new ByteArrayOutputStream();
        try {
            transformer.transform(new DOMSource(doc), new StreamResult(baos));
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }
    
    public static abstract class Editor {
        abstract public void edit(Document doc);
    }
}
