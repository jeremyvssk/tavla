// Every page's title and meta description in one place, so one test can hold them all to the SEO rules.
//
// A title is the page's own words; usePageMeta adds " · Tavla" while the whole stays under 60 characters.
// A description is 155 to 160 characters (the brief's rule): what a search result shows under the title.

export const PAGE_META = {
  home: ['Chess, Go and Backgammon equipment',
    'Chess sets, boards, pieces and clocks, Go stones and boards, and backgammon cases. Real stock counts, free shipping over €100 and 30-day returns, all at Tavla.'],
  catalog: ['All chess, Go and backgammon products',
    'Browse every chess set, board, clock, Go set and backgammon case Tavla stocks. Filter by brand, price, rating and stock, and sort by price, rating or newest.'],
  cart: ['Your cart',
    'The chess, Go and backgammon equipment in your Tavla cart: change quantities, remove items and see the total before shipping, then go on to the secure checkout.'],
  checkout: ['Checkout',
    'Check out at Tavla as a guest or signed in: delivery and billing address, a delivery option with its price and dates, card payment, and your order summary.'],
  orderStatus: ['Your order',
    'Your Tavla order: its order number, what you bought, the delivery address and option, the estimated delivery date and the payment reference, all in one place.'],
  login: ['Sign in',
    'Sign in to your Tavla account with your email and password, or with Google, to see your orders, saved addresses and reviews of chess, Go and backgammon gear.'],
  register: ['Create an account',
    'Create a Tavla account to follow your orders, save delivery addresses, review what you bought and check out faster next time you shop chess, Go and backgammon.'],
  forgot: ['Reset your password',
    'Forgot your Tavla password? Type your email and we send a link to set a new one. The link works once and for 15 minutes, and signs you out everywhere else.'],
  reset: ['Choose a new password',
    'Choose a new password for your Tavla account. Every other session is signed out once it changes, so only you can get back into your orders and saved addresses.'],
  account: ['Your profile',
    'Your Tavla account: change your name and phone, see how you sign in, download a copy of all your data, or delete your account while your orders stay on record.'],
  orders: ['Your orders',
    'Every order you placed at Tavla, newest first: status, delivery dates and totals. Open one to follow its parcel, cancel it while you still can, or review it.'],
  order: ['Order details',
    'One of your Tavla orders: what you bought, where it goes, the delivery option and parcel number, the payment and any refund, and what you can still change.'],
  addresses: ['Your addresses',
    'Save up to ten delivery and billing addresses to your Tavla account and pick one at the checkout instead of typing it. Set a default for each, edit or remove.'],
  security: ['Sign-in and security',
    'How you sign in to Tavla: change your password and turn on two-factor authentication with an authenticator app, with eight backup codes in case of a lost phone.'],
  contact: ['Contact us',
    'Questions about an order, a return or chess, Go or backgammon sets? Write to Tavla and we answer by email. Answers on delivery, refunds and payment are here.'],
  faq: ['Shipping, payment and returns FAQ',
    'Answers on Tavla shipping prices and delivery times, card payments, returns and refunds, cancelling an order, and managing your account, together on one page.'],
  about: ['About Tavla: chess, Go and backgammon',
    'Tavla sells chess sets, Go boards and stones, backgammon cases and clocks from three suppliers. Who we are, how we choose what we stock, and how to reach us.'],
  notFound: ['Page not found',
    'This page does not exist on Tavla, or it has moved. Search the shop, or go to all our chess, Go and backgammon sets, boards, pieces, clocks and accessories.'],
} as const satisfies Record<string, readonly [string, string]>;

export type PageKey = keyof typeof PAGE_META;

// Added in turn until a short text reaches 155 characters; together they cover even an empty one.
const TAILS = [
  'Buy it at Tavla, with free shipping over €100 and 30-day returns.',
  'Real stock counts, secure card payment and delivery in the Baltics and Finland.',
  'Chess, Go and backgammon equipment for home, club and tournament play.',
];

/**
 * A description of 155 to 160 characters from text of any length (a product's description): padded with the
 * shop's offer when short, cut at a word with "…" when long.
 */
export function metaDescription(text: string): string {
  let d = text.replace(/\s+/g, ' ').trim();
  for (const tail of TAILS) {
    if (d.length >= 155) break;
    d = `${d} ${tail}`.trim();
  }
  if (d.length <= 160) return d;
  const cut = d.slice(0, 159);
  const space = cut.lastIndexOf(' ');
  return (space >= 154 ? cut.slice(0, space) : cut).replace(/[\s,;:.]+$/, '') + '…';
}

/** The catalog's title and description: a search, a category, or everything. */
export function catalogMeta(q: string | undefined, category: string | undefined, total: number | undefined): [string, string] {
  const count = total === undefined ? '' : `${total} `;
  if (q) {
    return [`Search: ${q}`.slice(0, 50),
      metaDescription(`${count}results for “${q}” at Tavla. Filter by category, brand, price and rating, sort by best match, price or rating, and compare chess, Go and backgammon gear.`)];
  }
  if (category) {
    return [category.slice(0, 50),
      metaDescription(`${category}: ${count}products at Tavla. Filter by brand, price, rating and stock, sort by price, rating or newest, and see real stock counts before you buy.`)];
  }
  return [...PAGE_META.catalog];
}
