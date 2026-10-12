// Checkout form rules and their messages. UX only: the server checks the same fields when the order is placed.
//
// Every message says how to fix the field, not just that it is wrong. Postcodes are checked for their
// country's format, and for the biggest cities, for belonging to the city. Whether a postcode belongs to
// a street would need a postal lookup service, which this project doesn't use.
import { EMAIL_MAX, Errors } from './authRules';

export const COUNTRIES = [
  { code: 'EE', name: 'Estonia' },
  { code: 'LV', name: 'Latvia' },
  { code: 'LT', name: 'Lithuania' },
  { code: 'FI', name: 'Finland' },
] as const;

export type CountryCode = (typeof COUNTRIES)[number]['code'];

export interface CheckoutForm {
  email: string;
  phone: string;
  firstName: string;
  lastName: string;
  street: string;
  flat: string;
  postcode: string;
  city: string;
  country: CountryCode;
}

export type CheckoutField = keyof CheckoutForm;

export const FIELD_MAX = 100;

const POSTCODES: Record<CountryCode, { pattern: RegExp; message: string }> = {
  EE: { pattern: /^\d{5}$/, message: 'Estonian postcodes have 5 digits, like 10141.' },
  LV: { pattern: /^(LV-?)?\d{4}$/i, message: 'Latvian postcodes have 4 digits, like LV-1050.' },
  LT: { pattern: /^(LT-?)?\d{5}$/i, message: 'Lithuanian postcodes have 5 digits, like LT-01103.' },
  FI: { pattern: /^\d{5}$/, message: 'Finnish postcodes have 5 digits, like 00100.' },
};

/**
 * For the biggest cities, the first digits every postcode there shares, so "Tallinn 50090" (a Tartu
 * postcode) is caught as a typo. Only cities whose postcodes all start the same way are listed, so a
 * real address is never refused; any other city gets the format check only. The server checks the
 * same list (backend CheckoutRules.java). `digits` is matched without the LV-/LT- prefix.
 */
const CITIES: { country: CountryCode; name: string; digits: RegExp; starts: string; example: string }[] = [
  { country: 'EE', name: 'Tallinn', digits: /^1[0-5]\d{3}$/, starts: '10 to 15', example: '10141' },
  { country: 'EE', name: 'Tartu', digits: /^5[01]\d{3}$/, starts: '50 or 51', example: '50090' },
  { country: 'EE', name: 'Narva', digits: /^2[01]\d{3}$/, starts: '20 or 21', example: '20308' },
  { country: 'EE', name: 'Pärnu', digits: /^80\d{3}$/, starts: '80', example: '80010' },
  { country: 'LV', name: 'Rīga', digits: /^10\d{2}$/, starts: 'LV-10', example: 'LV-1050' },
  { country: 'LV', name: 'Daugavpils', digits: /^54\d{2}$/, starts: 'LV-54', example: 'LV-5401' },
  { country: 'LV', name: 'Liepāja', digits: /^34\d{2}$/, starts: 'LV-34', example: 'LV-3401' },
  { country: 'LV', name: 'Jelgava', digits: /^30\d{2}$/, starts: 'LV-30', example: 'LV-3001' },
  { country: 'LT', name: 'Vilnius', digits: /^(0[1-9]|1[0-4])\d{3}$/, starts: 'LT-01 to LT-14', example: 'LT-01103' },
  { country: 'LT', name: 'Kaunas', digits: /^(4[4-9]|5[0-2])\d{3}$/, starts: 'LT-44 to LT-52', example: 'LT-44248' },
  { country: 'FI', name: 'Helsinki', digits: /^00\d{3}$/, starts: '00', example: '00100' },
  { country: 'FI', name: 'Espoo', digits: /^02\d{3}$/, starts: '02', example: '02100' },
  { country: 'FI', name: 'Vantaa', digits: /^01\d{3}$/, starts: '01', example: '01300' },
  { country: 'FI', name: 'Tampere', digits: /^33\d{3}$/, starts: '33', example: '33100' },
  { country: 'FI', name: 'Turku', digits: /^20\d{3}$/, starts: '20', example: '20100' },
  { country: 'FI', name: 'Oulu', digits: /^90\d{3}$/, starts: '90', example: '90100' },
];

/** "  RĪGA " and "riga" are the same city: lower case, no accents, no spaces around. */
const fold = (value: string) => value.trim().normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();

/** A message when the city is one of the listed ones and the postcode can't be in it. */
export function cityMismatch(country: CountryCode, city: string, postcode: string) {
  const digits = postcode.trim().toUpperCase().replace(/^(LV|LT)-?/, '');
  const known = CITIES.find((c) => c.country === country && fold(c.name) === fold(city));
  return known && !known.digits.test(digits)
    ? `Postcodes in ${known.name} start with ${known.starts}, like ${known.example}. Check the postcode, or the city.`
    : undefined;
}

/** As loose as the sign-up form's rule (Hibernate's @Email): something@something, no spaces. */
export function validateContactEmail(email: string) {
  const value = email.trim();
  if (!value) return 'Enter your email. The receipt goes there.';
  if (value.length > EMAIL_MAX) return `Email must be at most ${EMAIL_MAX} characters.`;
  return /^[^\s@]+@[^\s@]+$/.test(value) ? undefined : 'Enter an email like name@example.com.';
}

/** A plus, a country code, and 7 to 14 more digits; spaces are allowed between them. */
export function validatePhone(phone: string) {
  const value = phone.trim();
  if (!value) return 'Enter a phone number. The courier calls it if they can\'t find you.';
  if (!/^\+\d[\d\s]{6,17}\d$/.test(value) || value.replace(/\D/g, '').length < 8) {
    return 'Enter the full number with the country code, like +372 5123 4567.';
  }
  return undefined;
}

export function validatePostcode(postcode: string, country: CountryCode, city = '') {
  const value = postcode.trim();
  if (!value) return 'Enter the postcode.';
  if (!POSTCODES[country].pattern.test(value)) return POSTCODES[country].message;
  return cityMismatch(country, city, value);
}

function required(value: string, message: string) {
  const v = value.trim();
  if (!v) return message;
  return v.length > FIELD_MAX ? `Keep it under ${FIELD_MAX} characters.` : undefined;
}

export function validateCheckout(f: CheckoutForm): Errors<CheckoutField> {
  return {
    email: validateContactEmail(f.email),
    phone: validatePhone(f.phone),
    firstName: required(f.firstName, 'Enter your first name.'),
    lastName: required(f.lastName, 'Enter your last name.'),
    street: required(f.street, 'Enter the street and house number.')
      ?? (/\d/.test(f.street) ? undefined : 'Add the house number, like Narva mnt 7.'),
    flat: f.flat.trim().length > FIELD_MAX ? `Keep it under ${FIELD_MAX} characters.` : undefined,
    postcode: validatePostcode(f.postcode, f.country, f.city),
    city: required(f.city, 'Enter the city.'),
    country: COUNTRIES.some((c) => c.code === f.country) ? undefined : 'Choose a country we deliver to.',
  };
}

/** The names the error summary links to, in the order the fields appear on the page. */
export const FIELD_LABELS: Record<CheckoutField, string> = {
  email: 'Email',
  phone: 'Phone',
  firstName: 'First name',
  lastName: 'Last name',
  street: 'Street',
  flat: 'Flat',
  postcode: 'Postcode',
  city: 'City',
  country: 'Country',
};

/** An invoice address when it differs from the delivery one: a person or a company, and where it is. */
export interface BillingForm {
  name: string;
  street: string;
  postcode: string;
  city: string;
  country: CountryCode;
}
export type BillingField = keyof BillingForm;

export function validateBilling(b: BillingForm): Errors<BillingField> {
  return {
    name: required(b.name, 'Enter the name or company the invoice is for.'),
    street: required(b.street, 'Enter the street and house number.'),
    postcode: validatePostcode(b.postcode, b.country, b.city),
    city: required(b.city, 'Enter the city.'),
  };
}
