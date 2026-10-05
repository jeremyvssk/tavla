// The cart: a panel from the right with the lines, quantity steppers, subtotal and the checkout button (checkout itself is Project 2).
import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { formatPrice } from '../lib/format';
import { useAppDispatch, useAppSelector } from '../store';
import { closed, itemCount, LINE_LIMIT, quantitySet, removed, subtotal } from '../store/cartSlice';
import Icon from './Icon';
import Photo from './Photo';

// The same threshold the delivery line promises.
const FREE_SHIPPING = 100;

export default function CartPanel() {
  const cart = useAppSelector((s) => s.cart);
  const dispatch = useAppDispatch();
  const closeBtn = useRef<HTMLButtonElement>(null);
  const count = itemCount(cart);
  const total = subtotal(cart);
  const toFree = Math.max(0, FREE_SHIPPING - total);

  // Focus moves into the panel on open and back to whatever opened it on close.
  useEffect(() => {
    if (!cart.open) return;
    const before = document.activeElement as HTMLElement | null;
    closeBtn.current?.focus({ preventScroll: true });
    document.documentElement.style.overflow = 'hidden';
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && dispatch(closed());
    addEventListener('keydown', onKey);
    return () => {
      removeEventListener('keydown', onKey);
      document.documentElement.style.overflow = '';
      before?.focus?.({ preventScroll: true });
    };
  }, [cart.open, dispatch]);

  return (
    <>
      <div className="t-scrim cart-scrim" data-on={cart.open ? '' : undefined} onClick={() => dispatch(closed())} />
      <aside className="cart" role="dialog" aria-modal="true" aria-labelledby="cart-h" data-on={cart.open ? '' : undefined}
        aria-hidden={!cart.open}>
        <div className="cart-head">
          <h2 id="cart-h">Your cart{count > 0 && <small>{count} {count === 1 ? 'item' : 'items'}</small>}</h2>
          <button type="button" ref={closeBtn} aria-label="Close cart" onClick={() => dispatch(closed())}>
            <Icon name="close" width={2} />
          </button>
        </div>

        {cart.lines.length === 0 ? (
          <div className="cart-empty">
            <span className="ico"><Icon name="bag" width={1.6} /></span>
            <p><b>Your cart is empty.</b><br />Sets, boards and clocks you add will wait here.</p>
            <Link className="btn" to="/catalog" onClick={() => dispatch(closed())}>Browse products</Link>
          </div>
        ) : (
          <>
            <div className="cart-ship" aria-live="polite">
              <p>{toFree > 0 ? <>Add <b>{formatPrice(toFree)}</b> more for free shipping</> : <>Your order ships <b>free</b></>}</p>
              <span className="bar"><i style={{ transform: `scaleX(${Math.min(1, total / FREE_SHIPPING)})` }} /></span>
            </div>
            <ul className="cart-lines">
              {cart.lines.map((l) => (
                <li key={l.productId}>
                  <Link className="im" to={`/catalog/${l.productId}`} tabIndex={-1} aria-hidden="true" onClick={() => dispatch(closed())}>
                    {l.imageUrl ? <Photo src={l.imageUrl} framing={l.imageFraming} pad={0.07} loading="lazy" /> : <Icon name="bag" width={1.4} />}
                  </Link>
                  <div className="tx">
                    <Link className="nm" to={`/catalog/${l.productId}`} onClick={() => dispatch(closed())}>{l.name}</Link>
                    <span className="meta">{[l.brandName ?? 'Tavla', l.options].filter(Boolean).join(' · ')}</span>
                    <div className="row">
                      <div className="qty" role="group" aria-label={`Quantity of ${l.name}`}>
                        <button type="button" aria-label="One fewer" disabled={l.quantity <= 1}
                          onClick={() => dispatch(quantitySet({ productId: l.productId, quantity: l.quantity - 1 }))}>
                          <Icon name="minus" width={2} />
                        </button>
                        <output aria-live="polite">{l.quantity}</output>
                        <button type="button" aria-label="One more" disabled={l.quantity >= Math.min(l.maxQuantity, LINE_LIMIT)}
                          onClick={() => dispatch(quantitySet({ productId: l.productId, quantity: l.quantity + 1 }))}>
                          <Icon name="plus" width={2} />
                        </button>
                      </div>
                      <b className="pz">{formatPrice(l.price * l.quantity)}</b>
                    </div>
                    <button type="button" className="rm" onClick={() => dispatch(removed(l.productId))}>Remove</button>
                  </div>
                </li>
              ))}
            </ul>
            <div className="cart-foot">
              <p className="sum"><span>Subtotal</span><b>{formatPrice(total)}</b></p>
              <p className="note">Shipping and taxes are worked out at checkout.</p>
              {/* the checkout flow and payments are Project 2; the button is in place for it and does nothing yet */}
              <button type="button" className="btn">Checkout<Icon name="arrow" width={2} /></button>
              <button type="button" className="btn btn--ghost" onClick={() => dispatch(closed())}>Continue shopping</button>
            </div>
          </>
        )}
      </aside>
    </>
  );
}
