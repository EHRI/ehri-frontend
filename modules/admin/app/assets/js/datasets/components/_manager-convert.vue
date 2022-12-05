<script lang="ts">

import Draggable from 'vuedraggable';
import FilePicker from './_file-picker';
import ModalConvertConfig from './_modal-convert-config';
import ModalParamEditor from "./_modal-param-editor.vue";
import EditorTransformation from './_editor-transformation';
import TransformationItem from './_transformation-item';
import PanelLogWindow from './_panel-log-window';
import DragHandle from './_drag-handle';
import PanelConvertPreview from './_panel-convert-preview';

import MixinTwoPanel from './_mixin-two-panel';
import MixinValidator from './_mixin-validator';
import MixinError from './_mixin-error';
import MixinUtil from './_mixin-util';
import MixinTasklog from './_mixin-tasklog';
import {DatasetManagerApi} from '../api';
import _takeWhile from 'lodash/takeWhile';
import _find from 'lodash/find';
import {DataTransformation} from "../types";
import {Terminal} from "xterm";
import termopts from "../termopts";


let initialConvertState = function(config) {
  return {
    ingesting: {},
    previewStage: config.input,
    previewPipeline: [],
    previewing: null,
    tab: 'preview',
    showOptions: false,
    available: [],
    parametersForEditor: {},
    state: [], // List of [mapping-id, parameters, disabled] pairs
    editing: null,
    editingParameters: null,
    loading: false,
    initialised: false,
  };
};

export default {
  components: {
    Draggable, FilePicker, ModalParamEditor, ModalConvertConfig, PanelConvertPreview, EditorTransformation, TransformationItem, PanelLogWindow, DragHandle},
  mixins: [MixinTwoPanel, MixinValidator, MixinError, MixinUtil, MixinTasklog],
  props: {
    datasetId: String,
    datasetContentType: String,
    fileStage: String,
    urlKey: {
      type: String,
      default: 'convert-id',
    },
    config: Object,
    api: DatasetManagerApi,
    active: Boolean,
  },
  data: function () {
    return initialConvertState(this.config);
  },
  methods: {
    loadTransformations: async function() {
      this.loading = true;
      try {
        this.available = await this.api.listDataTransformations();
      } catch (e) {
        this.showError("Unable to load transformations", e);
      } finally {
        this.loading = false;
      }
    },
    editTransformation: function(item: DataTransformation) {
      console.log("Editing", item)
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
        hasParams: false,
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
    convert: async function(file, force) {
      console.debug("Converting:", file)
      this.tab = "info";
      try {
        let {url, jobId} = await this.api.convert(this.datasetId, file ? file.key : null, {
          mappings: this.convertState,
          force: force
        });
        this.replaceUrlState(this.urlKey, jobId);
        await this.monitor(url, jobId, (m: string) => {
          this.$emit('refresh-stage', this.config.output);
        });
      } catch (e) {
        this.showError("Error submitting conversion", e);
      } finally {
        this.removeUrlState(this.urlKey);
      }
    },
    resumeMonitor: async function() {
      let jobId = this.getQueryParam(window.location.search, this.urlKey);
      if (jobId) {
        try {
          this.tab = "info";
          await this.monitor(this.config.monitorUrl(jobId), jobId, (m: string) => {
            this.$emit('refresh-stage', this.config.output);
          });
        } finally {
          this.removeUrlState(this.urlKey);
        }
      }
    },
    loadConfig: async function() {
      this.loading = true;
      try {
        let data = await this.api.getConvertConfig(this.datasetId);
        this.state = data.map(([id, p]) => [id, p, false]);
      } catch (e) {
        this.showError("Error loading convert configuration", e);
      } finally {
        this.loading = false;
      }
    },
    saveConfig: async function(force: boolean = false) {
      try {
        let config = this.state.map(([id, p, _]) => [id, p]);
        await this.api.saveConvertConfig(this.datasetId, config);
      } catch (e) {
        this.showError("Failed to save mapping list", e);
      }
    },
    priorConversions: function(dt) {
      return _takeWhile(this.enabled, s => s.id !== dt.id);
    },
    removeTransformation: function(i: number) {
      this.state.splice(i, 1);
    },
    disableTransformation: function(i: number) {
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
    },
    initialise: async function() {
      await this.loadConfig();
      await this.loadTransformations();
      this.initialised = true;
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
        // Read-only
      }
    },
  },
  watch: {
    state: function() {
      if (!this.loading) {
        this.saveConfig();
      }
    },
    datasetId: function() {
      Object.assign(this.$data, initialConvertState(this.config));
      this.initialise();
    }
  },
  created: function () {
    this.initialise();
    this.resumeMonitor();
  },
};
</script>

<template>
  <div id="manager-convert-container" class="stage-manager-container">

    <editor-transformation
        v-if="editing !== null"
        v-bind:id="editing.id"
        v-bind:name="editing.name"
        v-bind:generic="!editing.repoId"
        v-bind:body-type="editing.bodyType"
        v-bind:body="editing.body"
        v-bind:comments="editing.comments"
        v-bind:has-params="editing.hasParams"
        v-bind:dataset-id="datasetId"
        v-bind:dataset-content-type="datasetContentType"
        v-bind:file-stage="previewStage"
        v-bind:init-previewing="previewing"
        v-bind:init-parameters="parametersForEditor"
        v-bind:config="config"
        v-bind:api="api"
        v-bind:input-pipeline="previewPipeline"
        v-on:saved="saved"
        v-on:close="closeEditForm"/>

    <div class="actions-bar">
      <file-picker v-bind:disabled="jobId !== null"
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

      <button v-if="!jobId" class="btn btn-sm btn-default" v-on:click.prevent="showOptions = true">
        <i class="fa fa-fw fa-file-code-o"/>
        Convert Files...
      </button>
      <button v-else v-bind:disabled="cancelling" class="btn btn-sm btn-outline-danger" v-on:click.prevent="cancelJob">
        <i class="fa fa-fw fa-spin fa-circle-o-notch"></i>
        Cancel Convert
      </button>
    </div>

    <div id="convert-panel-container" class="panel-container">
      <div class="top-panel">

        <modal-convert-config
            v-bind:config="config"
            v-bind:api="api"
            v-bind:dataset-id="datasetId"
            v-on:close="showOptions = false"
            v-on:convert="convert"
            v-if="showOptions" />

        <modal-param-editor
          v-if="editingParameters !== null"
          v-bind:obj="this.state[this.editingParameters][1]"
          v-on:close="cancelEditParamters"
          v-on:saved="saveParameters" />

        <div id="convert-mappings">
          <template v-if="initialised">
            <div  class="card">
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
                  v-model="transformations"
                  item-key="id">
                  <template v-slot:item="{element, index}">
                    <transformation-item
                        v-bind:item="element"
                        v-bind:disabled="false"
                        v-bind:key="index"
                        v-bind:active="false"
                        v-bind:parameters="null"
                        v-on:edit="editTransformation(element)"
                    />
                  </template>
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
                  v-model="enabled"
                  item-key="id">
                <template v-slot:item="{element, index}">
                  <transformation-item
                    v-bind:item="element"
                    v-bind:parameters="state[index][1]"
                    v-bind:disabled="state[index][2]"
                    v-bind:key="index"
                    v-bind:active="true"
                    v-on:edit="editActiveTransformation(index)"
                    v-on:delete="removeTransformation(index)"
                    v-on:disable="disableTransformation(index)"
                    v-on:edit-params="editParameters(index)"
                  />
                </template>
              </draggable>
            </div>
          </template>
          <div v-else class="panel-placeholder">
            <h2>
              Loading...
              <i class="fa fa-lg fa-spin fa-spinner"></i>
            </h2>
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
            <a href="#" class="nav-link" v-bind:class="{'active': tab === 'info'}"
               v-on:click.prevent="tab = 'info'">
              Info
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
          <div class="status-panel log-container" v-if="tab === 'info'">
            <panel-log-window v-bind:log="log" v-bind:panel-size="panelSize" v-bind:visible="tab === 'info'" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

