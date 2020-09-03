"use strict";

// Default log message testing. Overrideable in settings
const LOG_MESSAGE = "Testing Ingest";

// Prevent default drag/drop action...
window.addEventListener("dragover", e => e.preventDefault(), false);
window.addEventListener("drop", e => e.preventDefault(), false);

function sequential(func, arr, index) {
  if (index >= arr.length) return Promise.resolve();
  return func(arr[index])
    .then(() => sequential(func, arr, index + 1));
}

let stageMixin = {
  data: function() {
    return {
      loaded: false,
      truncated: false,
      tab: 'preview',
      previewing: null,
      deleting: {},
      selected: {},
      filter : {
        value: "",
        active: false
      },
      files: [],
      log: [],
    }
  },
  created: function() {
    console.log("Refreshing with", this.fileStage);
    this.load();
  },
  methods: {
    clearFilter: function () {
      this.filter.value = "";
      return this.refresh();
    },
    filterFiles: function () {
      let func = () => {
        this.filter.active = true;
        return this.load().then(r => {
          this.filter.active = false;
          return r;
        });
      };
      return _.debounce(func, 300)();
    },
    refresh: _.debounce(function() {
      return this.load();
    }, 500),
    load: function () {
      return DAO.listFiles(this.fileStage, this.filter.value).then(data => {
        this.files = data.files;
        this.truncated = data.truncated;
        this.loaded = true;
      })
    },
    filesLoaded: function (truncated) {
      this.truncated = truncated;
    },
    deleteFiles: function (keys) {
      if (keys.includes(this.previewing)) {
        this.previewing = null;
      }
      keys.forEach(key => this.$set(this.deleting, key, true));
      DAO.deleteFiles(this.fileStage, keys).then(deleted => {
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
      return DAO.deleteAll(this.fileStage).then(r => {
        this.refresh();
        this.deleting = {};
        r;
      });
    },
  },
  computed: {
    selectedKeys: function () {
      return Object.keys(this.selected);
    },
  }
}

Vue.component("upload-progress", {
  props: {
    uploading: Array,
  },
  template: `
    <div class="upload-progress" v-if="uploading.length > 0">
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
    fileStage: String,
    dropping: Boolean,
    loaded: Boolean,
    previewing: String,
    validating: Object,
    validationResults: Object,
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
      return DAO.listFiles(this.fileStage, this.filter, from).then(data => {
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
      this.$el.querySelector("#" + this.fileStage + "-checkall").indeterminate =
        selected > 0 && selected !== this.files.length;
    },
  },
  computed: {
    allChecked: function () {
      return Object.keys(this.selected).length === this.files.length;
    },
    utilRows: function() {
      return Number(this.deleted !== null) +
        Number(this.validating !== null) +
        Number(this.deleting !== null);
    }
  },
  template: `
    <div class="file-list-container" v-bind:class="{'loading': !loaded, 'dropping': dropping}">
      <table class="table table-bordered table-striped table-sm" v-if="files.length > 0">
        <thead>
        <tr>
          <th><input type="checkbox" v-bind:id="fileStage + '-checkall'" v-on:change="toggleAll"/></th>
          <th>Name</th>
          <th>Last Modified</th>
          <th>Size</th>
          <th v-bind:colspan="utilRows"></th>
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
          
          <td v-if="validating !== null"><a href="#" v-on:click.prevent.stop="$emit('validate-files', [file.key])">
            <i v-if="validating[file.key]" class="fa fa-fw fa-circle-o-notch fa-spin"></i>
            <i v-else-if="validationResults[file.key]" class="fa fa-fw" v-bind:class="{
              'fa-check text-success': validationResults[file.key].length === 0,
              'fa-exclamation-circle text-danger': validationResults[file.key].length > 0
             }"></i>
            <i v-else class="fa fa-fw fa-flag-o"></i>
          </a>
          </td>
          <td v-if="ingesting !== null"><a href="#" v-on:click.prevent.stop="$emit('ingest-files', [file.key])">
            <i class="fa fa-fw" v-bind:class="{
              'fa-database': !ingesting[file.key], 
              'fa-circle-o-notch fa-spin': ingesting[file.key]
            }"></i></a>
          </td>
          <td v-if="deleting !== null">
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
      <div class="panel-placeholder" v-else-if="loaded && filter && files.length === 0">
        No files found starting with &quot;<code>{{filter}}</code>&quot;...
      </div>
      <div class="panel-placeholder" v-else-if="loaded && files.length === 0">
        There are no files here yet.
      </div>
      <div class="file-list-loading-indicator" v-show="!loaded">
        <i class="fa fa-3x fa-spinner fa-spin"></i>
      </div>
    </div>
  `
});

Vue.component("upload-manager", {
  mixins: [stageMixin, twoPanelMixin, previewMixin, validatorMixin],
  props: {
    fileStage: String,
    config: Object,
  },
  data: function () {
    return {
      dropping: false,
      uploading: [],
      cancelled: [],
    }
  },
  methods: {
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
    dragOver: function () {
      this.dropping = true;
    },
    dragLeave: function () {
      this.dropping = false;
    },
    uploadFile: function (file) {
      // Check we're still in the queue and have not been cancelled...
      if (_.findIndex(this.uploading, f => f.spec.name === file.name) === -1) {
        return Promise.resolve();
      }

      let self = this;

      return DAO.uploadHandle(this.fileStage, {
        name: file.name,
        type: file.type,
        size: file.size
      }).then(data => {
        self.setUploadProgress(file, 0);
        return DAO.uploadFile(data.presignedUrl, file, function (evt) {
          return evt.lengthComputable
            ? self.setUploadProgress(file, Math.round((evt.loaded / evt.total) * 100))
            : true;
        }).then(() => {
          self.finishUpload(file);
          self.log.push("Uploaded file: " + file.name);
          return self.load();
        });
      });
    },
    uploadFiles: function (event) {
      this.dragLeave(event);
      this.tab = 'upload';
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
        .then(() => {
          if (event.target.files) {
            // Delete the value of the control, if loaded
            event.target.value = null;
          }

          console.log("Files uploaded...")
        });
    },
  },
  template: `
    <div id="upload-manager-container" class="stage-manager-container"
         v-on:dragover.prevent.stop="dragOver"
         v-on:dragleave.prevent.stop="dragLeave"
         v-on:drop.prevent.stop="uploadFiles">

      <div class="actions-bar">
        <filter-control v-bind:filter="filter"
                        v-on:filter="filterFiles"
                        v-on:clear="clearFilter"/>
        
        <button v-bind:disabled="files.length===0" class="btn btn-sm btn-default"
                v-on:click.prevent="validateFiles(selectedKeys)" v-if="selectedKeys.length">
          <i class="fa fa-flag-o"/>
          Validate Selected ({{selectedKeys.length}})
        </button>
        <button v-bind:disabled="files.length===0" class="btn btn-sm btn-default"
                v-on:click.prevent="validateFiles(files.map(f => f.key))" v-else>
          <i class="fa fa-flag-o"/>
          Validate All
        </button>

        <button v-bind:disabled="files.length===0" class="btn btn-sm btn-default"
                v-on:click.prevent="deleteFiles(selectedKeys)" v-if="selectedKeys.length > 0">
          <i class="fa fa-trash-o"/>
          Delete Selected ({{selectedKeys.length}})
        </button>
        <button v-bind:disabled="files.length===0" class="btn btn-sm btn-default" v-on:click.prevent="deleteAll()"
                v-else>
          <i class="fa fa-trash-o"/>
          Delete All
        </button>

        <button class="file-upload-button btn btn-sm btn-default">
          <input class="file-selector-input"
                 v-on:change="uploadFiles"
                 v-on:dragover.prevent="dragOver"
                 v-on:dragleave.prevent="dragLeave"
                 v-on:drop.prevent="uploadFiles"
                 type="file"
                 accept="text/xml" multiple/>
          <i class="fa fa-cloud-upload"/>
          Upload Files...
        </button>
      </div>

      <div id="upload-panel-container" class="panel-container">
        <div class="top-panel">
          <files-table
            v-bind:fileStage="fileStage"
            v-bind:dropping="dropping"
            v-bind:loaded="loaded"
            v-bind:files="files"
            v-bind:selected="selected"
            v-bind:previewing="previewing"
            v-bind:validating="validating"
            v-bind:validationResults="validationResults"
            v-bind:truncated="truncated"
            v-bind:deleting="deleting"
            v-bind:ingesting="null"
            v-bind:filter="filter.value"

            v-on:delete-files="deleteFiles"
            v-on:validate-files="validateFiles"
            v-on:files-loaded="filesLoaded"
            v-on:show-preview="showPreview"
          />
        </div>

        <div id="upload-status-panel" class="bottom-panel">
          <ul class="status-panel-tabs nav nav-tabs">
            <li class="nav-item">
              <a href="#" class="nav-link" v-bind:class="{'active': tab === 'preview'}"
                 v-on:click.prevent="tab = 'preview'">
                File Preview
                <template v-if="previewing"> - {{previewing}}</template>
              </a>
            </li>
            <li class="nav-item">
              <a href="#" class="nav-link" v-bind:class="{'active': tab === 'validation'}"
                 v-on:click.prevent="tab = 'validation'">
                Validation Log
              </a>
            </li>
            <li class="nav-item">
              <a href="#" class="nav-link" v-bind:class="{'active': tab === 'upload'}"
                 v-on:click.prevent="tab = 'upload'">
                Upload Log
              </a>
            </li>
            <li>
              <drag-handle
                v-bind:ns="fileStage"
                v-bind:p2="$root.$el.querySelector('#upload-status-panel')"
                v-bind:container="$root.$el.querySelector('#upload-panel-container')"
                v-on:resize="setPanelSize"
              />
            </li>
          </ul>

          <div class="status-panels">
            <div class="status-panel" v-show="tab === 'preview'">
              <preview v-bind:file-stage="fileStage"
                       v-bind:previewing="previewing"
                       v-bind:errors="validationResults"
                       v-bind:panel-size="panelSize"
                       v-bind:config="config"
                       v-show="previewing !== null" />
              <div class="panel-placeholder" v-if="previewing === null">
                No file selected.
              </div>
            </div>
            <div class="status-panel log-container" v-show="tab === 'validation'">
              <log-window v-bind:log="validationLog" v-if="validationLog.length > 0"/>
              <div id="validation-placeholder" class="panel-placeholder" v-else>
                Validation log output will show here.
              </div>
            </div>
            <div class="status-panel log-container" v-show="tab === 'upload'">
              <log-window v-bind:log="log" v-if="log.length > 0"/>
              <div class="panel-placeholder" v-else>
                Upload log output will show here.
              </div>
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
  },
  watch: {
    config: function(newValue) {
      this.url = newValue ? newValue.url : null;
      this.format = newValue ? newValue.format : null;
      this.set = newValue ? newValue.set : null;
    }
  },
  template: `
    <div class="options-dialog modal show fade" tabindex="-1" role="dialog" v-if="show"
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
            <div class="options-form">
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
            <button v-on:click="$emit('close')" type="button" class="btn btn-default">
              Cancel
            </button>
            <button v-on:click="testEndpoint" type="button" class="btn btn-default">
              <i v-if="tested === null" class="fa fa-fw fa-question"/>
              <i v-else-if="tested" class="fa fa-fw fa-check text-success"/>
              <i v-else class="fa fa-fw fa-close text-danger"/>
              Test Endpoint
            </button>
            <button v-bind:disabled="!isValidConfig"
                    v-on:click="save" type="button" class="btn btn-secondary">
              Harvest Endpoint
            </button>
          </div>
        </div>
      </div>
    </div>
  `
})

Vue.component("oaipmh-manager", {
  mixins: [stageMixin, twoPanelMixin, previewMixin, validatorMixin],
  props: {
    fileStage: String,
    config: Object,
  },
  data: function () {
    return {
      harvestJobId: null,
      showOptions: false,
      harvestConfig: null,
    }
  },
  methods: {
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
          this.monitorHarvest(this.config.monitorUrl(parts[1]), parts[1]);
        }
      }
    },
    saveConfigAndHarvest: function(config) {
      this.harvestConfig = config;
      this.showOptions = false;
      this.harvest();
    },
    loadConfig: function() {
      DAO.getConfig()
        .then(data => this.harvestConfig = data);
    },
  },
  created: function () {
    this.loadConfig();
    this.resumeMonitor();
  },
  template: `
    <div id="oaipmh-manager-container" class="stage-manager-container">
      <div class="actions-bar">
        <filter-control v-bind:filter="filter"
                        v-on:filter="filterFiles"
                        v-on:clear="clearFilter"/>

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

        <button v-bind:disabled="files.length===0" class="btn btn-sm btn-default"
                v-on:click.prevent="deleteFiles(selectedKeys)" v-if="selectedKeys.length > 0">
          <i class="fa fa-trash-o"/>
          Delete Selected ({{selectedKeys.length}})
        </button>
        <button v-bind:disabled="files.length===0" class="btn btn-sm btn-default" v-on:click.prevent="deleteAll()"
                v-else>
          <i class="fa fa-trash-o"/>
          Delete All
        </button>

        <button v-if="!harvestJobId" v-bind:disabled="!harvestConfig" class="btn btn-sm btn-default"
                v-on:click.prevent="showOptions = !showOptions">
          <i class="fa fa-fw fa-cloud-download"/>
          Harvest Files...
        </button>
        <button v-else class="btn btn-sm btn-outline-danger" v-on:click.prevent="cancelHarvest">
          <i class="fa fa-fw fa-spin fa-circle-o-notch"></i>
          Cancel Harvest
        </button>
        
        <oaipmh-config-modal 
          v-bind:config="harvestConfig"
          v-bind:show="showOptions"
          v-on:saved-config="saveConfigAndHarvest"
          v-on:close="showOptions = false"/>
      </div>

      <div id="oaipmh-panel-container" class="panel-container">
        <div class="top-panel">
          <files-table
            v-bind:fileStage="fileStage"
            v-bind:loaded="loaded"
            v-bind:files="files"
            v-bind:selected="selected"
            v-bind:previewing="previewing"
            v-bind:validating="validating"
            v-bind:validationResults="validationResults"
            v-bind:truncated="truncated"
            v-bind:deleting="deleting"
            v-bind:ingesting="null"
            v-bind:filter="filter.value"

            v-on:delete-files="deleteFiles"
            v-on:validate-files="validateFiles"
            v-on:files-loaded="filesLoaded"
            v-on:show-preview="showPreview"
          />
        </div>

        <div id="oaipmh-status-panels" class="bottom-panel">
          <ul class="status-panel-tabs nav nav-tabs">
            <li class="nav-item">
              <a href="#" class="nav-link" v-bind:class="{'active': tab === 'preview'}"
                 v-on:click.prevent="tab = 'preview'">
                File Preview
                <template v-if="previewing"> - {{previewing}}</template>
              </a>
            </li>
            <li class="nav-item">
              <a href="#" class="nav-link" v-bind:class="{'active': tab === 'validation'}"
                 v-on:click.prevent="tab = 'validation'">
                Validation Log
              </a>
            </li>
            <li class="nav-item">
              <a href="#" class="nav-link" v-bind:class="{'active': tab === 'harvest'}"
                 v-on:click.prevent="tab = 'harvest'">
                Harvest Log
              </a>
            </li>
            <li>
              <drag-handle
                v-bind:ns="fileStage"
                v-bind:p2="$root.$el.querySelector('#oaipmh-status-panels')"
                v-bind:container="$root.$el.querySelector('#oaipmh-panel-container')"
                v-on:resize="setPanelSize"
              />
            </li>
          </ul>

          <div class="status-panels">
            <div class="status-panel" v-show="tab === 'preview'">
              <preview v-bind:file-stage="fileStage" 
                       v-bind:previewing="previewing"
                       v-bind:errors="validationResults"
                       v-bind:panel-size="panelSize"
                       v-bind:config="config"
                       v-show="previewing !== null"/>
              <div class="panel-placeholder" v-if="previewing === null">
                No file selected.
              </div>
            </div>
            <div class="status-panel log-container" v-show="tab === 'validation'">
              <log-window v-bind:log="validationLog" v-if="validationLog.length > 0"/>
              <div  class="panel-placeholder" v-else>
                Validation log output will show here.
              </div>
            </div>
            <div class="status-panel log-container" v-show="tab === 'harvest'">
              <log-window v-bind:log="log" v-if="log.length > 0"/>
              <div class="panel-placeholder" v-else>
                Harvest log output will show here.
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
});

Vue.component("convert-config", {
  props: {
    show: Boolean,
  },
  data: function() {
    return {
      sources: [],
    }
  },
  methods: {
    convert: function() {
      this.$emit("convert", this.sources);
      this.$emit("close");
    },
  },
  computed: {
    isValidConfig: function() {
      return this.sources;
    },
  },
  template: `
    <div class="options-dialog modal show fade" tabindex="-1" role="dialog" v-if="show"
          style="display: block">
      <div class="modal-dialog modal-dialog-centered" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Transformation/Enhancement Configuration</h5>
            <button type="button" class="close" data-dismiss="modal" aria-label="Close" v-on:click="$emit('close')">
              <span aria-hidden="true">&times;</span>
            </button>
          </div>
          <div class="modal-body">
            <div class="options-form">
              <div class="form-group">
                <label class="form-label" for="opt-convert-src">
                  File Source(s)
                </label>
                <select id="opt-convert-src" class="form-control" multiple v-model="sources">
                  <option value="oaipmh">OAI-PMH</option>
                  <option value="upload">Upload</option>
                </select>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button v-bind:disabled="!isValidConfig" v-on:click="convert" type="button" class="btn btn-secondary">
              Run Conversion
            </button>
          </div>
        </div>
      </div>    
    </div>
  `
});

Vue.component("transformation-item", {
  props: {
    item: Object,
    enabled: Boolean,
  },
  template: `
    <div class="list-group-item transformation-item list-group-item-action" v-bind:class="{'enabled': enabled}">
      <h4 class="transformation-item-name">
        {{item.name}}
        <span class="transformation-item-comments" v-bind:title="item.comments">{{item.comments}}</span>
      </h4>
      <button class="transformation-item-edit btn btn-sm btn-default" v-on:click="$emit('edit')">
        <i class="fa fa-edit"></i>
      </button>
      <span class="transformation-item-meta">
        <span class="badge badge-pill" v-bind:class="'badge-' + item.bodyType">{{item.bodyType}}</span>
        <span v-if="!item.repoId" class="badge badge-light">Generic</span>
      </span>
    </div>
  `
});

Vue.component("convert-manager", {
  mixins: [twoPanelMixin, previewMixin, validatorMixin],
  props: {
    fileStage: String,
    config: Object,
  },
  data: function () {
    return {
      convertJobId: null,
      ingesting: {},
      previewing: null,
      previewList: [],
      tab: 'preview',
      log: [],
      showOptions: false,
      available: [],
      enabled: [],
      mappings: [], // IDs of enabled transformations
      editing: null,
      loading: false,
    }
  },
  methods: {
    loadTransformations: function() {
      this.loading = true;

      return DAO.listDataTransformations().then(available => {
        let each = _.partition(available, item => !_.includes(this.mappings, item.id));
        this.available = each[0];
        this.enabled = each[1];

        this.loading = false;
      });
    },
    editTransformation: function(item) {
      this.editing = item;
    },
    newTransformation: function() {
      this.editing = {
        id: null,
        repoId: this.config.repositoryId,
        name: "",
        bodyType: "xslt",
        body: "",
        comments: "",
      };
    },
    closeEditForm: function() {
      this.editing = null;
      this.loadTransformations();
    },
    saved: function(item) {
      this.editing = item;
    },
    convert: function(sources) {
      DAO.convert({mappings: this.mappings, src: sources}).then(data => {
          this.convertJobId = data.jobId;
          this.monitorConvert(data.url, data.jobId);
        });
    },
    cancelConvert: function() {
      if (this.convertJobId) {
        DAO.cancelConvert(this.convertJobId).then(r => {
          if (r.ok) {
            this.convertJobId = null;
          }
        });
      }
    },
    monitorConvert: function (url, jobId) {
      let self = this;
      this.tab = 'convert';
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
        self.$emit('refresh-stage', self.config.ingest);
        if (msg.indexOf(DONE_MSG) !== -1 || msg.indexOf(ERR_MSG) !== -1) {
          websocket.close();
        }
      };
      websocket.onclose = function() {
        self.convertJobId = null;
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
          this.convertJobId = parts[1];
          this.monitorConvert(this.config.monitorUrl(parts[1]), parts[1]);
        }
      }
    },
    loadConfig: function() {
      return DAO.getConvertConfig()
        .then(data => this.mappings = data.map(item => item.id));
    },
    saveConfig: function() {
      let mappings = this.enabled.map(item => item.id);
      if (!_.isEqual(mappings, this.mappings)) {
        console.log("saving enabled:", this.enabled)
        this.mappings = mappings;
        DAO.saveConvertConfig(this.mappings)
          .then(ok => console.log("Saved mapping list..."));
      }
    },
    loadPreviewList: function() {
      this.loading = true;
      DAO.listFiles("upload", "")
        .then(data => {
          this.previewList = data.files;
          this.loading = false;
        });
    },
  },
  watch: {
    enabled: function(newValue, oldValue) {
      this.saveConfig();
    }
  },
  created: function () {
    this.loadConfig().then(_ => {
      this.loadTransformations();
    });
    this.loadPreviewList();
    this.resumeMonitor();
  },
  template: `
    <div id="convert-manager-container" class="stage-manager-container">

      <div class="actions-bar">
        <select id="preview-file-selector" v-model="previewing" v-bind:disabled="convertJobId !== null" class="btn btn-sm btn-default">
          <option v-bind:value="null">Select file to preview...</option>
          <option v-for="file in previewList" v-bind:value="file.key">{{file.key}}</option>
        </select>
        
        <button class="btn btn-sm btn-default" v-on:click.prevent="newTransformation">
          <i class="fa fa-file-code-o"></i>
          New Transformation...
        </button>

        <button v-if="!convertJobId" class="btn btn-sm btn-default" v-on:click.prevent="showOptions = true">
          <i class="fa fa-fw fa-file-code-o"/>
          Convert Files...
        </button>
        <button v-else class="btn btn-sm btn-outline-danger" v-on:click.prevent="cancelConvert">
          <i class="fa fa-fw fa-spin fa-circle-o-notch"></i>
          Cancel Convert
        </button>
        
        <edit-form-panes
          v-if="editing !== null"
          v-bind:id="editing.id"
          v-bind:name="editing.name"
          v-bind:generic="!editing.repoId"
          v-bind:body-type="editing.bodyType"
          v-bind:body="editing.body"
          v-bind:comments="editing.comments"
          v-bind:preview="previewing"
          v-bind:preview-list="previewList"
          v-bind:config="config"
          v-on:saved="saved"
          v-on:close="closeEditForm"/>

      </div>

      <div id="convert-panel-container" class="panel-container">
        <div class="top-panel">

          <convert-config
            v-bind:show="showOptions"
            v-on:close="showOptions = false"
            v-on:convert="convert"
            v-show="showOptions" />
          
          <div id="convert-mappings">
            <div class="card">
              <h4 class="card-header">
                Available Transformations
              </h4>
              
              <div class="transformation-list-placeholder" v-if="enabled.length === 0 && available.length === 0">
                <h3>No transformations available.</h3>
                <p><a href="#" v-on:click.prevent="newTransformation">Create a new one now...</a></p>
              </div>

            <draggable
              class="list-group transformation-list"
              draggable=".transformation-item"
              group="transformations"
              v-bind:sort="false"
              v-model="available">
              <transformation-item
                v-for="(dt, i) in available"
                v-bind:item="dt"
                v-bind:key="i"
                v-bind:enabled="_.includes(mappings, item => item.id)"
                v-on:edit="editTransformation(dt)"
              />
            </draggable>
            </div>
            
            
            <div class="spacer"></div>
            <div class="card">
              <h4 class="card-header">
                Enabled Transformations
              </h4>
              
              <div class="transformation-list-placeholder" v-if="enabled.length === 0">
                <h3>No transformations are enabled.</h3>
                <p>Drag available transformations into this area to
                  activate them.</p>
              </div>

              <draggable
                class="list-group transformation-list"
                draggable=".transformation-item"
                group="transformations"
                v-bind:sort="true"
                v-model="enabled">
                <transformation-item
                  v-for="(dt, i) in enabled"
                  v-bind:item="dt"
                  v-bind:key="i"
                  v-bind:enabled="_.includes(mappings, item => item.id)"
                  v-on:edit="editTransformation(dt)"
                />
              </draggable>
            </div>
          </div>
        </div>

        <div id="convert-status-panels" class="bottom-panel">
          <ul class="status-panel-tabs nav nav-tabs">
            <li class="nav-item">
              <a href="#" class="nav-link" v-bind:class="{'active': tab === 'preview'}"
                 v-on:click.prevent="tab = 'preview'">
                File Preview
                <template v-if="previewing"> - {{previewing}}</template>
              </a>
            </li>
            <li class="nav-item">
              <a href="#" class="nav-link" v-bind:class="{'active': tab === 'convert'}"
                 v-on:click.prevent="tab = 'convert'">
                Convert Log
              </a>
            </li>
            <li>
              <drag-handle
                v-bind:ns="fileStage"
                v-bind:p2="$root.$el.querySelector('#convert-status-panels')"
                v-bind:container="$root.$el.querySelector('#convert-panel-container')"
                v-on:resize="setPanelSize"
              />
            </li>
          </ul>

          <div class="status-panels">
            <div class="status-panel" v-show="tab === 'preview'">
              <convert-preview
                       v-bind:file-stage="'upload'"
                       v-bind:mappings="mappings"
                       v-bind:trigger="JSON.stringify({
                         mappings: mappings, 
                         previewing: previewing
                       })"
                       v-bind:previewing="previewing"
                       v-bind:errors="validationResults"
                       v-bind:panel-size="panelSize"
                       v-bind:config="config"
                       v-show="previewing !== null"/>
              <div class="panel-placeholder" v-if="previewing === null">
                No file selected.
              </div>
            </div>
            <div class="status-panel log-container" v-show="tab === 'convert'">
              <log-window v-bind:log="log" v-if="log.length > 0"/>
              <div class="panel-placeholder" v-else>
                Convert log output will show here.
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
});

Vue.component("ingest-options-panel", {
  props: {
    opts: Object
  },
  computed: {
    isValidConfig: function() {
      return this.opts.logMsg && this.opts.logMsg.trim() !== "";
    }
  },
  template: `
    <div class="options-dialog modal show fade" tabindex="-1" role="dialog"
         style="display: block">
      <div class="modal-dialog modal-dialog-centered" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Testing Parameters</h5>
            <button type="button" class="close" data-dismiss="modal" aria-label="Close"
                    v-on:click="$emit('close')">
              <span aria-hidden="true">&times;</span>
            </button>
          </div>
          <div class="modal-body">
            <div class="options-form">
              <div class="form-group form-check">
                <input class="form-check-input" id="opt-tolerant-check" type="checkbox" v-model="opts.tolerant"/>
                <label class="form-check-label" for="opt-tolerant-check">
                  Tolerant Mode: do not abort on individual file errors
                </label>
              </div>
              <div class="form-group form-check">
                <input class="form-check-input" id="opt-commit-check" type="checkbox" v-model="opts.commit"/>
                <label class="form-check-label" for="opt-commit-check">
                  Commit Ingest: make changes to database
                </label>
              </div>
              <div class="form-group">
                <label for="opt-log-message">Log Message</label>
                <input class="form-control form-control-sm" id="opt-log-message" v-model="opts.logMsg"/>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-default" data-dismiss="modal" v-on:click="$emit('close')">
              Cancel
            </button>
            <button v-bind:disabled="!isValidConfig" type="button" class="btn btn-secondary" data-dismiss="modal" v-on:click="$emit('submit')">
              Run Ingest
            </button>
          </div>
        </div>
      </div>
    </div>
  `
});

Vue.component("ingest-manager", {
  mixins: [stageMixin, twoPanelMixin, previewMixin, validatorMixin],
  props: {
    fileStage: String,
    config: Object,
  },
  data: function () {
    return {
      ingesting: {},
      showOptions: false,
      opts: {
        commit: false,
        tolerant: false,
        logMsg: LOG_MESSAGE
      }
    }
  },
  methods: {
    monitorIngest: function (url, keys) {
      let self = this;
      let websocket = new WebSocket(url);
      websocket.onerror = function (e) {
        self.log.push("ERROR: a websocket communication error occurred");
        console.error("Socket error!", e);
        keys.forEach(key => self.$delete(self.ingesting, key));
      };
      websocket.onmessage = function (e) {
        let msg = JSON.parse(e.data);
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

      DAO.ingestFiles(this.fileStage, keys, self.opts.tolerant, self.opts.commit, self.opts.logMsg)
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

      DAO.ingestAll(this.fileStage, self.opts.tolerant, self.opts.commit, self.opts.logMsg).then(data => {
        if (data.url && data.jobId) {
          self.monitorIngest(data.url, keys);
        } else {
          console.error("unexpected job data", data);
        }
      });
    },
    doIngest: function() {
      this.showOptions = false;
      if (this.selectedKeys.length > 0) {
        this.ingestFiles(this.selectedKeys);
      } else {
        this.ingestAll();
      }
    }
  },
  template: `
    <div id="ingest-manager-container" class="stage-manager-container">
      <div class="actions-bar">
        <filter-control 
          v-bind:filter="filter"
          v-on:filter="filterFiles" 
          v-on:clear="clearFilter"
          v-on:refresh="load" />
        
        <button v-bind:disabled="files.length===0" class="btn btn-sm btn-default"
                v-on:click.prevent="validateFiles(selectedKeys)" v-if="selectedKeys.length">
          <i class="fa fa-flag-o"/>
          Validate Selected ({{selectedKeys.length}})
        </button>
        <button v-bind:disabled="files.length===0" class="btn btn-sm btn-default"
                v-on:click.prevent="validateFiles(files.map(f => f.key))" v-else>
          <i class="fa fa-flag-o"/>
          Validate All
        </button>

        <button v-bind:disabled="files.length===0" class="btn btn-sm btn-default"
                v-on:click.prevent="deleteFiles(selectedKeys)" v-if="selectedKeys.length > 0">
          <i class="fa fa-trash-o"/>
          Delete Selected ({{selectedKeys.length}})
        </button>
        <button v-bind:disabled="files.length===0" class="btn btn-sm btn-default" v-on:click.prevent="deleteAll()"
                v-else>
          <i class="fa fa-trash-o"/>
          Delete All
        </button>

        <button v-bind:disabled="files.length===0" class="btn btn-sm btn-default"
                v-on:click.prevent="showOptions = !showOptions" v-if="selectedKeys.length">
          <i class="fa fa-database"/>
          Ingest Selected... ({{selectedKeys.length}})
        </button>
        <button v-bind:disabled="files.length===0" class="btn btn-sm btn-default" v-on:click.prevent="showOptions = !showOptions"
                v-else>
          <i class="fa fa-database"/>
          Ingest All...
        </button>

        <ingest-options-panel 
          v-if="showOptions"
          v-bind:selected="selectedKeys"
          v-bind:opts="opts"
          v-on:submit="doIngest"
          v-on:close="showOptions = false" />
      </div>

      <div id="ingest-panel-container" class="panel-container">
        <div class="top-panel">
          <files-table
            v-bind:fileStage="fileStage"
            v-bind:loaded="loaded"
            v-bind:files="files"
            v-bind:selected="selected"
            v-bind:previewing="previewing"
            v-bind:validating="validating"
            v-bind:validationResults="validationResults"
            v-bind:truncated="truncated"
            v-bind:deleting="deleting"
            v-bind:ingesting="ingesting"
            v-bind:filter="filter.value"

            v-on:delete-files="deleteFiles"
            v-on:ingest-files="ingestFiles"
            v-on:validate-files="validateFiles"
            v-on:files-loaded="filesLoaded"
            v-on:show-preview="showPreview"
          />
        </div>

        <div id="ingest-status-panel" class="bottom-panel">
          <ul class="status-panel-tabs nav nav-tabs">
            <li class="nav-item">
              <a href="#" class="nav-link" v-bind:class="{'active': tab === 'preview'}"
                 v-on:click.prevent="tab = 'preview'">
                File Preview
                <template v-if="previewing"> - {{previewing}}</template>
              </a>
            </li>
            <li class="nav-item">
              <a href="#" class="nav-link" v-bind:class="{'active': tab === 'validation'}"
                 v-on:click.prevent="tab = 'validation'">
                Validation Log
              </a>
            </li>
            <li class="nav-item">
              <a href="#" class="nav-link" v-bind:class="{'active': tab === 'ingest'}"
                 v-on:click.prevent="tab = 'ingest'">
                Ingest Log
              </a>
            </li>
            <li>
              <drag-handle
                v-bind:ns="fileStage"
                v-bind:p2="$root.$el.querySelector('#ingest-status-panel')"
                v-bind:container="$root.$el.querySelector('#ingest-panel-container')"
                v-on:resize="setPanelSize"
              />
            </li>
          </ul>

          <div class="status-panels">
            <div class="status-panel" v-show="tab === 'preview'">
              <preview v-bind:file-stage="fileStage"
                       v-bind:previewing="previewing"
                       v-bind:errors="validationResults"
                       v-bind:panel-size="panelSize"
                       v-bind:config="config"
                       v-show="previewing !== null"/>
              <div class="panel-placeholder" v-if="previewing === null">
                No file selected.
              </div>
            </div>
            <div class="status-panel log-container" v-show="tab === 'validation'">
              <log-window v-bind:log="validationLog" v-if="validationLog.length > 0"/>
              <div id="validation-placeholder" class="panel-placeholder" v-else>
                Validation log output will show here.
              </div>
            </div>
            <div class="status-panel log-container" v-show="tab === 'ingest'">
              <log-window v-bind:log="log" v-if="log.length > 0"/>
              <div class="panel-placeholder" v-else>
                Ingest log output will show here.
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
});

Vue.component("data-manager", {
  props: {
    config: Object,
  },
  data: function() {
    return {
      stage: 'upload'
    }
  },
  template: `
    <div id="data-manager-container" class="container">
      <ul id="stage-tabs" class="nav nav-tabs">
        <li class="nav-item">
          <a href="#tab-oaipmh" class="nav-link" v-bind:class="{'active': stage === 'oaipmh'}"
             v-on:click.prevent="stage = 'oaipmh'">
            Harvesting
          </a>
        </li>
        <li class="nav-item">
          <a href="#tab-upload" class="nav-link" v-bind:class="{'active': stage === 'upload'}"
             v-on:click.prevent="stage = 'upload'">
            Upload Files
          </a>
        </li>
        <li class="nav-item">
          <a href="#tab-convert" class="nav-link" v-bind:class="{'active': stage === 'convert'}"
             v-on:click.prevent="stage = 'convert'">
            Transform
          </a>
        </li>
        <li class="nav-item">
          <a href="#tab-ingest" class="nav-link" v-bind:class="{'active': stage === 'ingest'}"
                v-on:click.prevent="stage = 'ingest'">
              Ingest
            </a>
        </li>
      </ul>
      <div id="tab-oaipmh" class="stage-tab" v-show="stage === 'upload'">
        <upload-manager v-bind:fileStage="'upload'" v-bind:config="config" />
      </div>
      <div id="tab-upload" class="stage-tab" v-show="stage === 'oaipmh'">
        <oaipmh-manager v-bind:fileStage="'oaipmh'" v-bind:config="config" />
      </div>
      <div id="tab-convert" class="stage-tab" v-show="stage === 'convert'">
        <convert-manager v-bind:fileStage="'ingest'" v-bind:config="config" />
      </div>
      <div id="tab-ingest" class="stage-tab" v-show="stage === 'ingest'">
        <ingest-manager v-bind:fileStage="'ingest'" v-bind:config="config" />
      </div>
    </div>  
  `
});

let app = new Vue({
  el: '#vue-app',
  data: {
    config: CONFIG,
  },
  template: `
    <div id="app-container">
      <data-manager v-bind:config="config" />
    </div>
  `
});

