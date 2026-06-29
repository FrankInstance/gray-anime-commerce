package com.gray.anime.ingestion.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("import_task")
public class ImportTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceType;
    private String sourceName;
    private String status;
    private Integer importedWorks;
    private Integer importedProducts;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getImportedWorks() { return importedWorks; }
    public void setImportedWorks(Integer importedWorks) { this.importedWorks = importedWorks; }
    public Integer getImportedProducts() { return importedProducts; }
    public void setImportedProducts(Integer importedProducts) { this.importedProducts = importedProducts; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
