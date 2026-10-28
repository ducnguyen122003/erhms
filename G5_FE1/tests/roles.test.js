import test from 'node:test';
import assert from 'node:assert/strict';
import {dashboardPath, dashboardRoles, resolveRole, roleError, rolesById, workspaceRoute} from '../src/roles.js';

const users = Object.entries(rolesById).map(([roleId, role]) => ({roleId: Number(roleId), role}));

function assertUnsupported(user) {
 assert.equal(resolveRole(user), null);
 assert.notEqual(roleError(user), '');
 assert.equal(dashboardPath(user), '/dashboard');
 for (const path of ['/', '/login', '/dashboard', '/profile', '/recruitment', '/admin/users', ...users.map(dashboardPath)]) {
  assert.equal(workspaceRoute(path, user), 'unsupported');
 }
}

test('the seven configured role IDs match the database table exactly', () => {
 assert.deepEqual(rolesById, {1: 'ADMIN', 2: 'LECTURER', 3: 'STUDENT', 4: 'ACCOUNTANT', 5: 'IT_SUPPORT', 6: 'MANAGER', 7: 'HR'});
 assert.equal(Object.keys(dashboardRoles).length, 7);
});

test('all seven roles resolve to their own dashboard after login and reload', () => {
 for (const user of users) {
  assert.equal(resolveRole(user), user.role);
  assert.equal(roleError(user), '');
  assert.equal(dashboardPath(user), '/dashboard/' + dashboardRoles[user.role].slug);
  assert.equal(workspaceRoute('/login', user), 'redirect');
  assert.equal(workspaceRoute('/', user), 'redirect');
  assert.equal(workspaceRoute('/dashboard', user), 'redirect');
  assert.equal(workspaceRoute(dashboardPath(user), user), 'dashboard');
 }
});

test('every role rejects all six other dashboard URLs', () => {
 let denied = 0;
 for (const user of users) {
  for (const other of users) {
   if (user.roleId !== other.roleId) {
    assert.equal(workspaceRoute(dashboardPath(other), user), 'forbidden');
    denied++;
   }
  }
 }
 assert.equal(denied, 42);
});

test('only HR and manager enter the existing recruitment flow', () => {
 for (const user of users) {
  assert.equal(workspaceRoute('/recruitment', user), [6, 7].includes(user.roleId) ? 'recruitment' : 'forbidden');
  assert.equal(workspaceRoute('/profile', user), 'profile');
 }
 assert.equal(workspaceRoute('/unknown', users[2]), 'missing');
 assert.equal(workspaceRoute('/dashboard/unknown', users[0]), 'forbidden');
});

test('only the canonical admin role can enter user account management', () => {
 for (const user of users) {
  assert.equal(workspaceRoute('/admin/users', user), user.roleId === 1 ? 'users' : 'forbidden');
 }
 assert.equal(workspaceRoute('/admin/users', {roleId: 8, role: 'ADMIN'}), 'unsupported');
 assert.equal(workspaceRoute('/admin/users', {roleId: 3, role: 'ADMIN'}), 'unsupported');
 assert.equal(workspaceRoute('/admin/users', {roleId: 1, role: 'STUDENT'}), 'unsupported');
});

test('unsupported or missing IDs fail even when their code is a valid dashboard role', () => {
 for (const roleId of [0, -1, 8, 9, 10, 999, null, undefined, 1.5, NaN, Infinity, '1', '7', '', true, {}]) {
  for (const {role} of users) assertUnsupported({roleId, role});
  assert.match(roleError({roleId, role: 'ADMIN'}), /role_id từ 1 đến 7/);
 }
 for (const user of [null, undefined, 'ADMIN', 1, {}, {role: 'ADMIN'}]) assertUnsupported(user);
});

test('a valid ID cannot grant access when its role code is missing or mismatched', () => {
 for (const user of users) {
  for (const other of users) {
   if (user.roleId !== other.roleId) assertUnsupported({...user, role: other.role});
  }
  for (const role of [undefined, null, '', 'WORKER', 'SUPERADMIN', 'toString', '__proto__']) {
   assertUnsupported({...user, role});
  }
  assert.match(roleError({...user, role: 'WORKER'}), new RegExp('role_id ' + user.roleId));
  assert.match(roleError({...user, role: 'WORKER'}), new RegExp(user.role));
 }
});

test('legacy aliases and altered canonical codes never grant dashboard access', () => {
 const legacy = {1: ['admin', 'ROLE_ADMIN'], 2: ['Lecture', 'LECTURE', 'TEACHER', 'lecturer'], 3: ['student'], 4: ['accountant'], 5: ['IT_Support', 'it-support'], 6: ['manager', 'SERVICE_MANAGER'], 7: ['USER', 'EMPLOYER_HR', 'hr']};
 for (const user of users) {
  for (const role of [...legacy[user.roleId], ' ' + user.role, user.role + ' ', 'ROLE_' + user.role]) {
   assertUnsupported({...user, role});
  }
 }
});
