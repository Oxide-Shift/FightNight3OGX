// Exercise scoring and persistence without an Android WebView.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const html = fs.readFileSync(path.join(__dirname, '../android/assets/corner.html'), 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
const storage = new Map();
function mount() {
  const fields = new Map();
  const field = id => {
    if (!fields.has(id)) fields.set(id, {setAttribute(k,v){ this[k]=v; }});
    return fields.get(id);
  };
  const steps = ['r','b'].flatMap(side => [-1,1].map(step => ({dataset:{side,step}})));
  const context = {
    document: {getElementById:field, querySelectorAll:()=>steps},
    localStorage: {getItem:k=>storage.get(k), setItem:(k,v)=>storage.set(k,v)},
    window:{addEventListener(){}}
  };
  vm.runInNewContext(script, context);
  return {field,steps};
}
let view = mount();
view.steps[2].onclick();
view.field('save').onclick();
assert.equal(view.field('total').textContent, '10  /  9');
view = mount();
assert.equal(view.field('round').textContent, 'Round 02');
view.field('undo').onclick();
assert.equal(view.field('b').textContent, 9);
assert.equal(view.field('undo').disabled, true);
for(let i=0;i<12;i++) view.field('save').onclick();
assert.equal(view.field('round').textContent, 'Card complete');
assert.equal(view.field('save').disabled, true);
assert.equal(view.field('total').textContent, '120  /  119');
view.field('newBout').onclick(); view.field('keep').onclick();
assert.equal(view.field('round').textContent, 'Card complete');
view.field('newBout').onclick(); view.field('clear').onclick();
assert.equal(view.field('round').textContent, 'Round 01');
console.log('Score totals, persistence, undo, 12-round limit and reset passed');
