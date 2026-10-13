// k6 load test: four shopper journeys (browse, search, buy, sign in) against the whole stack through nginx.
//
// Run by run.sh, which starts a separate copy of the stack and steps through several user counts. One run:
//   docker run --rm --network ils-load_default -v "$PWD":/scripts -e VUS=50 -e DURATION=60s \
//     grafana/k6:1.3.0 run /scripts/shop.js
//
// Each virtual user is a person: it waits 1-4 seconds between pages, like someone reading. A journey that
// completes is one transaction (the `transactions` counter); `journey_ok` is the share that completed
// without a single failed step. Images are not fetched: nginx serves them from disk, never the backend.
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'https://frontend';
const VUS = Number(__ENV.VUS || 50);
const DURATION = __ENV.DURATION || '60s';
const PASSWORD = 'loadtest-password-1';
const USERS = 40;

const transactions = new Counter('transactions');
const journeyOk = new Rate('journey_ok');

// The mix of what people do: most browse, fewer search, fewer still buy or sign in.
const MIX = [['browse', 0.45], ['search', 0.3], ['buy', 0.15], ['account', 0.1]];

export const options = {
  insecureSkipTLSVerify: true, // the stack's self-signed certificate
  scenarios: {
    shoppers: { executor: 'constant-vus', vus: VUS, duration: DURATION, gracefulStop: '10s' },
  },
  // The brief's targets. A missed one marks the run failed in the summary; run.sh records it either way.
  thresholds: {
    http_req_duration: ['p(90)<2000'],
    http_req_failed: ['rate<0.05'],
    journey_ok: ['rate>0.98'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const json = { headers: { 'Content-Type': 'application/json' } };
const think = (min = 1, max = 4) => sleep(min + Math.random() * (max - min));
const pick = (list) => list[Math.floor(Math.random() * list.length)];

/** Once per run: the products and categories to visit, and shopper accounts to sign in with. */
export function setup() {
  const categories = http.get(`${BASE}/categories`).json().map((c) => c.slug);
  // in-stock products from the first few pages (48 is the API's page limit)
  const products = [0, 1, 2, 3].flatMap((page) => http.get(`${BASE}/products?sort=rating&size=48&page=${page}`).json().items)
    .filter((p) => p.inStock).map((p) => p.id);
  const run = Date.now();
  const users = [];
  for (let i = 0; i < USERS; i++) {
    const email = `loadtest-${run}-${i}@example.com`;
    const res = http.post(`${BASE}/auth/register`,
      JSON.stringify({ email, password: PASSWORD, fullName: `Load Tester ${i}` }), json);
    if (res.status === 201) users.push(email);
  }
  if (!products.length || !users.length) throw new Error('setup found no products or could not register users');
  return { categories, products, users };
}

function step(res, name, ok = (r) => r.status === 200) {
  const passed = check(res, { [name]: ok });
  return passed;
}

function browse(data) {
  let ok = step(http.get(`${BASE}/categories`, { tags: { name: 'categories' } }), 'categories');
  think();
  const category = pick(data.categories);
  const list = http.get(`${BASE}/products?category=${category}&sort=rating&size=24`, { tags: { name: 'listing' } });
  ok = step(list, 'listing') && ok;
  think(2, 5);
  const items = list.status === 200 ? list.json().items : [];
  const id = items.length ? pick(items).id : pick(data.products);
  ok = step(http.get(`${BASE}/products/${id}`, { tags: { name: 'product' } }), 'product') && ok;
  ok = step(http.get(`${BASE}/products/${id}/reviews?size=10`, { tags: { name: 'reviews' } }), 'reviews') && ok;
  think(3, 8);
  return ok;
}

function search() {
  const word = pick(['dgt', 'walnut', 'magnetic', 'go stones', 'backgammon', 'clock', 'staunton', 'travel']);
  let ok = true;
  // typed letter by letter: the box asks for suggestions as the words grow (it waits 250 ms between keys)
  for (let n = 2; n <= Math.min(word.length, 5); n++) {
    ok = step(http.get(`${BASE}/search/suggestions?q=${encodeURIComponent(word.slice(0, n))}`,
      { tags: { name: 'suggestions' } }), 'suggestions') && ok;
    sleep(0.3);
  }
  think();
  const results = http.get(`${BASE}/products?q=${encodeURIComponent(word)}&sort=relevance&size=24`, { tags: { name: 'search' } });
  ok = step(results, 'search') && ok;
  think(2, 5);
  const items = results.status === 200 ? results.json().items : [];
  if (items.length) {
    ok = step(http.get(`${BASE}/products/${pick(items).id}`, { tags: { name: 'product' } }), 'product') && ok;
    think(3, 6);
  }
  return ok;
}

function buy(data) {
  // a guest: the cart lives on the server, found by the cart_token cookie k6 keeps per virtual user
  const id = pick(data.products);
  let ok = step(http.get(`${BASE}/products/${id}`, { tags: { name: 'product' } }), 'product');
  think();
  ok = step(http.post(`${BASE}/cart/items`, JSON.stringify({ productId: id, quantity: 1 }),
    { ...json, tags: { name: 'add to cart' } }), 'add to cart', (r) => r.status === 200 || r.status === 201) && ok;
  think();
  ok = step(http.get(`${BASE}/cart`, { tags: { name: 'cart' } }), 'cart') && ok;
  ok = step(http.get(`${BASE}/delivery-options`, { tags: { name: 'delivery options' } }), 'delivery options') && ok;
  think(5, 10); // typing the address
  const order = http.post(`${BASE}/orders`, JSON.stringify({
    email: `guest-${__VU}-${__ITER}@example.com`, phone: '+372 5123 4567', firstName: 'Load', lastName: 'Tester',
    street: 'Narva mnt 7', postcode: '10117', city: 'Tallinn', country: 'EE', shippingMethod: 'standard',
  }), { ...json, tags: { name: 'place order' } });
  // The fake gateway never takes a card, so the order waits unpaid and its stock comes back when the hold
  // ends: run.sh shortens the hold to two minutes, or a long run would sell the shelves empty.
  ok = step(order, 'place order', (r) => r.status === 201) && ok;
  think();
  return ok;
}

function account(data) {
  const email = pick(data.users);
  const login = http.post(`${BASE}/auth/login`, JSON.stringify({ email, password: PASSWORD }), { ...json, tags: { name: 'login' } });
  let ok = step(login, 'login');
  if (login.status !== 200) return false;
  const auth = { headers: { Authorization: `Bearer ${login.json().accessToken}` } };
  think();
  ok = step(http.get(`${BASE}/users/me`, { ...auth, tags: { name: 'profile' } }), 'profile') && ok;
  think();
  ok = step(http.get(`${BASE}/orders`, { ...auth, tags: { name: 'my orders' } }), 'my orders') && ok;
  think(2, 5);
  return ok;
}

export default function (data) {
  let r = Math.random();
  const [name] = MIX.find(([, share]) => (r -= share) < 0) ?? MIX[0];
  let ok = false;
  group(name, () => {
    ok = name === 'browse' ? browse(data) : name === 'search' ? search() : name === 'buy' ? buy(data) : account(data);
  });
  transactions.add(1, { journey: name });
  journeyOk.add(ok, { journey: name });
}
