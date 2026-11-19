package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.foundation.Journal;
import com.google.gson.*;
import java.io.IOException;
import java.util.Map;

/** Recovers a committed altar debit when the corresponding chunk was older than the world journal. */
public final class RitualEnergy {
    record Balance(String operation, long stored) {}
    private static final Gson JSON = new Gson();
    private static Journal journal;

    private RitualEnergy() {}

    public static void install(Journal world) { journal = world; }
    static boolean ready() { return journal != null; }
    private static String key(String node) { return "ritualMana/v1/" + node; }
    static Balance recorded(String node) { return journal.get(key(node), Balance.class, null); }

    static void record(Map<String,JsonElement> writes,String node,String operation,long stored) {
        writes.put(key(node), JSON.toJsonTree(new Balance(operation, stored)));
    }

    static void commit(Map<String,JsonElement> writes) throws IOException {
        journal.transaction(writes);
    }
}
