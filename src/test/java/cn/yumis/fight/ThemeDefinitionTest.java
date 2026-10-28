package cn.yumis.fight;
import cn.yumis.fight.modules.presentation.api.ThemeDefinition;

import cn.yumis.fight.modules.inventory.api.StorageRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;

class ThemeDefinitionTest {
    private ThemeDefinition defaults()throws Exception{try(var input=getClass().getResourceAsStream("/assets/yumis_dungeon_and_fight/ui-theme.json")){assertNotNull(input);return ThemeDefinition.parse(new String(input.readAllBytes(),StandardCharsets.UTF_8),null);}}

    @Test void invalidEditsFailBeforeReplacingPalette()throws Exception{
        var original=defaults();assertThrows(IllegalArgumentException.class,()->ThemeDefinition.parse("{\"colors\":{\"PANEL\":\"#xyz\"}}",original));assertThrows(IllegalArgumentException.class,()->ThemeDefinition.parse("{\"border\":99}",original));assertThrows(IllegalArgumentException.class,()->ThemeDefinition.parse("{\"style\":\"unknown\"}",original));assertEquals(0xffc6c6c6,original.color("PANEL"));
    }
    @Test void currentBagPagesKeepStableCapacity(){assertEquals(416,StorageRules.capacity(16));assertEquals(7,StorageRules.pages(16));assertEquals(32,StorageRules.visible(16,6));assertEquals(0,StorageRules.visible(16,7));}
}
