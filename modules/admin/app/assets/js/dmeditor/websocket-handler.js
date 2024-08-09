addEventListener('message', ({data}) => {

  let url = data.url;
  let websocket = new WebSocket(url);
  websocket.onopen = function () {
    console.debug("Websocket open")
  };
  websocket.onerror = function (e) {
    postMessage({error: e});
  };
  websocket.onmessage = function (e) {
    let msg = JSON.parse(e.data);
    let done = msg.indexOf(data.DONE) !== -1 || msg.indexOf(data.ERR) !== -1;
    postMessage({msg: msg, done: done});
    if (done) {
      websocket.close();
    }
  };
  websocket.onclose = function () {
    console.debug("Websocket close")
  }
}, false);
