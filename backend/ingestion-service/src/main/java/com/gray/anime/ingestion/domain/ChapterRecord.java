package com.gray.anime.ingestion.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("chapter")
public class ChapterRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workId;
    private Integer chapterNo;
    private String title;
    private Boolean freeFlag;
    private Integer pricePoints;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public Integer getChapterNo() { return chapterNo; }
    public void setChapterNo(Integer chapterNo) { this.chapterNo = chapterNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Boolean getFreeFlag() { return freeFlag; }
    public void setFreeFlag(Boolean freeFlag) { this.freeFlag = freeFlag; }
    public Integer getPricePoints() { return pricePoints; }
    public void setPricePoints(Integer pricePoints) { this.pricePoints = pricePoints; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
