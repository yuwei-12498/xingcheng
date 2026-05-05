package com.citytrip.service.application.itinerary;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.citytrip.common.NotFoundException;
import com.citytrip.mapper.SavedItineraryEditVersionMapper;
import com.citytrip.model.entity.SavedItineraryEditVersion;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

@Repository
public class SavedItineraryEditVersionRepository {

    private final SavedItineraryEditVersionMapper savedItineraryEditVersionMapper;

    public SavedItineraryEditVersionRepository(SavedItineraryEditVersionMapper savedItineraryEditVersionMapper) {
        this.savedItineraryEditVersionMapper = savedItineraryEditVersionMapper;
    }

    public List<SavedItineraryEditVersion> listByItineraryId(Long itineraryId) {
        if (itineraryId == null) {
            return List.of();
        }
        QueryWrapper<SavedItineraryEditVersion> wrapper = new QueryWrapper<>();
        wrapper.eq("itinerary_id", itineraryId).orderByAsc("version_no").orderByAsc("id");
        return savedItineraryEditVersionMapper.selectList(wrapper);
    }

    public SavedItineraryEditVersion save(SavedItineraryEditVersion entity) {
        if (entity.getId() == null) {
            savedItineraryEditVersionMapper.insert(entity);
        } else {
            savedItineraryEditVersionMapper.updateById(entity);
        }
        return entity;
    }

    public void clearActiveFlag(Long itineraryId) {
        if (itineraryId == null) {
            return;
        }
        UpdateWrapper<SavedItineraryEditVersion> wrapper = new UpdateWrapper<>();
        wrapper.eq("itinerary_id", itineraryId).set("active_flag", 0);
        savedItineraryEditVersionMapper.update(null, wrapper);
    }

    public SavedItineraryEditVersion requireOwnedVersion(Long userId, Long itineraryId, Long versionId) {
        SavedItineraryEditVersion entity = versionId == null ? null : savedItineraryEditVersionMapper.selectById(versionId);
        if (entity == null
                || !Objects.equals(entity.getUserId(), userId)
                || !Objects.equals(entity.getItineraryId(), itineraryId)) {
            throw new NotFoundException("行程版本不存在");
        }
        return entity;
    }

    public void markActive(Long versionId) {
        if (versionId == null) {
            return;
        }
        UpdateWrapper<SavedItineraryEditVersion> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", versionId).set("active_flag", 1);
        savedItineraryEditVersionMapper.update(null, wrapper);
    }
}
