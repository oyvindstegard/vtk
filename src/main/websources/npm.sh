#!/bin/sh
basedir=`dirname "$0"`

NODE_EXE="$basedir/node/node"
NPM_CLI_JS="$basedir/node/node_modules/npm/bin/npm-cli.js"

"$NODE_EXE" "$NPM_CLI_JS" "$@"
