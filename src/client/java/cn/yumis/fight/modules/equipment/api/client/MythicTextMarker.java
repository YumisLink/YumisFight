package cn.yumis.fight.modules.equipment.api.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Pure local style metadata. Normal insertion access and component serialization see the original value. */
public final class MythicTextMarker {
    private static final String PREFIX="yumis_dungeon_and_fight:mythic_gradient/";
    private MythicTextMarker(){}
    public static boolean marked(String value){return value!=null&&value.startsWith(PREFIX);}
    public static String encode(String original){return PREFIX+(original==null?"-":Base64.getEncoder().encodeToString(original.getBytes(StandardCharsets.UTF_8)));}
    public static String original(String value){
        if(!marked(value))return value;String encoded=value.substring(PREFIX.length());if(encoded.equals("-"))return null;
        try{return new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8);}catch(IllegalArgumentException invalid){return null;}
    }
}
