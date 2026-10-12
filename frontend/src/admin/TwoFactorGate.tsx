// What a staff account without two-factor sees instead of the admin: turn it on (password, QR, code, backup codes) or leave.
import { QRCodeSVG } from 'qrcode.react';
import { ClipboardEvent, FormEvent, KeyboardEvent, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import * as authApi from '../api/auth';
import { toApiError } from '../api/errors';
import { endSession, reloadUser } from '../auth/session';
import Icon from '../components/Icon';
import { usePageMeta } from '../hooks/usePageMeta';
import { useAppDispatch, useAppSelector } from '../store';
import { ROLE_NAMES } from './access';

type Step = { name: 'password' } | { name: 'scan'; secret: string; uri: string } | { name: 'codes'; codes: string[] };

export default function TwoFactorGate() {
  usePageMeta('Turn on two-factor · Admin');
  const user = useAppSelector((s) => s.auth.user)!;
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [step, setStep] = useState<Step>({ name: 'password' });
  const [password, setPassword] = useState('');
  const [digits, setDigits] = useState(['', '', '', '', '', '']);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const boxes = useRef<(HTMLInputElement | null)[]>([]);

  async function run(action: () => Promise<void>) {
    setError(null);
    setPending(true);
    try { await action(); } catch (err) { setError(toApiError(err).message); } finally { setPending(false); }
  }
  const start = (e: FormEvent) => {
    e.preventDefault();
    run(async () => {
      const r = await authApi.startTwoFactorSetup(password);
      setPassword('');
      setStep({ name: 'scan', secret: r.secret, uri: r.otpauthUri });
      requestAnimationFrame(() => boxes.current[0]?.focus());
    });
  };
  const confirm = (e: FormEvent) => {
    e.preventDefault();
    run(async () => setStep({ name: 'codes', codes: await authApi.enableTwoFactor(digits.join('')) }));
  };

  // six boxes that act like one field: typing moves on, Backspace moves back, a pasted code fills them all
  function type(i: number, value: string) {
    const d = value.replace(/\D/g, '').slice(-1);
    setDigits((all) => all.map((x, j) => (j === i ? d : x)));
    if (d && i < 5) boxes.current[i + 1]?.focus();
  }
  function back(i: number, e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Backspace' && !digits[i] && i > 0) boxes.current[i - 1]?.focus();
  }
  function paste(e: ClipboardEvent<HTMLInputElement>) {
    const code = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    if (code.length < 2) return;
    e.preventDefault();
    setDigits(Array.from({ length: 6 }, (_, j) => code[j] ?? ''));
    boxes.current[Math.min(code.length, 5)]?.focus();
  }

  return (
    <div className="t">
      <header className="cbar">
        <div className="wrap gate-bar">
          <Link className="back" to="/"><Icon name="left" width={2} /><span>Back to the shop</span></Link>
          <span className="gate-brand"><span className="t-logo">Tavla</span><span className="adm-brand"><span>Admin</span></span></span>
          <button type="button" className="secure linkish" onClick={async () => { await endSession(dispatch); navigate('/'); }}>Sign out</button>
        </div>
      </header>
      <main className="gate">
        <section className={step.name === 'scan' ? 'gate-card' : 'gate-card one'} aria-labelledby="g-h">
          <div>
            <span className="lockmark" aria-hidden="true"><Icon name="lock" width={2} /></span>
            <h1 id="g-h">{step.name === 'codes' ? 'Save your backup codes' : 'Turn on two-factor to open the admin'}</h1>
            {step.name === 'codes' ? (
              <>
                <p className="mute">Each code signs you in once if your phone is lost. Keep them somewhere safe: you see them only now.</p>
                <ul className="backup-codes">{step.codes.map((c) => <li key={c}><code>{c}</code></li>)}</ul>
                <button className="btn" type="button" onClick={() => reloadUser(dispatch)}>I saved them, open the admin</button>
              </>
            ) : (
              <>
                <p className="mute">You're signed in as <b>{user.email}</b> with the {ROLE_NAMES[user.role]} role. Every staff account needs a second step at sign-in before it can see orders, customers or settings.</p>
                <ol>
                  <li>Scan the code with an authenticator app, like Google Authenticator, 1Password or Aegis.</li>
                  <li>Type the 6-digit code the app shows.</li>
                  <li>Save the 8 backup codes we show next. You see them only once.</li>
                </ol>
                {step.name === 'password' ? (
                  <form noValidate onSubmit={start}>
                    <div className="f">
                      <label htmlFor="g-pw">Your password, to start</label>
                      <input className="in" id="g-pw" type="password" autoComplete="current-password" value={password}
                        onChange={(e) => setPassword(e.target.value)} autoFocus />
                    </div>
                    {error && <p className="err" role="alert"><Icon name="alert" width={2} />{error}</p>}
                    <div className="row gate-acts">
                      <button className="btn" type="submit" disabled={pending || !password}>{pending ? 'Checking…' : 'Show the QR code'}</button>
                      <Link className="btn btn--ghost" to="/">Not now</Link>
                    </div>
                  </form>
                ) : (
                  <form noValidate onSubmit={confirm}>
                    <fieldset className="otpset">
                      <legend>Code from the app</legend>
                      <div className="otp">
                        {digits.map((d, i) => (
                          <input key={i} ref={(el) => { boxes.current[i] = el; }} inputMode="numeric" autoComplete={i === 0 ? 'one-time-code' : 'off'}
                            maxLength={1} value={d} aria-label={`Digit ${i + 1}`} onChange={(e) => type(i, e.target.value)}
                            onKeyDown={(e) => back(i, e)} onPaste={paste} />
                        ))}
                      </div>
                    </fieldset>
                    {error && <p className="err" role="alert"><Icon name="alert" width={2} />{error}</p>}
                    <div className="row gate-acts">
                      <button className="btn" type="submit" disabled={pending || digits.join('').length < 6}>{pending ? 'Checking…' : 'Turn on and continue'}</button>
                      <Link className="btn btn--ghost" to="/">Not now</Link>
                    </div>
                  </form>
                )}
              </>
            )}
          </div>
          {step.name === 'scan' && (
            <div className="gate-qr">
              <div className="qrbox"><QRCodeSVG value={step.uri} size={200} title="QR code for your authenticator app" /></div>
              <p className="hint">Can't scan it? Type this key into the app:</p>
              <p className="mono">{step.secret.replace(/(.{4})/g, '$1 ').trim()}</p>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
