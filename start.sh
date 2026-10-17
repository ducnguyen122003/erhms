#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
if [ ! -f .env ]; then cp .env.example .env; fi
docker compose up --build -d
printf '\nWeb tư vấn: http://localhost:8081\nWeb quản lý: http://localhost:8082\n'
