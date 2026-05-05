package com.citytrip.analytics.event;

import com.citytrip.analytics.command.UserBehaviorTrackCommand;

public class UserBehaviorTrackedEvent {

    private final UserBehaviorTrackCommand command;

    public UserBehaviorTrackedEvent(UserBehaviorTrackCommand command) {
        this.command = command;
    }

    public UserBehaviorTrackCommand getCommand() {
        return command;
    }
}
