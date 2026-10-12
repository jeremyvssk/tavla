// Typed calls for the signed-in shopper's own account. PLANNED: none of these endpoints exist yet; this is the shape the pages expect.
import type { CountryCode } from '../validation/checkoutRules';
import { api } from './client';

export interface Profile {
  fullName: string;
  email: string;
  phone: string | null;
  emailVerified: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Address {
  id: string;
  /** what the shopper calls it: Home, Work */
  label: string;
  firstName: string;
  lastName: string;
  street: string;
  flat: string | null;
  postcode: string;
  city: string;
  country: CountryCode;
  phone: string;
  defaultDelivery: boolean;
  defaultBilling: boolean;
}

export type AddressInput = Omit<Address, 'id' | 'defaultDelivery' | 'defaultBilling'>;

/** GET /users/me */
export async function fetchProfile(): Promise<Profile> {
  return (await api.get('/users/me')).data;
}

/** PATCH /users/me: name and phone only. The sign-in email can't be changed. */
export async function updateProfile(changes: { fullName: string; phone: string | null }): Promise<Profile> {
  return (await api.patch('/users/me', changes)).data;
}

/** GET /users/me/export: everything kept about the shopper as one JSON file (GDPR access and portability). */
export async function exportMyData(): Promise<Blob> {
  return (await api.get('/users/me/export', { responseType: 'blob' })).data;
}

/** DELETE /users/me with the password: removes the account, addresses, cart and reviews; orders stay, unlinked. */
export async function deleteAccount(password: string) {
  await api.delete('/users/me', { data: { password } });
}

/** GET /users/me/addresses: at most 10. */
export async function fetchAddresses(): Promise<Address[]> {
  return (await api.get('/users/me/addresses')).data;
}

export async function createAddress(input: AddressInput): Promise<Address[]> {
  return (await api.post('/users/me/addresses', input)).data;
}

export async function updateAddress(id: string, input: AddressInput): Promise<Address[]> {
  return (await api.put(`/users/me/addresses/${encodeURIComponent(id)}`, input)).data;
}

/** The default delivery address can't be removed; the server answers 409 address_is_default. */
export async function removeAddress(id: string): Promise<Address[]> {
  return (await api.delete(`/users/me/addresses/${encodeURIComponent(id)}`)).data;
}

/** PUT /users/me/addresses/{id}/default: one default delivery and one default billing address. */
export async function makeDefault(id: string, kind: 'delivery' | 'billing'): Promise<Address[]> {
  return (await api.put(`/users/me/addresses/${encodeURIComponent(id)}/default`, { kind })).data;
}
