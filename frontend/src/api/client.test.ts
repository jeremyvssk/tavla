// The refresh interceptor: one refresh for many failed requests, and none for a wrong password.
import { AxiosAdapter, AxiosError, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { api, getAccessToken, refreshClient, setAccessToken, setSessionExpiredHandler } from './client';

function respond(config: InternalAxiosRequestConfig, status: number, data: unknown): Promise<AxiosResponse> {
  const response = { status, data, headers: {}, config, statusText: '' } as AxiosResponse;
  if (status >= 400) {
    return Promise.reject(new AxiosError('failed', String(status), config, null, response));
  }
  return Promise.resolve(response);
}

describe('api client', () => {
  let refreshCalls: number;

  beforeEach(() => {
    refreshCalls = 0;
    setAccessToken('expired');
    refreshClient.defaults.adapter = (async (config) => {
      refreshCalls++;
      // Slow enough that both failing requests are waiting on it at the same time.
      await new Promise((r) => setTimeout(r, 20));
      return respond(config, 200, { accessToken: 'fresh' });
    }) as AxiosAdapter;
  });

  it('shares one refresh between requests that fail together, then retries each with the new token', async () => {
    api.defaults.adapter = ((config) =>
      config.headers.Authorization === 'Bearer fresh'
        ? respond(config, 200, { ok: true })
        : respond(config, 401, { error: 'unauthorized' })) as AxiosAdapter;

    const results = await Promise.all([api.get('/auth/me'), api.get('/products')]);

    // Two refreshes would present the same refresh token twice, which the server treats as theft.
    expect(refreshCalls).toBe(1);
    expect(results.map((r) => r.status)).toEqual([200, 200]);
    expect(getAccessToken()).toBe('fresh');
  });

  it('does not refresh on a wrong password, which is also a 401', async () => {
    api.defaults.adapter = ((config) => respond(config, 401, { error: 'invalid_credentials' })) as AxiosAdapter;

    await expect(api.post('/auth/2fa/setup', { password: 'wrong' })).rejects.toBeInstanceOf(AxiosError);
    expect(refreshCalls).toBe(0);
  });

  it('clears the token and reports the session as over when the refresh itself fails', async () => {
    const expired = vi.fn();
    setSessionExpiredHandler(expired);
    refreshClient.defaults.adapter = ((config) => respond(config, 401, { error: 'invalid_credentials' })) as AxiosAdapter;
    api.defaults.adapter = ((config) => respond(config, 401, { error: 'unauthorized' })) as AxiosAdapter;

    await expect(api.get('/auth/me')).rejects.toBeInstanceOf(AxiosError);
    expect(getAccessToken()).toBeNull();
    expect(expired).toHaveBeenCalledOnce();
  });
});
