package com.easychat.entity.enums;

public enum mqMessageStatusEnum {
    Process(0, "未处理"),
    Success(1, "成功");

    private Integer status;

    private String desc;

    mqMessageStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    public Integer getStatus() {
        return status;
    }

    public String getDesc() {
        return desc;
    }
}
