
<script lang="ts">

import AccessPoint from './_access-point';
import AddForm from './_add-form';

export default {
  components: {AccessPoint, AddForm},
  props: {
    type: String,
    accessPoints: Array,
    api: Object,
    config: Object,
  },
  data: function () {
    return {
      adding: false
    };
  },
  computed: {
    title: function () {
      return this.config.labels[this.type];
    }
  },
  methods: {
    added: function () {
      this.adding = false;
      this.$emit('added');
    }
  },
}
</script>

<template>
  <li class="ap-editor-type">
    <h3>{{ title }}</h3>
    <ul class="ap-editor-access-points">
      <access-point
          v-for="ap in accessPoints"
          v-bind:key="ap.accessPoint.id"
          v-bind:access-point="ap.accessPoint"
          v-bind:link="ap.link"
          v-bind:target="ap.target"
          v-bind:api="api"
          v-bind:config="config"
          v-on:deleted="$emit('deleted')"
        />
    </ul>
    <a v-if="!adding" class="ap-editor-add-toggle" v-on:click.prevent="adding = !adding" href="#">
      <i class="glyphicon" v-bind:class="adding ? 'glyphicon-minus-sign' : 'glyphicon-plus-sign'"></i>
      Add New
    </a>
    <add-form
        v-if="adding"
        v-bind:type="type"
        v-bind:api="api"
        v-bind:config="config"
        v-on:added="added"
        v-on:cancel-add="adding = false"
      />
    <hr/>
  </li>
</template>
