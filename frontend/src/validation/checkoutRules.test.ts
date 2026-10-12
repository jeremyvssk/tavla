// Checkout rules: every missing field and bad format gets a message that says how to fix it.
import { BillingForm, CheckoutForm, cityMismatch, validateBilling, validateCheckout, validatePhone, validatePostcode } from './checkoutRules';

const good: CheckoutForm = {
  email: 'kadri.saar@example.com', phone: '+372 5123 4567', firstName: 'Kadri', lastName: 'Saar',
  street: 'Narva mnt 7', flat: '12', postcode: '10117', city: 'Tallinn', country: 'EE',
};

describe('checkout rules', () => {
  it('passes a complete Estonian address', () => {
    expect(Object.values(validateCheckout(good)).filter(Boolean)).toEqual([]);
  });

  it('names every missing field, and the flat stays optional', () => {
    const errors = validateCheckout({ ...good, email: ' ', phone: '', firstName: '', lastName: '', street: '', flat: '', postcode: '', city: '' });
    expect(errors).toMatchObject({
      email: 'Enter your email. The receipt goes there.',
      phone: "Enter a phone number. The courier calls it if they can't find you.",
      firstName: 'Enter your first name.',
      lastName: 'Enter your last name.',
      street: 'Enter the street and house number.',
      postcode: 'Enter the postcode.',
      city: 'Enter the city.',
    });
    expect(errors.flat).toBeUndefined();
  });

  it('says what a good email, phone and street look like', () => {
    const errors = validateCheckout({ ...good, email: 'mart.kask@', phone: '5876', street: 'Pärnu mnt' });
    expect(errors.email).toBe('Enter an email like name@example.com.');
    expect(errors.phone).toBe('Enter the full number with the country code, like +372 5123 4567.');
    expect(errors.street).toBe('Add the house number, like Narva mnt 7.');
  });

  it('wants the country code and enough digits in a phone number', () => {
    expect(validatePhone('+372 5123 4567')).toBeUndefined();
    expect(validatePhone('+3725123456')).toBeUndefined();
    expect(validatePhone('5123 4567')).toBeDefined();
    expect(validatePhone('+372 51')).toBeDefined();
    expect(validatePhone('+372 5123 4567 ext')).toBeDefined();
  });

  it('checks postcodes against their country', () => {
    expect(validatePostcode('1014', 'EE')).toBe('Estonian postcodes have 5 digits, like 10141.');
    expect(validatePostcode('10141', 'EE')).toBeUndefined();
    expect(validatePostcode('LV-1050', 'LV')).toBeUndefined();
    expect(validatePostcode('1050', 'LV')).toBeUndefined();
    expect(validatePostcode('10500', 'LV')).toBe('Latvian postcodes have 4 digits, like LV-1050.');
    expect(validatePostcode('LT-01103', 'LT')).toBeUndefined();
    expect(validatePostcode('00100', 'FI')).toBeUndefined();
    expect(validatePostcode('FI-00100', 'FI')).toBe('Finnish postcodes have 5 digits, like 00100.');
  });

  it('catches a postcode from another city, for the cities it knows', () => {
    expect(validateCheckout({ ...good, postcode: '50090', city: 'Tallinn' }).postcode)
      .toBe('Postcodes in Tallinn start with 10 to 15, like 10141. Check the postcode, or the city.');
    expect(cityMismatch('EE', 'Tartu', '50090')).toBeUndefined();
    // accents and case don't matter, the LV- prefix is optional
    expect(cityMismatch('LV', ' RIGA ', 'LV-3401')).toBe('Postcodes in Rīga start with LV-10, like LV-1050. Check the postcode, or the city.');
    expect(cityMismatch('LV', 'Rīga', '1050')).toBeUndefined();
    expect(cityMismatch('EE', 'parnu', '80010')).toBeUndefined();
    expect(cityMismatch('LT', 'Vilnius', 'LT-44248')).toBeDefined();
    expect(cityMismatch('FI', 'Helsinki', '00100')).toBeUndefined();
  });

  it('leaves other cities, and a wrong format, to the format check alone', () => {
    expect(cityMismatch('EE', 'Kuressaare', '93815')).toBeUndefined();
    // Tallinn in Latvia isn't the Tallinn it knows
    expect(cityMismatch('LV', 'Tallinn', 'LV-3401')).toBeUndefined();
    expect(validatePostcode('1014', 'EE', 'Tallinn')).toBe('Estonian postcodes have 5 digits, like 10141.');
  });
});

describe('billing address rules', () => {
  const bill: BillingForm = { name: 'Kask Kirjastus OÜ', street: 'Küüni 7', postcode: '51004', city: 'Tartu', country: 'EE' };

  it('passes a complete invoice address', () => {
    expect(Object.values(validateBilling(bill)).filter(Boolean)).toEqual([]);
  });

  it('names every missing field', () => {
    expect(validateBilling({ ...bill, name: '', street: ' ', postcode: '', city: '' })).toMatchObject({
      name: 'Enter the name or company the invoice is for.',
      street: 'Enter the street and house number.',
      postcode: 'Enter the postcode.',
      city: 'Enter the city.',
    });
  });

  it('checks the postcode against the country and the city, as the delivery address does', () => {
    expect(validateBilling({ ...bill, postcode: '5100' }).postcode).toBe('Estonian postcodes have 5 digits, like 10141.');
    expect(validateBilling({ ...bill, postcode: '10117' }).postcode).toBeDefined();
  });
});
