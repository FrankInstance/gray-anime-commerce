package com.gray.anime.order.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("chapter")
public class ChapterSnapshot {
    @TableId
    private Long id;
    private Long workId;
    private String title;
    private Boolean freeFlag;
    private Integer pricePoints;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Boolean getFreeFlag() { return freeFlag; }
    public void setFreeFlag(Boolean freeFlag) { this.freeFlag = freeFlag; }
    public Integer getPricePoints() { return pricePoints; }
    public void setPricePoints(Integer pricePoints) { this.pricePoints = pricePoints; }
}
