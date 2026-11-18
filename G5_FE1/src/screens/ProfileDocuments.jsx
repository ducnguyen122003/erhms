import {CircleAlert,CircleCheck,FileText} from 'lucide-react';
import {documentCompleteness,groupedProfileDocuments} from './admissionDocuments';
import './admission-documents.css';

export function ProfileDocumentStatus({documents=[]}){
 const {complete,received,missing}=documentCompleteness(documents),Icon=complete?CircleCheck:CircleAlert;
 return <div className={'admission-profile-status '+(complete?'is-complete':'is-incomplete')} role="status"><Icon size={20}/><div><strong>{complete?'Đã hoàn thành hồ sơ':'Hồ sơ còn thiếu'}</strong><p>{complete?'Đã có đủ 5 loại giấy tờ.':`Đã có ${received}/5 loại giấy tờ. Còn thiếu: ${missing.map(item=>item.label).join(', ')}.`}</p></div></div>;
}
export default function ProfileDocuments({documents=[],legacyEvidence='',compact=false,showCompleteness=false}){
 const groups=groupedProfileDocuments(documents,legacyEvidence);
 if(!groups.length&&!showCompleteness)return null;
 const count=groups.reduce((total,group)=>total+group.files.length,0);
 const contents=<div className="admission-document-groups">{groups.map(group=><div key={group.code}><strong>{group.label}</strong><ul>{group.files.map(file=><li key={file.fileId}><a href={'/api/workspace/files/'+file.fileId} download><FileText size={14}/>{file.filename}</a></li>)}</ul></div>)}</div>;
 return compact?<details className="admission-documents-compact"><summary>Giấy tờ hồ sơ ({count} tệp)</summary>{contents}</details>:<section className="admission-documents-view"><h3>Giấy tờ hồ sơ · {count} tệp</h3>{showCompleteness&&<ProfileDocumentStatus documents={documents}/>} {contents}</section>;
}
