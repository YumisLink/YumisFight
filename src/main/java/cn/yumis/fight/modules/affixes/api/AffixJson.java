package cn.yumis.fight.modules.affixes.api;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.*;
import java.io.IOException;

/** Preserve fields owned by future/temporarily unavailable affix providers across explicit equipment mutations. */
public final class AffixJson implements TypeAdapterFactory {
    @Override public <T> TypeAdapter<T> create(Gson gson,TypeToken<T> type){
        if(!Affix.class.isAssignableFrom(type.getRawType()))return null;
        TypeAdapter<T> delegate=gson.getDelegateAdapter(this,type);
        TypeAdapter<JsonElement> tree=gson.getAdapter(JsonElement.class);
        return new TypeAdapter<>() {
            @Override public T read(JsonReader reader)throws IOException {
                JsonElement value=tree.read(reader);T result=delegate.fromJsonTree(value);
                if(result!=null&&value.isJsonObject())((Affix)result).preserved=value.getAsJsonObject().deepCopy();
                return result;
            }
            @Override public void write(JsonWriter writer,T value)throws IOException {
                if(value==null){writer.nullValue();return;}
                Affix affix=(Affix)value;JsonObject current=delegate.toJsonTree(value).getAsJsonObject();
                JsonObject result=affix.preserved==null?new JsonObject():affix.preserved.deepCopy();
                // Known nullable fields must be removable; unknown provider fields must survive.
                result.remove("origin");
                for(var entry:current.entrySet())result.add(entry.getKey(),entry.getValue());
                tree.write(writer,result);
            }
        };
    }
}
