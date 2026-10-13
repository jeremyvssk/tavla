// Admin delivery options: the free-shipping limit, the options shoppers see (in their order), and a drawer to edit one.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { DragEvent, FormEvent, KeyboardEvent, useEffect, useState } from 'react';
import { DeliveryOption, DeliverySettings, fetchDelivery, reorderDeliveryOptions, saveDeliveryOption, saveFreeShipping } from '../../api/admin';
import { toApiError } from '../../api/errors';
import Icon from '../../components/Icon';
import { formatPrice } from '../../lib/format';
import { arrivalWindow } from '../../lib/shipping';
import { COUNTRIES } from '../../validation/checkoutRules';
import { AdminHead, Drawer, FieldError, invalid, LoadState } from '../parts';

const KEY = ['staff', 'delivery'];
const MONEY = /^\d{1,5}([.,]\d{1,2})?$/;
const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;
const money = (v: string) => Number(v.replace(',', '.'));

/** "Next working day", "2–4 working days": what the checkout row says under the name. */
const arrives = (min: number, max: number) =>
  min === 1 && max === 1 ? 'Next working day' : min === max ? `${min} working days` : `${min}–${max} working days`;

export default function DeliveryPage() {
  const client = useQueryClient();
  const q = useQuery({ queryKey: KEY, queryFn: fetchDelivery, retry: false });
  const [editing, setEditing] = useState<DeliveryOption | 'new' | null>(null);
  const put = (d: DeliverySettings) => {
    client.setQueryData(KEY, d);
    // the checkout's and the cart's copy of the options
    client.invalidateQueries({ queryKey: ['delivery-options'] });
  };
  const toggle = useMutation({ mutationFn: (o: DeliveryOption) => saveDeliveryOption({ ...o, active: !o.active }), onSuccess: put });
  const reorder = useMutation({
    mutationFn: (options: DeliveryOption[]) => reorderDeliveryOptions(options.map((o) => o.id)),
    // the new order shows at once; a refusal puts the server's back
    onMutate: (options) => client.setQueryData<DeliverySettings>(KEY, (d) => d && { ...d, options }),
    onSuccess: put,
    onError: () => client.invalidateQueries({ queryKey: KEY }),
  });
  const [dragged, setDragged] = useState<number | null>(null);
  const [over, setOver] = useState<{ i: number; below: boolean } | null>(null);
  const d = q.data;
  const error = toggle.error ?? reorder.error;

  function move(from: number, to: number) {
    if (!d || to < 0 || to >= d.options.length || from === to) return;
    const next = [...d.options];
    next.splice(to, 0, next.splice(from, 1)[0]);
    reorder.mutate(next);
  }
  // the handle moves its row with the arrow keys too, so the order isn't mouse-only
  function onHandleKey(e: KeyboardEvent<HTMLButtonElement>, i: number) {
    if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
    e.preventDefault();
    const to = i + (e.key === 'ArrowUp' ? -1 : 1);
    move(i, to);
    requestAnimationFrame(() => document.querySelectorAll<HTMLButtonElement>('.grip')[to]?.focus());
  }
  function onDragOver(e: DragEvent<HTMLTableRowElement>, i: number) {
    if (dragged === null) return;
    e.preventDefault();
    const box = e.currentTarget.getBoundingClientRect();
    setOver({ i, below: e.clientY > box.top + box.height / 2 });
  }
  function onDrop() {
    if (dragged !== null && over) {
      const to = over.i + (over.below ? 1 : 0);
      move(dragged, to > dragged ? to - 1 : to);
    }
    setDragged(null);
    setOver(null);
  }

  return (
    <main className="adm-body">
      <AdminHead title="Delivery options" sub="What shoppers can choose at checkout. Changes apply to new orders only.">
        <button className="btn" type="button" disabled={!d} onClick={() => setEditing('new')}>Add an option</button>
      </AdminHead>
      <LoadState query={q} what="the delivery options" />
      {error && <p className="msg msg--bad adm-err" role="alert"><Icon name="alert" />{toApiError(error).message}</p>}
      {d && (
        <>
          <FreeShipping free={d.freeShipping} onSaved={put} />
          <section className="apanel" aria-labelledby="op-h">
            <div className="panel-h">
              <h2 id="op-h">Options</h2>
              <span className="mute">Drag to change the order shoppers see them in</span>
            </div>
            <div className="tblw">
              <table className="tbl">
                <thead>
                  <tr>
                    <th scope="col" className="w0"><span className="vh">Order</span></th>
                    <th scope="col">Option</th>
                    <th scope="col">Arrives in</th>
                    <th scope="col">Order by</th>
                    <th scope="col" className="r">Price</th>
                    <th scope="col">Free over limit</th>
                    <th scope="col">Countries</th>
                    <th scope="col">At checkout</th>
                    <th scope="col" className="w0"><span className="vh">Edit</span></th>
                  </tr>
                </thead>
                <tbody>
                  {d.options.map((o, i) => {
                    const open = editing !== 'new' && editing?.id === o.id;
                    const cls = [open && 'is-sel', dragged === i && 'dragging', over?.i === i && (over.below ? 'drop-below' : 'drop-above')].filter(Boolean).join(' ');
                    return (
                      <tr key={o.id} className={cls || undefined} draggable={dragged === i}
                        onDragStart={(e) => { e.dataTransfer.effectAllowed = 'move'; setDragged(i); }}
                        onDragOver={(e) => onDragOver(e, i)} onDrop={onDrop} onDragEnd={() => { setDragged(null); setOver(null); }}>
                        <td className="w0">
                          <button className="grip" type="button" aria-label={`Move ${o.name}: arrow up or down`}
                            onPointerDown={() => setDragged(i)} onPointerUp={() => dragged === i && over === null && setDragged(null)}
                            onKeyDown={(e) => onHandleKey(e, i)}>
                            <Icon name="grip" />
                          </button>
                        </td>
                        <td className="nw"><b>{o.name}</b></td>
                        <td className="nw">{arrives(o.daysMin, o.daysMax)}</td>
                        <td className="num2">{o.orderBy}</td>
                        <td className="r">{formatPrice(o.price)}</td>
                        <td>{o.freeOverLimit ? <span className="on2fa"><Icon name="check" width={2.4} />Yes</span> : <span className="mutet">No</span>}</td>
                        <td className="mono">{o.countries.join(' ')}</td>
                        <td>
                          <label className="sw">
                            <input type="checkbox" checked={o.active} disabled={toggle.isPending} onChange={() => toggle.mutate(o)} />
                            <i aria-hidden="true" /><span className="vh">{o.name} shown at checkout</span>
                          </label>
                        </td>
                        <td className="w0">
                          {open ? <span className="st proc">Editing</span> : <button className="linkb" type="button" onClick={() => setEditing(o)}>Edit</button>}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </section>
          {editing && (
            <OptionDrawer key={editing === 'new' ? 'new' : editing.id} option={editing === 'new' ? null : editing} free={d.freeShipping}
              onClose={() => setEditing(null)} onSaved={(next) => { put(next); setEditing(null); }} />
          )}
        </>
      )}
    </main>
  );
}

/** The one limit, saved when the field is left or the switch flips. */
function FreeShipping({ free, onSaved }: { free: DeliverySettings['freeShipping']; onSaved: (d: DeliverySettings) => void }) {
  const [amount, setAmount] = useState(free.from.toFixed(2));
  const [bad, setBad] = useState(false);
  const [saved, setSaved] = useState(false);
  const save = useMutation({
    mutationFn: saveFreeShipping,
    onSuccess: (d) => { onSaved(d); setSaved(true); },
  });
  useEffect(() => setAmount(free.from.toFixed(2)), [free.from]);
  function commit() {
    const ok = MONEY.test(amount.trim());
    setBad(!ok);
    if (ok && money(amount) !== free.from) save.mutate({ from: money(amount), on: free.on });
  }
  return (
    <section className="apanel fsrow" aria-labelledby="fs-h">
      <div>
        <h2 id="fs-h">Free shipping</h2>
        <p>The delivery line at the top of every page quotes this. Applies to options marked “free over the limit”.</p>
      </div>
      <div className="f">
        <label htmlFor="fsa">For orders from</label>
        <div className="fw fw--cur">
          <input className="in" id="fsa" inputMode="decimal" value={amount} {...invalid('fsa', bad ? 'x' : undefined)}
            onChange={(e) => { setAmount(e.target.value); setSaved(false); }} onBlur={commit}
            onKeyDown={(e) => e.key === 'Enter' && commit()} />
          <span className="unit">€</span>
        </div>
        <FieldError id="fsa" error={bad ? 'A price like 100 or 99.90' : save.error ? toApiError(save.error).message : undefined} />
      </div>
      <div className="row">
        <label className="sw">
          <input type="checkbox" checked={free.on} disabled={save.isPending} onChange={(e) => save.mutate({ from: free.from, on: e.target.checked })} />
          <i aria-hidden="true" />{free.on ? 'On' : 'Off'}
        </label>
        <span className="saved" aria-live="polite">{save.isPending ? 'Saving…' : saved ? 'Saved' : ''}</span>
      </div>
    </section>
  );
}

type Form = { name: string; price: string; orderBy: string; daysMin: string; daysMax: string; countries: string[]; active: boolean; freeOverLimit: boolean };
type Errors = Partial<Record<'name' | 'price' | 'orderBy' | 'days' | 'countries', string>>;

function check(f: Form): Errors {
  const e: Errors = {};
  if (f.name.trim().length < 2 || f.name.trim().length > 40) e.name = 'A name of 2 to 40 characters';
  if (!MONEY.test(f.price.trim())) e.price = 'A price like 4.90';
  if (!TIME.test(f.orderBy.trim())) e.orderBy = 'A time like 15:00';
  const min = Number(f.daysMin), max = Number(f.daysMax);
  if (!Number.isInteger(min) || !Number.isInteger(max) || min < 1 || max > 30) e.days = 'Whole working days, from 1 to 30';
  else if (max < min) e.days = 'The slowest can’t be faster than the fastest';
  if (!f.countries.length) e.countries = 'Pick at least one country';
  return e;
}

function OptionDrawer({ option, free, onClose, onSaved }: {
  option: DeliveryOption | null;
  free: DeliverySettings['freeShipping'];
  onClose: () => void;
  onSaved: (d: DeliverySettings) => void;
}) {
  const [f, setF] = useState<Form>(() => ({
    name: option?.name ?? '',
    price: option ? option.price.toFixed(2) : '',
    orderBy: option?.orderBy ?? '15:00',
    daysMin: String(option?.daysMin ?? 2),
    daysMax: String(option?.daysMax ?? 4),
    countries: option?.countries ?? ['EE'],
    active: option?.active ?? true,
    freeOverLimit: option?.freeOverLimit ?? false,
  }));
  const [errors, setErrors] = useState<Errors>({});
  const [tried, setTried] = useState(false);
  const save = useMutation({ mutationFn: saveDeliveryOption, onSuccess: onSaved });
  const edit = (changes: Partial<Form>) => {
    const next = { ...f, ...changes };
    setF(next);
    if (tried) setErrors(check(next));
  };
  function submit(e: FormEvent) {
    e.preventDefault();
    setTried(true);
    const found = check(f);
    setErrors(found);
    if (Object.keys(found).length) return;
    save.mutate({
      id: option?.id, name: f.name.trim(), price: money(f.price), orderBy: f.orderBy.trim(), daysMin: Number(f.daysMin), daysMax: Number(f.daysMax),
      countries: f.countries, active: f.active, freeOverLimit: f.freeOverLimit,
    });
  }
  const days: [number, number] = [Number(f.daysMin) || 1, Math.max(Number(f.daysMin) || 1, Number(f.daysMax) || 1)];
  const country = COUNTRIES.find((c) => c.code === f.countries[0])?.name;

  return (
    <Drawer title={option ? option.name : 'New delivery option'} onClose={onClose}>
      <form className="modal-b dform" id="opt-form" noValidate onSubmit={submit}>
        <div className="f">
          <label htmlFor="en">Name shoppers see</label>
          <input className="in" id="en" value={f.name} maxLength={40} {...invalid('en', errors.name)} onChange={(e) => edit({ name: e.target.value })} />
          <FieldError id="en" error={errors.name} />
        </div>
        <div className="row2">
          <div className="grid2">
            <div className="f">
              <label htmlFor="ep">Price</label>
              <div className="fw fw--cur">
                <input className="in" id="ep" inputMode="decimal" value={f.price} {...invalid('ep', errors.price)} onChange={(e) => edit({ price: e.target.value })} />
                <span className="unit">€</span>
              </div>
              <FieldError id="ep" error={errors.price} />
            </div>
            <div className="f">
              <label htmlFor="ec">Order by</label>
              <input className="in" id="ec" value={f.orderBy} {...invalid('ec', errors.orderBy, 'ec-h')} onChange={(e) => edit({ orderBy: e.target.value })} />
              <FieldError id="ec" error={errors.orderBy} />
            </div>
          </div>
          <p className="hint" id="ec-h">Orders placed before {TIME.test(f.orderBy) ? f.orderBy : 'this time'} on a working day leave the same day.</p>
        </div>
        <fieldset>
          <legend>Arrives in, working days</legend>
          <div className="grid2">
            <div className="f">
              <label className="vh" htmlFor="emin">Fastest</label>
              <div className="fw fw--word">
                <input className="in" id="emin" inputMode="numeric" value={f.daysMin} {...invalid('emin', errors.days && 'x')} onChange={(e) => edit({ daysMin: e.target.value })} />
                <span className="unit" aria-hidden="true">Fastest</span>
              </div>
            </div>
            <div className="f">
              <label className="vh" htmlFor="emax">Slowest</label>
              <div className="fw fw--word">
                <input className="in" id="emax" inputMode="numeric" value={f.daysMax} {...invalid('emin', errors.days && 'x')} onChange={(e) => edit({ daysMax: e.target.value })} />
                <span className="unit" aria-hidden="true">Slowest</span>
              </div>
            </div>
          </div>
          <FieldError id="emin" error={errors.days} />
        </fieldset>
        <fieldset aria-describedby={errors.countries ? 'ct-e' : undefined}>
          <legend>Ships to</legend>
          <div className="ctiles">
            {COUNTRIES.map((c) => (
              <label key={c.code} className="ctile">
                <input type="checkbox" checked={f.countries.includes(c.code)}
                  onChange={(e) => edit({ countries: e.target.checked ? [...f.countries, c.code] : f.countries.filter((x) => x !== c.code) })} />
                {c.name}<span className="cc">{c.code}</span>
              </label>
            ))}
          </div>
          <FieldError id="ct" error={errors.countries} />
        </fieldset>
        <div className="setrows">
          <label className="setrow">
            <span><b>Shown at checkout</b><small>Off hides it without deleting it</small></span>
            <span className="sw"><input type="checkbox" checked={f.active} onChange={(e) => edit({ active: e.target.checked })} /><i aria-hidden="true" /></span>
          </label>
          <label className="setrow">
            <span>
              <b>Free over the limit</b>
              <small>{free.on ? `Free when the order is ${formatPrice(free.from)} or more` : 'Free shipping is off for now'}</small>
            </span>
            <span className="sw"><input type="checkbox" checked={f.freeOverLimit} onChange={(e) => edit({ freeOverLimit: e.target.checked })} /><i aria-hidden="true" /></span>
          </label>
        </div>
        {country && (
          <div className="prev" aria-label="Preview">
            <p className="flb">Shoppers in {country} see</p>
            <div className="opt" aria-hidden="true">
              <input type="radio" tabIndex={-1} readOnly />
              <b>{f.name.trim() || 'Name shoppers see'}</b>
              <span className="d">{arrives(days[0], days[1])} · arrives {arrivalWindow({ daysMin: days[0], daysMax: days[1], orderBy: TIME.test(f.orderBy) ? f.orderBy : undefined })}</span>
              <span className="p">{MONEY.test(f.price.trim()) ? formatPrice(money(f.price)) : '€–'}</span>
            </div>
          </div>
        )}
        {save.error && <p className="msg msg--bad" role="alert"><Icon name="alert" />{toApiError(save.error).message}</p>}
      </form>
      <div className="modal-f">
        <button className="btn btn--ghost" type="button" onClick={onClose}>Cancel</button>
        <button className="btn" type="submit" form="opt-form" disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save'}</button>
      </div>
    </Drawer>
  );
}
