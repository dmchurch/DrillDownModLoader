package org.shyou.testmod;

import static de.dakror.modding.ModAPI.*;

import java.util.Arrays;

import de.dakror.modding.Patcher.AugmentationClass;
import de.dakror.modding.Patcher.AugmentationClass.DelayedInitArgs;
import de.dakror.modding.Patcher.AugmentationClass.PreInit;
import de.dakror.quarry.game.Item.ItemCategory;
import de.dakror.quarry.game.Item.ItemType;

// @EnumExtensionClass(extendsEnum = ItemType.class)
@AugmentationClass(augments = ItemType.class)
public enum MyItemType {
    NewItemType(200, "nil", 100),
    OtherNewItemType(144, "nil", 1440, ItemCategory.Material);

    public int worth;

    @PreInit(preInitMethod="preMyItemType", inClass=Aux.class)
    @DelayedInitArgs("callSuper")
    private MyItemType(MyItemType type, int meta, String name, int worth, ItemCategory... categories) {
        DEBUGLN("creating ItemType.%s[%d] = ItemType(%s, %d, %s, %d, [%s])", this.name(), this.ordinal(), type, meta, name, worth, String.join(", ", Arrays.stream(categories).map(String::valueOf).toArray(String[]::new)));
        callSuper(this.name(), this.ordinal(), type, meta, name, worth+1, categories);
        // super(enumName, enumValue, type, meta, name, worth, categories);
        DEBUGLN("created ItemType.%s[%d] = ItemType(%s, %d, %s, %d, [%s])", this.name(), this.ordinal(), type, meta, name, this.worth, String.join(", ", Arrays.stream(categories).map(String::valueOf).toArray(String[]::new)));
    }

    private void callSuper(String enumName, int enumOrd, MyItemType type, int meta, String name, int worth, ItemCategory... categories) {}

    @PreInit(preInitMethod="preMyItemType", inClass=Aux.class)
    private MyItemType(int value, String name, int worth, ItemCategory... categories) {
        // super(enumName, enumValue, value, name, worth, categories);
        DEBUGLN("created ItemType.%s[%d] = ItemType(%d, %s, %d, [%s])", this.name(), this.ordinal(), value, name, worth, String.join(", ", Arrays.stream(categories).map(String::valueOf).toArray(String[]::new)));
    }

    @PreInit(preInitMethod="preMyItemType", inClass=Aux.class)
    private MyItemType(MyItemType type, int meta, MyItemType stackable) {
        // super(enumName, enumValue, type, meta, stackable);
        DEBUGLN("created ItemType.%s[%d] = ItemType(%s, %d, %s)", this.name(), this.ordinal(), type, meta, stackable);
    }

    @SuppressWarnings("unused")
    private static class Aux {
        @PreInit
        private static void preMyItemType(String enumName, int enumOrd, MyItemType type, int meta, String name, int worth, ItemCategory... categories) {
        }
        private static void preMyItemType(String enumName, int enumOrd, int value, String name, int worth, ItemCategory... categories) {
            DEBUGLN("before named init for ItemType.%s: %s", enumName, name);
        }
        private static void preMyItemType(String enumName, int enumOrd, MyItemType type, int meta, MyItemType stackable) {
        }
    }

    static void printItems() {
        System.out.println("Items:");
        System.out.println("ID,value,title,name,worth,categories,stackable");
        try {
            for (var v: ItemType.values) {
                System.out.println(String.format("%s,0x%04x,%s,%s,%d,\"%s\",%s", v, v.value, v.title, v.name, v.worth, v.categories, String.valueOf(v.stackable)));
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.out.println(" - done - ");
    }
    static {
        printItems();
    }
}

// class ItemTypeBase {
//     protected ItemTypeBase(String enumName, int enumValue, ItemTypeBase type, int worth, Element element, ItemCategory... categories) { }
//     protected ItemTypeBase(String enumName, int enumValue, ItemTypeBase type, int worth, Composite composite, ItemCategory... categories) { }
//     protected ItemTypeBase(String enumName, int enumValue, ItemTypeBase type, int meta, String name, int worth, ItemCategory... categories) { }
//     protected ItemTypeBase(String enumName, int enumValue, int value, String name, int worth, ItemCategory... categories) { }
//     protected ItemTypeBase(String enumName, int enumValue, ItemTypeBase type, int meta, ItemTypeBase stackable) { }
// }