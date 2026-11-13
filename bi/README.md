# 2bike4 · Painel de rendimento no Power BI

Painel que mostra **quanto os donos de bike ganham** alugando pela plataforma:
receita por mês, por dono, por tipo de bike e por cidade.

## O que é cada coisa

- **Power BI Desktop**: programa gratuito da Microsoft para fazer gráficos a partir de
  planilhas/tabelas. Você arrasta colunas para dentro de gráficos e ele soma e agrupa sozinho.
- **`bi/dados/rendimento.csv`**: a tabela com os dados, tirada do banco do app. Uma linha por
  reserva, com estas colunas:

| Coluna | O que é |
|---|---|
| `reserva` | número da reserva |
| `mes` | mês da retirada (ex.: `2026-07`) |
| `data` | dia da retirada |
| `dono` | nome do dono da bike (o locador) |
| `bike`, `tipo`, `cidade` | qual bike foi alugada |
| `diarias` | quantos dias de aluguel |
| `situacao` | Concluída, Confirmada, Aguardando, Recusada ou Cancelada |
| `pago` | Sim / Não |
| `receita_centavos` | quanto o dono recebeu, **em centavos** (22500 = R$ 225,00); 0 se não foi pago |
| `nota_da_bike` | nota de 1 a 5 que o locatário deu (vazio se não avaliou) |

> Os outros arquivos da pasta (`reservas`, `bikes`, `donos`, `avaliacoes`) são os mesmos dados
> separados em tabelas, para quem quiser montar um modelo mais completo. Não precisa usar.

## Passo a passo (uns 20 minutos)

### 1. Instalar
Microsoft Store → procure **Power BI Desktop** → Instalar. Abra e feche a tela de login
(não precisa de conta para usar no computador).

### 2. Carregar os dados
1. **Página Inicial → Obter dados → Texto/CSV**.
2. Escolha `bi\dados\rendimento.csv` → aparece uma prévia da tabela → **Carregar**.
3. À direita, no painel **Dados**, aparece a tabela `rendimento` com as colunas.

### 3. Criar a medida "Receita (R$)"
Medida é uma conta que o Power BI refaz para cada gráfico. Esta transforma centavos em reais.

1. Clique com o botão direito na tabela `rendimento` (painel Dados) → **Nova medida**.
2. Na barra de fórmula que aparece em cima, apague tudo e cole:
   ```
   Receita (R$) = SUM ( rendimento[receita_centavos] ) / 100
   ```
   e aperte Enter.
3. Com a medida selecionada, na faixa de cima **Ferramentas de medida → Formato → Moeda**.

Se quiser, crie também (mesmo jeito, uma de cada vez):
```
Reservas pagas = CALCULATE ( COUNTROWS ( rendimento ), rendimento[pago] = "Sim" )
```
```
Ticket médio (R$) = DIVIDE ( [Receita (R$)], [Reservas pagas] )
```
```
Nota média = AVERAGE ( rendimento[nota_da_bike] )
```

### 4. Montar os gráficos
Para cada gráfico: clique num espaço vazio da página → clique no ícone do gráfico no painel
**Visualizações** → arraste as colunas do painel Dados para os campos indicados.

| Gráfico | Ícone | O que arrastar |
|---|---|---|
| **Cartão** (número grande) ×4 | Cartão | Receita (R$) · Reservas pagas · Ticket médio (R$) · Nota média (um em cada cartão) |
| **Receita por mês** | Gráfico de colunas | Eixo X: `mes` · Eixo Y: Receita (R$) |
| **Quanto cada dono ganhou** | Gráfico de barras | Eixo Y: `dono` · Eixo X: Receita (R$) |
| **Receita por tipo de bike** | Gráfico de rosca | Legenda: `tipo` · Valores: Receita (R$) |
| **Receita por cidade** | Gráfico de barras ou Mapa | `cidade` · Receita (R$) |
| **Filtro por dono** | Segmentação de dados | Campo: `dono` |

- No gráfico por mês, se os meses saírem fora de ordem: clique nos **⋯** do gráfico →
  **Classificar eixo → mes** → **Classificar em ordem crescente**.
- O **filtro por dono** é o mais legal para apresentar: clicando num nome, a página toda
  mostra só o rendimento daquele locador.
- Cores da marca: **Exibir → Temas → Personalizar o tema atual** → cor 1 `#15803d` (verde),
  cor 2 `#f97316` (laranja).
- Título: **Inserir → Caixa de texto** → "2bike4 · Rendimento dos donos de bike".

### 5. Salvar
**Arquivo → Salvar como** → `bi\2bike4-rendimento.pbix`. É esse arquivo que você mostra
na disciplina (e pode tirar prints para os slides).

## O que falar na apresentação

O painel responde:
1. Quanto a plataforma gerou de renda para os donos e como isso muda mês a mês.
2. Quais donos e quais bikes rendem mais — e se bike com nota maior rende mais.
3. Qual tipo de bike dá mais receita.
4. Em quais cidades está a demanda.
5. Quantas reservas foram perdidas (filtre `situacao` = Cancelada/Recusada).

**Sobre os dados:** são os dados de demonstração do app (reservas de jun. a ago. de 2026 mais
as feitas nos testes), por isso os meses mais recentes têm pouca receita. Dá para dizer que
o painel se atualiza sozinho quando o app tiver uso real: basta gerar o CSV de novo e clicar em
**Página Inicial → Atualizar**.

## Gerar os dados de novo

```bash
cd backend
DATABASE_URL=postgresql://... clojure -M:bi
```

Só lê o banco e sobrescreve os arquivos de `bi/dados/`. Com o `DATABASE_URL` da branch
`production` do Neon, sai com os dados do site publicado.
