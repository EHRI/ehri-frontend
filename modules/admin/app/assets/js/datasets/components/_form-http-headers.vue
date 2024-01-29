<script lang="ts">

import _isArray from 'lodash/isArray';

export default {
  props: {
    modelValue: Array,
  },
  data: function () {
    return {
      show: _isArray(this.modelValue),
      str: this.modelValue ? this.serialize(this.modelValue) : "",
    }
  },
  methods: {
    update: function () {
      this.$emit("update:modelValue", this.headers);
    },
    deserialize: function (s: string): [string, string][] {
      if (s && s.trim()) {
        return s.split(/\s*\n\r?\s*/).map(s => s.split(/\s*:\s*/).slice(0, 2));
      } else {
        return [];
      }
    },
    serialize: function (value: [string, string][]) {
      return value.map(([k, v]) => k + ": " + v).join("\n");
    },
  },
  computed: {
    headers: function () {
      return this.show
          ? this.deserialize(this.str)
          : null;
    },
  },
  watch: {
    show: function () {
      this.update();
    },
    str: function () {
      this.update();
    }
  }
}
</script>

<template>
  <div class="http-headers">
    <div class="form-group">
      <div class="form-check">
        <input type="checkbox" class="form-check-input" id="opt-http-headers" v-model="show"/>
        <label class="form-check-label" for="opt-http-headers">
          HTTP Headers
        </label>
      </div>
    </div>
    <fieldset v-if="show">
      <div class="form-group">
        <label class="form-label" for="header-values">
          HTTP Headers
        </label>
        <textarea rows="3" v-model="str" class="form-control" id="header-values" type="text"
                  autocomplete="off"></textarea>
      </div>
    </fieldset>
  </div>
</template>
