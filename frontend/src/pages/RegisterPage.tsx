// Registration with client validation mirroring RegisterRequest, the CAPTCHA widget, and sign-in on success.
import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import * as authApi from '../api/auth';
import { ApiError, toApiError } from '../api/errors';
import { startSession } from '../auth/session';
import Field from '../components/Field';
import Recaptcha, { RECAPTCHA_SITE_KEY } from '../components/Recaptcha';
import { useAppDispatch } from '../store';
import { Errors, hasErrors, PASSWORD_MAX, PASSWORD_MIN, validateRegister } from '../validation/authRules';

type FieldName = 'email' | 'fullName' | 'password' | 'confirmPassword';

export default function RegisterPage() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [values, setValues] = useState({ email: '', fullName: '', password: '', confirmPassword: '' });
  const [errors, setErrors] = useState<Errors<FieldName>>({});
  const [captchaToken, setCaptchaToken] = useState<string | null>(null);
  const [captchaReset, setCaptchaReset] = useState(0);
  const [pending, setPending] = useState(false);
  const [serverError, setServerError] = useState<ApiError | null>(null);

  const set = (name: FieldName) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setValues((v) => ({ ...v, [name]: e.target.value }));

  async function submit(event: FormEvent) {
    event.preventDefault();
    const found = validateRegister(values);
    setErrors(found);
    if (hasErrors(found)) return;
    if (RECAPTCHA_SITE_KEY && !captchaToken) {
      setServerError({ code: 'captcha_missing', message: 'Tick the CAPTCHA box first.', fields: {} });
      return;
    }

    setPending(true);
    setServerError(null);
    try {
      const email = values.email.trim();
      await authApi.register({
        email,
        password: values.password,
        fullName: values.fullName.trim(),
        captchaToken: captchaToken ?? undefined,
      });
      // A brand-new account has no 2FA, so this login always returns tokens directly.
      const result = await authApi.login(email, values.password);
      if ('accessToken' in result) {
        await startSession(dispatch, result.accessToken);
      }
      navigate('/account', { replace: true });
    } catch (err) {
      const apiError = toApiError(err);
      setServerError(apiError);
      // The server is the authority: show its per-field messages next to the fields they belong to.
      setErrors((current) => ({ ...current, ...apiError.fields }));
      setCaptchaReset((n) => n + 1);
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="auth-card" aria-labelledby="register-title">
      <p className="label">Account</p>
      <h1 id="register-title" className="title">
        Create an account
      </h1>
      <form onSubmit={submit} noValidate>
        <Field label="Email" name="email" type="email" autoComplete="email"
          value={values.email} error={errors.email} onChange={set('email')} />
        <Field label="Full name" name="fullName" autoComplete="name"
          value={values.fullName} error={errors.fullName} onChange={set('fullName')} />
        <Field label="Password" name="password" type="password" autoComplete="new-password"
          hint={`${PASSWORD_MIN} to ${PASSWORD_MAX} characters.`}
          value={values.password} error={errors.password} onChange={set('password')} />
        <Field label="Confirm password" name="confirmPassword" type="password" autoComplete="new-password"
          value={values.confirmPassword} error={errors.confirmPassword} onChange={set('confirmPassword')} />
        <Recaptcha onToken={setCaptchaToken} resetSignal={captchaReset} />
        {serverError && (
          <p className="form-error" role="alert">
            {serverError.message}
          </p>
        )}
        <button className="button" type="submit" disabled={pending}>
          {pending ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p className="auth-links">
        <Link to="/login">Already have an account? Sign in</Link>
      </p>
    </section>
  );
}
