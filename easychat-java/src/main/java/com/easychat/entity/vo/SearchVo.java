package com.easychat.entity.vo;

import com.easychat.entity.po.GroupInfo;
import com.easychat.entity.po.UserInfo;
import java.util.List;

public class SearchVo {
    List<UserInfo> userInfoList;
    GroupInfo groupInfo;

    public List<UserInfo> getUserInfoList() {
        return userInfoList;
    }

    public void setUserInfoList(List<UserInfo> userInfoList) {
        this.userInfoList = userInfoList;
    }

    public GroupInfo getGroupInfo() {
        return groupInfo;
    }

    public void setGroupInfo(GroupInfo groupInfo) {
        this.groupInfo = groupInfo;
    }
}
