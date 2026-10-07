const { app, BrowserWindow, Menu, shell } = require('electron')
const path = require('node:path')

const isDev = !app.isPackaged
let windowNumber = 0

function profileFromArgs() {
  const value = process.argv.find(arg => arg.startsWith('--profile='))
  return value ? value.slice('--profile='.length).replace(/[^a-zA-Z0-9_-]/g, '_') : null
}

function createWindow(profile) {
  windowNumber += 1
  const profileId = profile || `window-${windowNumber}`
  const window = new BrowserWindow({
    width: 1440,
    height: 920,
    minWidth: 980,
    minHeight: 640,
    title: `EasyChat · ${profileId}`,
    backgroundColor: '#f3f6f5',
    autoHideMenuBar: false,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      partition: `persist:easychat-${profileId}`
    }
  })

  if (isDev) {
    window.loadURL('http://localhost:5173')
  } else {
    window.loadFile(path.join(__dirname, '..', 'dist', 'index.html'))
  }

  window.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('http:') || url.startsWith('https:')) shell.openExternal(url)
    return { action: 'deny' }
  })
  return window
}

function setupMenu() {
  const template = [
    {
      label: 'EasyChat',
      submenu: [
        { label: '新建独立窗口', accelerator: 'CmdOrCtrl+Shift+N', click: () => createWindow() },
        { type: 'separator' },
        { role: 'quit', label: '退出' }
      ]
    },
    { role: 'editMenu', label: '编辑' },
    { role: 'viewMenu', label: '查看' },
    { role: 'windowMenu', label: '窗口' }
  ]
  Menu.setApplicationMenu(Menu.buildFromTemplate(template))
}

app.whenReady().then(() => {
  setupMenu()
  createWindow(profileFromArgs())
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow() })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
