# Contrato Parking FAG v1

Fluxo: MP4 em loop → Python → Java → MySQL → Java → React. Somente Java acessa o MySQL.
Todas as datas são ISO 8601 UTC. A precisão persistida é de microssegundos.
Uma origem de monitoramento é configurada em `MONITOR_SOURCE` (padrão `parking-video`).

## Ingestão

`POST /api/v1/parking-spots/sync` recebe o catálogo completo e o estado observado:

```json
{
  "source": "parking-video",
  "observedAt": "2026-10-07T20:00:00.000000Z",
  "spots": [{
    "spotCode": "A-01", "status": "FREE", "sector": "A",
    "accessible": true, "layoutOrder": 0,
    "polygon": [{"x":0.23,"y":0.24},{"x":0.29,"y":0.25},{"x":0.25,"y":0.35},{"x":0.19,"y":0.35}]
  }]
}
```

Esse exemplo ilustra o contrato; não é um cadastro de produção. O catálogo real vem exclusivamente de `config/spots.json` do Python.
Polígonos usam coordenadas normalizadas do frame inteiro, de 0 a 1. `layoutOrder` define a ordem física dentro do setor.
Os códigos são estáveis, únicos e independentes da posição na lista. Estados: `FREE`, `OCCUPIED`, `UNKNOWN`.

`POST /api/v1/parking-spots/events` recebe alterações em lote:

```json
{"source":"parking-video","events":[{"spotCode":"A-01","status":"OCCUPIED","observedAt":"2026-10-07T20:00:02.000000Z"}]}
```

Uma vaga deve ter sido cadastrada por sincronização antes de receber eventos. Falha de validação rejeita o lote inteiro.
Observações anteriores ou iguais a `lastObserved` são ignoradas. Em empate de horário, o primeiro estado aceito prevalece.
Sincronizações anteriores à observação mais recente do catálogo são ignoradas integralmente para impedir restauração de uma configuração antiga.
Mudança de estado altera `lastUpdated`; confirmação do mesmo estado altera somente `lastObserved`.
Sincronização idêntica não escreve novamente. Não há tabela de histórico de transições.
Alterações de configuração devem ser enviadas com uma observação posterior.

`REMOVE_MISSING_SPOTS` controla remoção das vagas ausentes na sincronização. Padrão `false`, até definição dessa regra pelo usuário.
Quando habilitado, o catálogo recebido substitui a configuração, preservando a proteção contra snapshots antigos.
Uma sincronização vazia pode excluir todas as vagas quando essa opção estiver habilitada.

`POST /api/v1/logs` recebe até 100 eventos operacionais por lote:

```json
{"logs":[{"source":"parking-video","level":"WARN","message":"Falha de captura; último estado preservado","createdAt":"2026-10-07T20:00:03.000000Z"}]}
```

Níveis: `INFO`, `WARN`, `ERROR`. Mensagem limitada a 2000 caracteres. Reenvios idênticos são ignorados.
Não transmitir senhas, tokens, frames ou logs por frame. O Python produz somente mensagens operacionais definidas pela aplicação.

Ingestão retorna HTTP 200 e `{"applied":1,"ignored":0}`. Quantidades se referem a observações aplicadas, não necessariamente a mudanças de estado.
HTTP 400 indica payload, origem ou data inválidos. Datas mais de 30 segundos no futuro são rejeitadas.
HTTP 401 indica chave incorreta, quando a autenticação de ingestão estiver configurada.

## Leitura

`GET /api/v1/parking-spots` retorna um snapshot consistente:

```json
{
  "spots": [{"spotCode":"A-01","status":"FREE","sector":"A","accessible":true,"layoutOrder":0,
    "polygon":[{"x":0.23,"y":0.24},{"x":0.29,"y":0.25},{"x":0.25,"y":0.35}],
    "lastUpdated":"2026-10-07T20:00:00.000000Z","lastObserved":"2026-10-07T20:00:02.000000Z"}],
  "summary":{"total":1,"free":1,"occupied":0,"unknown":0,"occupancyPercent":0.0},
  "serverTime":"2026-10-07T20:00:05Z","lastObserved":"2026-10-07T20:00:02.000000Z","monitoringActive":true
}
```

`occupancyPercent = occupied / (free + occupied) × 100`, arredondado a uma casa decimal. Se não há estados conhecidos, o percentual não é enviado.
`GET /api/v1/parking-spots/summary` retorna o objeto `summary`. O React utiliza a lista e o resumo do mesmo snapshot, sem uma segunda consulta.
O catálogo vazio retorna `spots: []` e contagens zero.
`monitoringActive` exige que todas as vagas tenham observações recentes dentro de `MONITOR_STALE_SECONDS` (45 por padrão).
Uma falha de vídeo ou rede não converte estados conhecidos em `UNKNOWN`: os últimos estados permanecem, conforme decisão do usuário.
`UNKNOWN` representa incerteza real na análise do frame ou estado inicial ainda não confirmado.

## Operação e segurança

`GET /actuator/health` informa saúde da aplicação e banco. OpenAPI: `/v3/api-docs`; Swagger UI: `/swagger-ui.html`.
Erros da aplicação usam `code`, `message`, `timestamp`, sem detalhes internos ou credenciais.
Uma chave opcional `INGEST_API_KEY` protege os endpoints POST, pelo cabeçalho `X-API-Key`. O frontend nunca recebe essa chave.
CORS aceita somente `CORS_ORIGINS`. O proxy de desenvolvimento do React oferece acesso pela mesma origem na rede local.

## Cadência e recuperação

Python analisa 2 amostras por segundo e confirma mudanças em 3 amostras consecutivas. SYNC_SECONDS controla a sincronização do catálogo; o exemplo atual usa 0.1 segundo, respeitando a cadência da thread de comunicação de 250 ms e a chegada de novas observações.
Comunicação ocorre em thread separada. Retry exponencial começa em 1 segundo e é limitado por RETRY_MAX_SECONDS; o exemplo atual usa limite de 3 segundos.
Alterações pendentes são consolidadas por vaga; após falha é enviado o estado observado mais recente, sem replay obrigatório de todas as transições.
Horários de observação são da captura real, não do instante de envio. Sem captura válida, não há renovação de atividade.
React consulta imediatamente e agenda a próxima consulta 500 ms após sucesso ou 2 segundos após falha; timeout de 8 segundos e nenhuma sobreposição. VITE_POLL_INTERVAL_MS permite ajustar o intervalo de sucesso entre 250 e 30000 ms, com padrão de 500 ms. A confirmação de três amostras a 2 FPS no Python continua independente da consulta do frontend.
Mantém o último snapshot também em armazenamento local do navegador, sem avisos de erro na tela. O horário da consulta bem-sucedida permanece visível.

## Limites arquiteturais

Esta versão opera com um monitor e uma instância Java. A serialização das escritas cobre a transação completa nessa instância.
Múltiplas instâncias Java ou câmeras requerem controle de concorrência no banco e contratos de origem/revisão por catálogo.
Relógios devem estar sincronizados. Uma exclusão de todo o catálogo remove também sua referência temporal; snapshots antigos após catálogo vazio requerem um mecanismo de revisão persistente antes de uso em múltiplos produtores.
