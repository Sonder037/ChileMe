// Run: node design/check.cjs. No packages or browser required.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const html = fs.readFileSync(`${__dirname}/index.html`, 'utf8');
const nodes = new Map();
const handlers = {};
const document = {
  getElementById(id) {
    if (!nodes.has(id)) nodes.set(id, {innerHTML: '', dataset: {}, classList: {add(){}, remove(){}, toggle(){}}});
    return nodes.get(id);
  },
  querySelectorAll() { return []; },
  addEventListener(name, handler) { handlers[name] = handler; }
};
const ctx = vm.createContext({document, window: {}, setTimeout(){}, clearTimeout(){}});
vm.runInContext(html.match(/<script>([\s\S]*?)<\/script>/)[1], ctx);
const render = () => nodes.get('screen').innerHTML;
const click = (dataset = {}, id = '') => handlers.click({target: {closest: () => ({dataset, id})}});
assert.match(render(), /认识一下你/);
assert.equal(nodes.get('nav').innerHTML, '');
assert.equal(vm.runInContext("resting({sex:'male',age:30,height:175,weight:70})",ctx),1649);
assert.equal(vm.runInContext("resting({sex:'female',age:30,height:175,weight:70})",ctx),1483);
assert.equal(vm.runInContext("resting({sex:'unspecified',age:30,height:175,weight:70})",ctx),null);
assert.equal(vm.runInContext("resting({sex:'male',age:16,height:175,weight:70})",ctx),null);
assert.equal(vm.runInContext("validProfile({sex:'male',age:30,height:0,weight:70})",ctx),false);
for (const [id,value] of Object.entries({sex:'male',age:'30',height:'175',weight:'70'}))document.getElementById(id).value=value;
handlers.submit({target:{id:'profileForm'},preventDefault(){}});
assert.match(render(), /连接你的 AI/);
document.getElementById('setupKey').value='DEMO-ONLY-NOT-A-REAL-KEY';
handlers.submit({target:{id:'modelForm'},preventDefault(){}});
assert.equal(nodes.get('setupKey').value,'');
assert.equal(vm.runInContext('measurements.length',ctx),1);
assert.match(render(), /今天吃了么/);
assert.ok(!nodes.get('nav').innerHTML.includes('知识'));
assert.match(nodes.get('composer').innerHTML, /拍照记餐/);
click({go:'capture'});
click({factor:'0.5'});
assert.match(render(), /id="estimate">260/);
click({}, 'saveMeal');
assert.match(render(), /1,680/);
click({go:'capture'});
click({factor:'2'});
click({}, 'saveMeal');
click({go:'today'});
assert.match(render(), /1680/); // Saved values cannot follow later draft edits.
for (const name of ['trends','assistant','today','settings','data','profile','help']) {
  click({go:name});
  assert.ok(render().includes('<h2>'));
}
click({go:'assistant'});
click({prompt:'我不吃香菜'});
click({go:'settings'});
assert.match(render(), /不吃香菜/);
click({go:'assistant'});
click({prompt:'我现在吃香菜'});
click({go:'settings'});
assert.match(render(), /现在接受香菜/);
assert.ok(!render().includes('不吃香菜'));
click({forget:'coriander'});
assert.ok(!render().includes('现在接受香菜'));
click({}, 'toggleMemory');
click({prompt:'我不吃香菜'});
click({go:'settings'});
assert.ok(!render().includes('来源：用户明确表达的偏好'));
click({go:'assistant'});
vm.runInContext('sendMessage("<img src=x onerror=alert(1)>")',ctx);
assert.match(render(), /&lt;img/);
assert.ok(!/\bfetch\s*\(|XMLHttpRequest|localStorage|sessionStorage/.test(html));
assert.match(nodes.get('composer').innerHTML, /<textarea[^>]+rows="2"/);
vm.runInContext('sendMessage("第一行\\n第二行")',ctx);
assert.match(render(), /第一行\n第二行/);
click({go:'trends'});
assert.match(render(), /周摄入折线/);
click({period:'month'});
assert.equal(vm.runInContext('monthValues.length',ctx),24);
assert.match(render(), /2026 年 9 月/);
assert.equal((render().match(/class="calendar-cell/g)||[]).length,30);
click({day:'2026-09-24'});
assert.match(render(), /1,420/);
click({monthStep:'-1'});
assert.match(render(), /2026 年 8 月/);
assert.equal((render().match(/class="calendar-cell/g)||[]).length,31);
vm.runInContext('monthOffset=-31',ctx);
click({period:'month'});
assert.equal((render().match(/class="calendar-cell/g)||[]).length,29);
assert.ok(!render().includes('全年明细'));
click({period:'week'});
assert.match(render(), /周摄入折线/);
console.log('PASS: multiline chat, weekly line, monthly calendar, day detail, month paging and leap February.');
console.log('PASS: onboarding, profile validation, resting calculation, discarded demo key, chat-first navigation, camera entry, save idempotency, implicit memory update/delete/disable, escaped chat, no network/storage APIs.');
