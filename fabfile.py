"""
Fabric deployment script for EHRI front-end webapp.
"""

from __future__ import with_statement

import os
import datetime
import subprocess
from fabric.api import *
from fabric.contrib.console import confirm
from fabric.contrib.project import upload_project
from contextlib import contextmanager as _contextmanager

# globals
env.project_name = 'docview'
env.prod = False
env.use_ssh_config = True
env.path = '/opt/webapps/' + env.project_name
env.user = os.getenv("USER")

# environments
def test():
    "Use the remote testing server"
    env.hosts = ['ehritest']

def stage():
    "Use the remote staging server"
    env.hosts = ['ehristage']

def production():
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
        restart_docview()

def clean_deploy():
    """Build a clean version and deploy."""
    local('play clean stage')
    deploy()

def get_version_stamp():
    "Get a dated and revision stamped version string"
    rev = subprocess.check_output(["git","rev-parse", "--short", "HEAD"]).strip()
    timestamp = datetime.datetime.now().strftime("%Y%m%d%H%M%S")    
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

def restart_docview():
    "Restart docview"
    # NB: This doesn't use sudo() directly because it insists on asking
    # for a password, even though we should have NOPASSWD in visudo.
    run('sudo service %(project_name)s restart' % env, pty=False)

def rollback():
    "Rollback to the last versioned dir and restart"
    with cd(env.path):
        output = run("ls -1rt deploys | tail -n 2 | head -n 1").strip()
        if output == "":
            raise Exception("Unable to get previous version for rollback!")
        with settings(version=output):
            symlink_current()
            restart_docview()

def latest():
    "Point symlink at latest version"
    with cd(env.path):
        output = run("ls -1rt deploys | tail -n 1").strip()
        if output == "":
            raise Exception("Unable to get latest version for rollback!")
        with settings(version=output):
            symlink_current()
            restart_docview()


