/** Compara origen y límites del path; evita enviar credenciales a prefijos parecidos. */
export function isApiRequest(requestUrl: string, apiBasePath: string, baseUri: string): boolean {
  try {
    const request = new URL(requestUrl, baseUri);
    const api = new URL(apiBasePath, baseUri);
    const path = api.pathname.replace(/\/+$/, '');
    return (
      request.origin === api.origin &&
      (request.pathname === path || request.pathname.startsWith(`${path}/`))
    );
  } catch {
    return false;
  }
}
