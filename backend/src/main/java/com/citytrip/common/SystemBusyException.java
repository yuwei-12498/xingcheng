package com.citytrip.common;

public class SystemBusyException extends RuntimeException {
    public SystemBusyException(String message) {
        super(message);
    }
}
