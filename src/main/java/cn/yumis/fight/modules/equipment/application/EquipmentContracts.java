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
    public Map<String,JsonElement> storeContract(UUID player,long expected,long until){if(contractUntil(player)!=expected)throw new IllegalArgumentException("契约存储快照已变化");return Map.of(key(player),new JsonPrimitive(until));}
}
