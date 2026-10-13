// Checkout: known details prefilled, every problem listed before paying, the card checked before any order, and a lost connection keeps the input.
import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError } from 'axios';
import * as cartApi from '../api/cart';
import * as deliveryApi from '../api/delivery';
import * as ordersApi from '../api/orders';
import { renderWithProviders } from '../test/renderWithProviders';
import { signedIn, signedOut } from '../store/authSlice';
import CheckoutPage from './CheckoutPage';

vi.mock('../api/cart', async (actual) => ({ ...(await actual<typeof import('../api/cart')>()), fetchCart: vi.fn() }));
vi.mock('../api/orders');
vi.mock('../api/delivery', async (actual) => ({ ...(await actual<typeof import('../api/delivery')>()), fetchDeliveryOptions: vi.fn() }));

// the three options the migration seeds, as GET /delivery-options sends them
const delivery: deliveryApi.DeliverySettings = {
  freeShipping: { from: 100, on: true },
  options: [
    { id: 'standard', name: 'Standard courier', price: 4.9, orderBy: '15:00', daysMin: 2, daysMax: 4, countries: ['EE', 'LV', 'LT', 'FI'], active: true, freeOverLimit: true },
    { id: 'express', name: 'Express courier', price: 12.9, orderBy: '15:00', daysMin: 1, daysMax: 1, countries: ['EE', 'LV', 'LT', 'FI'], active: true, freeOverLimit: false },
    { id: 'locker', name: 'Parcel locker', price: 3.9, orderBy: '15:00', daysMin: 1, daysMax: 3, countries: ['EE'], active: true, freeOverLimit: false },
  ],
};

// Stripe's frame can't run in jsdom; a stand-in answers check and confirm the way the test says.
const card = vi.hoisted(() => ({ check: vi.fn(), confirm: vi.fn() }));
vi.mock('../components/CardPayment', async () => {
  const { forwardRef, useImperativeHandle } = await import('react');
  return { default: forwardRef(function FakeCard(_props, ref) {
    useImperativeHandle(ref, () => card);
    return <p>card form</p>;
  }) };
});

const placed: ordersApi.PlacedOrder = { id: 'o1', number: 'TV-10001', total: 102.9, holdUntil: '2026-10-08T12:30:00Z', clientSecret: 'pi_1_secret_2' };

const cart: cartApi.Cart = {
  lines: [{ productId: 'p1', name: 'Beskid Chess Set', brandName: 'Sunrise', options: null, imageUrl: null, imageFraming: null,
    unitPrice: 90, quantity: 1, countedQuantity: 1, lineTotal: 90, maxQuantity: 3, state: 'ok' }],
  itemCount: 1,
  subtotal: 90,
};

async function open(user?: { fullName: string; email: string }) {
  const result = renderWithProviders(<CheckoutPage />, { path: '/checkout' });
  act(() => {
    result.store.dispatch(user ? signedIn({ id: 'u1', role: 'CUSTOMER', authProvider: 'LOCAL', twoFactorEnabled: false, ...user }) : signedOut());
  });
  await screen.findByRole('heading', { name: 'Checkout' });
  await screen.findByRole('radio', { name: /Standard courier/ });
  return result;
}

describe('CheckoutPage', () => {
  beforeEach(() => {
    vi.mocked(cartApi.fetchCart).mockResolvedValue(cart);
    vi.mocked(deliveryApi.fetchDeliveryOptions).mockResolvedValue(delivery);
    // no earlier order unless a test says so (clearAllMocks keeps implementations, so each test starts here)
    vi.mocked(ordersApi.fetchLastDelivery).mockResolvedValue(null);
    card.check.mockResolvedValue(null);
    card.confirm.mockResolvedValue(null);
  });
  afterEach(() => vi.clearAllMocks());

  async function fillIn() {
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Phone'), '+372 5876 2210');
    await user.type(screen.getByLabelText('Street and house number'), 'Pärnu mnt 22');
    await user.type(screen.getByLabelText('Postcode'), '10141');
    await user.type(screen.getByLabelText('City'), 'Tallinn');
    return user;
  }

  it('fills in what the account knows', async () => {
    await open({ fullName: 'Kadri Saar', email: 'kadri.saar@example.com' });
    expect(screen.getByLabelText('Email')).toHaveValue('kadri.saar@example.com');
    expect(screen.getByLabelText('First name')).toHaveValue('Kadri');
    expect(screen.getByLabelText('Last name')).toHaveValue('Saar');
    expect(screen.getByText(/Signed in as/)).toBeInTheDocument();
  });

  it('fills in the phone and address of the last order', async () => {
    vi.mocked(ordersApi.fetchLastDelivery).mockResolvedValue({
      email: 'kadri.saar@example.com', phone: '+372 5123 4567', firstName: 'Kadri', lastName: 'Saar',
      street: 'Riia 14', flat: '3', postcode: '51010', city: 'Tartu', country: 'EE',
    });
    await open({ fullName: 'Kadri Saar', email: 'kadri.saar@example.com' });
    expect(await screen.findByDisplayValue('Riia 14')).toBeInTheDocument();
    expect(screen.getByLabelText('Phone')).toHaveValue('+372 5123 4567');
    expect(screen.getByLabelText('Postcode')).toHaveValue('51010');
    expect(screen.getByLabelText('City')).toHaveValue('Tartu');
  });

  it('asks a guest nothing about earlier orders', async () => {
    await open();
    expect(ordersApi.fetchLastDelivery).not.toHaveBeenCalled();
  });

  it('lists every field to fix at the top, marks them, and sends nothing', async () => {
    await open();
    // €90 is under the free-shipping line, so standard costs €4.90
    await userEvent.click(await screen.findByRole('button', { name: 'Pay €94.90' }));
    const summary = screen.getByText(/things to fix before you pay/).closest('.msg') as HTMLElement;
    for (const name of ['Email', 'Phone', 'First name', 'Last name', 'Street', 'Postcode', 'City']) {
      expect(within(summary).getByRole('button', { name })).toBeInTheDocument();
    }
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('Enter your email. The receipt goes there.')).toBeInTheDocument();
    expect(screen.getByText('Nothing was charged. Fix the fields in red and press Pay again.')).toBeInTheDocument();
    expect(ordersApi.placeOrder).not.toHaveBeenCalled();
  });

  it('keeps everything typed when the connection drops, and says nothing was charged', async () => {
    vi.mocked(ordersApi.placeOrder).mockRejectedValue(new AxiosError('Network Error'));
    await open({ fullName: 'Mart Kask', email: 'mart.kask@example.com' });
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Phone'), '+372 5876 2210');
    await user.type(screen.getByLabelText('Street and house number'), 'Pärnu mnt 22');
    await user.type(screen.getByLabelText('Postcode'), '10141');
    await user.type(screen.getByLabelText('City'), 'Tallinn');
    await user.click(screen.getByRole('radio', { name: /Express courier/ }));
    await user.click(screen.getByRole('button', { name: 'Pay €102.90' }));

    expect(await screen.findByText(/couldn't reach the server, so nothing was charged/)).toBeInTheDocument();
    expect(ordersApi.placeOrder).toHaveBeenCalledWith(expect.objectContaining({
      email: 'mart.kask@example.com', phone: '+372 5876 2210', postcode: '10141', country: 'EE', flat: null, shippingMethod: 'express',
    }));
    expect(screen.getByLabelText('Street and house number')).toHaveValue('Pärnu mnt 22');
  });

  it('places the order, confirms it with Stripe, then goes to the order page', async () => {
    vi.mocked(ordersApi.placeOrder).mockResolvedValue(placed);
    await open({ fullName: 'Mart Kask', email: 'mart.kask@example.com' });
    const user = await fillIn();
    await user.click(screen.getByRole('button', { name: 'Pay €94.90' }));

    expect(await screen.findByText('navigated away')).toBeInTheDocument();
    expect(card.check).toHaveBeenCalled();
    expect(card.confirm).toHaveBeenCalledWith('pi_1_secret_2', 'o1');
  });

  it('stops at a card Stripe finds wrong, before any order exists', async () => {
    card.check.mockResolvedValue('Your card number is incomplete.');
    await open({ fullName: 'Mart Kask', email: 'mart.kask@example.com' });
    const user = await fillIn();
    await user.click(screen.getByRole('button', { name: 'Pay €94.90' }));

    expect(await screen.findByText('Your card number is incomplete.')).toBeInTheDocument();
    expect(ordersApi.placeOrder).not.toHaveBeenCalled();
    expect(card.confirm).not.toHaveBeenCalled();
  });

  it('says nothing was charged when the payment provider is down, and keeps the input', async () => {
    vi.mocked(ordersApi.placeOrder).mockRejectedValue(new AxiosError('Service Unavailable', '503', undefined, undefined,
      { status: 503, data: { error: 'payment_unavailable' }, statusText: '', headers: {}, config: {} as never }));
    await open({ fullName: 'Mart Kask', email: 'mart.kask@example.com' });
    const user = await fillIn();
    await user.click(screen.getByRole('button', { name: 'Pay €94.90' }));

    expect(await screen.findByText(/couldn't reach the payment provider, so nothing was charged/)).toBeInTheDocument();
    expect(card.confirm).not.toHaveBeenCalled();
    expect(screen.getByLabelText('City')).toHaveValue('Tallinn');
  });

  it('says so when there is nothing to buy', async () => {
    vi.mocked(cartApi.fetchCart).mockResolvedValue({ lines: [], itemCount: 0, subtotal: 0 });
    const { store } = renderWithProviders(<CheckoutPage />, { path: '/checkout' });
    act(() => {
      store.dispatch(signedOut());
    });
    expect(await screen.findByRole('heading', { name: 'Nothing to check out' })).toBeInTheDocument();
  });
});
