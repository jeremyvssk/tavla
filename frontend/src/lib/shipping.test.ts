// The summary arithmetic the checkout shows: the free-shipping rule, VAT inside the total, delivery dates.
import { arrivalWindow, daysText, dispatchDay, shippingCost, summary, toFreeShipping } from './shipping';

const free = { from: 100, on: true };
const standard = { id: 'standard', name: 'Standard courier', price: 4.9, orderBy: '15:00', daysMin: 2, daysMax: 4, countries: ['EE'], active: true, freeOverLimit: true };
const express = { ...standard, id: 'express', name: 'Express courier', price: 12.9, daysMin: 1, daysMax: 1, freeOverLimit: false };

describe('shipping', () => {
  it('makes an option that takes part free from the limit exactly, and never the others', () => {
    expect(shippingCost(standard, 99.99, free)).toBe(4.9);
    expect(shippingCost(standard, 100, free)).toBe(0);
    expect(shippingCost(express, 500, free)).toBe(12.9);
    expect(shippingCost(standard, 500, { from: 100, on: false })).toBe(4.9);
  });

  it('counts VAT as a part of the total, not on top, without float drift', () => {
    // 2 × €24.00 + €65.00 = €113.00, free shipping: VAT is 113 × 24 / 124
    expect(summary(113, 0)).toEqual({ shipping: 0, total: 113, vat: 21.87 });
    expect(summary(0.1 + 0.2, 3.9)).toEqual({ shipping: 3.9, total: 4.2, vat: 0.81 });
  });

  it('says how far an order is from free delivery, and nothing when no option is ever free', () => {
    expect(toFreeShipping({ freeShipping: free, options: [standard] }, 70)).toEqual({ left: 30, from: 100 });
    expect(toFreeShipping({ freeShipping: free, options: [standard] }, 120)).toEqual({ left: 0, from: 100 });
    expect(toFreeShipping({ freeShipping: free, options: [express] }, 70)).toBeNull();
    expect(toFreeShipping(undefined, 70)).toBeNull();
  });

  it("ships the same working day before the option's cut-off, else the next one", () => {
    const wedMorning = new Date(2026, 9, 7, 10, 0);
    const wedEvening = new Date(2026, 9, 7, 16, 0);
    const friEvening = new Date(2026, 9, 9, 16, 0);
    expect(dispatchDay(wedMorning).getDate()).toBe(7);
    expect(dispatchDay(wedEvening).getDate()).toBe(8);
    expect(dispatchDay(friEvening).getDate()).toBe(12);
    expect(dispatchDay(new Date(2026, 9, 7, 10, 30), '10:30').getDate()).toBe(8);
  });

  it('writes the window the way the screens do, skipping weekends', () => {
    const wedMorning = new Date(2026, 9, 7, 10, 0);
    expect(arrivalWindow(standard, wedMorning)).toBe('Fri 9 – Tue 13 Oct');
    expect(arrivalWindow(express, wedMorning)).toBe('Thu 8 Oct');
    expect(daysText(standard)).toBe('2–4 working days');
    expect(daysText(express)).toBe('Next working day');
  });
});
