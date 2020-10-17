"use strict";

Vue.component("transformation-item", {
  props: {
    item: Object,
    enabled: Boolean,
  },
  template: `
    <div v-bind:class="{'enabled': enabled}"
         v-on:dblclick="$emit('edit')"
         class="list-group-item transformation-item list-group-item-action">
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

Vue.component("convert-config", {
  props: {
    show: Boolean,
    config: Object,
  },
  methods: {
    convert: function() {
      this.$emit("convert");
      this.$emit("close");
    },
  },
  template: `
    <modal-window v-on:close="$emit('close')">
      <template v-slot:title>Transformation Configuration</template>

      <p>TODO: options here...</p>
      
      <template v-slot:footer>
        <button v-on:click="convert" type="button" class="btn btn-secondary">
          Run Conversion
        </button>
      </template>
    </modal-window>
  `
});

let initialConvertState = function(config) {
  return {
    convertJobId: null,
    ingesting: {},
    previewStage: config.input,
    previewing: null,
    tab: 'preview',
    log: [],
    showOptions: false,
    available: [],
    enabled: [],
    mappings: [], // IDs of enabled transformations
    editing: null,
    loading: false,
  };
};

Vue.component("convert-manager", {
  mixins: [twoPanelMixin, previewMixin, validatorMixin, errorMixin, utilMixin],
  props: {
    datasetId: String,
    fileStage: String,
    config: Object,
    api: DAO,
    active: Boolean,
  },
  data: function () {
    return initialConvertState(this.config);
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
    convert: function() {
      this.api.convert(this.datasetId, {mappings: this.mappings})
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
          this.$emit('refresh-stage', this.config.output);
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
      return this.api.getConvertConfig(this.datasetId)
        .then(data => this.mappings = data.map(item => item.id))
        .catch(error => this.showError("Error loading convert configuration", error));
    },
    saveConfig: function() {
      let mappings = this.enabled.map(item => item.id);
      if (!_.isEqual(mappings, this.mappings)) {
        console.log("saving enabled:", this.enabled)
        this.mappings = mappings;
        this.api.saveConvertConfig(this.datasetId, this.mappings)
          .catch(error => this.showError("Failed to save mapping list", error));
      }
    },
  },
  watch: {
    enabled: function() {
      if (!this.loading) {
        this.saveConfig();
      }
    },
    datasetId: function() {
      Object.assign(this.$data, initialConvertState(this.config));
      this.loadConfig().then(_ => {
        this.loadTransformations();
      });
    }
  },
  created: function () {
    this.loadConfig().then(_ => {
      this.loadTransformations();
    });
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
        v-bind:dataset-id="datasetId"
        v-bind:file-stage="previewStage"
        v-bind:init-previewing="previewing"
        v-bind:config="config"
        v-bind:api="api"
        v-on:saved="saved"
        v-on:close="closeEditForm"/>

      <div class="actions-bar">
        <file-picker v-bind:disabled="convertJobId !== null"
                     v-bind:dataset-id="datasetId"
                     v-bind:file-stage="config.input"
                     v-bind:api="api"
                     v-bind:config="config"
                     v-model="previewing" />
        
        <button class="btn btn-sm btn-default" v-on:click.prevent="newTransformation">
          <i class="fa fa-file-o"></i>
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
            v-bind:config="config"
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
                       v-bind:dataset-id="datasetId"
                       v-bind:file-stage="previewStage"
                       v-bind:mappings="mappings"
                       v-bind:trigger="JSON.stringify({
                         mappings: mappings, 
                         previewing: previewing
                       })"
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

