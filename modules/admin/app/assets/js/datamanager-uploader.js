"use strict";

function fileUploader(url, file, opts) {
  let noop = function() { return false };
  let onProgress = opts.progress || noop;
  let onStart = opts.begin || noop;
  let onDone = opts.done || noop;
  let onError = opts.error || noop;
  let isAborted = opts.aborted || noop;

  return new Promise((resolve, reject) => {
    let xhr = new XMLHttpRequest();
    xhr.overrideMimeType(file.type);

    xhr.upload.addEventListener("progress", evt => {
      onProgress(evt);
      if (isAborted()) {
        // the upload has been cancelled...
        xhr.abort();
      }
    });

    xhr.addEventListener("load", evt => {
      if (xhr.readyState === xhr.DONE && xhr.status === 200) {
        onDone(evt);
        resolve(xhr.responseXML);
      } else {
        onError(evt);
        reject(xhr.responseText);
      }
    });

    xhr.addEventListener("error", evt => {
      onError(evt);
      reject(xhr.responseText);
    });

    xhr.addEventListener("abort", evt => {
      resolve(xhr.responseText);
    });

    onStart(xhr);
    xhr.open("PUT", url);
    xhr.setRequestHeader("Content-Type", file.type);
    xhr.send(file);
  });
}

