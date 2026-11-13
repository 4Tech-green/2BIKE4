# 2bike4 · Análise de rendimento no Power BI

Painel sobre quanto os donos de bike ganham na plataforma: receita por mês, por dono,
por cidade e por tipo de bike, ticket médio, diárias alugadas e nota das bikes.

## 1. Gerar os dados

Os CSVs saem do banco (só leitura) com o script `backend/dev/bi.clj`:

```bash
cd backend
DATABASE_URL=postgresql://... clojure -M:bi
```

Gera em `bi/dados/`:

| Arquivo | Uma linha por | Colunas principais |
|---|---|---|
| `reservas.csv` (fato) | reserva | `bike_id`, `dono_id`, `inicio`, `fim`, `diarias`, `status`, `pagamento`, `valor_centavos`, `receita_centavos` |
| `bikes.csv` | bike | `bike_id`, `dono_id`, `titulo`, `tipo`, `cidade`, `diaria_centavos` |
| `donos.csv` | dono | `dono_id`, `nome`, `total_bikes` |
| `avaliacoes.csv` | avaliação | `reserva_id`, `bike_id`, `alvo`, `nota` (1 a 5) |

- `receita_centavos` = valor da reserva quando o pagamento foi capturado (reserva paga e não
  estornada); 0 nos demais casos. É o rendimento do dono.
- Valores em **centavos** (inteiros) de propósito: o Power BI em português leria `10.50` como
  1050. As medidas abaixo dividem por 100.
- Para usar os dados do site publicado, rode com o `DATABASE_URL` da branch `production` do Neon.

## 2. Importar (Power BI Desktop, gratuito)

1. **Página Inicial → Obter dados → Texto/CSV** → `reservas.csv` → **Carregar**.
   Repita para `bikes.csv`, `donos.csv` e `avaliacoes.csv`.
2. Confira os tipos em **Transformar dados**: `inicio`/`fim` como *Data*; `*_centavos`,
   `diarias`, `nota` como *Número inteiro*. Feche e aplique.

## 3. Relacionamentos (Exibição de modelo)

Arraste para ligar (todos 1 → *, filtro em direção única):

- `bikes[bike_id]` → `reservas[bike_id]`
- `bikes[bike_id]` → `avaliacoes[bike_id]`
- `donos[dono_id]` → `bikes[dono_id]`

Desative ou apague o relacionamento automático `donos[dono_id]` → `reservas[dono_id]`, se o
Power BI criar (o dono chega às reservas pela bike; dois caminhos deixam o modelo ambíguo).

Crie uma tabela de calendário (**Modelagem → Nova tabela**) e ligue `Calendario[Data]` →
`reservas[inicio]`:

```DAX
Calendario =
ADDCOLUMNS (
    CALENDAR ( MIN ( reservas[inicio] ), MAX ( reservas[fim] ) ),
    "Ano", YEAR ( [Date] ),
    "Mês", FORMAT ( [Date], "mmm/yy" ),
    "MesOrdem", YEAR ( [Date] ) * 100 + MONTH ( [Date] )
)
```

Renomeie a coluna `Date` para `Data` e, em `Mês`, use **Classificar por coluna → MesOrdem**.

## 4. Medidas (Modelagem → Nova medida, na tabela `reservas`)

```DAX
Receita (R$) = SUM ( reservas[receita_centavos] ) / 100

Reservas pagas = CALCULATE ( COUNTROWS ( reservas ), reservas[pagamento] = "captured" )

Total de reservas = COUNTROWS ( reservas )

Ticket médio (R$) = DIVIDE ( [Receita (R$)], [Reservas pagas] )

Diárias alugadas = CALCULATE ( SUM ( reservas[diarias] ), reservas[pagamento] = "captured" )

Receita por diária (R$) = DIVIDE ( [Receita (R$)], [Diárias alugadas] )

Taxa de cancelamento =
DIVIDE (
    CALCULATE ( COUNTROWS ( reservas ), reservas[status] IN { "cancelled", "rejected" } ),
    [Total de reservas]
)

Nota média das bikes =
CALCULATE ( AVERAGE ( avaliacoes[nota] ), avaliacoes[alvo] = "listing" )

Receita por bike (R$) = DIVIDE ( [Receita (R$)], DISTINCTCOUNT ( bikes[bike_id] ) )
```

Formate as medidas em R$ como **Moeda** e `Taxa de cancelamento` como **Porcentagem**.

## 5. Painel sugerido (uma página)

Tema: **Exibir → Temas → Personalizar** com verde `#15803d` (cor 1) e laranja `#f97316` (cor 2),
as cores da marca. Logo `frontend/public/brand/logo-fundo-branco.png` no canto superior.

| Posição | Visual | Campos |
|---|---|---|
| Linha de cima | 4 **Cartões** | Receita (R$) · Reservas pagas · Ticket médio (R$) · Nota média das bikes |
| Meio, esquerda | **Gráfico de colunas** — receita no tempo | Eixo: `Calendario[Mês]` · Valores: Receita (R$) |
| Meio, direita | **Gráfico de barras** — ranking de donos | Eixo: `donos[nome]` · Valores: Receita (R$) (ordenar desc.) |
| Baixo, esquerda | **Rosca** — receita por tipo de bike | Legenda: `bikes[tipo]` · Valores: Receita (R$) |
| Baixo, meio | **Mapa** ou barras — receita por cidade | Local: `bikes[cidade]` · Tamanho: Receita (R$) |
| Baixo, direita | **Tabela** — desempenho por bike | `bikes[titulo]`, Diárias alugadas, Receita (R$), Nota média das bikes |
| Lateral | **Segmentações** | `donos[nome]`, `bikes[cidade]`, `Calendario[Mês]` |

Com a segmentação de dono, o painel vira a visão de **um locador**: quanto ele ganhou,
em que meses, qual das bikes dele rende mais e como ela é avaliada.

Perguntas que o painel responde (bom para a apresentação):

1. Quanto a plataforma gerou de renda para os donos, e como isso evolui mês a mês?
2. Quais donos e quais bikes rendem mais? Bikes com nota maior rendem mais?
3. Que tipo de bike (elétrica, mountain, urbana...) traz mais receita por diária?
4. Em quais cidades a demanda está concentrada?
5. Quantas reservas se perdem (canceladas/recusadas)?

## Observação sobre os dados

Os dados vêm do seed de demonstração (`backend/dev/seed.clj`): histórico de reservas
concluídas de jun. a ago. de 2026 e as reservas do roteiro da demo perto da data em que o
seed rodou, mais o que tiver sido feito de verdade no app. Por isso os meses mais recentes
têm menos receita — diga isso na apresentação, ou rode o export no banco de produção depois
de usar o app.
