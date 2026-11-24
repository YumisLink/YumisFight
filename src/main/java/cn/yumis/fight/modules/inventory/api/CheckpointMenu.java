package cn.yumis.fight.modules.inventory.api;
/** Closing this menu must checkpoint player storage even when no external slots changed. */
public interface CheckpointMenu {
    /** Contents that closeCursor should return in the inventory transaction before native close. */
    default boolean hasTemporaryInputs(){return false;}
}
