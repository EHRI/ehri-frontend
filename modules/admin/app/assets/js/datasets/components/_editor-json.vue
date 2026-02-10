<script lang="ts">

import PanelCodeView from './_panel-code-view';

export default {
  props: {
    modelValue: Object,
    errors: {
      type: Array,
      default: [],
    }
  },
  emits: ['update:modelValue'],
  components: {PanelCodeView},
  data: function () {
    return {
      // Initialize the local string buffer
      internalText: JSON.stringify(this.modelValue, null, 2),
      localSyntaxError: null
    }
  },
  computed: {
    lineErrors: function () {
      return this.localSyntaxError ? [{
        line: 0,
        error: this.localSyntaxError
      }] : [];
    },
    // Merge external prop errors with local JSON validation errors.
    combinedErrors() {
      return [...this.errors, ...this.lineErrors];
    }
  },
  watch: {
    modelValue: {
      handler(newVal) {
        // Only update internalText if the object actually changed.
        try {
          const currentObj = JSON.parse(this.internalText);
          if (JSON.stringify(currentObj) !== JSON.stringify(newVal)) {
            this.internalText = JSON.stringify(newVal, null, 2);
          }
        } catch (e) {
          // If the current text is invalid JSON, overwrite it with the
          // new valid object coming from the parent.
          this.internalText = JSON.stringify(newVal, null, 2);
        }
      },
      deep: true
    },

    internalText(newText) {
      // Only emit if the string is valid JSON and different from current prop.
      try {
        const parsed = JSON.parse(newText);
        this.localSyntaxError = null;

        // Prevent infinite loops: only emit if the data actually changed
        if (JSON.stringify(parsed) !== JSON.stringify(this.modelValue)) {
          this.$emit('update:modelValue', parsed);
        }
      } catch (e) {
        this.localSyntaxError = "Invalid JSON syntax";
      }
    }
  },
}
</script>

<template>
  <panel-code-view
    v-model="internalText"
    v-bind:content-type="'application/json'"
    v-bind:errors="combinedErrors"/>
</template>

