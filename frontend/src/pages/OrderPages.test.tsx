// Order pages against a mocked /orders API: filters sent to the server, the cancel sheet, and the waiting page.
import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import * as ordersApi from '../api/orders';
import type { OrderDetail, OrderListResponse } from '../api/orders';
import { renderWithProviders } from '../test/renderWithProviders';
import { signedIn } from '../store/authSlice';
import OrderPage from './OrderPage';
import OrdersPage from './OrdersPage';
import OrderStatusPage from './OrderStatusPage';

vi.mock('../api/orders');

const card = vi.hoisted(() => ({ check: vi.fn(), confirm: vi.fn() }));
vi.mock('../components/CardPayment', async () => {
  const { forwardRef, useImperativeHandle } = await import('react');
  return { default: forwardRef(function FakeCard(_props, ref) {
    useImperativeHandle(ref, () => card);
    return <p>card form</p>;
  }) };
});

const order: OrderDetail = {
  id: 'o1', number: 'TV-24817', placedAt: '2026-10-07T11:31:00Z', status: 'PAYMENT_SUCCESSFUL', itemCount: 2, total: 155,
  email: 'kadri.saar@example.com', phone: '+372 5123 4567',
  shipTo: { firstName: 'Kadri', lastName: 'Saar', street: 'Narva mnt 7', flat: '12', postcode: '10117', city: 'Tallinn', country: 'EE' },
  shippingMethod: 'standard',
  lines: [
    { productId: 'p1', name: 'Beskid Chess Set', options: null, imageUrl: null, imageFraming: null, unitPrice: 90, quantity: 1, lineTotal: 90 },
    { productId: 'p2', name: 'DGT 3000', options: null, imageUrl: null, imageFraming: null, unitPrice: 65, quantity: 1, lineTotal: 65 },
  ],
  subtotal: 155, shippingCost: 0, vat: 30, cancellable: true,
  payment: { brand: 'Visa', last4: '4242', paidAt: '2026-10-07T11:32:00Z' },
  paymentFailure: null, holdUntil: null, tracking: null,
  history: [
    { at: '2026-10-07T11:32:00Z', status: 'PAYMENT_SUCCESSFUL', note: 'Stripe confirmed the payment.' },
    { at: '2026-10-07T11:31:00Z', status: 'PENDING_PAYMENT', note: 'Order placed.' },
  ],
};

const list: OrderListResponse = {
  items: [{ id: 'o1', number: 'TV-24817', placedAt: order.placedAt, status: 'PAYMENT_SUCCESSFUL', itemCount: 2, total: 155,
    preview: [{ name: 'Beskid Chess Set', imageUrl: null, imageFraming: null }, { name: 'DGT 3000', imageUrl: null, imageFraming: null }] }],
  page: 0, size: 10, totalItems: 1, totalPages: 1,
  counts: { all: 1, in_progress: 1, delivered: 0, cancelled: 0, failed: 0 },
};

const signIn = (store: ReturnType<typeof renderWithProviders>['store']) => act(() => {
  store.dispatch(signedIn({ id: 'u1', email: 'kadri.saar@example.com', fullName: 'Kadri Saar', role: 'CUSTOMER', authProvider: 'LOCAL', twoFactorEnabled: false }));
});

describe('order pages', () => {
  afterEach(() => vi.clearAllMocks());

  it('asks the server for the chosen status, date range and sort', async () => {
    vi.mocked(ordersApi.fetchOrders).mockResolvedValue(list);
    renderWithProviders(<OrdersPage />, { path: '/account/orders' });

    const row = await screen.findByRole('link', { name: /TV-24817/ });
    expect(within(row).getByText('Payment successful')).toBeInTheDocument();
    expect(ordersApi.fetchOrders).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'all', sort: 'newest', page: 0 }));

    await userEvent.click(screen.getByRole('button', { name: /In progress/ }));
    await userEvent.selectOptions(screen.getByLabelText('Sort'), 'total_desc');
    await userEvent.selectOptions(screen.getByLabelText('Date'), 'any');
    expect(ordersApi.fetchOrders).toHaveBeenLastCalledWith({ status: 'in_progress', sort: 'total_desc', from: undefined, to: undefined, size: 10, page: 0 });
  });

  it('cancels only after asking, and shows the refund it will make', async () => {
    vi.mocked(ordersApi.fetchOrder).mockResolvedValue(order);
    vi.mocked(ordersApi.cancelOrder).mockResolvedValue({ ...order, status: 'CANCELLED', cancellable: false });
    renderWithProviders(<OrderPage />, { path: '/account/orders/:orderId', url: '/account/orders/o1' });

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel order' }));
    const sheet = screen.getByRole('dialog', { name: 'Cancel order TV-24817?' });
    expect(within(sheet).getByText(/back to Visa ending 4242/)).toBeInTheDocument();
    // the safe choice has the focus, so a stray Enter keeps the order
    expect(within(sheet).getByRole('button', { name: 'Keep my order' })).toHaveFocus();

    await userEvent.selectOptions(within(sheet).getByLabelText(/Why are you cancelling/), 'Ordered by mistake');
    await userEvent.click(within(sheet).getByRole('button', { name: 'Yes, cancel and refund' }));
    expect(ordersApi.cancelOrder).toHaveBeenCalledWith('o1', 'Ordered by mistake');
    expect(await screen.findAllByText('Cancelled')).not.toHaveLength(0);
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument();
    expect(screen.getByText(/€155.00 on its way back to Visa ending 4242/)).toBeInTheDocument();
  });

  it('says the refund was sent once Stripe confirmed it', async () => {
    vi.mocked(ordersApi.fetchOrder).mockResolvedValue({ ...order, status: 'REFUNDED', cancellable: false });
    renderWithProviders(<OrderPage />, { path: '/account/orders/:orderId', url: '/account/orders/o1' });
    expect(await screen.findByText(/€155.00 sent back to Visa ending 4242/)).toBeInTheDocument();
  });

  it('lets a guest cancel from the page their email links to', async () => {
    vi.mocked(ordersApi.fetchOrder).mockResolvedValue(order);
    vi.mocked(ordersApi.cancelOrder).mockResolvedValue({ ...order, status: 'REFUNDED', cancellable: false });
    renderWithProviders(<OrderStatusPage />, { path: '/checkout/:orderId', url: '/checkout/o1' });

    await userEvent.click(await screen.findByRole('button', { name: 'Cancel this order' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Yes, cancel and refund' }));
    expect(ordersApi.cancelOrder).toHaveBeenCalledWith('o1', null);
    expect(await screen.findByRole('heading', { name: 'This order was cancelled' })).toBeInTheDocument();
    expect(screen.getByText('The refund is on its way back to your card.')).toBeInTheDocument();
  });

  it('waits on a pending payment, then thanks the shopper once the status changes', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      vi.mocked(ordersApi.fetchOrder)
        .mockResolvedValueOnce({ ...order, status: 'PENDING_PAYMENT', payment: null })
        .mockResolvedValue(order);
      const { store } = renderWithProviders(<OrderStatusPage />, { path: '/checkout/:orderId', url: '/checkout/o1' });
      signIn(store);
      expect(await screen.findByRole('heading', { name: 'Confirming your payment' })).toBeInTheDocument();
      await act(() => vi.advanceTimersByTimeAsync(2100));
      expect(await screen.findByRole('heading', { name: 'Thank you, Kadri. Your order is in.' })).toBeInTheDocument();
      expect(screen.getByText(/Visa ending 4242/)).toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it('tells a failed payment apart, with nothing charged', async () => {
    vi.mocked(ordersApi.fetchOrder).mockResolvedValue({ ...order, status: 'PAYMENT_FAILED', payment: null, cancellable: false,
      paymentFailure: 'Your bank declined the card: not enough funds.' });
    renderWithProviders(<OrderStatusPage />, { path: '/checkout/:orderId', url: '/checkout/o1' });
    expect(await screen.findByRole('heading', { name: "Your payment didn't go through" })).toBeInTheDocument();
    expect(screen.getByText('Your bank declined the card: not enough funds. Nothing was charged.')).toBeInTheDocument();
  });

  it('offers another card while the items are held, on the same order', async () => {
    const failed: OrderDetail = { ...order, status: 'PAYMENT_FAILED', payment: null, cancellable: false,
      paymentFailure: 'Your card has insufficient funds.', holdUntil: new Date(Date.now() + 20 * 60_000).toISOString() };
    vi.mocked(ordersApi.fetchOrder).mockResolvedValueOnce(failed).mockResolvedValue({ ...failed, status: 'PENDING_PAYMENT', paymentFailure: null });
    vi.mocked(ordersApi.startPayment).mockResolvedValue({ clientSecret: 'pi_1_secret_2' });
    card.check.mockResolvedValue(null);
    card.confirm.mockResolvedValue(null);
    renderWithProviders(<OrderStatusPage />, { path: '/checkout/:orderId', url: '/checkout/o1' });

    expect(await screen.findByText('Your card has insufficient funds. Nothing was charged.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Pay €155.00' }));

    expect(ordersApi.startPayment).toHaveBeenCalledWith('o1');
    expect(card.confirm).toHaveBeenCalledWith('pi_1_secret_2', 'o1');
    expect(await screen.findByRole('heading', { name: 'Confirming your payment' })).toBeInTheDocument();
  });
});
