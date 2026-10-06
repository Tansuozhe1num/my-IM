package com.easychat.entity.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;


/**
 * 
 */
public class UserInfoBeauty implements Serializable {


	/**
	 * 自增
	 */
	private Integer id;

	/**
	 * 用户ID
	 */
        private String userId;

	/**
	 * 邮箱
	 */
	private String email;

	/**
	 * 状态
	 */
	private Integer status;


	public void setId(Integer id){
		this.id = id;
	}

	public Integer getId(){
		return this.id;
	}

        public void setUserId(String userId){
		this.userId = userId;
	}

        public String getUserId(){
		return this.userId;
	}

	public void setEmail(String email){
		this.email = email;
	}

	public String getEmail(){
		return this.email;
	}

	public void setStatus(Integer status){
		this.status = status;
	}

	public Integer getStatus(){
		return this.status;
	}

	@Override
	public String toString (){
		return "自增:"+(id == null ? "空" : id)+"，用户ID:"+(userId == null ? "空" : userId)+"，邮箱:"+(email == null ? "空" : email)+"，状态:"+(status == null ? "空" : status);
	}
}
