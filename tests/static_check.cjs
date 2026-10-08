// Basic source checks; no browser required.
const fs=require("fs");
for (const f of ["web/app.js","web/sw.js"]) {
  const s=fs.readFileSync(f,"utf8");
  if(!s.length) throw new Error(f+" empty");
}
console.log("Static file checks: OK");
