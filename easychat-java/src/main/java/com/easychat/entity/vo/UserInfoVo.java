package com.easychat.entity.vo;

import java.io.Serializable;

/**
 *
 */
public class UserInfoVo implements Serializable {

    private static final long SerializableUID = -71219829819328222L;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 0:女 1:男
     */
    private Integer sex;

    private Integer joinType;

    /**
     * 个性签名
     */
    private String personalSignature;

    private String areaCode;

    private String areaName;

    private String token;

    private Boolean admin;

    private Integer contactStatus;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public Integer getSex() {
        return sex;
    }

    public void setSex(Integer sex) {
        this.sex = sex;
    }

    public Integer getJoinType() {
        return joinType;
    }

    public void setJoinType(Integer joinType) {
        this.joinType = joinType;
    }

    public String getPersonalSignature() {
        return personalSignature;
    }

    public void setPersonalSignature(String personalSignature) {
        this.personalSignature = personalSignature;
    }

    public String getAreaCode() {
        return areaCode;
    }

    public void setAreaCode(String areaCode) {
        this.areaCode = areaCode;
    }

    public String getAreaName() {
        return areaName;
    }

    public void setAreaName(String areaName) {
        this.areaName = areaName;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Boolean getAdmin() {
        return admin;
    }

    public void setAdmin(Boolean admin) {
        this.admin = admin;
    }

    public Integer getContactStatus() {
        return contactStatus;
    }

    public void setContactStatus(Integer contactStatus) {
        this.contactStatus = contactStatus;
    }
}
