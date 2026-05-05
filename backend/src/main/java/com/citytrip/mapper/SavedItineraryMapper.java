package com.citytrip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.citytrip.model.entity.SavedItinerary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SavedItineraryMapper extends BaseMapper<SavedItinerary> {
    @Select("""
            select *
            from saved_itinerary
            where id = #{itineraryId} and user_id = #{userId}
            for update
            """)
    SavedItinerary selectOwnedForUpdate(@Param("userId") Long userId, @Param("itineraryId") Long itineraryId);

    @Select("""
            select *
            from saved_itinerary
            where id = #{itineraryId}
            for update
            """)
    SavedItinerary selectByIdForUpdate(@Param("itineraryId") Long itineraryId);
}