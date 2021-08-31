<script type="ts">

import {timeToRelative, humanFileSize} from "../common";
import ModalWindow from './_modal-window';

export default {
  components: {ModalWindow},
  props: {
    fileInfo: Object
  },
  methods: { prettyDate: timeToRelative, humanFileSize, decodeURI }
};
</script>

<template>
  <modal-window v-on:close="$emit('close')">
    <template v-slot:title>{{ decodeURI(fileInfo.meta.key) }}</template>
    <div class="file-meta">
      <h4>Attributes</h4>
      <table class="info-table">
        <tr>
          <td>File size:</td>
          <td>{{ humanFileSize(fileInfo.meta.size) }}</td>
        </tr>
        <tr>
          <td>Last Modified:</td>
          <td v-bind:title="fileInfo.meta.lastModified">{{ prettyDate(fileInfo.meta.lastModified) }}</td>
        </tr>
        <tr>
          <td>E-Tag:</td>
          <td v-bind:title="fileInfo.meta.eTag">{{fileInfo.meta.eTag}}</td>
        </tr>
      </table>
    </div>
    <div class="file-meta" v-if="fileInfo.user">
      <h4>Metadata</h4>
      <table class="info-table">
        <tr v-for="(value, key) in fileInfo.user">
          <td>{{ key }}</td>
          <td>{{ value }}</td>
        </tr>
      </table>
    </div>
    <div class="file-user-meta" v-if="fileInfo.versions && fileInfo.versions.length > 1">
      <h4>Version History</h4>
      <table class="version-history-list info-table">
        <tr>
          <th>ID</th>
          <th>Created</th>
          <th>Size</th>
        </tr>
        <tr v-for="version in fileInfo.versions">
          <td>{{version.versionId}}</td>
          <td v-bind:title="version.lastModified">{{ prettyDate(version.lastModified) }}</td>
          <td>{{ humanFileSize(version.size) }}</td>
        </tr>
      </table>
    </div>
    <template v-slot:footer>
      <button v-on:click="$emit('close')" type="button" class="btn btn-default">
        Close
      </button>
    </template>
  </modal-window>
</template>

