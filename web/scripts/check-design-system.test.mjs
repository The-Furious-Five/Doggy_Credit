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

test('temas: texto semántico 4.5:1 y bordes de controles 3:1', async () => {
  const sass = await import('sass');
  const postcss = (await import('postcss')).default;
  const { fileURLToPath } = await import('node:url');
  const css = sass.compileString(
    "@use 'tokens' as t; .test-light { @include t.light; } .test-dark { @include t.dark; }",
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
    if (!['.test-light', '.test-dark'].includes(rule.selector)) return;
    themes++;
    const values = Object.fromEntries(
      rule.nodes
        .filter((n) => n.type === 'decl')
        .map((n) => [n.prop.replace('--ds-', ''), n.value]),
    );
    const pairs = [
      ['text', 'bg'],
      ['text', 'surface'],
      ['muted', 'bg'],
      ['muted', 'surface'],
      ['muted', 'surface-muted'],
      ['on-primary', 'primary'],
      ['on-primary', 'primary-hover'],
      ...['success', 'warning', 'danger', 'info'].map((t) => [t, t + '-bg']),
    ];
    for (const [fg, bg] of pairs)
      assert.ok(
        ratio(values[fg], values[bg]) >= 4.5,
        `${rule.selector}: ${fg}/${bg} no alcanza 4.5:1`,
      );
    assert.ok(
      ratio(values['control-border'], values.surface) >= 3,
      `${rule.selector}: borde del control no alcanza 3:1`,
    );
  });
  assert.equal(themes, 2);
});
