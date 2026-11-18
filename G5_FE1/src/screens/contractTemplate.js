const authoritativeFields=new Set(['contractNumber','studentName','studentPhone','studentEmail','courseName','durationMonths','startsOn','tuitionDue','depositDue','paymentDueOn']);
const map=value=>value&&typeof value==='object'&&!Array.isArray(value)?value:{};

export function editableTemplateData(value,metadata){
 const computed=new Set([...Object.keys(metadata?.fixedValues||{}),...(metadata?.sections||[]).flatMap(section=>(section.fields||[]).filter(field=>field.readOnly||field.computed).map(field=>field.key))]);
 return Object.fromEntries(Object.entries(map(value)).filter(([key])=>!authoritativeFields.has(key)&&!computed.has(key)));
}

export function hasContractTemplate(values,options={},admission){
 return Boolean(options.contractTemplate||options.contractTemplateDefaults||values.templateData||admission?.contractTemplateDefaults);
}

export function initialContractTemplateData(values,options={},admission){
 return editableTemplateData({...map(options.contractTemplateDefaults),...map(admission?.contractTemplateDefaults),...map(values.templateData)},options.contractTemplate);
}

export function resetContractTemplateData(values,options={},admission){
 const retained=Object.fromEntries(Object.entries(map(values.templateData)).filter(([key])=>key.startsWith('center')||key==='agreementPlace'));
 return editableTemplateData({...map(options.contractTemplateDefaults),...map(admission?.contractTemplateDefaults),...retained},options.contractTemplate);
}

function scaled(raw){
 let text=String(raw??'').trim();
 if(!/^\d+(?:\.\d*)?$/.test(text)){
  const number=Number(text);if(!Number.isFinite(number)||number<0)return 0n;
  text=number.toLocaleString('en-US',{useGrouping:false,maximumFractionDigits:2});
 }
 const [whole,fraction='']=text.split('.');
 return BigInt(whole||'0')*100n+BigInt((fraction+'00').slice(0,2));
}
const decimal=value=>String(value/100n)+(value%100n?'.'+String(value%100n).padStart(2,'0').replace(/0$/,''):'');
const digits=['không','một','hai','ba','bốn','năm','sáu','bảy','tám','chín'],groups=['','nghìn','triệu','tỷ','nghìn tỷ','triệu tỷ'];
function threeDigits(value,full){
 const hundreds=Math.floor(value/100),tens=Math.floor(value/10)%10,ones=value%10,parts=[];
 if(hundreds||full)parts.push(digits[hundreds],'trăm');
 if(!tens){if(ones&&(hundreds||full))parts.push('linh');}else if(tens===1)parts.push('mười');else parts.push(digits[tens],'mươi');
 if(ones)parts.push(ones===1&&tens>1?'mốt':ones===5&&tens>0?'lăm':digits[ones]);
 return parts.join(' ');
}
function moneyWords(amount){
 let integer=amount/100n,words='không';const chunks=[];
 while(integer>0n){chunks.push(Number(integer%1000n));integer/=1000n;}
 if(chunks.length>groups.length)return decimal(amount)+' đồng';
 const parts=[];let higher=false;
 for(let index=chunks.length-1;index>=0;index--){if(!chunks[index])continue;parts.push(threeDigits(chunks[index],higher)+(index?' '+groups[index]:''));higher=true;}
 if(parts.length)words=parts.join(' ');
 const fraction=Number(amount%100n);if(fraction)words+=' phẩy '+digits[Math.floor(fraction/10)]+' '+digits[fraction%10];
 return words.charAt(0).toUpperCase()+words.slice(1)+' đồng';
}

export function computedContractTemplateData(values={}){
 const total=scaled(values.tuitionDue)+scaled(values.depositDue);
 return {initialTotal:decimal(total),initialTotalInWords:moneyWords(total)};
}

const calculatedFields=new Set(['initialTotal','initialTotalInWords']);

export function contractTemplateValue(field,values={},selection={}){
 if(calculatedFields.has(field.key))return computedContractTemplateData(values)[field.key];
 const admission=selection.admission;
 const unchangedAdmission=Boolean(selection.savedRecord?.id&&String(values.consultationId)===String(selection.savedRecord.consultationId));
 const unchangedCourse=unchangedAdmission&&String(values.courseId??'')===String(selection.savedRecord.courseId??'');
 switch(field.key){
  case 'contractNumber':return values.code||'';
  case 'studentName':return (unchangedAdmission&&(values.applicantName||values.studentName))||admission?.fullName||values.applicantName||values.studentName||'';
  case 'studentPhone':return (unchangedAdmission&&values.applicantPhone)||admission?.phone||values.applicantPhone||values.phone||'';
  case 'studentEmail':return ((unchangedAdmission&&values.studentEmail)||admission?.email||values.studentEmail||'').trim().toLowerCase();
  case 'courseName':return (unchangedCourse&&(values.registeredCourseName||values.courseName))||selection.registeredName||selection.course?.name||values.registeredCourseName||values.courseName||'';
  case 'durationMonths':return (unchangedCourse?(values.registeredDurationMonths??values.durationMonths):undefined)??selection.durationMonths??values.registeredDurationMonths??values.durationMonths??'';
  case 'startsOn':case 'tuitionDue':case 'depositDue':case 'paymentDueOn':return values[field.key]??'';
  default:return values.templateData?.[field.key]??field.defaultValue??'';
 }
}

export function isComputedTemplateField(field){return authoritativeFields.has(field.key)||Boolean(field.readOnly||field.computed);}
export function templateFieldError(errors,key){return errors?.['templateData.'+key]||'';}
export function visibleTemplateFields(section,values){return (section.fields||[]).filter(field=>!field.condition||values.templateData?.[field.condition]===true);}
export function templateSectionFilled(section,values,selection){return visibleTemplateFields(section,values).filter(field=>field.type==='checkbox'?Boolean(contractTemplateValue(field,values,selection)):String(contractTemplateValue(field,values,selection)).trim()!=='').length;}

export function contractPdfFieldErrors(errors={},metadata){
 const labels=new Map((metadata?.sections||[]).flatMap(section=>(section.fields||[]).map(field=>['templateData.'+field.key,field.label])));
 return Object.entries(errors).map(([key,message])=>({key,label:labels.get(key)||'Thông tin hợp đồng',message:String(message)}));
}
