import {useEffect, useRef, useState} from 'react';
import {ClipboardList, Search, Send} from 'lucide-react';
import {api} from '../api';
import {AdminDialog, AdminLoading, AdminMessage, AdminPagination} from '../admin/AdminUI';
import {forms} from './definitions';
import {Fields, prepareValues, useUnsavedChanges} from './ScreenForm';
import './initial-equipment-request.css';

const endpoint = '/api/workspace/equipment-initial-requests';
const maxStudents = 50;
const fields = forms.AddEquipmentRequest
  .filter(field => ['title', 'quantity', 'neededOn', 'description'].includes(field.key))
  .map(field => {
    if (field.key === 'quantity') return {...field, label: 'Số lượng cho mỗi học viên', step: 1};
    if (field.key === 'title') return {...field, wide: true, maxLength: 160};
    if (field.key === 'description') return {...field, maxLength: 2000};
    return field;
  });
const defaultValues = {title: 'Cấp thiết bị ban đầu', quantity: '1', neededOn: '', description: ''};
const classLabel = selected => [selected?.code, selected?.name].filter(Boolean).join(' · ');
const enrollmentDate = new Intl.DateTimeFormat('vi-VN', {dateStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh'});

export default function InitialEquipmentRequest({onLogout, onCreated}) {
  const [classId, setClassId] = useState('');
  const [classes, setClasses] = useState([]);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState({q: '', page: 0});
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const [selection, setSelection] = useState(() => new Map());
  const [values, setValues] = useState(defaultValues);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [conflict, setConflict] = useState(false);
  const [notice, setNotice] = useState('');
  const saving = useRef(null);
  const dirty = useUnsavedChanges();

  useEffect(() => () => saving.current?.abort(), []);

  useEffect(() => {
    const timer = setTimeout(() => {
      setFilter(current => current.q === search.trim() ? current : {q: search.trim(), page: 0});
    }, 250);
    return () => clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    const query = new URLSearchParams(filter);
    if (classId) query.set('classId', classId);
    api(`${endpoint}?${query}`, {signal: controller.signal}).then(result => {
      if (controller.signal.aborted) return;
      setClasses(result.classes);
      if (!classId && result.classes.length) {
        setClassId(result.classes[0].id);
        return;
      }
      if (filter.page > 0 && filter.page >= result.totalPages) {
        setFilter(current => ({...current, page: Math.max(0, result.totalPages - 1)}));
        return;
      }
      setData(result);
    }).catch(cause => {
      if (controller.signal.aborted) return;
      if (cause.status === 401) onLogout();
      else setError(cause.message);
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
    return () => controller.abort();
  }, [classId, filter, revision, onLogout]);

  const rows = data?.items || [];
  const checkedOnPage = rows.filter(row => selection.has(row.id)).length;
  const allChecked = rows.length > 0 && checkedOnPage === rows.length;
  const selectedClass = classes.find(option => option.id === classId);
  const reload = () => setRevision(current => current + 1);

  function chooseClass(nextClass) {
    setClassId(nextClass);
    setData(null);
    setSelection(new Map());
    setFilter(current => ({...current, page: 0}));
    dirty.current = false;
  }

  function chooseRows(nextRows, checked) {
    const next = new Map(selection);
    for (const row of nextRows) {
      if (!checked) next.delete(row.id);
      else if (next.size < maxStudents) next.set(row.id, row);
    }
    setSelection(next);
    dirty.current = next.size > 0;
  }

  function openReview() {
    setNotice('');
    setSubmitError('');
    setFieldErrors({});
    setConflict(false);
    setReviewOpen(true);
  }

  function refreshAfterConflict() {
    setReviewOpen(false);
    setError('');
    setSelection(new Map());
    dirty.current = false;
    reload();
  }

  async function submit(event) {
    event.preventDefault();
    if (saving.current || !selection.size || conflict) return;
    const controller = new AbortController();
    saving.current = controller;
    setSubmitting(true);
    setSubmitError('');
    setFieldErrors({});
    try {
      const body = {...prepareValues(values, fields), classId, studentIds: [...selection.keys()]};
      const result = await api(endpoint, {method: 'POST', body: JSON.stringify(body), signal: controller.signal});
      if (controller.signal.aborted) return;
      dirty.current = false;
      setSelection(new Map());
      setReviewOpen(false);
      setNotice(result.notice || `Đã gửi yêu cầu cấp thiết bị cho ${result.createdCount} học viên.`);
      reload();
      onCreated?.();
    } catch (cause) {
      if (controller.signal.aborted) return;
      if (cause.status === 401) onLogout();
      else {
        setSubmitError(cause.message);
        setFieldErrors(cause.fields || {});
        setConflict(cause.status === 409);
      }
    } finally {
      saving.current = null;
      if (!controller.signal.aborted) setSubmitting(false);
    }
  }

  return <section className="admin-card initial-equipment" aria-labelledby="initial-equipment-title">
    <header className="admin-card-heading"><div>
      <h2 id="initial-equipment-title"><ClipboardList size={18} aria-hidden="true"/> Cấp thiết bị lần đầu</h2>
      <p>Chọn học viên chưa từng nhận thiết bị trong lớp và chưa có yêu cầu cấp ban đầu đang hiệu lực.</p>
    </div></header>
    <div className="initial-equipment-notice"><AdminMessage notice={notice}/></div>
    {classes.length > 0 && <div className="initial-equipment-filters">
      <label>Lớp phụ trách<select value={classId} onChange={event => chooseClass(event.target.value)}>
        {classes.map(option => <option key={option.id} value={option.id}>{classLabel(option)}</option>)}
      </select></label>
      <label>Tìm học viên<span><Search size={16} aria-hidden="true"/><input type="search" value={search} maxLength={200}
        placeholder="Tên hoặc mã học viên" onChange={event => setSearch(event.target.value)}/></span></label>
    </div>}
    {loading || error ? <AdminLoading error={error} onRetry={reload}/>
      : !classes.length ? <p className="initial-equipment-empty">Chưa có lớp được phân công đang chuẩn bị khai giảng hoặc đang học.</p>
      : <>
        {rows.length > 0 ? <div className="admin-table-scroll"><table className="admin-table initial-equipment-table">
          <thead><tr>
            <th scope="col" className="initial-equipment-check"><input type="checkbox" aria-label="Chọn tất cả học viên ở trang này"
              checked={allChecked} ref={input => {if (input) input.indeterminate = checkedOnPage > 0 && !allChecked;}}
              disabled={!allChecked && selection.size + rows.length - checkedOnPage > maxStudents}
              onChange={event => chooseRows(rows, event.target.checked)}/></th>
            <th scope="col">Mã học viên</th><th scope="col">Học viên</th><th scope="col">Ngày xếp lớp</th>
          </tr></thead>
          <tbody>{rows.map(row => <tr key={row.id} className={selection.has(row.id) ? 'is-selected' : undefined}>
            <td className="initial-equipment-check"><input type="checkbox" aria-label={`Chọn ${row.name}`}
              checked={selection.has(row.id)} disabled={!selection.has(row.id) && selection.size >= maxStudents}
              onChange={event => chooseRows([row], event.target.checked)}/></td>
            <td>{row.studentCode}</td><td><strong>{row.name}</strong></td>
            <td>{row.enrolledAt ? enrollmentDate.format(new Date(row.enrolledAt)) : '—'}</td>
          </tr>)}</tbody>
        </table></div> : <p className="initial-equipment-empty">{filter.q ? 'Không tìm thấy học viên phù hợp.' : 'Lớp này không có học viên cần yêu cầu cấp thiết bị lần đầu.'}</p>}
        {data?.totalPages > 1 && <AdminPagination page={filter.page} total={data.total} size={data.size} totalPages={data.totalPages}
          onPage={page => setFilter(current => ({...current, page}))}/>}
      </>}
    {classes.length > 0 && <footer className="initial-equipment-footer">
      <span role="status">Đã chọn <strong>{selection.size}</strong>/{maxStudents} học viên</span>
      {selection.size > 0 && <button type="button" className="admin-text-button" onClick={() => chooseRows([...selection.values()], false)}>Bỏ chọn</button>}
      <button type="button" disabled={!selection.size || loading || Boolean(error)} onClick={openReview}><Send size={15}/> Tạo yêu cầu cho học viên đã chọn</button>
    </footer>}
    {reviewOpen && <AdminDialog title="Tạo yêu cầu cấp thiết bị lần đầu" busy={submitting} onClose={() => setReviewOpen(false)}>
      <form className="admin-dialog-body initial-equipment-confirm" aria-busy={submitting} onSubmit={submit}>
        <AdminMessage error={submitError}/>
        <p><strong>{classLabel(selectedClass)}</strong> · {selection.size} học viên</p>
        <details><summary>Xem học viên đã chọn</summary><ul>{[...selection.values()].map(row => <li key={row.id}>{row.name} · {row.studentCode}</li>)}</ul></details>
        <fieldset disabled={submitting || conflict}>
          <Fields fields={fields} values={values} errors={fieldErrors}
            setValue={(key, value) => setValues(current => ({...current, [key]: value}))}/>
        </fieldset>
        <div className="admin-dialog-actions">
          <button type="button" className="admin-secondary" data-initial-focus disabled={submitting} onClick={() => setReviewOpen(false)}>Hủy</button>
          {conflict ? <button type="button" onClick={refreshAfterConflict}>Tải lại danh sách</button>
            : <button type="submit" disabled={submitting}>{submitting ? 'Đang gửi…' : `Gửi ${selection.size} yêu cầu`}</button>}
        </div>
      </form>
    </AdminDialog>}
  </section>;
}
