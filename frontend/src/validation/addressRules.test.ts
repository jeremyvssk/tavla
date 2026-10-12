// Address book rules: the checkout's rules for each field, minus the email, plus a short name.
import { AddressForm, LABEL_MAX, validateAddress } from './addressRules';

const good: AddressForm = {
  label: 'Home', phone: '+372 5123 4567', firstName: 'Kadri', lastName: 'Saar',
  street: 'Narva mnt 7', flat: '', postcode: '10117', city: 'Tallinn', country: 'EE',
};

describe('address rules', () => {
  it('passes a complete address, and never asks for an email', () => {
    const errors = validateAddress(good);
    expect(Object.values(errors).filter(Boolean)).toEqual([]);
    expect('email' in errors).toBe(false);
  });

  it('wants a short name for it', () => {
    expect(validateAddress({ ...good, label: '  ' }).label).toBe('Give it a name, like Home or Work.');
    expect(validateAddress({ ...good, label: 'x'.repeat(LABEL_MAX) }).label).toBeUndefined();
    expect(validateAddress({ ...good, label: 'x'.repeat(LABEL_MAX + 1) }).label).toBe(`Keep it under ${LABEL_MAX} characters.`);
  });

  it('checks the rest as the checkout does: house number, postcode against the city', () => {
    const errors = validateAddress({ ...good, street: 'Narva mnt', postcode: '50090' });
    expect(errors.street).toBe('Add the house number, like Narva mnt 7.');
    expect(errors.postcode).toBeDefined();
  });
});
