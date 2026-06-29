package com.gray.anime.payment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("app_user")
public class AppUserRecord {
    @TableId
    private Long id;
    private LocalDateTime vipUntil;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getVipUntil() { return vipUntil; }
    public void setVipUntil(LocalDateTime vipUntil) { this.vipUntil = vipUntil; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
