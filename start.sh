#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
if [ ! -f .env ]; then cp .env.example .env; fi
docker compose up --build -d
printf '\nMở http://localhost:5173\n'
