package cn.yumis.fight.modules.inventory.api;
import cn.yumis.fight.architecture.TransactionParticipant;
import com.google.gson.JsonElement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
/** 具有额外持久状态的容器能力；库存事务不识别具体机器类型。 */
public interface PersistentContainer extends Container,TransactionParticipant<JsonElement> {
    BlockPos getBlockPos();
    Level getLevel();
    void setChanged();
    JsonElement transactionState();
    void restoreTransactionState(JsonElement value);
    void requestRecovery();
    @Override default JsonElement snapshot(){return transactionState();}
    @Override default void restore(JsonElement value){restoreTransactionState(value);}
}
