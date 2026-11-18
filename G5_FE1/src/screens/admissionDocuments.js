export const documentTypes=[
 {code:'IDENTITY_CARD',label:'CCCD'},
 {code:'CURRICULUM_VITAE',label:'Sơ yếu lý lịch'},
 {code:'BIRTH_CERTIFICATE',label:'Giấy khai sinh bản sao'},
 {code:'ID_PHOTO',label:'Ảnh thẻ'},
 {code:'APPLICATION_FORM',label:'Đơn xin'}
];
export const maxDocumentFiles=50;
export const maxDocumentFileBytes=2*1024*1024;
const legacyType={code:'LEGACY_SCAN',label:'Bản scan hồ sơ cũ'};
export const uploadDocumentTypes=uploads=>uploads.LEGACY_SCAN?[...documentTypes,legacyType]:documentTypes;
export function emptyDocumentUploads(documents=[]){
 const uploads=Object.fromEntries(documentTypes.map(item=>[item.code,{files:[]}]));
 for(const document of documents){
  if(document.documentType==='LEGACY_SCAN')uploads.LEGACY_SCAN??={files:[]};
  if(uploads[document.documentType])uploads[document.documentType].files.push({id:document.fileId,filename:document.filename});
 }
 return uploads;
}
export const uploadedFileCount=uploads=>uploadDocumentTypes(uploads).reduce((total,item)=>total+(uploads[item.code]?.files.length||0),0);
export function documentGroups(uploads){
 return uploadDocumentTypes(uploads).filter(item=>uploads[item.code]?.files.length).map(item=>({type:item.code,files:uploads[item.code].files.map(file=>file.id)}));
}
export function documentCompleteness(documents=[]){
 const types=new Set(documents.map(document=>document.documentType)),missing=documentTypes.filter(item=>!types.has(item.code));
 return {complete:missing.length===0,received:documentTypes.length-missing.length,missing};
}
export function admissionStatus(record){return record.status==='PROFILE_PENDING'&&record.documentStatus?record.documentStatus:record.status;}
export function validateDocumentUploads(uploads,{allowEmpty=false}={}){
 const errors={};
 if(!allowEmpty&&!uploadedFileCount(uploads))errors.documents='Tải ít nhất một file PDF trước khi gửi hồ sơ.';
 if(uploadedFileCount(uploads)>maxDocumentFiles)errors.documents='Toàn bộ hồ sơ tối đa 50 tệp PDF.';
 return errors;
}
export function pdfSelectionError(files,currentCount){
 if(currentCount+files.length>maxDocumentFiles)return 'Toàn bộ hồ sơ tối đa 50 tệp PDF.';
 for(const file of files){
  if(!String(file.name||'').toLowerCase().endsWith('.pdf'))return 'Chỉ nhận file PDF: '+file.name;
  if(!file.size||file.size>maxDocumentFileBytes)return 'Tệp '+file.name+' cần có dữ liệu và không vượt quá 2 MB.';
 }
 return '';
}
export function groupedProfileDocuments(documents=[],legacyEvidence=''){
 let rows=documents;
 if(!rows.length){const match=/^file:([0-9a-f-]{36}):(.+)$/i.exec(legacyEvidence||'');if(match)rows=[{documentType:'LEGACY_SCAN',fileId:match[1],filename:match[2]}];}
 const types=[...documentTypes,{code:'LEGACY_SCAN',label:'Bản scan hồ sơ cũ'}];
 return types.map(type=>({...type,files:rows.filter(row=>row.documentType===type.code)})).filter(type=>type.files.length);
}
