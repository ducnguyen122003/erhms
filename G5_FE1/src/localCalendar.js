const vietnamDate = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
});

export function vietnamLocalDay(value = new Date()) {
  return vietnamDate.format(new Date(value));
}

export function shiftCalendarDay(day, days) {
  const date = new Date(`${day}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

export function nextVietnamDay(value) {
  return shiftCalendarDay(vietnamLocalDay(value), 1);
}
