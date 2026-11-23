package cn.yumis.fight.modules.equipment;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** 强化/增幅与既有装备加工链路的集成：真实模板、侵染通货、腐化共存与存档代际门槛。 */
class EnhancementFlowTest {
    private static Gear katana(){
        return EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.COMMON,20,new Random(1));
    }


    @Test void schemaGateKeepsLegacyRevisionInstancesAndRejectsOlderGenerations(){
        Gear g=katana();
        g.enhanceLevel=9;
        String json=EquipmentJson.JSON.toJson(g);
        Gear read=EquipmentResolution.read(json);
        assertEquals(9,read.enhanceLevel);
        JsonObject legacy=JsonParser.parseString(json).getAsJsonObject();
        legacy.addProperty("version",Gear.SCHEMA_VERSION-1);
        legacy.remove("enhanceLevel");
        legacy.remove("amplifyLevel");
        legacy.remove("contamination");
        Gear restored=EquipmentResolution.read(legacy.toString());
        assertEquals(Gear.SCHEMA_VERSION-1,restored.version);
        assertEquals(0,restored.enhanceLevel);
        assertEquals(0,restored.amplifyLevel);
        assertEquals("",restored.contamination);
        assertNotNull(EquipmentResolution.view(legacy.toString()),"v9 存量装备必须继续解析出视图（validWeapon 依赖该路径）");
        Gear working=EquipmentResolution.resolve(restored);
        assertEquals(Gear.SCHEMA_VERSION-1,working.version);
        JsonObject ancient=JsonParser.parseString(json).getAsJsonObject();
        ancient.addProperty("version",Gear.MINIMUM_SCHEMA_VERSION-1);
        assertThrows(IllegalArgumentException.class,()->EquipmentResolution.read(ancient.toString()));
    }
}
