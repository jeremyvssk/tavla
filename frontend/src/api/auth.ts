// Typed calls to the /auth endpoints.
import { api } from './client';

export interface Me {
  id: string;
  email: string;
  fullName: string;
  role: 'CUSTOMER' | 'ADMIN';
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

export async function enableTwoFactor(code: string): Promise<string[]> {
  return (await api.post<{ backupCodes: string[] }>('/auth/2fa/enable', { code })).data.backupCodes;
}

export async function disableTwoFactor(password: string) {
  await api.post('/auth/2fa/disable', { password });
}
