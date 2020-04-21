"use strict";

// FIXME: make this dynamic?
const LOG_MESSAGE = "Testing Ingest";

// Prevent default drag/drop action...
window.addEventListener("dragover", function (e) {
  e = e || event;
  e.preventDefault();
}, false);
window.addEventListener("drop", function (e) {
  e = e || event;
  e.preventDefault();
}, false);

function sequential(func, arr, index) {
  if (index >= arr.length) return Promise.resolve();
  return func(arr[index])
    .then(r => {
      return sequential(func, arr, index + 1)
    });
}

// Bytes-to-human readable string from:
// https://stackoverflow.com/a/14919494/285374
Vue.filter("humanFileSize", function(bytes, si) {
  let f = (bytes, si) => {
    let thresh = si ? 1000 : 1024;
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
  };
  return _.memoize(f)(bytes, si);
});

Vue.filter("prettyDate", function(time) {
  let f = time => {
    let m = moment(time);
    return m.isValid() ? m.fromNow() : "";
  };
  return _.memoize(f)(time);
});

/**
 * A data access object containing functions to vocabulary concepts.
 */
let DAO = {
  call: function (endpoint, data) {
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

  ingestFiles: function (paths, tolerant, commit, logMessage) {
    let data = {
      logMessage: logMessage,
      tolerant: tolerant,
      commit: commit,
      files: paths
    };
    return this.call(SERVICE.ingestFiles(CONFIG.repositoryId), data);
  },

  ingestAll: function (tolerant, commit, logMessage) {
    return this.call(SERVICE.ingestAll(CONFIG.repositoryId), {
      logMessage: logMessage,
      tolerant: tolerant,
      commit: commit,
      files: []
    });
  },

  deleteFiles: function (paths) {
    return this.call(SERVICE.deleteFiles(CONFIG.repositoryId), paths);
  },

  deleteAll: function () {
    return this.call(SERVICE.deleteAll(CONFIG.repositoryId)).then(data => data.ok || false);
  },

  validateFiles: function (paths) {
    return this.call(SERVICE.validateFiles(CONFIG.repositoryId), paths);
  },

  fileUrls: function (paths) {
    return this.call(SERVICE.fileUrls(CONFIG.repositoryId), paths);
  },

  uploadHandle: function (fileSpec) {
    return this.call(SERVICE.uploadHandle(CONFIG.repositoryId), fileSpec);
  },

  uploadFile: function (url, file, progressHandler) {
    const CancelToken = axios.CancelToken;
    const source = CancelToken.source();

    return axios.put(url, file, {
      onUploadProgress: function (evt) {
        if (!progressHandler(evt)) {
          source.cancel();
        }
      },
      headers: {'Content-type': file.type,},
      cancelToken: source.token,
    }).then(r => r.status === 200)
      .catch(function (e) {
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
    panelSize: Number,
    previewing: String,
  },
  data: function () {
    return {
      loading: false,
      validating: false,
      previewData: null,
      previewTruncated: false,
      percentDone: 0,
      errors: null,
    }
  },
  methods: {
    validate: function() {
      let self = this;
      if (self.previewing === null) {
        return;
      }

      self.validating = true;
      DAO.validateFiles([self.previewing]).then(errs => {
        this.errors = errs[self.previewing];
        this.updateErrors();
        this.validating = false;
      });
    } ,
    updateErrors: function() {
      if (this.errors && this.editor) {
        let doc = this.editor.getDoc();

        function makeMarker(err) {
          let marker = document.createElement("div");
          marker.style.color = "#822";
          marker.style.marginLeft = "3px";
          marker.className = "validation-error";
          marker.innerHTML = '<i class="fa fa-exclamation-circle"></i>';
          marker.querySelector("i").setAttribute("title", err.error);
          marker.addEventListener("click", function() {
            if (marker.widget) {
              marker.widget.clear();
              delete marker.widget;
            } else {
              marker.widget = doc.addLineWidget(err.line - 1, makeWidget(err));
            }
          });
          return marker;
        }

        function makeWidget(err) {
          let widget = document.createElement("div");
          widget.style.color = "#822";
          widget.style.backgroundColor = "rgba(255,197,199,0.44)";
          widget.innerHTML = err.error;
          return widget;
        }

        this.errors.forEach (e => {
          doc.addLineClass(e.line - 1, 'background', 'line-error');
          doc.setGutterMarker(e.line - 1, 'validation-errors', makeMarker(e));
        });
      }

    },
    load: function () {
      let self = this;
      if (self.previewing === null) {
        return;
      }

      self.loading = true;
      self.errors = null;
      DAO.fileUrls([self.previewing]).then(data => {
        let init = true;

        fetch(data[self.previewing]).then(r => {
          let reader = r.body.getReader();
          let decoder = new TextDecoder("UTF-8");
          reader.read().then(function appendBody({done, value}) {
            if (!done) {
              let text = decoder.decode(value);
              if (init) {
                if (self.editor) {
                  self.validate();
                  self.editor.scrollTo(0, 0);
                }
                self.previewData = text;
                init = false;
                self.loading = false;
              } else {
                self.previewData += text;
              }
              reader.read().then(appendBody);
            }
          });
        });
      });
    },
  },
  watch: {
    previewData: function (newValue, oldValue) {
      let editorValue = this.editor.getValue();
      if (newValue !== editorValue) {
        var scrollInfo = this.editor.getScrollInfo();
        this.editor.setValue(newValue);
        this.editor.scrollTo(scrollInfo.left, scrollInfo.top);
      }
    },
    previewing: function(newValue, oldValue) {
      if (newValue !== null && newValue !== oldValue) {
        this.load();
      }
    },
    panelSize: function(newValue, oldValue) {
      if (newValue !== null && newValue !== oldValue) {
        this.editor.refresh();
      }
    }
  },
  mounted: function () {
    var self = this;
    this.editor = CodeMirror.fromTextArea(this.$el.querySelector("textarea"), {
      mode: 'xml',
      lineNumbers: true,
      readOnly: true,
      gutters: [{className: "validation-errors", style: "width: 18px"}]
    });
    this.editor.on("refresh", evt => {
      this.updateErrors();
    });

    this.load();
  },
  beforeDestroy: function () {
    if (this.editor) {
      this.editor.toTextArea();
    }
  },
  template: `
    <div id="preview-container">
      <textarea>{{previewData}}</textarea>
      <div id="validation-loading-indicator" v-if="validating">
        <i class="fa fa-circle"></i>
      </div>
      <div id="valid-indicator" title="No errors detected" 
           v-if="!validating && errors !== null && errors.length === 0">
        <i class="fa fa-check"></i>
      </div>
      <div id="preview-loading-indicator" v-if="loading">
        <i class="fa fa-3x fa-spinner fa-spin"></i>
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
    dropping: Boolean,
    loaded: Boolean,
    previewing: String,
    files: Array,
    selected: Object,
    truncated: Boolean,
    deleting: Object,
    ingesting: Object,
    filter: String,
  },
  data: function () {
    return {
      loadingMore: false,
    }
  },
  methods: {
    fetchMore: function () {
      this.loadingMore = true;
      let from = this.files.length > 0
        ? this.files[this.files.length - 1].key : null;
      return DAO.listFiles("", from).then(data => {
        this.files.push.apply(this.files, data.files);
        this.$emit("files-loaded", data.truncated);
        this.loadingMore = false;
      });
    },

    toggleAll: function(evt) {
      for (let i = 0; i < this.files.length; i++) {
        this.toggleItem(this.files[i].key, evt);
      }
    },
    toggleItem: function(key, evt) {
      if (evt.target.checked) {
        this.$set(this.selected, key, true);
      } else {
        this.$delete(this.selected, key);
      }
    }
  },
  watch: {
    selected: function(newValue, oldValue) {
      let selected = Object.keys(newValue).length;
      this.$el.querySelector("#checkall").indeterminate =
        selected > 0 && selected !== this.files.length;
    },
  },
  computed: {
    allChecked: function() {
      return Object.keys(this.selected).length === this.files.length;
    }
  },
  template: `
    <div id="file-list-container" v-bind:class="{'loading': !loaded, 'dropping': dropping}">
      <table class="table table-bordered table-striped table-sm" v-if="files.length > 0">
        <thead>
        <tr>
          <th><input type="checkbox" id="checkall" v-on:change="toggleAll"/></th>
          <th>Name</th>
          <th>Last Modified</th>
          <th>Size</th>
          <th colspan="3"></th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="(file, idx) in files" 
            v-bind:key="file.key" 
            v-on:click="$emit('show-preview', file.key)" 
            v-bind:class="{'active': previewing === file.key}">
          <td><input type="checkbox" v-bind:checked="selected[file.key]" v-on:click.stop="toggleItem(file.key, $event)"></td>
          <td>{{file.key}}</td>
          <td v-bind:title="file.lastModified">{{file.lastModified | prettyDate}}</td>
          <td>{{file.size | humanFileSize(true)}}</td>
          <td><a href="#" v-on:click.prevent.stop="$emit('ingest-files', [file.key])">
            <i class="fa fa-fw" v-bind:class="{
              'fa-database': !ingesting[file.key], 
              'fa-circle-o-notch fa-spin': ingesting[file.key]
            }"></i></a>
          </td>
          <td>
            <a href="#" v-on:click.prevent.stop="$emit('delete-files', [file.key])">
              <i class="fa fa-fw" v-bind:class="{
                'fa-circle-o-notch fa-spin': deleting[file.key], 
                'fa-trash-o': !deleting[file.key] 
              }"></i>
            </a>
          </td>
        </tr>
        </tbody>
      </table>
      <button class="btn btn-sm btn-default" v-if="truncated" v-on:click.prevent.stop="fetchMore">
        Load more
        <i v-if="loadingMore" class="fa fa-fw fa-cog fa-spin"/>
        <i v-else class="fa fa-fw fa-caret-down"/>
      </button>
      <div id="filter-placeholder" class="panel-placeholder" v-else-if="loaded && filter && files.length === 0">
        No files found starting with &quot;<code>{{filter}}</code>&quot;...
      </div>
      <div id="list-placeholder" class="panel-placeholder" v-else-if="loaded && files.length === 0">
        There are no files here yet.
      </div>
      <div id="file-list-loading-indicator" v-show="!loaded">
        <i class="fa fa-3x fa-spinner fa-spin"></i>
      </div>
    </div>
  `
});

Vue.component("log-window", {
  props: {
    log: Array,
  },
  updated: function() {
      this.$el.scrollTop = this.$el.clientHeight + 1000;
  },
  template: `
    <pre v-if="log.length > 0"><template v-for="msg in log">{{msg}}<br/></template></pre>
  `
});

Vue.component("drag-handle", {
  props: {
    p1: Element,
    container: Element,
  },

  methods: {
    move: function(evt) {
      let max = this.container.clientHeight - 100;
      let basis = Math.min(Math.max(0, evt.clientY - this.p1.offsetTop), max);
      this.p1.style.flexBasis = basis + "px";
    },
    startDrag: function(evt) {
      console.log("Bind resize", new Date());
      let us = app.$el.style.userSelect;
      this.container.addEventListener("mousemove", this.move);
      this.container.style.userSelect = "none";
      window.addEventListener("mouseup", e => {
        console.log("Stop resize");
        this.$emit("resize", this.p1.clientHeight);
        this.container.style.userSelect = us;
        this.container.removeEventListener("mousemove", this.move);
      }, {once: true});
    },
  },
  template: `
    <div id="drag-handle" v-on:mousedown="startDrag"></div>
  `
});

let app = new Vue({
  el: '#data-manager',
  data: function () {
    return {
      loaded: false,
      truncated: false,
      filter: "",
      filtering: false,
      files: [],
      deleting: {},
      selected: {},
      dropping: false,
      uploading: [],
      cancelled: [],
      log: [],
      ingesting: {},
      tab: 'preview',
      previewing: null,
      panelSize: null,
      showOptions: false,
      optCommit: false,
      optTolerant: false,
      optLogMsg: LOG_MESSAGE
    }
  },
  methods: {
    clearFilter: function() {
      this.filter = "";
      return this.refresh();
    },
    filterFiles: function() {
      let func = () => {
        this.filtering = true;
        return this.refresh().then(r => {
          this.filtering = false;
          return r;
        });
      };
      _.debounce(func, 300)();
    },
    refresh: function () {
      return DAO.listFiles(this.filter).then(data => {
        this.files = data.files;
        this.truncated = data.truncated;
        this.loaded = true;
      });
    },
    filesLoaded: function (truncated) {
      this.truncated = truncated;
    },
    deleteFiles: function (keys) {
      if (keys.includes(this.previewing)) {
        this.previewing = null;
      }
      keys.forEach(key => this.$set(this.deleting, key, true));
      DAO.deleteFiles(keys).then(deleted => {
        deleted.forEach(key => {
          this.$delete(this.deleting, key);
          this.$delete(this.selected, key);
        });
        this.refresh();
      })
    },
    deleteAll: function () {
      this.previewing = null;
      this.files.forEach(f => this.$set(this.deleting, f.key, true));
      return DAO.deleteAll().then(r => {
        this.refresh();
        this.deleting = {};
        r;
      });
    },
    finishUpload: function (fileSpec) {
      this.setUploadProgress(fileSpec, 100);
      setTimeout(() => {
        let i = _.findIndex(this.uploading, s => s.spec.name === fileSpec.name);
        this.uploading.splice(i, 1)
      }, 1000);
    },
    setUploadProgress: function (fileSpec, percent) {
      let i = _.findIndex(this.uploading, s => s.spec.name === fileSpec.name);
      if (i > -1) {
        this.uploading[i].progress = Math.min(100, percent);
        return true;
      }
      return false;
    },
    showPreview: function (key) {
      this.previewing = key;
      this.tab = 'preview';
    },
    closePreview: function () {
      this.previewing = null;
      this.previewData = null;
      this.previewTruncated = false;
    },
    dragOver: function (event) {
      this.dropping = true;
    },
    dragLeave: function (event) {
      this.dropping = false;
    },
    uploadFile: function (file) {
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
        return DAO.uploadFile(data.presignedUrl, file, function (evt) {
          return evt.lengthComputable
            ? self.setUploadProgress(file, Math.round((evt.loaded / evt.total) * 100))
            : true;
        }).then(r => {
          self.finishUpload(file);
          return self.refresh();
        });
      });
    },
    uploadFiles: function (event) {
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
    monitorIngest: function (url, keys) {
      let self = this;
      let websocket = new WebSocket(url);
      websocket.onerror = function (e) {
        self.log.push("ERROR: " + e);
        console.error("Socket error!", e);
        keys.forEach(key => self.$delete(self.ingesting, key));
      };
      websocket.onmessage = function (e) {
        var msg = JSON.parse(e.data);
        self.log.push(msg.trim());
        if (msg.indexOf(DONE_MSG) !== -1 || msg.indexOf(ERR_MSG) !== -1) {
          keys.forEach(key => self.$delete(self.ingesting, key));
          websocket.close();
        }
      };
    },
    ingestFiles: function (keys) {
      let self = this;

      // Switch to ingest tab...
      this.tab = "ingest";

      // Clear existing log...
      self.log.length = 0;

      // Set key status to ingesting.
      keys.forEach(key => this.$set(this.ingesting, key, true));

      DAO.ingestFiles(keys, self.optTolerant, self.optCommit, self.optLogMsg)
        .then(data => {
          if (data.url && data.jobId) {
            self.monitorIngest(data.url, keys);
          } else {
            console.error("unexpected job data", data);
          }
        });
    },
    ingestAll: function () {
      let self = this;

      // Switch to ingest tab...
      this.tab = "ingest";

      // Clear existing log...
      self.log.length = 0;

      let keys = self.files.map(f => f.key);

      // Set key status to ingesting.
      keys.forEach(key => this.$set(this.ingesting, key, true));

      DAO.ingestAll(self.optTolerant, self.optCommit, self.optLogMsg).then(data => {
        if (data.url && data.jobId) {
          self.monitorIngest(data.url, keys);
        } else {
          console.error("unexpected job data", data);
        }
      });
    },
    setPanelSize: function(arbitrarySize) {
      this.panelSize = arbitrarySize;
    }
  },
  created: function () {
    this.refresh();
  },
  computed: {
    selectedKeys: function() {
      return Object.keys(this.selected);
    }
  },
  template: `
    <div id="data-manager-container"
         v-on:dragover.prevent.stop="dragOver"
         v-on:dragleave.prevent.stop="dragLeave"
         v-on:drop.prevent.stop="uploadFiles">
      <div id="panel-1">
        <div id="actions-bar">
          <div id="filter-control">
            <label for="filter-input" class="sr-only">Filter files</label>
            <input id="filter-input" class="form-control form-control-sm" type="text" v-model.trim="filter" placeholder="Filter files..." v-on:keyup="filterFiles"/>
            <i id="filtering-indicator" class="fa fa-circle-o-notch fa-fw fa-spin" v-if="filtering"/>
            <i id="filtering-indicator" style="cursor: pointer" v-on:click="clearFilter" class="fa fa-close fa-fw" v-else-if="filter"/>
          </div>

          <button v-bind:disabled="files.length===0" class="btn btn-sm btn-default" v-on:click.prevent="ingestFiles(selectedKeys)" v-if="selectedKeys.length">
            <i class="fa fa-database"/>
            Ingest Selected ({{selectedKeys.length}})
          </button>
          <button v-bind:disabled="files.length===0" class="btn btn-sm btn-default" v-on:click.prevent="ingestAll()" v-else>
            <i class="fa fa-database"/>
            Ingest All
          </button>

          <button v-bind:disabled="files.length===0" class="btn btn-sm btn-default" v-on:click.prevent="deleteFiles(selectedKeys)" v-if="selectedKeys.length > 0">
            <i class="fa fa-trash-o"/>
            Delete Selected ({{selectedKeys.length}})
          </button>
          <button v-bind:disabled="files.length===0" class="btn btn-sm btn-default" v-on:click.prevent="deleteAll()" v-else>
            <i class="fa fa-trash-o"/>
            Delete All
          </button>

          <button id="file-upload" class="btn btn-sm btn-default">
            <input id="file-selector"
                   v-on:change="uploadFiles"
                   v-on:dragover.prevent="dragOver"
                   v-on:dragleave.prevent="dragLeave"
                   v-on:drop.prevent="uploadFiles"
                   type="file"
                   accept="text/xml" multiple/>
            <i class="fa fa-cloud-upload"/>
            Upload Files
          </button>

          <button id="show-options" class="btn btn-sm btn-default" v-on:click="showOptions = !showOptions">
            <i class="fa fa-gear"/>
          </button>

          <div id="options-dialog" class="modal show fade" tabindex="-1" role="dialog" v-if="showOptions" style="display: block">
            <div class="modal-dialog modal-dialog-centered" role="document">
              <div class="modal-content">
                <div class="modal-header">
                  <h5 class="modal-title">Testing Parameters</h5>
                  <button type="button" class="close" data-dismiss="modal" aria-label="Close" v-on:click="showOptions = false">
                    <span aria-hidden="true">&times;</span>
                  </button>
                </div>
                <div class="modal-body">
                  <div id="options-form">
                    <div class="form-group form-check">
                      <input class="form-check-input" id="opt-tolerant-check" type="checkbox" v-model="optTolerant"/>
                      <label class="form-check-label" for="opt-tolerant-check">
                        Tolerant Mode: do not abort on individual file errors
                      </label>
                    </div>
                    <div class="form-group form-check">
                      <input class="form-check-input" id="opt-commit-check" type="checkbox" v-model="optCommit"/>
                      <label class="form-check-label" for="opt-commit-check">
                        Commit Ingest: make changes to database
                      </label>
                    </div>
                    <div class="form-group">
                      <label for="opt-log-message">Log Message</label>
                      <input class="form-control form-control-sm" id="opt-log-message" v-model="optLogMsg"/>
                    </div>
                  </div>
                </div>
                <div class="modal-footer">
                  <button type="button" class="btn btn-secondary" data-dismiss="modal" v-on:click="showOptions = false">Close</button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <files-table
          v-bind:dropping="dropping" 
          v-bind:loaded="loaded"
          v-bind:files="files"
          v-bind:selected="selected"
          v-bind:previewing="previewing"
          v-bind:truncated="truncated"
          v-bind:deleting="deleting"
          v-bind:ingesting="ingesting"
          v-bind:filter="filter"

          v-on:delete-files="deleteFiles"
          v-on:ingest-files="ingestFiles"
          v-on:files-loaded="filesLoaded"
          v-on:show-preview="showPreview"
        />
      </div>

      <div id="panel-2">
        <ul id="status-panel-tabs" class="nav nav-tabs">
          <li class="nav-item">
            <a href="#tab-preview" class="nav-link" v-bind:class="{'active': tab === 'preview'}"
               v-on:click.prevent="tab = 'preview'">
              File Preview <template v-if="previewing"> - {{previewing}}</template>
            </a>
          </li>
          <li class="nav-item">
            <a href="#tab-ingest-log" class="nav-link" v-bind:class="{'active': tab === 'ingest'}"
               v-on:click.prevent="tab = 'ingest'">
              Ingest Log
            </a>
          </li>
          <li>
            <drag-handle
              v-bind:p1="$el.querySelector('#panel-1')"
              v-bind:container="$el"
              v-on:resize="setPanelSize"
            />
          </li>
        </ul>

        <div id="status-panels">
          <div class="status-panel" id="tab-preview" v-show="tab === 'preview'">
            <preview v-bind:previewing="previewing" v-bind:panelSize="panelSize" v-show="previewing !== null"/>
            <div id="preview-placeholder" class="panel-placeholder" v-if="previewing === null">
              No file selected.
            </div>
          </div>
          <div class="status-panel" id="tab-ingest-log" v-show="tab === 'ingest'">
            <log-window v-bind:log="log" v-if="log.length > 0"/>
            <div id="preview-placeholder" class="panel-placeholder" v-else>
              Ingest log output will show here.
            </div>
          </div>
        </div>
      </div>

      <upload-progress
        v-bind:uploading="uploading"
        v-on:finish-item="finishUpload"/>
    </div>
  `
});
