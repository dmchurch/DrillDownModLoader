package org.shyou.testmod;

import de.dakror.common.libgdx.PlatformInterface;
import de.dakror.modding.Patcher.AugmentationClass;
import de.dakror.quarry.Quarry;

import static de.dakror.modding.ModAPI.*;

@AugmentationClass
public class MyQuarry extends Quarry {
    @AugmentationClass.PreInit
    private static void preMyQuarry(PlatformInterface pi, boolean fullVersion, int versionNumber, String version, boolean desktop, boolean newAndroid, WindowMode mode) {
        DEBUGLN("About to instantiate Quarry(%s, %s, %d, %s, %s, %s, %s)", pi, fullVersion, versionNumber, version, desktop, newAndroid, mode);
    }
    @AugmentationClass.PreInit(preInitMethod = "preMyQuarry")
    public MyQuarry(PlatformInterface pi, boolean fullVersion, int versionNumber, String version, boolean desktop, boolean newAndroid, WindowMode mode) {
        super(pi, fullVersion, versionNumber, version, desktop, newAndroid, mode);
        DEBUGLN("MyQuarry()");
    }

    @Override
    public void create() {
        DEBUGLN("MyQuarry: in create()");
        DEBUGLN("Current Q: "+Q);
        super.create();
        DEBUGLN("MyQuarry: exit create()");
        DEBUGLN("New Q: "+Q);
    }
}