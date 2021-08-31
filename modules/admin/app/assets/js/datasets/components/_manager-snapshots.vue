<script lang="ts">

import MixinUtil from './_mixin-util';
import MixinError from './_mixin-error';
import ModalAlert from './_modal-alert';
import ModalSnapshotConfig from './_modal-snapshot-config';
import {DatasetManagerApi} from "../api";
import {Snapshot} from "../types";
import {timeToRelative} from "../common";

export default {
  mixins: [MixinUtil, MixinError],
  components: {ModalAlert, ModalSnapshotConfig},
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
      showCreateDialog: false,
      filter: "",
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
    takeSnapshot: function(notes) {
      this.showCreateDialog = false;
      this.inProgress = true;
      this.api.takeSnapshot({notes})
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
    },
    isFiltered: function() {
      return this.filter.trim() !== "";
    }
  },
  created() {
    this.refresh();
  },
  filters: { timeToRelative }
}
</script>
<template>
  <div id="snapshot-manager">
    <modal-snapshot-config v-if="showCreateDialog"
                           v-on:create="takeSnapshot"
                           v-on:close="showCreateDialog = false"/>

    <modal-alert v-if="showCleanupDialog && cleanup !== null"
                 v-on:accept="showCleanupDialog = false; doCleanup()"
                 v-on:close="showCleanupDialog = false"
                 v-bind:title="'Cleanup Actions?'"
                 v-bind:accept="'Perform Cleanup Actions!'">
      <p>This action will delete {{ cleanup.deletions.length }} items and create {{ cleanup.redirects.length }} redirects!</p>
    </modal-alert>
    <div class="actions-bar">
      <div class="filter-control">
        <label class="sr-only">Filter Snapshots</label>
        <input v-model="filter" v-bind:disabled="current || snapshots.length===0" type="text" placeholder="Filter snapshots..." class="filter-input form-control form-control-sm">
        <i class="filtering-indicator fa fa-close fa-fw" style="cursor: pointer" v-on:click="filter = ''" v-if="isFiltered()"/>
      </div>

      <button v-if="current" v-bind:disabled="inProgress || cleanupRunning" v-on:click="current = null" class="btn btn-sm btn-default">
        <i class="fa fa-fw fa-arrow-left"></i>
        Back to list
      </button>
      <button v-else v-bind:disabled="inProgress || cleanupRunning" v-on:click.prevent="showCreateDialog = true" class="btn btn-sm btn-info">
        <i v-if="!inProgress" class="fa fa-fw fa-list"></i>
        <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
          Create Snapshot
      </button>
    </div>
    <p class="admin-help-notice">
      Snapshots record the global and local item identifiers
      present in a repository at a given time. This can be used
      to remove items not present in a set of import operations
      by creating a before/after difference.
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
        <h4 v-bind:title="current.created">Snapshot taken: {{ current.created | timeToRelative }}</h4>
        <p v-if="loadingCleanup">
          Loading heuristic cleanup...
          <i class="fa fa-spin fa-spinner"></i>
        </p>
        <template v-else-if="cleanup !== null">
          <h5>Heuristic redirects: <strong>{{ cleanup.redirects.length }}</strong></h5>
          <p class="admin-help-notice">
            Inferred redirects are inferred from items pending cleanup which share local identifiers
            with other existing items. <strong>This assumes unique local identifiers across all of the
            repository's items!</strong>
          </p>

          <textarea readonly class="form-control" id="snapshot-manager-redirects">{{ cleanup.redirects.map(([n, o]) => n + "," + o).join("\n") }}</textarea>
          <h5>Heuristic deletions: <strong>{{ cleanup.deletions.length }}</strong></h5>
          <p class="admin-help-notice">
            Deletions are items that do not exist in the set of items imported since the snapshot was
            taken.
          </p>
          <textarea readonly class="form-control" id="snapshot-manager-diff">{{ cleanup.deletions.join("\n") }}</textarea>

          <button v-bind:disabled="cleanupRunning" v-on:click.prevent="showCleanupDialog = true" class="btn btn-sm btn-danger">
            <i v-if="!cleanupRunning" class="fa fa-fw fa-exclamation-circle"></i>
            <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
            Perform Cleanup
          </button>
        </template>
      </template>
    </div>
    <div v-else-if="snapshots" class="list-group" id="snapshot-manager-snapshot-list">
      <div v-for="snapshot in snapshots" v-if="!isFiltered() || (snapshot.notes && snapshot.notes.includes(this.filter) )"
            v-on:click.stop.prevent="load(snapshot)" class="snapshot-manager-item" style="cursor: pointer">
        <div v-bind:title="snapshot.created" class="snapshot-timestamp">
          {{ snapshot.created | timeToRelative }}
        </div>
        <div class="snapshot-notes">
          {{ snapshot.notes }}
        </div>
      </div>
    </div>
    <div v-else class="snapshot-loading-indicator">
      <h3>Loading snapshots...</h3>
      <i class="fa fa-3x fa-spinner fa-spin"></i>
    </div>
  </div>
</template>
