<script lang="ts">

import {conceptTitle} from '../common';

export default {
  name: 'concept-hierarchy',
  props: {
    id: String,
    data: Array,
    lang: String
  },
  methods: {conceptTitle},
}
</script>

<template>
  <ul class="concept-hierarchy" v-if="data">
    <li v-for="parent in data">{{ conceptTitle(parent, lang, id) }}
      <concept-hierarchy
          v-bind:data="parent.broaderTerms || []"
          v-bind:lang="lang"
          v-bind:id="parent.id"/>
    </li>
  </ul>
</template>
