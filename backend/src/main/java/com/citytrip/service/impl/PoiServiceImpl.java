package com.citytrip.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.citytrip.mapper.PoiMapper;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.PoiSearchResultVO;
import com.citytrip.service.PoiService;
import com.citytrip.service.geo.GeoPoiCandidate;
import com.citytrip.service.geo.GeoSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PoiServiceImpl extends ServiceImpl<PoiMapper, Poi> implements PoiService {

    private static final int STALE_DAYS = 14;
    @Autowired(required = false)
    private GeoSearchService geoSearchService;

    @Override
    public Poi getDetailWithStatus(Long id, LocalDate tripDate) {
        Poi poi = getById(id);
        if (poi == null) {
            return null;
        }
        applyOperatingStatus(poi, tripDate == null ? LocalDate.now() : tripDate);
        return poi;
    }

    @Override
    public List<Poi> enrichOperatingStatus(List<Poi> pois, LocalDate tripDate) {
        if (pois == null || pois.isEmpty()) {
            return Collections.emptyList();
        }
        LocalDate effectiveDate = tripDate == null ? LocalDate.now() : tripDate;
        pois.forEach(poi -> applyOperatingStatus(poi, effectiveDate));
        return pois;
    }

    @Override
    public List<PoiSearchResultVO> searchLive(String keyword, String city, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        String normalizedKeyword = keyword.trim();
        String normalizedCity = StringUtils.hasText(city) ? city.trim() : null;
        int boundedLimit = Math.max(1, Math.min(limit <= 0 ? 8 : limit, 20));

        if (geoSearchService != null) {
            List<PoiSearchResultVO> liveResults = geoSearchService.searchByKeyword(normalizedKeyword, normalizedCity, boundedLimit).stream()
                    .map(this::toSearchResult)
                    .limit(boundedLimit)
                    .toList();
            if (!liveResults.isEmpty()) {
                return liveResults;
            }
        }

        return baseMapper.searchByNameInCity(normalizedKeyword, null, normalizedCity, boundedLimit).stream()
                .map(this::toSearchResult)
                .limit(boundedLimit)
                .toList();
    }

    private void applyOperatingStatus(Poi poi, LocalDate tripDate) {
        boolean stale = isStatusStale(poi.getStatusUpdatedAt());
        boolean temporarilyClosed = Integer.valueOf(1).equals(poi.getTemporarilyClosed());
        boolean closedByWeekday = parseClosedWeekdays(poi.getClosedWeekdays()).contains(tripDate.getDayOfWeek());
        boolean missingBusinessHours = poi.getOpenTime() == null || poi.getCloseTime() == null;

        poi.setStatusStale(stale);
        poi.setAvailableOnTripDate(Boolean.TRUE);
        poi.setOperatingStatus("OPEN");
        poi.setAvailabilityNote(trimToNull(poi.getStatusNote()));

        if (temporarilyClosed) {
            poi.setAvailableOnTripDate(Boolean.FALSE);
            poi.setOperatingStatus("CLOSED");
            poi.setAvailabilityNote(firstNonBlank(poi.getStatusNote(), "\u666F\u70B9\u5F53\u524D\u5904\u4E8E\u4E34\u65F6\u5173\u95ED\u72B6\u6001\u3002"));
            return;
        }

        if (closedByWeekday) {
            poi.setAvailableOnTripDate(Boolean.FALSE);
            poi.setOperatingStatus("CLOSED");
            poi.setAvailabilityNote(firstNonBlank(
                    poi.getStatusNote(),
                    "\u8BE5\u666F\u70B9\u5728" + describeWeekday(tripDate.getDayOfWeek()) + "\u4E0D\u5F00\u653E\u3002"
            ));
            return;
        }

        if (stale || missingBusinessHours) {
            poi.setOperatingStatus("CHECK_REQUIRED");
            poi.setAvailabilityNote(firstNonBlank(
                    poi.getStatusNote(),
                    missingBusinessHours
                            ? "\u8425\u4E1A\u65F6\u95F4\u4FE1\u606F\u4E0D\u5B8C\u6574\uFF0C\u8BF7\u51FA\u53D1\u524D\u518D\u6B21\u786E\u8BA4\u3002"
                            : "\u5F53\u524D\u573A\u9986\u72B6\u6001\u8D85\u8FC7 14 \u5929\u672A\u6838\u9A8C\uFF0C\u8BF7\u51FA\u53D1\u524D\u518D\u6B21\u786E\u8BA4\u3002"
            ));
        }
    }

    private boolean isStatusStale(LocalDateTime updatedAt) {
        return updatedAt == null || updatedAt.toLocalDate().isBefore(LocalDate.now().minusDays(STALE_DAYS));
    }

    private Set<DayOfWeek> parseClosedWeekdays(String raw) {
        if (!StringUtils.hasText(raw)) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> {
                    try {
                        return DayOfWeek.valueOf(value.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                })
                .filter(day -> day != null)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    private String describeWeekday(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "\u5468\u4E00";
            case TUESDAY -> "\u5468\u4E8C";
            case WEDNESDAY -> "\u5468\u4E09";
            case THURSDAY -> "\u5468\u56DB";
            case FRIDAY -> "\u5468\u4E94";
            case SATURDAY -> "\u5468\u516D";
            case SUNDAY -> "\u5468\u65E5";
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private PoiSearchResultVO toSearchResult(GeoPoiCandidate candidate) {
        return new PoiSearchResultVO(
                candidate.getName(),
                candidate.getAddress(),
                candidate.getCategory(),
                candidate.getLatitude(),
                candidate.getLongitude(),
                candidate.getCityName(),
                null,
                StringUtils.hasText(candidate.getSource()) ? candidate.getSource() : "vivo-geo"
        );
    }

    private PoiSearchResultVO toSearchResult(Poi poi) {
        return new PoiSearchResultVO(
                poi.getName(),
                poi.getAddress(),
                poi.getCategory(),
                poi.getLatitude(),
                poi.getLongitude(),
                poi.getCityName(),
                poi.getCityCode(),
                StringUtils.hasText(poi.getStatusSource()) ? poi.getStatusSource() : "local-db"
        );
    }
}
