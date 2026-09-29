// A sideways row of product cards: arrows, mouse drag with coasting, and a scrub bar under it.
import { ReactNode, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import type { ProductSummary } from '../api/catalog';
import Icon from './Icon';
import ProductCard from './ProductCard';

/** A CSS cubic-bezier as a function of time, solved by bisection. */
function bezier(x1: number, y1: number, x2: number, y2: number) {
  const f = (a: number, b: number) => (t: number) => 3 * a * t * (1 - t) ** 2 + 3 * b * t * t * (1 - t) + t ** 3;
  const X = f(x1, x2), Y = f(y1, y2);
  return (x: number) => {
    if (x <= 0) return 0;
    if (x >= 1) return 1;
    let lo = 0, hi = 1;
    for (let i = 0; i < 30; i++) {
      const m = (lo + hi) / 2;
      if (X(m) < x) lo = m;
      else hi = m;
    }
    return Y((lo + hi) / 2);
  };
}
// ease-out rather than in-out: a click gets movement on the very next frame, and a second click
// mid-glide carries on at speed instead of stalling at a slow start
const OUT = bezier(0.22, 1, 0.36, 1);
const STEP_MS = 480;
const STAGGER = 10;

interface Props {
  id: string;
  title: string;
  items: ProductSummary[];
  more: { label: ReactNode; to: string };
}

export default function ProductRow({ id, title, items, more }: Props) {
  const trackRef = useRef<HTMLDivElement>(null);
  const railRef = useRef<HTMLDivElement>(null);
  const prevRef = useRef<HTMLButtonElement>(null);
  const nextRef = useRef<HTMLButtonElement>(null);

  // The motion runs on the DOM directly: it is per-frame work that React state would only slow down.
  // Arrows step by whole cards on an ease-out curve, the cards trailing a few ms apart so the row moves
  // like a strip; clicks during a glide stack up from where it was heading. A mouse can grab and flick the row; it coasts and stops where it runs out, with no
  // snapping. Touch keeps the browser's own scrolling, whose inertia is better than anything here.
  useEffect(() => {
    const track = trackRef.current!, rail = railRef.current!, prev = prevRef.current!, next = nextRef.current!;
    const thumb = rail.querySelector('i')!;
    const cards = [...track.children] as HTMLElement[];
    const still = matchMedia('(prefers-reduced-motion:reduce)');
    let raf = 0;
    // where the running glide will stop, and each card's current trailing offset in px
    let target: number | null = null;
    const off = cards.map(() => 0);
    const max = () => track.scrollWidth - track.clientWidth;
    const pitch = () => (cards.length > 1 ? cards[1].offsetLeft - cards[0].offsetLeft : track.clientWidth);
    const clamp = (x: number) => Math.max(0, Math.min(max(), x));
    const thumbW = () => Math.max(48, (rail.clientWidth * track.clientWidth) / track.scrollWidth);
    const sync = () => {
      const m = max(), x = track.scrollLeft;
      prev.disabled = x < 4;
      next.disabled = x > m - 4;
      rail.hidden = m < 4;
      const tw = thumbW();
      rail.style.setProperty('--tw', `${tw}px`);
      rail.style.setProperty('--tx', `${m > 0 ? Math.min(1, x / m) * (rail.clientWidth - tw) : 0}px`);
    };
    const settle = () => { off.fill(0); cards.forEach((c) => { c.style.transform = ''; }); };
    const stop = () => { cancelAnimationFrame(raf); raf = 0; target = null; settle(); };

    // Each card runs the track's curve from its own start time, shifted by how far it trails. A glide
    // that interrupts another takes over the cards' trailing offsets and eases them out, so nothing jumps.
    const glide = (to: number, stagger: boolean) => {
      cancelAnimationFrame(raf);
      const from = track.scrollLeft, end = clamp(to), dist = end - from, carried = off.slice();
      if (still.matches || (Math.abs(dist) < 1 && carried.every((o) => Math.abs(o) < 0.5))) {
        stop();
        track.scrollLeft = end;
        return;
      }
      target = end;
      const d = Math.sign(dist), step = pitch(), seen = Math.max(1, Math.round(track.clientWidth / step));
      const delay = cards.map((c) => {
        const slot = Math.floor((c.offsetLeft - from) / step);
        return stagger ? Math.max(0, Math.min(3, d > 0 ? slot : seen - 1 - slot)) * STAGGER : 0;
      });
      const total = STEP_MS + Math.max(...delay), t0 = performance.now();
      const tick = (now: number) => {
        const t = now - t0;
        const at = (p: number) => from + dist * OUT(Math.max(0, Math.min(1, p)));
        const lead = at(t / STEP_MS), fade = 1 - OUT(Math.min(1, t / STEP_MS));
        track.scrollLeft = lead;
        cards.forEach((c, k) => {
          off[k] = (delay[k] ? lead - at((t - delay[k]) / STEP_MS) : 0) + carried[k] * fade;
          c.style.transform = Math.abs(off[k]) > 0.05 ? `translateX(${off[k].toFixed(1)}px)` : '';
        });
        if (t < total) raf = requestAnimationFrame(tick);
        else { raf = 0; target = null; settle(); }
      };
      raf = requestAnimationFrame(tick);
    };

    const onArrow = (dir: number) => () => {
      const step = pitch(), per = Math.max(1, Math.floor((track.clientWidth + 1) / step));
      glide((Math.round((target ?? track.scrollLeft) / step) + dir * per) * step, true);
    };
    const onPrev = onArrow(-1), onNext = onArrow(1);
    prev.addEventListener('click', onPrev);
    next.addEventListener('click', onNext);

    // Mouse drag: 1:1, resisting past either end; letting go with speed coasts on ~0.998/ms friction.
    let drag: { x0: number; s0: number; moved: boolean; v: number; lx: number; lt: number; over: number; id: number } | null = null;
    const onDown = (e: PointerEvent) => {
      if (e.pointerType !== 'mouse' || e.button !== 0) return;
      stop();
      drag = { x0: e.clientX, s0: track.scrollLeft, moved: false, v: 0, lx: e.clientX, lt: performance.now(), over: 0, id: e.pointerId };
    };
    const onMove = (e: PointerEvent) => {
      if (!drag || e.pointerId !== drag.id) return;
      const dx = e.clientX - drag.x0;
      if (!drag.moved) {
        if (Math.abs(dx) < 6) return;
        drag.moved = true;
        track.setPointerCapture(e.pointerId);
        track.classList.add('is-grabbing');
        getSelection()?.removeAllRanges();
      }
      // speed over the last few moves, capped: a flick is worth a page, never the whole row
      const now = performance.now(), dt = Math.max(8, now - drag.lt);
      drag.v = Math.max(-3, Math.min(3, 0.7 * ((drag.lx - e.clientX) / dt) + 0.3 * drag.v));
      drag.lx = e.clientX;
      drag.lt = now;
      const raw = drag.s0 - dx, x = clamp(raw);
      track.scrollLeft = x;
      drag.over = raw - x;
      const give = -Math.sign(drag.over) * Math.min(80, Math.abs(drag.over) * 0.35);
      cards.forEach((c) => { c.style.transform = give ? `translateX(${give.toFixed(1)}px)` : ''; });
    };
    const release = (e: PointerEvent) => {
      if (!drag || e.pointerId !== drag.id) return;
      const dr = drag;
      drag = null;
      if (!dr.moved) return;
      track.classList.remove('is-grabbing');
      // the pointer went up over a card after a drag: that is not a click on the card
      const eat = (ev: Event) => { ev.preventDefault(); ev.stopPropagation(); };
      track.addEventListener('click', eat, { capture: true, once: true });
      setTimeout(() => track.removeEventListener('click', eat, { capture: true }), 0);
      if (dr.over) {
        cards.forEach((c) => { c.style.transition = 'transform .5s cubic-bezier(.22,1,.3,1)'; c.style.transform = ''; });
        setTimeout(() => cards.forEach((c) => { c.style.transition = ''; }), 520);
        return;
      }
      if (performance.now() - dr.lt > 80 || Math.abs(dr.v) < 0.05 || still.matches) return;
      let v = dr.v, lt = performance.now();
      const coast = (now: number) => {
        const dt = now - lt;
        lt = now;
        v *= Math.pow(0.998, dt);
        const x = track.scrollLeft + v * dt;
        track.scrollLeft = clamp(x);
        if (Math.abs(v) > 0.02 && x > 0 && x < max()) raf = requestAnimationFrame(coast);
        else raf = 0;
      };
      raf = requestAnimationFrame(coast);
    };
    const interrupt = () => { if (raf) stop(); };
    const noDrag = (e: Event) => e.preventDefault();
    track.addEventListener('dragstart', noDrag);
    track.addEventListener('pointerdown', onDown);
    track.addEventListener('pointermove', onMove);
    track.addEventListener('pointerup', release);
    track.addEventListener('pointercancel', release);
    track.addEventListener('wheel', interrupt, { passive: true });
    track.addEventListener('touchstart', interrupt, { passive: true });

    // Scrubber: grab the thumb where it was pressed; pressing the rail beside it glides there first.
    let scrub: { grab: number; id: number } | null = null;
    const toScroll = (px: number, grab: number) =>
      ((px - rail.getBoundingClientRect().left - grab) / Math.max(1, rail.clientWidth - thumbW())) * max();
    const onRailDown = (e: PointerEvent) => {
      if (e.button !== 0) return;
      e.preventDefault();
      stop();
      const r = thumb.getBoundingClientRect(), on = e.clientX >= r.left && e.clientX <= r.right;
      const grab = on ? e.clientX - r.left : thumbW() / 2;
      scrub = { grab, id: e.pointerId };
      rail.setPointerCapture(e.pointerId);
      rail.classList.add('is-grabbing');
      if (!on) glide(toScroll(e.clientX, grab), false);
    };
    const onRailMove = (e: PointerEvent) => {
      if (!scrub || e.pointerId !== scrub.id || !e.buttons) return;
      if (raf) stop();
      track.scrollLeft = clamp(toScroll(e.clientX, scrub.grab));
    };
    const endScrub = (e: PointerEvent) => {
      if (scrub && e.pointerId === scrub.id) { scrub = null; rail.classList.remove('is-grabbing'); }
    };
    rail.addEventListener('pointerdown', onRailDown);
    rail.addEventListener('pointermove', onRailMove);
    rail.addEventListener('pointerup', endScrub);
    rail.addEventListener('pointercancel', endScrub);

    track.addEventListener('scroll', sync, { passive: true });
    addEventListener('resize', sync);
    sync();

    return () => {
      stop();
      prev.removeEventListener('click', onPrev);
      next.removeEventListener('click', onNext);
      track.removeEventListener('dragstart', noDrag);
      track.removeEventListener('pointerdown', onDown);
      track.removeEventListener('pointermove', onMove);
      track.removeEventListener('pointerup', release);
      track.removeEventListener('pointercancel', release);
      track.removeEventListener('wheel', interrupt);
      track.removeEventListener('touchstart', interrupt);
      rail.removeEventListener('pointerdown', onRailDown);
      rail.removeEventListener('pointermove', onRailMove);
      rail.removeEventListener('pointerup', endScrub);
      rail.removeEventListener('pointercancel', endScrub);
      track.removeEventListener('scroll', sync);
      removeEventListener('resize', sync);
    };
  }, [items]);

  return (
    <section className="t-sec" aria-labelledby={id}>
      <div className="t-head">
        <h2 id={id}>{title}</h2>
        <div className="fp-nav">
          <button type="button" ref={prevRef} aria-label="Previous products" disabled><Icon name="left" width={2} /></button>
          <button type="button" ref={nextRef} aria-label="More products"><Icon name="arrow" width={2} /></button>
        </div>
      </div>
      <div className="fp" ref={trackRef} tabIndex={0} aria-label={`${title}, scrolls sideways`}>
        {items.map((p) => <ProductCard key={p.id} product={p} />)}
        <Link className="pc pc--all" to={more.to} draggable={false}>
          <span className="ph"><span>{more.label}<Icon name="arrow" width={2.2} /></span></span>
        </Link>
      </div>
      <div className="fp-bar" ref={railRef} aria-hidden="true"><i /></div>
    </section>
  );
}
