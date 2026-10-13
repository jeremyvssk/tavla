// The FAQ's questions and answers, by topic. Delivery prices and days come from the server's options, so they can't drift from the checkout.
import type { DeliverySettings } from '../api/delivery';
import { formatPrice } from '../lib/format';
import { daysText } from '../lib/shipping';

export interface FaqTopic {
  id: string;
  title: string;
  items: { q: string; a: string[] }[];
}

/** While the options load, the shipping answers say where to look rather than guess a price. */
function shippingAnswers(delivery: DeliverySettings | undefined) {
  if (!delivery || delivery.options.length === 0) {
    return { cost: 'Each delivery option shows its price at checkout, before you pay.', time: 'Each option shows its delivery days at checkout.' };
  }
  const { freeShipping: free, options } = delivery;
  const cost = options.map((o) => `${o.name} is ${formatPrice(o.price)}${o.freeOverLimit && free.on
    ? `, and free when your order is ${formatPrice(free.from)} or more` : ''}.`).join(' ');
  const time = options.map((o) => `${o.name}: ${daysText(o).toLowerCase()}.`).join(' ')
    + ` Orders placed on a working day by ${options[0].orderBy} leave the same day.`;
  return { cost, time };
}

export function faqTopics(delivery?: DeliverySettings): FaqTopic[] {
  const shipping = shippingAnswers(delivery);
  return FAQ.map((t) => t.id !== 'shipping' ? t : {
    ...t,
    items: t.items.map((i, n) => (n === 0 ? { ...i, a: [shipping.cost, ...i.a.slice(1)] } : n === 1 ? { ...i, a: [shipping.time] } : i)),
  });
}

const FAQ: FaqTopic[] = [
  {
    id: 'shipping',
    title: 'Shipping',
    items: [
      {
        q: 'How much does shipping cost?',
        a: [
          '',
          'You see each price before you pay, and the total never changes after that.',
        ],
      },
      {
        q: 'How long does delivery take?',
        a: [''],
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
