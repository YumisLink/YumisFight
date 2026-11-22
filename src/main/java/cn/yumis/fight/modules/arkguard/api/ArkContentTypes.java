package cn.yumis.fight.modules.arkguard.api;

import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.npc.api.NpcEntity;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.*;
import java.util.*;

public final class ArkContentTypes {
    private static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(Registries.ENTITY_TYPE,ItemData.NS);
    private static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(Registries.MOB_EFFECT,ItemData.NS);
    public static final DeferredHolder<MobEffect,MobEffect> OBSERVER=EFFECTS.register("ark_observer",ObserverEffect::new);
    private static final class ObserverEffect extends MobEffect implements cn.yumis.fight.modules.combat.api.CombatEffect {
        ObserverEffect(){super(MobEffectCategory.BENEFICIAL,0x80d8ff);}
        @Override public boolean preventsDamage(){return true;}
        @Override public double outgoingDamageScale(){return .001;}
        @Override public boolean externalHitEffects(){return false;}
        @Override public boolean waivesMovementStamina(){return true;}
    }
    public static final DeferredHolder<EntityType<?>,EntityType<ArkRoleEntity>> ROLE=TYPES.register("ark_role",()->EntityType.Builder.<ArkRoleEntity>of(ArkRoleEntity::new,MobCategory.MISC).sized(.6f,1.8f).eyeHeight(1.62f).noSave().clientTrackingRange(14).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ItemData.NS,"ark_role"))));
    public static final Map<String,DeferredHolder<EntityType<?>,EntityType<ArkEnemyEntity>>> ENEMIES=new LinkedHashMap<>();
    static {
        for(String kind:List.of("goblin","goblin_thrower","goblin_captain","goblin_shaman","dragonman","wyvern","blue_dragonman","blue_wyvern","lucas")){
            boolean wing=kind.contains("wyvern"),goblin=kind.startsWith("goblin");String id="ark_enemy_"+kind;
            ENEMIES.put(kind,TYPES.register(id,()->EntityType.Builder.<ArkEnemyEntity>of((type,level)->new ArkEnemyEntity(type,level,kind),MobCategory.MONSTER)
                .sized(wing||kind.equals("lucas")?1.1f:.9f,wing?1.2f:kind.equals("lucas")?2.25f:kind.equals("goblin_shaman")?2.1f:goblin?1.3f:1.95f)
                .noSave().clientTrackingRange(14).build(ResourceKey.create(Registries.ENTITY_TYPE,Identifier.fromNamespaceAndPath(ItemData.NS,id)))));
        }
    }
    private ArkContentTypes(){}
    public static boolean actorEffect(MobEffectInstance effect){
        var type=effect.getEffect();
        return type.equals(cn.yumis.fight.modules.combat.api.CombatContentTypes.STUN)||type.equals(cn.yumis.fight.modules.combat.api.CombatContentTypes.FREEZE)
            ||type.equals(cn.yumis.fight.modules.combat.api.CombatContentTypes.SHOCK)||type.equals(cn.yumis.fight.modules.combat.api.CombatContentTypes.IGNITE)
            ||type.equals(cn.yumis.fight.modules.combat.api.CombatContentTypes.EROSION);
    }
    public static void register(IEventBus bus){TYPES.register(bus);EFFECTS.register(bus);bus.addListener((EntityAttributeCreationEvent event)->{event.put(ROLE.get(),NpcEntity.attributes().add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE,0).build());for(var type:ENEMIES.values())event.put(type.get(),DungeonEnemy.createAttributes().build());});}
}
