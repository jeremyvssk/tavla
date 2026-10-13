// Contact: four tiles that answer the usual questions, then the form, with errors listed at the top and by each field.
import { FormEvent, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { toApiError } from '../api/errors';
import { sendContactMessage } from '../api/support';
import Icon, { IconName } from '../components/Icon';
import Recaptcha, { RECAPTCHA_SITE_KEY } from '../components/Recaptcha';
import { usePageMeta } from '../hooks/usePageMeta';
import { useAppSelector } from '../store';
import type { Errors } from '../validation/authRules';
import { ContactField, ContactForm, MESSAGE_MAX, TOPICS, validateContact } from '../validation/contactRules';
import { PAGE_META } from '../content/pageMeta';

const TILES: { icon: IconName; title: string; text: string; to: string }[] = [
  { icon: 'box', title: 'Where is my order?', text: 'Signed in: Your orders. As a guest: the link in your confirmation email.', to: '/account/orders' },
  { icon: 'back', title: 'Returns and refunds', text: '30 days to send it back. Refunds go to the card you paid with.', to: '/faq#returns' },
  { icon: 'truck', title: 'Shipping and delivery', text: 'Prices, times and the three ways we deliver.', to: '/faq#shipping' },
  { icon: 'card', title: 'Payments', text: 'Cards we take, and what to do when one is declined.', to: '/faq#payment' },
];

const LABELS: Record<ContactField, string> = {
  name: 'Your name', email: 'Email', topic: 'What it is about', orderNumber: 'Order number', message: 'Message',
};
const EMPTY: ContactForm = { name: '', email: '', topic: '', orderNumber: '', message: '' };

export default function ContactPage() {
  usePageMeta(...PAGE_META.contact);
  const user = useAppSelector((s) => s.auth.user);
  const [form, setForm] = useState<ContactForm>({ ...EMPTY, name: user?.fullName ?? '', email: user?.email ?? '' });
  const [errors, setErrors] = useState<Errors<ContactField>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [captcha, setCaptcha] = useState<string | null>(null);
  const [captchaReset, setCaptchaReset] = useState(0);
  const summary = useRef<HTMLDivElement>(null);

  const set = (k: ContactField) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [k]: e.target.value }));
  const fieldProps = (k: ContactField) => ({
    id: `c-${k}`,
    value: form[k],
    onChange: set(k),
    'aria-invalid': errors[k] ? true : undefined,
    'aria-describedby': [errors[k] && `c-${k}-e`, k === 'email' && 'c-email-h', k === 'orderNumber' && 'c-orderNumber-h', k === 'message' && 'c-message-c']
      .filter(Boolean).join(' ') || undefined,
  });
  const error = (k: ContactField) => errors[k] && <p className="err" id={`c-${k}-e`}><Icon name="alert" width={2} />{errors[k]}</p>;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setServerError(null);
    const found = validateContact(form);
    setErrors(found);
    if (Object.keys(found).length) {
      // the summary takes focus, so a screen reader hears what to fix and a keyboard can jump to each field
      requestAnimationFrame(() => summary.current?.focus());
      return;
    }
    if (RECAPTCHA_SITE_KEY && !captcha) {
      setServerError('Tick the CAPTCHA box first.');
      return;
    }
    setSending(true);
    try {
      await sendContactMessage({ ...form, captchaToken: captcha ?? undefined });
      setSent(true);
    } catch (err) {
      const api = toApiError(err);
      setErrors(api.fields as Errors<ContactField>);
      setServerError(api.message);
      setCaptchaReset((n) => n + 1);
    } finally {
      setSending(false);
    }
  }

  const problems = Object.entries(errors) as [ContactField, string][];

  return (
    <div className="hpage contact">
      <div className="conhead">
        <nav aria-label="Breadcrumb">
          <ol className="crumbs"><li><Link to="/">Home</Link></li><li><span aria-current="page">Contact</span></li></ol>
        </nav>
        <h1 className="dt">How can we help?</h1>
        <p className="lede">Most answers are one click away. If yours isn't, write to us below.</p>
      </div>
      <nav className="helpgrid" aria-label="Common questions">
        {TILES.map((t) => (
          <Link key={t.title} to={t.to}><Icon name={t.icon} /><b>{t.title}</b><span>{t.text}</span></Link>
        ))}
      </nav>
      <div className="cgrid even">
        <section className="card" aria-labelledby="form-h">
          <div className="card-h"><h2 id="form-h">Write to us</h2><span className="mute">We answer by email</span></div>
          {sent ? (
            <div className="sentnote" role="status">
              <Icon name="check" width={2.4} />
              <div>
                <h3>Thank you, your message is on its way.</h3>
                <p>We'll answer at {form.email.trim()}.</p>
                <button type="button" className="btn btn--ghost" onClick={() => { setForm({ ...EMPTY, name: form.name, email: form.email }); setSent(false); }}>
                  Write another message
                </button>
              </div>
            </div>
          ) : (
            <form noValidate onSubmit={submit}>
              {problems.length > 0 && (
                <div className="errsum" role="alert" tabIndex={-1} ref={summary}>
                  <h2><Icon name="alert" width={2} />{problems.length === 1 ? '1 thing' : `${problems.length} things`} to fix before we can send this</h2>
                  <ul>{problems.map(([k, msg]) => <li key={k}><a href={`#c-${k}`}>{LABELS[k]}</a>: {msg}</li>)}</ul>
                </div>
              )}
              {serverError && problems.length === 0 && <p className="form-error" role="alert">{serverError}</p>}
              <div className="grid2">
                <div className="f"><label htmlFor="c-name">Your name</label><input className="in" autoComplete="name" maxLength={100} {...fieldProps('name')} />{error('name')}</div>
                <div className="f">
                  <label htmlFor="c-email">Email</label>
                  <input className="in" type="email" autoComplete="email" maxLength={255} {...fieldProps('email')} />
                  {error('email') ?? <p className="hint" id="c-email-h">Our answer goes here.</p>}
                </div>
              </div>
              <div className="grid2">
                <div className="f">
                  <label htmlFor="c-topic">What is it about?</label>
                  <div className="fw">
                    <select className="in" {...fieldProps('topic')}>
                      <option value="">Choose a topic</option>
                      {TOPICS.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                    </select>
                    <Icon name="down" width={2} />
                  </div>
                  {error('topic')}
                </div>
                <div className="f">
                  <label htmlFor="c-orderNumber">Order number <small>(optional)</small></label>
                  <input className="in" placeholder="TV-24851" maxLength={8} {...fieldProps('orderNumber')} />
                  {error('orderNumber') ?? <p className="hint" id="c-orderNumber-h">At the top of your confirmation email.</p>}
                </div>
              </div>
              <div className="f">
                <label htmlFor="c-message">Message</label>
                <textarea className="in" rows={6} maxLength={MESSAGE_MAX} {...fieldProps('message')} />
                <p className={`count${form.message.length >= MESSAGE_MAX ? ' over' : ''}`} id="c-message-c">{form.message.length} / {MESSAGE_MAX}</p>
                {error('message')}
              </div>
              <Recaptcha onToken={setCaptcha} resetSignal={captchaReset} />
              <div className="formfoot">
                <p className="hint">We use your email only to answer this message.</p>
                <button className="btn" type="submit" disabled={sending}>{sending ? 'Sending…' : 'Send message'}</button>
              </div>
            </form>
          )}
        </section>
        <aside className="reach">
          <section className="card" aria-labelledby="other-h">
            <h2 id="other-h">Other ways to reach us</h2>
            <dl>
              <div><dt>Email</dt><dd>[support@your-domain]</dd></div>
              <div><dt>Phone</dt><dd>[+372 phone number]<br /><span className="mute">Mon–Fri, 9:00–17:00</span></dd></div>
              <div><dt>Shop and returns address</dt><dd>[Street, postcode, city]<br />Estonia</dd></div>
            </dl>
          </section>
          {user ? (
            <section className="card dark" aria-labelledby="acc-h">
              <h2 id="acc-h">About an order?</h2>
              <p>Your orders show where each one is, and let you cancel one before we pack it.</p>
              <Link className="btn btn--light" to="/account/orders">Your orders</Link>
            </section>
          ) : (
            <section className="card dark" aria-labelledby="acc-h">
              <h2 id="acc-h">Have an account?</h2>
              <p>Sign in to see every order, cancel one before we pack it, or start a return.</p>
              <Link className="btn btn--light" to="/login">Sign in</Link>
            </section>
          )}
        </aside>
      </div>
    </div>
  );
}
