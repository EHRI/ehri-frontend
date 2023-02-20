<script>

import _isObject from 'lodash/isObject';

export default {
  props: {
    modelValue: Object,
  },
  data: function() {
    return {
      show: _isObject(this.modelValue),
      header: this.modelValue ? this.modelValue.header : "",
    }
  },
  methods: {
    update: function() {
      this.$emit("update:modelValue", this.headerOpt)
    }
  },
  computed: {
    headerOpt: function() {
      return this.show ? {
        header: this.header
      } : null;
    },
  },
  watch: {
    show: function() {
      this.update();
    }
  }
}
</script>

<template>
  <div class="http-header-params">
    <div class="form-group">
      <div class="form-check">
        <input type="checkbox" class="form-check-input" id="opt-header" v-model="show"/>
        <label class="form-check-label" for="opt-header">
          HTTP Header
        </label>
      </div>
    </div>
    <fieldset v-if="show">
      <div class="form-group">
        <label class="form-label" for="header-value">
          Header
        </label>
        <input v-model="header" v-on:change="update" class="form-control" id="header-value" type="text" autocomplete="off"/>
      </div>
    </fieldset>
  </div>
</template>
