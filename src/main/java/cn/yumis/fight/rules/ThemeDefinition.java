package cn.yumis.fight.rules;

import com.google.gson.*;
import java.util.*;

/** Renderer-free parser; invalid edits never partially replace a live palette. */
public record ThemeDefinition(String style,int border,int textInset,Map<String,Integer> colors) {
    public static ThemeDefinition parse(String text,ThemeDefinition fallback){
        JsonObject json=JsonParser.parseString(text).getAsJsonObject();
        String style=json.has("style")?json.get("style").getAsString():fallback==null?"beveled":fallback.style;
        if(!Set.of("beveled","flat").contains(style))throw new IllegalArgumentException("主题style必须为beveled或flat");
        int border=json.has("border")?json.get("border").getAsInt():fallback==null?2:fallback.border;
        int inset=json.has("textInset")?json.get("textInset").getAsInt():fallback==null?6:fallback.textInset;
        if(border<1||border>4||inset<2||inset>16)throw new IllegalArgumentException("主题边框或文字间距超出范围");
        Map<String,Integer> colors=new LinkedHashMap<>();if(fallback!=null)colors.putAll(fallback.colors);
        if(json.has("colors"))for(var entry:json.getAsJsonObject("colors").entrySet()){
            String value=entry.getValue().getAsString().replace("#","");
            if(!value.matches("[a-fA-F0-9]{6}|[a-fA-F0-9]{8}"))throw new IllegalArgumentException("无效主题颜色："+entry.getKey());
            colors.put(entry.getKey(),(int)Long.parseLong(value.length()==6?"ff"+value:value,16));
        }
        return new ThemeDefinition(style,border,inset,Map.copyOf(colors));
    }
    public int color(String key){Integer value=colors.get(key);if(value==null)throw new IllegalArgumentException("主题缺少颜色："+key);return value;}
}
