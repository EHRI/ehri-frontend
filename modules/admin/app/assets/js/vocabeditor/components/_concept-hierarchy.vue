<script lang="ts">

import {conceptTitle} from '../common';

export default {
  name: 'concept-hierarchy',
  props: {
    id: String,
    data: Array,
    lang: String
  },
  filters: {conceptTitle},
}
</script>

<template functional>
  <ul class="concept-hierarchy" v-if="props.data">
    <li v-for="parent in props.data">{{parent|conceptTitle(lang, id)}}
      <concept-hierarchy
          v-bind:data="parent.broaderTerms || []"
          v-bind:lang="props.lang"
          v-bind:id="parent.id"/>
    </li>
  </ul>
</template>
