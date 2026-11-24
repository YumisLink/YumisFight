package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.api.PracticeRoom;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.content.PracticeLayouts;
import cn.yumis.fight.modules.dungeons.content.PracticeMobs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Continuous waves share the ordinary dungeon cell and the committed enemy death receipts. */
final class PracticeEncounterService {
    private final DungeonService owner;
    private final Journal journal;
    private final Random random = new Random();

    PracticeEncounterService(DungeonService owner, Journal journal) {
        this.owner = owner;
        this.journal = journal;
    }

    boolean defeated(Dungeon dungeon) {
        return dungeon.practice != null && dungeon.status.equals("RUNNING")
            && dungeon.enemies.size() == PracticeRoom.WAVE_SIZE
            && dungeon.enemies.stream().allMatch(id -> {
                var target = journal.get("target/" + id, Target.class, null);
                return target != null && target.life.dead;
            });
    }

    void spawn(Dungeon dungeon) throws Exception {
        var previous = dungeon.enemies;
        int wave = dungeon.wave;
        var spawns = PracticeLayouts.require(dungeon.dungeon).spawns();
        journal.begin();
        try {
            dungeon.enemies = new LinkedHashSet<>();
            if (!previous.isEmpty()) dungeon.wave++;
            for (var slot : spawns) spawn(dungeon, slot);
            journal.put("instance/" + dungeon.id, dungeon);
            journal.finish();
        } catch (Exception failure) {
            journal.abort();
            for (UUID id : dungeon.enemies) {
                var entity = owner.level().getEntity(id);
                if (entity != null) entity.discard();
            }
            dungeon.enemies = previous;
            dungeon.wave = wave;
            throw failure;
        }
    }

    private DungeonEnemy spawn(Dungeon dungeon, PracticeLayouts.Spawn slot) {
        var pos = slot.pos();
        if (dungeon.practice.kind() == PracticeRoom.Kind.MOB) {
            // Every slot redraws an ordinary dungeon mob and may promote it to an elite.
            var mob = PracticeMobs.pick(random);
            boolean elite = random.nextDouble() < PracticeRoom.MOB_ELITE_CHANCE;
            return owner.spawn(dungeon, mob.enemy(), elite, false, dungeon.cell * 512 + pos.getX() + .5, 80, pos.getZ() + .5);
        }
        return owner.spawn(dungeon, slot.enemy(), false, false, dungeon.cell * 512 + pos.getX() + .5, 80, pos.getZ() + .5);
    }

    void tick(Dungeon dungeon, List<ServerPlayer> players) throws Exception {
        if (dungeon.enemies.isEmpty() || defeated(dungeon)) spawn(dungeon);
        var arena = PracticeLayouts.require(dungeon.dungeon);
        var fighters = players.stream().filter(owner::aliveParticipant)
            .filter(player -> onFloor(dungeon, arena, player)).toList();
        int slot = 0;
        for (UUID id : List.copyOf(dungeon.enemies)) {
            var home = arena.spawns().get(slot++);
            if (!(owner.level().getEntity(id) instanceof DungeonEnemy enemy) || !enemy.isAlive()) {
                var target = journal.get("target/" + id, Target.class, null);
                if (target == null || !target.life.dead) replace(dungeon, id, home);
                continue;
            }
            enemy.practice();
            if (!onFloor(dungeon, arena, enemy)) {
                var pos = home.pos();
                enemy.getNavigation().stop();
                enemy.setPos(dungeon.cell * 512 + pos.getX() + .5, 80, pos.getZ() + .5);
                enemy.setDeltaMovement(0,0,0);
            }
            var nearest = CombatContentTypes.controlled(enemy) ? null
                : fighters.stream().filter(enemy::canAttack).min(Comparator.comparingDouble(enemy::distanceToSqr)).orElse(null);
            if (enemy.getTargetUnchecked() != nearest) {
                enemy.setTarget(nearest);
                if (nearest == null) enemy.getNavigation().stop();
            }
        }
    }

    private static boolean onFloor(Dungeon dungeon, PracticeLayouts.Arena arena, Entity entity) {
        return entity.getY() >= 79 && entity.getY() < 96
            && arena.combatFloor(entity.getBlockX() - dungeon.cell * 512, entity.getBlockZ());
    }

    /** A missing native projection does not count as a kill or pay out a new wave. */
    private void replace(Dungeon dungeon, UUID id, PracticeLayouts.Spawn slot) throws Exception {
        var previous = new LinkedHashSet<>(dungeon.enemies);
        journal.begin();
        try {
            var replacement = spawn(dungeon, slot);
            var enemies = new LinkedHashSet<UUID>();
            for (UUID before : previous) enemies.add(before.equals(id) ? replacement.getUUID() : before);
            dungeon.enemies = enemies;
            journal.put("instance/" + dungeon.id, dungeon);
            journal.finish();
        } catch (Exception failure) {
            journal.abort();
            for (UUID spawned : dungeon.enemies) {
                if (!previous.contains(spawned) && owner.level().getEntity(spawned) instanceof DungeonEnemy enemy) enemy.discard();
            }
            dungeon.enemies = previous;
            throw failure;
        }
    }
}
