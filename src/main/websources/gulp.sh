#!/bin/sh
basedir=`dirname "$0"`

NODE_EXE="$basedir/node/node"
GULP_CLI_JS="$basedir/node_modules/gulp/bin/gulp.js"

"$NODE_EXE" "$GULP_CLI_JS" "$@"
