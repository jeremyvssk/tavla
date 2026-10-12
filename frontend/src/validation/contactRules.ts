// Client copy of the contact form's rules. UX only: the server validates the message again.
import { Errors, validateEmail } from './authRules';

export const TOPICS = [
  { value: 'order', label: 'An order I placed' },
  { value: 'return', label: 'Returning something' },
  { value: 'product', label: 'A product question' },
  { value: 'payment', label: 'Payment' },
  { value: 'other', label: 'Something else' },
] as const;

export const MESSAGE_MIN = 20;
export const MESSAGE_MAX = 2000;
export const CONTACT_NAME_MAX = 100;
// order numbers are TV- and five digits: TV-10001 is the first (orders.number)
const ORDER_NUMBER = /^TV-\d{5}$/;

export interface ContactForm {
  name: string;
  email: string;
  topic: string;
  orderNumber: string;
  message: string;
}

export type ContactField = keyof ContactForm;

export function validateContact(f: ContactForm): Errors<ContactField> {
  const errors: Errors<ContactField> = {};
  const name = f.name.trim();
  if (!name) errors.name = 'Enter your name, so we know how to greet you.';
  else if (name.length > CONTACT_NAME_MAX) errors.name = `Keep your name under ${CONTACT_NAME_MAX} characters.`;
  const email = validateEmail(f.email);
  if (email) errors.email = email === 'Enter a valid email address.' ? 'Enter an email like name@example.com.' : email;
  if (!TOPICS.some((t) => t.value === f.topic)) errors.topic = 'Choose a topic, so the right person answers.';
  const order = f.orderNumber.trim().toUpperCase();
  if (order && !ORDER_NUMBER.test(order)) {
    errors.orderNumber = "Order numbers are TV- and 5 digits, like TV-24851. Leave it empty if you don't have one.";
  }
  const message = f.message.trim();
  if (!message) errors.message = 'Write your message.';
  else if (message.length < MESSAGE_MIN) errors.message = `Tell us a little more: at least ${MESSAGE_MIN} characters.`;
  else if (message.length > MESSAGE_MAX) errors.message = `Keep it under ${MESSAGE_MAX} characters.`;
  return errors;
}
