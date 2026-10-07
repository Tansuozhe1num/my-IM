package com.easychat.entity.query;



/**
 * 参数
 */
public class UserInfoBeautyQuery extends BaseParam {


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

	private String emailFuzzy;

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

	public void setEmailFuzzy(String emailFuzzy){
		this.emailFuzzy = emailFuzzy;
	}

	public String getEmailFuzzy(){
		return this.emailFuzzy;
	}

	public void setStatus(Integer status){
		this.status = status;
	}

	public Integer getStatus(){
		return this.status;
	}

}
