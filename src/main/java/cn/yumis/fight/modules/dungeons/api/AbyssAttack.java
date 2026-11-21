package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** The same geometry and times drive the telegraph, impact effect and recipient-side contact. */
public record AbyssAttack(UUID id, UUID source, AbyssParty.Arena arena, Vec3 origin, Vec3 direction,
                          Shape shape, Effect effect, double range, double width, double elevation,
                          long warning, long release, long end, double coefficient, boolean magic, int element,
                          boolean telegraph) {
    public enum Shape { CIRCLE, FAN, LINE, RING }
    public enum Effect { SLASH, WAVE, ORB, ICE, FIRE, SUMMON, PROJECTION, BONE, COLUMN, LANDING, BEAM }

    public boolean travels() {
        return shape == Shape.LINE && (effect == Effect.WAVE || effect == Effect.ORB || effect == Effect.ICE);
    }

    public double progress(long time) {
        return Math.clamp((time - release) / (double) (end - release), 0, 1);
    }

    public boolean contains(Vec3 point) {
        if (!arena.contains(point)) return false;
        Vec3 offset = point.subtract(origin);
        if (Math.abs(offset.y) > 2.6) return false;
        double radius = offset.horizontalDistance();
        double forward = offset.dot(direction);
        return switch (shape) {
            case CIRCLE -> radius <= range;
            case FAN -> radius <= range && (radius < .01 || forward / radius >= Math.cos(width));
            case LINE -> forward >= 0 && forward <= range
                && Math.abs(offset.x * direction.z - offset.z * direction.x) <= width * .5;
            case RING -> radius >= range - width && radius <= range;
        };
    }

    public int color() {
        return switch (effect) {
            case BONE -> 0xc7d9a0;
            case COLUMN, LANDING -> 0xe07b64;
            default -> switch (element) {
                case 0 -> 0xffdc8d;
                case 1 -> 0xff7652;
                case 2 -> 0x91dafa;
                case 3 -> 0xb17ce3;
                default -> 0xf2a486;
            };
        };
    }
}
