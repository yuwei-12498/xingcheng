package com.citytrip.controller;

import com.citytrip.model.vo.PoiSearchResultVO;
import com.citytrip.service.PoiService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PoiControllerTest {

    @Test
    void searchEndpointShouldReturnLivePoiShape() throws Exception {
        PoiService poiService = mock(PoiService.class);
        when(poiService.searchLive(anyString(), anyString(), anyInt())).thenReturn(List.of(
                new PoiSearchResultVO(
                        "成都远洋太古里",
                        "锦江区中纱帽街8号",
                        "商场",
                        new BigDecimal("30.655"),
                        new BigDecimal("104.083"),
                        "成都市",
                        "510100",
                        "vivo-geo"
                )
        ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PoiController(poiService)).build();

        mockMvc.perform(get("/api/pois/search").param("keyword", "太古里").param("city", "成都"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("成都远洋太古里"))
                .andExpect(jsonPath("$[0].source").value("vivo-geo"));
    }
}
