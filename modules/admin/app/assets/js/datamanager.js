"use strict";

// Prevent default drag/drop action...
window.addEventListener("dragover", e => e.preventDefault(), false);
window.addEventListener("drop", e => e.preventDefault(), false);





Vue.component("dataset-form", {
  props: {
    api: DAO,
    info: Object,
  },
  data: function() {
    return {
      id: this.info ? this.info.id : null,
      name: this.info ? this.info.name : null,
      src: this.info ? this.info.src : null,
      notes: this.info ? this.info.notes : null,
      error: null,
      saving: false,
      deleting: false,
      showRemoveDialog: false,
    }
  },
  methods: {
    save: function() {
      this.saving = true;

      let data = {
        id: this.id,
        name: this.name,
        src: this.src,
        notes: this.notes,
      };

      let op = this.info !== null
        ? this.api.updateDataset(this.id, data).then(ds => this.$emit('saved-dataset', ds))
        : this.api.createDataset(data).then(ds => {
            this.$emit('saved-dataset', ds);
            this.$emit('close');
          });

      op.catch(error => {
        if (error.response && error.response.data && error.response.data.error) {
          this.error = error.response.data.error;
        } else {
          throw error;
        }
      })
      .finally(() => this.saving = false);
    },
    remove: function() {
      this.deleting = true;
      this.showRemoveDialog = false;
      this.api.deleteDataset(this.id)
        .then(() => {
            this.$emit('deleted-dataset');
            this.$emit('close');
          }
        )
        .catch(error => this.error = error)
        .finally(() => this.deleting = false);
    },
  },
  computed: {
    isValidConfig: function() {
      return this.src !== null
        && this.name !== null
        && this.id !== null;
    },
    isValidIdentifier: function() {
      return !this.id || (this.id.match(/^[a-z0-9_]+$/) !== null && this.id.length <= 50);
    },
    hasChanged: function() {
      return this.info === null || (
        this.info.name !== this.name
          || this.info.src !== this.src
          || this.info.notes !== this.notes);
    }
  },
  template: `
    <modal-window v-on:close="$emit('close')">
      <template v-if="info === null" v-slot:title>Create New Dataset...</template>
      <template v-else v-slot:title>Update Dataset: {{info.name}}</template>

      <modal-alert v-if="showRemoveDialog"
                   v-on:accept="remove"
                   v-on:close="showRemoveDialog = false"
                   v-bind:title="'Delete Dataset?'"
                   v-bind:accept="'Yes, delete it !'">
        <p>This will remove all files associated with the dataset and
          cannot be undone. Are you sure?</p>
      </modal-alert>
      <modal-alert v-if="error"
                   v-on:accept="error = null"
                   v-on:close="error = null"
                   v-bind:cancel="null"
                   v-bind:title="'Error saving dataset...'"
                   v-bind:cls="'warning'">
        <p>{{error}}</p>
      </modal-alert>
      <fieldset v-bind:disabled="deleting" class="options-form">
        <div class="form-group" v-if="info === null">
          <label class="form-label" for="dataset-id">
            Identifier
            <span class="required-input">*</span>
          </label>
          <input v-model="id"
                 v-bind:class="{'is-invalid': !isValidIdentifier}"
                 pattern="[a-z0-9_]+"
                 maxlength="50"
                 type="text"
                 id="dataset-id"
                 class="form-control"
                 autofocus="autofocus"
                 autocomplete="off" 
                 placeholder="(required)" />
          <div class="small form-text">
            Dataset identifiers must be at least 6 characters in length
            and can only contain lower case letters, numbers and underscores.
          </div>
        </div>
        <div class="form-group">
          <label class="form-label" for="dataset-name">
            Name
            <span class="required-input">*</span>
          </label>
          <input type="text" v-model="name" id="dataset-name" class="form-control" placeholder="(required)"/>
        </div>
        <div class="form-group">
          <label class="form-label" for="dataset-src">
            Type
            <span class="required-input">*</span>
          </label>
          <select v-model="src" class="form-control" id="dataset-src">
            <option v-bind:value="null" disabled selected hidden>(required)</option>
            <option value="upload">Uploads</option>
            <option value="oaipmh">OAI-PMH Harvesting</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label" for="dataset-notes">Notes</label>
          <textarea rows="4" v-model="notes" id="dataset-notes" class="form-control" placeholder="(optional)"/>
        </div>
      </fieldset>
      <template v-slot:footer>
        <button v-if="info"
                v-bind:disabled="saving || deleting"
                v-on:click="showRemoveDialog = true" class="btn btn-danger" id="delete-dataset" tabindex="-1">
          <i v-if="deleting" class="fa fa-fw fa-spin fa-circle-o-notch"></i>
          <i v-else class="fa fa-fw fa-trash-o"></i>
          Delete Dataset
        </button>
        <button v-bind:disabled="saving || deleting || !hasChanged || !(isValidConfig && isValidIdentifier)"
                v-on:click="save" type="button" class="btn btn-secondary">
          <i v-if="saving" class="fa fa-fw fa-spin fa-circle-o-notch"></i>
          <i v-else class="fa fa-fw fa-save"></i>
          <template v-if="info">Save Dataset</template>
          <template v-else>Create Dataset</template>
        </button>
      </template>
    </modal-window>
  `
});

Vue.component("dataset-manager", {
  mixins: [utilMixin],
  props: {
    config: Object,
    api: DAO,
    initTab: String,
  },
  data: function() {
    return {
      loaded: false,
      datasets: [],
      dataset: null,
      tab: this.initTab,
      error: null,
      showForm: false,
      showSelector: false,
      stats: {},
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
    },
    selectDataset: function(ds) {
      if (!ds) {
        this.dataset = null;
        return;
      }
      this.dataset = ds;
      history.pushState(
        _.merge(this.queryParams(window.location.search), {'ds': ds.id}),
        document.title,
        this.setQueryParam(window.location.search, 'ds', ds.id));
    },
    closeDataset: function() {
      this.dataset = null;
      history.pushState(
        _.omit(this.queryParams(window.location.search), 'ds', 'tab'),
        document.title,
        window.location.pathname
          + this.removeQueryParam(window.location.search, ['ds', 'tab']));
    },
    showNewDatasetForm: function () {
      this.showForm = true;
    },
    loadDatasets: function() {
      this.api.datasetStats().then(stats => this.stats = stats);
      return this.api.listDatasets()
        .then(dsl => this.datasets = dsl)
        .catch(e => this.showError("Error loading datasets", e))
        .finally(() => this.loaded = true);
    },
    reloadDatasets: function(ds) {
      this.loadDatasets().then(() => this.selectDataset(ds));
    },
  },
  created() {
    if (!this.config.versioned) {
      this.error = "Note: file storage does not have versioning enabled."
    }
    window.onpopstate = event => {
      if (event.state && event.state.tab) {
        this.tab = event.state.tab;
      } else {
        this.tab = this.initTab;
      }
      if (event.state && event.state.ds) {
        this.dataset = _.find(this.datasets, d => d.id === event.state.ds);
      } else {
        this.dataset = null;
      }
    };

    let qsTab = this.getQueryParam(window.location.search, "tab");
    if (qsTab) {
      this.tab = qsTab;
    }

    this.loadDatasets().then(() => {
      let qsDs = this.getQueryParam(window.location.search, "ds");
      if (qsDs) {
        this.selectDataset(_.find(this.datasets, d => d.id === qsDs));
      }
    });
  },
  template: `
    <div id="data-manager-container" class="container">
      <div v-if="error" id="app-error-notice" class="alert alert-danger alert-dismissable">
        <span class="close" v-on:click="error = null">&times;</span>
        {{error}}
      </div>
      <dataset-form v-if="showForm"
                         v-bind:info="dataset"
                         v-bind:api="api"
                         v-on:close="showForm = false"
                         v-on:saved-dataset="reloadDatasets"
                         v-on:deleted-dataset="reloadDatasets" />
      
      <div v-if="!loaded && dataset === null" class="dataset-loading-indicator">
        <h2>
          <i class="fa fa-lg fa-spin fa-spinner"></i>
          Loading datasets...
        </h2>
      </div>
      <div v-else-if="dataset === null" id="dataset-manager">
        <template v-if="loaded && datasets.length === 0">
          <h2>Create a dataset...</h2>
          <p class="info-message">
            To manage institution data you must create at least one dataset. A dataset is
            a set of files that typically come from the same source and are processed in
            the same way.
          </p>
        </template>
        <template v-else>
          <h2 v-if="datasets">Select dataset:</h2>
          <div class="dataset-manager-list">
            <div v-for="ds in datasets" v-on:click.prevent="selectDataset(ds)" class="dataset-manager-item">
              <div class="badge badge-primary" v-bind:class="'badge-' + ds.src">
                {{ds.src|stageName(config)}}
                <span v-if="ds.id in stats">({{stats[ds.id]}})</span>
              </div>
              <h3>{{ds.name}}</h3>
              <p v-if="ds.notes">{{ds.notes}}</p>
            </div>
          </div>
        </template>
        <button v-on:click.prevent="showNewDatasetForm" class="btn btn-success">
          <i class="fa fa-plus-circle"></i>
          Create a new dataset...
        </button>
      </div>
      <template v-else>
        <ul id="stage-tabs" class="nav nav-tabs">
          <li class="nav-item">
            <a v-if="dataset.src === 'oaipmh'" href="#tab-input" class="nav-link" v-bind:class="{'active': tab === 'input'}"
               v-on:click.prevent="switchTab('input')">
              <i class="fa fw-fw fa-cloud-download"></i>
              Harvest Data
            </a>
            <a v-if="dataset.src === 'upload'" href="#tab-input" class="nav-link" v-bind:class="{'active': tab === 'input'}"
               v-on:click.prevent="switchTab('input')">
              <i class="fa fw-fw fa-upload"></i>
              Uploads
            </a>
          </li>
          <li class="nav-item">
            <a href="#tab-convert" class="nav-link" v-bind:class="{'active': tab === 'convert'}"
               v-on:click.prevent="switchTab('convert')">
              <i class="fa fa-fw fa-file-code-o"></i>
              Transform
            </a>
          </li>
          <li class="nav-item">
            <a href="#tab-ingest" class="nav-link" v-bind:class="{'active': tab === 'ingest'}"
               v-on:click.prevent="switchTab('ingest')">
              <i class="fa fa-fw fa-database"></i>
              Ingest
            </a>
          </li>
          <li class="dataset-menu">
            <div class="dropdown">
              <button class="btn btn-info" v-on:click="showSelector = !showSelector">
                <i class="fa fa-lg fa-caret-down"></i>
                Dataset: {{dataset.name}}
              </button>
              <div v-if="showSelector" class="dropdown-backdrop" v-on:click="showSelector = false">
              </div>
              <div v-if="showSelector" class="dropdown-menu dropdown-menu-right show">
                <a v-on:click.prevent="showSelector = false; showForm = true" class="dropdown-item" href="#">
                  <i class="fa fa-edit"></i>
                  Edit Dataset
                </a>
                <template v-if="datasets.length > 1">
                  <div class="dropdown-divider"></div>
                  <a v-for="ds in datasets" v-on:click.prevent="selectDataset(ds); showSelector = false" href="#" class="dropdown-item">
                    <i class="fa fa-fw" v-bind:class="{'fa-asterisk': ds.id===dataset.id}"></i>
                    {{ ds.name }}
                    <div class="badge badge-pill" v-bind:class="'badge-' + ds.src">
                      <span v-if="ds.id in stats">({{stats[ds.id]}})</span>
                    </div>
                  </a>
                </template>
                <div class="dropdown-divider"></div>
                <a v-on:click.prevent="closeDataset(); showSelector = false" href="#" class="dropdown-item">
                  <i class="fa fa-close"></i>
                  Close
                </a>
              </div>
            </div>
          </li>
        </ul>
        <div id="tab-input" class="stage-tab" v-show="tab === 'input'">
          <oaipmh-manager
            v-if="dataset.src === 'oaipmh'"
            v-bind:dataset-id="dataset.id"
            v-bind:fileStage="config.input"
            v-bind:config="config"
            v-bind:active="tab === 'input'"
            v-bind:api="api"
            v-on:error="setError"  />
          <upload-manager
            v-else
            v-bind:dataset-id="dataset.id"
            v-bind:fileStage="config.input"
            v-bind:config="config"
            v-bind:active="tab === 'input'"
            v-bind:api="api"
            v-on:error="setError"  />
        </div>
        <div id="tab-convert" class="stage-tab" v-show="tab === 'convert'">
          <convert-manager
            v-bind:dataset-id="dataset.id"
            v-bind:fileStage="config.output"
            v-bind:config="config"
            v-bind:active="tab === 'convert'"
            v-bind:api="api"
            v-on:error="setError" />
        </div>
        <div id="tab-ingest" class="stage-tab" v-show="tab === 'ingest'">
          <ingest-manager
            v-bind:dataset-id="dataset.id"
            v-bind:fileStage="config.output"
            v-bind:config="config"
            v-bind:active="tab === 'ingest'"
            v-bind:api="api"
            v-on:error="setError"  />
        </div>
        
      </template>
    </div>  
  `
});

let app = new Vue({
  el: '#vue-app',
  mixins: [utilMixin],
  data: {
    config: CONFIG,
    api: new DAO(SERVICE, CONFIG.repositoryId, null),
    tab: 'input',
  },
  template: `
    <div id="app-container">
      <dataset-manager
                    v-bind:config="config" 
                    v-bind:init-tab="tab"
                    v-bind:api="api" />
    </div>
  `
});

