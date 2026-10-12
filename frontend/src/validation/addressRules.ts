// A saved address follows the checkout's rules, plus a short name for it ("Home", "Work").
import type { Errors } from './authRules';
import { CheckoutForm, validateCheckout } from './checkoutRules';

export type AddressForm = Omit<CheckoutForm, 'email'> & { label: string };
export type AddressField = keyof AddressForm;
export const LABEL_MAX = 40;
export const ADDRESS_LIMIT = 10;

export function validateAddress(f: AddressForm): Errors<AddressField> {
  // the checkout's rules for every field but the email, which an address doesn't have
  const { email: _ignored, ...rest } = validateCheckout({ ...f, email: 'ignored@example.com' });
  const label = f.label.trim();
  return {
    label: !label ? 'Give it a name, like Home or Work.' : label.length > LABEL_MAX ? `Keep it under ${LABEL_MAX} characters.` : undefined,
    ...rest,
  };
}
