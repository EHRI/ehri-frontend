<script lang="ts">

import Draggable from 'vuedraggable';
import FilePicker from './_file-picker';
import ModalConvertConfig from './_modal-convert-config';
import ModalParamEditor from "./_modal-param-editor.vue";
import TransformationEditor from './_transformation-editor';
import TransformationItem from './_transformation-item';
import PanelLogWindow from './_panel-log-window';
import DragHandle from './_drag-handle';
import PanelConvertPreview from './_panel-convert-preview';

import MixinTwoPanel from './_mixin-two-panel';
import MixinValidator from './_mixin-validator';
import MixinError from './_mixin-error';
import MixinUtil from './_mixin-util';
import {DatasetManagerApi} from '../api';

import _partition from 'lodash/partition';
import _takeWhile from 'lodash/takeWhile';
import _isEqual from 'lodash/isEqual';
import _fromPairs from 'lodash/fromPairs';
import _find from 'lodash/find';
import {DataTransformation} from "../types";


let initialConvertState = function(config) {
  return {
    convertJobId: null,
    ingesting: {},
    previewStage: config.input,
    previewPipeline: [],
    previewing: null,
    tab: 'preview',
    log: [],
    showOptions: false,
    available: [],
    parametersForEditor: {},
    state: [], // List of [mapping-id, parameters, muted] pairs
    editing: null,
    editingParameters: null,
    loading: false,
  };
};

export default {
  components: {
    Draggable, FilePicker, ModalParamEditor, ModalConvertConfig, PanelConvertPreview, TransformationEditor, TransformationItem, PanelLogWindow, DragHandle},
  mixins: [MixinTwoPanel, MixinValidator, MixinError, MixinUtil],
  props: {
    datasetId: String,
    fileStage: String,
    config: Object,
    api: DatasetManagerApi,
    active: Boolean,
  },
  data: function () {
    return initialConvertState(this.config);
  },
  methods: {
    loadTransformations: function() {
      this.loading = true;

      return this.api.listDataTransformations()
          .then(available => this.available = available)
          .catch(error => this.showError("Unable to load transformations", error))
          .finally(() => this.loading = false);
    },
    editTransformation: function(item: DataTransformation) {
      this.editing = item;
      this.previewPipeline = [];
    },
    editActiveTransformation: function(i: number) {
      this.editing = this.enabled[i];
      this.parametersForEditor = this.parameters[i];
      this.previewPipeline = this.previewSettings(i);
    },
    previewSettings: function(i: number): [string, string, object][] {
      return this.state
          .map(([_, params], i) => [this.enabled[i].bodyType, this.enabled[i].body, params])
          .slice(0, i);
    },
    newTransformation: function() {
      this.editing = {
        id: null,
        repoId: this.config.repoId,
        name: "",
        bodyType: "xslt",
        body: "",
        comments: "",
      };
    },
    closeEditForm: function() {
      this.editing = null;
      this.parametersForEditor = {};
      this.loadTransformations();
    },
    saved: function(item) {
      this.editing = item;
    },
    transformationAt: function(i: number): DataTransformation {
      return _find(this.available, dt => dt.id === this.mappings[i]);
    },
    convert: function(file, force) {
      console.debug("Converting:", file)
      this.api.convert(this.datasetId, file ? file.key : null, {mappings: this.convertState, force: force})
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
      worker.postMessage({type: 'websocket', url: url, DONE: DatasetManagerApi.DONE_MSG, ERR: DatasetManagerApi.ERR_MSG});
      this.replaceUrlState('convert-job-id', jobId);
    },
    resumeMonitor: function() {
      let jobId = this.getQueryParam(window.location.search, "convert-job-id");
      if (jobId) {
        this.convertJobId = jobId;
        this.monitorConvert(this.config.monitorUrl(jobId), jobId);
      }
    },
    loadConfig: function(): Promise<void> {
      this.loading = true;
      return this.api.getConvertConfig(this.datasetId)
          .then(data => this.state = data.map(([id, p]) => [id, p, false]))
          .catch(error => this.showError("Error loading convert configuration", error))
          .finally(() => this.loading = false);
    },
    saveConfig: function(force: boolean = false) {
      let config = this.state.map(([id, p, _]) => [id, p]);
      this.api.saveConvertConfig(this.datasetId, config)
          .catch(error => this.showError("Failed to save mapping list", error));
    },
    priorConversions: function(dt) {
      return _takeWhile(this.enabled, s => s.id !== dt.id);
    },
    removeTransformation: function(i: number) {
      this.state.splice(i, 1);
    },
    muteTransformation: function(i: number) {
      let [id, p, m] = this.state[i];
      this.state.splice(i, 1, [id, p, !m]);
    },
    addTransformation: function(data: {newIndex: number, oldIndex: number}) {
      console.log("Add", data.newIndex, data.oldIndex)
      let id = this.transformations[data.oldIndex].id
      this.state.splice(data.newIndex, 0, [id, {}, false]);
    },
    editParameters: function(i: number) {
      this.editingParameters = i;
    },
    saveParameters: function(obj: object) {
      let [id, _, m] = this.state[this.editingParameters];
      this.state.splice(this.editingParameters, 1, [id, obj, m]);
      this.editingParameters = null;
      this.saveConfig(true);
    },
    cancelEditParamters: function() {
      this.editingParameters = null;
    }
  },
  computed: {
    convertState: function() {
      return this.state
          .filter(([id, p, m]) => !m)
          .map(([id, p, _]) => [id, p]);
    },
    mappings: function() {
      return this.state.map(pair => pair[0]);
    },
    parameters: function() {
      return this.state.map(pair => pair[1]);
    },
    transformations: {
      get(): DataTransformation[] {
        return this.available;
      },
      set() {
        // Read-only
      }
    },
    enabled: {
      get(): DataTransformation[] {
        return this.available.length > 0
          ? this.mappings.map(id => _find(this.available, dt => dt.id === id))
          : [];
      },
      set(arr: DataTransformation[]) {
        // this.mappings = arr.map(dt => dt.id);
      }
    },
  },
  watch: {
    state: function() {
      if (!this.loading) {
        this.saveConfig();
      }
    },
    // parameters: function() {
    //   if (!this.loading) {
    //     this.saveConfig();
    //   }
    // },
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
};
</script>

<template>
  <div id="manager-convert-container" class="stage-manager-container">

    <transformation-editor
        v-if="editing !== null"
        v-bind:id="editing.id"
        v-bind:name="editing.name"
        v-bind:generic="!editing.repoId"
        v-bind:body-type="editing.bodyType"
        v-bind:body="editing.body"
        v-bind:comments="editing.comments"
        v-bind:has-params="editing.hasParams"
        v-bind:dataset-id="datasetId"
        v-bind:file-stage="previewStage"
        v-bind:init-previewing="previewing"
        v-bind:init-parameters="parametersForEditor"
        v-bind:config="config"
        v-bind:api="api"
        v-bind:input-pipeline="previewPipeline"
        v-on:saved="saved"
        v-on:close="closeEditForm"/>

    <div class="actions-bar">
      <file-picker v-bind:disabled="convertJobId !== null"
                   v-bind:dataset-id="datasetId"
                   v-bind:file-stage="config.input"
                   v-bind:api="api"
                   v-bind:config="config"
                   v-bind:placeholder="'Select file to preview...'"
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

        <modal-convert-config
            v-bind:show="showOptions"
            v-bind:config="config"
            v-bind:api="api"
            v-bind:dataset-id="datasetId"
            v-on:close="showOptions = false"
            v-on:convert="convert"
            v-show="showOptions" />

        <modal-param-editor
          v-if="editingParameters !== null"
          v-bind:obj="this.state[this.editingParameters][1]"
          v-on:close="cancelEditParamters"
          v-on:saved="saveParameters" />

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
                v-bind:group="{name: 'transformations', put: false, pull: 'clone'}"
                v-bind:sort="false"
                v-model="transformations">
              <transformation-item
                  v-for="(dt, i) in transformations"
                  v-bind:item="dt"
                  v-bind:muted="false"
                  v-bind:key="i"
                  v-bind:deleteable="false"
                  v-bind:muteable="false"
                  v-bind:parameters="null"
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
                v-bind:group="{name: 'transformations', put: true, pull: true}"
                v-bind:sort="true"
                v-on:add="addTransformation"
                v-model="enabled">
              <transformation-item
                  v-for="(dt, i) in enabled"
                  v-bind:item="dt"
                  v-bind:parameters="state[i][1]"
                  v-bind:muted="state[i][2]"
                  v-bind:key="i"
                  v-bind:deleteable="true"
                  v-bind:muteable="true"
                  v-on:edit="editActiveTransformation(i)"
                  v-on:delete="removeTransformation(i)"
                  v-on:mute="muteTransformation(i)"
                  v-on:edit-params="editParameters(i)"
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
                v-bind:p2="() => $root.$el.querySelector('#convert-status-panels')"
                v-bind:container="() => $root.$el.querySelector('#convert-panel-container')"
                v-on:resize="setPanelSize"
            />
          </li>
        </ul>

        <div class="status-panels">
          <div class="status-panel" v-show="tab === 'preview'">
            <panel-convert-preview
                v-bind:dataset-id="datasetId"
                v-bind:file-stage="previewStage"
                v-bind:mappings="convertState"
                v-bind:trigger="JSON.stringify({
                         state: state,
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
            <panel-log-window v-bind:log="log" v-if="log.length > 0"/>
            <div class="panel-placeholder" v-else>
              Convert log output will show here.
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

