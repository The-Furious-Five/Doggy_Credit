import { adminOf, expect, login, openFromMenu, test } from './support';

test('FREE: analítica avanzada, integraciones extra y webhooks quedan bloqueados', async ({ page }) => {
  await login(page, adminOf('FREE'));

  await openFromMenu(page, 'Analítica');
  await expect(page.getByRole('heading', { name: 'Analítica', level: 1 })).toBeVisible();
  await expect(page.getByText('Analítica avanzada disponible con Pro')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Recomendaciones por producto' })).toHaveCount(0);

  await openFromMenu(page, 'Integraciones');
  const bank = page.locator('ds-card').filter({ has: page.getByRole('heading', { name: 'Core bancario' }) });
  await expect(bank.getByText('Plan Pro', { exact: true })).toBeVisible();
  // El buró ocupa la única integración del plan: el backend rechaza la segunda (409 PLAN_LIMIT_REACHED).
  await bank.getByRole('button', { name: 'Conectar: Core bancario' }).click();
  await expect(page.getByText('Tu plan Gratis incluye 1 integración')).toBeVisible();
  await expect(bank.getByText('Plan Pro', { exact: true })).toBeVisible();

  await openFromMenu(page, 'Desarrolladores');
  await expect(page.getByText('Webhooks disponibles con Pro')).toBeVisible();
  await expect(page.getByRole('textbox', { name: 'URL del receptor' })).toHaveCount(0);
});

test('PRO: analítica avanzada, integraciones y webhooks disponibles', async ({ page }) => {
  await login(page, adminOf('PRO'));

  await openFromMenu(page, 'Analítica');
  await expect(page.getByRole('heading', { name: 'Recomendaciones por producto' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Modelos', exact: true })).toBeVisible();
  await expect(page.getByText('Analítica avanzada disponible con Pro')).toHaveCount(0);

  await openFromMenu(page, 'Integraciones');
  await expect(page.getByRole('heading', { name: 'Core bancario' })).toBeVisible();
  await expect(page.locator('ds-card').getByText('Plan Pro', { exact: true })).toHaveCount(0);

  await openFromMenu(page, 'Desarrolladores');
  await expect(page.getByRole('textbox', { name: 'URL del receptor' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Guardar Webhooks' })).toBeVisible();
  await expect(page.getByText('Webhooks disponibles con Pro')).toHaveCount(0);
});
