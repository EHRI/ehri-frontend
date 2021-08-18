<script lang="ts">

import MixinUtil from './_mixin-util';
import MixinError from './_mixin-error';
import {DatasetManagerApi} from "../api";
import {Snapshot} from "../types";

export default {
  mixins: {MixinUtil, MixinError},
  props: {
    config: Object,
    api: DatasetManagerApi,
  },
  data: function() {
    return {
      snapshots: [],
      current: null,
      diff: [],
      loading: false,
      loadingDiff: false,
      snapshotInProgress: false,
    }
  },
  methods: {
    load: function(snapshot: Snapshot) {
      this.current = snapshot;
      this.loadingDiff = true;
      this.api.diffSnapshot(snapshot.id)
          .then(diff => this.diff = diff)
          .catch(e => this.showError("Unable to load snapshot diff", e))
          .finally(() => this.loadingDiff = false);
    },
    takeSnapshot: function() {
      this.snapshotInProgress = true;
      this.api.takeSnapshot({})
          .then(data => this.refresh())
          .catch(e => console.error("Error taking snapshot", e))
          .finally(() => this.snapshotInProgress = false);
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
      <button v-on:click.prevent="showOptions = false; takeSnapshot()" class="btn btn-sm btn-success">
        <i v-if="!snapshotInProgress" class="fa fa-fw fa-list"></i>
        <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
        Create Snapshot
      </button>
    </p>

    <div v-if="current" id="snapshot-manager-inspector">
      <h4>Snapshot taken at: {{ current.created }}</h4>

      <p v-if="loadingDiff">
        Loading untouched files since snapshot was taken...
        <i class="fa fa-spin fa-spinner"></i>
      </p>
      <template v-else-if="diff">
        <p>Untouched files since snapshot taken: <strong>{{ diff.length }}</strong></p>
        <textarea class="form-control" id="snapshot-manager-diff">{{ diff.map(([item]) => item).join("\n") }}</textarea>
      </template>
    </div>
    <ul v-else-if="snapshots" class="list-group" id="snapshot-manager-snapshot-list">
      <li v-for="snapshot in snapshots" class="list-group-item">
        <a class="list-group-item-action" v-on:click.prevent="load(snapshot)" href="#">{{ snapshot.created }}</a>
      </li>
    </ul>
  </div>
</template>
