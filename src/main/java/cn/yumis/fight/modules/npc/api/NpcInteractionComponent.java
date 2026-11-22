package cn.yumis.fight.modules.npc.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Persistent, synchronized interaction binding; independent of the NPC entity's model and AI. */
public final class NpcInteractionComponent {
    @FunctionalInterface public interface ActionHandler {void execute(ServerPlayer player,LivingEntity entity,NpcDefinitions.Action action);}
    private static final Map<String,ActionHandler> ACTIONS=new LinkedHashMap<>();
    private static final Map<String,java.util.function.BiPredicate<LivingEntity,NpcDefinitions.Action>> CLIENT_ACTIONS=new LinkedHashMap<>();
    private NpcInteractionComponent(){}
    public static void attach(LivingEntity entity,String definition){NpcDefinitions.current().require(definition);entity.setData(NpcContentTypes.BINDING,definition);}
    public static void detach(LivingEntity entity){entity.setData(NpcContentTypes.BINDING,"");}
    public static String id(LivingEntity entity){return entity.hasData(NpcContentTypes.BINDING)?entity.getData(NpcContentTypes.BINDING):"";}
    public static NpcDefinitions.Definition query(LivingEntity entity){return NpcDefinitions.current().npcs.get(id(entity));}
    public static void register(String type,ActionHandler handler){if(ACTIONS.putIfAbsent(type,Objects.requireNonNull(handler))!=null)throw new IllegalArgumentException("NPC动作重复："+type);}
    public static void execute(ServerPlayer player,LivingEntity entity,String id){
        var d=query(entity);if(d==null)return;
        var a=d.actions().stream().filter(value->value.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("NPC选项已变化"));
        var handler=ACTIONS.get(a.type());if(handler==null)throw new IllegalArgumentException("NPC动作未登记："+a.type());handler.execute(player,entity,a);
    }
    public static void unregister(String type){ACTIONS.remove(type);}
    /** A personal action can publish its result from the owner before the server applies world facts. */
    public static void registerClient(String type,java.util.function.BiPredicate<LivingEntity,NpcDefinitions.Action> handler){if(CLIENT_ACTIONS.putIfAbsent(type,Objects.requireNonNull(handler))!=null)throw new IllegalArgumentException("NPC本机动作重复："+type);}
    public static void unregisterClient(String type){CLIENT_ACTIONS.remove(type);}
    public static boolean clientAction(LivingEntity entity,NpcDefinitions.Action action){var handler=CLIENT_ACTIONS.get(action.type());return handler!=null&&handler.test(entity,action);}
    /** Client presentation marks a held conversation continuation so the shared action panel can keep its hint. */
    private static final Map<String,java.util.function.BiPredicate<LivingEntity,NpcDefinitions.Action>> CLIENT_CONTINUES=new LinkedHashMap<>();
    public static void registerClientContinue(String type,java.util.function.BiPredicate<LivingEntity,NpcDefinitions.Action> handler){if(CLIENT_CONTINUES.putIfAbsent(type,Objects.requireNonNull(handler))!=null)throw new IllegalArgumentException("NPC本机续读契约重复："+type);}
    public static void unregisterClientContinue(String type){CLIENT_CONTINUES.remove(type);}
    public static boolean clientContinues(LivingEntity entity,NpcDefinitions.Action action){var handler=CLIENT_CONTINUES.get(action.type());return handler!=null&&handler.test(entity,action);}
}
