package cn.yumis.fight.modules.inventory.api;

import com.google.gson.*;

/** 次元背包自动收纳偏好：默认关闭；开启后默认仅普通背包放不下时才收纳。 */
public record BagOption(boolean autoStore,boolean onlyWhenFull){
    public static final BagOption DEFAULT=new BagOption(false,true);
    /** 宽容解析：缺失或非法字段按默认值处理，不中断拾取。 */
    public static BagOption parse(JsonElement raw){
        if(raw==null||!raw.isJsonObject())return DEFAULT;
        JsonObject value=raw.getAsJsonObject();
        JsonElement auto=value.get("autoStore"),only=value.get("onlyWhenFull");
        return new BagOption(auto!=null&&auto.isJsonPrimitive()&&auto.getAsBoolean(),only==null||!only.isJsonPrimitive()||only.getAsBoolean());
    }
    public JsonElement encode(){
        JsonObject value=new JsonObject();
        value.addProperty("autoStore",autoStore);
        value.addProperty("onlyWhenFull",onlyWhenFull);
        return value;
    }
}
