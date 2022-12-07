<script lang="ts">
export default {
  props: {
    selected: Boolean,
    item: Object,
  },
}
</script>

<template>
  <div v-on:click="$emit('selected', item)" class="ap-editor-autocomplete-widget-suggestion">
    {{ item.name }}
    <span class="badge suggestion-type">{{ item.type }}</span>
    <span v-if="selected"> *</span>
  </div>
</template>
