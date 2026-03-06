<script lang="ts">


import {Compartment, EditorState} from "@codemirror/state";
import {basicSetup, EditorView} from "codemirror";
import {xml} from "@codemirror/lang-xml";
import {json} from "@codemirror/lang-json";
import {setValidationErrors, validationExtension} from "../codemirror-error-ext";

export default {
  props: {
    modelValue: {
      type: String,
      default: '',
    },
    fileKey: String,
    contentType: {
      type: String,
      default: 'text/xml',
    },
    readOnly: {
      type: Boolean,
      default: false,
    },
    errors: Array as [] | null,
  },
  emits: ["update:modelValue"],
  data: function () {
    return {
      editor: null as EditorView | null,
      compartment: null as Compartment | null,
    }
  },
  methods: {
    refresh: function () {
      this.editor?.requestMeasure();
    }
  },
  computed: {
    codeMode: function () {
      if (this.contentType && this.contentType.includes("json")) {
        return json();
      } else {
        return xml();
      }
    },
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
          effects: this.compartment.reconfigure([this.codeMode])
        })
      }
    },
    modelValue: function (incoming: string, oldValue: string) {
      if (this.editor) {
        const current = this.editor.state.doc.toString();
        if (current !== incoming) {
          const scrollTop = this.editor.scrollDOM.scrollTop;
          this.editor.dispatch({
            changes: {
              from: 0,
              to: this.editor.state.doc.length,
              insert: incoming
            }
          });
          requestAnimationFrame(() => {
            this.editor.scrollDOM.scrollTop = scrollTop;
          });
        }
      }
    },
    errors: function (incoming) {
      this.editor?.dispatch({
        effects: setValidationErrors.of(incoming)
      });
    }
  },
  mounted: function (): void {
    this.compartment = new Compartment();
    let state = EditorState.create({
      doc: this.modelValue,
      extensions: [
        basicSetup,
        this.compartment.of(this.codeMode),
        EditorState.readOnly.of(this.readOnly),
        ...validationExtension,
        EditorView.updateListener.of(update => {
          if (!this.readOnly && update.docChanged) {
            const nextValue = update.state.doc.toString();
            this.$emit('update:modelValue', nextValue);
          }
        }),
      ]
    });

    this.editor = new EditorView({
      state,
      parent: this.$el,
    });
    console.log("Setting initial errors: ", this.errors)
    this.editor.dispatch({
      effects: setValidationErrors.of(this.errors)
    })
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
