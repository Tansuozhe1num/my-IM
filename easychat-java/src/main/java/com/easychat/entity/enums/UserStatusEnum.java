package com.easychat.entity.enums;

public enum UserStatusEnum {

    Enable(0, "启用"),
    Disable(1, "禁用");

    private Integer code;

    private String desc;

    UserStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static UserStatusEnum getByCode(Integer code) {
        for (UserStatusEnum item : UserStatusEnum.values()) {
            if (item.getCode().equals(code)) {
                return item;
            }
        }
        return null;
    }

}
