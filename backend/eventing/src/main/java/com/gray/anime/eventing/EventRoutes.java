package com.gray.anime.eventing;

public final class EventRoutes {
    public static final String EXCHANGE = "gray.events";
    public static final String DEAD_EXCHANGE = "gray.events.dead";

    public static final String PAYMENT_CONFIRMED = "payment.confirmed";
    public static final String ORDER_PAID_PRODUCT = "order.paid.product";
    public static final String ORDER_PAID_VIP = "order.paid.vip";
    public static final String ORDER_PAID_POINTS = "order.paid.points";
    public static final String ORDER_CANCELLED_PRODUCT = "order.cancelled.product";
    public static final String FULFILLMENT_COMPLETED = "order.fulfillment.completed";
    public static final String FULFILLMENT_FAILED = "order.fulfillment.failed";

    public static final String ORDER_PAYMENT_QUEUE = "gray.order.payment";
    public static final String ORDER_FULFILLMENT_QUEUE = "gray.order.fulfillment";
    public static final String INVENTORY_FULFILLMENT_QUEUE = "gray.inventory.fulfillment";
    public static final String USER_FULFILLMENT_QUEUE = "gray.user.fulfillment";

    private EventRoutes() {
    }
}
