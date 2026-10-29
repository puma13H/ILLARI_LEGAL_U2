#!/bin/sh
set -eu
for dir in /logs/*; do
  if [ -z "$(ls -A "$dir")" ]; then chown 10001:10001 "$dir"; chmod 750 "$dir"; fi
done
