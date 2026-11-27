package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.architecture.CommandRouter;
import com.google.gson.JsonObject;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** The bootstrap injects the established client hit queue without exposing another module's state. */
public final class DungeonClientMessages {
    public static void register(CommandRouter<Void,JsonObject> router,Predicate<JsonObject> contact,Consumer<String> cancel){
        Consumer<JsonObject> hit=contact::test;
        router.register("dungeons","towerStele",(context,j)->DungeonActions.enterTower(j));
        router.register("dungeons","towerLobby",(context,j)->TowerClient.receiveLobby(j));
        router.register("dungeons","towerBattle",(context,j)->TowerClient.receiveBattle(j));
        router.register("dungeons","treasureMapClue",(context,j)->TreasureMapClient.receiveClue(j));
        router.register("dungeons","bossAction",(context,j)->{j.remove("clientContact");EnemyTelegraphs.receive(j);KenoVisuals.receive(j);hit.accept(j);});
        router.register("dungeons","cancelBossAction",(context,j)->{String id=j.get("id").getAsString();EnemyTelegraphs.cancel(id);KenoVisuals.cancel(id);cancel.accept(id);});
        router.register("dungeons","palaceAttack",(context,j)->{PalaceVisuals.receive(j);EncounterClientContacts.receive(EncounterClientContacts.Family.PALACE,j,hit);});
        router.register("dungeons","forbiddenAttack",(context,j)->{ForbiddenVisuals.receive(j);EncounterClientContacts.receive(EncounterClientContacts.Family.FORBIDDEN,j,hit);});
        router.register("dungeons","skasaAttack",(context,j)->{SkasaVisuals.receive(j);EncounterClientContacts.receive(EncounterClientContacts.Family.SKASA,j,hit);});
        router.register("dungeons","darkCityAttack",(context,j)->DarkCityClient.receive(j,hit));
        router.register("dungeons","classicAttack",(context,j)->{ClassicVisuals.receive(j);EncounterClientContacts.receive(EncounterClientContacts.Family.CLASSIC,j,hit);});
        router.register("dungeons","abyssAttack",(context,j)->AbyssVisuals.receive(j,hit));
        router.register("dungeons","abyssCancel",(context,j)->AbyssVisuals.cancel(j));
        router.register("dungeons","abyssFlash",(context,j)->AbyssVisuals.flash(j));
        router.register("dungeons","ritualMenu",(context,j)->RitualFightClient.openMenu(j));
        router.register("dungeons","ritualRun",(context,j)->RitualFightClient.run(j));
        router.register("dungeons","ritualAttack",(context,j)->RitualFightClient.attack(j,contact));
        router.register("dungeons","cancelRitualAttack",(context,j)->RitualFightClient.cancel(j.get("id").getAsString()));
        router.register("dungeons","ritualClearDebt",(context,j)->RitualFightClient.clearDebt());
        router.register("dungeons","sealBossEvent",(context,j)->ElainaBossClient.onEvent(j));
        router.register("dungeons","sealBossHazard",(context,j)->ElainaBossClient.onHazard(j));
        router.register("dungeons","theaterBossEvent",(context,j)->LiangyueBossClient.onEvent(j));
        router.register("dungeons","theaterBossHazard",(context,j)->LiangyueBossClient.onHazard(j));
        router.register("dungeons","frostBossEvent",(context,j)->FrostnovaBossClient.onEvent(j));
        router.register("dungeons","frostBossHazard",(context,j)->FrostnovaBossClient.onHazard(j));
        router.register("dungeons","orreryBossEvent",(context,j)->GilgameshBossClient.onEvent(j));
        router.register("dungeons","orreryBossHazard",(context,j)->GilgameshBossClient.onHazard(j));
        router.register("dungeons","forgeBossEvent",(context,j)->SwordMastersBossClient.onEvent(j));
        router.register("dungeons","forgeBossHazard",(context,j)->SwordMastersBossClient.onHazard(j));
        router.register("dungeons","sluiceBossEvent",(context,j)->RoxyBossClient.onEvent(j));
        router.register("dungeons","sluiceBossHazard",(context,j)->RoxyBossClient.onHazard(j));
        router.register("dungeons","lucasBossEvent",(context,j)->LucasBossClient.onEvent(j));
        router.register("dungeons","lucasBossHazard",(context,j)->LucasBossClient.onHazard(j));
    }
    private DungeonClientMessages(){}
}
