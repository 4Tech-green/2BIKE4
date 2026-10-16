# 2Bike4

Aluguel de bicicletas P2P. Backend em Clojure (`backend/`), frontend em ClojureScript (`frontend/`, em breve).

## Backend

Requisitos: JDK 21, Clojure CLI e a variável de ambiente `DATABASE_URL`
(connection string do Postgres no Neon, formato `postgresql://...`).

```
cd backend
clojure -M:run        # sobe a API em http://localhost:3000 e aplica as migrações
clojure -M:test       # testes
clojure -M:migrate    # só as migrações (rollback | create <nome>)
```

REPL: `clojure -M:dev`, depois `(go)`, `(reset)` e `(halt)`.

Saúde da API: `GET /api/v1/health`.

Estrutura (hexagonal): cada módulo de negócio em `src/bikerent/<modulo>/` com
`domain` (regras puras), `ports` (protocolos), `adapters` (Postgres, IA, pagamento)
e `logic` (casos de uso). `shared/` tem o que é comum, `http.clj` as rotas e
`system.clj` monta tudo com Integrant a partir de `resources/config.edn`.
