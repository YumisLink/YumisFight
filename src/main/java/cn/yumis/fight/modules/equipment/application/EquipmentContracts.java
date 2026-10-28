package cn.yumis.fight.modules.equipment.application;
import cn.yumis.fight.modules.equipment.api.EquipmentEntitlements;
import cn.yumis.fight.foundation.Journal;
import com.google.gson.*;
import java.util.*;
public final class EquipmentContracts implements EquipmentEntitlements {
    private final Journal journal;
    public EquipmentContracts(Journal journal){this.journal=journal;}
    private static String key(UUID id){return "equipmentContract/"+id;}
    public long contractUntil(UUID player){return journal.get(key(player),Long.class,0L);}
    public Map<String,JsonElement> prepareContract(UUID player,long now,long duration){if(duration<=0)throw new IllegalArgumentException("契约时长无效");return Map.of(key(player),new JsonPrimitive(Math.addExact(Math.max(now,contractUntil(player)),duration)));}
}
