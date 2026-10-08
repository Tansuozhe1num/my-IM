package com.easychat.entity.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;


/**
 * 会话用户
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatSessionUser implements Serializable {


	/**
	 * 用户ID
	 */
	private String userId;

	/**
	 * 联系人ID
	 */
	private String contactId;

	/**
	 * 会话ID
	 */
	private String sessionId;

	/**
	 * 联系人名称
	 */
	private String contactName;

	private String lastMessage;

	private String lastReceiveTime;

	private Integer memberCount;

	@Override
	public String toString (){
		return "用户ID:"+(userId == null ? "空" : userId)+"，联系人ID:"+(contactId == null ? "空" : contactId)+"，会话ID:"+(sessionId == null ? "空" : sessionId)+"，联系人名称:"+(contactName == null ? "空" : contactName);
	}
}
