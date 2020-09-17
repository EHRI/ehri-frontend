addEventListener('message', ({data}) => {

  function readStream(r) {
    let init = true;
    let reader = r.body.getReader();
    let decoder = new TextDecoder("UTF-8");
    reader.read().then(function appendBody({done, value}) {
      if (!done) {
        let text = decoder.decode(value);
        postMessage({init: init, text: text, done: done, type: r.headers.get("content-type")});
        if (init) {
          init = false;
        }
        reader.read().then(appendBody);
      } else {
        // Special case if there's an empty file, we post
        // an empty string
        postMessage({init: init, text: "", done: done})
      }
    });
  }

  if (data.type === 'preview') {
    fetch(data.url).then(r => readStream(r));

  } else if (data.type === 'convert-preview') {
    fetch(data.url, {
      method: 'POST',
      body: JSON.stringify(data),
      headers: {
        "ajax-ignore-csrf": true,
        "Content-Type": "application/json",
        "Accept": "application/json; charset=utf-8",
        "X-Requested-With": "XMLHttpRequest",
      },
      credentials: 'same-origin',
    }).then(r => {
      if (r.status === 200) {
        readStream(r)
      } else {
        r.json().then(data => postMessage({error: data, done: true}));
      }
    });
  } else if (data.type === 'websocket') {
    let url = data.url;
    let websocket = new WebSocket(url);
    websocket.onopen = function() {
      console.log("Websocket open")
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
    websocket.onclose = function() {
      console.log("Websocket close")
    }
  }
}, false);
