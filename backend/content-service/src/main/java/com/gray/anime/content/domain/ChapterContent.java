package com.gray.anime.content.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("chapter_content")
public class ChapterContent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long chapterId;
    private String contentText;
    private String contentImages;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }
    public String getContentImages() { return contentImages; }
    public void setContentImages(String contentImages) { this.contentImages = contentImages; }
}
