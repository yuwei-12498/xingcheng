package com.citytrip.service;

import com.citytrip.common.BadRequestException;
import org.springframework.util.StringUtils;

import java.util.Arrays;

public enum TravelModeRequest {
    AUTO("auto", null),
    WALK("walk", "\u6B65\u884C"),
    BIKE("bike", "\u9A91\u884C"),
    TRANSIT("transit", "\u516C\u4EA4+\u6B65\u884C"),
    TAXI("taxi", "\u6253\u8F66");

    private final String code;
    private final String label;

    TravelModeRequest(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public boolean isAuto() {
        return this == AUTO;
    }

    public static TravelModeRequest fromCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return AUTO;
        }
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(raw.trim()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unsupported travel mode: " + raw));
    }
}
