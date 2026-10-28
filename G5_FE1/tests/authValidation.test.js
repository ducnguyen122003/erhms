import test from 'node:test';
import assert from 'node:assert/strict';
import {resetPasswordError} from '../src/authValidation.js';

test('reset password validation counts Unicode characters and BCrypt UTF-8 byte limits', () => {
 assert.match(resetPasswordError('🔒'.repeat(7), '🔒'.repeat(7)), /8 ký tự/);
 assert.equal(resetPasswordError('🔒'.repeat(8), '🔒'.repeat(8)), '');
 assert.equal(resetPasswordError('a'.repeat(72), 'a'.repeat(72)), '');
 assert.match(resetPasswordError('a'.repeat(73), 'a'.repeat(73)), /72 byte/);
 assert.equal(resetPasswordError('á'.repeat(36), 'á'.repeat(36)), '');
 assert.match(resetPasswordError('á'.repeat(37), 'á'.repeat(37)), /72 byte/);
});

test('reset confirmation must match exactly and whitespace-only passwords are rejected', () => {
 assert.match(resetPasswordError('        ', '        '), /8 ký tự/);
 assert.match(resetPasswordError('Password 1', 'Password 1 '), /không khớp/);
 assert.equal(resetPasswordError(' Password 1 ', ' Password 1 '), '');
});
