package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.presentation.api.client.ClientSettings;
import cn.yumis.fight.modules.skills.api.SkillVisualEvent;
import cn.yumis.fight.modules.skills.api.SkillVisualEvent.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.List;

record SkillSound(Phase phase, SoundEvent sound, float volume, float pitch) {
    static void play(SkillVisualEvent event, List<SkillSound> sounds) {
        if (sounds.isEmpty()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var point = event.position();
        if (event.phase() == Phase.START) {
            var caster = level.getPlayerByUUID(event.caster());
            if (caster != null) point = new SkillVisualEvent.Point(caster.getX(), caster.getY(), caster.getZ());
        }
        for (var cue : sounds) {
            if (cue.phase() == event.phase()) {
                playAt(point, cue.sound(), cue.volume(), cue.pitch());
            }
        }
    }

    static void playAt(SkillVisualEvent.Point point, SoundEvent sound, float volume, float pitch) {
        playAt(point.x(), point.y(), point.z(), sound, volume, pitch);
    }

    static void playAt(double x, double y, double z, SoundEvent sound, float volume, float pitch) {
        var level = Minecraft.getInstance().level;
        float scaledVolume = volume * (ClientSettings.skillVolumePercent() / 100f);
        if (level != null && scaledVolume > 0f) {
            level.playLocalSound(x, y, z, sound, SoundSource.PLAYERS, scaledVolume, pitch, false);
        }
    }
}
