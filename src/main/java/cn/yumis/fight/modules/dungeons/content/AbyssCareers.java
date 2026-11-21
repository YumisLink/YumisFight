package cn.yumis.fight.modules.dungeons.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static cn.yumis.fight.modules.character.api.JobCatalog.*;

/** Career party appearance and draw; plain data so champion kits stay verifiable headlessly. */
public final class AbyssCareers {
    public record Career(String job, boolean ranged, int color) {}

    public static final List<Career> ALL = List.of(
        new Career(SWORDMASTER, false, 0xb5e6ef),
        new Career(ORACLE, false, 0xf2cb72),
        new Career(SOUL_BENDER, false, 0xaf76ee),
        new Career(ASURA, true, 0xcac0f1),
        new Career(BERSERKER, false, 0xe34559),
        new Career(HAVOC, false, 0x94e25a),
        new Career(SUMMONER, true, 0xaadbb1),
        new Career(SUN_MOON, true, 0xeeb1ff));

    public static List<Career> draw(Random random, int count) {
        var careers = new ArrayList<>(ALL);
        Collections.shuffle(careers, random);
        return List.copyOf(careers.subList(0, count));
    }

    private AbyssCareers() {}
}
