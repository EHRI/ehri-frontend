<script lang="ts">
import {defineComponent} from 'vue';
export default defineComponent({
  props: {
    selected: Boolean,
    item: Object,
  },
});
</script>

<template>
  <div v-on:click="$emit('selected', item)" class="ap-editor-autocomplete-widget-suggestion">
    {{ item.name }}
    <span class="badge suggestion-type">{{ item.type }}</span>
    <span v-if="selected"> *</span>
  </div>
</template>
