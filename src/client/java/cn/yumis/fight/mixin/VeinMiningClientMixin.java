package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.mining.client.VeinMiningClient;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiPlayerGameMode.class)
public abstract class VeinMiningClientMixin {
    @Unique private int ydnf$predictionSequence = -1;

    @WrapOperation(method = "startPrediction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/prediction/PredictiveAction;predict(I)Lnet/minecraft/network/protocol/Packet;"))
    private Packet<ServerGamePacketListener> ydnf$capturePrediction(PredictiveAction action, int sequence,
                                                                    Operation<Packet<ServerGamePacketListener>> original) {
        ydnf$predictionSequence = sequence;
        try {
            return original.call(action, sequence);
        } finally {
            ydnf$predictionSequence = -1;
        }
    }

    @WrapMethod(method = "destroyBlock")
    private boolean ydnf$sendVeinPlan(BlockPos pos, Operation<Boolean> original) {
        var plan = ydnf$predictionSequence == -1 ? null : VeinMiningClient.plan(pos, ydnf$predictionSequence);
        boolean removed = original.call(pos);
        // Vanilla sends the matching START or STOP packet after this prediction returns.
        if (removed && plan != null) VeinMiningClient.submitPlan(plan);
        return removed;
    }
}
