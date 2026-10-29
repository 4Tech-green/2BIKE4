# 2bike4 — identidade visual

Plataforma web de aluguel de bicicletas entre pessoas (P2P). Estas regras valem para qualquer tela, componente ou material que você criar.

## Cores (paleta "Verde natureza + laranja", fundo branco)

| Papel | Nome | Hex |
|---|---|---|
| Primária | Verde natureza | `#15803d` |
| Destaque (CTA, números, ícones de ação) | Laranja | `#f97316` |
| Texto / títulos | Azul-noite | `#0f172a` |
| Fundo | Branco | `#ffffff` |

Regras de uso:
- Fundo da aplicação é branco. Verde é a cor dominante (botões primários, links, cabeçalhos, pin).
- Laranja é só para destaque pontual (um CTA por tela, badges, preços, o "4" do logo). Não usar laranja como cor de fundo grande.
- Texto em `#0f172a`; texto secundário em `#0f172a` com 75% de opacidade.
- Estados: hover do verde `#166534`, hover do laranja `#ea580c`.
- Garantir contraste: texto branco sobre verde `#15803d` é ok; texto branco sobre laranja só em tamanho grande/negrito.

### Variáveis CSS

```css
:root {
  --color-primary: #15803d;
  --color-primary-hover: #166534;
  --color-accent: #f97316;
  --color-accent-hover: #ea580c;
  --color-text: #0f172a;
  --color-bg: #ffffff;
}
```

## Tipografia

Poppins (Google Fonts). Títulos e logo em 700/800, texto em 400/500.

## Logo

Arquivos em `brand/` (ou na pasta de assets do projeto):

- `logo.svg` / `logo-fundo-branco.svg` — pin com roda de bicicleta + "2bike4" + slogan "Encontre · Alugue · Pedale"
- `logo-sem-slogan.svg` — versão compacta para cabeçalho/navbar
- `icone-pin.svg`, `icone-512.png`, `favicon-64.png` — só o pin com a roda (favicon, app icon, avatar)

Regras:
- Use sempre os arquivos oficiais; não redesenhar nem recolorir o logo.
- Deixar espaço livre ao redor equivalente à largura do "4".
- Navbar: `logo-sem-slogan.svg` com altura ~40px. Favicon: `icone-pin.svg`.
- O logo é feito para fundo branco/claro.
