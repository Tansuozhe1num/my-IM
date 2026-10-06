package com.easychat.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenUserinfoDTO implements Serializable {

    private static final long serialVersionUID = -1212232332333312L;

    private String token;
    private String userId;
    private String nickname;
    private boolean admin;

}
