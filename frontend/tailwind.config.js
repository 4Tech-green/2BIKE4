// As cores vêm de variáveis CSS (src/css/main.css): trocar a identidade
// visual é mudar os valores lá, sem tocar nas telas.
const cor = (nome) => `rgb(var(--cor-${nome}) / <alpha-value>)`;

module.exports = {
  content: {
    files: ['./src/**/*.cljs', './public/index.html', './public/design-kit.html'],
    // Hiccup escreve classes como :div.p-3.text-sm; separar também no ponto.
    extract: { cljs: (conteudo) => conteudo.split(/[\s"'.(){}#,]+/) },
  },
  theme: {
    extend: {
      colors: {
        primaria: cor('primaria'),
        'primaria-hover': cor('primaria-hover'),
        'primaria-contraste': cor('primaria-contraste'),
        destaque: cor('destaque'),
        'destaque-hover': cor('destaque-hover'),
        fundo: cor('fundo'),
        superficie: cor('superficie'),
        texto: cor('texto'),
        suave: cor('suave'),
        borda: cor('borda'),
        sucesso: cor('sucesso'),
        alerta: cor('alerta'),
        perigo: cor('perigo'),
        info: cor('info'),
      },
    },
  },
};
