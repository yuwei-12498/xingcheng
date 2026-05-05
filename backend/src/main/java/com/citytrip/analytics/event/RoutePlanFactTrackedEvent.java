package com.citytrip.analytics.event;

import com.citytrip.analytics.command.RoutePlanFactTrackCommand;

public class RoutePlanFactTrackedEvent {

    private final RoutePlanFactTrackCommand command;

    public RoutePlanFactTrackedEvent(RoutePlanFactTrackCommand command) {
        this.command = command;
    }

    public RoutePlanFactTrackCommand getCommand() {
        return command;
    }
}
