// Typed call for the contact form.
import { api } from './client';
import type { ContactForm } from '../validation/contactRules';

/** POST /support/messages: emailed to the shop's inbox through the queue, with the sender as reply-to; nothing is stored. 202, no body. */
export async function sendContactMessage(form: ContactForm & { captchaToken?: string }) {
  await api.post('/support/messages', {
    ...form,
    orderNumber: form.orderNumber.trim().toUpperCase() || null,
  });
}
