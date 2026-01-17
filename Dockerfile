# Etapa 1: Construção (Usa uma imagem que já tem o Maven instalado)
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
# O comando abaixo constrói o site ignorando os testes para ser mais rápido
RUN mvn clean package -DskipTests

# Etapa 2: Execução (Usa um Java leve para rodar o site)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]