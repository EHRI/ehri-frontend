"use strict";

// Prevent default drag/drop action...
window.addEventListener("dragover",function(e){
  e = e || event;
  e.preventDefault();
},false);
window.addEventListener("drop",function(e){
  e = e || event;
  e.preventDefault();
},false);

/**
 * A data access object containing functions to vocabulary concepts.
 */
let DAO = {
  ajaxHeaders: {
    "ajax-ignore-csrf": true,
    "Content-Type": "application/json",
    "Accept": "application/json; charset=utf-8"
  },

  /**
   *
   * @param obj an object of URL parameters
   * @returns {string}
   */
  objToQueryString: function (obj) {
    let str = [];
    for (var p in obj)
      if (obj.hasOwnProperty(p)) {
        if (Array.isArray(obj[p])) {
          obj[p].forEach(v => {
            str.push(encodeURIComponent(p) + "=" + encodeURIComponent(v));
          });
        } else {
          str.push(encodeURIComponent(p) + "=" + encodeURIComponent(obj[p]));
        }
      }
    return str.join("&");
  },

  listFiles: function (prefix) {
    return fetch(SERVICE.listFiles(CONFIG.repositoryId, prefix).url)
      .then(r => r.json());
  },

  ingestFiles: function (paths) {
    return SERVICE.ingestFiles(CONFIG.repositoryId).ajax({
      data: JSON.stringify(paths),
      headers: this.ajaxHeaders
    });
  },

  deleteFiles: function (paths) {
    return SERVICE.deleteFiles(CONFIG.repositoryId).ajax({
      data: JSON.stringify(paths),
      headers: this.ajaxHeaders
    });
  },

  uploadHandle: function(fileSpec) {
    return SERVICE.uploadHandle(CONFIG.repositoryId).ajax({
      data: JSON.stringify(fileSpec),
      headers: this.ajaxHeaders
    });
  }
};

var app = new Vue({
  el: '#data-manager',
  data: function () {
    return {
      loaded: false,
      truncated: false,
      files: [],
      deleting: [],
      dropping: false,
      uploading: [],
      cancelled: [],
      log: [],
      ingesting: false
    }
  },
  watch: {
  },
  methods: {
    // EJohn's pretty date:
    // Takes an ISO time and returns a string representing how
    // long ago the date represents.
    // https://johnresig.com/blog/javascript-pretty-date/
    prettyDate: function (time) {
      var date = new Date((time || "").replace(/-/g, "/").replace(/[TZ]/g, " ")),
        diff = (((new Date()).getTime() - date.getTime()) / 1000),
        day_diff = Math.floor(diff / 86400);

      if (isNaN(day_diff) || day_diff < 0 || day_diff >= 31)
        return;

      return day_diff === 0 && (
        diff < 60 && "just now" ||
        diff < 120 && "1 minute ago" ||
        diff < 3600 && Math.floor(diff / 60) + " minutes ago" ||
        diff < 7200 && "1 hour ago" ||
        diff < 86400 && Math.floor(diff / 3600) + " hours ago") ||
        day_diff === 1 && "Yesterday" ||
        day_diff < 7 && day_diff + " days ago" ||
        day_diff < 31 && Math.ceil(day_diff / 7) + " weeks ago";
    },

    refresh: function () {
      return DAO.listFiles("").then(data => {
        this.files = data.files;
        this.truncated = data.truncated;
        this.loaded = true;
      });
    },
    deleteFile: function(key) {
      this.deleting.push(key);
      DAO.deleteFiles([key]).then(deleted => {
        this.deleting = _.difference(this.deleting, deleted);
        this.refresh();
      })
    },
    finishUpload: function(fileSpec) {
      let i = _.findIndex(this.uploading, s => s.spec.name === fileSpec.name);
      this.uploading[i].progress = 100;
      setTimeout(_ => this.uploading.splice(i, 1), 1000);
    },
    setUploadProgress: function(fileSpec, percent) {
      let i = _.findIndex(this.uploading, s => s.spec.name === fileSpec.name);
      if (i > -1) {
        this.uploading[i].progress = Math.min(100, percent);
        return true;
      }
      return false;
    },
    isDeleting: function(key) {
      return this.deleting.includes(key);
    },
    dragOver: function(event) {
      this.dropping = true;
    },
    dragLeave: function(event) {
      this.dropping = false;
    },
    uploadFile: function(file) {

      // Check we're still in the queue and have not been cancelled...
      if (_.findIndex(this.uploading, f => f.spec.name === file.name) === -1) {
        return Promise.resolve();
      }

      let self = this;

      return DAO.uploadHandle({
        name: file.name,
        type: file.type,
        size: file.size
      }).then(data => {
        return new Promise((resolve, reject) => {
          let url = data.presignedUrl;
          self.setUploadProgress(file, 0);
          let xhr = new XMLHttpRequest();
          xhr.overrideMimeType(file.type);

          xhr.upload.addEventListener("progress", evt => {
            if (evt.lengthComputable) {
              if (!self.setUploadProgress(file, Math.round((evt.loaded / evt.total) * 100))) {
                // the upload has been cancelled...
                xhr.abort();
              }
            }
          });
          xhr.addEventListener("load", evt => {
            if (xhr.readyState === xhr.DONE && xhr.status === 200) {
              self.finishUpload(file);
              self.refresh().then(_ => {
                resolve(xhr.responseXML);
              });
              console.log(xhr.responseXML);
            } else {
              reject(xhr.responseText);
            }
          });
          xhr.addEventListener("error", evt => {
            reject(xhr.responseText);
          });
          xhr.addEventListener("abort", evt => {
            resolve(xhr.responseText);
          });

          xhr.open("PUT", url);
          xhr.setRequestHeader("Content-Type", file.type);
          xhr.send(file);
        });
      });
    },
    uploadFiles: function(event) {
      this.dragLeave(event);
      let self = this;

      let fileList = event.dataTransfer
        ? event.dataTransfer.files
        : event.target.files;

      function sequential(arr, index) {
        if (index >= arr.length) return Promise.resolve();
        return self.uploadFile(arr[index])
          .then(r => {
            return sequential(arr, index + 1)
          });
      }

      let files = [];
      for (let i = 0; i < fileList.length; i++) {
        let file = fileList[i];
        if (file.type === "text/xml") {
          this.uploading.push({
            spec: file,
            progress: 0,
          });
          files.push(file);
        }
      }

      // Files were dropped but there were no file ones
      if (files.length === 0 && fileList.length > 0) {
        return Promise.reject("No valid files found")
      }

      // Nothing is selected: no-op
      if (files.length === 0) {
        return Promise.resolve();
      }

      // Proceed with upload
      return sequential(files, 0)
        .then(_ => {
          if (event.target.files) {
            // Delete the value of the control, if loaded
            event.target.value = null;
          }

          console.log("Files uploaded...")
        });
    },
    ingestFiles: function(event) {
      let self = this;
      self.ingesting = true;
      DAO.ingestFiles(this.files.map(f => f.key))
        .then(data => {
          if (data.url && data.jobId) {
            var websocket = new WebSocket(data.url);
            websocket.onopen = function() {
              console.debug("Opened monitoring socket...")
            };
            websocket.onerror = function(e) {
              self.log.push("ERROR. Try refreshing the page. ");
              console.error("Socket error!", e);
            };
            websocket.onclose = function() {
              console.debug("Closed!");
            };
            websocket.onmessage = function(e) {
              var msg = JSON.parse(e.data);
              self.log.push(msg.trim());
              if (msg.indexOf(DONE_MSG) !== -1 || msg.indexOf(ERR_MSG) !== -1) {
                websocket.close();
                console.debug("Closed socket")
              }
              // FIXME
              let logElem = document.getElementById("update-progress");
              logElem.scrollTop = logElem.clientHeight;
            };

          } else {
            console.error("Unexpected job data", data);
          }
        });

    },
  },
  computed: {
  },
  created: function () {
    this.refresh();
  },
  template: `
    <div id="data-manager-container">
      <table class="table table-bordered table-striped table-sm" v-if="files.length > 0">
        <thead>
        <tr>
          <td>Name</td>
          <td>Last Modified</td>
          <td>Size</td>
          <td></td>
        </tr>
        </thead>
        <tbody>
        <tr v-for="file in files">
          <td>{{file.key}}</td>
          <td>{{prettyDate(file.lastModified)}}</td>
          <td>{{file.size}}</td>
          <td>
            <a href="#" v-on:click.prevent="deleteFile(file.key)">
              <i class="fa fa-fw" v-bind:class="{
                'fa-circle-o-notch fa-spin': isDeleting(file.key), 
                'fa-trash-o': !isDeleting(file.key) 
              }"></i>
            </a>
          </td>
        </tr>
        </tbody>
      </table>
      <div class="admin-help-notice" v-else-if="loaded">
        There are no files yet.
      </div>
      <div id="drop-target" v-if="!ingesting"
           v-bind:class="{dropping: dropping}"
      >
        <input id="file-selector" 
               v-on:change="uploadFiles"
               v-on:dragover.prevent="dragOver"
               v-on:dragleave.prevent="dragLeave"
               v-on:drop.prevent="uploadFiles"
               type="file" 
               accept="text/xml" multiple/>
        Click to select or drop files here...
      </div>
      <div id="update-progress" v-else>
            <pre v-if="log.length > 0"><template v-for="msg in log">{{msg}}<br/></template></pre>
      </div>
      <button v-on:click="ingestFiles">Ingest Files</button>
      <div id="upload-progress" v-if="uploading.length > 0">
        <div v-for="job in uploading" class="progress-container">
          <div class="progress">
            <div class="progress-bar progress-bar-striped progress-bar-animated" 
                 role="progressbar"
                 v-bind:aria-valuemax="100" 
                 v-bind:aria-valuemin="0" 
                 v-bind:aria-valuenow="job.progress"
                  v-bind:style="'width: ' + job.progress + '%'">
              {{ job.spec.name}}
            </div>
          </div>
          <button class="cancel-button" v-on:click.prevent="finishUpload(job.spec)">
              <i class="fa fa-fw fa-times-circle"/>
          </button>
        </div>
      </div>
    </div>
  `
});
