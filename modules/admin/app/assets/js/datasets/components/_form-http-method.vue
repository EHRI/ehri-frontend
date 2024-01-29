<script lang="ts">

export default {
  props: {
    modelValue: String,
  },
  data: function () {
    return {
      str: this.modelValue ? this.modelValue : "GET",
    }
  },
  methods: {
    update: function () {
      this.$emit("update:modelValue", this.str);
    },
  },
  watch: {
    str: function () {
      this.update();
    }
  }
}
</script>

<template>
  <div class="http-method">
    <fieldset>
      <div class="form-group">
        <label class="form-label" for="http-method">
          HTTP Method
        </label>
        <select id="http-method" class="form-control" v-model="str">
          <option value="GET" selected>GET</option>
          <option value="POST">POST</option>
        </select>
      </div>
    </fieldset>
  </div>
</template>
