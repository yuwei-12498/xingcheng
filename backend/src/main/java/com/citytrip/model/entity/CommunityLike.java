package com.citytrip.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("community_like")
public class CommunityLike {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long itineraryId;
    private Long userId;
    private LocalDateTime createTime;
}
