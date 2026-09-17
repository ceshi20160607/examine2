import fs from 'node:fs/promises'
export async function connect() {
  const tabs = await (await fetch('http://127.0.0.1:9339/json/list')).json()
  const socket = new WebSocket(tabs.find(t => t.type === 'page').webSocketDebuggerUrl)
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject })
  let sequence = 0
  const pending = new Map()
  socket.onmessage = e => {
    const response = JSON.parse(e.data)
    const entry = pending.get(response.id)
    if (!entry) return
    clearTimeout(entry.timer); pending.delete(response.id)
    response.error ? entry.reject(new Error(response.error.message)) : entry.resolve(response.result)
  }
  const command = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++sequence
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)) }, 20000)
    pending.set(id, { resolve, reject, timer }); socket.send(JSON.stringify({ id, method, params }))
  })
  const evaluate = async expression => {
    const result = await command('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.text + ': ' + (result.result.description || ''))
    return result.result.value
  }
  const wait = async expression => {
    for (let i = 0; i < 80; i++) {
      if (await evaluate(expression)) return
      await new Promise(r => setTimeout(r, 150))
    }
    throw new Error(`UI state timeout: ${expression}`)
  }
  const click = async expression => {
    await new Promise(r => setTimeout(r, 250))
    const point = await evaluate(`(()=>{const el=${expression};if(!el)throw Error('Visible control missing');el.scrollIntoView({block:'center',behavior:'instant'});const r=el.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()`)
    await command('Input.dispatchMouseEvent', { type: 'mouseMoved', ...point })
    await command('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, ...point })
    await command('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, ...point })
  }
  const button = text => click(`[...document.querySelectorAll('button')].find(el=>el.getClientRects().length && el.textContent.replace(/\\s/g,'')===${JSON.stringify(text.replace(/\s/g, ''))})`)
  const fill = async (expression, text) => {
    await click(expression)
    await command('Input.dispatchKeyEvent',{type:'keyDown',key:'a',code:'KeyA',modifiers:2})
    await command('Input.dispatchKeyEvent',{type:'keyUp',key:'a',code:'KeyA',modifiers:2})
    await command('Input.insertText',{text})
    await command('Input.dispatchKeyEvent',{type:'keyDown',key:'Tab',code:'Tab'})
    await command('Input.dispatchKeyEvent',{type:'keyUp',key:'Tab',code:'Tab'})
  }
  const screenshot = async path => {
    await new Promise(r => setTimeout(r, 350))
    const result = await command('Page.captureScreenshot', { format: 'png' })
    await fs.writeFile(path, Buffer.from(result.data, 'base64'))
  }
  await command('Page.enable'); await command('Runtime.enable')
  await command('Emulation.setDeviceMetricsOverride', { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false })
  return { command, evaluate, wait, click, button, fill, screenshot, close: () => socket.close() }
}
