// Account, Addresses: up to ten saved addresses, one default for delivery and one for billing, added and edited in a dialog.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useEffect, useRef, useState } from 'react';
import { Address, createAddress, fetchAddresses, makeDefault, removeAddress, updateAddress } from '../api/account';
import { toApiError } from '../api/errors';
import Icon from '../components/Icon';
import { AccountTabs } from '../components/OrderParts';
import { usePageMeta } from '../hooks/usePageMeta';
import { useAppSelector } from '../store';
import type { Errors } from '../validation/authRules';
import { ADDRESS_LIMIT, AddressField, AddressForm, validateAddress } from '../validation/addressRules';
import { COUNTRIES, CountryCode } from '../validation/checkoutRules';

const countryName = (code: string) => COUNTRIES.find((c) => c.code === code)?.name ?? code;

export default function AddressesPage() {
  usePageMeta('Your addresses', 'Save delivery and billing addresses to your Tavla account and pick one at checkout instead of typing it.');
  const user = useAppSelector((s) => s.auth.user)!;
  const client = useQueryClient();
  const key = ['addresses', user.id];
  const list = useQuery({ queryKey: key, queryFn: fetchAddresses, retry: false });
  const [editing, setEditing] = useState<Address | 'new' | null>(null);
  const [said, setSaid] = useState<string | null>(null);
  const done = (message: string) => (addresses: Address[]) => {
    client.setQueryData(key, addresses);
    setSaid(message);
  };
  const setDefault = useMutation({
    mutationFn: (v: { a: Address; kind: 'delivery' | 'billing' }) => makeDefault(v.a.id, v.kind),
    onSuccess: (data, v) => done(`${v.a.label} is your default for ${v.kind} now.`)(data),
  });
  const remove = useMutation({
    mutationFn: (a: Address) => removeAddress(a.id),
    onSuccess: (data, a) => done(`${a.label} is removed.`)(data),
  });
  const failed = setDefault.error ?? remove.error;
  const addresses = list.data ?? [];

  return (
    <main className="acct">
      <p className="t-eyebrow">Your account</p>
      <AccountTabs />
      <div className="ttl"><h1 className="co-h1">Addresses</h1><span className="mute">Pick one at checkout instead of typing it</span></div>
      <div aria-live="polite">
        {said && <p className="msg msg--ok"><Icon name="check" width={2.4} /><b>{said}</b></p>}
        {failed && <p className="msg msg--bad" role="alert"><Icon name="alert" />{toApiError(failed).message}</p>}
      </div>
      {list.isPending && <p className="status-line">Loading your addresses…</p>}
      {list.isError && <p className="msg msg--bad" role="alert"><Icon name="alert" />{toApiError(list.error).message}</p>}
      {list.isSuccess && (
        <ul className="adrs">
          {addresses.map((a) => (
            <li key={a.id} className={a.defaultDelivery ? 'adr def' : 'adr'}>
              <div className="adr-h"><b>{a.label}</b></div>
              {(a.defaultDelivery || a.defaultBilling) && (
                <div className="defs">
                  {a.defaultDelivery && <span className="dtag">Default delivery</span>}
                  {a.defaultBilling && <span className="dtag soft">Default billing</span>}
                </div>
              )}
              <address>
                {a.firstName} {a.lastName}<br />{a.street}{a.flat && `-${a.flat}`}<br />{a.postcode} {a.city}<br />{countryName(a.country)}<br />{a.phone}
              </address>
              <div className="acts4">
                <button className="linkb" type="button" onClick={() => setEditing(a)}>Edit</button>
                {!a.defaultDelivery && (
                  <button className="linkb" type="button" onClick={() => setDefault.mutate({ a, kind: 'delivery' })}>Use for delivery</button>
                )}
                {!a.defaultBilling && (
                  <button className="linkb" type="button" onClick={() => setDefault.mutate({ a, kind: 'billing' })}>Use for billing</button>
                )}
                {!a.defaultDelivery && <button className="linkb red" type="button" onClick={() => remove.mutate(a)}>Remove</button>}
              </div>
            </li>
          ))}
          {addresses.length < ADDRESS_LIMIT && (
            <li>
              <button className="adr add" type="button" onClick={() => setEditing('new')}>
                <Icon name="plus" width={2.2} />Add an address<small>Up to {ADDRESS_LIMIT}</small>
              </button>
            </li>
          )}
        </ul>
      )}
      {addresses.length > 1 && <p className="hint adr-note">The default for delivery can't be removed. Make another address the default first.</p>}
      {editing && (
        <AddressDialog address={editing === 'new' ? null : editing} fullName={user.fullName} onClose={() => setEditing(null)}
          onSaved={(data, label) => { done(editing === 'new' ? `${label} is saved.` : `${label} is updated.`)(data); setEditing(null); }} />
      )}
    </main>
  );
}

function AddressDialog({ address, fullName, onClose, onSaved }: {
  address: Address | null;
  fullName: string;
  onClose: () => void;
  onSaved: (addresses: Address[], label: string) => void;
}) {
  const [first, ...rest] = fullName.split(' ');
  const [form, setForm] = useState<AddressForm>(address
    ? { ...address, flat: address.flat ?? '' }
    : { label: '', firstName: first, lastName: rest.join(' '), street: '', flat: '', postcode: '', city: '', country: 'EE', phone: '' });
  const [errors, setErrors] = useState<Errors<AddressField>>({});
  const dialog = useRef<HTMLElement>(null);
  const save = useMutation({
    mutationFn: () => {
      const input = { ...form, label: form.label.trim(), flat: form.flat.trim() || null };
      return address ? updateAddress(address.id, input) : createAddress(input);
    },
    onSuccess: (data) => onSaved(data, form.label.trim()),
    onError: (err) => setErrors(toApiError(err).fields as Errors<AddressField>),
  });

  useEffect(() => {
    const before = document.activeElement as HTMLElement | null;
    dialog.current?.querySelector<HTMLElement>('input')?.focus();
    document.documentElement.style.overflow = 'hidden';
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    addEventListener('keydown', onKey);
    return () => { removeEventListener('keydown', onKey); document.documentElement.style.overflow = ''; before?.focus?.(); };
  }, [onClose]);

  const field = (k: AddressField, label: string, props: React.InputHTMLAttributes<HTMLInputElement> = {}) => (
    <div className="f">
      <label htmlFor={`ad-${k}`}>{label}</label>
      <input className="in" id={`ad-${k}`} value={form[k]} onChange={(e) => setForm({ ...form, [k]: e.target.value })}
        aria-invalid={errors[k] ? true : undefined} aria-describedby={errors[k] ? `ad-${k}-e` : undefined} {...props} />
      {errors[k] && <p className="err" id={`ad-${k}-e`}><Icon name="alert" width={2} />{errors[k]}</p>}
    </div>
  );

  function submit(e: FormEvent) {
    e.preventDefault();
    const found = validateAddress(form);
    setErrors(found);
    if (!Object.values(found).some(Boolean)) save.mutate();
  }

  return (
    <>
      <div className="mscrim" onClick={onClose} />
      <section className="modal" role="dialog" aria-modal="true" aria-labelledby="ad-h" ref={dialog}>
        <div className="modal-h">
          <h2 id="ad-h">{address ? `Edit ${address.label}` : 'Add an address'}</h2>
          <button className="iconb" type="button" aria-label="Close" onClick={onClose}><Icon name="close" width={2} /></button>
        </div>
        <form className="modal-b" id="address-form" noValidate onSubmit={submit}>
          {field('label', 'Name for this address', { maxLength: 40, placeholder: 'Home, Work' })}
          <div className="grid2">
            {field('firstName', 'First name', { autoComplete: 'given-name' })}
            {field('lastName', 'Last name', { autoComplete: 'family-name' })}
          </div>
          <div className="grid2">
            {field('street', 'Street and house number', { autoComplete: 'address-line1' })}
            {field('flat', 'Flat (optional)', { autoComplete: 'address-line2' })}
          </div>
          <div className="grid2">
            {field('postcode', 'Postcode', { autoComplete: 'postal-code', inputMode: 'numeric' })}
            {field('city', 'City', { autoComplete: 'address-level2' })}
          </div>
          <div className="grid2">
            <div className="f">
              <label htmlFor="ad-country">Country</label>
              <div className="fw">
                <select className="in" id="ad-country" value={form.country} onChange={(e) => setForm({ ...form, country: e.target.value as CountryCode })}>
                  {COUNTRIES.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
                </select>
                <Icon name="down" width={2} />
              </div>
            </div>
            {field('phone', 'Phone', { type: 'tel', autoComplete: 'tel' })}
          </div>
          {save.error && <p className="form-error" role="alert">{toApiError(save.error).message}</p>}
        </form>
        <div className="modal-f">
          <button className="btn btn--ghost" type="button" onClick={onClose}>Cancel</button>
          <button className="btn" type="submit" form="address-form" disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save address'}</button>
        </div>
      </section>
    </>
  );
}
