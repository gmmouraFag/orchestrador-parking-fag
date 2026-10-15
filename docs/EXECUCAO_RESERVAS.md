# Execução integrada com reservas

Este guia substitui as instruções anteriores de landing page, polling de 500ms e exposição do Vite.
Os três repositórios devem estar lado a lado, com seus nomes originais. Requisitos:
Windows/PowerShell, Java 17, Python 3.12/3.13, Node 24, MySQL 8 e Git.
Somente o Java acessa o banco. Para outros sistemas, os mesmos serviços podem ser
iniciados manualmente com os comandos dos READMEs e as variáveis abaixo.

## Preparação

No Java, copie `.env.example` para `.env` somente se ainda não existir e configure
DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD. root/root é apenas desenvolvimento local.
Flyway aplica V1/V2 sem recriar tabelas existentes. Faça backup do banco antes de atualizar
uma instalação com dados importantes. Não altere migrations que já tenham sido aplicadas.

No Python, dentro de `monitoramento-vagas`:

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m pip install -e . --no-deps
Copy-Item .env.example .env # somente na primeira preparação
.\.venv\Scripts\python.exe -m parking_monitor.main --validate-config
```

O vídeo incluído é `video/estacionamento.mp4`. Outra fonte exige recalibração das regiões.
No React, `VITE_API_BASE_URL` deve ficar vazio para operação pela mesma origem.
Não coloque chave de ingestão ou credenciais em variáveis VITE_*.

## Iniciar

Com MySQL ativo, na pasta `orchestrador-parking-fag`:

```powershell
.\scripts\run-stack.ps1
# Sem janela de vídeo:
.\scripts\run-stack.ps1 -Headless
# Após builds já prontos: acrescente -SkipBuild
```

O script compila Java/React, aguarda saúde do Java, inicia Python e o servidor Node
do build. Gera uma chave temporária de ingestão compartilhada entre Java/Python se
INGEST_API_KEY estiver vazia; não imprime nem persiste essa chave.
As portas 8080 e 4173 devem estar livres. Logs: `runtime/stack/` no repositório Java.
O script falha se um serviço encerrar; Ctrl+C encerra os três processos rastreados.

- `http://127.0.0.1:4173/` e `/totem`: painel informativo.
- `/mobile`: reservas, temporizador, confirmação privada e alternativas.
- `/painel`: alias legado do totem.
- Swagger local: `http://127.0.0.1:8080/swagger-ui.html`.

Java e servidor público escutam apenas em loopback por padrão. Não abra MySQL,
8080 ou o Vite na internet. Para desenvolvimento separado, continuam disponíveis
`scripts/run.ps1` no Java/Python e `npm run dev` no React. Nesse caso configure a
mesma chave de ingestão nos dois processos; use o hostname permitido em CORS_ORIGINS.

## Endereço HTTPS para celular

Instale cloudflared pela distribuição oficial:
https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/downloads/

Em outro terminal:

```powershell
cloudflared tunnel --url http://127.0.0.1:4173
# Se a rede bloquear QUIC, acrescente --protocol http2
```

Abra o endereço HTTPS informado, acrescentando `/mobile`, no celular (inclusive fora
do Wi-Fi). `/totem` usa os mesmos dados. Quick Tunnel suporta WebSocket; não suporta
SSE, motivo da escolha deste transporte. O endereço muda ao iniciar um novo túnel.
Fonte: https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/

O servidor `view-parking-fag/scripts/serve.mjs` serve somente o build e uma lista
explícita das APIs públicas. Ingestão, logs, Swagger, actuator e arquivos de configuração
não são encaminhados pelo túnel. POST exige origem correspondente e cabeçalho próprio;
há limite de corpo, sessão HttpOnly/SameSite=Strict/Secure em HTTPS e limite de 30
operações de reserva por minuto por endereço visto pelo Java. Pela camada Node,
esse limite é compartilhado entre clientes, porque o backend vê loopback.

Verifique monitoramento ativo e atualização das versões. Na reconexão o WebSocket
recebe snapshot completo; HTTP sincroniza a cada 15s, com retry de 30s após falha.
A consulta privada também se recupera ao voltar à aba/celular. Ao limpar cookies,
o dispositivo perde o controle da reserva anterior; ela expira normalmente no servidor.

Para encerrar: Ctrl+C no terminal do túnel e no da aplicação, ou execute
`.\scripts\stop-stack.ps1` para os serviços rastreados. Encerrar apenas a aplicação
deixa o túnel sem origem; encerrar apenas o túnel mantém acesso local. Desligar a
máquina encerra a disponibilidade. Para operação 24h, planejar hospedagem separadamente.

## Operação e critérios

Reservas duram 90 segundos pelo relógio Java. Uma vaga comum, livre e com observação
recente pode ser reservada. PCD permanece visível, mas bloqueada para reservas até
definir elegibilidade. Estado físico nunca recebe RESERVED. Visual: cinza se sem dados
recentes/incertos, vermelho se ocupada, amarelo se livre com reserva, verde se livre.
Expiração é verificada nas operações além do agendador de 1s; atraso do agendador não
mantém bloqueio indevido. Dois clientes disputando a vaga recebem um sucesso e um 409.

Ocupação observada durante a validade solicita confirmação ao dono da sessão.
Sim conclui; não encerra como DISPLACED. A sugestão não cria reserva: aceitar revalida
a disponibilidade. A distância é estimada pelos centros dos polígonos na imagem
1920×1080, conforme autorização do usuário; não representa metros ou caminho físico.
Sem resposta, expira no prazo original. Liberação antes da resposta retira o popup.
Reservas persistem após reinício, mas não se prolongam. UTC no banco e contratos;
horários da tela são explicitamente America/Sao_Paulo. Tokens não entram nos logs.

## Verificação

```powershell
# Java: testes H2, MySQL isolado, migrations e dois clientes WebSocket reais
$env:MYSQL_TEST='true'
.\mvnw.cmd verify
# Bancos de teste: parking_fag_test e parking_fag_test_reservations. Nunca use o banco operacional.
```

No Python: `python -m ruff check .` e `python -m pytest -q` no ambiente preparado.
No React: `npm ci`, `npm run lint`, `npm test`, `npm run build`.
Cada repositório mantém CI própria. A regressão Python exige o vídeo incluído no Git.
Consulte `docs/PCD.md` no Python para causa, medição e limites da calibração.
