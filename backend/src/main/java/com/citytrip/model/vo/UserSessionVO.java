package com.citytrip.model.vo;

import lombok.Data;

@Data
public class UserSessionVO {
    private Long id;
    private String username;
    private String nickname;
    private Integer role;
    private String token;
}
