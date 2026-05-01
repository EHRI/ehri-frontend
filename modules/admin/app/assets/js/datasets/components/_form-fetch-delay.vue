<script lang="ts">

export default {
  props: {
    modelValue: Number,
  },
  data: function () {
    return {
      int: this.modelValue ? this.modelValue : null,
    }
  },
  methods: {
    update: function () {
      this.$emit("update:modelValue", this.int);
    },
  },
  watch: {
    int: function () {
      this.update();
    }
  }
}
</script>

<template>
  <div class="fetch-delay">
    <div class="form-group">
      <label class="form-label" for="opt-delay">
        File Fetching Delay (milliseconds)
      </label>
      <input class="form-control" id="opt-delay" min="0" type="number" v-model.trim="int" placeholder="(optional)"
             title="Wait for a period (in milliseconds) between fetching each file to prevent rate-limiting"/>
    </div>
  </div>
</template>
