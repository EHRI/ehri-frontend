from __future__ import with_statement
from fabric.api import *
from fabric.contrib.console import confirm
from fabric.contrib.project import rsync_project
from contextlib import contextmanager as _contextmanager

# globals
env.project_name = 'docview'
env.prod = False
env.use_ssh_config = True

# environments
def stage():
    "Use the remote staging server"
    env.hosts = ['ehristage']
    env.path = '/opt/docview'
    env.user = 'michaelb'

def production():
    "Use the remote virtual server"
    env.hosts = ['ehriprod']
    env.path = '/opt/docview'
    env.user = 'michaelb'
    env.prod = True

def setup():
    """
    Setup environment, then run a full deployment
    """
    require('hosts', provided_by=[local])
    require('path')
    deploy()

def deploy():
    """
    Deploy the latest version of the site to the servers, install any
    required third party modules, and then restart the webserver
    """
    play_stage()
    restart()

# Helpers. These are called by other functions rather than directly
def play_stage():
    "Create an archive from the current Git master branch and upload it"
    require("path")
    #local('play clean stage')
    sudo('mkdir -p %(path)s' % env)
    #put('target', env.path, use_sudo=True, mirror_local_mode=True)
    rsync_project(local_dir="target", remote_dir=env.path, delete=True)

def install_requirements():
    "Install the required packages from the requirements file using pip"

def symlink_current_release():
    "Symlink our current release"

def activate_production_settings():
    """Copy shared/production_settings.py to the release
    folder where they will be imported by settings.py"""

def update_index():
    """Rebuild document index from database."""

def rebuild_prod_index():
    """Rebuild document index from database."""

def rebuild_index():
    """Rebuild document index from database."""

def restart_docview():
    "Restart Solr (via Jetty)"
    sudo('/etc/init.d/docview restart')

