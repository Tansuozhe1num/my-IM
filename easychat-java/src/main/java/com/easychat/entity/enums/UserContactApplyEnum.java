package com.easychat.entity.enums;

public enum UserContactApplyEnum {

    Progress(0, "申请中"),
    Accept(1, "通过"),
    Reject(2, "不通过"),
    Black(3, "拉黑");

    private Integer status;
    private String desc;

    UserContactApplyEnum(Integer stat, String desc) {
        status = stat;
        this.desc = desc;
    }

    public Integer getStatus() {
        return status;
    }

    public String getDesc() {
        return desc;
    }

    public UserContactApplyEnum getByStatus(Integer status) {
        for (UserContactApplyEnum info : UserContactApplyEnum.values()) {
            if (info.getStatus().equals(status)) {
                return info;
            }
        }
        return null;
    }
}
