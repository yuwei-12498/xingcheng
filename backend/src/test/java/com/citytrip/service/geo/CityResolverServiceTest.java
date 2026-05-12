package com.citytrip.service.geo;

import com.citytrip.config.GeoSearchProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CityResolverServiceTest {

    @Test
    void shouldGuessChineseCityNameInsteadOfReturningCityCode() {
        CityResolverService service = new CityResolverService(new GeoSearchProperties());

        assertThat(service.guessCityNameFromText("杭州一天预算200元，想逛西湖")).isEqualTo("杭州");
    }

    @Test
    void shouldGuessEnglishCityAliasAsChineseCityName() {
        CityResolverService service = new CityResolverService(new GeoSearchProperties());

        assertThat(service.guessCityNameFromText("Hangzhou one day budget 200 rmb")).isEqualTo("杭州");
    }
}
