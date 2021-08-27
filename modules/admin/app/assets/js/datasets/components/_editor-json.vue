<script lang="ts">

import CodeMirror from 'codemirror';
import 'codemirror/lib/codemirror.css';
import 'codemirror/mode/javascript/javascript';

export default {
  props: {
    value: Object,
    resize: {
      // this value provides a trigger to refresh the editor when size changes
      // it does not reflect the actual value of the panel
      type: Number,
    },
  },
  data: function() {
    return {
      error: false,
    }
  },
  methods: {
    fromString: function(s: string): object {
      try {
        let value = JSON.parse(s);
        this.error = false;
        return value;
      } catch (e) {
        this.error = true;
        return {};
      }
    },
    toString: function(obj: object): string {
      return JSON.stringify(obj, null, 2);
    }
  },
  mounted: function () {
    this.editor = CodeMirror.fromTextArea(this.$el.querySelector("textarea"), {
      mode: 'javascript',
      lineNumbers: false,
      readOnly: false,
    });
    this.editor.on("change", () => {
      this.$emit('input', this.fromString(this.editor.getValue()));
    });
  },
  beforeDestroy: function () {
    if (this.editor) {
      this.editor.toTextArea();
    }
  },
};
</script>

<template>
  <div v-bind:class="{'has-error': error}" class="json-editor">
    <textarea>{{ toString(value) }}</textarea>
  </div>
</template>

