package org.shyou.testmod;

import de.dakror.modding.XMLResourceEditor;

import org.w3c.dom.Document;

import de.dakror.modding.Patcher;

@Patcher.XMLEditor(file = "lml/main-menu.xml")
public class MenuEditor extends XMLResourceEditor.Editor {

    @Override
    public void edit(Document doc) {
        var buttons = doc.getElementsByTagName("textbutton");
        var nbuttons = buttons.getLength();
        var lastButton = buttons.item(nbuttons - 1);
        var newButton = lastButton.cloneNode(true);
        var newAttrs = newButton.getAttributes();
        newAttrs.getNamedItem("id").setNodeValue("mods");
        newAttrs.getNamedItem("text").setNodeValue("@menu.mods");
        lastButton.getParentNode().insertBefore(newButton, lastButton.getNextSibling());
    }
    
}
