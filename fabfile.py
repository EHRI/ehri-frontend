"""
Fabric deployment script for EHRI front-end webapp.
"""

from __future__ import with_statement

import os
import datetime
import subprocess
from datetime import datetime

from fabric.api import *
from fabric.contrib.console import confirm
from fabric.contrib.project import upload_project
from contextlib import contextmanager as _contextmanager

# globals
env.play_bin = 'activator'
env.project_name = 'docview'
env.prod = False
env.use_ssh_config = True
env.path = '/opt/webapps/' + env.project_name
env.user = os.getenv("USER")
env.java_version = 6

TIMESTAMP_FORMAT = "%Y%m%d%H%M%S"

# environments
def test():
    "Use the remote testing server"
    env.hosts = ['ehritest']

def stage():
    "Use the remote staging server"
    env.hosts = ['ehristage']

def prod():
    "Use the remote virtual server"
    env.hosts = ['ehriprod']
    env.prod = True

def deploy():
    """
    Deploy the latest version of the site to the servers, install any
    required third party modules, and then restart the webserver
    """
    with settings(version = get_version_stamp()):
        copy_to_server()
        set_permissions()
        symlink_current()
        restart()

def clean_deploy():
    """Build a clean version and deploy."""
    check_java_version()
    local("%(play_bin)s clean stage" % env)
    deploy()

def get_version_stamp():
    "Get a dated and revision stamped version string"
    rev = subprocess.check_output(["git","rev-parse", "--short", "HEAD"]).strip()
    timestamp = datetime.now().strftime(TIMESTAMP_FORMAT)    
    return "%s_%s" % (timestamp, rev)

def copy_to_server():
    "Upload the app to a versioned path."
    # Ensure the deployment directory is there...
    with cd(env.path):
        run("mkdir -p deploys/%(version)s" % env)
    upload_project(local_dir="./target", remote_dir="%(path)s/deploys/%(version)s" % env)

def symlink_current():
    with cd(env.path):
        run("ln --force --no-dereference --symbolic deploys/%(version)s/target target" % env)

def set_permissions():
    with cd(env.path):
        run("chown -R %(user)s.webadm deploys/%(version)s/target/universal/stage" % env)
        run("chmod g+x deploys/%(version)s/target/universal/stage/bin/%(project_name)s" % env)

def start():
    "Start docview"
    # NB: This doesn't use sudo() directly because it insists on asking
    # for a password, even though we should have NOPASSWD in visudo.
    run('sudo service %(project_name)s start' % env, pty=False, shell=False)

def stop():
    "Stop docview"
    # NB: This doesn't use sudo() directly because it insists on asking
    # for a password, even though we should have NOPASSWD in visudo.
    run('sudo service %(project_name)s stop' % env, pty=False, shell=False)

def restart():
    "Restart docview"
    # NB: This doesn't use sudo() directly because it insists on asking
    # for a password, even though we should have NOPASSWD in visudo.
    run('sudo service %(project_name)s restart' % env, pty=False, shell=False)

def rollback():
    "Rollback to the last versioned dir and restart"
    with cd(env.path):
        output = run("ls -1rt deploys | tail -n 2 | head -n 1").strip()
        if output == "":
            raise Exception("Unable to get previous version for rollback!")
        with settings(version=output):
            symlink_current()
            restart()

def latest():
    "Point symlink at latest version"
    with cd(env.path):
        output = run("ls -1rt deploys | tail -n 1").strip()
        if output == "":
            raise Exception("Unable to get latest version for rollback!")
        with settings(version=output):
            symlink_current()
            restart()

def current_version():
    "Show the current date/revision"
    with cd(env.path):
        path = run("readlink -f target")
        deploy = os.path.split(path)
        if deploy[-1] != "target":
            abort("Unexpected path for deploy directory: " + path)
        timestamp, revision = os.path.basename(deploy[-2]).split("_")        
        date = datetime.strptime(timestamp, TIMESTAMP_FORMAT)
        print("Timestamp: %s, revision: %s" % (date, revision))
        return date, revision

def current_version_log():
    "Output git log between HEAD and the current deployed version."
    _, revision = current_version()
    local("git log %s..HEAD" % revision)

def check_java_version():
    "Ensure we're building with the right java version"
    version_string = local("java -version 2>&1", capture=True)
    if version_string.find("java version \"1.%d" % env.java_version) == -1:
        abort("Incorrect java version: should be 1.%d" % env.java_version)

    "Ensure we're building with the right javac version"
    version_string = local("javac -version 2>&1", capture=True)
    if version_string.find("javac 1.%d" % env.java_version) == -1:
        abort("Incorrect javac version: should be 1.%d" % env.java_version)
