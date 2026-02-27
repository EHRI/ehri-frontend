<script lang="ts">

import {decodeCsv, decodeTsv, encodeCsv, encodeTsv} from '../common';

export default {
  props: {
    data: {
      type: String,
      default: '',
    },
    contentType: {
      type: String,
      default: 'text/csv',
    },
    expectedColumns: {
      type: Number,
      // somewhat arbitrary, but it actually makes no difference
      // since the parser currently ignores this.
      default: 10,
    }
  },
  methods: {
    isCsv: function () {
      return !this.contentType || (this.contentType.includes('csv'));
    },
    // NB: expected columns is actually unused here, because we can't really know it for arbitrary data.
    encode: function (data) {
      return this.isCsv() ? encodeCsv(data, this.expectedColumns) : encodeTsv(data, this.expectedColumns);
    },
    decode: function (data) {
      return this.isCsv() ? decodeCsv(data, this.expectedColumns) : decodeTsv(data, this.expectedColumns);
    },
  },
  computed: {
    decodedData: function (): string[][] {
      return this.decode(this.data);
    },
  }
}
</script>

<template>
  <div class="tabular-view-container">
    <table v-if="data" class="table table-striped table-bordered table-sm tabular-view">
      <tbody>
      <tr v-for="row in decodedData">
        <td v-for="col in row">{{ col }}</td>
      </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.tabular-view-container {
  display: flex;
  overflow: auto;
}

.tabular-view {
}
</style>
