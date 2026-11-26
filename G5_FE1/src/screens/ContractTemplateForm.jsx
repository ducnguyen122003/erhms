import {useEffect,useId,useState} from 'react';
import {ChevronDown,FileText} from 'lucide-react';
import {contractTemplateValue,isComputedTemplateField,templateFieldError,templateSectionFilled,visibleTemplateFields} from './contractTemplate';
import './contract-template.css';

export default function ContractTemplateForm({metadata,values,selection,onChange,errors={},disabled=false}){
 const prefix=useId(),sections=metadata?.sections||[],fixedValues=metadata?.fixedValues||{};
 const [expanded,setExpanded]=useState(()=>Object.fromEntries(sections.map(section=>[section.key,!section.optional||templateSectionFilled(section,values,selection,fixedValues)>0])));
 useEffect(()=>{
  const invalid=sections.filter(section=>visibleTemplateFields(section,values).some(field=>templateFieldError(errors,field.key)));
  if(invalid.length)setExpanded(current=>({...current,...Object.fromEntries(invalid.map(section=>[section.key,true]))}));
 },[metadata,errors]);
 if(!sections.length)return null;
 return <section className="contract-template-form" aria-labelledby={prefix+'-title'}>
  <header><div><FileText size={20}/><h3 id={prefix+'-title'}>{metadata.title||'Thông tin theo mẫu hợp đồng'}</h3></div><p>Có thể lưu bản nháp khi chưa điền đủ. Các mục có dấu <span aria-hidden="true">*</span> cần hoàn thiện trước khi xuất PDF.</p>{metadata.fixedValues&&<p>Thông tin trung tâm, tài khoản nhận tiền và địa điểm đào tạo đã có sẵn theo mẫu.</p>}</header>
  {sections.map(section=>{
   const fields=visibleTemplateFields(section,values),invalid=fields.filter(field=>templateFieldError(errors,field.key)).length;
   return <details key={section.key} className="contract-template-section" open={Boolean(expanded[section.key])} onToggle={event=>{const open=event.currentTarget.open;setExpanded(current=>current[section.key]===open?current:{...current,[section.key]:open});}}>
    <summary><span><strong>{section.title}</strong>{section.optional&&<small>Nếu áp dụng</small>}</span><span className="contract-template-section-status">{invalid?<b>{invalid} mục cần kiểm tra</b>:<small>{templateSectionFilled(section,values,selection,fixedValues)}/{fields.length} mục đã điền</small>}<ChevronDown size={16}/></span></summary>
    <div className="contract-template-section-content">{section.description&&<p className="admin-muted">{section.description}</p>}<div className="admin-field-grid">
     {fields.map(field=>{
      const id=prefix+'-'+field.key,error=templateFieldError(errors,field.key),readOnly=isComputedTemplateField(field),value=contractTemplateValue(field,values,selection,fixedValues);
      const shared={id,name:'templateData.'+field.key,disabled,readOnly,'aria-invalid':Boolean(error),'aria-describedby':error?id+'-error':undefined};
      const choices=Array.isArray(field.source)?field.source:field.options||[];
      return <label key={field.key} htmlFor={id} className={'admin-field'+(field.type==='textarea'||field.wide?' screen-field-wide':'')}>{field.label}{field.requiredForPdf&&<span className="contract-template-required" title="Cần điền để xuất PDF"> *</span>}
       {field.type==='checkbox'?<span className="screen-checkbox"><input {...shared} type="checkbox" checked={Boolean(value)} disabled={disabled||readOnly} onChange={event=>onChange(field.key,event.target.checked)}/>Áp dụng</span>
        :field.type==='textarea'?<textarea {...shared} value={value} rows={field.rows||3} maxLength={field.maxLength||4000} onChange={event=>onChange(field.key,event.target.value)}/>
        :field.type==='select'&&!readOnly?<select {...shared} value={value} onChange={event=>onChange(field.key,event.target.value)}><option value="">Chọn {field.label.toLowerCase()}</option>{choices.map((choice,index)=>{const option=typeof choice==='string'?{value:choice,label:choice}:choice;return <option key={String(option.value??option.id)+'-'+index} value={option.value??option.id}>{option.label??option.name??option.value??option.id}</option>;})}</select>
        :<input {...shared} type={field.type==='select'?'text':field.type||'text'} value={value} min={field.min} max={field.max} step={field.type==='number'?field.step??'any':undefined} maxLength={field.type==='number'?undefined:field.maxLength||1000} placeholder={field.placeholder||(field.key==='contractNumber'?'Được cấp khi lưu hợp đồng':undefined)} onChange={event=>onChange(field.key,event.target.value)}/>}
       {error&&<small className="field-error" id={id+'-error'}>{error}</small>}
      </label>;
     })}
    </div></div>
   </details>;
  })}
 </section>;
}
