import { TestBed } from '@angular/core/testing';
import { DateAdapter } from '@angular/material/core';

import { provideUkDateAdapter, UkNativeDateAdapter } from './uk-date-adapter';

describe('UkNativeDateAdapter', () => {
  let adapter: DateAdapter<Date>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideUkDateAdapter()] });
    adapter = TestBed.inject(DateAdapter);
  });

  it('is the adapter in use', () => {
    expect(adapter).toBeInstanceOf(UkNativeDateAdapter);
  });

  it('parses dd/mm/yyyy day first', () => {
    const date = adapter.parse('01/10/2026', null)!;
    expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([2026, 9, 1]);
  });

  it('rejects impossible dates', () => {
    expect(adapter.isValid(adapter.parse('31/02/2026', null)!)).toBe(false);
  });

  it('displays dates as dd/mm/yyyy', () => {
    expect(adapter.format(new Date(2026, 9, 1), { year: 'numeric', month: '2-digit', day: '2-digit' })).toBe(
      '01/10/2026',
    );
  });
});
