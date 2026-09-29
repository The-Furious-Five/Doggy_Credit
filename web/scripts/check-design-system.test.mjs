import { test } from 'node:test';
import assert from 'node:assert/strict';
import { checkCss, checkTemplate, checkTypescript } from './check-design-system.mjs';
test('rechaza controles propios incluso dentro de bloques Angular', () => {
  assert.ok(checkTemplate('@if (ok) { <button>Continuar</button> }').length);
  assert.ok(checkTemplate('<input />').length);
  assert.equal(checkTemplate('<button dsButton>Continuar</button>').length, 0);
});
test('rechaza estilos inline y bindings', () => {
  assert.ok(checkTemplate('<p [style.color]="color">Texto</p>').length);
  assert.ok(checkTemplate('<div [ngStyle]="custom"></div>').length);
});
test('exige colores sólidos: sin degradados ni desenfoques, también con tokens', () => {
  assert.ok(
    checkCss('.hero { background: linear-gradient(var(--ds-primary), var(--ds-accent)); }').length,
  );
  assert.ok(checkCss('.bar { backdrop-filter: blur(var(--ds-space-4)); }').length);
  assert.ok(checkCss('.art { filter: saturate(2); }').length);
  assert.equal(checkCss('.hero { background: var(--ds-primary); }').length, 0);
});
test('el propio Design System no define degradados, desenfoques ni brillos', async () => {
  const fs = await import('node:fs');
  const dir = new URL('../src/app/design-system/', import.meta.url);
  for (const name of fs.readdirSync(dir).filter((f) => f.endsWith('.scss'))) {
    const source = fs.readFileSync(new URL(name, dir), 'utf8');
    assert.doesNotMatch(source, /gradient\s*\(|backdrop-filter|blur\s*\(|text-shadow/i, name);
  }
});
test('rechaza colores, tamaños y tokens desconocidos', () => {
  assert.ok(checkCss('.card { color: red; padding: 24px; }').length);
  assert.ok(checkCss('.card { color: var(--ds-inventado); }').length);
  assert.equal(checkCss('.card { padding: var(--ds-space-6); color: var(--ds-text); }').length, 0);
});
test('no permite rodear la regla con componentes inline ni imports internos', () => {
  assert.ok(
    checkTypescript('@Component({template: `<button>Hola</button>`}) class X {}', 'x.ts').length,
  );
  assert.ok(
    checkTypescript("import { DsButton } from './design-system/primitives';", 'x.ts').length,
  );
  assert.equal(checkTypescript("import { DsButton } from './design-system';", 'x.ts').length, 0);
});

test('temas y superficie de marca: texto 4.5:1, bordes y foco 3:1', async () => {
  const sass = await import('sass');
  const postcss = (await import('postcss')).default;
  const { fileURLToPath } = await import('node:url');
  const css = sass.compileString(
    "@use 'tokens' as t; .test-light { @include t.light; } .test-dark { @include t.dark; } .test-brand { @include t.brand; }",
    {
      loadPaths: [fileURLToPath(new URL('../src/app/design-system', import.meta.url))],
    },
  ).css;
  const luminance = (hex) => {
    const rgb = hex
      .replace('#', '')
      .match(/.{2}/g)
      .map((c) => parseInt(c, 16) / 255)
      .map((c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
    return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
  };
  const ratio = (a, b) => {
    const x = luminance(a),
      y = luminance(b);
    return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
  };
  let themes = 0;
  postcss.parse(css).walkRules((rule) => {
    if (!['.test-light', '.test-dark', '.test-brand'].includes(rule.selector)) return;
    themes++;
    const values = Object.fromEntries(
      rule.nodes
        .filter((n) => n.type === 'decl')
        .map((n) => [n.prop.replace('--ds-', ''), n.value]),
    );
    const surfaces = ['bg', 'surface', 'surface-muted'];
    const pairs = [
      ['text', 'surface-raised'],
      ['muted', 'surface-strong'],
      ['on-primary', 'primary'],
      ['on-primary', 'primary-hover'],
      ['on-inverse', 'inverse'],
      ...['text', 'muted', 'link', 'accent-text', 'success', 'warning', 'danger', 'info'].flatMap(
        (fg) => surfaces.map((bg) => [fg, bg]),
      ),
    ];
    for (const [fg, bg] of pairs)
      assert.ok(
        ratio(values[fg], values[bg]) >= 4.5,
        `${rule.selector}: ${fg}/${bg} no alcanza 4.5:1`,
      );
    for (const [fg, bg] of [
      ['control-border', 'surface'],
      ['focus', 'bg'],
      ['focus', 'surface'],
    ])
      assert.ok(ratio(values[fg], values[bg]) >= 3, `${rule.selector}: ${fg}/${bg} no alcanza 3:1`);
  });
  assert.equal(themes, 3);
});
