package com.citytrip.analytics;

import com.citytrip.analytics.command.UserBehaviorTrackCommand;
import com.citytrip.analytics.event.UserBehaviorTrackedEvent;
import com.citytrip.mapper.UserBehaviorEventMapper;
import com.citytrip.model.entity.UserBehaviorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.context.event.EventListener;

@Component
public class UserBehaviorEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserBehaviorEventListener.class);

    private final UserBehaviorEventMapper userBehaviorEventMapper;

    public UserBehaviorEventListener(UserBehaviorEventMapper userBehaviorEventMapper) {
        this.userBehaviorEventMapper = userBehaviorEventMapper;
    }

    @Async("analyticsEventExecutor")
    @EventListener
    @Transactional
    public void onUserBehaviorTracked(UserBehaviorTrackedEvent event) {
        if (event == null || event.getCommand() == null || !StringUtils.hasText(event.getCommand().getEventType())) {
            return;
        }

        UserBehaviorTrackCommand command = event.getCommand();
        UserBehaviorEvent entity = new UserBehaviorEvent();
        entity.setUserId(command.getUserId());
        entity.setSessionId(command.getSessionId());
        entity.setRequestId(command.getRequestId());
        entity.setEventType(command.getEventType());
        entity.setEventSource(command.getEventSource());
        entity.setItineraryId(command.getItineraryId());
        entity.setPoiId(command.getPoiId());
        entity.setOptionKey(command.getOptionKey());
        entity.setInteractionWeight(command.getInteractionWeight());
        entity.setSuccessFlag(Boolean.TRUE.equals(command.getSuccessFlag()) ? 1 : 0);
        entity.setCostMs(command.getCostMs());
        entity.setRequestUri(command.getRequestUri());
        entity.setHttpMethod(command.getHttpMethod());
        entity.setClientIp(command.getClientIp());
        entity.setUserAgent(command.getUserAgent());
        entity.setReferer(command.getReferer());
        entity.setExtraJson(command.getExtraJson());
        entity.setEventTime(command.getEventTime());
        try {
            userBehaviorEventMapper.insert(entity);
        } catch (RuntimeException ex) {
            log.warn("忽略用户行为埋点写入失败，eventType={}, reason={}",
                    command.getEventType(),
                    ex.getMessage());
        }
    }
}
