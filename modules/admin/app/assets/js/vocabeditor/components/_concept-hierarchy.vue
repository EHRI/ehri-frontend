<script lang="ts">
import {defineComponent, PropType} from 'vue';

import {conceptTitle} from '../common';
import {Concept} from '../types';

export default defineComponent({
  name: 'concept-hierarchy',
  props: {
    id: String,
    data: Array as PropType<Concept[]>,
    lang: String
  },
  methods: {conceptTitle},
});
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
