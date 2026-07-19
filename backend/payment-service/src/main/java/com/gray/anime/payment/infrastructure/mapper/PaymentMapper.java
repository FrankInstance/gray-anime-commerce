package com.gray.anime.payment.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gray.anime.payment.domain.PaymentRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PaymentMapper extends BaseMapper<PaymentRecord> {
    @Select("SELECT * FROM payment WHERE payment_no = #{paymentNo} AND user_id = #{userId} LIMIT 1 FOR UPDATE")
    PaymentRecord lockOwned(@Param("paymentNo") String paymentNo, @Param("userId") Long userId);
}
