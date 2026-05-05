package com.citytrip.service.application.community;

import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.service.impl.CommunityItineraryCacheService;
import org.springframework.stereotype.Service;

@Service
public class CommunityCacheInvalidationService {

    private final CommunityItineraryCacheService communityItineraryCacheService;

    public CommunityCacheInvalidationService(CommunityItineraryCacheService communityItineraryCacheService) {
        this.communityItineraryCacheService = communityItineraryCacheService;
    }

    public void markDirty() {
        communityItineraryCacheService.markCommunityPageCacheDirty();
    }

    public void evictIfPublic(SavedItinerary entity) {
        if (entity != null && entity.getIsPublic() != null && entity.getIsPublic() == 1) {
            communityItineraryCacheService.markCommunityPageCacheDirty();
        }
    }
}
