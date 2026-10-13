// The legal pages' text: terms of sale, privacy policy and cookies. [Brackets] are facts only the shop owner has.
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { formatPrice } from '../lib/format';
import { useDelivery } from '../hooks/useDelivery';

export interface LegalDoc {
  slug: 'terms' | 'privacy' | 'cookies';
  tab: string;
  title: string;
  description: string;
  sections: { id: string; title: string; body: ReactNode }[];
}

/** The free-shipping sentence, from the limit staff set; nothing while it loads or when it is off. */
function FreeShipping() {
  const { data } = useDelivery();
  const free = data?.options.filter((o) => o.freeOverLimit) ?? [];
  if (!data?.freeShipping.on || free.length === 0) return null;
  return <>{free.map((o) => o.name).join(', ')} {free.length === 1 ? 'is' : 'are'} free on orders of {formatPrice(data.freeShipping.from)} or more. </>;
}

const COOKIES = (
  <>
    <p>Two, both needed for the shop to work, and neither used for tracking:</p>
    <ul>
      <li>One keeps a guest's cart for 30 days.</li>
      <li>One keeps you signed in for up to 7 days.</li>
    </ul>
  </>
);

export const LEGAL: LegalDoc[] = [
  {
    slug: 'terms',
    tab: 'Terms of sale',
    title: 'Terms of sale',
    description: 'The terms for buying from Tavla: prices and VAT, payment by card, delivery in the Baltics and Finland, cancelling before it is packed, and our 30-day returns.',
    sections: [
      { id: 'seller', title: 'Who sells to you', body: <p>Tavla is run by [company name], registry code [code], [address], Estonia. These terms apply to every order placed on this site.</p> },
      { id: 'prices', title: 'Prices and payment', body: <><p>Prices are in euros and include Estonian VAT (24%). The total you see before you press Pay is the total charged.</p><p>You pay by card through Stripe. Your card is charged when you press Pay.</p></> },
      { id: 'delivery', title: 'Delivery', body: <p>We deliver to Estonia, Latvia, Lithuania and Finland. <FreeShipping />Delivery times are estimates from the day the parcel leaves us.</p> },
      { id: 'cancel', title: 'Cancelling an order', body: <p>You can cancel an order from its page until we start packing it. The full amount goes back to your card.</p> },
      { id: 'returns', title: 'Returns', body: <p>You can return anything within 30 days of delivery, unused and in its box. We refund the price of what you return once the parcel reaches us. [Your return postage rule]</p> },
      { id: 'faults', title: 'Faulty or damaged goods', body: <p>If something arrives damaged or stops working because of a fault, <Link className="lnk" to="/contact">write to us</Link> with your order number and a photo. You have the rights the Estonian Law of Obligations Act gives a consumer for two years after delivery.</p> },
    ],
  },
  {
    slug: 'privacy',
    tab: 'Privacy policy',
    title: 'Privacy policy',
    description: 'What personal data Tavla keeps and why, what it never keeps, how it is encrypted and protected, who else gets to see it, and how to download or delete yours.',
    sections: [
      { id: 'who', title: 'Who we are', body: <p>Tavla is run by [company name], registry code [code], [address], Estonia. For anything about your data, write to [privacy email].</p> },
      {
        id: 'what',
        title: 'What we keep, and why',
        body: (
          <table className="dtable">
            <thead><tr><th scope="col">What</th><th scope="col">Why</th><th scope="col">How long</th></tr></thead>
            <tbody>
              <tr><td>Account: email, name, password</td><td data-label="Why">To sign you in. The password is stored only as a BCrypt hash.</td><td data-label="How long">Until you delete the account</td></tr>
              <tr><td>Addresses and phone</td><td data-label="Why">To deliver your orders</td><td data-label="How long">Until you remove them</td></tr>
              <tr><td>Orders: items, prices, delivery details</td><td data-label="Why">To deliver, handle returns, and keep the accounts the law asks for</td><td data-label="How long">7 years, as Estonian accounting law requires. Deleting your account unlinks them from you</td></tr>
              <tr><td>Payments: card brand, last 4 digits, Stripe's payment id</td><td data-label="Why">To show how you paid, and to refund you</td><td data-label="How long">With the order</td></tr>
              <tr><td>Reviews and helpful votes</td><td data-label="Why">To show them on product pages</td><td data-label="How long">Until you or we remove them</td></tr>
              <tr><td>Messages from the contact form</td><td data-label="Why">To answer you</td><td data-label="How long">12 months after we answer</td></tr>
            </tbody>
          </table>
        ),
      },
      { id: 'never', title: 'What we never keep', body: <p>Your card number and security code. You type them into Stripe's own fields, and they go straight to Stripe.</p> },
      {
        id: 'protect',
        title: 'How we protect it',
        body: (
          <ul>
            <li>Everything between your browser and us travels over HTTPS.</li>
            <li>Names, addresses, phone numbers and order details are encrypted in our database with AES-256-GCM, so a copy of the database alone shows nothing readable.</li>
            <li>Sign-in tokens are stored only as hashes, and expire.</li>
            <li>Staff who can see orders must use two-factor sign-in, and every change they make is logged.</li>
          </ul>
        ),
      },
      { id: 'share', title: 'Who else sees it', body: <p>Stripe, to take the payment. The courier, who gets your name, address and phone for the delivery. Our email provider, [provider], to send receipts. We never sell your data or show it to advertisers.</p> },
      { id: 'rights', title: 'Your rights', body: <p>You can see, download, correct or delete your data at any time from <Link className="lnk" to="/account">Your account, Profile</Link>. If we get something wrong, you can complain to the Estonian Data Protection Inspectorate.</p> },
      { id: 'cookies', title: 'Cookies', body: COOKIES },
    ],
  },
  {
    slug: 'cookies',
    tab: 'Cookies',
    title: 'Cookies',
    description: 'Tavla uses just two cookies, both needed for the shop to work: one keeps a guest cart for 30 days, one keeps you signed in. No tracking or advertising cookies.',
    sections: [
      { id: 'which', title: 'Which cookies we set', body: COOKIES },
      { id: 'none', title: 'What we don\'t use', body: <p>No analytics, advertising or social media cookies, so there is nothing to accept or refuse.</p> },
    ],
  },
];
