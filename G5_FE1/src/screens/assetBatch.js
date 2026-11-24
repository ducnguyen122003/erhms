export function assetBatchCodes(value, quantity) {
 const code=String(value??'').trim(),count=Number(quantity);
 if(!code||code.length>40||!Number.isInteger(count)||count<1||count>500)return null;
 const match=/^(.*?)([0-9]+)$/.exec(code),prefix=match?match[1]:code;
 const first=match?BigInt(match[2]):1n,width=Math.max(3,match?.[2].length||0);
 if(!/\p{L}/u.test(prefix)||first<1n)return null;
 const codes=Array.from({length:count},(_,index)=>prefix+(first+BigInt(index)).toString().padStart(width,'0'));
 return codes.at(-1).length<=40?codes:null;
}
