// The summary arithmetic the checkout shows: the free-shipping threshold, VAT inside the total, delivery dates.
import { arrivalWindow, dispatchDay, SHIPPING, shippingCost, summary } from './shipping';

const standard = SHIPPING[0];
const express = SHIPPING[1];

describe('shipping', () => {
  it('makes standard free from €100 exactly, and never the other options', () => {
    expect(shippingCost('standard', 99.99)).toBe(4.9);
    expect(shippingCost('standard', 100)).toBe(0);
    expect(shippingCost('express', 500)).toBe(12.9);
    expect(shippingCost('locker', 500)).toBe(3.9);
  });

  it('counts VAT as a part of the total, not on top, without float drift', () => {
    // 2 × €24.00 + €65.00 = €113.00, free standard shipping: VAT is 113 × 24 / 124
    expect(summary(113, 'standard')).toEqual({ shipping: 0, total: 113, vat: 21.87 });
    expect(summary(0.1 + 0.2, 'locker')).toEqual({ shipping: 3.9, total: 4.2, vat: 0.81 });
  });

  it('ships the same working day before 15:00, else the next one', () => {
    const wedMorning = new Date(2026, 9, 7, 10, 0);
    const wedEvening = new Date(2026, 9, 7, 16, 0);
    const friEvening = new Date(2026, 9, 9, 16, 0);
    expect(dispatchDay(wedMorning).getDate()).toBe(7);
    expect(dispatchDay(wedEvening).getDate()).toBe(8);
    expect(dispatchDay(friEvening).getDate()).toBe(12);
  });

  it('writes the window the way the screens do, skipping weekends', () => {
    const wedMorning = new Date(2026, 9, 7, 10, 0);
    expect(arrivalWindow(standard, wedMorning)).toBe('Fri 9 – Tue 13 Oct');
    expect(arrivalWindow(express, wedMorning)).toBe('Thu 8 Oct');
  });
});
