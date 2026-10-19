"""E-04: configuración de releases y activación/rollback sin acceder a una VPS."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest

import yaml

ROOT = Path(__file__).resolve().parents[1]

# Ejecutar el shell real del healthcheck, simulando únicamente comandos externos.
FAKE_COMMAND = r'''
import json, os, pathlib, shutil, subprocess, sys
name = pathlib.Path(sys.argv[0]).name
args = sys.argv[1:]
with open(os.environ["TEST_COMMAND_LOG"], "a") as log:
    log.write(json.dumps({"command": name, "args": args, "cwd": os.getcwd()}) + "\n")
if name == "docker":
    if args[0] == "images":
        print("ghcr.io/example/doggycredit-api:new")
        print("ghcr.io/example/doggycredit-web:new")
    elif "pull" in args and os.environ.get("TEST_PULL_FAIL"):
        sys.exit(1)
    elif "up" in args and os.environ.get("TEST_STARTUP_FAIL") and pathlib.Path.cwd().name == "new":
        sys.exit(1)
    elif "exec" in args:
        index = args.index("exec")
        service = args[index + 2]
        if service.startswith("wiremock-"):
            env = dict(os.environ, TEST_SERVICE=service)
            sys.exit(subprocess.call(args[index + 3:], env=env))
        if service == "keycloak" and args[index + 3:] == ["bash", "-s"]:
            script = sys.stdin.read()
            if "doggycredit-mobile" not in script or os.environ.get("TEST_MOBILE_SYNC_FAIL"):
                sys.exit(1)
elif name == "wget":
    phase = "health" if args[-1].endswith("/__admin/health") else "data"
    failure = os.environ.get("TEST_SIM_FAILURE", "")
    service = os.environ["TEST_SERVICE"]
    if failure == service + ":" + phase:
        sys.exit(1)
    if phase == "data":
        document = "wrong-document" if failure == service + ":wrong" else "CI-DEMO-0001001"
        print(json.dumps({"numeroDocumento": document}))
elif name == "curl" and os.environ.get("TEST_API_FAIL"):
    sys.exit(1)
elif name == "find":
    if "-printf" in args:
        for path in pathlib.Path(args[0]).iterdir():
            if path.is_dir():
                print(str(path.stat().st_mtime) + " " + str(path))
    else:
        sys.exit(subprocess.call(["/usr/bin/find", *args]))
elif name in ("chmod", "rm"):
    # LOS-117: una release vieja con archivos de otro dueño (los temas de Keycloak).
    denied = os.environ.get("TEST_CLEANUP_DENIED")
    if denied and any(denied in arg for arg in args):
        print(name + ": Operation not permitted", file=sys.stderr)
        sys.exit(1)
    sys.exit(subprocess.call([shutil.which(name, path=os.environ['TEST_REAL_PATH']), *args]))
'''


class DeployTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name).resolve()
        self.bin = self.base / "bin"
        self.bin.mkdir()
        for command in ("docker", "wget", "curl", "find", "chmod", "rm"):
            executable = self.bin / command
            executable.write_text("#!/usr/bin/env python3\n" + FAKE_COMMAND)
            executable.chmod(0o755)
        self.log = self.base / "commands.jsonl"
        self.env = dict(os.environ, PATH=f"{self.bin}:{os.environ['PATH']}",
                        TEST_REAL_PATH=os.environ['PATH'],
                        DC_DEPLOY_BASE=str(self.base), TEST_COMMAND_LOG=str(self.log))
        (self.base / ".env").write_text("DC_MASTER_KEY=test-only\n")
        self.previous = self.make_release("previous")
        self.release = self.make_release("new")
        self.current = self.base / "current"
        self.current.symlink_to(self.previous, target_is_directory=True)

    def make_release(self, sha):
        release = self.base / "releases" / sha
        (release / "scripts").mkdir(parents=True)
        (release / "infra").mkdir()
        shutil.copy(ROOT / "scripts/deploy.sh", release / "scripts")
        shutil.copy(ROOT / "scripts/check_simulators.sh", release / "scripts")
        shutil.copy(ROOT / "scripts/ensure_deploy_env.sh", release / "scripts")
        shutil.copy(ROOT / "scripts/sync_mobile_client.sh", release / "scripts")
        (release / "infra/release.env").write_text(
            f"DC_API_IMAGE=ghcr.io/example/doggycredit-api:{sha}\n"
            f"DC_WEB_IMAGE=ghcr.io/example/doggycredit-web:{sha}\n"
            "DC_KEYCLOAK_ISSUER_URI=https://demo.example/auth/realms/doggycredit\n")
        return release

    def run_script(self, script, **env):
        return subprocess.run(["bash", f"scripts/{script}"], cwd=self.release,
                              env=dict(self.env, **env), text=True, capture_output=True)

    def commands(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()]

    def test_success_activates_release_after_both_simulators_and_api(self):
        result = self.run_script("deploy.sh")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(self.release, self.current.resolve())
        commands = self.commands()
        probes = [c["args"][-1] for c in commands if c["command"] == "wget"]
        self.assertEqual([
            "http://127.0.0.1:8080/__admin/health",
            "http://127.0.0.1:8080/bank/v1/clientes/CI-DEMO-0001001",
            "http://127.0.0.1:8080/__admin/health",
            "http://127.0.0.1:8080/wallet/v1/titulares/CI-DEMO-0001001",
        ], probes)
        up = next(c for c in commands if "up" in c["args"])
        self.assertIn("--no-build", up["args"])
        self.assertIn("--wait", up["args"])
        api = next(c for c in commands if c["command"] == "curl")
        last_probe = max(i for i, c in enumerate(commands) if c["command"] == "wget")
        self.assertGreater(commands.index(api), last_probe)

    def test_provider_failure_restores_previous_release_and_preserves_current(self):
        for failure in ("wiremock-bank:health", "wiremock-bank:data", "wiremock-bank:wrong",
                        "wiremock-wallet:health", "wiremock-wallet:data",
                        "wiremock-wallet:wrong"):
            with self.subTest(failure=failure):
                self.log.unlink(missing_ok=True)
                result = self.run_script("deploy.sh", TEST_SIM_FAILURE=failure)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual(self.previous, self.current.resolve())
                commands = self.commands()
                rollback = [c for c in commands if "up" in c["args"]][-1]
                self.assertEqual(str(self.previous), rollback["cwd"])
                self.assertIn("--wait", rollback["args"])
                self.assertIn("--no-build", rollback["args"])
                self.assertFalse(any(c["command"] == "curl" for c in commands))

    def test_first_release_failure_does_not_activate_current(self):
        self.current.unlink()
        result = self.run_script("deploy.sh", TEST_SIM_FAILURE="wiremock-bank:data")
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.current.exists())
        self.assertEqual(1, sum("up" in c["args"] for c in self.commands()))

    def test_api_failure_keeps_current_and_rolls_back(self):
        result = self.run_script("deploy.sh", TEST_API_FAIL="1")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(self.previous, self.current.resolve())
        rollback = [c for c in self.commands() if "up" in c["args"]][-1]
        self.assertEqual(str(self.previous), rollback["cwd"])

    def test_startup_failure_records_simulator_diagnostics_before_rollback(self):
        result = self.run_script("deploy.sh", TEST_STARTUP_FAIL="1")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(self.previous, self.current.resolve())
        commands = self.commands()
        logs = next(i for i, command in enumerate(commands) if "logs" in command["args"])
        rollback = next(i for i, command in enumerate(commands)
                        if "up" in command["args"] and command["cwd"] == str(self.previous))
        self.assertLess(logs, rollback)

    def test_mobile_client_failure_rolls_back_before_activating_release(self):
        result = self.run_script("deploy.sh", TEST_MOBILE_SYNC_FAIL="1")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(self.previous, self.current.resolve())
        rollback = [c for c in self.commands() if "up" in c["args"]][-1]
        self.assertEqual(str(self.previous), rollback["cwd"])
        self.assertFalse(any(c["command"] == "curl" for c in self.commands()))

    def test_deploy_generates_missing_platform_password_once_without_logging_it(self):
        result = self.run_script("deploy.sh")
        self.assertEqual(0, result.returncode, result.stderr)
        first = (self.base / ".env").read_text()
        password = first.split('DC_SEED_PLATFORM_ADMIN_PASSWORD=')[1].strip()
        self.assertRegex(password, r'^[0-9a-f]{64}$')
        self.assertNotIn(password, result.stdout + result.stderr)
        result = self.run_script("deploy.sh")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(first, (self.base / ".env").read_text())

    def test_existing_platform_password_is_preserved(self):
        env_file = self.base / ".env"
        original = 'DC_MASTER_KEY=test-only\nDC_SEED_PLATFORM_ADMIN_PASSWORD="existing-password"\n'
        env_file.write_text(original)
        result = self.run_script("deploy.sh")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(original, env_file.read_text())

    def test_empty_platform_password_is_initialized(self):
        for empty in ('', '   ', '""', "''", '# pendiente'):
            with self.subTest(empty=empty):
                env_file = self.base / ".env"
                env_file.write_text('DC_MASTER_KEY=test-only\nDC_SEED_PLATFORM_ADMIN_PASSWORD=' + empty + '\n')
                result = self.run_script("deploy.sh")
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertRegex(env_file.read_text().splitlines()[-1],
                                 r'^DC_SEED_PLATFORM_ADMIN_PASSWORD=[0-9a-f]{64}$')

    def test_pull_failure_leaves_running_release_untouched(self):
        result = self.run_script("deploy.sh", TEST_PULL_FAIL="1")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(self.previous, self.current.resolve())
        self.assertFalse(any("up" in c["args"] for c in self.commands()))

    def test_cleanup_keeps_current_previous_and_one_more_release(self):
        stale = [self.make_release(f"stale-{n}") for n in range(3)]
        result = self.run_script("deploy.sh")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(self.release.is_dir() and self.previous.is_dir())
        self.assertEqual(1, sum(release.is_dir() for release in stale))

    def test_old_release_that_cannot_be_deleted_does_not_fail_the_deploy(self):
        """LOS-117: el job salía en rojo con la release nueva ya activa."""
        stale = [self.make_release(f"stale-{n}") for n in range(3)]
        result = self.run_script("deploy.sh", TEST_CLEANUP_DENIED="stale-")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(self.release, self.current.resolve())
        self.assertTrue(all(release.is_dir() for release in stale))
        self.assertEqual(2, result.stderr.count("no se pudo borrar la release vieja"), result.stderr)
        # La limpieza de imágenes sigue después del aviso.
        self.assertTrue(any(c["command"] == "docker" and c["args"][:1] == ["images"] for c in self.commands()))

    def test_healthcheck_forwards_compose_options_to_both_services(self):
        options = ["--env-file", str(self.base / ".env"), "--env-file", "infra/release.env",
                   "-f", "infra/compose.yaml", "-f", "infra/compose.prod.yaml"]
        result = subprocess.run(["bash", "scripts/check_simulators.sh", *options],
                                cwd=self.release, env=self.env, text=True, capture_output=True)
        self.assertEqual(0, result.returncode, result.stderr)
        calls = [c for c in self.commands() if c["command"] == "docker"]
        self.assertEqual(2, len(calls))
        for call in calls:
            self.assertEqual(["compose", *options], call["args"][:1 + len(options)])


class ReleasePackageTest(unittest.TestCase):
    def test_ci_archive_renders_tagged_production_release_with_private_readonly_simulators(self):
        workflow = yaml.safe_load((ROOT / ".github/workflows/ci-cd.yml").read_text())
        packaging = next(step["run"] for step in workflow["jobs"]["deploy"]["steps"]
                         if step.get("name", "").startswith("Empaquetar release"))
        with tempfile.TemporaryDirectory() as temporary:
            stage = Path(temporary).resolve() / "stage"
            stage.mkdir()
            for directory in ("infra", "simulators", "scripts"):
                shutil.copytree(ROOT / directory, stage / directory,
                                ignore=shutil.ignore_patterns("__pycache__"))
            if sys.platform == 'darwin':
                mapping = next((stage / 'simulators/wiremock/wallet/mappings').glob('*.json'))
                subprocess.run(['xattr', '-w', 'com.doggycredit.test', 'metadata-test', str(mapping)], check=True)
            env = dict(os.environ, REPO="ghcr.io/example", GITHUB_SHA="test-sha",
                       DC_KEYCLOAK_ISSUER_URI="https://demo.example/auth/realms/doggycredit",
                       DC_MASTER_KEY="configuration-test-only")
            subprocess.run(["bash", "-euc", packaging], cwd=stage, env=env, check=True)
            extracted = Path(temporary).resolve() / "release"
            extracted.mkdir()
            with tarfile.open(stage / "release.tar.gz") as archive:
                self.assertFalse(any(part.startswith('._') for member in archive.getmembers()
                                     for part in Path(member.name).parts), 'No incluir AppleDouble en Linux')
                for member in archive.getmembers():
                    if '/mappings/' in member.name and member.name.endswith('.json'):
                        json.load(archive.extractfile(member))
                archive.extractall(extracted, filter="data")
            self.assertTrue((extracted / "scripts/check_simulators.sh").is_file())
            self.assertTrue((extracted / "scripts/ensure_deploy_env.sh").is_file())
            self.assertTrue((extracted / "scripts/sync_mobile_client.sh").is_file())
            self.assertTrue((extracted / "infra/keycloak/doggycredit-mobile.json").is_file())
            result = subprocess.run([
                "docker", "compose", "--env-file", str(ROOT / ".env.example"),
                "--env-file", "infra/release.env", "-f", "infra/compose.yaml",
                "-f", "infra/compose.prod.yaml", "config", "--format", "json",
            ], cwd=extracted, env=env, text=True, capture_output=True)
            self.assertEqual(0, result.returncode, result.stderr)
            services = json.loads(result.stdout)["services"]
            mobile = next(v for v in services['keycloak']['volumes']
                          if v['target'] == '/opt/keycloak/doggycredit-mobile.json')
            self.assertTrue(mobile['read_only'])
            self.assertFalse(mobile.get('bind', {}).get('create_host_path', False))
            self.assertEqual(extracted / 'infra/keycloak/doggycredit-mobile.json', Path(mobile['source']))
            # BaseLoader admite las etiquetas Compose (!reset/!override) sin
            # interpretarlas; sus escalares se conservan como texto.
            production = yaml.load((extracted / "infra/compose.prod.yaml").read_text(),
                                   Loader=yaml.BaseLoader)
            for service, source in (("wiremock-bank", "bank"), ("wiremock-wallet", "wallet")):
                simulator = services[service]
                self.assertFalse(simulator.get("ports"))
                self.assertEqual("wiremock/wiremock:3.9.1", simulator["image"])
                volume = next(v for v in simulator["volumes"] if v["target"] == "/home/wiremock")
                self.assertTrue(volume["read_only"])
                # compose-go antiguo serializa false con omitempty. Verificar
                # también el YAML para exigir false explícito en toda versión.
                configured = next(v for v in production["services"][service]["volumes"]
                                  if v["target"] == "/home/wiremock")
                self.assertEqual("false", configured["bind"]["create_host_path"])
                self.assertFalse(volume.get("bind", {}).get("create_host_path", False))
                self.assertEqual(extracted / "simulators/wiremock" / source, Path(volume["source"]))
                self.assertTrue(list(Path(volume["source"]).glob("mappings/*.json")))
                self.assertEqual("service_healthy", services["doggycredit-api"]["depends_on"][service]["condition"])
            api = services["doggycredit-api"]
            for dependency in ("postgres", "keycloak"):
                self.assertEqual("service_healthy", api["depends_on"][dependency]["condition"])
            self.assertEqual("http://wiremock-bank:8080", api["environment"]["DC_INTEGRATION_BANK_URL"])
            self.assertEqual("http://wiremock-wallet:8080", api["environment"]["DC_INTEGRATION_WALLET_URL"])
            for name, image in (("doggycredit-api", "doggycredit-api"), ("caddy", "doggycredit-web")):
                self.assertEqual(f"ghcr.io/example/{image}:test-sha", services[name]["image"])
                self.assertFalse(services[name].get("build"))


if __name__ == "__main__":
    unittest.main()
