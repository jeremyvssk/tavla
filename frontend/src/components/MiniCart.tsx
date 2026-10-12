// Quick cart on a desktop: a dropdown from the cart icon after Add to cart or a click, with View cart and Checkout.
import { useEffect, useRef } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useCart } from '../hooks/useCart';
import { formatPrice } from '../lib/format';
import { FREE_SHIPPING_FROM } from '../lib/shipping';
import { useAppDispatch, useAppSelector } from '../store';
import { closed } from '../store/cartSlice';
import Icon from './Icon';
import Photo from './Photo';

// Closed, it stays mounted but hidden (visibility), so it can fade out and is out of the tab order.
// Three lines keep the dropdown short enough to sit inside the window under the bar.
const SHOWN = 3;

export default function MiniCart({ button }: { button: React.RefObject<HTMLButtonElement> }) {
  const { open, added } = useAppSelector((s) => s.cart);
  const dispatch = useAppDispatch();
  const { cart } = useCart();
  const panel = useRef<HTMLElement>(null);
  const location = useLocation();

  // Escape or a click anywhere else closes it; Escape hands focus back to the cart button.
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      dispatch(closed());
      button.current?.focus();
    };
    const onDown = (e: PointerEvent) => {
      const t = e.target as Node;
      if (!panel.current?.contains(t) && !button.current?.contains(t)) dispatch(closed());
    };
    addEventListener('keydown', onKey);
    addEventListener('pointerdown', onDown);
    return () => {
      removeEventListener('keydown', onKey);
      removeEventListener('pointerdown', onDown);
    };
  }, [open, dispatch, button]);

  // the line just added goes first, so it is the one in view
  const lines = [...cart.lines].sort((a, b) => Number(b.productId === added) - Number(a.productId === added));
  const rest = cart.lines.length - SHOWN;
  const toFree = FREE_SHIPPING_FROM - cart.subtotal;
  const count = `${cart.itemCount} ${cart.itemCount === 1 ? 'item' : 'items'}`;

  return (
    <section className="mc" id="mc" ref={panel} aria-label="Your cart" data-on={open ? '' : undefined}>
      <div className="mc-h">
        {added ? <><Icon name="check" width={2.4} /><span role="status">Added to your cart</span></> : <span>Your cart</span>}
        {cart.itemCount > 0 && <small>{count}</small>}
      </div>
      {cart.lines.length === 0 ? (
        <div className="mc-f">
          <p className="note2">Your cart is empty.</p>
          <Link className="btn btn--ghost" to="/catalog">Browse products</Link>
        </div>
      ) : (
        <>
          <ul>
            {lines.slice(0, SHOWN).map((l) => (
              <li key={l.productId}>
                <span className="th">{l.imageUrl && <Photo src={l.imageUrl} framing={l.imageFraming} pad={0.06} />}</span>
                <div>
                  {l.productId === added && <span className="just">Just added</span>}
                  <p className="nm">{l.name}</p>
                  <span className="meta">{l.options ? `${l.options} · ` : ''}Qty {l.quantity}</span>
                </div>
                <b className="pz">{formatPrice(l.lineTotal)}</b>
              </li>
            ))}
          </ul>
          {rest > 0 && <p className="more-items">and {rest} more {rest === 1 ? 'line' : 'lines'} in your cart</p>}
          <div className="mc-f">
            <p className="sumr"><span>Subtotal</span><b>{formatPrice(cart.subtotal)}</b></p>
            <p className="note2">
              {toFree <= 0 ? 'Shipping is free on this order.' : `Add ${formatPrice(toFree)} more for free shipping.`} Prices include VAT.
            </p>
            <div className="two">
              <Link className="btn btn--ghost" to="/bag">View cart</Link>
              <Link className="btn" to="/checkout" state={{ from: location.pathname + location.search }}>Checkout</Link>
            </div>
          </div>
        </>
      )}
    </section>
  );
}
