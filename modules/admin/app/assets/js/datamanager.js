"use strict";

// FIXME: make this dynamic?
const LOG_MESSAGE = "TEST TEST TEST";

// Prevent default drag/drop action...
window.addEventListener("dragover",function(e){
  e = e || event;
  e.preventDefault();
},false);
window.addEventListener("drop",function(e){
  e = e || event;
  e.preventDefault();
},false);

function sequential(func, arr, index) {
  if (index >= arr.length) return Promise.resolve();
  return func(arr[index])
    .then(r => {
      return sequential(func, arr, index + 1)
    });
}

/**
 * A data access object containing functions to vocabulary concepts.
 */
let DAO = {
  call: function(endpoint, data) {
    return axios.request({
      url: endpoint.url,
      method: endpoint.method,
      data: data,
      headers: {
        "ajax-ignore-csrf": true,
        "Content-Type": "application/json",
        "Accept": "application/json; charset=utf-8",
        "X-Requested-With": "XMLHttpRequest",
      },
      withCredentials: true,
    }).then(r => r.data);
  },

  listFiles: function (prefix, after) {
    return this.call(SERVICE.listFiles(CONFIG.repositoryId, prefix, after));
  },

  validateFiles: function (paths) {
    return this.call(SERVICE.validateFiles(CONFIG.repositoryId), paths);
  },

  ingestFiles: function (paths, logMessage) {
    return this.call(SERVICE.ingestFiles(CONFIG.repositoryId), {logMessage: logMessage, files: paths});
  },

  ingestAll: function (logMessage) {
    return this.call(SERVICE.ingestAll(CONFIG.repositoryId), {logMessage: logMessage, files: []});
  },

  deleteFiles: function (paths) {
    return this.call(SERVICE.deleteFiles(CONFIG.repositoryId), paths);
  },

  deleteAll: function() {
    return this.call(SERVICE.deleteAll(CONFIG.repositoryId)).then(data => data.ok || false);
  },

  fileUrls: function (paths) {
    return this.call(SERVICE.fileUrls(CONFIG.repositoryId), paths);
  },

  uploadHandle: function(fileSpec) {
    return this.call(SERVICE.uploadHandle(CONFIG.repositoryId), fileSpec);
  },

  uploadFile: function(url, file, progressHandler) {
    const CancelToken = axios.CancelToken;
    const source = CancelToken.source();

    return axios.put(url, file, {
      onUploadProgress: function(evt) {
        if (!progressHandler(evt)) {
          source.cancel();
        }
      },
      headers: {'Content-type': file.type,},
      cancelToken: source.token,
    }).then(r => r.status === 200)
      .catch(function(e) {
      if (axios.isCancel(e)) {
        console.log('Request canceled', file.name);
        return false;
      } else {
        throw e;
      }
    });
  }
};

Vue.component("preview", {
  props: {
    previewing: String,
  },
  data: function() {
    return {
      previewData: null,
      previewTruncated: false,
    }
  },
  methods: {
   load: function() {
     let self = this;
     DAO.fileUrls([self.previewing]).then(data => {
       fetch(data[self.previewing]).then(r => {
         let reader = r.body.getReader();
         let self = this;
         let decoder = new TextDecoder("UTF-8");
         reader.read().then(function appendBody({done, value}) {
           if (!done) {
             if (self.previewData !== null && self.previewData.length > 10) {
               self.previewTruncated = true;
               reader.cancel();
             } else {
               let text = decoder.decode(value);
               if (self.previewData === null) {
                 self.previewData = text;
               } else {
                 self.previewData += text;
               }
               reader.read().then(appendBody);
             }
           }
         });
       });
     });
   },
   closePreview: function() {
     this.previewData = null;
     this.previewTruncated = false;
     this.$emit("close-preview");
   }
  },
  created: function() {
    this.load();
  },
  template: `
    <div class="modal show" role="dialog" style="display: block">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h3 class="modal-title">{{previewing}}</h3>
            <button type="button" class="close" data-dismiss="modal" aria-label="Close"
                    v-on:click="closePreview">
              <span aria-hidden="true">&times;</span>
            </button>
          </div>
          <div class="modal-body" id="preview-data" v-bind:class="{'loading':previewData === null}">
                  <pre>{{previewData}}
                    <template v-if="previewTruncated"><br/>... [truncated] ...</template></pre>
          </div>
        </div>
      </div>
    </div>
  `
});

Vue.component("upload-progress", {
  props: {
    uploading: Array,
  },
  template: `
    <div id="upload-progress" v-if="uploading.length > 0">
      <div v-for="job in uploading" v-bind:key="job.spec.name" class="progress-container">
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
        <button class="cancel-button" v-on:click.prevent="$emit('finish-item', job.spec)">
          <i class="fa fa-fw fa-times-circle"/>
        </button>
      </div>
    </div>
  `
});

Vue.component("files-table", {
  props: {
    loaded: Boolean,
    files: Array,
    truncated: Boolean,
    deleting: Object,
    ingesting: Object,
    validating: Object,
    validationLog: Object,
  },
  data: function() {
    return {
      loadingMore: false,
    }
  },
  methods: {
    fetchMore: function() {
      this.loadingMore = true;
      let from = this.files.length > 0
        ? this.files[this.files.length - 1].key : null;
      return DAO.listFiles("", from).then(data => {
        this.files.push.apply(this.files, data.files);
        this.$emit("files-loaded", data.truncated);
        this.loadingMore = false;
      });
    },

    // Bytes-to-human readable string from:
    // https://stackoverflow.com/a/14919494/285374
    humanFileSize: function(bytes, si) {
      var thresh = si ? 1000 : 1024;
      if (Math.abs(bytes) < thresh) {
        return bytes + ' B';
      }
      var units = si
        ? ['kB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB']
        : ['KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];
      var u = -1;
      do {
        bytes /= thresh;
        ++u;
      } while (Math.abs(bytes) >= thresh && u < units.length - 1);
      return bytes.toFixed(1) + ' ' + units[u];
    },

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

    isValid: function(key) {
      let err = this.validationLog[key];
      if (_.isArray(err)) {
        return err.length === 0;
      }
      return null;
    },
  },
  template: `
    <div id="file-list-container" v-bind:class="{loading:'loading'}">
      <table class="table table-bordered table-striped table-sm" v-if="files.length > 0">
        <thead>
        <tr>
          <td>Name</td>
          <td>Last Modified</td>
          <td>Size</td>
          <td colspan="4"></td>
        </tr>
        </thead>
        <tbody>
        <tr v-for="file in files" v-bind:key="file.key">
          <td>{{file.key}}</td>
          <td>{{prettyDate(file.lastModified)}}</td>
          <td>{{humanFileSize(file.size, true)}}</td>
          <td>
            <a href="#" v-on:click.prevent="$emit('delete-file', file.key)">
              <i class="fa fa-fw" v-bind:class="{
                'fa-circle-o-notch fa-spin': deleting[file.key], 
                'fa-trash-o': !deleting[file.key] 
              }"></i>
            </a>
          </td>
          <td>
            <a href="#" v-bind:disabled="validating[file.key]" v-on:click.prevent="$emit('validate-file', file.key)">
              <i class="fa fa-fw fa-spin fa-circle-o-notch" v-if="validating[file.key]"/>
              <i class="fa fa-fw fa-check-circle-o" v-else-if="isValid(file.key)"/>
              <i class="fa fa-fw fa-exclamation-triangle" v-else-if="isValid(file.key) === false"/>
              <i class="fa fa-fw fa-question-circle-o" v-else/>
            </a>
          </td>
          <td><a href="#" v-on:click.prevent="$emit('show-preview', file.key)"><i class="fa fa-eye"></i></a></td>
          <td><a href="#" v-on:click.prevent="$emit('ingest-files', [file.key])">
            <i class="fa fa-fw" v-bind:class="{
              'fa-database': !ingesting[file.key], 
              'fa-circle-o-notch fa-spin': ingesting[file.key]
            }"></i></a>
          </td>
        </tr>
        </tbody>
      </table>
      <button class="btn btn-sm btn-default" v-if="truncated" v-on:click.prevent="fetchMore">
        Load more 
        <i v-if="loadingMore" class="fa fa-fw fa-cog fa-spin" />
        <i v-else class="fa fa-fw fa-caret-down" />
      </button>
      <div id="list-placeholder" v-else-if="loaded && files.length === 0">
        There are no files yet.
      </div>
      <div id="file-list-loading-indicator" v-show="!loaded">
        <i class="fa fa-3x fa-spinner fa-spin"></i>
      </div>
    </div>
  `
});

Vue.component("validation-error-messages", {
  props: {
    messages: Array,
  },
  template: `<pre><template v-for="msg in messages">Line {{msg.line}}<template v-if="msg.pos">, {{msg.pos}}</template>: {{msg.error}}</template></pre>`
});

let app = new Vue({
  el: '#data-manager',
  data: function () {
    return {
      loaded: false,
      truncated: false,
      files: [],
      deleting: {},
      selected: [],
      dropping: false,
      uploading: [],
      cancelled: [],
      log: [],
      ingesting: {},
      validating: {},
      validationLog: {},
      lastValidated: null,
      tab: 'upload',
      previewing: null,
    }
  },
  watch: {
  },
  methods: {
    refresh: function () {
      return DAO.listFiles("").then(data => {
        this.files = data.files;
        this.truncated = data.truncated;
        this.loaded = true;
      });
    },
    filesLoaded: function(truncated) {
      this.truncated = truncated;
    },
    deleteFile: function(key) {
      this.$set(this.deleting, key, true);
      DAO.deleteFiles([key]).then(deleted => {
        deleted.forEach(key => this.$delete(this.deleting, key));
        this.refresh();
      })
    },
    deleteAll: function() {
      this.files.forEach(f => this.$set(this.deleting, f.key, true));
      return DAO.deleteAll().then(r => {
        this.refresh();
        this.deleting = {};
        r;
      });
    },
    finishUpload: function(fileSpec) {
      this.setUploadProgress(fileSpec, 100);
      setTimeout(() => {
        let i = _.findIndex(this.uploading, s => s.spec.name === fileSpec.name);
        this.uploading.splice(i, 1)
      }, 1000);
    },
    setUploadProgress: function(fileSpec, percent) {
      let i = _.findIndex(this.uploading, s => s.spec.name === fileSpec.name);
      if (i > -1) {
        this.uploading[i].progress = Math.min(100, percent);
        return true;
      }
      return false;
    },
    showPreview: function(key) {
      this.previewing = key;
    },
    closePreview: function() {
      this.previewing = null;
      this.previewData = null;
      this.previewTruncated = false;
    },
    validateFile: function(key) {
      this.$set(this.validating, key, true);
      DAO.validateFiles([key]).then(e => {
        let errors = e[key];
        this.$set(this.validationLog, key, errors);
        this.$delete(this.validating, key);
        this.lastValidated = key;
        this.tab = 'validation';
      });
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
        self.setUploadProgress(file, 0);
        return DAO.uploadFile(data.presignedUrl, file, function(evt) {
          return evt.lengthComputable
            ? self.setUploadProgress(file, Math.round((evt.loaded / evt.total) * 100))
            : true;
        }).then(r => {
          self.finishUpload(file);
          return self.refresh();
        });
      });
    },
    uploadFiles: function(event) {
      this.dragLeave(event);
      let self = this;

      let fileList = event.dataTransfer
        ? event.dataTransfer.files
        : event.target.files;

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
      return sequential(self.uploadFile, files, 0)
        .then(_ => {
          if (event.target.files) {
            // Delete the value of the control, if loaded
            event.target.value = null;
          }

          console.log("Files uploaded...")
        });
    },
    monitorIngest: function(url, keys) {
      let self = this;
      let websocket = new WebSocket(url);
      websocket.onerror = function(e) {
        self.log.push("ERROR. Try refreshing the page. ");
        console.error("Socket error!", e);
        keys.forEach(key => self.$delete(self.ingesting, key));
      };
      websocket.onmessage = function(e) {
        var msg = JSON.parse(e.data);
        self.log.push(msg.trim());
        if (msg.indexOf(DONE_MSG) !== -1 || msg.indexOf(ERR_MSG) !== -1) {
          keys.forEach(key => self.$delete(self.ingesting, key));
          websocket.close();
        }
        // FIXME
        let logElem = document.getElementById("tab-ingest-log");
        if (logElem) {
          logElem.scrollTop = logElem.clientHeight;
        }
      };
    },
    ingestFiles: function(keys) {
      let self = this;

      // Switch to ingest tab...
      this.tab = "ingest";

      // Clear existing log...
      self.log.length = 0;

      // Set key status to ingesting.
      keys.forEach(key => this.$set(this.ingesting, key, true));

      DAO.ingestFiles(keys, LOG_MESSAGE)
        .then(data => {
          if (data.url && data.jobId) {
            self.monitorIngest(data.url, keys);
          } else {
            console.error("unexpected job data", data);
          }
        });
    },
    ingestAll: function() {
      let self = this;

      // Switch to ingest tab...
      this.tab = "ingest";

      // Clear existing log...
      self.log.length = 0;

      let keys = self.files.map(f => f.key);

      // Set key status to ingesting.
      keys.forEach(key => this.$set(this.ingesting, key, true));

      DAO.ingestAll(LOG_MESSAGE).then(data => {
        if (data.url && data.jobId) {
          self.monitorIngest(data.url, keys);
        } else {
          console.error("unexpected job data", data);
        }
      });
    },
  },
  created: function () {
    this.refresh();
  },
  template: `
    <div id="data-manager-container">
      <div id="actions-menu" class="downdown">
        <a href="#" id="actions-menu-toggle" class="btn btn-default dropdown-toggle" role="button" 
            data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
          Actions
        </a>
        <div class="dropdown-menu" aria-labelledby="actions-menu-toggle">
          <a href="#" class="dropdown-item" v-on:click.prevent="deleteAll()">
            Delete All
          </a>
          <a href="#" class="dropdown-item" v-on:click.prevent="ingestAll()">
            Ingest All
          </a>
        </div>
      </div>
      
      <files-table
        v-bind:loaded="loaded"
        v-bind:files="files"
        v-bind:truncated="truncated"
        v-bind:validating="validating"
        v-bind:validationLog="validationLog"
        v-bind:deleting="deleting"
        v-bind:ingesting="ingesting"
        
        v-on:delete-file="deleteFile"
        v-on:ingest-files="ingestFiles"
        v-on:validate-file="validateFile"
        v-on:files-loaded="filesLoaded"
        v-on:show-preview="showPreview"
       /> 
      
      <div id="status">
        <ul id="status-panel-tabs" class="nav nav-tabs">
          <li class="nav-item">
            <a href="#tab-file-upload" class="nav-link" v-bind:class="{'active': tab === 'upload'}"
               v-on:click.prevent="tab = 'upload'">
              Upload Files
            </a>
          </li>
          <li class="nav-item">
            <a href="#tab-validation-errors" class="nav-link" v-bind:class="{'active': tab === 'validation'}"
               v-on:click.prevent="tab = 'validation'">
              Validation
            </a>
          </li>
          <li class="nav-item">
            <a href="#tab-ingest-log" class="nav-link" v-bind:class="{'active': tab === 'ingest'}"
               v-on:click.prevent="tab = 'ingest'">
              Ingest
            </a>
          </li>
        </ul>

        <div id="status-panels">
          <div class="status-panel" id="tab-validation-errors" v-show="tab === 'validation'">
            <validation-error-messages v-if="lastValidated && validationLog[lastValidated]"
                v-bind:messages="validationLog[lastValidated]" />
          </div>
          <div class="status-panel" id="tab-ingest-log" v-show="tab === 'ingest'">
            <pre v-if="log.length > 0"><template v-for="msg in log">{{msg}}<br/></template></pre>
          </div>
          <div class="status-panel" id="tab-file-upload" v-show="tab === 'upload'" v-bind:class="{dropping: dropping}">
            <input id="file-selector"
                   v-on:change="uploadFiles"
                   v-on:dragover.prevent="dragOver"
                   v-on:dragleave.prevent="dragLeave"
                   v-on:drop.prevent="uploadFiles"
                   type="file"
                   accept="text/xml" multiple/>
            Click to select or drop files here...
          </div>

        </div>
      </div>

      <preview 
        v-if="previewing" 
        v-bind:previewing="previewing" 
        v-on:close-preview="previewing = null" />
      
      <upload-progress 
        v-bind:uploading="uploading" 
        v-on:finish-item="finishUpload" />
    </div>
  `
});
