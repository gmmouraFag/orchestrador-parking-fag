# Orquestrador Parking FAG

Backend central Java/Spring Boot. Recebe o monitor Python, persiste no MySQL e fornece snapshots ao React.

## Execução local

Pré-requisitos: Java 17 e MySQL 8 em execução. Maven é obtido pelo wrapper; não exige instalação global.
Copie `.env.example` para `.env` e ajuste as variáveis. Credenciais `root/root` destinam-se somente ao desenvolvimento local.

Windows:

```powershell
Copy-Item .env.example .env
./scripts/run.ps1
```

Linux/macOS, com variáveis exportadas no terminal:

```sh
chmod +x mvnw
./mvnw verify
./mvnw spring-boot:run
```

O script PowerShell carrega `.env`; Spring Boot sozinho lê variáveis de ambiente, não esse arquivo automaticamente.
O banco `parking_fag` é criado pelo JDBC quando o usuário tem permissão; em ambientes restritos, crie-o previamente.
Flyway aplica as migrations e Hibernate valida o schema. Nenhuma vaga é inserida até o Python enviar sua configuração real.

- API: [localhost:8080/api/v1/parking-spots](http://localhost:8080/api/v1/parking-spots)
- Saúde: [localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- Documentação: [localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

## Testes e build

```powershell
./mvnw.cmd verify
$env:MYSQL_TEST = 'true'
./mvnw.cmd verify
```

A segunda execução também verifica as migrations e os mesmos contratos no MySQL real, em `parking_fag_test`, separado do banco de monitoramento.
`TEST_DB_NAME` permite outro banco exclusivamente de testes. Esses testes limpam as tabelas desse banco.
CI executa `verify` com MySQL 8 isolado. Testes sem MySQL usam H2 e não substituem a validação MySQL.

## Integração

1. Inicie MySQL e este backend.
2. No repositório `monitoramento-vagas`, inicie o vídeo em loop.
3. No repositório `view-parking-fag`, execute `npm ci` e `npm run dev`.
4. Verifique a listagem, contagens e sincronização antes de apresentar na rede.

Para hospedagem separada, ajuste banco, `CORS_ORIGINS`, endereço do backend no Python e API/proxy do React.
Uma chave opcional de ingestão fica no backend e no Python. Leituras não exigem autenticação nesta versão para rede local.

[Contrato de API](docs/API.md) · [Decisões](docs/DECISIONS.md) · [Resultados de validação](docs/VALIDATION.md)
