addEventListener('message', ({data}) => {
  if (data.type === 'preview') {
    let init = true;
    fetch(data.url).then(r => {
      let reader = r.body.getReader();
      let decoder = new TextDecoder("UTF-8");
      reader.read().then(function appendBody({done, value}) {
        if (!done) {
          let text = decoder.decode(value);
          if (init) {
            postMessage({init: init, text: text});
            init = false;
          } else {
            postMessage({init: init, text: text});
          }
          reader.read().then(appendBody);
        } else {
          close();
        }
      });
    });
  }
});
