package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.architecture.CommandRouter;
import com.google.gson.JsonObject;
import java.util.function.Consumer;

/** The bootstrap injects the established client hit queue without exposing another module's state. */
public final class DungeonClientMessages {
    public static void register(CommandRouter<Void,JsonObject> router,Consumer<JsonObject> hit,Consumer<String> cancel){
        router.register("dungeons","bossAction",(context,j)->{EnemyTelegraphs.receive(j);hit.accept(j);});
        router.register("dungeons","cancelBossAction",(context,j)->{String id=j.get("id").getAsString();EnemyTelegraphs.cancel(id);cancel.accept(id);});
        router.register("dungeons","palaceAttack",(context,j)->PalaceVisuals.receive(j));
    }
    private DungeonClientMessages(){}
}
