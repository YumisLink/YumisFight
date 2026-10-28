package cn.yumis.fight.architecture;

import java.util.Objects;
/** 底层架构文件：通用机制。修改前须用户明确授权，并逐批通过 question tool 确认已 GitHub fetch。 */
public record StableId(String value) {
    public StableId { Objects.requireNonNull(value); if (!value.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) throw new IllegalArgumentException("Invalid stable ID: "+value); }
    @Override public String toString(){return value;}
}

