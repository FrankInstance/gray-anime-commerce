# Payment state machine

Cash orders keep their order state and payment state separate. The payment service owns payment transitions; the order service applies confirmed payments and tracks fulfillment through domain events.

## States

- Payment: `CREATED -> PENDING`; `PENDING -> CONFIRMED | FAILED | CANCELLED | EXPIRED`; `FAILED -> PENDING | CANCELLED | EXPIRED`.
- `CONFIRMED`, `CANCELLED`, and `EXPIRED` are terminal. Repeated confirmation returns the existing confirmed result without writing another event.
- Order: `PENDING_PAYMENT -> PAID | CANCELLED`.
- Fulfillment: `PENDING -> COMPLETED | FAILED`; chapter point redemption uses `NOT_REQUIRED` because it does not enter the cash-payment flow.

A failed provider attempt can be retried on the same order while the ten-minute order window remains open. There is no product-level retry-count limit in that window; `attempt_count` and `payment_transition` retain an audit trail. Gateway rate limits still protect the endpoint from rapid repeated requests.

## Event chain

1. Payment confirmation locks the order and payment in that order, changes only the payment to `CONFIRMED`, and writes `payment.confirmed` to the local Outbox in the same transaction.
2. The publisher conditionally claims events, sends persistent messages with Publisher Confirm and mandatory routing, and marks an event published only after Broker ACK. Failed sends use exponential backoff and become `DEAD` after ten publisher attempts. Stale publisher claims are recovered automatically.
3. The order consumer uses Inbox deduplication, changes the order to `PAID`, and emits the matching product, points, or VIP fulfillment event.
4. Inventory confirms reservations for product orders. The user service credits points or extends VIP. Each consumer commits its Inbox row and business update in one local transaction.
5. Fulfillment completion or final failure returns to the order service and updates `fulfillment_status`.

Cancellation and payment confirmation use the same lock order: order first, payment second. A confirmed payment cannot be cancelled. Timeout cancellation marks the payment `EXPIRED`; user cancellation marks it `CANCELLED`. Product cancellations release inventory through the same reliable event path.

## Operations

- Watch `gray_outbox_backlog`, `gray_outbox_dead`, `gray_outbox_publish_total`, and `gray_payment_transition_total` in Prometheus/Grafana.
- A dead Outbox event or a failed fulfillment requires investigation before manual replay. Preserve its `event_id`, routing key, trace ID, and aggregate ID during reconciliation.
- Apply `deploy/mysql/migrations/20260719-payment-state-machine.sql` only after a verified backup. The migration is idempotent and does not delete existing account or order data.
