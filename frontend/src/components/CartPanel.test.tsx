// Cart panel against a mocked /cart API: lines, a sold-out line out of the total, recommendations, refused writes.
import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosResponse } from 'axios';
import * as cartApi from '../api/cart';
import type { Cart } from '../api/cart';
import { renderWithProviders } from '../test/renderWithProviders';
import { signedOut } from '../store/authSlice';
import { opened } from '../store/cartSlice';
import CartPanel from './CartPanel';

vi.mock('../api/cart', async (actual) => ({
  ...(await actual<typeof import('../api/cart')>()),
  fetchCart: vi.fn(),
  setCartQuantity: vi.fn(),
  removeFromCart: vi.fn(),
  addToCart: vi.fn(),
  fetchRecommendations: vi.fn(),
}));

const line = (over: Partial<cartApi.CartLine>): cartApi.CartLine => ({
  productId: 'p1', name: 'DGT 3000', brandName: 'DGT', options: null, imageUrl: null, imageFraming: null,
  unitPrice: 65, quantity: 1, countedQuantity: 1, lineTotal: 65, maxQuantity: 5, state: 'ok', ...over,
});

const cart: Cart = {
  lines: [
    line({}),
    line({ productId: 'p2', name: 'Classic plastic chess clock', unitPrice: 20, quantity: 1, countedQuantity: 0, lineTotal: 0, maxQuantity: 0, state: 'sold_out' }),
  ],
  itemCount: 1,
  subtotal: 65,
};

function conflict(body: cartApi.StockConflict) {
  return new AxiosError('failed', '409', undefined, null, { status: 409, data: body, headers: {} } as AxiosResponse);
}

async function openPanel() {
  const result = renderWithProviders(<CartPanel />);
  act(() => {
    result.store.dispatch(signedOut());
    result.store.dispatch(opened());
  });
  await screen.findByText('DGT 3000');
  return result;
}

describe('CartPanel', () => {
  beforeEach(() => {
    vi.mocked(cartApi.fetchCart).mockResolvedValue(cart);
    vi.mocked(cartApi.fetchRecommendations).mockResolvedValue([
      { id: 'r1', name: 'Walnut chessboard', price: 37, averageRating: null, reviewCount: 0, inStock: true, brandName: null,
        categorySlug: 'wooden-boards', primaryImageUrl: null, primaryImageFraming: null },
    ]);
  });
  afterEach(() => vi.clearAllMocks());

  it('shows the lines with the server prices, and a sold-out line outside the total', async () => {
    await openPanel();
    const dialog = screen.getByRole('dialog', { name: /your cart/i });
    expect(within(dialog).getByText('1 item')).toBeInTheDocument();
    expect(within(dialog).getByText('Sold out')).toBeInTheDocument();
    expect(within(dialog).getByText(/so it is not in your total/)).toBeInTheDocument();
    expect(within(dialog).getAllByText('€65.00').length).toBeGreaterThan(0);
    expect(within(dialog).getByRole('link', { name: /checkout/i })).toHaveAttribute('href', '/checkout');
  });

  it('puts the recommendations above the subtotal, and adds one with a tap', async () => {
    vi.mocked(cartApi.addToCart).mockResolvedValue(cart);
    await openPanel();
    const recs = await screen.findByRole('region', { name: 'Goes with your order' });
    await userEvent.click(within(recs).getByRole('button', { name: 'Add Walnut chessboard to cart' }));
    expect(cartApi.addToCart).toHaveBeenCalledWith('r1', undefined);
  });

  it('changes a quantity and removes a line through the API', async () => {
    vi.mocked(cartApi.setCartQuantity).mockResolvedValue({ ...cart, lines: [line({ quantity: 2, countedQuantity: 2, lineTotal: 130 })], itemCount: 2, subtotal: 130 });
    vi.mocked(cartApi.removeFromCart).mockResolvedValue({ lines: [], itemCount: 0, subtotal: 0 });
    await openPanel();
    await userEvent.click(screen.getAllByRole('button', { name: 'One more' })[0]);
    expect(cartApi.setCartQuantity).toHaveBeenCalledWith('p1', 2);
    expect(await screen.findByText('2 items')).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole('button', { name: 'Remove' })[0]);
    expect(cartApi.removeFromCart).toHaveBeenCalledWith('p1');
    expect(await screen.findByText('Your cart is empty.')).toBeInTheDocument();
  });

  it('says how many are left when the server refuses more', async () => {
    vi.mocked(cartApi.setCartQuantity).mockRejectedValue(conflict({ error: 'not_enough_stock', productId: 'p1', maxQuantity: 1, inCart: 1 }));
    await openPanel();
    await userEvent.click(screen.getAllByRole('button', { name: 'One more' })[0]);
    expect(await screen.findByText('Only 1 in stock, and all of them are in your cart.')).toBeInTheDocument();
  });
});
