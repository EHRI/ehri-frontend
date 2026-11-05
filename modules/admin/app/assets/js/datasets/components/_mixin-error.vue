<script lang="ts">
import {defineComponent} from 'vue';

export default defineComponent({
  data: function () {
    return {
      errored: false,
    };
  },
  methods: {
    showError: function (desc: string, exception: Error) {
      this.errored = true;
      this.$emit("error", desc, exception);
    },
    clearError: function () {
      this.errored = false;
    }
  }
});
</script>
