<script lang="ts">
import ListEt from "./components/_list-et";
import EntityTypeMetadataApi from "./api";

export default {
  components: {ListEt},
  props: {
    service: Object,
    config: Object,
  },
  data: function() {
    return {
      api: new EntityTypeMetadataApi(this.service),
      error: null,
    }
  },
}

</script>

<template>
    <div class="app-content">
        <list-et v-bind:api="api"/>
    </div>
</template>
