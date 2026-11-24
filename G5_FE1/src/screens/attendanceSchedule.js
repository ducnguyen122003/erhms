const zone = 'Asia/Ho_Chi_Minh';
const dateKey = new Intl.DateTimeFormat('sv-SE', {timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit'});
const shortDate = new Intl.DateTimeFormat('vi-VN', {timeZone: zone, day: '2-digit', month: '2-digit'});
const fullDate = new Intl.DateTimeFormat('vi-VN', {timeZone: zone, weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric'});
const time = new Intl.DateTimeFormat('vi-VN', {timeZone: zone, hour: '2-digit', minute: '2-digit', hour12: false});
const statuses = new Set(['ALL', 'PENDING', 'UPCOMING', 'FINALIZED', 'BACKLOG']);
const classIdPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function asDate(day) { return new Date(`${day}T12:00:00Z`); }

function validDay(day) {
 const year = Number(day?.slice(0, 4));
 if (!/^\d{4}-\d{2}-\d{2}$/.test(day || '') || year < 1000 || year > 9998) return false;
 const date = asDate(day);
 return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === day;
}

export function shiftAttendanceDay(day, delta) {
 const date = asDate(day);
 date.setUTCDate(date.getUTCDate() + delta);
 return date.toISOString().slice(0, 10);
}

export function attendanceWeek(day) {
 if (!validDay(day)) return '';
 const weekday = asDate(day).getUTCDay();
 return shiftAttendanceDay(day, -((weekday + 6) % 7));
}

export function attendanceLocalDay(value = new Date()) { return dateKey.format(new Date(value)); }
export function attendanceDayLabel(day) { return fullDate.format(asDate(day)); }
export function attendanceTime(value) { return time.format(new Date(value)); }

export function attendanceWeekLabel(week) {
 const end = shiftAttendanceDay(week, 6);
 return `${shortDate.format(asDate(week))} – ${shortDate.format(asDate(end))}/${end.slice(0, 4)}`;
}

export function attendanceFilters(params) {
 const week = params.get('week');
 const classId = params.get('classId');
 const status = params.get('status');
 const page = params.get('page');
 return {
  week: attendanceWeek(week) || attendanceWeek(attendanceLocalDay()),
  classId: classIdPattern.test(classId || '') ? classId : '',
  status: statuses.has(status) ? status : 'ALL',
  page: /^\d{1,6}$/.test(page || '') && Number(page) <= 100000 ? Number(page) : 0,
 };
}

export function attendanceSearch(filters) {
 const params = new URLSearchParams({week: filters.week});
 if (filters.classId) params.set('classId', filters.classId);
 if (filters.status !== 'ALL') params.set('status', filters.status);
 if (filters.page) params.set('page', filters.page);
 return params;
}

export function attendanceReturnSearch(params) {
 if (params.get('returnAttendance') !== '1') return '';
 return `?${attendanceSearch(attendanceFilters(params))}`;
}

export function attendanceDetailSearch(params) {
 const search = new URLSearchParams(attendanceReturnSearch(params));
 if ([...search].length) search.set('returnAttendance', '1');
 return search;
}
