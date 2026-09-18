// Requests a password reset email; the answer is the same whether or not the address has an account.
import { FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { forgotPassword } from '../api/auth';
import { toApiError } from '../api/errors';
import Field from '../components/Field';
import { validateEmail } from '../validation/authRules';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [error, setError] = useState<string>();
  const [serverError, setServerError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [sent, setSent] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const found = validateEmail(email);
    setError(found);
    if (found) return;
    setPending(true);
    setServerError(null);
    try {
      await forgotPassword(email.trim());
      setSent(true);
    } catch (err) {
      setServerError(toApiError(err).message);
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="auth-card" aria-labelledby="forgot-title">
      <p className="label">Account recovery</p>
      <h1 id="forgot-title" className="title">
        Reset your password
      </h1>
      {sent ? (
        <p className="notice" role="status">
          If an account exists for that address, a reset link is on its way. The link works for 15 minutes.
          In development, open MailHog at localhost:8025 to read it.
        </p>
      ) : (
        <form onSubmit={submit} noValidate>
          <Field label="Email" name="email" type="email" autoComplete="email"
            value={email} error={error} onChange={(e) => setEmail(e.target.value)} />
          {serverError && (
            <p className="form-error" role="alert">
              {serverError}
            </p>
          )}
          <button className="button" type="submit" disabled={pending}>
            {pending ? 'Sending…' : 'Send reset link'}
          </button>
        </form>
      )}
      <p className="auth-links">
        <Link to="/login">Back to sign in</Link>
      </p>
    </section>
  );
}
