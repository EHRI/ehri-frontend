<script lang="ts">

import CodeMirror from 'codemirror';
import 'codemirror/lib/codemirror.css';
import 'codemirror/mode/xml/xml';

export default {
  props: {
    modelValue: String,
    resize: {
      // this value provides a trigger to refresh the editor when size changes
      // it does not reflect the actual value of the panel
      type: Number,
    },
    timestamp: String,
  },
  watch: {
    resize: function() {
      this.editor.refresh();
    },
    timestamp: function() {
      console.debug("XSLT editor value updated...")
      this.editor.setValue(this.modelValue);
    }
  },
  mounted: function () {
    this.editor = CodeMirror.fromTextArea(this.$el.querySelector("textarea"), {
      mode: 'xml',
      lineNumbers: false,
      readOnly: false,
    });
    this.editor.on("change", () => {
      this.$emit('update:modelValue', this.editor.getValue());
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
  <div class="xslt-editor">
    <textarea>{{ modelValue }}</textarea>
  </div>
</template>

