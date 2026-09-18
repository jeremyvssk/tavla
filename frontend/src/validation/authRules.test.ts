// The client copies of the DTO rules, checked at their boundaries so they can't drift from the server's.
import { validateEmail, validateLogin, validateNewPassword, validateRegister } from './authRules';

describe('validateNewPassword mirrors @Size(min = 8, max = 72)', () => {
  it.each([
    ['1234567', false],
    ['12345678', true],
    ['x'.repeat(72), true],
    ['x'.repeat(73), false],
  ])('%s -> valid %s', (password, valid) => {
    expect(validateNewPassword(password) === undefined).toBe(valid);
  });
});

describe('validateEmail', () => {
  it.each(['ada@shop.com', 'Ada.Lovelace+test@shop.example', '  ada@shop.com  '])('accepts %s', (email) => {
    expect(validateEmail(email)).toBeUndefined();
  });

  it.each(['', '   ', 'ada', 'ada@', '@shop.com', 'ada lovelace@shop.com'])('rejects "%s"', (email) => {
    expect(validateEmail(email)).toBeDefined();
  });

  it('rejects more than 255 characters, like the column', () => {
    expect(validateEmail(`${'a'.repeat(250)}@x.com`)).toBeDefined();
  });
});

describe('validateLogin', () => {
  it('does not apply the new-password length rule to an existing password', () => {
    expect(validateLogin({ email: 'ada@shop.com', password: 'short' })).toEqual({ email: undefined, password: undefined });
  });
});

describe('validateRegister', () => {
  it('flags a blank name and a confirmation that does not match', () => {
    const errors = validateRegister({ email: 'ada@shop.com', fullName: '   ', password: 'password123', confirmPassword: 'password124' });
    expect(errors.fullName).toBeDefined();
    expect(errors.confirmPassword).toBe('Passwords do not match.');
    expect(errors.email).toBeUndefined();
    expect(errors.password).toBeUndefined();
  });
});
