import { computed, Injectable, signal } from '@angular/core';

/**
 * POC ONLY. The mock users case-service knows (see SecurityConfig), switched from the toolbar.
 * In the real app, the identity provider supplies the user and roles; delete this file, the
 * switcher and basicAuthInterceptor.
 */
export type DevUserName = 'admin' | 'staff' | 'external';
export type Role = 'ADMIN' | 'STAFF' | 'EXTERNAL';

const ROLES: Record<DevUserName, readonly Role[]> = {
  admin: ['ADMIN', 'STAFF'],
  staff: ['STAFF'],
  external: ['EXTERNAL'],
};

@Injectable({ providedIn: 'root' })
export class DevUser {
  readonly names: readonly DevUserName[] = ['admin', 'staff', 'external'];

  readonly name = signal<DevUserName>('admin');
  readonly roles = computed(() => ROLES[this.name()]);

  // Role checks only shape the UI. case-service checks every request itself.
  readonly isAdmin = computed(() => this.roles().includes('ADMIN'));
  readonly isStaff = computed(() => this.roles().includes('STAFF'));
  readonly isExternal = computed(() => this.roles().includes('EXTERNAL'));

  /** The role shown in the header pill: the user's highest role. */
  readonly roleLabel = computed(() => (this.isAdmin() ? 'Admin' : this.isStaff() ? 'Staff' : 'External'));

  /** HTTP Basic credentials. The mock users' passwords equal their names. */
  authorizationHeader(): string {
    return `Basic ${btoa(`${this.name()}:${this.name()}`)}`;
  }
}
