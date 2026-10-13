// Typed calls to the /auth endpoints.
import { api, setAccessToken } from './client';

export interface Me {
  id: string;
  email: string;
  fullName: string;
  role: 'CUSTOMER' | 'ADMIN' | 'SUPPORT' | 'SALES';
  authProvider: 'LOCAL' | 'GOOGLE' | 'FACEBOOK';
  twoFactorEnabled: boolean;
}

export type LoginResponse = { accessToken: string } | { twoFactorRequired: true; challenge: string };

export interface RegisterInput {
  email: string;
  password: string;
  fullName: string;
  captchaToken?: string;
}

export async function register(input: RegisterInput) {
  await api.post('/auth/register', input);
}

export async function login(email: string, password: string): Promise<LoginResponse> {
  return (await api.post<LoginResponse>('/auth/login', { email, password })).data;
}

export async function twoFactorLogin(challenge: string, code: string): Promise<string> {
  return (await api.post<{ accessToken: string }>('/auth/2fa/login', { challenge, code })).data.accessToken;
}

export async function googleLogin(idToken: string): Promise<string> {
  return (await api.post<{ accessToken: string }>('/auth/oauth/google', { idToken })).data.accessToken;
}

export async function fetchMe(): Promise<Me> {
  return (await api.get<Me>('/auth/me')).data;
}

export async function logout() {
  await api.post('/auth/logout');
}

export async function forgotPassword(email: string) {
  await api.post('/auth/forgot-password', { email });
}

export async function resetPassword(token: string, newPassword: string) {
  await api.post('/auth/reset-password', { token, newPassword });
}

export async function startTwoFactorSetup(password: string): Promise<{ secret: string; otpauthUri: string }> {
  return (await api.post('/auth/2fa/setup', { password })).data;
}

/**
 * Turning it on also upgrades this session: the answer carries a new access token that says the second factor
 * was given, so a staff member's admin pages open without signing in again. The refresh cookie is marked too.
 */
export async function enableTwoFactor(code: string): Promise<string[]> {
  const { backupCodes, accessToken } = (await api.post<{ backupCodes: string[]; accessToken: string }>('/auth/2fa/enable', { code })).data;
  setAccessToken(accessToken);
  return backupCodes;
}

export async function disableTwoFactor(password: string) {
  await api.post('/auth/2fa/disable', { password });
}
