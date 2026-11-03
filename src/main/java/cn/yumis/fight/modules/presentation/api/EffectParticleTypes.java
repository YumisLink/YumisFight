package cn.yumis.fight.modules.presentation.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

/** Separate particle identities for explicitly opted-in spatial effects; vanilla providers stay intact. */
public final class EffectParticleTypes {
    private static final DeferredRegister<ParticleType<?>> TYPES=DeferredRegister.create(Registries.PARTICLE_TYPE,"yumis_dungeon_and_fight");
    public static final DeferredHolder<ParticleType<?>,ParticleType<Dust>> DUST=TYPES.register("effect_dust",()->new ParticleType<Dust>(false){
        @Override public MapCodec<Dust> codec(){return Dust.CODEC;}
        @Override public StreamCodec<? super RegistryFriendlyByteBuf,Dust> streamCodec(){return Dust.STREAM_CODEC;}
    });
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> EXPLOSION=TYPES.register("effect_explosion",()->new SimpleParticleType(true));

    public record Dust(int color,float scale) implements ParticleOptions {
        public static final MapCodec<Dust> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
            ExtraCodecs.RGB_COLOR_CODEC.fieldOf("color").forGetter(Dust::color),Codec.floatRange(.01f,4f).fieldOf("scale").forGetter(Dust::scale)).apply(i,Dust::new));
        public static final StreamCodec<RegistryFriendlyByteBuf,Dust> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.INT,Dust::color,ByteBufCodecs.FLOAT,Dust::scale,Dust::new);
        @Override public ParticleType<Dust> getType(){return DUST.get();}
    }

    public static void register(IEventBus bus){TYPES.register(bus);}
    private EffectParticleTypes(){}
}
