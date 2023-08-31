<script lang="ts">

import MixinUtil from './_mixin-util';
import MixinError from './_mixin-error';
import ModalAlert from './_modal-alert';
import ModalSnapshotConfig from './_modal-snapshot-config';
import ModalCleanupConfig from './_modal-cleanup-config.vue';
import {DatasetManagerApi} from "../api";
import {Snapshot} from "../types";
import {timeToRelative, displayDate} from "../common";

export default {
  mixins: [MixinUtil, MixinError],
  components: {ModalAlert, ModalSnapshotConfig, ModalCleanupConfig},
  props: {
    config: Object,
    api: DatasetManagerApi,
  },
  data: function () {
    return {
      snapshots: [],
      current: null,
      cleanup: null,
      actions: [],
      loading: false,
      loadingCleanup: false,
      inProgress: false,
      cleanupRunning: false,
      showCleanupDialog: false,
      showCreateDialog: false,
      filter: "",
    }
  },
  methods: {
    loadCleanup: async function (snapshot: Snapshot) {
      this.loadingCleanup = true;
      try {
        this.actions = await this.api.listCleanups(snapshot.id);
        this.cleanup = await this.api.cleanup(snapshot.id);
      } catch (e) {
        this.showError("Unable to find snapshots", e);
      } finally {
        this.loadingCleanup = false;
      }
    },
    load: async function (snapshot: Snapshot) {
      this.current = snapshot;
      await this.loadCleanup(snapshot);
    },
    takeSnapshot: async function (notes) {
      this.showCreateDialog = false;
      this.inProgress = true;
      try {
        await this.api.takeSnapshot({notes});
        await this.refresh();
      } catch (e) {
        console.error("Error taking snapshot", e);
      } finally {
        this.inProgress = false;
      }
    },
    refresh: async function () {
      this.loading = true;
      try {
        this.snapshots = await this.api.listSnapshots();
      } catch (e) {
        this.showError("Unable to list snapshots", e);
      } finally {
        this.loading = false;
      }
    },
    isFiltered: function () {
      return this.filter.trim() !== "";
    },
    timeToRelative,
    displayDate
  },
  created() {
    this.refresh();
  }
}
</script>
<template>
  <div id="snapshot-manager">
    <modal-snapshot-config v-if="showCreateDialog"
                           v-on:create="takeSnapshot"
                           v-on:close="showCreateDialog = false"/>

    <div class="actions-bar">
      <div class="filter-control">
        <label class="sr-only">Filter Snapshots</label>
        <input v-model="filter" v-bind:disabled="current || snapshots.length===0" type="text"
               placeholder="Filter snapshots..." class="filter-input form-control form-control-sm">
        <i class="filtering-indicator fa fa-close fa-fw" style="cursor: pointer" v-on:click="filter = ''"
           v-if="isFiltered()"/>
      </div>

      <button v-if="current" v-bind:disabled="inProgress || cleanupRunning" v-on:click="current = null"
              class="btn btn-sm btn-default">
        <i class="fa fa-fw fa-arrow-left"></i>
        Back to list
      </button>
      <button v-else v-bind:disabled="inProgress || cleanupRunning" v-on:click.prevent="showCreateDialog = true"
              class="btn btn-sm btn-info">
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
      <h4 v-bind:title="current.created">Snapshot taken: {{ timeToRelative(current.created) }}</h4>
      <div class="alert alert-info" v-for="[id, date] in actions.slice(0, 3)">
        Cleanup run on {{ displayDate(date) }}.
      </div>
      <p v-if="loadingCleanup">
        Loading heuristic cleanup...
        <i class="fa fa-spin fa-spinner"></i>
      </p>
      <template v-else-if="cleanup !== null">
        <keep-alive>
          <modal-cleanup-config v-if="showCleanupDialog"
                                v-bind:visible="showCleanupDialog"
                                v-bind:snapshot="current"
                                v-bind:cleanup="cleanup"
                                v-bind:api="api"
                                v-bind:config="config"
                                v-on:close="showCleanupDialog = false"
                                v-on:run-cleanup="cleanupRunning = true"
                                v-on:cleanup-complete="cleanupRunning = false"/>
        </keep-alive>
        <h5>Heuristic redirects: <strong>{{ cleanup.redirects.length.toLocaleString() }}</strong></h5>
        <p class="admin-help-notice">
          Inferred redirects are inferred from items pending cleanup which share local identifiers
          with other existing items. <strong>This assumes unique local identifiers across all of the
          repository's items!</strong>
        </p>

        <textarea readonly class="form-control" id="snapshot-manager-redirects">{{ cleanup.redirects.map(([n, o]) => n + "," + o).join("\n") }}</textarea>
        <h5>Heuristic deletions: <strong>{{ cleanup.deletions.length.toLocaleString() }}</strong></h5>
        <p class="admin-help-notice">
          Deletions are items that do not exist in the set of items imported since the snapshot was
          taken.
        </p>
        <textarea readonly class="form-control" id="snapshot-manager-diff">{{ cleanup.deletions.join("\n") }}</textarea>

        <button v-on:click.prevent="showCleanupDialog = true" class="btn btn-sm btn-secondary">
          <i v-if="!cleanupRunning" class="fa fa-fw fa-trash"></i>
          <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
          Perform Cleanup
        </button>
      </template>
    </div>
    <div v-else-if="snapshots" class="snapshot-manager-snapshot-list">
      <div v-for="snapshot in snapshots"
           v-if="!isFiltered() || (snapshot.notes && snapshot.notes.includes(this.filter) )"
           v-on:click.stop.prevent="load(snapshot)" class="snapshot-manager-item">
        <div v-bind:title="snapshot.created" class="snapshot-timestamp item-icon">
          <i class="fa fa-clock-o"></i>
          {{ timeToRelative(snapshot.created) }}
        </div>
        <div class="snapshot-notes item-heading">
          {{ snapshot.notes }}
        </div>
      </div>
    </div>
    <div v-else-if="loading" class="snapshot-loading-indicator">
      Loading snapshots...
      <i class="fa fa-3x fa-spinner fa-spin"></i>
    </div>
  </div>
</template>
