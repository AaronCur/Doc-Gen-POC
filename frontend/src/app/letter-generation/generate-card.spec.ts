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
});
