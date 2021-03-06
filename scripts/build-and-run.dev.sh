#!/usr/bin/env bash

set -e
set -x

# Parse options
ORIENTDB_HOST=${ORIENTDB_HOST:-localhost}
while [ "$#" != "0" ] ; do
    if   [ "$1" == "--orientdb-host" ] ; then
        ORIENTDB_HOST="$2"
        shift
    elif [ "$1" == "--start-orientdb-server" ] ; then
        START_ORIENTDB_SERVER=true
    else
        echo "Unknown parameter '$1'"
        exit 1
    fi
    shift
done

# Configure/reconfigure proxy in case it is set
/opt/debattons/setup/proxy-on-ubuntu.sh

export ORIENTDB_ROOT_PASSWORD=${ORIENTDB_ROOT_PASSWORD:-default_ORIENTDB_ROOT_PASSWORD_to_be_changed}

if [ "$START_ORIENTDB_SERVER" = "true" ];then
    /opt/orientdb/bin/server.sh &
fi

# Creating the api-server user if not exist
while ! nc -vz "$ORIENTDB_HOST" 2424; do
    sleep 1
done

/opt/orientdb/bin/console.sh "CREATE DATABASE remote:$ORIENTDB_HOST/debattons root $ORIENTDB_ROOT_PASSWORD PLOCAL; CREATE USER \`api-server\` IDENTIFIED BY password ROLE admin;" || echo "Already created"

export DEBATTONS_DATABASE_URL=$ORIENTDB_HOST/debattons
cd /opt/debattons/api-server && mvn spring-boot:run &
cd /opt/debattons/ui && \
    yarn install --no-bin-links && # no bin links else we got "EPROTO: protocol error, symlink '../../../../less/bin/lessc' -> '/vagrant/ui/node_modules/@angular/cli/node_modules/.bin/lessc'" with Vagrant on Windows thanks to https://github.com/npm/npm/issues/9901#issuecomment-146585579 \
    chmod +x ./node_modules/@angular/cli/bin/ng && # to avoid "Permission denied" when executing the following line \
    ./node_modules/@angular/cli/bin/ng serve --host 0.0.0.0 # authorize remote access thanks to https://github.com/angular/angular-cli/issues/1793#issuecomment-241343672