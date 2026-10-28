package cn.yumis.fight.modules.combat.api;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** An explicit, nested attack scope binds post-hit mutations to one issued native root. */
public final class NativeEffects {
    private static final ThreadLocal<ArrayDeque<Scope>> SCOPES=ThreadLocal.withInitial(ArrayDeque::new);
    private static volatile java.util.function.Predicate<Entity> eligible=entity->false;
    public static void configure(java.util.function.Predicate<Entity> eligibility){eligible=Objects.requireNonNull(eligibility);}
    public static void clear(){eligible=entity->false;SCOPES.remove();}
    private NativeEffects(){}
    public static final class Scope implements AutoCloseable {
        private final ServerPlayer victim;
        private final Scope shared;
        private final List<Runnable> effects=new ArrayList<>();
        private boolean attempted,finished;
        private String root;
        private Scope(ServerPlayer victim,Scope shared){this.victim=victim;this.shared=shared;}
        private Scope actual(){return shared==null?this:shared.actual();}
        public boolean deferred(){return actual().root!=null;}
        public void defer(Runnable action){actual().effects.add(action);}
        public void bind(String root){actual().root=root;}
        public void resolve(boolean hit){
            Scope s=actual();if(s.finished)return;s.finished=true;
            List<Runnable> actions=List.copyOf(s.effects);s.effects.clear();
            if(hit)replay(()->actions.forEach(Runnable::run));
        }
        @Override public void close(){
            if(shared!=null||victim==null)return;
            var stack=SCOPES.get();if(stack.peek()!=this)throw new IllegalStateException("Native attack scope mismatch");stack.pop();
            if(root==null)resolve(!attempted);
            if(stack.isEmpty())SCOPES.remove();
        }
    }
    public static final class Batch {
        public Scope scope;
        public Entity victim;
        public final Set<Player> deferred=new HashSet<>();
        public void close(){if(scope!=null){if(scope.deferred()&&victim instanceof Player player)deferred.add(player);scope.close();scope=null;}}
    }
    public static Scope open(Entity victim,Entity attacker){
        if(!(victim instanceof ServerPlayer player)||!(attacker instanceof LivingEntity)||attacker==victim||!eligible.test(attacker))return new Scope(null,null);
        for(Scope scope:SCOPES.get())if(scope.victim==player)return new Scope(player,scope);
        Scope scope=new Scope(player,null);SCOPES.get().push(scope);return scope;
    }
    public static Scope current(Entity victim){
        for(Scope scope:SCOPES.get())if(scope.victim==victim)return scope;
        return null;
    }
    public static void attempted(Entity victim){Scope scope=current(victim);if(scope!=null)scope.attempted=true;}
    public static boolean defer(Entity victim,Runnable action){Scope scope=current(victim);if(scope==null)return false;scope.defer(action);return true;}
    public static void replay(Runnable action){
        var previous=SCOPES.get();SCOPES.set(new ArrayDeque<>());
        try{action.run();}finally{if(previous.isEmpty())SCOPES.remove();else SCOPES.set(previous);}
    }
}
