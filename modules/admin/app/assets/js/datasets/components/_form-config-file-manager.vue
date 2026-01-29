<script lang="ts">

import _pick from "lodash/pick";
import _forIn from "lodash/forIn";
import {FileMeta} from "../types";
import {timeToRelative} from "../common";
import {DatasetManagerApi} from "../api";
import _size from "lodash/size";

export default {
  props: {
    modelValue: String,
    title: String,
    inputId: String,
    suffix: String,
    datasetId: String,
    api: DatasetManagerApi,
    config: Object,
    disabled: Boolean,
    required: Boolean,
  },
  data: function () {
    return {
      configFile: this.modelValue,
      configOptions: [],
      loading: false,
    }
  },
  methods: {
    uploadConfig: async function (event: InputEvent | DragEvent) {
      let fileList = event.dataTransfer ? event.dataTransfer.files : event.target.files;
      if (fileList.length == 0) {
        return;
      }
      let file: FileList = fileList[0];

      // NB: the fileStage arg here is 'config', since we are uploading a config file, rather then
      // the stage of the ingest manager ('output').
      try {
        this.loading = true;
        let fileSpec = _pick(file, ['name', 'type', 'size']);
        fileSpec['meta'] = {source: 'user'};
        let data = await this.api.uploadHandle(this.datasetId, this.config.config, fileSpec)
        await this.api.uploadFile(data.presignedUrl, file, () => true);
        this.configFile = file.name;
        await this.loadConfigOptions();
        this.update();
        if (event.target.files) {
          event.target.files = null;
        }
        event.target.value = '';
      } catch (e) {
        this.error = "Error uploading config: " + e;
      } finally {
        this.loading = false;
      }
    },
    downloadConfig: async function (key) {
      this.loading = true;
      this.$emit("update");
      try {
        let urls = await this.api.fileUrls(this.datasetId, this.config.config, [key]);
        _forIn(urls, (url, fileName) => window.open(url, key));
      } catch (e) {
        this.error = "Error fetching download URLs" + e;
      } finally {
        this.loading = false;
      }
    },
    deleteConfig: async function (file: FileMeta) {
      this.loading = true;
      if (file.key === this.configFile) {
        this.configFile = null;
      }
      try {
        await this.api.deleteFiles(this.datasetId, this.config.config, [file.key]);
        await this.loadConfigOptions();
        this.update();
      } finally {
        this.loading = false;
      }
    },
    selectConfigFile: function (file: FileMeta) {
      this.configFile = this.configFile === file.key ? null : file.key;
      this.update();
    },
    prettyDate: timeToRelative,
    update: function () {
      this.$emit("update:modelValue", this.configFile);
    },
    loadConfigOptions: async function () {
      this.loading = true;
      try {
        let data = await this.api.listFiles(this.datasetId, this.config.config);
        this.configOptions = data.files.filter(f => f.key.endsWith(this.suffix));
      } catch (e) {
        this.showError("Error loading files", e);
      } finally {
        this.loading = false;
      }
    },
  },
  computed: {
    hasConfigs: function () {
      return _size(this.configOptions) > 0;
    },
    patternRegex: function() {
      return this.suffix ? (".*\\" + this.suffix + "$") : ".*";
    }
  },
  watch: {
    show: async function () {
      await this.update();
    },
  },
  async created() {
    await this.loadConfigOptions();
  }
}
</script>

<template>
  <div class="config-file-manager">
      <div class="form-group">
          <div class="input-group">
              <label class="form-label" v-bind:for="inputId">
                  <i class="fa fa-plus-circle" v-bind:class="{
                'text-success': required && hasConfigs,
                'text-warning': required && !hasConfigs,
              }"></i>
                  {{ title }}
              </label>
              <input
                      v-on:change.prevent="uploadConfig"
                      v-bind:pattern="patternRegex"
                      v-bind:accept="suffix"
                      v-bind:required="required"
                      v-bind:id="inputId"
                      class="sr-only"
                      type="file" />
          </div>
          <div v-if="hasConfigs" class="config-options-selector-container">
              <table v-if="hasConfigs" class="config-options-selector table table-bordered table-sm table-striped">
                  <tr>
                      <th></th>
                      <th>File</th>
                      <th>Last Modified</th>
                      <th></th>
                      <th></th>
                  </tr>
                  <tr v-for="f in configOptions" v-on:click="selectConfigFile(f)" v-bind:class="{'active': f.key===configFile}">
                      <td><i v-bind:class="{
                  'fa-check': f.key===configFile,
                  'text-success': f.key===configFile,
                  'fa-minus': f.key!==configFile,
                  'text-muted': f.key!==configFile,
                }" class="fa fa-fw"></i></td>
                      <td>{{ f.key }}</td>
                      <td v-bind:title="f.lastModified">{{ prettyDate(f.lastModified) }}</td>
                      <td title="Download config file" v-on:click.stop.prevent="downloadConfig(f.key)"><i class="fa fa-download"></i></td>
                      <td title="Delete config file" v-on:click.stop.prevent="deleteConfig(f)"><i class="fa fa-trash-o"></i></td>
                  </tr>
              </table>
              <div v-else-if="loading" class="panel-placeholder">
                  Loading file list...
              </div>
              <div v-else class="panel-placeholder">
                  No file loaded...
                  <input class="option-new-config-input" type="file" v-bind:pattern="patternRegex" v-on:change.prevent="uploadConfig"/>
              </div>
          </div>
      </div>
  </div>
</template>
