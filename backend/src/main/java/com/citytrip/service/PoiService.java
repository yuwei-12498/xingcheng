package com.citytrip.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.PoiSearchResultVO;

import java.time.LocalDate;
import java.util.List;

public interface PoiService extends IService<Poi> {
    Poi getDetailWithStatus(Long id, LocalDate tripDate);

    List<Poi> enrichOperatingStatus(List<Poi> pois, LocalDate tripDate);

    List<PoiSearchResultVO> searchLive(String keyword, String city, int limit);
}
