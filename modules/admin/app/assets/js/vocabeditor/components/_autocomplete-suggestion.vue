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
  <div @click="$emit('selected', item)" class="vocab-editor-autocomplete-widget-suggestion">
    {{ item.name }}
    <span v-if="selected"> *</span>
  </div>
</template>
