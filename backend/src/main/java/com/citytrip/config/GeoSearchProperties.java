package com.citytrip.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.geo")
public class GeoSearchProperties {

    /**
     * 是否启用外部 GEO API。
     */
    private boolean enabled = false;

    /**
     * GEO API 基础地址。
     */
    private String baseUrl = "";

    /**
     * GEO API Key（支持 query/header 两种传递方式）。
     */
    private String apiKey = "";

    /**
     * API Key 的 query 参数名。
     */
    private String apiKeyQueryName = "key";

    /**
     * API Key 的 header 名，留空则不走 header。
     */
    private String apiKeyHeaderName = "";

    /**
     * 地理编码接口路径。
     */
    private String geocodePath = "/geo/geocode";

    /**
     * 关键词检索接口路径。
     */
    private String keywordSearchPath = "/geo/search";

    /**
     * 周边检索接口路径。
     */
    private String nearbySearchPath = "/geo/nearby";

    /**
     * 璺緞瑙勫垝鎺ュ彛璺緞锛堢敤浜庣簿缁嗛€氳鏃堕棿浼扮畻锛夈€?     */
    private String routePath = "/geo/route";

    /**
     * GEO 请求连接超时（毫秒）。
     */
    private int connectTimeoutMs = 800;

    /**
     * GEO 请求读取超时（毫秒）。
     */
    private int readTimeoutMs = 1200;

    /**
     * 单次默认拉取条数。
     */
    private int defaultLimit = 8;

    /**
     * 默认城市（兜底）。
     */
    private String defaultCityName = "成都";

    /**
     * 周边检索默认半径（米）。
     */
    private int nearbyRadiusMeters = 1200;

    /**
     * 单个行程节点周边补全超时（毫秒）。
     */
    private int nodeNearbyTimeoutMs = 260;
}
