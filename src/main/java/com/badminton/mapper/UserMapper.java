package com.badminton.mapper;

import com.badminton.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    User selectById(@Param("id") Long id);

    User selectByIdForUpdate(@Param("id") Long id);

    List<User> selectAll();

    int insert(User user);

    int updateById(User user);

    int incrementNoshowCountAndRestrict(@Param("id") Long id,
                                        @Param("threshold") int threshold);

    User findByPhone(@Param("phone") String phone);

    List<User> findUsers(@Param("keyword") String keyword, @Param("status") String status);
}
