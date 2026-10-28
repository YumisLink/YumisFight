package cn.yumis.fight.architecture;

import java.util.Map;
/** 底层架构文件：通用机制。修改前须用户明确授权，并逐批通过 question tool 确认已 GitHub fetch。 */
public interface UnitOfWork<V> {
    V read(String key);
    void transaction(Map<String,V> changes)throws Exception;
}

