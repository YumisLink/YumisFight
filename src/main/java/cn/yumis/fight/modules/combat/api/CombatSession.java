package cn.yumis.fight.modules.combat.api;
import java.util.UUID;
/** Combat-owned mutable state for one login. Character progress is supplied separately by its module. */
public class CombatSession {
    public final UUID id=UUID.randomUUID();
    public Runtime resources;
    public Life life;
    public CombatSession(Runtime resources,Life life){this.resources=resources;this.life=life;}
}
