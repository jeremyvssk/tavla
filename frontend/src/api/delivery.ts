// The delivery options and the free-shipping limit, as the server keeps them (staff edit them on the admin's Delivery page).
import { api } from './client';

export interface DeliveryOption {
  id: string;
  name: string;
  price: number;
  /** "15:00": orders placed before it on a working day leave the same day */
  orderBy: string;
  daysMin: number;
  daysMax: number;
  /** ISO codes, e.g. EE */
  countries: string[];
  /** shown at checkout; off hides it without deleting it */
  active: boolean;
  /** free once the order reaches the free-shipping limit */
  freeOverLimit: boolean;
}

export interface DeliverySettings {
  /** the one limit every page quotes ("Free delivery from €100") */
  freeShipping: { from: number; on: boolean };
  /** in the order shoppers see them */
  options: DeliveryOption[];
}

/** The checkout's list: active options only. */
export async function fetchDeliveryOptions(): Promise<DeliverySettings> {
  return (await api.get('/delivery-options')).data;
}
