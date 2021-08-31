<script lang="ts">

import MixinUtil from './_mixin-util';
import MixinError from './_mixin-error';
import {DatasetManagerApi} from "../api";
import {timeToRelative} from "../common";

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
    }
  },
  filters: {
    prettyDate: timeToRelative,
  },
  created() {
    this.refresh().then(() => this.initialised = true);
  }
}
</script>
<template>
  <div id="timeline-manager">
    <div class="actions-bar">
      <div class="custom-control custom-switch">
        <input v-bind:disabled="logs.length === 0" v-model="noops" type="checkbox" class="custom-control-input" id="opt-show-noops">
        <label class="custom-control-label" for="opt-show-noops">Show no-op imports</label>
      </div>
    </div>
    <div id="timeline-manager-log-list" v-if="initialised">
      <div v-if="logs" id="timeline-manager-log-entries">
        <div v-for="event in logs" v-if="noops || (event.created > 0 && event.updated > 0)"
             v-bind:class="{noop: (event.created === 0 && event.updated === 0)}" class="timeline-manager-item">
          <div v-bind:title="event.timestamp">
            <a v-if="event.eventId" v-bind:href="'/admin/events/' + event.eventId" target="_blank">
              {{ event.timestamp | prettyDate }}
            </a>
            <template v-else>
              {{ event.timestamp | prettyDate }}
            </template>
          </div>
          <h4>{{ datasetName(event.datasetId) }}</h4>
          <div>{{ event.message }}</div>
          <div>Created: {{ event.created }}</div>
          <div>Updated: {{ event.updated }}</div>
          <div>Unchanged: {{ event.unchanged }}</div>
        </div>
      </div>
      <p v-else class="info-message">
        No import logs found
      </p>
    </div>
    <div v-else class="coreference-loading-indicator">
      <h3>Loading logs...</h3>
      <i class="fa fa-3x fa-spinner fa-spin"></i>
    </div>
  </div>
</template>
