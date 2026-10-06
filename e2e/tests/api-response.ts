import type { APIResponse, Page, Request, Route } from '@playwright/test';

export type CapturedResponse = Pick<APIResponse, 'url' | 'status' | 'headers' | 'body' | 'json'> & {
  request(): Pick<Request, 'method' | 'headerValue'>;
};

/** Captura bytes reales antes de que el navegador pueda navegar y descartarlos. */
export async function apiResponse(
  page: Page,
  path: string,
  action: () => Promise<unknown>,
  method = 'GET',
  timeout = 30_000,
): Promise<CapturedResponse> {
  const origin = new URL(page.url()).origin;
  const matches = (url: URL) => url.origin === origin && url.pathname === `/api/v1${path}`;
  let resolve!: (response: CapturedResponse) => void;
  let reject!: (error: Error) => void;
  const captured = new Promise<CapturedResponse>((ok, fail) => { resolve = ok; reject = fail; });
  // La captura también puede fallar durante el registro de la ruta.
  void captured.catch(() => {});
  let claimed = false;
  const handler = async (route: Route) => {
    const request = route.request();
    if (request.method() !== method || claimed) {
      await route.fallback();
      return;
    }
    claimed = true;
    try {
      const requestHeaders = await request.allHeaders();
      // Una sola petición real, sin reintentar POST ni ocultar redirecciones.
      const upstream = await route.fetch({ maxRedirects: 0, maxRetries: 0, timeout });
      try {
        const body = Buffer.from(await upstream.body());
        const headers = { ...upstream.headers() };
        const url = upstream.url();
        const status = upstream.status();
        const response: CapturedResponse = {
          url: () => url,
          status: () => status,
          headers: () => ({ ...headers }),
          body: async () => Buffer.from(body),
          json: async () => JSON.parse(body.toString('utf8')),
          request: () => ({
            method: () => method,
            headerValue: async name => requestHeaders[name.toLowerCase()] ?? null,
          }),
        };
        // Status, cabeceras y cuerpo proceden de la API, sin datos sustitutos.
        await route.fulfill({ response: upstream, body });
        resolve(response);
      } finally {
        await upstream.dispose();
      }
    } catch {
      // Los errores de route.fetch pueden contener cabeceras con credenciales.
      reject(new Error(`No se pudo capturar la respuesta real de ${method} ${path}.`));
      await route.abort('failed').catch(() => {});
    }
  };

  // La ruta debe estar registrada antes de disparar el login, clic o goto.
  await page.route(matches, handler);
  const timer = setTimeout(() => reject(new Error(`No llegó una respuesta de ${method} ${path} en ${timeout} ms.`)), timeout);
  try {
    const [, response] = await Promise.all([Promise.resolve().then(action), captured]);
    return response;
  } finally {
    clearTimeout(timer);
    if (!page.isClosed()) await page.unroute(matches, handler);
  }
}
