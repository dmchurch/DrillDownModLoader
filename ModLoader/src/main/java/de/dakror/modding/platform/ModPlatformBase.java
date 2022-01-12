package de.dakror.modding.platform;

import java.util.ArrayList;
import java.util.List;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;

interface ModPlatformBase extends IModPlatform {
    static final String MODLOADER_CLASS = "de.dakror.modding.ModLoader";
    default IModLoader createModLoader(String[] args) throws Exception {
        var modLoaderClass = loadClass(MODLOADER_CLASS);
        var loader = (IModLoader) modLoaderClass.getMethod("newInstance", IModPlatform.class, String[].class).invoke(null, this, args);
        return loader.init(this, getAppLoader(), args);
    }

    @Override
    default void callMain(String mainClass, String[] args) throws Throwable {
        publicLookup().findStatic(loadClass(mainClass), "main", methodType(void.class, String[].class)).invoke(args);
    }
    
    default void start(String mainClass, String[] args) throws Throwable {
        List<String> passArgs = new ArrayList<>();
        boolean disableMods = false;
        for (var arg: args) {
            if (arg.equals("--disable-mods") || arg.equals("safe")) {
                disableMods = true;
            } else {
                passArgs.add(arg);
            }
        }
        args = passArgs.toArray(String[]::new);
        if (disableMods) {
            if (mainClass != null) {
                callMain(mainClass, args);
            }
        } else {
            createModLoader(args).start(mainClass, args);
        }
    }
}
