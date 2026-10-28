package cn.yumis.fight.modules.equipment.api;
import com.google.gson.*;
import cn.yumis.fight.modules.affixes.api.AffixJson;
/** Equipment codec preserves extension fields on embedded affixes. */
public final class EquipmentJson {
    public static final Gson JSON=new GsonBuilder().disableHtmlEscaping().registerTypeAdapterFactory(new AffixJson()).create();
    private EquipmentJson(){}
    public static <T>T copy(T value,Class<T> type){return JSON.fromJson(JSON.toJson(value),type);}
}
