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

  // Post an error for a failed response. JSON error bodies (e.g. from
  // the conversion API) are passed through as-is, but other responses,
  // such as HTML error pages or auth failures, must not be shown as data.
  function postResponseError(r) {
    return r.text().then(body => {
      try {
        let json = JSON.parse(body);
        if (json && json.error) {
          return postMessage({error: json, done: true});
        }
      } catch (e) {
        // not JSON...
      }
      let msg = r.status === 401
          ? "not logged in, or session has expired"
          : r.status === 403
              ? "permission denied"
              : `request failed (${r.status} ${r.statusText})`;
      postMessage({error: {error: msg}, done: true});
    });
  }

  function postFetchError(e) {
    if (e.name !== 'AbortError') {
      postMessage({error: {error: e.message}, done: true});
    }
  }

  if (data.type === 'preview') {
    // Requests to the app (as opposed to storage URLs) are marked as AJAX so
    // that auth failures give a 401 rather than redirecting to the login page.
    // This header is not sent to storage, since it would need a CORS preflight.
    fetch(data.url, {
      headers: data.ajax ? {"X-Requested-With": "XMLHttpRequest"} : {},
      signal: abortController.signal
    }).then(r => r.ok ? readStream(r) : postResponseError(r))
      .catch(postFetchError);

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
    }).then(r => r.ok ? readStream(r) : postResponseError(r))
      .catch(postFetchError);
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
