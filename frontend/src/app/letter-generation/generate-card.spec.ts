import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';

import { GenerateCard } from './generate-card';

describe('GenerateCard', () => {
  let fixture: ComponentFixture<GenerateCard>;
  let card: GenerateCard;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GenerateCard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNativeDateAdapter()],
    }).compileComponents();

    fixture = TestBed.createComponent(GenerateCard);
    card = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('letterTypes', [
      { code: 'LETTER_A', name: 'Letter type A' },
      { code: 'LETTER_B', name: 'Letter type B' },
    ]);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function generateButton(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('button[type="submit"]');
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  async function selectType(code: string, eligibleCount: number) {
    card.form.controls.letterType.setValue(code);
    http.expectOne(`/api/letter-types/${code}/eligible-count`).flush({ code, eligibleCount });
    await fixture.whenStable();
  }

  async function setDate() {
    card.form.controls.claimSubmittedDate.setValue(new Date(2026, 1, 1));
    await fixture.whenStable();
  }

  it('is disabled until both letter type and date are chosen', async () => {
    expect(generateButton().disabled).toBe(true);

    await selectType('LETTER_A', 5);
    expect(generateButton().disabled).toBe(true);

    await setDate();
    expect(generateButton().disabled).toBe(false);
  });

  it('is disabled with an error when no applications are eligible', async () => {
    await setDate();
    await selectType('LETTER_A', 0);

    expect(generateButton().disabled).toBe(true);
    expect(text()).toContain('No new applications are awaiting this letter type.');
  });

  it('is disabled with an error while the type has an active run', async () => {
    fixture.componentRef.setInput('activeLetterTypes', new Set(['LETTER_A']));
    await setDate();
    await selectType('LETTER_A', 5);

    expect(generateButton().disabled).toBe(true);
    expect(text()).toContain('A run for this letter type is still generating.');
  });

  it('is re-enabled when the active run for the type finishes', async () => {
    fixture.componentRef.setInput('activeLetterTypes', new Set(['LETTER_A']));
    await setDate();
    await selectType('LETTER_A', 5);
    expect(generateButton().disabled).toBe(true);

    fixture.componentRef.setInput('activeLetterTypes', new Set());
    await fixture.whenStable();

    expect(generateButton().disabled).toBe(false);
  });

  it('notes that previous runs exist for the type', async () => {
    fixture.componentRef.setInput('letterTypesWithRuns', new Set(['LETTER_A']));
    await selectType('LETTER_A', 5);

    expect(text()).toContain('Previous runs exist for this type.');
  });

  it('an active run for another type does not block', async () => {
    fixture.componentRef.setInput('activeLetterTypes', new Set(['LETTER_B']));
    await setDate();
    await selectType('LETTER_A', 5);

    expect(generateButton().disabled).toBe(false);
  });

  function button(label: string): HTMLButtonElement | undefined {
    return Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === label,
    );
  }

  it('shows that the eligible count is loading', async () => {
    card.form.controls.letterType.setValue('LETTER_A');
    await fixture.whenStable();
    expect(text()).toContain('Checking eligible applications');

    http.expectOne('/api/letter-types/LETTER_A/eligible-count').flush({ code: 'LETTER_A', eligibleCount: 5 });
    await fixture.whenStable();
    expect(text()).not.toContain('Checking eligible applications');
  });

  it('explains a failed eligible count under the field and retries it', async () => {
    await setDate();
    card.form.controls.letterType.setValue('LETTER_A');
    http
      .expectOne('/api/letter-types/LETTER_A/eligible-count')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });
    await fixture.whenStable();

    expect(generateButton().disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('mat-error')?.textContent).toContain(
      "Couldn't check how many applications are eligible.",
    );

    button('Retry check')!.click();
    http.expectOne('/api/letter-types/LETTER_A/eligible-count').flush({ code: 'LETTER_A', eligibleCount: 5 });
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('mat-error')).toBeNull();
    expect(button('Retry check')).toBeUndefined();
    expect(generateButton().disabled).toBe(false);
  });

  it('shows a letter types load failure with a retry', async () => {
    fixture.componentRef.setInput('letterTypesError', 'Could not reach the server. Please try again.');
    await fixture.whenStable();
    let retried = false;
    card.retryLetterTypes.subscribe(() => (retried = true));

    const alert = fixture.nativeElement.querySelector('[role="alert"]') as HTMLElement;
    expect(alert.textContent).toContain("Couldn't load letter types. Could not reach the server.");
    button('Retry')!.click();

    expect(retried).toBe(true);
  });

  it('titles the card with a heading', () => {
    expect(fixture.nativeElement.querySelector('h2')?.textContent).toBe('Generate letters');
  });
});
