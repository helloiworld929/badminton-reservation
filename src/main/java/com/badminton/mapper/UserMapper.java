package com.badminton.mapper;

import com.badminton.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    User selectById(@Param("id") Long id);

    List<User> selectAll();

    int insert(User user);

    int updateById(User user);

    User findByPhone(@Param("phone") String phone);

    User findByUsername(@Param("username") String username);

    List<User> findUsers(@Param("keyword") String keyword, @Param("status") String status);
}
