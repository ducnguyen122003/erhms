import assert from 'node:assert/strict';
import test from 'node:test';
import {attendanceDetailSearch, attendanceFilters, attendanceLocalDay, attendanceReturnSearch, attendanceWeek, shiftAttendanceDay} from '../src/screens/attendanceSchedule.js';

test('dates and week navigation follow Vietnamese time at UTC midnight and year changes', () => {
 assert.equal(attendanceLocalDay('2026-12-27T18:30:00Z'), '2026-12-28');
 assert.equal(attendanceWeek('2027-01-03'), '2026-12-28');
 assert.equal(shiftAttendanceDay('2026-12-28', 7), '2027-01-04');
 assert.equal(attendanceWeek('2027-02-29'), '');
});

test('attendance details and corrections preserve class, week and status without carrying unrelated query data', () => {
 const classId = 'a296af26-b784-4517-a408-972409366719';
 const params = new URLSearchParams({returnAttendance: '1', week: '2027-01-20', classId, status: 'PENDING', page: '2', studentId: '17'});
 assert.deepEqual(attendanceFilters(params), {week: '2027-01-18', classId, status: 'PENDING', page: 2});
 assert.equal(attendanceReturnSearch(params), `?week=2027-01-18&classId=${classId}&status=PENDING&page=2`);
 assert.equal(attendanceDetailSearch(params).get('returnAttendance'), '1');
 assert.equal(attendanceDetailSearch(params).has('studentId'), false);
 assert.equal(attendanceReturnSearch(new URLSearchParams('week=2027-01-18')), '');
});
