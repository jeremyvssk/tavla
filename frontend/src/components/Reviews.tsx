// Product reviews: the average and its spread (each star row filters), buyers' reviews by helpfulness, votes and reporting.
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  fetchReviews, fetchReviewSummary, ProductDetail, reportReview, Review, ReviewSort, voteReview,
} from '../api/catalog';
import { toApiError } from '../api/errors';
import { useAppSelector } from '../store';
import Icon, { Star } from './Icon';
import ReviewModal from './ReviewModal';

const SORT_LABELS: [ReviewSort, string][] = [
  ['helpful', 'Most helpful'], ['newest', 'Newest'], ['highest', 'Highest rating'], ['lowest', 'Lowest rating'],
];
const day = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });

function StarRow({ rating, size }: { rating: number; size?: 'lg' }) {
  return (
    <span className={size ? 'stars lg' : 'stars'} role="img" aria-label={`${Number(rating.toFixed(1))} out of 5`}>
      {[1, 2, 3, 4, 5].map((i) => <Star key={i} off={rating < i - 0.25} />)}
    </span>
  );
}

export default function Reviews({ product }: { product: ProductDetail }) {
  const status = useAppSelector((s) => s.auth.status);
  const [sort, setSort] = useState<ReviewSort>('helpful');
  const [stars, setStars] = useState<number | null>(null);
  const [pages, setPages] = useState(1);
  const [writing, setWriting] = useState(false);

  // PLANNED endpoint: until it exists, the summary falls back to the product's own average and count
  const summary = useQuery({
    queryKey: ['reviews', product.id, 'summary', status],
    queryFn: () => fetchReviewSummary(product.id),
    retry: false,
    staleTime: 60_000,
  });
  const list = useQuery({
    queryKey: ['reviews', product.id, sort, stars, pages],
    queryFn: async () => {
      // "Show more" reads the next pages and appends them, so the list keeps its place
      const all = await Promise.all(Array.from({ length: pages }, (_, i) => fetchReviews(product.id, i, sort, stars ?? undefined)));
      return { items: all.flatMap((p) => p.items), total: all[0].totalItems, more: all[all.length - 1].page + 1 < all[0].totalPages };
    },
    placeholderData: keepPreviousData,
  });

  const count = summary.data?.count ?? product.reviewCount;
  const average = summary.data?.average ?? product.averageRating;
  const spread = summary.data?.distribution;
  const me = summary.data?.me;

  return (
    <section className="t-sec rv-sec" id="reviews" aria-labelledby="rv-h">
      <div className="t-head"><h2 id="rv-h">Reviews</h2></div>
      <div className="rv">
        <div className="rv-sum">
          <div className="rv-avg">
            {average !== null && count > 0 ? (
              <>
                <span className="big">{average.toFixed(1)}</span>
                <div><StarRow rating={average} size="lg" /><p>Average of all {count} {count === 1 ? 'review' : 'reviews'}</p></div>
              </>
            ) : (
              <div><p className="none">No reviews yet.</p></div>
            )}
          </div>
          {spread && count > 0 && (
            <ul className="dist" aria-label="Filter by rating">
              {[5, 4, 3, 2, 1].map((n) => {
                const k = spread[n - 1];
                return (
                  <li key={n}>
                    <button type="button" aria-pressed={stars === n} disabled={k === 0}
                      aria-label={`${n} ${n === 1 ? 'star' : 'stars'}, ${k === 0 ? 'no reviews' : `${k} ${k === 1 ? 'review' : 'reviews'}`}`}
                      onClick={() => { setStars(stars === n ? null : n); setPages(1); }}>
                      <span>{n}<Star /></span>
                      <span className="tr"><i style={{ width: `${Math.round((k / count) * 100)}%` }} /></span>
                      <span>{k}</span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
          <WriteCta status={status} me={me} productId={product.id} onWrite={() => setWriting(true)} />
        </div>
        <div>
          {count > 0 && (
            <div className="rv-bar">
              <p className="mute" aria-live="polite">
                {stars ? `${list.data?.total ?? 0} with ${stars} ${stars === 1 ? 'star' : 'stars'}` : `Showing ${list.data?.items.length ?? 0} of ${list.data?.total ?? count} reviews`}
                {stars && <> · <button type="button" className="lnk" onClick={() => setStars(null)}>Show all</button></>}
              </p>
              <label className="sortl">
                <span>Sort</span>
                <select value={sort} onChange={(e) => { setSort(e.target.value as ReviewSort); setPages(1); }}>
                  {SORT_LABELS.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
                </select>
                <Icon name="down" width={2.4} />
              </label>
            </div>
          )}
          <ul className="rv-list">
            {list.data?.items.map((r) => <ReviewItem key={r.id} review={r} productId={product.id} signedIn={status === 'authenticated'} />)}
          </ul>
          {list.data?.more && (
            <div className="rv-more">
              <button className="btn btn--ghost" type="button" disabled={list.isFetching} onClick={() => setPages(pages + 1)}>
                {list.isFetching ? 'Loading…' : 'Show more reviews'}
              </button>
            </div>
          )}
        </div>
      </div>
      {writing && <ReviewModal product={product} purchasedAt={me?.purchasedAt ?? null} onClose={() => setWriting(false)} />}
    </section>
  );
}

/** Who may write: buyers, once per product. Guests are asked to sign in; others are told why not. */
function WriteCta({ status, me, productId, onWrite }: {
  status: string;
  me: { purchasedAt: string | null; reviewed: boolean } | null | undefined;
  productId: string;
  onWrite: () => void;
}) {
  if (status !== 'authenticated') {
    return (
      <div className="rv-cta">
        <p><b>Reviews come from people who bought this.</b> Bought it? Sign in and write one.</p>
        <Link className="btn btn--ghost" to="/login" state={{ from: `/catalog/${productId}` }}>Sign in to review</Link>
      </div>
    );
  }
  if (me?.reviewed) return <div className="rv-cta"><p><b>Thank you for your review.</b> It is in the list with the others.</p></div>;
  if (me && !me.purchasedAt) {
    return <div className="rv-cta"><p><b>Reviews come from people who bought this.</b> Once yours arrives, you can write one here.</p></div>;
  }
  return (
    <div className="rv-cta">
      <p>{me?.purchasedAt ? <><b>You bought this on {day.format(new Date(me.purchasedAt))}.</b> How is it holding up?</> : <b>Bought this? Tell other players how it plays.</b>}</p>
      <button className="btn" type="button" onClick={onWrite}>Write a review</button>
    </div>
  );
}

function ReviewItem({ review: r, productId, signedIn }: { review: Review; productId: string; signedIn: boolean }) {
  const client = useQueryClient();
  const [said, setSaid] = useState<string | null>(null);
  const [reported, setReported] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  // the API sends vote counts once helpful votes exist; until then the review shows without them
  const votable = r.helpfulCount !== undefined;
  const vote = useMutation({
    mutationFn: (v: 'helpful' | 'not_helpful' | null) => voteReview(productId, r.id, v),
    onSuccess: (updated, v) => {
      setProblem(null);
      setSaid(v === 'helpful' ? 'You said this helped. Press Yes again to take it back.'
        : v === 'not_helpful' ? "You said this didn't help. Press No again to take it back." : 'Your vote is taken back.');
      client.setQueriesData<{ items: Review[] }>({ queryKey: ['reviews', productId] }, (old) =>
        old && 'items' in old ? { ...old, items: old.items.map((x) => (x.id === r.id ? updated : x)) } : old);
    },
    onError: (err) => setProblem(toApiError(err).message),
  });
  const report = useMutation({
    mutationFn: () => reportReview(productId, r.id, 'shopper_report'),
    onSuccess: () => setReported(true),
    onError: (err) => setProblem(toApiError(err).message),
  });
  const press = (v: 'helpful' | 'not_helpful') => {
    if (!signedIn) { setProblem('Sign in to vote. One vote per shopper keeps the count honest.'); return; }
    vote.mutate(r.myVote === v ? null : v);
  };

  return (
    <li className="rv-item">
      <header><StarRow rating={r.rating} /></header>
      <h3>{r.title}</h3>
      <div className="rv-who">
        <b>{r.authorName ?? 'A Tavla shopper'}</b>
        {r.verifiedPurchase && <span className="vbadge"><Icon name="check" width={2.4} />Verified purchase</span>}
        <span>{day.format(new Date(r.createdAt))}</span>
      </div>
      <p className="body">{r.body}</p>
      {votable && (
        <div className="helpful">
          <span>Helpful?</span>
          <button type="button" aria-pressed={r.myVote === 'helpful'} disabled={vote.isPending} onClick={() => press('helpful')}>
            <Icon name="thumb" />Yes <small>{r.helpfulCount}</small>
          </button>
          <button type="button" aria-pressed={r.myVote === 'not_helpful'} disabled={vote.isPending} onClick={() => press('not_helpful')}>
            No <small>{r.notHelpfulCount ?? 0}</small>
          </button>
          {said && <span className="said" role="status">{said}</span>}
          {reported
            ? <span className="said" role="status">Reported. Our team will look at it.</span>
            : <button className="rep" type="button" disabled={report.isPending} onClick={() => report.mutate()}>Report</button>}
        </div>
      )}
      {problem && <p className="err" role="alert"><Icon name="alert" width={2} />{problem}</p>}
    </li>
  );
}
