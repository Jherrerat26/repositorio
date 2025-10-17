#!/usr/bin/env python3
import subprocess
import sys
import datetime


def run(cmd, check=True):
    """Ejecuta un comando y devuelve el resultado, imprime salida útil."""
    try:
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    except Exception as e:
        print(f"Error ejecutando {cmd}: {e}")
        sys.exit(1)
    print(f"$ {' '.join(cmd)}")
    if res.stdout:
        print(res.stdout.strip())
    if check and res.returncode != 0:
        print(f"ERROR: el comando falló con código {res.returncode}")
        sys.exit(res.returncode)
    return res


def is_git_repo():
    res = run(["git", "rev-parse", "--is-inside-work-tree"], check=False)
    return res.returncode == 0 and res.stdout.strip() == "true"


def current_branch():
    return run(["git", "rev-parse", "--abbrev-ref", "HEAD"]).stdout.strip()


def has_changes():
    return run(["git", "status", "--porcelain"], check=False).stdout.strip() != ""


def main():
    if not is_git_repo():
        print("Este directorio no es un repositorio Git. Inicializa con 'git init' y configura el remoto.")
        sys.exit(1)

    branch = current_branch()
    print(f"Rama actual: {branch}")

    # Trae cambios remotos si existen
    run(["git", "fetch", "origin"], check=False)

    # Staging
    run(["git", "add", "-A"])

    # Commit solo si hay cambios
    if has_changes():
        ts = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        default_msg = f"Auto-commit: sincroniza cambios del proyecto ({ts})"
        msg = default_msg
        # Permite pasar un mensaje por CLI: python push_to_git.py "mensaje"
        if len(sys.argv) > 1:
            msg = sys.argv[1]
        run(["git", "commit", "-m", msg])
    else:
        print("No hay cambios para commitear. Continuo con pull/push.")

    # Integra cambios remotos con rebase si hay upstream
    run(["git", "pull", "--rebase", "origin", branch], check=False)

    # Detecta si hay upstream configurado
    upstream = run(["git", "rev-parse", "--symbolic-full-name", "--abbrev-ref", "@{u}"], check=False)
    if upstream.returncode == 0:
        # Ya hay upstream: push normal
        run(["git", "push", "origin", branch])
    else:
        # Sin upstream: setea upstream
        run(["git", "push", "-u", "origin", branch])

    print("Push completado correctamente.")


if __name__ == "__main__":
    main()