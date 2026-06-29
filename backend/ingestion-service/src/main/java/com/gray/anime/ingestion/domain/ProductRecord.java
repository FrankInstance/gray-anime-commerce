package com.gray.anime.ingestion.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("product")
public class ProductRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String productType;
    private String description;
    private String coverUrl;
    private String status;
    private Boolean limitedFlag;
    private LocalDateTime saleStartAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getLimitedFlag() { return limitedFlag; }
    public void setLimitedFlag(Boolean limitedFlag) { this.limitedFlag = limitedFlag; }
    public LocalDateTime getSaleStartAt() { return saleStartAt; }
    public void setSaleStartAt(LocalDateTime saleStartAt) { this.saleStartAt = saleStartAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
