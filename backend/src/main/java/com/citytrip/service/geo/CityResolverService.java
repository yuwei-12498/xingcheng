package com.citytrip.service.geo;

import com.citytrip.config.GeoSearchProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class CityResolverService {

    private static final Map<String, String> CITY_CODE_TO_NAME = Map.of(
            "CD", "成都",
            "BJ", "北京",
            "SH", "上海",
            "GZ", "广州",
            "SZ", "深圳",
            "CQ", "重庆",
            "HZ", "杭州",
            "WH", "武汉",
            "XA", "西安"
    );

    private static final Map<String, String> CITY_NAME_TO_CODE = Map.ofEntries(
            Map.entry("成都", "CD"),
            Map.entry("chengdu", "CD"),
            Map.entry("北京", "BJ"),
            Map.entry("beijing", "BJ"),
            Map.entry("上海", "SH"),
            Map.entry("shanghai", "SH"),
            Map.entry("广州", "GZ"),
            Map.entry("guangzhou", "GZ"),
            Map.entry("深圳", "SZ"),
            Map.entry("shenzhen", "SZ"),
            Map.entry("重庆", "CQ"),
            Map.entry("chongqing", "CQ"),
            Map.entry("杭州", "HZ"),
            Map.entry("hangzhou", "HZ"),
            Map.entry("武汉", "WH"),
            Map.entry("wuhan", "WH"),
            Map.entry("西安", "XA"),
            Map.entry("xian", "XA"),
            Map.entry("xi'an", "XA")
    );

    private final GeoSearchProperties geoSearchProperties;

    public CityResolverService(GeoSearchProperties geoSearchProperties) {
        this.geoSearchProperties = geoSearchProperties;
    }

    public String resolveCityName(String cityName, String cityCode) {
        if (StringUtils.hasText(cityName)) {
            return cityName.trim();
        }
        if (StringUtils.hasText(cityCode)) {
            String code = cityCode.trim().toUpperCase(Locale.ROOT);
            if (CITY_CODE_TO_NAME.containsKey(code)) {
                return CITY_CODE_TO_NAME.get(code);
            }
        }
        return StringUtils.hasText(geoSearchProperties.getDefaultCityName())
                ? geoSearchProperties.getDefaultCityName().trim()
                : "成都";
    }

    public String resolveCityCode(String cityCode, String cityName) {
        if (StringUtils.hasText(cityCode)) {
            return cityCode.trim().toUpperCase(Locale.ROOT);
        }
        if (StringUtils.hasText(cityName)) {
            String key = cityName.trim().toLowerCase(Locale.ROOT);
            if (CITY_NAME_TO_CODE.containsKey(key)) {
                return CITY_NAME_TO_CODE.get(key);
            }
        }
        String defaultName = StringUtils.hasText(geoSearchProperties.getDefaultCityName())
                ? geoSearchProperties.getDefaultCityName().trim()
                : "成都";
        return CITY_NAME_TO_CODE.getOrDefault(defaultName.toLowerCase(Locale.ROOT), "CD");
    }

    public String guessCityNameFromText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String source = text.trim();
        Set<String> cityNames = new TreeSet<>((a, b) -> Integer.compare(b.length(), a.length()));
        cityNames.addAll(CITY_CODE_TO_NAME.values());
        for (String cityName : cityNames) {
            if (StringUtils.hasText(cityName) && source.contains(cityName)) {
                return cityName;
            }
        }
        String lower = source.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : CITY_NAME_TO_CODE.entrySet()) {
            if (lower.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                return resolveCityName(entry.getValue(), entry.getValue());
            }
        }
        return null;
    }
}
