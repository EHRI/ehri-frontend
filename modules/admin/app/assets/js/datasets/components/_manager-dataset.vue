<script lang="ts">

import ModalDatasetConfig from './_modal-dataset-config';
import ManagerOaipmh from './_manager-oaipmh';
import ManagerUpload from './_manager-upload';
import ManagerIngest from './_manager-ingest';
import ManagerRs from './_manager-rs';
import ManagerConvert from './_manager-convert.vue';
import ManagerSnapshots from "./_manager-snapshots.vue";

import MixinUtil from './_mixin-util';
import MixinError from './_mixin-error';
import {ImportDatasetSrc} from '../types';
import {DatasetManagerApi} from "../api";

import _merge from 'lodash/merge';

export default {
  components: {ManagerSnapshots, ModalDatasetConfig, ManagerOaipmh, ManagerUpload, ManagerIngest, ManagerRs, ManagerConvert},
  mixins: [MixinUtil, MixinError],
  props: {
    config: Object,
    api: DatasetManagerApi,
    initTab: String,
    dataset: Object,
    datasets: Array,
    stats: Object,
  },
  data: function() {
    return {
      loaded: false,
      tab: this.initTab,
      showSelector: false,
    }
  },
  methods: {
    switchTab: function(tab: string) {
      this.tab = tab;
      history.pushState(
          _merge(this.queryParams(window.location.search), {'tab': tab}),
          document.title,
          this.setQueryParam(window.location.search, 'tab', tab));
    },
    stageName: function(code: ImportDatasetSrc): string {
      switch (code) {
        case "oaipmh": return "Harvesting";
        case "upload": return "Uploads";
        case "rs": return "ResourceSync";
        default: return code;
      }
    },
  },
  created() {
    window.onpopstate = event => {
      if (event.state && event.state.tab) {
        this.tab = event.state.tab;
      } else {
        this.tab = this.initTab;
      }
    };

    let qsTab = this.getQueryParam(window.location.search, "tab");
    if (qsTab) {
      this.tab = qsTab;
    }
  },

};
</script>

<template>
  <div id="dataset-manager-container">
    <ul id="stage-tabs" class="nav nav-tabs">
      <li class="nav-item">
        <a v-if="dataset.src === 'oaipmh'" href="#tab-input" class="nav-link" v-bind:class="{active: tab === 'input'}"
           v-on:click.prevent="switchTab('input')">
          <i class="fa fw-fw fa-cloud-download"></i>
          Harvest Data
        </a>
        <a v-if="dataset.src === 'rs'" href="#tab-input" class="nav-link" v-bind:class="{active: tab === 'input'}"
           v-on:click.prevent="switchTab('input')">
          <i class="fa fw-fw fa-clone"></i>
          Sync Data
        </a>
        <a v-if="dataset.src === 'upload'" href="#tab-input" class="nav-link" v-bind:class="{active: tab === 'input'}"
           v-on:click.prevent="switchTab('input')">
          <i class="fa fw-fw fa-upload"></i>
          Uploads
        </a>
      </li>
      <li class="nav-item">
        <a href="#tab-convert" class="nav-link" v-bind:class="{active: tab === 'convert'}"
           v-on:click.prevent="switchTab('convert')">
          <i class="fa fa-fw fa-file-code-o"></i>
          Transform
        </a>
      </li>
      <li class="nav-item">
        <a href="#tab-ingest" class="nav-link" v-bind:class="{active: tab === 'ingest'}"
           v-on:click.prevent="switchTab('ingest')">
          <i class="fa fa-fw fa-database"></i>
          Ingest
        </a>
      </li>
      <li class="dataset-menu">
        <div class="dropdown">
          <button class="btn btn-info" v-on:click="showSelector = !showSelector">
            <i class="fa fa-lg fa-caret-down"></i>
            Dataset: {{dataset.name}}
          </button>
          <template v-if="showSelector">
            <div class="dropdown-backdrop" v-on:click="showSelector = false"></div>
            <div class="dropdown-menu dropdown-menu-right show" id="dataset-selector">
              <a v-on:click.prevent="$emit('edit-dataset'); showSelector = false" class="dropdown-item" href="#">
                <i class="fa fa-edit"></i>
                Edit Dataset
              </a>
              <template v-if="datasets.length > 1">
                <div class="dropdown-divider"></div>
                <div id="dataset-selector-list">
                  <a v-for="ds in datasets" v-on:click.prevent="$emit('select-dataset', ds); showSelector = false" href="#" class="dropdown-item">
                    <i class="fa fa-fw" v-bind:class="{'fa-asterisk': ds.id===dataset.id}"></i>
                    {{ ds.name }}
                    <div class="badge badge-pill" v-bind:class="'badge-' + ds.src">
                      <span v-if="ds.id in stats">({{stats[ds.id]}})</span>
                    </div>
                  </a>
                </div>
              </template>
              <div class="dropdown-divider"></div>
              <a v-on:click.prevent="$emit('close-dataset'); showSelector = false" href="#" class="dropdown-item">
                <i class="fa fa-close"></i>
                Close
              </a>
            </div>
          </template>
        </div>
      </li>
    </ul>
    <div id="tab-input" class="stage-tab" v-show="tab === 'input'">
      <manager-oaipmh
          v-if="dataset.src === 'oaipmh'"
          v-bind:dataset-id="dataset.id"
          v-bind:dataset-content-type="dataset.contentType"
          v-bind:fileStage="config.input"
          v-bind:config="config"
          v-bind:active="tab === 'input'"
          v-bind:api="api"
          v-on:updated="$emit('refresh-stats')"
          v-on:error="showError"  />
      <manager-rs
          v-else-if="dataset.src === 'rs'"
          v-bind:dataset-id="dataset.id"
          v-bind:dataset-content-type="dataset.contentType"
          v-bind:fileStage="config.input"
          v-bind:config="config"
          v-bind:active="tab === 'input'"
          v-bind:api="api"
          v-on:updated="$emit('refresh-stats')"
          v-on:error="showError"  />
      <manager-upload
          v-else
          v-bind:dataset-id="dataset.id"
          v-bind:dataset-content-type="dataset.contentType"
          v-bind:fileStage="config.input"
          v-bind:config="config"
          v-bind:active="tab === 'input'"
          v-bind:api="api"
          v-on:updated="$emit('refresh-stats')"
          v-on:error="showError"  />
    </div>
    <div id="tab-convert" class="stage-tab" v-show="tab === 'convert'">
      <manager-convert
          v-bind:dataset-id="dataset.id"
          v-bind:dataset-content-type="dataset.contentType"
          v-bind:fileStage="config.output"
          v-bind:config="config"
          v-bind:active="tab === 'convert'"
          v-bind:api="api"
          v-on:error="showError" />
    </div>
    <div id="tab-ingest" class="stage-tab" v-show="tab === 'ingest'">
      <manager-ingest
          v-bind:dataset-id="dataset.id"
          v-bind:fileStage="config.output"
          v-bind:config="config"
          v-bind:active="tab === 'ingest'"
          v-bind:api="api"
          v-on:error="showError"  />
    </div>
  </div>
</template>

