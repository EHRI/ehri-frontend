<script lang="ts">
export default {
  props: {
    selected: Boolean,
    item: Object,
  },
}
</script>

<template functional>
  <div @click="listeners['selected'](props.item)" class="vocab-editor-autocomplete-widget-suggestion">
    {{ props.item.name }}
    <span v-if="props.selected"> *</span>
  </div>

</template>
