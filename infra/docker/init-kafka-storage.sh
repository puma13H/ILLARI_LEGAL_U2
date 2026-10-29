#!/bin/sh
set -eu
# Only initialize an empty project volume. Never rewrite an existing Kafka log or recurse into it.
if [ -f /data/meta.properties ]; then
    [ "$(stat -c %u /data)" = 1000 ] || { echo 'Existing Kafka storage owner needs operator review' >&2; exit 1; }
elif [ -z "$(ls -A /data)" ]; then
    chown 1000:1000 /data
else
    echo 'Nonempty unformatted Kafka storage needs operator review' >&2
    exit 1
fi
