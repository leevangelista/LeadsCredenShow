#!/bin/bash
# Gera o release assinado e publica a atualização em credenshow.com.br/app/leads/ por FTP.
# Os aparelhos com o app release instalado passam a oferecer a atualização (ui/Atualizacao.kt).
#
# Uso:  ./publicar-atualizacao.sh
# Acesso FTP em publicacao.properties (fora do git; modelo: publicacao.properties.exemplo)
# ou em variáveis de ambiente com os mesmos nomes (é assim que o GitHub Actions passa os segredos).
set -euo pipefail

cd "$(dirname "$0")"

CONFIG=publicacao.properties

# Variável de ambiente tem prioridade; senão lê do publicacao.properties
ler() {
    if [ -n "${!1:-}" ]; then
        echo "${!1}"
    elif [ -f "$CONFIG" ]; then
        grep -E "^$1=" "$CONFIG" | head -1 | cut -d= -f2- || true
    fi
}
FTP_HOST=$(ler FTP_HOST)
FTP_USER=$(ler FTP_USER)
FTP_PASS=$(ler FTP_PASS)
# Sem FTP_DIR = "leads": a conta FTP do app de credenciamento já abre na pasta app/ do site,
# então o Leads fica em app/leads/ usando o MESMO acesso FTP.
FTP_DIR=$(ler FTP_DIR)
FTP_DIR=${FTP_DIR:-leads}
FTP_SEGURO=$(ler FTP_SEGURO)
URL_SITE=https://credenshow.com.br/app/leads

for v in FTP_HOST FTP_USER FTP_PASS; do
    [ -n "${!v}" ] || { echo "Falta $v (no $CONFIG ou como variável de ambiente)." >&2; exit 1; }
done

# FTPS (criptografado) por padrão; FTP_SEGURO=nao só se o servidor não aceitar
TLS=(--ssl-reqd)
[ "${FTP_SEGURO:-sim}" = "nao" ] && TLS=()

# 1. Gera o release (assinado, versionCode novo) em apks/atualizacao/
if [ -z "${JAVA_HOME:-}" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
echo "==> Gerando release..."
./gradlew assembleRelease -q

PASTA=apks/atualizacao
VERSAO=$(grep -E '"versionCode"' "$PASTA/versao.json" | grep -oE '[0-9]+')
TAMANHO=$(wc -c < "$PASTA/leads.apk" | tr -d ' ')
echo "    versionCode $VERSAO, APK com $TAMANHO bytes"

URL_FTP="ftp://$FTP_HOST/${FTP_DIR%/}/"
# Limites de tempo: sem eles, um FTP que não responde trava o script (e o Actions) por 30 min
CURL_FTP=(curl --fail --silent --show-error "${TLS[@]}" --ftp-create-dirs --connect-timeout 30
    --user "$FTP_USER:$FTP_PASS")

# Traduz os erros mais comuns do curl para o que conferir
enviar() {
    local codigo=0
    "${CURL_FTP[@]}" "$@" || codigo=$?
    case "$codigo" in
        0) ;;
        60) echo "Certificado SSL do FTP não confere com '$FTP_HOST'. Use em FTP_HOST o nome do servidor" >&2
            echo "da HostGator (ex.: sh00220.hostgator.com.br), que aparece no endereço do cPanel." >&2 ;;
        67) echo "Login FTP recusado: confira FTP_USER e FTP_PASS." >&2 ;;
        9)  echo "Sem permissão na pasta '$FTP_DIR': confira FTP_DIR (com a conta FTP do app/ use 'leads')." >&2 ;;
        28) echo "Tempo esgotado falando com o FTP ($FTP_HOST): servidor ou rede não responderam." >&2 ;;
        *)  echo "Falha no FTP (curl código $codigo)." >&2 ;;
    esac
    return "$codigo"
}

# Testa login e pasta antes de enviar 40 MB, para o erro aparecer em segundos
echo "==> Testando acesso FTP: $FTP_USER em $FTP_HOST, pasta '$FTP_DIR'..."
enviar --max-time 60 --list-only "$URL_FTP" > /dev/null

# 2. APK primeiro, com nome temporário e renomeado só no fim: nenhum aparelho baixa um APK pela metade
echo "==> Enviando APK..."
enviar --max-time 900 -T "$PASTA/leads.apk" "${URL_FTP}leads.apk.parcial" \
    -Q "-RNFR leads.apk.parcial" -Q "-RNTO leads.apk"

# 3. versao.json por último: só aqui os aparelhos passam a ver a versão nova
echo "==> Enviando versao.json..."
enviar --max-time 60 -T "$PASTA/versao.json" "${URL_FTP}versao.json"

# 4. Confere pelo site o que os aparelhos vão ver
echo "==> Conferindo $URL_SITE ..."
NO_SITE=$(curl -fsS -H 'Cache-Control: no-cache' "$URL_SITE/versao.json?t=$(date +%s)" | grep -E '"versionCode"' | grep -oE '[0-9]+' || true)
TAMANHO_SITE=$(curl -fsSI "$URL_SITE/leads.apk?t=$(date +%s)" | grep -i '^content-length' | tr -dc '0-9' || true)

if [ "$NO_SITE" = "$VERSAO" ] && [ "$TAMANHO_SITE" = "$TAMANHO" ]; then
    echo "OK: versão $VERSAO publicada. Os aparelhos vão oferecer a atualização na próxima verificação."
else
    echo "ATENÇÃO: o site mostra versionCode '$NO_SITE' e APK com '$TAMANHO_SITE' bytes (esperado $VERSAO / $TAMANHO)." >&2
    echo "Confira FTP_DIR no $CONFIG (deve ser a pasta que aparece em $URL_SITE/)." >&2
    exit 1
fi
