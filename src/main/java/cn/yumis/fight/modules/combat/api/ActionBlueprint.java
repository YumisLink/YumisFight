package cn.yumis.fight.modules.combat.api;

import java.util.*;

/**
 * Pure, actor-agnostic skill/move blueprint shared across players, humanoid enemies and sourceless actions.
 * Blueprints declare timing, geometry cues, cancel priority and status leases without referencing platform entities.
 */
public final class ActionBlueprint {
    public enum Geometry { NONE, SLASH, FAN, AREA, RECT, WAVE, PROJECTILE, DASH }

    public record Cue(
        long atMillis,
        Geometry geometry,
        double range,
        double width,
        double height,
        double coefficientShare,
        double knockback,
        double controlSeconds,
        boolean inward,
        boolean ground,
        boolean atCenter,
        String visualPhase,
        String effectTag,
        boolean releasePose
    ) {
        public Cue {
            if (atMillis < 0) throw new IllegalArgumentException("Negative cue time");
            Objects.requireNonNull(geometry, "geometry");
            if (!Double.isFinite(range) || range < 0) range = 0;
            if (!Double.isFinite(width) || width < 0) width = 0;
            if (!Double.isFinite(height) || height < 0) height = 0;
            if (!Double.isFinite(coefficientShare) || coefficientShare < 0) coefficientShare = 0;
            if (!Double.isFinite(knockback) || knockback < 0) knockback = 0;
            if (!Double.isFinite(controlSeconds) || controlSeconds < 0) controlSeconds = 0;
            visualPhase = visualPhase == null ? "" : visualPhase;
            effectTag = effectTag == null ? "" : effectTag;
        }
    }

    private final String id;
    private final String title;
    private final String poseClip;
    private final long durationMillis;
    private final long canBreakAtMillis;
    private final int cancelLevel;
    private final int stiffLevel;
    private final boolean movement;
    private final boolean fixedActionTime;
    private final Set<ActionStatus> activeStatuses;
    private final List<Cue> cues;

    private ActionBlueprint(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.title = b.title == null ? b.id : b.title;
        this.poseClip = b.poseClip == null ? b.id : b.poseClip;
        this.durationMillis = Math.max(0, b.durationMillis);
        this.canBreakAtMillis = b.canBreakAtMillis;
        this.cancelLevel = b.cancelLevel;
        this.stiffLevel = b.stiffLevel;
        this.movement = b.movement;
        this.fixedActionTime = b.fixedActionTime;
        this.activeStatuses = b.activeStatuses.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(b.activeStatuses));
        List<Cue> sorted = new ArrayList<>(b.cues);
        sorted.sort(Comparator.comparingLong(Cue::atMillis));
        this.cues = List.copyOf(sorted);
    }

    public String id() { return id; }
    public String title() { return title; }
    public String poseClip() { return poseClip; }
    public long durationMillis() { return durationMillis; }
    public long canBreakAtMillis() { return canBreakAtMillis; }
    public int cancelLevel() { return cancelLevel; }
    public int stiffLevel() { return stiffLevel; }
    public boolean movement() { return movement; }
    public boolean fixedActionTime() { return fixedActionTime; }
    public Set<ActionStatus> activeStatuses() { return activeStatuses; }
    public List<Cue> cues() { return cues; }

    public static Builder builder(String id, String title) {
        return new Builder(id, title);
    }

    public static final class Builder {
        private final String id;
        private final String title;
        private String poseClip;
        private long durationMillis = 400;
        private long canBreakAtMillis = -1;
        private int cancelLevel;
        private int stiffLevel;
        private boolean movement;
        private boolean fixedActionTime;
        private final EnumSet<ActionStatus> activeStatuses = EnumSet.noneOf(ActionStatus.class);
        private final List<Cue> cues = new ArrayList<>();

        private Builder(String id, String title) {
            if (id == null || id.isEmpty()) throw new IllegalArgumentException("Blueprint id must not be empty");
            this.id = id;
            this.title = title;
        }

        public Builder poseClip(String clip) { this.poseClip = clip; return this; }
        public Builder duration(long millis) { this.durationMillis = Math.max(0, millis); return this; }
        public Builder canBreakAt(long millis) { this.canBreakAtMillis = millis; return this; }
        public Builder cancelLevel(int level) { this.cancelLevel = level; return this; }
        public Builder stiffLevel(int level) { this.stiffLevel = level; return this; }
        public Builder movement(boolean value) { this.movement = value; return this; }
        public Builder fixedActionTime(boolean value) { this.fixedActionTime = value; return this; }
        public Builder status(ActionStatus status) { this.activeStatuses.add(Objects.requireNonNull(status)); return this; }
        public Builder cue(Cue cue) { this.cues.add(Objects.requireNonNull(cue)); return this; }

        public Builder slash(long atMillis, double range, double width, double height, double share, double knockback, String visualPhase) {
            return cue(new Cue(atMillis, Geometry.SLASH, range, width, height, share, knockback, 0, false, false, false, visualPhase, "", false));
        }

        public Builder fan(long atMillis, double range, double degrees, double height, double share, double knockback, String visualPhase) {
            return cue(new Cue(atMillis, Geometry.FAN, range, degrees, height, share, knockback, 0, false, false, false, visualPhase, "", false));
        }

        public Builder area(long atMillis, double radius, double share, boolean inward, boolean atCenter, String visualPhase) {
            return cue(new Cue(atMillis, Geometry.AREA, radius, radius * 2, 4, share, inward ? 0.4 : 0, 0, inward, false, atCenter, visualPhase, "", false));
        }

        public Builder wave(long atMillis, double range, double width, double height, double share, boolean ground, String visualPhase) {
            return cue(new Cue(atMillis, Geometry.WAVE, range, width, height, share, 0.04, 0, false, ground, false, visualPhase, "", false));
        }

        public Builder dash(long atMillis, double distance, double width, double share, String visualPhase) {
            return cue(new Cue(atMillis, Geometry.DASH, distance, width, 2, share, 0.4, 0, false, false, false, visualPhase, "", false));
        }

        public Builder effect(long atMillis, String effectTag, String visualPhase, boolean releasePose) {
            return cue(new Cue(atMillis, Geometry.NONE, 0, 0, 0, 0, 0, 0, false, false, false, visualPhase, effectTag, releasePose));
        }

        public ActionBlueprint build() {
            return new ActionBlueprint(this);
        }
    }
}
