import { Injectable, Provider } from '@angular/core';
import {
  DateAdapter,
  MAT_DATE_LOCALE,
  MAT_NATIVE_DATE_FORMATS,
  MatDateFormats,
  NativeDateAdapter,
  provideNativeDateAdapter,
} from '@angular/material/core';

/**
 * Dates typed as dd/mm/yyyy, as in the UX mockup. NativeDateAdapter displays dates in the
 * en-GB locale but parses typed text with Date.parse, which reads 01/10/2026 as 10 January.
 * This adapter parses day-first instead.
 *
 * A real build might prefer a library-backed adapter (e.g. date-fns or Luxon); this keeps the
 * POC free of extra dependencies.
 */
@Injectable()
export class UkNativeDateAdapter extends NativeDateAdapter {
  override parse(value: unknown, parseFormat?: unknown): Date | null {
    if (typeof value === 'string') {
      const match = /^\s*(\d{1,2})\/(\d{1,2})\/(\d{4})\s*$/.exec(value);
      if (match) {
        const [day, month, year] = [Number(match[1]), Number(match[2]), Number(match[3])];
        const date = new Date(year, month - 1, day);
        // Rejects impossible dates such as 31/02/2026 instead of rolling them over.
        const valid = date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
        return valid ? date : this.invalid();
      }
    }
    return super.parse(value, parseFormat);
  }
}

const UK_DATE_FORMATS: MatDateFormats = {
  ...MAT_NATIVE_DATE_FORMATS,
  display: {
    ...MAT_NATIVE_DATE_FORMATS.display,
    dateInput: { year: 'numeric', month: '2-digit', day: '2-digit' },
  },
};

export function provideUkDateAdapter(): Provider[] {
  return [
    provideNativeDateAdapter(UK_DATE_FORMATS),
    { provide: DateAdapter, useClass: UkNativeDateAdapter },
    { provide: MAT_DATE_LOCALE, useValue: 'en-GB' },
  ];
}
