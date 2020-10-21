"use strict";

function sequential(func, arr, index) {
  if (index >= arr.length) return Promise.resolve();
  return func(arr[index])
    .then(() => sequential(func, arr, index + 1));
}

// Bytes-to-human readable string from:
// https://stackoverflow.com/a/14919494/285374
Vue.filter("humanFileSize", function (bytes, si) {
  let f = (bytes, si) => {
    let thresh = si ? 1000 : 1024;
    if (Math.abs(bytes) < thresh) {
      return bytes + ' B';
    }
    let units = si
      ? ['kB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB']
      : ['KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];
    let u = -1;
    do {
      bytes /= thresh;
      ++u;
    } while (Math.abs(bytes) >= thresh && u < units.length - 1);
    return bytes.toFixed(1) + ' ' + units[u];
  };
  return _.memoize(f)(bytes, si);
});

Vue.filter("stageName", function(code, config) {
  switch (code) {
    case "oaipmh": return "Harvesting";
    case "upload": return "Uploads";
    default: return code;
  }
})

Vue.filter("prettyDate", function (time) {
  let f = time => {
    let m = luxon.DateTime.fromISO(time);
    return m.isValid ? m.toRelative() : "";
  };
  return _.memoize(f)(time);
});

let previewMixin = {
  data: function() {
    return {
      tab: 'preview'
    }
  },
  methods: {
    showPreview: function (file) {
      this.previewing = file;
      this.tab = 'preview';
    },
  }
};

let utilMixin = {
  methods: {
    removeQueryParam: function(qs, names) {
      let qp = this.queryParams(qs);
      return this.queryString(_.omit(qp, names));
    },
    setQueryParam: function(qs, name, value) {
      let qp = this.queryParams(qs);
      return this.queryString(_.set(qp, name, value));
    },
    getQueryParam: function(qs, name) {
      let qp = this.queryParams(qs);
      return _.has(qp, name) ? qp[name] : null;
    },
    queryParams: function(qs) {
      let qsp = (qs && qs[0] === '?') ? qs.slice(1): qs;
      return (qsp && qsp.trim() !== "")
        ? _.fromPairs(qsp.split("&").map(p => p.split("=")))
        : {};
    },
    queryString: function(qp) {
      return !_.isEmpty(qp)
        ? ("?" + _.toPairs(qp).map(p => p.join("=")).join("&"))
        : "";
    },
    removeUrlState: function(key) {
      history.replaceState(
        _.omit(this.queryParams(window.location.search), key),
        document.title,
        this.removeQueryParam(window.location.search, key)
      );
    },
    replaceUrlState: function(key, value) {
      history.replaceState(
        _.extend(this.queryParams(window.location.search), {key: value}),
        document.title,
        this.setQueryParam(window.location.search, key, value)
      );
    }
  }
};

let errorMixin = {
  data: function() {
    return {
      errored: false,
    };
  },
  methods: {
    showError: function(desc, exception) {
      this.errored = true;
      this.$emit("error", desc, exception);
    },
    clearError: function() {
      this.errored = false;
    }
  }
}

let twoPanelMixin = {
  data: function() {
    return {
      panelSize: null,
    }
  },
  methods: {
    setPanelSize: function (arbitrarySize) {
      this.panelSize = arbitrarySize;
    }
  }
};

let validatorMixin = {
  props: {
    datasetId: String,
    fileStage: String,
    api: Object,
  },
  data: function() {
    return {
      validating: {},
      validationRunning: false,
      validationResults: {},
      validationLog: [],
    }
  },
  methods: {
    handleValidationResults: function(errs) {
      _.forEach(errs, (item) => {
        this.$set(this.validationResults, item.eTag, item.errors)
        this.$delete(this.validating, item.eTag);
      });
      if (_.isUndefined(_.find(errs, (err) => err.errors.length > 0))) {
        this.validationLog.push('<span class="text-success">No errors found ✓</span>');
      } else {
        errs.forEach(item => {
          if (item.errors.length > 0) {
            this.validationLog.push('<span class="text-danger">' + item.key + ':</span>')
            item.errors.forEach(err => {
              this.validationLog.push("    " + err.line + "/" + err.pos + " - " + err.error);
            })
          }
        });
      }
    },
    validateAll: function() {
      this.tab = 'validation';
      this.validationRunning = true;
      this.validationLog = [];
      this.files.forEach(f => this.$set(this.validating, f.eTag, true));

      this.api.validateAll(this.datasetId, this.fileStage)
        .then(errs => this.handleValidationResults(errs))
        .catch(error => this.showError("Error attempting validation", error))
        .finally(() => {
          this.validating = {};
          this.validationRunning = false;
        });
    },
    validateFiles: function (tagToKey) {
      this.tab = 'validation';
      this.validationRunning = true;
      this.validationLog = [];
      _.forEach(tagToKey, (key, tag) => this.$set(this.validating, tag, true));

      this.api.validateFiles(this.datasetId, this.fileStage, tagToKey)
        .then(errs => this.handleValidationResults(errs))
        .catch(error => this.showError("Error attempting validation", error))
        .finally(() => {
          this.validating = {};
          this.validationRunning = false;
        });
    },
  }
};

let previewPanelMixin = {
  mixins: [errorMixin],
  props: {
    datasetId: String,
    fileStage: String,
    panelSize: Number,
    previewing: Object,
    config: Object,
    api: Object,
    maxSize: Number,
    validationResults: {
      type: Object,
      default: function () { return {} },
    },
  },
  data: function () {
    return {
      loading: false,
      validating: false,
      previewData: null,
      previewTruncated: false,
      percentDone: 0,
      wrap: false,
      prettifying: false,
      prettified: false,
      showingError: false,
      worker: null,
      errored: false,
      errors: null,
    }
  },
  methods: {
    prettifyXml: function(xml) {
      let parser = new DOMParser();
      let xmlDoc = parser.parseFromString(xml, 'application/xml');
      let xsltDoc = parser.parseFromString(`
        <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
          <xsl:template match="node()|@*">
            <xsl:copy>
              <xsl:apply-templates select="node()|@*"/>
            </xsl:copy>
          </xsl:template>
          <xsl:output indent="yes"/>
        </xsl:stylesheet>
      `, 'application/xml');

      let xsltProcessor = new XSLTProcessor();
      xsltProcessor.importStylesheet(xsltDoc);
      let resultDoc = xsltProcessor.transformToDocument(xmlDoc);
      return new XMLSerializer().serializeToString(resultDoc);
    },
    makePretty: function() {
      this.prettifying = true;
      this.previewData = this.prettifyXml(this.previewData);
      Vue.nextTick(() => {
        this.prettified = true
        this.prettifying = false;
      });
    },
    validate: function () {
      if (this.previewing === null) {
        return;
      }

      if (this.validationResults[this.previewing.eTag]) {
        this.errors = this.validationResults[this.previewing.eTag];
        this.updateErrors();
      } else {
        this.validating = true;
        let tagToPath = _.fromPairs([[this.previewing.eTag, this.previewing.key]]);
        this.api.validateFiles(this.datasetId, this.fileStage, tagToPath)
          .then(errors => {
            let e = _.find(errors, e => this.previewing.eTag === e.eTag);
            if (e) {
              this.errors = e.errors;
              this.updateErrors()
              this.$emit("validation-results", this.previewing.eTag, e.errors);
            }
          })
          .catch(error => this.showError("Error attempting validation", error))
          .finally(() => this.validating = false);
      }
    },
    updateErrors: function () {
      function makeWidget(err) {
        let widget = document.createElement("div");
        widget.style.color = "#822";
        widget.style.backgroundColor = "rgba(255,197,199,0.44)";
        widget.innerHTML = err.error;
        return widget;
      }

      function makeMarker(doc, err) {
        let marker = document.createElement("div");
        marker.style.color = "#822";
        marker.style.marginLeft = "3px";
        marker.className = "validation-error";
        marker.innerHTML = '<i class="fa fa-exclamation-circle"></i>';
        marker.querySelector("i").setAttribute("title", err.error);
        marker.addEventListener("click", function () {
          if (marker.widget) {
            marker.widget.clear();
            delete marker.widget;
          } else {
            marker.widget = doc.addLineWidget(err.line - 1, makeWidget(err));
          }
        });
        return marker;
      }

      if (this.previewing && this.errors && this.editor) {
        let doc = this.editor.getDoc();
        this.errors.forEach(e => {
          doc.addLineClass(e.line - 1, 'background', 'line-error');
          doc.setGutterMarker(e.line - 1, 'validation-errors', makeMarker(doc, e));
        });
      }
    },
    setLoading:  function() {
      this.loading = true;
      this.$emit("loading");
    },
    load: function () {
      if (this.previewing === null) {
        return;
      }

      this.setLoading();
      this.api.fileUrls(this.datasetId, this.fileStage, [this.previewing.key])
        .then(data => this.worker.postMessage({
          type: 'preview',
          url: data[this.previewing.key],
          max: this.config.maxPreviewSize,
        }))
        .catch(error => this.showError('Unable to load preview URL', error))
        .finally(() => this.loading = false);
    },
    refresh: function() {
      this.editor.refresh();
    },
    receiveMessage: function(msg) {
      if (msg.data.error) {
        let errObj = msg.data.error;
        this.previewData = errObj.line
          ? "Error at line " + errObj.line + ": " + errObj.error
          : "Error: " + msg.data.error.error;
        this.editor.setOption("mode", null);
        this.loading = false;
        this.showingError = true;
      } else if (msg.data.init) {
        if (this.editor) {
          this.validate();
          this.editor.scrollTo(0, 0);
          this.editor.setOption("mode", "xml");
        }
        // Stop loading indicator when first data arrives
        this.loading = false;
        this.showingError = false;
        this.previewTruncated = false;
        this.previewData = msg.data.text;
      } else {
        this.previewData += msg.data.text;
        this.previewTruncated = msg.data.truncated;
      }
      if (msg.data.done) {
        this.$emit("loaded");
      }
    }
  },
  watch: {
    previewData: function (newValue, oldValue) {
      let editorValue = this.editor.getValue();
      if (newValue !== editorValue) {
        let scrollInfo = this.editor.getScrollInfo();
        this.editor.setValue(newValue);
        this.editor.scrollTo(scrollInfo.left, scrollInfo.top);
      }
      // FIXME: why is this only needed some times, e.g for convert
      // previews but not file previews?
      if (newValue !== oldValue) {
        if (!this.prettifying) {
          this.prettified = false;
        }
        this.refresh();
      }
    },
    previewing: function (newValue, oldValue) {
      if (!_.isEqual(newValue, oldValue)) {
        this.load();
      }
    },
    panelSize: function (newValue, oldValue) {
      if (newValue !== null && newValue !== oldValue) {
        this.refresh();
      }
    },
  },
  created: function() {
    this.worker = new Worker(this.config.previewLoader);
    this.worker.onmessage = this.receiveMessage;
  },
  mounted: function () {
    this.editor = CodeMirror.fromTextArea(this.$el.querySelector("textarea"), {
      mode: 'xml',
      lineWrapping: this.wrap,
      lineNumbers: true,
      readOnly: true,
      gutters: [{className: "validation-errors", style: "width: 18px"}]
    });
    this.editor.on("refresh", () => this.updateErrors());

    this.load();
  },
  beforeDestroy: function () {
    if (this.editor) {
      this.editor.toTextArea();
    }
    if (this.worker) {
      this.worker.terminate();
    }
  },
  template: `
    <div class="preview-container">
      <textarea>{{previewData}}</textarea>
      <div class="validation-loading-indicator" v-if="validating">
        <i class="fa fa-circle"></i>
      </div>
      <div class="valid-indicator" title="No errors detected"
           v-if="!validating && previewing && (_.isArray(errors) && errors.length === 0)">
        <i class="fa fa-check"></i>
      </div>
      <div class="preview-loading-indicator" v-if="loading">
        <i class="fa fa-3x fa-spinner fa-spin"></i>
      </div>
      <button v-else-if="!showingError"
              v-on:click="makePretty"
              v-bind:class="{'active': !prettified}"
              v-bind:disabled="previewTruncated || prettified"
              class="pretty-xml btn btn-sm"    
              title="Apply code formatting...">
        <i class="fa fa-code"></i>
      </button>
    </div>
  `
};

Vue.component("preview", {
  mixins: [previewPanelMixin]
});

Vue.component("convert-preview", {
  mixins: [previewPanelMixin],
  props: {
    mappings: Array,
    trigger: String,
    api: Object,
  },
  methods: {
    validate: function() {
      // FIXME: not yet supported
    },
    load: function() {
      if (this.previewing === null) {
        return;
      }

      this.setLoading();
      this.worker.postMessage({
        type: 'convert-preview',
        url: this.api.convertFileUrl(this.datasetId, this.fileStage, this.previewing.key),
        mappings: this.mappings,
        max: this.config.maxPreviewSize,
      });
    }
  },
  watch: {
    trigger: function() {
      this.load();
    },
    config: function(newConfig, oldConfig) {
      if (newConfig !== oldConfig && newConfig !== null) {
        console.log("Refresh convert preview...");
        this.load();
      }
    }
  }
});

Vue.component("modal-alert", {
  props: {
    title: String,
    cls: {
      type: String,
      default: 'danger'
    },
    accept: {
      type: String,
      default: "Okay"
    },
    cancel: {
      type: String,
      default: "Cancel"
    }
  },
  mounted() {
    this.$el.querySelector("button[data-dismiss='modal']").focus();
  },
  template: `
    <div v-bind:class="cls" class="modal modal-alert" tabindex="-1" role="dialog">
      <div class="modal-dialog modal-sm" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">{{title}}</h5>
            <button type="button" class="close" data-dismiss="modal" aria-label="Close" v-on:click="$emit('close')" tabindex="-1">
              <span aria-hidden="true">&times;</span>
            </button>
          </div>
          <div class="modal-body">
            <slot></slot>
          </div>
          <div class="modal-footer">
            <button v-if="cancel" type="button" class="btn" data-dismiss="modal" v-on:click="$emit('close')" autofocus>{{cancel}}</button>
            <button v-if="accept" type="button" class="btn" v-bind:class="'btn-' + cls" v-on:click="$emit('accept')">{{accept}}</button>
          </div>
        </div>
      </div>
    </div>
  `
});

Vue.component("files-table", {
  props: {
    api: DAO,
    fileStage: String,
    loadingMore: Boolean,
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
    filter: String,
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
    toggleAll: function (evt) {
      this.files.forEach(f => this.toggleItem(f, evt));
    },
    toggleItem: function (file, evt) {
      if (evt.target.checked) {
        this.$emit('item-selected', file);
      } else {
        this.$emit('item-deselected', file);
      }
    },
    isPreviewing: function(file) {
      return this.previewing !== null && this.previewing.key === file.key;
    }
  },
  watch: {
    selected: function (newValue) {
      let selected = Object.keys(newValue).length;
      let checkAll = this.$el.querySelector("#" + this.fileStage + "-checkall");
      if (checkAll) {
        checkAll.indeterminate = selected > 0 && selected !== this.files.length;
      }
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
          <td><input type="checkbox" v-bind:checked="selected[file.key]" v-on:click.stop="toggleItem(file, $event)">
          </td>
          <td>{{file.key}}</td>
          <td v-bind:title="file.lastModified">{{file.lastModified | prettyDate}}</td>
          <td>{{file.size | humanFileSize(true)}}</td>
          
          <td v-if="validating !== null">
            <a href="#" v-on:click.prevent.stop="$emit('validate-files', _.fromPairs([[file.eTag, file.key]]))">
                <i v-if="validating[file.eTag]" class="fa fa-fw fa-circle-o-notch fa-spin"></i>
                <i v-else-if="validationResults && validationResults[file.eTag]" class="fa fa-fw" v-bind:class="{
                    'fa-check text-success': validationResults[file.eTag].length === 0,
                    'fa-exclamation-circle text-danger': validationResults[file.eTag].length > 0
                    }">
                </i>
              <i v-else class="fa fa-fw fa-flag-o"></i>
            </a>
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
      <button class="btn btn-sm btn-default" v-if="truncated" v-on:click.prevent.stop="$emit('load-more')">
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

Vue.component("file-picker-suggestion", {
  props: {selected: Boolean, item: Object,},
  template: `
    <div @click="$emit('selected', item)" class="file-picker-suggestion" v-bind:class="{'selected': selected}">
        {{ item.key }} 
    </div>
  `
});

Vue.component("file-picker", {
  props: {
    datasetId: String,
    fileStage: String,
    value: Object,
    disabled: Boolean,
    api: Object,
    config: Object
  },
  data: function () {
    return {
      text: null,
      selectedIdx: -1,
      suggestions: [],
      loading: false,
      showSuggestions: false,
    }
  },
  methods: {
    search: function () {
      this.loading = true;
      let list = () => {
        this.api.listFiles(this.datasetId, this.fileStage, this.text ? this.text : "")
          .then(data => {
            this.suggestions = data.files;
            this.showSuggestions = true;
          })
          .finally(() => this.loading = false);
      }
      _.debounce(list, 300)();
    },
    selectPrev: function () {
      this.selectedIdx = Math.max(-1, this.selectedIdx - 1);
    },
    selectNext: function () {
      this.selectedIdx = Math.min(this.suggestions.length, this.selectedIdx + 1);
    },
    setAndChooseItem: function (item) {
      this.$emit("input", item);
      this.cancelComplete();
      this.text = null;
    },
    setItemFromSelection: function () {
      let idx = this.selectedIdx,
        len = this.suggestions.length;
      if (idx > -1 && len > 0 && idx < len) {
        this.setAndChooseItem(this.suggestions[idx]);
      } else if (idx === -1) {
        this.$emit('input', null);
      }
    },
    cancelComplete: function () {
      this.$nextTick(() => {
        this.suggestions = [];
        this.selectedIdx = -1;
        this.showSuggestions = false;
      })
    }
  },
  template: `
    <div class="file-picker">
      <div class="file-picker-input-container">
        <div v-show="showSuggestions" class="dropdown-backdrop" v-on:click="cancelComplete"></div>
        <label class="control-label sr-only">File:</label>
        <input class="file-picker-input form-control form-control-sm" type="text" placeholder="Select file to preview"
               v-bind:disabled="disabled"
               v-bind:value="text !== null ? text : (value ? value.key : '')"
               v-on:focus="search"
               v-on:input="text = $event.target.value; search()"
               v-on:keydown.up="selectPrev"
               v-on:keydown.down="selectNext"
               v-on:keydown.enter="setItemFromSelection"
               v-on:keydown.esc="cancelComplete"/>
        <i v-if="loading" class="loading-indicator fa fa-circle-o-notch fa-fw fa-spin"></i>
        <div v-if="showSuggestions" class="file-picker-suggestions dropdown-list">
          <file-picker-suggestion
            v-for="(suggestion, i) in suggestions"
            v-bind:class="{selected: i === selectedIdx}"
            v-bind:key="suggestion.key"
            v-bind:item="suggestion"
            v-bind:selected="i === selectedIdx"
            v-on:selected="setAndChooseItem"/>
          <div v-if="!loading && suggestions.length === 0" class="file-picker-suggestions-empty">
            No files found...
          </div>
        </div>
      </div>
    </div>
  `
});

Vue.component("filter-control", {
  props: {
    filter: Object
  },
  template: `
    <div class="filter-control">
      <label class="sr-only">Filter files</label>
      <input class="filter-input form-control form-control-sm" type="text" v-model.trim="filter.value"
             placeholder="Filter files..." v-on:keyup="$emit('filter')"/>
      <i class="filtering-indicator fa fa-circle-o-notch fa-fw fa-spin" v-if="filter.active"/>
      <i class="filtering-indicator fa fa-close fa-fw" style="cursor: pointer" v-on:click="$emit('clear')" v-else-if="filter.value"/>
    </div>
  `
});

Vue.component("log-window", {
  props: {
    log: Array,
  },
  updated: function () {
    this.$el.scrollTop = this.$el.scrollHeight;
  },
  template: `
    <pre v-if="log.length > 0"><template v-for="msg in log"><span v-html="msg"></span><br/></template></pre>
  `
});

Vue.component("drag-handle", {
  props: {
    ns: String,
    p2: Element,
    container: Element,
  },
  data: function () {
    return {
      offset: 0,
    }
  },

  methods: {
    move: function (evt) {
      // Calculate the height of the topmost panel in percent.
      let maxY = this.container.offsetTop + this.container.offsetHeight;
      let topY = this.container.offsetTop;
      let posY = evt.clientY - this.offset;

      let pxHeight = Math.min(maxY, Math.max(0, posY - topY));
      let percentHeight = pxHeight / this.container.offsetHeight * 100;

      // Now convert to the height of the lower panel.
      let perc = 100 - percentHeight;
      this.p2.style.flexBasis = perc + "%";
    },
    startDrag: function (evt) {
      let us = this.container.style.userSelect;
      let cursor = this.container.style.cursor;
      this.offset = evt.clientY - this.$el.offsetTop;
      this.container.addEventListener("mousemove", this.move);
      this.container.style.userSelect = "none";
      this.container.style.cursor = "ns-resize";
      window.addEventListener("mouseup", () => {
        console.log("Stop resize");
        this.offset = 0;
        this.$emit("resize", this.p2.clientHeight);
        this.container.style.userSelect = us;
        this.container.style.cursor = cursor;
        this.container.removeEventListener("mousemove", this.move);
      }, {once: true});
    },
  },
  template: `
    <div v-bind:id="ns + '-drag-handle'" class="drag-handle" v-on:mousedown="startDrag"></div>
  `
});

Vue.component("modal-window", {
  template: `
    <div class="modal show fade" tabindex="-1" role="dialog" style="display: block">
      <div class="modal-dialog modal-dialog-centered" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">
                <slot name="title"></slot>
            </h5>
            <button type="button" class="close" data-dismiss="modal" aria-label="Close" v-on:click="$emit('close')">
              <span aria-hidden="true">&times;</span>
            </button>
          </div>
          <div class="modal-body">
            <slot></slot>
          </div>
          <div class="modal-footer">
            <slot name="footer"></slot>
          </div>
        </div>
      </div>
    </div>
  `
});


let initialStageState = function() {
  return {
    loaded: false,
    loadingMore: false,
    truncated: false,
    tab: 'preview',
    previewing: null,
    deleting: {},
    downloading: {},
    selected: {},
    filter: {
      value: "",
      active: false
    },
    files: [],
    log: [],
  };
};

let stageMixin = {
  props: {
    datasetId: String,
    active: Boolean,
  },
  data: function() {
    return initialStageState();
  },
  computed: {
    selectedKeys: function () {
      return Object.keys(this.selected);
    },
    selectedTags: function() {
      return _.invert(_.mapValues(this.selected, f => f.eTag));
    }
  },
  methods: {
    reset: function() {
      Object.assign(this.$data, initialStageState());
    },
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
      return this.api.listFiles(this.datasetId, this.fileStage, this.filter.value)
        .then(data => {
          this.files = data.files;
          this.truncated = data.truncated;
        })
        .catch(error => this.showError("Error listing files", error))
        .finally(() => this.loaded = true);
    },
    loadMore: function () {
      this.loadingMore = true;
      let from = this.files.length > 0
        ? this.files[this.files.length - 1].key
        : null;
      return this.api.listFiles(this.datasetId, this.fileStage, this.filter.value, from)
        .then(data => {
          this.files.push.apply(this.files, data.files);
          this.truncated = data.truncated;
        })
        .catch(error => this.showError("Error listing files", error))
        .finally(() => this.loadingMore = false);
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
      this.api.deleteFiles(this.datasetId, this.fileStage, keys)
        .then(deleted => {
          deleted.forEach(key => {
            this.$delete(this.deleting, key);
            this.$delete(this.selected, key);
          });
          this.refresh();
        })
        .catch(error => this.showError("Error deleting files", error))
        .finally(() => this.deleting = {});
    },
    deleteAll: function () {
      this.previewing = null;
      this.files.forEach(f => this.$set(this.deleting, f.key, true));
      return this.api.deleteAll(this.datasetId, this.fileStage)
        .then(r => {
          this.load();
          r;
        })
        .catch(error => this.showError("Error deleting files", error))
        .finally(() => this.deleting = {});
    },
    selectItem: function(file) {
      this.$set(this.selected, file.key, file);
    },
    deselectItem: function(file) {
      this.$delete(this.selected, file.key);
    },
    showError: function() {}, // Overridden by inheritors
    deselect: function() {
      this.previewing = null;
    }
  },
  watch: {
    active: function(newValue) {
      if (newValue) {
        this.load();
      }
    },
    datasetId: function() {
      this.reset();
      this.load();
    }
  },
  created: function() {
    this.load();
  },
};
