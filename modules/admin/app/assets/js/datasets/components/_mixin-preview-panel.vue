<script lang="ts">

import {nextTick} from "vue";
import MixinError from './_mixin-error';
import {basicSetup, EditorView} from 'codemirror'
import {json} from '@codemirror/lang-json';
import {xml} from '@codemirror/lang-xml';

import _find from 'lodash/find';
import _isEqual from 'lodash/isEqual';
import _isArray from 'lodash/isArray';
import _fromPairs from 'lodash/fromPairs';
import {FileMeta} from "../types";
import {DatasetManagerApi} from "../api";
import PanelTabularView from "./_panel-tabular-view.vue";
import {Compartment, EditorState} from "@codemirror/state";

import {setValidationErrors, validationExtension} from "../codemirror-error-ext";

export default {
  components: {PanelTabularView},
  mixins: [MixinError],
  props: {
    api: DatasetManagerApi,
    datasetId: String,
    contentType: String,
    fileStage: String,
    panelSize: Number,
    previewing: Object,
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
      lineHandles: [],
      firstLoad: true,
      editor: null as EditorView | null,
      compartment: null as Compartment | null,
    }
  },
  methods: {
    _isArray,
    isCode: function() {
      // For historical reasons a null contentType means 'XML'
      return !this.contentType || (this.contentType.includes("json") || this.contentType.includes("xml"));
    },
    isTabular: function() {
      return this.contentType && (this.contentType.includes("csv") || this.contentType.includes("tsv"));
    },
    canValidate: function() {
      return !this.contentType || this.contentType.includes("xml");
    },
    codeMode: function() {
      if (this.isCode() && this.contentType && this.contentType.includes("json")) {
        return json();
      } else {
        return xml();
      }
    },
    prettifyData: function(data: string): string {
      if (!this.contentType || this.contentType.includes("xml")) {
        return this.prettifyXml(data);
      } else if (this.contentType && this.contentType.includes("json")) {
        try {
          let json = JSON.parse(data);
          return JSON.stringify(json, null, 2);
        } catch (e) {
          return data;
        }
      } else {
        return data;
      }
    },
    prettifyXml: function (xml: string): string {
      // FIXME: if/when XSLT gets removed from browsers this will stop working...
      let stripBom = xml.charAt(0) == '\uFEFF' ? xml.substring(1) : xml;
      let parser = new DOMParser();
      let xmlDoc = parser.parseFromString(stripBom, 'application/xml');
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
    makePretty: function () {
      this.prettifying = true;
      this.previewData = this.prettifyData(this.previewData);
      nextTick(() => {
        this.prettified = true
        this.prettifying = false;
      });
    },
    validate: function (): void {
      if (this.previewing === null) {
        return;
      }

      if (this.validationResults[this.previewing.eTag]) {
        this.errors = this.validationResults[this.previewing.eTag];
        this.updateErrors();
      } else {
        this.validating = true;
        if (this.canValidate()) {
          let tagToKey = _fromPairs([[this.previewing.eTag, this.previewing.key]]);
          this.api.validateFiles(this.datasetId, this.fileStage, tagToKey)
              .then(errors => {
                let e = _find(errors, e => this.previewing.eTag === e.eTag);
                if (e) {
                  this.errors = e.errors;
                  this.updateErrors()
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
    updateErrors: function () {
      this.editor?.dispatch({
        effects: setValidationErrors.of(this.errors ?? [])
      })
    },
    setLoading: function () {
      this.loading = true;
      this.$emit("loading");
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
    refresh: function () {
      this.editor?.requestMeasure();
    },
    receiveMessage: function (msg: object) {
      if (msg.data.error) {
        let errObj = msg.data.error;
        this.previewData = errObj.line
            ? "Error at line " + errObj.line + ": " + errObj.error
            : "Error: " + msg.data.error.error;
        this.editor && this.compartment.reconfigure([]);
        this.loading = false;
        this.showingError = true;
      } else if (msg.data.init) {
        if (this.editor) {
          this.validate();
          this.compartment.reconfigure(this.codeMode());

          // On the first load of a given file scroll back
          // to the beginning...
          if (this.firstLoad) {
            this.editor.dispatch({
              effects: EditorView.scrollIntoView(0)
            })
            this.firstLoad = false;
          }
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
    previewData: function (incoming: string, oldValue: string) {
      if (this.editor) {
        const current = this.editor?.state.doc.toString()
        if (current !== incoming) {
          const scrollTop = this.editor.scrollDOM.scrollTop
          this.editor.dispatch({
            changes: { from: 0, to: this.editor.state.doc.length, insert: incoming }
          })
          requestAnimationFrame(() => {
            this.editor.scrollDOM.scrollTop = scrollTop
          })
        }

        // FIXME: why is this only needed some times, e.g for convert
        // previews but not file previews?
        if (incoming !== oldValue) {
          if (!this.prettifying) {
            this.prettified = false;
          }
          this.refresh();
        }
      }
    },
    previewing: function (newValue: FileMeta, oldValue: FileMeta) {
      if (!_isEqual(newValue, oldValue)) {
        this.load();
      }
      if (newValue && oldValue && newValue.key !== oldValue.key) {
        this.firstLoad = true;
      }
    },
    panelSize: function (newValue: number, oldValue: number) {
      if (newValue !== null && newValue !== oldValue) {
        this.refresh();
      }
    },
  },
  created: function () {
    this.worker = new Worker(this.config.websocketHandler);
    this.worker.onmessage = this.receiveMessage;
  },
  mounted: function () {
    if (this.isCode()) {
      console.log("Code mode: ", this.codeMode());

      this.compartment = new Compartment();
      let state = EditorState.create({
        extensions: [
          basicSetup,
          xml(),
          json(),
          this.compartment.of(this.codeMode()),
          EditorState.readOnly.of(true),
          EditorView.editable.of(false),
          EditorView.updateListener.of(update => {
            if (update.docChanged) {
              this.updateErrors();
            }
          }),
          ...validationExtension,
        ]
      });

      this.editor = new EditorView({
        state,
        doc: this.previewData,
        parent: this.$el.querySelector('.preview-data-container'),
      });
      this.editor?.dispatch({
        effects: setValidationErrors.of(this.errors ?? [])
      });
    } else if (this.isTabular()) {

    }

    this.load();
  },
  beforeUnmount: function () {
    this.editor?.destroy();
    if (this.worker) {
      this.worker.terminate();
    }
  },
}
</script>

<template>
  <div class="preview-container">
    <panel-tabular-view v-if="isTabular()" v-bind:data="previewData" v-bind:content-type="contentType"></panel-tabular-view>
<!--    <textarea v-else class="preview-data-container" v-bind:name="datasetId + '-' + fileStage" readonly>{{previewData}}</textarea>-->
    <div v-else class="preview-data-container"></div>
    <div class="validation-loading-indicator" v-if="canValidate() && validating">
      <i class="fa fa-circle"></i>
    </div>
    <div class="valid-indicator" title="No errors detected"
         v-if="!validating && previewing && (_isArray(errors) && errors.length === 0)">
      <i class="fa fa-check"></i>
    </div>
    <div class="preview-loading-indicator" v-if="loading">
      <i class="fa fa-3x fa-spinner fa-spin"></i>
    </div>
    <button v-else-if="isCode() && !showingError"
            v-on:click="makePretty"
            v-bind:class="{'active': !prettified}"
            v-bind:disabled="previewTruncated || prettified"
            class="prettify-data btn btn-sm"
            title="Apply code formatting...">
      <i class="fa fa-code"></i>
    </button>
  </div>
</template>
