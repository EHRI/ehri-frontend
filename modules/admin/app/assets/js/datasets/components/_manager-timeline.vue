<script lang="ts">

import MixinUtil from './_mixin-util';
import MixinError from './_mixin-error';
import {DatasetManagerApi} from "../api";
import {timeToRelative} from "../common";
import {Coreference, ImportLogSummary} from "../types";
import _includes from "lodash/includes";

export default {
  mixins: {MixinUtil, MixinError},
  props: {
    datasets: Array,
    config: Object,
    api: DatasetManagerApi,
  },
  data: function() {
    return {
      logs: [],
      loading: false,
      initialised: false,
      noops: false,
      set: null,
    }
  },
  methods: {
    refresh: async function() {
      this.loading = true;
      try {
        this.logs = await this.api.logs();
      } catch (e) {
        this.showError("Error loading import log data", e);
      } finally {
        this.loading = false;
      }
    },
    datasetName: function(dsId: string) {
      let ds = this.datasets.find(ds => ds.id === dsId)
      return ds ? ds.name : null;
    },
    datasetType: function(dsId: string) {
      let ds = this.datasets.find(ds => ds.id === dsId)
      return ds ? ds.src : null;
    }
  },
  computed: {
    sets: function() {
      let out = [];
      let logs = this.logs as ImportLogSummary[];
      for (let r of logs) {
        if (!_includes(out, r.datasetId)) {
          out.push(r.datasetId);
        }
      }
      return out;
    },
    filteredLogs: function() {
      let logs = this.logs as ImportLogSummary[];
      let filter = (log: ImportLogSummary) => {
        return (this.set === null || log.datasetId === this.set) &&
            (this.noops || log.created > 0 || log.updated > 0);
      }
      return logs.filter(filter);
    }
  },
  filters: {
    timeToRelative,
  },
  created() {
    this.refresh().then(() => this.initialised = true);
  }
}
</script>
<template>
  <div id="timeline-manager">
    <div class="actions-bar">
      <div class="filter-control">
        <select v-model="set" v-bind:disabled="logs.length===0 || sets.length < 2" class="form-control form-control-sm">
          <option v-bind:value="null">Filter dataset</option>
          <option v-for="setId in sets" v-bind:value="setId">{{ datasetName(setId) }}</option>
        </select>
      </div>
      <div id="timeline-manager-noop-toggle" class="custom-control custom-switch">
        <label class="custom-control-label" for="opt-show-noops">Show no-op imports</label>
        <input v-bind:disabled="logs.length === 0" v-model="noops" type="checkbox" class="custom-control-input" id="opt-show-noops">
      </div>
    </div>
    <div id="timeline-manager-log-list" v-if="initialised">
      <div id="timeline-manager-log-entries">
        <table v-if="logs.length > 0" class="table table-bordered">
          <thead>
            <tr>
              <th>When</th>
              <th>Dataset ID</th>
              <th>Dataset</th>
              <th>Created Items</th>
              <th>Updated Items</th>
              <th>Unchanged Items</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="event in filteredLogs" v-bind:class="{noop: event.created === 0 && event.updated === 0}">
              <td v-bind:title="event.timestamp">
                <a v-if="event.eventId" v-bind:href="'/admin/events/' + event.eventId" target="_blank">
                  {{ event.timestamp | timeToRelative }}
                </a>
                <template v-else>
                  {{ event.timestamp | timeToRelative }}
                </template>
              </td>
              <td><strong>{{ event.datasetId }}</strong></td>
              <td><strong>{{ datasetName(event.datasetId) }}</strong></td>
              <td v-bind:class="{'table-success': event.created > 0}">{{ event.created }}</td>
              <td v-bind:class="{'table-warning': event.updated > 0}">{{ event.updated }}</td>
              <td v-bind:class="{'table-info': event.unchanged > 0}">{{ event.unchanged }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="info-message">
          No import logs found
        </p>
      </div>
    </div>
    <div v-else class="coreference-loading-indicator">
      <h3>Loading logs...</h3>
      <i class="fa fa-3x fa-spinner fa-spin"></i>
    </div>
  </div>
</template>
