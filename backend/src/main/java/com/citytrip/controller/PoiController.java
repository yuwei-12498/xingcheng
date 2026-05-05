package com.citytrip.controller;

import com.citytrip.common.NotFoundException;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.PoiSearchResultVO;
import com.citytrip.service.PoiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/pois")
public class PoiController {

    private final PoiService poiService;

    public PoiController(PoiService poiService) {
        this.poiService = poiService;
    }

    @GetMapping
    public List<Poi> list(@RequestParam(value = "tripDate", required = false) String tripDate) {
        List<Poi> pois = poiService.list();
        poiService.enrichOperatingStatus(pois, parseTripDate(tripDate));
        return pois;
    }

    @GetMapping("/{id}")
    public Poi detail(@PathVariable("id") Long id,
                      @RequestParam(value = "tripDate", required = false) String tripDate) {
        Poi poi = poiService.getDetailWithStatus(id, parseTripDate(tripDate));
        if (poi == null) {
            throw new NotFoundException("未找到对应景点");
        }
        return poi;
    }

    @GetMapping("/search")
    public List<PoiSearchResultVO> search(@RequestParam("keyword") String keyword,
                                          @RequestParam(value = "city", required = false) String city,
                                          @RequestParam(value = "limit", required = false, defaultValue = "8") int limit) {
        return poiService.searchLive(keyword, city, limit);
    }

    private LocalDate parseTripDate(String tripDate) {
        if (tripDate == null || tripDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(tripDate);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
