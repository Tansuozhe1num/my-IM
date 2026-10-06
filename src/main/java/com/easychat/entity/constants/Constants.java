package com.easychat.entity.constants;

public class Constants {

    public static final String REDIS_CHECK_KEY_CODE = "easychat:checkcode:";

    public static final String REDIS_CHECK_Heart_Beat = "easychat:user:heartbeat:";

    public static final Long REDIS_Heart_Beat = 6L;

    public static final String REDIS_KEY_WS_TOKEN = "easychat:ws:token:";

    public static final String REDIS_KEY_WS_TOKEN_USERID = "easychat:ws:token:userid:";

    public static final String ROBOT_UID = "easychat:robot:uid";

    public static final String REDIS_SYSTEM_CONFIG = "easychat:system:config";

    public static final String FILE_PATH = "/file/";

    public static final String AVATOR_FILE_PATH = "avator/";

    public static final String IMAGE_SUFFER = ".png";

    public static final String COVER_IMAGE_SUFFER = "_cover.png";

    public static final String PASSWORDPATTERN = "^(?![0-9]+$)(?![a-zA-Z]+$)[0-9A-Za-z]{10}$";

    public static final String REDIS_CONTACT_LIST = "easychat:ws:contact:";

    public static final String REDIS_MQ_MESSAGE_ONLY_MARK = "easy:chat:message:id:";

    public static final String REDIS_MQ_ERROR_MESSAGE = "easy:chat:error:message:id";
    public static final String REDIS_MQ_EXTEND_MESSAGE = "easy:chat:extend:message:id";

    public static final Integer ONE_DAY = 60 * 60 * 24;
}
