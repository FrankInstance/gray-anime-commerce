package com.gray.anime.content.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gray.anime.content.domain.ReadingProgress;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface ReadingProgressMapper extends BaseMapper<ReadingProgress> {
    @Insert("""
            INSERT INTO reading_progress
                (user_id, work_id, chapter_id, chapter_no, chapter_title, progress_percent, updated_at)
            VALUES
                (#{progress.userId}, #{progress.workId}, #{progress.chapterId}, #{progress.chapterNo},
                 #{progress.chapterTitle}, #{progress.progressPercent}, #{progress.updatedAt})
            ON DUPLICATE KEY UPDATE
                progress_percent = CASE
                    WHEN #{preserveExistingProgress}
                        THEN IF(chapter_id = VALUES(chapter_id), progress_percent, 0)
                    ELSE VALUES(progress_percent)
                END,
                chapter_id = VALUES(chapter_id),
                chapter_no = VALUES(chapter_no),
                chapter_title = VALUES(chapter_title),
                updated_at = VALUES(updated_at)
            """)
    int upsert(@Param("progress") ReadingProgress progress,
               @Param("preserveExistingProgress") boolean preserveExistingProgress);
}
