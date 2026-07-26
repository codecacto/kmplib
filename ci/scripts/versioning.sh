#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# versioning.sh - Calcula versões para CI/CD de apps KMP
#
# Uso:
#   bash ci/scripts/versioning.sh [version-override]
#
# Saída (variáveis exportáveis):
#   MARKETING_VERSION  - Versão visível (X.Y.Z)
#   BUILD_NUMBER       - Número do build (incremental)
#   ANDROID_VERSION_CODE - versionCode Android (inteiro crescente)
#   GIT_SHA            - SHA curto do commit
# ============================================================

VERSION_OVERRIDE="${1:-}"

GIT_SHA="$(git rev-parse --short=8 HEAD)"

if [ -n "${VERSION_OVERRIDE}" ]; then
  MARKETING_VERSION="${VERSION_OVERRIDE}"
else
  LAST_TAG="$(git describe --tags --abbrev=0 --match "v[0-9]*.[0-9]*.[0-9]*" 2>/dev/null || echo "v0.1.0")"
  MARKETING_VERSION="${LAST_TAG#v}"
fi

# Fora do CI (release rodado na máquina do fundador) GITHUB_RUN_NUMBER não existe, e o antigo
# fallback `1` fazia todo build local sair com o mesmo número — rejeitado pela loja como duplicado.
# O nº de commits da branch é monotônico e disponível em qualquer clone.
#
# NOTA: este é apenas o PISO. Na publicação real quem decide é a loja:
#   Android → gradle-play-publisher (resolutionStrategy = AUTO) consulta a Play API
#   iOS     → fastlane (latest_testflight_build_number + 1)
# Ver docs/15-release-mobile-spec.md.
BUILD_NUMBER="${GITHUB_RUN_NUMBER:-$(git rev-list --count HEAD 2>/dev/null || echo 1)}"

IFS='.' read -r MAJOR MINOR PATCH <<< "${MARKETING_VERSION}"
MAJOR="${MAJOR:-0}"
MINOR="${MINOR:-1}"
PATCH="${PATCH:-0}"

# versionCode: usa GITHUB_RUN_NUMBER diretamente (sempre crescente, sem colisão)
ANDROID_VERSION_CODE="${BUILD_NUMBER}"

echo "MARKETING_VERSION=${MARKETING_VERSION}"
echo "BUILD_NUMBER=${BUILD_NUMBER}"
echo "ANDROID_VERSION_CODE=${ANDROID_VERSION_CODE}"
echo "GIT_SHA=${GIT_SHA}"
