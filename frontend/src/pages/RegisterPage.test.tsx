// Registration form: mirrors the DTO rules client-side and shows the server's field errors in place.
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosResponse } from 'axios';
import * as authApi from '../api/auth';
import { renderWithProviders } from '../test/renderWithProviders';
import RegisterPage from './RegisterPage';

vi.mock('../api/auth');

async function fill(values: { email: string; fullName: string; password: string; confirm: string }) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email'), values.email);
  if (values.fullName) await user.type(screen.getByLabelText('Full name'), values.fullName);
  await user.type(screen.getByLabelText('Password'), values.password);
  await user.type(screen.getByLabelText('Confirm password'), values.confirm);
  await user.click(screen.getByRole('button', { name: 'Create account' }));
}

describe('RegisterPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('rejects a short password, a missing name and a mismatched confirmation without calling the API', async () => {
    renderWithProviders(<RegisterPage />, { path: '/register' });

    await fill({ email: 'ada@shop.com', fullName: '', password: 'short', confirm: 'shorter' });

    expect(screen.getByText('Password must be at least 8 characters.')).toBeInTheDocument();
    expect(screen.getByText('Enter your name.')).toBeInTheDocument();
    expect(screen.getByText('Passwords do not match.')).toBeInTheDocument();
    expect(authApi.register).not.toHaveBeenCalled();
  });

  it('shows a conflict from the server when the email is taken', async () => {
    vi.mocked(authApi.register).mockRejectedValue(
      new AxiosError('failed', '409', undefined, null, { status: 409, data: { error: 'email_already_exists' }, headers: {} } as AxiosResponse),
    );
    renderWithProviders(<RegisterPage />, { path: '/register' });

    await fill({ email: 'ada@shop.com', fullName: 'Ada', password: 'password123', confirm: 'password123' });

    expect(await screen.findByRole('alert')).toHaveTextContent('An account with this email already exists.');
  });

  it('puts server-side field errors next to their fields', async () => {
    // The server is the real boundary: a rule the client copy missed still reaches the user.
    vi.mocked(authApi.register).mockRejectedValue(
      new AxiosError('failed', '400', undefined, null, {
        status: 400, data: { error: 'validation_failed', fields: { email: 'must be a well-formed email address' } }, headers: {},
      } as AxiosResponse),
    );
    renderWithProviders(<RegisterPage />, { path: '/register' });

    await fill({ email: 'ada@shop', fullName: 'Ada', password: 'password123', confirm: 'password123' });

    expect(await screen.findByText('must be a well-formed email address')).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
  });
});
