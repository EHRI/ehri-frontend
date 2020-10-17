"use strict";

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
    <modal-window v-on:close="$emit('close')">
      <template v-slot:title>Ingest Settings</template>

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
      
      <template v-slot:footer>
        <button type="button" class="btn btn-default" data-dismiss="modal" v-on:click="$emit('close')">
          Cancel
        </button>
        <button v-bind:disabled="!isValidConfig" type="button" class="btn btn-secondary" data-dismiss="modal"
                v-on:click="$emit('submit')">
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
          if (this.log && _.startsWith(_.last(this.log), "Ingesting...") && _.startsWith(msg.data.msg, "Ingesting...")) {
            this.log.splice(this.log.length - 1, 1, msg.data.msg);
          } else {
            this.log.push(msg.data.msg);
          }
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

      this.api.ingestFiles(this.datasetId, this.fileStage, keys, this.opts.tolerant, this.opts.commit, this.opts.logMsg)
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

      this.api.ingestAll(this.datasetId, this.fileStage, this.opts.tolerant, this.opts.commit, this.opts.logMsg)
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
            v-bind:loading-more="loadingMore"
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
            v-on:load-more="loadMore"
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
              <preview v-bind:dataset-id="datasetId" 
                       v-bind:file-stage="fileStage"
                       v-bind:previewing="previewing"
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

