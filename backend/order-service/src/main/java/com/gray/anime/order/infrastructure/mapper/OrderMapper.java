package com.gray.anime.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gray.anime.order.domain.OrderRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface OrderMapper extends BaseMapper<OrderRecord> {
    @Select("SELECT * FROM orders WHERE order_no = #{orderNo} AND user_id = #{userId} LIMIT 1 FOR UPDATE")
    OrderRecord lockOwned(@Param("orderNo") String orderNo, @Param("userId") Long userId);
}
