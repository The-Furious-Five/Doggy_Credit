import { test, expect, adminOf, login, openFromMenu, connectorCard } from './support';

test('E-15: credenciales propias, rotación, foco y eliminación en escritorio y móvil', async ({ page }) => {
  await login(page, adminOf('PRO'));
  await openFromMenu(page, 'Integraciones');
  const card = connectorCard(page, 'Buró de crédito nacional');
  const trigger = card.getByRole('button', { name: 'Credenciales propias: Buró de crédito nacional' });
  await trigger.click();
  const dialog = page.getByRole('dialog', { name: 'Credenciales propias · Buró de crédito nacional' });
  const input = dialog.getByLabel('Nueva clave API');
  try {
    await expect(input).toHaveValue('');
    await input.fill('e2e-dummy-key-1357');
    await dialog.getByRole('button', { name: 'Guardar clave' }).click();
    await expect(dialog.getByText('1357', { exact: true })).toBeVisible();
    await expect(input).toHaveValue('');
    await expect(dialog).not.toContainText('e2e-dummy-key-1357');
    for (const width of [1440, 768, 375]) {
      await page.setViewportSize({ width, height: 900 });
      const bounds = await dialog.boundingBox();
      expect(bounds!.x).toBeGreaterThanOrEqual(0);
      expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(width);
      expect(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
      // Capturas locales opcionales; las de errores las administra Playwright en CI.
      if (!process.env['CI']) await page.screenshot({ path: `/tmp/e15-dialog-${width}.png` });
    }
    await page.keyboard.press('Escape');
    await expect(dialog).not.toBeVisible();
    await expect(trigger).toBeFocused();
    await trigger.click();
    await expect(input).toHaveValue('');
    await expect(dialog.getByText('1357', { exact: true })).toBeVisible();
    await input.fill('e2e-dummy-key-2468');
    await dialog.getByRole('button', { name: 'Guardar clave' }).click();
    await expect(dialog.getByText('2468', { exact: true })).toBeVisible();
    await dialog.getByRole('button', { name: 'Eliminar credenciales propias' }).click();
    await expect(dialog.getByText('Se utilizan las credenciales de DoggyCredit.')).toBeVisible();
  } finally {
    // La base se reutiliza entre escenarios; no dejar la clave sintética si falla una aserción.
    if (await dialog.isVisible()) {
      const remove = dialog.getByRole('button', { name: 'Eliminar credenciales propias' });
      if (await remove.isVisible()) {
        await remove.click();
        await expect(dialog.getByText('Se utilizan las credenciales de DoggyCredit.')).toBeVisible();
      }
      await page.keyboard.press('Escape');
    }
  }
  const bank = connectorCard(page, 'Core bancario');
  await bank.getByRole('button', { name: 'Credenciales propias: Core bancario' }).click();
  const bankDialog = page.getByRole('dialog', { name: 'Credenciales propias · Core bancario' });
  await bankDialog.getByRole('combobox', { name: 'Proveedor bancario' }).click();
  await page.getByRole('option', { name: 'Segundo banco' }).click();
  await expect(bankDialog.getByLabel('Nueva clave API')).toBeVisible();
  await expect(bankDialog.getByLabel('Nueva clave API')).toHaveValue('');
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' });
  for (const width of [1440, 375]) {
    await page.setViewportSize({ width, height: 900 });
    const bounds = await bankDialog.boundingBox();
    expect(bounds!.x).toBeGreaterThanOrEqual(0);
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(width);
    if (!process.env['CI']) await page.screenshot({ path: `/tmp/e15-bank-dark-${width}.png` });
  }
});
