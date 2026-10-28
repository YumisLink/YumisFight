package cn.yumis.fight.modules.industry.api;
import com.mojang.serialization.Codec;
import java.util.*;
/** Bounded NBT string parts in the current save format. */
public final class ChunkedStrings {
    public static final Codec<String> CODEC=Codec.STRING.listOf().xmap(parts->String.join("",parts),value->{List<String> parts=new ArrayList<>();for(int i=0;i<value.length();i+=8000)parts.add(value.substring(i,Math.min(i+8000,value.length())));return parts;});
    private ChunkedStrings(){}
}
