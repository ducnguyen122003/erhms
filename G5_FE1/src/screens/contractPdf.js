import {api} from '../api.js';

export function contractPdfPath(id,download=false){return '/api/workspace/contracts/'+encodeURIComponent(String(id))+'/pdf?download='+String(Boolean(download));}
export function contractPdfFilename(record){return 'hop-dong-'+String(record.code||record.id).replace(/[\\/:*?"<>|\u0000-\u001f]/g,'-')+'.pdf';}
export async function fetchContractPdf(id,download=false,signal){return api(contractPdfPath(id,download),{responseType:'blob',cache:'no-store',signal});}

export function savePdfBlob(blob,filename){
 const url=URL.createObjectURL(blob),link=document.createElement('a');
 link.href=url;link.download=filename;document.body.appendChild(link);link.click();link.remove();
 setTimeout(()=>URL.revokeObjectURL(url),1000);
}
