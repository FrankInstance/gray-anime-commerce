package com.gray.anime.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gray.anime.order.domain.PaymentRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

public interface PaymentMapper extends BaseMapper<PaymentRecord> {
    @Select("SELECT * FROM payment WHERE payment_no = #{paymentNo} LIMIT 1 FOR UPDATE")
    PaymentRecord lockByPaymentNo(@Param("paymentNo") String paymentNo);

    @Insert("""
            INSERT IGNORE INTO payment_transition
                (payment_no, from_status, to_status, trigger_type, idempotency_key, trace_id, created_at)
            VALUES (#{paymentNo}, #{fromStatus}, #{toStatus}, #{triggerType}, #{idempotencyKey}, #{traceId}, NOW())
            """)
    int insertTransition(@Param("paymentNo") String paymentNo,
                         @Param("fromStatus") String fromStatus,
                         @Param("toStatus") String toStatus,
                         @Param("triggerType") String triggerType,
                         @Param("idempotencyKey") String idempotencyKey,
                         @Param("traceId") String traceId);
}
