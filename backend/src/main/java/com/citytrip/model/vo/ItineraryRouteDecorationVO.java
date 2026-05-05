package com.citytrip.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class ItineraryRouteDecorationVO {
    private String routeWarmTip;
    private List<RouteNodeDecorationVO> nodes;
}
