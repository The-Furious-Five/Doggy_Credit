import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
import { parseTemplate } from '@angular/compiler';
import * as sass from 'sass';
import postcss from 'postcss';

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const src = path.join(web, 'src');
const system = path.join(src, 'app/design-system');
const generated = path.join(src, 'app/api');
const inside = (file, root) => file === root || file.startsWith(root + path.sep);
const walk = (dir) =>
  fs
    .readdirSync(dir, { withFileTypes: true })
    .flatMap((entry) =>
      entry.isDirectory() ? walk(path.join(dir, entry.name)) : [path.join(dir, entry.name)],
    );
const tokens = new Set(
  walk(system)
    .filter((f) => /\.scss$/.test(f))
    .flatMap((f) =>
      [...fs.readFileSync(f, 'utf8').matchAll(/(--ds-[\w-]+)\s*:/g)].map((m) => m[1]),
    ),
);

export function checkTemplate(source, file = 'template') {
  const failures = [];
  const parsed = parseTemplate(source, file);
  if (parsed.errors?.length) return parsed.errors.map((e) => `${file}: ${e.msg}`);
  function visit(node) {
    if (!node || typeof node !== 'object') return;
    if (node.name && node.attributes && node.inputs) {
      const attributes = [...node.attributes, ...node.inputs];
      const names = new Set(attributes.map((a) => a.name));
      const tag = node.name.toLowerCase();
      const markers = {
        button: ['dsButton'],
        a: ['dsLink'],
        input: ['dsInput', 'dsCheckbox', 'dsRadio'],
        textarea: ['dsInput'],
        select: ['dsSelect'],
        table: ['dsTable'],
      };
      if (markers[tag] && !markers[tag].some((n) => names.has(n)))
        failures.push(`${file}: <${tag}> requiere ${markers[tag].join(' / ')}.`);
      if (tag === 'input') {
        const type = node.attributes.find((a) => a.name === 'type')?.value;
        if (
          (names.has('dsCheckbox') && type !== 'checkbox') ||
          (names.has('dsRadio') && type !== 'radio')
        )
          failures.push(
            `${file}: declara el type nativo para que Angular Forms use el ValueAccessor correcto.`,
          );
        if (
          (type === 'checkbox' && !names.has('dsCheckbox')) ||
          (type === 'radio' && !names.has('dsRadio'))
        )
          failures.push(`${file}: usa dsCheckbox / dsRadio para controles de selección.`);
      }
      if (tag === 'dialog' || tag === 'svg')
        failures.push(
          `${file}: usa ${tag === 'dialog' ? 'DsDialog' : 'DsIcon'} de la API pública.`,
        );
      if (
        attributes.some((a) => ['style', 'ngStyle', 'innerHTML'].includes(a.name) || a.type === 3)
      )
        failures.push(
          `${file}: estilos inline/HTML dinámico no permitidos; utiliza componentes y tokens.`,
        );
      if (
        node.attributes.some(
          (a) => a.name === 'role' && ['button', 'checkbox', 'switch', 'tab'].includes(a.value),
        )
      )
        failures.push(
          `${file}: utiliza un control nativo del Design System, no role="${node.attributes.find((a) => a.name === 'role').value}".`,
        );
    }
    for (const key of [
      'children',
      'branches',
      'cases',
      'empty',
      'placeholder',
      'loading',
      'error',
    ]) {
      const value = node[key];
      if (Array.isArray(value)) value.forEach(visit);
      else if (value) visit(value);
    }
  }
  parsed.nodes.forEach(visit);
  return failures;
}

export function checkCss(source, file = 'styles', compile = false) {
  const failures = [];
  if (/::ng-deep|!important|@import|@keyframes/.test(source))
    failures.push(`${file}: no se permiten escapes de estilos ni animaciones propias.`);
  if (/gradient\s*\(|backdrop-filter|\bfilter\s*:|mix-blend-mode/i.test(source))
    failures.push(
      `${file}: la identidad usa colores sólidos; sin degradados, desenfoques ni mezclas.`,
    );
  let css;
  try {
    css = compile
      ? sass.compileString(source, { loadPaths: [path.dirname(file), src], style: 'expanded' }).css
      : source;
  } catch (error) {
    return [...failures, `${file}: ${error.message}`];
  }
  try {
    postcss.parse(css).walkDecls((decl) => {
      const prop = decl.prop.toLowerCase();
      let value = decl.value;
      if (prop.startsWith('--'))
        failures.push(`${file}: define los tokens en design-system, no ${prop}.`);
      value = value.replace(/var\((--ds-[\w-]+)\)/g, (_, token) => {
        if (!tokens.has(token)) failures.push(`${file}: token desconocido ${token}.`);
        return '';
      });
      if (
        /#|(?:rgb|hsl|oklch|lab|color-mix)\s*\(|\b\d*\.?\d+(?:px|rem|em|ms|s)\b|var\(/i.test(value)
      )
        failures.push(
          `${file}: ${prop}: ${decl.value} necesita tokens existentes, sin valores visuales locales.`,
        );
      const visual =
        /^(color|background.*|font.*|line-height|letter-spacing|border.*|outline.*|box-shadow|text-shadow|opacity|z-index|transition.*|animation.*|padding.*|margin.*|gap|row-gap|column-gap)$/;
      if (
        visual.test(prop) &&
        !/^(?:\s|0|auto|none|inherit|initial|unset|normal|transparent|currentColor|solid|dashed)*$/i.test(
          value,
        )
      )
        failures.push(`${file}: ${prop} debe componerse con tokens del Design System.`);
    });
  } catch (error) {
    failures.push(`${file}: ${error.message}`);
  }
  return failures;
}

export function checkTypescript(source, file) {
  const failures = [];
  const tree = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true);
  const inspect = (node) => {
    if (ts.isImportDeclaration(node) && ts.isStringLiteral(node.moduleSpecifier)) {
      const spec = node.moduleSpecifier.text;
      if (spec.includes('design-system/') && !spec.endsWith('design-system/index'))
        failures.push(`${file}: importa desde la API pública design-system.`);
    }
    if (ts.isPropertyAssignment(node)) {
      const name = node.name.getText(tree).replace(/['"]/g, '');
      if (name === 'template') {
        if (ts.isStringLiteralLike(node.initializer))
          failures.push(...checkTemplate(node.initializer.text, file));
        else failures.push(`${file}: el template debe ser estático y verificable.`);
      }
      if (name === 'styles') {
        const values = ts.isArrayLiteralExpression(node.initializer)
          ? node.initializer.elements
          : [node.initializer];
        for (const value of values) {
          if (ts.isStringLiteralLike(value)) failures.push(...checkCss(value.text, file));
          else failures.push(`${file}: estilos no verificables.`);
        }
      }
      if (
        name === 'host' &&
        /(?:\[style|['"]style['"]|ngStyle)/.test(node.initializer.getText(tree))
      )
        failures.push(`${file}: estilos de host locales no permitidos.`);
      if (name === 'encapsulation' && /None/.test(node.initializer.getText(tree)))
        failures.push(`${file}: no desactives la encapsulación de estilos.`);
    }
    if (
      ts.isCallExpression(node) &&
      /(?:setStyle|setProperty)$/.test(node.expression.getText(tree))
    )
      failures.push(`${file}: usa tokens/componentes, no mutaciones imperativas de estilo.`);
    ts.forEachChild(node, inspect);
  };
  inspect(tree);
  return failures;
}

export function checkProject() {
  const failures = [];
  for (const file of walk(src)) {
    if (file.endsWith('.spec.ts') || inside(file, generated)) continue;
    if (inside(file, system)) {
      if (file.endsWith('.ts')) {
        const tree = ts.createSourceFile(
          file,
          fs.readFileSync(file, 'utf8'),
          ts.ScriptTarget.Latest,
          true,
        );
        for (const node of tree.statements) {
          if (!ts.isImportDeclaration(node) || !ts.isStringLiteral(node.moduleSpecifier)) continue;
          const spec = node.moduleSpecifier.text;
          if (
            (spec.startsWith('.') && !inside(path.resolve(path.dirname(file), spec), system)) ||
            (!spec.startsWith('.') &&
              !['@angular/core', '@angular/common', '@angular/forms'].includes(spec))
          )
            failures.push(`${file}: el Design System no puede depender del negocio (${spec}).`);
        }
      }
      continue;
    }
    const source = fs.readFileSync(file, 'utf8');
    if (file.endsWith('.html')) failures.push(...checkTemplate(source, file));
    if (file.endsWith('.ts')) failures.push(...checkTypescript(source, file));
    if (/\.(scss|css)$/.test(file)) {
      if (file === path.join(src, 'styles.scss')) {
        const content = source.replace(/\/\/[^\n]*/g, '').trim();
        if (content !== "@use './app/design-system/styles';")
          failures.push(
            `${file}: el único estilo global permitido es la entrada del Design System.`,
          );
      } else failures.push(...checkCss(source, file, file.endsWith('.scss')));
    }
  }
  return [...new Set(failures)];
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const failures = checkProject();
  if (failures.length) {
    console.error('Design System: corrige estas divergencias:\n' + failures.join('\n'));
    process.exitCode = 1;
  } else console.log('Design System: componentes, tokens e imports conformes.');
}
