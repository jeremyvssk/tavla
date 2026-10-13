// Pieces every admin page shares: the role guard, the page head, a table's foot, a row's menu, the drawer, the confirm dialog and the load states.
import { KeyboardEvent as ReactKeyboardEvent, ReactNode, RefObject, useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import type { AuditEntry } from '../api/admin';
import { toApiError } from '../api/errors';
import Icon, { IconName } from '../components/Icon';
import { usePageMeta } from '../hooks/usePageMeta';
import { useAppSelector } from '../store';
import { can, Section } from './access';

/** A section the role can't open says so, instead of a page full of refused requests. */
export function Guard({ section, children }: { section: Section; children: ReactNode }) {
  const role = useAppSelector((s) => s.auth.user!.role);
  if (can(role, section)) return <>{children}</>;
  return (
    <div className="adm-body">
      <section className="apanel adm-msg" role="alert">
        <h1>Your role can't open this</h1>
        <p>Ask an admin if your work needs it. Each role sees only the tools its job uses.</p>
        <Link className="btn btn--ghost" to="/admin">Back to the overview</Link>
      </section>
    </div>
  );
}

export function AdminHead({ title, sub, pill, children }: { title: string; sub?: ReactNode; pill?: ReactNode; children?: ReactNode }) {
  usePageMeta(`${title} · Admin`);
  return (
    <div className="adm-h">
      <div><h1 className={pill ? 'withpill' : undefined}>{title}{pill}</h1>{sub && <p>{sub}</p>}</div>
      {children && <div className="row">{children}</div>}
    </div>
  );
}

/** An audit entry's sentence, with its target linked where the target has a page; `lower` when it follows a name. */
export function AuditWhat({ entry: e, lower }: { entry: AuditEntry; lower?: boolean }) {
  const [start, after = ''] = e.action.split('{target}');
  const before = lower && start ? start[0].toLowerCase() + start.slice(1) : start;
  const target = e.target && e.targetLink ? <Link className="lnk" to={e.targetLink}>{e.target}</Link> : e.target;
  return <>{before}{e.action.includes('{target}') && target}{after}</>;
}

/** Loading, or why it didn't load: until the /staff API exists this says what the server answered. */
export function LoadState({ query, what }: { query: { isPending: boolean; isError: boolean; error: unknown; refetch: () => void }; what: string }) {
  if (query.isPending) return <p className="status-line panel-b">Loading {what}…</p>;
  if (!query.isError) return null;
  const e = toApiError(query.error);
  return (
    <div className="msg msg--bad msg--block adm-err" role="alert">
      <Icon name="alert" />
      <div>
        <p><b>{what[0].toUpperCase() + what.slice(1)} didn't load.</b> {e.message}</p>
        <p><button type="button" className="link" onClick={() => query.refetch()}>Try again</button></p>
      </div>
    </div>
  );
}

/**
 * The side drawer the admin edits in (delivery options, categories, roles). A modal: focus goes in,
 * Tab stays inside, Escape and the scrim close it, the page behind doesn't scroll, and focus goes back.
 */
export function Drawer({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const box = useRef<HTMLElement>(null);
  useModal(box, onClose, 'input, select, textarea');
  return (
    <>
      <div className="mscrim" aria-hidden="true" onClick={onClose} />
      <section className="adm-drawer" role="dialog" aria-modal="true" aria-labelledby="dr-h" ref={box}>
        <div className="modal-h">
          <h2 id="dr-h">{title}</h2>
          <button className="iconb" type="button" aria-label="Close" onClick={onClose}><Icon name="close" width={2} /></button>
        </div>
        {children}
      </section>
    </>
  );
}

/** Focus in on open (the first match of `first`), Tab kept inside, Escape closes, no scrolling behind, focus back on close. */
function useModal(box: RefObject<HTMLElement | null>, onClose: () => void, first: string) {
  const close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    const before = document.activeElement as HTMLElement | null;
    box.current?.querySelector<HTMLElement>(first)?.focus();
    document.documentElement.style.overflow = 'hidden';
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close.current();
      if (e.key !== 'Tab' || !box.current) return;
      const f = [...box.current.querySelectorAll<HTMLElement>('button, input, select, textarea, a[href]')].filter((el) => !el.matches(':disabled'));
      const first = f[0], last = f[f.length - 1];
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    };
    addEventListener('keydown', onKey);
    return () => {
      removeEventListener('keydown', onKey);
      document.documentElement.style.overflow = '';
      before?.focus?.();
    };
  }, [box, first]);
}

export interface ConfirmOptions {
  title: string;
  body?: ReactNode;
  /** the button that goes ahead: "Archive", "Refund €41.90" */
  action: string;
  danger?: boolean;
}

/**
 * The page's own "are you sure", in place of the browser's confirm(), which can't be styled, reads as a
 * warning from the browser rather than the shop, and blocks the page. `ask` resolves true on the action
 * button and false on Cancel, Escape or the scrim. Focus starts on Cancel, so Enter alone does nothing harmful.
 */
export function useConfirm(): [(options: ConfirmOptions) => Promise<boolean>, ReactNode] {
  const [open, setOpen] = useState<{ options: ConfirmOptions; resolve: (yes: boolean) => void } | null>(null);
  const ask = (options: ConfirmOptions) => new Promise<boolean>((resolve) => setOpen({ options, resolve }));
  const answer = (yes: boolean) => {
    open?.resolve(yes);
    setOpen(null);
  };
  return [ask, open && <ConfirmDialog options={open.options} onAnswer={answer} />];
}

function ConfirmDialog({ options: o, onAnswer }: { options: ConfirmOptions; onAnswer: (yes: boolean) => void }) {
  const box = useRef<HTMLElement>(null);
  const id = useId();
  useModal(box, () => onAnswer(false), '[data-cancel]');
  return (
    <>
      <div className="mscrim" aria-hidden="true" onClick={() => onAnswer(false)} />
      <section className="modal confirm" role="alertdialog" aria-modal="true" aria-labelledby={`${id}-h`}
        aria-describedby={o.body ? `${id}-b` : undefined} ref={box}>
        <div className="modal-h"><h2 id={`${id}-h`}>{o.title}</h2></div>
        {o.body && <div className="modal-b" id={`${id}-b`}>{o.body}</div>}
        <div className="modal-f">
          <button className="btn btn--ghost" type="button" data-cancel onClick={() => onAnswer(false)}>Cancel</button>
          <button className={o.danger ? 'btn btn--danger' : 'btn'} type="button" onClick={() => onAnswer(true)}>{o.action}</button>
        </div>
      </section>
    </>
  );
}

export interface RowMenuItem {
  label: string;
  icon: IconName;
  /** a link (opened in a new tab when `newTab`), or an action */
  to?: string;
  newTab?: boolean;
  onSelect?: () => void;
  danger?: boolean;
}

/**
 * A table row's "more" menu. Fixed to the screen, so the table's scroll box can't cut it off, and it
 * opens upward near the bottom. Arrows move, Escape closes and puts focus back on the button.
 */
export function RowMenu({ label, items }: { label: string; items: RowMenuItem[] }) {
  const [at, setAt] = useState<{ top?: number; bottom?: number; right: number } | null>(null);
  const button = useRef<HTMLButtonElement>(null);
  const list = useRef<HTMLDivElement>(null);
  const id = useId();
  const close = (focusBack: boolean) => {
    setAt(null);
    if (focusBack) button.current?.focus();
  };
  function open() {
    const r = button.current!.getBoundingClientRect();
    const tall = items.length * 40 + 12;
    const right = innerWidth - r.right;
    setAt(r.bottom + tall + 8 > innerHeight ? { bottom: innerHeight - r.top + 4, right } : { top: r.bottom + 4, right });
  }
  useEffect(() => {
    if (!at) return;
    list.current?.querySelector<HTMLElement>('[role=menuitem]')?.focus();
    const outside = (e: PointerEvent) => {
      if (!list.current?.contains(e.target as Node) && !button.current?.contains(e.target as Node)) close(false);
    };
    // the menu is pinned to where the button was, so anything that moves the button closes it
    const moved = () => close(false);
    addEventListener('pointerdown', outside);
    addEventListener('scroll', moved, true);
    addEventListener('resize', moved);
    return () => {
      removeEventListener('pointerdown', outside);
      removeEventListener('scroll', moved, true);
      removeEventListener('resize', moved);
    };
  }, [at]); // eslint-disable-line react-hooks/exhaustive-deps
  function onKey(e: ReactKeyboardEvent<HTMLDivElement>) {
    const all = [...list.current!.querySelectorAll<HTMLElement>('[role=menuitem]')];
    const i = all.indexOf(document.activeElement as HTMLElement);
    const go = (n: number) => { e.preventDefault(); all[(n + all.length) % all.length]?.focus(); };
    if (e.key === 'ArrowDown') go(i + 1);
    else if (e.key === 'ArrowUp') go(i - 1);
    else if (e.key === 'Home') go(0);
    else if (e.key === 'End') go(all.length - 1);
    else if (e.key === 'Escape') { e.preventDefault(); close(true); }
    else if (e.key === 'Tab') close(false);
  }
  return (
    <>
      <button ref={button} className="rowact" type="button" aria-label={label} aria-haspopup="menu" aria-expanded={!!at}
        aria-controls={at ? id : undefined} onClick={() => (at ? close(false) : open())}>
        <Icon name="more" width={3} />
      </button>
      {at && (
        <div className="rmenu" role="menu" id={id} aria-label={label} ref={list} onKeyDown={onKey}
          data-up={at.bottom !== undefined ? '' : undefined} style={{ top: at.top, bottom: at.bottom, right: at.right }}>
          {items.map((it) => it.to ? (
            <Link key={it.label} role="menuitem" tabIndex={-1} to={it.to} target={it.newTab ? '_blank' : undefined} onClick={() => close(false)}>
              <Icon name={it.icon} />{it.label}
            </Link>
          ) : (
            <button key={it.label} role="menuitem" tabIndex={-1} type="button" className={it.danger ? 'danger' : undefined}
              onClick={() => { close(true); it.onSelect?.(); }}>
              <Icon name={it.icon} />{it.label}
            </button>
          ))}
        </div>
      )}
    </>
  );
}

/** A field's error line, tied to its input by `${id}-e`. */
export function FieldError({ id, error }: { id: string; error?: string }) {
  return error ? <p className="err" id={`${id}-e`}><Icon name="alert" width={2} />{error}</p> : null;
}

/** aria-invalid and aria-describedby for a field that may carry an error. */
export const invalid = (id: string, error?: string, hint?: string) => ({
  'aria-invalid': error ? true : undefined,
  'aria-describedby': [error && `${id}-e`, hint].filter(Boolean).join(' ') || undefined,
});

export const ROW_SIZES = [25, 50, 100];

/** "1–25 of 312 · oldest first", the keys a page answers to, rows per page where the page offers it, and the previous and next pages. */
export function TableFoot({ page, size, total, noun, note, onPage, onSize, keys }: {
  page: number;
  size: number;
  total: number;
  /** "orders": "12 orders", "1–25 of 312 orders" */
  noun?: string;
  note?: string;
  onPage: (p: number) => void;
  onSize?: (s: number) => void;
  keys?: ReactNode;
}) {
  const last = Math.max(0, Math.ceil(total / size) - 1);
  const range = (total <= size ? total.toLocaleString('en') : `${page * size + 1}–${Math.min(total, (page + 1) * size)} of ${total.toLocaleString('en')}`)
    + (noun ? ` ${noun}` : '');
  return (
    <div className="tbl-foot">
      <span>{range}{note && ` · ${note}`}</span>
      {keys}
      <span className="grow" />
      {onSize && (
        <label className="pgsz">
          Rows
          <span className="fw">
            <select className="in" value={size} onChange={(e) => onSize(Number(e.target.value))}>
              {ROW_SIZES.map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
            <Icon name="down" width={2.4} />
          </span>
        </label>
      )}
      <div className="pgs">
        <button type="button" aria-label="Previous page" disabled={page === 0} onClick={() => onPage(page - 1)}><Icon name="chevL" width={2} /></button>
        <button type="button" aria-label="Next page" disabled={page >= last} onClick={() => onPage(page + 1)}><Icon name="chevR" width={2} /></button>
      </div>
    </div>
  );
}

const ago = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
/** "4 h 13 min", "19 h", "2 d 7 h": how long something has waited, as exact as it still matters. */
export function waited(iso: string, now = Date.now()) {
  const min = Math.max(0, Math.floor((now - new Date(iso).getTime()) / 60000));
  const h = Math.floor(min / 60);
  if (h < 1) return `${min} min`;
  if (h < 12) return `${h} h ${min % 60} min`;
  if (h < 24) return `${h} h`;
  return `${Math.floor(h / 24)} d ${h % 24} h`;
}
export const relative = (iso: string) => {
  const days = Math.round((new Date(iso).getTime() - Date.now()) / 86_400_000);
  return ago.format(days, 'day');
};
/** Hands a downloaded file to the browser's save dialog. */
export function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  Object.assign(document.createElement('a'), { href: url, download: name }).click();
  URL.revokeObjectURL(url);
}

export const stamp = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
