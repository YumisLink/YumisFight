package cn.yumis.fight.modules.equipment.api;
import com.google.gson.JsonElement;
import java.util.*;
/** The equipment module owns equipment-level entitlements; preparation must not commit separately. */
public interface EquipmentEntitlements {
    long contractUntil(UUID player);
    Map<String,JsonElement> prepareContract(UUID player,long now,long duration);
}
