package com.easychat.entity.enums;

public enum UserContactTypeEnum {

    USER(0, "U", "好友"),
    Group(1, "G", "群聊");

    private Integer type;

    private String prefix;

    private String desc;

    UserContactTypeEnum(Integer type, String prefix, String desc) {
        this.type = type;
        this.prefix = prefix;
        this.desc = desc;
    }

    public Integer getType() {
        return type;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getDesc() {
        return desc;
    }

    public static UserContactTypeEnum getByType(Integer type) {
        for (UserContactTypeEnum item : UserContactTypeEnum.values()) {
            if (item.getType().equals(type)) {
                return item;
            }
        }
        return null;
    }

    public static UserContactTypeEnum getByPrefix(String prefix) {
        for (UserContactTypeEnum item : UserContactTypeEnum.values()) {
            if (item.getPrefix().equals(prefix)) {
                return item;
            }
        }
        return null;
    }
}
