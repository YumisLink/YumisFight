package cn.yumis.fight.bootstrap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Serialization assembly; business models remain in their owning modules. */
public final class ApplicationJson {
    public static final Gson JSON=new GsonBuilder().disableHtmlEscaping()
        .registerTypeAdapterFactory(new cn.yumis.fight.modules.affixes.api.AffixJson()).create();
    private ApplicationJson(){}
    public static <T>T copy(T value,Class<T> type){return JSON.fromJson(JSON.toJson(value),type);}
}
