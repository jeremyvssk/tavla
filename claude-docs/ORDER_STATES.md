# Order states

An order is a state machine: one status at a time, and only the moves in the table below. The code
that enforces it is `backend/src/main/java/com/iloveshopping/order/OrderStatus.java` (the table) and
`Order.moveTo` (the only method that changes a status). A move the table doesn't list, or one made
by the wrong actor, is refused with `409 order_status_conflict`. Every move writes a row to
`order_status_history`: from, to, who, a note, and when.

## Statuses

| Status | Means | Stock |
|---|---|---|
| `PENDING_PAYMENT` | Placed, waiting for Stripe's answer | Reserved, until `hold_until` |
| `PAYMENT_FAILED` | The last card was declined; another card can still pay | Still reserved, until `hold_until` |
| `PAYMENT_SUCCESSFUL` | Paid. Not packed yet, so it can still be cancelled | Taken |
| `PROCESSING` | Being packed. Too late to cancel | Taken |
| `SHIPPED` | With the courier | Taken |
| `DELIVERED` | Arrived. Final | Taken |
| `CANCELLED` | Released by the timer, or cancelled after paying | Back on sale |
| `REFUNDED` | Cancelled, and Stripe confirmed the refund. Final | Back on sale |

## Moves

| From | To | Who | What else happens |
|---|---|---|---|
| (new) | `PENDING_PAYMENT` | Shopper | Stock reserved, cart lines copied into the order and removed from the cart, Stripe PaymentIntent created |
| `PENDING_PAYMENT` | `PAYMENT_SUCCESSFUL` | Stripe | Hold cleared; card brand and last 4 saved; confirmation email |
| `PENDING_PAYMENT` | `PAYMENT_FAILED` | Stripe | Decline code saved; stock stays held; failure email with the hold time |
| `PENDING_PAYMENT` | `CANCELLED` | Timer | PaymentIntent cancelled at Stripe, stock back, lines back in the cart; "not paid in time" email |
| `PAYMENT_FAILED` | `PENDING_PAYMENT` | Shopper | "Try another card": same PaymentIntent, so never two payments |
| `PAYMENT_FAILED` | `PAYMENT_SUCCESSFUL` | Stripe | A late success for an earlier attempt still counts: the money moved |
| `PAYMENT_FAILED` | `CANCELLED` | Timer | As above |
| `PAYMENT_SUCCESSFUL` | `PROCESSING` | Admin | Packing starts (admin tools are Project 3) |
| `PAYMENT_SUCCESSFUL` | `CANCELLED` | Shopper, Admin | Stock back on sale (not back in the cart); payment `REFUND_DUE`; cancellation email; then the Stripe refund is asked for |
| `PROCESSING` | `SHIPPED` | Admin | |
| `SHIPPED` | `DELIVERED` | Admin | |
| `CANCELLED` | `REFUNDED` | Stripe | Stripe's answer to the refund request, or its `charge.refunded` webhook; refund email |

`DELIVERED` and `REFUNDED` have no moves out.

## The rules behind the table

**Stock is taken when the order is placed, not when it is paid.** By the time Stripe says "paid", the
money has already moved. If stock were checked only then, two people could both pay for the last
item and one of them would need a refund. Taking it at placement means the second buyer hears
"sold out" before giving any card details. The price of that choice is that unpaid orders hold
stock, so every order has a hold (`ORDER_PAYMENT_HOLD`, 30 minutes) and `OrderExpiry` releases it.

**One statement takes the stock.** `UPDATE products SET stock_quantity = stock_quantity - n WHERE id = ?
AND active AND stock_quantity >= n`. Zero rows changed means sold out, and the whole order rolls back.
Lines are taken in product-id order so two orders can't deadlock. `CHECK (stock_quantity >= 0)` is the
safety net under it.

**Only Stripe says "paid".** The browser reaching the success page proves nothing: the tab can close
first, and anyone can type that URL. The webhook is signed by Stripe, so the server can check it came
from Stripe. The order page only *asks* the server for the status and waits.

**Each Stripe event is applied once.** Its id goes into `processed_payment_events` in the same
transaction as the order change. A second delivery finds it and does nothing.

**A late failure doesn't overwrite a new attempt.** If a decline arrives after the shopper already
started paying again, its timestamp is older than the order's last status change, and it's ignored.

**Stripe's webhook goes through a queue.** The webhook checks the signature, publishes a
`PaymentEvent` (ids and the outcome only, no card or address) to RabbitMQ and waits for the broker's
confirm before answering Stripe 200; if RabbitMQ is down it answers 503 and Stripe sends the event
again. `PaymentEventListener` applies it. A message that throws is retried (3 tries in all), then moved
to `orders.payment-events.dlq`. Emails go through a second queue, `orders.emails`, published only after
the order's transaction commits, so a rolled-back change never sends an email.

**Cancelling is a status move with a refund behind it.** Allowed only from `PAYMENT_SUCCESSFUL`; anything
later (packing has started) or earlier (not paid) answers `409 order_not_cancellable`, decided on the
server under the order's row lock. A second click finds it `CANCELLED` and changes nothing. The refund
is asked for after the cancellation commits, with `refund-{orderId}` as Stripe's idempotency key, so a
retry can't refund twice. The payment row says where the money is: `REFUND_DUE` (Stripe hasn't accepted
the refund yet; `RefundRetry` asks again every minute), `REFUNDING`, `REFUNDED`. The order shows
`CANCELLED` until Stripe confirms, then `REFUNDED`. A refund can take days, so the order never claims
the money is back before Stripe says so.

**When the hold runs out, Stripe is asked first.** "No webhook yet" isn't the same as "not paid".
`OrderExpiry` checks the PaymentIntent: paid means mark it paid (the webhook got lost), still at the
bank means wait a minute, open means cancel it at Stripe and only then release the stock.

**If Stripe can't be reached when the order is placed,** the order is withdrawn in a second
transaction: stock back, lines back in the cart, and a `503 payment_unavailable`. Nothing was charged,
because only the browser can confirm a payment and it never got the secret.

## Still to build

- Project 3: the admin moves (`PROCESSING`, `SHIPPED`, `DELIVERED`) and an admin cancel. The table
  already allows them; no endpoint makes them yet.
- A refund Stripe declines after accepting it (`refund.failed`) isn't handled: rare with cards, and it
  would show in Stripe's dashboard.
