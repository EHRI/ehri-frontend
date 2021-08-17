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
    <h2>Snapshots</h2>
    <div v-if="current">
      Snapshot taken at: {{ current.created }}

      <div v-if="loadingDiff">
        Loading untouched files since snapshot was taken...
      </div>
      <div v-else>
        <p>Untouched files since snapshot taken: {{ diff.length }}</p>
        <ul v-if="diff">
          <li v-for="[item, local] in diff">
            <a target="_blank" v-bind:href="'/admin/units/' + item">{{ item }}</a> ({{ local }})
          </li>
        </ul>
      </div>
    </div>
    <ul v-else-if="snapshots">
      <li v-for="snapshot in snapshots" v-on:click.prevent="load(snapshot)">
        <a href="#">{{ snapshot.created }}</a>
      </li>
    </ul>
  </div>
</template>
