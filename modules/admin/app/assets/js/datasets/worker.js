addEventListener('message', ({data}) => {

  let abortController = new AbortController();

  function getDecoder(r) {
    let contentType = data.contentType || r.headers.get("Content-Type");
    let re = /charset=([^()<>@,;:"/[\]?.=\s]*)/i;
    let charset = re.test(contentType) ? re.exec(contentType)[1] : 'utf8';
    console.debug("Reading file with charset", charset);
    return new TextDecoder(charset.toLowerCase());
  }

  function readStream(r) {
    let init = true;
    let truncated = false;
    let read = 0;

    let decoder = getDecoder(r);
    let reader = r.body.getReader();

    reader.read().then(function appendBody({done, value}) {
      if (!done) {
        let text = decoder.decode(value);
        read += (value ? value.length : 0);
        if (data.max && read > data.max) {
          truncated = true;
          console.log("Aborting preview at", data.max, "bytes")
          abortController.abort();
        }
        postMessage({
          init: init,
          text: text,
          done: false,
          truncated: truncated,
          type: r.headers.get("content-type"),
        });

        if (init) {
          init = false;
        }
        reader.read().then(appendBody);
      } else {
        // Special case if there's an empty file, we post
        // an empty string
        postMessage({
          init: init,
          text: "",
          done: true,
          truncated: false,
        });
      }
    });
  }

  if (data.type === 'preview') {
    fetch(data.url, {
      signal: abortController.signal
    }).then(r => readStream(r));

  } else if (data.type === 'convert-preview') {
    fetch(data.url, {
      method: 'POST',
      headers: {
        "ajax-ignore-csrf": true,
        "Content-Type": "application/json",
        "Accept": "application/json; charset=utf-8",
        "X-Requested-With": "XMLHttpRequest",
      },
      body: JSON.stringify({mappings: data.mappings, force: false}),
      credentials: 'same-origin',
      signal: abortController.signal,
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
  }
}, false);
