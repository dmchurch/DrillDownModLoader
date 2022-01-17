package org.shyou.testmod;
import java.util.Locale;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.I18NBundle;

import static de.dakror.modding.ModAPI.*;
import de.dakror.modding.Patcher.AugmentationClass;

@AugmentationClass
public class MyI18NBundle extends I18NBundle {
    public static I18NBundle createBundle(FileHandle baseFileHandle, Locale locale) {
        DEBUGLN("createBundle(%s, %s)", baseFileHandle, locale);
        return I18NBundle.createBundle(baseFileHandle, new Locale("es", ""));
    }
}
