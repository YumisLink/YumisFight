package cn.yumis.fight.modules.character.api;

import com.google.gson.JsonElement;
import java.util.*;

/** Narrow persistence port: callers join these writes to their existing transaction. */
public interface CharacterPersistence {
    record Opened(Progress progress,CareerProfiles profiles,boolean fresh){}
    Opened open(UUID player);
    Progress read(UUID player);
    CareerProfiles profiles(UUID player);
    Map<String,JsonElement> writes(UUID player,Progress progress);
    Map<String,JsonElement> writes(UUID player,Progress progress,CareerProfiles profiles);
}
