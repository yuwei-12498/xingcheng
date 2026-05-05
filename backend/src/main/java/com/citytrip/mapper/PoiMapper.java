package com.citytrip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.citytrip.model.entity.Poi;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PoiMapper extends BaseMapper<Poi> {

    @Select("""
            select id, city_code, city_name, name, category, district, address, latitude, longitude, open_time, close_time,
                   closed_weekdays, temporarily_closed, status_note, status_source, status_updated_at,
                   avg_cost, stay_duration, indoor, night_available, rain_friendly, walking_level,
                   tags, suitable_for, description, priority_score, crowd_penalty
            from poi
            where id = #{poiId}
            limit 1
            """)
    Poi selectAdminPoiById(@Param("poiId") Long poiId);

    @Update("""
            update poi
            set temporarily_closed = #{temporarilyClosed},
                status_note = #{statusNote},
                status_source = 'admin',
                status_updated_at = now()
            where id = #{poiId}
            """)
    int updatePoiTemporaryStatus(@Param("poiId") Long poiId,
                                 @Param("temporarilyClosed") Integer temporarilyClosed,
                                 @Param("statusNote") String statusNote);

    @Delete("""
            delete from poi
            where id = #{poiId}
            """)
    int deleteAdminPoi(@Param("poiId") Long poiId);

    List<Poi> selectAdminPoiPageRecords(@Param("offset") long offset,
                                        @Param("size") long size,
                                        @Param("name") String name);

    long countAdminPois(@Param("name") String name);

    List<Poi> selectPlanningCandidates(@Param("rainy") boolean rainy,
                                       @Param("walkingLevel") String walkingLevel,
                                       @Param("cityCode") String cityCode,
                                       @Param("cityName") String cityName,
                                       @Param("limit") int limit);

    default List<Poi> selectPlanningCandidates(boolean rainy,
                                               String walkingLevel,
                                               int limit) {
        return selectPlanningCandidates(rainy, walkingLevel, null, null, limit);
    }

    List<Poi> searchByNameInCity(@Param("keyword") String keyword,
                                 @Param("cityCode") String cityCode,
                                 @Param("cityName") String cityName,
                                 @Param("limit") int limit);

    int insertAdminPoi(Poi poi);

    int updateAdminPoi(Poi poi);
}
