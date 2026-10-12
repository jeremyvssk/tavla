// Contact form rules: each field says how to fix it, and the order number is optional but must look like one.
import { ContactForm, MESSAGE_MAX, MESSAGE_MIN, validateContact } from './contactRules';

const good: ContactForm = {
  name: 'Kadri Saar', email: 'kadri.saar@example.com', topic: 'order', orderNumber: '', message: 'Where is my parcel? It was due on Friday.',
};
const clean = (f: ContactForm) => Object.values(validateContact(f)).filter(Boolean);

describe('contact rules', () => {
  it('passes a complete message, with or without an order number', () => {
    expect(clean(good)).toEqual([]);
    expect(clean({ ...good, orderNumber: 'TV-24851' })).toEqual([]);
  });

  it('names every missing field', () => {
    expect(validateContact({ name: ' ', email: '', topic: '', orderNumber: '', message: '' })).toMatchObject({
      name: 'Enter your name, so we know how to greet you.',
      topic: 'Choose a topic, so the right person answers.',
      message: 'Write your message.',
    });
    expect(validateContact({ ...good, email: '' }).email).toBeDefined();
  });

  it('says what a good email looks like', () => {
    expect(validateContact({ ...good, email: 'kadri@' }).email).toBe('Enter an email like name@example.com.');
  });

  it('takes an order number in any case, and refuses one with the wrong shape', () => {
    expect(validateContact({ ...good, orderNumber: ' tv-24851 ' }).orderNumber).toBeUndefined();
    for (const bad of ['24851', 'TV-2485', 'TV-248510', 'TV 24851']) {
      expect(validateContact({ ...good, orderNumber: bad }).orderNumber).toMatch(/^Order numbers are TV- and 5 digits/);
    }
  });

  it('wants a message between the limits, counted without the spaces around it', () => {
    expect(validateContact({ ...good, message: 'x'.repeat(MESSAGE_MIN - 1) }).message).toBe(`Tell us a little more: at least ${MESSAGE_MIN} characters.`);
    expect(validateContact({ ...good, message: `  ${'x'.repeat(MESSAGE_MIN)}  ` }).message).toBeUndefined();
    expect(validateContact({ ...good, message: 'x'.repeat(MESSAGE_MAX) }).message).toBeUndefined();
    expect(validateContact({ ...good, message: 'x'.repeat(MESSAGE_MAX + 1) }).message).toBe(`Keep it under ${MESSAGE_MAX} characters.`);
  });

  it('refuses a topic that is not on the list', () => {
    expect(validateContact({ ...good, topic: 'refund-please' }).topic).toBeDefined();
  });
});
