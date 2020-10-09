"use strict";

Vue.component("xslt-editor", {
  props: {
    value: String,
  },
  mounted: function () {
    this.editor = CodeMirror.fromTextArea(this.$el.querySelector("textarea"), {
      mode: 'xml',
      lineNumbers: false,
      readOnly: false,
    });
    this.editor.on("change", () => {
      this.$emit('input', this.editor.getValue());
    });
  },
  beforeDestroy: function () {
    if (this.editor) {
      this.editor.toTextArea();
    }
  },
  template: `
    <div class="xslt-editor">
      <textarea>{{value}}</textarea>
    </div>
  `
});

Vue.component("xquery-editor", {
  props: {
    value: String,
  },
  data: function() {
    return {
      mappings: this.deserialize(this.value),
      selected: -1,
    }
  },
  methods: {
    update: function() {
      this.$emit('input', this.serialize(this.mappings));
      // Return a promise when the DOM is ready...
      return Vue.nextTick();
    },
    focus: function(row, col) {
      let elem = this.$refs[_.padStart(row, 4, 0) + '-' + col];
      if (elem && elem[0]) {
        elem[0].focus();
      }
    },
    add: function() {
      // Insert a new item below the current selection, or
      // at the end if nothing is selected.
      let point = this.selected === -1
        ? this.mappings.length
        : this.selected + 1;
      this.mappings.splice(point, 0, ["", "", "", ""])
      this.selected = point;
      this.update()
        .then(() => this.focus(this.selected, 0));
    },
    duplicate: function(i) {
      let m = _.clone(this.mappings[i]);
      this.selected = i + 1;
      this.mappings.splice(this.selected, 0, m);
      this.update();
    },
    remove: function(i) {
      this.mappings.splice(i, 1);
      this.selected = Math.min(i, this.mappings.length - 1);
      this.update();
    },
    moveUp: function(i) {
      if (i > 0) {
        let m = this.mappings.splice(i, 1)[0];
        this.mappings.splice(i - 1, 0, m);
        this.selected = i - 1;
        this.update();
      }
    },
    moveDown: function(i) {
      if (i < this.mappings.length - 1) {
        let m = this.mappings.splice(i, 1)[0];
        this.mappings.splice(i + 1, 0, m);
        this.selected = i + 1;
        this.update();
      }
    },
    deserialize: function(str) {
      if (str !== "") {
        // Ignore the header row here...
        return str
          .split("\n")
          .slice(1)
          .map (m => {
            let parts = m.split("\t");
            return [
              parts[0] ? parts[0] : "",
              parts[1] ? parts[1] : "",
              parts[2] ? parts[2] : "",
              parts[3] ? parts[3] : "",
            ];
          });
      } else {
        return [];
      }
    },
    serialize: function(mappings) {
      let header = ["target-path\ttarget-node\tsource-node\tvalue"]
      let rows = mappings.map(m => m.join("\t"))
      let all = _.concat(header, rows)
      return all.join("\n");
    },
  },
  template: `
    <div class="xquery-editor">
      <div class="xquery-editor-data" v-on:keyup.esc="selected = -1">
        <div class="xquery-editor-header">
            <input readonly disabled type="text" value="target-path" @click="selected = -1"/>
            <input readonly disabled type="text" value="target-node" @click="selected = -1"/>
            <input readonly disabled type="text" value="source-node" @click="selected = -1"/>
            <input readonly disabled type="text" value="value" @click="selected = -1"/>
        </div>
        <div class="xquery-editor-mappings">
          <template v-for="(mapping, row) in mappings">
            <input
              v-for="col in [0, 1, 2, 3]"
              type="text"
              v-bind:ref="_.padStart(row, 4, 0) + '-' + col"
              v-bind:key="_.padStart(row, 4, 0) + '-' + col"
              v-bind:class="{'selected': selected === row}"
              v-model="mappings[row][col]"
              @change="update"
              @focusin="selected = row" />
          </template>
        </div>
      </div>
      <div class="xquery-editor-toolbar">
        <button class="btn btn-default btn-sm" v-on:click="add">
          <i class="fa fa-plus"></i>
          Add Mapping
        </button>
        <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0" v-on:click="duplicate(selected)">
          <i class="fa fa-copy"></i>
          Duplicate Mapping
        </button>
        <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0" v-on:click="remove(selected)">
          <i class="fa fa-trash-o"></i>
          Delete Mapping
        </button>
        <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0 || selected === 0" v-on:click="moveUp(selected)">
          <i class="fa fa-caret-up"></i>
          Move Up
        </button>
        <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0 || selected === mappings.length - 1" v-on:click="moveDown(selected)">
          <i class="fa fa-caret-down"></i>
          Move Down
        </button>
        <div class="xquery-editor-toolbar-info">
          Data mappings: {{mappings.length}}
        </div>
      </div>
    </div>
  `
});

Vue.component("transformation-editor", {
  mixins: [twoPanelMixin],
  props: {
    id: String,
    name: String,
    generic: Boolean,
    bodyType: String,
    body: String,
    comments: String,
    initPreviewStage: String,
    initPreviewing: Object,
    config: Object,
    api: Object,
  },
  data: function() {
    return {
      saving: false,
      previewStage: this.initPreviewStage,
      previewing: this.initPreviewing,
      loading: false,
      panelSize: 0,
      data: {
        name: this.name,
        generic: this.generic,
        bodyType: this.bodyType,
        body: this.body,
        comments: this.comments,
      },
      timestamp: (new Date()).toString(),
      fileStage: 'upload',
      inputValidationResults: {},
      outputValidationResults: {},
      showOptions: false,
      loadingIn: false,
      loadingOut: false,
      showRemoveDialog: false,
      error: null,
    }
  },
  methods: {
    save: function() {
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
          }
        })
        .finally(() => this.saving = false);
    },
    confirmRemove: function() {

    },
    remove: function () {
      this.api.deleteDataTransformation(this.id).then(_ => {
        this.showRemoveDialog = false;
        this.$emit('deleted');
        this.$emit('close');
      });
    },
    triggerRefresh: function() {
      this.timestamp = (new Date()).toString();
    }
  },
  computed: {
    mappings: function() {
      return [[this.data.bodyType, this.data.body]];
    },
    modified: function() {
      return !_.isEqual(this.data, {
        name: this.name,
        generic: this.generic,
        bodyType: this.bodyType,
        body: this.body,
        comments: this.comments,
      });
    },
    valid: function() {
      return this.data.name.trim() !== "" && this.data.comments.trim() !== "";
    }
  },
  template: `
    <div 
      v-on:keyup.esc="showOptions = false; showRemoveDialog = false" 
      v-on:keyup.ctrl.enter="triggerRefresh"
      class="modal" id="transformation-editor-modal">
      <div class="modal-dialog" id="transformation-editor-container">
        <div id="transformation-editor" class="modal-content">
          <div id="transformation-editor-heading" class="modal-header">
            <h5 class="modal-title">{{id ? ('Edit transformation: ' + name) : 'New Transformation...'}}</h5>
            <button type="button" class="close" tabindex="-1" data-dismiss="modal" aria-label="Close" v-on:click="$emit('close')">
              <span aria-hidden="true">&times;</span>
            </button>
          </div>
          <div id="transformation-editor-panes" class="panel-container modal-body">
            <div id="transformation-editor-map" class="top-panel">
              <div id="transformation-editor-controls" class="controls">
                  <label for="transformation-name">Name</label>
                  <input v-model.trim="data.name" id="transformation-name" minlength="3" maxlength="255" required placeholder="(required)"/>
                <label for="transformation-type">Type</label>
                <select id="transformation-type" v-model="data.bodyType">
                  <option v-bind:value="'xquery'">XQuery</option>
                  <option v-bind:value="'xslt'">XSLT</option>
                </select>
                <label for="transformation-type">Scope</label>
                <select id="transformation-type" v-model="data.generic">
                  <option v-bind:value="false">Repository Specific</option>
                  <option v-bind:value="true">Generic</option>
                </select>
                <label for="transformation-comments">Description</label>
                <input v-model.trim="data.comments" id="transformation-comments" minlength="3" required placeholder="(required)" />
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
                      <button class="dropdown-item btn btn-sm" 
                              v-on:click="showRemoveDialog = true; showOptions = false;" 
                              v-bind:disabled="!Boolean(id)">Delete Transformation</button>
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
                        <p>{{error}}</p>    
                    </modal-alert>
                  </div>
                </div>
              </div>
              <div id="transformation-editor-map-input">
                <xquery-editor v-if="data.bodyType === 'xquery'" v-model.lazy="data.body"/>
                <xslt-editor v-else v-model.lazy="data.body"></xslt-editor>
              </div>
            </div>
            <div id="transformation-editor-preview-section" class="bottom-panel">
              <div id="transformation-editor-preview-select">
                <label for="transformation-editor-preview-options">Preview transformation</label>
                <file-picker v-bind:disabled="loading"
                             v-bind:file-stage="config.input"
                             v-bind:api="api"
                             v-bind:config="config"
                             v-model="previewing" />

                <button id="transformation-editor-preview-refresh"  title="Refresh preview"
                        class="btn btn-sm" v-bind:disabled="previewing === null || loadingOut" v-on:click="triggerRefresh">
                  <i class="fa fa-refresh"></i>
                </button>
                <drag-handle v-bind:ns="'transformation-editor-preview-drag'"
                             v-bind:p2="$root.$el.querySelector('#transformation-editor-preview-section')"
                             v-bind:container="$root.$el.querySelector('#transformation-editor-panes')"
                              v-on:resize="setPanelSize" />
              </div>
              <div id="transformation-editor-previews">
                <div class="transformation-editor-preview-window">
                  <preview 
                    v-if="previewing !== null"
                    v-bind:file-stage="previewStage"
                    v-bind:previewing="previewing"
                    v-bind:panel-size="panelSize"
                    v-bind:config="config"
                    v-bind:api="api"
                    v-on:loading="loadingIn = true"
                    v-on:loaded="loadingIn = false" />
                  <div class="panel-placeholder" v-if="previewing === null">
                    Input preview
                  </div>
                </div>
                <div class="transformation-editor-preview-window">
                  <convert-preview 
                    v-if="previewing !== null"
                    v-bind:mappings="mappings"
                    v-bind:trigger="timestamp"
                    v-bind:file-stage="previewStage"
                    v-bind:previewing="previewing"
                    v-bind:panel-size="panelSize"
                    v-bind:config="config"
                    v-bind:api="api"
                    v-on:loading="loadingOut = true"
                    v-on:loaded="loadingOut = false" />
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
  `
});
