"use strict";

// Bytes-to-human readable string from:
// https://stackoverflow.com/a/14919494/285374
Vue.filter("humanFileSize", function (bytes, si) {
  let f = (bytes, si) => {
    let thresh = si ? 1000 : 1024;
    if (Math.abs(bytes) < thresh) {
      return bytes + ' B';
    }
    let units = si
      ? ['kB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB']
      : ['KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];
    let u = -1;
    do {
      bytes /= thresh;
      ++u;
    } while (Math.abs(bytes) >= thresh && u < units.length - 1);
    return bytes.toFixed(1) + ' ' + units[u];
  };
  return _.memoize(f)(bytes, si);
});

Vue.filter("prettyDate", function (time) {
  let f = time => {
    let m = moment(time);
    return m.isValid() ? m.fromNow() : "";
  };
  return _.memoize(f)(time);
});

/**
 * A data access object containing OAI-PMH API functions.
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
    return this.call(SERVICE.listFiles(CONFIG.repositoryId, CONFIG.stage, prefix, after));
  },

  validateFiles: function (paths) {
    return this.call(SERVICE.validateFiles(CONFIG.repositoryId, CONFIG.stage), paths);
  },

  fileUrls: function (paths) {
    return this.call(SERVICE.fileUrls(CONFIG.repositoryId, CONFIG.stage), paths);
  },

  harvest: function (config) {
    return this.call(SERVICE.oaipmhHarvest(CONFIG.repositoryId), config);
  },

  cancelHarvest: function(jobId) {
    return this.call(SERVICE.oaipmhCancelHarvest(CONFIG.repositoryId, jobId));
  },

  getConfig: function () {
    return this.call(SERVICE.oaipmhGetConfig(CONFIG.repositoryId));
  },

  saveConfig: function (config) {
    return this.call(SERVICE.oaipmhSaveConfig(CONFIG.repositoryId), config);
  },

  deleteConfig: function () {
    return this.call(SERVICE.oaipmhDeleteConfig(CONFIG.repositoryId));
  },

  testConfig: function (config) {
    return this.call(SERVICE.oaipmhTestConfig(CONFIG.repositoryId), config);
  },
};

Vue.component("preview", {
  props: {
    panelSize: Number,
    previewing: String,
    errors: null,
  },
  data: function () {
    return {
      loading: false,
      validating: false,
      previewData: null,
      previewTruncated: false,
      percentDone: 0,
    }
  },
  methods: {
    validate: function () {
      let self = this;
      if (self.previewing === null) {
        return;
      }

      self.validating = true;
      DAO.validateFiles([self.previewing]).then(errs => {
        this.$set(this.errors, self.previewing, errs[self.previewing]);
        this.updateErrors();
        this.validating = false;
      });
    },
    updateErrors: function () {
      if (this.errors[this.previewing] && this.editor) {
        let doc = this.editor.getDoc();

        function makeMarker(err) {
          let marker = document.createElement("div");
          marker.style.color = "#822";
          marker.style.marginLeft = "3px";
          marker.className = "validation-error";
          marker.innerHTML = '<i class="fa fa-exclamation-circle"></i>';
          marker.querySelector("i").setAttribute("title", err.error);
          marker.addEventListener("click", function () {
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

        this.errors[this.previewing].forEach(e => {
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
    previewData: function (newValue) {
      let editorValue = this.editor.getValue();
      if (newValue !== editorValue) {
        let scrollInfo = this.editor.getScrollInfo();
        this.editor.setValue(newValue);
        this.editor.scrollTo(scrollInfo.left, scrollInfo.top);
      }
    },
    previewing: function (newValue, oldValue) {
      if (newValue !== null && newValue !== oldValue) {
        this.load();
      }
    },
    panelSize: function (newValue, oldValue) {
      if (newValue !== null && newValue !== oldValue) {
        this.editor.refresh();
      }
    }
  },
  mounted: function () {
    this.editor = CodeMirror.fromTextArea(this.$el.querySelector("textarea"), {
      mode: 'xml',
      lineNumbers: true,
      readOnly: true,
      gutters: [{className: "validation-errors", style: "width: 18px"}]
    });
    this.editor.on("refresh", () => this.updateErrors());

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
           v-if="!validating && errors[previewing] && errors[previewing].length === 0">
        <i class="fa fa-check"></i>
      </div>
      <div id="preview-loading-indicator" v-if="loading">
        <i class="fa fa-3x fa-spinner fa-spin"></i>
      </div>
    </div>
  `
});

Vue.component("files-table", {
  props: {
    loaded: Boolean,
    previewing: String,
    validating: Object,
    validationResults: Object,
    files: Array,
    selected: Object,
    truncated: Boolean,
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
    toggleAll: function (evt) {
      for (let i = 0; i < this.files.length; i++) {
        this.toggleItem(this.files[i].key, evt);
      }
    },
    toggleItem: function (key, evt) {
      if (evt.target.checked) {
        this.$set(this.selected, key, true);
      } else {
        this.$delete(this.selected, key);
      }
    }
  },
  watch: {
    selected: function (newValue) {
      let selected = Object.keys(newValue).length;
      this.$el.querySelector("#checkall").indeterminate =
        selected > 0 && selected !== this.files.length;
    },
  },
  computed: {
    allChecked: function () {
      return Object.keys(this.selected).length === this.files.length;
    }
  },
  template: `
    <div id="file-list-container" v-bind:class="{'loading': !loaded}">
      <table class="table table-bordered table-striped table-sm" v-if="files.length > 0">
        <thead>
        <tr>
          <th><input type="checkbox" id="checkall" v-on:change="toggleAll"/></th>
          <th>Name</th>
          <th>Last Modified</th>
          <th>Size</th>
          <th colspan="2"></th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="file in files"
            v-bind:key="file.key"
            v-on:click="$emit('show-preview', file.key)"
            v-bind:class="{'active': previewing === file.key}">
          <td><input type="checkbox" v-bind:checked="selected[file.key]" v-on:click.stop="toggleItem(file.key, $event)">
          </td>
          <td>{{file.key}}</td>
          <td v-bind:title="file.lastModified">{{file.lastModified | prettyDate}}</td>
          <td>{{file.size | humanFileSize(true)}}</td>
          <td><a href="#" v-on:click.prevent.stop="$emit('validate-files', [file.key])">
            <i v-if="validating[file.key]" class="fa fa-fw fa-circle-o-notch fa-spin"></i>
            <i v-else-if="validationResults[file.key]" class="fa fa-fw" v-bind:class="{
              'fa-check text-success': validationResults[file.key].length === 0,
              'fa-exclamation-circle text-danger': validationResults[file.key].length > 0
             }"></i>
            <i v-else class="fa fa-fw fa-flag-o"></i>
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
  updated: function () {
    this.$el.scrollTop = this.$el.scrollHeight;
  },
  template: `
    <pre v-if="log.length > 0"><template v-for="msg in log"><span v-html="msg"></span><br/></template></pre>
  `
});

Vue.component("drag-handle", {
  props: {
    p2: Element,
    container: Element,
  },
  data: function () {
    return {
      offset: 0,
    }
  },

  methods: {
    move: function (evt) {
      // Calculate the height of the topmost panel in percent.
      let maxY = this.container.offsetTop + this.container.offsetHeight;
      let topY = this.container.offsetTop;
      let posY = evt.clientY - this.offset;

      let pxHeight = Math.min(maxY, Math.max(0, posY - topY));
      let percentHeight = pxHeight / this.container.offsetHeight * 100;

      // Now convert to the height of the lower panel.
      let perc = 100 - percentHeight;
      this.p2.style.flexBasis = perc + "%";
    },
    startDrag: function (evt) {
      console.log("Bind resize", new Date());
      let us = this.container.style.userSelect;
      let cursor = this.container.style.cursor;
      this.offset = evt.clientY - this.$el.offsetTop;
      this.container.addEventListener("mousemove", this.move);
      this.container.style.userSelect = "none";
      this.container.style.cursor = "ns-resize";
      window.addEventListener("mouseup", () => {
        console.log("Stop resize");
        this.offset = 0;
        this.$emit("resize", this.p2.clientHeight);
        this.container.style.userSelect = us;
        this.container.style.cursor = cursor;
        this.container.removeEventListener("mousemove", this.move);
      }, {once: true});
    },
  },
  template: `
    <div id="drag-handle" v-on:mousedown="startDrag"></div>
  `
});

Vue.component("oaipmh-config-modal", {
  props: {
    show: Boolean,
    config: Object
  },
  data: function() {
    return {
      url: this.config ? this.config.url : null,
      format: this.config ? this.config.format : null,
      set: this.config ? this.config.set : null,
      tested: null,
      error: null
    }
  },
  methods: {
    save: function() {
      DAO.saveConfig({url: this.url, format: this.format, set: this.set})
        .then(data => this.$emit("saved-config", data));
    },
    testEndpoint: function() {
      DAO.testConfig({url: this.url, format: this.format, set: this.set})
        .then( r => {
          this.tested = !!r.name;
          this.error = null;
        })
        .catch(e => {
          this.tested = false;
          let err = e.response.data;
          if (err.error) {
            this.error = err.error;
          }
        });
    }
  },
  computed: {
    isValidConfig: function() {
      return this.url
        && this.url.trim() !== ""
        && this.format
        && this.format.trim() !== "";
    },
    hasConfigChanged: function() {
      return !this.config || !(
        this.config.url === this.url
        && this.config.format === this.format
        && this.config.set === this.set);
    },
  },
  watch: {
    config: function(newValue) {
      this.url = newValue ? newValue.url : null;
      this.format = newValue ? newValue.format : null;
      this.set = newValue ? newValue.set : null;
    }
  },
  template: `
    <div id="options-dialog" class="modal show fade" tabindex="-1" role="dialog" v-if="show"
         style="display: block">
      <div class="modal-dialog modal-dialog-centered" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">OAI-PMH Endpoint Configuration</h5>
            <button type="button" class="close" data-dismiss="modal" aria-label="Close"
                    v-on:click="$emit('close')">
              <span aria-hidden="true">&times;</span>
            </button>
          </div>
          <div class="modal-body">
            <div id="options-form">
              <div class="form-group">
                <label class="form-label" for="opt-endpoint-url">
                  OAI-PMH endpoint URL
                </label>
                <input class="form-control" id="opt-endpoint-url" type="url" v-model.trim="url"/>
              </div>
              <div class="form-group">
                <label class="form-label" for="opt-format">
                  OAI-PMH metadata format
                </label>
                <input class="form-control" id="opt-format" type="text" v-model.trim="format"/>
              </div>
              <div class="form-group">
                <label class="form-label" for="opt-set">
                  OAI-PMH set
                </label>
                <input class="form-control" id="opt-set" type="text" v-model.trim="set"/>
              </div>
              <div id="endpoint-errors">
                <span v-if="tested === null">&nbsp;</span>
                <span v-else-if="error" class="text-danger">{{error}}</span>
                <span v-else class="text-success">No errors detected</span>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button v-on:click="testEndpoint" type="button" class="btn btn-default">
              <i v-if="tested === null" class="fa fa-fw fa-question"/>
              <i v-else-if="tested" class="fa fa-fw fa-check text-success"/>
              <i v-else class="fa fa-fw fa-close text-danger"/>
              Test
            </button>
            <button v-bind:disabled="!isValidConfig || !hasConfigChanged"
                    v-on:click="save" type="button" class="btn btn-secondary">
              Save
            </button>
          </div>
        </div>
      </div>
    </div>
  `
})

Vue.component("oaipmh-manager", {
  data: function () {
    return {
      loaded: false,
      harvestJobId: null,
      truncated: false,
      filter: "",
      filtering: false,
      files: [],
      selected: {},
      log: [],
      validating: {},
      validationResults: {},
      tab: 'preview',
      previewing: null,
      panelSize: null,
      showOptions: false,
      harvestConfig: null,
    }
  },
  methods: {
    clearFilter: function () {
      this.filter = "";
      return this.refresh();
    },
    filterFiles: function () {
      let func = () => {
        this.filtering = true;
        return this.refresh().then(r => {
          this.filtering = false;
          return r;
        });
      };
      return _.debounce(func, 300)();
    },
    refresh: _.debounce(function () {
      return DAO.listFiles(this.filter).then(data => {
        this.files = data.files;
        this.truncated = data.truncated;
        this.loaded = true;
      });
    }, 500),
    filesLoaded: function (truncated) {
      this.truncated = truncated;
    },
    showPreview: function (key) {
      this.previewing = key;
      this.tab = 'preview';
    },
    validateFiles: function (keys) {
      keys.forEach(key => this.$set(this.validating, key, true));
      keys.forEach(key => this.$delete(this.validationResults, key));
      DAO.validateFiles(keys).then(errs => {
        this.tab = 'validation';
        keys.forEach(key => {
          this.$set(this.validationResults, key, errs[key] ? errs[key] : []);
          this.$delete(this.validating, key);
        });
      });
    },
    harvest: function() {
      DAO.harvest(this.harvestConfig)
        .then(data => {
          this.harvestJobId = data.jobId;
          this.monitorHarvest(data.url, data.jobId);
        });
    },
    cancelHarvest: function() {
      if (this.harvestJobId) {
        DAO.cancelHarvest(this.harvestJobId).then(r => {
          if (r.ok) {
            this.harvestJobId = null;
          }
        });
      }
    },
    monitorHarvest: function (url, jobId) {
      let self = this;
      this.tab = 'harvest';
      let websocket = new WebSocket(url);
      websocket.onopen = function() {
        window.location.hash = "#jobId:" + jobId;
        console.debug("Connected to", url);
      };
      websocket.onerror = function (e) {
        self.log.push("ERROR: a websocket communication error occurred");
        console.error("Socket error!", e);
      };
      websocket.onmessage = function (e) {
        let msg = JSON.parse(e.data);
        self.log.push(msg.trim());
        self.refresh()
        if (msg.indexOf(DONE_MSG) !== -1 || msg.indexOf(ERR_MSG) !== -1) {
          websocket.close();
        }
      };
      websocket.onclose = function() {
        self.harvestJobId = null;
        history.pushState("", document.title, window.location.pathname
          + window.location.search);
        console.debug("Socket closed")
      }
    },
    resumeMonitor: function() {
      let hash = window.location.hash;
      if (hash) {
        let parts = hash.split(":");
        if (parts.length === 2 && parts[0] === "#jobId") {
          this.harvestJobId = parts[1];
          this.monitorHarvest("ws://localhost:9000/admin/tasks/monitor?jobId=" + parts[1], parts[1]);
        }
      }
    },
    setPanelSize: function (arbitrarySize) {
      this.panelSize = arbitrarySize;
    },
    savedConfig: function(config) {
      this.harvestConfig = config;
    },
    loadConfig: function() {
      DAO.getConfig()
        .then(data => this.harvestConfig = data);
    },
  },
  created: function () {
    this.refresh();
    this.loadConfig();
    this.resumeMonitor();
  },
  computed: {
    selectedKeys: function () {
      return Object.keys(this.selected);
    },
    validationLog: function () {
      let log = [];
      this.files.forEach(file => {
        let key = file.key;
        let errs = this.validationResults[key];
        if (errs) {
          let cls = errs.length === 0 ? "text-success" : "text-danger";
          log.push('<span class="' + cls + '">' + key + '</span>' + ":" + (errs.length === 0 ? " ✓" : ""));
          errs.forEach(err => {
            log.push("    " + err.line + "/" + err.pos + " - " + err.error);
          });
        }
      });
      return log;
    }
  },
  template: `
    <div id="oaipmh-manager-container"
         v-on:dragover.prevent.stop="dragOver"
         v-on:dragleave.prevent.stop="dragLeave"
         v-on:drop.prevent.stop="uploadFiles">

      <div id="actions-bar">
        <div id="filter-control">
          <label for="filter-input" class="sr-only">Filter files</label>
          <input id="filter-input" class="form-control form-control-sm" type="text" v-model.trim="filter"
                 placeholder="Filter files..." v-on:keyup="filterFiles"/>
          <i id="filtering-indicator" class="fa fa-circle-o-notch fa-fw fa-spin" v-if="filtering"/>
          <i id="filtering-indicator" style="cursor: pointer" v-on:click="clearFilter" class="fa fa-close fa-fw"
             v-else-if="filter"/>
        </div>

        <button v-if="!harvestJobId" v-bind:disabled="!harvestConfig" class="btn btn-sm btn-default"
                v-on:click.prevent="harvest">
          <i class="fa fa-fw fa-cloud-download"/>
          Harvest Files
        </button>
        <button v-else class="btn btn-sm btn-outline-danger" v-on:click.prevent="cancelHarvest">
          <i class="fa fa-fw fa-spin fa-circle-o-notch"></i>
          Cancel Harvest
        </button>

        <button v-if="selectedKeys.length" v-bind:disabled="files.length===0" class="btn btn-sm btn-default"
                v-on:click.prevent="validateFiles(selectedKeys)">
          <i class="fa fa-flag-o"/>
          Validate Selected ({{selectedKeys.length}})
        </button>
        <button v-else v-bind:disabled="files.length===0" class="btn btn-sm btn-default"
                v-on:click.prevent="validateFiles(files.map(f => f.key))">
          <i class="fa fa-flag-o"/>
          Validate All
        </button>

        <button id="show-options" class="btn btn-sm btn-default" v-on:click="showOptions = !showOptions">
          <i class="fa fa-gear"/>
          Configuration
        </button>
        
        <oaipmh-config-modal 
          v-bind:config="harvestConfig"
          v-bind:show="showOptions"
          v-on:saved-config="savedConfig"
          v-on:close="showOptions = false"/>
      </div>

      <div id="panel-container">
        <div id="panel-1">
          <files-table
            v-bind:loaded="loaded"
            v-bind:files="files"
            v-bind:selected="selected"
            v-bind:previewing="previewing"
            v-bind:validating="validating"
            v-bind:validationResults="validationResults"
            v-bind:truncated="truncated"
            v-bind:filter="filter"

            v-on:validate-files="validateFiles"
            v-on:files-loaded="filesLoaded"
            v-on:show-preview="showPreview"
          />
        </div>

        <div id="panel-2">
          <ul id="status-panel-tabs" class="nav nav-tabs">
            <li class="nav-item">
              <a href="#tab-preview" class="nav-link" v-bind:class="{'active': tab === 'preview'}"
                 v-on:click.prevent="tab = 'preview'">
                File Preview
                <template v-if="previewing"> - {{previewing}}</template>
              </a>
            </li>
            <li class="nav-item">
              <a href="#tab-validation-log" class="nav-link" v-bind:class="{'active': tab === 'validation'}"
                 v-on:click.prevent="tab = 'validation'">
                Validation Log
              </a>
            </li>
            <li class="nav-item">
              <a href="#tab-harvest-log" class="nav-link" v-bind:class="{'active': tab === 'harvest'}"
                 v-on:click.prevent="tab = 'harvest'">
                Harvest Log
              </a>
            </li>
            <li>
              <drag-handle
                v-bind:p2="$root.$el.querySelector('#panel-2')"
                v-bind:container="$root.$el.querySelector('#panel-container')"
                v-on:resize="setPanelSize"
              />
            </li>
          </ul>

          <div id="status-panels">
            <div class="status-panel" id="tab-preview" v-show="tab === 'preview'">
              <preview v-bind:previewing="previewing"
                       v-bind:errors="validationResults"
                       v-bind:panelSize="panelSize"
                       v-show="previewing !== null"/>
              <div id="preview-placeholder" class="panel-placeholder" v-if="previewing === null">
                No file selected.
              </div>
            </div>
            <div class="status-panel log-container" id="tab-validation-log" v-show="tab === 'validation'">
              <log-window v-bind:log="validationLog" v-if="validationLog.length > 0"/>
              <div id="validation-placeholder" class="panel-placeholder" v-else>
                Validation log output will show here.
              </div>
            </div>
            <div class="status-panel log-container" id="tab-harvest-log" v-show="tab === 'harvest'">
              <log-window v-bind:log="log" v-if="log.length > 0"/>
              <div id="harvest-placeholder" class="panel-placeholder" v-else>
                Harvest log output will show here.
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
});

let app = new Vue({
  el: '#oaipmh-manager',
  data: function() {
    return {

    }
  },
  template: `
    <div id="data-manager-container">
        <oaipmh-manager/>
    </div>
  `
});


