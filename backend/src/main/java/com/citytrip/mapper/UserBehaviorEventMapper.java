package com.citytrip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.citytrip.model.entity.UserBehaviorEvent;
import com.citytrip.model.query.UserPoiPreferenceStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserBehaviorEventMapper extends BaseMapper<UserBehaviorEvent> {

    @Select("""
            <script>
            SELECT pref.user_id AS userId,
                   pref.poi_id AS poiId,
                   pref.preference_score AS preferenceScore
            FROM (
                SELECT aggregated.user_id,
                       aggregated.poi_id,
                       ROUND(SUM(aggregated.preference_score), 6) AS preference_score
                FROM (
                    SELECT e.user_id,
                           COALESCE(e.poi_id, rnf.poi_id) AS poi_id,
                           (
                               COALESCE(e.interaction_weight, 1.0)
                               * CASE e.event_type
                                   WHEN 'favorite_add' THEN 1.60
                                   WHEN 'option_select' THEN 1.35
                                   WHEN 'public_status_update' THEN 1.45
                                   WHEN 'community_like' THEN 1.30
                                   WHEN 'community_comment' THEN 1.20
                                   WHEN 'poi_replace' THEN 1.10
                                   WHEN 'replan_click' THEN 1.05
                                   ELSE 1.00
                                 END
                               * (1 / (1 + GREATEST(TIMESTAMPDIFF(DAY, e.event_time, NOW()), 0) / 30.0))
                           ) AS preference_score
                    FROM user_behavior_event e
                    LEFT JOIN route_node_fact rnf
                      ON e.poi_id IS NULL
                     AND e.itinerary_id IS NOT NULL
                     AND rnf.itinerary_id = e.itinerary_id
                     AND rnf.plan_fact_id = (
                         SELECT MAX(rnf2.plan_fact_id)
                         FROM route_node_fact rnf2
                         WHERE rnf2.itinerary_id = e.itinerary_id
                     )
                    WHERE e.user_id = #{userId}
                      AND e.success_flag = 1
                      AND e.user_id IS NOT NULL
                      AND e.event_time IS NOT NULL
                      AND TIMESTAMPDIFF(DAY, e.event_time, NOW()) &lt;= #{recentDays}
                ) aggregated
                WHERE aggregated.poi_id IS NOT NULL
                GROUP BY aggregated.user_id, aggregated.poi_id
            ) pref
            ORDER BY pref.preference_score DESC, pref.poi_id ASC
            </script>
            """)
    List<UserPoiPreferenceStat> selectUserPoiPreferences(@Param("userId") Long userId,
                                                         @Param("recentDays") int recentDays);

    @Select("""
            <script>
            SELECT pref.user_id
            FROM (
                SELECT aggregated.user_id,
                       aggregated.poi_id,
                       ROUND(SUM(aggregated.preference_score), 6) AS preference_score
                FROM (
                    SELECT e.user_id,
                           COALESCE(e.poi_id, rnf.poi_id) AS poi_id,
                           (
                               COALESCE(e.interaction_weight, 1.0)
                               * CASE e.event_type
                                   WHEN 'favorite_add' THEN 1.60
                                   WHEN 'option_select' THEN 1.35
                                   WHEN 'public_status_update' THEN 1.45
                                   WHEN 'community_like' THEN 1.30
                                   WHEN 'community_comment' THEN 1.20
                                   WHEN 'poi_replace' THEN 1.10
                                   WHEN 'replan_click' THEN 1.05
                                   ELSE 1.00
                                 END
                               * (1 / (1 + GREATEST(TIMESTAMPDIFF(DAY, e.event_time, NOW()), 0) / 30.0))
                           ) AS preference_score
                    FROM user_behavior_event e
                    LEFT JOIN route_node_fact rnf
                      ON e.poi_id IS NULL
                     AND e.itinerary_id IS NOT NULL
                     AND rnf.itinerary_id = e.itinerary_id
                     AND rnf.plan_fact_id = (
                         SELECT MAX(rnf2.plan_fact_id)
                         FROM route_node_fact rnf2
                         WHERE rnf2.itinerary_id = e.itinerary_id
                     )
                    WHERE e.success_flag = 1
                      AND e.user_id IS NOT NULL
                      AND e.event_time IS NOT NULL
                      AND TIMESTAMPDIFF(DAY, e.event_time, NOW()) &lt;= #{recentDays}
                ) aggregated
                WHERE aggregated.poi_id IS NOT NULL
                GROUP BY aggregated.user_id, aggregated.poi_id
            ) pref
            WHERE pref.user_id != #{excludeUserId}
              AND pref.poi_id IN
              <foreach collection="poiIds" item="poiId" open="(" separator="," close=")">
                  #{poiId}
              </foreach>
            GROUP BY pref.user_id
            ORDER BY SUM(pref.preference_score) DESC, pref.user_id ASC
            LIMIT #{limit}
            </script>
            """)
    List<Long> selectSimilarUserIdsByPoiIds(@Param("poiIds") List<Long> poiIds,
                                            @Param("excludeUserId") Long excludeUserId,
                                            @Param("recentDays") int recentDays,
                                            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT pref.user_id AS userId,
                   pref.poi_id AS poiId,
                   pref.preference_score AS preferenceScore
            FROM (
                SELECT aggregated.user_id,
                       aggregated.poi_id,
                       ROUND(SUM(aggregated.preference_score), 6) AS preference_score
                FROM (
                    SELECT e.user_id,
                           COALESCE(e.poi_id, rnf.poi_id) AS poi_id,
                           (
                               COALESCE(e.interaction_weight, 1.0)
                               * CASE e.event_type
                                   WHEN 'favorite_add' THEN 1.60
                                   WHEN 'option_select' THEN 1.35
                                   WHEN 'public_status_update' THEN 1.45
                                   WHEN 'community_like' THEN 1.30
                                   WHEN 'community_comment' THEN 1.20
                                   WHEN 'poi_replace' THEN 1.10
                                   WHEN 'replan_click' THEN 1.05
                                   ELSE 1.00
                                 END
                               * (1 / (1 + GREATEST(TIMESTAMPDIFF(DAY, e.event_time, NOW()), 0) / 30.0))
                           ) AS preference_score
                    FROM user_behavior_event e
                    LEFT JOIN route_node_fact rnf
                      ON e.poi_id IS NULL
                     AND e.itinerary_id IS NOT NULL
                     AND rnf.itinerary_id = e.itinerary_id
                     AND rnf.plan_fact_id = (
                         SELECT MAX(rnf2.plan_fact_id)
                         FROM route_node_fact rnf2
                         WHERE rnf2.itinerary_id = e.itinerary_id
                     )
                    WHERE e.success_flag = 1
                      AND e.user_id IS NOT NULL
                      AND e.event_time IS NOT NULL
                      AND TIMESTAMPDIFF(DAY, e.event_time, NOW()) &lt;= #{recentDays}
                ) aggregated
                WHERE aggregated.poi_id IS NOT NULL
                GROUP BY aggregated.user_id, aggregated.poi_id
            ) pref
            WHERE pref.user_id IN
              <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                  #{userId}
              </foreach>
              AND pref.poi_id IN
              <foreach collection="poiIds" item="poiId" open="(" separator="," close=")">
                  #{poiId}
              </foreach>
            ORDER BY pref.user_id ASC, pref.preference_score DESC, pref.poi_id ASC
            </script>
            """)
    List<UserPoiPreferenceStat> selectUserPoiPreferencesByUserIdsAndPoiIds(@Param("userIds") List<Long> userIds,
                                                                           @Param("poiIds") List<Long> poiIds,
                                                                           @Param("recentDays") int recentDays);
}

