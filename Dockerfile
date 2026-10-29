# Imagem única: compila o frontend (shadow-cljs + Tailwind) e o backend
# (uberjar) e serve os dois pela API, numa URL só.

FROM clojure:temurin-21-tools-deps-bookworm AS build
RUN apt-get update \
 && apt-get install -y --no-install-recommends nodejs npm \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app

# dependências primeiro, para o cache do Docker reaproveitar entre deploys
COPY backend/deps.edn backend/build.clj backend/
RUN cd backend && clojure -P
COPY frontend/package.json frontend/package-lock.json frontend/
RUN cd frontend && npm ci

COPY frontend frontend
RUN cd frontend \
 && npx tailwindcss -i src/css/main.css -o public/css/main.css --minify \
 && API_URL=/api/v1 npx shadow-cljs release app

COPY backend backend
RUN mkdir -p backend/resources/public \
 && cp -r frontend/public/. backend/resources/public/ \
 && cd backend && clojure -T:build uber

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/backend/target/bikerent.jar bikerent.jar
EXPOSE 3000
# o plano gratuito tem 512 MB de memória
CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "bikerent.jar"]
