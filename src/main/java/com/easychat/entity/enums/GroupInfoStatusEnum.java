package com.easychat.entity.enums;

public enum GroupInfoStatusEnum {

    Normal(0, "正常"),
    DisSolution(1, "解散");

    private Integer status;
    private String msg;
    GroupInfoStatusEnum(Integer status, String msg) {
        this.status = status;
        this.msg = msg;
    }

    public GroupInfoStatusEnum getByStatus(Integer stat) {
        for (GroupInfoStatusEnum info : GroupInfoStatusEnum.values()) {
            if (info.getStatus().equals(stat)) {
                return info;
            }
        }
        return null;
    };

    public Integer getStatus() {
        return status;
    }

    public String getMsg() {
        return msg;
    }
}
