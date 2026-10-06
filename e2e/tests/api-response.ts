import type { Page, Response } from '@playwright/test';

export function apiResponse(page: Page, path: string, method = 'GET'): Promise<Response> {
  return page.waitForResponse(async response => {
    if (new URL(response.url()).pathname !== `/api/v1${path}` || response.request().method() !== method) {
      return false;
    }
    // Chromium libera el cuerpo al cambiar de documento. Leerlo al recibir la
    // respuesta permite usar json() después de que click()/goto() hayan terminado.
    await response.body();
    return true;
  });
}
