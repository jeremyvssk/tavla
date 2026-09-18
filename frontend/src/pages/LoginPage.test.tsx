// Sign-in form: client validation blocks bad input, submit locks while pending, server errors and 2FA render.
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosResponse } from 'axios';
import * as authApi from '../api/auth';
import { renderWithProviders } from '../test/renderWithProviders';
import LoginPage from './LoginPage';

vi.mock('../api/auth');

function apiFailure(status: number, data: unknown, headers: Record<string, string> = {}) {
  return new AxiosError('failed', String(status), undefined, null, { status, data, headers } as AxiosResponse);
}

async function fillAndSubmit(email: string, password: string) {
  const user = userEvent.setup();
  if (email) await user.type(screen.getByLabelText('Email'), email);
  if (password) await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Sign in' }));
  return user;
}

describe('LoginPage', () => {
  beforeEach(() => vi.resetAllMocks());

  it('shows field messages and never calls the API when the input is invalid', async () => {
    renderWithProviders(<LoginPage />, { path: '/login' });

    await fillAndSubmit('not-an-email', '');

    expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument();
    expect(screen.getByText('Enter your password.')).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
    expect(authApi.login).not.toHaveBeenCalled();
  });

  it('disables the button while the request is in flight', async () => {
    let finish: (value: authApi.LoginResponse) => void = () => {};
    vi.mocked(authApi.login).mockReturnValue(new Promise((resolve) => (finish = resolve)));
    renderWithProviders(<LoginPage />, { path: '/login' });

    await fillAndSubmit('ada@shop.com', 'password123');

    expect(screen.getByRole('button', { name: 'Signing in…' })).toBeDisabled();
    finish({ twoFactorRequired: true, challenge: 'c' });
    await screen.findByLabelText('Code');
  });

  it('renders the server message for wrong credentials', async () => {
    vi.mocked(authApi.login).mockRejectedValue(apiFailure(401, { error: 'invalid_credentials' }));
    renderWithProviders(<LoginPage />, { path: '/login' });

    await fillAndSubmit('ada@shop.com', 'wrong-password');

    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.');
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled();
  });

  it('turns a 429 into a wait time taken from Retry-After', async () => {
    vi.mocked(authApi.login).mockRejectedValue(apiFailure(429, { error: 'too_many_requests' }, { 'retry-after': '600' }));
    renderWithProviders(<LoginPage />, { path: '/login' });

    await fillAndSubmit('ada@shop.com', 'password123');

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts. Try again in 10 minutes.');
  });

  it('asks for a code when the account has 2FA, then signs in with it', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ twoFactorRequired: true, challenge: 'challenge-1' });
    vi.mocked(authApi.twoFactorLogin).mockResolvedValue('access-token');
    vi.mocked(authApi.fetchMe).mockResolvedValue({
      id: '1', email: 'ada@shop.com', fullName: 'Ada', role: 'CUSTOMER', authProvider: 'LOCAL', twoFactorEnabled: true,
    });
    const { store } = renderWithProviders(<LoginPage />, { path: '/login' });

    const user = await fillAndSubmit('ada@shop.com', 'password123');
    await user.type(await screen.findByLabelText('Code'), '123456');
    await user.click(screen.getByRole('button', { name: 'Verify' }));

    await waitFor(() => expect(store.getState().auth.status).toBe('authenticated'));
    expect(authApi.twoFactorLogin).toHaveBeenCalledWith('challenge-1', '123456');
    expect(screen.getByText('navigated away')).toBeInTheDocument();
  });
});
