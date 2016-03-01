#!/bin/sh
basedir=`dirname "$0"`

NODE_EXE="$basedir/node/node"
BOWER_CLI="$basedir/node_modules/bower/bin/bower"

"$NODE_EXE" "$BOWER_CLI" "$@"

