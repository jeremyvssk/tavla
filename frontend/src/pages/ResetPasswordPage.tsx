// Landing page for the emailed reset link (/reset?token=...): choose a new password.
import { FormEvent, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { resetPassword } from '../api/auth';
import { toApiError } from '../api/errors';
import Field from '../components/Field';
import { Errors, hasErrors, PASSWORD_MAX, PASSWORD_MIN, validateReset } from '../validation/authRules';

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const [values, setValues] = useState({ password: '', confirmPassword: '' });
  const [errors, setErrors] = useState<Errors<'password' | 'confirmPassword'>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [done, setDone] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const found = validateReset(values);
    setErrors(found);
    if (hasErrors(found) || !token) return;
    setPending(true);
    setServerError(null);
    try {
      await resetPassword(token, values.password);
      setDone(true);
    } catch (err) {
      const apiError = toApiError(err);
      setServerError(apiError.message);
      if (apiError.fields.newPassword) setErrors({ password: apiError.fields.newPassword });
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="auth-card" aria-labelledby="reset-title">
      <p className="label">Account recovery</p>
      <h1 id="reset-title" className="title">
        Choose a new password
      </h1>
      {!token ? (
        <p className="form-error" role="alert">
          This page needs the link from the reset email. <Link to="/forgot">Request a new link.</Link>
        </p>
      ) : done ? (
        <p className="notice" role="status">
          Password changed. Every device that was signed in has been signed out.{' '}
          <Link to="/login">Sign in with the new password.</Link>
        </p>
      ) : (
        <form onSubmit={submit} noValidate>
          <Field label="New password" name="password" type="password" autoComplete="new-password"
            hint={`${PASSWORD_MIN} to ${PASSWORD_MAX} characters.`} value={values.password} error={errors.password}
            onChange={(e) => setValues((v) => ({ ...v, password: e.target.value }))} />
          <Field label="Confirm new password" name="confirmPassword" type="password" autoComplete="new-password"
            value={values.confirmPassword} error={errors.confirmPassword}
            onChange={(e) => setValues((v) => ({ ...v, confirmPassword: e.target.value }))} />
          {serverError && (
            <p className="form-error" role="alert">
              {serverError}
            </p>
          )}
          <button className="button" type="submit" disabled={pending}>
            {pending ? 'Saving…' : 'Set new password'}
          </button>
        </form>
      )}
    </section>
  );
}
