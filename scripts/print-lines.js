const fs=require('fs');
const path='feature/reader/src/main/java/info/lwb/feature/reader/ReaderScreen.kt';
const lines=fs.readFileSync(path,'utf8').split(/\r?\n/);
lines.forEach((l,i)=>{ if(i>=190 && i<=215) console.log(String(i+1).padStart(4,'0'), l); });