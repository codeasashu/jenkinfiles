# Jenkinfiles

**NOTE**-  This project currently supports [gitlab.com](https://gitlab.com)

This is a collection of jenkinfile pipelines for several usage. This repo holds several jenkinfiles suited for different languages. For instance, `Jenkinfile-php` is best suited for `PHP` projects while `Jenkinfile-ansible` is best for ansible related operations.

## Quick Start
To get started quickly, navigate to your jenkins server and then do the following:

1. Create new item, give any name. Select `Pipeline` from below item list
2. In job configuration, go to `pipeline` section
3. In definition section, select `Pipeline script from SCM`. In SCM, select `git` and enter  this git repo in `Repository URL` section

## How to use

The scripts are written so that it can be used individually. However, it can still work when your jobs are interlinked (dependent). 

For instance, you may want to run ansible script after your `php` based job has been built, for deployment. For this to work, you can create two jobs:

- PHP
- Ansible

And in `php` job, you can set environment variable `NEXT_JOB` in the build configuration to run the next job after this

## Features

- Auto build on merge request event and push events
- Git diffs artifacts available as `diff.txt`
- Automerge source branch to target and push
- Ansible send slack notification