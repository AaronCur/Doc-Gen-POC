import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ActionSelectCard } from './action-select-card';
import { AdminSelection, GENERATE_DECISION_LETTER } from './admin-actions';

const PLAN_TYPES = [
  { id: 'PT-1', name: '[Plan type 1]' },
  { id: 'PT-2', name: '[Plan type 2]' },
];
const PT1_PLANS = [
  { id: 'PLAN-1A', planTypeId: 'PT-1', name: '[Plan 1A]' },
  { id: 'PLAN-1B', planTypeId: 'PT-1', name: '[Plan 1B]' },
];
const PT2_PLANS = [{ id: 'PLAN-2A', planTypeId: 'PT-2', name: '[Plan 2A]' }];

describe('ActionSelectCard', () => {
  let fixture: ComponentFixture<ActionSelectCard>;
  let card: ActionSelectCard;
  let http: HttpTestingController;
  let selections: AdminSelection[];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActionSelectCard],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ActionSelectCard);
    card = fixture.componentInstance;
    selections = [];
    card.selectionChange.subscribe((selection) => selections.push(selection));
    fixture.detectChanges(); // ngOnInit
    http.expectOne('/api/plan-types').flush(PLAN_TYPES);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  function lastSelection(): AdminSelection | undefined {
    return selections.at(-1);
  }

  async function choosePlanType(id: string, plans: unknown[]) {
    card.planTypeId.setValue(id);
    http.expectOne(`/api/plan-types/${id}/plans`).flush(plans);
    await fixture.whenStable();
  }

  it('enables only the plan type select at first', () => {
    expect(card.planTypeId.enabled).toBe(true);
    expect(card.planId.disabled).toBe(true);
    expect(card.actionId.disabled).toBe(true);
  });

  it('loads the plans for the chosen plan type and enables Plan, then Action', async () => {
    await choosePlanType('PT-1', PT1_PLANS);
    expect(card.planId.enabled).toBe(true);
    expect(card.actionId.disabled).toBe(true);

    card.planId.setValue('PLAN-1A');
    expect(card.actionId.enabled).toBe(true);

    card.actionId.setValue(GENERATE_DECISION_LETTER);
    expect(lastSelection()).toEqual({
      plan: PT1_PLANS[0],
      action: expect.objectContaining({ id: GENERATE_DECISION_LETTER, name: 'Generate decision letter' }),
    });
  });

  it('clears the plan and action when the plan type changes', async () => {
    await choosePlanType('PT-1', PT1_PLANS);
    card.planId.setValue('PLAN-1A');
    card.actionId.setValue(GENERATE_DECISION_LETTER);

    await choosePlanType('PT-2', PT2_PLANS);

    expect(card.planId.value).toBeNull();
    expect(card.planId.enabled).toBe(true);
    expect(card.actionId.value).toBeNull();
    expect(card.actionId.disabled).toBe(true);
    expect(lastSelection()).toEqual({ plan: null, action: null });
  });

  it('clears the action when the plan changes', async () => {
    await choosePlanType('PT-1', PT1_PLANS);
    card.planId.setValue('PLAN-1A');
    card.actionId.setValue(GENERATE_DECISION_LETTER);

    card.planId.setValue('PLAN-1B');

    expect(card.actionId.value).toBeNull();
    expect(card.actionId.enabled).toBe(true);
    expect(lastSelection()).toEqual({ plan: PT1_PLANS[1], action: null });
  });

  it('shows a failed plans load with a retry', async () => {
    card.planTypeId.setValue('PT-1');
    http.expectOne('/api/plan-types/PT-1/plans').flush(null, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();

    const alert = fixture.nativeElement.querySelector('[role="alert"]') as HTMLElement;
    expect(alert.textContent).toContain("Couldn't load plans.");

    alert.querySelector('button')!.click();
    http.expectOne('/api/plan-types/PT-1/plans').flush(PT1_PLANS);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });
});
