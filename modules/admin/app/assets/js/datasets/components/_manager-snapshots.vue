<script lang="ts">

import MixinUtil from './_mixin-util';
import MixinError from './_mixin-error';
import ModalAlert from './_modal-alert';
import {DatasetManagerApi} from "../api";
import {Snapshot} from "../types";

export default {
  mixins: [MixinUtil, MixinError],
  components: {ModalAlert},
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
      cleanupRunning: false,
      cleanupSummary: null,
      showCleanupDialog: false,
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
    doCleanup: async function() {
      this.cleanupRunning = true;
      try {
        let log = window.prompt("Deletion log message:");
        this.cleanupSummary = await this.api.doCleanup(this.current.id, { msg: log});
      } catch (e) {
        this.showError("Error running cleanup", e);
      } finally {
        this.cleanupRunning = false;
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
    <modal-alert v-if="showCleanupDialog && cleanup !== null"
                 v-on:accept="showCleanupDialog = false; doCleanup()"
                 v-on:close="showCleanupDialog = false"
                 v-bind:title="'Cleanup Actions?'"
                 v-bind:accept="'Perform Cleanup Actions!'">
      <p>This action will delete {{ cleanup.deletions.length }} items and create {{ cleanup.redirects.length }} redirects!</p>
    </modal-alert>
    <p>
      Snapshots record the global and local item identifiers
      present in a repository at a given time. This can be used
      to remove items not present in a set of import operations
      by creating a before/after difference.
      <button v-bind:disabled="inProgress || cleanupRunning" v-on:click.prevent="takeSnapshot" class="btn btn-sm btn-info">
        <i v-if="!inProgress" class="fa fa-fw fa-list"></i>
        <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
        Create Snapshot
      </button>
    </p>

    <div v-if="current" id="snapshot-manager-inspector">
      <template v-if="cleanupSummary">
        <h4>Cleanup summary</h4>
        <dl>
          <dd>Retargeted Links:</dd>
          <dt>{{ cleanupSummary.relinks}}</dt>
          <dd>Redirects:</dd>
          <dt>{{ cleanupSummary.redirects}}</dt>
          <dd>Deletions:</dd>
          <dt>{{ cleanupSummary.deletions}}</dt>
        </dl>
      </template>
      <template v-else>
        <h4>Snapshot taken at: {{ current.created }}</h4>
        <p v-if="loadingCleanup">
          Loading heuristic cleanup...
          <i class="fa fa-spin fa-spinner"></i>
        </p>
        <template v-else-if="cleanup !== null">
          <p>Heuristic redirects: <strong>{{ cleanup.redirects.length }}</strong></p>
          <textarea readonly class="form-control" id="snapshot-manager-redirects">{{ cleanup.redirects.map(([n, o]) => n + "," + o).join("\n") }}</textarea>
          <p>Heuristic deletions: <strong>{{ cleanup.deletions.length }}</strong></p>
          <textarea readonly class="form-control" id="snapshot-manager-diff">{{ cleanup.deletions.join("\n") }}</textarea>

          <button v-bind:disabled="cleanupRunning" v-on:click.prevent="showCleanupDialog = true" class="btn btn-sm btn-danger">
            <i v-if="!cleanupRunning" class="fa fa-fw fa-exclamation-circle"></i>
            <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
            Perform Cleanup
          </button>
        </template>
      </template>
    </div>
    <ul v-else-if="snapshots" class="list-group" id="snapshot-manager-snapshot-list">
      <li v-for="snapshot in snapshots" class="list-group-item">
        <a class="list-group-item-action" v-on:click.prevent="load(snapshot)" href="#">{{ snapshot.created }}</a>
      </li>
    </ul>
  </div>
</template>
