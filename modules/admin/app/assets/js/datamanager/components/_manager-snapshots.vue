<script lang="ts">

import MixinUtil from './_mixin-util';
import MixinError from './_mixin-error';
import {DatasetManagerApi} from "../api";
import {Snapshot} from "../types";

export default {
  mixins: [MixinUtil, MixinError],
  props: {
    config: Object,
    api: DatasetManagerApi,
  },
  data: function() {
    return {
      snapshots: [],
      current: null,
      cleanup: null,
      loading: false,
      loadingCleanup: false,
      inProgress: false,
    }
  },
  methods: {
    loadCleanup: async function(snapshot: Snapshot) {
      this.loadingCleanup = true;
      try {
        this.cleanup = await this.api.cleanup(snapshot.id);
      } catch (e) {
        this.showError("Unable to find redirects", e);
      } finally {
        this.loadingCleanup = false;
      }
    },
    load: async function(snapshot: Snapshot) {
      this.current = snapshot;
      await this.loadCleanup(snapshot);
    },
    takeSnapshot: function() {
      this.inProgress = true;
      this.api.takeSnapshot({})
          .then(data => this.refresh())
          .catch(e => console.error("Error taking snapshot", e))
          .finally(() => this.inProgress = false);
    },
    refresh: function() {
      this.loading = true;
      this.api.listSnapshots()
          .then(snapshots => this.snapshots = snapshots)
          .catch(e => this.showError("Unable to list snapshots", e))
          .finally(() => this.loading = false);
    }
  },
  created() {
    this.refresh();
  }
}
</script>
<template>
  <div id="snapshot-manager">
    <div id="snapshot-manager-header">
      <h2>Snapshots</h2>
      <button v-on:click="$emit('close')" class="btn btn-sm btn-default">
        <i class="fa fa-arrow-left"></i>
        Back to dataset list
      </button>
    </div>
    <p>
      Snapshots record the global and local item identifiers
      present in a repository at a given time. This can be used
      to remove items not present in a set of import operations
      by creating a before/after difference.
      <button v-on:click.prevent="takeSnapshot" class="btn btn-sm btn-success">
        <i v-if="!inProgress" class="fa fa-fw fa-list"></i>
        <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
        Create Snapshot
      </button>
    </p>

    <div v-if="current" id="snapshot-manager-inspector">
      <h4>Snapshot taken at: {{ current.created }}</h4>

        <p v-if="loadingCleanup">
          Loading heuristic cleanup...
          <i class="fa fa-spin fa-spinner"></i>
        </p>
        <template v-else-if="cleanup !== null">
          <p>Heuristic redirects: <strong>{{ cleanup.redirects.length }}</strong></p>
          <textarea class="form-control" id="snapshot-manager-redirects">{{ cleanup.redirects.map(([n, o]) => n + "," + o).join("\n") }}</textarea>
          <p>Heuristic deletes: <strong>{{ cleanup.deletes.length }}</strong></p>
          <textarea class="form-control" id="snapshot-manager-diff">{{ cleanup.deletes.join("\n") }}</textarea>
        </template>

    </div>
    <ul v-else-if="snapshots" class="list-group" id="snapshot-manager-snapshot-list">
      <li v-for="snapshot in snapshots" class="list-group-item">
        <a class="list-group-item-action" v-on:click.prevent="load(snapshot)" href="#">{{ snapshot.created }}</a>
      </li>
    </ul>
  </div>
</template>
