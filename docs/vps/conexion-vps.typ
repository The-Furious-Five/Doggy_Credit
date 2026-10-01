#set document(title: "DoggyCredit — Conexión al VPS")
#set page(paper: "a4", margin: 2cm, numbering: "1")
#set text(lang: "es", size: 10.5pt)
#set heading(numbering: "1.")
#show raw.where(block: true): block.with(fill: luma(240), inset: 8pt, radius: 4pt, width: 100%)
#show link: underline

#align(center)[
  #text(18pt, weight: "bold")[DoggyCredit — Conexión al VPS]
  #v(-4pt)
  #text(10pt, fill: luma(90))[Acceso administrativo y usuario de despliegue para GitHub Actions]
]

#v(8pt)

#table(
  columns: (auto, 1fr),
  inset: 6pt,
  stroke: luma(180),
  [*Dato*], [*Valor*],
  [IP pública], [`98.91.235.248`],
  [Puerto SSH], [`22`],
  [Usuario administrador], [`ubuntu` (con `sudo`, equivale a root)],
  [Usuario de despliegue], [`deploy` — contraseña `deploy123`],
  [Llave privada de `ubuntu`], [`los-5-furiosos.pem`],
  [Llave privada de `deploy`], [`los5furiosos-deploy` (pública: `los5furiosos-deploy.pub`)],
  [Directorio de la app], [`/srv/doggycredit`],
)

#block(fill: rgb("#fff4e5"), inset: 8pt, radius: 4pt, width: 100%)[
  *Seguridad:* este documento contiene una contraseña. No publicar el repositorio
  ni el PDF fuera del equipo. La contraseña `deploy123` es débil: cambiarla
  (`sudo passwd deploy`) en cuanto el entorno deje de ser de pruebas. GitHub
  Actions *no* usa la contraseña, sino una clave SSH (sección 4).
]

= Requisitos en tu máquina

- Cliente SSH: incluido en Linux, macOS y Windows 10/11 (PowerShell o Git Bash).
- La clave privada del usuario `ubuntu` (archivo `.pem` entregado al crear el
  servidor). Pedirla al responsable de infraestructura; nunca subirla al repo.

En Linux, macOS o Git Bash la clave debe tener permisos restringidos:

```bash
chmod 400 ~/.ssh/los-5-furiosos.pem
```

En Windows (PowerShell), si SSH rechaza la clave por permisos:

```powershell
icacls $HOME\.ssh\los-5-furiosos.pem /inheritance:r /grant:r "$($env:USERNAME):R"
```

= Conectarse como administrador (`ubuntu`)

```bash
ssh -i ~/.ssh/los-5-furiosos.pem ubuntu@98.91.235.248
```

La primera vez SSH muestra la huella del servidor; aceptar con `yes` solo si
coincide con la que entregó el responsable. Para operar como root:

```bash
sudo -i        # shell de root
exit           # volver a ubuntu
```

Atajo opcional en `~/.ssh/config` para escribir solo `ssh doggy`:

```
Host doggy
    HostName 98.91.235.248
    User ubuntu
    IdentityFile ~/.ssh/los-5-furiosos.pem
```

= Crear el usuario `deploy` (una sola vez, como `ubuntu`)

```bash
sudo adduser --gecos "" deploy            # pide la contraseña: deploy123
sudo usermod -aG docker deploy            # puede usar Docker sin sudo
sudo mkdir -p /srv/doggycredit/releases
sudo chown -R deploy:deploy /srv/doggycredit
```

`deploy` *no* va en el grupo `sudo`: solo necesita Docker y `/srv/doggycredit`.
Requisitos previos del servidor (Docker Engine, Docker Compose ≥ 2.24, `curl`,
puerto 443 abierto) y el archivo `/srv/doggycredit/.env` se describen en
`docs/runbooks/despliegue-vps.md`.

Probar el usuario:

```bash
sudo -iu deploy
docker ps          # debe responder sin error de permisos
exit
```

== Entrar como `deploy` con contraseña

Ubuntu en la nube suele tener desactivado el acceso SSH por contraseña. Para
habilitarlo solo para `deploy`:

```bash
sudo tee /etc/ssh/sshd_config.d/60-deploy.conf <<'EOF'
Match User deploy
    PasswordAuthentication yes
EOF
sudo sshd -t && sudo systemctl reload ssh
```

Luego, desde tu máquina: `ssh deploy@98.91.235.248` (contraseña `deploy123`).
No es necesario para GitHub Actions; omitir este paso si no se requiere.

= Acceso de GitHub Actions con clave SSH

El job `deploy` de `.github/workflows/ci-cd.yml` se conecta con
`ssh -o BatchMode=yes`, que *no admite contraseñas*: usa una clave dedicada.

+ Generar el par de claves en tu máquina (sin passphrase):
  ```bash
  ssh-keygen -t ed25519 -C "github-actions-deploy" -f los5furiosos-deploy -N ""
  ```
+ Autorizar la clave pública en el VPS (como `ubuntu`):
  ```bash
  sudo -iu deploy mkdir -p -m 700 .ssh
  echo "CONTENIDO_DE_los5furiosos-deploy.pub" | sudo -iu deploy tee -a .ssh/authorized_keys
  sudo -iu deploy chmod 600 .ssh/authorized_keys
  ```
+ Obtener la huella del servidor (verificarla contra la del VPS):
  ```bash
  ssh-keyscan -t ed25519 98.91.235.248
  ```
+ Probar desde tu máquina: `ssh -i los5furiosos-deploy deploy@98.91.235.248 docker ps`.
+ En GitHub: *Settings → Environments → production* cargar:

#table(
  columns: (auto, auto, 1fr),
  inset: 6pt,
  stroke: luma(180),
  [*Tipo*], [*Nombre*], [*Valor*],
  [Secret], [`VPS_HOST`], [`98.91.235.248`],
  [Secret], [`VPS_USER`], [`deploy`],
  [Secret], [`VPS_SSH_KEY`], [contenido completo del archivo `los5furiosos-deploy` (privada)],
  [Secret], [`VPS_KNOWN_HOSTS`], [salida de `ssh-keyscan` del paso 3],
  [Variable], [`DC_KEYCLOAK_ISSUER_URI`], [`https://DOMINIO/auth/realms/doggycredit`],
)

6. Borrar `los5furiosos-deploy` y `los5furiosos-deploy.pub` de tu máquina una vez cargados.

GHCR usa el `GITHUB_TOKEN` del job; no hace falta un token personal.

= Comandos útiles en el VPS

```bash
# Estado de los contenedores (como deploy)
cd /srv/doggycredit/current
docker compose --env-file ../../.env --env-file infra/release.env \
  -f infra/compose.yaml -f infra/compose.prod.yaml ps

# Logs de la API
docker compose --env-file ../../.env --env-file infra/release.env \
  -f infra/compose.yaml -f infra/compose.prod.yaml logs -f doggycredit-api

# Grafana (desde tu máquina; luego abrir http://localhost:3300)
ssh -i ~/.ssh/los-5-furiosos.pem -L 3300:127.0.0.1:3300 ubuntu@98.91.235.248
```

= Problemas frecuentes

#table(
  columns: (auto, 1fr),
  inset: 6pt,
  stroke: luma(180),
  [*Síntoma*], [*Causa / solución*],
  [`Permission denied (publickey)`], [Clave equivocada o no está en `authorized_keys` del usuario.],
  [`UNPROTECTED PRIVATE KEY FILE`], [Permisos abiertos en el `.pem`: aplicar `chmod 400` / `icacls`.],
  [`Connection timed out`], [El firewall / grupo de seguridad no permite el puerto 22 desde tu IP.],
  [`REMOTE HOST IDENTIFICATION HAS CHANGED`], [El servidor se recreó: `ssh-keygen -R 98.91.235.248` y actualizar `VPS_KNOWN_HOSTS`.],
  [`permission denied ... docker.sock`], [`deploy` no está en el grupo `docker`, o falta cerrar y abrir sesión.],
)
