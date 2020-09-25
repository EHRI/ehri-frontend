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
    removeQueryParam: function(qs, name) {
      let qp = this.queryParams(qs);
      return this.queryString(_.omit(qp, name));
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
    api: Object,
  },
  data: function() {
    return {
      validating: {},
      validationResults: {},
    }
  },
  computed: {
    validationLog: function () {
      let log = [];
      this.files.forEach(file => {
        let key = file.key;
        let errs = this.validationResults[key];
        if (errs) {
          let cls = errs.length === 0 ? "text-success" : "text-danger";
          log.push('<span class="' + cls + '">' + key + '</span>' + ":" + (errs.length === 0 ? " ✓" : ""));
          errs.forEach(err => {
            log.push("    " + err.line + "/" + err.pos + " - " + err.error);
          });
        }
      });
      return log;
    }
  },
  methods: {
    validateFiles: function (keys) {
      keys.forEach(key => this.$set(this.validating, key, true));
      keys.forEach(key => this.$delete(this.validationResults, key));
      this.api.validateFiles(this.fileStage, keys)
        .then(errs => {
          this.tab = 'validation';
          keys.forEach(key => {
            this.$set(this.validationResults, key, errs[key] ? errs[key] : []);
            this.$delete(this.validating, key);
          });
        })
        .catch(error => this.showError("Error attempting validation", error));
    },
  }
};

let previewPanelMixin = {
  mixins: [errorMixin],
  props: {
    fileStage: String,
    panelSize: Number,
    previewing: Object,
    errors: Object,
    config: Object,
    api: Object,
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

      this.validating = true;
      this.api.validateFiles(this.fileStage, [this.previewing.key])
        .then(errs => {
          this.$set(this.errors, this.previewing.key, errs[this.previewing.key]);
          this.updateErrors();
          this.validating = false;
        })
        .catch(error => this.showError("Error attempting validation", error));
    },
    updateErrors: function () {
      if (this.previewing && this.errors[this.previewing.key] && this.editor) {
        let doc = this.editor.getDoc();

        function makeMarker(err) {
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

        function makeWidget(err) {
          let widget = document.createElement("div");
          widget.style.color = "#822";
          widget.style.backgroundColor = "rgba(255,197,199,0.44)";
          widget.innerHTML = err.error;
          return widget;
        }

        this.errors[this.previewing.key].forEach(e => {
          doc.addLineClass(e.line - 1, 'background', 'line-error');
          doc.setGutterMarker(e.line - 1, 'validation-errors', makeMarker(e));
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
      this.api.fileUrls(this.fileStage, [this.previewing.key])
        .then(data => this.worker.postMessage({
          type: 'preview',
          url: data[this.previewing.key]
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
        this.previewData = msg.data.text;
      } else {
        this.previewData += msg.data.text;
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
           v-if="!validating && previewing && errors[previewing.key] && errors[previewing.key].length === 0">
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
        url: this.api.convertFileUrl(this.fileStage, this.previewing.key),
        src: [],
        mappings: this.mappings,
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

Vue.component("file-picker-suggestion", {
  props: {selected: Boolean, item: Object,},
  template: `
    <div @click="$emit('selected', item)" class="file-picker-suggestion" v-bind:class="{'selected': selected}">
        {{ item.key }} 
    </div>
  `
});

Vue.component("file-picker", {
  props: {value: Object, type: String, disabled: Boolean, api: Object},
  data: function () {
    return {
      text: this.value ? this.value.key : "",
      selectedIdx: -1,
      suggestions: [],
      loading: false,
    }
  },
  methods: {
    search: function () {
      this.loading = true;
      let list = () => {
        this.api.listFiles(this.type, this.text).then(data => {
          this.loading = false;
          this.suggestions = data.files;
        });
      }
      _.debounce(list, 300)();
    },
    selectPrev: function () {
      this.selectedIdx = Math.max(-1, this.selectedIdx - 1);
      this.setItemFromSelection();
    },
    selectNext: function () {
      this.selectedIdx = Math.min(this.suggestions.length, this.selectedIdx + 1);
      this.setItemFromSelection();
    },
    setAndChooseItem: function (item) {
      this.$emit("input", item);
      this.cancelComplete();
      this.text = item ? item.key : "";
    },
    setItemFromSelection: function () {
      let idx = this.selectedIdx,
        len = this.suggestions.length;
      if (idx > -1 && len > 0 && idx < len) {
        this.setItem(this.suggestions[idx]);
      } else if (idx === -1) {
        this.$emit('input', null);
      }
    },
    cancelComplete: function () {
      this.suggestions = [];
      this.selectedIdx = -1;
    }
  },
  template: `
    <div class="file-picker">
      <label class="control-label sr-only">File:</label>
      <input class="form-control" type="text" placeholder="Select file to preview"
        v-bind:disabled="disabled"
        v-model="text"
        v-on:focus="search"
        v-on:input="search"
        v-on:keydown.up="selectPrev"
        v-on:keydown.down="selectNext"
        v-on:keydown.esc="cancelComplete"/>
      <div class="dropdown-list" v-if="suggestions.length">
        <div class="file-picker-suggestions">
          <file-picker-suggestion
              v-for="(suggestion, i) in suggestions"
              v-bind:class="{selected: i === selectedIdx}"
              v-bind:key="suggestion.key"
              v-bind:item="suggestion"
              v-bind:selected="i === selectedIdx"
              v-on:selected="setAndChooseItem"/>
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


