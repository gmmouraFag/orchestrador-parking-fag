# Validação local — 7 de outubro de 2026

Ambiente: Windows, Python 3.12.14, Java 17.0.19, Node 24.16.0, npm 11.13, MySQL 8.0.46.
Código desenvolvido nas branches `main` dos três projetos. Sem push ao GitHub nesta etapa.

## Resultado dos comandos

| Verificação | Resultado | Evidência |
|---|---|---|
| Python pytest | Aprovado: 9 testes | Estabilidade, estados, payload, retry, sincronização, concorrência com publicação, timeout, EOF e falha de captura |
| Python lint | Aprovado | `python -m ruff check .` |
| Importação/compilação Python | Aprovado | `python -m compileall -q src` e validação da configuração |
| Instalação Python em ambiente limpo | Aprovado | Instalação pelos requirements; pytest e `pip check`, sem Torch/modelos |
| Java/H2 | Aprovado: 6 testes | Transições, idempotência, datas antigas, validação/atomicidade, consulta, logs e retenção |
| Java/MySQL real | Aprovado: 6 testes | Mesmo contrato em `parking_fag_test`, migrations Flyway e schema validado |
| Java build/package | Aprovado | `MYSQL_TEST=true ./mvnw verify` |
| React testes | Aprovado: 9 testes | Vagas, acessibilidade, indicadores, vazio, polling 15/30s, cancelamento, cache, validação e painel isolado |
| React lint e tipagem | Aprovado | ESLint e TypeScript |
| React build de produção | Aprovado | `npm run build` |
| Sintaxe dos scripts PowerShell | Aprovado | Parser PowerShell dos scripts dos projetos e atalhos integrados |
| Atalhos integrados start/stop | Não executado em conjunto | Serviços foram iniciados diretamente para validação; scripts têm somente sintaxe verificada |
| GitHub Actions remoto | Não executado | Workflows configurados; comandos executados localmente. Exige push para rodar no GitHub |
| CI em Linux | Não executado localmente | Workflows preparados para runner Ubuntu e MySQL 8 isolado |

## Vídeo real e integração

O vídeo fornecido contém 1451 frames e duração aproximada de 60,5 segundos. Foram analisadas 121 amostras a 2 FPS,
com confirmação de três amostras consecutivas. Conferência visual de regiões e frames reais, sem geração de estados fictícios.

Transições encontradas na execução de validação:

| Instante no vídeo | Vaga | Estado confirmado |
|---|---|---|
| 5,5 s | B-03 | Livre após saída do veículo vermelho |
| 17,5 s | B-03 | Ocupada após chegada de outro veículo |
| 25,5 s | A-08 | Livre |
| 35,0 s | B-06 | Livre |
| 44,5 s | A-01 | Desconhecida durante aumento de textura/oclusão na região |
| 48,5 s | A-09 | Livre |
| 57,5 s | B-06 | Ocupada novamente |

EOF foi verificado por reposicionamento ao fim e leitura do primeiro frame. Uma execução real de 75 segundos também ultrapassou a duração do arquivo,
e uma execução contínua posterior permaneceu em vários ciclos.

Fluxo MP4 → Python → Java → MySQL → React: aprovado localmente. Banco de monitoramento contém 21 vagas reais configuradas e 4 identificadas como acessíveis.
Contagens do snapshot, total e percentual sobre estados conhecidos foram conferidos por script e consultas ao banco.

## Indisponibilidade e reinício

O processo Java foi interrompido temporariamente durante a captura contínua. O Python registrou uma falha operacional,
continuou processando e entrou em retry. A interface conservou estados e horário anterior, sem mensagens de erro, inclusive após recarregar pelo cache.
Após reinício do Java, houve sincronização completa e log de recuperação persistido no MySQL.
Health check e OpenAPI responderam. O backend foi reiniciado novamente para carregar o build final.

Reinício do monitor após execução limitada foi executado e o catálogo voltou a ser sincronizado sem duplicar códigos.
Falhas de leitura e frames inválidos foram verificadas em testes; corrupção física do MP4 e interrupção do MySQL não foram provocadas.

## Interface

Conferida no navegador com dados da API, em visualização física e painel. Vagas com deficiência aparecem com símbolo e texto acessível.
Viewport móvel de 390×844: sem transbordamento horizontal; painel, indicadores e seções empilhados.
Capturas locais: `analysis/frontend-desktop.jpg` e `analysis/frontend-mobile.jpg`, na pasta superior dos repositórios.

## Limitações e decisões pendentes

Atualização posterior: rota `/painel` verificada no navegador e por HTTP 200, preservando `/`.
Monitor reiniciado com janela OpenCV, códigos/estados sobrepostos e leitura de todos os frames para reprodução contínua.
Reinício no EOF observado em execução real com prévia aberta. Detecção permanece em 2 amostras por segundo, independente da exibição.
9 testes Python, 9 React, lint e build React aprovados nesta atualização; backend não sofreu alteração.

- A calibração de textura foi feita para este vídeo fixo. Não é uma validação estatística de acurácia nem garantia para outra câmera, iluminação ou resolução.
- Sombras, símbolos pintados e objetos podem afetar a densidade de bordas. A faixa de incerteza e confirmação temporal reduzem oscilações, sem eliminá-las completamente.
- Não foram anotados todos os frames com verdade de referência; não se declara percentual de acurácia.
- Remoção de vagas ausentes depende da definição do usuário. `REMOVE_MISSING_SPOTS=false` preserva o cadastro por padrão; opção de remoção testada apenas no banco isolado.
- Um monitor e uma instância Java. Múltiplos produtores/instâncias exigem revisão persistente de catálogo e concorrência no banco.
- Histórico completo de transições não é mantido, conforme escolha do usuário. A recuperação prioriza o estado mais recente.
- Rede local acessível pela porta 5173; acesso a partir de outro dispositivo físico e regras de firewall não foram testados.
- Execução permanente significa processo contínuo enquanto iniciado. Inicialização como serviço do Windows não foi configurada.
- Pipelines ainda precisam ser executadas no GitHub após envio dos arquivos.
