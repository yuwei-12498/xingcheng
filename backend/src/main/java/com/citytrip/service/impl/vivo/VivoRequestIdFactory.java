package com.citytrip.service.impl.vivo;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class VivoRequestIdFactory {

    public String create() {
        return UUID.randomUUID().toString();
    }
}
