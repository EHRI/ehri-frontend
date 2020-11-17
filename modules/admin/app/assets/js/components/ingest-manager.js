"use strict";

Vue.component("ingest-options-panel", {
  props: {
    datasetId: String,
    api: DAO,
    config: Object,
    opts: Object,
    waiting: Boolean,
    props: Array,
  },
  data: function() {
    return {
      tolerant: this.opts.tolerant,
      commit: this.opts.commit,
      logMessage: this.opts.logMessage,
      properties: this.opts.properties,
      loading: false,
      error: null,
    }
  },
  methods: {
    submit: function() {
      this.$emit('submit', {
        tolerant: this.tolerant,
        commit: this.commit,
        logMessage: this.logMessage,
        properties: this.properties,
      });
    },
    uploadProperties: function(event) {
      this.loading = true;
      let fileList = event.dataTransfer
        ? event.dataTransfer.files
        : event.target.files;

      if (fileList.length > 0) {
        let file = fileList[0];

        this.api.uploadHandle(
          this.datasetId,
          this.config.config,
          _.pick(file, ['name', 'type', 'size'])
        )
          .then(data => this.api
            .uploadFile(data.presignedUrl, file, () => true)
            .then(() => {
              this.$emit("update");
              this.properties = file.name;
              if (event.target.files) {
                event.target.files = null;
              }
            })
          )
          .catch(e => this.error = "Error uploading properties: " + e)
          .finally(() => this.loading = false);
      }
    },
    deleteProperties: function(file) {
      this.loading = true;
      this.api.deleteFiles(this.datasetId, this.config.config, [file.key])
        .then(() => this.$emit("update"))
        .finally(() => this.loading = false);
    },
    selectPropFile: function(file) {
      this.properties = this.properties === file.key ? null : file.key;
    }
  },
  computed: {
    isValidConfig: function() {
      return this.opts.logMessage && this.opts.logMessage.trim() !== "";
    },
    hasProps: function() {
      return _.size(this.props) > 0;
    }
  },
  template: `
    <modal-window v-on:close="$emit('close')">
      <template v-slot:title>Ingest Settings</template>

      <fieldset v-bind:disabled="loading" class="options-form">
        <div class="form-group form-check">
          <input class="form-check-input" id="opt-tolerant-check" type="checkbox" v-model="tolerant"/>
          <label class="form-check-label" for="opt-tolerant-check">
            Tolerant Mode: do not abort on individual file errors
          </label>
        </div>
        <div class="form-group form-check">
          <input class="form-check-input" id="opt-commit-check" type="checkbox" v-model="commit"/>
          <label class="form-check-label" for="opt-commit-check">
            Commit Ingest: make changes to database
          </label>
        </div>
        <div class="form-group">
          <label class="form-label" for="opt-properties">
            Properties File
            <span class="text-success" title="Upload Properties File" id="opt-new-props">
              <i class="fa fa-plus-circle"></i>
              <label class="sr-only" for="opt-new-props-input">Upload Properties File...</label>
              <input class="opt-new-props-input" 
                     type="file" pattern=".*.properties$" v-on:change.prevent="uploadProperties"/>
            </span>
          </label>
          <div class="ingest-options-properties-container">
            <table v-if="hasProps" class="ingest-options-properties table table-bordered table-sm table-striped">
              <tr v-for="f in props" v-on:click="selectPropFile(f)" v-bind:class="{'active': f.key===properties}">
                <td><i class="fa fa-fw" v-bind:class="{
                  'fa-check': f.key===properties,
                  'text-success': f.key===properties,
                  'fa-minus': f.key!==properties,
                  'text-muted': f.key!==properties,
                }"></i></td>
                <td>{{f.key}}</td>
                <td v-on:click.stop.prevent="deleteProperties(f)"><i class="fa fa-trash-o"></i></td>
              </tr>
            </table>
            <div v-else-if="loading" class="panel-placeholder">
              Loading properties...
            </div>
            <div v-else class="panel-placeholder">
              No custom properties...
              <input class="opt-new-props-input"
                     type="file" pattern=".*.properties$" v-on:change.prevent="uploadProperties"/>
            </div>
          </div>
        </div>
        <div class="form-group">
          <label class="form-label" for="opt-log-message">Log Message</label>
          <input class="form-control form-control-sm" id="opt-log-message" v-model="logMessage"/>
        </div>
        <div v-if="error" class="alert alert-danger">
          {{error}}
        </div>
      </fieldset>
      
      <template v-slot:footer>
        <button type="button" class="btn btn-default" data-dismiss="modal" v-on:click="$emit('close')">
          Cancel
        </button>
        <button v-bind:disabled="!isValidConfig" type="button" class="btn btn-secondary" data-dismiss="modal"
                v-on:click="submit">
          <i v-if="!waiting" class="fa fa-fw fa-database"></i>
          <i v-else class="fa fa-fw fa-spin fa-circle-o-notch"></i>
          Run Ingest
        </button>
      </template>
    </modal-window>
  `
});

Vue.component("ingest-manager", {
  mixins: [stageMixin, twoPanelMixin, previewMixin, validatorMixin, errorMixin, utilMixin],
  props: {
    datasetId: String,
    fileStage: String,
    config: Object,
    api: DAO,
  },
  data: function () {
    return {
      waiting: false,
      ingestJobId: null,
      showOptions: false,
      propertyConfigs: [],
      opts: {
        commit: false,
        tolerant: false,
        logMessage: LOG_MESSAGE,
        properties: null,
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
          if (this.log && _.startsWith(_.last(this.log), "Ingesting...") && _.startsWith(msg.data.msg, "Ingesting...")) {
            this.log.splice(this.log.length - 1, 1, msg.data.msg);
          } else {
            this.log.push(msg.data.msg);
          }
        }
        if (msg.data.done || msg.data.error) {
          worker.terminate();

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
    doIngest: function(opts) {
      this.opts = opts;

      this.waiting = true;

      let keys = this.selectedKeys.length > 0
                  ? this.selectedKeys
                  : this.files.map(f => f.key);

      let op = this.selectedKeys.length > 0
        ? this.api.ingestFiles(this.datasetId, this.fileStage, keys, this.opts)
        : this.api.ingestAll(this.datasetId, this.fileStage, this.opts);

      op.then(data => {
        if (data.url && data.jobId) {
          // Switch to ingest tab...
          this.tab = "ingest";
          // Clear existing log...
          this.log.length = 0;
          this.showOptions = false;

          this.ingestJobId = data.jobId;
          this.monitorIngest(data.url, data.jobId, keys);
        } else {
          console.error("unexpected job data", data);
        }
      }).catch(error => this.showError("Error running ingest", error))
        .finally(() => this.waiting = false);
    },
    loadPropertyConfigs: function() {
      this.loading = true;
      this.api.listFiles(this.datasetId, this.config.config)
        .then(data => this.propertyConfigs = data.files.filter(f => f.key.endsWith(".properties")))
        .catch(e => this.showError("Error loading files", e))
        .finally(() => this.loading = false);
    },
  },
  created() {
    this.resumeMonitor();
    this.loadPropertyConfigs();
  },
  template: `
    <div id="ingest-manager-container" class="stage-manager-container">
      <div class="actions-bar">
        <filter-control 
          v-bind:filter="filter"
          v-on:filter="filterFiles" 
          v-on:clear="clearFilter"
          v-on:refresh="load" />
        
        <button v-bind:disabled="files.length===0 || validationRunning" class="btn btn-sm btn-default"
                v-on:click.prevent="validateFiles(selectedTags)" v-if="selectedKeys.length">
          <i class="fa fa-fw" v-bind:class="{'fa-flag-o': !validationRunning, 'fa-circle-o-notch fa-spin': validationRunning}"/>
          Validate Selected ({{selectedKeys.length}})
        </button>
        <button v-bind:disabled="files.length===0 || validationRunning" class="btn btn-sm btn-default"
                v-on:click.prevent="validateAll" v-else>
          <i class="fa fa-fw" v-bind:class="{'fa-flag-o': !validationRunning, 'fa-circle-o-notch fa-spin': validationRunning}"/>
          Validate All
        </button>

        <button v-bind:disabled="files.length === 0 || ingestJobId" class="btn btn-sm btn-default"
                v-on:click.prevent="deleteFiles(selectedKeys)" v-if="selectedKeys.length > 0">
          <i class="fa fa-trash-o"/>
          Delete Selected ({{selectedKeys.length}})
        </button>
        <button v-bind:disabled="files.length === 0 || ingestJobId" class="btn btn-sm btn-default" v-on:click.prevent="deleteAll()"
                v-else>
          <i class="fa fa-trash-o"/>
          Delete All
        </button>

        <button v-bind:disabled="files.length === 0 || ingestJobId" class="btn btn-sm btn-default"
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
          v-bind:waiting="waiting"
          v-bind:props="propertyConfigs"
          v-bind:opts="opts"
          v-bind:api="api"
          v-bind:config="config"
          v-bind:dataset-id="datasetId"
          v-on:submit="doIngest"
          v-on:update="loadPropertyConfigs"
          v-on:close="showOptions = false" />
        
        <info-modal v-if="fileInfo !== null" v-bind:file-info="fileInfo" v-on:close="fileInfo = null"/>
      </div>

      <div id="ingest-panel-container" class="panel-container">
        <div class="top-panel">
          <files-table
            v-bind:api="api"
            v-bind:fileStage="fileStage"
            v-bind:loaded="loaded"
            v-bind:loading-more="loadingMore"
            v-bind:files="files"
            v-bind:selected="selected"
            v-bind:previewing="previewing"
            v-bind:validating="validating"
            v-bind:validationResults="validationResults"
            v-bind:truncated="truncated"
            v-bind:deleting="deleting"
            v-bind:downloading="downloading"
            v-bind:loading-info="loadingInfo"
            v-bind:filter="filter.value"

            v-on:delete-files="deleteFiles"
            v-on:download-files="downloadFiles"
            v-on:validate-files="validateFiles"
            v-on:load-more="loadMore"
            v-on:show-preview="showPreview"
            v-on:item-selected="selectItem"
            v-on:item-deselected="deselectItem"
            v-on:deselect-all="deselect"
            v-on:info="info"
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
              <preview v-bind:dataset-id="datasetId" 
                       v-bind:file-stage="fileStage"
                       v-bind:previewing="previewing"
                       v-bind:panel-size="panelSize"
                       v-bind:config="config"
                       v-bind:api="api"
                       v-bind:validation-results="validationResults"
                       v-on:validation-results="(tag, e) => this.$set(this.validationResults, tag, e)"
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

