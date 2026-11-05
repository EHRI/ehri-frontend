<script lang="ts">
import {defineComponent, PropType} from 'vue';

import AccessPointType from "./components/_access-point-type.vue";
import AccessPointEditorApi from "./api";
import {ConfigType} from "./types";

export default defineComponent({
  components: {AccessPointType},
  props: {
    config: Object as PropType<ConfigType>,
    service: Object,
  },
  data() {
    return {
      api: new AccessPointEditorApi(this.service, this.config),
      loading: true,
      types: []
    }
  },
  methods: {
    reload: function () {
      this.loading = true;
      this.api.getAccessPoints(this.config.id, this.config.did).then(types => {
        this.loading = false;
        this.types = types
      });
    }
  },
  created: function () {
    this.reload();
  },
});
</script>

<template>
  <div id="access-point-editor" class="ap-editor">
    <span v-if="loading">Loading...</span>
    <ul class="ap-editor-types">
      <access-point-type
          v-for="type in types"
          v-bind:key="type.type"
          v-bind:type="type.type"
          v-bind:access-points="type.data"
          v-bind:api="api"
          v-bind:config="config"
          v-on:deleted="reload"
          v-on:added="reload"
        />
    </ul>
  </div>
</template>
