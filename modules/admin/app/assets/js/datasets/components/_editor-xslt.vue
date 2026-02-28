<script lang="ts">

import {basicSetup, EditorView} from 'codemirror'
import {xml} from '@codemirror/lang-xml';
import {EditorState} from "@codemirror/state";

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
  data: function() {
    return {
      editor: null as EditorView | null,
    }
  },
  watch: {
    resize: function () {
      this.editor.requestMeasure();
    },
    timestamp: function () {
      console.debug("XSLT editor value updated...")
      this.editor.dispatch({
        changes: { from: 0, to: this.editor.state.doc.length, insert: this.modelValue }
      });
    }
  },
  mounted: function () {
    this.editor = new EditorView({
      doc: this.modelValue,
      extensions: [
        basicSetup,
        xml(),
        EditorState.readOnly.of(false),
        EditorView.updateListener.of(update => {
          if (update.docChanged) {
            this.$emit('update:modelValue', this.editor.state.doc.toString());
          }
        })
      ],
      parent: this.$el
    });
    console.log("Initalised editor...")
  },
  beforeUnmount: function () {
    if (this.editor) {
      this.editor.destroy();
    }
  },
};
</script>

<template>
  <div class="xslt-editor"></div>
</template>

