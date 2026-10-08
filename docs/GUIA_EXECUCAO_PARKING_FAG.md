# Parking FAG — guia de execução e funcionamento

Este guia descreve a versão atual dos três projetos, com comandos para Windows PowerShell. Use um terminal separado para cada aplicação. A pasta usada neste computador é `C:\Temp\parkingfag`; se instalar em outro local, ajuste os caminhos.

## 1. Visão geral

| Projeto | Responsabilidade | Tecnologia |
| --- | --- | --- |
| `monitoramento-vagas` | Ler o vídeo, detectar o estado das vagas e enviar as observações | Python e OpenCV |
| `orchestrador-parking-fag` | Receber as observações, salvar no banco e fornecer a API | Java 17 e Spring Boot |
| `view-parking-fag` | Apresentar o site e o painel de vagas | React, TypeScript e Vite |

O fluxo é:

```text
Vídeo MP4 em loop
        ↓
Python: análise das vagas
        ↓ HTTP
Java: validação e API REST ↔ MySQL: últimos estados e logs
        ↓ HTTP
React: site completo e painel isolado
```

Somente o Java acessa o MySQL. O Python envia dados ao Java, e o frontend consulta o Java.

## 2. Pré-requisitos e organização

Instale ou disponibilize no PATH:

- Java JDK 17.
- MySQL 8, com o serviço em execução.
- Python 3.12 ou 3.13; a validação local usou Python 3.12.
- Node.js 24 e npm.
- Git, caso precise baixar os repositórios.

O Maven é obtido pelo wrapper incluído no projeto Java. A primeira instalação das dependências precisa de internet.

Confira os programas no PowerShell:

```powershell
java -version
python --version
node --version
npm.cmd --version
```

Organização esperada:

```text
C:\Temp\parkingfag\
├── monitoramento-vagas\
│   └── video\estacionamento.mp4
├── orchestrador-parking-fag\
└── view-parking-fag\
```

O MP4 está incluído no projeto Python em `video/estacionamento.mp4`. Caso utilize outra fonte, ajuste `VIDEO_PATH` no `.env` do Python.

Se os projetos ainda não estiverem baixados, execute na pasta que receberá os três repositórios:

```powershell
git clone https://github.com/gmmouraFag/monitoramento-vagas.git
git clone https://github.com/gmmouraFag/orchestrador-parking-fag.git
git clone https://github.com/gmmouraFag/view-parking-fag.git
```

## 3. Banco de dados e projeto Java

### Preparação, somente na primeira execução

Inicie o serviço MySQL. Em seguida:

```powershell
Set-Location 'C:\Temp\parkingfag\orchestrador-parking-fag'
Copy-Item .env.example .env
notepad .env
```

Copie o exemplo somente na configuração inicial, para preservar alterações futuras no `.env`.

Configure as credenciais reais do seu MySQL. O exemplo local contém:

```dotenv
DB_HOST=localhost
DB_PORT=3306
DB_NAME=parking_fag
DB_USER=root
DB_PASSWORD=root
SERVER_PORT=8080
CORS_ORIGINS=http://localhost:5173
MONITOR_SOURCE=parking-video
MONITOR_STALE_SECONDS=45
LOG_RETENTION_DAYS=7
REMOVE_MISSING_SPOTS=false
INGEST_API_KEY=
```

`root/root` é a configuração de desenvolvimento deste ambiente; em outra máquina, use o usuário e a senha configurados nela. O Java cria o banco `parking_fag` se o usuário tiver permissão. Caso contrário, crie previamente esse banco e conceda ao usuário as permissões necessárias para aplicar as migrations e ler/gravar dados.

### Iniciar o Java — terminal 1

```powershell
Set-Location 'C:\Temp\parkingfag\orchestrador-parking-fag'
.\scripts\run.ps1
```

O script carrega o `.env`, compila o projeto e inicia o arquivo JAR. Mantenha esse terminal aberto. O Spring Boot executado diretamente não carrega o `.env` automaticamente; nesse caso, as variáveis devem estar definidas no ambiente.

Confira no navegador:

- Saúde da aplicação e do banco: http://localhost:8080/actuator/health
- Dados das vagas: http://localhost:8080/api/v1/parking-spots
- Documentação interativa da API: http://localhost:8080/swagger-ui.html

O catálogo começa vazio em um banco novo. As vagas são cadastradas quando o Python envia a configuração.

### Como o Java funciona

O backend recebe a configuração completa e alterações de estado, valida os dados e guarda o estado mais recente de cada vaga. Observações antigas não substituem observações mais novas. O Flyway cria a estrutura do banco pelas migrations.

As tabelas de domínio são `parking_spots` e `system_logs`; o Flyway mantém também sua tabela técnica de migrations. Os logs operacionais têm retenção padrão de sete dias. Não existe uma tabela com todas as transições históricas das vagas.

Principais endpoints:

| Método e caminho | Função |
| --- | --- |
| `POST /api/v1/parking-spots/sync` | Receber configuração e estados completos do Python |
| `POST /api/v1/parking-spots/events` | Receber alterações de estado |
| `POST /api/v1/logs` | Receber logs operacionais |
| `GET /api/v1/parking-spots` | Fornecer vagas, resumo e informações do monitoramento |
| `GET /api/v1/parking-spots/summary` | Fornecer as contagens e o percentual de ocupação |

`REMOVE_MISSING_SPOTS=false` conserva vagas cadastradas que deixem de aparecer na configuração recebida. O padrão atual deve ser mantido até definir a regra de remoção.

## 4. Projeto Python: vídeo e detecção

### Preparação, somente na primeira execução

```powershell
Set-Location 'C:\Temp\parkingfag\monitoramento-vagas'
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m pip install -e . --no-deps
Copy-Item .env.example .env
notepad .env
```

O comando `python` deve apontar para a versão 3.12 ou 3.13. Se usar o launcher do Windows, pode substituir o primeiro comando de criação por `py -3.12 -m venv .venv`.

Configurações principais:

```dotenv
VIDEO_PATH=video/estacionamento.mp4
SPOTS_CONFIG=config/spots.json
JAVA_API_URL=http://localhost:8080
MONITOR_SOURCE=parking-video
INGEST_API_KEY=
PROCESS_FPS=2
CONFIRM_FRAMES=3
SYNC_SECONDS=0.1
PREVIEW=true
```

O caminho relativo do vídeo acima parte da pasta `monitoramento-vagas`. Também é possível informar um caminho absoluto. `MONITOR_SOURCE` e a chave opcional `INGEST_API_KEY` precisam corresponder aos valores do Java.

### Iniciar o Python — terminal 2

Com o Java já em execução:

```powershell
Set-Location 'C:\Temp\parkingfag\monitoramento-vagas'
.\.venv\Scripts\python.exe -m parking_monitor.main --validate-config
.\scripts\run.ps1
```

A primeira chamada valida o arquivo de configuração. A segunda abre a janela do vídeo, com contornos, códigos e estados das vagas. O vídeo volta automaticamente ao início quando termina e continua em loop enquanto o monitor estiver aberto.

Para executar sem janela de vídeo:

```powershell
.\scripts\run.ps1 -Headless
```

### Como a detecção funciona

São monitoradas 21 vagas com limites identificáveis: 13 no setor A e oito no setor B. Quatro estão marcadas como acessíveis. Os códigos identificam a configuração criada para este vídeo.

As regiões são polígonos definidos em `config/spots.json`. O OpenCV analisa a densidade de bordas na parte interna de cada região e compara o resultado com os limiares calibrados:

- `FREE`: vaga livre.
- `OCCUPIED`: vaga ocupada.
- `UNKNOWN`: análise incerta ou estado ainda não confirmado.

O padrão analisa duas amostras por segundo e confirma uma mudança após três amostras consecutivas. O vídeo continua sendo exibido na sua cadência original. A classificação usa os pixels do vídeo; não depende de estados simulados nem de GPU.

Alterações são enviadas ao Java em uma thread separada, que verifica envios a cada 250 ms. `SYNC_SECONDS` controla as sincronizações completas; o exemplo atual usa 0.1 segundo, respeitando a cadência da thread e das novas observações. Se a comunicação falhar, tenta novamente e prioriza o estado mais recente, sem exigir a recuperação de todas as transições intermediárias. Os logs locais ficam em `runtime/monitor.log`.

A calibração é específica do vídeo fornecido. Outra câmera, posição ou iluminação exige ajustar os polígonos e limiares. Sombras, pinturas e objetos podem influenciar a classificação.

## 5. Projeto React: site e painel

### Preparação, somente na primeira execução

```powershell
Set-Location 'C:\Temp\parkingfag\view-parking-fag'
npm.cmd ci
Copy-Item .env.example .env
```

A configuração padrão usa:

```dotenv
VITE_API_BASE_URL=
JAVA_PROXY_URL=http://localhost:8080
VITE_POLL_INTERVAL_MS=500
```

Com `VITE_API_BASE_URL` vazio, o navegador consulta `/api` no mesmo servidor do site. O Vite encaminha essas chamadas para o Java definido em `JAVA_PROXY_URL`.

### Iniciar o frontend — terminal 3

```powershell
Set-Location 'C:\Temp\parkingfag\view-parking-fag'
npm.cmd run dev
```

Acesse:

- Site completo: http://localhost:5173/
- Somente o painel: http://localhost:5173/painel

O comando permanece em execução no terminal. Se a porta 5173 estiver ocupada, confira a URL informada pelo Vite.

### Como o frontend funciona

As duas páginas usam os mesmos dados do Java. A interface mostra vagas, setores, acessibilidade, contagens e percentual de ocupação. Verde representa livre, vermelho ocupada e cinza desconhecida.

A consulta inicial é imediata. A próxima ocorre 500 ms após uma consulta bem-sucedida ou dois segundos após uma falha. `VITE_POLL_INTERVAL_MS` ajusta o intervalo de sucesso entre 250 e 30000 ms; valores inválidos usam 500 ms. O frontend preserva a última leitura, também armazenada no navegador, e não mostra erros técnicos ao público. Sem leitura anterior, apresenta a espera pelo monitoramento.

Uma alteração persistida no Java aparece na próxima consulta, normalmente em cerca de 500 ms mais o tempo da requisição. Antes disso, o Python precisa confirmar três amostras consecutivas; a 2 FPS, isso exige cerca de 1 a 1,5 segundo de classificação consistente. Se a análise oscilar por sombras ou movimento, essa confirmação pode demorar mais.

O percentual considera somente vagas com estado conhecido:

```text
ocupação (%) = ocupadas ÷ (livres + ocupadas) × 100
```

## 6. Rotina diária e encerramento

Após a configuração inicial, não é necessário recriar os ambientes nem copiar os `.env` a cada execução.

1. Inicie o MySQL.
2. No terminal 1, execute o script do Java.
3. Aguarde o endpoint de saúde responder e, no terminal 2, execute o Python.
4. No terminal 3, execute `npm.cmd run dev`.
5. Abra o site ou `/painel` e confira a atualização das vagas.

Para encerrar o Python, pressione `q` na janela do vídeo, feche a janela ou use `Ctrl+C` no terminal. Encerre React e Java com `Ctrl+C` em seus terminais. Ao parar o monitoramento, os últimos estados continuam disponíveis; isso não significa que estejam recebendo novas observações.

O backend marca o monitoramento como inativo quando as observações deixam de ser recentes, usando 45 segundos como limite padrão. Essa interrupção não transforma as vagas em `UNKNOWN`; esse estado corresponde à incerteza da análise.

## 7. Acesso na rede local

Execute `ipconfig` no computador servidor e localize seu endereço IPv4. Nos outros computadores da mesma rede, use:

```text
http://IP_DO_SERVIDOR:5173/
http://IP_DO_SERVIDOR:5173/painel
```

O frontend já escuta em `0.0.0.0`. Se o acesso for bloqueado, verifique a liberação da porta 5173 no Firewall do Windows para a rede usada. Com o proxy padrão, o navegador dos clientes acessa a API por meio do servidor do frontend.

Se o Java estiver em outra máquina, ajuste `JAVA_API_URL` no Python e `JAVA_PROXY_URL` no frontend. Se o navegador acessar o Java diretamente, configure `VITE_API_BASE_URL` e inclua a origem do site em `CORS_ORIGINS` no Java. Reinicie os serviços após mudar variáveis.

A versão atual oferece leitura sem login na rede local. A chave opcional de ingestão pertence ao Java e ao Python; credenciais do banco e essa chave não devem ser colocadas nas variáveis `VITE_*`, que são expostas ao navegador.

## 8. Problemas comuns

| Sintoma | O que conferir |
| --- | --- |
| Java não inicia | Java 17, serviço MySQL ativo, host, porta, usuário, senha e permissão no banco |
| Vídeo não abre | Existência do MP4 e `VIDEO_PATH`; execute o Python dentro da pasta do projeto |
| Frontend fica aguardando | Java e Python ativos; configuração válida; URL do Java; lista retornada pela API |
| Dados ficam parados | Janela do vídeo, terminal do Python, `runtime/monitor.log` e conectividade com o Java |
| Outro computador não abre o site | IPv4 correto, mesma rede, frontend ativo e firewall |
| PowerShell bloqueia o script | Para a sessão atual, execute `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` e tente novamente |

Durante falhas, a tela conserva os dados anteriores. Use o horário da leitura e os logs para distinguir dados preservados de monitoramento ativo.

## 9. Verificação e build

Estes comandos são opcionais para operação diária e úteis depois de alterar código.

Python:

```powershell
Set-Location 'C:\Temp\parkingfag\monitoramento-vagas'
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
.\.venv\Scripts\python.exe -m ruff check .
.\.venv\Scripts\python.exe -m pytest -q
```

Java:

```powershell
Set-Location 'C:\Temp\parkingfag\orchestrador-parking-fag'
.\mvnw.cmd verify
```

Frontend:

```powershell
Set-Location 'C:\Temp\parkingfag\view-parking-fag'
npm.cmd run lint
npm.cmd run typecheck
npm.cmd test
npm.cmd run build
npm.cmd run preview
```

`build` gera a pasta `dist`. `preview` permite conferir esse build localmente; use a URL informada no terminal. Para hospedagem permanente, o servidor que servir `dist` precisa encaminhar `/api` ao Java ou usar uma API configurada separadamente, além de direcionar rotas como `/painel` para `index.html`.

Os contratos detalhados estão em `docs/API.md` e os resultados da validação em `docs/VALIDATION.md` de cada projeto.
