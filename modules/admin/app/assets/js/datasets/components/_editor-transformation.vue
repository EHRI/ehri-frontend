<script lang="ts">

import {nextTick} from 'vue';

import ModalWindow from './_modal-window';
import EditorXslt from './_editor-xslt.vue';
import EditorXquery from './_editor-xquery.vue';
import EditorJson from './_editor-json.vue';
import FilePicker from './_file-picker';
import DragHandle from './_drag-handle';
import PanelFilePreview from './_panel-file-preview';
import PanelConvertPreview from './_panel-convert-preview';
import ModalAlert from './_modal-alert';

import MixinTwoPanel from './_mixin-two-panel';

import _concat from 'lodash/concat';
import _isEqual from 'lodash/isEqual';
import {ImportDataset} from "../types";
import {DatasetManagerApi} from "../api";


export default {
  components: {
    ModalWindow,
    EditorXslt,
    EditorXquery,
    EditorJson,
    FilePicker,
    DragHandle,
    PanelFilePreview,
    PanelConvertPreview,
    ModalAlert
  },
  mixins: [MixinTwoPanel],
  props: {
    id: String,
    name: String,
    generic: Boolean,
    bodyType: String,
    body: String,
    comments: String,
    hasParams: Boolean,
    dataset: Object as ImportDataset,
    fileStage: String,
    initPreviewing: Object,
    config: Object,
    api: DatasetManagerApi,
    inputPipeline: Array,
    initParameters: {
      type: Object,
      default: function () {
        return {};
      }
    },
  },
  data: function () {
    return {
      solo: false,
      saving: false,
      previewing: this.initPreviewing,
      loading: false,
      panelSize: 0,
      data: {
        name: this.name,
        generic: this.generic,
        bodyType: this.bodyType,
        body: this.body,
        comments: this.comments,
        hasParams: this.hasParams,
      },
      parameters: this.initParameters,
      timestamp: (new Date()).toString(),
      inputValidationResults: {},
      outputValidationResults: {},
      showOptions: false,
      loadingIn: false,
      loadingOut: false,
      showRemoveDialog: false,
      pasteHelper: false,
      pasteText: "",
      confirmPaste: false,
      error: null,
    }
  },
  methods: {
    save: function () {
      this.saving = true;
      let p = this.id
          ? this.api.updateDataTransformation(this.id, this.data.generic, this.data)
          : this.api.createDataTransformation(this.data.generic, this.data);

      return p
          .then(item => {
            this.saving = false;
            this.$emit('saved', item)
          })
          .catch(error => {
            if (error.response && error.response.data && error.response.data.error) {
              this.error = error.response.data.error;
            } else {
              throw error;
            }
          })
          .finally(() => this.saving = false);
    },
    revert: function () {
      this.data = {
        name: this.name,
        generic: this.generic,
        bodyType: this.bodyType,
        body: this.body,
        comments: this.comments,
        hasParams: this.hasParams,
      };
    },
    remove: function () {
      this.api.deleteDataTransformation(this.id).then(_ => {
        this.showRemoveDialog = false;
        this.$emit('deleted');
        this.$emit('close');
      });
    },
    triggerRefresh: function () {
      this.timestamp = (new Date()).toString();
    },
    copyToClipboard: function () {
      navigator.clipboard.writeText(this.data.body);
    },
    download: function () {
      let text = this.data.body;
      let ext = (this.bodyType === 'xslt' ? ".xsl" : ".tsv");
      let filename = `${this.name}_${this.id}${ext}`;
      let mime = this.bodyType === 'xslt' ? 'text/xml' : 'text/tab-separated-values';
      let element = document.createElement('a');
      element.setAttribute('href', 'data:' + mime + ';charset=utf-8,' + encodeURIComponent(text));
      element.setAttribute('download', filename);

      element.style.display = 'none';
      document.body.appendChild(element);

      element.click();

      document.body.removeChild(element);
    },
    acceptPasteInput: function () {
      this.data["body"] = this.pasteText;
      this.pasteHelper = false;
      this.pasteText = "";
      this.triggerRefresh();
    },
    importFromClipboard: function () {
      if (typeof navigator.clipboard.readText === 'function') {
        navigator.clipboard.readText().then(text => {
          this.data["body"] = text;
          this.triggerRefresh();
        });
      }
    },
    confirmImportFromClipboard: function () {
      if (typeof navigator.clipboard.readText === 'function') {
        if (this.data.body.trim() !== "") {
          this.confirmPaste = true;
        } else {
          this.importFromClipboard();
        }
      } else {
        this.pasteHelper = true;
        this.pasteText = this.data.body;
        nextTick(() => {
          let el = this.$el.querySelector(".textarea-paste-helper");
          el.focus();
          el.select();
        });
      }
    }
  },
  computed: {
    mappings: function () {
      let stage = [[this.data.bodyType, this.data.body, this.parameters]];
      return this.solo
          ? stage
          : _concat(this.inputPipeline, stage);
    },
    modified: function () {
      return !_isEqual(this.data, {
        name: this.name,
        generic: this.generic,
        bodyType: this.bodyType,
        body: this.body,
        comments: this.comments,
        hasParams: this.hasParams,
      });
    },
    valid: function () {
      return this.data.name.trim() !== "" && this.data.comments.trim() !== "";
    }
  },
  mounted() {

  }
};
</script>

<template>
  <div
      v-on:keyup.esc="showOptions = false; showRemoveDialog = false"
      v-on:keyup.ctrl.enter="triggerRefresh" v-on:keyup.ctrl.prevent.s="save"
      class="modal" id="transformation-editor-modal">
    <div class="modal-dialog" id="transformation-editor-container">
      <div id="transformation-editor" class="modal-content">
        <div id="transformation-editor-heading" class="modal-header">
          <h5 class="modal-title">{{ id ? ('Edit transformation: ' + name) : 'New Transformation...' }}</h5>
          <button type="button" class="close" tabindex="-1" aria-label="Close" v-on:click="$emit('close')">
            <span aria-hidden="true">&times;</span>
          </button>
        </div>

        <modal-window v-if="pasteHelper" v-bind:resizable="true" v-on:keyup.esc="pasteHelper = false"
                      v-on:close="pasteHelper = false; pasteText = ''">
          <template v-slot:title>Paste input...</template>
          <textarea v-if="pasteHelper" placeholder="Paste TSV here..." class="textarea-paste-helper"
                    v-model="pasteText"></textarea>
          <template v-slot:footer>
            <button v-if="pasteHelper" class="btn btn-default btn-sm" v-on:click.prevent.stop="acceptPasteInput">
              <i class="fa fa-check text-success"></i>
              Accept TSV Input...
            </button>
          </template>
        </modal-window>

        <modal-alert
            v-if="confirmPaste"
            v-bind:title="'Overwrite existing contents?'"
            v-bind:accept="'Yes, overwrite'"
            v-on:accept="importFromClipboard(); confirmPaste = false"
            v-on:close="confirmPaste = false"
        />

        <div id="transformation-editor-panes" class="panel-container modal-body">
          <div id="transformation-editor-map" class="top-panel">
            <div id="transformation-editor-controls" class="controls">
              <label for="transformation-name">Name</label>
              <input v-model.trim="data.name" id="transformation-name" minlength="3" maxlength="255" required
                     placeholder="(required)"/>
              <label for="transformation-type">Type</label>
              <select id="transformation-type" v-model="data.bodyType">
                <option v-bind:value="'xquery'">XQuery</option>
                <option v-bind:value="'xslt'">XSLT</option>
              </select>
              <label for="transformation-scope">Scope</label>
              <select id="transformation-scope" v-model="data.generic">
                <option v-bind:value="false">Repository Specific</option>
                <option v-bind:value="true">Generic</option>
              </select>
              <label for="transformation-has-params">Parameters</label>
              <input type="checkbox" id="transformation-has-params" v-model="data.hasParams"/>
              <label for="transformation-comments">Description</label>
              <input v-model.trim="data.comments" id="transformation-comments" minlength="3" required
                     placeholder="(required)"/>
              <div class="buttons">
                <button class="btn btn-success btn-sm" v-on:click="save" v-bind:disabled="!valid || !modified">
                  Save
                  <i v-if="saving" class="fa fa-spin fa-circle-o-notch fa-fw"></i>
                  <i v-else class="fa fa-save fa-fw"></i>
                </button>
                <div class="dropdown">
                  <button class="btn btn-default btn-sm" v-on:click="showOptions = !showOptions">
                    <i class="fa fa-fw fa-ellipsis-v"></i>
                  </button>
                  <div v-if="showOptions" class="dropdown-backdrop" v-on:click="showOptions = false">
                  </div>
                  <div v-if="showOptions" class="dropdown-menu dropdown-menu-right show">
                    <button class="dropdown-item btn btn-sm" v-on:click="showOptions = false; revert()"
                            v-bind:disabled="!modified">
                      <i class="fa fa-undo"></i>
                      Revert Changes
                    </button>
                    <div class="dropdown-divider"></div>
                    <button class="dropdown-item btn btn-sm" v-on:click="showOptions = false; download()">
                      <i class="fa fa-download"></i>
                      Download As File
                    </button>
                    <button class="dropdown-item btn btn-sm" v-on:click="showOptions = false; copyToClipboard()">
                      <i class="fa fa-copy"></i>
                      Copy To Clipboard
                    </button>
                    <button class="dropdown-item btn btn-sm"
                            v-on:click="showOptions = false; confirmImportFromClipboard()">
                      <i class="fa fa-clipboard"></i>
                      Import From Clipboard
                    </button>
                    <div class="dropdown-divider"></div>
                    <button class="dropdown-item btn btn-sm"
                            v-on:click="showRemoveDialog = true; showOptions = false;"
                            v-bind:disabled="!Boolean(id)">
                      <i class="fa fa-trash-o"></i>
                      Delete Transformation
                    </button>
                  </div>
                  <modal-alert v-if="showRemoveDialog"
                               v-on:accept="remove"
                               v-on:close="showRemoveDialog = false"
                               v-bind:title="'Delete Transformation?'"
                               v-bind:accept="'Yes, delete it !'">
                    <p>Are you sure? This action can't be undone.</p>
                  </modal-alert>
                  <modal-alert v-if="error"
                               v-on:accept="error = null"
                               v-on:close="error = null"
                               v-bind:cancel="null"
                               v-bind:title="'Error saving transformation...'"
                               v-bind:cls="'warning'">
                    <p>{{ error }}</p>
                  </modal-alert>
                </div>
              </div>
            </div>
            <div id="transformation-editor-map-input">
              <editor-xquery v-if="data.bodyType === 'xquery'" v-model="data.body"/>
              <editor-xslt v-else v-model="data.body" v-bind:resize="panelSize" v-bind:timestamp="timestamp"/>
            </div>
            <div v-if="data.hasParams" id="transformation-editor-map-parameters">
              <editor-json v-model.lazy="parameters" v-bind:resize="panelSize"/>
            </div>
          </div>
          <div id="transformation-editor-preview-section" class="bottom-panel">
            <div id="transformation-editor-preview-select">
              <label for="transformation-editor-preview-options">Preview transformation</label>
              <file-picker id="transformation-editor-preview-options" v-bind:disabled="loading"
                           v-bind:dataset-id="dataset.id"
                           v-bind:file-stage="config.input"
                           v-bind:api="api"
                           v-bind:config="config"
                           v-bind:placeholder="'Select file to preview...'"
                           v-model="previewing"/>

              <button id="transformation-editor-preview-refresh" title="Refresh preview"
                      class="btn btn-sm" v-bind:disabled="previewing === null || loadingOut"
                      v-on:click="triggerRefresh">
                <i class="fa fa-refresh"></i>
              </button>
              <drag-handle v-bind:ns="'transformation-editor-preview-drag'"
                           v-bind:p2="() => $root.$el.querySelector('#transformation-editor-preview-section')"
                           v-bind:container="() => $root.$el.querySelector('#transformation-editor-panes')"
                           v-on:resize="setPanelSize"/>
            </div>
            <div id="transformation-editor-previews">
              <div class="transformation-editor-preview-window">
                <panel-file-preview
                    v-if="previewing !== null"
                    v-bind:dataset-id="dataset.id"
                    v-bind:content-type="dataset.contentType"
                    v-bind:file-stage="fileStage"
                    v-bind:previewing="previewing"
                    v-bind:panel-size="panelSize"
                    v-bind:config="config"
                    v-bind:api="api"
                    v-on:loading="loadingIn = true"
                    v-on:loaded="loadingIn = false"/>
                <div class="panel-placeholder" v-if="previewing === null">
                  Input preview
                </div>
              </div>
              <div class="transformation-editor-preview-window">
                <panel-convert-preview
                    v-if="previewing !== null"
                    v-bind:dataset-id="dataset.id"
                    v-bind:content-type="dataset.contentType"
                    v-bind:file-stage="fileStage"
                    v-bind:mappings="mappings"
                    v-bind:trigger="JSON.stringify(data)"
                    v-bind:previewing="previewing"
                    v-bind:panel-size="panelSize"
                    v-bind:config="config"
                    v-bind:api="api"
                    v-on:loading="loadingOut = true"
                    v-on:loaded="loadingOut = false"/>
                <div class="panel-placeholder" v-if="previewing === null">
                  Output preview
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

