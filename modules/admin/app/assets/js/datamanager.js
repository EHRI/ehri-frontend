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
  props: {
    active: Boolean,
  },
  data: function() {
    return {
      loaded: false,
      truncated: false,
      tab: 'preview',
      previewing: null,
      deleting: {},
      downloading: {},
      selected: {},
      filter : {
        value: "",
        active: false
      },
      files: [],
      log: [],
    }
  },
  computed: {
    selectedKeys: function () {
      return Object.keys(this.selected);
    },
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
      return this.api.listFiles(this.fileStage, this.filter.value)
        .then(data => {
          this.files = data.files;
          this.truncated = data.truncated;
        })
        .catch(error => this.showError("Error listing files", error))
        .finally(() => this.loaded = true);
    },
    filesLoaded: function (truncated) {
      this.truncated = truncated;
    },
    downloadFiles: function(keys) {
      keys.forEach(key => this.$set(this.downloading, key, true));
      this.api.fileUrls(this.fileStage, keys)
        .then(urls => {
          _.forIn(urls, (url, fileName) => {
            window.open(url, '_blank');
            this.$delete(this.downloading, fileName);
          });
        })
        .catch(error => this.showError("Error fetching download URLs", error))
        .finally(() => this.downloading = {});
    },
    deleteFiles: function (keys) {
      if (keys.includes(this.previewing)) {
        this.previewing = null;
      }
      keys.forEach(key => this.$set(this.deleting, key, true));
      this.api.deleteFiles(this.fileStage, keys)
        .then(deleted => {
          deleted.forEach(key => {
            this.$delete(this.deleting, key);
            this.$delete(this.selected, key);
          });
          this.load();
        })
        .catch(error => this.showError("Error deleting files", error))
        .finally(() => this.deleting = {});
    },
    deleteAll: function () {
      this.previewing = null;
      this.files.forEach(f => this.$set(this.deleting, f.key, true));
      return this.api.deleteAll(this.fileStage)
        .then(r => {
          this.load();
          r;
        })
        .catch(error => this.showError("Error deleting files", error))
        .finally(() => this.deleting = {});
    },
    showError: function() {}, // Overridden by inheritors
    selectNext: function() {
      console.log("No implemented yet...")
    },
    selectPrev: function() {
      console.log("No implemented yet...")
    },
    deselect: function() {
      this.previewing = null;
    }
  },
  watch: {
    active: function(newValue) {
      if (newValue) {
        this.load();
      }
    }
  },
  created: function() {
    this.load();
  },
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
    api: Object,
    fileStage: String,
    dropping: Boolean,
    loaded: Boolean,
    previewing: Object,
    validating: Object,
    validationResults: Object,
    files: Array,
    selected: Object,
    truncated: Boolean,
    deleting: Object,
    downloading: Object,
    ingesting: Object,
    filter: String,
  },
  data: function () {
    return {
      loadingMore: false,
    }
  },
  computed: {
    allChecked: function () {
      return Object.keys(this.selected).length === this.files.length;
    },
    utilRows: function() {
      return Number(this.deleted !== null) +
        Number(this.validating !== null) +
        Number(this.deleting !== null) +
        Number(this.downloading !== null);
    }
  },
  methods: {
    fetchMore: function () {
      this.loadingMore = true;
      let from = this.files.length > 0
        ? this.files[this.files.length - 1].key : null;
      return this.api.listFiles(this.fileStage, this.filter, from)
        .then(data => {
          this.files.push.apply(this.files, data.files);
          this.$emit("files-loaded", data.truncated);
        })
        .catch(error => this.showError("Error fetching files", error))
        .finally(() => this.loadingMore = false);
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
    },
    isPreviewing: function(file) {
      return this.previewing !== null && this.previewing.key === file.key;
    }
  },
  watch: {
    selected: function (newValue) {
      let selected = Object.keys(newValue).length;
      this.$el.querySelector("#" + this.fileStage + "-checkall").indeterminate =
        selected > 0 && selected !== this.files.length;
    },
  },
  template: `
    <div v-bind:class="{'loading': !loaded, 'dropping': dropping}"
         v-on:keyup.down="$emit('select-next')"
         v-on:keyup.up="$emit('select-prev')"
         v-on:click.stop="$emit('deselect-all')" 
         class="file-list-container">
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
            v-on:click.stop="$emit('show-preview', file)"
            v-bind:class="{'active': isPreviewing(file)}">
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
          <td v-if="downloading !== null">
            <a href="#" title="" v-on:click.prevent.stop="$emit('download-files', [file.key])">
              <i class="fa fa-fw" v-bind:class="{
                'fa-circle-o-notch fa-spin': downloading[file.key],
                'fa-download': !downloading[file.key]
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
  mixins: [stageMixin, twoPanelMixin, previewMixin, validatorMixin, errorMixin, utilMixin],
  props: {
    fileStage: String,
    config: Object,
    api: Object,
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

      return this.api.uploadHandle(this.fileStage, {
        name: file.name,
        type: file.type,
        size: file.size
      })
      .then(data => {
        let self = this;
        this.setUploadProgress(file, 0);
        return this.api.uploadFile(data.presignedUrl, file, function (evt) {
          return evt.lengthComputable
            ? self.setUploadProgress(file, Math.round((evt.loaded / evt.total) * 100))
            : true;
        })
        .then(() => {
          this.finishUpload(file);
          this.log.push("Uploaded file: " + file.name);
          return this.load();
        });
      })
      .catch(error => this.showError("Upload error", error));
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
            v-bind:api="api"
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
            v-bind:downloading="downloading"
            v-bind:ingesting="null"
            v-bind:filter="filter.value"

            v-on:delete-files="deleteFiles"
            v-on:download-files="downloadFiles"
            v-on:validate-files="validateFiles"
            v-on:files-loaded="filesLoaded"
            v-on:show-preview="showPreview"
            v-on:select-next="selectNext"
            v-on:select-prev="selectPrev"
            v-on:deselect-all="deselect"
          />
        </div>

        <div id="upload-status-panel" class="bottom-panel">
          <ul class="status-panel-tabs nav nav-tabs">
            <li class="nav-item">
              <a href="#" class="nav-link" v-bind:class="{'active': tab === 'preview'}"
                 v-on:click.prevent="tab = 'preview'">
                File Preview
                <template v-if="previewing"> - {{previewing.key}}</template>
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
                       v-bind:api="api"
                       v-on:error="showError"
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
    config: Object,
    api: Object,
  },
  data: function() {
    return {
      url: this.config ? this.config.url : null,
      format: this.config ? this.config.format : null,
      set: this.config ? this.config.set : null,
      tested: null,
      testing: false,
      error: null,
      noResume: false,
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
  methods: {
    save: function() {
      this.api.saveConfig({url: this.url, format: this.format, set: this.set})
        .then(data => this.$emit("saved-config", data, !this.noResume))
        .catch(error => this.$emit("error", "Error saving OAI-PMH config", error));
    },
    testEndpoint: function() {
      this.testing = true;
      this.api.testConfig({url: this.url, format: this.format, set: this.set})
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
        })
        .finally(() => this.testing = false);
    }
  },
  watch: {
    config: function(newValue) {
      this.url = newValue ? newValue.url : null;
      this.format = newValue ? newValue.format : null;
      this.set = newValue ? newValue.set : null;
    }
  },
  template: `
    <div class="options-dialog modal show fade" tabindex="-1" role="dialog"
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
                <input class="form-control" id="opt-endpoint-url" type="url" v-model.trim="url" placeholder="(required)"/>
              </div>
              <div class="form-group">
                <label class="form-label" for="opt-format">
                  OAI-PMH metadata format
                </label>
                <input class="form-control" id="opt-format" type="text" v-model.trim="format" placeholder="(required)"/>
              </div>
              <div class="form-group">
                <label class="form-label" for="opt-set">
                  OAI-PMH set
                </label>
                <input class="form-control" id="opt-set" type="text" v-model.trim="set"/>
              </div>
              <div class="form-group">
                <div class="form-check">
                  <input type="checkbox" class="form-check-input" id="opt-no-resume" v-model="noResume"/>
                  <label class="form-check-label" for="opt-no-resume">
                    <strong>Do not</strong> resume from last harvest timestamp
                  </label>
                </div>
              </div>
              <div id="endpoint-errors">
                <span v-if="tested === null">&nbsp;</span>
                <span v-else-if="tested" class="text-success">No errors detected</span>
                <span v-else-if="error" class="text-danger">{{error}}</span>
                <span v-else class="text-danger">Test unsuccessful</span>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button v-on:click="$emit('close')" type="button" class="btn btn-default">
              Cancel
            </button>
            <button v-bind:disabled="!isValidConfig" 
                    v-on:click="testEndpoint" type="button" class="btn btn-default">
              <i v-if="testing" class="fa fa-fw fa-circle-o-notch fa-spin"></i>
              <i v-else-if="tested === null" class="fa fa-fw fa-question"/>
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
  mixins: [stageMixin, twoPanelMixin, previewMixin, validatorMixin, errorMixin, utilMixin],
  props: {
    fileStage: String,
    config: Object,
    api: Object,
  },
  data: function () {
    return {
      harvestJobId: null,
      showOptions: false,
      harvestConfig: null,
      fromLast: true,
    }
  },
  methods: {
    harvest: function() {
      this.api.harvest(this.harvestConfig, this.fromLast)
        .then(data => {
          this.harvestJobId = data.jobId;
          this.monitorHarvest(data.url, data.jobId);
        });
    },
    cancelHarvest: function() {
      if (this.harvestJobId) {
        this.api.cancelHarvest(this.harvestJobId).then(r => {
          if (r.ok) {
            this.harvestJobId = null;
          }
        });
      }
    },
    monitorHarvest: function (url, jobId) {
      this.tab = 'harvest';

      let worker = new Worker(this.config.previewLoader);
      worker.onmessage = msg => {
        if (msg.data.error) {
          this.log.push(msg.data.error);
        } else if (msg.data.msg) {
          this.log.push(msg.data.msg);
          this.refresh();
        }
        if (msg.data.done || msg.data.error) {
          worker.terminate();

          this.harvestJobId = null;
          this.removeUrlState('harvest-job-id');
        }
      };
      worker.postMessage({type: 'websocket', url: url, DONE: DONE_MSG, ERR: ERR_MSG});
      this.replaceUrlState('harvest-job-id', jobId);
    },
    resumeMonitor: function() {
      let jobId = this.getQueryParam(window.location.search, "harvest-job-id");
      if (jobId) {
        this.harvestJobId = jobId;
        this.monitorHarvest(this.config.monitorUrl(jobId), jobId);
      }
    },
    saveConfigAndHarvest: function(config, fromLast) {
      this.harvestConfig = config;
      this.fromLast = fromLast;
      this.showOptions = false;
      this.harvest();
    },
    loadConfig: function() {
      this.api.getConfig()
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

        <button v-if="!harvestJobId" class="btn btn-sm btn-default"
                v-on:click.prevent="showOptions = !showOptions">
          <i class="fa fa-fw fa-cloud-download"/>
          Harvest Files...
        </button>
        <button v-else class="btn btn-sm btn-outline-danger" v-on:click.prevent="cancelHarvest">
          <i class="fa fa-fw fa-spin fa-circle-o-notch"></i>
          Cancel Harvest
        </button>
        
        <oaipmh-config-modal 
          v-if="showOptions"
          v-bind:config="harvestConfig"
          v-bind:api="api"
          v-on:saved-config="saveConfigAndHarvest"
          v-on:error="showError"
          v-on:close="showOptions = false"/>
      </div>

      <div id="oaipmh-panel-container" class="panel-container">
        <div class="top-panel">
          <files-table
            v-bind:api="api"
            v-bind:fileStage="fileStage"
            v-bind:loaded="loaded"
            v-bind:files="files"
            v-bind:selected="selected"
            v-bind:previewing="previewing"
            v-bind:validating="validating"
            v-bind:validationResults="validationResults"
            v-bind:truncated="truncated"
            v-bind:deleting="deleting"
            v-bind:downloading="downloading"
            v-bind:ingesting="null"
            v-bind:filter="filter.value"

            v-on:delete-files="deleteFiles"
            v-on:download-files="downloadFiles"
            v-on:validate-files="validateFiles"
            v-on:files-loaded="filesLoaded"
            v-on:show-preview="showPreview"
            v-on:deselect-all="deselect"
          />
        </div>

        <div id="oaipmh-status-panels" class="bottom-panel">
          <ul class="status-panel-tabs nav nav-tabs">
            <li class="nav-item">
              <a href="#" class="nav-link" v-bind:class="{'active': tab === 'preview'}"
                 v-on:click.prevent="tab = 'preview'">
                File Preview
                <template v-if="previewing"> - {{previewing.key}}</template>
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
                       v-bind:api="api"
                       v-on:error="showError"
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
  computed: {
    isValidConfig: function() {
      return this.sources;
    },
  },
  methods: {
    convert: function() {
      this.$emit("convert", this.sources);
      this.$emit("close");
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
  mixins: [twoPanelMixin, previewMixin, validatorMixin, errorMixin, utilMixin],
  props: {
    fileStage: String,
    config: Object,
    api: Object,
    active: Boolean,
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

      return this.api.listDataTransformations()
        .then(available => {
          let each = _.partition(available, item => !_.includes(this.mappings, item.id));
          this.available = each[0];
          this.enabled = this.mappings.map(id => _.find(each[1], a => a.id === id));
        })
        .catch(error => this.showError("Unable to load transformations", error))
        .finally(() => this.loading = false);
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
      this.api.convert({mappings: this.mappings, src: sources})
        .then(data => {
          this.convertJobId = data.jobId;
          this.monitorConvert(data.url, data.jobId);
        })
        .catch(error => this.showError("Error submitting conversion", error));
    },
    cancelConvert: function() {
      if (this.convertJobId) {
        this.api.cancelConvert(this.convertJobId).then(r => {
          if (r.ok) {
            this.convertJobId = null;
          }
        });
      }
    },
    monitorConvert: function (url, jobId) {
      this.tab = 'convert';

      let worker = new Worker(this.config.previewLoader);
      worker.onmessage = msg => {
        if (msg.data.error) {
          this.log.push(msg.data.error);
        } else if (msg.data.msg) {
          this.log.push(msg.data.msg);
          this.$emit('refresh-stage', this.config.ingest);
        }
        if (msg.data.done || msg.data.error) {
          worker.terminate();

          this.convertJobId = null;
          this.removeUrlState('convert-job-id');
        }
      };
      worker.postMessage({type: 'websocket', url: url, DONE: DONE_MSG, ERR: ERR_MSG});
      this.replaceUrlState('convert-job-id', jobId);
    },
    resumeMonitor: function() {
      let jobId = this.getQueryParam(window.location.search, "convert-job-id");
      if (jobId) {
        this.convertJobId = jobId;
        this.monitorConvert(this.config.monitorUrl(jobId), jobId);
      }
    },
    loadConfig: function() {
      return this.api.getConvertConfig()
        .then(data => this.mappings = data.map(item => item.id))
        .catch(error => this.showError("Error loading convert configuration", error));
    },
    saveConfig: function() {
      let mappings = this.enabled.map(item => item.id);
      if (!_.isEqual(mappings, this.mappings)) {
        console.log("saving enabled:", this.enabled)
        this.mappings = mappings;
        this.api.saveConvertConfig(this.mappings)
          .catch(error => this.showError("Failed to save mapping list", error));
      }
    },
    loadPreviewList: function() {
      this.loading = true;
      this.api.listFiles("upload", "")
        .then(data => this.previewList = data.files)
        .catch(error => this.showError("Unable to list preview files", error))
        .finally(() => this.loading = false);
    },
  },
  watch: {
    enabled: function() {
      if (!this.loading) {
        this.saveConfig();
      }
    },
    active: function(value) {
      if (value) {
        this.loadPreviewList();
      }
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

      <transformation-editor
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
        v-bind:api="api"
        v-on:saved="saved"
        v-on:close="closeEditForm"/>


      <div class="actions-bar">
<!--        <select id="preview-file-selector" v-model="previewing" v-bind:disabled="convertJobId !== null" class="btn btn-sm btn-default">-->
<!--          <option v-bind:value="null">Select file to preview...</option>-->
<!--          <option v-for="file in previewList" v-bind:value="file">{{file.key}}</option>-->
<!--        </select>-->
        <file-picker v-bind:disabled="convertJobId !== null"
                     v-bind:type="fileStage"
                     v-bind:api="api"
                     v-model="previewing" />
        
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
              <div class="transformation-list-placeholder" v-else-if="available.length === 0">
                <p>Drag transformations into this area to deactivate them.</p>
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
                <template v-if="previewing"> - {{previewing.key}}</template>
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
                       v-bind:api="api"
                       v-on:error="showError"
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
            <h5 class="modal-title">Ingest Settings</h5>
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
            <button v-bind:disabled="!isValidConfig" type="button" class="btn btn-secondary" data-dismiss="modal" 
                    v-on:click="$emit('submit')">
              Run Ingest
            </button>
          </div>
        </div>
      </div>
    </div>
  `
});

Vue.component("ingest-manager", {
  mixins: [stageMixin, twoPanelMixin, previewMixin, validatorMixin, errorMixin, utilMixin],
  props: {
    fileStage: String,
    config: Object,
    api: Object,
  },
  data: function () {
    return {
      ingesting: {},
      ingestJobId: null,
      showOptions: false,
      opts: {
        commit: false,
        tolerant: false,
        logMsg: LOG_MESSAGE
      }
    }
  },
  methods: {
    monitorIngest: function (url, jobId, keys) {
      this.tab = 'ingest';

      let worker = new Worker(this.config.previewLoader);
      worker.onmessage = msg => {
        if (msg.data.error) {
          this.log.push(msg.data.error);
        } else if (msg.data.msg) {
          this.log.push(msg.data.msg);
        }
        if (msg.data.done || msg.data.error) {
          worker.terminate();
          keys.forEach(key => this.$delete(this.ingesting, key));

          this.ingestJobId = null;
          this.removeUrlState('ingest-job-id');
        }
      };
      worker.postMessage({type: 'websocket', url: url, DONE: DONE_MSG, ERR: ERR_MSG});
      this.replaceUrlState('ingest-job-id', jobId);
    },
    resumeMonitor: function() {
      let jobId = this.getQueryParam(window.location.search, "ingest-job-id");
      if (jobId) {
        this.ingestJobId = jobId;
        this.monitorIngest(this.config.monitorUrl(jobId), jobId, this.files.map(f => f.key));
      }
    },
    ingestFiles: function (keys) {
      // Switch to ingest tab...
      this.tab = "ingest";

      // Clear existing log...
      this.log.length = 0;

      // Set key status to ingesting.
      keys.forEach(key => this.$set(this.ingesting, key, true));

      this.api.ingestFiles(this.fileStage, keys, this.opts.tolerant, this.opts.commit, this.opts.logMsg)
        .then(data => {
          if (data.url && data.jobId) {
            this.ingestJobId = data.jobId;
            this.monitorIngest(data.url, data.jobId, keys);
          } else {
            console.error("unexpected job data", data);
          }
        })
        .catch(error => this.showError("Error running ingest", error));
    },
    ingestAll: function () {
      // Switch to ingest tab...
      this.tab = "ingest";

      // Clear existing log...
      this.log.length = 0;

      let keys = this.files.map(f => f.key);

      // Set key status to ingesting.
      keys.forEach(key => this.$set(this.ingesting, key, true));

      this.api.ingestAll(this.fileStage, this.opts.tolerant, this.opts.commit, this.opts.logMsg).then(data => {
        if (data.url && data.jobId) {
          this.ingestJobId = data.jobId;
          this.monitorIngest(data.url, data.jobId, keys);
        } else {
          console.error("unexpected job data", data);
        }
      }).catch(error => this.showError("Error running ingest", error));
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
  created() {
    this.resumeMonitor();
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

        <button v-bind:disabled="files.length===0 || ingestJobId" class="btn btn-sm btn-default"
                v-on:click.prevent="showOptions = !showOptions" v-if="selectedKeys.length">
          <i v-if="!ingestJobId" class="fa fa-fw fa-database"/>
          <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
          Ingest Selected... ({{selectedKeys.length}})
        </button>
        <button v-bind:disabled="files.length===0 || ingestJobId" class="btn btn-sm btn-default" v-on:click.prevent="showOptions = !showOptions"
                v-else>
          <i v-if="!ingestJobId" class="fa fa-fw fa-database"/>
          <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
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
            v-bind:api="api"
            v-bind:fileStage="fileStage"
            v-bind:loaded="loaded"
            v-bind:files="files"
            v-bind:selected="selected"
            v-bind:previewing="previewing"
            v-bind:validating="validating"
            v-bind:validationResults="validationResults"
            v-bind:truncated="truncated"
            v-bind:deleting="deleting"
            v-bind:downloading="downloading"
            v-bind:ingesting="null"
            v-bind:filter="filter.value"

            v-on:delete-files="deleteFiles"
            v-on:download-files="downloadFiles"
            v-on:ingest-files="ingestFiles"
            v-on:validate-files="validateFiles"
            v-on:files-loaded="filesLoaded"
            v-on:show-preview="showPreview"
            v-on:deselect-all="deselect"
          />
        </div>

        <div id="ingest-status-panel" class="bottom-panel">
          <ul class="status-panel-tabs nav nav-tabs">
            <li class="nav-item">
              <a href="#" class="nav-link" v-bind:class="{'active': tab === 'preview'}"
                 v-on:click.prevent="tab = 'preview'">
                File Preview
                <template v-if="previewing"> - {{previewing.key}}</template>
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
                       v-bind:api="api"
                       v-on:error="showError"
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
  mixins: [utilMixin],
  props: {
    config: Object,
    api: Object,
  },
  data: function() {
    return {
      tab: this.config.defaultTab,
      error: null,
    }
  },
  methods: {
    setError: function(err, exc) {
      this.error = err + ": " + exc.message;
    },
    switchTab: function(tab) {
      this.tab = tab;
      history.pushState(
        _.merge(this.queryParams(window.location.search), {'tab': tab}),
        document.title,
        this.setQueryParam(window.location.search, 'tab', tab));
    }
  },
  created() {
    window.onpopstate = event => {
      if (event.state && event.state.tab) {
        this.tab = event.state.tab;
      } else {
        this.tab = this.config.defaultTab;
      }
    }
    let qsTab = this.getQueryParam(window.location.search, "tab");
    if (qsTab) {
      this.tab = qsTab;
    }
  },
  template: `
    <div id="data-manager-container" class="container">
      <div v-if="error" id="app-error-notice" class="alert alert-danger alert-dismissable">
        <span class="close" v-on:click="error = null">&times;</span>
        {{error}}
      </div>
      <ul id="stage-tabs" class="nav nav-tabs">
        <li class="nav-item">
          <a href="#tab-oaipmh" class="nav-link" v-bind:class="{'active': tab === 'oaipmh'}"
             v-on:click.prevent="switchTab('oaipmh')">
            Harvesting
          </a>
        </li>
        <li class="nav-item">
          <a href="#tab-upload" class="nav-link" v-bind:class="{'active': tab === 'upload'}"
             v-on:click.prevent="switchTab('upload')">
            Uploads
          </a>
        </li>
        <li class="nav-item">
          <a href="#tab-convert" class="nav-link" v-bind:class="{'active': tab === 'convert'}"
             v-on:click.prevent="switchTab('convert')">
            Transform
          </a>
        </li>
        <li class="nav-item">
          <a href="#tab-ingest" class="nav-link" v-bind:class="{'active': tab === 'ingest'}"
                v-on:click.prevent="switchTab('ingest')">
              Ingest
            </a>
        </li>
      </ul>
      <div id="tab-oaipmh" class="stage-tab" v-show="tab === 'upload'">
        <upload-manager 
          v-bind:fileStage="config.upload" 
          v-bind:config="config" 
          v-bind:active="tab === config.upload"
          v-bind:api="api"
          v-on:error="setError"  />
      </div>
      <div id="tab-upload" class="stage-tab" v-show="tab === 'oaipmh'">
        <oaipmh-manager 
          v-bind:fileStage="config.oaipmh" 
          v-bind:config="config" 
          v-bind:active="tab === config.oaipmh"
          v-bind:api="api"
          v-on:error="setError"  />
      </div>
      <div id="tab-convert" class="stage-tab" v-show="tab === 'convert'">
        <convert-manager 
          v-bind:fileStage="config.ingest" 
          v-bind:config="config" 
          v-bind:active="tab === 'convert'"
          v-bind:api="api"
          v-on:error="setError" />
      </div>
      <div id="tab-ingest" class="stage-tab" v-show="tab === 'ingest'">
        <ingest-manager 
          v-bind:fileStage="config.ingest" 
          v-bind:config="config" 
          v-bind:active="tab === config.ingest"
          v-bind:api="api"
          v-on:error="setError"  />
      </div>
    </div>  
  `
});

let app = new Vue({
  el: '#vue-app',
  data: {
    config: CONFIG,
    api: DAO,
  },
  template: `
    <div id="app-container">
      <data-manager v-bind:config="config" v-bind:api="api" />
    </div>
  `
});

