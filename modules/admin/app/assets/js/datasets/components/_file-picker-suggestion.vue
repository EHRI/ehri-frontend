<script lang="ts">

export default {
  props: {
    selected: Boolean,
    item: Object,
  },
  methods: { decodeURI }
};
</script>

<template>
  <div v-on:click="$emit('selected', item)" class="file-picker-suggestion" v-bind:class="{'selected': selected}">
    {{ decodeURI(item.key) }}
  </div>
</template>

