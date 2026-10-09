package com.easychat.entity.query;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话信息参数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSessionQuery extends BaseParam {


	/**
	 * 会话ID
	 */
	private String sessionId;

	private String sessionIdFuzzy;

	/**
	 * 最后接受的消息
	 */
	private String lastMessage;

	private String lastMessageFuzzy;

	/**
	 * 最后接受消息时间毫秒
	 */
	private Long lastReceiveTime;


}
