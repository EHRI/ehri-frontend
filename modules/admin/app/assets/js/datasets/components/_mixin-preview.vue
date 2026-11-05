<script lang="ts">
import {defineComponent} from 'vue';

import {FileMeta} from '../types';

export default defineComponent({
  data: function () {
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
});
</script>
