// App desktop: uma janela que abre o 2bike4 publicado. Links de fora
// (OpenStreetMap, créditos das fotos) vão para o navegador padrão.
const { app, BrowserWindow, shell } = require('electron');
const path = require('node:path');

const URL_APP = process.env.BIKE4_URL || 'https://twobike4-ym4q.onrender.com/';

function abrir() {
  const janela = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 380,
    title: '2bike4',
    // o .exe não é assinado nem editado (ver package.json), então o ícone vem daqui
    icon: path.join(__dirname, 'build', 'icon.png'),
    backgroundColor: '#ffffff',
    autoHideMenuBar: true,
    webPreferences: { contextIsolation: true, sandbox: true },
  });

  janela.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });
  janela.webContents.on('will-navigate', (evento, url) => {
    if (!url.startsWith(URL_APP)) {
      evento.preventDefault();
      shell.openExternal(url);
    }
  });

  janela.loadURL(URL_APP);
}

app.whenReady().then(abrir);
app.on('window-all-closed', () => app.quit());
