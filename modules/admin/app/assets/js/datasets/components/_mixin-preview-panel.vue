<script lang="ts">

import MixinError from './_mixin-error';

import _find from 'lodash/find';
import _isEqual from 'lodash/isEqual';
import _isArray from 'lodash/isArray';
import _fromPairs from 'lodash/fromPairs';
import {FileMeta} from "../types";
import {DatasetManagerApi} from "../api";
import PanelTabularView from "./_panel-tabular-view";
import PanelCodeView from "./_panel-code-view";

export default {
  components: {PanelTabularView, PanelCodeView},
  mixins: [MixinError],
  emits: ["loading", "loaded", "validation-results"],
  props: {
    api: DatasetManagerApi,
    datasetId: String,
    contentType: String,
    fileStage: String,
    panelSize: Number,
    previewing: Object as FileMeta | null,
    config: Object,
    maxSize: Number,
    validationResults: {
      type: Object,
      default: function () {
        return {}
      },
    },
  },
  data: function () {
    return {
      loading: false,
      validating: false,
      previewData: null as string | null,
      previewTruncated: false,
      percentDone: 0,
      wrap: false,
      prettifying: false,
      prettified: false,
      showingError: false,
      worker: null,
      errored: false,
      errors: null,
      lineHandles: [],
      firstLoad: true,
    }
  },
  methods: {
    _isArray,
    isCode: function() {
      return !this.isTabular();
    },
    isTabular: function() {
      return this.contentType && (this.contentType.includes("csv") || this.contentType.includes("tsv"));
    },
    canValidate: function() {
      // For historical reasons a null contentType means 'XML'
      return !this.contentType || this.contentType.includes("xml");
    },
    validate: function (): void {
      if (this.previewing === null) {
        return;
      }

      // Check if we have previously validated this file
      if (this.validationResults[this.previewing.eTag]) {
        this.errors = this.validationResults[this.previewing.eTag];
      } else {
        if (this.canValidate()) {
          console.log("Validating", this.previewing.key)
          this.validating = true;
          let tagToKey = _fromPairs([[this.previewing.eTag, this.previewing.key]]);
          this.api.validateFiles(this.datasetId, this.fileStage, tagToKey)
              .then(errors => {
                let e = _find(errors, e => this.previewing.eTag === e.eTag);
                if (e) {
                  this.errors = e.errors;
                  this.$emit("validation-results", this.previewing.eTag, e.errors);
                }
              })
              .catch(error => this.showError("Error attempting validation", error))
              .finally(() => this.validating = false);
        } else {
          this.errors = null;
        }
      }
    },
    setLoading: function () {
      this.loading = true;
      this.$emit("loading");
    },
    prettyPrint: function(): void {
      if (this.previewing === null) {
        return;
      }

      let prettyPrintUrl = this.api.reformatUrl(this.datasetId, this.fileStage, [this.previewing.key])
      this.worker.postMessage({
        type: 'preview',
        url: prettyPrintUrl,
        contentType: this.contentType,
        max: this.config.maxPreviewSize,
      });
    },
    load: function (): void {
      if (this.previewing === null) {
        return;
      }

      this.setLoading();
      this.api.fileUrls(this.datasetId, this.fileStage, [this.previewing.key])
          .then(data => this.worker.postMessage({
            type: 'preview',
            url: data[this.previewing.key],
            contentType: this.contentType,
            max: this.config.maxPreviewSize,
          }))
          .catch(error => this.showError('Unable to load preview URL', error))
          .finally(() => this.loading = false);
    },
    receiveMessage: function (msg: object) {
      if (msg.data.error) {
        let errObj = msg.data.error;
        this.previewData = errObj.line
            ? "Error at line " + errObj.line + ": " + errObj.error
            : "Error: " + msg.data.error.error;
        this.loading = false;
        this.showingError = true;
      } else if (msg.data.init) {
        this.validate();
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
    previewing: function (newValue?: FileMeta, oldValue?: FileMeta) {
      if (!_isEqual(newValue, oldValue)) {
        this.load();
      }
      if (newValue && oldValue && newValue.key !== oldValue.key) {
        this.firstLoad = true;
      }
    },
  },
  created: function () {
    this.worker = new Worker(this.config.websocketHandler);
    this.worker.onmessage = this.receiveMessage;
  },
  mounted: function () {
    this.load();
  },
  beforeUnmount: function () {
    this.worker?.terminate();
  },
}
</script>

<template>
  <div class="preview-container">
    <panel-tabular-view v-if="isTabular()" v-bind:data="previewData" v-bind:content-type="contentType"></panel-tabular-view>
    <panel-code-view v-else
                     v-model="previewData"
                     v-bind:content-type="contentType"
                     v-bind:read-only="true"
                     v-bind:errors="errors"
                     v-bind:file-key="previewing ? previewing.key : null"
                    />
    <div class="validation-loading-indicator" v-if="canValidate() && validating">
      <i class="fa fa-circle"></i>
    </div>
    <div v-if="loading" class="preview-loading-indicator">
      <i class="fa fa-3x fa-spinner fa-spin"></i>
    </div>
    <button v-else-if="isCode() && !showingError"
            v-on:click="prettyPrint"
            v-bind:class="{'active': !prettified}"
            v-bind:disabled="previewTruncated || prettified"
            class="prettify-data btn btn-sm"
            title="Apply code formatting...">
      <i class="fa fa-code"></i>
    </button>
  </div>
</template>
