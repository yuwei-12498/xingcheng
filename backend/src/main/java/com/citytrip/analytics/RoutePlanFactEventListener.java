package com.citytrip.analytics;

import com.citytrip.analytics.event.RoutePlanFactTrackedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class RoutePlanFactEventListener {

    private static final Logger log = LoggerFactory.getLogger(RoutePlanFactEventListener.class);

    private final RoutePlanFactPersistenceService routePlanFactPersistenceService;

    public RoutePlanFactEventListener(RoutePlanFactPersistenceService routePlanFactPersistenceService) {
        this.routePlanFactPersistenceService = routePlanFactPersistenceService;
    }

    @Async("analyticsEventExecutor")
    @EventListener
    public void onRoutePlanFactTracked(RoutePlanFactTrackedEvent event) {
        if (event == null || event.getCommand() == null) {
            return;
        }
        try {
            routePlanFactPersistenceService.persist(event.getCommand());
        } catch (RuntimeException ex) {
            log.warn("忽略路线事实表写入失败，planSource={}, reason={}",
                    event.getCommand().getPlanSource(),
                    ex.getMessage());
        }
    }
}
