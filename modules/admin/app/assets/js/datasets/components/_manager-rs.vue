<script lang="ts">

import FilterControl from './_filter-control';
import ButtonValidate from './_button-validate';
import ButtonDelete from './_button-delete';
import FilesTable from './_files-table';
import FastFilesTable from './_fast-files-table';
import DragHandle from './_drag-handle';
import PanelFilePreview from './_panel-file-preview';
import ModalInfo from './_modal-info';
import PanelLogWindow from './_panel-log-window';
import ModalRsConfig from './_modal-rs-config';

import MixinTwoPanel from './_mixin-two-panel';
import MixinValidator from './_mixin-validator';
import MixinPreview from './_mixin-preview';
import MixinError from './_mixin-error';
import MixinStage from './_mixin-stage';
import MixinUtil from './_mixin-util';
import MixinTasklog from './_mixin-tasklog';

import {DatasetManagerApi} from '../api';
import {ResourceSyncConfig} from "../types";


export default {
  components: {FilterControl, FilesTable, FastFilesTable, PanelLogWindow, DragHandle, ModalInfo, PanelFilePreview, ButtonValidate, ButtonDelete, ModalRsConfig},
  mixins: [MixinStage, MixinTwoPanel, MixinPreview, MixinValidator, MixinError, MixinUtil, MixinTasklog],
  props: {
    datasetContentType: String,
    fileStage: String,
    urlKey: {
      type: String,
      default: 'sync-id',
    },
    config: Object,
    api: DatasetManagerApi,
  },
  data: function () {
    return {
      showOptions: false,
      syncConfig: null,
      waiting: false,
    }
  },
  methods: {
    doSync: async function (opts: ResourceSyncConfig) {
      this.tab = "sync";
      this.waiting = true;
      this.syncConfig = opts;

      try {
        let {url, jobId} = await this.api.sync(this.datasetId, opts);
        this.showOptions = false;
        this.replaceUrlState(this.urlKey, jobId);
        await this.monitor(url, jobId, this.refresh);
        this.$emit('updated');
      } catch (e) {
        this.showError("Error running sync", e)
      } finally {
        this.removeUrlState(this.urlKey);
        this.waiting = false;
      }
    },
    resumeMonitor: async function () {
      let jobId = this.getQueryParam(window.location.search, this.urlKey);
      if (jobId) {
        try {
          this.tab = "sync";
          await this.monitor(this.config.monitorUrl(jobId), jobId, this.refresh);
          this.$emit("updated");
        } finally {
          this.removeUrlState(this.urlKey);
        }
      }
    },
    loadConfig: async function () {
      this.syncConfig = await this.api.getSyncConfig(this.datasetId);
    },
  },
  created: function () {
    this.loadConfig();
    this.resumeMonitor();
  }
}
</script>
<template>
  <div id="rs-manager-container" class="stage-manager-container">
    <div class="actions-bar">
      <filter-control v-bind:filter="filter"
                      v-on:filter="filterFiles"
                      v-on:clear="clearFilter"/>

      <button-validate
          v-bind:selected="selectedKeys.length"
          v-bind:disabled="files.length === 0 || jobId !== null"
          v-bind:active="validationRunning"
          v-on:validate="validateFiles(selectedTags)"
      />

      <button-delete
          v-bind:selected="selectedKeys.length"
          v-bind:disabled="files.length === 0 || jobId !== null"
          v-bind:active="deleting.length > 0"
          v-on:delete="deleteFiles(selectedKeys)"
      />

      <button v-if="!jobId" class="btn btn-sm btn-default"
              v-on:click.prevent="showOptions = !showOptions">
        <i class="fa fa-fw fa-clone"/>
        Sync Files...
      </button>
      <button v-else v-bind:disabled="cancelling" class="btn btn-sm btn-outline-danger" v-on:click.prevent="cancelJob">
        <i class="fa fa-fw fa-spin fa-circle-o-notch"></i>
        Cancel Sync
      </button>

      <modal-rs-config
          v-if="showOptions"
          v-bind:waiting="waiting"
          v-bind:dataset-id="datasetId"
          v-bind:sync-config="syncConfig"
          v-bind:api="api"
          v-bind:config="config"
          v-on:saving="waiting = true"
          v-on:saved-config="doSync"
          v-on:deleted-orphans="refresh"
          v-on:error="showError"
          v-on:close="showOptions = false"/>

      <modal-info v-if="fileInfo !== null" v-bind:file-info="fileInfo" v-on:close="fileInfo = null"/>
    </div>

    <div id="rs-panel-container" class="panel-container">
      <div class="top-panel">
        <files-table
            v-bind:fileStage="fileStage"
            v-bind:loading-more="loadingMore"
            v-bind:loaded="loaded"
            v-bind:files="files"
            v-bind:selected="selected"
            v-bind:previewing="previewing"
            v-bind:validating="validating"
            v-bind:validationResults="validationResults"
            v-bind:truncated="truncated"
            v-bind:deleting="deleting"
            v-bind:downloading="downloading"
            v-bind:loading-info="loadingInfo"
            v-bind:filter="filter.value"

            v-on:delete-files="deleteFiles"
            v-on:download-files="downloadFiles"
            v-on:validate-files="validateFiles"
            v-on:load-more="loadMore"
            v-on:show-preview="showPreview"
            v-on:item-selected="selectItem"
            v-on:item-deselected="deselectItem"
            v-on:deselect-all="deselect"
            v-on:toggle-all="toggleAll"
            v-on:toggle-file="toggleFile"
            v-on:info="info"
        />
      </div>

      <div id="rs-status-panels" class="bottom-panel">
        <ul class="status-panel-tabs nav nav-tabs">
          <li class="nav-item">
            <a href="#" class="nav-link" v-bind:class="{'active': tab === 'preview'}"
               v-on:click.prevent="tab = 'preview'">
              File Preview
              <template v-if="previewing"> - {{ decodeURI(previewing.key) }}</template>
            </a>
          </li>
          <li class="nav-item">
            <a href="#" class="nav-link" v-bind:class="{'active': tab === 'validation'}"
               v-on:click.prevent="tab = 'validation'">
              Validation Log
            </a>
          </li>
          <li class="nav-item">
            <a href="#" class="nav-link" v-bind:class="{'active': tab === 'sync'}"
               v-on:click.prevent="tab = 'sync'">
              Sync Log
            </a>
          </li>
          <li>
            <drag-handle
                v-bind:ns="fileStage"
                v-bind:p2="() => $root.$el.querySelector('#rs-status-panels')"
                v-bind:container="() => $root.$el.querySelector('#rs-panel-container')"
                v-on:resize="setPanelSize"
            />
          </li>
        </ul>

        <div class="status-panels">
          <div class="status-panel" v-show="tab === 'preview'">
            <panel-file-preview v-bind:dataset-id="datasetId"
                                v-bind:content-type="datasetContentType"
                                v-bind:file-stage="fileStage"
                                v-bind:previewing="previewing"
                                v-bind:panel-size="panelSize"
                                v-bind:config="config"
                                v-bind:api="api"
                                v-bind:validation-results="validationResults"
                                v-on:validation-results="(tag, e) => this.$set(this.validationResults, tag, e)"
                                v-on:error="showError"
                                v-show="previewing !== null"/>
            <div class="panel-placeholder" v-if="previewing === null">
              No file selected.
            </div>
          </div>
          <div class="status-panel log-container" v-show="tab === 'validation'">
            <panel-log-window v-bind:log="validationLog" v-if="validationLog.length > 0"/>
            <div  class="panel-placeholder" v-else>
              Validation log output will show here.
            </div>
          </div>
          <div class="status-panel log-container" v-show="tab === 'sync'">
            <panel-log-window v-bind:log="log" v-if="log.length > 0"/>
            <div class="panel-placeholder" v-else>
              Sync log output will show here.
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
