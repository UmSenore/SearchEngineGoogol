# Instruções para Execução

## Mudanças no código

-Altere a chave do google api para a sua chave. A chave atual não está em uso.

## Inicializando na pasta `/java`

- Para executar o programa, utilize o script: `./build.sh`.
- Para executar cada componente individualmente, aceda à pasta `target/`: cd target/

- E utilize um dos seguintes comandos conforme o componente que deseja executar
  - `java -cp "./lib/jsoup-1.18.3.jar:." search.IndexServer`
  - `java -cp "./lib/jsoup-1.18.3.jar:." search.Robot`
  - `java -cp "./lib/jsoup-1.18.3.jar:." search.Client`
  - `java -cp "./lib/jsoup-1.18.3.jar:." search.Barrel

## Inicializando na pasta `/webserver`

- Para executar o servidor web com Spring Boot: `./mvnw spring-boot:run`
