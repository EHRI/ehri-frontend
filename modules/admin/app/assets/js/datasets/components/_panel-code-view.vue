<script lang="ts">


import {Compartment, EditorState} from "@codemirror/state";
import {basicSetup, EditorView} from "codemirror";
import {xml} from "@codemirror/lang-xml";
import {json} from "@codemirror/lang-json";
import {setValidationErrors, validationExtension} from "../codemirror-error-ext";

export default {
  props: {
    data: {
      type: String,
      default: '',
    },
    fileKey: String,
    contentType: {
      type: String,
      default: 'text/xml',
    },
    errors: {
      type: Array,
    }
  },
  data: function () {
    return {
      editor: null as EditorView | null,
      compartment: null as Compartment | null,
    }
  },
  methods: {
    codeMode: function () {
      if (this.contentType && this.contentType.includes("json")) {
        return json();
      } else {
        return xml();
      }
    },
    refresh: function () {
      this.editor?.requestMeasure();
    }
  },
  watch: {
    // When the key changes that means we've changed file, so
    // reset the viewer back to line 1
    fileKey: function (newValue, oldValue) {
      if (this.editor && newValue !== oldValue) {
        requestAnimationFrame(() => {
          this.editor.dispatch({
            effects: EditorView.scrollIntoView(0)
          });
        });
      }
    },
    contentType: function (newValue, oldValue) {
      if (this.compartment && (newValue !== oldValue)) {
        this.editor?.dispatch({
          effects: this.compartment.reconfigure([this.codeMode()])
        })
      }
    },
    data: function (incoming, oldValue) {
      if (this.editor) {
        const current = this.editor?.state.doc.toString()
        if (current !== incoming) {
          const scrollTop = this.editor.scrollDOM.scrollTop
          this.editor.dispatch({
            changes: {from: 0, to: this.editor.state.doc.length, insert: incoming}
          })
          requestAnimationFrame(() => {
            this.editor.scrollDOM.scrollTop = scrollTop
          })
        }

        // FIXME: why is this only needed some times, e.g for convert
        // previews but not file previews?
        if (incoming !== oldValue) {
          if (!this.prettifying) {
            this.prettified = false;
          }
          this.refresh();
        }
      }
    },
    errors: function (incoming) {
      this.editor?.dispatch({
        effects: setValidationErrors.of(this.errors)
      })
    }
  },
  mounted: function (): void {
    this.compartment = new Compartment();
    let state = EditorState.create({
      doc: this.data,
      extensions: [
        basicSetup,
        this.compartment.of(this.codeMode()),
        EditorState.readOnly.of(true),
        EditorView.editable.of(false),
        ...validationExtension,
      ]
    });

    this.editor = new EditorView({
      state,
      parent: this.$el,
    });
  },
  beforeUnmount: function () {
    this.editor?.destroy();
  },
}
</script>

<template>
  <div class="code-view-container"></div>
</template>

<style scoped>

</style>
