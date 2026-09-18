// Client-side copies of the auth DTO constraints. UX only: the server's @Valid is the real boundary.
//
// RegisterRequest:      email @NotBlank @Email @Size(max=255), password @Size(min=8,max=72), fullName @NotBlank @Size(max=255)
// ResetPasswordRequest: newPassword @Size(min=8,max=72)
// 72 is BCrypt's input limit; anything longer would be silently truncated before hashing.

export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 72;
export const NAME_MAX = 255;
export const EMAIL_MAX = 255;

export type Errors<K extends string> = Partial<Record<K, string>>;

export function validateEmail(email: string): string | undefined {
  const value = email.trim();
  if (!value) return 'Enter your email.';
  if (value.length > EMAIL_MAX) return `Email must be at most ${EMAIL_MAX} characters.`;
  // As loose as Hibernate's @Email: something@something, no spaces. Stricter rules reject real addresses.
  if (!/^[^\s@]+@[^\s@]+$/.test(value)) return 'Enter a valid email address.';
  return undefined;
}

export function validateNewPassword(password: string): string | undefined {
  if (password.length < PASSWORD_MIN) return `Password must be at least ${PASSWORD_MIN} characters.`;
  if (password.length > PASSWORD_MAX) return `Password must be at most ${PASSWORD_MAX} characters.`;
  return undefined;
}

export function validateLogin(values: { email: string; password: string }): Errors<'email' | 'password'> {
  return {
    email: validateEmail(values.email),
    // No length rule on login: an old account's password shouldn't be rejected by a newer policy.
    password: values.password ? undefined : 'Enter your password.',
  };
}

export function validateRegister(values: {
  email: string;
  fullName: string;
  password: string;
  confirmPassword: string;
}): Errors<'email' | 'fullName' | 'password' | 'confirmPassword'> {
  return {
    email: validateEmail(values.email),
    fullName: !values.fullName.trim()
      ? 'Enter your name.'
      : values.fullName.length > NAME_MAX
        ? `Name must be at most ${NAME_MAX} characters.`
        : undefined,
    password: validateNewPassword(values.password),
    confirmPassword: values.confirmPassword === values.password ? undefined : 'Passwords do not match.',
  };
}

export function validateReset(values: { password: string; confirmPassword: string }): Errors<'password' | 'confirmPassword'> {
  return {
    password: validateNewPassword(values.password),
    confirmPassword: values.confirmPassword === values.password ? undefined : 'Passwords do not match.',
  };
}

export function hasErrors(errors: Record<string, string | undefined>) {
  return Object.values(errors).some(Boolean);
}
