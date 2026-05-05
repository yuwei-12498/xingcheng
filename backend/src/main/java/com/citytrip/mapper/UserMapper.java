package com.citytrip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.citytrip.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("""
            select id, username, email, password_hash, password_salt, nickname, role, status, create_time, update_time
            from trip_user
            where username = #{username}
            limit 1
            """)
    @Results(id = "userResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "username", column = "username"),
            @Result(property = "email", column = "email"),
            @Result(property = "passwordHash", column = "password_hash"),
            @Result(property = "passwordSalt", column = "password_salt"),
            @Result(property = "nickname", column = "nickname"),
            @Result(property = "role", column = "role"),
            @Result(property = "status", column = "status"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time")
    })
    User selectByUsername(String username);

    @Select("""
            select id, username, email, password_hash, password_salt, nickname, role, status, create_time, update_time
            from trip_user
            where id = #{userId}
            limit 1
            """)
    @ResultMap("userResultMap")
    User selectAdminUserById(@Param("userId") Long userId);

    @Select("""
            select id, username, email, password_hash, password_salt, nickname, role, status, create_time, update_time
            from trip_user
            where email = #{email}
            limit 1
            """)
    @ResultMap("userResultMap")
    User selectByEmail(String email);

    @Update("""
            update trip_user
            set status = #{status},
                update_time = now()
            where id = #{userId}
            """)
    int updateAdminUserStatus(@Param("userId") Long userId, @Param("status") Integer status);

    List<User> selectAdminUserPageRecords(@Param("offset") long offset,
                                          @Param("size") long size,
                                          @Param("username") String username);

    long countAdminUsers(@Param("username") String username);
}
