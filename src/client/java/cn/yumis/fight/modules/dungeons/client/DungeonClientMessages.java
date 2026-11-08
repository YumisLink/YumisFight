package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.architecture.CommandRouter;
import com.google.gson.JsonObject;
import java.util.function.Consumer;

/** The bootstrap injects the established client hit queue without exposing another module's state. */
public final class DungeonClientMessages {
    public static void register(CommandRouter<Void,JsonObject> router,Consumer<JsonObject> hit,Consumer<String> cancel){
        router.register("dungeons","bossAction",(context,j)->{j.remove("clientContact");EnemyTelegraphs.receive(j);hit.accept(j);});
        router.register("dungeons","cancelBossAction",(context,j)->{String id=j.get("id").getAsString();EnemyTelegraphs.cancel(id);cancel.accept(id);});
        router.register("dungeons","palaceAttack",(context,j)->{PalaceVisuals.receive(j);EncounterClientContacts.receive(EncounterClientContacts.Family.PALACE,j,hit);});
        router.register("dungeons","forbiddenAttack",(context,j)->{ForbiddenVisuals.receive(j);EncounterClientContacts.receive(EncounterClientContacts.Family.FORBIDDEN,j,hit);});
        router.register("dungeons","skasaAttack",(context,j)->{SkasaVisuals.receive(j);EncounterClientContacts.receive(EncounterClientContacts.Family.SKASA,j,hit);});
        router.register("dungeons","darkCityAttack",(context,j)->DarkCityClient.receive(j,hit));
        router.register("dungeons","classicAttack",(context,j)->{ClassicVisuals.receive(j);EncounterClientContacts.receive(EncounterClientContacts.Family.CLASSIC,j,hit);});
    }
    private DungeonClientMessages(){}
}
