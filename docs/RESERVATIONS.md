# Evolução aprovada — 15/10/2026

Java é a autoridade das reservas de 90 segundos; Python informa somente estado físico.
PCD permanece monitorada, mas não pode ser reservada até definir elegibilidade.
A alternativa usa distância euclidiana entre centros dos polígonos na imagem 1920×1080:
é uma estimativa visual autorizada, sem representar metros ou rota real.

## Contrato previsto

Base `/api/v1`. POSTs de ingestão existentes continuam exigindo X-API-Key.
Reservas usam cookie de sessão aleatório HttpOnly/SameSite e hash no banco; sem cadastro.
POSTs do navegador exigem origem válida. Tokens nunca integram URLs, logs ou eventos públicos.

| Método/caminho | Corpo | Resultado |
|---|---|---|
| POST /reservations | `{spotCode}` | 201: reserva da sessão; repetição para a mesma vaga retorna a reserva existente |
| GET /reservations/current | — | reserva atual/última e serverTime; somente sessão proprietária |
| POST /reservations/{id}/confirm | `{}` | COMPLETED, somente se ocupada e dentro do prazo |
| POST /reservations/{id}/reject | `{}` | DISPLACED e alternativas; não reserva outra vaga automaticamente |
| POST /reservations/{id}/cancel | `{}` | CANCELLED; repetição idempotente |
| GET /parking-spots/alternatives?from=CODE | — | vagas comuns livres, frescas e sem reserva, ordenadas pela distância estimada |
| GET /events (WebSocket upgrade) | — | snapshots públicos versionados; nenhum dado privado |

Erros: 400 payload inválido; 401 sessão ausente; 403 origem inválida; 404 recurso inexistente
ou de outra sessão; 409 conflito de disponibilidade/transição; 429 excesso de requisições.

## Estados e consistência

ACTIVE → PENDING_CONFIRMATION ao receber ocupação observada durante a validade.
PENDING_CONFIRMATION → COMPLETED (sim), DISPLACED (não), ACTIVE (liberou).
ACTIVE/PENDING_CONFIRMATION → EXPIRED ao atingir expiresAt, ou CANCELLED pelo dono.
Não responder nunca confirma; o prazo original não é prorrogado. Reiniciar preserva reservas.
FREE/OCCUPIED/UNKNOWN físicos são independentes. Visual: indisponível UNKNOWN;
ocupada OCCUPIED; livre com reserva RESERVED; demais livres FREE.
Dados antigos são preservados para diagnóstico, mas vagas sem observação recente não são reserváveis.

Um registro de coordenação persistente serializa transações de catálogo/reserva/expiração;
a versão cresce na mesma transação. Eventos são publicados depois do commit.
Reconexão recebe snapshot completo. Clientes descartam versões antigas e consultam a reserva
privada ao mudar o estado público. Polling de segurança 15s e retry 30s.

## Entregas e validação

Commits separados: contratos; persistência/regras/testes; eventos; integração Python e PCD;
interfaces; logs; exposição; testes integrados/documentação/pipelines.
Testar relógio simulado, concorrência real no MySQL, privacidade, reinício, retries,
confirmação/liberação/expiração e múltiplos clientes. UTC na persistência, Brasília na tela.
Cloudflare Quick Tunnel não suporta SSE; por isso esta entrega usa WebSocket.
