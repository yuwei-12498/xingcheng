package com.citytrip.service.application.itinerary;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.citytrip.common.NotFoundException;
import com.citytrip.mapper.UserCustomPoiMapper;
import com.citytrip.model.entity.UserCustomPoi;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class UserCustomPoiRepository {

    private final UserCustomPoiMapper userCustomPoiMapper;

    public UserCustomPoiRepository(UserCustomPoiMapper userCustomPoiMapper) {
        this.userCustomPoiMapper = userCustomPoiMapper;
    }

    public UserCustomPoi save(UserCustomPoi entity) {
        if (entity.getId() == null) {
            userCustomPoiMapper.insert(entity);
        } else {
            userCustomPoiMapper.updateById(entity);
        }
        return entity;
    }

    public UserCustomPoi requireOwned(Long userId, Long customPoiId) {
        UserCustomPoi entity = customPoiId == null ? null : userCustomPoiMapper.selectById(customPoiId);
        if (entity == null || !Objects.equals(entity.getUserId(), userId)) {
            throw new NotFoundException("自定义地点不存在");
        }
        return entity;
    }

    public List<UserCustomPoi> listOwned(Long userId) {
        if (userId == null) {
            return List.of();
        }
        QueryWrapper<UserCustomPoi> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("update_time").orderByDesc("id");
        return userCustomPoiMapper.selectList(wrapper);
    }
}
