package com.citytrip.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class ChatStatusVO {
    private String provider;
    private boolean configured;
    private boolean realModelAvailable;
    private boolean fallbackToMock;
    private int timeoutSeconds;
    private String model;
    private String baseUrl;
    private boolean toolReady;
    private boolean geoReady;
    private boolean embeddingReady;
    private boolean rerankReady;
    private List<String> warnings;
    private String message;
}
