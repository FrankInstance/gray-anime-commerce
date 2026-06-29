package com.gray.anime.inventory.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gray.anime.common.exception.BizException;
import com.gray.anime.inventory.domain.Inventory;
import com.gray.anime.inventory.domain.StockReservation;
import com.gray.anime.inventory.infrastructure.mapper.InventoryMapper;
import com.gray.anime.inventory.infrastructure.mapper.StockReservationMapper;
import com.gray.anime.inventory.interfaces.dto.InventoryView;
import com.gray.anime.inventory.interfaces.dto.ReservationView;
import com.gray.anime.inventory.interfaces.dto.ReserveStockRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class InventoryApplicationService {
    private final InventoryMapper inventoryMapper;
    private final StockReservationMapper reservationMapper;
    private final StringRedisTemplate redisTemplate;

    public InventoryApplicationService(InventoryMapper inventoryMapper, StockReservationMapper reservationMapper, StringRedisTemplate redisTemplate) {
        this.inventoryMapper = inventoryMapper;
        this.reservationMapper = reservationMapper;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public ReservationView reserve(ReserveStockRequest request) {
        StockReservation existed = reservationMapper.selectOne(new LambdaQueryWrapper<StockReservation>()
                .eq(StockReservation::getBizKey, request.bizKey())
                .last("limit 1"));
        if (existed != null) {
            return view(existed);
        }
        Inventory inventory = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>().eq(Inventory::getSkuId, request.skuId()));
        if (inventory == null) {
            throw new BizException("INVENTORY_NOT_FOUND", "Inventory not found");
        }
        enforcePerUserLimit(request, inventory);
        int updated = inventoryMapper.update(null, new LambdaUpdateWrapper<Inventory>()
                .eq(Inventory::getId, inventory.getId())
                .ge(Inventory::getStockAvailable, request.quantity())
                .setSql("stock_available = stock_available - " + request.quantity())
                .setSql("stock_locked = stock_locked + " + request.quantity())
                .setSql("version = version + 1")
                .set(Inventory::getUpdatedAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BizException("STOCK_NOT_ENOUGH", "Stock is not enough");
        }
        StockReservation reservation = new StockReservation();
        reservation.setReservationNo("RSV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase());
        reservation.setUserId(request.userId());
        reservation.setSkuId(request.skuId());
        reservation.setQuantity(request.quantity());
        reservation.setBizKey(request.bizKey());
        reservation.setStatus("LOCKED");
        reservation.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        reservation.setCreatedAt(LocalDateTime.now());
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationMapper.insert(reservation);
        cacheStock(request.skuId());
        return view(reservation);
    }

    @Transactional
    public ReservationView release(String reservationNo) {
        StockReservation reservation = requireReservation(reservationNo);
        if (!"LOCKED".equals(reservation.getStatus())) {
            return view(reservation);
        }
        inventoryMapper.update(null, new LambdaUpdateWrapper<Inventory>()
                .eq(Inventory::getSkuId, reservation.getSkuId())
                .setSql("stock_available = stock_available + " + reservation.getQuantity())
                .setSql("stock_locked = greatest(stock_locked - " + reservation.getQuantity() + ", 0)")
                .setSql("version = version + 1")
                .set(Inventory::getUpdatedAt, LocalDateTime.now()));
        reservation.setStatus("RELEASED");
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationMapper.updateById(reservation);
        cacheStock(reservation.getSkuId());
        return view(reservation);
    }

    @Transactional
    public ReservationView confirm(String reservationNo) {
        StockReservation reservation = requireReservation(reservationNo);
        if ("LOCKED".equals(reservation.getStatus())) {
            inventoryMapper.update(null, new LambdaUpdateWrapper<Inventory>()
                    .eq(Inventory::getSkuId, reservation.getSkuId())
                    .setSql("stock_locked = greatest(stock_locked - " + reservation.getQuantity() + ", 0)")
                    .setSql("version = version + 1")
                    .set(Inventory::getUpdatedAt, LocalDateTime.now()));
            reservation.setStatus("CONFIRMED");
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationMapper.updateById(reservation);
            cacheStock(reservation.getSkuId());
        }
        return view(reservation);
    }

    public InventoryView getBySku(Long skuId) {
        Inventory inventory = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>().eq(Inventory::getSkuId, skuId));
        if (inventory == null) {
            throw new BizException("INVENTORY_NOT_FOUND", "Inventory not found");
        }
        return new InventoryView(skuId, inventory.getStockAvailable(), inventory.getStockLocked(), inventory.getLimitPerUser());
    }

    private void enforcePerUserLimit(ReserveStockRequest request, Inventory inventory) {
        int limit = inventory.getLimitPerUser() == null ? 0 : inventory.getLimitPerUser();
        if (limit <= 0) {
            return;
        }
        long active = reservationMapper.selectCount(new LambdaQueryWrapper<StockReservation>()
                .eq(StockReservation::getUserId, request.userId())
                .eq(StockReservation::getSkuId, request.skuId())
                .in(StockReservation::getStatus, "LOCKED", "CONFIRMED"));
        if (active + request.quantity() > limit) {
            throw new BizException("PURCHASE_LIMIT_EXCEEDED", "This limited item allows only " + limit + " per user");
        }
    }

    private StockReservation requireReservation(String reservationNo) {
        StockReservation reservation = reservationMapper.selectOne(new LambdaQueryWrapper<StockReservation>().eq(StockReservation::getReservationNo, reservationNo));
        if (reservation == null) {
            throw new BizException("RESERVATION_NOT_FOUND", "Reservation not found");
        }
        return reservation;
    }

    private ReservationView view(StockReservation reservation) {
        return new ReservationView(reservation.getReservationNo(), reservation.getSkuId(), reservation.getQuantity(), reservation.getStatus(), reservation.getExpiresAt());
    }

    private void cacheStock(Long skuId) {
        Inventory inventory = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>().eq(Inventory::getSkuId, skuId));
        if (inventory != null) {
            redisTemplate.opsForValue().set("inventory:sku:" + skuId, inventory.getStockAvailable().toString());
        }
    }
}
