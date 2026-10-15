# Validação — 15/10/2026

## Automatizado, executado localmente

- Python: 10 testes; Ruff aprovado. Regressão do vídeo em 121 amostras a 2 FPS:
  A-01 livre sob sombra e demais 20 vagas idênticas à versão anterior.
- Java: `MYSQL_TEST=true mvnw verify`: 29 testes aprovados (14 H2, 14 MySQL e
  um teste com servidor HTTP/WebSocket real e dois clientes).
- Flyway V1/V2 e validação Hibernate aprovados no MySQL 8 real, incluindo banco
  operacional existente e bancos de teste isolados.
- Reservas: 90s com Clock simulado, expiração na fronteira do prazo, unicidade em
  disputa simultânea, autorização, confirmação/rejeição, liberação, alternativa
  geométrica, bloqueio PCD/dados antigos, cookie/hash e recuperação da sessão.
- Logs: persistência e retry concorrente sem duplicar; instante com offset -03:00
  recuperado como o UTC equivalente com microssegundos no MySQL.
- React: 16 testes de componentes/hooks aprovados; um teste Node do servidor público.
  Lint e build de produção aprovados. Cobrem temporizador sem conclusão local,
  popup privado, rejeição/alternativa, conflito, cache, versão antiga e reconexão.
- Servidor público: bloqueio de ingestão, logs, configuração, actuator e outra origem.
- Scripts PowerShell: sintaxe validada; launcher/stop executados com serviços reais.

## Fluxo real com vídeo e HTTPS

Iniciado MySQL → Java → Python headless → build React servido pelo Node.
21 vagas retornadas pelo backend com versão persistida e monitoramento ativo.
Túnel Cloudflare temporário criado; /mobile, /totem e API pública responderam 200.
Ingestão, logs, actuator e .env retornaram 404 pelo endereço público.
WebSocket WSS recebeu snapshot com 21 vagas e nenhuma reserva privada.
Cookie recebido por HTTPS apresentou Secure, HttpOnly e SameSite=Strict.

No navegador:

1. Reserva de A-08 pelo servidor local; amarelo, contagem 1:30 e expiração observada.
2. Reserva de B-06 pela origem pública HTTPS; totem recebeu a mesma versão/amarelo.
3. O vídeo real provocou ocupação; mobile exibiu popup somente na experiência privada.
4. Resposta negativa; B-03 sugerida por proximidade estimada.
5. Aceitação criou nova reserva; ocupação de B-03 produziu nova confirmação.
6. Recarregar recuperou a confirmação pendente; resposta positiva concluiu a reserva.
7. Consulta ao MySQL confirmou EXPIRED, DISPLACED e COMPLETED e duração de 90s.
8. Serviços encerrados e reiniciados pelo launcher. A reserva A-08 persistida foi
   recuperada e expirou pelo agendador do novo processo, mantendo o prazo original.

Screenshot local: `analysis/mobile-confirmation.jpg` na pasta superior dos repositórios.
Viewport móvel de 390×844 conferido visualmente. Túnel é temporário: não registre
o hostname em configuração permanente nem espere disponibilidade após encerramento.

## Limites e verificações externas

- Não foi usado um celular físico nem um segundo computador. A origem pública foi
  testada por HTTPS/WSS nesta máquina e no navegador; repetir no dispositivo de apresentação.
- Não foram executados runners GitHub Actions nesta sessão: commits ainda são locais.
  Os workflows independentes executam os mesmos comandos e falham se testes falharem.
- A-01 não recebe veículo neste vídeo. Não se declara acurácia de detecção de veículo
  nessa vaga nem generalização para outra câmera; ver `monitoramento-vagas/docs/PCD.md`.
- Distância estimada entre posições da imagem, sem métricas reais de circulação.
- O controle usa um registro de coordenação no MySQL: adequado ao catálogo pequeno;
  carga elevada exige medir contenção e conexões antes de ampliar a instalação.
- Limite de reservas por IP visto pelo Java é compartilhado na fronteira Node.
  Quick Tunnel não oferece disponibilidade contínua; máquina/serviços devem ficar ativos.
