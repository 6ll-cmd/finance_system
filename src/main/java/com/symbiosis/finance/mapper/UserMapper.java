package com.symbiosis.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.symbiosis.finance.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
