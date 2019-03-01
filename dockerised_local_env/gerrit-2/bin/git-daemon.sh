#!/bin/sh

git daemon --verbose --enable=receive-pack --base-path=/var/gerrit/git --export-all
