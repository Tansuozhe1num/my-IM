package com.easychat.entity.enums;

public enum JoinTypeEnum {

    JOIN(0, "直接加入"),
    APPLY(1, "需要审核");

    private Integer type;

    private String desc;

    JoinTypeEnum(Integer type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public Integer getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }

    public static JoinTypeEnum getByType(Integer type) {
        for (JoinTypeEnum item : JoinTypeEnum.values()) {
            if (item.getType().equals(type)) {
                return item;
            }
        }
        return null;
    }
}
