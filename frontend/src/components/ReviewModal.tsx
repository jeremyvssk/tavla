// Write a review: a dialog with the product, a five-star picker, a headline and the text, posted as the shopper's verified review.
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useEffect, useRef, useState } from 'react';
import { postReview, ProductDetail } from '../api/catalog';
import { toApiError } from '../api/errors';
import { useAppSelector } from '../store';
import Icon, { Star } from './Icon';
import Photo from './Photo';

// The design's limits. The server's ReviewRequest still allows a 255-character title and 5000-character body.
export const HEADLINE_MAX = 80;
export const REVIEW_MIN = 20;
export const REVIEW_MAX = 2000;
const WORDS = ['', 'Poor', 'Fair', 'Okay', 'Good', 'Excellent'];
const day = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });

/** "Kadri Saar" -> "Kadri S.", the way reviews show their author. */
export function shownName(fullName: string) {
  const [first, ...rest] = fullName.trim().split(/\s+/);
  return rest.length ? `${first} ${rest[rest.length - 1][0]}.` : first;
}

export default function ReviewModal({ product, purchasedAt, onClose }: {
  product: ProductDetail;
  purchasedAt: string | null;
  onClose: () => void;
}) {
  const client = useQueryClient();
  const user = useAppSelector((s) => s.auth.user);
  const [rating, setRating] = useState(0);
  const [hover, setHover] = useState(0);
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [errors, setErrors] = useState<{ rating?: string; title?: string; body?: string }>({});
  const dialog = useRef<HTMLElement>(null);
  const image = product.images.find((i) => i.primary) ?? product.images[0];

  const post = useMutation({
    mutationFn: () => postReview(product.id, { rating, title: title.trim(), body: body.trim() }),
    onSuccess: () => {
      // the database trigger moved the product's average, so the product and its reviews are both stale
      client.invalidateQueries({ queryKey: ['reviews', product.id] });
      client.invalidateQueries({ queryKey: ['product', product.id] });
      onClose();
    },
  });

  // A modal: focus goes in, Escape closes, the page behind doesn't scroll, and focus returns to Write a review.
  useEffect(() => {
    const before = document.activeElement as HTMLElement | null;
    dialog.current?.querySelector<HTMLElement>('input')?.focus();
    document.documentElement.style.overflow = 'hidden';
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
      if (e.key !== 'Tab' || !dialog.current) return;
      // keep Tab inside the dialog
      const f = [...dialog.current.querySelectorAll<HTMLElement>('button, input, textarea')].filter((el) => !el.hasAttribute('disabled'));
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
  }, [onClose]);

  function submit(e: FormEvent) {
    e.preventDefault();
    const t = title.trim(), b = body.trim();
    const found = {
      rating: rating ? undefined : 'Choose from one to five stars.',
      title: !t ? 'Give your review a headline.' : t.length > HEADLINE_MAX ? `Keep the headline under ${HEADLINE_MAX} characters.` : undefined,
      body: !b ? 'Write your review.' : b.length < REVIEW_MIN ? `Tell us a little more: at least ${REVIEW_MIN} characters.`
        : b.length > REVIEW_MAX ? `Keep it under ${REVIEW_MAX} characters.` : undefined,
    };
    setErrors(found);
    if (!Object.values(found).some(Boolean)) post.mutate();
  }

  const shown = hover || rating;
  return (
    <>
      <div className="mscrim" onClick={onClose} />
      <section className="modal" role="dialog" aria-modal="true" aria-labelledby="wr-h" ref={dialog}>
        <div className="modal-h">
          <h2 id="wr-h">Write a review</h2>
          <button className="iconb" type="button" aria-label="Close" onClick={onClose}><Icon name="close" width={2} /></button>
        </div>
        <form className="modal-b" id="review-form" noValidate onSubmit={submit}>
          <div className="forprod">
            <span className="th">{image && <Photo src={image.url} framing={image.framing} pad={0.06} />}</span>
            <div><b>{product.name}</b>{purchasedAt && <small>You bought this on {day.format(new Date(purchasedAt))}</small>}</div>
          </div>
          <fieldset className="starpick" aria-describedby={errors.rating ? 'wr-r-e' : undefined}>
            <legend>Your rating <span className="req" aria-hidden="true">*</span></legend>
            <div className="row3" onMouseLeave={() => setHover(0)}>
              {[1, 2, 3, 4, 5].map((n) => (
                <label key={n} className={n > shown ? 'off' : undefined} onMouseEnter={() => setHover(n)}>
                  <input type="radio" name="rating" value={n} checked={rating === n} onChange={() => setRating(n)} />
                  <span className="vh">{n} {n === 1 ? 'star' : 'stars'}</span>
                  <Star />
                </label>
              ))}
              {shown > 0 && <span className="says" aria-hidden="true">{shown} of 5 · {WORDS[shown]}</span>}
            </div>
            {errors.rating && <p className="err" id="wr-r-e"><Icon name="alert" width={2} />{errors.rating}</p>}
          </fieldset>
          <div className="f">
            <label htmlFor="wr-t">Headline <span className="req" aria-hidden="true">*</span></label>
            <div className="fw">
              <input className="in wctr" id="wr-t" value={title} maxLength={HEADLINE_MAX} onChange={(e) => setTitle(e.target.value)}
                aria-invalid={errors.title ? true : undefined} aria-describedby={errors.title ? 'wr-t-e wr-t-c' : 'wr-t-c'} />
              <span className="ctr" id="wr-t-c">{title.length} / {HEADLINE_MAX}</span>
            </div>
            {errors.title && <p className="err" id="wr-t-e"><Icon name="alert" width={2} />{errors.title}</p>}
          </div>
          <div className="f">
            <label htmlFor="wr-b">Your review <span className="req" aria-hidden="true">*</span></label>
            <textarea className="in" id="wr-b" rows={6} value={body} maxLength={REVIEW_MAX} onChange={(e) => setBody(e.target.value)}
              aria-invalid={errors.body ? true : undefined} aria-describedby={`${errors.body ? 'wr-b-e ' : ''}wr-b-h wr-b-c`} />
            <div className="hintrow">
              <p className="hint" id="wr-b-h">How it plays, how it's made, how it arrived. At least {REVIEW_MIN} characters. No links or contact details.</p>
              <p className="count" id="wr-b-c">{body.length} / {REVIEW_MAX}</p>
            </div>
            {errors.body && <p className="err" id="wr-b-e"><Icon name="alert" width={2} />{errors.body}</p>}
          </div>
          {user && (
            <p className="hint shownas">
              Shown as <b>{shownName(user.fullName)}</b>{purchasedAt && ' with a Verified purchase badge'}. It goes up right away.
            </p>
          )}
          {post.error && <p className="form-error" role="alert">{toApiError(post.error).message}</p>}
        </form>
        <div className="modal-f">
          <button className="btn btn--ghost" type="button" onClick={onClose}>Cancel</button>
          <button className="btn" type="submit" form="review-form" disabled={post.isPending}>{post.isPending ? 'Posting…' : 'Post review'}</button>
        </div>
      </section>
    </>
  );
}
