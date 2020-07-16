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
<!--      <textarea @change="$emit('input', $event.target.value)">{{value}}</textarea>-->
      <textarea>{{value}}</textarea>
    </div>
  `
});

Vue.component("xquery-mapping", {
  props: {
    mapping: Object,
    selected: Boolean,
  },
  methods: {
    update: function() {
      this.$emit("input", this.mapping)
    },
  },
  template: `
    <div class="xquery-mapping" v-bind:class="{'selected':selected}">
      <input type="text" v-model="mapping.targetPath" @change="update" @click="$emit('select', $event)"/>
      <input type="text" v-model="mapping.targetNode" @change="update" @click="$emit('select', $event)" />
      <input type="text" v-model="mapping.sourceNode" @change="update" @click="$emit('select', $event)"/>
      <input type="text" v-model="mapping.value" @change="update" @click="$emit('select', $event)"/>
    </div>
  `
})

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
    },
    add: function() {
      this.mappings.push({
        targetPath: "",
        targetNode: "",
        sourceNode: "",
        value: ""
      });
      this.update();
    },
    duplicate: function(i) {
      let m = _.clone(this.mappings[i]);
      this.mappings.splice(i + 1, 0, m);
      this.update();
    },
    remove: function(i) {
      this.mappings.splice(i, 1);
      this.selected = -1;
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
      let mappings = [];
      if (str !== "") {
        // Ignore the header row here...
        return str
          .split("\n")
          .slice(1)
          .map (m => {
            let parts = m.split("\t");
            return {
              targetPath: parts[0] ? parts[0] : "",
              targetNode: parts[1] ? parts[1] : "",
              sourceNode: parts[2] ? parts[2] : "",
              value: parts[3] ? parts[3] : "",
            };
          });
      } else {
        return [];
      }
    },
    serialize: function(mappings) {
      let header = ["target-path\ttarget-node\tsource-node\tvalue"]
      let rows = mappings.map(m => [m.targetPath,m.targetNode,m.sourceNode,m.value].join("\t"))
      let all = _.concat(header, rows)
      return all.join("\n");
    }
  },
  created() {
    // this.mappings = this.deserialize(this.value);
  },
  template: `
    <div class="xquery-editor">
      <div class="xquery-editor-data">
        <div class="xquery-mapping">
          <input readonly disabled type="text" value="target-path" @click="selected = -1"/>
          <input readonly disabled type="text" value="target-node" @click="selected = -1"/>
          <input readonly disabled type="text" value="source-node" @click="selected = -1"/>
          <input readonly disabled type="text" value="value" @click="selected = -1"/>
        </div>
        <xquery-mapping 
          v-for="(mapping, i) in mappings" 
          v-bind:key="i" 
          v-bind:selected="selected === i"
          v-bind:mapping="mappings[i]"
          @select="selected = i"
          @input="update" />
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
      </div>
    </div>
  `
});

Vue.component("edit-form-panes", {
  mixins: [twoPanelMixin],
  props: {
    id: Number,
    name: String,
    generic: Boolean,
    bodyType: String,
    body: String,
    comments: String,
  },
  data: function() {
    return {
      saving: false,
      previewing: null,
      loading: false,
      previewOptions: [],
      panelSize: 0,
      data: {
        name: this.name,
        generic: this.generic,
        bodyType: this.bodyType,
        body: this.body,
        comments: this.comments,
      },
      fileStage: 'upload',
      inputValidationResults: [],
      outputValidationResults: [],
      showOptions: false,
    }
  },
  methods: {
    save: function() {
      this.saving = true;
      let p = this.id
        ? DAO.updateDataTransformation(this.id, this.data.generic, this.data)
        : DAO.createDataTransformation(this.data.generic, this.data);

      return p.then(item => {
        this.saving = false;
        this.$emit('saved', item)
      });
    },
    remove: function () {
      DAO.deleteDataTransformation(this.id).then(_ => {
        this.$emit('deleted');
        this.$emit('close');
      });
    },
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
  created: function() {
    DAO.listFiles(this.fileStage)
      .then(data => this.previewOptions = data.files);

  },
  template: `
    <div class="modal" id="edit-form-modal">
      <div class="modal-dialog" id="edit-form-container">
        <div id="edit-form" class="modal-content">
          <div id="edit-form-heading" class="modal-header">
            <h5 class="modal-title">{{id ? ('Edit transformation: ' + name) : 'New Transformation...'}}</h5>
            <div class="close" data-dismiss="modal" aria-label="Close" v-on:click="$emit('close')">
              <span aria-hidden="true">&times;</span>
            </div>
          </div>
          <div id="edit-form-panes" class="panel-container modal-body">
            <div id="edit-form-map" class="top-panel">
              <div id="edit-form-controls" class="controls">
                <label for="transformation-name">Name</label>
                <input v-model.trim="data.name" id="transformation-name" minlength="3" required/>
                <label for="transformation-type">Mapping Type</label>
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
                <input v-model.trim="data.comments" id="transformation-comments" minlength="3" required />
                <div class="buttons btn-group">
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
                      <button class="dropdown-item btn btn-sm" v-on:click="remove" v-bind:disabled="!Boolean(id)">Delete Transformation</button>
                    </div>
                  </div>
                </div>
              </div>
              <div id="edit-form-map-input">
                <xquery-editor v-if="data.bodyType === 'xquery'" v-model="data.body"/>
                <xslt-editor v-else v-model="data.body"></xslt-editor>
              </div>
            </div>
            <div id="edit-form-preview-section" class="bottom-panel">
              <div id="edit-form-preview-select">
                <label for="edit-form-preview-options">Preview transformation</label>
                <select id="edit-form-preview-options" v-model="previewing">
                  <option v-bind:value="null">---</option>
                  <option v-for="file in previewOptions" v-bind:value="file.key">{{file.key}}</option>
                </select>
                <drag-handle v-bind:ns="'edit-form-preview-drag'"
                             v-bind:p2="$root.$el.querySelector('#edit-form-preview-section')"
                             v-bind:container="$root.$el.querySelector('#edit-form-panes')"
                              v-on:resize="setPanelSize" />
              </div>
              <div id="edit-form-previews">
                <div class="edit-form-preview-window">
                  <preview 
                    v-if="previewing !== null"
                    v-bind:file-stage="fileStage"
                    v-bind:previewing="previewing"
                    v-bind:errors="inputValidationResults"
                    v-bind:panel-size="panelSize"/>
                  <div class="panel-placeholder" v-if="previewing === null">
                    Input preview
                  </div>
                </div>
                <div class="edit-form-preview-window">
                  <convert-preview 
                    v-if="previewing !== null"
                    v-bind:mappings="mappings"
                    v-bind:trigger="JSON.stringify({
                        bodyType: data.bodyType, 
                        body: data.body
                    })"
                    v-bind:file-stage="fileStage"
                    v-bind:previewing="previewing"
                    v-bind:errors="outputValidationResults"
                    v-bind:panel-size="panelSize"/>
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
