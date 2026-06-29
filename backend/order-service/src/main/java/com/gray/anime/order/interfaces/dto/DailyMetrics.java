package com.gray.anime.order.interfaces.dto;

public record DailyMetrics(long ordersToday, long paidOrdersToday, int revenueCentsToday, int vipRevenueCentsToday, long visitorsToday) {
}
