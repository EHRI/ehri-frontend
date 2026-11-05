<script lang="ts">
import {defineComponent} from 'vue';

export default defineComponent({
  data: function () {
    return {
      panelSize: null,
    }
  },
  methods: {
    setPanelSize: function (arbitrarySize: number): void {
      this.panelSize = arbitrarySize;
    }
  }
});
</script>
