package com.gray.anime.order.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gray.anime.common.api.PageResult;
import com.gray.anime.common.api.TraceIds;
import com.gray.anime.common.exception.BizException;
import com.gray.anime.common.security.CurrentUser;
import com.gray.anime.order.domain.*;
import com.gray.anime.order.infrastructure.client.InventoryClient;
import com.gray.anime.order.infrastructure.mapper.*;
import com.gray.anime.order.interfaces.dto.*;
import feign.FeignException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OrderApplicationService {
    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final PaymentMapper paymentMapper;
    private final SkuSnapshotMapper skuMapper;
    private final ProductSnapshotMapper productMapper;
    private final ChapterSnapshotMapper chapterMapper;
    private final AppUserSnapshotMapper userMapper;
    private final ChapterEntitlementRecordMapper entitlementMapper;
    private final PointsLedgerMapper pointsLedgerMapper;
    private final InventoryClient inventoryClient;
    private final OrderLifecycleService lifecycleService;

    public OrderApplicationService(OrderMapper orderMapper, OrderItemMapper itemMapper, PaymentMapper paymentMapper,
                                   SkuSnapshotMapper skuMapper, ProductSnapshotMapper productMapper, ChapterSnapshotMapper chapterMapper, AppUserSnapshotMapper userMapper,
                                   ChapterEntitlementRecordMapper entitlementMapper, PointsLedgerMapper pointsLedgerMapper, InventoryClient inventoryClient,
                                   OrderLifecycleService lifecycleService) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.paymentMapper = paymentMapper;
        this.skuMapper = skuMapper;
        this.productMapper = productMapper;
        this.chapterMapper = chapterMapper;
        this.userMapper = userMapper;
        this.entitlementMapper = entitlementMapper;
        this.pointsLedgerMapper = pointsLedgerMapper;
        this.inventoryClient = inventoryClient;
        this.lifecycleService = lifecycleService;
    }

    @Transactional
    public OrderView createProductOrder(CurrentUser user, CreateOrderRequest request) {
        requireLogin(user);
        String orderNo = "OD" + System.currentTimeMillis() + randomSuffix();
        LocalDateTime now = LocalDateTime.now();
        OrderRecord order = new OrderRecord();
        order.setOrderNo(orderNo);
        order.setUserId(user.id());
        order.setOrderType("PRODUCT");
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setFulfillmentStatus(FulfillmentStatus.PENDING.name());
        order.setTotalCents(0);
        order.setTotalPoints(0);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);

        int total = 0;
        for (OrderLineRequest line : request.items()) {
            SkuSnapshot sku = skuMapper.selectById(line.skuId());
            if (sku == null || !"ACTIVE".equals(sku.getStatus())) {
                throw new BizException("SKU_NOT_FOUND", "SKU not found");
            }
            int unitPrice = user.isVip() ? Math.round(sku.getPriceCents() * 0.9f) : sku.getPriceCents();
            ReservationView reservation = reserveStock(user.id(), sku.getId(), line.quantity(), orderNo + ":" + sku.getId());
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setItemType("SKU");
            item.setSkuId(sku.getId());
            item.setRefId(sku.getProductId());
            item.setTitle(productSkuTitle(sku));
            item.setQuantity(line.quantity());
            item.setUnitPriceCents(unitPrice);
            item.setUnitPoints(0);
            item.setReservationNo(reservation.reservationNo());
            itemMapper.insert(item);
            total += unitPrice * line.quantity();
        }
        order.setTotalCents(total);
        order.setPaymentNo(createPayment(orderNo, user.id(), total));
        orderMapper.updateById(order);
        return orderView(order);
    }

    @Transactional
    public OrderView createVipOrder(CurrentUser user) {
        requireLogin(user);
        String orderNo = "VIP" + System.currentTimeMillis() + randomSuffix();
        LocalDateTime now = LocalDateTime.now();
        OrderRecord order = new OrderRecord();
        order.setOrderNo(orderNo);
        order.setUserId(user.id());
        order.setOrderType("VIP");
        order.setTotalCents(3000);
        order.setTotalPoints(0);
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setFulfillmentStatus(FulfillmentStatus.PENDING.name());
        order.setPaymentNo(createPayment(orderNo, user.id(), 3000));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);
        return orderView(order);
    }

    @Transactional
    public OrderView createPointsOrder(CurrentUser user, PointsOrderRequest request) {
        requireLogin(user);
        int amountCents = request.amountCents() == null ? 0 : request.amountCents();
        if (amountCents != 1000 && amountCents != 5000 && amountCents != 10000) {
            throw new BizException("INVALID_POINTS_PACKAGE", "Unsupported points package");
        }
        int points = amountCents / 10;
        String orderNo = "PT" + System.currentTimeMillis() + randomSuffix();
        LocalDateTime now = LocalDateTime.now();
        OrderRecord order = new OrderRecord();
        order.setOrderNo(orderNo);
        order.setUserId(user.id());
        order.setOrderType("POINTS");
        order.setTotalCents(amountCents);
        order.setTotalPoints(points);
        order.setStatus(OrderStatus.PENDING_PAYMENT.name());
        order.setFulfillmentStatus(FulfillmentStatus.PENDING.name());
        order.setPaymentNo(createPayment(orderNo, user.id(), amountCents));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setItemType("POINTS");
        item.setTitle(points + "积分");
        item.setQuantity(1);
        item.setUnitPriceCents(amountCents);
        item.setUnitPoints(points);
        itemMapper.insert(item);

        return orderView(order);
    }

    @Transactional
    public OrderView purchaseChapter(CurrentUser user, Long chapterId) {
        requireLogin(user);
        ChapterSnapshot chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BizException("CHAPTER_NOT_FOUND", "Chapter not found");
        }
        if (hasEntitlement(user.id(), chapterId)) {
            return paidChapterOrder(user.id(), chapter, 0, "ALREADY_OWNED");
        }
        if (Boolean.TRUE.equals(chapter.getFreeFlag()) || user.isVip()) {
            grantChapter(user.id(), chapterId, user.isVip() ? "VIP" : "FREE");
            return paidChapterOrder(user.id(), chapter, 0, user.isVip() ? "VIP" : "FREE");
        }
        AppUserSnapshot appUser = userMapper.selectById(user.id());
        int price = chapter.getPricePoints() == null ? 0 : chapter.getPricePoints();
        if (appUser == null || appUser.getPoints() < price) {
            throw new BizException("POINTS_NOT_ENOUGH", "Not enough points");
        }
        int updated = userMapper.update(null, new LambdaUpdateWrapper<AppUserSnapshot>()
                .eq(AppUserSnapshot::getId, user.id())
                .ge(AppUserSnapshot::getPoints, price)
                .setSql("points = points - " + price)
                .set(AppUserSnapshot::getUpdatedAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BizException("POINTS_NOT_ENOUGH", "Not enough points");
        }
        if (price > 0) {
            writePointsLedger(user.id(), -price, "CHAPTER_PURCHASE", "CHAPTER:" + user.id() + ":" + chapterId);
        }
        grantChapter(user.id(), chapterId, "POINTS");
        return paidChapterOrder(user.id(), chapter, price, "POINTS");
    }

    public PageResult<OrderView> myOrders(CurrentUser user, long page, long size, String status) {
        requireLogin(user);
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<OrderRecord>()
                .eq(OrderRecord::getUserId, user.id())
                .orderByDesc(OrderRecord::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(OrderRecord::getStatus, status.trim());
        }
        Page<OrderRecord> result = orderMapper.selectPage(Page.of(page, size), wrapper);
        return new PageResult<>(result.getRecords().stream().map(this::orderView).toList(), page, size, result.getTotal());
    }

    public OrderView myOrder(CurrentUser user, String orderNo) {
        requireLogin(user);
        OrderRecord order = orderMapper.selectOne(new LambdaQueryWrapper<OrderRecord>()
                .eq(OrderRecord::getOrderNo, orderNo)
                .eq(OrderRecord::getUserId, user.id())
                .last("limit 1"));
        if (order == null) {
            throw new BizException("ORDER_NOT_FOUND", "Order not found");
        }
        return orderView(order);
    }

    @Transactional
    public OrderView cancelOrder(CurrentUser user, String orderNo) {
        requireLogin(user);
        OrderRecord order = orderMapper.selectOne(new LambdaQueryWrapper<OrderRecord>()
                .eq(OrderRecord::getOrderNo, orderNo)
                .eq(OrderRecord::getUserId, user.id())
                .last("limit 1"));
        if (order == null) {
            throw new BizException("ORDER_NOT_FOUND", "Order not found");
        }
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            throw new BizException("ORDER_CANNOT_CANCEL", "订单无法取消");
        }
        if (!lifecycleService.cancelPendingOrder(order, "USER_CANCELLED")) {
            throw new BizException("ORDER_CANNOT_CANCEL", "订单无法取消");
        }
        return myOrder(user, orderNo);
    }

    public PageResult<OrderView> adminOrders(long page, long size, String status) {
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<OrderRecord>().orderByDesc(OrderRecord::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(OrderRecord::getStatus, status.trim());
        }
        Page<OrderRecord> result = orderMapper.selectPage(Page.of(page, size), wrapper);
        return new PageResult<>(result.getRecords().stream().map(this::orderView).toList(), page, size, result.getTotal());
    }

    public DailyMetrics dailyMetrics() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        List<OrderRecord> today = orderMapper.selectList(new LambdaQueryWrapper<OrderRecord>()
                .ge(OrderRecord::getCreatedAt, start)
                .lt(OrderRecord::getCreatedAt, end));
        long paid = today.stream().filter(o -> OrderStatus.PAID.name().equals(o.getStatus())).count();
        int revenue = today.stream().filter(o -> OrderStatus.PAID.name().equals(o.getStatus())).mapToInt(o -> o.getTotalCents() == null ? 0 : o.getTotalCents()).sum();
        int vipRevenue = today.stream().filter(o -> OrderStatus.PAID.name().equals(o.getStatus()) && "VIP".equals(o.getOrderType())).mapToInt(OrderRecord::getTotalCents).sum();
        long syntheticVisitors = Math.max(128, today.size() * 41L + 326);
        return new DailyMetrics(today.size(), paid, revenue, vipRevenue, syntheticVisitors);
    }

    private OrderView paidChapterOrder(Long userId, ChapterSnapshot chapter, int price, String source) {
        String orderNo = "CH" + System.currentTimeMillis() + randomSuffix();
        LocalDateTime now = LocalDateTime.now();
        OrderRecord order = new OrderRecord();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setOrderType("CHAPTER");
        order.setTotalCents(0);
        order.setTotalPoints(price);
        order.setStatus(OrderStatus.PAID.name());
        order.setFulfillmentStatus(FulfillmentStatus.NOT_REQUIRED.name());
        order.setPaymentNo(null);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);
        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setItemType("CHAPTER");
        item.setRefId(chapter.getId());
        item.setTitle(chapter.getTitle());
        item.setQuantity(1);
        item.setUnitPriceCents(0);
        item.setUnitPoints(price);
        itemMapper.insert(item);
        return orderView(order);
    }

    private String createPayment(String orderNo, Long userId, int amountCents) {
        String paymentNo = "PAY" + System.currentTimeMillis() + randomSuffix();
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo(paymentNo);
        payment.setOrderNo(orderNo);
        payment.setUserId(userId);
        payment.setAmountCents(amountCents);
        payment.setChannel("UNASSIGNED");
        payment.setStatus("CREATED");
        payment.setAttemptCount(0);
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        paymentMapper.insert(payment);
        paymentMapper.insertTransition(paymentNo, null, "CREATED", "ORDER_CREATED",
                "PAYMENT_CREATED:" + paymentNo, TraceIds.current());
        return paymentNo;
    }

    private ReservationView reserveStock(Long userId, Long skuId, int quantity, String bizKey) {
        try {
            return inventoryClient.reserve(new ReserveStockRequest(userId, skuId, quantity, bizKey)).data();
        } catch (FeignException exception) {
            String body = exception.contentUTF8();
            if (body != null && body.contains("PURCHASE_LIMIT_EXCEEDED")) {
                throw new BizException("PURCHASE_LIMIT_EXCEEDED", "限购商品，你已超出购买数量");
            }
            if (body != null && body.contains("STOCK_NOT_ENOUGH")) {
                throw new BizException("STOCK_NOT_ENOUGH", "库存不足");
            }
            throw exception;
        }
    }

    private void grantChapter(Long userId, Long chapterId, String source) {
        if (hasEntitlement(userId, chapterId)) {
            return;
        }
        ChapterEntitlementRecord entitlement = new ChapterEntitlementRecord();
        entitlement.setUserId(userId);
        entitlement.setChapterId(chapterId);
        entitlement.setSource(source);
        entitlement.setCreatedAt(LocalDateTime.now());
        entitlementMapper.insert(entitlement);
    }

    private boolean hasEntitlement(Long userId, Long chapterId) {
        return entitlementMapper.selectCount(new LambdaQueryWrapper<ChapterEntitlementRecord>()
                .eq(ChapterEntitlementRecord::getUserId, userId)
                .eq(ChapterEntitlementRecord::getChapterId, chapterId)) > 0;
    }

    private void writePointsLedger(Long userId, int amount, String reason, String bizKey) {
        PointsLedgerRecord ledger = new PointsLedgerRecord();
        ledger.setUserId(userId);
        ledger.setAmount(amount);
        ledger.setReason(reason);
        ledger.setBizKey(bizKey);
        ledger.setCreatedAt(LocalDateTime.now());
        pointsLedgerMapper.insert(ledger);
    }

    private OrderView orderView(OrderRecord order) {
        List<OrderItemView> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()))
                .stream()
                .map(i -> new OrderItemView(i.getItemType(), i.getRefId(), i.getSkuId(), displayTitle(i), i.getQuantity(), i.getUnitPriceCents(), i.getUnitPoints(), i.getReservationNo()))
                .toList();
        PaymentRecord payment = paymentView(order);
        return new OrderView(order.getId(), order.getOrderNo(), order.getOrderType(), order.getTotalCents(), order.getTotalPoints(),
                order.getStatus(), order.getFulfillmentStatus(), order.getCancelReason(), order.getPaymentNo(),
                payment == null ? null : payment.getStatus(),
                payment == null ? null : payment.getChannel(),
                order.getPaidAt(), order.getCancelledAt(),
                order.getCreatedAt(), items);
    }

    private PaymentRecord paymentView(OrderRecord order) {
        if (order.getPaymentNo() == null || order.getPaymentNo().isBlank()) {
            return null;
        }
        return paymentMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getPaymentNo, order.getPaymentNo())
                .last("limit 1"));
    }

    private String displayTitle(OrderItem item) {
        if (!"SKU".equals(item.getItemType()) || item.getSkuId() == null) {
            return item.getTitle();
        }
        SkuSnapshot sku = skuMapper.selectById(item.getSkuId());
        if (sku == null) {
            return item.getTitle();
        }
        String title = productSkuTitle(sku);
        if (title == null || title.isBlank()) {
            return item.getTitle();
        }
        return title;
    }

    private String productSkuTitle(SkuSnapshot sku) {
        ProductSnapshot product = sku.getProductId() == null ? null : productMapper.selectById(sku.getProductId());
        String productTitle = product == null ? null : product.getTitle();
        String skuName = sku.getSkuName();
        if (productTitle == null || productTitle.isBlank()) {
            return skuName;
        }
        if (skuName == null || skuName.isBlank() || productTitle.contains(skuName)) {
            return productTitle;
        }
        return productTitle + " · " + skuName;
    }

    private void requireLogin(CurrentUser user) {
        if (user.id() <= 0) {
            throw new BizException("UNAUTHORIZED", "Login required");
        }
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
