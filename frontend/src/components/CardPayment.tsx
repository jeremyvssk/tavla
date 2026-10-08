// Stripe's card form (the Payment Element) and the two steps of paying with it: check the card, then confirm.
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js';
import type { StripeElementsOptions } from '@stripe/stripe-js';
import { forwardRef, useImperativeHandle } from 'react';
import { getStripe, stripeConfigured } from '../lib/stripe';
import Icon from './Icon';

/**
 * What the page holding the form can ask of it. Both return a sentence for the shopper when something
 * went wrong, or null when it went through.
 */
export interface CardPaymentHandle {
  /** Stripe checks the number, expiry and CVC inside its own frame and marks what is wrong there. */
  check(): Promise<string | null>;
  /** Confirms the order's PaymentIntent with the card typed in. 3-D Secure opens in Stripe's own frame. */
  confirm(clientSecret: string, orderId: string): Promise<string | null>;
}

const NOT_SET_UP = "Card payments aren't set up on this copy of the shop: the Stripe test keys are missing from .env.";

// The shop's walnut and Figtree inside Stripe's frame, which our own CSS can't reach.
const APPEARANCE: StripeElementsOptions['appearance'] = {
  theme: 'stripe',
  variables: { colorPrimary: '#5B3A28', colorText: '#1F1713', colorDanger: '#B2361F', borderRadius: '10px', fontFamily: 'Figtree, system-ui, sans-serif' },
};
const FONTS: StripeElementsOptions['fonts'] = [{ cssSrc: 'https://fonts.googleapis.com/css2?family=Figtree:wght@400;600&display=swap' }];

/**
 * "Deferred" mode: the form shows before the order exists, and the order and its PaymentIntent are
 * made only when Pay is pressed. The amount here is for Stripe's own checks; the server's order total
 * is what gets charged. Card details go from Stripe's frame straight to Stripe and never touch our server.
 */
const CardPayment = forwardRef<CardPaymentHandle, { amount: number }>(function CardPayment({ amount }, ref) {
  const options: StripeElementsOptions = {
    mode: 'payment',
    amount: Math.max(1, Math.round(amount * 100)),
    currency: 'eur',
    paymentMethodTypes: ['card'],
    appearance: APPEARANCE,
    fonts: FONTS,
  };
  return (
    <Elements stripe={stripeConfigured ? getStripe() : null} options={options}>
      <CardFields ref={ref} />
    </Elements>
  );
});

export default CardPayment;

const CardFields = forwardRef<CardPaymentHandle>(function CardFields(_, ref) {
  const stripe = useStripe();
  const elements = useElements();

  useImperativeHandle(ref, () => ({
    async check() {
      if (!stripeConfigured) return NOT_SET_UP;
      if (!stripe || !elements) return 'The card form is still loading. Wait a second and press Pay again.';
      const { error } = await elements.submit();
      return error ? (error.message ?? 'Check your card details.') : null;
    },
    async confirm(clientSecret, orderId) {
      if (!stripe || !elements) return 'The card form is still loading. Wait a second and press Pay again.';
      const { error } = await stripe.confirmPayment({
        elements,
        clientSecret,
        // only used if a bank insists on a full-page redirect; cards normally stay on this page
        confirmParams: { return_url: `${window.location.origin}/checkout/${encodeURIComponent(orderId)}` },
        redirect: 'if_required',
      });
      return error ? (error.message ?? 'The payment was declined.') : null;
    },
  }), [stripe, elements]);

  if (!stripeConfigured) {
    return (
      <div className="pe pe--off">
        <Icon name="card" />
        <p>{NOT_SET_UP} The README's setup section says where to get them.</p>
      </div>
    );
  }
  return <div className="pe"><PaymentElement options={{ layout: 'tabs' }} /></div>;
});
