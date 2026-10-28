package cn.yumis.fight.architecture;

/** 底层架构文件：通用机制。修改前须用户明确授权，并逐批通过 question tool 确认已 GitHub fetch。 */
public interface TransactionParticipant<S> {
    String key();
    S snapshot();
    void restore(S snapshot)throws Exception;
}

