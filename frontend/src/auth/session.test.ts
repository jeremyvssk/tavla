// startSession: the guest cart is merged before the shopper counts as signed in, and a failed merge doesn't block sign-in.
import * as authApi from '../api/auth';
import * as cartApi from '../api/cart';
import { createStore } from '../store';
import { startSession } from './session';

vi.mock('../api/auth');
vi.mock('../api/cart');

const me = { id: 'u1', email: 'kadri.saar@example.com', fullName: 'Kadri Saar', role: 'CUSTOMER' as const, authProvider: 'LOCAL' as const, twoFactorEnabled: false };

describe('startSession', () => {
  afterEach(() => vi.clearAllMocks());

  it('merges the guest cart first, so the account cart read after sign-in already has it', async () => {
    const order: string[] = [];
    vi.mocked(cartApi.mergeGuestCart).mockImplementation(async () => {
      order.push('merge');
      return { lines: [], itemCount: 0, subtotal: 0 };
    });
    vi.mocked(authApi.fetchMe).mockImplementation(async () => {
      order.push('me');
      return me;
    });
    const store = createStore();
    await startSession(store.dispatch, 'access-token');
    expect(order).toEqual(['merge', 'me']);
    expect(store.getState().auth.status).toBe('authenticated');
  });

  it('still signs in when the merge fails', async () => {
    vi.mocked(cartApi.mergeGuestCart).mockRejectedValue(new Error('offline'));
    vi.mocked(authApi.fetchMe).mockResolvedValue(me);
    const store = createStore();
    await startSession(store.dispatch, 'access-token');
    expect(store.getState().auth.status).toBe('authenticated');
  });
});
