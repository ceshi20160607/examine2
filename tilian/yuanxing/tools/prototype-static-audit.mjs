#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const target = process.argv[2] || 'docs/design/prototypes/index.html';
const file = path.resolve(target);

if (!fs.existsSync(file)) {
  console.error(`Prototype file not found: ${file}`);
  process.exit(2);
}

const html = fs.readFileSync(file, 'utf8');

function matches(pattern) {
  return [...html.matchAll(pattern)];
}

function valuesForAttr(attr) {
  const pattern = new RegExp(`\\b${attr}\\s*=\\s*["']([^"']+)["']`, 'g');
  return matches(pattern).map((m) => m[1].trim()).filter(Boolean);
}

const ids = new Set(matches(/\bid\s*=\s*["']([^"']+)["']/g).map((m) => m[1]));
const jsObjectKeys = new Set(matches(/^\s*([A-Za-z_$][\w$]*)\s*:/gm).map((m) => m[1]));
const refAttrs = [
  'data-screen',
  'data-page',
  'data-subpage',
  'data-work-subpage',
  'data-task-view',
  'data-drawer',
  'data-row-drawer',
  'data-jump-screen',
  'data-jump-page',
  'data-jump-drawer'
];

const brokenRefs = [];
for (const attr of refAttrs) {
  for (const value of valuesForAttr(attr)) {
    const drawerLike = attr.includes('drawer');
    const targetExists = ids.has(value) || (drawerLike && jsObjectKeys.has(value));
    if (!targetExists) {
      brokenRefs.push({ attr, value });
    }
  }
}

const defaultOrGenericRefs = matches(/\bdata-(?:drawer|row-drawer|jump-drawer)\s*=\s*["'](?:defaultDrawer|generic[^"']*Drawer)["']/gi);
const dataToast = valuesForAttr('data-toast');
const hrefHash = matches(/\bhref\s*=\s*["']#["']/g);
const messageCardStarts = matches(/class\s*=\s*["'][^"']*message-card[^"']*["']/g).map((m) => m.index ?? 0);
function findDivEnd(classAttrIndex) {
  const divStart = html.lastIndexOf('<div', classAttrIndex);
  if (divStart < 0) {
    return Math.min(html.length, classAttrIndex + 1600);
  }
  const tagPattern = /<\/?div\b[^>]*>/gi;
  tagPattern.lastIndex = divStart;
  let depth = 0;
  let tag;
  while ((tag = tagPattern.exec(html)) !== null) {
    if (tag[0].startsWith('</')) {
      depth -= 1;
    } else {
      depth += 1;
    }
    if (depth === 0) {
      return tagPattern.lastIndex;
    }
  }
  return Math.min(html.length, classAttrIndex + 1600);
}
const messageCardsWithButtons = messageCardStarts.filter((start, index) => {
  const end = findDivEnd(start);
  return /<button\b/i.test(html.slice(start, end));
});
const exactDetailButtons = matches(/<button\b[^>]*>\s*(?:详情|查看|打开|进入|进入详情|查看详情|打开详情)\s*<\/button>/g);

const hardFailures = [];
const warnings = [];

if (brokenRefs.length > 0) {
  hardFailures.push(`Broken data-* references: ${brokenRefs.length}`);
}

if (defaultOrGenericRefs.length > 0) {
  hardFailures.push(`default/generic drawer references: ${defaultOrGenericRefs.length}`);
}

if (dataToast.length > 0) {
  warnings.push(`data-toast attributes found: ${dataToast.length}. Critical actions should use result/task/log/drawer feedback.`);
}

if (hrefHash.length > 0) {
  warnings.push(`href="#" found: ${hrefHash.length}. Prefer buttons or real anchors.`);
}

if (messageCardsWithButtons.length > 0) {
  warnings.push(`message-card blocks containing buttons found: ${messageCardsWithButtons.length}. Check for duplicate jump actions.`);
}

if (exactDetailButtons.length > 0) {
  warnings.push(`Exact detail-like buttons found: ${exactDetailButtons.length}. If rows already open detail, remove duplicate actions.`);
}

const summary = {
  file,
  ids: ids.size,
  jsObjectKeys: jsObjectKeys.size,
  refs: Object.fromEntries(refAttrs.map((attr) => [attr, valuesForAttr(attr).length])),
  brokenRefs,
  hardFailures,
  warnings
};

console.log(JSON.stringify(summary, null, 2));

if (hardFailures.length > 0) {
  process.exit(1);
}
