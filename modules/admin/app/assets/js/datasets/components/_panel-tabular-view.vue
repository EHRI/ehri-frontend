<script lang="ts">

import {decodeCsv, decodeTsv, encodeCsv, encodeTsv} from '../common';

export default {
  props: {
    data: {
      type: String as string | null,
      required: true,
      default: () => '',
    },
    contentType: {
      type: String as string | null,
      required: true,
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
      return this.isCsv() ? encodeCsv(data, this.expectedColumns) : encodeTsv(data, this.expectedColumns, true);
    },
    decode: function (data) {
      return this.isCsv() ? decodeCsv(data, this.expectedColumns) : decodeTsv(data, this.expectedColumns, true);
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
      <thead>
      <tr v-for="(row, i) in decodedData.slice(0, 1)" :key="i">
        <th v-for="col in row">{{ col }}</th>
      </tr>
      </thead>
      <tbody>
      <tr v-for="(row, i) in decodedData.slice(1)" :key="i">
        <td v-for="col in row">{{ col }}</td>
      </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.tabular-view-container {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  overflow: auto;
}

.tabular-view th {
  white-space: nowrap;
}

.tabular-view td {
  /* preserve embedded newlines in cell values (e.g. from a derive/merge
     expression) instead of collapsing them to a space, while still wrapping
     long lines rather than overflowing */
  white-space: pre-wrap;
}
</style>
