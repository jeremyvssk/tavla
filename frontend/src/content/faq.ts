// The FAQ's questions and answers, by topic. Prices come from lib/shipping, so the answers can't drift from the checkout.
import { formatPrice } from '../lib/format';
import { FREE_SHIPPING_FROM, SHIPPING } from '../lib/shipping';

export interface FaqTopic {
  id: string;
  title: string;
  items: { q: string; a: string[] }[];
}

const [standard, express, locker] = SHIPPING;

export const FAQ: FaqTopic[] = [
  {
    id: 'shipping',
    title: 'Shipping',
    items: [
      {
        q: 'How much does shipping cost?',
        a: [
          `Standard courier is ${formatPrice(standard.price)}, and free when your order is ${formatPrice(FREE_SHIPPING_FROM)} or more. Express courier is ${formatPrice(express.price)}. A parcel locker is ${formatPrice(locker.price)}.`,
          'You see each price before you pay, and the total never changes after that.',
        ],
      },
      {
        q: 'How long does delivery take?',
        a: [`Standard: ${standard.blurb}. Express: the next working day. Parcel locker: ${locker.blurb}. Orders placed on a working day by 15:00 leave the same day.`],
      },
      { q: 'Which countries do you deliver to?', a: ['Estonia, Latvia, Lithuania and Finland.'] },
      { q: 'How do I follow my parcel?', a: ['When it ships we email a tracking link. It is on your order page too.'] },
    ],
  },
  {
    id: 'payment',
    title: 'Payment',
    items: [
      { q: 'How can I pay?', a: ['By Visa, Mastercard or American Express, through Stripe. Your card number goes to Stripe and never reaches our server.'] },
      {
        q: 'My card was declined. What now?',
        a: [
          'Nothing was taken from your card. The page tells you why the bank said no, for example not enough money or an expired card.',
          'Your order keeps its items for a while, so you can try again with the same or another card from the order page.',
        ],
      },
      { q: 'When is my card charged?', a: ['When you press Pay. If you cancel before we start packing, the full amount goes back to the card.'] },
    ],
  },
  {
    id: 'returns',
    title: 'Returns',
    items: [
      { q: 'Can I send something back?', a: ['Yes, within 30 days of delivery, unused and in its box.'] },
      { q: 'Who pays to send it back?', a: ['[Your return postage rule]'] },
      { q: 'When do I get my money back?', a: ['We refund as soon as the parcel reaches us. Your bank then takes 5–10 working days to show it.'] },
    ],
  },
  {
    id: 'orders',
    title: 'Orders',
    items: [
      { q: 'Can I cancel an order?', a: ['Yes, until we start packing it. Open the order and press Cancel order; the money goes back to your card.'] },
      { q: 'Can I change the delivery address?', a: ['Write to us with your order number before it ships, and we change it.'] },
    ],
  },
  {
    id: 'account',
    title: 'Account',
    items: [
      { q: 'Do I need an account to order?', a: ['No. Check out as a guest, and create an account afterwards if you like.'] },
      { q: 'How do I delete my account or get my data?', a: ['Both are on Your account, Profile: Download my data, and Delete my account.'] },
    ],
  },
];
