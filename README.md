# Orquestrador Parking FAG

Java 17 / Spring Boot / MySQL 8. Autoridade dos estados, reservas de 90 segundos,
confirmações privadas, logs e eventos WebSocket para totem e mobile React.

Leia o [guia integrado atualizado](docs/EXECUCAO_RESERVAS.md) para preparar os três
repositórios, configurar o banco, iniciar o sistema e acessar pelo celular via HTTPS.

```powershell
# MySQL ativo, .env configurado e ambiente Python preparado:
.\scripts\run-stack.ps1
# Sem janela OpenCV:
.\scripts\run-stack.ps1 -Headless
# Encerrar os serviços rastreados:
.\scripts\stop-stack.ps1
```

Somente backend: `scripts/run.ps1`. Copie `.env.example` somente na preparação inicial.
Credenciais root/root são exclusivamente locais. Ingestão usa X-API-Key, reservas
usam sessão anônima HttpOnly. Java escuta em 127.0.0.1 por padrão.

Rotas do frontend: `/totem` e `/mobile`; `/` mostra o totem e `/painel` é alias legado.
Não exponha Java/MySQL diretamente: o servidor do build React filtra as APIs públicas.
Swagger local: http://127.0.0.1:8080/swagger-ui.html e `/v3/api-docs`.

[Contratos e regras](docs/RESERVATIONS.md) · [Validação](docs/VALIDATION_RESERVAS.md).
UTC na persistência/contratos e Brasília na apresentação. PCD bloqueada para reservas
até definir elegibilidade; alternativa usa distância estimada no mapa, autorizada pelo usuário.

```powershell
$env:MYSQL_TEST='true'
.\mvnw.cmd verify
```

Os testes usam bancos isolados `parking_fag_test` e `parking_fag_test_reservations`;
nunca configure TEST_DB_NAME para apontar ao banco operacional.
