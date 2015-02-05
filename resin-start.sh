#!/bin/sh

CONF="`pwd`/target/resin4.conf"

if [ "$1" = -h ] || [ "$1" = --help ]; then
    echo "Usage: $0 [debug] [<Args to resin.sh ...>]"
    echo "Specify 'debug' as first arg to enable JVM debug port."
    exit 0
elif [ "$1" = debug ]; then
	echo "Debug mode"
	shift
	set -- -Xdebug -Xnoagent -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=12345 "$@"
fi

if ! [ -f "$CONF" ] || ! [ -f target/vtk-*/WEB-INF/web.xml ]; then
    echo >&2 "Error: webapp and/or Resin config not built under target/"
    echo >&2 "Run 'mvn package war:exploded' before attempting to run Resin locally."
elif test -z "$RESIN_HOME"; then
    echo >&2 "Error: \$RESIN_HOME must be set to use this script, point to your local installation of Resin."
    echo >&2 "(Set it in ~/.bashrc or similar)"
elif ! [ -x "$RESIN_HOME/bin/resin.sh" ]; then
    echo >&2 "Error: Resin control executable not found: $RESIN_HOME/bin/resin.sh"
elif ! $RESIN_HOME/bin/resin.sh version|grep -q 4.0; then
    echo >&2 "Error: Resin 4.0 required, but version reported was: `$RESIN_HOME/bin/resin.sh version`"
else
    echo Exec: $RESIN_HOME/bin/resin.sh console --log-directory "`pwd`/target" --conf "$CONF" "$@"
    exec $RESIN_HOME/bin/resin.sh console --log-directory "`pwd`/target" --conf "$CONF" "$@"
fi
