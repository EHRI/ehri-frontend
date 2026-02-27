<script lang="ts">

import { EditorView, basicSetup } from 'codemirror'
import { json } from '@codemirror/lang-json'
import { EditorState } from '@codemirror/state'

export default {
  props: {
    modelValue: Object,
    resize: {
      type: Number,
    },
  },
  data() {
    return {
      error: false,
      editor: null as EditorView | null,
    }
  },
  watch: {
    resize() {
      this.editor?.requestMeasure()
    },
    modelValue(newVal) {
      // Only update editor if content actually differs, to avoid cursor jumping
      const current = this.editor?.state.doc.toString()
      const incoming = this.toString(newVal)
      if (current !== incoming) {
        this.editor?.dispatch({
          changes: { from: 0, to: this.editor?.state.doc.length, insert: incoming }
        })
      }
    }
  },
  methods: {
    fromString(s: string): object {
      try {
        const value = JSON.parse(s)
        this.error = false
        return value
      } catch (e) {
        this.error = true
        return {}
      }
    },
    toString(obj: object): string {
      return JSON.stringify(obj, null, 2)
    }
  },
  mounted() {
    let state = EditorState.create({
      extensions: [
        basicSetup,
        json(),
        EditorState.readOnly.of(false),
        EditorView.updateListener.of(update => {
          if (update.docChanged) {
            this.$emit('update:modelValue', this.fromString(update.state.doc.toString()))
          }
        }),
      ],
    });
    this.editor = new EditorView({
      state,
      doc: this.modelValue,
      parent: this.$el,
    });
  },
  beforeUnmount() {
    this.editor?.destroy()
  },
}
</script>

<template>
  <div v-bind:class="{'has-error': error}" class="json-editor"></div>
</template>

