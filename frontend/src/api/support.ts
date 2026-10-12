// Typed call for the contact form. PLANNED: the backend endpoint does not exist yet; this is the shape the page expects.
import { api } from './client';
import type { ContactForm } from '../validation/contactRules';

/** POST /support/messages: saved for staff (admin Messages) and emailed to the shop through the queue. 201, no body. */
export async function sendContactMessage(form: ContactForm & { captchaToken?: string }) {
  await api.post('/support/messages', {
    ...form,
    orderNumber: form.orderNumber.trim().toUpperCase() || null,
  });
}
