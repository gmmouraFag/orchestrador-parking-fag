# Decisões arquiteturais

- Os três repositórios estavam vazios ou somente com README. Implementação inicial em `main`, sem código anterior para preservar.
- Java 17, Spring Boot 3.5.16, Maven 3.9.11 e MySQL 8.0.46 foram usados na validação local. Wrapper Maven versionado.
- Duas tabelas de domínio. Flyway mantém uma terceira tabela técnica de migrations, sem adicionar entidade de negócio.
- `sector`, `is_accessible`, `layout_order`, `polygon_json` são campos adicionais necessários para que Java forneça ao frontend a organização física e acessibilidade. `is_accessible` evita palavra reservada do MySQL.
- Coordenadas normalizadas evitam inferir posição pela numeração; o Python mantém a configuração original e o Java persiste os metadados recebidos.
- Timestamps em microssegundos evitam falhas de idempotência por arredondamento do banco. Horários iguais preservam o primeiro estado aceito.
- Sem histórico de estados: o usuário definiu recuperação pelo estado mais recente. Logs não registram cada frame nem todas as transições.
- Estados permanecem durante indisponibilidade. A indicação de atividade é metadado separado, obtido das sincronizações, e não da existência de mudanças.
- Retenção de logs: 7 dias por padrão, com limpeza horária e índice de data. Python mantém arquivos rotativos e fila limitada de 1000 logs; durante uma falha muito longa, logs antigos excedentes permanecem somente nos arquivos locais. Estados recentes são sempre ressincronizados.
- Polling configurável de 500 ms após sucesso e retry de 2 segundos após falha atende à atualização próxima do tempo real na rede local. Consultas não se sobrepõem. A confirmação da detecção é uma etapa independente desse intervalo.
- Backend com uma instância e origem única configurável, adequado à apresentação local. Escala horizontal exige adaptação explícita.
- Remoção automática de vagas ausentes permanece desabilitada até resposta do usuário. A opção existe e é testada em ambiente isolado.
- Docker Compose não foi imposto: MySQL, Java e Node já estão disponíveis no ambiente. CI usa um serviço MySQL isolado e reproduzível.
