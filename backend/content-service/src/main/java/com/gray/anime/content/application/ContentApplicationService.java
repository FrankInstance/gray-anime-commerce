package com.gray.anime.content.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gray.anime.common.api.PageResult;
import com.gray.anime.common.exception.BizException;
import com.gray.anime.common.security.CurrentUser;
import com.gray.anime.content.domain.Chapter;
import com.gray.anime.content.domain.ChapterContent;
import com.gray.anime.content.domain.ChapterEntitlement;
import com.gray.anime.content.domain.Work;
import com.gray.anime.content.infrastructure.mapper.ChapterContentMapper;
import com.gray.anime.content.infrastructure.mapper.ChapterEntitlementMapper;
import com.gray.anime.content.infrastructure.mapper.ChapterMapper;
import com.gray.anime.content.infrastructure.mapper.WorkMapper;
import com.gray.anime.content.interfaces.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class ContentApplicationService {
    private final WorkMapper workMapper;
    private final ChapterMapper chapterMapper;
    private final ChapterContentMapper contentMapper;
    private final ChapterEntitlementMapper entitlementMapper;

    public ContentApplicationService(WorkMapper workMapper, ChapterMapper chapterMapper, ChapterContentMapper contentMapper, ChapterEntitlementMapper entitlementMapper) {
        this.workMapper = workMapper;
        this.chapterMapper = chapterMapper;
        this.contentMapper = contentMapper;
        this.entitlementMapper = entitlementMapper;
    }

    public PageResult<WorkCard> listWorks(long page, long size, String type, String category, String keyword) {
        LambdaQueryWrapper<Work> wrapper = new LambdaQueryWrapper<Work>()
                .eq(Work::getStatus, "PUBLISHED")
                .orderByDesc(Work::getPopularity);
        if (type != null && !type.isBlank()) {
            wrapper.eq(Work::getWorkType, type.trim().toUpperCase());
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(Work::getCategory, category.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Work::getTitle, keyword.trim())
                    .or().like(Work::getAuthor, keyword.trim())
                    .or().like(Work::getCategory, keyword.trim()));
        }
        Page<Work> result = workMapper.selectPage(Page.of(page, size), wrapper);
        return new PageResult<>(result.getRecords().stream().map(this::card).toList(), page, size, result.getTotal());
    }

    public WorkDetail getWork(Long id) {
        Work work = requireWork(id);
        List<ChapterView> chapters = chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getWorkId, id)
                        .orderByAsc(Chapter::getChapterNo))
                .stream().map(this::chapterView).toList();
        return new WorkDetail(card(work), chapters);
    }

    public List<ChapterView> chapters(Long workId) {
        requireWork(workId);
        return chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getWorkId, workId)
                        .orderByAsc(Chapter::getChapterNo))
                .stream().map(this::chapterView).toList();
    }

    public ReaderResponse reader(Long chapterId, CurrentUser user) {
        Chapter chapter = requireChapter(chapterId);
        boolean unlocked = Boolean.TRUE.equals(chapter.getFreeFlag()) || user.isVip() || ownsChapter(user.id(), chapterId);
        if (!unlocked) {
            throw new BizException("CHAPTER_LOCKED", "Chapter requires purchase, VIP, or points redemption");
        }
        ChapterContent content = contentMapper.selectOne(new LambdaQueryWrapper<ChapterContent>().eq(ChapterContent::getChapterId, chapterId));
        String images = content == null ? "" : content.getContentImages();
        return new ReaderResponse(chapterId, chapter.getTitle(), true, content == null ? "" : content.getContentText(),
                images == null || images.isBlank() ? List.of() : Arrays.stream(images.split(",")).map(String::trim).toList());
    }

    @Transactional
    public WorkCard adminCreateWork(AdminWorkRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Work work = new Work();
        work.setTitle(request.title());
        work.setWorkType(request.workType().toUpperCase());
        work.setAuthor(request.author() == null ? "Unknown Circle" : request.author());
        work.setCategory(request.category() == null ? "Fantasy" : request.category());
        work.setDescription(request.description());
        work.setCoverUrl(request.coverUrl());
        work.setStatus("PUBLISHED");
        work.setPopularity(0);
        work.setCreatedAt(now);
        work.setUpdatedAt(now);
        workMapper.insert(work);
        return card(work);
    }

    public PageResult<WorkCard> adminWorks(long page, long size, String keyword) {
        LambdaQueryWrapper<Work> wrapper = new LambdaQueryWrapper<Work>().orderByDesc(Work::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Work::getTitle, keyword.trim());
        }
        Page<Work> result = workMapper.selectPage(Page.of(page, size), wrapper);
        return new PageResult<>(result.getRecords().stream().map(this::card).toList(), page, size, result.getTotal());
    }

    private Work requireWork(Long id) {
        Work work = workMapper.selectById(id);
        if (work == null) {
            throw new BizException("WORK_NOT_FOUND", "Work not found");
        }
        return work;
    }

    private Chapter requireChapter(Long id) {
        Chapter chapter = chapterMapper.selectById(id);
        if (chapter == null) {
            throw new BizException("CHAPTER_NOT_FOUND", "Chapter not found");
        }
        return chapter;
    }

    private boolean ownsChapter(Long userId, Long chapterId) {
        return userId != null && userId > 0 && entitlementMapper.selectCount(new LambdaQueryWrapper<ChapterEntitlement>()
                .eq(ChapterEntitlement::getUserId, userId)
                .eq(ChapterEntitlement::getChapterId, chapterId)) > 0;
    }

    private WorkCard card(Work work) {
        return new WorkCard(work.getId(), work.getTitle(), work.getWorkType(), work.getAuthor(), work.getCategory(), work.getDescription(), work.getCoverUrl(), work.getPopularity());
    }

    private ChapterView chapterView(Chapter chapter) {
        return new ChapterView(chapter.getId(), chapter.getChapterNo(), chapter.getTitle(), Boolean.TRUE.equals(chapter.getFreeFlag()), chapter.getPricePoints());
    }
}
