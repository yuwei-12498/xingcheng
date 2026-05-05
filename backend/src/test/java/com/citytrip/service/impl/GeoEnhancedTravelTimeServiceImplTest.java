package com.citytrip.service.impl;

import com.citytrip.model.entity.Poi;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoRouteEstimate;
import com.citytrip.service.geo.GeoSearchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeoEnhancedTravelTimeServiceImplTest {

    @Test
    void shouldGeocodeMissingCoordinatesBeforeEstimatingTravelTime() {
        GeoSearchService geoSearchService = mock(GeoSearchService.class);
        LocalTravelTimeServiceImpl localTravelTimeService = mock(LocalTravelTimeServiceImpl.class);
        GeoEnhancedTravelTimeServiceImpl service = new GeoEnhancedTravelTimeServiceImpl(geoSearchService, localTravelTimeService);

        Poi from = new Poi();
        from.setName("春熙路");
        from.setCityName("成都");
        Poi to = new Poi();
        to.setName("太古里");
        to.setCityName("成都");
        to.setLatitude(BigDecimal.valueOf(30.654));
        to.setLongitude(BigDecimal.valueOf(104.079));

        when(geoSearchService.geocode(anyString(), anyString()))
                .thenReturn(Optional.of(new GeoPoint(BigDecimal.valueOf(30.657), BigDecimal.valueOf(104.065))));
        when(localTravelTimeService.estimateTravelTimeMinutes(any(), any())).thenReturn(18);

        int result = service.estimateTravelTimeMinutes(from, to);

        ArgumentCaptor<Poi> fromCaptor = ArgumentCaptor.forClass(Poi.class);
        verify(localTravelTimeService).estimateTravelTimeMinutes(fromCaptor.capture(), any());
        assertThat(result).isEqualTo(18);
        assertThat(fromCaptor.getValue().getLatitude()).isEqualByComparingTo("30.657");
        assertThat(fromCaptor.getValue().getLongitude()).isEqualByComparingTo("104.065");
    }

    @Test
    void shouldFallbackToLocalServiceWhenGeocodeFails() {
        GeoSearchService geoSearchService = mock(GeoSearchService.class);
        LocalTravelTimeServiceImpl localTravelTimeService = mock(LocalTravelTimeServiceImpl.class);
        GeoEnhancedTravelTimeServiceImpl service = new GeoEnhancedTravelTimeServiceImpl(geoSearchService, localTravelTimeService);

        Poi from = new Poi();
        from.setName("未知地点");
        Poi to = new Poi();
        to.setName("太古里");

        when(geoSearchService.geocode(anyString(), anyString()))
                .thenThrow(new RuntimeException("timeout"));
        when(localTravelTimeService.estimateTravelTimeMinutes(from, to)).thenReturn(32);

        int result = service.estimateTravelTimeMinutes(from, to);

        assertThat(result).isEqualTo(32);
        verify(localTravelTimeService).estimateTravelTimeMinutes(from, to);
    }

    @Test
    void shouldUseRouteApiForDepartureLegWhenAvailable() {
        GeoSearchService geoSearchService = mock(GeoSearchService.class);
        LocalTravelTimeServiceImpl localTravelTimeService = mock(LocalTravelTimeServiceImpl.class);
        GeoEnhancedTravelTimeServiceImpl service = new GeoEnhancedTravelTimeServiceImpl(geoSearchService, localTravelTimeService);

        Poi from = new Poi();
        from.setId(-1L);
        from.setCityName("成都");
        from.setLatitude(BigDecimal.valueOf(30.65901D));
        from.setLongitude(BigDecimal.valueOf(104.19765D));

        Poi to = new Poi();
        to.setId(12L);
        to.setCityName("成都");
        to.setLatitude(BigDecimal.valueOf(30.658D));
        to.setLongitude(BigDecimal.valueOf(104.078D));

        when(geoSearchService.estimateTravel(any(), any(), anyString(), any()))
                .thenReturn(Optional.of(new GeoRouteEstimate(26, BigDecimal.valueOf(15.2D), "地铁+步行")));

        int result = service.estimateTravelTimeMinutes(from, to);

        assertThat(result).isEqualTo(26);
        verify(localTravelTimeService, never()).estimateTravelTimeMinutes(any(), any());
    }
}
