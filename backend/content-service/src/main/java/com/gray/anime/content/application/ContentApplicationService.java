package com.gray.anime.content.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gray.anime.common.api.PageResult;
import com.gray.anime.common.exception.BizException;
import com.gray.anime.common.security.CurrentUser;
import com.gray.anime.content.domain.Chapter;
import com.gray.anime.content.domain.ChapterContent;
import com.gray.anime.content.domain.ChapterEntitlement;
import com.gray.anime.content.domain.ReadingProgress;
import com.gray.anime.content.domain.UserBookshelf;
import com.gray.anime.content.domain.Work;
import com.gray.anime.content.infrastructure.mapper.ChapterContentMapper;
import com.gray.anime.content.infrastructure.mapper.ChapterEntitlementMapper;
import com.gray.anime.content.infrastructure.mapper.ChapterMapper;
import com.gray.anime.content.infrastructure.mapper.ReadingProgressMapper;
import com.gray.anime.content.infrastructure.mapper.UserBookshelfMapper;
import com.gray.anime.content.infrastructure.mapper.WorkMapper;
import com.gray.anime.content.interfaces.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
public class ContentApplicationService {
    private final WorkMapper workMapper;
    private final ChapterMapper chapterMapper;
    private final ChapterContentMapper contentMapper;
    private final ChapterEntitlementMapper entitlementMapper;
    private final UserBookshelfMapper bookshelfMapper;
    private final ReadingProgressMapper progressMapper;

    public ContentApplicationService(WorkMapper workMapper, ChapterMapper chapterMapper, ChapterContentMapper contentMapper,
                                     ChapterEntitlementMapper entitlementMapper, UserBookshelfMapper bookshelfMapper,
                                     ReadingProgressMapper progressMapper) {
        this.workMapper = workMapper;
        this.chapterMapper = chapterMapper;
        this.contentMapper = contentMapper;
        this.entitlementMapper = entitlementMapper;
        this.bookshelfMapper = bookshelfMapper;
        this.progressMapper = progressMapper;
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

    public WorkDetail getWork(Long id, CurrentUser user) {
        Work work = requireWork(id);
        List<ChapterView> chapters = chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getWorkId, id)
                        .orderByAsc(Chapter::getChapterNo))
                .stream().map(chapter -> chapterView(chapter, user)).toList();
        return new WorkDetail(card(work), chapters);
    }

    public List<ChapterView> chapters(Long workId, CurrentUser user) {
        requireWork(workId);
        return chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getWorkId, workId)
                        .orderByAsc(Chapter::getChapterNo))
                .stream().map(chapter -> chapterView(chapter, user)).toList();
    }

    public ReaderResponse reader(Long chapterId, CurrentUser user) {
        Chapter chapter = requireChapter(chapterId);
        boolean unlocked = Boolean.TRUE.equals(chapter.getFreeFlag()) || user.isVip() || ownsChapter(user.id(), chapterId);
        if (!unlocked) {
            throw new BizException("CHAPTER_LOCKED", "Chapter requires purchase, VIP, or points redemption");
        }
        ChapterContent content = contentMapper.selectOne(new LambdaQueryWrapper<ChapterContent>().eq(ChapterContent::getChapterId, chapterId));
        String images = content == null ? "" : content.getContentImages();
        recordProgress(user, chapter);
        return new ReaderResponse(chapterId, chapter.getTitle(), true, content == null ? "" : content.getContentText(),
                images == null || images.isBlank() ? List.of() : Arrays.stream(images.split(",")).map(String::trim).toList());
    }

    public PageResult<BookshelfItemView> bookshelf(CurrentUser user, long page, long size) {
        requireLogin(user);
        Page<UserBookshelf> result = bookshelfMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<UserBookshelf>()
                .eq(UserBookshelf::getUserId, user.id())
                .orderByDesc(UserBookshelf::getUpdatedAt)
                .orderByDesc(UserBookshelf::getId));
        List<BookshelfItemView> items = result.getRecords().stream()
                .map(this::bookshelfView)
                .filter(Objects::nonNull)
                .toList();
        return new PageResult<>(items, page, size, result.getTotal());
    }

    @Transactional
    public BookshelfItemView addToBookshelf(CurrentUser user, Long workId) {
        requireLogin(user);
        requireWork(workId);
        LocalDateTime now = LocalDateTime.now();
        UserBookshelf item = bookshelfMapper.selectOne(new LambdaQueryWrapper<UserBookshelf>()
                .eq(UserBookshelf::getUserId, user.id())
                .eq(UserBookshelf::getWorkId, workId)
                .last("limit 1"));
        if (item == null) {
            item = new UserBookshelf();
            item.setUserId(user.id());
            item.setWorkId(workId);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            bookshelfMapper.insert(item);
        } else {
            item.setUpdatedAt(now);
            bookshelfMapper.updateById(item);
        }
        return bookshelfView(item);
    }

    @Transactional
    public void removeFromBookshelf(CurrentUser user, Long workId) {
        requireLogin(user);
        bookshelfMapper.delete(new LambdaQueryWrapper<UserBookshelf>()
                .eq(UserBookshelf::getUserId, user.id())
                .eq(UserBookshelf::getWorkId, workId));
    }

    public PageResult<ReadingProgressView> readingProgress(CurrentUser user, long page, long size) {
        requireLogin(user);
        Page<ReadingProgress> result = progressMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<ReadingProgress>()
                .eq(ReadingProgress::getUserId, user.id())
                .orderByDesc(ReadingProgress::getUpdatedAt)
                .orderByDesc(ReadingProgress::getId));
        List<ReadingProgressView> items = result.getRecords().stream()
                .map(this::progressView)
                .filter(Objects::nonNull)
                .toList();
        return new PageResult<>(items, page, size, result.getTotal());
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

    private void requireLogin(CurrentUser user) {
        if (user == null || user.id() == null || user.id() <= 0) {
            throw new BizException("UNAUTHORIZED", "Login required");
        }
    }

    private WorkCard card(Work work) {
        return new WorkCard(work.getId(), work.getTitle(), work.getWorkType(), work.getAuthor(), work.getCategory(), work.getDescription(), work.getCoverUrl(), work.getPopularity());
    }

    private ChapterView chapterView(Chapter chapter, CurrentUser user) {
        boolean free = Boolean.TRUE.equals(chapter.getFreeFlag());
        boolean owned = ownsChapter(user == null ? null : user.id(), chapter.getId());
        boolean vipReadable = !free && !owned && user != null && user.isVip();
        boolean unlocked = free || owned || vipReadable;
        String accessLabel = free ? "免费" : owned ? "已解锁" : vipReadable ? "VIP可读" : chapter.getPricePoints() + "积分";
        return new ChapterView(chapter.getId(), chapter.getChapterNo(), chapter.getTitle(), free, chapter.getPricePoints(), unlocked, accessLabel);
    }

    private void recordProgress(CurrentUser user, Chapter chapter) {
        if (user == null || user.id() == null || user.id() <= 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        ReadingProgress progress = progressMapper.selectOne(new LambdaQueryWrapper<ReadingProgress>()
                .eq(ReadingProgress::getUserId, user.id())
                .eq(ReadingProgress::getWorkId, chapter.getWorkId())
                .last("limit 1"));
        if (progress == null) {
            progress = new ReadingProgress();
            progress.setUserId(user.id());
            progress.setWorkId(chapter.getWorkId());
            progress.setChapterId(chapter.getId());
            progress.setChapterNo(chapter.getChapterNo());
            progress.setChapterTitle(chapter.getTitle());
            progress.setUpdatedAt(now);
            progressMapper.insert(progress);
        } else {
            progress.setChapterId(chapter.getId());
            progress.setChapterNo(chapter.getChapterNo());
            progress.setChapterTitle(chapter.getTitle());
            progress.setUpdatedAt(now);
            progressMapper.updateById(progress);
        }

        bookshelfMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserBookshelf>()
                .eq(UserBookshelf::getUserId, user.id())
                .eq(UserBookshelf::getWorkId, chapter.getWorkId())
                .set(UserBookshelf::getUpdatedAt, now));
    }

    private BookshelfItemView bookshelfView(UserBookshelf item) {
        Work work = workMapper.selectById(item.getWorkId());
        if (work == null) {
            return null;
        }
        ReadingProgress progress = progressMapper.selectOne(new LambdaQueryWrapper<ReadingProgress>()
                .eq(ReadingProgress::getUserId, item.getUserId())
                .eq(ReadingProgress::getWorkId, item.getWorkId())
                .last("limit 1"));
        return new BookshelfItemView(
                work.getId(),
                work.getTitle(),
                work.getWorkType(),
                work.getAuthor(),
                work.getCategory(),
                work.getDescription(),
                work.getCoverUrl(),
                work.getPopularity(),
                progress == null ? null : progress.getChapterId(),
                progress == null ? null : progress.getChapterNo(),
                progress == null ? null : progress.getChapterTitle(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private ReadingProgressView progressView(ReadingProgress progress) {
        Work work = workMapper.selectById(progress.getWorkId());
        if (work == null) {
            return null;
        }
        return new ReadingProgressView(
                work.getId(),
                work.getTitle(),
                work.getWorkType(),
                work.getAuthor(),
                work.getCategory(),
                work.getDescription(),
                work.getCoverUrl(),
                progress.getChapterId(),
                progress.getChapterNo(),
                progress.getChapterTitle(),
                progress.getUpdatedAt()
        );
    }
}
