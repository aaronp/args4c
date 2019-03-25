#!/usr/bin/env bash

sbt clean coverage test coverageReport coveralls

cp -r target/scala-2.12/scoverage-report target

REPORT_URL="./target/scoverage-report/index.html"
echo "See ${REPORT_URL}"

[[ -x $BROWSER ]] && exec "$BROWSER" "$REPORT_URL"
path=$(which xdg-open || which gnome-open) && exec "$path" "$REPORT_URL"