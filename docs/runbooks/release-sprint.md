# Release de sprint en la VM (E-13 / LOS-124)

La Sprint Review se hace sobre <https://doggycredit.los5furiosos.cloud>, con la
versión de `main` que despliega el CI. Nunca se despliega desde un laptop.
El pipeline está descrito en [despliegue-vps.md](despliegue-vps.md).

## Cómo se sabe qué versión está en línea

La imagen web publica el commit de la release en `/release.json`, sin caché:

```bash
curl -s https://doggycredit.los5furiosos.cloud/release.json
# {"release":"<sha de main>"}
```

Al final de cada despliegue, el job **7 · Desplegar** ejecuta
`scripts/smoke_release.py` desde Internet. El job falla si algo de esto no se cumple:

| Comprobación | Qué demuestra |
| --- | --- |
| `/actuator/health` = `UP` | la API arrancó |
| `/release.json` = SHA del commit | está en línea la versión nueva, no la anterior |
| `/` y su `main-*.js`, con `X-Frame-Options DENY` | la SPA de esa release carga completa |
| Issuer OIDC = `https://DOMINIO/auth/realms/doggycredit` | Keycloak quedó con el dominio público |
| `/api/v1/tenant/actual` sin token → 401 RFC 9457 | la API está enrutada y exige autenticación |
| `/actuator/env` → 404 | no se expone más que la salud |
| HSTS con `max-age` > 0 en `/auth/` | el ajuste de localhost (PR #42) no llegó a producción |

Solo hace GET: no inicia sesión, no crea datos y no consume cuota. El job **6**
corre el mismo script contra el Compose de cada PR (`--release local`), así que
el script no se estrena en producción. Para repetirlo a mano:

```bash
python scripts/smoke_release.py https://doggycredit.los5furiosos.cloud \
  --release "$(git rev-parse origin/main)"
```

## Checklist del día de la review

1. **Congelar `main`.** Fusionar las historias del sprint que entran en la review.
   Cada push a `main` vuelve a desplegar y reinicia la API, así que **no se
   fusiona nada durante la review**.
2. **CI de `main` en verde.** En Actions, la última ejecución de *CI/CD* sobre
   `main` debe tener los 7 jobs en verde, incluido el smoke test del job 7.
   Si el último push no desplegó (por ejemplo, porque un job falló y se
   corrigió después), usar *Run workflow* sobre `main`.
3. **Versión en línea.** `curl …/release.json` debe devolver el SHA de
   `git rev-parse origin/main`.
4. **Recorrido de la demo sobre producción**, con los usuarios de demo del
   tenant Gratis y del Pro (las credenciales de producción no están en el repo):
   - login y Resumen con plan y cuota;
   - Nueva evaluación → resultado → perfil del cliente (Resumen, Score, Fuentes);
   - Integraciones, Analítica y Desarrolladores según el plan;
   - cada historia del sprint marcada *Done* en Linear, en su vista.
5. **Registrar** en LOS-124 (o en la issue de release del sprint siguiente) el
   SHA en línea y el enlace a la ejecución del job 7.

## Si algo falla

- **El job 7 falla antes de cambiar `current`:** `deploy.sh` ya restauró la
  release anterior. El sitio sigue en la versión previa (lo confirma
  `/release.json`).
- **El smoke test falla después del despliegue:** el job queda en rojo y el
  log indica qué comprobación falló. Corregir con un PR, o volver a la versión
  anterior con `git revert` del merge en `main`. Las dos vías vuelven a pasar
  por el CI.
- **Emergencia sin CI:** en el VPS, `cd /srv/doggycredit/releases/<sha anterior> &&
  bash scripts/deploy.sh`. Requiere acceso a la VM. Las migraciones Flyway no se
  revierten.
