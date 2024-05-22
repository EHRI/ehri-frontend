<script lang="ts">

import ModalWindow from './_modal-window';
import ModalAlert from './_modal-alert';
import MixinError from './_mixin-error';
import MixinUtil from './_mixin-util';
import MixinTasklog from './_mixin-tasklog'
import PanelLogWindow from './_panel-log-window';
import {DatasetManagerApi} from "../api";

export default {
  mixins: [MixinUtil, MixinError, MixinTasklog],
  components: {PanelLogWindow, ModalWindow, ModalAlert},
  props: {
    visible: Boolean,
    snapshot: Object,
    cleanup: Object,
    api: DatasetManagerApi,
    config: Object
  },
  data: function () {
    return {
      cleanupRunning: false,
      data: "",
      timestamp: (new Date()).getTime(),
    }
  },
  methods: {
    doCleanup: async function () {
      this.$emit("run-cleanup");
      this.cleanupRunning = true;
      try {
        let {url, jobId} = await this.api.doCleanupAsync(this.snapshot.id, {msg: this.data});
        this.reset();
        this.logDeleteLinePrefix = "Deleting...";
        await this.monitor(url, jobId);
      } catch (e) {
        this.showError("Error running cleanup", e);
      } finally {
        this.$emit("cleanup-complete")
        this.cleanupRunning = false;
      }
    },
    resize: function () {
      this.timestamp = (new Date()).getTime();
    },
  },
  computed: {
    isValid: function (): boolean {
      return this.data.trim() !== "";
    }
  },
};
</script>

<template>
  <modal-window v-on:close="$emit('close')" v-on:move="resize" v-bind:resizable="true">
    <template v-slot:title>Run cleanup</template>
    <p>This action will delete <strong>{{ cleanup.deletions.length.toLocaleString() }}</strong> item(s)
        and create <strong>{{ cleanup.redirects.length.toLocaleString() }}</strong> redirects!</p>
    <fieldset class="options-form">
      <div class="form-group">
        <label class="form-label" for="opt-cleanup-msg">
          Cleanup log message
          <span class="input-mandatory">*</span>
        </label>
        <input v-bind:disabled="cleanupRunning" type="text" v-model="data"
               id="opt-cleanup-msg" class="form-control" placeholder="Heuristic cleanup for update on..."/>
      </div>
    </fieldset>
    <div class="log-container">
      <panel-log-window v-bind:log="log" v-bind:resize="timestamp" v-bind:visible="visible" v-bind:disabled="!cleanupRunning"/>
    </div>
    <template v-slot:footer>
      <button v-bind:disabled="!isValid || cleanupRunning" v-on:click="doCleanup()" type="button"
              class="btn btn-danger">
        <i v-if="!cleanupRunning" class="fa fa-fw fa-exclamation-circle"></i>
        <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
        Run Cleanup Actions!
      </button>
    </template>
  </modal-window>
</template>

