// Sign-in: email and password with client validation, then a 2FA code step when the account needs one.
import { FormEvent, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import * as authApi from '../api/auth';
import { ApiError, toApiError } from '../api/errors';
import { startSession } from '../auth/session';
import Field from '../components/Field';
import GoogleButton from '../components/GoogleButton';
import { useAppDispatch } from '../store';
import { Errors, hasErrors, validateLogin } from '../validation/authRules';

export default function LoginPage() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const redirectTo = (location.state as { from?: string } | null)?.from ?? '/account';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<Errors<'email' | 'password'>>({});
  const [challenge, setChallenge] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [pending, setPending] = useState(false);
  const [serverError, setServerError] = useState<ApiError | null>(null);

  async function run(action: () => Promise<void>) {
    setPending(true);
    setServerError(null);
    try {
      await action();
    } catch (err) {
      setServerError(toApiError(err));
    } finally {
      setPending(false);
    }
  }

  async function finish(accessToken: string) {
    await startSession(dispatch, accessToken);
    navigate(redirectTo, { replace: true });
  }

  function submitPassword(event: FormEvent) {
    event.preventDefault();
    const found = validateLogin({ email, password });
    setErrors(found);
    if (hasErrors(found)) return;
    run(async () => {
      const result = await authApi.login(email.trim(), password);
      if ('twoFactorRequired' in result) {
        setChallenge(result.challenge);
      } else {
        await finish(result.accessToken);
      }
    });
  }

  function submitCode(event: FormEvent) {
    event.preventDefault();
    if (!challenge || !code.trim()) return;
    run(async () => finish(await authApi.twoFactorLogin(challenge, code.trim())));
  }

  if (challenge) {
    return (
      <section className="auth-card" aria-labelledby="login-title">
        <p className="label">Step 2 of 2</p>
        <h1 id="login-title" className="title">
          Two-factor code
        </h1>
        <p className="lede">Enter the 6-digit code from your authenticator app, or one of your backup codes.</p>
        <form onSubmit={submitCode} noValidate>
          <Field
            label="Code"
            name="code"
            autoComplete="one-time-code"
            inputMode="text"
            autoFocus
            value={code}
            onChange={(e) => setCode(e.target.value)}
          />
          {serverError && (
            <p className="form-error" role="alert">
              {serverError.code === 'invalid_2fa_code'
                ? 'That code is not valid. After five wrong codes you have to sign in again.'
                : serverError.message}
            </p>
          )}
          <button className="button" type="submit" disabled={pending || !code.trim()}>
            {pending ? 'Checking…' : 'Verify'}
          </button>
        </form>
        <button type="button" className="text-button" onClick={() => setChallenge(null)}>
          Start over
        </button>
      </section>
    );
  }

  return (
    <section className="auth-card" aria-labelledby="login-title">
      <p className="label">Account</p>
      <h1 id="login-title" className="title">
        Sign in
      </h1>
      <form onSubmit={submitPassword} noValidate>
        <Field
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          value={email}
          error={errors.email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Field
          label="Password"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          error={errors.password}
          onChange={(e) => setPassword(e.target.value)}
        />
        {serverError && (
          <p className="form-error" role="alert">
            {serverError.message}
          </p>
        )}
        <button className="button" type="submit" disabled={pending}>
          {pending ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <GoogleButton onCredential={(idToken) => run(async () => finish(await authApi.googleLogin(idToken)))} />
      <p className="auth-links">
        <Link to="/forgot">Forgot your password?</Link>
        <Link to="/register">Create an account</Link>
      </p>
    </section>
  );
}
