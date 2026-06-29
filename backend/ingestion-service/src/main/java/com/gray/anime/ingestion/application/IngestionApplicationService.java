package com.gray.anime.ingestion.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gray.anime.common.api.PageResult;
import com.gray.anime.ingestion.domain.*;
import com.gray.anime.ingestion.infrastructure.mapper.*;
import com.gray.anime.ingestion.interfaces.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class IngestionApplicationService {
    private final ImportTaskMapper taskMapper;
    private final WorkRecordMapper workMapper;
    private final ChapterRecordMapper chapterMapper;
    private final ChapterContentRecordMapper contentMapper;
    private final ProductRecordMapper productMapper;
    private final SkuRecordMapper skuMapper;
    private final InventoryRecordMapper inventoryMapper;

    public IngestionApplicationService(ImportTaskMapper taskMapper, WorkRecordMapper workMapper, ChapterRecordMapper chapterMapper,
                                       ChapterContentRecordMapper contentMapper, ProductRecordMapper productMapper, SkuRecordMapper skuMapper,
                                       InventoryRecordMapper inventoryMapper) {
        this.taskMapper = taskMapper;
        this.workMapper = workMapper;
        this.chapterMapper = chapterMapper;
        this.contentMapper = contentMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.inventoryMapper = inventoryMapper;
    }

    @Transactional
    public ImportTaskView importDemo() {
        BulkImportRequest demo = new BulkImportRequest(List.of(
                new WorkImport("星轨书店的魔女", "NOVEL", "Lumen Circle", "Fantasy", "在废弃轨道尽头经营书店的魔女，收集被遗忘的故事碎片。", cover("novel-a"),
                        List.of(new ChapterImport("第一章 夜班列车", true, 0, demoText("夜班列车")), new ChapterImport("第二章 星砂契约", false, 20, demoText("星砂契约")))),
                new WorkImport("雨港机械少女", "MANGA", "North Pier", "Sci-Fi", "机械少女在雨港寻找失踪设计师的漫画分镜演示。", cover("manga-a"),
                        List.of(new ChapterImport("第1话 蓝色雨衣", true, 0, "分镜页：蓝色雨衣、旧码头、启动核心。"), new ChapterImport("第2话 生锈心跳", false, 25, "分镜页：维修仓、错位记忆、追逐。"))),
                new WorkImport("月面社团观察日志", "NOVEL", "Orbit Notes", "Campus", "月面转学生和地球社团的轻喜剧观察日志。", cover("novel-b"),
                        List.of(new ChapterImport("记录 01 新部员", true, 0, demoText("新部员"))))
        ), List.of(
                new ProductImport("《星轨书店的魔女》实体书限定版", "BOOK", "首刷附星砂书签，VIP 9折。", cover("book-a"), false, null, "限定版", 6800, 120, 0),
                new ProductImport("雨港机械少女 亚克力立牌", "GOODS", "限时开售，每人限购 1 件。", cover("stand-a"), true, LocalDateTime.now().plusHours(2), "标准款", 3900, 30, 1)
        ));
        return importBulk("DEMO", "local-demo-crawler", demo);
    }

    @Transactional
    public ImportTaskView importBulk(String sourceType, String sourceName, BulkImportRequest request) {
        ImportTask task = newTask(sourceType, sourceName);
        int works = 0;
        int products = 0;
        try {
            for (WorkImport work : safeList(request.works())) {
                importWork(work);
                works++;
            }
            for (ProductImport product : safeList(request.products())) {
                importProduct(product);
                products++;
            }
            task.setStatus("SUCCESS");
            task.setImportedWorks(works);
            task.setImportedProducts(products);
            task.setFinishedAt(LocalDateTime.now());
        } catch (RuntimeException exception) {
            task.setStatus("FAILED");
            task.setErrorMessage(exception.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            throw exception;
        } finally {
            taskMapper.updateById(task);
        }
        return view(task);
    }

    public PageResult<ImportTaskView> tasks(long page, long size) {
        Page<ImportTask> result = taskMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<ImportTask>().orderByDesc(ImportTask::getId));
        return new PageResult<>(result.getRecords().stream().map(this::view).toList(), page, size, result.getTotal());
    }

    private ImportTask newTask(String sourceType, String sourceName) {
        ImportTask task = new ImportTask();
        task.setSourceType(sourceType);
        task.setSourceName(sourceName);
        task.setStatus("RUNNING");
        task.setImportedWorks(0);
        task.setImportedProducts(0);
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return task;
    }

    private void importWork(WorkImport input) {
        LocalDateTime now = LocalDateTime.now();
        WorkRecord work = new WorkRecord();
        work.setTitle(input.title());
        work.setWorkType(defaultText(input.workType(), "NOVEL").toUpperCase());
        work.setAuthor(defaultText(input.author(), "Demo Circle"));
        work.setCategory(defaultText(input.category(), "Fantasy"));
        work.setDescription(input.description());
        work.setCoverUrl(input.coverUrl());
        work.setStatus("PUBLISHED");
        work.setPopularity(700 + (int) (Math.random() * 300));
        work.setCreatedAt(now);
        work.setUpdatedAt(now);
        workMapper.insert(work);
        int no = 1;
        for (ChapterImport chapterInput : safeList(input.chapters())) {
            ChapterRecord chapter = new ChapterRecord();
            chapter.setWorkId(work.getId());
            chapter.setChapterNo(no++);
            chapter.setTitle(chapterInput.title());
            chapter.setFreeFlag(chapterInput.free());
            chapter.setPricePoints(chapterInput.pricePoints());
            chapter.setPublishedAt(now);
            chapter.setCreatedAt(now);
            chapterMapper.insert(chapter);
            ChapterContentRecord content = new ChapterContentRecord();
            content.setChapterId(chapter.getId());
            content.setContentText(chapterInput.contentText());
            content.setContentImages("");
            contentMapper.insert(content);
        }
    }

    private void importProduct(ProductImport input) {
        LocalDateTime now = LocalDateTime.now();
        ProductRecord product = new ProductRecord();
        product.setTitle(input.title());
        product.setProductType(defaultText(input.productType(), "BOOK").toUpperCase());
        product.setDescription(input.description());
        product.setCoverUrl(input.coverUrl());
        product.setLimitedFlag(input.limited());
        product.setSaleStartAt(input.saleStartAt());
        product.setStatus("ON_SALE");
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        productMapper.insert(product);

        SkuRecord sku = new SkuRecord();
        sku.setProductId(product.getId());
        sku.setSkuName(defaultText(input.skuName(), "Standard"));
        sku.setPriceCents(input.priceCents());
        sku.setStatus("ACTIVE");
        skuMapper.insert(sku);

        InventoryRecord inventory = new InventoryRecord();
        inventory.setSkuId(sku.getId());
        inventory.setStockAvailable(input.stock());
        inventory.setStockLocked(0);
        inventory.setLimitPerUser(input.limitPerUser());
        inventory.setVersion(0);
        inventory.setUpdatedAt(now);
        inventoryMapper.insert(inventory);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private ImportTaskView view(ImportTask task) {
        return new ImportTaskView(task.getId(), task.getSourceType(), task.getSourceName(), task.getStatus(), task.getImportedWorks(), task.getImportedProducts(),
                task.getErrorMessage(), task.getCreatedAt(), task.getFinishedAt());
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String cover(String seed) {
        return "https://picsum.photos/seed/gray-" + seed + "/900/1200";
    }

    private String demoText(String title) {
        return "《" + title + "》\n\n这是一段用于简历项目演示的原创模拟文本。它展示章节试看、积分兑换、VIP 免费阅读和后台导入能力，不来自任何未经授权的站点。";
    }
}
