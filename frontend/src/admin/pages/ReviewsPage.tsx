// Admin reviews: reviews go live at once, so new and reported ones queue here to be kept, or hidden with a reason.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ModerationParams, ModerationReview, ModerationState, moderateReviews, fetchModeration } from '../../api/admin';
import { toApiError } from '../../api/errors';
import Icon, { Star } from '../../components/Icon';
import Photo from '../../components/Photo';
import { AdminHead, LoadState, stamp, TableFoot } from '../parts';

const TABS: [ModerationState, string][] = [['todo', 'To look at'], ['reported', 'Reported'], ['live', 'Live'], ['hidden', 'Hidden']];
const RATINGS = [['', 'Any rating'], ['low', '1–2 stars'], ['mid', '3 stars'], ['high', '4–5 stars']] as const;
const REASONS = ['Contains a link or an ad', 'Not about the product', 'Rude or personal', 'Personal details', 'Something else'];
const SIZE = 25;

export default function ReviewsPage() {
  const [url, setUrl] = useSearchParams();
  const client = useQueryClient();
  const state = (TABS.find(([v]) => v === url.get('state'))?.[0] ?? 'todo') as ModerationState;
  const rating = (url.get('rating') ?? '') as '' | 'low' | 'mid' | 'high';
  const sort = url.get('sort') === 'newest' ? 'newest' : 'oldest';
  const page = Number(url.get('page') ?? 0);
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [bulkHide, setBulkHide] = useState(false);
  const [bulkReason, setBulkReason] = useState(REASONS[0]);
  const set = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(url);
    for (const [k, v] of Object.entries(changes)) { if (v === null || v === '') next.delete(k); else next.set(k, v); }
    if (!('page' in changes)) next.delete('page');
    setUrl(next, { replace: true });
  };

  const params: ModerationParams = { state, rating: rating || undefined, sort, page, size: SIZE };
  const q = useQuery({ queryKey: ['staff', 'reviews', params], queryFn: () => fetchModeration(params), retry: false });
  const items = q.data?.items ?? [];
  useEffect(() => { setPicked(new Set()); setBulkHide(false); }, [state, rating, sort, page]);

  const act = useMutation({
    mutationFn: ({ ids, action, reason }: { ids: string[]; action: 'keep' | 'hide' | 'unhide'; reason?: string }) => moderateReviews(ids, action, reason),
    onSuccess: () => {
      setPicked(new Set());
      setBulkHide(false);
      client.invalidateQueries({ queryKey: ['staff', 'reviews'] });
      client.invalidateQueries({ queryKey: ['staff', 'overview'] });
    },
  });
  const toggle = (id: string) => setPicked((s) => { const n = new Set(s); if (n.has(id)) n.delete(id); else n.add(id); return n; });
  const total = q.data?.totalItems ?? 0;
  const counts = q.data?.counts;

  return (
    <main className="adm-body">
      <AdminHead title="Reviews"
        sub="Reviews go live as soon as a buyer writes one. New and reported ones wait here for a look: keep them, or hide the false, hateful or off-topic ones." />
      {act.error && <p className="msg msg--bad adm-err" role="alert"><Icon name="alert" />{toApiError(act.error).message}</p>}
      <section className="apanel adm-q" aria-label="Review queue">
        <div className="filt">
          <nav className="tabs2" aria-label="Show">
            {TABS.map(([value, label]) => (
              <Link key={value} to={value === 'todo' ? '?' : `?state=${value}`} replace aria-current={value === state ? 'page' : undefined}>
                {label}{counts && counts[value] > 0 && <small>{counts[value].toLocaleString('en')}</small>}
              </Link>
            ))}
          </nav>
          <div className="row">
            <label className="sel">
              <span className="vh">Rating</span>
              <select value={rating} onChange={(e) => set({ rating: e.target.value })}>
                {RATINGS.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
              </select>
              <Icon name="down" width={2.4} />
            </label>
            <label className="sel">
              <span className="vh">Sort</span>
              <select value={sort} onChange={(e) => set({ sort: e.target.value === 'oldest' ? null : e.target.value })}>
                <option value="oldest">Oldest first</option>
                <option value="newest">Newest first</option>
              </select>
              <Icon name="down" width={2.4} />
            </label>
          </div>
        </div>
        <LoadState query={q} what="the reviews" />
        {q.data && (items.length === 0
          ? <p className="panel-b mute">{state === 'todo' ? 'Nothing to look at. Every review has been seen.' : 'No reviews here.'}</p>
          : (
            <ul className="rq">
              {items.map((r) => (
                <Item key={r.id} review={r} picked={picked.has(r.id)} onPick={() => toggle(r.id)} busy={act.isPending}
                  onAct={(action, reason) => act.mutate({ ids: [r.id], action, reason })} />
              ))}
            </ul>
          ))}
        {picked.size > 0 && (
          <div className="bulk" role="region" aria-label={`${picked.size} reviews selected`}>
            <span className="bsel"><i aria-hidden="true" /><b>{picked.size} selected</b></span>
            <span className="sep" aria-hidden="true" />
            {bulkHide ? (
              <>
                <label className="bulk-sel">
                  <span className="vh">Reason</span>
                  <select value={bulkReason} onChange={(e) => setBulkReason(e.target.value)} autoFocus>
                    {REASONS.map((x) => <option key={x}>{x}</option>)}
                  </select>
                </label>
                <button className="del" type="button" disabled={act.isPending} onClick={() => act.mutate({ ids: [...picked], action: 'hide', reason: bulkReason })}>
                  <Icon name="eye" />Hide {picked.size}
                </button>
                <button type="button" onClick={() => setBulkHide(false)}>Cancel</button>
              </>
            ) : state === 'hidden' ? (
              <button type="button" disabled={act.isPending} onClick={() => act.mutate({ ids: [...picked], action: 'unhide' })}><Icon name="eye" />Unhide</button>
            ) : (
              <>
                {state !== 'live' && <button type="button" disabled={act.isPending} onClick={() => act.mutate({ ids: [...picked], action: 'keep' })}><Icon name="check" />Keep</button>}
                <button className="del" type="button" onClick={() => setBulkHide(true)}><Icon name="eye" />Hide…</button>
              </>
            )}
            <button className="x" type="button" aria-label="Clear selection" onClick={() => setPicked(new Set())}><Icon name="close" width={2} /></button>
          </div>
        )}
        {q.data && total > 0 && (
          <TableFoot page={page} size={SIZE} total={total} noun={state === 'todo' ? 'to look at' : undefined}
            note={sort === 'oldest' ? 'oldest first' : 'newest first'} onPage={(p) => set({ page: String(p) })} />
        )}
      </section>
    </main>
  );
}

function Item({ review: r, picked, onPick, onAct, busy }: {
  review: ModerationReview;
  picked: boolean;
  onPick: () => void;
  onAct: (action: 'keep' | 'hide' | 'unhide', reason?: string) => void;
  busy: boolean;
}) {
  const [hiding, setHiding] = useState(false);
  const [reason, setReason] = useState(r.reports[0] && REASONS.includes(r.reports[0].reason) ? r.reports[0].reason : REASONS[0]);
  const name = r.authorName ?? 'A shopper';
  return (
    <li className={picked ? 'is-sel' : undefined}>
      <input className="cbx" type="checkbox" checked={picked} onChange={onPick} aria-label={`Select review by ${name}`} />
      <span className="th">{r.productImageUrl && <Photo src={r.productImageUrl} framing={r.productImageFraming} pad={0.06} loading="lazy" />}</span>
      <div>
        <p className="pn2"><Link to={`/catalog/${r.productId}`} target="_blank">{r.productName}</Link></p>
        <span className="stars" role="img" aria-label={`${r.rating} out of 5`}>
          {[1, 2, 3, 4, 5].map((i) => <Star key={i} off={r.rating < i} />)}
        </span>
        <h3>{r.title}</h3>
        <p className="body">{r.body}</p>
        {(r.flags.length > 0 || r.reports.length > 0 || r.hiddenReason) && (
          <div className="flags">
            {r.hiddenReason && <span className="flag">Hidden: {r.hiddenReason}</span>}
            {r.reports.map((x, i) => <span key={`r${i}`} className="flag red"><Icon name="flag" />Reported: {x.reason}</span>)}
            {r.flags.map((f) => <span key={f.text} className={f.serious ? 'flag red' : 'flag'}>{f.serious && <Icon name="alert" />}{f.text}</span>)}
          </div>
        )}
        <div className="meta2">
          <span>{name}</span>
          {r.orderNumber && <span className="vbadge"><Icon name="check" />Bought it, order {r.orderNumber}</span>}
          <span>Sent {stamp.format(new Date(r.createdAt))}</span>
        </div>
      </div>
      <div className="acts5">
        {r.state === 'HIDDEN' ? (
          <button className="btn btn--ghost btn--sm" type="button" disabled={busy} onClick={() => onAct('unhide')}>Unhide</button>
        ) : hiding ? (
          <>
            <label className="sel">
              <span className="vh">Reason</span>
              <select value={reason} onChange={(e) => setReason(e.target.value)} autoFocus>
                {REASONS.map((x) => <option key={x}>{x}</option>)}
              </select>
              <Icon name="down" width={2.4} />
            </label>
            <button className="btn btn--danger btn--sm" type="button" disabled={busy} onClick={() => onAct('hide', reason)}>Hide</button>
            <button className="linkb" type="button" onClick={() => setHiding(false)}>Cancel</button>
          </>
        ) : (
          <>
            {r.state !== 'LIVE' && <button className="btn btn--sm" type="button" disabled={busy} onClick={() => onAct('keep')}>Keep</button>}
            <button className="btn btn--ghost btn--sm" type="button" onClick={() => setHiding(true)}>Hide…</button>
          </>
        )}
      </div>
    </li>
  );
}
