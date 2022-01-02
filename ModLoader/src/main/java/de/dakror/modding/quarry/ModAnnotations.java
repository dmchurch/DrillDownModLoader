package de.dakror.modding.quarry;

import de.dakror.modding.Patcher.ModEnum;
import de.dakror.quarry.game.Item.ItemCategory;
import de.dakror.quarry.game.Item.ItemType;

public class ModAnnotations {
    @ModEnum(ItemType.class)
    public static @interface ModBasicItemType {
        int id();

        String name();

        int worth();

        ItemCategory[] categories() default {};
    }
}