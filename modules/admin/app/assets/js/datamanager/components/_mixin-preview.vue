<script lang="ts">

import {FileMeta} from '../types';

export default {
  data: function() {
    return {
      tab: 'preview'
    }
  },
  methods: {
    showPreview: function (file: FileMeta) {
      this.previewing = file;
      this.tab = 'preview';
    },
  }
}
</script>
