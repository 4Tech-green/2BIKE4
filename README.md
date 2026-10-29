# 2Bike4

Aluguel de bicicletas P2P. Backend em Clojure (`backend/`), frontend em ClojureScript (`frontend/`).

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

Busca semântica (`GET /api/v1/search?q=...`): sem configuração usa vetores fake,
que só acham palavras em comum. Com `GEMINI_API_KEY` definida usa o Gemini, que
entende sinônimos. Ao trocar de um para o outro, rode
`update listings set embedding = null` no banco e reinicie a API para reindexar.

Estrutura (hexagonal): cada módulo de negócio em `src/bikerent/<modulo>/` com
`domain` (regras puras), `ports` (protocolos), `adapters` (Postgres, IA, pagamento)
e `logic` (casos de uso). `shared/` tem o que é comum, `http.clj` as rotas e
`system.clj` monta tudo com Integrant a partir de `resources/config.edn`.

## Frontend

Requisitos: Node.js 20+ e JDK 21 (o shadow-cljs roda na JVM). Com a API rodando:

```
cd frontend
npm install
npm run dev     # http://localhost:8280, recompila CSS e CLJS ao salvar
npm run build   # versão de produção em public/ (API_URL=https://... aponta a API)
```

re-frame + Reagent, rotas com hash (`#/bikes/1`) para funcionar igual na web, no
Capacitor e no Electron. Telas em `src/bikerent_ui/views/`; componentes
reutilizáveis (card de bike, badge de status, estrelas...) em `views/comum.cljs`.
As cores são variáveis CSS em `src/css/main.css`: a identidade visual atual é
provisória e muda só ali.

Fotos: o dono envia a foto do celular/computador direto para o Cloudinary
(conta `xcmfxt9w`, upload preset unsigned `2bike4_fotos`, padrões em
`shadow-cljs.edn`; outra conta = variáveis `CLOUDINARY_CLOUD` e `CLOUDINARY_PRESET`
no build). Com os dois vazios, a tela pede o link de uma imagem já publicada.

## Dados de demonstração

`DATABASE_URL=... clojure -M:seed` (em `backend/`) apaga e recria 12 pessoas,
45 bikes em 11 capitais, cerca de 100 reservas e 200 avaliações. Pode rodar
quantas vezes quiser; só mexe nas contas `@2bike4.test`. Senha de todas: `demo1234`.
As fotos (`backend/dev/fotos.edn`) são do Wikimedia Commons, com autores e
licenças em [CREDITOS-FOTOS.md](CREDITOS-FOTOS.md); as speeds ficam sem foto.

- `ana@2bike4.test`: dona das bikes de Recife/Olinda, com pedidos para confirmar
- `bruno@2bike4.test`: locatário, com uma reserva em cada estado (pagar, avaliar...)

## Deploy (Render + Neon)

Um serviço só: o `Dockerfile` compila o frontend e o backend, e a API entrega
as telas na mesma URL. No Render: *New → Blueprint*, escolher este repositório
(usa o `render.yaml`) e preencher `DATABASE_URL` com a connection string do
branch `main` do Neon. As migrações rodam ao subir; depois, rodar o seed
apontando para o mesmo banco. No plano gratuito o serviço dorme após 15 minutos
sem acesso e leva cerca de 1 minuto para acordar.
