package cn.yumis.fight;

import cn.yumis.fight.rules.ThemeDefinition;
import cn.yumis.fight.industry.StorageRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;

class ThemeDefinitionTest {
    private ThemeDefinition defaults()throws Exception{try(var input=getClass().getResourceAsStream("/assets/yumis_dungeon_and_fight/ui-theme.json")){assertNotNull(input);return ThemeDefinition.parse(new String(input.readAllBytes(),StandardCharsets.UTF_8),null);}}
    @Test void oneOverrideKeepsEveryOtherRoleAndDoesNotMutateDefaults()throws Exception{
        var original=defaults();var changed=ThemeDefinition.parse("{\"colors\":{\"PANEL\":\"#123456\"}}",original);assertEquals(0xff123456,changed.color("PANEL"));assertEquals(original.color("TEXT"),changed.color("TEXT"));assertEquals(0xffc6c6c6,original.color("PANEL"));assertEquals("beveled",changed.style());
    }
    @Test void invalidEditsFailBeforeReplacingPalette()throws Exception{
        var original=defaults();assertThrows(IllegalArgumentException.class,()->ThemeDefinition.parse("{\"colors\":{\"PANEL\":\"#xyz\"}}",original));assertThrows(IllegalArgumentException.class,()->ThemeDefinition.parse("{\"border\":99}",original));assertThrows(IllegalArgumentException.class,()->ThemeDefinition.parse("{\"style\":\"unknown\"}",original));assertEquals(0xffc6c6c6,original.color("PANEL"));
    }
    @Test void eightColumnAndPagedAnchorsMigrateWithoutMovingStoredItems(){assertEquals(42,StorageRules.migrateRow(1,6));assertEquals(39,StorageRules.migrateRow(2,44));assertEquals(39,StorageRules.migrateRow(3,39));assertEquals(416,StorageRules.capacity(16));}
}
