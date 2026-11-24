# CredenShow Leads

App Android de coleta de leads para expositores (usuários do CAEX).

Fluxo: login (e-mail/senha do CAEX) → troca de senha no 1º acesso → evento → estande →
dashboard (leads do estande, hoje, lidos por você, estandes do evento) → leitura do QR Code
da credencial (o mesmo do evento) → dados do visitante + interesse/observação → salvar.

- API: `credenShow/ApiLeads/` no servidor (`https://credenshow.com.br/ApiLeads/`).
- Banco: `ApiLeads/_sql/001_leads_app.sql` (colunas estande_id, caex_usuario_id, origem em `leads`).
- Lead pertence ao ESTANDE: o mesmo visitante conta uma vez por estande; ler de novo permite atualizar as anotações.

## Publicar atualização (igual ao app de credenciamento)
Os aparelhos com o app (release) consultam `https://credenshow.com.br/app/leads/versao.json`
a cada 10 min (login e dashboard) e exigem a atualização quando o versionCode do site é maior.
O versionCode é calculado no build (minutos desde 01/01/2025), então todo release é mais novo.

- **Pelo GitHub:** Actions > "Publicar atualização do app Leads" > Run workflow.
  Segredos do repositório: KEYSTORE_BASE64, KEYSTORE_PASSWORD, FTP_HOST, FTP_USER, FTP_PASS
  (os mesmos do repositório CredenShowApp; FTP_DIR não precisa, o padrão é `leads`).
- **Pelo Mac:** `./publicar-atualizacao.sh` (usa `publicacao.properties`, fora do git).

Os dois geram o release, enviam `leads.apk` (com nome temporário, renomeado no fim) e depois
o `versao.json`, e conferem pelo site. Gerar só o APK: `./gradlew assembleRelease`
(sai em `apks/atualizacao/`).
A assinatura usa a mesma chave do app de credenciamento: `keystore.properties` aponta para
`../credenshow app 4/credenshow-release.jks`. Não apague nem recrie a chave.

## Testar contra o XAMPP no emulador
```
cd /Applications/XAMPP/xamppfiles/htdocs/credenShow && php -S 127.0.0.1:8098 -t .
./gradlew installDebug -PleadsApiUrl=http://10.0.2.2:8098/ApiLeads/
```
