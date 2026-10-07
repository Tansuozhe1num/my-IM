package com.easychat.entity.vo;

import com.easychat.entity.po.GroupInfo;
import com.easychat.entity.po.UserContact;
import com.easychat.entity.po.UserInfo;

import java.util.List;

public class ContactInfoVO {
    public List<UserContact> friendUserInfo;

    public Integer ContactType;


    private List<GroupInfoVO> groupInfos;

    public List<UserContact> getFriendUserInfo() {
        return friendUserInfo;
    }

    public void setFriendUserInfo(List<UserContact> friendUserInfo) {
        this.friendUserInfo = friendUserInfo;
    }

    public Integer getContactType() {
        return ContactType;
    }

    public void setContactType(Integer contactType) {
        ContactType = contactType;
    }

    public List<GroupInfoVO> getGroupInfos() {
        return groupInfos;
    }

    public void setGroupInfos(List<GroupInfoVO> groupInfos) {
        this.groupInfos = groupInfos;
    }
}
