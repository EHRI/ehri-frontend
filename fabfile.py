"""
Fabric deployment script for EHRI front-end webapp.
"""

import os
from datetime import datetime

from fabric import task
from invoke import run as local

deploys_dir = "/opt/docview/deploys"
target_link = "/opt/docview/target"


@task
def deploy(ctx, clean=False):
    """Build (optionally with clean) and deploy the distribution"""
    version = get_version_stamp(ctx)
    build_cmd = "sbt dist" if not clean else "sbt clean dist"
    local(build_cmd)
    file = local("ls -1t target/universal/docview-*.zip").stdout.strip()
    base = os.path.basename(file)
    if not file or file == "":
        raise Exception("Cannot find latest build zip in target/universal!")
    version_dir = f"{deploys_dir}/{version}"

    ctx.put(file, remote="/tmp")
    ctx.run(f"mkdir -p {version_dir}")
    ctx.run(f"unzip /tmp/{base} -d {version_dir}")
    symlink_target(ctx, version_dir, target_link)
    restart(ctx)


@task
def rollback(ctx):
    """Set the current version to the previous version directory"""
    output = ctx.run(f"ls -1rt {deploys_dir} | tail -n 2 | head -n 1").stdout.strip()
    if output == "":
        raise Exception("Unable to get previous version for rollback!")
    symlink_target(ctx, f"{deploys_dir}/{output}", target_link)
    restart(ctx)


@task
def latest(ctx):
    """Set the current version to the latest version directory"""
    output = ctx.run(f"ls -1rt {deploys_dir} | tail -n 1").stdout.strip()
    if output == "":
        raise Exception("Unable to get previous version for rollback!")
    symlink_target(ctx, f"{deploys_dir}/{output}", target_link)
    restart(ctx)


@task
def symlink_target(ctx, version_dir, target):
    """Symlink a version directory"""
    ctx.run(f"ln --force --no-dereference --symbolic {version_dir} {target}")
    ctx.run(f"chgrp -R webadm {target_link}")


@task
def restart(ctx):
    """Restart the docview process"""
    ctx.run("sudo service docview restart")


@task
def get_version_stamp(ctx):
    """Get the tag for a version, consisting of the current time and git revision"""
    res = local("git rev-parse --short HEAD").stdout.strip()
    timestamp = datetime.now().strftime("%Y%m%d%H%M%S")
    return f"{timestamp}_{res}"


@task
def whitelist(ctx, ip=None):
    """Toggle the IP whitelist"""
    filename = "/opt/docview/IP_WHITELIST"
    if ip is None:
        if check_file_exists(ctx, filename):
            ctx.run(f"rm -f {filename}")
            print("IP_WHITELIST mode is OFF")
        else:
            print("No IP given, and no whitelist file present. Nothing to do...")
    else:
        # check the format in a VERY crude and stupid way
        import re
        m = re.match(r"^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$", ip)
        if not m:
            raise Exception(f"This doesn't look like a single IP(?): {ip}")
        else:
            ctx.run(f"echo '{ip}' > {filename}")
            print(f"IP_WHITELIST is ON for {ip}")


@task
def message(ctx, msg=None):
    """Toggle the global message (with the given message), or remove it."""
    filename = "/opt/docview/MESSAGE"
    if msg is not None:
        ctx.run(f"echo '{msg}' > {filename}")
        print("MESSAGE mode is ON")
    else:
        ctx.run(f"rm {filename}")
        print("MESSAGE mode is OFF")


@task
def readonly(ctx):
    """Toggle read-only mode"""
    toggle_mode(ctx, "READONLY")


@task
def maintenance(ctx):
    """Toggle maintenance mode"""
    toggle_mode(ctx, "MAINTENANCE")


def toggle_mode(ctx, mode):
    filename = f"/opt/docview/{mode}"
    if check_file_exists(ctx, filename):
        ctx.run(f"rm {filename}")
        print(f"{mode} mode is OFF")
    else:
        ctx.run(f"touch {filename}")
        print(f"{mode} mode is ON")


def check_file_exists(ctx, remote_path):
    """
    Check if a file exists on the remote server.
    """
    try:
        # Using test command with -f flag to check for regular file
        result = ctx.run(f'test -f {remote_path} && echo "EXISTS" || echo "NOT_FOUND"', hide=True)
        return result.stdout.strip() == "EXISTS"
    except Exception as e:
        return False
