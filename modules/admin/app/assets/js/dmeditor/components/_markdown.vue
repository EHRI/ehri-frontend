<script lang="ts">

import markdownit from 'markdown-it';

export default {
  props: {
    content: String,
    cls: {
      type: String,
      default: 'markdown'
    }
  },
    data() {
        return {
            md: null
        }
    },
  created() {
    this.md = markdownit({
      html: true,
      linkify: true,
      typographer: true,
    });
  }
}
</script>

<template>
    <div v-if="content" v-html="md.render(content)"></div>
</template>
