// Account: profile from /auth/me, and two-factor setup (QR, confirm, backup codes shown once) or disable.
import { QRCodeSVG } from 'qrcode.react';
import { FormEvent, useState } from 'react';
import * as authApi from '../api/auth';
import { toApiError } from '../api/errors';
import { reloadUser } from '../auth/session';
import Field from '../components/Field';
import { useAppDispatch, useAppSelector } from '../store';

export default function AccountPage() {
  const user = useAppSelector((s) => s.auth.user)!;

  return (
    <section className="account" aria-labelledby="account-title">
      <p className="label">Signed in</p>
      <h1 id="account-title" className="title">
        {user.fullName}
      </h1>
      <dl className="spec-list">
        <div>
          <dt>Email</dt>
          <dd>{user.email}</dd>
        </div>
        <div>
          <dt>Role</dt>
          <dd>{user.role}</dd>
        </div>
        <div>
          <dt>Sign-in method</dt>
          <dd>{user.authProvider === 'LOCAL' ? 'Email and password' : user.authProvider}</dd>
        </div>
        <div>
          <dt>Two-factor</dt>
          <dd>{user.twoFactorEnabled ? 'On' : 'Off'}</dd>
        </div>
      </dl>
      {user.authProvider === 'LOCAL' ? (
        <TwoFactorPanel enabled={user.twoFactorEnabled} />
      ) : (
        <p className="notice">Two-factor settings are managed by your Google account.</p>
      )}
    </section>
  );
}

type Step =
  | { name: 'idle' }
  | { name: 'scan'; secret: string; otpauthUri: string }
  | { name: 'codes'; backupCodes: string[] };

function TwoFactorPanel({ enabled }: { enabled: boolean }) {
  const dispatch = useAppDispatch();
  const [step, setStep] = useState<Step>({ name: 'idle' });
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function run(action: () => Promise<void>) {
    setPending(true);
    setError(null);
    try {
      await action();
    } catch (err) {
      const apiError = toApiError(err);
      setError(apiError.code === 'invalid_credentials' ? 'Password is incorrect.' : apiError.message);
    } finally {
      setPending(false);
    }
  }

  function start(event: FormEvent) {
    event.preventDefault();
    run(async () => {
      const setup = await authApi.startTwoFactorSetup(password);
      setPassword('');
      setStep({ name: 'scan', ...setup });
    });
  }

  function confirm(event: FormEvent) {
    event.preventDefault();
    run(async () => {
      const backupCodes = await authApi.enableTwoFactor(code.trim());
      setCode('');
      // Held in component state only: leaving this page drops them, and the server never shows them again.
      setStep({ name: 'codes', backupCodes });
    });
  }

  function disable(event: FormEvent) {
    event.preventDefault();
    run(async () => {
      await authApi.disableTwoFactor(password);
      setPassword('');
      await reloadUser(dispatch);
    });
  }

  async function finishSetup() {
    await run(() => reloadUser(dispatch));
    setStep({ name: 'idle' });
  }

  if (step.name === 'codes') {
    return (
      <div className="panel">
        <h2 className="panel__title">Save your backup codes</h2>
        <p>
          Each code signs you in once if you lose your phone. <strong>They will not be shown again.</strong>
        </p>
        <ul className="backup-codes">
          {step.backupCodes.map((c) => (
            <li key={c}>{c}</li>
          ))}
        </ul>
        <button className="button" type="button" onClick={finishSetup}>
          I have saved them
        </button>
      </div>
    );
  }

  if (step.name === 'scan') {
    return (
      <div className="panel">
        <h2 className="panel__title">Scan with your authenticator app</h2>
        {/* Rendered locally: the URI contains the secret, so it must never go to an online QR service. */}
        <div className="qr">
          <QRCodeSVG value={step.otpauthUri} size={184} marginSize={2} title="Two-factor setup QR code" />
        </div>
        <p className="field__hint">
          Can't scan? Enter this key manually: <code className="secret">{step.secret}</code>
        </p>
        <form onSubmit={confirm} noValidate>
          <Field label="6-digit code from the app" name="code" inputMode="numeric" autoComplete="one-time-code"
            value={code} onChange={(e) => setCode(e.target.value)} />
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="button" type="submit" disabled={pending || !code.trim()}>
            {pending ? 'Checking…' : 'Turn on two-factor'}
          </button>
        </form>
      </div>
    );
  }

  return (
    <div className="panel">
      <h2 className="panel__title">Two-factor authentication</h2>
      <p>
        {enabled
          ? 'Sign-in asks for a code from your authenticator app. Turning it off needs your password.'
          : 'Add a second step to sign-in with an authenticator app. Starting setup needs your password.'}
      </p>
      <form onSubmit={enabled ? disable : start} noValidate>
        <Field label="Password" name="password" type="password" autoComplete="current-password"
          value={password} onChange={(e) => setPassword(e.target.value)} />
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className={enabled ? 'button button--danger' : 'button'} type="submit" disabled={pending || !password}>
          {pending ? 'Working…' : enabled ? 'Turn off two-factor' : 'Set up two-factor'}
        </button>
      </form>
    </div>
  );
}
