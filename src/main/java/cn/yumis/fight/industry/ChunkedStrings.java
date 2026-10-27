package cn.yumis.fight.industry;

import com.mojang.serialization.Codec;
import com.mojang.datafixers.util.Either;
import java.util.*;
import java.util.function.Function;

/** NBT strings use bounded UTF payloads; retain compatibility with the old single string. */
public final class ChunkedStrings {
    public static final Codec<String> CODEC=Codec.either(Codec.STRING,Codec.STRING.listOf()).xmap(value->value.map(Function.identity(),parts->String.join("",parts)),value->{
        if(value.length()<=8000)return Either.left(value);List<String> parts=new ArrayList<>();for(int i=0;i<value.length();i+=8000)parts.add(value.substring(i,Math.min(i+8000,value.length())));return Either.right(parts);
    });
    private ChunkedStrings(){}
}
