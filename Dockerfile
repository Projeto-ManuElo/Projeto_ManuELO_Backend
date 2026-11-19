FROM eclipse-temurin:21-jdk AS build

ENV HOME=/app
RUN mkdir -p $HOME
WORKDIR $HOME
COPY . $HOME

# Cache do repositorio Maven. O Railway exige que o "id" de --mount=type=cache
# tenha o formato s/<service id>-<target path>. HOME=/app, entao o .m2 do Maven
# fica em /app/.m2 (nao em /root/.m2).
# O projeto usa Vaadin core (sem Pro/Offline key): nao ha secret mounts.
RUN --mount=type=cache,id=s/afc20b6d-5356-4f6d-9231-5745f3c0e8f8-/app/.m2,target=/app/.m2 \
    ./mvnw clean package -DskipTests

# Split the JAR into layers, so that the dependencies get their own image layer
RUN cp target/*.jar application.jar && \
    java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /application
COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./
EXPOSE 8080
# Let the heap use 75% of the container's memory limit, instead of the default 25%
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "application.jar", "--spring.profiles.active=prod"]
