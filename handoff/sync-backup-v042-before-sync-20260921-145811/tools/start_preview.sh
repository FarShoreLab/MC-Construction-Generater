#!/usr/bin/env sh
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
python3 "$HERE/build_offline.py"
exec python3 "$HERE/preview_server.py" "$@"
