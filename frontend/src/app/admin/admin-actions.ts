import { Plan } from '../api/models';

export interface AdminAction {
  id: string;
  name: string;
}

/** The action that shows the letter generation section. */
export const GENERATE_DECISION_LETTER = 'GENERATE_DECISION_LETTER';

/**
 * STAND-IN: the real admin screen supplies its own action list. Only "Generate decision letter"
 * is implemented in the POC; the other two are placeholders.
 */
export const ADMIN_ACTIONS: readonly AdminAction[] = [
  { id: GENERATE_DECISION_LETTER, name: 'Generate decision letter' },
  { id: 'PLACEHOLDER_ACTION_1', name: '[Placeholder action 1]' },
  { id: 'PLACEHOLDER_ACTION_2', name: '[Placeholder action 2]' },
];

/** What the "Select an action" card has chosen so far. Each part needs the one before it. */
export interface AdminSelection {
  plan: Plan | null;
  action: AdminAction | null;
}
